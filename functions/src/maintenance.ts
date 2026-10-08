import { Timestamp } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import { REGION, db } from "./config";
import { REGISTRATION_SECRETS, replayFailedSideEffects } from "./registrations";

export const FINISHED_GAME_RETENTION_DAYS = 30;
const PURGE_BATCH = 50;

export function purgeCutoff(nowMs: number): Timestamp {
  return Timestamp.fromMillis(nowMs - FINISHED_GAME_RETENTION_DAYS * 24 * 60 * 60 * 1000);
}

/**
 * Deletes games finished for longer than the retention period. Deleting the
 * game document lets `onGameDeleted` cancel tasks and purge everything else.
 */
export const purgeFinishedGames = onSchedule(
  { schedule: "every day 03:30", timeZone: "Europe/Brussels", region: REGION },
  async () => {
    const stale = await db()
      .collection("games")
      .where("status", "==", "done")
      .where("timing.end", "<", purgeCutoff(Date.now()))
      .limit(PURGE_BATCH)
      .get();
    for (const doc of stale.docs) {
      try {
        await doc.ref.delete();
      } catch (err) {
        logger.error("[purge] game delete failed", { gameId: doc.id, error: String(err) });
      }
    }
    logger.info("[purge] finished games deleted", { count: stale.size });
  }
);

export const replayRegistrationSideEffects = onSchedule(
  {
    schedule: "every 15 minutes",
    region: REGION,
    secrets: [REGISTRATION_SECRETS.RESEND_API_KEY, REGISTRATION_SECRETS.GOOGLE_SHEET_ID],
  },
  async () => {
    const resolved = await replayFailedSideEffects(
      REGISTRATION_SECRETS.RESEND_API_KEY.value(),
      REGISTRATION_SECRETS.GOOGLE_SHEET_ID.value()
    );
    if (resolved > 0) logger.info("[replay] side effects resolved", { resolved });
  }
);
