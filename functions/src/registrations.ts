import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getAppCheck } from "firebase-admin/app-check";
import { onRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { defineSecret } from "firebase-functions/params";
import { randomInt } from "crypto";
// Stripe v22 ships as `export = StripeConstructor`, so the
// `import = require(…)` form is what makes both the constructor
// (`new Stripe(...)`) and its namespace types resolve from the same
// import. The nested types (`Stripe.Event`, `Stripe.Checkout.Session`)
// aren't directly addressable on the imported identifier, so the
// handler infers them via `ReturnType<typeof stripe.webhooks.…>`
// + discriminated-union narrowing on `event.type`.
import Stripe = require("stripe");

import { sendRegistrationConfirmationEmail } from "./email/registrationConfirmation";
import { appendRegistrationRow, markRegistrationRefunded } from "./sheets";

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

export function originFor(req: { headers: Record<string, string | string[] | undefined> }): string {
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

export function basePathForLocale(locale: string): string {
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
  /** XPLAT-H5 (store-audit 2026-05-18): ISO-8601 timestamp captured
   *  client-side when the buyer ticked the T&C / Privacy checkbox.
   *  Required (CRD Art. 8(2) explicit consent + audit trail). */
  consentAcknowledgedAt?: string | null;
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
  /** Persisted on the first paid flip so the refund webhook can find
   *  the registration by `payment_intent` without an extra Stripe API
   *  round-trip. Falls back to a `checkout.sessions.list` lookup for
   *  docs created before this field was introduced. */
  stripePaymentIntentId?: string;
  /** Set by `charge.refunded` webhook on a FULL refund. Pairs with
   *  `paid: false` so the Google Sheet view of paid attendees stays
   *  accurate and the wristband desk on D-Day skips refunded codes. */
  refunded?: boolean;
  refundedAt?: Timestamp;
  locale: string;
  /** XPLAT-H5 (store-audit 2026-05-18): timestamp persisted from the
   *  client-side consent checkbox (CRD Art. 8(2) audit trail). The
   *  validator rejects the submit if missing. */
  consentAcknowledgedAt: Timestamp;
}

function db() {
  return getFirestore();
}

// CRIT-4 (audit 2026-05-17): allowlist of accepted batchIds. Today only
// D-Day 06/06/2026 is in scope. Adding a new event = add it here. Without
// this allowlist any string was accepted as a batchId, polluting Firestore
// + Stripe with garbage / spoofed registrations.
const ALLOWED_BATCH_IDS = new Set<string>([
  "game-06-06-2026",
]);

// CRIT-4: max lengths on free-text fields. The previous validation only
// checked `length > 0`, so a 10 MB playerName could pass and either
// blow the Firestore 1 MB doc limit (failing AFTER the Stripe Checkout
// session was already created → orphan Stripe session) or hit Stripe's
// 500-char metadata cap.
const MAX_NAME_LEN = 60;
const MAX_EMAIL_LEN = 254; // RFC 5321
const MAX_PHONE_LEN = 20;

export function validatePayload(body: unknown): RegistrationFormPayload {
  if (!body || typeof body !== "object") {
    throw new Error("Missing request body");
  }
  const b = body as Record<string, unknown>;

  // CRIT-4: honeypot field. The web form ships a hidden, aria-hidden
  // input that real users never see or touch. Bots that auto-fill every
  // visible field will populate it — reject if non-empty.
  // XPLAT-staging-fix 2026-05-18: renamed from `company` because Chrome
  // autofill was populating it with the user's Google profile
  // organization despite `autoComplete="off"`, triggering false-positive
  // 400 "invalid request" on every form submit.
  const honeypot = typeof b.nicknameAlt === "string" ? b.nicknameAlt.trim() : "";
  if (honeypot.length > 0) {
    throw new Error("invalid request");
  }

  const batchId = typeof b.batchId === "string" ? b.batchId.trim() : "";
  if (!batchId) throw new Error("batchId is required");
  if (!ALLOWED_BATCH_IDS.has(batchId)) {
    throw new Error("batchId is not recognized");
  }

  const playerName = (typeof b.playerName === "string" ? b.playerName.trim() : "").slice(0, MAX_NAME_LEN);
  if (!playerName) throw new Error("playerName is required");

  const teamName = (typeof b.teamName === "string" ? b.teamName.trim() : "").slice(0, MAX_NAME_LEN);
  if (!teamName) throw new Error("teamName is required");

  const emailRaw = typeof b.email === "string" ? b.email.trim().toLowerCase() : "";
  if (
    !emailRaw ||
    emailRaw.length > MAX_EMAIL_LEN ||
    // CRIT-4: tighten email regex — the old `/^\S+@\S+\.\S+$/` accepted
    // `\r\n` (whitespace minus tab) so an email like
    // `foo@bar.com\r\nBcc: victim@…` could survive `.trim()` and slip
    // CR/LF into the Resend HTTP API → header-injection risk.
    !/^[^\s@,]+@[^\s@,]+\.[^\s@,]+$/.test(emailRaw)
  ) {
    throw new Error("Valid email is required");
  }
  const email = emailRaw;

  const phone = (typeof b.phone === "string" ? b.phone.trim() : "").slice(0, MAX_PHONE_LEN);
  if (!phone || !/^[+\d\s().-]{6,20}$/.test(phone)) {
    throw new Error("phone is required");
  }

  const teamSizeRaw = typeof b.teamSize === "number" ? b.teamSize : Number(b.teamSize);
  const teamSize = ALLOWED_TEAM_SIZES.find((s) => s === teamSizeRaw);
  if (teamSize === undefined) {
    throw new Error("teamSize must be 3, 4, or 5");
  }

  const locale = typeof b.locale === "string" && b.locale.length === 2 ? b.locale : "fr";

  // XPLAT-H5 (store-audit 2026-05-18): explicit consent (CRD Art. 8(2))
  // must be present. The web form's checkbox cannot be ticked
  // server-side, so a missing/empty value is a sign of a bot, an old
  // cached page, or a tampered request.
  const consentRaw = typeof b.consentAcknowledgedAt === "string" ? b.consentAcknowledgedAt.trim() : "";
  if (!consentRaw || Number.isNaN(Date.parse(consentRaw))) {
    throw new Error("consentAcknowledgedAt is required");
  }
  const consentAcknowledgedAt = consentRaw;

  return { batchId, playerName, teamName, email, phone, teamSize, locale, consentAcknowledgedAt };
}

// 6-char uppercase alphanum. Skips ambiguous chars (0/O, 1/I) so the
// code stays readable when typed from the email at the bar.
export const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function generateCode(): string {
  // CRIT-5 (audit 2026-05-17): use `crypto.randomInt` instead of
  // `Math.random()`. V8's PRNG is xorshift128+, which is non-cryptographic
  // and has published prediction techniques — an attacker who captured a
  // few codes could in principle predict subsequent ones. With 32^6 ≈
  // 1.07 B space and ~50 valid codes per batch, the entropy gate only
  // holds if the codes are truly unpredictable.
  let out = "";
  for (let i = 0; i < 6; i += 1) {
    out += CODE_ALPHABET[randomInt(0, CODE_ALPHABET.length)];
  }
  return out;
}

/**
 * CRIT-5 (audit 2026-05-17): generate a unique code AND reserve the
 * registration doc inside a single Firestore transaction, so two
 * concurrent form submits can't both land on the same code (the previous
 * `query-then-set` pattern was a TOCTOU — both callers saw `empty` then
 * both wrote the same id, leaving one of them unreachable from JoinFlow's
 * `limit 1` lookup).
 *
 * Returns the reserved registration id + code. Caller fills in the rest
 * of the doc fields via a later `update(stripeSessionId)`.
 */
async function reserveRegistrationCode(
  batchId: string,
  baseFields: Omit<RegistrationDoc, "code" | "registrationId">
): Promise<{ registrationId: string; code: string }> {
  const docRef = db().collection(COLLECTION).doc();
  const registrationId = docRef.id;
  return await db().runTransaction(async (tx) => {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      const code = generateCode();
      const collision = await tx.get(
        db()
          .collection(COLLECTION)
          .where("batchId", "==", batchId)
          .where("code", "==", code)
          .limit(1)
      );
      if (collision.empty) {
        const doc: RegistrationDoc = { ...baseFields, registrationId, code };
        tx.set(docRef, doc);
        return { registrationId, code };
      }
    }
    throw new Error(
      "Could not generate unique registration code after 5 attempts"
    );
  });
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
    // CRIT-4 (audit 2026-05-17): bounded autoscale + explicit CORS
    // allowlist. The previous `cors: true` accepted any origin, and the
    // default scaling let a small script blow up Stripe API quota +
    // Firestore writes within minutes. 10 instances × ~50 req/s/instance
    // caps the worst case while leaving generous headroom for D-Day
    // peak traffic (~150 paying registrations).
    cors: [
      "https://pouleparty.be",
      "https://pouleparty-ba586.web.app",
      "https://pouleparty-prod.web.app",
      "http://localhost:5173",
    ],
    maxInstances: 10,
    concurrency: 50,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    // CRIT-4 (audit 2026-05-17, enforced 2026-05-18): verify the
    // Firebase App Check token before running any handler logic.
    // `onRequest` doesn't support the `enforceAppCheck` option (it's
    // callable-only); manual verification via admin SDK is the documented
    // pattern for HTTP functions. The web form attaches a reCAPTCHA
    // Enterprise token via `X-Firebase-AppCheck`. Mobile apps don't hit
    // this endpoint.
    const appCheckHeader = req.header("X-Firebase-AppCheck");
    if (!appCheckHeader) {
      logger.warn("createPendingRegistration: missing App Check token");
      res.status(401).json({ error: "Missing App Check token" });
      return;
    }
    try {
      await getAppCheck().verifyToken(appCheckHeader);
    } catch (err) {
      logger.warn("createPendingRegistration: App Check verify failed", err);
      res.status(401).json({ error: "Invalid App Check token" });
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
      // CRIT-5 (audit 2026-05-17): reserve the registration doc + the
      // unique code inside a single transaction. The previous
      // query-then-set pattern allowed two concurrent submits to land
      // on the same code.
      const reservation = await reserveRegistrationCode(payload.batchId, {
        batchId: payload.batchId,
        playerName: payload.playerName,
        teamName: payload.teamName,
        email: payload.email,
        phone: payload.phone,
        teamSize: payload.teamSize,
        paid: false,
        createdAt: Timestamp.now(),
        locale: payload.locale ?? "fr",
        consentAcknowledgedAt: Timestamp.fromDate(new Date(payload.consentAcknowledgedAt!)),
      });
      const registrationId = reservation.registrationId;
      const docRef = db().collection(COLLECTION).doc(registrationId);

      const stripe = new Stripe(STRIPE_SECRET_KEY.value());
      // HIGH-4 (audit 2026-05-17): pass an idempotency key so a network
      // hiccup that drops the create-session response can't accidentally
      // mint two charges when the user re-submits. The registrationId is
      // already unique per call (Firestore auto-id from
      // reserveRegistrationCode), so it's a perfect natural key.
      const session = await stripe.checkout.sessions.create(
        {
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
                  name: "PouleParty — Inscription événement physique 06/06/2026 Ixelles",
                  description: `Inscription événement en présentiel, samedi 6 juin 2026, 20h30, Ixelles (Bruxelles). Équipe « ${payload.teamName} » (${payload.teamSize} joueur·euse·s).`,
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
        },
        { idempotencyKey: `checkout-${registrationId}` }
      );

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

    // Refund branch — fires when an inscription is refunded from the
    // Stripe Dashboard (or via API). FULL refunds mark the row as
    // refunded in the Google Sheet so the wristband desk on D-Day
    // skips the code. Partial refunds are ignored (e.g. refunding one
    // player from a team of 4 to drop down to 3 — the inscription is
    // still valid for the remaining players).
    if (event.type === "charge.refunded") {
      const charge = event.data.object;
      if (charge.amount_refunded < charge.amount) {
        logger.info(
          `charge.refunded ${charge.id}: partial refund (${charge.amount_refunded}/${charge.amount}), keeping registration valid`
        );
        res.status(200).json({ received: true, ignored: "partial-refund" });
        return;
      }
      const paymentIntentId =
        typeof charge.payment_intent === "string"
          ? charge.payment_intent
          : charge.payment_intent?.id ?? null;
      if (!paymentIntentId) {
        logger.warn(`charge.refunded ${charge.id}: missing payment_intent`);
        res.status(200).json({ received: true, ignored: "no-payment-intent" });
        return;
      }

      let registrationId: string | null = null;
      const indexed = await db()
        .collection(COLLECTION)
        .where("stripePaymentIntentId", "==", paymentIntentId)
        .limit(1)
        .get();
      if (!indexed.empty) {
        registrationId = indexed.docs[0].id;
      } else {
        // Fallback for docs paid before `stripePaymentIntentId` was
        // persisted — one extra Stripe API call to map PI → session →
        // client_reference_id.
        const sessions = await stripe.checkout.sessions.list({
          payment_intent: paymentIntentId,
          limit: 1,
        });
        registrationId = sessions.data[0]?.client_reference_id ?? null;
      }

      if (!registrationId) {
        logger.warn(
          `charge.refunded ${charge.id}: could not resolve to a registration (pi=${paymentIntentId})`
        );
        res.status(200).json({ received: true, ignored: "no-registration" });
        return;
      }

      const refundDocRef = db().collection(COLLECTION).doc(registrationId);
      const refundResult = await db().runTransaction<
        { kind: "notFound" } | { kind: "alreadyRefunded" } | { kind: "flipped" }
      >(async (tx) => {
        const snap = await tx.get(refundDocRef);
        if (!snap.exists) return { kind: "notFound" };
        const data = snap.data() as RegistrationDoc;
        if (data.refunded === true) return { kind: "alreadyRefunded" };
        tx.update(refundDocRef, {
          paid: false,
          refunded: true,
          refundedAt: FieldValue.serverTimestamp(),
        });
        return { kind: "flipped" };
      });

      if (refundResult.kind === "notFound") {
        logger.error(
          `charge.refunded ${charge.id}: registration ${registrationId} doesn't exist`
        );
        res.status(200).json({ received: true, error: "registration-not-found" });
        return;
      }
      if (refundResult.kind === "alreadyRefunded") {
        logger.info(
          `charge.refunded re-delivery for ${registrationId} — already refunded, noop`
        );
        res.status(200).json({ received: true, idempotent: true });
        return;
      }
      // Side effect (post-transaction, same pattern as the paid flip):
      // mirror the refunded state into the Google Sheet so the roster
      // Martin reads stays in sync. Independent try/catch so a Sheets
      // outage doesn't 5xx Stripe (which would retry the webhook and
      // hit the idempotency guard above, losing this call entirely).
      try {
        await markRegistrationRefunded(registrationId, GOOGLE_SHEET_ID.value());
      } catch (err) {
        logger.error(`Sheet refund mark failed for ${registrationId}`, err);
      }

      logger.info(
        `Registration ${registrationId} marked refunded (charge ${charge.id})`
      );
      res.status(200).json({ received: true });
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

    // HIGH-5 (audit 2026-05-17): defense-in-depth cross-check the
    // session shape before flipping `paid`. Stripe is the source of
    // truth for "did the user pay", but the webhook envelope is signed
    // not encrypted — a compromised webhook secret would let an
    // attacker forge a checkout.session.completed for any
    // client_reference_id they guessed. Asserting the amount + currency
    // + payment_status here blocks 0-amount forgeries, currency swaps,
    // and "pending" sessions from sneaking through.
    if (session.payment_status !== "paid") {
      logger.warn(`Webhook for ${registrationId}: payment_status=${session.payment_status}, refusing`);
      res.status(200).json({ received: true, ignored: "not-paid" });
      return;
    }
    if (session.currency !== CURRENCY) {
      logger.warn(`Webhook for ${registrationId}: currency=${session.currency}, expected ${CURRENCY}`);
      res.status(200).json({ received: true, ignored: "wrong-currency" });
      return;
    }
    if (session.mode !== "payment") {
      logger.warn(`Webhook for ${registrationId}: mode=${session.mode}, expected 'payment'`);
      res.status(200).json({ received: true, ignored: "wrong-mode" });
      return;
    }

    const docRef = db().collection(COLLECTION).doc(registrationId);

    // Idempotent flip: a second delivery of the same event MUST be a noop.
    // HIGH-5 cross-check: also verify the amount matches the expected
    // teamSize × UNIT_PRICE_CENTS for this registration. Done inside
    // the transaction so the check sees the canonical doc state.
    // HIGH-FN-M9 (audit 2026-05-17): catch the missing-doc case
    // explicitly so Stripe doesn't retry the webhook for 3 days on a
    // doc that was deleted between checkout creation and webhook
    // delivery.
    const result = await db().runTransaction<
      | { kind: "notFound" }
      | { kind: "amountMismatch"; expected: number; got: number | null }
      | { kind: "alreadyPaid"; snapshot: RegistrationDoc }
      | { kind: "flipped"; snapshot: RegistrationDoc }
    >(async (tx) => {
      const snap = await tx.get(docRef);
      if (!snap.exists) return { kind: "notFound" };
      const data = snap.data() as RegistrationDoc;
      const expected = data.teamSize * UNIT_PRICE_CENTS;
      if (session.amount_total !== expected) {
        return { kind: "amountMismatch", expected, got: session.amount_total };
      }
      if (data.paid) return { kind: "alreadyPaid", snapshot: data };
      const paymentIntentId =
        typeof session.payment_intent === "string"
          ? session.payment_intent
          : session.payment_intent?.id;
      tx.update(docRef, {
        paid: true,
        paidAt: FieldValue.serverTimestamp(),
        stripeSessionId: session.id,
        ...(paymentIntentId ? { stripePaymentIntentId: paymentIntentId } : {}),
      });
      return {
        kind: "flipped",
        snapshot: {
          ...data,
          paid: true,
          stripeSessionId: session.id,
          ...(paymentIntentId ? { stripePaymentIntentId: paymentIntentId } : {}),
        },
      };
    });

    if (result.kind === "notFound") {
      logger.error(`Webhook for non-existent registration ${registrationId} — was the doc deleted?`);
      res.status(200).json({ received: true, error: "registration-not-found" });
      return;
    }
    if (result.kind === "amountMismatch") {
      logger.warn(
        `Webhook for ${registrationId}: amount_total=${result.got}, expected ${result.expected}; refusing flip`
      );
      res.status(200).json({ received: true, ignored: "amount-mismatch" });
      return;
    }
    const wasFirstFlip = result.kind === "flipped";
    const snapshot = result.snapshot;

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
