import * as admin from "firebase-admin";

const PROD_PROJECTS = new Set(["pouleparty-prod"]);

/**
 * Initializes firebase-admin with Application Default Credentials on the
 * project named by FIREBASE_PROJECT_ID. A production project additionally
 * requires CONFIRM_PROD=yes so a script never lands on prod by accident.
 */
export function initAdmin(options: { withDatabase?: boolean } = {}): string {
  const projectId = process.env.FIREBASE_PROJECT_ID;
  if (!projectId) {
    console.error("Set FIREBASE_PROJECT_ID to the target project (pouleparty-ba586 or pouleparty-prod).");
    process.exit(1);
  }
  if (PROD_PROJECTS.has(projectId) && process.env.CONFIRM_PROD !== "yes") {
    console.error(`Refusing to run on ${projectId} without CONFIRM_PROD=yes.`);
    process.exit(1);
  }
  admin.initializeApp({
    credential: admin.credential.applicationDefault(),
    projectId,
    ...(options.withDatabase
      ? {
          databaseURL:
            process.env.FIREBASE_DATABASE_URL ??
            `https://${projectId}-default-rtdb.europe-west1.firebasedatabase.app`,
        }
      : {}),
  });
  return projectId;
}
