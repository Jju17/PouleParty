/**
 * One-off migration: backfill `/games/{gameId}/challenges` for active
 * games created before the `onGameCreated` snapshot step was deployed.
 * Idempotent — re-runs are no-ops on already-snapshotted games.
 *
 * Usage (from the functions/ directory):
 *   FIREBASE_PROJECT_ID=pouleparty-ba586 npx tsx scripts/migrateExistingGamesChallenges.ts
 *   FIREBASE_SERVICE_ACCOUNT=/path/to/prod-sa.json npx tsx scripts/migrateExistingGamesChallenges.ts
 */

import * as admin from "firebase-admin";
import { initAdmin } from "./adminApp";



const ACTIVE_STATUSES = ["waiting", "readyToLaunch", "inProgress"] as const;

async function main(): Promise<void> {
  const projectId = initAdmin({ withDatabase: false });
  console.log(`[migrateExistingGamesChallenges] project=${projectId}`);

  const db = admin.firestore();

  const templateSnap = await db.collection("challenges").get();
  if (templateSnap.empty) {
    console.error(
      "Global /challenges is empty. Nothing to copy. Aborting before we " +
        "silently mark games as 'snapshotted with zero challenges'."
    );
    process.exit(1);
  }
  console.log(`Loaded ${templateSnap.size} challenges from /challenges template.`);

  let scanned = 0;
  let skippedDone = 0;
  let skippedAlreadySnapshotted = 0;
  let migrated = 0;
  let failed = 0;

  const gamesSnap = await db.collection("games").get();
  for (const gameDoc of gamesSnap.docs) {
    scanned += 1;
    const data = gameDoc.data();
    const status = typeof data.status === "string" ? data.status : "";
    if (!ACTIVE_STATUSES.includes(status as (typeof ACTIVE_STATUSES)[number])) {
      skippedDone += 1;
      continue;
    }

    const gameChallengesRef = gameDoc.ref.collection("challenges");
    const existing = await gameChallengesRef.limit(1).get();
    if (!existing.empty) {
      console.log(`  ${gameDoc.id} [${status}] — already snapshotted, skipping`);
      skippedAlreadySnapshotted += 1;
      continue;
    }

    try {
      const batch = db.batch();
      for (const challengeDoc of templateSnap.docs) {
        batch.set(gameChallengesRef.doc(challengeDoc.id), challengeDoc.data());
      }
      await batch.commit();
      console.log(
        `  ${gameDoc.id} [${status}] — copied ${templateSnap.size} challenges`
      );
      migrated += 1;
    } catch (err) {
      console.error(
        `  ${gameDoc.id} [${status}] — FAILED: ${(err as Error).message}`
      );
      failed += 1;
    }
  }

  console.log("");
  console.log(`Done. scanned=${scanned} migrated=${migrated} ` +
    `skipped_already=${skippedAlreadySnapshotted} skipped_done=${skippedDone} ` +
    `failed=${failed}`);
  process.exit(failed > 0 ? 1 : 0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
