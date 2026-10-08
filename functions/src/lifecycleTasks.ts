import { getFunctions } from "firebase-admin/functions";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";

export const POWER_UP_INITIAL_BATCH_SIZE = 5;
export const POWER_UP_PERIODIC_BATCH_SIZE = 2;
export const MAX_POWER_UP_SHRINK_BATCHES = 100;
export const MAX_SHRINK_NOTIFICATIONS = 100;

/**
 * Task handler: transitions a game's status if it's still in the expected state.
 */
export const transitionGameStatus = onTaskDispatched(
  {
    region: REGION,
    retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 100 },
  },
  async (req) => {
    const { gameId, targetStatus, expectedCurrentStatus } = req.data as {
      gameId: string;
      targetStatus: string;
      expectedCurrentStatus: string;
    };

    const ref = db().collection("games").doc(gameId);
    // Compare-and-set: a concurrent client cancel must never be clobbered
    // back to the target status.
    await db().runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) {
        logger.info("[transitionGameStatus] doc missing", { gameId });
        return;
      }
      const currentStatus = snap.data()?.status;
      if (currentStatus === expectedCurrentStatus) {
        tx.update(ref, { status: targetStatus });
        logger.info("[transitionGameStatus] applied", { gameId, from: currentStatus, to: targetStatus });
      } else {
        logger.info("[transitionGameStatus] skipped", {
          gameId,
          current: currentStatus,
          expected: expectedCurrentStatus,
        });
      }
    });
  }
);

/**
 * Enqueues all lifecycle Cloud Tasks (status transitions, notifications,
 * power-up batches) for a game that is ready to be played.
 *
 * Returns true if tasks were scheduled, false if the game was rejected due
 * to a validation error (timing past, etc.).
 */
export async function scheduleGameLifecycleTasks(
  gameId: string,
  data: FirebaseFirestore.DocumentData,
): Promise<boolean> {
  const timing = data.timing as { start?: FirebaseFirestore.Timestamp; end?: FirebaseFirestore.Timestamp; headStartMinutes?: number } | undefined;
  const zone = data.zone as { shrinkIntervalMinutes?: number } | undefined;

  const startTimestamp = timing?.start?.toDate() as Date | undefined;
  const endTimestamp = timing?.end?.toDate() as Date | undefined;
  const headStartMinutes = (timing?.headStartMinutes as number) ?? 0;
  const shrinkIntervalMinutes = (zone?.shrinkIntervalMinutes as number) ?? 5;

  if (shrinkIntervalMinutes < 1) {
    logger.error("[schedule] invalid shrinkIntervalMinutes", { gameId, shrinkIntervalMinutes });
    return false;
  }
  if (startTimestamp && endTimestamp && startTimestamp >= endTimestamp) {
    logger.error("[schedule] start is not before end", { gameId });
    return false;
  }
  if (headStartMinutes < 0) {
    logger.error("[schedule] negative headStartMinutes", { gameId, headStartMinutes });
    return false;
  }
  // Cloud Tasks fires past-scheduled tasks immediately, which would avalanche
  // the whole lifecycle the instant the doc is created.
  const now = Date.now();
  const PAST_THRESHOLD_MS = 60 * 1000;
  if (startTimestamp && startTimestamp.getTime() < now - PAST_THRESHOLD_MS) {
    logger.error("[schedule] start is in the past", { gameId, start: startTimestamp.toISOString() });
    return false;
  }
  if (endTimestamp && endTimestamp.getTime() < now - PAST_THRESHOLD_MS) {
    logger.error("[schedule] end is in the past", { gameId, end: endTimestamp.toISOString() });
    return false;
  }

  const statusQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/transitionGameStatus`
  );
  const notifQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/sendGameNotification`
  );
  const spawnQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/spawnPowerUpBatch`
  );

  // Every enqueued task id, so `onGameDeleted` can cancel them.
  const enqueuedTasksByQueue: Record<string, string[]> = {
    transitionGameStatus: [],
    sendGameNotification: [],
    spawnPowerUpBatch: [],
  };

  // Manual-start games defer everything that depends on the effective start
  // to `launchGame`.
  const manualStartEnabled = data.manualStartEnabled === true;

  if (startTimestamp) {
    const id = `status-start-${gameId}`;
    const chosenTarget = manualStartEnabled ? "readyToLaunch" : "inProgress";
    await statusQueue.enqueue(
      {
        gameId,
        targetStatus: chosenTarget,
        expectedCurrentStatus: "waiting",
      },
      { scheduleTime: startTimestamp, id }
    );
    enqueuedTasksByQueue.transitionGameStatus.push(id);

    const chickenStartId = `notif-chickenstart-${gameId}`;
    await notifQueue.enqueue(
      { gameId, notificationType: "chicken_start", notifId: chickenStartId },
      { scheduleTime: startTimestamp, id: chickenStartId }
    );
    enqueuedTasksByQueue.sendGameNotification.push(chickenStartId);

    if (!manualStartEnabled) {
      const initialSpawnId = `spawn-${gameId}-0`;
      await spawnQueue.enqueue(
        { gameId, batchIndex: 0, count: POWER_UP_INITIAL_BATCH_SIZE },
        { scheduleTime: startTimestamp, id: initialSpawnId }
      );
      enqueuedTasksByQueue.spawnPowerUpBatch.push(initialSpawnId);
    }
  }

  if (endTimestamp && !manualStartEnabled) {
    const id = `status-end-${gameId}`;
    await statusQueue.enqueue(
      {
        gameId,
        targetStatus: "done",
        expectedCurrentStatus: "inProgress",
      },
      { scheduleTime: endTimestamp, id }
    );
    enqueuedTasksByQueue.transitionGameStatus.push(id);
  }

  if (startTimestamp && endTimestamp && !manualStartEnabled) {
    const hunterStartDate = new Date(
      startTimestamp.getTime() + headStartMinutes * 60 * 1000
    );

    const hunterStartId = `notif-hunterstart-${gameId}`;
    await notifQueue.enqueue(
      { gameId, notificationType: "hunter_start", notifId: hunterStartId },
      { scheduleTime: hunterStartDate, id: hunterStartId }
    );
    enqueuedTasksByQueue.sendGameNotification.push(hunterStartId);

    const intervalMs = shrinkIntervalMinutes * 60 * 1000;
    let shrinkTime = new Date(hunterStartDate.getTime() + intervalMs);
    let shrinkCount = 0;

    while (shrinkTime < endTimestamp && shrinkCount < MAX_SHRINK_NOTIFICATIONS) {
      const id = `notif-shrink-${gameId}-${shrinkCount}`;
      await notifQueue.enqueue(
        { gameId, notificationType: "zone_shrink", notifId: id },
        { scheduleTime: shrinkTime, id }
      );
      enqueuedTasksByQueue.sendGameNotification.push(id);
      shrinkTime = new Date(shrinkTime.getTime() + intervalMs);
      shrinkCount++;
    }

    const spawnCount = Math.min(shrinkCount, MAX_POWER_UP_SHRINK_BATCHES);
    let spawnTime = new Date(hunterStartDate.getTime() + intervalMs);
    for (let batchIndex = 1; batchIndex <= spawnCount; batchIndex++) {
      const id = `spawn-${gameId}-${batchIndex}`;
      await spawnQueue.enqueue(
        {
          gameId,
          batchIndex,
          count: POWER_UP_PERIODIC_BATCH_SIZE,
        },
        { scheduleTime: spawnTime, id }
      );
      enqueuedTasksByQueue.spawnPowerUpBatch.push(id);
      spawnTime = new Date(spawnTime.getTime() + intervalMs);
    }
  }

  try {
    await db()
      .collection("games")
      .doc(gameId)
      .collection("lifecycle")
      .doc("taskManifest")
      .set({ enqueuedTasksByQueue });
  } catch (err) {
    logger.warn("[schedule] task manifest write failed", { gameId, error: String(err) });
  }

  return true;
}
