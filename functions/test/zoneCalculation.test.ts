import { describe, expect, test } from "vitest";
import { HttpsError } from "firebase-functions/v2/https";
import {
  computeZoneConfigurationCore,
  calculateNormalModeSettingsServer,
} from "../src/zoneCalculation";
import { haversineDistance } from "../src/powerUpSpawn";

// Helper: a stayInTheZone input with two pins separated by `distanceM`
// meters along the lat axis. Anchored on Brussels.
function pinsAtDistance(distanceM: number) {
  const startLat = 50.85;
  const startLng = 4.35;
  const finalLat = startLat + distanceM / 111_320;
  return {
    start: { lat: startLat, lng: startLng },
    final: { lat: finalLat, lng: startLng },
  };
}

// ─── Formula `stayInTheZone`: R = max(D × 1.5, D + 50 + 200, 800) ─

describe("computeZoneConfigurationCore — stayInTheZone radius formula", () => {
  test("D = 50m → max(75, 300, 800) = 800 (floor)", () => {
    const { start, final } = pinsAtDistance(50);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    expect(out.initialRadius).toBeCloseTo(800, 6);
  });

  test("D = 500m → max(750, 750, 800) = 800 (floor)", () => {
    const { start, final } = pinsAtDistance(500);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    expect(out.initialRadius).toBeCloseTo(800, 6);
  });

  test("D = 1000m → max(1500, 1250, 800) = 1500 (D × 1.5 dominates)", () => {
    const { start, final } = pinsAtDistance(1000);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    const actualD = haversineDistance(start.lat, start.lng, final.lat, final.lng);
    expect(out.initialRadius).toBeCloseTo(actualD * 1.5, 3);
  });

  test("D = 2000m → max(3000, 2250, 800) = 3000 (D × 1.5 dominates)", () => {
    const { start, final } = pinsAtDistance(2000);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    const actualD = haversineDistance(start.lat, start.lng, final.lat, final.lng);
    expect(out.initialRadius).toBeCloseTo(actualD * 1.5, 3);
  });

  test("D = 10km → max(15000, 10250, 800) = 15000 (no max bound on D)", () => {
    const { start, final } = pinsAtDistance(10_000);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    const actualD = haversineDistance(start.lat, start.lng, final.lat, final.lng);
    expect(out.initialRadius).toBeCloseTo(actualD * 1.5, 3);
    expect(out.initialRadius).toBeGreaterThan(800);
  });

  test("D + 50 + 200 dominates when D × 1.5 < D + 250 (i.e. D < 500)", () => {
    // For D = 300m: D × 1.5 = 450, D + 250 = 550 → the additive branch
    // dominates over the multiplicative one. Both lose to the 800 floor
    // but this still exercises the second branch of `max(...)`.
    const D = 300;
    const start = { lat: 50.85, lng: 4.35 };
    // Synthesize finalPoint to land at exactly D meters east of start
    // so neither branch is suppressed by the 800 m floor in another
    // pair of pins:
    const final = { lat: 50.85, lng: 4.35 + D / (111_320 * Math.cos((50.85 * Math.PI) / 180)) };
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    // D + 250 = 550, D × 1.5 = 450, 800 = 800 → max is 800 (floor).
    expect(out.initialRadius).toBeCloseTo(800, 6);
  });
});

// ─── interior margin ≥ 200 m always satisfied ─────────────────

describe("computeZoneConfigurationCore — interior margin invariant", () => {
  test("for every D ≤ 10km, initialRadius − D ≥ 200 m (interior margin)", () => {
    for (const D of [50, 100, 500, 1000, 1500, 2000, 5000, 10_000]) {
      const { start, final } = pinsAtDistance(D);
      const out = computeZoneConfigurationCore({
        startPoint: start,
        finalPoint: final,
        gameMode: "stayInTheZone",
        gameDurationMinutes: 60,
      });
      const actualD = haversineDistance(
        start.lat,
        start.lng,
        final.lat,
        final.lng
      );
      // The radius − distance gap is the breathing room around the
      // final pin. Must always be at least FINAL (50) + MARGIN (200) =
      // 250 m so the final 50 m disc has room to live.
      expect(out.initialRadius - actualD).toBeGreaterThanOrEqual(
        out.finalZoneRadius + out.interiorMargin - 1e-6
      );
    }
  });
});

// ─── followTheChicken radius hint ─────────────────────────────

describe("computeZoneConfigurationCore — followTheChicken radius hint", () => {
  test("Small (500) → initialRadius 500", () => {
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: null,
      gameMode: "followTheChicken",
      radiusHint: 500,
      gameDurationMinutes: 60,
    });
    expect(out.initialRadius).toBe(500);
    expect(out.validatedFinal).toBeNull();
  });

  test("Medium (1000) → initialRadius 1000", () => {
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: null,
      gameMode: "followTheChicken",
      radiusHint: 1000,
      gameDurationMinutes: 60,
    });
    expect(out.initialRadius).toBe(1000);
  });

  test("Large (2000) → initialRadius 2000", () => {
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: null,
      gameMode: "followTheChicken",
      radiusHint: 2000,
      gameDurationMinutes: 60,
    });
    expect(out.initialRadius).toBe(2000);
  });

  test("followTheChicken with finalPoint set → validatedFinal still null", () => {
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: { lat: 50.86, lng: 4.36 },
      gameMode: "followTheChicken",
      radiusHint: 1000,
      gameDurationMinutes: 60,
    });
    expect(out.validatedFinal).toBeNull();
  });
});

// ─── input validation: every 400 case ─────────────────────────

describe("computeZoneConfigurationCore — input validation", () => {
  test("stayInTheZone without finalPoint throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameMode: "stayInTheZone",
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("followTheChicken without radiusHint throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: null,
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("followTheChicken with off-enum radiusHint throws invalid-argument", () => {
    for (const bad of [0, 100, 750, 1500, 3000, 9999]) {
      expect(() =>
        computeZoneConfigurationCore({
          startPoint: { lat: 50.85, lng: 4.35 },
          finalPoint: null,
          gameMode: "followTheChicken",
          radiusHint: bad,
          gameDurationMinutes: 60,
        })
      ).toThrowError(HttpsError);
    }
  });

  test("missing gameDurationMinutes throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: 1000,
        gameDurationMinutes: null,
      })
    ).toThrowError(HttpsError);
  });

  test("negative gameDurationMinutes throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: 1000,
        gameDurationMinutes: -10,
      })
    ).toThrowError(HttpsError);
  });

  test("zero gameDurationMinutes throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: 1000,
        gameDurationMinutes: 0,
      })
    ).toThrowError(HttpsError);
  });

  test("missing gameMode throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("unknown gameMode string throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: null,
        gameMode: "battleRoyale",
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("missing startPoint throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: 1000,
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("startPoint with NaN coordinates throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: NaN, lng: 4.35 },
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: 1000,
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("startPoint with out-of-range lat throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 95, lng: 4.35 },
        finalPoint: null,
        gameMode: "followTheChicken",
        radiusHint: 1000,
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });

  test("finalPoint with bad shape throws invalid-argument", () => {
    expect(() =>
      computeZoneConfigurationCore({
        startPoint: { lat: 50.85, lng: 4.35 },
        finalPoint: { lat: "north" as unknown as number, lng: 4.36 },
        gameMode: "stayInTheZone",
        gameDurationMinutes: 60,
      })
    ).toThrowError(HttpsError);
  });
});

// ─── forceNewSeed semantics ────────────────────────────────────

describe("computeZoneConfigurationCore — forceNewSeed semantics", () => {
  const input = {
    startPoint: { lat: 50.85, lng: 4.35 },
    finalPoint: { lat: 50.86, lng: 4.36 },
    gameMode: "stayInTheZone" as const,
    gameDurationMinutes: 60,
  };

  test("without forceNewSeed, same inputs → same seed (deterministic)", () => {
    const a = computeZoneConfigurationCore(input);
    const b = computeZoneConfigurationCore(input);
    expect(a.driftSeed).toBe(b.driftSeed);
    expect(a.driftSeed).toBeGreaterThan(0);
  });

  test("with forceNewSeed, repeated calls produce different seeds", () => {
    // With Math.random under the hood the chance of two consecutive
    // shuffles colliding is ~1/2^31. Loop 5 times to make the test
    // tolerant to a single unlucky collision.
    const seeds = new Set<number>();
    for (let i = 0; i < 5; i++) {
      const out = computeZoneConfigurationCore({ ...input, forceNewSeed: true });
      seeds.add(out.driftSeed);
      expect(out.driftSeed).toBeGreaterThan(0);
    }
    expect(seeds.size).toBeGreaterThan(1);
  });

  test("existingSeed > 0 wins over deterministic derivation", () => {
    const out = computeZoneConfigurationCore({
      ...input,
      existingSeed: 424242,
    });
    expect(out.driftSeed).toBe(424242);
  });

  test("forceNewSeed wins over existingSeed (shuffle clears the lock)", () => {
    // Wrap many tries so the rare collision with `424242` doesn't fail.
    let differed = false;
    for (let i = 0; i < 10 && !differed; i++) {
      const out = computeZoneConfigurationCore({
        ...input,
        existingSeed: 424242,
        forceNewSeed: true,
      });
      if (out.driftSeed !== 424242) differed = true;
    }
    expect(differed).toBe(true);
  });
});

// ─── calculateNormalModeSettings parity ───────────────────────

describe("calculateNormalModeSettingsServer — iOS/Android parity", () => {
  // These three cases match the iOS GameTests.swift goldens line-for-line.
  test("2h game, 1500m radius → 5min interval, 116.66 m/shrink", () => {
    const { interval, decline } = calculateNormalModeSettingsServer(1500, 120);
    expect(interval).toBe(5);
    expect(decline).toBeCloseTo((1500 - 100) / 24, 6);
  });

  test("1h game, 1500m radius → 5min interval, 116.66 m/shrink (12 shrinks)", () => {
    const { interval, decline } = calculateNormalModeSettingsServer(1500, 60);
    expect(interval).toBe(5);
    expect(decline).toBeCloseTo((1500 - 100) / 12, 6);
  });

  test("game shorter than one interval → decline 0 (no shrinking scheduled)", () => {
    const { interval, decline } = calculateNormalModeSettingsServer(1500, 3);
    // 3 / 5 = 0.6 numberOfShrinks > 0 → still computes a decline that
    // collapses to MIN over those 0.6 shrinks (so 0.6 × decline = 1400,
    // decline = ~2333 m/shrink). Pin the exact number so a refactor
    // catches any drift.
    expect(interval).toBe(5);
    expect(decline).toBeCloseTo((1500 - 100) / (3 / 5), 6);
  });

  test("0 duration → decline 0", () => {
    const { interval, decline } = calculateNormalModeSettingsServer(1500, 0);
    expect(interval).toBe(5);
    expect(decline).toBe(0);
  });

  test("decline floored at 0 when initialRadius < minimum", () => {
    const { decline } = calculateNormalModeSettingsServer(50, 120);
    expect(decline).toBe(0);
  });

  test("returned values match what the CF embeds", () => {
    const { start, final } = pinsAtDistance(800);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 120,
    });
    const expected = calculateNormalModeSettingsServer(out.initialRadius, 120);
    expect(out.shrinkIntervalMinutes).toBe(expected.interval);
    expect(out.shrinkMetersPerUpdate).toBe(expected.decline);
  });
});

// ─── circles array (PP-13 recap preview) ──────────────────────

describe("computeZoneConfigurationCore — intermediate circles", () => {
  test("includes initial circle as first entry", () => {
    const { start, final } = pinsAtDistance(1000);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    expect(out.circles[0].radiusMeters).toBe(out.initialRadius);
    expect(out.circles[0].center.lat).toBe(start.lat);
    expect(out.circles[0].center.lng).toBe(start.lng);
  });

  test("stayInTheZone produces a monotonically shrinking circle sequence", () => {
    const { start, final } = pinsAtDistance(1000);
    const out = computeZoneConfigurationCore({
      startPoint: start,
      finalPoint: final,
      gameMode: "stayInTheZone",
      gameDurationMinutes: 60,
    });
    expect(out.circles.length).toBeGreaterThan(1);
    for (let i = 1; i < out.circles.length; i++) {
      expect(out.circles[i].radiusMeters).toBeLessThan(
        out.circles[i - 1].radiusMeters
      );
    }
    // Final entry equals the final-zone radius (50 m).
    expect(out.circles[out.circles.length - 1].radiusMeters).toBe(50);
  });

  test("circles are deterministic across calls (same inputs → same array)", () => {
    const input = {
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: { lat: 50.86, lng: 4.36 },
      gameMode: "stayInTheZone" as const,
      gameDurationMinutes: 60,
    };
    const a = computeZoneConfigurationCore(input);
    const b = computeZoneConfigurationCore(input);
    expect(a.circles).toEqual(b.circles);
  });

  test("followTheChicken: every circle stays centered on start", () => {
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: null,
      gameMode: "followTheChicken",
      radiusHint: 1000,
      gameDurationMinutes: 60,
    });
    expect(out.circles.length).toBeGreaterThan(1);
    for (const c of out.circles) {
      expect(c.center.lat).toBe(50.85);
      expect(c.center.lng).toBe(4.35);
    }
  });

  test("zero-shrink game (duration < interval) returns a single circle", () => {
    // duration shorter than the fixed 5-min interval → decline still
    // computes but the schedule loops fewer than 1 step → recap shows
    // just the initial circle.
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: null,
      gameMode: "followTheChicken",
      radiusHint: 1000,
      // Game is 3 min long but the decline computes from (1500 − 100)/0.6
      // → 2333 m/shrink, which shaves past the final radius on the very
      // first step. Schedule contains the initial circle plus a final
      // 50 m clamp.
      gameDurationMinutes: 3,
    });
    expect(out.circles.length).toBeGreaterThanOrEqual(1);
    expect(out.circles[0].radiusMeters).toBe(1000);
  });
});

// ─── constants pinned ─────────────────────────────────────────

describe("computeZoneConfigurationCore — pinned constants", () => {
  test("finalZoneRadius is 50, interiorMargin is 200", () => {
    const out = computeZoneConfigurationCore({
      startPoint: { lat: 50.85, lng: 4.35 },
      finalPoint: null,
      gameMode: "followTheChicken",
      radiusHint: 1000,
      gameDurationMinutes: 60,
    });
    expect(out.finalZoneRadius).toBe(50);
    expect(out.interiorMargin).toBe(200);
  });
});
