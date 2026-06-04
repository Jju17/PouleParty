import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { timingSafeEqual } from "crypto";
import { isChicken, isHunter } from "./roles";

const REGION = "europe-west1";

// Server-side rate limit on wrong found-code attempts. The client cooldown is
// advisory only (reset by an app restart), so the server enforces its own:
// after N wrong codes within the window, the hunter is locked out for a short
// cooldown. Mirrors the gmRateLimits / validationRateLimits pattern. Doc lives
// in `/foundCodeRateLimits/{uid}_{gameId}`, admin-SDK-only.
const FOUND_CODE_MAX_WRONG_ATTEMPTS = 3;
const FOUND_CODE_COOLDOWN_MS = 10 * 1000;

interface FoundCodeRateLimit {
  attempts: number;
  firstAttemptAt: Timestamp;
  lockedUntil: Timestamp | null;
}

// CRIT-3 (audit 2026-05-17) — server-authoritative winner submission.
//
// Before this CF, `firestore.rules` allowed any authenticated user to write
// the `winners` array on /games/{gameId}. Combined with foundCode being
// publicly readable on the game doc, any hunter (or any anonymous user that
// guessed the gameId) could self-declare victory without ever finding the
// chicken. We now:
//   1) Lock the `winners` field — clients can no longer write it directly.
//   2) Require every winner submission to flow through this callable, which
//      verifies the caller is a hunter AND that the foundCode they
//      provided matches the game's foundCode (constant-time compare).
//   3) Use a Firestore transaction so the read/compare/append is atomic
//      and idempotent — re-submitting the same hunterId twice is a no-op.

interface SubmitFoundCodeInput {
  gameId?: string;
  foundCode?: string;
  hunterName?: string;
}

interface SubmitFoundCodeResult {
  success: boolean;
  /** When false, what went wrong — drives the UI error copy. */
  reason?: "invalidCode" | "notAHunter" | "alreadyWinner" | "gameNotInProgress" | "cooldown";
  /** Epoch ms the cooldown ends at, when `reason === "cooldown"`. */
  lockedUntil?: number;
}

function ensureNonEmptyString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required`);
  }
  return value.trim();
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
>({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureNonEmptyString(request.data?.gameId, "gameId");
  // The hunter's typed-in 4-digit code. Trim only — we do NOT uppercase
  // because the game's foundCode is digits-only.
  const submittedCode = ensureNonEmptyString(request.data?.foundCode, "foundCode");
  // hunterName is the team name / display label shown on the leaderboard.
  // Cap at 50 chars defensively, same as the FCM notif truncation in
  // `onGameUpdated`.
  const hunterName = ensureNonEmptyString(request.data?.hunterName, "hunterName").slice(0, 50);

  const ref = getFirestore().collection("games").doc(gameId);

  const privateRef = ref.collection("private").doc("security");
  const rateLimitRef = getFirestore()
    .collection("foundCodeRateLimits")
    .doc(`${uid}_${gameId}`);
  const result = await getFirestore().runTransaction<SubmitFoundCodeResult>(async (tx) => {
    // All reads up front (transaction contract: reads before writes).
    const snap = await tx.get(ref);
    if (!snap.exists) {
      throw new HttpsError("not-found", "Game not found");
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
    let rateLimit: FoundCodeRateLimit = (rateLimitSnap.data() as FoundCodeRateLimit) ?? {
      attempts: 0,
      firstAttemptAt: now,
      lockedUntil: null,
    };
    // Auto-reset an expired cooldown so the hunter can retry.
    if (rateLimit.lockedUntil && rateLimit.lockedUntil.toMillis() <= now.toMillis()) {
      rateLimit = { attempts: 0, firstAttemptAt: now, lockedUntil: null };
    }
    if (rateLimit.lockedUntil) {
      throw new HttpsError("resource-exhausted", "Too many wrong attempts", {
        lockedUntil: rateLimit.lockedUntil.toMillis(),
      });
    }

    // CRIT-2 (audit 2026-05-17): read foundCode from the admin-only
    // /private/security subcollection. The public Game doc's
    // `foundCode` field is "" after onGameCreated relocates it.
    const privSnap = await tx.get(privateRef);
    const foundCode = (privSnap.data()?.foundCode as string | undefined) ?? "";
    if (!constantTimeEquals(submittedCode, foundCode)) {
      const attempts = rateLimit.attempts + 1;
      const reachedLock = attempts >= FOUND_CODE_MAX_WRONG_ATTEMPTS;
      const lockedUntil = reachedLock
        ? Timestamp.fromMillis(now.toMillis() + FOUND_CODE_COOLDOWN_MS)
        : null;
      tx.set(rateLimitRef, {
        attempts,
        firstAttemptAt: rateLimit.firstAttemptAt,
        lockedUntil,
      } satisfies FoundCodeRateLimit);
      if (reachedLock) {
        return { success: false, reason: "cooldown", lockedUntil: lockedUntil!.toMillis() };
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

// CRIT-2 (audit 2026-05-17) — chicken fetches the foundCode via this
// callable instead of reading it off the public Game doc. The CF
// returns the code only if the caller is the game's chicken; the
// foundCode itself lives in /private/security (admin-SDK only).
interface GetFoundCodeInput {
  gameId?: string;
}

export const getFoundCode = onCall<
  GetFoundCodeInput,
  Promise<{ foundCode: string }>
>({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureNonEmptyString(request.data?.gameId, "gameId");
  const ref = getFirestore().collection("games").doc(gameId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Game not found");
  const data = snap.data() ?? {};
  if (!isChicken(data, uid)) {
    throw new HttpsError(
      "permission-denied",
      "Only the chicken can read the found code"
    );
  }
  const priv = await ref.collection("private").doc("security").get();
  const foundCode = (priv.data()?.foundCode as string | undefined) ?? "";
  return { foundCode };
});
