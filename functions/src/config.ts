import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, FunctionsErrorCode } from "firebase-functions/v2/https";
import { defineBoolean } from "firebase-functions/params";

export const REGION = "europe-west1";

export const ENFORCE_APP_CHECK = defineBoolean("ENFORCE_APP_CHECK", {
  default: true,
  description: "Reject callable requests without a valid App Check token.",
});

export const GOOGLEAPIS_MEMORY = "512MiB" as const;

export const CALLABLE_OPTIONS = {
  region: REGION,
  enforceAppCheck: ENFORCE_APP_CHECK,
  maxInstances: 20,
};

export function db(): FirebaseFirestore.Firestore {
  return getFirestore();
}

/**
 * Error codes clients translate (`apiErrors.<code>`); the message stays a
 * developer-facing English hint and is never displayed.
 */
export type ApiErrorCode =
  | "unauthenticated"
  | "invalidArgument"
  | "gameNotFound"
  | "gameNotJoinable"
  | "gameFull"
  | "alreadyHasRole"
  | "registrationRequired"
  | "notAllowed"
  | "notAHunter"
  | "notInProgress"
  | "gameOver"
  | "tooManyAttempts"
  | "gameMasterDisabled"
  | "notWaiting"
  | "chickenCannotLeave"
  | "powerUpNotFound"
  | "powerUpTaken"
  | "powerUpWrongRole"
  | "powerUpTooFar"
  | "powerUpAlreadyActive"
  | "positionUnknown"
  | "submissionNotFound"
  | "submissionAlreadyHandled"
  | "challengeNotFound"
  | "alreadyLaunched"
  | "notReadyToLaunch"
  | "notADebugGame";

export function apiError(
  status: FunctionsErrorCode,
  code: ApiErrorCode,
  message: string,
  extra: Record<string, unknown> = {}
): HttpsError {
  return new HttpsError(status, message, { code, ...extra });
}

export function requireUid(request: { auth?: { uid: string } }): string {
  const uid = request.auth?.uid;
  if (!uid) throw apiError("unauthenticated", "unauthenticated", "Sign in required");
  return uid;
}

export function requireString(value: unknown, field: string, maxLength = 128): string {
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maxLength) {
    throw apiError("invalid-argument", "invalidArgument", `${field} is required (max ${maxLength} chars)`);
  }
  return value.trim();
}
