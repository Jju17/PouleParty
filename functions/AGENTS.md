# PouleParty Cloud Functions - Agent Instructions

## Stack

TypeScript, Firebase Functions v2 (modular SDK), Node.js 22, region `europe-west1`.

Single source file: `src/index.ts`.

## What the Functions Do

The backend handles two things the clients can't do reliably: **scheduled state transitions** and **push notifications**.

### Game Lifecycle Scheduling

When a game document is created (`onGameCreated` trigger), Cloud Tasks are scheduled for:
- **Status transitions**: `waiting → inProgress` at start time, `inProgress → done` at end time. The `transitionGameStatus` handler is idempotent — it only transitions if the current status matches the expected one.
- **Push notifications**: Chicken start, hunter start (after head start delay), zone shrink (at each interval), and winner found (via `onGameUpdated` trigger on the game document).

**Important caveat**: Tasks are scheduled at game creation time. If the game config changes afterwards (e.g., start time), the old tasks are NOT cancelled. This is a known limitation.

### Push Notifications

Notifications use **localized keys** (`titleLocKey` / `bodyLocKey`) — the OS resolves the actual string from each app's localization files. This means both Android (`strings.xml`) and iOS (`Localizable.xcstrings`) must have matching keys.

FCM tokens are stored in `/fcmTokens/{userId}`. The helper function automatically cleans up stale/invalid tokens after each send.

### Event Registration

`registerForEvent` (callable) and `getRegistrationCount` (callable) handle a separate event registration system used by the web landing page. This writes to a Firestore `registrations` collection and appends to a Google Sheet. Not related to the game itself.

## How to Add a New Notification Type

1. Add the notification type to the `sendGameNotification` handler's switch statement
2. Define the localization keys in Android `strings.xml` (+ `strings-fr.xml`) and iOS `Localizable.xcstrings`
3. Schedule the task in `onGameCreated` at the appropriate time
4. The FCM payload format differs between APNS and Android — both are already set up in `sendNotificationToTokens`, just add your keys

## Deployment

```bash
cd functions
npm run build          # TypeScript compilation
firebase deploy --only functions
```

The Firebase project config is in the root `firebase.json`. Firestore rules are in `firestore.rules` at the root.
