
import { getFirestore } from "firebase-admin/firestore";
import { initAdmin } from "./adminApp";

async function main() {
  const gameCode = (process.argv[2] || "").toUpperCase();
  if (!gameCode) {
    console.error("Usage: npx tsx scripts/debugGame.ts <GAMECODE>");
    process.exit(1);
  }
  const projectId = initAdmin({ withDatabase: false });
  console.log(`Searching for gameCode=${gameCode} in project "${projectId}"\n`);

  const db = getFirestore();
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
  console.log(`roles: ${JSON.stringify(data.roles)}`);
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
