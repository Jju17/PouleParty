import { cert, initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, GeoPoint, Timestamp } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { defineString, defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions/v2";
import { createHash } from "crypto";
import { google } from "googleapis";
import {
  deterministicDriftCenterServer,
  filterEnabledTypesServer,
  generatePowerUpsServer,
  interpolateZoneCenterServer,
  SpawnedPowerUp,
} from "./powerUpSpawn";
import { snapToRoad } from "./mapbox";

// eslint-disable-next-line @typescript-eslint/no-var-requires
const serviceAccount = require("../service-account.json");
initializeApp({ credential: cert(serviceAccount) });

const REGION = "europe-west1";
const db = getFirestore();

const REGISTRATION_SHEET_ID = defineString("REGISTRATION_SHEET_ID");
const MAPBOX_ACCESS_TOKEN = defineSecret("MAPBOX_ACCESS_TOKEN");

const POWER_UP_INITIAL_BATCH_SIZE = 5;
const POWER_UP_PERIODIC_BATCH_SIZE = 2;
// Max periodic batches scheduled per game to bound Cloud Task volume.
const MAX_POWER_UP_SHRINK_BATCHES = 100;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Fetch FCM tokens for a list of user IDs from /users/{userId}.
 * Firestore `in` queries are limited to 30 items, so we batch.
 */
async function getTokensForUserIds(userIds: string[]): Promise<string[]> {
  if (userIds.length === 0) return [];

  const tokens: string[] = [];
  const batchSize = 30;

  for (let i = 0; i < userIds.length; i += batchSize) {
    const batch = userIds.slice(i, i + batchSize);
    const snap = await db
      .collection("users")
      .where("__name__", "in", batch)
      .get();

    for (const doc of snap.docs) {
      const token = doc.data().token as string | undefined;
      if (token) tokens.push(token);
    }
  }

  return tokens;
}

/**
 * Send a localised notification to a list of FCM tokens.
 * Cleans up stale tokens automatically. Splits into 500-token batches
 * because `sendEachForMulticast` rejects anything larger.
 */
async function sendNotificationToTokens(
  tokens: string[],
  titleLocKey: string,
  bodyLocKey: string,
  bodyLocArgs?: string[],
  data?: Record<string, string>
): Promise<void> {
  if (tokens.length === 0) {
    console.log(`[FCM] No tokens to notify for "${titleLocKey}" — skipping`);
    return;
  }

  const messaging = getMessaging();
  // Firebase Admin caps `sendEachForMulticast` at 500 tokens per call.
  const FCM_BATCH_LIMIT = 500;
  const tokensToRemove: string[] = [];
  let totalSuccess = 0;
  let totalFailure = 0;

  for (let start = 0; start < tokens.length; start += FCM_BATCH_LIMIT) {
    const batch = tokens.slice(start, start + FCM_BATCH_LIMIT);
    let response;
    try {
      response = await messaging.sendEachForMulticast({
        tokens: batch,
        apns: {
          payload: {
            aps: {
              alert: {
                titleLocKey,
                locKey: bodyLocKey,
                ...(bodyLocArgs && bodyLocArgs.length > 0 ? { locArgs: bodyLocArgs } : {}),
              },
              sound: "default",
            },
          },
        },
        android: {
          notification: {
            channelId: "game_events",
            sound: "default",
            titleLocKey,
            bodyLocKey,
            ...(bodyLocArgs && bodyLocArgs.length > 0 ? { bodyLocArgs } : {}),
          },
        },
        ...(data && Object.keys(data).length > 0 ? { data } : {}),
      });
    } catch (err) {
      console.error(
        `[FCM] sendEachForMulticast failed for batch ${start}..${start + batch.length}:`,
        err,
      );
      continue;
    }

    totalSuccess += response.successCount;
    totalFailure += response.failureCount;

    response.responses.forEach((resp, idx) => {
      if (!resp.success && resp.error) {
        const token = batch[idx];
        console.error(
          `[FCM] send failed for token ${token.slice(0, 12)}...: ` +
          `code=${resp.error.code} message=${resp.error.message} ` +
          `stack=${resp.error.stack ?? "none"}`
        );
        if (
          resp.error.code === "messaging/registration-token-not-registered" ||
          resp.error.code === "messaging/invalid-registration-token"
        ) {
          tokensToRemove.push(token);
        }
      }
    });
  }

  console.log(
    `[FCM] "${titleLocKey}" → ${tokens.length} tokens: ` +
    `${totalSuccess} succeeded, ${totalFailure} failed`
  );

  if (tokensToRemove.length > 0) {
    // Firestore `in` queries limited to 30, so batch the cleanup
    for (let i = 0; i < tokensToRemove.length; i += 30) {
      const tokenBatch = tokensToRemove.slice(i, i + 30);
      try {
        const batch = db.batch();
        const snap = await db
          .collection("users")
          .where("token", "in", tokenBatch)
          .get();
        snap.docs.forEach((doc) => batch.update(doc.ref, { token: FieldValue.delete() }));
        await batch.commit();
      } catch (err) {
        console.error(`[FCM] failed to cleanup stale tokens batch`, err);
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Cloud Task handler: send a game notification
// ---------------------------------------------------------------------------

export const sendGameNotification = onTaskDispatched(
  {
    region: REGION,

    retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 100 },
  },
  async (req) => {
    const { gameId, notificationType } = req.data as {
      gameId: string;
      notificationType: "chicken_start" | "hunter_start" | "zone_shrink";
    };

    const ref = db.collection("games").doc(gameId);
    const doc = await ref.get();
    if (!doc.exists) return;

    const game = doc.data()!;
    if (game.status === "done") return;

    let userIds: string[];

    switch (notificationType) {
      case "chicken_start":
        userIds = game.creatorId ? [game.creatorId] : [];
        break;
      case "hunter_start":
        userIds = (game.hunterIds as string[]) ?? [];
        break;
      case "zone_shrink":
        userIds = [
          ...(game.creatorId ? [game.creatorId] : []),
          ...((game.hunterIds as string[]) ?? []),
        ];
        break;
    }

    console.log(
      `[Notif] Game ${gameId}: sending "${notificationType}" to ${userIds.length} users`
    );

    const tokens = await getTokensForUserIds(userIds);

    console.log(
      `[Notif] Game ${gameId}: found ${tokens.length} tokens for ${userIds.length} users`
    );

    const keyMap: Record<string, { title: string; body: string }> = {
      chicken_start: {
        title: "notif_chicken_start_title",
        body: "notif_chicken_start_body",
      },
      hunter_start: {
        title: "notif_hunter_start_title",
        body: "notif_hunter_start_body",
      },
      zone_shrink: {
        title: "notif_zone_shrink_title",
        body: "notif_zone_shrink_body",
      },
    };

    const keys = keyMap[notificationType];
    await sendNotificationToTokens(tokens, keys.title, keys.body);
  }
);

// ---------------------------------------------------------------------------
// Cloud Task handler: game status transition (existing)
// ---------------------------------------------------------------------------

/**
 * Task handler: transitions a game's status if it's still in the expected state.
 * Scheduled by onGameCreated via Cloud Tasks — fires exactly once per transition.
 */
export const transitionGameStatus = onTaskDispatched(
  {
    region: REGION,

    retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 100 },
  },
  async (req) => {
    const { gameId, targetStatus, expectedCurrentStatus } = req.data as {
      gameId: string;
      targetStatus: string;
      expectedCurrentStatus: string;
    };

    const ref = db.collection("games").doc(gameId);
    const doc = await ref.get();

    if (!doc.exists) return;

    const currentStatus = doc.data()?.status;
    if (currentStatus === expectedCurrentStatus) {
      await ref.update({ status: targetStatus });
    }
  }
);

// ---------------------------------------------------------------------------
// Firestore trigger: schedule tasks when a new game is created
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Power-up spawn — server-authoritative
// ---------------------------------------------------------------------------

/**
 * Deterministic power-up generation, ported from the client
 * `generatePowerUps` (`ios/PouleParty/Components/GameLogic/PowerUpSpawnLogic.swift`
 * and `android/.../ui/gamelogic/PowerUpSpawnHelper.kt`).
 *
 * MUST produce identical positions for the same `(driftSeed, batchIndex, count, enabledTypes)` —
 * clients no longer run this logic but the math is kept identical so any future
 * parity drift is easier to debug side-by-side.
 */
// `snapToRoad` lives in `./mapbox.ts` so the retry logic can be exercised by
// unit tests with a mocked `fetch` without having to initialise firebase-admin.

/**
 * Writes an already-generated batch of power-ups to Firestore. Uses the
 * deterministic IDs from `generatePowerUpsServer` so re-runs are idempotent
 * (reschedule / retries don't produce duplicates).
 */
async function writePowerUpBatch(
  gameId: string,
  powerUps: SpawnedPowerUp[]
): Promise<void> {
  if (powerUps.length === 0) return;
  const batch = db.batch();
  const col = db.collection("games").doc(gameId).collection("powerUps");
  for (const pu of powerUps) {
    // merge:true is important — a task retry must NOT overwrite
    // collect/activation fields written by clients between attempts
    // (otherwise a retry could "resurrect" a collected power-up).
    batch.set(col.doc(pu.id), {
      id: pu.id,
      type: pu.type,
      location: pu.location,
      spawnedAt: pu.spawnedAt,
    }, { merge: true });
  }
  await batch.commit();
}

/**
 * Generates + snaps + writes one batch of power-ups for a game.
 * Reads the current game doc to pick up the latest zone center / radius /
 * enabledTypes (the zone may have shrunk since the game was created).
 */
async function spawnBatchForGame(
  gameId: string,
  batchIndex: number,
  count: number,
  mapboxToken: string
): Promise<void> {
  const gameRef = db.collection("games").doc(gameId);
  const snap = await gameRef.get();
  if (!snap.exists) {
    console.warn(`[spawn] game ${gameId} missing — skipping batch ${batchIndex}`);
    return;
  }
  const data = snap.data() as Record<string, unknown>;

  // Don't spawn on games that were cancelled/finished. Checks both the
  // status flag (set by `transitionGameStatus`) and the endDate (in case the
  // status transition task is running late). Either means "game over — stop
  // spawning anything further".
  if (data.status === "done") {
    console.log(`[spawn] game ${gameId} is done — skipping batch ${batchIndex}`);
    return;
  }
  const endTimestamp = (data.timing as { end?: FirebaseFirestore.Timestamp } | undefined)?.end?.toDate();
  if (endTimestamp && endTimestamp <= new Date()) {
    console.log(`[spawn] game ${gameId} passed endTimestamp — skipping batch ${batchIndex}`);
    return;
  }

  const powerUps = data.powerUps as
    | { enabled?: boolean; enabledTypes?: string[] }
    | undefined;
  if (!powerUps?.enabled) return;
  const gameMode = (data.gameMode as string) ?? "followTheChicken";
  const enabledTypes = filterEnabledTypesServer(powerUps.enabledTypes ?? [], gameMode);
  if (enabledTypes.length === 0) return;

  const zone = data.zone as {
    center?: GeoPoint;
    finalCenter?: GeoPoint | null;
    radius?: number;
    shrinkMetersPerUpdate?: number;
    driftSeed?: number;
  } | undefined;
  const initialCenter = zone?.center;
  const initialRadius = zone?.radius;
  const shrinkMetersPerUpdate = zone?.shrinkMetersPerUpdate;
  const driftSeed = zone?.driftSeed;
  const finalCenter = zone?.finalCenter ?? undefined;
  if (
    !initialCenter ||
    typeof initialRadius !== "number" ||
    typeof shrinkMetersPerUpdate !== "number" ||
    typeof driftSeed !== "number"
  ) {
    console.error(`[spawn] game ${gameId} missing zone data for batch ${batchIndex}`);
    return;
  }

  // Compute the center + radius that match the zone at the time this batch
  // should fire. `batchIndex = 0` = initial spawn (no shrink), N > 0 = after
  // N shrinks.
  //
  // zoneFreeze adjustment: if a freeze is active at fire time, the zone
  // hasn't actually shrunk for this batch yet — treat the zone as one
  // shrink behind schedule. Doesn't handle multiple historical freezes
  // perfectly but covers the common case (freeze active when spawn fires).
  // The NOMINAL `batchIndex` is still used for deterministic IDs / PRNG
  // so different tasks never collide on the same ID.
  const activeEffects = (data.powerUps as { activeEffects?: Record<string, FirebaseFirestore.Timestamp> } | undefined)?.activeEffects;
  const zoneFreezeExpiresAt = activeEffects?.zoneFreeze?.toDate();
  const isZoneFrozen = zoneFreezeExpiresAt !== undefined && zoneFreezeExpiresAt > new Date();
  const effectiveBatchIndex = isZoneFrozen && batchIndex > 0 ? batchIndex - 1 : batchIndex;
  const currentRadius = Math.max(
    0,
    initialRadius - effectiveBatchIndex * shrinkMetersPerUpdate
  );
  if (currentRadius <= 0) {
    console.log(`[spawn] zone has collapsed for game ${gameId} — skipping batch ${batchIndex}`);
    return;
  }

  let spawnCenter: { latitude: number; longitude: number };
  if (effectiveBatchIndex === 0) {
    // Initial batch (or first batch frozen): use the raw zone.center
    // (no drift, no chicken location yet).
    spawnCenter = { latitude: initialCenter.latitude, longitude: initialCenter.longitude };
  } else if (gameMode === "stayInTheZone") {
    // Periodic shrink center = linear interpolation toward finalCenter + seeded drift.
    const interpolated = interpolateZoneCenterServer(
      { latitude: initialCenter.latitude, longitude: initialCenter.longitude },
      finalCenter ? { latitude: finalCenter.latitude, longitude: finalCenter.longitude } : undefined,
      initialRadius,
      currentRadius
    );
    const previousRadius = currentRadius + shrinkMetersPerUpdate;
    spawnCenter = deterministicDriftCenterServer(
      interpolated,
      previousRadius,
      currentRadius,
      driftSeed
    );
  } else {
    // followTheChicken: the zone tracks the chicken's live GPS. Read the
    // latest chickenLocation doc — fall back to initial center if missing.
    const locSnap = await db
      .collection("games").doc(gameId)
      .collection("chickenLocations").doc("latest")
      .get();
    const locData = locSnap.data() as { location?: GeoPoint } | undefined;
    if (locData?.location) {
      spawnCenter = {
        latitude: locData.location.latitude,
        longitude: locData.location.longitude,
      };
    } else {
      spawnCenter = { latitude: initialCenter.latitude, longitude: initialCenter.longitude };
    }
  }

  const generated = generatePowerUpsServer(
    spawnCenter,
    currentRadius,
    count,
    driftSeed,
    batchIndex,
    enabledTypes
  );

  // Snap each location in parallel; falls back per-point on failure.
  const snapped: SpawnedPowerUp[] = await Promise.all(
    generated.map(async (pu) => {
      const s = await snapToRoad(
        pu.location.latitude,
        pu.location.longitude,
        mapboxToken
      );
      return {
        ...pu,
        location: new GeoPoint(s.latitude, s.longitude),
      };
    })
  );

  await writePowerUpBatch(gameId, snapped);
  console.log(`[spawn] wrote ${snapped.length} power-ups for game ${gameId} (batch ${batchIndex})`);
}

/**
 * Cloud Task handler: spawn one periodic (post-shrink) power-up batch.
 * Scheduled by `onGameCreated` — fires once per zone shrink.
 */
export const spawnPowerUpBatch = onTaskDispatched(
  {
    region: REGION,
    retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 50 },
    secrets: [MAPBOX_ACCESS_TOKEN],
  },
  async (req) => {
    const { gameId, batchIndex, count } = req.data as {
      gameId: string;
      batchIndex: number;
      count: number;
    };
    await spawnBatchForGame(gameId, batchIndex, count, MAPBOX_ACCESS_TOKEN.value());
  }
);

// ---------------------------------------------------------------------------

/**
 * Firestore trigger: when a new game is created, schedule Cloud Tasks for:
 *   1. waiting → inProgress at startTimestamp
 *   2. inProgress → done at endTimestamp
 *   3. chicken_start notification at startTimestamp
 *   4. hunter_start notification at hunterStartDate
 *   5. zone_shrink notifications at each interval after hunterStartDate
 *   6. spawn initial power-up batch (inline, pre-game) + schedule one
 *      `spawnPowerUpBatch` task per zone shrink.
 */
/**
 * Decides whether `onGameCreated` should schedule lifecycle tasks right now.
 * Only games created directly in `waiting` status are playable immediately
 * (free mode + redeemFreeCreation 100%-off). Forfait games start in
 * `pending_payment` and are scheduled later by `onGameUpdated` when the
 * Stripe webhook flips them to `waiting`.
 *
 * Exported so unit tests can pin the gating rule without booting Firestore.
 */
export function shouldScheduleOnCreate(status: string | undefined): boolean {
  return status === "waiting";
}

/**
 * Decides whether `onGameUpdated` should trigger lifecycle task scheduling.
 * Returns true only on a transition from a payment-limbo status to
 * `waiting` — i.e. when a Forfait game becomes playable after a successful
 * Stripe webhook (`pending_payment → waiting`) or a successful retry after a
 * failed PaymentIntent (`payment_failed → waiting`).
 *
 * Exported so unit tests can pin the transition rule without booting Firestore.
 */
export function shouldScheduleOnUpdate(
  beforeStatus: string | undefined,
  afterStatus: string | undefined,
): boolean {
  if (afterStatus !== "waiting") return false;
  return beforeStatus === "pending_payment" || beforeStatus === "payment_failed";
}

/**
 * Pure helper: given a list of game doc refs + their data, return those
 * eligible for purge under a scheduled cleanup job. A game is eligible if
 * its status is in `staleStatuses` AND its `lastHeartbeat` is older than
 * `cutoffMs`. Exported for unit tests.
 */
export function selectStaleGamesForPurge(
  games: Array<{ id: string; status?: string; lastHeartbeatMs?: number }>,
  staleStatuses: ReadonlyArray<string>,
  cutoffMs: number,
): string[] {
  return games
    .filter(
      (g) =>
        typeof g.status === "string" &&
        staleStatuses.includes(g.status) &&
        typeof g.lastHeartbeatMs === "number" &&
        g.lastHeartbeatMs < cutoffMs,
    )
    .map((g) => g.id);
}

/**
 * Enqueues all lifecycle Cloud Tasks (status transitions, notifications,
 * power-up batches) for a game that is ready to be played. Extracted so both
 * `onGameCreated` (for free / 100%-off-redeemed games, which land directly
 * in `waiting`) and `onGameUpdated` (for Forfait games whose Stripe webhook
 * just flipped `pending_payment → waiting`) can call the exact same path.
 *
 * Returns true if tasks were scheduled, false if the game was rejected due
 * to a validation error (timing past, etc.).
 */
async function scheduleGameLifecycleTasks(
  gameId: string,
  data: FirebaseFirestore.DocumentData,
): Promise<boolean> {
  const timing = data.timing as { start?: FirebaseFirestore.Timestamp; end?: FirebaseFirestore.Timestamp; headStartMinutes?: number } | undefined;
  const zone = data.zone as { shrinkIntervalMinutes?: number } | undefined;

  const startTimestamp = timing?.start?.toDate() as Date | undefined;
  const endTimestamp = timing?.end?.toDate() as Date | undefined;
  const headStartMinutes = (timing?.headStartMinutes as number) ?? 0;
  const shrinkIntervalMinutes = (zone?.shrinkIntervalMinutes as number) ?? 5;

  // Validate inputs to prevent infinite loops or invalid scheduling
  if (shrinkIntervalMinutes < 1) {
    console.error(`Invalid shrinkIntervalMinutes (${shrinkIntervalMinutes}) for game ${gameId}`);
    return false;
  }
  if (startTimestamp && endTimestamp && startTimestamp >= endTimestamp) {
    console.error(`startTimestamp >= endTimestamp for game ${gameId}`);
    return false;
  }
  if (headStartMinutes < 0) {
    console.error(`Negative headStartMinutes (${headStartMinutes}) for game ${gameId}`);
    return false;
  }
  // Cloud Tasks silently auto-fires tasks scheduled in the past, which
  // avalanches the whole lifecycle (notifs + power-up spawns + status
  // transitions) the instant the doc is created. Reject stale game docs
  // so the creator sees a clear failure instead of a ghost game.
  const now = Date.now();
  const PAST_THRESHOLD_MS = 60 * 1000;
  if (startTimestamp && startTimestamp.getTime() < now - PAST_THRESHOLD_MS) {
    console.error(
      `Refusing to schedule tasks for game ${gameId}: startTimestamp (${startTimestamp.toISOString()}) is in the past`,
    );
    return false;
  }
  if (endTimestamp && endTimestamp.getTime() < now - PAST_THRESHOLD_MS) {
    console.error(
      `Refusing to schedule tasks for game ${gameId}: endTimestamp (${endTimestamp.toISOString()}) is in the past`,
    );
    return false;
  }

  const statusQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/transitionGameStatus`
  );
  const notifQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/sendGameNotification`
  );
  const spawnQueue = getFunctions().taskQueue(
    `locations/${REGION}/functions/spawnPowerUpBatch`
  );

  // Schedule waiting → inProgress at startTimestamp
  if (startTimestamp) {
    await statusQueue.enqueue(
      {
        gameId,
        targetStatus: "inProgress",
        expectedCurrentStatus: "waiting",
      },
      { scheduleTime: startTimestamp }
    );

    // Schedule chicken_start notification
    await notifQueue.enqueue(
      { gameId, notificationType: "chicken_start" },
      { scheduleTime: startTimestamp }
    );

    // Schedule the initial power-up batch at game start — not earlier,
    // so power-ups don't sit in Firestore while the game is still in
    // `waiting` state (which could be hours or days for a scheduled game).
    await spawnQueue.enqueue(
      {
        gameId,
        batchIndex: 0,
        count: POWER_UP_INITIAL_BATCH_SIZE,
      },
      { scheduleTime: startTimestamp }
    );
  }

  // Schedule inProgress → done at endTimestamp
  if (endTimestamp) {
    await statusQueue.enqueue(
      {
        gameId,
        targetStatus: "done",
        expectedCurrentStatus: "inProgress",
      },
      { scheduleTime: endTimestamp }
    );
  }

  // Compute hunterStartDate = startTimestamp + chickenHeadStartMinutes
  if (startTimestamp && endTimestamp) {
    const hunterStartDate = new Date(
      startTimestamp.getTime() + headStartMinutes * 60 * 1000
    );

    // Schedule hunter_start notification
    await notifQueue.enqueue(
      { gameId, notificationType: "hunter_start" },
      { scheduleTime: hunterStartDate }
    );

    // Schedule zone_shrink notifications at each interval after hunterStartDate
    // Cap at 100 to prevent scheduling an unreasonable number of tasks
    const MAX_SHRINK_NOTIFICATIONS = 100;
    const intervalMs = shrinkIntervalMinutes * 60 * 1000;
    let shrinkTime = new Date(hunterStartDate.getTime() + intervalMs);
    let shrinkCount = 0;

    while (shrinkTime < endTimestamp && shrinkCount < MAX_SHRINK_NOTIFICATIONS) {
      await notifQueue.enqueue(
        { gameId, notificationType: "zone_shrink" },
        { scheduleTime: shrinkTime }
      );
      shrinkTime = new Date(shrinkTime.getTime() + intervalMs);
      shrinkCount++;
    }

    // Schedule periodic power-up batches at each shrink boundary. The
    // batchIndex starts at 1 (batch 0 was the inline initial spawn above).
    const spawnCount = Math.min(shrinkCount, MAX_POWER_UP_SHRINK_BATCHES);
    let spawnTime = new Date(hunterStartDate.getTime() + intervalMs);
    for (let batchIndex = 1; batchIndex <= spawnCount; batchIndex++) {
      await spawnQueue.enqueue(
        {
          gameId,
          batchIndex,
          count: POWER_UP_PERIODIC_BATCH_SIZE,
        },
        { scheduleTime: spawnTime }
      );
      spawnTime = new Date(spawnTime.getTime() + intervalMs);
    }
  }

  return true;
}

export const onGameCreated = onDocumentCreated(
  {
    document: "games/{gameId}",
    region: REGION,
    secrets: [MAPBOX_ACCESS_TOKEN],
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data();
    const gameId = event.params.gameId;
    const status = data.status as string | undefined;

    // Forfait games are created in `pending_payment` and only become playable
    // after the Stripe webhook flips them to `waiting` — don't schedule
    // anything yet. `onGameUpdated` catches the transition and schedules at
    // that point. This avoids burning ~10-100 no-op Cloud Tasks per game
    // whose PaymentSheet is cancelled.
    if (!shouldScheduleOnCreate(status)) {
      logger.info(
        `onGameCreated: game ${gameId} created in status "${status}", deferring task scheduling until it becomes "waiting"`,
      );
      return;
    }

    try {
      await scheduleGameLifecycleTasks(gameId, data);
    } catch (error) {
      console.error(`Failed to schedule tasks for game ${gameId}:`, error);
      throw error;
    }
  }
);

// ---------------------------------------------------------------------------
// Firestore trigger: detect new winners
// ---------------------------------------------------------------------------

/**
 * Firestore trigger: when a game document is updated, check if a new winner
 * was added and notify all players.
 */
export const onGameUpdated = onDocumentUpdated(
  { document: "games/{gameId}", region: REGION },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const gameId = event.params.gameId;

    // Stripe webhook flipped the game from `pending_payment` (or
    // `payment_failed` retry) to `waiting` — this is the point at which the
    // game actually becomes playable, so this is when we schedule its
    // lifecycle Cloud Tasks. Scheduling earlier (in `onGameCreated`) would
    // burn queue slots for games that get cancelled.
    const beforeStatus = before.status as string | undefined;
    const afterStatus = after.status as string | undefined;
    if (shouldScheduleOnUpdate(beforeStatus, afterStatus)) {
      try {
        await scheduleGameLifecycleTasks(gameId, after);
        logger.info(`onGameUpdated: scheduled lifecycle tasks for game ${gameId} after payment confirmation`);
      } catch (error) {
        console.error(`Failed to schedule tasks for paid game ${gameId}:`, error);
        throw error;
      }
    }

    type WinnerShape = { hunterId?: string; hunterName?: string; timestamp?: unknown };
    const winnersBefore = (before.winners as Array<WinnerShape>) ?? [];
    const rawWinnersAfter = (after.winners as Array<WinnerShape>) ?? [];

    // ── Dedup by hunterId ──────────────────────────────────────────
    // Firestore's `arrayUnion` does NOT dedupe objects — two writes
    // from the same hunter with different `timestamp` values produce
    // two entries. That inflates `winners.length` past `hunterIds.length`
    // and would end the game early. The clients already debounce at
    // the UI layer; this is the server-side safety net.
    const seenHunterIds = new Set<string>();
    const winnersAfter: WinnerShape[] = [];
    let hadDuplicate = false;
    for (const w of rawWinnersAfter) {
      const hid = w.hunterId;
      if (!hid) {
        // Winners without a hunterId are malformed — drop them.
        hadDuplicate = true;
        continue;
      }
      if (seenHunterIds.has(hid)) {
        hadDuplicate = true;
        continue;
      }
      seenHunterIds.add(hid);
      winnersAfter.push(w);
    }
    if (hadDuplicate && event.data) {
      // Write the deduped array back. The resulting onGameUpdated
      // callback will see no duplicates and exit via the early-return
      // below (length comparison) — no infinite loop.
      await event.data.after.ref.update({ winners: winnersAfter });
    }

    // Count genuinely-new hunters (by id), not array-length deltas —
    // otherwise a dedup write by this function would look like "winners
    // count went down" and we'd always early-return.
    const beforeIds = new Set(
      winnersBefore.map((w) => w.hunterId).filter((v): v is string => !!v)
    );
    const newWinners = winnersAfter.filter(
      (w) => w.hunterId && !beforeIds.has(w.hunterId)
    );
    if (newWinners.length === 0) return;

    // Don't send notifications for finished games
    if (after.status === "done") return;

    const newWinner = newWinners[newWinners.length - 1];
    const hunterName = (newWinner.hunterName ?? "A hunter").slice(0, 50);
    const totalHunters = ((after.hunterIds as string[]) ?? []).length;
    const remainingCount = Math.max(0, totalHunters - winnersAfter.length);

    const allUserIds = [
      ...(after.creatorId ? [after.creatorId] : []),
      ...((after.hunterIds as string[]) ?? []),
    ];
    const tokens = await getTokensForUserIds(allUserIds);

    await sendNotificationToTokens(
      tokens,
      "notif_hunter_found_title",
      "notif_hunter_found_body",
      [hunterName, String(remainingCount)],
      { gameId: event.params.gameId }
    );
  }
);

// ---------------------------------------------------------------------------
// Callable: register for an event (server-side validation, dedup, anti-spam)
// ---------------------------------------------------------------------------

const MAX_REGISTRATIONS = 35;

export const registerForEvent = onCall(
  { region: REGION },
  async (request) => {
    const { firstName, lastName, email, gsm, willingToPay, comment } = request.data as {
      firstName?: string;
      lastName?: string;
      email?: string;
      gsm?: string;
      willingToPay?: string;
      comment?: string;
    };

    // --- Validation ---
    if (
      !firstName?.trim() ||
      !lastName?.trim() ||
      !email?.trim() ||
      !gsm?.trim() ||
      !willingToPay?.trim()
    ) {
      throw new HttpsError("invalid-argument", "All fields are required.");
    }

    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(email.trim())) {
      throw new HttpsError("invalid-argument", "Invalid email format.");
    }

    const gsmRegex = /^(\+\d{1,4}|0)\s*[1-9][\d\s.\-]{6,}$/;
    if (!gsmRegex.test(gsm.trim())) {
      throw new HttpsError("invalid-argument", "Invalid phone number format.");
    }

    const cleanFirst = firstName.trim();
    const cleanLast = lastName.trim();
    const cleanEmail = email.trim();
    const rawGsm = gsm.trim().startsWith("0")
      ? "+32" + gsm.trim().slice(1)
      : gsm.trim();
    const cleanGsm = "+" + rawGsm.replace(/[^\d]/g, "");
    const cleanWTP = willingToPay.trim();
    const cleanComment = (comment ?? "").trim();

    // --- Atomic duplicate + capacity check ---
    const docId = createHash("sha256").update(cleanEmail.toLowerCase()).digest("hex");
    const ref = db.collection("registrations").doc(docId);
    const now = new Date();

    await db.runTransaction(async (transaction) => {
      const existingDoc = await transaction.get(ref);
      if (existingDoc.exists) {
        throw new HttpsError("already-exists", "This email is already registered.");
      }

      transaction.set(ref, {
        firstName: cleanFirst,
        lastName: cleanLast,
        email: cleanEmail,
        gsm: cleanGsm,
        willingToPay: cleanWTP,
        comment: cleanComment,
        event: "2026-04-23",
        createdAt: now,
      });
    });

    // --- Append to Google Sheet ---
    const auth = new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/spreadsheets"],
    });
    const sheets = google.sheets({ version: "v4", auth });

    try {
      await sheets.spreadsheets.values.append({
        spreadsheetId: REGISTRATION_SHEET_ID.value(),
        range: "A:H",
        valueInputOption: "USER_ENTERED",
        requestBody: {
          values: [[cleanFirst, cleanLast, cleanEmail, cleanGsm, cleanWTP, cleanComment, "2026-04-23", now.toISOString()]],
        },
      });
    } catch (error) {
      console.error("Failed to append registration to Google Sheet:", error);
      // Firestore write succeeded, so don't throw — but log for manual reconciliation
    }

    return { success: true };
  }
);

// ---------------------------------------------------------------------------
// Callable: get registration count
// ---------------------------------------------------------------------------

export const getRegistrationCount = onCall(
  { region: REGION },
  async () => {
    const countSnap = await db.collection("registrations").count().get();
    return { count: countSnap.data().count, max: MAX_REGISTRATIONS };
  }
);

// ---------------------------------------------------------------------------
// Stripe (creator/hunter payments, promo codes, webhook)
// ---------------------------------------------------------------------------

export {
  createCreatorPaymentSheet,
  createHunterPaymentSheet,
  validatePromoCode,
  redeemFreeCreation,
  stripeWebhook,
} from "./stripe";

// ---------------------------------------------------------------------------
// Scheduled: purge abandoned pending_payment games
// ---------------------------------------------------------------------------

/**
 * Forfait games are pre-created server-side in `pending_payment` status,
 * then flipped to `waiting` by the Stripe webhook handler. If the webhook
 * never arrives (Stripe outage, dropped event, signature mismatch caused
 * by a rotated secret, etc.), the game doc is stranded in `pending_payment`
 * forever. Users can't join, the creator can't see a failure, and these
 * docs accumulate silently.
 *
 * Run daily, delete games that have been `pending_payment` for more than
 * 24 h. 24 h is a generous window: Stripe retries webhooks for up to 3
 * days, but a real delivery happens in seconds. A game still pending after
 * 24 h is either truly abandoned (user never completed the sheet) or
 * broken (webhook permanently lost).
 *
 * Idempotent by construction: each call requeries + deletes the same set
 * if re-run.
 */
export const cleanupAbandonedPendingGames = onSchedule(
  {
    schedule: "every 24 hours",
    region: REGION,
    timeZone: "Europe/Brussels",
  },
  async () => {
    const cutoff = Timestamp.fromMillis(Date.now() - 24 * 60 * 60 * 1000);

    // Purge both `pending_payment` AND `payment_failed` orphans. Both states
    // are terminal-limbo for the client: the game can never transition to
    // `waiting` without a successful webhook, and the user has no way to
    // resume from them in the UI. 24 h covers Stripe retry delivery while
    // leaving a comfortable margin for a genuinely-resumed payment flow.
    async function purge(status: "pending_payment" | "payment_failed") {
      const snap = await db
        .collection("games")
        .where("status", "==", status)
        .where("lastHeartbeat", "<", cutoff)
        .limit(500)
        .get();

      if (snap.empty) return 0;

      const batch = db.batch();
      for (const doc of snap.docs) {
        batch.delete(doc.ref);
      }
      await batch.commit();
      return snap.size;
    }

    const deletedPending = await purge("pending_payment");
    const deletedFailed = await purge("payment_failed");
    const total = deletedPending + deletedFailed;

    if (total === 0) {
      logger.info("cleanupAbandonedPendingGames: nothing to delete");
      return;
    }
    logger.info(
      `cleanupAbandonedPendingGames: deleted ${deletedPending} pending_payment + ${deletedFailed} payment_failed games`,
    );
  },
);
