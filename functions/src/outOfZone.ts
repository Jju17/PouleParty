import { Timestamp } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";
import { huntersOf } from "./roles";
import { haversineDistance } from "./powerUpSpawn";
import { selectActiveCircleIndex } from "./zoneCalculation";
import { EFFECT_DURATION_SECONDS } from "./powerUps";
import { enqueueTask } from "./lifecycleTasks";
import { applyPenaltyPoints } from "./validation";

export const OUT_OF_ZONE_PENALTY_INTERVAL_MS = 5_000;
export const OUT_OF_ZONE_CHECK_PERIOD_MS = 30_000;
const POSITION_FRESHNESS_MS = 60_000;
const MAX_CHECKS = 2_000;

export interface ServerPenaltyDecision {
  points: number;
  newLastPenaltyAtMs: number | null;
}

/**
 * Server backstop for the client penalty: -1 point per full interval spent
 * outside the zone since the last recorded penalty. A missing or stale window
 * only opens a new one, so a hunter is never charged for time it was not
 * observed outside.
 */
export function decideServerPenalty(
  isOutside: boolean,
  lastPenaltyAtMs: number | null,
  nowMs: number,
  intervalMs = OUT_OF_ZONE_PENALTY_INTERVAL_MS,
  checkPeriodMs = OUT_OF_ZONE_CHECK_PERIOD_MS
): ServerPenaltyDecision {
  if (!isOutside) return { points: 0, newLastPenaltyAtMs: null };
  const windowIsFresh =
    lastPenaltyAtMs !== null &&
    nowMs >= lastPenaltyAtMs &&
    nowMs - lastPenaltyAtMs <= checkPeriodMs + intervalMs;
  if (!windowIsFresh) return { points: 0, newLastPenaltyAtMs: nowMs };
  const maxPoints = Math.ceil(checkPeriodMs / intervalMs) + 1;
  const points = Math.min(maxPoints, Math.floor((nowMs - lastPenaltyAtMs) / intervalMs));
  if (points === 0) return { points: 0, newLastPenaltyAtMs: null };
  return { points, newLastPenaltyAtMs: lastPenaltyAtMs + points * intervalMs };
}

export function isZoneCheckedForHunters(gameMode: string): boolean {
  return gameMode === "stayInTheZone" || gameMode === "followTheChicken";
}

interface Circle {
  radiusMeters: number;
  lat: number;
  lng: number;
}

async function activeZone(
  gameId: string,
  game: FirebaseFirestore.DocumentData,
  nowMs: number
): Promise<{ lat: number; lng: number; radius: number } | null> {
  const schedule = await db().collection("games").doc(gameId).collection("zone").doc("schedule").get();
  const circles = (schedule.data()?.circles as Circle[] | undefined) ?? [];
  if (circles.length === 0) return null;
  const timing = game.timing as { start?: Timestamp; actualStart?: Timestamp; headStartMinutes?: number } | undefined;
  const startMs = (timing?.actualStart ?? timing?.start)?.toMillis();
  if (startMs === undefined) return null;
  const freezeEnd = (game.powerUps?.activeEffects?.zoneFreeze as Timestamp | undefined)?.toMillis() ?? null;
  const index = selectActiveCircleIndex(
    startMs + (timing?.headStartMinutes ?? 0) * 60_000,
    (game.zone?.shrinkIntervalMinutes as number | undefined) ?? 5,
    circles.length,
    freezeEnd,
    EFFECT_DURATION_SECONDS.zoneFreeze ?? 0,
    nowMs
  );
  const circle = circles[index];
  if (game.gameMode === "stayInTheZone") {
    return { lat: circle.lat, lng: circle.lng, radius: circle.radiusMeters };
  }
  const chicken = (await getDatabase().ref(`/games/${gameId}/chickenLocations/latest`).get()).val() as
    | { lat?: number; lng?: number }
    | null;
  if (typeof chicken?.lat !== "number" || typeof chicken?.lng !== "number") return null;
  return { lat: chicken.lat, lng: chicken.lng, radius: circle.radiusMeters };
}

export const evaluateOutOfZone = onTaskDispatched(
  {
    region: REGION,
    retryConfig: { maxAttempts: 2, minBackoffSeconds: 5 },
    rateLimits: { maxConcurrentDispatches: 50 },
  },
  async (req) => {
    const { gameId, step, idSuffix } = req.data as { gameId: string; step: number; idSuffix?: string };
    const gameSnap = await db().collection("games").doc(gameId).get();
    const game = gameSnap.data();
    if (!game || game.status === "done") return;
    const nowMs = Date.now();
    const endMs = (game.timing?.end as Timestamp | undefined)?.toMillis();
    if (endMs !== undefined && endMs <= nowMs) return;

    if (game.status === "inProgress" && isZoneCheckedForHunters(String(game.gameMode))) {
      const zone = await activeZone(gameId, game, nowMs);
      if (zone) {
        const positions = ((await getDatabase().ref(`/games/${gameId}/hunterLocations`).get()).val() ?? {}) as Record<
          string,
          { lat?: number; lng?: number; ts?: number }
        >;
        for (const hunterId of huntersOf(game)) {
          const pos = positions[hunterId];
          if (typeof pos?.lat !== "number" || typeof pos?.lng !== "number") continue;
          if (typeof pos.ts !== "number" || nowMs - pos.ts > POSITION_FRESHNESS_MS) continue;
          const isOutside = haversineDistance(pos.lat, pos.lng, zone.lat, zone.lng) > zone.radius;
          if (!isOutside) continue;
          try {
            await applyPenaltyPoints(gameId, hunterId, (lastPenaltyAtMs) =>
              decideServerPenalty(true, lastPenaltyAtMs, nowMs)
            );
          } catch (err) {
            logger.error("[outOfZone] penalty write failed", { gameId, hunterId, error: String(err) });
          }
        }
      }
    }

    if (step + 1 >= MAX_CHECKS) return;
    const nextId = `ooz-${gameId}${idSuffix ?? ""}-${step + 1}`;
    await enqueueTask({
      queue: "evaluateOutOfZone",
      id: nextId,
      scheduleTime: new Date(nowMs + OUT_OF_ZONE_CHECK_PERIOD_MS),
      payload: { gameId, step: step + 1, idSuffix: idSuffix ?? "" },
    });
  }
);
