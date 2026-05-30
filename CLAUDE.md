# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is this project?

A cross-platform (iOS + Android) location-based mobile game. One player is the **Chicken** (Poule) who must evade **Hunters** within a shrinking circular zone on a real map. Positions are synced in real-time via Firebase Firestore.

There's also a **web** landing page (React/Vite) and **Cloud Functions** backend for game lifecycle management and push notifications.

## Game Concepts

### Roles
- **Creator** (admin owner): Creates the game, owns it (`creatorId`), can delete or reconfigure it while `status == waiting`. By default also starts as the chicken (`chickenId == creatorId` at create) but a GameMaster can re-designate someone else (PP-26).
- **Chicken** (`chickenId`): The single player who runs and hides. Shows the 4-digit `foundCode` to hunters who physically find them. Distinct from the creator: a GameMaster can swap the chicken to any registered hunter while `status == waiting`. Use `game.isChicken(userId)` for the "is this user the chicken" check.
- **Hunters**: Join via the 6-character game code. Must physically find the chicken and enter the `foundCode` to win. When the game is linked to a paid event batch (`Game.registrationBatchId != null`, PP-52), the JoinFlow inserts a second textfield for the validation code received by email, queried server-side against `/eventRegistrations`.
- **GameMasters** (PP-23, PP-24, PP-70): 0..N validators distinct from the chicken and the hunters. The creator sets a 4-digit `gameMasterPassword` at game creation; anyone with the code calls `joinAsGameMaster` to land in `gameMasterIds`. Validators get a dedicated observer map (`GameMasterMapFeature` on iOS, `GameMasterMapScreen` on Android) that streams the chicken's position, every hunter, and every spawned power-up in read-only mode. They never write their own GPS and cannot collect / activate power-ups. Hunters always broadcast their position when at least one GameMaster has joined (`game.gameMasterIds.isNotEmpty()`), regardless of `chickenCanSeeHunters` or `gameMode`, so the GM sees them even in `stayInTheZone`. The password lives in `/games/{gameId}/private/` (admin-SDK-only); only `gameMasterIds` is on the public Game doc.

### Game Modes
- **Follow the Chicken** (`followTheChicken`): The zone circle follows the chicken's live GPS. Hunters see where the chicken is (within the zone). Chicken doesn't see hunters unless `chickenCanSeeHunters` is enabled.
- **Stay in the Zone** (`stayInTheZone`): Fixed zone that shrinks and drifts deterministically. No position sharing at all (except via Radar Ping power-up).

### Zone Mechanics
The zone is a circle that shrinks periodically. The shrink interval and amount are configurable. In "Stay in the Zone" mode, the center drifts deterministically using a seed stored in the game document — every client computes the same drift. There's also a "final zone" point: as the radius shrinks, the center interpolates linearly from start to final position.

### Zone Setup Wizard (PP-11 / PP-12)
The game-creation wizard splits zone setup into two dedicated sub-steps:

- **`startZoneSetup`** (iOS `case .startZoneSetup`, Android `START_ZONE_SETUP`): the chicken places the **start pin**. In `stayInTheZone` the radius slider is hidden — the radius is recomputed at the recap step (PP-13) from the two pins. In `followTheChicken` a 3-button **size picker** (Small 500m / Medium 1000m / Large 2000m) sets `Game.zone.radius` inline. Next is gated by `isStartZoneConfigured` (start ≠ default Brussels).
- **`finalZoneSetup`** (iOS `case .finalZoneSetup`, Android `FINAL_ZONE_SETUP`): `stayInTheZone` only — skipped entirely (forward and back) in `followTheChicken`. The start pin renders as a read-only reference; the user can only edit the final pin. Next is gated by `isFinalZoneConfigured` (final ≠ null AND haversine ≥ 100 m from start). Tapping the start pin away on PP-11 within 100 m of an existing final clears the final so PP-12 forces a re-placement.
- **Backend: `computeZoneConfiguration` Cloud Function (PP-69)**: single source of truth for the zone math. Takes the two pins + game mode + duration + `forceNewSeed` flag (Shuffle button, PP-14 Phase 2) and returns `initialRadius`, `driftSeed`, `shrinkIntervalMinutes` / `shrinkMetersPerUpdate` (TS port of `calculateNormalModeSettings`), and the precomputed shrink schedule (`circles[]`) so the recap step (PP-13) doesn't have to re-walk the drift algorithm client-side. iOS calls it via `ApiClient.computeZoneConfiguration`, Android via `FirestoreRepository.computeZoneConfiguration`. The client-side mirrors in `Models/GameSettings.swift` / `model/GameSettings.kt` are kept as cross-platform parity references and get deleted in PP-13 Phase 2 / PP-14 Phase 2.

### Power-Ups
6 types, split between chicken and hunter: `zonePreview` + `radarPing` (hunter), `invisibility` + `zoneFreeze` + `decoy` + `jammer` (chicken). **Server-authoritative spawning** (since April 2026): a Cloud Function (`spawnPowerUpBatch`) generates deterministically from the game's `zone.driftSeed`, snaps to nearest roads via Mapbox API, and writes docs directly — clients only consume via `powerUpsStream`. Initial batch (5) fires at `timing.start`; periodic batches (2) fire at each zone shrink. Collected by proximity (30m). Some have timed effects tracked as timestamps in `powerUps.activeEffects` on the game document. In `stayInTheZone` mode, position-dependent chicken power-ups (invisibility, decoy, jammer) are automatically disabled server-side.

**PP-35 default + mode filter (May 2026):** the wizard only ships `zoneFreeze` + `zonePreview` enabled by default; the other 4 stay in the code and the spawner so they can be flipped back on per-game via Firestore. The `PowerUpsStep` reads `Game.gameMode` and strict-filters the cards through the pure helper `availablePowerUpTypes(for:)` (iOS `Components/GameLogic/PowerUpAvailability.swift`, Android `ui/gamelogic/PowerUpAvailability.kt`). Incompatible types in `stayInTheZone` (invisibility / decoy / jammer) are not rendered at all, not greyed out. The helper mirrors `filterEnabledTypesServer` in `functions/src/powerUpSpawn.ts`.

**Activation is also atomic** — clients call `ApiClient.activatePowerUp` / `FirestoreRepository.activatePowerUp` with an optional `activeEffectField`. The implementation uses a Firestore transaction to update both the power-up doc (`activatedAt`, `expiresAt`) and the game doc (`powerUps.activeEffects.<field>`) in one commit, so the state can't half-apply.

### Game Lifecycle
1. Chicken creates game → Firestore document created → Cloud Functions schedule status transitions and notifications via Cloud Tasks
2. Game starts at `timing.start` (status: waiting → inProgress, OR waiting → `readyToLaunch` in manual-start mode — see below)
3. Hunters get a head start delay (`timing.headStartMinutes`) before the hunt begins
4. Zone shrinks periodically until collapse or `timing.end`
5. Hunters who find the chicken enter the found code → added to `winners` array
6. Game ends when time runs out, zone collapses, chicken cancels, or all hunters find the chicken

### Manual launch (PP-71)
When the chicken toggles **manual launch** on the `startTime` wizard step, the wizard sets `Game.manualStartEnabled: true`. At `timing.start` the Cloud Task flips `status` to **`readyToLaunch`** instead of `inProgress`. The chicken + every GameMaster see a full-screen LAUNCH overlay (`Features/Map/ReadyToLaunchOverlay.swift` / `ui/map/ReadyToLaunchOverlay.kt`); hunters see a passive waiting overlay. Tapping LAUNCH calls the `launchGame` callable, which atomically flips `status → inProgress`, stamps `timing.actualStart` server-side, recomputes `timing.end = actualStart + (planned end − planned start)`, and enqueues the runtime Cloud Tasks (status→done, hunter_start notif, zone_shrink notifs, power-up batches) deferred at creation. `Game.hunterStartDate` is computed from `effectiveStartDate = actualStart ?? start + headStartMinutes`, so every downstream timer stays anchored on the real start. Two simultaneous LAUNCH taps end up with a single `actualStart` thanks to the Firestore transaction. `chicken_start` notif still fires at the planned `timing.start` in both modes (the manual-mode "gather now" reminder). `firestore.rules` blocks every client-side status write to `inProgress` / `readyToLaunch` — only `creator → 'done'` from `waiting` / `readyToLaunch` is allowed (cancel game).

### Paid event registrations (PP-52)

For real-world events where players pay to participate (D-Day 06/06/2026 being the first), inscriptions happen on the public web form (`pouleparty.be/fr/inscription` FR, `/en/registration` EN, `/nl/inschrijving` NL — PP-99 i18n URL convention with `/<locale>/<localized-slug>` prefix; legacy slugs without prefix continue to 301 to the new ones) **before** any in-app Game exists. The flow:

1. **Form submit → Stripe Checkout** : `createPendingRegistration` Cloud Function writes a `/eventRegistrations/{rid}` doc with `paid: false` + a unique 6-char alphanum code, then opens a Stripe Checkout Session (12€ × teamSize, EUR). `success_url` / `cancel_url` are built from the request's `Origin` header (staging vs prod) and the visitor's locale.
2. **Stripe webhook → idempotent flip** : `confirmRegistrationPayment` verifies the Stripe signature, flips `paid: true` inside a transaction (re-deliveries are noops), then runs side effects: send a localized confirmation email via Resend (FR / EN / NL) and append a row to the D-Day Google Sheet. Email + Sheet failures are logged but don't 5xx Stripe (would re-trigger the webhook past the idempotency guard, losing the side effects entirely).
3. **In-app linking** : when the admin creates the matching Game in-app (admin mode `jujurahier`, lifted maxPlayers), they set `Game.registrationBatchId` to the same `batchId` the form was using (e.g. `game-06-06-2026`). The Game doc is otherwise standard.
4. **Mobile JoinFlow** : when a hunter resolves a gameCode whose `Game.registrationBatchId != null`, the iOS `JoinFlowFeature` / Android `HomeViewModel` inserts a `validationCodeEntry` step between `codeValidated` and `joiningWithTeamName`. The entered code is checked via `ApiClient.validateRegistrationCode` / `FirestoreRepository.validateRegistrationCode` (query `where batchId == X && code == Y && paid == true` limit 1). Wrong code → inline error, stay on the step.
5. **Universal Link / App Link deeplinks** : the confirmation email's CTA button points at `https://pouleparty.be/join?code=ABCDEF`. iOS associated-domain (`applinks:pouleparty.be` in `PouleParty.entitlements`) + Android `<intent-filter android:autoVerify="true">` (`AndroidManifest.xml`) verify against `web/public/.well-known/{apple-app-site-association,assetlinks.json}` and route the OS to open the app. iOS uses `PoulePartyApp.onOpenURL` as the **primary** Universal Link handler — `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)` does NOT fire on SwiftUI App lifecycle (known SwiftUI limitation: the NSUserActivity is silently dropped before the modifier sees it). `.onContinueUserActivity` + an `AppDelegate.application(_:continue:)` → `NotificationCenter` channel are kept as defense-in-depth for macOS Catalyst / iPad multi-window. The handler parses the URL and sends `AppFeature.deeplinkValidationCodeReceived(code)`. Android `MainActivity.handleDeeplink` (onCreate + onNewIntent) pushes into the top-level `DeeplinkBus` object (Kotlin singleton — Hilt field injection on the Activity hit a Dagger metadata-jvm 2.1.0 vs Kotlin 2.2.0 mismatch, so we sidestepped DI). Mid-game states (chickenMap / hunterMap / gameMasterMap / victory) ignore the deeplink so a tap doesn't yank a player out of their game; the email keeps the code in plain text as backup.

6. **Deeplink resolution UX (3 states)** : when the deeplink lands on Home, the JoinFlow triggers an async `lookupGameByValidationCode(code)` Firestore query (`ApiClient` / `FirestoreRepository`) that does two reads — `/eventRegistrations where code == X && paid == true` to get the `batchId`, then `/games where registrationBatchId == batchId` filtering `done` client-side. The result branches into one of three JoinFlow steps:
   - **`gameReady(Game)`** : both reads succeed and an active Game exists → server-side validation is already done, so we **skip the manual `validationCodeEntry` step entirely** and drop the user straight on the teamName form (`joiningWithTeamName(game)`). One Submit and they're in.
   - **`gameNotYetCreated(batchId)`** : the inscription is valid but no in-app Game has that `registrationBatchId` yet (D-Day day-of before the chicken sets up the party). Friendly view (🐔 "Partie pas encore ouverte" / "Party not open yet" / "Feest nog niet open") with the validation code displayed prominently as backup. OK button resets to manual code-entry while preserving `validationCode` so the user can still type the gameCode when announced.
   - **`invalidCode`** : no paid `eventRegistration` matches the code (typo, stale link). Vue ⚠️ "Code invalide" with a link back to `julien@rahier.dev`. Close button resets like above.
   
   A spinner (`resolvingDeeplink(code)`) covers the lookup window. Network failure falls through to the generic `.networkError` step so the user can dismiss and retry.

### Admin mode

All in-app games are **Free**. The wizard caps `maxPlayers` at 5 by default; the
admin entry point on Home (gated by the admin code, obfuscation only, no real
auth) lifts the cap to 500 via `state.isAdminCreation = true`. The same flag is
written to the Game doc and enforced server-side by the `firestore.rules` `allow
create` clause: `maxPlayers <= 5 || isAdminCreation == true && maxPlayers <=
500`. See PP-45. The admin code itself is now tunable via Remote Config (key
`admin_code`, see below); `AdminCode.value` / `AdminCode.VALUE` (`"jujurahier"`)
remains as the compiled fallback.

### QA debug mode (PP-104)

A testing mode to run a full game lifecycle in minutes instead of the 1h
minimum. Entered at the **same long-press of "Create Party"** as admin mode:
typing the QA code (Remote Config key `qa_debug_code`, compiled fallback
`DebugCode.value` / `DebugCode.VALUE` = `"qadebug"`) creates a game with
`Game.isDebugGame = true` plus the lifted player cap. An **empty Remote Config
value disables debug-game creation** (the getter returns the raw value, no
fallback; the match requires a non-empty code).

Clearing `qa_debug_code` is optional hygiene, not a security requirement: the
mode is safe by construction. The `DebugQAPanel` renders only when
`game.isDebugGame == true` (a normal game, including the D-Day admin game, has
it `false`), and `debugAdvanceGame` refuses any non-debug game server-side and
requires the caller be that game's creator/GM. Worst case if the code leaks:
someone creates their own separate debug game — harmless to real events. The
dedicated `isDebugGame` flag (vs reusing `isAdminCreation`, which the D-Day game
also sets) is exactly what makes this safe.

A debug game gets **compressed timing** (`applyDebugTiming` in iOS
`GameCreationFeature` / Android `GameCreationViewModel`): start ≈ now+60s, head
start 0, duration 5 min, shrink interval 1 min, manual launch ON. Both maps
(chicken + GameMaster) render a small **`DebugQAPanel`** (only when
`game.isDebugGame`) with two buttons — **Spawn** (power-up batch now) and
**End/Finir** (status → done) — wired to the `debugAdvanceGame` callable
(`functions/src/debugAdvanceGame.ts`), which is gated server-side on
`isDebugGame == true` + caller creator/GM, so it's inert on real games.
`functions/src/qaBot.ts` is a ts-node script (not deployed) that simulates N
hunters to test the maps without phones.

### Remote Config (runtime-tunable constants)

A small set of client-only constants is overridable at runtime via Firebase
Remote Config, so they can be changed without shipping a new build. The
compiled values stay in the code (`AppConstants` / `AdminCode`) as the default,
and Remote Config only ever overrides them, so the app behaves correctly
offline and before the first fetch. Migrated keys (identical on both platforms):

| Remote Config key | Default | Used by |
|---|---|---|
| `admin_code` | `jujurahier` | admin-mode gate on Home |
| `qa_debug_code` | `qadebug` | QA debug-game gate on Home (empty = disabled, PP-104) |
| `found_code_max_wrong_attempts` | `3` | hunter found-code cooldown trigger |
| `found_code_cooldown_seconds` | `10` | hunter found-code cooldown duration |
| `default_initial_radius_meters` | `1500` | wizard's starting zone radius |

iOS: `Clients/RemoteConfigClient.swift` (TCA `@Dependency(\.remoteConfigClient)`),
defaults registered + `fetchAndActivate` fired in `App/AppDelegate.swift`.
Android: `config/RemoteConfigProvider.kt` (Hilt `@Singleton`, defaults +
`fetchAndActivate` in its `init`).

**Deliberately NOT in Remote Config**: anything server-authoritative
(power-up batch sizes / collection radius live in `functions/`) or
determinism-critical (`normalMode*`, `JAMMER_NOISE_DEGREES`,
`outOfZonePenaltyInterval`, zone-calculation mirrors). A client Remote Config
that delivered different values to iOS vs Android mid-rollout would desync
players. Tune those server-side or not at all.

## Build & run

### iOS

```bash
xcodebuild -scheme PouleParty -configuration Debug build
xcodebuild -scheme PoulePartyTests test
xcodebuild -scheme PoulePartyTests test -only-testing:PoulePartyTests/GameTests
```

Requires `GoogleService-Info.plist` in `ios/Firebase/` (gitignored). GPX files in `ios/` simulate locations in the Simulator.

### Android

```bash
cd android && ./gradlew assembleDebug
cd android && ./gradlew test
cd android && ./gradlew testDebugUnitTest --tests "dev.rahier.pouleparty.GameTest"
```

compileSdk 35, minSdk 26, JVM target 17. Requires `google-services.json` (gitignored). Product flavors: `staging` and `production`.

### Cloud Functions

```bash
cd functions && npm run build && firebase deploy --only functions
```

### Web

```bash
cd web && npm run dev
```

## Tech stack

| Concern | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose + Material 3 |
| Architecture | TCA (Composable Architecture) | MVVM + Hilt DI |
| Maps | Mapbox Maps SDK | Mapbox Maps Compose SDK |
| Location | CoreLocation | FusedLocationProvider (Play Services) |
| Async | AsyncStream / async-await | Kotlin Coroutines + Flow |
| Backend | Firebase Firestore, Auth, Analytics, Messaging | Firebase Firestore, Auth, Analytics, Messaging |

## Firestore data model

```
/games/{gameId}
  ├── id, name, maxPlayers, gameMode, chickenCanSeeHunters, isAdminCreation, manualStartEnabled (PP-71), isDebugGame (PP-104, QA debug mode)
  ├── registrationBatchId (PP-52, optional — links the game to a batch of pre-paid web registrations in `/eventRegistrations`; when set, JoinFlow gates the join on a validation code)
  ├── foundCode, hunterIds, gameMasterIds, status (one of `waiting` / `readyToLaunch` (PP-71) / `inProgress` / `done`), winners, creatorId, chickenId  (the chicken `lastHeartbeat` ping moved to RTDB presence, PP-102)
  ├── timing: { start, end, headStartMinutes, actualStart (PP-71, server-stamped at LAUNCH when `manualStartEnabled == true`) }
  ├── zone: { center, finalCenter, radius, shrinkIntervalMinutes, shrinkMetersPerUpdate, driftSeed }
  ├── powerUps: { enabled, enabledTypes, activeEffects: { invisibility, zoneFreeze, radarPing, decoy, jammer } }
  ├── (chickenLocations/latest + hunterLocations/{hunterId} moved to Realtime Database — PP-102, see "Location tracking")
  ├── /powerUps/{powerUpId}        → One doc per spawned power-up (collected/activated state)
  ├── /registrations/{userId}      → One doc per in-app hunter (teamName, joinedAt). PP-90 dropped the registration-required gate — anyone can join at any time, but the subcollection is still written so the GameMaster can pick a chicken from the team-name list (PP-86). Deliberately distinct from the top-level `/eventRegistrations` collection (PP-52, paying web registrations) — names overlap but namespaces and ownership are different.
  ├── /challengeCompletions/{hunterId} → One doc per hunter who has completed at least one challenge (completedChallengeIds, totalPoints, teamName)
  ├── /challenges/{challengeId}    → PP-98 per-game snapshot of the challenges catalog. Written exclusively by `onGameCreated` (admin SDK, copies every doc from the global `/challenges` template at game creation time, idempotent). Once a game exists, the catalog is frozen — subsequent edits to the global template don't rewrite this game's snapshot. Runtime reads (iOS `ApiClient.challengesStream(gameId)`, Android `FirestoreRepository.challengesStream(gameId)`, the `validateChallengeSubmission` callable, and the `renderChallengesSheet` printable sheet at `/challenges-{fr|en|nl}/<gameId>`) all hit this subcollection. Same doc-id and payload as the global template — `localizedTitle(locale)` / `localizedBody(locale)` accessors keep working unchanged.
  └── /private/{docId}             → Admin-SDK-only subcollection. Holds the `gameMasterPassword` (PP-23) and any future field that must never be client-readable. Rules: `read, write: if false`.

/challenges/{challengeId}          → Admin-only template (PP-98 — `firestore.rules` locks `read, write: if false` so clients can't reach it; only Cloud Functions read it via admin SDK at game creation, to populate the per-game subcollection above). `{titleByLocale: {fr,en,nl}, bodyByLocale: {fr,en,nl}, points, lastUpdated, type: "oneShot" | "repeatable" (PP-27), location: GeoPoint? (bar partner pin), proximityRadiusMeters: Int? (consumed by PP-72 anti-cheat, default 100 m set by migration), partner: String? (brand label), level: Int (1/2/3, default 1, PP-31), number: Int (auto-numbered within level, default 0 sentinel = "not yet numbered", PP-31)}`. `oneShot` = validable once per hunter (street stunts), `repeatable` = validable multiple times (bar partners). The `migrateChallengesV2` script in `functions/src/migrateChallengesV2.ts` (run via `npx ts-node`, one pass per project) backfills `type: "oneShot"`, `proximityRadiusMeters: 100`, `level: 1`, the next free `number > 0` within each level, copies legacy `title` / `body` into `titleByLocale` / `bodyByLocale` when those maps are absent, and then `FieldValue.delete()`-s the legacy `title` and `body` fields so the schema converges to the localized-only shape. Unicity of `(level, number)` is admin-managed via the Console; the script logs warnings on duplicates but never overwrites an admin-assigned `number > 0`. PP-32 gates the per-level Submit button via the pure helper `ChallengeProgress.isLevelUnlocked` (iOS `Components/GameLogic/ChallengeProgress.swift`, Android `ui/gamelogic/ChallengeProgress.kt`): `level == 1` is always unlocked, otherwise the hunter needs `ceil(N × 0.80)` of the previous level's `oneShot` challenges validated. `repeatable` challenges are excluded from the count. PP-76 i18n: `localizedTitle(locale)` / `localizedBody(locale)` accessors (iOS + Android + TS `pickLocalized`) apply a 2-level cascade — `titleByLocale[locale]` → `titleByLocale["fr"]` — and return `""` when both are missing so the admin sees the gap in the UI immediately. Empty strings in the map count as missing.
/users/{userId}                    → User profile (nickname, FCM token, platform, updatedAt)
/reports/{reportId}                → Player reports (admin-only, in-app moderation)
/eventRegistrations/{registrationId} → PP-52 pre-paid event registrations from the public web form. Top-level + decoupled from `/games` (web form runs before any in-app Game exists). One doc per inscription: `{batchId, playerName, teamName, email, phone, teamSize, code (6-char alphanum, unique within batch), paid, createdAt, paidAt, stripeSessionId, stripePaymentIntentId, refunded, refundedAt, locale, consentAcknowledgedAt}`. Writes are admin-SDK only via `createPendingRegistration` + `confirmRegistrationPayment` Cloud Functions. The same webhook also processes `charge.refunded`: a FULL refund flips `paid: false` + `refunded: true` so the `validateRegistrationCode` / `lookupGameByValidationCode` callables (which filter `where paid == true`) stop matching the code; partial refunds are ignored. Reads are locked (`firestore.rules: if false`) — JoinFlow goes through callables. Linked to a Game via `Game.registrationBatchId == batchId`.
```

The `gameCode` is derived from the document ID (first 6 chars, uppercased).

## Cross-platform parity

**This is critical**: iOS and Android must produce identical results for all game logic. Several pure functions are duplicated across platforms and must stay in sync:

- **Zone computation**: `findLastUpdate()`, `deterministicDriftCenter()`, `interpolateZoneCenter()`, `processRadiusUpdate()`
- **Seeded random**: Uses splitmix64-style PRNG with unsigned shifts (used at runtime for jammer noise)
- **Game timer logic**: Countdown, zone check, winner detection, power-up activation detection
- **Normal mode settings**: `calculateNormalModeSettings()` — auto-computes shrink params from duration
- **Wizard zone helpers (PP-13 / PP-14, Phase 1 reference mirrors)**: `computeZoneRadius`, `generateDriftSeed`, `pickInitialZoneCenter` in iOS `Models/GameSettings.swift` and Android `model/GameSettings.kt`. These mirror `functions/src/zoneCalculation.ts` (PP-69 source of truth). Pinned by `ZoneCalculationTests.swift` + `ZoneCalculationTest.kt` + `functions/test/zoneCalculation.test.ts` (golden distances 50 m / 500 m / 1 km / 2 km / 10 km, interior-margin invariant, lens-containment invariant). Scheduled for removal in PP-13 Phase 2 / PP-14 Phase 2.
- **Power-up spawning reference**: `generatePowerUps()` exists on iOS + Android as a reference mirror of the authoritative TS implementation in `functions/src/powerUpSpawn.ts`. Not called at runtime but kept so parity tests catch accidental drift. The TS port — `generatePowerUpsServer`, `interpolateZoneCenterServer`, `deterministicDriftCenterServer`, `filterEnabledTypesServer` — has its own unit tests under `functions/test/`.

When modifying any of these, **always update both platforms** (and the TS version when applicable) and verify with unit tests that outputs match for the same inputs.

## Authentication

Anonymous Firebase Auth. Users don't create accounts — a UID is generated on first launch and persists. The chicken's UID is stored as `creatorId`, hunters are identified by their UID in `hunterIds`. User profiles (nickname, FCM token, platform) are saved to `/users/{userId}`.

## Location tracking

Player positions and chicken presence live in **Firebase Realtime Database** (PP-102), not Firestore: high-frequency ephemeral data that RTDB bills by volume rather than per-operation. RTDB subtree, per game (schema identical across iOS / Android):

- `/games/{gameId}/chickenLocations/latest` → `{ lat, lng, ts (server ms), invisible }`. The chicken keeps writing during Invisibility with `invisible: true` (PP-87); hunters filter the marker out client-side, the GameMaster (PP-24) ignores the flag.
- `/games/{gameId}/hunterLocations/{hunterId}` → `{ lat, lng, ts }` (the `hunterId` is the RTDB key, not in the payload). Written when `chickenCanSeeHunters` is enabled OR a GameMaster has joined (`game.gameMasterIds.isNotEmpty()`, PP-24 Phase B).
- `/games/{gameId}/presence/chicken` → `{ online, ts }` set with an RTDB `onDisconnect`, so a dropped chicken flips offline server-side immediately (replaces the old 30s `lastHeartbeat` write to the game doc).
- `/games/{gameId}/meta` → `{ creatorId, chickenId, gameMode, status, hunterIds: {uid:true}, gameMasterIds: {uid:true} }`. Membership mirror written by the `mirrorGameMetaToRtdb` Cloud Function so the RTDB rules (`database.rules.json`) can authorize (RTDB rules can't read Firestore; `hunterIds`/`gameMasterIds` are maps, not arrays, so a rule can membership-test a key). The same function removes the whole `/games/{gameId}` RTDB subtree on `status == done` / deletion (cleanup).

Client behaviour (unchanged from the Firestore era):
- 10m minimum distance filter, 5s write throttle (client-side).
- Location tracking is gated behind game start times (no early position leaking).
- Power-ups affect location: Jammer adds ~200m noise, Radar Ping reveals the chicken in `stayInTheZone` (client-side gating), Invisibility flips the `invisible` flag on writes (PP-87, replaces the pre-PP-87 "stop writing" gate).
- iOS reads/writes via `ApiClient` (FirebaseDatabase `observe` / `setValue`); Android via `FirestoreRepository` (RTDB `ValueEventListener` / `setValue`).

## Security rules

See `firestore.rules`. Key principle: the creator has full control over their game, hunters can only update `hunterIds`, `winners`, and `powerUps` (active effects) fields. Subcollection writes are restricted by role. **Power-up creation is denied to all clients** (`allow create: if false`) — only the Cloud Function (which bypasses rules via admin SDK) can spawn them. Power-up collection uses transactions to prevent double-collection.

No `@firebase/rules-unit-testing` harness is wired in yet. Rules coverage lives in `firestore.rules.gamemaster-tests.md` as a manual emulator checklist (PP-66) plus the platform unit tests that mirror the rule expectations in pure Swift / Kotlin (`Game.isChicken`, `markChallengeCompleted` transaction shape). A real harness is scheduled to land with PP-25.

## Conventions

- **Design assets**: All design work must use the assets from `/Assets` (fonts, images). A design direction (DA) reference page is available at `/Assets/index.html` — always consult it for visual guidelines.
- Custom fonts: `Early GameBoy` (retro pixel) and `Bangers` (display/headings) — sourced from `/Assets`
- Color palette: CRBeige, CROrange (#FE6A00), CRPink (#EF0778) — consistent hex values across platforms
- Dark mode: fully supported everywhere
- Profanity filter on nicknames (FR + EN, with leetspeak detection)
- **Player identifiers**: `teamName` is the label displayed everywhere in gameplay (map markers, leaderboard, submissions, GameMaster drawer, validation queue). `nickname` (= username) is an internal identifier for settings, profile, and auth — never displayed in gameplay. Every hunter must have a teamName: PP-90 collects it on the join sheet (pre-filled with the saved nickname), so it's always set before the user lands on the hunter map.
- Game codes: 6 chars, found codes: 4 digits
- All Firestore write operations use retry logic (3 attempts, exponential backoff)
- When adding a new feature, keep parity between iOS and Android implementations
- **Language**: All code, comments, variable/function names, and string literals in the codebase must be in English. No French or other languages in the source code.
- No CI/CD pipelines or linting tools configured

## Adding a new feature

1. **Both platforms** — implement on iOS AND Android with identical behavior
2. **Determinism** — if it involves game state computation, both platforms must produce the same result
3. **Firestore rules** — if it touches new fields, update `firestore.rules`
4. **Cloud Functions** — if it needs server-side scheduling or validation
5. **Tests** — both platforms have unit tests for models and game logic
6. **Documentation** — update `README.md`, `CLAUDE.md` (Firestore model, game concepts, conventions), and `.claude/rules/*.md` (iOS/Android/Functions guides) to reflect any new or changed functionality, field names, or architecture

### Preparing a release

When asked to prepare a release or create a build:

1. **Bump versions — iOS `MARKETING_VERSION` and Android `versionName` MUST match at all times.** Bumping one platform means bumping both, even if only one has functional changes. Build numbers stay independent: iOS `CURRENT_PROJECT_VERSION` resets to `1` when the marketing version bumps (convention from 1.7.1 / 1.8.1 / 1.9.1), Android `versionCode` is globally monotonic. If a platform genuinely has no user-visible delta, archive/bundle it anyway so the store carries the matching version, and call it out explicitly in the CHANGELOG (never leave `iOS: unchanged` while Android moves).
2. **Update CHANGELOG.md** — add a new section at the top with the version number, date, and all changes grouped by Added/Changed/Fixed. Include iOS and Android version info. Review git log since last release to capture everything.
3. **Pre-flight iOS icons** — App Store rejects icons with an alpha channel (error 90717). Check the 1024×1024 icons in `ios/PouleParty/Resources/Assets.xcassets/AppIcon.appiconset/` with `sips -g hasAlpha <file>.png`. If any return `hasAlpha: yes`, flatten them first (e.g. via Pillow: open as RGBA, paste onto an RGB background — black for Dark variants, white for Default — and save back).
   - **Export compliance** is pre-declared in `ios/PouleParty/Info.plist` via `ITSAppUsesNonExemptEncryption = false`. App uses only Apple-standard crypto (HTTPS/Firebase), which is exempt. Keep this key — it skips the App Encryption Documentation prompt at every App Store Connect upload.
4. **Build release artifacts**:
   - **iOS**: `xcodebuild archive -scheme PouleParty -configuration Release -destination 'generic/platform=iOS'` — **do NOT pass `-archivePath`** unless you manually move the archive into `~/Library/Developer/Xcode/Archives/YYYY-MM-DD/` afterwards; Xcode Organizer only scans that default path, so a custom path makes the archive invisible in the UI. Running from the `ios/` directory ensures xcodebuild finds the project. After archiving, run `open "$(ls -dt ~/Library/Developer/Xcode/Archives/*/PouleParty*.xcarchive | head -1)"` so Xcode pops directly on the fresh archive inside Organizer — saves the user from hunting for it manually.
   - **Android**: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew bundleProductionRelease` — AAB lands at `android/app/build/outputs/bundle/productionRelease/app-production-release.aab`.
5. **Write store copy** — prepare localized text in `RELEASE_NOTES.md`. **The two stores expect different formats, do not cross-paste.**
   - **App Store Connect** — **plain text, one block per locale tab** (English/French/Dutch). **No** XML-style `<en>…</en>` tags. Use markdown headers like `**English (U.S.)**` in `RELEASE_NOTES.md` to label each block; the ASC UI has its own locale tabs in the top-right of the version page.
   - **App Store Connect — "What's New in This Version"** — **never mention "Android", "Google Play", or any other platform**. Apple rejects under guideline 2.3.10. Build 1.8.1 (1) was rejected for exactly this reason.
   - **App Store Connect — "What's New in This Version"** — **4000 characters max per locale** (enforced by ASC). The App Review Information → Notes field shares the same 4000-char cap. Shorter is better; the field was only ever raised from tight historical limits, reviewers don't read past a paragraph or two anyway.
   - **App Store Connect — "Promotional Text"** — iOS-only field, **170 characters max per locale**, editable anytime without a new submission. Keep it evergreen (the game pitch), swap for campaigns. Always verify the length with `python3 -c "print(len('...'))"` before committing.
   - **Google Play Console** — **"Release notes" field** uses the multi-locale tag format `<en-US>…</en-US>` / `<fr-FR>…</fr-FR>` / `<nl-NL>…</nl-NL>`, **all three in the same field**. Android mentions are fine here. **500 characters max per locale** — Google Play rejects the upload (not the release, the upload itself) when a locale block exceeds 500 chars. Verify every `<xx-YY>…</xx-YY>` block before pasting with `awk '/<en-US>/{in=1;next} in && /<\/en-US>/{exit} in{printf "%s",$0}' RELEASE_NOTES.md | wc -c` (repeat for each locale). Target ≤ 450 to keep margin for a one-word tweak later.
   - **RELEASE_NOTES.md structure** (since 1.8.1, do not revert): each release section starts with a `> ⚠️ Do not paste the Summary paragraph` warning, then an internal `**Summary (internal, do not paste):**` paragraph, then labelled paste targets with headers that name the exact store + field (`## 📱 App Store Connect — field "What's New in This Version"`, `## 📱 App Store Connect — field "Promotional Text"`, `## 🤖 Google Play Console — field "Release notes"`, `## 📝 App Store Connect — field "App Review Information → Notes"`). The old `## What's New` header was ambiguous and got pasted into ASC — do not reintroduce it.
6. **Write App Review Notes** (App Store Connect, "App Review Information → Notes" field) — **mandatory whenever the release touches sensitive permissions** (location, camera, push). Always include:
   - For changes to `NSLocation*UsageDescription`, a one-liner explaining what the new strings say and that tracking stops when the game ends.
   - If a new permission is added, an in-app reproduction path so the reviewer can land on the prompt without guessing.
7. **Deploy server-side changes.** This is part of the release, not something to leave for the user: a mobile build that hits an un-deployed backend handler will 404 or crash in prod. Check diffs and deploy what moved:
   - `git diff HEAD functions/src/` → if non-empty, `cd functions && npm run build && firebase deploy --only functions --project pouleparty-ba586 && firebase deploy --only functions --project pouleparty-prod`. Both projects, always, staging first so a bad change is caught before prod.
   - `git diff HEAD firestore.rules` → if non-empty, `firebase deploy --only firestore:rules --project pouleparty-ba586 && firebase deploy --only firestore:rules --project pouleparty-prod`.
   - `git diff HEAD web/` → if non-empty and touching user-facing copy (Terms, Privacy), deploy web hosting per `.claude/rules/web.md`.
   - Skip silently when the diff is empty. No-op deploys are not worth the confirmation.
8. **List next steps.** Only truly manual things remain: upload the iOS archive from Organizer to App Store Connect, upload the AAB to Play Console production, and paste the per-locale blocks from `RELEASE_NOTES.md` into each store field.

### Zero Warnings Policy

**The codebase must compile with zero warnings on both iOS and Android.** After every build:
- If Xcode logs contain warnings → fix them immediately, even if unrelated to the current task.
- If Gradle/Android Studio logs contain warnings → fix them immediately, even if unrelated to the current task.

This includes deprecation warnings, unused variable warnings, type inference warnings, and any other compiler/linter warnings. No warning is acceptable.
