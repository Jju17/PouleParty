import { GeoPoint, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";
import { chickenIdOf, gameMastersOf, huntersOf, rolesOf } from "./roles";
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

export function derivedGameCode(gameId: string): string {
  return gameId.slice(0, 6).toUpperCase();
}

/**
 * Released iOS versions write a 64-bit seed that Android cannot decode. The
 * server PRNG already truncates to 32 bits, so normalizing keeps the schedule.
 */
export function normalizedDriftSeed(seed: unknown): number | null {
  if (typeof seed !== "number" || !Number.isFinite(seed)) return null;
  if (seed >= -2147483648 && seed <= 2147483647 && Number.isInteger(seed)) return null;
  const truncated = seed | 0;
  return truncated === 0 ? 1 : truncated;
}

export function randomFoundCode(): string {
  return String(Math.floor(Math.random() * 10000)).padStart(4, "0");
}

async function ensureFoundCode(gameRef: FirebaseFirestore.DocumentReference): Promise<void> {
  const securityRef = gameRef.collection("private").doc("security");
  await db().runTransaction(async (tx) => {
    const snap = await tx.get(securityRef);
    if (typeof snap.data()?.foundCode === "string" && snap.data()?.foundCode !== "") return;
    tx.set(securityRef, { foundCode: randomFoundCode() }, { merge: true });
  });
}

/**
 * Claims `/gameCodes/{code}` for this game. A code still pointing at another
 * live game is never overwritten.
 */
async function claimGameCode(gameId: string): Promise<void> {
  const code = derivedGameCode(gameId);
  const codeRef = db().collection("gameCodes").doc(code);
  await db().runTransaction(async (tx) => {
    const existing = await tx.get(codeRef);
    const owner = existing.data()?.gameId as string | undefined;
    if (owner && owner !== gameId) {
      const ownerSnap = await tx.get(db().collection("games").doc(owner));
      if (ownerSnap.exists && ownerSnap.data()?.status !== "done") {
        logger.error("[gameCodes] code collision with a live game", { code, gameId, owner });
        return;
      }
    }
    tx.set(codeRef, { gameId, createdAt: Timestamp.now() });
  });
}

export const onGameCreated = onDocumentCreated(
  {
    document: "games/{gameId}",
    region: REGION,
    retry: true,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    // Retries stop after a day: a failure that old is not transient.
    if (Date.now() - Date.parse(event.time) > 24 * 60 * 60 * 1000) {
      logger.error("[onGameCreated] giving up on a stale event", { gameId: event.params.gameId });
      return;
    }

    let data = snap.data();
    const gameId = event.params.gameId;

    const seed = normalizedDriftSeed(data.zone?.driftSeed);
    if (seed !== null) {
      await snap.ref.update({ "zone.driftSeed": seed });
      data = { ...data, zone: { ...data.zone, driftSeed: seed } };
    }

    await ensureFoundCode(snap.ref);
    await claimGameCode(gameId);
    await writeZoneScheduleForGame(gameId, data);
    await snapshotChallengesIntoGame(gameId);
    await scheduleGameLifecycleTasks(gameId, data);
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
      await purgeGameData(gameId, event.data?.data());
      return;
    }

    const manifestRef = db()
      .collection("games")
      .doc(gameId)
      .collection("lifecycle")
      .doc("taskManifest");
    const manifestSnap = await manifestRef.get();
    const byQueue =
      (manifestSnap.data()?.enqueuedTasksByQueue as Record<string, string[]> | undefined) ?? {};

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

    await purgeGameData(gameId, event.data?.data());
  }
);

/**
 * Removes everything a game leaves behind outside its own document:
 * subcollections, the code index, each member's membership entry and the
 * challenge proofs in Storage.
 */
export async function purgeGameData(
  gameId: string,
  data: FirebaseFirestore.DocumentData | undefined
): Promise<void> {
  const gameRef = db().collection("games").doc(gameId);
  try {
    await db().recursiveDelete(gameRef);
  } catch (err) {
    logger.warn("[purge] recursiveDelete failed", { gameId, error: String(err) });
  }

  const batch = db().batch();
  for (const uid of Object.keys(rolesOf(data))) {
    batch.delete(db().collection("users").doc(uid).collection("memberships").doc(gameId));
  }
  const codeRef = db().collection("gameCodes").doc(derivedGameCode(gameId));
  const codeSnap = await codeRef.get();
  if (codeSnap.data()?.gameId === gameId) batch.delete(codeRef);
  try {
    await batch.commit();
  } catch (err) {
    logger.warn("[purge] index cleanup failed", { gameId, error: String(err) });
  }

  try {
    await getStorage().bucket().deleteFiles({ prefix: `gameSubmissions/${gameId}/` });
  } catch (err) {
    logger.warn("[purge] storage cleanup failed", { gameId, error: String(err) });
  }
}

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
      ...gameMastersOf(after),
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
