/**
 * QA bot: simulates N hunters in a (staging) game so the chicken /
 * GameMaster map can be tested without several phones. NOT a Cloud Function —
 * run via ts-node. Staging only.
 *
 * It sets `roles.<uid> = "hunter"`, writes a `players` doc per bot (so the
 * GameMaster drawer shows a team name, PP-86), then random-walks each bot's
 * `hunterLocations/{uid}` doc around the zone centre every few seconds. On
 * Ctrl+C it removes the bots and their location docs so they vanish from the
 * map.
 *
 * Usage:
 *   FIREBASE_PROJECT_ID=pouleparty-ba586 npx tsx scripts/qaBot.ts <GAMECODE> [botCount] [intervalSec]
 *
 * Credentials resolve like `debugGame.ts`: FIREBASE_SERVICE_ACCOUNT, or
 * FIREBASE_PROJECT_ID + Application Default Credentials.
 */

import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { ServerValue, getDatabase } from "firebase-admin/database";
import { initAdmin } from "./adminApp";



// ~metres → degrees (latitude is uniform; longitude scales by cos(lat)).
function metresToLat(m: number): number {
  return m / 111_111;
}
function metresToLng(m: number, atLat: number): number {
  return m / (111_111 * Math.cos((atLat * Math.PI) / 180));
}

async function main() {
  const gameCode = (process.argv[2] || "").toUpperCase();
  const botCount = Math.max(1, parseInt(process.argv[3] || "3", 10));
  const intervalSec = Math.max(1, parseInt(process.argv[4] || "3", 10));
  if (!gameCode) {
    console.error(
      "Usage: FIREBASE_PROJECT_ID=pouleparty-ba586 npx tsx scripts/qaBot.ts <GAMECODE> [botCount] [intervalSec]"
    );
    process.exit(1);
  }

  const projectId = initAdmin({ withDatabase: true });
  const db = getFirestore();
  const rtdb = getDatabase();

  // gameCode = first 6 chars of the doc id, uppercased.
  const snap = await db.collection("games").get();
  const doc = snap.docs.find(
    (d) => d.id.substring(0, 6).toUpperCase() === gameCode
  );
  if (!doc) {
    console.error(`No game with code ${gameCode} in project "${projectId}"`);
    process.exit(1);
  }
  const gameId = doc.id;
  const data = doc.data();
  const center = data.zone?.center as
    | { latitude: number; longitude: number }
    | undefined;
  if (!center) {
    console.error("Game has no zone.center — cannot place bots.");
    process.exit(1);
  }
  const radius = (data.zone?.radius as number | undefined) ?? 1000;
  const lat0 = center.latitude;
  const lng0 = center.longitude;

  const botIds = Array.from({ length: botCount }, (_, i) => `qa-bot-${i + 1}`);
  const gameRef = db.collection("games").doc(gameId);

  // Register bots: roles.<uid> = "hunter" + a players doc (team name for the
  // GM drawer).
  for (let i = 0; i < botIds.length; i++) {
    const uid = botIds[i];
    await gameRef.update({ [`roles.${uid}`]: "hunter" });
    await gameRef.collection("players").doc(uid).set({
      teamName: `QA Bot ${i + 1}`,
      joinedAt: Timestamp.now(),
    });
  }
  console.log(
    `Spawned ${botCount} bot(s) in game ${gameId} (${gameCode}) on "${projectId}". Ctrl+C to stop + clean up.`
  );

  // Seed each bot somewhere inside half the radius of the centre.
  const positions = botIds.map(() => {
    const r = Math.random() * radius * 0.5;
    const theta = Math.random() * 2 * Math.PI;
    return {
      lat: lat0 + metresToLat(r * Math.cos(theta)),
      lng: lng0 + metresToLng(r * Math.sin(theta), lat0),
    };
  });

  let stopping = false;
  const cleanup = async () => {
    if (stopping) return;
    stopping = true;
    console.log("\nCleaning up bots...");
    for (const uid of botIds) {
      await gameRef
        .update({ [`roles.${uid}`]: FieldValue.delete() })
        .catch(() => undefined);
      await rtdb
        .ref(`/games/${gameId}/hunterLocations/${uid}`)
        .remove()
        .catch(() => undefined);
      await gameRef
        .collection("players")
        .doc(uid)
        .delete()
        .catch(() => undefined);
    }
    console.log("Done.");
    process.exit(0);
  };
  process.on("SIGINT", cleanup);
  process.on("SIGTERM", cleanup);

  // Random-walk + write each bot's location every `intervalSec`.
  const tick = async () => {
    for (let i = 0; i < botIds.length; i++) {
      // ~10 m random step per tick.
      positions[i].lat += metresToLat((Math.random() - 0.5) * 20);
      positions[i].lng += metresToLng((Math.random() - 0.5) * 20, positions[i].lat);
      await rtdb
        .ref(`/games/${gameId}/hunterLocations/${botIds[i]}`)
        .set({
          lat: positions[i].lat,
          lng: positions[i].lng,
          ts: ServerValue.TIMESTAMP,
        });
    }
    console.log(
      `Updated ${botCount} bot position(s) @ ${new Date().toISOString()}`
    );
  };

  await tick();
  setInterval(tick, intervalSec * 1000);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
