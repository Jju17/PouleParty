import { onCall, HttpsError } from "firebase-functions/v2/https";
import {
  deterministicDriftCenterServer,
  haversineDistance,
} from "./powerUpSpawn";

/**
 * PP-69 — central zone-configuration calculator.
 *
 * Single source of truth for the zone-creation wizard math: the client
 * passes the two pins + game mode + duration, the function returns the
 * fully computed zone (initial radius, validated final pin, drift seed,
 * shrink schedule, intermediate circles) so every platform draws the
 * same picture.
 *
 * Phase 1 (PP-13 / PP-14) shipped client mirrors of `computeZoneRadius`,
 * `interpolateZoneCenter`, `deterministicDriftCenter` so the recap step
 * could render today. Phase 2 deletes those mirrors and routes everyone
 * through this CF, with the wizard helpers staying alive only as
 * cross-platform parity references.
 *
 * Inputs / outputs / formulas are described inline; the iOS + Android
 * client wrappers mirror this contract field-for-field.
 */

const REGION = "europe-west1";

// Constants — kept identical to the iOS / Android mirrors so any client
// that still runs the old helpers (during the PP-13 phase-2 rollout
// window) produces the exact same number.
const FINAL_ZONE_RADIUS = 50; // meters
const INTERIOR_MARGIN = 200; // meters
const MINIMUM_INITIAL_RADIUS = 800; // meters
const ALLOWED_FTC_RADII: ReadonlyArray<number> = [500, 1000, 2000];
const NORMAL_MODE_FIXED_INTERVAL = 5; // minutes — mirrors AppConstants.normalModeFixedInterval
const NORMAL_MODE_MINIMUM_RADIUS = 100; // meters — mirrors AppConstants.normalModeMinimumRadius

interface LatLng {
  lat: number;
  lng: number;
}

interface ZoneCircle {
  radiusMeters: number;
  center: LatLng;
}

interface ComputeZoneConfigurationInput {
  startPoint?: LatLng | null;
  finalPoint?: LatLng | null;
  gameMode?: string;
  radiusHint?: number | null;
  gameDurationMinutes?: number | null;
  forceNewSeed?: boolean;
  /**
   * Optional explicit seed — exposed so a client that already holds a
   * `Game.zone.driftSeed` (e.g. recap step re-fetch) can re-derive the
   * same shrink schedule deterministically. When omitted the function
   * derives a stable seed from the inputs themselves.
   */
  existingSeed?: number | null;
}

interface ComputeZoneConfigurationOutput {
  initialRadius: number;
  validatedFinal: LatLng | null;
  driftSeed: number;
  finalZoneRadius: number;
  interiorMargin: number;
  shrinkIntervalMinutes: number;
  shrinkMetersPerUpdate: number;
  circles: ZoneCircle[];
}

function ensureLatLng(value: unknown, fieldName: string): LatLng {
  if (
    typeof value !== "object" ||
    value === null ||
    typeof (value as { lat?: unknown }).lat !== "number" ||
    typeof (value as { lng?: unknown }).lng !== "number"
  ) {
    throw new HttpsError(
      "invalid-argument",
      `${fieldName} must be { lat: number, lng: number }`
    );
  }
  const lat = (value as { lat: number }).lat;
  const lng = (value as { lng: number }).lng;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    throw new HttpsError(
      "invalid-argument",
      `${fieldName} contains non-finite coordinates`
    );
  }
  if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    throw new HttpsError(
      "invalid-argument",
      `${fieldName} coordinates out of range`
    );
  }
  return { lat, lng };
}

function ensureGameMode(value: unknown): "stayInTheZone" | "followTheChicken" {
  if (value !== "stayInTheZone" && value !== "followTheChicken") {
    throw new HttpsError(
      "invalid-argument",
      "gameMode must be 'stayInTheZone' or 'followTheChicken'"
    );
  }
  return value;
}

function ensureDuration(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
    throw new HttpsError(
      "invalid-argument",
      "gameDurationMinutes must be a positive number"
    );
  }
  return value;
}

/**
 * Mirrors iOS `calculateNormalModeSettings` and Android
 * `calculateNormalModeSettings`. Returns the fixed 5-minute shrink
 * interval and the matching per-shrink decline so the zone collapses
 * to `NORMAL_MODE_MINIMUM_RADIUS` exactly at the end of the game.
 *
 * Char-for-char identical formula (parity rule from CLAUDE.md):
 *   numberOfShrinks = duration / interval
 *   declinePerUpdate = (R₀ − minRadius) / numberOfShrinks  (floored at 0)
 *
 * When `numberOfShrinks <= 0`, decline is 0 (game too short to shrink).
 */
export function calculateNormalModeSettingsServer(
  initialRadius: number,
  gameDurationMinutes: number
): { interval: number; decline: number } {
  const numberOfShrinks = gameDurationMinutes / NORMAL_MODE_FIXED_INTERVAL;
  if (numberOfShrinks <= 0) {
    return { interval: NORMAL_MODE_FIXED_INTERVAL, decline: 0 };
  }
  const declinePerUpdate =
    (initialRadius - NORMAL_MODE_MINIMUM_RADIUS) / numberOfShrinks;
  return {
    interval: NORMAL_MODE_FIXED_INTERVAL,
    decline: Math.max(0, declinePerUpdate),
  };
}

/**
 * Computes the initial zone radius. Mirrors `computeZoneRadius` on iOS
 * (`ios/.../GameSettings.swift`) and Android (`.../model/GameSettings.kt`).
 *
 * - `stayInTheZone`: `max(D × 1.5, D + FINAL + INTERIOR_MARGIN, 800)` with
 *   `D = haversine(start, final)`. No max bound on D — bike / car games
 *   produce legitimately large radii.
 * - `followTheChicken`: validated `radiusHint` from {500, 1000, 2000}.
 */
function computeInitialRadius(
  gameMode: "stayInTheZone" | "followTheChicken",
  start: LatLng,
  finalPoint: LatLng | null,
  radiusHint: number | null
): number {
  if (gameMode === "followTheChicken") {
    if (radiusHint === null || !ALLOWED_FTC_RADII.includes(radiusHint)) {
      throw new HttpsError(
        "invalid-argument",
        "radiusHint must be 500, 1000, or 2000 in followTheChicken mode"
      );
    }
    return radiusHint;
  }
  // stayInTheZone
  if (finalPoint === null) {
    throw new HttpsError(
      "invalid-argument",
      "finalPoint is required in stayInTheZone mode"
    );
  }
  const D = haversineDistance(start.lat, start.lng, finalPoint.lat, finalPoint.lng);
  return Math.max(
    D * 1.5,
    D + FINAL_ZONE_RADIUS + INTERIOR_MARGIN,
    MINIMUM_INITIAL_RADIUS
  );
}

/**
 * Derives a deterministic seed from the inputs when `forceNewSeed` is
 * false and no `existingSeed` was provided. Same inputs → same seed,
 * so two clients hitting the function back-to-back agree on the shrink
 * schedule. Always returns a strictly positive Int32 because the
 * runtime PRNG treats 0 as "no drift".
 */
function deriveSeedFromInputs(
  start: LatLng,
  finalPoint: LatLng | null,
  initialRadius: number,
  gameDurationMinutes: number
): number {
  // Mix the inputs into a single 32-bit signed integer via a cheap
  // hash. The exact bit-pattern doesn't matter — it just has to be
  // (a) deterministic across calls with the same inputs and (b)
  // sensitive enough to inputs that two nearby pins produce different
  // schedules.
  const buffer = [
    start.lat,
    start.lng,
    finalPoint?.lat ?? 0,
    finalPoint?.lng ?? 0,
    initialRadius,
    gameDurationMinutes,
  ];
  let h = 0x811c9dc5 | 0; // FNV-1a 32-bit seed
  for (const v of buffer) {
    // Multiply by a large prime then XOR with the bit-pattern of the
    // double. We round to micro-degree precision so floating-point
    // noise on the inputs doesn't reshuffle the seed.
    const scaled = Math.round(v * 1_000_000);
    // 32-bit truncation on every step keeps the result deterministic
    // across platforms that might widen Number to BigInt.
    h = Math.imul(h ^ scaled, 16777619) | 0;
  }
  // Force strictly positive — the PRNG and drift code treat 0 as
  // "no drift" and we don't want callers to land there by accident.
  const positive = Math.abs(h);
  return positive === 0 ? 1 : positive;
}

/**
 * Picks a fresh strictly-positive 32-bit seed using `crypto.randomInt`
 * so the Shuffle button (PP-14 phase 2) gets a genuinely random new
 * schedule on every press.
 */
function freshRandomSeed(): number {
  // Use Math.random — runs in Cloud Functions Node 22 just fine and
  // avoids pulling in `crypto` only for this one helper. The PRNG is
  // not cryptographically sensitive (the player can read the seed
  // off the Game doc anyway); we just need "different on every press".
  const seed = Math.floor(Math.random() * 0x7fffffff);
  return seed === 0 ? 1 : seed;
}

/**
 * Computes the intermediate circles the zone will pass through, one
 * entry per shrink step, from `initialRadius` down to `finalZoneRadius`.
 * Used by the wizard recap (PP-13) so the client doesn't have to
 * re-walk the drift algorithm — the CF is now the single source of
 * truth.
 *
 * Step-by-step:
 *   - Step 0 is the initial circle (no drift, raw `start` center).
 *   - Subsequent steps decrement by `shrinkMetersPerUpdate` and, in
 *     `stayInTheZone`, drift the center via `deterministicDriftCenter`
 *     keyed by `(driftSeed, newRadius)`. In `followTheChicken` the
 *     center stays at `start` (the live chicken position drives the
 *     real zone at runtime).
 *   - Stops when the next step would drop at or below `FINAL_ZONE_RADIUS`.
 *     The final entry has `radiusMeters = max(FINAL_ZONE_RADIUS,
 *     candidateRadius)` so the recap always renders a non-degenerate
 *     final circle.
 */
function computeShrinkSchedule(
  gameMode: "stayInTheZone" | "followTheChicken",
  start: LatLng,
  finalPoint: LatLng | null,
  initialRadius: number,
  shrinkMetersPerUpdate: number,
  driftSeed: number
): ZoneCircle[] {
  const circles: ZoneCircle[] = [
    { radiusMeters: initialRadius, center: { lat: start.lat, lng: start.lng } },
  ];
  // No shrinking scheduled (game shorter than one interval): the recap
  // shows the initial circle alone.
  if (shrinkMetersPerUpdate <= 0) return circles;

  let radius = initialRadius;
  // Hard cap on iterations so a malformed input (NaN-resistant: the
  // guard above plus the `shrinkMetersPerUpdate <= 0` short-circuit
  // catch any pathology) can't run away.
  const MAX_STEPS = 1000;
  let step = 0;
  while (step < MAX_STEPS) {
    const next = radius - shrinkMetersPerUpdate;
    if (next <= FINAL_ZONE_RADIUS) {
      // Final step: clamp to the final-zone disc size so the recap
      // never renders a circle smaller than the final-zone glow.
      if (radius > FINAL_ZONE_RADIUS) {
        const center =
          gameMode === "stayInTheZone" && finalPoint
            ? finalPoint
            : { lat: start.lat, lng: start.lng };
        circles.push({
          radiusMeters: FINAL_ZONE_RADIUS,
          center,
        });
      }
      break;
    }
    radius = next;
    let center: LatLng;
    if (gameMode === "stayInTheZone") {
      const drifted = deterministicDriftCenterServer(
        { latitude: start.lat, longitude: start.lng },
        initialRadius,
        radius,
        driftSeed,
        finalPoint
          ? { latitude: finalPoint.lat, longitude: finalPoint.lng }
          : undefined
      );
      center = { lat: drifted.latitude, lng: drifted.longitude };
    } else {
      // followTheChicken: live center is the chicken's GPS at runtime;
      // the recap previews the schedule centered on the start pin.
      center = { lat: start.lat, lng: start.lng };
    }
    circles.push({ radiusMeters: radius, center });
    step++;
  }
  return circles;
}

/**
 * Pure-function core of `computeZoneConfiguration`. Exposed so unit
 * tests can exercise every validation branch and the formula directly,
 * without spinning up the `onCall` HTTP wrapper.
 *
 * Throws `HttpsError(invalid-argument)` on any input failure — the
 * callable below just forwards `request.data` here.
 */
export function computeZoneConfigurationCore(
  input: ComputeZoneConfigurationInput
): ComputeZoneConfigurationOutput {
  const start = ensureLatLng(input.startPoint, "startPoint");
  const gameMode = ensureGameMode(input.gameMode);
  const gameDurationMinutes = ensureDuration(input.gameDurationMinutes);

  const finalPoint =
    input.finalPoint !== null && input.finalPoint !== undefined
      ? ensureLatLng(input.finalPoint, "finalPoint")
      : null;
  const radiusHint =
    input.radiusHint !== null && input.radiusHint !== undefined
      ? input.radiusHint
      : null;

  const initialRadius = computeInitialRadius(
    gameMode,
    start,
    finalPoint,
    radiusHint
  );

  // stayInTheZone always returns finalPoint; followTheChicken returns
  // null so the client doesn't render a stale glow on the chicken map.
  const validatedFinal = gameMode === "stayInTheZone" ? finalPoint : null;

  // Pick the seed.
  let driftSeed: number;
  if (input.forceNewSeed === true) {
    driftSeed = freshRandomSeed();
  } else if (
    typeof input.existingSeed === "number" &&
    Number.isFinite(input.existingSeed) &&
    input.existingSeed > 0
  ) {
    driftSeed = Math.floor(input.existingSeed);
  } else {
    driftSeed = deriveSeedFromInputs(
      start,
      finalPoint,
      initialRadius,
      gameDurationMinutes
    );
  }

  const { interval, decline } = calculateNormalModeSettingsServer(
    initialRadius,
    gameDurationMinutes
  );

  const circles = computeShrinkSchedule(
    gameMode,
    start,
    finalPoint,
    initialRadius,
    decline,
    driftSeed
  );

  return {
    initialRadius,
    validatedFinal,
    driftSeed,
    finalZoneRadius: FINAL_ZONE_RADIUS,
    interiorMargin: INTERIOR_MARGIN,
    shrinkIntervalMinutes: interval,
    shrinkMetersPerUpdate: decline,
    circles,
  };
}

/**
 * `computeZoneConfiguration` — the PP-69 callable. See file header for
 * the contract; throws `HttpsError(invalid-argument)` for every input
 * validation failure.
 */
export const computeZoneConfiguration = onCall<
  ComputeZoneConfigurationInput,
  Promise<ComputeZoneConfigurationOutput>
>({ region: REGION }, async (request) => {
  return computeZoneConfigurationCore(request.data ?? {});
});

// Re-export the helpers so tests can pin their parity invariants
// against the iOS / Android mirrors directly.
export type {
  ComputeZoneConfigurationInput,
  ComputeZoneConfigurationOutput,
  LatLng,
  ZoneCircle,
};
