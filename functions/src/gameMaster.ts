import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";
const RATE_LIMIT_MAX_ATTEMPTS = 5;
const RATE_LIMIT_LOCK_MS = 5 * 60 * 1000;

const PRIVATE_DOC_ID = "security";

interface GamePrivateSecurity {
  gameMasterPassword?: string;
}

interface GmRateLimit {
  attempts: number;
  firstAttemptAt: Timestamp;
  lockedUntil: Timestamp | null;
}

function gamePrivateRef(gameId: string) {
  return getFirestore()
    .collection("games")
    .doc(gameId)
    .collection("private")
    .doc(PRIVATE_DOC_ID);
}

function gameRef(gameId: string) {
  return getFirestore().collection("games").doc(gameId);
}

function rateLimitRef(userId: string, gameId: string) {
  return getFirestore()
    .collection("gmRateLimits")
    .doc(`${userId}_${gameId}`);
}

function ensurePasswordFormat(password: unknown): string {
  if (typeof password !== "string" || !/^\d{4}$/.test(password)) {
    throw new HttpsError(
      "invalid-argument",
      "Password must be a 4-digit string"
    );
  }
  return password;
}

function ensureGameId(gameId: unknown): string {
  if (typeof gameId !== "string" || gameId.trim().length === 0) {
    throw new HttpsError("invalid-argument", "gameId is required");
  }
  return gameId;
}

/**
 * Sets (or replaces) the 4-digit GameMaster password on a Game.
 * Only the game's `creatorId` can call this. The password lands in
 * `/games/{gameId}/private/security` — a subcollection denied to all
 * clients by firestore.rules so only admin SDK (this handler) can
 * read it.
 */
export const setGameMasterPassword = onCall(
  { region: REGION },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

    const gameId = ensureGameId(request.data?.gameId);
    const password = ensurePasswordFormat(request.data?.password);

    const game = (await gameRef(gameId).get()).data();
    if (!game) throw new HttpsError("not-found", "Game not found");
    if (game.creatorId !== uid) {
      throw new HttpsError(
        "permission-denied",
        "Only the creator can set the GameMaster password"
      );
    }

    // Update both the private doc (the actual secret) and the public
    // `hasGameMasterPassword` flag in a batch so the Game doc stays
    // truthful even if a CF retry happens mid-write.
    const batch = getFirestore().batch();
    batch.set(gamePrivateRef(gameId), { gameMasterPassword: password } satisfies GamePrivateSecurity);
    batch.update(gameRef(gameId), { hasGameMasterPassword: true });
    await batch.commit();
    return { success: true };
  }
);

/**
 * Clears the GameMaster password. Existing GameMasters in
 * `gameMasterIds` are kept — clearing just stops new joins (PP-70
 * decision). Only the creator can call this.
 */
export const clearGameMasterPassword = onCall(
  { region: REGION },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

    const gameId = ensureGameId(request.data?.gameId);
    const game = (await gameRef(gameId).get()).data();
    if (!game) throw new HttpsError("not-found", "Game not found");
    if (game.creatorId !== uid) {
      throw new HttpsError(
        "permission-denied",
        "Only the creator can clear the GameMaster password"
      );
    }

    const batch = getFirestore().batch();
    batch.delete(gamePrivateRef(gameId));
    batch.update(gameRef(gameId), { hasGameMasterPassword: false });
    await batch.commit();
    return { success: true };
  }
);

/**
 * Adds the caller to `Game.gameMasterIds` if they provide the right
 * password. Rate-limited via `gmRateLimits/{userId}_{gameId}`:
 * 5 attempts per user per game; on the 5th failure the user is
 * locked for 5 minutes (auto-reset after the lock expires). The
 * whole flow runs inside a Firestore transaction so two concurrent
 * tries from the same UID can't bypass the limit or double-add the
 * UID to `gameMasterIds`.
 */
export const joinAsGameMaster = onCall(
  { region: REGION },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

    const gameId = ensureGameId(request.data?.gameId);
    const password = ensurePasswordFormat(request.data?.password);

    const result = await getFirestore().runTransaction(async (tx) => {
      const gameSnap = await tx.get(gameRef(gameId));
      const game = gameSnap.data();
      if (!game) {
        throw new HttpsError("not-found", "Game not found");
      }
      if (game.creatorId === uid) {
        throw new HttpsError(
          "failed-precondition",
          "The creator cannot also be a GameMaster"
        );
      }
      if ((game.hunterIds ?? []).includes(uid)) {
        throw new HttpsError(
          "failed-precondition",
          "A hunter cannot also be a GameMaster"
        );
      }
      if ((game.gameMasterIds ?? []).includes(uid)) {
        // Idempotent re-join: already a GM, no change, no rate-limit
        // consumption.
        return { success: true, attemptsRemaining: RATE_LIMIT_MAX_ATTEMPTS };
      }

      const rateLimitSnap = await tx.get(rateLimitRef(uid, gameId));
      const now = Timestamp.now();
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
        throw new HttpsError("resource-exhausted", "Too many attempts", {
          lockedUntil: rateLimit.lockedUntil.toMillis(),
        });
      }

      const privateSnap = await tx.get(gamePrivateRef(gameId));
      const securedPassword = (privateSnap.data() as GamePrivateSecurity | undefined)?.gameMasterPassword;
      if (!securedPassword) {
        throw new HttpsError(
          "failed-precondition",
          "GameMaster role is not enabled on this game"
        );
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
        tx.set(rateLimitRef(uid, gameId), updated);
        return {
          success: false,
          attemptsRemaining: Math.max(0, RATE_LIMIT_MAX_ATTEMPTS - attempts),
          lockedUntil: updated.lockedUntil?.toMillis() ?? null,
        };
      }

      // Success: append the UID to gameMasterIds AND reset the
      // rate-limit counter in the same transaction.
      tx.update(gameRef(gameId), {
        gameMasterIds: [...(game.gameMasterIds ?? []), uid],
      });
      tx.set(rateLimitRef(uid, gameId), {
        attempts: 0,
        firstAttemptAt: now,
        lockedUntil: null,
      } satisfies GmRateLimit);

      return { success: true, attemptsRemaining: RATE_LIMIT_MAX_ATTEMPTS };
    });

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
