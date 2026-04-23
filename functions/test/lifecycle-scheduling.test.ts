import { describe, expect, test } from "vitest";
import {
  selectStaleGamesForPurge,
  shouldScheduleOnCreate,
  shouldScheduleOnUpdate,
} from "../src/index";

// Pure-function tests for the three gates introduced by the pending_payment
// orphan fix + the defer-scheduling refactor:
//
//   • shouldScheduleOnCreate(status) → only "waiting" passes (Forfait games
//     start in "pending_payment" and must wait for the webhook).
//   • shouldScheduleOnUpdate(before, after) → true only on the exact
//     pending_payment / payment_failed → waiting transition.
//   • selectStaleGamesForPurge(games, statuses, cutoffMs) → filters correctly
//     by status AND heartbeat age so the scheduled cleanup never deletes an
//     active game.
//
// A regression in any of these three gates would either burn Cloud Tasks on
// cancelled games, skip scheduling on legitimate paid ones, or delete live
// games. Covered here so the damage shows up as a failing test, not a
// Firestore janitor deleting a prod game.

describe("shouldScheduleOnCreate", () => {
  test("waiting → true (free / 100%-off / legacy client-created)", () => {
    expect(shouldScheduleOnCreate("waiting")).toBe(true);
  });

  test("pending_payment → false (Forfait still awaiting Stripe webhook)", () => {
    expect(shouldScheduleOnCreate("pending_payment")).toBe(false);
  });

  test("payment_failed → false (shouldn't happen on create but defensive)", () => {
    expect(shouldScheduleOnCreate("payment_failed")).toBe(false);
  });

  test("inProgress → false (mid-game doc resurrect, shouldn't re-schedule)", () => {
    expect(shouldScheduleOnCreate("inProgress")).toBe(false);
  });

  test("done → false", () => {
    expect(shouldScheduleOnCreate("done")).toBe(false);
  });

  test("undefined → false (missing status field, reject)", () => {
    expect(shouldScheduleOnCreate(undefined)).toBe(false);
  });

  test("unknown string → false (whitelist, not blacklist)", () => {
    expect(shouldScheduleOnCreate("archived")).toBe(false);
  });
});

describe("shouldScheduleOnUpdate", () => {
  test("pending_payment → waiting: true (happy-path Stripe webhook)", () => {
    expect(shouldScheduleOnUpdate("pending_payment", "waiting")).toBe(true);
  });

  test("payment_failed → waiting: true (retry after declined payment)", () => {
    expect(shouldScheduleOnUpdate("payment_failed", "waiting")).toBe(true);
  });

  test("waiting → waiting: false (no transition, just another field update)", () => {
    expect(shouldScheduleOnUpdate("waiting", "waiting")).toBe(false);
  });

  test("waiting → inProgress: false (status transition task already scheduled at create)", () => {
    expect(shouldScheduleOnUpdate("waiting", "inProgress")).toBe(false);
  });

  test("pending_payment → payment_failed: false (stripe failure, game dead)", () => {
    expect(shouldScheduleOnUpdate("pending_payment", "payment_failed")).toBe(false);
  });

  test("inProgress → done: false (normal game end)", () => {
    expect(shouldScheduleOnUpdate("inProgress", "done")).toBe(false);
  });

  test("undefined → waiting: false (defensive, shouldn't reschedule)", () => {
    expect(shouldScheduleOnUpdate(undefined, "waiting")).toBe(false);
  });

  test("pending_payment → undefined: false", () => {
    expect(shouldScheduleOnUpdate("pending_payment", undefined)).toBe(false);
  });

  test("pending_payment → done: false (cancelled mid-flow, don't schedule anything)", () => {
    expect(shouldScheduleOnUpdate("pending_payment", "done")).toBe(false);
  });
});

describe("selectStaleGamesForPurge", () => {
  const NOW = 1_700_000_000_000;
  const ONE_HOUR_MS = 60 * 60 * 1000;
  const cutoff = NOW - 24 * ONE_HOUR_MS; // Match cleanupAbandonedPendingGames

  test("selects pending_payment older than the cutoff", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "old-pending", status: "pending_payment", lastHeartbeatMs: cutoff - 1 }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual(["old-pending"]);
  });

  test("selects payment_failed older than the cutoff (covers the extended purge)", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "old-failed", status: "payment_failed", lastHeartbeatMs: cutoff - 1 }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual(["old-failed"]);
  });

  test("skips pending_payment younger than the cutoff (still in grace period)", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "fresh", status: "pending_payment", lastHeartbeatMs: NOW - 1000 }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual([]);
  });

  test("skips waiting games regardless of age — this is the critical safety", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "very-old-waiting", status: "waiting", lastHeartbeatMs: 0 }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual([]);
  });

  test("skips inProgress games regardless of age", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "very-old-progress", status: "inProgress", lastHeartbeatMs: 0 }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual([]);
  });

  test("skips games with missing lastHeartbeat (defensive, never delete an incomplete doc)", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "no-heartbeat", status: "pending_payment" }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual([]);
  });

  test("skips games with missing status", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "no-status", lastHeartbeatMs: 0 }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual([]);
  });

  test("exact cutoff boundary: strict less-than means heartbeat == cutoff is NOT purged", () => {
    const result = selectStaleGamesForPurge(
      [{ id: "boundary", status: "pending_payment", lastHeartbeatMs: cutoff }],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result).toEqual([]);
  });

  test("mixed batch returns only the stale eligible ids", () => {
    const result = selectStaleGamesForPurge(
      [
        { id: "stale-pending", status: "pending_payment", lastHeartbeatMs: cutoff - 1 },
        { id: "fresh-pending", status: "pending_payment", lastHeartbeatMs: NOW },
        { id: "stale-failed", status: "payment_failed", lastHeartbeatMs: cutoff - 10 },
        { id: "stale-waiting", status: "waiting", lastHeartbeatMs: 0 },
        { id: "stale-done", status: "done", lastHeartbeatMs: 0 },
      ],
      ["pending_payment", "payment_failed"],
      cutoff,
    );
    expect(result.sort()).toEqual(["stale-failed", "stale-pending"]);
  });

  test("custom status list (future extension point)", () => {
    const result = selectStaleGamesForPurge(
      [
        { id: "stale-done", status: "done", lastHeartbeatMs: cutoff - 1 },
        { id: "stale-pending", status: "pending_payment", lastHeartbeatMs: cutoff - 1 },
      ],
      ["done"],
      cutoff,
    );
    expect(result).toEqual(["stale-done"]);
  });
});
