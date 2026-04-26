import { GeoPoint, Timestamp } from "firebase-admin/firestore";

/**
 * Pure server-side power-up spawn logic. Kept in its own module so the
 * functions can be unit-tested in isolation (no Firestore, no Mapbox).
 *
 * Mirrors the client logic in:
 *   - `ios/PouleParty/Components/GameLogic/PowerUpSpawnLogic.swift`
 *   - `ios/PouleParty/Components/GameLogic/GameTimerLogic.swift`
 *     (`interpolateZoneCenter`, `deterministicDriftCenter`)
 *   - `android/.../ui/gamelogic/PowerUpSpawnHelper.kt`
 *   - `android/.../ui/gamelogic/GameTimerHelper.kt`
 *
 * Any change here that affects spawn positions should be cross-referenced
 * against the client-side functions to keep parity debuggable.
 */

export interface SpawnedPowerUp {
  id: string;
  type: string;
  location: GeoPoint;
  spawnedAt: Timestamp;
}

// Power-up types that require position sharing (useless in stayInTheZone mode
// since the chicken doesn't broadcast its position in that mode).
export const POSITION_DEPENDENT_POWER_UPS = new Set([
  "invisibility",
  "decoy",
  "jammer",
]);

/**
 * Strips the position-dependent types from `enabledTypes` when the game is
 * in `stayInTheZone` mode. Mirrors the client's `filterEnabledTypes`.
 */
export function filterEnabledTypesServer(
  enabledTypes: string[],
  gameMode: string
): string[] {
  if (gameMode !== "stayInTheZone") return enabledTypes;
  return enabledTypes.filter((t) => !POSITION_DEPENDENT_POWER_UPS.has(t));
}

/**
 * Linearly interpolates the zone center from `initialCenter` toward
 * `finalCenter` based on how far the radius has shrunk.
 * Mirrors client `interpolateZoneCenter`. If `finalCenter` is undefined,
 * returns `initialCenter` unchanged.
 */
export function interpolateZoneCenterServer(
  initialCenter: { latitude: number; longitude: number },
  finalCenter: { latitude: number; longitude: number } | undefined,
  initialRadius: number,
  currentRadius: number
): { latitude: number; longitude: number } {
  if (!finalCenter || initialRadius <= 0) return initialCenter;
  const progress = Math.min(
    Math.max((initialRadius - currentRadius) / initialRadius, 0),
    1
  );
  return {
    latitude:
      initialCenter.latitude +
      progress * (finalCenter.latitude - initialCenter.latitude),
    longitude:
      initialCenter.longitude +
      progress * (finalCenter.longitude - initialCenter.longitude),
  };
}

/**
 * Extra meters carved out of the drift budget so floating-point error in
 * the meters ↔ degrees conversion can never push `finalCenter` outside
 * the new circle. The conversion is consistent (same cos(lat) used for
 * the offset and for the distance check), so 1 m is plenty.
 */
export const FINAL_CENTER_SAFETY_METERS = 1.0;

/**
 * Radius of the "final zone" the chicken sees as a green glow on the
 * map — the whole disk, not just its center, must stay inside every
 * drifted circle. Matches the hardcoded 50 m used by
 * `finalZoneGlowContent` on iOS and the Android equivalent in
 * `ChickenMapScreen`. Kept here (alongside the other drift
 * constants) so the drift algo and the UI can never disagree on
 * what "final zone" means.
 */
export const FINAL_ZONE_RADIUS_METERS = 50.0;

/** How many rejection-sampling attempts before falling back to the
 * deterministic "pull toward finalCenter by `delta`" point. Each
 * attempt costs one splitmix64 evaluation; 32 is plenty — the
 * rejection rate only gets high near game end where disk(C, delta)
 * sticks out past disk(F, r), and even at 50 % rejection 32 attempts
 * succeed with probability > 99.99 %. */
const MAX_DRIFT_ATTEMPTS = 32;

/** splitmix64 — 64-bit mix function matching iOS `seededRandom` in
 * `GameTimerLogic.swift` and Android `seededRandom` in
 * `GameTimerHelper.kt`. Returns a deterministic [0, 1) double for the
 * given `(seed, index)` pair. Uses BigInt because JS numbers can't
 * safely wrap 64-bit. */
function seededRandomServer(seed: number, index: number): number {
  const MASK = 0xffffffffffffffffn;
  const INT64_MAX = 0x7fffffffffffffffn;
  let z =
    (BigInt.asUintN(64, BigInt.asIntN(64, BigInt(seed))) +
      BigInt.asUintN(64, BigInt.asIntN(64, BigInt(index))) *
        0x9e3779b97f4a7c15n) &
    MASK;
  z = ((z ^ (z >> 30n)) * 0xbf58476d1ce4e5b9n) & MASK;
  z = ((z ^ (z >> 27n)) * 0x94d049bb133111ebn) & MASK;
  z = (z ^ (z >> 31n)) & MASK;
  return Number(z >> 1n) / Number(INT64_MAX);
}

/**
 * Deterministic drift center for stayInTheZone mode — mirrors the
 * client `deterministicDriftCenter`. Picks the zone center for one
 * shrink step as a pseudo-random point that simultaneously satisfies:
 *   A. `|candidate − basePoint| ≤ oldRadius − newRadius` → the new
 *      circle fits entirely inside `disk(basePoint, oldRadius)`.
 *   B. when [finalCenter] is provided,
 *      `|candidate − finalCenter| ≤ newRadius − FINAL_ZONE_RADIUS −
 *      safety` → the final-zone disk (50 m glow) fits entirely inside
 *      the drifted circle.
 *
 * Caller contract: [basePoint] is the **initial** zone center and
 * [oldRadius] is the **initial** zone radius — NOT the previous
 * drifted center. Every shrink's candidate is drawn independently
 * from `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − FINAL − safety)`,
 * so successive circles can overlap each other freely as long as
 * both constraints hold. This matches the product rules:
 *   1. no circle escapes the start zone,
 *   2. every circle contains the final zone,
 *   3. intermediate circles have no nesting constraint between them.
 *
 * Strategy: sample uniformly inside the *smaller* of disk A / disk B
 * and reject against the larger. When one disk contains the other,
 * the first sample is always valid. When they partially overlap, the
 * lens area / smaller-disk area ratio is usually > 10 %, so 32
 * splitmix64-seeded attempts succeed with overwhelming probability.
 * When rejection exhausts (adversarial game configs with final zone
 * near the edge of the start zone), fall back to a deterministic
 * point on the base→final line that always lies in the intersection
 * whenever the disks are not disjoint.
 */
export function deterministicDriftCenterServer(
  basePoint: { latitude: number; longitude: number },
  oldRadius: number,
  newRadius: number,
  driftSeed: number,
  finalCenter?: { latitude: number; longitude: number }
): { latitude: number; longitude: number } {
  // No shrink → no drift. Frozen zones and equal-radius calls
  // round-trip. A collapsed (≤ 0) radius skips drift too.
  if (newRadius >= oldRadius || newRadius <= 0) return basePoint;

  const rA = oldRadius - newRadius; // disk A: around basePoint
  const rB = finalCenter
    ? Math.max(0, newRadius - FINAL_ZONE_RADIUS_METERS - FINAL_CENTER_SAFETY_METERS)
    : Number.POSITIVE_INFINITY; // disk B: around finalCenter

  const metersPerDegreeLat = 111_320.0;
  const metersPerDegreeLng =
    111_320.0 * Math.cos((basePoint.latitude * Math.PI) / 180.0);

  const stepSeed = (driftSeed | 0) ^ (Math.floor(newRadius) | 0);

  // No final constraint → sample uniformly in disk A.
  if (!finalCenter) {
    const angle = seededRandomServer(stepSeed, 0) * 2 * Math.PI;
    const dist = rA * Math.sqrt(seededRandomServer(stepSeed, 1));
    return {
      latitude: basePoint.latitude + (dist * Math.sin(angle)) / metersPerDegreeLat,
      longitude: basePoint.longitude + (dist * Math.cos(angle)) / metersPerDegreeLng,
    };
  }

  // v = finalCenter − basePoint, in local flat-earth meters.
  const vx = (finalCenter.longitude - basePoint.longitude) * metersPerDegreeLng;
  const vy = (finalCenter.latitude - basePoint.latitude) * metersPerDegreeLat;
  const vLen = Math.sqrt(vx * vx + vy * vy);

  // Sample from the smaller disk, reject against the larger. When the
  // smaller is fully contained in the larger, every sample is valid
  // (the loop exits on the first iteration).
  const sampleFromA = rA <= rB;
  const sampleR = sampleFromA ? rA : rB;

  for (let k = 0; k < MAX_DRIFT_ATTEMPTS; k++) {
    const angle = seededRandomServer(stepSeed, 2 * k) * 2 * Math.PI;
    const dist = sampleR * Math.sqrt(seededRandomServer(stepSeed, 2 * k + 1));
    // Offset is expressed in the sampling disk's local frame.
    const ox = dist * Math.cos(angle);
    const oy = dist * Math.sin(angle);
    // Candidate offset from basePoint (the caller's frame).
    const cdx = sampleFromA ? ox : vx + ox;
    const cdy = sampleFromA ? oy : vy + oy;
    // Check: is candidate in the *other* disk?
    const checkDx = sampleFromA ? cdx - vx : cdx; // offset from checkCenter
    const checkDy = sampleFromA ? cdy - vy : cdy;
    const checkR = sampleFromA ? rB : rA;
    if (Math.sqrt(checkDx * checkDx + checkDy * checkDy) <= checkR) {
      return {
        latitude: basePoint.latitude + cdy / metersPerDegreeLat,
        longitude: basePoint.longitude + cdx / metersPerDegreeLng,
      };
    }
  }

  // Deterministic fallback: the point on the base→final line at
  // distance `min(rA, vLen)` from basePoint. This is in disk A by
  // construction; it's in disk B whenever the disks overlap
  // (`vLen ≤ rA + rB`), which the caller invariant guarantees as
  // long as the final zone fits inside the start zone.
  if (vLen > 0) {
    const pull = Math.min(rA, vLen);
    return {
      latitude: basePoint.latitude + ((vy / vLen) * pull) / metersPerDegreeLat,
      longitude: basePoint.longitude + ((vx / vLen) * pull) / metersPerDegreeLng,
    };
  }
  return basePoint;
}

/**
 * Deterministic power-up generation. Given identical
 * `(center, radius, count, driftSeed, batchIndex, enabledTypes)` returns
 * identical output.
 *
 * @param now optional timestamp used for `spawnedAt`. Defaults to
 *   `Timestamp.now()`. Exposed so tests can assert exact values.
 */
export function generatePowerUpsServer(
  center: { latitude: number; longitude: number },
  radius: number,
  count: number,
  driftSeed: number,
  batchIndex: number,
  enabledTypes: string[],
  now: Timestamp = Timestamp.now()
): SpawnedPowerUp[] {
  if (enabledTypes.length === 0 || count <= 0) return [];

  const result: SpawnedPowerUp[] = [];
  const baseSeed = driftSeed ^ (batchIndex * 7919);

  for (let i = 0; i < count; i++) {
    const itemSeed = Math.abs(baseSeed * 31 + i * 127);

    const angleSeed = Math.abs(itemSeed * 53 ^ (i * 97));
    const distSeed = Math.abs(itemSeed * 79 ^ (i * 151));

    const angle = ((angleSeed % 36000) / 36000.0) * 2.0 * Math.PI;
    const distFraction = (distSeed % 10000) / 10000.0;
    // sqrt for uniform area distribution, 0.85 factor keeps items inside zone.
    const distance = radius * 0.85 * Math.sqrt(distFraction);

    const metersPerDegreeLat = 111_320.0;
    const metersPerDegreeLng =
      111_320.0 * Math.cos((center.latitude * Math.PI) / 180.0);

    const dLat = (distance * Math.cos(angle)) / metersPerDegreeLat;
    const dLng = (distance * Math.sin(angle)) / metersPerDegreeLng;

    const lat = center.latitude + dLat;
    const lng = center.longitude + dLng;

    const type = enabledTypes[itemSeed % enabledTypes.length];
    const id = `pu-${batchIndex}-${i}-${Math.abs(itemSeed)}`;

    result.push({
      id,
      type,
      location: new GeoPoint(lat, lng),
      spawnedAt: now,
    });
  }

  return result;
}

/**
 * Haversine distance in meters between two lat/lng pairs. Shared helper —
 * used by road-snap validation to reject snaps that moved too far.
 */
export function haversineDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const r = 6_371_000.0;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) *
      Math.cos(toRad(lat2)) *
      Math.sin(dLon / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(a));
}
