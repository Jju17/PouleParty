import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onTaskDispatched } from "firebase-functions/v2/tasks";

initializeApp();

const REGION = "europe-west1";
const db = getFirestore();

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Fetch FCM tokens for a list of user IDs from /fcmTokens/{userId}.
 * Firestore `in` queries are limited to 30 items, so we batch.
 */
async function getTokensForUserIds(userIds: string[]): Promise<string[]> {
  if (userIds.length === 0) return [];

  const tokens: string[] = [];
  const batchSize = 30;

  for (let i = 0; i < userIds.length; i += batchSize) {
    const batch = userIds.slice(i, i + batchSize);
    const snap = await db
      .collection("fcmTokens")
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
 * Cleans up stale tokens automatically.
 */
async function sendNotificationToTokens(
  tokens: string[],
  titleLocKey: string,
  bodyLocKey: string,
  bodyLocArgs?: string[],
  data?: Record<string, string>
): Promise<void> {
  if (tokens.length === 0) return;

  const messaging = getMessaging();

  const response = await messaging.sendEachForMulticast({
    tokens,
    apns: {
      payload: {
        aps: {
          alert: {
            titleLocKey,
            locKey: bodyLocKey,
            locArgs: bodyLocArgs ?? [],
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
        bodyLocArgs: bodyLocArgs ?? [],
      },
    },
    data: data ?? {},
  });

  // Clean up stale / invalid tokens
  const tokensToRemove: string[] = [];
  response.responses.forEach((resp, idx) => {
    if (
      !resp.success &&
      resp.error &&
      (resp.error.code === "messaging/registration-token-not-registered" ||
        resp.error.code === "messaging/invalid-registration-token")
    ) {
      tokensToRemove.push(tokens[idx]);
    }
  });

  if (tokensToRemove.length > 0) {
    const batch = db.batch();
    const snap = await db
      .collection("fcmTokens")
      .where("token", "in", tokensToRemove.slice(0, 30))
      .get();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
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

    const tokens = await getTokensForUserIds(userIds);

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

/**
 * Firestore trigger: when a new game is created, schedule Cloud Tasks for:
 *   1. waiting → inProgress at startTimestamp
 *   2. inProgress → done at endTimestamp
 *   3. chicken_start notification at startTimestamp
 *   4. hunter_start notification at hunterStartDate
 *   5. zone_shrink notifications at each interval after hunterStartDate
 */
export const onGameCreated = onDocumentCreated(
  { document: "games/{gameId}", region: REGION },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data();
    const gameId = event.params.gameId;

    const startTimestamp = data.startTimestamp?.toDate() as Date | undefined;
    const endTimestamp = data.endTimestamp?.toDate() as Date | undefined;
    const chickenHeadStartMinutes =
      (data.chickenHeadStartMinutes as number) ?? 0;
    const radiusIntervalUpdate =
      (data.radiusIntervalUpdate as number) ?? 5;

    const statusQueue = getFunctions().taskQueue(
      `locations/${REGION}/functions/transitionGameStatus`
    );
    const notifQueue = getFunctions().taskQueue(
      `locations/${REGION}/functions/sendGameNotification`
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
        startTimestamp.getTime() + chickenHeadStartMinutes * 60 * 1000
      );

      // Schedule hunter_start notification
      await notifQueue.enqueue(
        { gameId, notificationType: "hunter_start" },
        { scheduleTime: hunterStartDate }
      );

      // Schedule zone_shrink notifications at each interval after hunterStartDate
      const intervalMs = radiusIntervalUpdate * 60 * 1000;
      let shrinkTime = new Date(hunterStartDate.getTime() + intervalMs);

      while (shrinkTime < endTimestamp) {
        await notifQueue.enqueue(
          { gameId, notificationType: "zone_shrink" },
          { scheduleTime: shrinkTime }
        );
        shrinkTime = new Date(shrinkTime.getTime() + intervalMs);
      }
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

    const winnersBefore = (before.winners as Array<{ name?: string }>) ?? [];
    const winnersAfter = (after.winners as Array<{ name?: string }>) ?? [];

    if (winnersAfter.length <= winnersBefore.length) return;

    // A new winner was added
    const newWinner = winnersAfter[winnersAfter.length - 1];
    const hunterName = newWinner.name ?? "A hunter";
    const totalHunters = ((after.hunterIds as string[]) ?? []).length;
    const remainingCount = totalHunters - winnersAfter.length;

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
