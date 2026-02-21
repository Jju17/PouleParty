import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getFunctions } from "firebase-admin/functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onTaskDispatched } from "firebase-functions/v2/tasks";

initializeApp();

const REGION = "europe-west1";
const db = getFirestore();

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

/**
 * Firestore trigger: when a new game is created, schedule two Cloud Tasks:
 *   1. waiting → inProgress at startTimestamp
 *   2. inProgress → done at endTimestamp
 *
 * Each task fires exactly once at the right time, then disappears.
 * No polling, no recurring schedule.
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

    const queue = getFunctions().taskQueue(
      `locations/${REGION}/functions/transitionGameStatus`
    );

    // Schedule waiting → inProgress at startTimestamp
    if (startTimestamp) {
      await queue.enqueue(
        { gameId, targetStatus: "inProgress", expectedCurrentStatus: "waiting" },
        { scheduleTime: startTimestamp }
      );
    }

    // Schedule inProgress → done at endTimestamp
    if (endTimestamp) {
      await queue.enqueue(
        { gameId, targetStatus: "done", expectedCurrentStatus: "inProgress" },
        { scheduleTime: endTimestamp }
      );
    }
  }
);
