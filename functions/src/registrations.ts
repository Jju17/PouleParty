import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { getAppCheck } from "firebase-admin/app-check";
import { onRequest, onCall, HttpsError } from "firebase-functions/v2/https";
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

import { CALLABLE_OPTIONS, GOOGLEAPIS_MEMORY, requireUid } from "./config";
import { eventBatch } from "./events";
import { fetchWithRetry } from "./http";
import { sendRegistrationConfirmationEmail } from "./email/registrationConfirmation";
import { appendRegistrationRow, markRegistrationRefunded } from "./sheets";

const REGION = "europe-west1";
const COLLECTION = "eventRegistrations";

const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");
const STRIPE_WEBHOOK_SECRET = defineSecret("STRIPE_WEBHOOK_SECRET");
const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
const GOOGLE_SHEET_ID = defineSecret("GOOGLE_SHEET_ID");

const CURRENCY = "eur";
const ALLOWED_TEAM_SIZES = [3, 4, 5] as const;
type TeamSize = (typeof ALLOWED_TEAM_SIZES)[number];

// Where Stripe Checkout returns the user, built off the request's
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

const LOCALE_INSCRIPTION_PATH: Record<string, string> = {
  fr: "/fr/inscription",
  en: "/en/registration",
  nl: "/nl/inschrijving",
};

export function basePathForLocale(locale: string): string {
  return LOCALE_INSCRIPTION_PATH[locale] ?? LOCALE_INSCRIPTION_PATH.fr;
}

interface RegistrationFormPayload {
  batchId: string;
  playerName: string;
  teamName: string;
  email: string;
  phone: string;
  teamSize: TeamSize;
  locale?: string;
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
  consentAcknowledgedAt: Timestamp;
  claimedAt?: Timestamp;
  claimedBy?: string;
}

function unitPriceCents(batchId: string): number {
  return eventBatch(batchId)?.unitPriceCents ?? 0;
}

function db() {
  return getFirestore();
}

const MAX_NAME_LEN = 60;
const MAX_EMAIL_LEN = 254; // RFC 5321
const MAX_PHONE_LEN = 20;

export function validatePayload(body: unknown): RegistrationFormPayload {
  if (!body || typeof body !== "object") {
    throw new Error("Missing request body");
  }
  const b = body as Record<string, unknown>;

  const honeypot = typeof b.nicknameAlt === "string" ? b.nicknameAlt.trim() : "";
  if (honeypot.length > 0) {
    throw new Error("invalid request");
  }

  const batchId = typeof b.batchId === "string" ? b.batchId.trim() : "";
  if (!batchId) throw new Error("batchId is required");
  if (!eventBatch(batchId)) {
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
  let out = "";
  for (let i = 0; i < 6; i += 1) {
    out += CODE_ALPHABET[randomInt(0, CODE_ALPHABET.length)];
  }
  return out;
}

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

    // Tracks the reserved doc so the catch can clean it up if Stripe
    // fails, otherwise a `paid:false` orphan would consume a code.
    let reservedDocRef: FirebaseFirestore.DocumentReference | null = null;
    try {
      const origin = originFor(req);
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
      reservedDocRef = docRef;

      const stripe = new Stripe(STRIPE_SECRET_KEY.value());
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
                unit_amount: unitPriceCents(payload.batchId),
                product_data: {
                  name: eventBatch(payload.batchId)!.productName,
                  description: eventBatch(payload.batchId)!.productDescription(payload.teamName, payload.teamSize),
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
      // Delete the reserved doc so a Stripe failure doesn't leave an
      // orphan `paid:false` registration consuming a code. Safe: it has
      // no `stripeSessionId` yet, so it can't be a paid registration.
      if (reservedDocRef) {
        await reservedDocRef.delete().catch((delErr) => {
          logger.error("createPendingRegistration: orphan cleanup failed", delErr);
        });
      }
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
    memory: GOOGLEAPIS_MEMORY,
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

    // Refund branch, fires when an inscription is refunded from the
    // Stripe Dashboard (or via API). FULL refunds mark the row as
    // refunded in the Google Sheet so the wristband desk on D-Day
    // skips the code. Partial refunds are ignored (e.g. refunding one
    // player from a team of 4 to drop down to 3, the inscription is
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
        // persisted, one extra Stripe API call to map PI → session →
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
        | { kind: "notFound" }
        | { kind: "alreadyRefunded" }
        | { kind: "flipped" }
        | { kind: "amountMismatch"; expected: number; got: number }
        | { kind: "currencyMismatch"; got: string }
      >(async (tx) => {
        const snap = await tx.get(refundDocRef);
        if (!snap.exists) return { kind: "notFound" };
        const data = snap.data() as RegistrationDoc;
        if (data.refunded === true) return { kind: "alreadyRefunded" };
        // Defense-in-depth, mirroring the completed-session path: only flip
        // when the charge currency + amount match what this registration was
        // billed (teamSize × unit price). A forged refund event with a
        // mismatched amount/currency can't strip a code.
        if (charge.currency !== CURRENCY) {
          return { kind: "currencyMismatch", got: charge.currency };
        }
        const expected = data.teamSize * unitPriceCents(data.batchId);
        if (charge.amount !== expected) {
          return { kind: "amountMismatch", expected, got: charge.amount };
        }
        tx.update(refundDocRef, {
          paid: false,
          refunded: true,
          refundedAt: FieldValue.serverTimestamp(),
        });
        return { kind: "flipped" };
      });

      if (refundResult.kind === "currencyMismatch") {
        logger.warn(
          `charge.refunded ${charge.id}: currency=${refundResult.got}, expected ${CURRENCY}; refusing flip`
        );
        res.status(200).json({ received: true, ignored: "currency-mismatch" });
        return;
      }
      if (refundResult.kind === "amountMismatch") {
        logger.warn(
          `charge.refunded ${charge.id}: amount=${refundResult.got}, expected ${refundResult.expected}; refusing flip`
        );
        res.status(200).json({ received: true, ignored: "amount-mismatch" });
        return;
      }
      if (refundResult.kind === "notFound") {
        logger.error(
          `charge.refunded ${charge.id}: registration ${registrationId} doesn't exist`
        );
        res.status(200).json({ received: true, error: "registration-not-found" });
        return;
      }
      if (refundResult.kind === "alreadyRefunded") {
        logger.info(
          `charge.refunded re-delivery for ${registrationId}: already refunded, noop`
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

    const result = await db().runTransaction<
      | { kind: "notFound" }
      | { kind: "amountMismatch"; expected: number; got: number | null }
      | { kind: "alreadyPaid"; snapshot: RegistrationDoc }
      | { kind: "flipped"; snapshot: RegistrationDoc }
    >(async (tx) => {
      const snap = await tx.get(docRef);
      if (!snap.exists) return { kind: "notFound" };
      const data = snap.data() as RegistrationDoc;
      const expected = data.teamSize * unitPriceCents(data.batchId);
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
      logger.error(`Webhook for non-existent registration ${registrationId}: was the doc deleted?`);
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
      logger.info(`Webhook re-delivery for ${registrationId}: already paid, skipping side effects`);
      res.status(200).json({ received: true, idempotent: true });
      return;
    }

    // Side effects run AFTER the transaction. If either fails we log
    // but still return 200, the registration is marked paid (source
    // of truth) and the failure is recoverable manually. Returning a
    // 5xx here would make Stripe retry the webhook, which would hit
    // the idempotency guard above and skip these calls entirely.
    try {
      await sendRegistrationConfirmationEmail(snapshot, RESEND_API_KEY.value());
    } catch (err) {
      logger.error(`Resend email failed for ${registrationId}`, err);
      await recordFailedSideEffect(registrationId, snapshot, "email", err, RESEND_API_KEY.value());
    }
    try {
      await appendRegistrationRow(snapshot, GOOGLE_SHEET_ID.value());
    } catch (err) {
      logger.error(`Google Sheet append failed for ${registrationId}`, err);
      await recordFailedSideEffect(registrationId, snapshot, "sheet", err, RESEND_API_KEY.value());
    }

    logger.info(`Registration ${registrationId} marked paid (Stripe session ${session.id})`);
    res.status(200).json({ received: true });
  }
);

/**
 * Records a paid-registration side-effect failure durably and pings ops.
 * The marker doc at `/failedSideEffects/{registrationId}` is the safety net
 * (queryable, survives even if the alert below also fails), and the Sheet
 * append is idempotent so a manual re-run from it is safe. The alert is
 * best-effort: it may itself fail when Resend is the outage, which is exactly
 * why the marker doc is written first.
 */
async function recordFailedSideEffect(
  registrationId: string,
  snapshot: { email: string },
  effect: "email" | "sheet",
  err: unknown,
  resendApiKey: string
): Promise<void> {
  await getFirestore()
    .collection("failedSideEffects")
    .doc(registrationId)
    .set(
      {
        registrationId,
        email: snapshot.email,
        [`${effect}Error`]: String(err),
        [`${effect}FailedAt`]: FieldValue.serverTimestamp(),
        resolved: false,
      },
      { merge: true }
    )
    .catch((e) =>
      logger.error(`failedSideEffects marker write failed for ${registrationId}`, e)
    );

  try {
    const response = await fetchWithRetry("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: "PouleParty <noreply@pouleparty.be>",
        to: "julien@rahier.dev",
        subject: `[PouleParty] paid-registration ${effect} side-effect failed (${registrationId})`,
        text:
          `The "${effect}" side effect failed for PAID registration ${registrationId} ` +
          `(${snapshot.email || "no email on doc"}).\n\nError: ${String(err)}\n\n` +
          `The registration is marked paid (source of truth). A marker was written to ` +
          `/failedSideEffects/${registrationId}. The Google Sheet append is idempotent, so ` +
          `re-running the side effect from the marker is safe.`,
      }),
    });
    if (!response.ok) {
      logger.error(`ops alert returned ${response.status} for ${registrationId}`);
    }
  } catch (e) {
    logger.error(`ops alert send failed for ${registrationId}`, e);
  }
}

// Per-UID rate limit. Threat model: brute-forcing a 6-char alphanum code
// (32^6 ~ 1B combinations, ~50 valid codes per batch). The legitimate JoinFlow
// makes 1 call per submit, so 10 attempts inside a 10 min sliding window covers
// typo retries while keeping a 60 min lockout in reserve. Anonymous Auth UIDs
// are device-bound, so this is effectively per-device. Doc lives in
// `/validationRateLimits/{uid}`, admin-SDK-only by firestore.rules.
const VALIDATION_RATE_LIMIT_MAX = 10;
const VALIDATION_RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
const VALIDATION_RATE_LIMIT_LOCK_MS = 60 * 60 * 1000;

interface ValidationRateLimit {
  attempts: number;
  firstAttemptAt: Timestamp;
  lockedUntil: Timestamp | null;
}

function validationRateLimitRef(uid: string) {
  return db().collection("validationRateLimits").doc(uid);
}

// Single-transaction bump-and-check. Throws `resource-exhausted` BEFORE the
// lookup runs so brute-force attempts can't reach the Firestore query. Both
// successes and failures count against the budget.
async function bumpValidationRateLimit(uid: string): Promise<void> {
  await db().runTransaction(async (tx) => {
    const ref = validationRateLimitRef(uid);
    const snap = await tx.get(ref);
    const now = Timestamp.now();
    let rl: ValidationRateLimit = (snap.data() as ValidationRateLimit) ?? {
      attempts: 0,
      firstAttemptAt: now,
      lockedUntil: null,
    };

    if (rl.lockedUntil && rl.lockedUntil.toMillis() <= now.toMillis()) {
      rl = { attempts: 0, firstAttemptAt: now, lockedUntil: null };
    }
    if (rl.lockedUntil) {
      throw new HttpsError("resource-exhausted", "Too many validation attempts", {
        lockedUntil: rl.lockedUntil.toMillis(),
      });
    }

    if (
      now.toMillis() - rl.firstAttemptAt.toMillis() >
      VALIDATION_RATE_LIMIT_WINDOW_MS
    ) {
      rl = { attempts: 0, firstAttemptAt: now, lockedUntil: null };
    }

    const attempts = rl.attempts + 1;
    const reachedLock = attempts >= VALIDATION_RATE_LIMIT_MAX;
    const lockedUntil = reachedLock
      ? Timestamp.fromMillis(now.toMillis() + VALIDATION_RATE_LIMIT_LOCK_MS)
      : null;
    tx.set(ref, {
      attempts,
      firstAttemptAt: rl.firstAttemptAt,
      lockedUntil,
      expiresAt: Timestamp.fromMillis(now.toMillis() + 24 * 60 * 60 * 1000),
    });

    if (reachedLock) {
      throw new HttpsError("resource-exhausted", "Too many validation attempts", {
        lockedUntil: lockedUntil!.toMillis(),
      });
    }
  });
}

interface ValidateRegistrationCodeInput {
  batchId?: string;
  code?: string;
}

type ValidateRegistrationCodeResult =
  | { status: "valid" }
  | { status: "invalid" }
  | { status: "alreadyUsed" };

function normalizeBatchId(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeJoinCode(value: unknown): string {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}

/**
 * `validateRegistrationCode(batchId, code) -> { status }`.
 *   - `invalid`    , no paid eventRegistration matches the (batchId, code) pair
 *   - `alreadyUsed`, the code was already claimed by a different device
 *   - `valid`      , match found and now claimed by this caller (idempotent if
 *                     this caller already owns the claim)
 * The lookup + claim run in one transaction so two simultaneous submits can't
 * both win the same code.
 */
export const validateRegistrationCode = onCall<
  ValidateRegistrationCodeInput,
  Promise<ValidateRegistrationCodeResult>
>(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const batchId = normalizeBatchId(request.data?.batchId);
  const code = normalizeJoinCode(request.data?.code);
  if (!batchId || !code) return { status: "invalid" };

  // Throws `resource-exhausted` if this UID is over budget, before any lookup.
  await bumpValidationRateLimit(uid);

  return await db().runTransaction(async (tx) => {
    const query = db()
      .collection(COLLECTION)
      .where("batchId", "==", batchId)
      .where("code", "==", code)
      .where("paid", "==", true)
      .limit(1);
    const snap = await tx.get(query);
    const doc = snap.docs[0];
    if (!doc) return { status: "invalid" } as const;

    const data = doc.data() as RegistrationDoc;
    if (data.claimedBy && data.claimedBy !== uid) {
      return { status: "alreadyUsed" } as const;
    }
    if (data.claimedBy !== uid) {
      tx.update(doc.ref, {
        claimedBy: uid,
        claimedAt: Timestamp.now(),
      });
    }
    return { status: "valid" } as const;
  });
});

/**
 * Replays the confirmation email and the sheet row of paid registrations
 * whose side effects failed after the webhook. Both are safe to rerun: the
 * sheet append skips existing rows and the marker is resolved per effect.
 */
export async function replayFailedSideEffects(resendApiKey: string, sheetId: string): Promise<number> {
  const markers = await db()
    .collection("failedSideEffects")
    .where("resolved", "==", false)
    .limit(20)
    .get();
  let resolvedCount = 0;
  for (const marker of markers.docs) {
    const data = marker.data();
    const registrationId = marker.id;
    const regSnap = await db().collection(COLLECTION).doc(registrationId).get();
    const reg = regSnap.data() as RegistrationDoc | undefined;
    if (!reg || reg.paid !== true) {
      await marker.ref.update({ resolved: true, resolvedReason: "notPaid", resolvedAt: FieldValue.serverTimestamp() });
      resolvedCount++;
      continue;
    }
    const updates: Record<string, unknown> = {};
    let emailDone = data.emailFailedAt === undefined || data.emailResolvedAt !== undefined;
    let sheetDone = data.sheetFailedAt === undefined || data.sheetResolvedAt !== undefined;
    if (!emailDone) {
      try {
        await sendRegistrationConfirmationEmail(reg, resendApiKey);
        updates.emailResolvedAt = FieldValue.serverTimestamp();
        emailDone = true;
      } catch (err) {
        logger.warn("[replay] email still failing", { registrationId, error: String(err) });
      }
    }
    if (!sheetDone) {
      try {
        await appendRegistrationRow(reg, sheetId);
        updates.sheetResolvedAt = FieldValue.serverTimestamp();
        sheetDone = true;
      } catch (err) {
        logger.warn("[replay] sheet still failing", { registrationId, error: String(err) });
      }
    }
    if (emailDone && sheetDone) {
      updates.resolved = true;
      updates.resolvedAt = FieldValue.serverTimestamp();
      resolvedCount++;
    }
    if (Object.keys(updates).length > 0) await marker.ref.update(updates);
  }
  return resolvedCount;
}

export const REGISTRATION_SECRETS = { RESEND_API_KEY, GOOGLE_SHEET_ID };
