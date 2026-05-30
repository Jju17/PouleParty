import { getFirestore } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";
const DEBUG_SPAWN_BATCH_SIZE = 5;

type DebugAction = "endNow" | "spawnPowerUp";

interface DebugAdvanceGameInput {
  gameId?: string;
  action?: DebugAction;
}

interface DebugAdvanceGameResult {
  success: boolean;
  message: string;
}

function ensureString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required`);
  }
  return value.trim();
}

/**
 * QA-only callable. Lets the host of a *debug* game (one created via the
 * `qa_debug_code` long-press, flagged `isDebugGame == true`) force-advance
 * phases without waiting on the clock:
 *
 * - `endNow`       → flips `status` to `done` (the same terminal state the
 *                    scheduled `transitionGameStatus` task would write).
 * - `spawnPowerUp` → enqueues an immediate `spawnPowerUpBatch` task, reusing
 *                    the deployed handler (deterministic generation + Mapbox
 *                    road-snapping + Firestore write). A `Date`-derived batch
 *                    index keeps the generated doc IDs unique per tap.
 *
 * It refuses to act on any game where `isDebugGame !== true`, so it is inert
 * on real games even if accidentally reachable. Deployed to staging only.
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
    case "spawnPowerUp": {
      // Unique batch index per tap so the generated doc IDs don't collide
      // with the scheduled batches (small sequential indices) and each tap
      // adds fresh power-ups instead of merging onto the same docs.
      const batchIndex = 1000 + (Date.now() % 100000);
      const spawnQueue = getFunctions().taskQueue(
        `locations/${REGION}/functions/spawnPowerUpBatch`
      );
      await spawnQueue.enqueue(
        { gameId, batchIndex, count: DEBUG_SPAWN_BATCH_SIZE },
        { scheduleTime: new Date(), id: `debug-spawn-${gameId}-${batchIndex}` }
      );
      logger.info(
        `debugAdvanceGame: enqueued spawn batch ${batchIndex} for game ${gameId} (by ${uid})`
      );
      return { success: true, message: "Power-up batch enqueued" };
    }
    default:
      throw new HttpsError("invalid-argument", `Unknown action: ${action}`);
  }
});
