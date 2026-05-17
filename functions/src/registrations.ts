import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { defineSecret } from "firebase-functions/params";
// Stripe v22 ships as `export = StripeConstructor`, so the
// `import = require(…)` form is what makes both the constructor
// (`new Stripe(...)`) and its namespace types resolve from the same
// import. The nested types (`Stripe.Event`, `Stripe.Checkout.Session`)
// aren't directly addressable on the imported identifier, so the
// handler infers them via `ReturnType<typeof stripe.webhooks.…>`
// + discriminated-union narrowing on `event.type`.
import Stripe = require("stripe");

import { sendRegistrationConfirmationEmail } from "./email/registrationConfirmation";
import { appendRegistrationRow } from "./sheets";

// PP-52 — Pre-paid event registrations from the public web form.
// Top-level collection `/eventRegistrations/{rid}` deliberately
// decoupled from `/games` (the form runs before any Game exists).
// See CLAUDE.md "Firestore data model" and PP-52.

const REGION = "europe-west1";
const COLLECTION = "eventRegistrations";

const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");
const STRIPE_WEBHOOK_SECRET = defineSecret("STRIPE_WEBHOOK_SECRET");
const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
const GOOGLE_SHEET_ID = defineSecret("GOOGLE_SHEET_ID");

// 12.00 EUR per player. Charged as teamSize × UNIT_PRICE_CENTS.
const UNIT_PRICE_CENTS = 1200;
const CURRENCY = "eur";
const ALLOWED_TEAM_SIZES = [3, 4, 5] as const;
type TeamSize = (typeof ALLOWED_TEAM_SIZES)[number];

// Where Stripe Checkout returns the user — built off the request's
// `Origin` header so staging form (pouleparty-ba586.web.app) bounces
// back to staging and prod form (pouleparty.be) bounces back to prod.
// Fallback to prod when the header is missing (e.g. a non-browser
// client). Keep paths aligned with the React routes in `web/src/`.
const FALLBACK_ORIGIN = "https://pouleparty.be";
const ALLOWED_ORIGINS = new Set([
  "https://pouleparty.be",
  "https://pouleparty-ba586.web.app",
  "https://pouleparty-prod.web.app",
  "http://localhost:5173",
]);

function originFor(req: { headers: Record<string, string | string[] | undefined> }): string {
  const raw = req.headers.origin;
  const origin = typeof raw === "string" ? raw : undefined;
  if (origin && ALLOWED_ORIGINS.has(origin)) return origin;
  return FALLBACK_ORIGIN;
}

// Mirror of `web/src/pages/inscriptionPaths.ts`. Used to build the
// Stripe Checkout `success_url` / `cancel_url` on the same slug the
// visitor came from so they don't get bounced into a foreign language.
const LOCALE_BASE_PATH: Record<string, string> = {
  fr: "/inscription",
  en: "/registration",
  nl: "/inschrijving",
};

function basePathForLocale(locale: string): string {
  return LOCALE_BASE_PATH[locale] ?? LOCALE_BASE_PATH.fr;
}

interface RegistrationFormPayload {
  batchId: string;
  playerName: string;
  teamName: string;
  email: string;
  phone: string;
  teamSize: TeamSize;
  locale?: string;
}

interface RegistrationDoc {
  registrationId: string;
  batchId: string;
  playerName: string;
  teamName: string;
  email: string;
  phone: string;
  teamSize: TeamSize;
  code: string;
  paid: boolean;
  createdAt: Timestamp;
  paidAt?: Timestamp;
  stripeSessionId?: string;
  locale: string;
}

function db() {
  return getFirestore();
}

function validatePayload(body: unknown): RegistrationFormPayload {
  if (!body || typeof body !== "object") {
    throw new Error("Missing request body");
  }
  const b = body as Record<string, unknown>;

  const batchId = typeof b.batchId === "string" ? b.batchId.trim() : "";
  if (!batchId) throw new Error("batchId is required");

  const playerName = typeof b.playerName === "string" ? b.playerName.trim() : "";
  if (!playerName) throw new Error("playerName is required");

  const teamName = typeof b.teamName === "string" ? b.teamName.trim() : "";
  if (!teamName) throw new Error("teamName is required");

  const email = typeof b.email === "string" ? b.email.trim().toLowerCase() : "";
  if (!email || !/^\S+@\S+\.\S+$/.test(email)) throw new Error("Valid email is required");

  const phone = typeof b.phone === "string" ? b.phone.trim() : "";
  if (!phone) throw new Error("phone is required");

  const teamSizeRaw = typeof b.teamSize === "number" ? b.teamSize : Number(b.teamSize);
  const teamSize = ALLOWED_TEAM_SIZES.find((s) => s === teamSizeRaw);
  if (teamSize === undefined) {
    throw new Error("teamSize must be 3, 4, or 5");
  }

  const locale = typeof b.locale === "string" && b.locale.length === 2 ? b.locale : "fr";

  return { batchId, playerName, teamName, email, phone, teamSize, locale };
}

// 6-char uppercase alphanum. Skips ambiguous chars (0/O, 1/I) so the
// code stays readable when typed from the email at the bar.
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function generateCode(): string {
  let out = "";
  for (let i = 0; i < 6; i += 1) {
    out += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  }
  return out;
}

async function generateUniqueCode(batchId: string): Promise<string> {
  // 32^6 ≈ 1.07 billion combinations vs ≤ ~50 registrations per batch
  // makes collisions effectively impossible. The retry loop is a
  // safety net, not a hot path.
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const code = generateCode();
    const collision = await db()
      .collection(COLLECTION)
      .where("batchId", "==", batchId)
      .where("code", "==", code)
      .limit(1)
      .get();
    if (collision.empty) return code;
  }
  throw new Error("Could not generate unique registration code after 5 attempts");
}

/**
 * Step 1 of the registration flow. Public HTTPS endpoint hit by the
 * web form. Creates a pending registration (`paid: false`) and a
 * Stripe Checkout Session whose `client_reference_id` matches the
 * registration doc id. The user is then redirected to the Stripe URL.
 * The webhook flips `paid: true` once Stripe confirms the payment.
 */
export const createPendingRegistration = onRequest(
  {
    region: REGION,
    secrets: [STRIPE_SECRET_KEY],
    cors: true,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    let payload: RegistrationFormPayload;
    try {
      payload = validatePayload(req.body);
    } catch (err) {
      res.status(400).json({ error: (err as Error).message });
      return;
    }

    try {
      const origin = originFor(req);
      const docRef = db().collection(COLLECTION).doc();
      const registrationId = docRef.id;
      const code = await generateUniqueCode(payload.batchId);

      const doc: RegistrationDoc = {
        registrationId,
        batchId: payload.batchId,
        playerName: payload.playerName,
        teamName: payload.teamName,
        email: payload.email,
        phone: payload.phone,
        teamSize: payload.teamSize,
        code,
        paid: false,
        createdAt: Timestamp.now(),
        locale: payload.locale ?? "fr",
      };
      await docRef.set(doc);

      const stripe = new Stripe(STRIPE_SECRET_KEY.value());
      const session = await stripe.checkout.sessions.create({
        mode: "payment",
        client_reference_id: registrationId,
        customer_email: payload.email,
        line_items: [
          {
            quantity: payload.teamSize,
            price_data: {
              currency: CURRENCY,
              unit_amount: UNIT_PRICE_CENTS,
              product_data: {
                name: "PouleParty — Inscription D-Day 06/06/2026",
                description: `Équipe « ${payload.teamName} » (${payload.teamSize} joueur·euse·s)`,
              },
            },
          },
        ],
        metadata: {
          registrationId,
          batchId: payload.batchId,
          teamName: payload.teamName,
        },
        success_url: `${origin}${basePathForLocale(payload.locale ?? "fr")}/success?session_id={CHECKOUT_SESSION_ID}`,
        cancel_url: `${origin}${basePathForLocale(payload.locale ?? "fr")}/cancel?batchId=${encodeURIComponent(payload.batchId)}`,
      });

      await docRef.update({ stripeSessionId: session.id });

      logger.info(`Pending registration ${registrationId} created for batch ${payload.batchId}`);
      res.status(200).json({
        registrationId,
        checkoutUrl: session.url,
      });
    } catch (err) {
      logger.error("createPendingRegistration failed", err);
      res.status(500).json({ error: "Internal error creating registration" });
    }
  }
);

/**
 * Step 2 of the registration flow. Stripe webhook hit on
 * `checkout.session.completed`. Verifies the signature, idempotently
 * flips `paid: true`, then (only on the first successful flip) sends
 * the confirmation email + appends the Google Sheet row.
 */
export const confirmRegistrationPayment = onRequest(
  {
    region: REGION,
    secrets: [STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, RESEND_API_KEY, GOOGLE_SHEET_ID],
  },
  async (req, res) => {
    const signature = req.headers["stripe-signature"];
    if (!signature || typeof signature !== "string") {
      res.status(400).send("Missing Stripe-Signature header");
      return;
    }

    const stripe = new Stripe(STRIPE_SECRET_KEY.value());
    let event: ReturnType<typeof stripe.webhooks.constructEvent>;
    try {
      event = stripe.webhooks.constructEvent(
        req.rawBody,
        signature,
        STRIPE_WEBHOOK_SECRET.value()
      );
    } catch (err) {
      logger.warn("Stripe webhook signature verification failed", err);
      res.status(400).send(`Invalid signature: ${(err as Error).message}`);
      return;
    }

    // We only care about completed Checkout Sessions. Acknowledge
    // every other event with 200 so Stripe doesn't keep retrying.
    // The discriminated-union narrowing on `event.type` types
    // `event.data.object` as `Stripe.Checkout.Session` automatically.
    if (event.type !== "checkout.session.completed") {
      res.status(200).json({ received: true, ignored: event.type });
      return;
    }

    const session = event.data.object;
    const registrationId = session.client_reference_id;
    if (!registrationId) {
      logger.warn("checkout.session.completed missing client_reference_id", session.id);
      res.status(200).json({ received: true, ignored: "missing-reference" });
      return;
    }

    const docRef = db().collection(COLLECTION).doc(registrationId);

    // Idempotent flip: a second delivery of the same event MUST be a noop.
    const { wasFirstFlip, snapshot } = await db().runTransaction(async (tx) => {
      const snap = await tx.get(docRef);
      if (!snap.exists) {
        throw new Error(`Registration ${registrationId} not found`);
      }
      const data = snap.data() as RegistrationDoc;
      if (data.paid) {
        return { wasFirstFlip: false, snapshot: data };
      }
      tx.update(docRef, {
        paid: true,
        paidAt: FieldValue.serverTimestamp(),
        stripeSessionId: session.id,
      });
      return { wasFirstFlip: true, snapshot: { ...data, paid: true, stripeSessionId: session.id } };
    });

    if (!wasFirstFlip) {
      logger.info(`Webhook re-delivery for ${registrationId} — already paid, skipping side effects`);
      res.status(200).json({ received: true, idempotent: true });
      return;
    }

    // Side effects run AFTER the transaction. If either fails we log
    // but still return 200 — the registration is marked paid (source
    // of truth) and the failure is recoverable manually. Returning a
    // 5xx here would make Stripe retry the webhook, which would hit
    // the idempotency guard above and skip these calls entirely.
    try {
      await sendRegistrationConfirmationEmail(snapshot, RESEND_API_KEY.value());
    } catch (err) {
      logger.error(`Resend email failed for ${registrationId}`, err);
    }
    try {
      await appendRegistrationRow(snapshot, GOOGLE_SHEET_ID.value());
    } catch (err) {
      logger.error(`Google Sheet append failed for ${registrationId}`, err);
    }

    logger.info(`Registration ${registrationId} marked paid (Stripe session ${session.id})`);
    res.status(200).json({ received: true });
  }
);
