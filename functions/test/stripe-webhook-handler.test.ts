import { describe, expect, test, vi } from "vitest";
import { handlePaymentIntentSucceededWithDb } from "../src/stripe";
import type Stripe from "stripe";

// Focused unit tests for the `payment_intent.succeeded` handler. The dedup /
// claim logic is covered by `stripe-webhook-dedup.test.ts`; this file pins
// the actual *side effects* of a successful payment:
//   • `creator_flat` → game doc flipped from `pending_payment` to `waiting`,
//     paymentIntentId + paidAt written.
//   • `hunter_deposit` → registration doc set with `paid: true`, payment
//     intent id + timestamps, using merge so collisions don't wipe anything.
// The handler is exported as `handlePaymentIntentSucceededWithDb` so we
// can inject a mocked Firestore and assert every branch without spinning
// up the emulator.

type RegRef = { set: ReturnType<typeof vi.fn> };
type DocRef = {
  get: ReturnType<typeof vi.fn>;
  update: ReturnType<typeof vi.fn>;
  collection: (name: string) => { doc: (id: string) => RegRef };
};

function makeDb(opts: {
  gameSnap?: { exists: boolean; data?: () => unknown };
  regSnap?: { exists: boolean; data?: () => unknown };
}) {
  const gameGet = vi.fn().mockResolvedValue(opts.gameSnap ?? { exists: false });
  const gameUpdate = vi.fn().mockResolvedValue(undefined);
  const regSet = vi.fn().mockResolvedValue(undefined);

  const regDoc: RegRef = { set: regSet };
  const gameDoc: DocRef = {
    get: gameGet,
    update: gameUpdate,
    collection: () => ({ doc: () => regDoc }),
  };

  const db = {
    collection: () => ({ doc: () => gameDoc }),
  } as unknown as FirebaseFirestore.Firestore;

  return { db, gameGet, gameUpdate, regSet };
}

function makePI(opts: {
  kind?: string;
  gameId?: string;
  firebaseUid?: string;
  id?: string;
}): Stripe.PaymentIntent {
  return {
    id: opts.id ?? "pi_test",
    metadata: {
      ...(opts.kind !== undefined ? { kind: opts.kind } : {}),
      ...(opts.gameId !== undefined ? { gameId: opts.gameId } : {}),
      ...(opts.firebaseUid !== undefined ? { firebaseUid: opts.firebaseUid } : {}),
    },
  } as unknown as Stripe.PaymentIntent;
}

describe("handlePaymentIntentSucceededWithDb — creator_flat", () => {
  test("flips pending_payment → waiting with paymentIntentId + paidAt", async () => {
    const { db, gameUpdate } = makeDb({
      gameSnap: { exists: true, data: () => ({ status: "pending_payment" }) },
    });
    const pi = makePI({ kind: "creator_flat", gameId: "g1", firebaseUid: "u1", id: "pi_c1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).toHaveBeenCalledTimes(1);
    const updateArg = gameUpdate.mock.calls[0][0] as Record<string, unknown>;
    expect(updateArg.status).toBe("waiting");
    expect(updateArg["payment.paymentIntentId"]).toBe("pi_c1");
    expect(updateArg["payment.paidAt"]).toBeDefined();
  });

  test("no-op when game already in `waiting` (idempotent)", async () => {
    const { db, gameUpdate } = makeDb({
      gameSnap: { exists: true, data: () => ({ status: "waiting" }) },
    });
    const pi = makePI({ kind: "creator_flat", gameId: "g1", firebaseUid: "u1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).not.toHaveBeenCalled();
  });

  test("no-op when game doc missing", async () => {
    const { db, gameUpdate } = makeDb({ gameSnap: { exists: false } });
    const pi = makePI({ kind: "creator_flat", gameId: "ghost", firebaseUid: "u1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).not.toHaveBeenCalled();
  });
});

describe("handlePaymentIntentSucceededWithDb — hunter_deposit", () => {
  test("writes registration with paid:true + paymentIntentId + joinedAt, merging", async () => {
    const { db, regSet } = makeDb({});
    const pi = makePI({ kind: "hunter_deposit", gameId: "g1", firebaseUid: "hunter42", id: "pi_h1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(regSet).toHaveBeenCalledTimes(1);
    const [regData, opts] = regSet.mock.calls[0];
    expect(regData.userId).toBe("hunter42");
    expect(regData.paid).toBe(true);
    expect(regData.paymentIntentId).toBe("pi_h1");
    expect(regData.paidAt).toBeDefined();
    expect(regData.joinedAt).toBeDefined();
    // merge:true ensures a later client write (e.g. teamName) isn't wiped
    expect(opts).toEqual({ merge: true });
  });
});

describe("handlePaymentIntentSucceededWithDb — guards", () => {
  test("missing kind → early return, no writes", async () => {
    const { db, gameUpdate, regSet } = makeDb({});
    const pi = makePI({ gameId: "g1", firebaseUid: "u1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).not.toHaveBeenCalled();
    expect(regSet).not.toHaveBeenCalled();
  });

  test("missing gameId → early return, no writes", async () => {
    const { db, gameUpdate, regSet } = makeDb({});
    const pi = makePI({ kind: "creator_flat", firebaseUid: "u1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).not.toHaveBeenCalled();
    expect(regSet).not.toHaveBeenCalled();
  });

  test("missing firebaseUid → early return, no writes", async () => {
    const { db, gameUpdate, regSet } = makeDb({});
    const pi = makePI({ kind: "hunter_deposit", gameId: "g1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).not.toHaveBeenCalled();
    expect(regSet).not.toHaveBeenCalled();
  });

  test("unknown kind → early return, no writes", async () => {
    const { db, gameUpdate, regSet } = makeDb({});
    const pi = makePI({ kind: "bogus_kind", gameId: "g1", firebaseUid: "u1" });

    await handlePaymentIntentSucceededWithDb(db, pi);

    expect(gameUpdate).not.toHaveBeenCalled();
    expect(regSet).not.toHaveBeenCalled();
  });
});
