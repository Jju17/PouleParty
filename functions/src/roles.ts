import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { mirrorGameMetaInline } from "./rtdbMirror";
import { CALLABLE_OPTIONS, apiError, db, requireString, requireUid } from "./config";

export const MAX_TEAM_NAME_LENGTH = 30;

// Single source of truth for game membership. `game.roles` is a map
// `{ <uid>: Role }` on the game doc; a uid has exactly one role, so a
// "ghost" (no role) or a double-role is impossible by construction.
// `roles` is `write: if false` for clients (firestore.rules), every
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
  return db().collection("games").doc(gameId);
}

function playerRef(gameId: string, uid: string) {
  return db()
    .collection("games")
    .doc(gameId)
    .collection("players")
    .doc(uid);
}

function membershipRef(uid: string, gameId: string) {
  return db()
    .collection("users")
    .doc(uid)
    .collection("memberships")
    .doc(gameId);
}

function userRef(uid: string) {
  return db().collection("users").doc(uid);
}

export function ensureTeamName(value: unknown): string {
  if (typeof value !== "string") {
    throw apiError("invalid-argument", "invalidArgument", "teamName is required");
  }
  const trimmed = value.trim();
  if (trimmed.length === 0 || trimmed.length > MAX_TEAM_NAME_LENGTH) {
    throw apiError("invalid-argument", "invalidArgument", `teamName must be 1 to ${MAX_TEAM_NAME_LENGTH} characters`);
  }
  return trimmed;
}

function paidClaimQuery(batchId: string, uid: string) {
  return db()
    .collection("eventRegistrations")
    .where("batchId", "==", batchId)
    .where("claimedBy", "==", uid)
    .where("paid", "==", true)
    .limit(1);
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
 * Joins the caller as a hunter. On a game linked to a paid batch, the caller
 * must already own a paid registration claimed by `validateRegistrationCode`.
 */
export const joinGame = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const teamName = ensureTeamName(request.data?.teamName);

  await db().runTransaction(async (tx) => {
    const game = (await tx.get(gameRef(gameId))).data();
    if (!game) throw apiError("not-found", "gameNotFound", "Game not found");

    const status = typeof game.status === "string" ? game.status : "";
    if (!JOINABLE_STATUSES.includes(status)) {
      throw apiError("failed-precondition", "gameNotJoinable", "Game is not joinable");
    }

    const existing = roleOf(game, uid);
    if (existing === "chicken" || existing === "gameMaster") {
      throw apiError("failed-precondition", "alreadyHasRole", "You already have a role in this game");
    }

    if (existing !== "hunter") {
      const batchId = typeof game.registrationBatchId === "string" ? game.registrationBatchId : "";
      if (batchId !== "") {
        const claim = await tx.get(paidClaimQuery(batchId, uid));
        if (claim.empty) {
          throw apiError("failed-precondition", "registrationRequired", "A paid registration code is required");
        }
      }
      const maxPlayers =
        typeof game.maxPlayers === "number" ? game.maxPlayers : 0;
      if (huntersOf(game).length + 1 > maxPlayers) {
        throw apiError("resource-exhausted", "gameFull", "Game is full");
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

  await mirrorGameMetaInline(gameId, (await gameRef(gameId).get()).data());
  logger.info(`joinGame: ${uid} joined ${gameId} as hunter`);
  return { success: true };
});

export const designateChicken = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const newChickenUid = requireString(request.data?.newChickenUid, "newChickenUid");

  await db().runTransaction(async (tx) => {
    const game = (await tx.get(gameRef(gameId))).data();
    if (!game) throw apiError("not-found", "gameNotFound", "Game not found");

    const status = typeof game.status === "string" ? game.status : "";
    if (status !== "waiting") {
      throw apiError("failed-precondition", "notWaiting", "The chicken can only be re-designated while the game is waiting");
    }

    const isCreator = game.creatorId === uid;
    if (!isCreator && roleOf(game, uid) !== "gameMaster") {
      throw apiError("permission-denied", "notAllowed", "Only the creator or a GameMaster can designate the chicken");
    }

    if (roleOf(game, newChickenUid) !== "hunter") {
      throw apiError("failed-precondition", "notAHunter", "The new chicken must currently be a hunter");
    }

    const oldChickenUid = chickenIdOf(game);
    if (oldChickenUid === newChickenUid) {
      return; // no-op
    }

    // Read the demoted chicken's nickname (for a default team name) BEFORE
    // any write, transactions require reads first.
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
        teamName: oldChickenTeamName.slice(0, MAX_TEAM_NAME_LENGTH),
        joinedAt: Timestamp.now(),
      });
      setMembership(tx, oldChickenUid, gameId, "hunter");
    }
  });

  await mirrorGameMetaInline(gameId, (await gameRef(gameId).get()).data());
  logger.info(`designateChicken: ${gameId} -> ${newChickenUid} (by ${uid})`);
  return { success: true };
});

/**
 * Removes the caller from a game. Only hunters and GameMasters can leave;
 * the chicken cancels the game instead (the creator-only `status -> done`
 * rule). Clears the role, the team-name doc, and the membership index.
 */
export const leaveGame = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");

  await db().runTransaction(async (tx) => {
    const game = (await tx.get(gameRef(gameId))).data();
    if (!game) throw apiError("not-found", "gameNotFound", "Game not found");

    const role = roleOf(game, uid);
    if (role === null) {
      tx.delete(membershipRef(uid, gameId));
      return;
    }
    if (role === "chicken") {
      throw apiError("failed-precondition", "chickenCannotLeave", "The chicken cannot leave; cancel the game instead");
    }

    tx.update(gameRef(gameId), { [`roles.${uid}`]: FieldValue.delete() });
    tx.delete(playerRef(gameId, uid));
    tx.delete(membershipRef(uid, gameId));
  });

  await mirrorGameMetaInline(gameId, (await gameRef(gameId).get()).data());
  logger.info(`leaveGame: ${uid} left ${gameId}`);
  return { success: true };
});
