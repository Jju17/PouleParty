import { describe, expect, test, vi } from "vitest";
import { claimWebhookEvent } from "../src/stripe";

// Exercises the four transaction branches that matter for the webhook dedup
// behaviour: first-time claim, already-completed, in-flight (both fresh and
// stale), without booting firebase-admin. We drive the txCallback by
// constructing a minimal mock of `runTransaction` that invokes the callback
// with a fake `tx`.

type Snap = {
  exists: boolean;
  data?: () => { completedAt?: { toMillis(): number }; receivedAt?: { toMillis(): number } };
};

function makeDb(snap: Snap) {
  const txSet = vi.fn();
  const txGet = vi.fn().mockResolvedValue(snap);
  const run = vi.fn(async (cb: (tx: { get: typeof txGet; set: typeof txSet }) => Promise<unknown>) =>
    cb({ get: txGet, set: txSet }),
  );
  return {
    db: { runTransaction: run },
    txGet,
    txSet,
    run,
  };
}

const FAKE_REF = {} as FirebaseFirestore.DocumentReference;

describe("claimWebhookEvent", () => {
  test("first-time event claims and schedules a write", async () => {
    const { db, txSet, run } = makeDb({ exists: false });
    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded");

    expect(out.status).toBe("claimed");
    expect(run).toHaveBeenCalledTimes(1);
    expect(txSet).toHaveBeenCalledTimes(1);
    expect(txSet).toHaveBeenCalledWith(
      FAKE_REF,
      expect.objectContaining({ type: "payment_intent.succeeded" }),
    );
  });

  test("event with completedAt → already processed, no write", async () => {
    const { db, txSet } = makeDb({
      exists: true,
      data: () => ({ completedAt: { toMillis: () => 1_700_000_000_000 } }),
    });

    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded");

    expect(out.status).toBe("completed");
    expect(txSet).not.toHaveBeenCalled();
  });

  test("event with fresh receivedAt (< 30 min ago) is in-flight, no write", async () => {
    const NOW = 1_700_000_000_000;
    const { db, txSet } = makeDb({
      exists: true,
      data: () => ({ receivedAt: { toMillis: () => NOW - 60_000 } }), // 1 min ago
    });

    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded", () => NOW);

    expect(out.status).toBe("in_flight");
    expect(txSet).not.toHaveBeenCalled();
  });

  test("event with stale receivedAt (> 30 min ago) is re-claimed", async () => {
    const NOW = 1_700_000_000_000;
    const { db, txSet } = makeDb({
      exists: true,
      data: () => ({ receivedAt: { toMillis: () => NOW - 45 * 60 * 1000 } }), // 45 min ago
    });

    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded", () => NOW);

    expect(out.status).toBe("claimed");
    expect(txSet).toHaveBeenCalledTimes(1);
  });

  test("boundary: exactly 30 min ago still in-flight (strict <)", async () => {
    const NOW = 1_700_000_000_000;
    const { db, txSet } = makeDb({
      exists: true,
      data: () => ({ receivedAt: { toMillis: () => NOW - 30 * 60 * 1000 } }), // exactly 30 min ago
    });

    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded", () => NOW);

    // diff = staleAfterMs, NOT strictly less than → stale branch → re-claim.
    expect(out.status).toBe("claimed");
    expect(txSet).toHaveBeenCalledTimes(1);
  });

  test("custom staleAfterMs window is respected", async () => {
    const NOW = 1_700_000_000_000;
    const { db, txSet } = makeDb({
      exists: true,
      data: () => ({ receivedAt: { toMillis: () => NOW - 30_000 } }), // 30 s ago
    });

    // Window is 10 s, the 30 s-old claim is stale → re-claim.
    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded", () => NOW, 10_000);

    expect(out.status).toBe("claimed");
    expect(txSet).toHaveBeenCalledTimes(1);
  });

  test("doc exists but has neither completedAt nor receivedAt is treated as in-flight at epoch", async () => {
    // Defensive: legacy docs that were written with only `{type}` should
    // still be in_flight for 5 min after NOW=0, i.e. immediately stale for
    // any realistic `now` → re-claim. Confirms the `?? 0` fallback.
    const NOW = 1_700_000_000_000;
    const { db, txSet } = makeDb({
      exists: true,
      data: () => ({}),
    });

    const out = await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded", () => NOW);

    expect(out.status).toBe("claimed");
    expect(txSet).toHaveBeenCalledTimes(1);
  });

  test("transaction is invoked exactly once per call", async () => {
    const { db, run } = makeDb({ exists: false });

    await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded");
    await claimWebhookEvent(db, FAKE_REF, "payment_intent.succeeded");

    expect(run).toHaveBeenCalledTimes(2);
  });
});
