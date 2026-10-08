import { getFunctions } from "firebase-admin/functions";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";

export const POWER_UP_INITIAL_BATCH_SIZE = 5;
export const POWER_UP_PERIODIC_BATCH_SIZE = 2;
export const MAX_POWER_UP_SHRINK_BATCHES = 100;
export const MAX_SHRINK_NOTIFICATIONS = 100;
export const READY_TO_LAUNCH_GRACE_MS = 60 * 60 * 1000;
export const IN_PROGRESS_GRACE_MS = 24 * 60 * 60 * 1000;

export type QueueName = "transitionGameStatus" | "sendGameNotification" | "spawnPowerUpBatch" | "evaluateOutOfZone";
export type TaskManifest = Record<string, string[]>;

export interface PlannedTask {
  queue: QueueName;
  id: string;
  scheduleTime: Date;
  payload: Record<string, unknown>;
}

/**
 * Enqueues a task by deterministic id. A redelivered trigger hitting an id
 * that already exists is the expected idempotent outcome, not a failure.
 */
export async function enqueueTask(task: PlannedTask): Promise<void> {
  const queue = getFunctions().taskQueue(`locations/${REGION}/functions/${task.queue}`);
  try {
    await queue.enqueue(task.payload, { scheduleTime: task.scheduleTime, id: task.id });
  } catch (err) {
    const code = (err as { code?: string }).code ?? "";
    if (code.endsWith("task-already-exists")) {
      logger.info("[tasks] task already enqueued", { id: task.id });
      return;
    }
    throw err;
  }
}

export async function enqueueAll(tasks: PlannedTask[]): Promise<TaskManifest> {
  const CHUNK = 25;
  for (let i = 0; i < tasks.length; i += CHUNK) {
    await Promise.all(tasks.slice(i, i + CHUNK).map(enqueueTask));
  }
  const manifest: TaskManifest = {};
  for (const task of tasks) {
    (manifest[task.queue] ??= []).push(task.id);
  }
  return manifest;
}

export async function mergeIntoTaskManifest(gameId: string, additions: TaskManifest): Promise<void> {
  const ref = db().collection("games").doc(gameId).collection("lifecycle").doc("taskManifest");
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const existing = (snap.data()?.enqueuedTasksByQueue as TaskManifest | undefined) ?? {};
    for (const [queue, ids] of Object.entries(additions)) {
      existing[queue] = [...new Set([...(existing[queue] ?? []), ...ids])];
    }
    tx.set(ref, { enqueuedTasksByQueue: existing }, { merge: true });
  });
}

/**
 * Plans the tasks anchored on the effective start: initial spawn, status end,
 * hunter start notification, one shrink notification and one power-up batch
 * per shrink, and the first out-of-zone evaluation.
 */
export function planRuntimeTasks(
  gameId: string,
  start: Date,
  end: Date,
  headStartMinutes: number,
  shrinkIntervalMinutes: number,
  idSuffix: string
): PlannedTask[] {
  const tasks: PlannedTask[] = [];
  tasks.push({
    queue: "spawnPowerUpBatch",
    id: `spawn-${gameId}${idSuffix}-0`,
    scheduleTime: start,
    payload: { gameId, batchIndex: 0, count: POWER_UP_INITIAL_BATCH_SIZE },
  });
  tasks.push({
    queue: "transitionGameStatus",
    id: `status-end-${gameId}${idSuffix}`,
    scheduleTime: end,
    payload: { gameId, targetStatus: "done", expectedCurrentStatus: "inProgress" },
  });

  const hunterStartDate = new Date(start.getTime() + headStartMinutes * 60 * 1000);
  const hunterStartId = `notif-hunterstart-${gameId}${idSuffix}`;
  tasks.push({
    queue: "sendGameNotification",
    id: hunterStartId,
    scheduleTime: hunterStartDate,
    payload: { gameId, notificationType: "hunter_start", notifId: hunterStartId },
  });
  tasks.push({
    queue: "evaluateOutOfZone",
    id: `ooz-${gameId}${idSuffix}-0`,
    scheduleTime: hunterStartDate,
    payload: { gameId, step: 0, idSuffix },
  });

  const intervalMs = shrinkIntervalMinutes * 60 * 1000;
  let shrinkTime = new Date(hunterStartDate.getTime() + intervalMs);
  let shrinkCount = 0;
  while (shrinkTime < end && shrinkCount < MAX_SHRINK_NOTIFICATIONS) {
    const notifId = `notif-shrink-${gameId}${idSuffix}-${shrinkCount}`;
    tasks.push({
      queue: "sendGameNotification",
      id: notifId,
      scheduleTime: shrinkTime,
      payload: { gameId, notificationType: "zone_shrink", notifId },
    });
    if (shrinkCount < MAX_POWER_UP_SHRINK_BATCHES) {
      const batchIndex = shrinkCount + 1;
      tasks.push({
        queue: "spawnPowerUpBatch",
        id: `spawn-${gameId}${idSuffix}-${batchIndex}`,
        scheduleTime: shrinkTime,
        payload: { gameId, batchIndex, count: POWER_UP_PERIODIC_BATCH_SIZE },
      });
    }
    shrinkTime = new Date(shrinkTime.getTime() + intervalMs);
    shrinkCount++;
  }
  return tasks;
}

/**
 * Plans every task scheduled at creation time. Manual-start games only get
 * the start transition, the gathering reminder and a fallback end; the rest is
 * planned at launch from the effective start.
 */
export function planCreationTasks(
  gameId: string,
  start: Date,
  end: Date,
  headStartMinutes: number,
  shrinkIntervalMinutes: number,
  manualStartEnabled: boolean
): PlannedTask[] {
  const chickenStartId = `notif-chickenstart-${gameId}`;
  const tasks: PlannedTask[] = [
    {
      queue: "transitionGameStatus",
      id: `status-start-${gameId}`,
      scheduleTime: start,
      payload: {
        gameId,
        targetStatus: manualStartEnabled ? "readyToLaunch" : "inProgress",
        expectedCurrentStatus: "waiting",
      },
    },
    {
      queue: "sendGameNotification",
      id: chickenStartId,
      scheduleTime: start,
      payload: { gameId, notificationType: "chicken_start", notifId: chickenStartId },
    },
    {
      queue: "transitionGameStatus",
      id: `status-end-fallback-${gameId}`,
      scheduleTime: new Date(end.getTime() + IN_PROGRESS_GRACE_MS),
      payload: { gameId, targetStatus: "done", expectedCurrentStatus: "inProgress" },
    },
  ];
  if (manualStartEnabled) {
    tasks.push({
      queue: "transitionGameStatus",
      id: `status-unlaunched-${gameId}`,
      scheduleTime: new Date(end.getTime() + READY_TO_LAUNCH_GRACE_MS),
      payload: { gameId, targetStatus: "done", expectedCurrentStatus: "readyToLaunch" },
    });
    return tasks;
  }
  return [
    ...tasks,
    ...planRuntimeTasks(gameId, start, end, headStartMinutes, shrinkIntervalMinutes, ""),
  ];
}

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

export type ScheduleRejection = "invalidInterval" | "startNotBeforeEnd" | "negativeHeadStart" | "missingTiming" | "inThePast";

export function validateSchedulingInput(
  start: Date | undefined,
  end: Date | undefined,
  headStartMinutes: number,
  shrinkIntervalMinutes: number,
  nowMs: number
): ScheduleRejection | null {
  if (!start || !end) return "missingTiming";
  if (shrinkIntervalMinutes < 1) return "invalidInterval";
  if (start >= end) return "startNotBeforeEnd";
  if (headStartMinutes < 0) return "negativeHeadStart";
  // Cloud Tasks fires past-scheduled tasks immediately, which would avalanche
  // the whole lifecycle the instant the doc is created.
  const PAST_THRESHOLD_MS = 60 * 1000;
  if (start.getTime() < nowMs - PAST_THRESHOLD_MS || end.getTime() < nowMs - PAST_THRESHOLD_MS) {
    return "inThePast";
  }
  return null;
}

/**
 * Enqueues all lifecycle Cloud Tasks for a newly created game and records
 * their ids so `onGameDeleted` can cancel them.
 */
export async function scheduleGameLifecycleTasks(
  gameId: string,
  data: FirebaseFirestore.DocumentData,
): Promise<boolean> {
  const timing = data.timing as { start?: FirebaseFirestore.Timestamp; end?: FirebaseFirestore.Timestamp; headStartMinutes?: number } | undefined;
  const zone = data.zone as { shrinkIntervalMinutes?: number } | undefined;
  const start = timing?.start?.toDate();
  const end = timing?.end?.toDate();
  const headStartMinutes = timing?.headStartMinutes ?? 0;
  const shrinkIntervalMinutes = zone?.shrinkIntervalMinutes ?? 5;

  const rejection = validateSchedulingInput(start, end, headStartMinutes, shrinkIntervalMinutes, Date.now());
  if (rejection || !start || !end) {
    logger.error("[schedule] refusing to schedule tasks", { gameId, reason: rejection });
    return false;
  }

  const tasks = planCreationTasks(
    gameId,
    start,
    end,
    headStartMinutes,
    shrinkIntervalMinutes,
    data.manualStartEnabled === true
  );
  const manifest = await enqueueAll(tasks);
  await mergeIntoTaskManifest(gameId, manifest);
  return true;
}
