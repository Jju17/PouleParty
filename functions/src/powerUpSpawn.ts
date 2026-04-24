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
 * Deterministic drift center for stayInTheZone mode — ports
 * `deterministicDriftCenter` from the client. Drifts [basePoint] by a
 * seeded offset bounded by:
 *   - `newRadius * 0.5` of basePoint (so basePoint stays well inside)
 *   - `(oldRadius - newRadius) * 0.5` so successive shrinks don't lurch
 *   - (when [finalCenter] is provided) `newRadius − dist(base, finalCenter) − margin`
 *     so the final collapse point is ALWAYS inside the drifted circle,
 *     regardless of how close `finalCenter` was chosen to the edge of
 *     the initial zone.
 *
 * The third bound is what makes the geometric invariant
 * `finalCenter ∈ circle(center_i, radius_i)` hold by construction at
 * every shrink step — the first two alone don't guarantee it once
 * `|initialCenter - finalCenter|` grows past `initialRadius / 2`.
 */
export function deterministicDriftCenterServer(
  basePoint: { latitude: number; longitude: number },
  oldRadius: number,
  newRadius: number,
  driftSeed: number,
  finalCenter?: { latitude: number; longitude: number }
): { latitude: number; longitude: number } {
  const metersPerDegreeLat = 111_320.0;
  const metersPerDegreeLng =
    111_320.0 * Math.cos((basePoint.latitude * Math.PI) / 180.0);

  const maxFromBase = newRadius * 0.5;
  const maxFromPrev = Math.max(0, oldRadius - newRadius) * 0.5;

  let maxFromFinal = Number.POSITIVE_INFINITY;
  if (finalCenter) {
    const dLatM = (finalCenter.latitude - basePoint.latitude) * metersPerDegreeLat;
    const dLngM = (finalCenter.longitude - basePoint.longitude) * metersPerDegreeLng;
    const distBaseFinal = Math.sqrt(dLatM * dLatM + dLngM * dLngM);
    maxFromFinal = Math.max(0, newRadius - distBaseFinal - FINAL_CENTER_SAFETY_METERS);
  }

  const safeDrift = Math.min(maxFromBase, maxFromPrev, maxFromFinal);
  if (safeDrift <= 0) return basePoint;

  // Bitwise ops in JS are 32-bit. Values stay well below 2^31 for normal inputs.
  const angleSeed = Math.abs(driftSeed * 31 ^ Math.floor(newRadius));
  const distSeed = Math.abs(driftSeed * 127 ^ (Math.floor(newRadius) * 37));

  const angle = ((angleSeed % 36000) / 36000.0) * 2.0 * Math.PI;
  const distFraction = (distSeed % 10000) / 10000.0;
  const distance = safeDrift * Math.sqrt(distFraction);

  return {
    latitude:
      basePoint.latitude + (distance * Math.cos(angle)) / metersPerDegreeLat,
    longitude:
      basePoint.longitude + (distance * Math.sin(angle)) / metersPerDegreeLng,
  };
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
