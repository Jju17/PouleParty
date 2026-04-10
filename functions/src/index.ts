import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineString } from "firebase-functions/params";
import { createHash } from "crypto";
import { google } from "googleapis";

initializeApp();

const REGION = "europe-west1";
const db = getFirestore();

const REGISTRATION_SHEET_ID = defineString("REGISTRATION_SHEET_ID");

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
        resp.error.code === "messaging/invalid-registration-token" ||
        resp.error.code === "messaging/mismatched-credential" ||
        resp.error.code === "messaging/third-party-auth-error")
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

    // Validate inputs to prevent infinite loops or invalid scheduling
    if (radiusIntervalUpdate <= 0) {
      console.error(`Invalid radiusIntervalUpdate (${radiusIntervalUpdate}) for game ${gameId}`);
      return;
    }
    if (startTimestamp && endTimestamp && startTimestamp >= endTimestamp) {
      console.error(`startTimestamp >= endTimestamp for game ${gameId}`);
      return;
    }
    if (chickenHeadStartMinutes < 0) {
      console.error(`Negative chickenHeadStartMinutes (${chickenHeadStartMinutes}) for game ${gameId}`);
      return;
    }

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
    const hunterName = ((newWinner.name as string) ?? "A hunter").slice(0, 50);
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

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
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

      const countSnap = await db.collection("registrations").count().get();
      if (countSnap.data().count >= MAX_REGISTRATIONS) {
        throw new HttpsError("resource-exhausted", "Event is full.");
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
