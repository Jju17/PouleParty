# Infrastructure

Everything the backend needs that is not created by `firebase deploy`. Apply it with `infra/apply.sh <project-id>` on both `pouleparty-ba586` (staging) and `pouleparty-prod`.

| Resource | Declared in | Notes |
|---|---|---|
| Firestore, RTDB and Storage rules, indexes, TTL policies | `firestore.rules`, `database.rules.json`, `storage.rules`, `firestore.indexes.json` | Deployed by `scripts/deploy.sh`; TTL on `expiresAt` for the three rate-limit collections |
| Cloud Functions, Cloud Tasks queues, schedules | `functions/src` | Queues are created on deploy for `transitionGameStatus`, `sendGameNotification`, `spawnPowerUpBatch`, `evaluateOutOfZone`; the purge runs daily at 03:30 Brussels time |
| Firestore backups | `infra/apply.sh` | Daily, kept 14 days; restore steps in `docs/runbook.md` |
| Storage lifecycle | `infra/storage-lifecycle.json` | Proofs older than 45 days are deleted, after the 30-day game purge |
| Secrets | `infra/apply.sh` checks them | `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `RESEND_API_KEY`, `GOOGLE_SHEET_ID`, `MAPBOX_ACCESS_TOKEN` |
| Function parameter | `functions/src/config.ts` | `ENFORCE_APP_CHECK` defaults to true; set false in `functions/.env.<project>` only while App Check metrics show legitimate traffic without tokens |
| Hosting | `firebase.json` | Security headers and CSP on every route |
| Uptime monitoring | Google Cloud Monitoring | Uptime checks on `https://pouleparty.be/` and the staging site, alerting `julien@rahier.dev` |

Admin scripts in `functions/scripts/` use Application Default Credentials (`gcloud auth application-default login`). No service account key is ever stored in the repository; `functions/service-account.json` stays ignored and should not exist.
