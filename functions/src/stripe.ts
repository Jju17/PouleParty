import { onCall, HttpsError, onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { getFirestore, FieldValue, GeoPoint, Timestamp } from "firebase-admin/firestore";
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
    /** Required for `stayInTheZone`, null for `followTheChicken` (the final
     *  zone is dynamically the chicken's live position in that mode). */
    finalCenter: { latitude: number; longitude: number } | null;
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
// Exported so unit tests can pin the contract: Stripe amount ≡
// `pricePerPlayer × maxPlayers` for Forfait, zero for Caution (hunter-only
// for now). A bug here silently double-charges or under-charges creators.
export function computeCreatorAmountCents(pricing: PendingGamePayload["pricing"], maxPlayers: number): number {
  if (pricing.model === "flat") {
    return pricing.pricePerPlayer * maxPlayers;
  }
  // Caution creator fee: deferred per product decision (hunters-only for now).
  return 0;
}

/**
 * Reject anything that isn't a finite number in [min, max]. `Number.isFinite`
 * rejects NaN and Infinity; range check then ensures the value round-trips
 * through the downstream Firestore + mobile decoders without surprise.
 */
function assertFiniteNumberInRange(
  value: unknown,
  field: string,
  min: number,
  max: number,
): asserts value is number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < min || value > max) {
    throw new HttpsError("invalid-argument", `${field} must be a finite number in [${min}, ${max}]`);
  }
}

function assertLatLng(
  value: unknown,
  field: string,
): asserts value is { latitude: number; longitude: number } {
  if (!value || typeof value !== "object") {
    throw new HttpsError("invalid-argument", `${field} must be an object`);
  }
  const v = value as { latitude?: unknown; longitude?: unknown };
  assertFiniteNumberInRange(v.latitude, `${field}.latitude`, -90, 90);
  assertFiniteNumberInRange(v.longitude, `${field}.longitude`, -180, 180);
}

// Bounds enforced at the API boundary. Any client sending past these is
// either a misconfigured build or a malicious caller, so we reject up front.
const MAX_NAME_LENGTH = 80;
const MAX_ENABLED_POWER_UP_TYPES = 32;
const VALID_GAME_MODES = new Set(["followTheChicken", "stayInTheZone"]);
// Scheduled tasks fired in the past get rejected by Cloud Tasks; anything
// more than 5 min in the past is guaranteed to be a stale/replay request.
// Allow a small negative skew so clock drift between the phone and the server
// doesn't reject legit 'start now' games.
const START_MIN_PAST_MS = 5 * 60 * 1000;
// Upper bound of a game scheduled into the future; prevents trivial abuse of
// the Cloud Tasks queue with impossible-to-play dates.
const START_MAX_FUTURE_MS = 365 * 24 * 60 * 60 * 1000;

export function sanitiseGamePayload(
  raw: unknown,
  now: () => number = () => Date.now(),
): PendingGamePayload {
  if (!raw || typeof raw !== "object") {
    throw new HttpsError("invalid-argument", "gameConfig missing");
  }
  const g = raw as Partial<PendingGamePayload>;
  if (typeof g.name !== "string") throw new HttpsError("invalid-argument", "name must be a string");
  const trimmedName = g.name.trim();
  if (trimmedName.length === 0 || trimmedName.length > MAX_NAME_LENGTH) {
    throw new HttpsError("invalid-argument", `name must be 1..${MAX_NAME_LENGTH} chars`);
  }
  g.name = trimmedName;
  if (typeof g.maxPlayers !== "number" || !Number.isInteger(g.maxPlayers) || g.maxPlayers < 1 || g.maxPlayers > 100) {
    throw new HttpsError("invalid-argument", "maxPlayers out of range");
  }
  if (typeof g.gameMode !== "string" || !VALID_GAME_MODES.has(g.gameMode)) {
    throw new HttpsError("invalid-argument", "gameMode invalid");
  }
  if (typeof g.chickenCanSeeHunters !== "boolean") {
    throw new HttpsError("invalid-argument", "chickenCanSeeHunters must be a boolean");
  }
  if (typeof g.foundCode !== "string" || !/^\d{4}$/.test(g.foundCode)) {
    throw new HttpsError("invalid-argument", "foundCode must be 4 digits");
  }
  if (!g.pricing || !["free", "flat", "deposit"].includes(g.pricing.model)) {
    throw new HttpsError("invalid-argument", "pricing.model invalid");
  }
  if (g.pricing.model === "free") {
    throw new HttpsError("invalid-argument", "free games should not go through Stripe");
  }
  if (typeof g.pricing.pricePerPlayer !== "number" || !Number.isFinite(g.pricing.pricePerPlayer) || g.pricing.pricePerPlayer < 0) {
    throw new HttpsError("invalid-argument", "pricePerPlayer invalid");
  }
  if (typeof g.pricing.deposit !== "number" || !Number.isFinite(g.pricing.deposit) || g.pricing.deposit < 0) {
    throw new HttpsError("invalid-argument", "deposit invalid");
  }
  if (typeof g.pricing.commission !== "number" || !Number.isFinite(g.pricing.commission) || g.pricing.commission < 0 || g.pricing.commission > 100) {
    throw new HttpsError("invalid-argument", "commission invalid");
  }
  // Zone structure — tight validation here is what prevents the `HashMap`
  // decode crashes on mobile clients. We refuse to persist anything that
  // wouldn't round-trip cleanly through `new GeoPoint(lat, lng)`.
  if (!g.zone || typeof g.zone !== "object") {
    throw new HttpsError("invalid-argument", "zone missing");
  }
  const zone = g.zone as Partial<PendingGamePayload["zone"]>;
  assertLatLng(zone.center, "zone.center");
  // finalCenter is optional: required for `stayInTheZone`, absent for
  // `followTheChicken`. Normalise `undefined → null` so downstream code never
  // has to distinguish the two.
  if (zone.finalCenter !== undefined && zone.finalCenter !== null) {
    assertLatLng(zone.finalCenter, "zone.finalCenter");
  } else {
    (zone as { finalCenter: null }).finalCenter = null;
  }
  // Radius upper bound matches the Firestore rule (`zone.radius <= 50000`).
  assertFiniteNumberInRange(zone.radius, "zone.radius", 0.001, 50_000);
  assertFiniteNumberInRange(zone.shrinkIntervalMinutes, "zone.shrinkIntervalMinutes", 1, 1440);
  assertFiniteNumberInRange(zone.shrinkMetersPerUpdate, "zone.shrinkMetersPerUpdate", 0, 10_000);
  assertFiniteNumberInRange(zone.driftSeed, "zone.driftSeed", Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER);
  // Timing structure — mirror the Firestore rule (`timing.start < timing.end`,
  // `headStartMinutes >= 0`) and reject scheduleTime in the past so Cloud Tasks
  // doesn't auto-fire everything immediately.
  if (!g.timing || typeof g.timing !== "object") {
    throw new HttpsError("invalid-argument", "timing missing");
  }
  const timing = g.timing as Partial<PendingGamePayload["timing"]>;
  assertFiniteNumberInRange(timing.startMillis, "timing.startMillis", 0, Number.MAX_SAFE_INTEGER);
  assertFiniteNumberInRange(timing.endMillis, "timing.endMillis", 0, Number.MAX_SAFE_INTEGER);
  if (typeof timing.startMillis === "number" && typeof timing.endMillis === "number" && timing.startMillis >= timing.endMillis) {
    throw new HttpsError("invalid-argument", "timing.startMillis must be before timing.endMillis");
  }
  const currentMs = now();
  if (typeof timing.startMillis === "number") {
    if (timing.startMillis < currentMs - START_MIN_PAST_MS) {
      throw new HttpsError("invalid-argument", "timing.startMillis is in the past");
    }
    if (timing.startMillis > currentMs + START_MAX_FUTURE_MS) {
      throw new HttpsError("invalid-argument", "timing.startMillis is too far in the future");
    }
  }
  assertFiniteNumberInRange(timing.headStartMinutes, "timing.headStartMinutes", 0, 240);

  // Registration / powerUps shape — both land in the Firestore doc verbatim,
  // so a malformed client payload would break the mobile decoders.
  if (!g.registration || typeof g.registration !== "object") {
    throw new HttpsError("invalid-argument", "registration missing");
  }
  const reg = g.registration as Partial<PendingGamePayload["registration"]>;
  if (typeof reg.required !== "boolean") {
    throw new HttpsError("invalid-argument", "registration.required must be a boolean");
  }
  // closesMinutesBefore is optional (null/undefined = no deadline); validate only
  // when present.
  if (reg.closesMinutesBefore !== null && reg.closesMinutesBefore !== undefined) {
    assertFiniteNumberInRange(reg.closesMinutesBefore, "registration.closesMinutesBefore", 0, 10_080);
  }

  if (!g.powerUps || typeof g.powerUps !== "object") {
    throw new HttpsError("invalid-argument", "powerUps missing");
  }
  const pu = g.powerUps as Partial<PendingGamePayload["powerUps"]>;
  if (typeof pu.enabled !== "boolean") {
    throw new HttpsError("invalid-argument", "powerUps.enabled must be a boolean");
  }
  if (!Array.isArray(pu.enabledTypes) || pu.enabledTypes.length > MAX_ENABLED_POWER_UP_TYPES) {
    throw new HttpsError("invalid-argument", "powerUps.enabledTypes invalid");
  }
  for (const t of pu.enabledTypes) {
    if (typeof t !== "string" || t.length === 0 || t.length > 32) {
      throw new HttpsError("invalid-argument", "powerUps.enabledTypes contains invalid entry");
    }
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

/**
 * Turn a sanitised client payload into the exact shape Firestore must hold so
 * iOS (`FirebaseFirestore.GeoPoint` / `Timestamp`) and Android
 * (`com.google.firebase.firestore.GeoPoint` / `Timestamp`) decode it without
 * a single custom converter. Exported so the unit tests in
 * `test/stripe-zone.test.ts` can assert the exact output shape.
 */
export function materialiseGameDoc(
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
    // zone.center and zone.finalCenter MUST be Firestore GeoPoint instances,
    // not plain maps. A previous version spread `g.zone` directly, which stored
    // `{latitude, longitude}` as a HashMap in Firestore. Android's Kotlin
    // decoder then crashed with `Failed to convert value of type java.util.HashMap
    // to GeoPoint (found in field 'zone.center')` the first time a client
    // streamed a Forfait-created game. Keep these constructors in place.
    zone: {
      center: new GeoPoint(g.zone.center.latitude, g.zone.center.longitude),
      finalCenter: g.zone.finalCenter
        ? new GeoPoint(g.zone.finalCenter.latitude, g.zone.finalCenter.longitude)
        : null,
      radius: g.zone.radius,
      shrinkIntervalMinutes: g.zone.shrinkIntervalMinutes,
      shrinkMetersPerUpdate: g.zone.shrinkMetersPerUpdate,
      driftSeed: g.zone.driftSeed,
    },
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

    // Enforce the registration deadline server-side. The Firestore rule
    // checks `closesMinutesBefore` at client-side registration creation
    // time, but this callable is the admin-SDK path that bypasses rules —
    // without a re-check here, a hunter who opens the payment sheet just
    // after the deadline can still be charged and join a game that was
    // supposed to be closed.
    const closesMinutesBefore = game.registration?.closesMinutesBefore as number | undefined;
    if (closesMinutesBefore !== undefined && closesMinutesBefore !== null) {
      const start = game.timing?.start;
      const startMs = start && typeof (start as { toMillis?: () => number }).toMillis === "function"
        ? (start as { toMillis: () => number }).toMillis()
        : null;
      if (startMs !== null) {
        const deadlineMs = startMs - closesMinutesBefore * 60_000;
        if (Date.now() > deadlineMs) {
          throw new HttpsError(
            "failed-precondition",
            "Registration deadline has passed for this game",
          );
        }
      }
    }

    // Prevent duplicate registration / duplicate payment.
    // Note: this `.get()` is not atomic with the PaymentIntent creation
    // below, but Stripe's `idempotencyKey: pi_hunter_${gameId}_${uid}`
    // is the actual safety net — two concurrent calls return the SAME
    // PaymentIntent, so no double charge. This check exists purely to
    // surface a clearer error to a hunter who has already completed
    // their registration.
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

    // Dedup, Stripe may deliver an event more than once, and two deliveries
    // can hit this handler concurrently. The previous get-then-set pattern
    // could admit both, leading to double side-effects (e.g. two hunter
    // registrations from a single PaymentIntent). Claim the event atomically
    // in a transaction so only one handler invocation ever proceeds.
    const eventRef = db().collection("paymentEvents").doc(event.id);
    const claim = await claimWebhookEvent(db(), eventRef, event.type);

    if (claim.status === "completed") {
      res.status(200).send("already processed");
      return;
    }
    if (claim.status === "in_flight") {
      // 409 → Stripe will retry with backoff, letting the other invocation
      // finish first and mark the event `completedAt`.
      res.status(409).send("in-flight, retry later");
      return;
    }

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
      await eventRef.set({ completedAt: FieldValue.serverTimestamp() }, { merge: true });
      res.status(200).send("ok");
    } catch (err) {
      console.error("[stripeWebhook] handler failed:", err);
      // Remove dedup marker so Stripe's retry can re-process.
      await eventRef.delete().catch(() => {});
      res.status(500).send("handler error");
    }
  },
);

export type WebhookClaimStatus = "claimed" | "completed" | "in_flight";

// Exposed + pure (no global state) so the unit tests can drive the
// transaction callback with a mocked snapshot + tx, verifying every branch
// without booting firebase-admin.
export async function claimWebhookEvent(
  dbInstance: Pick<FirebaseFirestore.Firestore, "runTransaction">,
  eventRef: FirebaseFirestore.DocumentReference,
  eventType: string,
  now: () => number = () => Date.now(),
  // 30 min covers an OOM / cold deploy / retry loop without letting a truly
  // stuck claim block replays forever. The earlier 5 min window let a
  // redeploy arriving 3 min after first dispatch double-process the event.
  staleAfterMs = 30 * 60 * 1000,
): Promise<{ status: WebhookClaimStatus }> {
  return dbInstance.runTransaction(async (tx) => {
    const snap = await tx.get(eventRef);
    if (snap.exists) {
      const data = snap.data() as { completedAt?: FirebaseFirestore.Timestamp; receivedAt?: FirebaseFirestore.Timestamp } | undefined;
      if (data?.completedAt) {
        return { status: "completed" as const };
      }
      // In-flight: another invocation is still processing OR crashed. If the
      // claim is older than `staleAfterMs`, treat it as stale and re-claim;
      // otherwise ask Stripe to retry later (via 409) so we don't race.
      const receivedMs = data?.receivedAt?.toMillis() ?? 0;
      if (now() - receivedMs < staleAfterMs) {
        return { status: "in_flight" as const };
      }
    }
    tx.set(eventRef, {
      type: eventType,
      receivedAt: FieldValue.serverTimestamp(),
    });
    return { status: "claimed" as const };
  });
}

// Exported so unit tests can drive the handler with a mocked Firestore
// — otherwise the handler's branching is only covered by manual staging
// runs and a quiet webhook dedup test. Left as a separate export rather
// than folding into `handlePaymentIntentSucceeded` so the `stripeWebhook`
// onRequest handler can keep using the no-arg form.
export async function handlePaymentIntentSucceededWithDb(
  dbInstance: FirebaseFirestore.Firestore,
  pi: Stripe.PaymentIntent,
): Promise<void> {
  const kind = pi.metadata.kind;
  const gameId = pi.metadata.gameId;
  const uid = pi.metadata.firebaseUid;

  if (!kind || !gameId || !uid) {
    console.warn("[stripeWebhook] payment_intent.succeeded missing metadata", pi.id);
    return;
  }

  if (kind === "creator_flat") {
    const gameRef = dbInstance.collection("games").doc(gameId);
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
    const regRef = dbInstance.collection("games").doc(gameId).collection("registrations").doc(uid);
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

async function handlePaymentIntentSucceeded(pi: Stripe.PaymentIntent): Promise<void> {
  return handlePaymentIntentSucceededWithDb(db(), pi);
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
