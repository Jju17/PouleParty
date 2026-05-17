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
| `computeZoneConfiguration` (PP-69) | Callable HTTPS | Wizard recap/shuffle math — initial radius, drift seed, shrink schedule |
| `setGameMasterPassword` (PP-70) | Callable HTTPS | Creator sets the 4-digit GM password on a game (written to `/games/{id}/private/security`) |
| `clearGameMasterPassword` (PP-70) | Callable HTTPS | Creator removes the GM password (kept GMs stay) |
| `joinAsGameMaster` (PP-70) | Callable HTTPS | UID enrollment via 4-digit code, rate-limited (5 attempts, 5 min lock) |
| `createPendingRegistration` (PP-52) | HTTPS POST | Web inscription form entry: writes `/eventRegistrations` doc, opens Stripe Checkout Session |
| `confirmRegistrationPayment` (PP-52) | HTTPS POST (Stripe webhook) | Verifies signature, idempotent `paid: true` flip, sends Resend email + appends Google Sheet |

The PP-9 legacy `registerForEvent` / `getRegistrationCount` were deleted with the rest of the Stripe in-app code. PP-52 replaced them with a different model (top-level `/eventRegistrations` collection, Stripe Checkout from the web form, no in-app payment).

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
| `STRIPE_SECRET_KEY` (PP-52) | Stripe secret key. `sk_test_…` on staging, `sk_live_…` on prod (live mode since 2026-05-17) |
| `STRIPE_WEBHOOK_SECRET` (PP-52) | `whsec_…` returned when the webhook endpoint is registered in Stripe. Distinct per (project, mode) |
| `RESEND_API_KEY` (PP-52) | `re_…` from `resend.com`. Same key on staging + prod since `pouleparty.be` is verified once |
| `GOOGLE_SHEET_ID` (PP-52) | D-Day spreadsheet ID (the long string in the Sheet URL). Same value on staging + prod for the first event |

Defined via `defineSecret(...)` in `src/index.ts` / `src/registrations.ts`. Rotate with:

```bash
firebase functions:secrets:set <NAME> --project pouleparty-ba586
firebase functions:secrets:set <NAME> --project pouleparty-prod
```

**Caveat** (PP-52 lesson): `defineSecret(...).value()` reads the latest version at invocation, but Firebase Functions binds a specific version at deploy time. After `secrets:set`, the CLI prints "Please deploy your functions for the change to take effect" — re-run `firebase deploy --only functions:<name>` so the binding picks up the new version. Don't skip the redeploy.

## Firebase Admin SDK initialization

`src/index.ts` calls `initializeApp()` with no args — **Application Default Credentials**. ADC resolves to the deployed project's compute service account (staging `847523524308-compute@…`, prod `1047338092854-compute@…`), so the same build correctly targets the right project's Firestore / FCM / Cloud Tasks. **Do not** reintroduce the previous `require("../service-account.json")` + `cert(serviceAccount)` path — that file was permanently pinned to prod and made every staging-deployed function read/write prod Firestore. The `src/seedChallenges.ts` admin script is the only place that still loads the SA file (it can target any project from a dev machine, not deployed).

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
