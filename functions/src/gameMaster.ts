import { Timestamp } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { mirrorGameMetaInline } from "./rtdbMirror";
import { isChicken, isGameMaster, isHunter } from "./roles";
import { CALLABLE_OPTIONS, apiError, db, requireString, requireUid } from "./config";

const RATE_LIMIT_MAX_ATTEMPTS = 5;
const RATE_LIMIT_LOCK_MS = 5 * 60 * 1000;
// Fresh anonymous accounts reset the per-user counter, so every failure also
// counts against the game itself.
export const GAME_RATE_LIMIT_MAX_FAILURES = 20;
export const GAME_RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;
export const GAME_RATE_LIMIT_LOCK_MS = 15 * 60 * 1000;

const PRIVATE_DOC_ID = "security";

interface GamePrivateSecurity {
  gameMasterPassword?: string;
}

interface GmRateLimit {
  attempts: number;
  firstAttemptAt: Timestamp;
  lockedUntil: Timestamp | null;
}

export interface GameFailureCounter {
  failures: number;
  windowStartMs: number;
  lockedUntilMs: number | null;
}

/** Records one failed attempt against the game-wide counter. */
export function recordGameFailure(counter: GameFailureCounter | null, nowMs: number): GameFailureCounter {
  const fresh =
    counter !== null && nowMs - counter.windowStartMs <= GAME_RATE_LIMIT_WINDOW_MS
      ? counter
      : { failures: 0, windowStartMs: nowMs, lockedUntilMs: null };
  const failures = fresh.failures + 1;
  return {
    failures,
    windowStartMs: fresh.windowStartMs,
    lockedUntilMs: failures >= GAME_RATE_LIMIT_MAX_FAILURES ? nowMs + GAME_RATE_LIMIT_LOCK_MS : fresh.lockedUntilMs,
  };
}

export function isGameLocked(counter: GameFailureCounter | null, nowMs: number): boolean {
  return counter?.lockedUntilMs != null && counter.lockedUntilMs > nowMs;
}

function gamePrivateRef(gameId: string) {
  return db()
    .collection("games")
    .doc(gameId)
    .collection("private")
    .doc(PRIVATE_DOC_ID);
}

function gameRef(gameId: string) {
  return db().collection("games").doc(gameId);
}

function rateLimitRef(userId: string, gameId: string) {
  return db()
    .collection("gmRateLimits")
    .doc(`${userId}_${gameId}`);
}

function gameRateLimitRef(gameId: string) {
  return db().collection("gmRateLimits").doc(`game_${gameId}`);
}

function ensurePasswordFormat(password: unknown): string {
  if (typeof password !== "string" || !/^\d{4}$/.test(password)) {
    throw apiError("invalid-argument", "invalidArgument", "Password must be a 4-digit string");
  }
  return password;
}

/**
 * Sets (or replaces) the 4-digit GameMaster password on a Game.
 * Only the game's `creatorId` can call this. The password lands in
 * `/games/{gameId}/private/security` — a subcollection denied to all
 * clients by firestore.rules so only admin SDK (this handler) can
 * read it.
 */
export const setGameMasterPassword = onCall(
  CALLABLE_OPTIONS,
  async (request) => {
    const uid = requireUid(request);
    const gameId = requireString(request.data?.gameId, "gameId");
    const password = ensurePasswordFormat(request.data?.password);

    const game = (await gameRef(gameId).get()).data();
    if (!game) throw apiError("not-found", "gameNotFound", "Game not found");
    if (game.creatorId !== uid) {
      throw apiError("permission-denied", "notAllowed", "Only the creator can set the GameMaster password");
    }

    // Update both the private doc (the actual secret) and the public
    // `hasGameMasterPassword` flag in a batch so the Game doc stays
    // truthful even if a CF retry happens mid-write.
    const batch = db().batch();
    batch.set(gamePrivateRef(gameId), { gameMasterPassword: password } satisfies GamePrivateSecurity);
    batch.update(gameRef(gameId), { hasGameMasterPassword: true });
    await batch.commit();
    return { success: true };
  }
);

/**
 * Clears the GameMaster password. Existing GameMasters keep their
 * role — clearing just stops new joins (PP-70 decision). Only the
 * creator can call this.
 */
export const clearGameMasterPassword = onCall(
  CALLABLE_OPTIONS,
  async (request) => {
    const uid = requireUid(request);
    const gameId = requireString(request.data?.gameId, "gameId");
    const game = (await gameRef(gameId).get()).data();
    if (!game) throw apiError("not-found", "gameNotFound", "Game not found");
    if (game.creatorId !== uid) {
      throw apiError("permission-denied", "notAllowed", "Only the creator can clear the GameMaster password");
    }

    const batch = db().batch();
    batch.delete(gamePrivateRef(gameId));
    batch.update(gameRef(gameId), { hasGameMasterPassword: false });
    await batch.commit();
    return { success: true };
  }
);

/**
 * Sets the caller's role to `gameMaster` (`roles.<uid>`) if they
 * provide the right password. Rate-limited via
 * `gmRateLimits/{userId}_{gameId}`: 5 attempts per user per game; on
 * the 5th failure the user is locked for 5 minutes (auto-reset after
 * the lock expires). The whole flow runs inside a Firestore
 * transaction so two concurrent tries from the same UID can't bypass
 * the limit.
 */
export const joinAsGameMaster = onCall(
  CALLABLE_OPTIONS,
  async (request) => {
    const uid = requireUid(request);
    const gameId = requireString(request.data?.gameId, "gameId");
    const password = ensurePasswordFormat(request.data?.password);

    const result = await db().runTransaction(async (tx) => {
      const gameSnap = await tx.get(gameRef(gameId));
      const game = gameSnap.data();
      if (!game) {
        throw apiError("not-found", "gameNotFound", "Game not found");
      }
      const status = typeof game.status === "string" ? game.status : "";
      if (status === "done") {
        throw apiError("failed-precondition", "gameOver", "The game is over");
      }
      if (game.creatorId === uid || isChicken(game, uid) || isHunter(game, uid)) {
        throw apiError("failed-precondition", "alreadyHasRole", "You already have a role in this game");
      }
      if (isGameMaster(game, uid)) {
        // Idempotent re-join: already a GM, no change, no rate-limit
        // consumption.
        return { success: true, attemptsRemaining: RATE_LIMIT_MAX_ATTEMPTS };
      }

      const rateLimitSnap = await tx.get(rateLimitRef(uid, gameId));
      const gameCounterSnap = await tx.get(gameRateLimitRef(gameId));
      const gameCounter = (gameCounterSnap.data() as GameFailureCounter | undefined) ?? null;
      const now = Timestamp.now();
      if (isGameLocked(gameCounter, now.toMillis())) {
        throw apiError("resource-exhausted", "tooManyAttempts", "Too many attempts on this game", {
          lockedUntil: gameCounter!.lockedUntilMs,
        });
      }
      let rateLimit: GmRateLimit = (rateLimitSnap.data() as GmRateLimit) ?? {
        attempts: 0,
        firstAttemptAt: now,
        lockedUntil: null,
      };

      // Auto-reset an expired lock so the user can retry without
      // any chicken intervention.
      if (
        rateLimit.lockedUntil &&
        rateLimit.lockedUntil.toMillis() <= now.toMillis()
      ) {
        rateLimit = { attempts: 0, firstAttemptAt: now, lockedUntil: null };
      }

      if (rateLimit.lockedUntil) {
        throw apiError("resource-exhausted", "tooManyAttempts", "Too many attempts", {
          lockedUntil: rateLimit.lockedUntil.toMillis(),
        });
      }

      const privateSnap = await tx.get(gamePrivateRef(gameId));
      const securedPassword = (privateSnap.data() as GamePrivateSecurity | undefined)?.gameMasterPassword;
      if (!securedPassword) {
        throw apiError("failed-precondition", "gameMasterDisabled", "GameMaster role is not enabled on this game");
      }

      if (securedPassword !== password) {
        const attempts = rateLimit.attempts + 1;
        const reachedLock = attempts >= RATE_LIMIT_MAX_ATTEMPTS;
        const updated: GmRateLimit = {
          attempts,
          firstAttemptAt: rateLimit.firstAttemptAt ?? now,
          lockedUntil: reachedLock
            ? Timestamp.fromMillis(now.toMillis() + RATE_LIMIT_LOCK_MS)
            : null,
        };
        tx.set(rateLimitRef(uid, gameId), {
          ...updated,
          expiresAt: Timestamp.fromMillis(now.toMillis() + 24 * 60 * 60 * 1000),
        });
        const nextGameCounter = recordGameFailure(gameCounter, now.toMillis());
        tx.set(gameRateLimitRef(gameId), {
          ...nextGameCounter,
          expiresAt: Timestamp.fromMillis(now.toMillis() + 24 * 60 * 60 * 1000),
        });
        return {
          success: false,
          attemptsRemaining: Math.max(0, RATE_LIMIT_MAX_ATTEMPTS - attempts),
          lockedUntil: updated.lockedUntil?.toMillis() ?? null,
        };
      }

      // Success: set the caller's role to gameMaster, mirror the
      // membership reverse-index, AND delete the rate-limit doc in the
      // same transaction.
      // HIGH-6 (audit 2026-05-17): switched from `tx.set(..., {attempts:0})`
      // to `tx.delete(...)` so successful joins don't leave growing
      // dead docs in `/gmRateLimits` — the collection was unbounded
      // before this fix.
      tx.update(gameRef(gameId), { [`roles.${uid}`]: "gameMaster" });
      tx.set(
        db()
          .collection("users")
          .doc(uid)
          .collection("memberships")
          .doc(gameId),
        { gameId, role: "gameMaster" }
      );
      tx.delete(rateLimitRef(uid, gameId));

      return { success: true, attemptsRemaining: RATE_LIMIT_MAX_ATTEMPTS };
    });

    if (result.success) {
      await mirrorGameMetaInline(gameId, (await gameRef(gameId).get()).data());
    }
    if (!result.success) {
      logger.info(
        `joinAsGameMaster failed for ${uid} on game ${gameId} (${result.attemptsRemaining} attempts left)`
      );
    } else {
      logger.info(`joinAsGameMaster succeeded for ${uid} on game ${gameId}`);
    }
    return result;
  }
);
