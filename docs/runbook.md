# Runbook

Operational steps for staging (`pouleparty-ba586`) and production (`pouleparty-prod`). Commands assume `gcloud` and `firebase` are logged in as `julien@rahier.dev`.

## Deploy the backend

```bash
git switch main && git pull
scripts/deploy.sh            # staging, checks, then production after confirmation
scripts/deploy.sh staging    # staging only
```

Never run `firebase deploy` by hand: the script replays the gates, refuses a dirty tree and checks the live pages afterwards.

## First deploy after the October 2026 review

1. Open Firebase console, App Check, and confirm that both apps and the web site send valid tokens. `ENFORCE_APP_CHECK` defaults to true, so callables reject requests without a token once deployed.
2. Rotate the Mapbox public token that used to be committed in Android `strings.xml` and iOS `Info.plist`, restrict the new one to the app identifiers, and set it in `android/local.properties`, `ios/Secrets.xcconfig` and the Bitrise secrets.
3. Run `infra/apply.sh pouleparty-ba586`, deploy staging, play one short QA debug game end to end, then do the same for production.
4. Run `functions/scripts` migrations only if a release note asks for it; they require `FIREBASE_PROJECT_ID` and, for production, `CONFIRM_PROD=yes`.

## Restore Firestore from a backup

```bash
gcloud firestore backups list --project pouleparty-prod --location eur3
gcloud firestore databases restore --project pouleparty-prod \
  --source-backup=projects/pouleparty-prod/locations/eur3/backups/<BACKUP_ID> \
  --destination-database=restore-$(date +%Y%m%d)
```

A restore creates a new database. Compare it with `(default)`, then copy the documents you need with an admin script; never point the apps at the restored database. Practise this on staging once per season and note the date in `known-issues.md`.

## Incidents

| Symptom | First checks |
|---|---|
| Players cannot join | Functions logs for `joinGame`; App Check metrics; `/gameCodes/{code}` exists for the game |
| Zone does not move | `games/{id}/zone/schedule` exists; `onGameCreated` logs; Cloud Tasks queue `transitionGameStatus` |
| No push notifications | `/users/{uid}.token` is set; `sendGameNotification` logs list invalid registrations |
| Paid registration missing its email | `/failedSideEffects` documents; `replayRegistrationSideEffects` runs every 15 minutes |
| A game never starts in manual mode | Status `readyToLaunch`; a referee or the chicken taps LAUNCH; the fallback task ends unlaunched games one hour after their planned end |

## Data requests

- Account deletion requests arrive through the web form: `processAccountDeletion` stores them in `/accountDeletionRequests` and emails `julien@rahier.dev`. The scrub (Auth user, `/users/{uid}`, memberships) is manual within 30 days; in-app deletion from Settings is immediate.
- Finished games, their proofs and memberships disappear 30 days after the end, automatically.
