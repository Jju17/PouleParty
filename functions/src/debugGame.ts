/**
 * One-off diagnostic script. Reads a game doc by `gameCode` and prints
 * the PP-71-relevant fields + the scheduled Cloud Task IDs.
 *
 * Usage:
 *   FIREBASE_PROJECT_ID=pouleparty-ba586 npx ts-node src/debugGame.ts P2FGWE
 */

import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";

function resolveProjectAndInitAdmin(): string {
  const explicitSaPath = process.env.FIREBASE_SERVICE_ACCOUNT;
  const envProjectId = process.env.FIREBASE_PROJECT_ID;
  const defaultSaPath = path.resolve(__dirname, "..", "service-account.json");

  if (explicitSaPath) {
    const sa = JSON.parse(fs.readFileSync(explicitSaPath, "utf8"));
    if (envProjectId && envProjectId !== sa.project_id) {
      console.error(
        `Refusing to run: SA targets "${sa.project_id}" vs env "${envProjectId}"`
      );
      process.exit(1);
    }
    admin.initializeApp({
      credential: admin.credential.cert(sa),
      projectId: sa.project_id,
    });
    return sa.project_id;
  }
  if (envProjectId) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: envProjectId,
    });
    return envProjectId;
  }
  if (fs.existsSync(defaultSaPath)) {
    const sa = JSON.parse(fs.readFileSync(defaultSaPath, "utf8"));
    admin.initializeApp({
      credential: admin.credential.cert(sa),
      projectId: sa.project_id,
    });
    return sa.project_id;
  }
  console.error("No credentials.");
  process.exit(1);
}

async function main() {
  const gameCode = (process.argv[2] || "").toUpperCase();
  if (!gameCode) {
    console.error("Usage: npx ts-node src/debugGame.ts <GAMECODE>");
    process.exit(1);
  }
  const projectId = resolveProjectAndInitAdmin();
  console.log(`Searching for gameCode=${gameCode} in project "${projectId}"\n`);

  const db = admin.firestore();
  const snap = await db
    .collection("games")
    .where("gameCode", "==", gameCode)
    .limit(1)
    .get();

  if (snap.empty) {
    console.error(`No game with gameCode=${gameCode} found`);
    process.exit(1);
  }

  const doc = snap.docs[0];
  const data = doc.data();
  const now = new Date();

  console.log(`docId: ${doc.id}`);
  console.log(`name: ${data.name}`);
  console.log(`status: ${data.status}`);
  console.log(`manualStartEnabled: ${data.manualStartEnabled}`);
  console.log(`creatorId: ${data.creatorId}`);
  console.log(`gameMasterIds: ${JSON.stringify(data.gameMasterIds)}`);
  console.log(`hunterIds.length: ${(data.hunterIds || []).length}`);
  console.log(`timing.start: ${data.timing?.start?.toDate?.()?.toISOString() ?? "?"}`);
  console.log(`timing.end: ${data.timing?.end?.toDate?.()?.toISOString() ?? "?"}`);
  console.log(`timing.actualStart: ${data.timing?.actualStart?.toDate?.()?.toISOString() ?? "(null)"}`);
  console.log(`now:           ${now.toISOString()}`);

  const startDate = data.timing?.start?.toDate?.();
  if (startDate) {
    const deltaMs = now.getTime() - startDate.getTime();
    const deltaMin = Math.round(deltaMs / 60000);
    console.log(`now - start = ${deltaMin} min`);
  }

  const manifestSnap = await doc.ref
    .collection("lifecycle")
    .doc("taskManifest")
    .get();
  if (manifestSnap.exists) {
    console.log(`\nlifecycle/taskManifest:`);
    const byQueue = manifestSnap.data()?.enqueuedTasksByQueue as
      | Record<string, string[]>
      | undefined;
    if (byQueue) {
      for (const [queue, ids] of Object.entries(byQueue)) {
        console.log(`  ${queue}: ${ids.join(", ") || "(empty)"}`);
      }
    }
  } else {
    console.log(`\nlifecycle/taskManifest: MISSING (game probably created before CRIT-10)`);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
