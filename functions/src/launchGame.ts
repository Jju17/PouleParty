import { Timestamp } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { isChicken, isGameMaster } from "./roles";
import { mirrorGameMetaInline } from "./rtdbMirror";
import { CALLABLE_OPTIONS, apiError, db, requireString, requireUid } from "./config";
import { enqueueAll, mergeIntoTaskManifest, planRuntimeTasks } from "./lifecycleTasks";

interface LaunchGameInput {
  gameId?: string;
}

interface LaunchGameResult {
  status: "launched";
  actualStartMillis: number;
  endMillis: number;
}

/**
 * Launch core, shared by the `launchGame` callable and the QA
 * `debugAdvanceGame` "advanceStep" action. Assumes the caller already
 * authorized the request; performs the readyToLaunch → inProgress CAS,
 * stamps `actualStart` / recomputes `timing.end`, and enqueues the deferred
 * runtime tasks (status→done, hunter_start notif, zone_shrink notifs,
 * power-up batches incl. the initial one).
 */
export async function launchReadyGame(gameId: string): Promise<LaunchGameResult> {
  const firestore = db();
  const gameRef = firestore.collection("games").doc(gameId);

  // Read the duration + shrink interval BEFORE the transaction so we
  // can compute the new end without keeping a long-lived RW lock on
  // the doc. The transaction below re-reads `status` for the CAS.
  const preSnap = await gameRef.get();
  if (!preSnap.exists) {
    throw apiError("not-found", "gameNotFound", "Game not found");
  }
  const preData = preSnap.data() ?? {};

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
    throw apiError("failed-precondition", "notReadyToLaunch", "Game is missing timing.start or timing.end");
  }
  const headStartMinutes = (timing?.headStartMinutes as number | undefined) ?? 0;
  const originalDurationMs = plannedEnd.getTime() - plannedStart.getTime();
  if (originalDurationMs <= 0) {
    throw apiError("failed-precondition", "notReadyToLaunch", "Planned end is not after planned start");
  }
  const zone = preData.zone as { shrinkIntervalMinutes?: number } | undefined;
  const shrinkIntervalMinutes =
    typeof zone?.shrinkIntervalMinutes === "number" && zone.shrinkIntervalMinutes > 0
      ? zone.shrinkIntervalMinutes
      : 5;

  // Atomic compare-and-set: only the first concurrent caller wins.
  // The transaction stamps `actualStart` with the server clock so two
  // simultaneous taps end up with the same effective launch instant.
  const launchResult = await firestore.runTransaction(async (tx) => {
    const snap = await tx.get(gameRef);
    const data = snap.data() ?? {};
    const status = data.status as string | undefined;
    if (status === "inProgress" || status === "done") {
      throw apiError("failed-precondition", "alreadyLaunched", `Game already ${status}`);
    }
    if (status !== "readyToLaunch") {
      throw apiError("failed-precondition", "notReadyToLaunch", `Game is not ready to launch (current status: ${status ?? "unknown"})`);
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
  const enqueued = await enqueueAll(
    planRuntimeTasks(
      gameId,
      launchResult.actualStart.toDate(),
      launchResult.endTimestamp.toDate(),
      headStartMinutes,
      shrinkIntervalMinutes,
      "-launch"
    )
  );
  await mergeIntoTaskManifest(gameId, enqueued);

  // Push the new `status: inProgress` into the RTDB meta immediately so the
  // location/presence write gate opens without waiting for the async trigger.
  await mirrorGameMetaInline(gameId, (await gameRef.get()).data());

  logger.info(
    `launchReadyGame: game ${gameId} launched ` +
      `(actualStart=${launchResult.actualStart.toDate().toISOString()}, ` +
      `end=${launchResult.endTimestamp.toDate().toISOString()})`
  );

  return {
    status: "launched",
    actualStartMillis: launchResult.actualStart.toMillis(),
    endMillis: launchResult.endTimestamp.toMillis(),
  };
}

export const launchGame = onCall<LaunchGameInput, Promise<LaunchGameResult>>(
  CALLABLE_OPTIONS,
  async (request) => {
    const uid = requireUid(request);
    const gameId = requireString(request.data?.gameId, "gameId");
    const gameRef = db().collection("games").doc(gameId);
    const preSnap = await gameRef.get();
    if (!preSnap.exists) {
      throw apiError("not-found", "gameNotFound", "Game not found");
    }
    const preData = preSnap.data() ?? {};
    const creatorId = (preData.creatorId as string | undefined) ?? "";
    if (uid !== creatorId && !isChicken(preData, uid) && !isGameMaster(preData, uid)) {
      throw apiError("permission-denied", "notAllowed", "Only the chicken or a GameMaster can launch this game");
    }
    if (preData.manualStartEnabled !== true) {
      throw apiError("failed-precondition", "notReadyToLaunch", "Game was created without manual start");
    }

    return launchReadyGame(gameId);
  }
);
