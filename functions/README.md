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
| `spawnPowerUpBatch` | Cloud Task | Server-authoritative power-up spawning — generates, road-snaps, and writes each batch |
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
6. Initial power-up batch (5 items) at `timing.start` via `spawnPowerUpBatch(batchIndex: 0)`
7. Periodic power-up batches (2 items) at `hunterStartDate + N × shrinkIntervalMinutes` via `spawnPowerUpBatch(batchIndex: N)`

**Important:** Tasks are scheduled at creation time. If the game config changes afterwards, old tasks are NOT cancelled.

## Power-up spawning (server-authoritative)

Moved entirely server-side in April 2026 — clients no longer generate or write power-ups. Why:
- Chicken app could be backgrounded / offline when a shrink was due → missed spawns.
- Client writes were a trust surface — a malicious client could skip or tamper with spawns.

### How `spawnPowerUpBatch` works

Inputs via task payload: `{ gameId, batchIndex, count }`.

1. **Load game** — skip if missing, `status === "done"`, `timing.end ≤ now`, `powerUps.enabled === false`, or `currentRadius ≤ 0`.
2. **`zoneFreeze` awareness** — if the freeze is active at fire time, use `effectiveBatchIndex = batchIndex - 1` for radius + center. The **nominal** `batchIndex` is still used for the deterministic seed + Firestore IDs so retries and frozen vs. unfrozen tasks never collide.
3. **Filter enabled types** — `stayInTheZone` strips `invisibility`/`decoy`/`jammer` (position-dependent, useless without chicken broadcast). Skip if the resulting list is empty.
4. **Compute zone center** for this batch:
   - `batchIndex === 0` → raw `zone.center`
   - `stayInTheZone` → `interpolateZoneCenter` (lerp to `finalCenter` by shrink progress) + `deterministicDriftCenter` (seeded drift bounded by shrink step)
   - `followTheChicken` → latest `chickenLocations/latest` doc, falling back to `zone.center`
5. **Generate positions** via `generatePowerUpsServer` (pure, seeded, deterministic — the reference impl that iOS/Android still keep in sync for parity testing).
6. **Road-snap** each point via the Mapbox Directions API (`walking` profile).
7. **Write** all docs in a single Firestore batch with **`{ merge: true }`** — makes Cloud Task retries idempotent: already-collected/activated state in the existing doc is preserved.

Pure helpers live in `src/powerUpSpawn.ts` so they can be imported by tests without pulling in the firebase-functions runtime.

## Notifications

Uses localized keys (`titleLocKey` / `bodyLocKey`) — the OS resolves strings from each app's localization files. Keys must be defined in both iOS (`Localizable.xcstrings`) and Android (`strings.xml`).

Stale FCM tokens are auto-cleaned after send failures.

## Secrets

| Secret | Purpose |
|---|---|
| `MAPBOX_ACCESS_TOKEN` | Used by `spawnPowerUpBatch` + `onGameCreated` to road-snap power-up positions via the Mapbox Directions API |

Defined via `defineSecret(...)` in `src/index.ts`. Rotate with:

```bash
firebase functions:secrets:set MAPBOX_ACCESS_TOKEN --project pouleparty-ba586
firebase functions:secrets:set MAPBOX_ACCESS_TOKEN --project pouleparty-prod
```

No redeploy needed — `defineSecret(...).value()` reads the latest version at each invocation.

## Environment config

| Variable | Purpose |
|---|---|
| `REGISTRATION_SHEET_ID` | Google Sheets document ID for event registrations |

## Testing

```bash
npm test       # vitest run — covers src/powerUpSpawn.ts pure helpers
```

Only the pure helpers (`filterEnabledTypesServer`, `interpolateZoneCenterServer`, `deterministicDriftCenterServer`, `generatePowerUpsServer`, `haversineDistance`) have unit tests. The trigger + task wiring (`onGameCreated`, `spawnPowerUpBatch`) is validated end-to-end by creating a game in staging.

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
