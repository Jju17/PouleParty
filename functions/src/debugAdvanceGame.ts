import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { launchReadyGame } from "./launchGame";

const REGION = "europe-west1";
// Power-ups spawned on each debug shrink step — mirrors the real periodic
// batch fired at every zone shrink.
const DEBUG_SHRINK_SPAWN_SIZE = 2;

type DebugAction = "endNow" | "advanceStep";

interface DebugAdvanceGameInput {
  gameId?: string;
  action?: DebugAction;
}

interface DebugAdvanceGameResult {
  success: boolean;
  message: string;
}

interface GameDoc {
  status?: string;
  zone?: {
    shrinkIntervalMinutes?: number;
    radius?: number;
    shrinkMetersPerUpdate?: number;
  };
  timing?: {
    start?: Timestamp;
    actualStart?: Timestamp;
    headStartMinutes?: number;
  };
}

function ensureString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required`);
  }
  return value.trim();
}

function shrinkIntervalMinutesOf(data: GameDoc): number {
  const m = data.zone?.shrinkIntervalMinutes;
  return typeof m === "number" && m > 0 ? m : 1;
}

/**
 * Number of zone shrinks elapsed for `data` right now. Mirrors the client's
 * `findLastUpdate`: `shrinks = max(0, floor((now − hunterStart) / interval))`,
 * with `hunterStart = (actualStart ?? start) + headStartMinutes`.
 */
function elapsedShrinks(data: GameDoc): number {
  const anchor = data.timing?.actualStart ?? data.timing?.start;
  if (!anchor) return 0;
  const headStartMin =
    typeof data.timing?.headStartMinutes === "number" ? data.timing.headStartMinutes : 0;
  const intervalMs = shrinkIntervalMinutesOf(data) * 60_000;
  const hunterStartMs = anchor.toMillis() + headStartMin * 60_000;
  return Math.max(0, Math.floor((Date.now() - hunterStartMs) / intervalMs));
}

/**
 * QA-only callable. Lets the host of a *debug* game (one created via the
 * `qa_debug_code` long-press, flagged `isDebugGame == true`) drive the game
 * through its whole lifecycle without waiting on the clock:
 *
 * - `advanceStep` → context-aware "next step":
 *     • `waiting`        → flip to `readyToLaunch` (LAUNCH overlay appears now,
 *                          skipping the wait until `timing.start`).
 *     • `readyToLaunch`  → launch for real (`launchReadyGame`: → `inProgress`,
 *                          stamps `actualStart`, enqueues every deferred
 *                          runtime task incl. the initial power-up batch).
 *     • `inProgress`     → advance exactly one zone shrink (rewind the start
 *                          anchor so all clients derive one more shrink) and
 *                          spawn a periodic power-up batch, just like a real
 *                          shrink. When that shrink would collapse the zone,
 *                          flip to `done` instead.
 *     • `done`           → no-op.
 * - `endNow`      → flips `status` to `done` immediately (escape hatch).
 *
 * It refuses to act on any game where `isDebugGame !== true`, so it is inert
 * on real games even if accidentally reachable.
 */
export const debugAdvanceGame = onCall<
  DebugAdvanceGameInput,
  Promise<DebugAdvanceGameResult>
>({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureString(request.data?.gameId, "gameId");
  const action = ensureString(request.data?.action, "action") as DebugAction;

  const db = getFirestore();
  const gameRef = db.collection("games").doc(gameId);
  const snap = await gameRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Game not found");
  const data = snap.data() ?? {};

  // Hard gate: only debug games can be driven from the QA panel.
  if (data.isDebugGame !== true) {
    throw new HttpsError(
      "permission-denied",
      "debugAdvanceGame only operates on debug games"
    );
  }
  const creatorId = (data.creatorId as string | undefined) ?? "";
  const gameMasterIds = (data.gameMasterIds as string[] | undefined) ?? [];
  if (uid !== creatorId && !gameMasterIds.includes(uid)) {
    throw new HttpsError(
      "permission-denied",
      "Only the chicken or a GameMaster can drive the QA panel"
    );
  }

  switch (action) {
    case "endNow": {
      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(gameRef);
        if (!fresh.exists) return;
        if (fresh.data()?.status === "done") return;
        tx.update(gameRef, { status: "done" });
      });
      logger.info(`debugAdvanceGame: ended game ${gameId} (by ${uid})`);
      return { success: true, message: "Game ended" };
    }
    case "advanceStep": {
      const status = data.status as string | undefined;

      if (status === "done") {
        return { success: true, message: "Game already finished" };
      }
      if (status === "waiting") {
        await gameRef.update({ status: "readyToLaunch" });
        logger.info(`debugAdvanceGame: ${gameId} → readyToLaunch (by ${uid})`);
        return { success: true, message: "Ready to launch" };
      }
      if (status === "readyToLaunch") {
        await launchReadyGame(gameId);
        logger.info(`debugAdvanceGame: ${gameId} launched (by ${uid})`);
        return { success: true, message: "Game launched" };
      }

      // inProgress → advance exactly one zone shrink, atomically.
      const step = await db.runTransaction(async (tx) => {
        const fresh = await tx.get(gameRef);
        if (!fresh.exists) return null;
        const d = fresh.data() as GameDoc;
        if (d.status !== "inProgress") return null;
        const anchor = d.timing?.actualStart ?? d.timing?.start;
        if (!anchor) return null;

        const initialRadius = typeof d.zone?.radius === "number" ? d.zone.radius : 0;
        const perUpdate =
          typeof d.zone?.shrinkMetersPerUpdate === "number" ? d.zone.shrinkMetersPerUpdate : 0;
        const desired = elapsedShrinks(d) + 1;

        // Zone collapsed at this step → terminal state instead of a shrink.
        if (perUpdate > 0 && initialRadius - desired * perUpdate <= 0) {
          tx.update(gameRef, { status: "done" });
          return { collapsed: true as const };
        }

        // Set the anchor absolutely so every client derives exactly `desired`
        // elapsed shrinks (mid-interval offset avoids boundary flapping).
        const intervalMs = shrinkIntervalMinutesOf(d) * 60_000;
        const headStartMin =
          typeof d.timing?.headStartMinutes === "number" ? d.timing.headStartMinutes : 0;
        const newAnchorMs =
          Date.now() - headStartMin * 60_000 - (desired * intervalMs + intervalMs / 2);
        const usesActualStart = d.timing?.actualStart != null;
        tx.update(gameRef, {
          [usesActualStart ? "timing.actualStart" : "timing.start"]:
            Timestamp.fromMillis(newAnchorMs),
        });
        return { collapsed: false as const, spawnIndex: desired };
      });

      if (!step) {
        return { success: true, message: "No-op" };
      }
      if (step.collapsed) {
        logger.info(`debugAdvanceGame: zone collapsed → done for ${gameId} (by ${uid})`);
        return { success: true, message: "Zone collapsed — game over" };
      }

      // Spawn a periodic batch at the new shrink index, just like reality.
      // `idSalt` keeps the generated doc IDs unique so this batch coexists
      // with the scheduled one instead of merging onto its docs — and so the
      // shrink index (which drives the radius/center) is NOT inflated.
      const idSalt = Date.now() % 100000;
      const spawnQueue = getFunctions().taskQueue(
        `locations/${REGION}/functions/spawnPowerUpBatch`
      );
      await spawnQueue.enqueue(
        { gameId, batchIndex: step.spawnIndex, count: DEBUG_SHRINK_SPAWN_SIZE, idSalt },
        { scheduleTime: new Date(), id: `debug-spawn-${gameId}-${idSalt}` }
      );
      logger.info(
        `debugAdvanceGame: shrink → index ${step.spawnIndex} for ${gameId} (by ${uid})`
      );
      return { success: true, message: "Zone shrunk" };
    }
    default:
      throw new HttpsError("invalid-argument", `Unknown action: ${action}`);
  }
});
