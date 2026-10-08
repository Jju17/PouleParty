import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { timingSafeEqual } from "crypto";
import { isChicken, isHunter } from "./roles";
import { CALLABLE_OPTIONS, apiError, db, requireString, requireUid } from "./config";

// Server-side rate limit on wrong found-code attempts. The client cooldown is
// advisory only (reset by an app restart), so the server enforces its own:
// after N wrong codes within the window, the hunter is locked out for a short
// cooldown. Mirrors the gmRateLimits / validationRateLimits pattern. Doc lives
// in `/foundCodeRateLimits/{uid}_{gameId}`, admin-SDK-only.
export const FOUND_CODE_MAX_WRONG_ATTEMPTS = 3;
// Each successive lockout lasts longer; past the hard cap the hunter is
// locked until the game ends, which bounds brute force to a few dozen codes.
export const FOUND_CODE_LOCK_STEPS_MS = [10_000, 60_000, 5 * 60_000, 30 * 60_000];
export const FOUND_CODE_HARD_CAP = 15;

export interface FoundCodeAttempts {
  attempts: number;
  lockouts: number;
  totalFailures: number;
  lockedUntilMs: number | null;
}

export function emptyAttempts(): FoundCodeAttempts {
  return { attempts: 0, lockouts: 0, totalFailures: 0, lockedUntilMs: null };
}

export function isLocked(state: FoundCodeAttempts, nowMs: number): boolean {
  return state.lockedUntilMs !== null && state.lockedUntilMs > nowMs;
}

/** Records one wrong code and returns the next state. */
export function recordWrongCode(
  state: FoundCodeAttempts,
  nowMs: number,
  gameEndMs: number | null
): FoundCodeAttempts {
  const totalFailures = state.totalFailures + 1;
  const attempts = state.attempts + 1;
  if (totalFailures >= FOUND_CODE_HARD_CAP) {
    return {
      attempts: 0,
      lockouts: state.lockouts + 1,
      totalFailures,
      lockedUntilMs: gameEndMs ?? nowMs + FOUND_CODE_LOCK_STEPS_MS[FOUND_CODE_LOCK_STEPS_MS.length - 1],
    };
  }
  if (attempts < FOUND_CODE_MAX_WRONG_ATTEMPTS) {
    return { ...state, attempts, totalFailures, lockedUntilMs: null };
  }
  const step = FOUND_CODE_LOCK_STEPS_MS[Math.min(state.lockouts, FOUND_CODE_LOCK_STEPS_MS.length - 1)];
  return { attempts: 0, lockouts: state.lockouts + 1, totalFailures, lockedUntilMs: nowMs + step };
}

function readAttempts(raw: FirebaseFirestore.DocumentData | undefined): FoundCodeAttempts {
  if (!raw) return emptyAttempts();
  const lockedUntil = raw.lockedUntil as Timestamp | null | undefined;
  return {
    attempts: typeof raw.attempts === "number" ? raw.attempts : 0,
    lockouts: typeof raw.lockouts === "number" ? raw.lockouts : 0,
    totalFailures: typeof raw.totalFailures === "number" ? raw.totalFailures : 0,
    lockedUntilMs: lockedUntil ? lockedUntil.toMillis() : null,
  };
}

interface SubmitFoundCodeInput {
  gameId?: string;
  foundCode?: string;
  hunterName?: string;
}

interface SubmitFoundCodeResult {
  success: boolean;
  /** When false, what went wrong, drives the UI error copy. */
  reason?: "invalidCode" | "notAHunter" | "alreadyWinner" | "gameNotInProgress" | "cooldown";
  /** Epoch ms the cooldown ends at, when `reason === "cooldown"`. */
  lockedUntil?: number;
}

function constantTimeEquals(a: string, b: string): boolean {
  // crypto.timingSafeEqual requires equal-length buffers. Pad both to the
  // length of the longer string so the comparison itself is constant-time
  // for any input pair (a length mismatch is otherwise an early-out leak).
  const len = Math.max(a.length, b.length, 1);
  const aBuf = Buffer.alloc(len);
  const bBuf = Buffer.alloc(len);
  aBuf.write(a, 0, "utf8");
  bBuf.write(b, 0, "utf8");
  // Comparing the equality of the two padded buffers AND of the original
  // lengths catches the case where "1234" and "12340" would otherwise both
  // padded-equal but differ in length.
  const sameContent = timingSafeEqual(aBuf, bBuf);
  return sameContent && a.length === b.length;
}

export const submitFoundCode = onCall<
  SubmitFoundCodeInput,
  Promise<SubmitFoundCodeResult>
>(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const submittedCode = requireString(request.data?.foundCode, "foundCode", 8);
  const hunterName = requireString(request.data?.hunterName, "hunterName", 200).slice(0, 50);

  const ref = db().collection("games").doc(gameId);

  const privateRef = ref.collection("private").doc("security");
  const rateLimitRef = db()
    .collection("foundCodeRateLimits")
    .doc(`${uid}_${gameId}`);
  const result = await db().runTransaction<SubmitFoundCodeResult>(async (tx) => {
    // All reads up front (transaction contract: reads before writes).
    const snap = await tx.get(ref);
    if (!snap.exists) {
      throw apiError("not-found", "gameNotFound", "Game not found");
    }
    const rateLimitSnap = await tx.get(rateLimitRef);
    const data = snap.data() ?? {};
    const status = (data.status as string | undefined) ?? "waiting";
    if (status !== "inProgress") {
      return { success: false, reason: "gameNotInProgress" };
    }
    if (!isHunter(data, uid)) {
      return { success: false, reason: "notAHunter" };
    }

    const now = Timestamp.now();
    const attemptsState = readAttempts(rateLimitSnap.data());
    if (isLocked(attemptsState, now.toMillis())) {
      throw apiError("resource-exhausted", "tooManyAttempts", "Too many wrong attempts", {
        lockedUntil: attemptsState.lockedUntilMs,
      });
    }

    const privSnap = await tx.get(privateRef);
    const foundCode = (privSnap.data()?.foundCode as string | undefined) ?? "";
    if (!constantTimeEquals(submittedCode, foundCode)) {
      const gameEndMs = (data.timing?.end as Timestamp | undefined)?.toMillis() ?? null;
      const next = recordWrongCode(attemptsState, now.toMillis(), gameEndMs);
      tx.set(rateLimitRef, {
        attempts: next.attempts,
        lockouts: next.lockouts,
        totalFailures: next.totalFailures,
        lockedUntil: next.lockedUntilMs !== null ? Timestamp.fromMillis(next.lockedUntilMs) : null,
        expiresAt: Timestamp.fromMillis(now.toMillis() + 24 * 60 * 60 * 1000),
      });
      if (next.lockedUntilMs !== null) {
        return { success: false, reason: "cooldown", lockedUntil: next.lockedUntilMs };
      }
      return { success: false, reason: "invalidCode" };
    }
    const existingWinners = (data.winners as Array<{ hunterId?: string }> | undefined) ?? [];
    if (existingWinners.some((w) => w.hunterId === uid)) {
      return { success: false, reason: "alreadyWinner" };
    }
    const winner = {
      hunterId: uid,
      hunterName,
      timestamp: now,
    };
    tx.update(ref, { winners: FieldValue.arrayUnion(winner) });
    // Reset the wrong-attempt counter on success.
    tx.delete(rateLimitRef);
    return { success: true };
  });

  if (result.success) {
    logger.info(`submitFoundCode: hunter ${uid} won game ${gameId}`);
  } else {
    logger.info(`submitFoundCode: hunter ${uid} game ${gameId} reason=${result.reason}`);
  }
  return result;
});

interface GetFoundCodeInput {
  gameId?: string;
}

export const getFoundCode = onCall<
  GetFoundCodeInput,
  Promise<{ foundCode: string }>
>(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const ref = db().collection("games").doc(gameId);
  const snap = await ref.get();
  if (!snap.exists) throw apiError("not-found", "gameNotFound", "Game not found");
  const data = snap.data() ?? {};
  if (!isChicken(data, uid)) {
    throw apiError("permission-denied", "notAllowed", "Only the chicken can read the found code");
  }
  const priv = await ref.collection("private").doc("security").get();
  const foundCode = (priv.data()?.foundCode as string | undefined) ?? "";
  return { foundCode };
});
