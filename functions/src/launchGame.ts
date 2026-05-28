import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";

const POWER_UP_INITIAL_BATCH_SIZE = 5;
const POWER_UP_PERIODIC_BATCH_SIZE = 2;
const MAX_POWER_UP_SHRINK_BATCHES = 100;
const MAX_SHRINK_NOTIFICATIONS = 100;

interface LaunchGameInput {
  gameId?: string;
}

interface LaunchGameResult {
  status: "launched";
  actualStartMillis: number;
  endMillis: number;
}

function ensureString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required`);
  }
  return value.trim();
}

/**
 * Mirrors the post-start half of `scheduleGameLifecycleTasks` in
 * `index.ts`. Called at LAUNCH time to enqueue the tasks that were
 * deliberately deferred at game creation when `manualStartEnabled`
 * is true (status→done, hunter_start notif, zone_shrink notifs,
 * power-up spawns including the initial batch).
 *
 * Returns the manifest of enqueued task IDs so the caller can merge
 * them into the CRIT-10 task manifest.
 */
async function enqueueRuntimeTasksFromActualStart(
  gameId: string,
  actualStart: Date,
  endTimestamp: Date,
  headStartMinutes: number,
  shrinkIntervalMinutes: number
): Promise<Record<string, string[]>> {
  const statusQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/transitionGameStatus`
  );
  const notifQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/sendGameNotification`
  );
  const spawnQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/spawnPowerUpBatch`
  );

  const enqueued: Record<string, string[]> = {
    transitionGameStatus: [],
    sendGameNotification: [],
    spawnPowerUpBatch: [],
  };

  // Initial power-up batch fires immediately (the launch *is* the start).
  const initialSpawnId = `spawn-${gameId}-0`;
  await spawnQueue.enqueue(
    { gameId, batchIndex: 0, count: POWER_UP_INITIAL_BATCH_SIZE },
    { scheduleTime: actualStart, id: initialSpawnId }
  );
  enqueued.spawnPowerUpBatch.push(initialSpawnId);

  // inProgress → done at endTimestamp
  const endStatusId = `status-end-${gameId}`;
  await statusQueue.enqueue(
    {
      gameId,
      targetStatus: "done",
      expectedCurrentStatus: "inProgress",
    },
    { scheduleTime: endTimestamp, id: endStatusId }
  );
  enqueued.transitionGameStatus.push(endStatusId);

  const hunterStartDate = new Date(
    actualStart.getTime() + headStartMinutes * 60 * 1000
  );

  // hunter_start notification
  const hunterStartId = `notif-hunterstart-${gameId}`;
  await notifQueue.enqueue(
    { gameId, notificationType: "hunter_start" },
    { scheduleTime: hunterStartDate, id: hunterStartId }
  );
  enqueued.sendGameNotification.push(hunterStartId);

  // zone_shrink notifications + periodic power-up batches
  const intervalMs = shrinkIntervalMinutes * 60 * 1000;
  let shrinkTime = new Date(hunterStartDate.getTime() + intervalMs);
  let shrinkCount = 0;
  while (shrinkTime < endTimestamp && shrinkCount < MAX_SHRINK_NOTIFICATIONS) {
    const id = `notif-shrink-${gameId}-${shrinkCount}`;
    await notifQueue.enqueue(
      { gameId, notificationType: "zone_shrink" },
      { scheduleTime: shrinkTime, id }
    );
    enqueued.sendGameNotification.push(id);
    shrinkTime = new Date(shrinkTime.getTime() + intervalMs);
    shrinkCount += 1;
  }

  const spawnBatchCount = Math.min(shrinkCount, MAX_POWER_UP_SHRINK_BATCHES);
  let spawnTime = new Date(hunterStartDate.getTime() + intervalMs);
  for (let batchIndex = 1; batchIndex <= spawnBatchCount; batchIndex++) {
    const id = `spawn-${gameId}-${batchIndex}`;
    await spawnQueue.enqueue(
      { gameId, batchIndex, count: POWER_UP_PERIODIC_BATCH_SIZE },
      { scheduleTime: spawnTime, id }
    );
    enqueued.spawnPowerUpBatch.push(id);
    spawnTime = new Date(spawnTime.getTime() + intervalMs);
  }

  return enqueued;
}

async function mergeIntoTaskManifest(
  gameId: string,
  additions: Record<string, string[]>
): Promise<void> {
  const db = getFirestore();
  const ref = db
    .collection("games")
    .doc(gameId)
    .collection("lifecycle")
    .doc("taskManifest");
  try {
    const snap = await ref.get();
    const existing =
      (snap.data()?.enqueuedTasksByQueue as Record<string, string[]> | undefined) ?? {};
    for (const [queue, ids] of Object.entries(additions)) {
      const merged = new Set<string>([...(existing[queue] ?? []), ...ids]);
      existing[queue] = [...merged];
    }
    await ref.set({ enqueuedTasksByQueue: existing }, { merge: true });
  } catch (err) {
    // Non-fatal — onGameDeleted falls back to "no manifest → no cleanup",
    // which is no worse than the pre-CRIT-10 baseline.
    logger.warn(`mergeIntoTaskManifest: failed for game ${gameId}`, err);
  }
}

export const launchGame = onCall<LaunchGameInput, Promise<LaunchGameResult>>(
  { region: REGION },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

    const gameId = ensureString(request.data?.gameId, "gameId");

    const db = getFirestore();
    const gameRef = db.collection("games").doc(gameId);

    // Read the duration + shrink interval BEFORE the transaction so we
    // can compute the new end without keeping a long-lived RW lock on
    // the doc. The transaction below re-reads `status` for the CAS.
    const preSnap = await gameRef.get();
    if (!preSnap.exists) {
      throw new HttpsError("not-found", "Game not found");
    }
    const preData = preSnap.data() ?? {};
    const creatorId = (preData.creatorId as string | undefined) ?? "";
    const gameMasterIds = (preData.gameMasterIds as string[] | undefined) ?? [];
    if (uid !== creatorId && !gameMasterIds.includes(uid)) {
      throw new HttpsError(
        "permission-denied",
        "Only the chicken or a GameMaster can launch this game"
      );
    }
    if (preData.manualStartEnabled !== true) {
      throw new HttpsError(
        "failed-precondition",
        "Game was created without manualStartEnabled — it auto-launches at timing.start"
      );
    }

    const timing = preData.timing as
      | {
          start?: FirebaseFirestore.Timestamp;
          end?: FirebaseFirestore.Timestamp;
          headStartMinutes?: number;
        }
      | undefined;
    const plannedStart = timing?.start?.toDate();
    const plannedEnd = timing?.end?.toDate();
    if (!plannedStart || !plannedEnd) {
      throw new HttpsError("failed-precondition", "Game is missing timing.start or timing.end");
    }
    const headStartMinutes = (timing?.headStartMinutes as number | undefined) ?? 0;
    const originalDurationMs = plannedEnd.getTime() - plannedStart.getTime();
    if (originalDurationMs <= 0) {
      throw new HttpsError("failed-precondition", "Planned end is not after planned start");
    }
    const zone = preData.zone as { shrinkIntervalMinutes?: number } | undefined;
    const shrinkIntervalMinutes =
      typeof zone?.shrinkIntervalMinutes === "number" && zone.shrinkIntervalMinutes > 0
        ? zone.shrinkIntervalMinutes
        : 5;

    // Atomic compare-and-set: only the first concurrent caller wins.
    // The transaction stamps `actualStart` with the server clock so two
    // simultaneous taps end up with the same effective launch instant.
    const launchResult = await db.runTransaction(async (tx) => {
      const snap = await tx.get(gameRef);
      const data = snap.data() ?? {};
      const status = data.status as string | undefined;
      if (status === "inProgress" || status === "done") {
        throw new HttpsError("failed-precondition", `Game already ${status}`);
      }
      if (status !== "readyToLaunch") {
        throw new HttpsError(
          "failed-precondition",
          `Game is not ready to launch (current status: ${status ?? "unknown"})`
        );
      }
      // Stamp `actualStart` a few seconds in the future so every client
      // (chicken + hunters + GMs) catches the 3-2-1-GO! countdown phase
      // between LAUNCH tap and the real start, instead of jumping
      // straight to "RUN!". Mirrors `countdownThresholdSeconds = 3` on
      // the client; +1s buffer so the "3" frame is rendered cleanly
      // even with a small network round-trip.
      const LAUNCH_COUNTDOWN_MS = 4 * 1000;
      const actualStart = Timestamp.fromMillis(
        Timestamp.now().toMillis() + LAUNCH_COUNTDOWN_MS
      );
      const endTimestamp = Timestamp.fromMillis(
        actualStart.toMillis() + originalDurationMs
      );
      tx.update(gameRef, {
        status: "inProgress",
        "timing.actualStart": actualStart,
        "timing.end": endTimestamp,
      });
      return { actualStart, endTimestamp };
    });

    // Outside the transaction: enqueue the runtime tasks that were
    // deliberately deferred at game-creation time.
    const enqueued = await enqueueRuntimeTasksFromActualStart(
      gameId,
      launchResult.actualStart.toDate(),
      launchResult.endTimestamp.toDate(),
      headStartMinutes,
      shrinkIntervalMinutes
    );
    await mergeIntoTaskManifest(gameId, enqueued);

    logger.info(
      `launchGame: game ${gameId} launched by ${uid} ` +
        `(actualStart=${launchResult.actualStart.toDate().toISOString()}, ` +
        `end=${launchResult.endTimestamp.toDate().toISOString()})`
    );

    return {
      status: "launched",
      actualStartMillis: launchResult.actualStart.toMillis(),
      endMillis: launchResult.endTimestamp.toMillis(),
    };
  }
);
