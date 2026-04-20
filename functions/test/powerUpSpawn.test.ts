import { describe, expect, test } from "vitest";
import { Timestamp } from "firebase-admin/firestore";
import {
  deterministicDriftCenterServer,
  filterEnabledTypesServer,
  generatePowerUpsServer,
  haversineDistance,
  interpolateZoneCenterServer,
  POSITION_DEPENDENT_POWER_UPS,
} from "../src/powerUpSpawn";

// ─── filterEnabledTypesServer ─────────────────────────────────

describe("filterEnabledTypesServer", () => {
  const allTypes = [
    "invisibility",
    "zoneFreeze",
    "radarPing",
    "decoy",
    "jammer",
    "zonePreview",
  ];

  test("followTheChicken mode returns input unchanged", () => {
    expect(filterEnabledTypesServer(allTypes, "followTheChicken")).toEqual(allTypes);
  });

  test("unknown gameMode returns input unchanged", () => {
    expect(filterEnabledTypesServer(allTypes, "somethingElse")).toEqual(allTypes);
  });

  test("stayInTheZone strips position-dependent types", () => {
    const filtered = filterEnabledTypesServer(allTypes, "stayInTheZone");
    for (const t of POSITION_DEPENDENT_POWER_UPS) {
      expect(filtered).not.toContain(t);
    }
    expect(filtered).toContain("zoneFreeze");
    expect(filtered).toContain("radarPing");
    expect(filtered).toContain("zonePreview");
  });

  test("stayInTheZone with only position-dependent types returns empty", () => {
    const filtered = filterEnabledTypesServer(
      ["invisibility", "decoy", "jammer"],
      "stayInTheZone"
    );
    expect(filtered).toEqual([]);
  });

  test("empty input returns empty", () => {
    expect(filterEnabledTypesServer([], "stayInTheZone")).toEqual([]);
    expect(filterEnabledTypesServer([], "followTheChicken")).toEqual([]);
  });
});

// ─── interpolateZoneCenterServer ──────────────────────────────

describe("interpolateZoneCenterServer", () => {
  const initial = { latitude: 50.8466, longitude: 4.3528 };
  const final = { latitude: 50.85, longitude: 4.36 };

  test("undefined finalCenter returns initialCenter", () => {
    const result = interpolateZoneCenterServer(initial, undefined, 1000, 500);
    expect(result).toEqual(initial);
  });

  test("initialRadius 0 returns initialCenter", () => {
    const result = interpolateZoneCenterServer(initial, final, 0, 0);
    expect(result).toEqual(initial);
  });

  test("no shrink (currentRadius = initialRadius) returns initialCenter", () => {
    const result = interpolateZoneCenterServer(initial, final, 1000, 1000);
    expect(result.latitude).toBeCloseTo(initial.latitude, 6);
    expect(result.longitude).toBeCloseTo(initial.longitude, 6);
  });

  test("full shrink (currentRadius = 0) returns finalCenter", () => {
    const result = interpolateZoneCenterServer(initial, final, 1000, 0);
    expect(result.latitude).toBeCloseTo(final.latitude, 6);
    expect(result.longitude).toBeCloseTo(final.longitude, 6);
  });

  test("half shrink returns midpoint", () => {
    const result = interpolateZoneCenterServer(initial, final, 1000, 500);
    expect(result.latitude).toBeCloseTo(
      (initial.latitude + final.latitude) / 2,
      6
    );
    expect(result.longitude).toBeCloseTo(
      (initial.longitude + final.longitude) / 2,
      6
    );
  });

  test("progress is clamped at 0 when currentRadius > initialRadius", () => {
    // currentRadius > initialRadius shouldn't happen in practice but guard anyway
    const result = interpolateZoneCenterServer(initial, final, 1000, 2000);
    expect(result.latitude).toBeCloseTo(initial.latitude, 6);
    expect(result.longitude).toBeCloseTo(initial.longitude, 6);
  });

  test("progress is clamped at 1 when currentRadius < 0", () => {
    const result = interpolateZoneCenterServer(initial, final, 1000, -100);
    expect(result.latitude).toBeCloseTo(final.latitude, 6);
    expect(result.longitude).toBeCloseTo(final.longitude, 6);
  });
});

// ─── deterministicDriftCenterServer ───────────────────────────

describe("deterministicDriftCenterServer", () => {
  const base = { latitude: 50.8466, longitude: 4.3528 };

  test("is deterministic for same inputs", () => {
    const a = deterministicDriftCenterServer(base, 1500, 1400, 42);
    const b = deterministicDriftCenterServer(base, 1500, 1400, 42);
    expect(a).toEqual(b);
  });

  test("oldRadius == newRadius returns base (safeDrift = 0)", () => {
    const result = deterministicDriftCenterServer(base, 1000, 1000, 42);
    expect(result).toEqual(base);
  });

  test("newRadius = 0 returns base (maxFromBase = 0)", () => {
    const result = deterministicDriftCenterServer(base, 1000, 0, 42);
    expect(result).toEqual(base);
  });

  test("oldRadius < newRadius returns base (can't grow)", () => {
    const result = deterministicDriftCenterServer(base, 500, 1000, 42);
    expect(result).toEqual(base);
  });

  test("drift stays within safeDrift bound", () => {
    const oldRadius = 1500;
    const newRadius = 1400;
    const safeDrift = Math.min(newRadius * 0.5, (oldRadius - newRadius) * 0.5);
    const result = deterministicDriftCenterServer(base, oldRadius, newRadius, 123);
    const dist = haversineDistance(
      base.latitude,
      base.longitude,
      result.latitude,
      result.longitude
    );
    // Allow 1% slack for floating-point
    expect(dist).toBeLessThanOrEqual(safeDrift * 1.01);
  });

  test("different seeds produce different drifts", () => {
    const a = deterministicDriftCenterServer(base, 1500, 1400, 1);
    const b = deterministicDriftCenterServer(base, 1500, 1400, 2);
    // Not guaranteed to differ, but for these seeds they should
    expect(a).not.toEqual(b);
  });
});

// ─── generatePowerUpsServer ───────────────────────────────────

describe("generatePowerUpsServer", () => {
  const center = { latitude: 50.8466, longitude: 4.3528 };
  const radius = 1500;
  const fixedNow = Timestamp.fromMillis(1_700_000_000_000);
  const allTypes = ["invisibility", "zoneFreeze", "radarPing", "decoy", "jammer", "zonePreview"];

  test("empty enabledTypes returns empty", () => {
    const result = generatePowerUpsServer(center, radius, 5, 42, 0, [], fixedNow);
    expect(result).toEqual([]);
  });

  test("count 0 returns empty", () => {
    const result = generatePowerUpsServer(center, radius, 0, 42, 0, allTypes, fixedNow);
    expect(result).toEqual([]);
  });

  test("negative count returns empty", () => {
    const result = generatePowerUpsServer(center, radius, -1, 42, 0, allTypes, fixedNow);
    expect(result).toEqual([]);
  });

  test("count matches output length", () => {
    const result = generatePowerUpsServer(center, radius, 5, 42, 0, allTypes, fixedNow);
    expect(result).toHaveLength(5);
  });

  test("is deterministic for identical inputs", () => {
    const a = generatePowerUpsServer(center, radius, 5, 42, 0, allTypes, fixedNow);
    const b = generatePowerUpsServer(center, radius, 5, 42, 0, allTypes, fixedNow);
    expect(a.length).toBe(b.length);
    for (let i = 0; i < a.length; i++) {
      expect(a[i].id).toBe(b[i].id);
      expect(a[i].type).toBe(b[i].type);
      expect(a[i].location.latitude).toBeCloseTo(b[i].location.latitude, 10);
      expect(a[i].location.longitude).toBeCloseTo(b[i].location.longitude, 10);
    }
  });

  test("different driftSeed produces different output", () => {
    const a = generatePowerUpsServer(center, radius, 5, 42, 0, allTypes, fixedNow);
    const b = generatePowerUpsServer(center, radius, 5, 99, 0, allTypes, fixedNow);
    // At least one should differ (positions or types)
    const anyDifferent = a.some((p, i) => p.id !== b[i].id || p.type !== b[i].type);
    expect(anyDifferent).toBe(true);
  });

  test("different batchIndex produces different output", () => {
    const a = generatePowerUpsServer(center, radius, 5, 42, 0, allTypes, fixedNow);
    const b = generatePowerUpsServer(center, radius, 5, 42, 1, allTypes, fixedNow);
    const anyDifferent = a.some((p, i) => p.id !== b[i].id);
    expect(anyDifferent).toBe(true);
  });

  test("all positions within 0.85 * radius of center", () => {
    const result = generatePowerUpsServer(center, radius, 20, 42, 0, allTypes, fixedNow);
    for (const pu of result) {
      const dist = haversineDistance(
        center.latitude,
        center.longitude,
        pu.location.latitude,
        pu.location.longitude
      );
      // sqrt-uniform + 0.85 factor => max distance ≈ 0.85 * radius. Allow 1% slack.
      expect(dist).toBeLessThanOrEqual(radius * 0.85 * 1.01);
    }
  });

  test("IDs follow expected format", () => {
    const result = generatePowerUpsServer(center, radius, 3, 42, 7, allTypes, fixedNow);
    result.forEach((pu, i) => {
      expect(pu.id).toMatch(new RegExp(`^pu-7-${i}-\\d+$`));
    });
  });

  test("IDs are unique within a batch", () => {
    const result = generatePowerUpsServer(center, radius, 20, 42, 0, allTypes, fixedNow);
    const ids = new Set(result.map((p) => p.id));
    expect(ids.size).toBe(result.length);
  });

  test("IDs don't collide across batchIndices with same seed", () => {
    const b0 = generatePowerUpsServer(center, radius, 5, 42, 0, allTypes, fixedNow);
    const b1 = generatePowerUpsServer(center, radius, 5, 42, 1, allTypes, fixedNow);
    const b0Ids = new Set(b0.map((p) => p.id));
    for (const p of b1) {
      expect(b0Ids.has(p.id)).toBe(false);
    }
  });

  test("uses provided timestamp for spawnedAt", () => {
    const result = generatePowerUpsServer(center, radius, 3, 42, 0, allTypes, fixedNow);
    for (const pu of result) {
      expect(pu.spawnedAt.toMillis()).toBe(fixedNow.toMillis());
    }
  });

  test("type is drawn from enabledTypes only", () => {
    const types = ["radarPing", "zonePreview"];
    const result = generatePowerUpsServer(center, radius, 20, 42, 0, types, fixedNow);
    for (const pu of result) {
      expect(types).toContain(pu.type);
    }
  });

  test("handles single enabledType", () => {
    const result = generatePowerUpsServer(center, radius, 5, 42, 0, ["radarPing"], fixedNow);
    expect(result).toHaveLength(5);
    result.forEach((pu) => expect(pu.type).toBe("radarPing"));
  });
});

// ─── haversineDistance ────────────────────────────────────────

describe("haversineDistance", () => {
  test("distance from a point to itself is zero", () => {
    expect(haversineDistance(50.0, 4.0, 50.0, 4.0)).toBe(0);
  });

  test("~111km per degree of latitude", () => {
    // 1 degree of latitude ≈ 111 km anywhere on Earth
    const d = haversineDistance(50.0, 4.0, 51.0, 4.0);
    expect(d).toBeGreaterThan(111_000);
    expect(d).toBeLessThan(112_000);
  });

  test("is symmetric", () => {
    const a = haversineDistance(50.0, 4.0, 50.5, 4.5);
    const b = haversineDistance(50.5, 4.5, 50.0, 4.0);
    expect(a).toBeCloseTo(b, 6);
  });
});
