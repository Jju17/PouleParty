import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getDatabase } from "firebase-admin/database";
import * as logger from "firebase-functions/logger";

const REGION = "europe-west1";

export interface GameMeta {
  creatorId: string;
  chickenId: string;
  gameMode: string;
  status: string;
  hunterIds: Record<string, true>;
  gameMasterIds: Record<string, true>;
}

/**
 * Projects the auth-relevant fields of a game doc into the shape the RTDB
 * security rules consume. `hunterIds` / `gameMasterIds` become `{uid: true}`
 * maps because RTDB rules can membership-test a map key in O(1) but cannot
 * search an array.
 */
export function extractGameMeta(
  data: Record<string, unknown> | undefined
): GameMeta {
  const toIdMap = (v: unknown): Record<string, true> => {
    const out: Record<string, true> = {};
    if (Array.isArray(v)) {
      for (const id of v) {
        if (typeof id === "string" && id.length > 0) out[id] = true;
      }
    }
    return out;
  };
  return {
    creatorId: typeof data?.creatorId === "string" ? data.creatorId : "",
    chickenId: typeof data?.chickenId === "string" ? data.chickenId : "",
    gameMode: typeof data?.gameMode === "string" ? data.gameMode : "",
    status: typeof data?.status === "string" ? data.status : "",
    hunterIds: toIdMap(data?.hunterIds),
    gameMasterIds: toIdMap(data?.gameMasterIds),
  };
}

/**
 * Mirrors game membership into RTDB so the realtime-position rules can
 * authorize (RTDB rules cannot read Firestore). Fires on every game-doc write
 * so it tracks hunters/GMs joining and chicken re-designation, not just
 * creation. Doubles as the cleanup hook: when a game ends (`status == done`)
 * or is deleted, the whole `/games/{gameId}` RTDB subtree (positions +
 * presence + meta) is removed so stale location data does not linger or bill.
 */
export const mirrorGameMetaToRtdb = onDocumentWritten(
  { document: "games/{gameId}", region: REGION },
  async (event) => {
    const gameId = event.params.gameId;
    const after = event.data?.after?.data();
    const rtdbGameRef = getDatabase().ref(`/games/${gameId}`);

    if (!after || after.status === "done") {
      await rtdbGameRef.remove().catch((err) => {
        logger.error(`rtdb cleanup failed for game ${gameId}`, err);
      });
      return;
    }

    await rtdbGameRef
      .child("meta")
      .set(extractGameMeta(after))
      .catch((err) => {
        logger.error(`rtdb meta mirror failed for game ${gameId}`, err);
      });
  }
);
