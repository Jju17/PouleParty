import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";

// Single source of truth for game membership. `game.roles` is a map
// `{ <uid>: Role }` on the game doc; a uid has exactly one role, so a
// "ghost" (no role) or a double-role is impossible by construction.
// `roles` is `write: if false` for clients (firestore.rules) — every
// mutation goes through the callables below (admin SDK). `creatorId`
// stays on the doc as ownership (orthogonal to the play role; the
// creator also appears in `roles`).
export type Role = "chicken" | "hunter" | "gameMaster";
export type RolesMap = Record<string, Role>;

type GameData = Record<string, unknown>;

const JOINABLE_STATUSES = ["waiting", "readyToLaunch", "inProgress"];

export function rolesOf(game: GameData | undefined): RolesMap {
  const r = game?.roles;
  if (r && typeof r === "object" && !Array.isArray(r)) {
    return r as RolesMap;
  }
  return {};
}

export function roleOf(game: GameData | undefined, uid: string): Role | null {
  return rolesOf(game)[uid] ?? null;
}

export function uidsWithRole(game: GameData | undefined, role: Role): string[] {
  return Object.entries(rolesOf(game))
    .filter(([, v]) => v === role)
    .map(([uid]) => uid);
}

export function chickenIdOf(game: GameData | undefined): string | null {
  return uidsWithRole(game, "chicken")[0] ?? null;
}

export function huntersOf(game: GameData | undefined): string[] {
  return uidsWithRole(game, "hunter");
}

export function gameMastersOf(game: GameData | undefined): string[] {
  return uidsWithRole(game, "gameMaster");
}

export function isChicken(game: GameData | undefined, uid: string): boolean {
  return roleOf(game, uid) === "chicken";
}

export function isHunter(game: GameData | undefined, uid: string): boolean {
  return roleOf(game, uid) === "hunter";
}

export function isGameMaster(game: GameData | undefined, uid: string): boolean {
  return roleOf(game, uid) === "gameMaster";
}

function gameRef(gameId: string) {
  return getFirestore().collection("games").doc(gameId);
}

function playerRef(gameId: string, uid: string) {
  return getFirestore()
    .collection("games")
    .doc(gameId)
    .collection("players")
    .doc(uid);
}

function membershipRef(uid: string, gameId: string) {
  return getFirestore()
    .collection("users")
    .doc(uid)
    .collection("memberships")
    .doc(gameId);
}

function userRef(uid: string) {
  return getFirestore().collection("users").doc(uid);
}

function ensureGameId(gameId: unknown): string {
  if (typeof gameId !== "string" || gameId.trim().length === 0) {
    throw new HttpsError("invalid-argument", "gameId is required");
  }
  return gameId;
}

function ensureUid(value: unknown, label: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${label} is required`);
  }
  return value;
}

function ensureTeamName(value: unknown): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", "teamName is required");
  }
  return value.trim();
}

// Mirror a user's role into the reverse index `/users/{uid}/memberships/{gameId}`
// so "find my active game" is a single self-readable read instead of three
// `whereArrayContains` queries on the game collection. Live status is NOT
// denormalized here (it would go stale): the client reads the membership list
// then fetches the referenced game docs for their current status.
function setMembership(
  tx: FirebaseFirestore.Transaction,
  uid: string,
  gameId: string,
  role: Role
) {
  tx.set(membershipRef(uid, gameId), { gameId, role });
}

/**
 * Joins the caller as a hunter. Replaces the old client-side
 * `arrayUnion(hunterIds)` + direct `/registrations` write — membership is
 * now server-authoritative. The PP-52 validation-code gate (when the game
 * is batch-linked) runs in `validateRegistrationCode` BEFORE this call;
 * `joinGame` only records the hunter.
 */
export const joinGame = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureGameId(request.data?.gameId);
  const teamName = ensureTeamName(request.data?.teamName);

  await getFirestore().runTransaction(async (tx) => {
    const game = (await tx.get(gameRef(gameId))).data();
    if (!game) throw new HttpsError("not-found", "Game not found");

    const status = typeof game.status === "string" ? game.status : "";
    if (!JOINABLE_STATUSES.includes(status)) {
      throw new HttpsError("failed-precondition", "Game is not joinable");
    }

    const existing = roleOf(game, uid);
    if (existing === "chicken" || existing === "gameMaster") {
      throw new HttpsError(
        "failed-precondition",
        "You already have a role in this game"
      );
    }

    if (existing !== "hunter") {
      const maxPlayers =
        typeof game.maxPlayers === "number" ? game.maxPlayers : 0;
      if (huntersOf(game).length + 1 > maxPlayers) {
        throw new HttpsError("resource-exhausted", "Game is full");
      }
      tx.update(gameRef(gameId), { [`roles.${uid}`]: "hunter" });
      setMembership(tx, uid, gameId, "hunter");
    }

    // Upsert the hunter's public team name (idempotent re-join just
    // refreshes it).
    tx.set(playerRef(gameId, uid), {
      teamName,
      joinedAt: Timestamp.now(),
    });
  });

  logger.info(`joinGame: ${uid} joined ${gameId} as hunter`);
  return { success: true };
});

/**
 * Re-designates the chicken before the game starts (PP-86). Caller must be
 * the creator or a GameMaster, `status == waiting`, and `newChickenUid` must
 * currently be a hunter. Atomic swap: the new chicken leaves the hunter pool
 * and the OLD chicken becomes a hunter (a user always keeps exactly one
 * role — no ghost). The old chicken's team name defaults to their saved
 * nickname when they had none.
 */
export const designateChicken = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureGameId(request.data?.gameId);
  const newChickenUid = ensureUid(request.data?.newChickenUid, "newChickenUid");

  await getFirestore().runTransaction(async (tx) => {
    const game = (await tx.get(gameRef(gameId))).data();
    if (!game) throw new HttpsError("not-found", "Game not found");

    const status = typeof game.status === "string" ? game.status : "";
    if (status !== "waiting") {
      throw new HttpsError(
        "failed-precondition",
        "The chicken can only be re-designated while the game is waiting"
      );
    }

    const isCreator = game.creatorId === uid;
    if (!isCreator && roleOf(game, uid) !== "gameMaster") {
      throw new HttpsError(
        "permission-denied",
        "Only the creator or a GameMaster can designate the chicken"
      );
    }

    if (roleOf(game, newChickenUid) !== "hunter") {
      throw new HttpsError(
        "failed-precondition",
        "The new chicken must currently be a hunter"
      );
    }

    const oldChickenUid = chickenIdOf(game);
    if (oldChickenUid === newChickenUid) {
      return; // no-op
    }

    // Read the demoted chicken's nickname (for a default team name) BEFORE
    // any write — transactions require reads first.
    let oldChickenTeamName = "Player";
    if (oldChickenUid) {
      const oldChickenProfile = (await tx.get(userRef(oldChickenUid))).data();
      const nickname = oldChickenProfile?.nickname;
      if (typeof nickname === "string" && nickname.trim().length > 0) {
        oldChickenTeamName = nickname.trim();
      }
    }

    const roleUpdates: Record<string, Role> = {
      [`roles.${newChickenUid}`]: "chicken",
    };
    if (oldChickenUid) {
      roleUpdates[`roles.${oldChickenUid}`] = "hunter";
    }
    tx.update(gameRef(gameId), roleUpdates);

    // The new chicken is no longer a hunter: drop their team-name doc.
    tx.delete(playerRef(gameId, newChickenUid));
    setMembership(tx, newChickenUid, gameId, "chicken");

    // The demoted chicken becomes a hunter and needs a team name.
    if (oldChickenUid) {
      tx.set(playerRef(gameId, oldChickenUid), {
        teamName: oldChickenTeamName,
        joinedAt: Timestamp.now(),
      });
      setMembership(tx, oldChickenUid, gameId, "hunter");
    }
  });

  logger.info(`designateChicken: ${gameId} -> ${newChickenUid} (by ${uid})`);
  return { success: true };
});

/**
 * Removes the caller from a game. Only hunters and GameMasters can leave;
 * the chicken cancels the game instead (the creator-only `status -> done`
 * rule). Clears the role, the team-name doc, and the membership index.
 */
export const leaveGame = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureGameId(request.data?.gameId);

  await getFirestore().runTransaction(async (tx) => {
    const game = (await tx.get(gameRef(gameId))).data();
    if (!game) throw new HttpsError("not-found", "Game not found");

    const role = roleOf(game, uid);
    if (role === null) return; // already not a member
    if (role === "chicken") {
      throw new HttpsError(
        "failed-precondition",
        "The chicken cannot leave; cancel the game instead"
      );
    }

    tx.update(gameRef(gameId), { [`roles.${uid}`]: FieldValue.delete() });
    tx.delete(playerRef(gameId, uid));
    tx.delete(membershipRef(uid, gameId));
  });

  logger.info(`leaveGame: ${uid} left ${gameId}`);
  return { success: true };
});
