import { describe, expect, test } from "vitest";
import { GeoPoint, Timestamp } from "firebase-admin/firestore";
import { materialiseGameDoc, sanitiseGamePayload } from "../src/stripe";

/*
 * Cross-platform contract covered by this file:
 *
 *   iOS Game.Zone.center: GeoPoint            (Swift Firebase SDK)
 *   iOS Game.Zone.finalCenter: GeoPoint?      (nullable)
 *   Android Zone.center: GeoPoint             (com.google.firebase.firestore.GeoPoint)
 *   Android Zone.finalCenter: GeoPoint?       (nullable)
 *   Server TypeScript: new GeoPoint(lat, lng) (firebase-admin/firestore)
 *
 * All three SDKs serialise `GeoPoint` as the Firestore native `geopoint`
 * type. Anything else (plain object `{latitude, longitude}`, string, nested
 * map) is stored as a generic map and breaks mobile decoders, the Android
 * crash we shipped in 1.9.0 build 26 was exactly that:
 *
 *   java.lang.RuntimeException: Failed to convert value of type
 *     java.util.HashMap to GeoPoint (found in field 'zone.center')
 *
 * The tests below pin two things:
 *   - `sanitiseGamePayload` rejects any malformed zone so we never even reach
 *     the write step with dirty data.
 *   - `materialiseGameDoc` produces Firestore-native `GeoPoint` / `Timestamp`
 *     instances, never plain objects. `instanceof GeoPoint` against the same
 *     class the Firestore SDK uses to serialise is the strongest proof we can
 *     make here without spinning up an emulator, it guarantees the output
 *     round-trips through iOS + Android SDKs.
 */

// Minimal valid payload, shared across tests. Every test that wants to
// exercise a failure mode mutates one field of this baseline.
//
// `startMillis` is dynamic so the in-the-past guard inside sanitiseGamePayload
// stays in effect on real callers but doesn't reject the fixture as time
// marches on. We pin to "1 hour from now" so the rest of the validation
// (start < end, reasonable duration) keeps holding.
const ONE_HOUR_MS = 60 * 60 * 1000;
function validPayload(overrides: Record<string, unknown> = {}): unknown {
  const startMillis = Date.now() + ONE_HOUR_MS;
  return {
    name: "EVG Max",
    maxPlayers: 10,
    gameMode: "stayInTheZone",
    chickenCanSeeHunters: false,
    foundCode: "1234",
    timing: {
      startMillis,
      endMillis: startMillis + ONE_HOUR_MS,
      headStartMinutes: 5,
    },
    zone: {
      center: { latitude: 50.85, longitude: 4.35 },
      finalCenter: { latitude: 50.86, longitude: 4.36 },
      radius: 1500,
      shrinkIntervalMinutes: 5,
      shrinkMetersPerUpdate: 100,
      driftSeed: 42,
    },
    pricing: {
      model: "flat",
      pricePerPlayer: 500,
      deposit: 0,
      commission: 15,
    },
    registration: { required: false, closesMinutesBefore: null },
    powerUps: { enabled: true, enabledTypes: ["radarPing"] },
    ...overrides,
  };
}

function patchZone(zoneOverrides: Record<string, unknown>): unknown {
  const base = validPayload() as { zone: Record<string, unknown> };
  return { ...base, zone: { ...base.zone, ...zoneOverrides } };
}

function patchCenter(centerOverrides: Record<string, unknown>): unknown {
  const base = validPayload() as { zone: { center: Record<string, unknown> } };
  return {
    ...base,
    zone: { ...base.zone, center: { ...base.zone.center, ...centerOverrides } },
  };
}

// ---------------------------------------------------------------------------
// sanitiseGamePayload — the gate that stops malformed data from ever reaching
// materialiseGameDoc / Firestore in the first place.
// ---------------------------------------------------------------------------

describe("sanitiseGamePayload — zone.center", () => {
  test("accepts a well-formed center", () => {
    expect(() => sanitiseGamePayload(validPayload())).not.toThrow();
  });

  test("accepts (0, 0) — null island", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: 0, longitude: 0 }))).not.toThrow();
  });

  test("accepts (+90, 0) — north pole", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: 90, longitude: 0 }))).not.toThrow();
  });

  test("accepts (-90, 0) — south pole", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: -90, longitude: 0 }))).not.toThrow();
  });

  test("accepts (0, 180) — IDL east", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: 0, longitude: 180 }))).not.toThrow();
  });

  test("accepts (0, -180) — IDL west", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: 0, longitude: -180 }))).not.toThrow();
  });

  test("rejects latitude > 90", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: 90.001 }))).toThrow(/latitude/);
  });

  test("rejects latitude < -90", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: -90.001 }))).toThrow(/latitude/);
  });

  test("rejects longitude > 180", () => {
    expect(() => sanitiseGamePayload(patchCenter({ longitude: 180.001 }))).toThrow(/longitude/);
  });

  test("rejects longitude < -180", () => {
    expect(() => sanitiseGamePayload(patchCenter({ longitude: -180.001 }))).toThrow(/longitude/);
  });

  test("rejects NaN latitude", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: NaN }))).toThrow(/latitude/);
  });

  test("rejects +Infinity latitude", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: Number.POSITIVE_INFINITY }))).toThrow(
      /latitude/,
    );
  });

  test("rejects -Infinity longitude", () => {
    expect(() => sanitiseGamePayload(patchCenter({ longitude: Number.NEGATIVE_INFINITY }))).toThrow(
      /longitude/,
    );
  });

  test("rejects string latitude (JSON coerced, classic)", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: "50.85" }))).toThrow(/latitude/);
  });

  test("rejects null latitude", () => {
    expect(() => sanitiseGamePayload(patchCenter({ latitude: null }))).toThrow(/latitude/);
  });

  test("rejects missing latitude", () => {
    const base = validPayload() as { zone: { center: Record<string, unknown> } };
    const broken = {
      ...base,
      zone: { ...base.zone, center: { longitude: base.zone.center.longitude } },
    };
    expect(() => sanitiseGamePayload(broken)).toThrow(/latitude/);
  });

  test("rejects center that is a string", () => {
    expect(() => sanitiseGamePayload(patchZone({ center: "50.85,4.35" }))).toThrow(/zone.center/);
  });

  test("rejects center that is null", () => {
    expect(() => sanitiseGamePayload(patchZone({ center: null }))).toThrow(/zone.center/);
  });

  test("rejects a missing center field", () => {
    const base = validPayload() as { zone: Record<string, unknown> };
    const { center, ...rest } = base.zone as { center: unknown; [k: string]: unknown };
    void center;
    expect(() => sanitiseGamePayload({ ...base, zone: rest })).toThrow(/zone.center/);
  });
});

describe("sanitiseGamePayload — zone.finalCenter", () => {
  test("accepts explicit null", () => {
    expect(() => sanitiseGamePayload(patchZone({ finalCenter: null }))).not.toThrow();
  });

  test("accepts omitted (undefined normalises to null)", () => {
    const base = validPayload() as { zone: Record<string, unknown> };
    const { finalCenter, ...rest } = base.zone as { finalCenter: unknown; [k: string]: unknown };
    void finalCenter;
    const out = sanitiseGamePayload({ ...base, zone: rest });
    expect((out as { zone: { finalCenter: unknown } }).zone.finalCenter).toBeNull();
  });

  test("rejects malformed finalCenter when present", () => {
    expect(() => sanitiseGamePayload(patchZone({ finalCenter: { latitude: 99, longitude: 0 } }))).toThrow(
      /finalCenter.latitude/,
    );
  });

  test("accepts finalCenter at the same coord as center (degenerate but legal)", () => {
    const base = validPayload() as { zone: { center: Record<string, unknown> } };
    expect(() =>
      sanitiseGamePayload(patchZone({ finalCenter: { ...base.zone.center } })),
    ).not.toThrow();
  });
});

describe("sanitiseGamePayload — zone numeric fields", () => {
  test("rejects radius = 0", () => {
    expect(() => sanitiseGamePayload(patchZone({ radius: 0 }))).toThrow(/radius/);
  });

  test("rejects negative radius", () => {
    expect(() => sanitiseGamePayload(patchZone({ radius: -1 }))).toThrow(/radius/);
  });

  test("rejects radius above the Firestore-rule limit (50000 m)", () => {
    expect(() => sanitiseGamePayload(patchZone({ radius: 50_001 }))).toThrow(/radius/);
  });

  test("accepts radius at the upper limit", () => {
    expect(() => sanitiseGamePayload(patchZone({ radius: 50_000 }))).not.toThrow();
  });

  test("rejects NaN radius", () => {
    expect(() => sanitiseGamePayload(patchZone({ radius: NaN }))).toThrow(/radius/);
  });

  test("rejects Infinity radius", () => {
    expect(() => sanitiseGamePayload(patchZone({ radius: Number.POSITIVE_INFINITY }))).toThrow(
      /radius/,
    );
  });

  test("rejects shrinkIntervalMinutes = 0", () => {
    expect(() => sanitiseGamePayload(patchZone({ shrinkIntervalMinutes: 0 }))).toThrow(
      /shrinkIntervalMinutes/,
    );
  });

  test("rejects negative shrinkMetersPerUpdate", () => {
    expect(() => sanitiseGamePayload(patchZone({ shrinkMetersPerUpdate: -1 }))).toThrow(
      /shrinkMetersPerUpdate/,
    );
  });

  test("accepts shrinkMetersPerUpdate = 0 (chicken stays put, zone only shrinks)", () => {
    expect(() => sanitiseGamePayload(patchZone({ shrinkMetersPerUpdate: 0 }))).not.toThrow();
  });

  test("accepts negative driftSeed (splitmix64 uses signed inputs)", () => {
    expect(() => sanitiseGamePayload(patchZone({ driftSeed: -999_999 }))).not.toThrow();
  });

  test("rejects NaN driftSeed", () => {
    expect(() => sanitiseGamePayload(patchZone({ driftSeed: NaN }))).toThrow(/driftSeed/);
  });

  test("rejects missing zone entirely", () => {
    const base = validPayload() as Record<string, unknown>;
    const { zone: _zone, ...rest } = base;
    void _zone;
    expect(() => sanitiseGamePayload(rest)).toThrow(/zone/);
  });

  test("rejects zone being a string", () => {
    expect(() => sanitiseGamePayload(validPayload({ zone: "not-an-object" }))).toThrow(/zone/);
  });
});

describe("sanitiseGamePayload — timing", () => {
  test("rejects startMillis >= endMillis", () => {
    const start = Date.now() + ONE_HOUR_MS;
    expect(() =>
      sanitiseGamePayload(
        validPayload({ timing: { startMillis: start, endMillis: start, headStartMinutes: 0 } }),
      ),
    ).toThrow(/startMillis must be before/);
  });

  test("rejects negative headStartMinutes", () => {
    const start = Date.now() + ONE_HOUR_MS;
    expect(() =>
      sanitiseGamePayload(
        validPayload({ timing: { startMillis: start, endMillis: start + 1000, headStartMinutes: -1 } }),
      ),
    ).toThrow(/headStartMinutes/);
  });

  test("rejects NaN startMillis", () => {
    const start = Date.now() + ONE_HOUR_MS;
    expect(() =>
      sanitiseGamePayload(
        validPayload({ timing: { startMillis: NaN, endMillis: start, headStartMinutes: 0 } }),
      ),
    ).toThrow(/startMillis/);
  });

  test("rejects startMillis in the past", () => {
    const start = Date.now() - 60 * 60 * 1000;
    expect(() =>
      sanitiseGamePayload(
        validPayload({ timing: { startMillis: start, endMillis: start + ONE_HOUR_MS, headStartMinutes: 0 } }),
      ),
    ).toThrow(/in the past/);
  });
});

describe("sanitiseGamePayload — name", () => {
  test("accepts empty name (client shows \"Game {code}\" fallback)", () => {
    expect(() => sanitiseGamePayload(validPayload({ name: "" }))).not.toThrow();
  });

  test("accepts whitespace-only name (trimmed down to empty)", () => {
    const out = sanitiseGamePayload(validPayload({ name: "    " }));
    expect((out as { name: string }).name).toBe("");
  });

  test("rejects name longer than 80 chars", () => {
    expect(() => sanitiseGamePayload(validPayload({ name: "x".repeat(81) }))).toThrow(/name/);
  });

  test("trims whitespace around name", () => {
    const out = sanitiseGamePayload(validPayload({ name: "  EVG Max  " }));
    expect((out as { name: string }).name).toBe("EVG Max");
  });

  test("accepts 80-char name (boundary)", () => {
    expect(() => sanitiseGamePayload(validPayload({ name: "x".repeat(80) }))).not.toThrow();
  });
});

describe("sanitiseGamePayload — gameMode", () => {
  test("rejects unknown gameMode", () => {
    expect(() => sanitiseGamePayload(validPayload({ gameMode: "freeForAll" }))).toThrow(/gameMode/);
  });

  test("accepts followTheChicken", () => {
    expect(() => sanitiseGamePayload(validPayload({ gameMode: "followTheChicken" }))).not.toThrow();
  });

  test("accepts stayInTheZone", () => {
    expect(() => sanitiseGamePayload(validPayload({ gameMode: "stayInTheZone" }))).not.toThrow();
  });
});

describe("sanitiseGamePayload — chickenCanSeeHunters", () => {
  test("rejects non-boolean chickenCanSeeHunters", () => {
    expect(() => sanitiseGamePayload(validPayload({ chickenCanSeeHunters: "yes" }))).toThrow(
      /chickenCanSeeHunters/,
    );
  });

  test("rejects undefined chickenCanSeeHunters", () => {
    const base = validPayload() as Record<string, unknown>;
    delete base.chickenCanSeeHunters;
    expect(() => sanitiseGamePayload(base)).toThrow(/chickenCanSeeHunters/);
  });
});

describe("sanitiseGamePayload — registration", () => {
  test("rejects missing registration block", () => {
    const base = validPayload() as Record<string, unknown>;
    delete base.registration;
    expect(() => sanitiseGamePayload(base)).toThrow(/registration/);
  });

  test("rejects non-boolean registration.required", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ registration: { required: "no", closesMinutesBefore: 30 } })),
    ).toThrow(/registration\.required/);
  });

  test("accepts null closesMinutesBefore (no deadline)", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ registration: { required: false, closesMinutesBefore: null } })),
    ).not.toThrow();
  });

  test("accepts valid closesMinutesBefore", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ registration: { required: true, closesMinutesBefore: 60 } })),
    ).not.toThrow();
  });

  test("rejects negative closesMinutesBefore", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ registration: { required: true, closesMinutesBefore: -1 } })),
    ).toThrow(/closesMinutesBefore/);
  });

  test("rejects closesMinutesBefore > 1 week", () => {
    expect(() =>
      sanitiseGamePayload(
        validPayload({ registration: { required: true, closesMinutesBefore: 100_000 } }),
      ),
    ).toThrow(/closesMinutesBefore/);
  });
});

describe("sanitiseGamePayload — powerUps", () => {
  test("rejects missing powerUps block", () => {
    const base = validPayload() as Record<string, unknown>;
    delete base.powerUps;
    expect(() => sanitiseGamePayload(base)).toThrow(/powerUps/);
  });

  test("rejects non-boolean powerUps.enabled", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ powerUps: { enabled: "yes", enabledTypes: [] } })),
    ).toThrow(/powerUps\.enabled/);
  });

  test("rejects non-array enabledTypes", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ powerUps: { enabled: true, enabledTypes: "all" } })),
    ).toThrow(/enabledTypes/);
  });

  test("rejects enabledTypes with > 32 entries", () => {
    expect(() =>
      sanitiseGamePayload(
        validPayload({ powerUps: { enabled: true, enabledTypes: Array(33).fill("x") } }),
      ),
    ).toThrow(/enabledTypes/);
  });

  test("rejects enabledTypes with non-string entry", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ powerUps: { enabled: true, enabledTypes: [123] } })),
    ).toThrow(/enabledTypes/);
  });

  test("rejects enabledTypes with empty string entry", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ powerUps: { enabled: true, enabledTypes: [""] } })),
    ).toThrow(/enabledTypes/);
  });

  test("accepts empty enabledTypes (power-ups disabled at the catalog level)", () => {
    expect(() =>
      sanitiseGamePayload(validPayload({ powerUps: { enabled: false, enabledTypes: [] } })),
    ).not.toThrow();
  });
});

describe("sanitiseGamePayload — pricing.commission", () => {
  test("rejects negative commission", () => {
    expect(() =>
      sanitiseGamePayload(
        validPayload({ pricing: { model: "flat", pricePerPlayer: 500, deposit: 0, commission: -1 } }),
      ),
    ).toThrow(/commission/);
  });

  test("rejects commission > 100", () => {
    expect(() =>
      sanitiseGamePayload(
        validPayload({ pricing: { model: "flat", pricePerPlayer: 500, deposit: 0, commission: 150 } }),
      ),
    ).toThrow(/commission/);
  });
});

describe("sanitiseGamePayload — maxPlayers + pricing", () => {
  test("rejects non-integer maxPlayers (5.5)", () => {
    expect(() => sanitiseGamePayload(validPayload({ maxPlayers: 5.5 }))).toThrow(/maxPlayers/);
  });

  test("rejects maxPlayers = 0", () => {
    expect(() => sanitiseGamePayload(validPayload({ maxPlayers: 0 }))).toThrow(/maxPlayers/);
  });

  test("rejects maxPlayers > 100", () => {
    expect(() => sanitiseGamePayload(validPayload({ maxPlayers: 101 }))).toThrow(/maxPlayers/);
  });

  test("rejects NaN pricePerPlayer", () => {
    expect(() =>
      sanitiseGamePayload(
        validPayload({ pricing: { model: "flat", pricePerPlayer: NaN, deposit: 0, commission: 15 } }),
      ),
    ).toThrow(/pricePerPlayer/);
  });

  test("rejects free-mode (Stripe should never see one)", () => {
    expect(() =>
      sanitiseGamePayload(
        validPayload({ pricing: { model: "free", pricePerPlayer: 0, deposit: 0, commission: 0 } }),
      ),
    ).toThrow(/free games/);
  });
});

// ---------------------------------------------------------------------------
// materialiseGameDoc — the shape that actually lands in Firestore.
// ---------------------------------------------------------------------------

describe("materialiseGameDoc — GeoPoint serialisation (cross-platform contract)", () => {
  const GAME_ID = "abc123XYZ456";
  const UID = "user-1";

  test("zone.center is a GeoPoint instance (not a plain map)", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      UID,
      GAME_ID,
    );
    const zone = (out as { zone: { center: unknown } }).zone;
    expect(zone.center).toBeInstanceOf(GeoPoint);
  });

  test("zone.center latitude + longitude preserved exactly", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(patchCenter({ latitude: -33.868, longitude: 151.2093 })),
      UID,
      GAME_ID,
    );
    const center = (out as { zone: { center: GeoPoint } }).zone.center;
    expect(center.latitude).toBe(-33.868);
    expect(center.longitude).toBe(151.2093);
  });

  test("zone.finalCenter is a GeoPoint instance when provided", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      UID,
      GAME_ID,
    );
    const finalCenter = (out as { zone: { finalCenter: unknown } }).zone.finalCenter;
    expect(finalCenter).toBeInstanceOf(GeoPoint);
  });

  test("zone.finalCenter is null when the payload omits it", () => {
    const base = validPayload() as { zone: Record<string, unknown> };
    const { finalCenter, ...rest } = base.zone as { finalCenter: unknown; [k: string]: unknown };
    void finalCenter;
    const out = materialiseGameDoc(
      sanitiseGamePayload({ ...base, zone: rest }),
      UID,
      GAME_ID,
    );
    const finalCenterOut = (out as { zone: { finalCenter: unknown } }).zone.finalCenter;
    expect(finalCenterOut).toBeNull();
  });

  test("zone.finalCenter null survives `followTheChicken` round-trip", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(
        validPayload({ gameMode: "followTheChicken", zone: { ...(validPayload() as { zone: unknown }).zone as object, finalCenter: null } }),
      ),
      UID,
      GAME_ID,
    );
    expect((out as { zone: { finalCenter: unknown } }).zone.finalCenter).toBeNull();
  });

  test("timing.start / timing.end are Timestamp instances (not numbers)", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      UID,
      GAME_ID,
    );
    const timing = (out as { timing: { start: unknown; end: unknown } }).timing;
    expect(timing.start).toBeInstanceOf(Timestamp);
    expect(timing.end).toBeInstanceOf(Timestamp);
  });

  test("timing.start epoch-millis round-trip is exact", () => {
    const start = Date.now() + ONE_HOUR_MS;
    const end = start + 3_600_000;
    const out = materialiseGameDoc(
      sanitiseGamePayload(
        validPayload({ timing: { startMillis: start, endMillis: end, headStartMinutes: 5 } }),
      ),
      UID,
      GAME_ID,
    );
    const ts = (out as { timing: { start: Timestamp } }).timing.start;
    expect(ts.toMillis()).toBe(start);
  });

  test("zone.radius / shrink fields / driftSeed are preserved as plain numbers", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(
        patchZone({
          radius: 2500,
          shrinkIntervalMinutes: 3,
          shrinkMetersPerUpdate: 75,
          driftSeed: -12345,
        }),
      ),
      UID,
      GAME_ID,
    );
    const zone = (out as {
      zone: { radius: number; shrinkIntervalMinutes: number; shrinkMetersPerUpdate: number; driftSeed: number };
    }).zone;
    expect(zone.radius).toBe(2500);
    expect(zone.shrinkIntervalMinutes).toBe(3);
    expect(zone.shrinkMetersPerUpdate).toBe(75);
    expect(zone.driftSeed).toBe(-12345);
  });

  test("gameCode is the first 6 chars of the gameId, uppercased", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      UID,
      "abc123XYZ456",
    );
    expect((out as { gameCode: string }).gameCode).toBe("ABC123");
  });

  test("id field on the doc matches the gameId argument", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      UID,
      "abc123XYZ456",
    );
    expect((out as { id: string }).id).toBe("abc123XYZ456");
  });

  test("creatorId matches the uid argument", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      "uid-xyz",
      GAME_ID,
    );
    expect((out as { creatorId: string }).creatorId).toBe("uid-xyz");
  });

  test("powerUps.activeEffects starts empty (no lingering state from payload)", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(
        validPayload({ powerUps: { enabled: true, enabledTypes: ["radarPing", "zoneFreeze"] } }),
      ),
      UID,
      GAME_ID,
    );
    const powerUps = (out as { powerUps: { activeEffects: object } }).powerUps;
    expect(powerUps.activeEffects).toEqual({});
  });

  test("hunterIds and winners start as empty arrays (never carried from payload)", () => {
    const out = materialiseGameDoc(
      sanitiseGamePayload(validPayload()),
      UID,
      GAME_ID,
    );
    expect((out as { hunterIds: string[]; winners: unknown[] }).hunterIds).toEqual([]);
    expect((out as { hunterIds: string[]; winners: unknown[] }).winners).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Boundary lat/lng that must survive the round-trip. These are the values
// that caught the most bugs in similar projects, the equator, prime meridian,
// poles, dateline, and unusual negative / southern-hemisphere coords.
// ---------------------------------------------------------------------------

describe("materialiseGameDoc — boundary coordinates round-trip", () => {
  const UID = "user-1";
  const GAME_ID = "abc123";

  const BOUNDARY_CASES: { label: string; latitude: number; longitude: number }[] = [
    { label: "null island", latitude: 0, longitude: 0 },
    { label: "north pole", latitude: 90, longitude: 0 },
    { label: "south pole", latitude: -90, longitude: 0 },
    { label: "IDL east", latitude: 0, longitude: 180 },
    { label: "IDL west", latitude: 0, longitude: -180 },
    { label: "Brussels (our default)", latitude: 50.8503, longitude: 4.3517 },
    { label: "Sydney (southern + eastern hemisphere)", latitude: -33.868, longitude: 151.2093 },
    { label: "Easter Island (remote Pacific)", latitude: -27.1127, longitude: -109.3497 },
    { label: "very small sub-degree precision", latitude: 0.000_001, longitude: -0.000_001 },
  ];

  for (const c of BOUNDARY_CASES) {
    test(`(${c.latitude}, ${c.longitude}) — ${c.label}`, () => {
      const out = materialiseGameDoc(
        sanitiseGamePayload(patchCenter({ latitude: c.latitude, longitude: c.longitude })),
        UID,
        GAME_ID,
      );
      const center = (out as { zone: { center: GeoPoint } }).zone.center;
      expect(center).toBeInstanceOf(GeoPoint);
      expect(center.latitude).toBe(c.latitude);
      expect(center.longitude).toBe(c.longitude);
    });
  }
});
