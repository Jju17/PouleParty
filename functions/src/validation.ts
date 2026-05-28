import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";

interface ValidateChallengeSubmissionInput {
  gameId?: string;
  submissionId?: string;
  accept?: boolean;
}

interface ValidateChallengeSubmissionResult {
  status: "validated" | "rejected";
  pointsAwarded: number;
}

function ensureNonEmptyString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required`);
  }
  return value.trim();
}

export const validateChallengeSubmission = onCall<
  ValidateChallengeSubmissionInput,
  Promise<ValidateChallengeSubmissionResult>
>({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");

  const gameId = ensureNonEmptyString(request.data?.gameId, "gameId");
  const submissionId = ensureNonEmptyString(request.data?.submissionId, "submissionId");
  if (typeof request.data?.accept !== "boolean") {
    throw new HttpsError("invalid-argument", "accept must be a boolean");
  }
  const accept = request.data.accept;

  const db = getFirestore();
  const gameRef = db.collection("games").doc(gameId);
  const submissionRef = gameRef.collection("challengeSubmissions").doc(submissionId);

  const result = await db.runTransaction<ValidateChallengeSubmissionResult>(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) {
      throw new HttpsError("not-found", "Game not found");
    }
    const gameData = gameSnap.data() ?? {};
    const chickenId = (gameData.chickenId as string | undefined) ?? "";
    const gameMasterIds = (gameData.gameMasterIds as string[] | undefined) ?? [];
    const isAuthorized = uid === chickenId || gameMasterIds.includes(uid);
    if (!isAuthorized) {
      throw new HttpsError(
        "permission-denied",
        "Only the chicken or a GameMaster can validate submissions"
      );
    }

    const submissionSnap = await tx.get(submissionRef);
    if (!submissionSnap.exists) {
      throw new HttpsError("not-found", "Submission not found");
    }
    const submission = submissionSnap.data() ?? {};
    if (submission.status !== "pending") {
      throw new HttpsError(
        "failed-precondition",
        `Submission already ${submission.status}`
      );
    }

    const hunterId = ensureNonEmptyString(submission.hunterId, "submission.hunterId");
    const challengeId = ensureNonEmptyString(submission.challengeId, "submission.challengeId");
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
      throw new HttpsError(
        "not-found",
        `Challenge ${challengeId} not found in game ${gameId}`
      );
    }
    const challenge = challengeSnap.data() ?? {};
    const points = typeof challenge.points === "number" ? challenge.points : 0;

    const existingCompletion = completionSnap.exists ? completionSnap.data() ?? {} : {};
    const existingTotal = typeof existingCompletion.totalPoints === "number"
      ? existingCompletion.totalPoints
      : 0;
    const existingValidated = (existingCompletion.validatedChallengeIds as string[] | undefined) ?? [];
    const existingCounts = (existingCompletion.repeatableCounts as Record<string, number> | undefined) ?? {};
    const existingTeamName = (existingCompletion.teamName as string | undefined) ?? "";

    const teamName = existingTeamName.length > 0
      ? existingTeamName
      : await resolveTeamName(db, gameId, hunterId);

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

export const applyOutOfZonePenalty = onCall<
  ApplyOutOfZonePenaltyInput,
  Promise<{ newTotal: number }>
>({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required");
  const gameId = ensureNonEmptyString(request.data?.gameId, "gameId");

  const db = getFirestore();
  const gameRef = db.collection("games").doc(gameId);
  const completionRef = gameRef.collection("challengeCompletions").doc(uid);

  const newTotal = await db.runTransaction<number>(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) throw new HttpsError("not-found", "Game not found");
    const gameData = gameSnap.data() ?? {};
    const hunterIds = (gameData.hunterIds as string[] | undefined) ?? [];
    if (!hunterIds.includes(uid)) {
      throw new HttpsError("permission-denied", "Not a hunter on this game");
    }
    const completionSnap = await tx.get(completionRef);
    const existing = completionSnap.exists ? completionSnap.data() ?? {} : {};
    const existingTotal = typeof existing.totalPoints === "number" ? existing.totalPoints : 0;
    const next = existingTotal - 1;
    const payload: Record<string, unknown> = {
      hunterId: uid,
      totalPoints: next,
      validatedChallengeIds: existing.validatedChallengeIds ?? [],
      repeatableCounts: existing.repeatableCounts ?? {},
      teamName: existing.teamName ?? (await resolveTeamName(db, gameId, uid)),
    };
    tx.set(completionRef, payload, { merge: true });
    return next;
  });

  return { newTotal };
});

async function resolveTeamName(
  db: FirebaseFirestore.Firestore,
  gameId: string,
  hunterId: string
): Promise<string> {
  const regSnap = await db
    .collection("games")
    .doc(gameId)
    .collection("registrations")
    .doc(hunterId)
    .get();
  const fromRegistration = regSnap.data()?.teamName as string | undefined;
  if (fromRegistration && fromRegistration.length > 0) return fromRegistration;
  const userSnap = await db.collection("users").doc(hunterId).get();
  const nickname = userSnap.data()?.nickname as string | undefined;
  return nickname && nickname.length > 0 ? nickname : "Hunter";
}
