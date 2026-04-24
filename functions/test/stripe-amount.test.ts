import { describe, expect, test } from "vitest";
import { clampToStripeMinimumCentsEur, computeCreatorAmountCents } from "../src/stripe";

// The audit surfaced that nothing pins `payment.amountCents` against
// `pricing.pricePerPlayer × maxPlayers`. A bug here silently double-
// charges or under-charges creators, and the Stripe webhook has no
// server-side sanity check against the game doc — whatever amount the
// PaymentIntent was created for is what gets charged. These tests lock
// the formula.

describe("computeCreatorAmountCents, Forfait (flat)", () => {
  test("pricePerPlayer × maxPlayers for a typical game", () => {
    const amount = computeCreatorAmountCents(
      { model: "flat", pricePerPlayer: 500, deposit: 0 },
      10,
    );
    expect(amount).toBe(5_000);
  });

  test("single-player edge (maxPlayers=1)", () => {
    const amount = computeCreatorAmountCents(
      { model: "flat", pricePerPlayer: 500, deposit: 0 },
      1,
    );
    expect(amount).toBe(500);
  });

  test("zero players still returns zero (no negatives / NaN)", () => {
    const amount = computeCreatorAmountCents(
      { model: "flat", pricePerPlayer: 500, deposit: 0 },
      0,
    );
    expect(amount).toBe(0);
  });

  test("high-end game (50 players × 10 €) stays finite + exact", () => {
    const amount = computeCreatorAmountCents(
      { model: "flat", pricePerPlayer: 1_000, deposit: 0 },
      50,
    );
    expect(amount).toBe(50_000);
    expect(Number.isFinite(amount)).toBe(true);
  });
});

describe("computeCreatorAmountCents, Caution (deposit)", () => {
  test("creator pays nothing (deposit model — hunters-only fee)", () => {
    const amount = computeCreatorAmountCents(
      { model: "deposit", pricePerPlayer: 0, deposit: 1_000 },
      10,
    );
    expect(amount).toBe(0);
  });

  test("deposit size does not leak into creator amount", () => {
    // Protects against a regression where the function accidentally
    // multiplies `deposit × maxPlayers` for Caution creators.
    const amount = computeCreatorAmountCents(
      { model: "deposit", pricePerPlayer: 0, deposit: 99_999 },
      10,
    );
    expect(amount).toBe(0);
  });
});

describe("computeCreatorAmountCents, Free", () => {
  test("free games return zero regardless of maxPlayers", () => {
    const amount = computeCreatorAmountCents(
      { model: "free", pricePerPlayer: 0, deposit: 0 },
      5,
    );
    expect(amount).toBe(0);
  });
});

// Apple Reviewer test promo `APPLE_REVIEW_99` (99 %-off) on a typical
// minimum Forfait (6 × 3 € = 18 €) discounts the total to 18 cents,
// which is below Stripe's EUR minimum of 50 cents. Unclamped, the
// `paymentIntents.create` call throws `StripeInvalidRequestError:
// Amount must be at least 50 cents`, which the Firebase runtime wraps
// as `HttpsError('internal', 'INTERNAL')`. The App Store reviewer saw
// a bare "INTERNAL" alert when tapping "Payer". Clamp keeps the
// PaymentSheet renderable (+ Apple Pay button visible) for any
// aggressive promo while still failing for truly invalid inputs.
describe("clampToStripeMinimumCentsEur", () => {
  test("bumps a below-minimum amount up to 50 cents", () => {
    expect(clampToStripeMinimumCentsEur(18)).toBe(50);
    expect(clampToStripeMinimumCentsEur(1)).toBe(50);
    expect(clampToStripeMinimumCentsEur(49)).toBe(50);
  });

  test("leaves amounts at or above the minimum unchanged", () => {
    expect(clampToStripeMinimumCentsEur(50)).toBe(50);
    expect(clampToStripeMinimumCentsEur(51)).toBe(51);
    expect(clampToStripeMinimumCentsEur(1_800)).toBe(1_800);
    expect(clampToStripeMinimumCentsEur(50_000)).toBe(50_000);
  });

  test("passes zero through — the 100 %-off path is handled by redeemFreeCreation", () => {
    // Clamping zero here would charge the creator 50 cents on a promo
    // that's supposed to be fully free, and would silently bypass the
    // redeemFreeCreation flow that deactivates single-use 100 %-off codes.
    expect(clampToStripeMinimumCentsEur(0)).toBe(0);
  });

  test("passes negatives through — caller must reject before this step", () => {
    // createCreatorPaymentSheet already guards with `if (baseAmount <= 0)
    // throw invalid-argument`, so a negative should never reach the clamp.
    // Pinning the pass-through behaviour documents that the clamp is not
    // doing validation, only the min-floor.
    expect(clampToStripeMinimumCentsEur(-1)).toBe(-1);
  });

  test("99 %-off on the default min Forfait (6 × 300 cents) ends up at 50 cents", () => {
    // End-to-end reproduction of the Apple Reviewer case: the scenario
    // that got us the production "INTERNAL" alert.
    const base = computeCreatorAmountCents(
      { model: "flat", pricePerPlayer: 300, deposit: 0 },
      6,
    );
    expect(base).toBe(1_800);
    const after99Percent = Math.round(base * 0.01);
    expect(after99Percent).toBe(18); // below Stripe's EUR min
    expect(clampToStripeMinimumCentsEur(after99Percent)).toBe(50);
  });
});
