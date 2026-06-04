import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getDatabase } from "firebase-admin/database";
import * as logger from "firebase-functions/logger";
import { RolesMap } from "./roles";

const REGION = "europe-west1";

export interface GameMeta {
  creatorId: string;
  gameMode: string;
  status: string;
  roles: RolesMap;
}

const VALID_ROLES = ["chicken", "hunter", "gameMaster"];

/**
 * Projects the auth-relevant fields of a game doc into the shape the RTDB
 * security rules consume. The `roles` map `{uid: role}` is copied verbatim so
 * RTDB rules can read `meta.roles.<uid>` to authorize the realtime-position
 * reads/writes (RTDB rules cannot read Firestore).
 */
export function extractGameMeta(
  data: Record<string, unknown> | undefined
): GameMeta {
  const toRolesMap = (v: unknown): RolesMap => {
    const out: RolesMap = {};
    if (v && typeof v === "object" && !Array.isArray(v)) {
      for (const [uid, role] of Object.entries(v as Record<string, unknown>)) {
        if (
          typeof uid === "string" &&
          uid.length > 0 &&
          typeof role === "string" &&
          VALID_ROLES.includes(role)
        ) {
          out[uid] = role as RolesMap[string];
        }
      }
    }
    return out;
  };
  return {
    creatorId: typeof data?.creatorId === "string" ? data.creatorId : "",
    gameMode: typeof data?.gameMode === "string" ? data.gameMode : "",
    status: typeof data?.status === "string" ? data.status : "",
    roles: toRolesMap(data?.roles),
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
