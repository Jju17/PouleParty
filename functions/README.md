# Cloud Functions

Firebase Cloud Functions v2 (TypeScript) handling game lifecycle scheduling and push notifications.

## Stack

- **Runtime:** Node.js 22
- **Language:** TypeScript (strict mode)
- **Region:** `europe-west1` (Belgium)
- **Dependencies:** `firebase-admin` ^12.0.0, `firebase-functions` ^5.0.0, `googleapis` ^171.4.0

## Functions

| Export | Trigger | Purpose |
|---|---|---|
| `sendGameNotification` | Cloud Task | Sends localized push notifications (chicken start, hunter start, zone shrink) |
| `transitionGameStatus` | Cloud Task | Idempotent game status transitions (`waiting → inProgress → done`) |
| `onGameCreated` | Firestore onCreate | Schedules all Cloud Tasks when a game is created |
| `onGameUpdated` | Firestore onUpdate | Detects new winners and notifies all players |
| `registerForEvent` | Callable HTTPS | Event registration with validation, dedup, capacity check, Google Sheets sync |
| `getRegistrationCount` | Callable HTTPS | Returns current registration count and max capacity |

## Game lifecycle scheduling

When a game document is created, `onGameCreated` schedules:

1. `waiting → inProgress` at `timing.start`
2. `inProgress → done` at `timing.end`
3. Chicken start notification at `timing.start`
4. Hunter start notification at `timing.start + timing.headStartMinutes`
5. Zone shrink notifications at each `zone.shrinkIntervalMinutes` interval

**Important:** Tasks are scheduled at creation time. If the game config changes afterwards, old tasks are NOT cancelled.

## Notifications

Uses localized keys (`titleLocKey` / `bodyLocKey`) — the OS resolves strings from each app's localization files. Keys must be defined in both iOS (`Localizable.xcstrings`) and Android (`strings.xml`).

Stale FCM tokens are auto-cleaned after send failures.

## Environment config

| Variable | Purpose |
|---|---|
| `REGISTRATION_SHEET_ID` | Google Sheets document ID for event registrations |

## Build & deploy

```bash
# Build
cd functions && npm run build

# Local testing
npm run serve

# Deploy (always deploy to BOTH projects)
firebase deploy --only functions --project pouleparty-ba586
firebase deploy --only functions --project pouleparty-prod
firebase deploy --only firestore:rules --project pouleparty-ba586
firebase deploy --only firestore:rules --project pouleparty-prod
```

## Project IDs

- **Staging:** `pouleparty-ba586`
- **Production:** `pouleparty-prod`
