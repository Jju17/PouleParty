import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";

const REGION = "europe-west1";

// MUST stay in lockstep with iOS `PowerUp.PowerUpType.durationSeconds`
// and Android `PowerUpType` — drift would let a client claim a longer
// effect than the server commits to `activeEffects`.
const EFFECT_DURATION_SECONDS: Record<string, number | null> = {
  radarPing: 3,
  invisibility: 30,
  zoneFreeze: 120,
  decoy: 20,
  jammer: 30,
  zonePreview: null,
};

interface PowerUpDoc {
  id?: string;
  type?: string;
  collectedBy?: string | null;
  activatedAt?: Timestamp | null;
}

interface ActivatePowerUpInput {
  gameId?: string;
  powerUpId?: string;
}

interface ActivatePowerUpResult {
  activatedAt: number;
  expiresAt: number | null;
}

function ensureGameId(gameId: unknown): string {
  if (typeof gameId !== "string" || gameId.trim().length === 0) {
    throw new HttpsError("invalid-argument", "gameId is required");
  }
  return gameId;
}

function ensurePowerUpId(powerUpId: unknown): string {
  if (typeof powerUpId !== "string" || powerUpId.trim().length === 0) {
    throw new HttpsError("invalid-argument", "powerUpId is required");
  }
  return powerUpId;
}

export const activatePowerUp = onCall<
  ActivatePowerUpInput,
  Promise<ActivatePowerUpResult>
>({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in required");
  }

  const gameId = ensureGameId(request.data?.gameId);
  const powerUpId = ensurePowerUpId(request.data?.powerUpId);

  const db = getFirestore();
  const gameRef = db.collection("games").doc(gameId);
  const puRef = gameRef.collection("powerUps").doc(powerUpId);

  const result = await db.runTransaction<ActivatePowerUpResult>(async (tx) => {
    const puSnap = await tx.get(puRef);
    if (!puSnap.exists) {
      throw new HttpsError("not-found", "Power-up not found");
    }
    const pu = puSnap.data() as PowerUpDoc;

    if (pu.collectedBy !== uid) {
      // Same response whether uncollected or owned by another player —
      // don't leak which.
      throw new HttpsError(
        "permission-denied",
        "Only the collector can activate this power-up"
      );
    }
    if (pu.activatedAt) {
      throw new HttpsError(
        "failed-precondition",
        "Power-up already activated"
      );
    }
    const type = typeof pu.type === "string" ? pu.type : "";
    if (!(type in EFFECT_DURATION_SECONDS)) {
      throw new HttpsError(
        "failed-precondition",
        `Unknown power-up type: ${type}`
      );
    }
    const durationSeconds = EFFECT_DURATION_SECONDS[type];

    const now = Timestamp.now();
    const expiresAt =
      durationSeconds === null
        ? null
        : Timestamp.fromMillis(now.toMillis() + durationSeconds * 1000);

    tx.update(puRef, {
      activatedAt: now,
      expiresAt: expiresAt ?? FieldValue.delete(),
    });

    // zonePreview is personal — no game-level effect to mirror.
    if (expiresAt !== null) {
      tx.update(gameRef, {
        [`powerUps.activeEffects.${type}`]: expiresAt,
      });
    }

    return {
      activatedAt: now.toMillis(),
      expiresAt: expiresAt?.toMillis() ?? null,
    };
  });

  logger.info(
    `Power-up ${powerUpId} (game ${gameId}) activated by ${uid}, expires at ${result.expiresAt}`
  );
  return result;
});
