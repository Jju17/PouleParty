# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is this project?

A cross-platform (iOS + Android) location-based mobile game. One player is the **Chicken** (Poule) who must evade **Hunters**. The game takes place on a real map with a shrinking circular zone. Players' positions are synced in real-time via Firebase Firestore.

## Game modes

- **followTheChicken** (default): Hunters see a circle following the Chicken; Chicken doesn't see Hunters
- **stayInTheZone**: Fixed zone that shrinks over time, no position sharing
- **mutualTracking**: Both sides see each other in real-time

## Build & run

Both platforms require a Firebase project with Firestore, Auth, and Analytics enabled.

### iOS

Open `ios/PouleParty.xcodeproj` in Xcode. Requires `GoogleService-Info.plist` (gitignored).

```bash
# Build
xcodebuild -scheme PouleParty -configuration Debug build

# Run all tests
xcodebuild -scheme PoulePartyTests test

# Run a specific test class
xcodebuild -scheme PoulePartyTests test -only-testing:PoulePartyTests/GameTests

# Run a specific test method
xcodebuild -scheme PoulePartyTests test -only-testing:PoulePartyTests/GameTests/testGameCode
```

GPX files in `ios/` (Bxl, La Rochelle, Lasne, Lille, Namur, Stockholm) simulate locations in the iOS Simulator. The `PouleParty` scheme defaults to `Bxl.gpx`.

### Android

Open `android/` in Android Studio. Requires `google-services.json` (gitignored). compileSdk 35, minSdk 26, JVM target 17.

```bash
# Build debug APK
cd android && ./gradlew assembleDebug

# Run all unit tests
cd android && ./gradlew test

# Run a specific test class
cd android && ./gradlew testDebugUnitTest --tests "dev.rahier.pouleparty.GameTest"

# Run instrumentation tests (requires emulator/device)
cd android && ./gradlew connectedAndroidTest

# Full build + tests
cd android && ./gradlew build
```

No CI/CD pipelines or linting tools are configured.

## Project structure

```
PouleParty/
├── ios/                          iOS app (SwiftUI + TCA)
│   ├── PouleParty/              Source (~25 Swift files)
│   ├── PouleParty.xcodeproj/
│   ├── PoulePartyTests/         4 test files (Game, Selection, ChickenMap, HunterMap)
│   └── *.gpx                    Simulator location files
└── android/                      Android app (Jetpack Compose + MVVM + Hilt)
    └── app/src/
        ├── main/java/dev/rahier/pouleparty/
        │   ├── model/           Data models (Game, ChickenLocation, HunterLocation)
        │   ├── data/            Repositories (FirestoreRepository, LocationRepository)
        │   ├── di/              Hilt DI module (AppModule)
        │   ├── navigation/      Compose Navigation (Routes + NavHost)
        │   └── ui/              Screens & ViewModels (chickenconfig/, chickenmap/, huntermap/, onboarding/, selection/, rules/, endgamecode/, components/, theme/)
        └── test/                8 test files (model + ViewModel tests)
```

## Tech stack

| Concern | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose + Material 3 |
| Architecture | TCA (Composable Architecture) | MVVM + Hilt DI |
| Maps | MapKit | Google Maps Compose |
| Location | CoreLocation | FusedLocationProvider (Play Services) |
| Async | AsyncStream / async-await | Kotlin Coroutines + Flow |
| Backend | Firebase Firestore, Auth, Analytics | Firebase Firestore, Auth, Analytics |

## Firestore data model

```
/games/{gameId}                    → Game document
  /chickenLocations/{docId}        → ChickenLocation (location: GeoPoint, timestamp)
  /hunterLocations/{hunterId}      → HunterLocation (hunterId, location: GeoPoint, timestamp)
```

## Game model (shared across platforms)

Key fields: `id`, `name`, `numberOfPlayers` (default 10), `radiusIntervalUpdate` (minutes, default 5), `startTimestamp` (default +5min from now), `endTimestamp` (default +65min from now), `initialCoordinates` (default Brussels 50.8466, 4.3528), `initialRadius` (meters, default 1500), `radiusDeclinePerUpdate` (meters, default 100), `gameMod`.

- `gameCode` = first 6 chars of ID, uppercased
- `findLastUpdate()` walks from startDate in interval steps to find next shrink time and current radius
- Both models have a `.mock` static property for testing
- Android `GameMod` enum uses `firestoreValue` string for Firestore serialization

## iOS architecture (TCA)

- **AppFeature** (`App.swift`): Root reducer, enum-based state machine with cases: `.onboarding`, `.selection`, `.chickenMap`, `.hunterMap`
- Each feature = Reducer + State + Action + View in a single file
- **ApiClient** (`ApiClient.swift`): TCA dependency wrapping all Firestore operations (CRUD + AsyncStream listeners via `addSnapshotListener`)
- **LocationClient** (`LocationClient.swift`): TCA dependency wrapping CoreLocation (auth status, permission requests, tracking via AsyncStream)
- Navigation is state-driven via AppFeature's enum state
- Onboarding flag: `UserDefaults "hasCompletedOnboarding"`
- Distance filter: 10m minimum movement

## Android architecture (MVVM + Hilt)

- **AppNavigation** (`navigation/AppNavigation.kt`): Compose NavHost with routes: `onboarding`, `selection`, `chicken_config/{gameId}`, `chicken_map/{gameId}`, `hunter_map/{gameId}`
- Each screen = Screen composable + ViewModel in separate files under `ui/<feature>/`
- **FirestoreRepository** (`data/FirestoreRepository.kt`): Singleton, all Firestore ops (suspend CRUD + `callbackFlow` listeners)
- **LocationRepository** (`data/LocationRepository.kt`): Singleton, FusedLocationProvider
- **AppModule** (`di/AppModule.kt`): Hilt `@Module` providing Firebase & Location singletons
- Onboarding flag: `SharedPreferences "pouleparty" → "hasCompletedOnboarding"`

## Conventions

- iOS: Each TCA feature groups State, Action, Reducer, and View in a single Swift file
- Android: Screen and ViewModel are in separate files under `ui/<feature>/`
- Shared UI components go in `ui/components/` (Android) or as standalone files (iOS)
- iOS utility extensions: `Color+Utils`, `Date+Utils`, `Font+Utils`, `GeoPoint+Utils`, `Image+Utils`, `UUID+Utils`
- Android theme files: `Color.kt`, `Theme.kt`, `Type.kt`
- Custom fonts: `Early GameBoy.ttf`, `Bangers-Regular.ttf` (retro gaming aesthetic)
- Color palette: CRBeige, CROrange, CRPink (custom brand colors)
- Location writes are throttled (5s intervals) to limit Firestore usage
- When adding a new feature, keep parity between iOS and Android implementations
