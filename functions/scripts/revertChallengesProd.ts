/**
 * One-off prod-only script that restores the legacy `title` / `body`
 * fields on every `/challenges` doc by copying them back from
 * `titleByLocale.fr` / `bodyByLocale.fr`.
 *
 * Context : `migrateChallengesV2` accidentally ran on prod on
 * 2026-05-23 and stripped `title` / `body`, which the live 1.13.x
 * binaries still read directly. This restores the legacy fields so
 * the shipped app keeps working while the localized maps remain in
 * place for the next release.
 *
 * Idempotent : skips a doc when `title` is already a non-empty string.
 *
 * Usage (from the functions/ directory) :
 *
 *   FIREBASE_PROJECT_ID=pouleparty-prod npx tsx scripts/revertChallengesProd.ts
 *
 *   # or with an explicit SA :
 *   FIREBASE_SERVICE_ACCOUNT=/path/to/prod-sa.json npx tsx scripts/revertChallengesProd.ts
 */

import { getFirestore } from "firebase-admin/firestore";
import { initAdmin } from "./adminApp";



async function main() {
  const projectId = initAdmin({ withDatabase: false });
  if (projectId !== "pouleparty-prod") {
    console.error(
      `Refusing to run: this script targets prod only, but resolved project is "${projectId}".`
    );
    process.exit(1);
  }

  const db = getFirestore();
  console.log(`Restoring legacy title/body on /challenges in "${projectId}"...\n`);

  const snap = await db.collection("challenges").get();
  console.log(`Found ${snap.size} challenges.\n`);

  let restored = 0;
  let skipped = 0;
  let unrecoverable = 0;

  const batch = db.batch();
  for (const doc of snap.docs) {
    const data = doc.data();
    const hasTitle = typeof data.title === "string" && data.title.length > 0;
    const hasBody = typeof data.body === "string" && data.body.length > 0;

    if (hasTitle && hasBody) {
      console.log(`  · ${doc.id}: already has legacy title + body, skip`);
      skipped += 1;
      continue;
    }

    const titleMap =
      typeof data.titleByLocale === "object" && data.titleByLocale
        ? (data.titleByLocale as Record<string, string>)
        : {};
    const bodyMap =
      typeof data.bodyByLocale === "object" && data.bodyByLocale
        ? (data.bodyByLocale as Record<string, string>)
        : {};

    const restoredTitle = titleMap.fr ?? "";
    const restoredBody = bodyMap.fr ?? "";

    if (restoredTitle.length === 0 && restoredBody.length === 0) {
      console.warn(`  ✗ ${doc.id}: no titleByLocale.fr / bodyByLocale.fr to recover from`);
      unrecoverable += 1;
      continue;
    }

    const updates: Record<string, unknown> = {};
    if (!hasTitle && restoredTitle.length > 0) updates.title = restoredTitle;
    if (!hasBody && restoredBody.length > 0) updates.body = restoredBody;

    batch.update(doc.ref, updates);
    console.log(`  • ${doc.id}: ${Object.keys(updates).join(", ")}`);
    restored += 1;
  }

  if (restored === 0) {
    console.log(`\nNothing to restore. ✅`);
    return;
  }

  await batch.commit();
  console.log(
    `\n✅ Done. ${restored} restored, ${skipped} already-OK, ${unrecoverable} unrecoverable.`
  );
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
