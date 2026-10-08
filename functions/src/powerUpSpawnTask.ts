import { GeoPoint } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";
import {
  filterEnabledTypesServer,
  generatePowerUpsServer,
  SpawnedPowerUp,
} from "./powerUpSpawn";
import { snapToRoad } from "./mapbox";
import { selectActiveCircleIndex } from "./zoneCalculation";
import { EFFECT_DURATION_SECONDS } from "./powerUps";

export const MAPBOX_ACCESS_TOKEN = defineSecret("MAPBOX_ACCESS_TOKEN");

/**
 * Writes an already-generated batch of power-ups. Deterministic IDs make
 * re-runs idempotent.
 */
async function writePowerUpBatch(
  gameId: string,
  powerUps: SpawnedPowerUp[]
): Promise<void> {
  if (powerUps.length === 0) return;
  const batch = db().batch();
  const col = db().collection("games").doc(gameId).collection("powerUps");
  for (const pu of powerUps) {
    // merge: a task retry must not resurrect a power-up collected meanwhile.
    batch.set(col.doc(pu.id), {
      id: pu.id,
      type: pu.type,
      location: pu.location,
      spawnedAt: pu.spawnedAt,
    }, { merge: true });
  }
  await batch.commit();
}

/**
 * Generates, snaps and writes one batch of power-ups in the circle every
 * client renders at the time the batch fires.
 */
async function spawnBatchForGame(
  gameId: string,
  batchIndex: number,
  count: number,
  mapboxToken: string,
  idSalt?: number
): Promise<void> {
  const snap = await db().collection("games").doc(gameId).get();
  if (!snap.exists) {
    logger.warn("[spawn] game missing, skipping batch", { gameId, batchIndex });
    return;
  }
  const data = snap.data() as Record<string, unknown>;

  if (data.status === "done") {
    logger.info("[spawn] game is done, skipping batch", { gameId, batchIndex });
    return;
  }
  const endTimestamp = (data.timing as { end?: FirebaseFirestore.Timestamp } | undefined)?.end?.toDate();
  if (endTimestamp && endTimestamp <= new Date()) {
    logger.info("[spawn] game passed its end, skipping batch", { gameId, batchIndex });
    return;
  }

  const powerUps = data.powerUps as
    | { enabled?: boolean; enabledTypes?: string[] }
    | undefined;
  if (!powerUps?.enabled) return;
  const gameMode = (data.gameMode as string) ?? "followTheChicken";
  const enabledTypes = filterEnabledTypesServer(powerUps.enabledTypes ?? [], gameMode);
  if (enabledTypes.length === 0) return;

  const zone = data.zone as {
    driftSeed?: number;
    shrinkIntervalMinutes?: number;
  } | undefined;
  const driftSeed = zone?.driftSeed;
  if (typeof driftSeed !== "number") {
    logger.error("[spawn] game missing zone seed", { gameId, batchIndex });
    return;
  }

  const activeEffects = (data.powerUps as { activeEffects?: Record<string, FirebaseFirestore.Timestamp> } | undefined)?.activeEffects;
  const zoneFreezeExpiresAt = activeEffects?.zoneFreeze?.toDate();

  const scheduleSnap = await db()
    .collection("games").doc(gameId)
    .collection("zone").doc("schedule")
    .get();
  const scheduleData = scheduleSnap.data() as
    | { circles?: { order: number; radiusMeters: number; lat: number; lng: number }[] }
    | undefined;
  const circles = scheduleData?.circles;
  if (!circles || circles.length === 0) {
    logger.error("[spawn] game has no stored zone schedule", { gameId, batchIndex });
    return;
  }

  const spawnTiming = data.timing as { start?: FirebaseFirestore.Timestamp; actualStart?: FirebaseFirestore.Timestamp; headStartMinutes?: number } | undefined;
  const spawnStartMs = (spawnTiming?.actualStart ?? spawnTiming?.start)?.toMillis();
  const spawnHeadStartMinutes = spawnTiming?.headStartMinutes ?? 0;
  const spawnShrinkIntervalMinutes = zone?.shrinkIntervalMinutes ?? 5;
  const effectiveBatchIndex = spawnStartMs !== undefined
    ? selectActiveCircleIndex(
        spawnStartMs + spawnHeadStartMinutes * 60 * 1000,
        spawnShrinkIntervalMinutes,
        circles.length,
        zoneFreezeExpiresAt ? zoneFreezeExpiresAt.getTime() : null,
        EFFECT_DURATION_SECONDS.zoneFreeze ?? 0,
        Date.now()
      )
    : Math.min(batchIndex, circles.length - 1);
  const circle = circles[Math.min(effectiveBatchIndex, circles.length - 1)];
  const currentRadius = circle.radiusMeters;
  if (currentRadius <= 0) {
    logger.info("[spawn] zone radius is zero, skipping batch", { gameId, batchIndex });
    return;
  }

  let spawnCenter: { latitude: number; longitude: number };
  if (gameMode === "stayInTheZone") {
    spawnCenter = { latitude: circle.lat, longitude: circle.lng };
  } else {
    // followTheChicken: the zone tracks the chicken's live position.
    const locSnap = await getDatabase()
      .ref(`/games/${gameId}/chickenLocations/latest`)
      .get();
    const locData = locSnap.val() as { lat?: number; lng?: number } | null;
    if (locData && typeof locData.lat === "number" && typeof locData.lng === "number") {
      spawnCenter = { latitude: locData.lat, longitude: locData.lng };
    } else {
      spawnCenter = { latitude: circle.lat, longitude: circle.lng };
    }
  }

  const generated = generatePowerUpsServer(
    spawnCenter,
    currentRadius,
    count,
    driftSeed,
    batchIndex,
    enabledTypes
  );

  // QA debug spawns must not collide with the scheduled batch's doc IDs.
  if (idSalt !== undefined) {
    generated.forEach((pu, i) => {
      pu.id = `pu-debug-${idSalt}-${i}`;
    });
  }

  // Per-point fallback: a Mapbox failure keeps the raw coordinate instead of
  // dropping the whole batch.
  const snapResults = await Promise.allSettled(
    generated.map((pu) =>
      snapToRoad(pu.location.latitude, pu.location.longitude, mapboxToken)
    )
  );
  const snapped: SpawnedPowerUp[] = generated.map((pu, i) => {
    const result = snapResults[i];
    if (result.status === "fulfilled") {
      return { ...pu, location: new GeoPoint(result.value.latitude, result.value.longitude) };
    }
    logger.warn("[spawn] snap failed, using raw coordinate", {
      gameId,
      powerUpId: pu.id,
      reason: result.reason instanceof Error ? result.reason.message : String(result.reason),
    });
    return pu;
  });

  await writePowerUpBatch(gameId, snapped);
  logger.info("[spawn] batch written", { gameId, batchIndex, count: snapped.length });
}

export const spawnPowerUpBatch = onTaskDispatched(
  {
    region: REGION,
    retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 50 },
    secrets: [MAPBOX_ACCESS_TOKEN],
  },
  async (req) => {
    const { gameId, batchIndex, count, idSalt } = req.data as {
      gameId: string;
      batchIndex: number;
      count: number;
      idSalt?: number;
    };
    await spawnBatchForGame(gameId, batchIndex, count, MAPBOX_ACCESS_TOKEN.value(), idSalt);
  }
);
