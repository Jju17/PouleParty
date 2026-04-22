import { describe, expect, test } from "vitest";
import { computeCreatorAmountCents } from "../src/stripe";

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
