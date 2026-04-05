# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is this project?

A cross-platform (iOS + Android) location-based mobile game. One player is the **Chicken** (Poule) who must evade **Hunters** within a shrinking circular zone on a real map. Positions are synced in real-time via Firebase Firestore.

There's also a **web** landing page (React/Vite) and **Cloud Functions** backend for game lifecycle management and push notifications.

## Game Concepts

### Roles
- **Chicken** (creator): Creates the game, defines the zone, runs and hides. Shows a 4-digit "found code" to hunters who physically find them.
- **Hunters**: Join via a 6-character game code. Must physically find the chicken and enter the found code to win.

### Game Modes
- **Follow the Chicken** (`followTheChicken`): The zone circle follows the chicken's live GPS. Hunters see where the chicken is (within the zone). Chicken doesn't see hunters unless `chickenCanSeeHunters` is enabled.
- **Stay in the Zone** (`stayInTheZone`): Fixed zone that shrinks and drifts deterministically. No position sharing at all (except via Radar Ping power-up).

### Zone Mechanics
The zone is a circle that shrinks periodically. The shrink interval and amount are configurable. In "Stay in the Zone" mode, the center drifts deterministically using a seed stored in the game document — every client computes the same drift. There's also a "final zone" point: as the radius shrinks, the center interpolates linearly from start to final position.

### Power-Ups
6 types, split between chicken and hunter. They spawn deterministically on the map (using the game's `driftSeed`), get snapped to nearest roads via Mapbox API, and are collected by proximity (30m). Some have timed effects tracked as `active{Type}Until` timestamps on the game document. In `stayInTheZone` mode, position-dependent chicken power-ups (invisibility, decoy, jammer) are automatically disabled.

### Game Lifecycle
1. Chicken creates game → Firestore document created → Cloud Functions schedule status transitions and notifications via Cloud Tasks
2. Game starts at `startTimestamp` (status: waiting → inProgress)
3. Hunters get a head start delay (`chickenHeadStartMinutes`) before the hunt begins
4. Zone shrinks periodically until collapse or `endTimestamp`
5. Hunters who find the chicken enter the found code → added to `winners` array
6. Game ends when time runs out, zone collapses, chicken cancels, or all hunters find the chicken

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
/games/{gameId}                    → Game config + live state (status, winners, active effects)
  /chickenLocations/latest         → Single doc, overwritten each position update
  /hunterLocations/{hunterId}      → One doc per hunter, overwritten
  /powerUps/{powerUpId}            → One doc per spawned power-up (collected/activated state)

/fcmTokens/{userId}                → FCM token for push notifications
/registrations/{docId}             → Event registration (admin-only, used by web)
```

The `gameCode` field is stored separately (first 6 chars of ID, uppercased) to enable Firestore queries by code.

## Cross-platform parity

**This is critical**: iOS and Android must produce identical results for all game logic. Several pure functions are duplicated across platforms and must stay in sync:

- **Zone computation**: `findLastUpdate()`, `deterministicDriftCenter()`, `interpolateZoneCenter()`, `processRadiusUpdate()`
- **Power-up spawning**: `generatePowerUps()` — deterministic positions from seed
- **Seeded random**: Uses splitmix64-style PRNG with unsigned shifts
- **Game timer logic**: Countdown, zone check, winner detection, power-up activation detection
- **Normal mode settings**: `calculateNormalModeSettings()` — auto-computes shrink params from duration

When modifying any of these, **always update both platforms** and verify with unit tests that outputs match for the same inputs.

## Authentication

Anonymous Firebase Auth. Users don't create accounts — a UID is generated on first launch and persists. The chicken's UID is stored as `creatorId`, hunters are identified by their UID in `hunterIds`. FCM tokens are saved to `/fcmTokens/{userId}`.

## Location tracking

- 10m minimum distance filter, 5s write throttle to Firestore
- Chicken writes to `chickenLocations/latest` (simple overwrite, not a growing collection)
- Hunter writes only when `chickenCanSeeHunters` is enabled
- Location tracking is gated behind game start times (no early position leaking)
- Power-ups affect location: Invisibility stops writes, Jammer adds ~200m noise, Radar Ping forces writes even in stayInTheZone

## Security rules

See `firestore.rules`. Key principle: the creator has full control over their game, hunters can only update `hunterIds`, `winners`, and `active*Until` fields. Subcollection writes are restricted by role. Power-up collection uses transactions to prevent double-collection.

## Conventions

- **Design assets**: All design work must use the assets from `/Assets` (fonts, images). A design direction (DA) reference page is available at `/Assets/index.html` — always consult it for visual guidelines.
- Custom fonts: `Early GameBoy` (retro pixel) and `Bangers` (display/headings) — sourced from `/Assets`
- Color palette: CRBeige, CROrange (#FE6A00), CRPink (#EF0778) — consistent hex values across platforms
- Dark mode: fully supported everywhere
- Profanity filter on nicknames (FR + EN, with leetspeak detection)
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
6. **README.md** — update the root `README.md` to reflect any new or changed functionality

### 4. Zero Warnings Policy

**The codebase must compile with zero warnings on both iOS and Android.** After every build:
- If Xcode logs contain warnings → fix them immediately, even if unrelated to the current task.
- If Gradle/Android Studio logs contain warnings → fix them immediately, even if unrelated to the current task.

This includes deprecation warnings, unused variable warnings, type inference warnings, and any other compiler/linter warnings. No warning is acceptable.
