import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
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

function validatePayload(body: unknown): RegistrationFormPayload {
  if (!body || typeof body !== "object") {
    throw new Error("Missing request body");
  }
  const b = body as Record<string, unknown>;

  // CRIT-4: honeypot field. The web form ships a hidden, aria-hidden
  // `company` input that real users never see / touch. Bots that
  // auto-fill every visible field will populate it — reject if non-empty.
  const honeypot = typeof b.company === "string" ? b.company.trim() : "";
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

  const phoneRaw = (typeof b.phone === "string" ? b.phone.trim() : "").slice(0, MAX_PHONE_LEN);
  if (!phoneRaw || !/^[+\d\s().-]{6,20}$/.test(phoneRaw)) {
    throw new Error("phone is required");
  }
  // CRIT-4: defuse Google Sheets formula injection. Sheets writes use
  // valueInputOption `USER_ENTERED` which evaluates `=`, `+`, `-`, `@`
  // prefixes as formulas — prefix with `'` so the cell stays literal.
  const phone = /^[=+\-@]/.test(phoneRaw) ? `'${phoneRaw}` : phoneRaw;

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
    // CRIT-4 (audit 2026-05-17, activated 2026-05-18): require a valid
    // Firebase App Check token on every request. The web form attaches
    // a reCAPTCHA Enterprise token via `X-Firebase-AppCheck` header;
    // bots without a valid attestation are rejected with 401 before
    // the handler runs. Mobile apps don't hit this endpoint.
    enforceAppCheck: true,
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
      tx.update(docRef, {
        paid: true,
        paidAt: FieldValue.serverTimestamp(),
        stripeSessionId: session.id,
      });
      return {
        kind: "flipped",
        snapshot: { ...data, paid: true, stripeSessionId: session.id },
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

// ---------------------------------------------------------------------------
// CRIT-1 (audit 2026-05-17) — server-side validation of registration codes.
//
// Both callables replace the client-side Firestore queries that used to live in
// `ApiClient.lookupGameByValidationCode` / `validateRegistrationCode` (iOS) and
// `FirestoreRepository.lookupGameByValidationCode` / `validateRegistrationCode`
// (Android). Moving the lookup server-side lets `firestore.rules` lock
// `/eventRegistrations` reads to `if false` so anonymous users can't enumerate
// the collection and exfiltrate the email/phone/name PII of every paying
// player. The callables return only what the JoinFlow needs (a boolean, or a
// discriminated `{type, gameId, batchId}` payload), never PII.
// ---------------------------------------------------------------------------

interface ValidateRegistrationCodeInput {
  batchId?: string;
  code?: string;
}

interface LookupGameByValidationCodeInput {
  code?: string;
}

type LookupGameByValidationCodeResult =
  | { type: "invalidCode" }
  | { type: "gameNotYetCreated"; batchId: string }
  | { type: "gameReady"; gameId: string; batchId: string };

function normalizeBatchId(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeCode(value: unknown): string {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}

/**
 * `validateRegistrationCode(batchId, code) -> { valid: boolean }`.
 * Returns true iff a paid eventRegistration exists for that pair.
 * Requires an authenticated caller (anonymous Auth is enough — the gate
 * is to prevent unauth curl scraping). Empty inputs return false rather
 * than throwing so the JoinFlow can stay on the entry step.
 */
export const validateRegistrationCode = onCall<
  ValidateRegistrationCodeInput,
  Promise<{ valid: boolean }>
>({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const batchId = normalizeBatchId(request.data?.batchId);
  const code = normalizeCode(request.data?.code);
  if (!batchId || !code) return { valid: false };

  const snap = await db()
    .collection(COLLECTION)
    .where("batchId", "==", batchId)
    .where("code", "==", code)
    .where("paid", "==", true)
    .limit(1)
    .get();

  return { valid: !snap.empty };
});

/**
 * `lookupGameByValidationCode(code) -> {type, ...}`. Used by the
 * `pouleparty.be/join?code=…` deeplink resolution path. Distinguishes
 * three outcomes so the JoinFlow can show the right UI:
 *   - `invalidCode` — no paid registration matches the code
 *   - `gameNotYetCreated` — paid registration exists but no Game with
 *     `registrationBatchId == batchId` yet
 *   - `gameReady` — paid registration + active (non-`done`) Game found
 * Only the `gameId` + `batchId` are returned; the client fetches the
 * Game doc separately via the existing `/games/{id}` read path.
 */
export const lookupGameByValidationCode = onCall<
  LookupGameByValidationCodeInput,
  Promise<LookupGameByValidationCodeResult>
>({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }
  const code = normalizeCode(request.data?.code);
  if (!code) return { type: "invalidCode" };

  const regSnap = await db()
    .collection(COLLECTION)
    .where("code", "==", code)
    .where("paid", "==", true)
    .limit(1)
    .get();
  const regDoc = regSnap.docs[0];
  if (!regDoc) return { type: "invalidCode" };

  const batchIdRaw = (regDoc.data() as { batchId?: unknown }).batchId;
  const batchId = normalizeBatchId(batchIdRaw);
  if (!batchId) return { type: "invalidCode" };

  const gameSnap = await db()
    .collection("games")
    .where("registrationBatchId", "==", batchId)
    .limit(5)
    .get();
  const active = gameSnap.docs.find(
    (d) => (d.data() as { status?: string }).status !== "done"
  );
  if (active) {
    return { type: "gameReady", gameId: active.id, batchId };
  }
  return { type: "gameNotYetCreated", batchId };
});
