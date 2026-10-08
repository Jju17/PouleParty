import { Timestamp } from "firebase-admin/firestore";
import {
  deterministicDriftCenterServer,
  generatePowerUpsServer,
  haversineDistance,
  interpolateZoneCenterServer,
} from "../../src/powerUpSpawn";
import { calculateNormalModeSettingsServer } from "../../src/zoneCalculation";

type LatLng = { latitude: number; longitude: number };

const MASK64 = (1n << 64n) - 1n;

/** Reference splitmix64 stream, the one both mobile pickers must reproduce. */
function splitmix64(seed: number): () => bigint {
  let state = BigInt.asUintN(64, BigInt(seed === 0 ? 1 : seed));
  return () => {
    state = (state + 0x9e3779b97f4a7c15n) & MASK64;
    let z = state;
    z = ((z ^ (z >> 30n)) * 0xbf58476d1ce4e5b9n) & MASK64;
    z = ((z ^ (z >> 27n)) * 0x94d049bb133111ebn) & MASK64;
    return z ^ (z >> 31n);
  };
}

/** Reference of the mobile wizard's initial center picker. */
export function pickInitialZoneCenterReference(start: LatLng, final: LatLng, radius: number, seed: number): LatLng {
  const distance = haversineDistance(start.latitude, start.longitude, final.latitude, final.longitude);
  const midLat = (start.latitude + final.latitude) / 2;
  const midLng = (start.longitude + final.longitude) / 2;
  const maxOffset = Math.max(0, radius - distance / 2);
  const next = splitmix64(seed);
  const max = Number(MASK64);
  const angle = (Number(next()) / max) * 2 * Math.PI;
  const mag = Math.sqrt(Number(next()) / max) * maxOffset;
  const dLat = (mag * Math.sin(angle)) / 111_111;
  const cosLat = Math.cos((midLat * Math.PI) / 180);
  const dLng = cosLat === 0 ? 0 : (mag * Math.cos(angle)) / (111_111 * cosLat);
  return { latitude: midLat + dLat, longitude: midLng + dLng };
}

const BRUSSELS: LatLng = { latitude: 50.8466, longitude: 4.3528 };
const IXELLES: LatLng = { latitude: 50.8333, longitude: 4.3667 };
const PARIS: LatLng = { latitude: 48.8566, longitude: 2.3522 };
const SEEDS = [7, 42, 12345, 2_000_000_000, -2_000_000_000, 2_147_483_647];
const FIXED_NOW = Timestamp.fromMillis(1_800_000_000_000);

/** Every vector the three platforms must reproduce, computed from the server code. */
export function buildParityVectors() {
  return {
    distance: [
      [BRUSSELS, IXELLES],
      [BRUSSELS, PARIS],
      [BRUSSELS, BRUSSELS],
    ].map(([a, b]) => ({
      from: a,
      to: b,
      meters: haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude),
    })),
    normalModeSettings: [
      [1500, 120],
      [2000, 45],
      [500, 0],
    ].map(([initialRadius, gameDurationMinutes]) => ({
      initialRadius,
      gameDurationMinutes,
      ...calculateNormalModeSettingsServer(initialRadius, gameDurationMinutes),
    })),
    interpolateZoneCenter: [
      [1500, 1500],
      [1500, 750],
      [1500, 50],
      [1500, 0],
    ].map(([initialRadius, currentRadius]) => ({
      initialCenter: BRUSSELS,
      finalCenter: IXELLES,
      initialRadius,
      currentRadius,
      result: interpolateZoneCenterServer(BRUSSELS, IXELLES, initialRadius, currentRadius),
    })),
    deterministicDriftCenter: SEEDS.flatMap((seed) =>
      [
        [1500, 1400, undefined],
        [1500, 1400, IXELLES],
        [800, 300, IXELLES],
      ].map(([oldRadius, newRadius, finalCenter]) => ({
        basePoint: BRUSSELS,
        oldRadius: oldRadius as number,
        newRadius: newRadius as number,
        driftSeed: seed,
        finalCenter: (finalCenter as LatLng | undefined) ?? null,
        result: deterministicDriftCenterServer(
          BRUSSELS,
          oldRadius as number,
          newRadius as number,
          seed,
          finalCenter as LatLng | undefined
        ),
      }))
    ),
    generatePowerUps: SEEDS.flatMap((seed) =>
      [0, 3].map((batchIndex) => {
        const enabledTypes = ["zoneFreeze", "zonePreview", "radarPing", "invisibility"];
        return {
          center: BRUSSELS,
          radius: 1200,
          count: 4,
          driftSeed: seed,
          batchIndex,
          enabledTypes,
          result: generatePowerUpsServer(BRUSSELS, 1200, 4, seed, batchIndex, enabledTypes, FIXED_NOW).map((pu) => ({
            id: pu.id,
            type: pu.type,
            latitude: pu.location.latitude,
            longitude: pu.location.longitude,
          })),
        };
      })
    ),
    pickInitialZoneCenter: SEEDS.map((seed) => ({
      startPin: BRUSSELS,
      finalCenter: IXELLES,
      radius: 2500,
      seed,
      result: pickInitialZoneCenterReference(BRUSSELS, IXELLES, 2500, seed),
    })),
  };
}
