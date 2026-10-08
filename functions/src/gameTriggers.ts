import { GeoPoint, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";
import { chickenIdOf, huntersOf } from "./roles";
import { computeShrinkSchedule } from "./zoneCalculation";
import { scheduleGameLifecycleTasks } from "./lifecycleTasks";
import { getTokensForUserIds, sendNotificationToTokens } from "./notifications";

/**
 * Builds the ordered zone-circle schedule from the game doc and persists it to
 * `/games/{gameId}/zone/schedule`. Every client renders `circles[order]`
 * read-only. Re-running overwrites the same deterministic list.
 */
async function writeZoneScheduleForGame(
  gameId: string,
  data: FirebaseFirestore.DocumentData
): Promise<void> {
  const gameMode = (data.gameMode as string) === "stayInTheZone"
    ? "stayInTheZone"
    : "followTheChicken";
  const zone = data.zone as {
    center?: GeoPoint;
    finalCenter?: GeoPoint | null;
    radius?: number;
    shrinkMetersPerUpdate?: number;
    driftSeed?: number;
  } | undefined;
  const center = zone?.center;
  const initialRadius = zone?.radius;
  const shrinkMetersPerUpdate = zone?.shrinkMetersPerUpdate;
  const driftSeed = zone?.driftSeed;
  if (
    !center ||
    typeof initialRadius !== "number" ||
    typeof shrinkMetersPerUpdate !== "number" ||
    typeof driftSeed !== "number"
  ) {
    logger.error("[zoneSchedule] game missing zone data, skipping", { gameId });
    return;
  }
  const finalCenter = zone?.finalCenter ?? null;
  const circles = computeShrinkSchedule(
    gameMode,
    { lat: center.latitude, lng: center.longitude },
    finalCenter ? { lat: finalCenter.latitude, lng: finalCenter.longitude } : null,
    initialRadius,
    shrinkMetersPerUpdate,
    driftSeed
  );
  const persisted = circles.map((c, i) => ({
    order: i,
    radiusMeters: c.radiusMeters,
    lat: c.center.lat,
    lng: c.center.lng,
  }));
  await db()
    .collection("games")
    .doc(gameId)
    .collection("zone")
    .doc("schedule")
    .set({
      circles: persisted,
      gameMode,
      createdAt: Timestamp.now(),
    });
}

async function snapshotChallengesIntoGame(gameId: string): Promise<void> {
  const gameChallengesRef = db()
    .collection("games")
    .doc(gameId)
    .collection("challenges");

  const existing = await gameChallengesRef.limit(1).get();
  if (!existing.empty) {
    logger.info("[snapshotChallenges] game already has challenges, skipping", { gameId });
    return;
  }

  const templateSnap = await db().collection("challenges").get();
  if (templateSnap.empty) {
    logger.warn("[snapshotChallenges] global template is empty", { gameId });
    return;
  }

  const batch = db().batch();
  for (const doc of templateSnap.docs) {
    batch.set(gameChallengesRef.doc(doc.id), doc.data());
  }
  await batch.commit();

  logger.info("[snapshotChallenges] copied template", { gameId, count: templateSnap.size });
}

export const onGameCreated = onDocumentCreated(
  {
    document: "games/{gameId}",
    region: REGION,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data();
    const gameId = event.params.gameId;

    // The found code is generated here and stored only in the admin-only
    // private subcollection; the chicken reads it through getFoundCode.
    const foundCode = String(Math.floor(Math.random() * 10000)).padStart(4, "0");
    try {
      await snap.ref
        .collection("private")
        .doc("security")
        .set({ foundCode }, { merge: true });
    } catch (err) {
      logger.error("[onGameCreated] foundCode write failed", { gameId, error: String(err) });
      throw err;
    }

    try {
      await writeZoneScheduleForGame(gameId, data);
    } catch (error) {
      logger.error("[onGameCreated] zone schedule write failed", { gameId, error: String(error) });
    }

    try {
      await scheduleGameLifecycleTasks(gameId, data);
    } catch (error) {
      logger.error("[onGameCreated] task scheduling failed", { gameId, error: String(error) });
      throw error;
    }

    try {
      await snapshotChallengesIntoGame(gameId);
    } catch (error) {
      logger.error("[onGameCreated] challenge snapshot failed", { gameId, error: String(error) });
      throw error;
    }
  }
);

/**
 * Cancels the scheduled Cloud Tasks of a deleted game (from the manifest
 * written at scheduling time) and removes every subcollection.
 */
export const onGameDeleted = onDocumentDeleted(
  {
    document: "games/{gameId}",
    region: REGION,
  },
  async (event) => {
    const gameId = event.params.gameId;
    const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
    if (!project) {
      logger.warn("[onGameDeleted] GCLOUD_PROJECT not set", { gameId });
      return;
    }

    const manifestRef = db()
      .collection("games")
      .doc(gameId)
      .collection("lifecycle")
      .doc("taskManifest");
    const manifestSnap = await manifestRef.get();
    if (!manifestSnap.exists) {
      logger.info("[onGameDeleted] no task manifest, nothing to cancel", { gameId });
      return;
    }
    const manifest = manifestSnap.data() as {
      enqueuedTasksByQueue?: Record<string, string[]>;
    };
    const byQueue = manifest.enqueuedTasksByQueue ?? {};

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
          // 404: the task already fired or was garbage-collected.
          const status = (err as { code?: number }).code;
          if (status === 404) continue;
          failedCount++;
          logger.warn("[onGameDeleted] task delete failed", { gameId, task: name, error: String(err) });
        }
      }
    }
    logger.info("[onGameDeleted] tasks cancelled", { gameId, deleted: deletedCount, failed: failedCount });

    try {
      await db().recursiveDelete(event.data!.ref);
    } catch (err) {
      logger.warn("[onGameDeleted] recursiveDelete failed", { gameId, error: String(err) });
    }
  }
);

/**
 * Deduplicates winners and notifies every player when a new hunter finds
 * the chicken.
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

    // arrayUnion does not dedupe objects with different timestamps.
    const seenHunterIds = new Set<string>();
    const winnersAfter: WinnerShape[] = [];
    let hadDuplicate = false;
    for (const w of rawWinnersAfter) {
      const hid = w.hunterId;
      if (!hid) {
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
      await event.data.after.ref.update({ winners: winnersAfter });
    }

    const beforeIds = new Set(
      winnersBefore.map((w) => w.hunterId).filter((v): v is string => !!v)
    );
    const newWinners = winnersAfter.filter(
      (w) => w.hunterId && !beforeIds.has(w.hunterId)
    );
    if (newWinners.length === 0) return;
    if (after.status === "done") return;

    const newWinner = newWinners[newWinners.length - 1];
    const hunterName = (newWinner.hunterName ?? "A hunter").slice(0, 50);
    const totalHunters = huntersOf(after).length;
    const remainingCount = Math.max(0, totalHunters - winnersAfter.length);

    const afterChicken = chickenIdOf(after);
    const allUserIds = [
      ...(afterChicken ? [afterChicken] : []),
      ...huntersOf(after),
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
