/**
 * One-off migration script for the global `/challenges` collection.
 *
 * Backfills missing fields in a single pass:
 *   - `type`                  → "oneShot" when missing
 *   - `proximityRadiusMeters` → 100 when missing (anti-cheat default)
 *   - `level`                 → 1 when missing or 0 (best-effort; admin can retouch via Console)
 *   - `number`                → next free integer within the doc's level (1, 2, 3, …)
 *                               when missing or 0. Admin-assigned numbers > 0 are preserved.
 *   - `titleByLocale`         → { fr: <legacy title>, en: <legacy title>, nl: <legacy title> }
 *                               when missing (naive copy from the legacy `title`; admin
 *                               retouches real translations via Console). Admin-assigned
 *                               maps are preserved.
 *   - `bodyByLocale`          → same shape, copied from `body`.
 *
 * The legacy `title` / `body` fields are **intentionally preserved** here
 * so the currently-shipped 1.13.x binaries (which still read those fields
 * directly) keep working. Once the legacy build is decommissioned, drop
 * the fields with a dedicated cleanup script.
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

/**
 * Priority order is critical: `FIREBASE_PROJECT_ID` (explicit env
 * intent) wins over the legacy `functions/service-account.json` fallback.
 * Until 2026-05-23 the order was reversed, and the default SA file
 * silently re-routed a "staging" invocation to prod (which uploaded
 * a destructive migration to the wrong project). If both an explicit
 * SA and an env project id are set and they disagree, refuse to run.
 */
function resolveProjectAndInitAdmin(): string {
  const explicitSaPath = process.env.FIREBASE_SERVICE_ACCOUNT;
  const envProjectId = process.env.FIREBASE_PROJECT_ID;
  const defaultSaPath = path.resolve(__dirname, "..", "service-account.json");

  if (explicitSaPath) {
    const sa = JSON.parse(fs.readFileSync(explicitSaPath, "utf8"));
    if (envProjectId && envProjectId !== sa.project_id) {
      console.error(
        `Refusing to run: FIREBASE_SERVICE_ACCOUNT targets "${sa.project_id}" ` +
          `but FIREBASE_PROJECT_ID is "${envProjectId}". Unset one or align them.`
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
    console.warn(
      `⚠️  Using default ./service-account.json (project "${sa.project_id}"). ` +
        `Set FIREBASE_PROJECT_ID=<project> to override and silence this warning.`
    );
    admin.initializeApp({
      credential: admin.credential.cert(sa),
      projectId: sa.project_id,
    });
    return sa.project_id;
  }

  console.error(
    "No credentials found. Set FIREBASE_PROJECT_ID=<project> with ADC " +
      "(gcloud auth application-default login), or " +
      "FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json, " +
      "or place a service-account.json in functions/."
  );
  process.exit(1);
}

async function main() {
  const projectId = resolveProjectAndInitAdmin();

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

      const hasTitleMap =
        data.titleByLocale &&
        typeof data.titleByLocale === "object" &&
        Object.keys(data.titleByLocale as Record<string, unknown>).length > 0;
      if (!hasTitleMap) {
        const legacy = typeof data.title === "string" ? data.title : "";
        updates.titleByLocale = { fr: legacy, en: legacy, nl: legacy };
      }

      const hasBodyMap =
        data.bodyByLocale &&
        typeof data.bodyByLocale === "object" &&
        Object.keys(data.bodyByLocale as Record<string, unknown>).length > 0;
      if (!hasBodyMap) {
        const legacy = typeof data.body === "string" ? data.body : "";
        updates.bodyByLocale = { fr: legacy, en: legacy, nl: legacy };
      }

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
