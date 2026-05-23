/**
 * One-off migration script for the global `/challenges` collection.
 *
 * Backfills missing fields in a single pass:
 *   - `type`                  → "oneShot" when missing
 *   - `proximityRadiusMeters` → 100 when missing (anti-cheat default)
 *   - `level`                 → 1 when missing or 0 (best-effort; admin can retouch via Console)
 *   - `number`                → next free integer within the doc's level (1, 2, 3, …)
 *                               when missing or 0. Admin-assigned numbers > 0 are preserved.
 *
 * Unicity of `(level, number)` is admin's responsibility going forward
 * (Firestore has no composite uniqueness index). Duplicates encountered
 * at migration time are logged but not rewritten.
 *
 * Usage (from the functions/ directory):
 *
 *   # With a service-account JSON (prod uses ./service-account.json by default):
 *   npx ts-node src/migrateChallengesV2.ts
 *   FIREBASE_SERVICE_ACCOUNT=/path/to/staging-sa.json npx ts-node src/migrateChallengesV2.ts
 *
 *   # With Application Default Credentials (`gcloud auth application-default login`):
 *   FIREBASE_PROJECT_ID=pouleparty-ba586 npx ts-node src/migrateChallengesV2.ts
 *
 * Run staging first, eyeball the log, then prod.
 */

import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";

interface DocPlan {
  id: string;
  updates: Record<string, unknown>;
  level: number;
  number: number;
}

async function main() {
  const explicitSaPath = process.env.FIREBASE_SERVICE_ACCOUNT;
  const defaultSaPath = path.resolve(__dirname, "..", "service-account.json");
  const saPath = explicitSaPath ?? (fs.existsSync(defaultSaPath) ? defaultSaPath : null);

  let projectId: string;
  if (saPath) {
    const serviceAccount = JSON.parse(fs.readFileSync(saPath, "utf8"));
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: serviceAccount.project_id,
    });
    projectId = serviceAccount.project_id;
  } else if (process.env.FIREBASE_PROJECT_ID) {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId: process.env.FIREBASE_PROJECT_ID,
    });
    projectId = process.env.FIREBASE_PROJECT_ID;
  } else {
    console.error(
      "No credentials found. Set FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json " +
        "or provide ./service-account.json, or set FIREBASE_PROJECT_ID with ADC."
    );
    process.exit(1);
  }

  const db = admin.firestore();
  console.log(`Migrating /challenges in project "${projectId}"...`);

  const snap = await db.collection("challenges").get();
  console.log(`Found ${snap.size} challenges.\n`);

  // Group by level so we can auto-number within each.
  const byLevel = new Map<number, { id: string; data: FirebaseFirestore.DocumentData }[]>();
  for (const doc of snap.docs) {
    const data = doc.data();
    const level = typeof data.level === "number" && data.level > 0 ? data.level : 1;
    const bucket = byLevel.get(level) ?? [];
    bucket.push({ id: doc.id, data });
    byLevel.set(level, bucket);
  }

  const plans: DocPlan[] = [];

  for (const [level, docs] of [...byLevel.entries()].sort(([a], [b]) => a - b)) {
    // Stable order so re-running the migration produces the same numbers.
    docs.sort((a, b) => a.id.localeCompare(b.id));

    // Track admin-assigned numbers and warn on duplicates.
    const usedNumbers = new Set<number>();
    for (const { id, data } of docs) {
      if (typeof data.number === "number" && data.number > 0) {
        if (usedNumbers.has(data.number)) {
          console.warn(
            `  ⚠️  level ${level}: duplicate number ${data.number} at "${id}" — admin must resolve`
          );
        }
        usedNumbers.add(data.number);
      }
    }

    let nextFree = 1;
    const claimNextFree = (): number => {
      while (usedNumbers.has(nextFree)) nextFree += 1;
      const n = nextFree;
      usedNumbers.add(n);
      nextFree += 1;
      return n;
    };

    for (const { id, data } of docs) {
      const updates: Record<string, unknown> = {};

      if (typeof data.type !== "string" || data.type.length === 0) {
        updates.type = "oneShot";
      }

      if (
        typeof data.proximityRadiusMeters !== "number" ||
        data.proximityRadiusMeters <= 0
      ) {
        updates.proximityRadiusMeters = 100;
      }

      const hasLevel = typeof data.level === "number" && data.level > 0;
      if (!hasLevel) updates.level = 1;

      const hasNumber = typeof data.number === "number" && data.number > 0;
      const finalNumber = hasNumber ? (data.number as number) : claimNextFree();
      if (!hasNumber) updates.number = finalNumber;

      if (Object.keys(updates).length === 0) {
        console.log(`  · ${id}: up-to-date (level=${level}, number=${finalNumber})`);
        continue;
      }
      plans.push({ id, updates, level, number: finalNumber });
      console.log(
        `  • ${id}: ${Object.keys(updates).join(", ")} → level=${level}, number=${finalNumber}`
      );
    }
  }

  if (plans.length === 0) {
    console.log("\nNothing to migrate. ✅");
    return;
  }

  // Single batch — well below the 500-op Firestore cap for the current corpus.
  const batch = db.batch();
  for (const plan of plans) {
    batch.update(db.collection("challenges").doc(plan.id), plan.updates);
  }
  await batch.commit();
  console.log(`\n✅ Migrated ${plans.length} challenges.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
