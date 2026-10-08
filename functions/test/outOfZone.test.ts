import { describe, expect, it } from "vitest";
import { decideServerPenalty } from "../src/outOfZone";
import { decideClientPenalty } from "../src/validation";

const NOW = 1_800_000_000_000;

describe("decideServerPenalty", () => {
  it("does nothing while the hunter is inside the zone", () => {
    expect(decideServerPenalty(false, NOW - 10_000, NOW)).toEqual({ points: 0, newLastPenaltyAtMs: null });
  });
  it("opens a window on the first observation outside", () => {
    expect(decideServerPenalty(true, null, NOW)).toEqual({ points: 0, newLastPenaltyAtMs: NOW });
  });
  it("charges one point per full interval since the last penalty", () => {
    expect(decideServerPenalty(true, NOW - 30_000, NOW)).toEqual({ points: 6, newLastPenaltyAtMs: NOW });
    expect(decideServerPenalty(true, NOW - 12_000, NOW)).toEqual({ points: 2, newLastPenaltyAtMs: NOW - 2_000 });
  });
  it("charges nothing when an honest client already paid this interval", () => {
    expect(decideServerPenalty(true, NOW - 3_000, NOW)).toEqual({ points: 0, newLastPenaltyAtMs: null });
  });
  it("never back-charges a stale window", () => {
    expect(decideServerPenalty(true, NOW - 10 * 60_000, NOW)).toEqual({ points: 0, newLastPenaltyAtMs: NOW });
    expect(decideServerPenalty(true, NOW + 5_000, NOW)).toEqual({ points: 0, newLastPenaltyAtMs: NOW });
  });
});

describe("decideClientPenalty", () => {
  it("charges one point and records the time", () => {
    expect(decideClientPenalty(null, NOW)).toEqual({ points: 1, newLastPenaltyAtMs: NOW });
    expect(decideClientPenalty(NOW - 5_000, NOW)).toEqual({ points: 1, newLastPenaltyAtMs: NOW });
  });
  it("absorbs a retry landing inside the window", () => {
    expect(decideClientPenalty(NOW - 1_000, NOW)).toEqual({ points: 0, newLastPenaltyAtMs: null });
  });
});
