import { onCall, HttpsError, onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import Stripe from "stripe";

const REGION = "europe-west1";

// Stripe API version pinned: must match the iOS/Android SDK versions shipped.
// Update deliberately and in lock-step with the mobile SDKs.
const STRIPE_API_VERSION: Stripe.LatestApiVersion = "2025-02-24.acacia";

const STRIPE_SECRET_KEY = defineSecret("STRIPE_SECRET_KEY");
const STRIPE_WEBHOOK_SECRET = defineSecret("STRIPE_WEBHOOK_SECRET");

function stripe(): Stripe {
  return new Stripe(STRIPE_SECRET_KEY.value(), { apiVersion: STRIPE_API_VERSION });
}

const db = () => getFirestore();

// ---------------------------------------------------------------------------
// Types — mirror iOS/Android Game model subset needed for server-side creation
// ---------------------------------------------------------------------------

type PricingModel = "free" | "flat" | "deposit";

interface PendingGamePayload {
  name: string;
  maxPlayers: number;
  gameMode: string;
  chickenCanSeeHunters: boolean;
  foundCode: string;
  timing: {
    startMillis: number;
    endMillis: number;
    headStartMinutes: number;
  };
  zone: {
    center: { latitude: number; longitude: number };
    finalCenter: { latitude: number; longitude: number };
    radius: number;
    shrinkIntervalMinutes: number;
    shrinkMetersPerUpdate: number;
    driftSeed: number;
  };
  pricing: {
    model: PricingModel;
    pricePerPlayer: number; // cents
    deposit: number; // cents
    commission: number;
  };
  registration: {
    required: boolean;
    closesMinutesBefore: number;
  };
  powerUps: {
    enabled: boolean;
    enabledTypes: string[];
  };
}

interface CreatorPaymentRequest {
  gameConfig: PendingGamePayload;
  promoCodeId?: string; // previously validated via validatePromoCode
}

interface HunterPaymentRequest {
  gameId: string;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Server-side amount computation for Forfait mode.
 * NEVER trust a client-sent amount — always recompute from pricing fields.
 */
function computeCreatorAmountCents(pricing: PendingGamePayload["pricing"], maxPlayers: number): number {
  if (pricing.model === "flat") {
    return pricing.pricePerPlayer * maxPlayers;
  }
  // Caution creator fee: deferred per product decision (hunters-only for now).
  return 0;
}

function sanitiseGamePayload(raw: unknown): PendingGamePayload {
  if (!raw || typeof raw !== "object") {
    throw new HttpsError("invalid-argument", "gameConfig missing");
  }
  const g = raw as Partial<PendingGamePayload>;
  if (typeof g.name !== "string") throw new HttpsError("invalid-argument", "name must be a string");
  if (typeof g.maxPlayers !== "number" || g.maxPlayers < 1 || g.maxPlayers > 100) {
    throw new HttpsError("invalid-argument", "maxPlayers out of range");
  }
  if (typeof g.gameMode !== "string") throw new HttpsError("invalid-argument", "gameMode required");
  if (typeof g.foundCode !== "string" || !/^\d{4}$/.test(g.foundCode)) {
    throw new HttpsError("invalid-argument", "foundCode must be 4 digits");
  }
  if (!g.pricing || !["free", "flat", "deposit"].includes(g.pricing.model)) {
    throw new HttpsError("invalid-argument", "pricing.model invalid");
  }
  if (g.pricing.model === "free") {
    throw new HttpsError("invalid-argument", "free games should not go through Stripe");
  }
  if (typeof g.pricing.pricePerPlayer !== "number" || g.pricing.pricePerPlayer < 0) {
    throw new HttpsError("invalid-argument", "pricePerPlayer invalid");
  }
  if (typeof g.pricing.deposit !== "number" || g.pricing.deposit < 0) {
    throw new HttpsError("invalid-argument", "deposit invalid");
  }
  return g as PendingGamePayload;
}

async function getOrCreateCustomer(stripeClient: Stripe, uid: string, email?: string): Promise<string> {
  const userRef = db().collection("users").doc(uid);
  const snap = await userRef.get();
  const existing = snap.data()?.stripeCustomerId as string | undefined;
  if (existing) return existing;

  const customer = await stripeClient.customers.create(
    {
      email,
      metadata: { firebaseUid: uid },
    },
    { idempotencyKey: `customer_${uid}` },
  );
  await userRef.set({ stripeCustomerId: customer.id }, { merge: true });
  return customer.id;
}

/**
 * Applies a previously-validated Stripe promotion code to an amount.
 * Re-validates the code server-side (never trust a cached validation from client).
 * Returns the discounted amount, or throws if the code is invalid.
 */
async function applyPromoCode(
  stripeClient: Stripe,
  amountCents: number,
  promoCodeId: string,
): Promise<{ finalAmount: number; percentOff?: number; amountOff?: number }> {
  const promo = await stripeClient.promotionCodes.retrieve(promoCodeId, { expand: ["coupon"] });
  if (!promo.active || !promo.coupon.valid) {
    throw new HttpsError("failed-precondition", "Promo code is no longer valid");
  }
  const coupon = promo.coupon;
  if (coupon.percent_off != null) {
    const discounted = Math.round(amountCents * (1 - coupon.percent_off / 100));
    return { finalAmount: Math.max(0, discounted), percentOff: coupon.percent_off };
  }
  if (coupon.amount_off != null) {
    const discounted = amountCents - coupon.amount_off;
    return { finalAmount: Math.max(0, discounted), amountOff: coupon.amount_off };
  }
  return { finalAmount: amountCents };
}

// ---------------------------------------------------------------------------
// Callable: create creator PaymentSheet (Forfait mode)
// ---------------------------------------------------------------------------

export const createCreatorPaymentSheet = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const uid = request.auth.uid;

    const { gameConfig, promoCodeId } = request.data as CreatorPaymentRequest;
    const game = sanitiseGamePayload(gameConfig);

    if (game.pricing.model !== "flat") {
      throw new HttpsError("invalid-argument", "creator payment only for flat mode");
    }

    const stripeClient = stripe();
    const baseAmount = computeCreatorAmountCents(game.pricing, game.maxPlayers);
    if (baseAmount <= 0) throw new HttpsError("invalid-argument", "amount must be positive");

    let finalAmount = baseAmount;
    let promoInfo: { percentOff?: number; amountOff?: number } | null = null;
    if (promoCodeId) {
      const applied = await applyPromoCode(stripeClient, baseAmount, promoCodeId);
      finalAmount = applied.finalAmount;
      promoInfo = { percentOff: applied.percentOff, amountOff: applied.amountOff };
      if (finalAmount === 0) {
        // 100%-off — caller should use redeemFreeCreation instead.
        throw new HttpsError(
          "failed-precondition",
          "This promo code makes the game free. Call redeemFreeCreation instead of this function.",
        );
      }
    }

    // Pre-create the game doc in `pending_payment` status. Webhook flips to `waiting`.
    const gameRef = db().collection("games").doc();
    const gameId = gameRef.id;

    await gameRef.set({
      ...materialiseGameDoc(game, uid, gameId),
      status: "pending_payment",
      payment: {
        provider: "stripe",
        amountCents: finalAmount,
        currency: "eur",
        promoCodeId: promoCodeId ?? null,
        promoDiscount: promoInfo,
        baseAmountCents: baseAmount,
      },
    });

    const customerId = await getOrCreateCustomer(stripeClient, uid, request.auth.token.email);

    const ephemeralKey = await stripeClient.ephemeralKeys.create(
      { customer: customerId },
      { apiVersion: STRIPE_API_VERSION },
    );

    const paymentIntent = await stripeClient.paymentIntents.create(
      {
        amount: finalAmount,
        currency: "eur",
        customer: customerId,
        automatic_payment_methods: { enabled: true },
        metadata: {
          kind: "creator_flat",
          gameId,
          firebaseUid: uid,
          promoCodeId: promoCodeId ?? "",
        },
      },
      { idempotencyKey: `pi_creator_${gameId}` },
    );

    return {
      gameId,
      paymentIntentClientSecret: paymentIntent.client_secret,
      ephemeralKeySecret: ephemeralKey.secret,
      customerId,
      amountCents: finalAmount,
    };
  },
);

function materialiseGameDoc(
  g: PendingGamePayload,
  creatorId: string,
  gameId: string,
): Record<string, unknown> {
  const gameCode = gameId.slice(0, 6).toUpperCase();
  return {
    id: gameId,
    gameCode,
    name: g.name,
    maxPlayers: g.maxPlayers,
    gameMode: g.gameMode,
    chickenCanSeeHunters: g.chickenCanSeeHunters,
    foundCode: g.foundCode,
    hunterIds: [],
    winners: [],
    creatorId,
    lastHeartbeat: FieldValue.serverTimestamp(),
    timing: {
      start: Timestamp.fromMillis(g.timing.startMillis),
      end: Timestamp.fromMillis(g.timing.endMillis),
      headStartMinutes: g.timing.headStartMinutes,
    },
    zone: g.zone,
    pricing: g.pricing,
    registration: g.registration,
    powerUps: {
      enabled: g.powerUps.enabled,
      enabledTypes: g.powerUps.enabledTypes,
      activeEffects: {},
    },
  };
}

// ---------------------------------------------------------------------------
// Callable: create hunter PaymentSheet (Caution mode — deposit)
// ---------------------------------------------------------------------------

export const createHunterPaymentSheet = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const uid = request.auth.uid;
    const { gameId } = request.data as HunterPaymentRequest;
    if (typeof gameId !== "string" || !gameId) {
      throw new HttpsError("invalid-argument", "gameId required");
    }

    const gameSnap = await db().collection("games").doc(gameId).get();
    if (!gameSnap.exists) throw new HttpsError("not-found", "Game not found");
    const game = gameSnap.data()!;

    if (game.status !== "waiting") {
      throw new HttpsError("failed-precondition", "Game not open for registration");
    }
    const model = game.pricing?.model as PricingModel | undefined;
    if (model !== "deposit") {
      throw new HttpsError("failed-precondition", "This game does not require a deposit");
    }
    const depositCents = game.pricing?.deposit as number | undefined;
    if (!depositCents || depositCents <= 0) {
      throw new HttpsError("failed-precondition", "Deposit amount invalid");
    }

    // Prevent duplicate registration / duplicate payment.
    const regRef = db().collection("games").doc(gameId).collection("registrations").doc(uid);
    const regSnap = await regRef.get();
    if (regSnap.exists && regSnap.data()?.paid === true) {
      throw new HttpsError("already-exists", "You are already registered and paid");
    }

    const stripeClient = stripe();
    const customerId = await getOrCreateCustomer(stripeClient, uid, request.auth.token.email);

    const ephemeralKey = await stripeClient.ephemeralKeys.create(
      { customer: customerId },
      { apiVersion: STRIPE_API_VERSION },
    );

    const paymentIntent = await stripeClient.paymentIntents.create(
      {
        amount: depositCents,
        currency: "eur",
        customer: customerId,
        automatic_payment_methods: { enabled: true },
        metadata: {
          kind: "hunter_deposit",
          gameId,
          firebaseUid: uid,
        },
      },
      { idempotencyKey: `pi_hunter_${gameId}_${uid}` },
    );

    return {
      paymentIntentClientSecret: paymentIntent.client_secret,
      ephemeralKeySecret: ephemeralKey.secret,
      customerId,
      amountCents: depositCents,
    };
  },
);

// ---------------------------------------------------------------------------
// Callable: validate a promotion code (Forfait only)
// ---------------------------------------------------------------------------

export const validatePromoCode = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const uid = request.auth.uid;
    const { code } = request.data as { code?: string };
    if (typeof code !== "string" || !code.trim()) {
      throw new HttpsError("invalid-argument", "code required");
    }
    const trimmed = code.trim();
    if (trimmed.length > 50) throw new HttpsError("invalid-argument", "code too long");

    // Rate-limit: max 10 validation attempts per user per hour (anti-brute-force).
    await enforcePromoRateLimit(uid);

    const stripeClient = stripe();
    const list = await stripeClient.promotionCodes.list({
      code: trimmed,
      active: true,
      limit: 1,
      expand: ["data.coupon"],
    });
    const promo = list.data[0];
    if (!promo || !promo.coupon.valid) {
      return { valid: false };
    }

    const coupon = promo.coupon;
    const percentOff = coupon.percent_off ?? null;
    const amountOff = coupon.amount_off ?? null;
    const freeOverride = percentOff === 100;

    return {
      valid: true,
      promoCodeId: promo.id,
      percentOff,
      amountOff,
      freeOverride,
      currency: coupon.currency ?? null,
    };
  },
);

async function enforcePromoRateLimit(uid: string): Promise<void> {
  const ref = db().collection("rateLimits").doc(`promo_${uid}`);
  const now = Date.now();
  const WINDOW_MS = 60 * 60 * 1000;
  const MAX_ATTEMPTS = 10;

  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const data = snap.data() as { count?: number; windowStart?: number } | undefined;
    if (!data || (data.windowStart ?? 0) + WINDOW_MS < now) {
      tx.set(ref, { count: 1, windowStart: now });
      return;
    }
    if ((data.count ?? 0) >= MAX_ATTEMPTS) {
      throw new HttpsError("resource-exhausted", "Too many promo code attempts. Try again later.");
    }
    tx.update(ref, { count: FieldValue.increment(1) });
  });
}

// ---------------------------------------------------------------------------
// Callable: redeem a 100% off promo code to create a free game (Forfait only)
// ---------------------------------------------------------------------------

export const redeemFreeCreation = onCall(
  { region: REGION, secrets: [STRIPE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required");
    const uid = request.auth.uid;

    const { gameConfig, promoCodeId } = request.data as CreatorPaymentRequest;
    if (!promoCodeId) throw new HttpsError("invalid-argument", "promoCodeId required");
    const game = sanitiseGamePayload(gameConfig);
    if (game.pricing.model !== "flat") {
      throw new HttpsError("invalid-argument", "free redemption only for flat mode");
    }

    const stripeClient = stripe();

    // Re-validate server-side — never trust the client's earlier validation.
    const promo = await stripeClient.promotionCodes.retrieve(promoCodeId, { expand: ["coupon"] });
    if (!promo.active || !promo.coupon.valid || promo.coupon.percent_off !== 100) {
      throw new HttpsError("failed-precondition", "Promo code does not grant 100% off");
    }

    const baseAmount = computeCreatorAmountCents(game.pricing, game.maxPlayers);

    // Create the game doc directly, skip PaymentSheet entirely.
    const gameRef = db().collection("games").doc();
    const gameId = gameRef.id;
    await gameRef.set({
      ...materialiseGameDoc(game, uid, gameId),
      status: "waiting",
      payment: {
        provider: "stripe",
        amountCents: 0,
        currency: "eur",
        promoCodeId,
        baseAmountCents: baseAmount,
        redeemedAt: FieldValue.serverTimestamp(),
      },
    });

    // Deactivate single-use 100%-off codes so they can't be replayed.
    // If the promo code was intentionally multi-use, the dashboard owner can re-enable.
    if (promo.max_redemptions && promo.times_redeemed + 1 >= promo.max_redemptions) {
      await stripeClient.promotionCodes.update(promoCodeId, { active: false });
    }

    return { gameId };
  },
);

// ---------------------------------------------------------------------------
// Webhook: Stripe events (mounted at /stripeWebhook)
// ---------------------------------------------------------------------------

export const stripeWebhook = onRequest(
  { region: REGION, secrets: [STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET] },
  async (req, res) => {
    const sig = req.header("stripe-signature");
    if (!sig) {
      res.status(400).send("missing signature");
      return;
    }
    const stripeClient = stripe();
    let event: Stripe.Event;
    try {
      event = stripeClient.webhooks.constructEvent(req.rawBody, sig, STRIPE_WEBHOOK_SECRET.value());
    } catch (err) {
      console.error("[stripeWebhook] signature verification failed:", err);
      res.status(400).send(`invalid signature: ${(err as Error).message}`);
      return;
    }

    // Dedup — Stripe may deliver an event more than once.
    const eventRef = db().collection("paymentEvents").doc(event.id);
    const eventSnap = await eventRef.get();
    if (eventSnap.exists) {
      res.status(200).send("already processed");
      return;
    }
    await eventRef.set({
      type: event.type,
      receivedAt: FieldValue.serverTimestamp(),
    });

    try {
      switch (event.type) {
        case "payment_intent.succeeded":
          await handlePaymentIntentSucceeded(event.data.object as Stripe.PaymentIntent);
          break;
        case "payment_intent.payment_failed":
          await handlePaymentIntentFailed(event.data.object as Stripe.PaymentIntent);
          break;
        default:
          // Ignore other events — return 200 so Stripe doesn't retry.
          break;
      }
      res.status(200).send("ok");
    } catch (err) {
      console.error("[stripeWebhook] handler failed:", err);
      // Remove dedup marker so Stripe's retry can re-process.
      await eventRef.delete().catch(() => {});
      res.status(500).send("handler error");
    }
  },
);

async function handlePaymentIntentSucceeded(pi: Stripe.PaymentIntent): Promise<void> {
  const kind = pi.metadata.kind;
  const gameId = pi.metadata.gameId;
  const uid = pi.metadata.firebaseUid;

  if (!kind || !gameId || !uid) {
    console.warn("[stripeWebhook] payment_intent.succeeded missing metadata", pi.id);
    return;
  }

  if (kind === "creator_flat") {
    const gameRef = db().collection("games").doc(gameId);
    const snap = await gameRef.get();
    if (!snap.exists) {
      console.warn("[stripeWebhook] game doc missing for creator_flat", gameId);
      return;
    }
    if (snap.data()?.status !== "pending_payment") {
      // Already flipped (idempotent)
      return;
    }
    await gameRef.update({
      status: "waiting",
      "payment.paymentIntentId": pi.id,
      "payment.paidAt": FieldValue.serverTimestamp(),
    });
    return;
  }

  if (kind === "hunter_deposit") {
    const regRef = db().collection("games").doc(gameId).collection("registrations").doc(uid);
    await regRef.set(
      {
        userId: uid,
        paid: true,
        paymentIntentId: pi.id,
        paidAt: FieldValue.serverTimestamp(),
        joinedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    return;
  }

  console.warn("[stripeWebhook] unknown payment kind", kind, pi.id);
}

async function handlePaymentIntentFailed(pi: Stripe.PaymentIntent): Promise<void> {
  const kind = pi.metadata.kind;
  const gameId = pi.metadata.gameId;
  if (kind === "creator_flat" && gameId) {
    // Leave the pending_payment game doc so the client can see the failure,
    // but mark it as failed. A follow-up cleanup job can purge stale pending games.
    await db().collection("games").doc(gameId).update({
      status: "payment_failed",
      "payment.failedAt": FieldValue.serverTimestamp(),
    }).catch(() => {});
  }
}
