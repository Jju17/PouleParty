import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";
import { chickenIdOf, gameMastersOf, huntersOf } from "./roles";

export type GameNotificationType = "chicken_start" | "hunter_start" | "zone_shrink";

/**
 * Fetch FCM tokens for a list of user IDs from /users/{userId}.
 * Firestore `in` queries are limited to 30 items, so we batch.
 */
export async function getTokensForUserIds(userIds: string[]): Promise<string[]> {
  if (userIds.length === 0) return [];

  const tokens: string[] = [];
  const batchSize = 30;

  for (let i = 0; i < userIds.length; i += batchSize) {
    const batch = userIds.slice(i, i + batchSize);
    const snap = await db()
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
export async function sendNotificationToTokens(
  tokens: string[],
  titleLocKey: string,
  bodyLocKey: string,
  bodyLocArgs?: string[],
  data?: Record<string, string>
): Promise<void> {
  if (tokens.length === 0) {
    logger.info("[FCM] no tokens to notify, skipping", { titleLocKey });
    return;
  }

  const messaging = getMessaging();
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
      logger.error("[FCM] sendEachForMulticast failed", {
        batchStart: start,
        batchSize: batch.length,
        error: String(err),
      });
      continue;
    }

    totalSuccess += response.successCount;
    totalFailure += response.failureCount;

    response.responses.forEach((resp, idx) => {
      if (!resp.success && resp.error) {
        const token = batch[idx];
        logger.warn("[FCM] send failed for one token", {
          tokenPrefix: token.slice(0, 12),
          code: resp.error.code,
          message: resp.error.message,
        });
        if (
          resp.error.code === "messaging/registration-token-not-registered" ||
          resp.error.code === "messaging/invalid-registration-token"
        ) {
          tokensToRemove.push(token);
        }
      }
    });
  }

  logger.info("[FCM] notification sent", {
    titleLocKey,
    tokens: tokens.length,
    succeeded: totalSuccess,
    failed: totalFailure,
  });

  for (let i = 0; i < tokensToRemove.length; i += 30) {
    const tokenBatch = tokensToRemove.slice(i, i + 30);
    try {
      const batch = db().batch();
      const snap = await db()
        .collection("users")
        .where("token", "in", tokenBatch)
        .get();
      snap.docs.forEach((doc) => batch.update(doc.ref, { token: FieldValue.delete() }));
      await batch.commit();
    } catch (err) {
      logger.error("[FCM] stale token cleanup failed", { error: String(err) });
    }
  }
}

const NOTIFICATION_KEYS: Record<GameNotificationType, { title: string; body: string }> = {
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

export function recipientsFor(
  notificationType: GameNotificationType,
  game: Record<string, unknown>
): string[] {
  const chicken = chickenIdOf(game);
  switch (notificationType) {
    case "chicken_start":
      return [...(chicken ? [chicken] : []), ...gameMastersOf(game)];
    case "hunter_start":
      return huntersOf(game);
    case "zone_shrink":
      return [...(chicken ? [chicken] : []), ...huntersOf(game), ...gameMastersOf(game)];
  }
}

export const sendGameNotification = onTaskDispatched(
  {
    region: REGION,
    retryConfig: { maxAttempts: 3, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 100 },
  },
  async (req) => {
    const { gameId, notificationType, notifId } = req.data as {
      gameId: string;
      notificationType: GameNotificationType;
      notifId?: string;
    };

    const ref = db().collection("games").doc(gameId);
    const doc = await ref.get();
    if (!doc.exists) return;

    const game = doc.data()!;
    if (game.status === "done") return;
    // A zone_shrink task firing at the same instant as the end transition
    // must not notify after players already saw "Game Over".
    const endTimestamp = (game.timing as { end?: Timestamp } | undefined)?.end?.toDate();
    if (endTimestamp && endTimestamp.getTime() <= Date.now()) return;

    if (notifId) {
      const sentRef = ref.collection("lifecycle").doc("sent").collection("notifs").doc(notifId);
      const alreadySent = await db().runTransaction(async (tx) => {
        const sentSnap = await tx.get(sentRef);
        if (sentSnap.exists) return true;
        tx.set(sentRef, { sentAt: Timestamp.now(), notificationType });
        return false;
      });
      if (alreadySent) {
        logger.info("[Notif] already sent, skipping", { gameId, notifId });
        return;
      }
    }

    const userIds = recipientsFor(notificationType, game);
    const tokens = await getTokensForUserIds(userIds);
    logger.info("[Notif] sending", {
      gameId,
      notificationType,
      users: userIds.length,
      tokens: tokens.length,
    });

    const keys = NOTIFICATION_KEYS[notificationType];
    await sendNotificationToTokens(tokens, keys.title, keys.body);
  }
);
