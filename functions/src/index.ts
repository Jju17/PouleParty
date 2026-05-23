import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, GeoPoint, Timestamp } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { defineSecret } from "firebase-functions/params";
import {
  deterministicDriftCenterServer,
  filterEnabledTypesServer,
  generatePowerUpsServer,
  SpawnedPowerUp,
} from "./powerUpSpawn";
import { snapToRoad } from "./mapbox";

// Re-export the GameMaster Cloud Functions (PP-70) so they get deployed
// alongside `index.ts` via `firebase deploy --only functions`.
export {
  setGameMasterPassword,
  clearGameMasterPassword,
  joinAsGameMaster,
} from "./gameMaster";

// Re-export the zone-configuration callable (PP-69) so the wizard
// recap step can fetch a server-computed zone instead of recomputing
// it client-side.
export { computeZoneConfiguration } from "./zoneCalculation";

// Re-export the PP-52 event registration handlers (Stripe-backed web
// inscription flow). The form posts to `createPendingRegistration`;
// Stripe pings `confirmRegistrationPayment` once the checkout
// session completes. The handlers are entirely web-facing: the
// mobile binaries no longer call any registration callable
// (compliance with Apple 3.1.1 — see RELEASE_NOTES 1.13.1).
export {
  createPendingRegistration,
  confirmRegistrationPayment,
} from "./registrations";

// AND-H6 (store-audit 2026-05-18): self-service account-deletion request
// backing `pouleparty.be/delete-account`. Replaces the previous
// mailto-only fallback that Google Play 2024+ rejects at upload.
export { processAccountDeletion } from "./accountDeletion";

// CRIT-3 (audit 2026-05-17): server-authoritative winner submission.
// Replaces the client-side `addWinner` arrayUnion which previously let
// any authenticated user self-declare victory by writing to the
// `winners` field directly.
// CRIT-2 (audit 2026-05-17): `getFoundCode` lets the chicken fetch its
// 4-digit code now that it's stored server-side only in
// /private/security (no longer leaked on the public Game doc).
export { submitFoundCode, getFoundCode } from "./gameplay";

// Use Application Default Credentials so each deployed function
// writes to the project it was deployed to. The previous hardcoded
// `service-account.json` was always pointing at prod, which made
// every staging-deployed function read/write prod Firestore. ADC
// resolves to the compute service account of the host project
// automatically — both staging (847523524308-compute@…) and prod
// (1047338092854-compute@…) already have the Editor role they need.
// The seedChallenges.ts script still uses the explicit SA file
// (cross-project admin tooling, not deployed).
initializeApp();

const REGION = "europe-west1";
const db = getFirestore();

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
    // PP-40: catch the race where transitionGameStatus is itself a Cloud Task
    // scheduled at timing.end and a concurrent zone_shrink (or last-tick
    // notif) fires at the same instant. Without this gate, the notif sees
    // status === "inProgress" (transition hasn't run yet) and goes out
    // milliseconds after the user already saw "Game Over".
    const endTimestamp = (game.timing as { end?: Timestamp } | undefined)?.end?.toDate();
    if (endTimestamp && endTimestamp.getTime() <= Date.now()) return;

    let userIds: string[];

    switch (notificationType) {
      case "chicken_start":
        // Notifies the player currently designated as the chicken
        // (PP-26 — may be a hunter the GM picked, not the creator).
        userIds = game.chickenId ? [game.chickenId as string] : [];
        break;
      case "hunter_start":
        userIds = (game.hunterIds as string[]) ?? [];
        break;
      case "zone_shrink":
        userIds = [
          ...(game.chickenId ? [game.chickenId as string] : []),
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
    // CRIT-11 (audit 2026-05-17): atomic compare-and-set. Without the
    // transaction, a client write (e.g. chicken cancels → status: "done")
    // between read and update would be silently clobbered back to
    // targetStatus, resurrecting a finished game.
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) return;
      if (snap.data()?.status === expectedCurrentStatus) {
        tx.update(ref, { status: targetStatus });
      }
    });
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
    // Drift is now independent per shrink: the center for batch N is
    // sampled directly from `disk(initial, R₀ − r_N) ∩ disk(final,
    // r_N − FINAL − safety)` using only (seed, newRadius). No chain
    // walk needed — callers don't know previous steps' centers either,
    // because the algo no longer depends on them.
    spawnCenter = deterministicDriftCenterServer(
      { latitude: initialCenter.latitude, longitude: initialCenter.longitude },
      initialRadius,
      currentRadius,
      driftSeed,
      finalCenter
        ? { latitude: finalCenter.latitude, longitude: finalCenter.longitude }
        : undefined
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

  // HIGH-1 (audit 2026-05-17): per-point fallback instead of fail-the-batch.
  // The previous Promise.all rejected the whole batch on the first
  // Mapbox failure; during a 5 min Mapbox outage that meant 45 retries
  // per game and zero power-ups spawned. Promise.allSettled + raw
  // coordinate fallback degrades gracefully — players get power-ups,
  // they just aren't snapped to the road for that batch.
  const snapResults = await Promise.allSettled(
    generated.map((pu) =>
      snapToRoad(pu.location.latitude, pu.location.longitude, mapboxToken)
    )
  );
  const snapped: SpawnedPowerUp[] = generated.map((pu, i) => {
    const result = snapResults[i];
    if (result.status === "fulfilled") {
      return { ...pu, location: new GeoPoint(result.value.latitude, result.value.longitude) };
    }
    console.warn(
      `[spawn] snap failed for ${pu.id}, using raw coord:`,
      result.reason instanceof Error ? result.reason.message : String(result.reason)
    );
    return pu;
  });

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
 * Enqueues all lifecycle Cloud Tasks (status transitions, notifications,
 * power-up batches) for a game that is ready to be played.
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

  // CRIT-10 (audit 2026-05-17): track every enqueued task ID per queue
  // so `onGameDeleted` can call the Cloud Tasks REST API to delete them.
  // Without this, deleting a game in `waiting` left up to 150 dangling
  // tasks that kept firing (each handler early-returned on `!doc.exists`
  // but still paid the invocation + Firestore read + Cloud Tasks retry
  // cost, and bloated queue depth).
  const enqueuedTasksByQueue: Record<string, string[]> = {
    transitionGameStatus: [],
    sendGameNotification: [],
    spawnPowerUpBatch: [],
  };

  // Schedule waiting → inProgress at startTimestamp
  if (startTimestamp) {
    const id = `status-start-${gameId}`;
    await statusQueue.enqueue(
      {
        gameId,
        targetStatus: "inProgress",
        expectedCurrentStatus: "waiting",
      },
      { scheduleTime: startTimestamp, id }
    );
    enqueuedTasksByQueue.transitionGameStatus.push(id);

    // Schedule chicken_start notification
    const chickenStartId = `notif-chickenstart-${gameId}`;
    await notifQueue.enqueue(
      { gameId, notificationType: "chicken_start" },
      { scheduleTime: startTimestamp, id: chickenStartId }
    );
    enqueuedTasksByQueue.sendGameNotification.push(chickenStartId);

    // Schedule the initial power-up batch at game start — not earlier,
    // so power-ups don't sit in Firestore while the game is still in
    // `waiting` state (which could be hours or days for a scheduled game).
    const initialSpawnId = `spawn-${gameId}-0`;
    await spawnQueue.enqueue(
      {
        gameId,
        batchIndex: 0,
        count: POWER_UP_INITIAL_BATCH_SIZE,
      },
      { scheduleTime: startTimestamp, id: initialSpawnId }
    );
    enqueuedTasksByQueue.spawnPowerUpBatch.push(initialSpawnId);
  }

  // Schedule inProgress → done at endTimestamp
  if (endTimestamp) {
    const id = `status-end-${gameId}`;
    await statusQueue.enqueue(
      {
        gameId,
        targetStatus: "done",
        expectedCurrentStatus: "inProgress",
      },
      { scheduleTime: endTimestamp, id }
    );
    enqueuedTasksByQueue.transitionGameStatus.push(id);
  }

  // Compute hunterStartDate = startTimestamp + chickenHeadStartMinutes
  if (startTimestamp && endTimestamp) {
    const hunterStartDate = new Date(
      startTimestamp.getTime() + headStartMinutes * 60 * 1000
    );

    // Schedule hunter_start notification
    const hunterStartId = `notif-hunterstart-${gameId}`;
    await notifQueue.enqueue(
      { gameId, notificationType: "hunter_start" },
      { scheduleTime: hunterStartDate, id: hunterStartId }
    );
    enqueuedTasksByQueue.sendGameNotification.push(hunterStartId);

    // Schedule zone_shrink notifications at each interval after hunterStartDate
    // Cap at 100 to prevent scheduling an unreasonable number of tasks
    const MAX_SHRINK_NOTIFICATIONS = 100;
    const intervalMs = shrinkIntervalMinutes * 60 * 1000;
    let shrinkTime = new Date(hunterStartDate.getTime() + intervalMs);
    let shrinkCount = 0;

    while (shrinkTime < endTimestamp && shrinkCount < MAX_SHRINK_NOTIFICATIONS) {
      const id = `notif-shrink-${gameId}-${shrinkCount}`;
      await notifQueue.enqueue(
        { gameId, notificationType: "zone_shrink" },
        { scheduleTime: shrinkTime, id }
      );
      enqueuedTasksByQueue.sendGameNotification.push(id);
      shrinkTime = new Date(shrinkTime.getTime() + intervalMs);
      shrinkCount++;
    }

    // Schedule periodic power-up batches at each shrink boundary. The
    // batchIndex starts at 1 (batch 0 was the inline initial spawn above).
    const spawnCount = Math.min(shrinkCount, MAX_POWER_UP_SHRINK_BATCHES);
    let spawnTime = new Date(hunterStartDate.getTime() + intervalMs);
    for (let batchIndex = 1; batchIndex <= spawnCount; batchIndex++) {
      const id = `spawn-${gameId}-${batchIndex}`;
      await spawnQueue.enqueue(
        {
          gameId,
          batchIndex,
          count: POWER_UP_PERIODIC_BATCH_SIZE,
        },
        { scheduleTime: spawnTime, id }
      );
      enqueuedTasksByQueue.spawnPowerUpBatch.push(id);
      spawnTime = new Date(spawnTime.getTime() + intervalMs);
    }
  }

  // CRIT-10: persist the manifest so `onGameDeleted` can clean up.
  // Stored as a single doc to keep the write count low; `set` (not
  // `update`) replaces any previous manifest from a re-trigger.
  try {
    await db
      .collection("games")
      .doc(gameId)
      .collection("lifecycle")
      .doc("taskManifest")
      .set({ enqueuedTasksByQueue });
  } catch (err) {
    // Non-fatal — the tasks are still enqueued; the worst that
    // happens is a delete won't fully clean up.
    console.warn(`Failed to persist task manifest for game ${gameId}:`, err);
  }

  return true;
}

// CRIT-10 (audit 2026-05-17): clean up scheduled Cloud Tasks when a
// game doc is deleted. Reads the manifest written by
// `scheduleGameLifecycleTasks` and calls Cloud Tasks v2 REST DELETE
// for every task ID. Failures are logged but don't reject the
// trigger — partial cleanup is still better than nothing, and the
// handlers themselves early-return on `!doc.exists` so any tasks
// that survive are harmless beyond their invocation cost.
export const onGameDeleted = onDocumentDeleted(
  {
    document: "games/{gameId}",
    region: REGION,
  },
  async (event) => {
    const gameId = event.params.gameId;
    const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
    if (!project) {
      console.warn(`onGameDeleted: GCLOUD_PROJECT not set for game ${gameId}`);
      return;
    }

    // Try to read the manifest. If it's missing (game created before
    // CRIT-10 fix, or a write race) we silently skip the cleanup —
    // there's no way to know which task IDs were enqueued.
    const manifestRef = db
      .collection("games")
      .doc(gameId)
      .collection("lifecycle")
      .doc("taskManifest");
    const manifestSnap = await manifestRef.get();
    if (!manifestSnap.exists) {
      console.info(`onGameDeleted: no task manifest for game ${gameId} — nothing to cancel`);
      return;
    }
    const manifest = manifestSnap.data() as {
      enqueuedTasksByQueue?: Record<string, string[]>;
    };
    const byQueue = manifest.enqueuedTasksByQueue ?? {};

    // Lazy-load googleapis (already a dep via Sheets) to avoid a
    // cold-start hit when this trigger isn't fired.
    const { google } = await import("googleapis");
    const auth = new google.auth.GoogleAuth({
      scopes: ["https://www.googleapis.com/auth/cloud-platform"],
    });
    const tasksClient = google.cloudtasks({ version: "v2", auth });

    let deletedCount = 0;
    let failedCount = 0;
    for (const [queueId, taskIds] of Object.entries(byQueue)) {
      for (const taskId of taskIds) {
        const name = `projects/${project}/locations/${REGION}/queues/${queueId}/tasks/${taskId}`;
        try {
          await tasksClient.projects.locations.queues.tasks.delete({ name });
          deletedCount++;
        } catch (err) {
          // 404 is expected for tasks that already fired or were
          // GC'd by Cloud Tasks. Anything else is worth a warning.
          const status = (err as { code?: number }).code;
          if (status === 404) continue;
          failedCount++;
          console.warn(`onGameDeleted: failed to delete task ${name}:`, err);
        }
      }
    }
    console.info(
      `onGameDeleted ${gameId}: deleted ${deletedCount} tasks, failed ${failedCount}`
    );
  }
);

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

    // CRIT-2 (audit 2026-05-17): move foundCode off the public Game
    // doc so hunters can't read it and self-declare victory. The
    // chicken fetches it via the `getFoundCode` callable. We merge
    // into /private/security (which also holds gameMasterPassword
    // from PP-70) and null out the public field.
    const foundCodeRaw = data.foundCode;
    const foundCode = typeof foundCodeRaw === "string" ? foundCodeRaw : "";
    if (foundCode.length > 0) {
      try {
        await snap.ref
          .collection("private")
          .doc("security")
          .set({ foundCode }, { merge: true });
        await snap.ref.update({ foundCode: "" });
      } catch (err) {
        // Failure here is bad — the public foundCode would stay
        // readable. Re-throw so Firebase retries the trigger.
        console.error(`Failed to relocate foundCode for game ${gameId}:`, err);
        throw err;
      }
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

    // Notify the chicken + all hunters when a hunter finds the chicken
    // (PP-26: the chicken may be any designated player, not the creator).
    const allUserIds = [
      ...(after.chickenId ? [after.chickenId as string] : []),
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

