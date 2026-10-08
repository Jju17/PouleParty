import { FieldValue, GeoPoint, Timestamp } from "firebase-admin/firestore";
import { getDatabase } from "firebase-admin/database";
import { onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { mirrorGameMetaInline } from "./rtdbMirror";
import { CALLABLE_OPTIONS, apiError, db, requireString, requireUid } from "./config";
import { roleOf } from "./roles";
import { haversineDistance } from "./powerUpSpawn";

// Lockstep with the client duration tables: a drift would let a client show a
// longer effect than the server commits.
export const EFFECT_DURATION_SECONDS: Record<string, number | null> = {
  radarPing: 3,
  invisibility: 30,
  zoneFreeze: 120,
  decoy: 20,
  jammer: 30,
  zonePreview: null,
};

export const HUNTER_POWER_UP_TYPES = ["zonePreview", "radarPing"];
export const POWER_UP_COLLECTION_RADIUS_METERS = 30;
// GPS jitter between the device fix and the last stored position.
export const POWER_UP_COLLECTION_TOLERANCE_METERS = 30;
const STORED_POSITION_FRESHNESS_MS = 30_000;

interface PowerUpDoc {
  id?: string;
  type?: string;
  location?: GeoPoint;
  collectedBy?: string | null;
  activatedAt?: Timestamp | null;
}

export function roleMayUsePowerUp(role: string | null, type: string): boolean {
  if (role === "hunter") return HUNTER_POWER_UP_TYPES.includes(type);
  if (role === "chicken") return type in EFFECT_DURATION_SECONDS && !HUNTER_POWER_UP_TYPES.includes(type);
  return false;
}

export function isWithinCollectionRange(
  powerUp: { lat: number; lng: number },
  player: { lat: number; lng: number }
): boolean {
  const distance = haversineDistance(powerUp.lat, powerUp.lng, player.lat, player.lng);
  return distance <= POWER_UP_COLLECTION_RADIUS_METERS + POWER_UP_COLLECTION_TOLERANCE_METERS;
}

function requireCoordinate(value: unknown, field: string, limit: number): number {
  if (typeof value !== "number" || !Number.isFinite(value) || Math.abs(value) > limit) {
    throw apiError("invalid-argument", "invalidArgument", `${field} must be a valid coordinate`);
  }
  return value;
}

function assertLiveGame(gameData: FirebaseFirestore.DocumentData): void {
  if (gameData.status !== "inProgress") {
    throw apiError("failed-precondition", "notInProgress", "Game is not in progress");
  }
  const end = (gameData.timing as { end?: Timestamp } | undefined)?.end;
  if (end && end.toMillis() <= Date.now()) {
    throw apiError("failed-precondition", "gameOver", "Game has already ended");
  }
}

async function storedPosition(
  gameId: string,
  uid: string,
  role: string | null
): Promise<{ lat: number; lng: number } | null> {
  const path = role === "chicken"
    ? `/games/${gameId}/chickenLocations/latest`
    : `/games/${gameId}/hunterLocations/${uid}`;
  const value = (await getDatabase().ref(path).get()).val() as { lat?: number; lng?: number; ts?: number } | null;
  if (typeof value?.lat !== "number" || typeof value?.lng !== "number" || typeof value?.ts !== "number") return null;
  if (Date.now() - value.ts > STORED_POSITION_FRESHNESS_MS) return null;
  return { lat: value.lat, lng: value.lng };
}

/**
 * Collects a power-up for the caller. The client position must be within
 * range, and so must the last stored position when the player shares one.
 */
export const collectPowerUp = onCall(CALLABLE_OPTIONS, async (request) => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const powerUpId = requireString(request.data?.powerUpId, "powerUpId");
  const player = {
    lat: requireCoordinate(request.data?.lat, "lat", 90),
    lng: requireCoordinate(request.data?.lng, "lng", 180),
  };

  const gameRef = db().collection("games").doc(gameId);
  const puRef = gameRef.collection("powerUps").doc(powerUpId);
  const preGame = (await gameRef.get()).data();
  if (!preGame) throw apiError("not-found", "gameNotFound", "Game not found");
  const stored = await storedPosition(gameId, uid, roleOf(preGame, uid));

  await db().runTransaction(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) throw apiError("not-found", "gameNotFound", "Game not found");
    const gameData = gameSnap.data() ?? {};
    assertLiveGame(gameData);

    const puSnap = await tx.get(puRef);
    if (!puSnap.exists) throw apiError("not-found", "powerUpNotFound", "Power-up not found");
    const pu = puSnap.data() as PowerUpDoc;
    if (pu.collectedBy) throw apiError("failed-precondition", "powerUpTaken", "Power-up already collected");
    const type = typeof pu.type === "string" ? pu.type : "";
    if (!roleMayUsePowerUp(roleOf(gameData, uid), type)) {
      throw apiError("permission-denied", "powerUpWrongRole", "This power-up belongs to the other role");
    }
    if (!pu.location) throw apiError("failed-precondition", "powerUpNotFound", "Power-up has no location");
    const target = { lat: pu.location.latitude, lng: pu.location.longitude };
    if (!isWithinCollectionRange(target, player) || (stored && !isWithinCollectionRange(target, stored))) {
      throw apiError("failed-precondition", "powerUpTooFar", "Too far from the power-up");
    }
    tx.update(puRef, { collectedBy: uid, collectedAt: Timestamp.now() });
  });

  logger.info("[powerUp] collected", { gameId, powerUpId, uid });
  return { success: true };
});

interface ActivatePowerUpResult {
  activatedAt: number;
  expiresAt: number | null;
}

export const activatePowerUp = onCall(CALLABLE_OPTIONS, async (request): Promise<ActivatePowerUpResult> => {
  const uid = requireUid(request);
  const gameId = requireString(request.data?.gameId, "gameId");
  const powerUpId = requireString(request.data?.powerUpId, "powerUpId");

  const gameRef = db().collection("games").doc(gameId);
  const puRef = gameRef.collection("powerUps").doc(powerUpId);

  const { result, type: activatedType } = await db().runTransaction(async (tx) => {
    const gameSnap = await tx.get(gameRef);
    if (!gameSnap.exists) throw apiError("not-found", "gameNotFound", "Game not found");
    const gameData = gameSnap.data() ?? {};
    assertLiveGame(gameData);

    const puSnap = await tx.get(puRef);
    if (!puSnap.exists) throw apiError("not-found", "powerUpNotFound", "Power-up not found");
    const pu = puSnap.data() as PowerUpDoc;

    // Same answer whether uncollected or owned by someone else.
    if (pu.collectedBy !== uid) {
      throw apiError("permission-denied", "notAllowed", "Only the collector can activate this power-up");
    }
    if (pu.activatedAt) {
      throw apiError("failed-precondition", "powerUpAlreadyActive", "Power-up already activated");
    }
    const type = typeof pu.type === "string" ? pu.type : "";
    if (!(type in EFFECT_DURATION_SECONDS)) {
      throw apiError("failed-precondition", "powerUpNotFound", `Unknown power-up type: ${type}`);
    }
    if (!roleMayUsePowerUp(roleOf(gameData, uid), type)) {
      throw apiError("permission-denied", "powerUpWrongRole", "This power-up belongs to the other role");
    }
    const durationSeconds = EFFECT_DURATION_SECONDS[type];

    const now = Timestamp.now();
    const expiresAt =
      durationSeconds === null ? null : Timestamp.fromMillis(now.toMillis() + durationSeconds * 1000);

    tx.update(puRef, {
      activatedAt: now,
      expiresAt: expiresAt ?? FieldValue.delete(),
    });
    // zonePreview is personal: no game-level effect.
    if (expiresAt !== null) {
      tx.update(gameRef, { [`powerUps.activeEffects.${type}`]: expiresAt });
    }

    return {
      result: { activatedAt: now.toMillis(), expiresAt: expiresAt?.toMillis() ?? null },
      type,
    };
  });

  // The hunters' RTDB read of the chicken depends on the mirrored ping window.
  if (activatedType === "radarPing") {
    await mirrorGameMetaInline(gameId, (await gameRef.get()).data());
  }
  logger.info("[powerUp] activated", { gameId, powerUpId, uid, expiresAt: result.expiresAt });
  return result;
});
