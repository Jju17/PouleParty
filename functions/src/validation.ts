import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { isChicken, isGameMaster, isHunter } from "./roles";
import { CALLABLE_OPTIONS, apiError, db, requireString, requireUid } from "./config";

const OUT_OF_ZONE_PENALTY_INTERVAL_SECONDS = 5;

interface ValidateChallengeSubmissionInput {
  gameId?: string;
  submissionId?: string;
  accept?: boolean;
}

interface ValidateChallengeSubmissionResult {
  status: "validated" | "rejected";
  pointsAwarded: number;
}

export const validateChallengeSubmission = onCall<
  ValidateChallengeSubmissionInput,
  Promise<ValidateChallengeSubmissionResult>
>(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const submissionId = requireString(request.data?.submissionId, "submissionId");
  if (typeof request.data?.accept !== "boolean") {
    throw apiError("invalid-argument", "invalidArgument", "accept must be a boolean");
  }
  const accept = request.data.accept;

  const firestore = db();
  const gameRef = firestore.collection("games").doc(gameId);
  const submissionRef = gameRef.collection("challengeSubmissions").doc(submissionId);

  const result = await firestore.runTransaction<ValidateChallengeSubmissionResult>(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) {
      throw apiError("not-found", "gameNotFound", "Game not found");
    }
    const gameData = gameSnap.data() ?? {};
    const isAuthorized = isChicken(gameData, uid) || isGameMaster(gameData, uid);
    if (!isAuthorized) {
      throw apiError("permission-denied", "notAllowed", "Only the chicken or a GameMaster can validate submissions");
    }

    const submissionSnap = await tx.get(submissionRef);
    if (!submissionSnap.exists) {
      throw apiError("not-found", "submissionNotFound", "Submission not found");
    }
    const submission = submissionSnap.data() ?? {};
    if (submission.status !== "pending") {
      throw apiError("failed-precondition", "submissionAlreadyHandled", `Submission already ${submission.status}`);
    }

    const hunterId = requireString(submission.hunterId, "submission.hunterId");
    const challengeId = requireString(submission.challengeId, "submission.challengeId");
    const submissionType = submission.type === "repeatable" ? "repeatable" : "oneShot";

    const completionRef = gameRef.collection("challengeCompletions").doc(hunterId);
    const completionSnap = await tx.get(completionRef);

    const now = Timestamp.now();

    if (!accept) {
      tx.update(submissionRef, {
        status: "rejected",
        validatedBy: uid,
        validatedAt: now,
      });
      return { status: "rejected", pointsAwarded: 0 };
    }

    const challengeRef = gameRef.collection("challenges").doc(challengeId);
    const challengeSnap = await tx.get(challengeRef);
    if (!challengeSnap.exists) {
      throw apiError("not-found", "challengeNotFound", `Challenge ${challengeId} not found in game ${gameId}`);
    }
    const challenge = challengeSnap.data() ?? {};
    const points = typeof challenge.points === "number" ? challenge.points : 0;

    const existingCompletion = completionSnap.exists ? completionSnap.data() ?? {} : {};
    const existingTotal = typeof existingCompletion.totalPoints === "number"
      ? existingCompletion.totalPoints
      : 0;
    const existingValidated = (existingCompletion.validatedChallengeIds as string[] | undefined) ?? [];
    const existingCounts = (existingCompletion.repeatableCounts as Record<string, number> | undefined) ?? {};

    // Re-resolve the team name on every call so a rename propagates to the
    // completion + leaderboard, instead of freezing the first-seen value.
    const teamName = await resolveTeamName(firestore, gameId, hunterId);

    if (submissionType === "oneShot" && existingValidated.includes(challengeId)) {
      tx.update(submissionRef, {
        status: "validated",
        validatedBy: uid,
        validatedAt: now,
      });
      return { status: "validated", pointsAwarded: 0 };
    }

    const newTotal = existingTotal + points;
    const payload: Record<string, unknown> = {
      hunterId,
      teamName,
      totalPoints: newTotal,
    };
    if (submissionType === "oneShot") {
      payload.validatedChallengeIds = FieldValue.arrayUnion(challengeId);
      payload.repeatableCounts = existingCounts;
    } else {
      payload.repeatableCounts = {
        ...existingCounts,
        [challengeId]: (existingCounts[challengeId] ?? 0) + 1,
      };
      payload.validatedChallengeIds = existingValidated;
    }

    tx.set(completionRef, payload, { merge: true });
    // PP-103: denormalized leaderboard read-model. One entry per hunter,
    // updated in the same transaction so it never drifts from the
    // authoritative completion doc. Clients read this single doc instead of
    // streaming the whole challengeCompletions collection.
    tx.set(
      gameRef.collection("aggregates").doc("leaderboard"),
      { entries: { [hunterId]: { teamName, totalPoints: newTotal } } },
      { merge: true }
    );
    tx.update(submissionRef, {
      status: "validated",
      validatedBy: uid,
      validatedAt: now,
    });

    return { status: "validated", pointsAwarded: points };
  });

  logger.info(
    `validateChallengeSubmission: validator=${uid} game=${gameId} submission=${submissionId} ` +
    `status=${result.status} points=${result.pointsAwarded}`
  );
  return result;
});

interface ApplyOutOfZonePenaltyInput {
  gameId?: string;
}

export interface PenaltyDecision {
  points: number;
  newLastPenaltyAtMs: number | null;
}

/**
 * Removes `points` from a hunter's challenge total and keeps the leaderboard
 * read-model in sync, in one transaction. `decide` receives the stored
 * `lastPenaltyAt` so client calls and server checks share one window and can
 * never charge the same interval twice.
 */
export async function applyPenaltyPoints(
  gameId: string,
  hunterId: string,
  decide: (lastPenaltyAtMs: number | null) => PenaltyDecision
): Promise<number> {
  const firestore = db();
  const gameRef = firestore.collection("games").doc(gameId);
  const completionRef = gameRef.collection("challengeCompletions").doc(hunterId);
  const teamName = await resolveTeamName(firestore, gameId, hunterId);

  return firestore.runTransaction<number>(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) throw apiError("not-found", "gameNotFound", "Game not found");
    if (!isHunter(gameSnap.data() ?? {}, hunterId)) {
      throw apiError("permission-denied", "notAHunter", "Not a hunter on this game");
    }
    const completionSnap = await tx.get(completionRef);
    const existing = completionSnap.exists ? completionSnap.data() ?? {} : {};
    const existingTotal = typeof existing.totalPoints === "number" ? existing.totalPoints : 0;
    const lastPenaltyAt = existing.lastPenaltyAt as Timestamp | undefined;
    const decision = decide(lastPenaltyAt ? lastPenaltyAt.toMillis() : null);
    if (decision.points === 0 && decision.newLastPenaltyAtMs === null) return existingTotal;

    const next = existingTotal - decision.points;
    const payload: Record<string, unknown> = {
      hunterId,
      totalPoints: next,
      validatedChallengeIds: existing.validatedChallengeIds ?? [],
      repeatableCounts: existing.repeatableCounts ?? {},
      teamName,
    };
    if (decision.newLastPenaltyAtMs !== null) {
      payload.lastPenaltyAt = Timestamp.fromMillis(decision.newLastPenaltyAtMs);
    }
    tx.set(completionRef, payload, { merge: true });
    if (decision.points > 0) {
      tx.set(
        gameRef.collection("aggregates").doc("leaderboard"),
        { entries: { [hunterId]: { teamName, totalPoints: next } } },
        { merge: true }
      );
    }
    return next;
  });
}

/**
 * Client-reported penalty for immediate feedback. At most one point per
 * interval: a retried call landing inside the window is absorbed.
 */
export function decideClientPenalty(lastPenaltyAtMs: number | null, nowMs: number): PenaltyDecision {
  const guardMs = (OUT_OF_ZONE_PENALTY_INTERVAL_SECONDS - 1) * 1000;
  if (lastPenaltyAtMs !== null && nowMs - lastPenaltyAtMs < guardMs) {
    return { points: 0, newLastPenaltyAtMs: null };
  }
  return { points: 1, newLastPenaltyAtMs: nowMs };
}

export const applyOutOfZonePenalty = onCall<
  ApplyOutOfZonePenaltyInput,
  Promise<{ newTotal: number }>
>(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const nowMs = Date.now();
  const newTotal = await applyPenaltyPoints(gameId, uid, (last) => decideClientPenalty(last, nowMs));
  return { newTotal };
});

async function resolveTeamName(
  firestore: FirebaseFirestore.Firestore,
  gameId: string,
  hunterId: string
): Promise<string> {
  const playerSnap = await firestore
    .collection("games")
    .doc(gameId)
    .collection("players")
    .doc(hunterId)
    .get();
  const fromPlayer = playerSnap.data()?.teamName as string | undefined;
  if (fromPlayer && fromPlayer.length > 0) return fromPlayer;
  const userSnap = await firestore.collection("users").doc(hunterId).get();
  const nickname = userSnap.data()?.nickname as string | undefined;
  return nickname && nickname.length > 0 ? nickname : "Hunter";
}
