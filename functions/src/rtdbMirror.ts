import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getDatabase } from "firebase-admin/database";
import * as logger from "firebase-functions/logger";
import { RolesMap } from "./roles";

const REGION = "europe-west1";

export interface GameMeta {
  creatorId: string;
  gameMode: string;
  status: string;
  /** Whether the chicken is allowed to see hunter positions. */
  chickenCanSeeHunters: boolean;
  /**
   * Whether hunters may broadcast their position at all: true when the
   * chicken can see them OR at least one GameMaster has joined (a GM always
   * sees every hunter, regardless of `chickenCanSeeHunters`). Derived here
   * because RTDB rules can't test a map for the presence of a value.
   */
  shareHunterLocations: boolean;
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
  const roles = toRolesMap(data?.roles);
  const chickenCanSeeHunters = data?.chickenCanSeeHunters !== false;
  const hasGameMaster = Object.values(roles).includes("gameMaster");
  return {
    creatorId: typeof data?.creatorId === "string" ? data.creatorId : "",
    gameMode: typeof data?.gameMode === "string" ? data.gameMode : "",
    status: typeof data?.status === "string" ? data.status : "",
    chickenCanSeeHunters,
    shareHunterLocations: chickenCanSeeHunters || hasGameMaster,
    roles,
  };
}

/**
 * Writes the RTDB `meta` mirror immediately (admin SDK), in addition to the
 * async `onDocumentWritten` trigger, so a role/status callable doesn't leave a
 * propagation gap where the realtime-position rules still see stale membership
 * (a just-joined hunter denied, a swapped chicken's old uid lingering, or the
 * launch `status` not yet `inProgress`). Best-effort: the trigger is the
 * catch-all, so a failure here is logged and swallowed, never fails the caller.
 */
export async function mirrorGameMetaInline(
  gameId: string,
  gameData: Record<string, unknown> | undefined
): Promise<void> {
  if (!gameData || gameData.status === "done") return;
  await getDatabase()
    .ref(`/games/${gameId}/meta`)
    .set(extractGameMeta(gameData))
    .catch((err) => {
      logger.error(`rtdb inline meta mirror failed for game ${gameId}`, err);
    });
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

    const meta = extractGameMeta(after);
    if (meta.creatorId === "") {
      logger.warn(
        `rtdb meta mirror: game ${gameId} has empty creatorId — malformed or partial doc produces empty authorization data`
      );
    }
    await rtdbGameRef
      .child("meta")
      .set(meta)
      .catch((err) => {
        logger.error(`rtdb meta mirror failed for game ${gameId}`, err);
      });
  }
);
