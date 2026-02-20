# PouleParty

## What is this project?

A cross-platform (iOS + Android) location-based mobile game. One player is the **Chicken** (Poule) who must evade **Hunters**. The game takes place on a real map with a shrinking circular zone. Players' positions are synced in real-time via Firebase Firestore.

## Game modes

- **followTheChicken** (default): Hunters see a circle following the Chicken; Chicken doesn't see Hunters
- **stayInTheZone**: Fixed zone that shrinks over time, no position sharing
- **mutualTracking**: Both sides see each other in real-time

## Project structure

```
PouleParty/
├── ios/                          iOS app (SwiftUI + TCA)
│   ├── PouleParty/              Source (25 Swift files)
│   ├── PouleParty.xcodeproj/
│   └── PoulePartyTests/
└── android/                      Android app (Jetpack Compose + MVVM + Hilt)
    └── app/src/main/java/dev/rahier/pouleparty/
        ├── model/               Data models (Game, ChickenLocation, HunterLocation)
        ├── data/                Repositories (FirestoreRepository, LocationRepository)
        ├── di/                  Hilt DI module (AppModule)
        ├── navigation/          Compose Navigation (Routes + NavHost)
        └── ui/                  Screens & ViewModels
            ├── chickenconfig/
            ├── chickenmap/
            ├── huntermap/
            ├── onboarding/
            ├── selection/
            ├── rules/
            ├── endgamecode/
            ├── components/      Shared UI (CountdownView, SelectionButton)
            └── theme/           Colors, Typography, Theme
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

Key fields: `id`, `name`, `numberOfPlayers` (default 10), `radiusIntervalUpdate` (minutes, default 5), `startTimestamp`, `endTimestamp`, `initialCoordinates` (default Brussels 50.8466, 4.3528), `initialRadius` (meters, default 1500), `radiusDeclinePerUpdate` (meters, default 100), `gameMod`.

- `gameCode` = first 6 chars of ID, uppercased
- `findLastUpdate()` walks from startDate in interval steps to find next shrink time and current radius

## iOS architecture (TCA)

- **AppFeature** (`App.swift`): Root reducer, enum-based state machine with cases: `.onboarding`, `.selection`, `.chickenMap`, `.hunterMap`
- Each feature = Reducer + State + Action + View (in a single file)
- **ApiClient** (`ApiClient.swift`): TCA dependency wrapping all Firestore operations (CRUD + AsyncStream listeners)
- **LocationClient** (`LocationClient.swift`): TCA dependency wrapping CoreLocation (auth status, permission requests, tracking via AsyncStream)
- Navigation is state-driven via AppFeature's enum state
- Onboarding flag: `UserDefaults "hasCompletedOnboarding"`
- Distance filter: 10m minimum movement

### Key iOS files

| File | Role |
|---|---|
| `PoulePartyApp.swift` | Entry point, Firebase init |
| `App.swift` | Root AppFeature reducer + AppView |
| `ApiClient.swift` | Firestore dependency (streams + CRUD) |
| `LocationClient.swift` | CoreLocation dependency |
| `Game.swift` | Game model + GameMod enum |
| `ChickenMap.swift` | Chicken map feature (reducer + view) |
| `HunterMap.swift` | Hunter map feature (reducer + view) |
| `Selection.swift` | Main menu feature |
| `ChickenConfig.swift` | Game config feature |
| `Onboarding.swift` | Onboarding feature |
| `MapOverlays.swift` | Circle/zone map overlays |
| `ChickenLocation.swift` / `HunterLocation.swift` | Location models |

## Android architecture (MVVM + Hilt)

- **AppNavigation** (`navigation/AppNavigation.kt`): Compose NavHost with routes: `onboarding`, `selection`, `chicken_config/{gameId}`, `chicken_map/{gameId}`, `hunter_map/{gameId}`
- Each screen = Screen composable + ViewModel (in separate files)
- **FirestoreRepository** (`data/FirestoreRepository.kt`): Singleton, all Firestore ops (suspend CRUD + `callbackFlow` listeners)
- **LocationRepository** (`data/LocationRepository.kt`): Singleton, FusedLocationProvider
- **AppModule** (`di/AppModule.kt`): Hilt `@Module` providing Firebase & Location singletons
- Onboarding flag: `SharedPreferences "pouleparty" → "hasCompletedOnboarding"`
- `GameMod` enum uses `firestoreValue` string for Firestore serialization

### Key Android files

| File | Role |
|---|---|
| `PoulePartyApp.kt` | Application class (`@HiltAndroidApp`) |
| `MainActivity.kt` | Single activity hosting Compose |
| `AppNavigation.kt` | Routes + NavHost |
| `AppModule.kt` | Hilt DI bindings |
| `FirestoreRepository.kt` | All Firestore operations |
| `LocationRepository.kt` | GPS tracking |
| `Game.kt` | Game model + GameMod enum |
| `ChickenMapViewModel.kt` / `ChickenMapScreen.kt` | Chicken map |
| `HunterMapViewModel.kt` / `HunterMapScreen.kt` | Hunter map |
| `SelectionViewModel.kt` / `SelectionScreen.kt` | Main menu |
| `ChickenConfigViewModel.kt` / `ChickenConfigScreen.kt` | Game config |
| `OnboardingViewModel.kt` / `OnboardingScreen.kt` | Onboarding |

## Build & run

- **iOS**: Open `ios/PouleParty.xcodeproj` in Xcode, requires `GoogleService-Info.plist` (gitignored)
- **Android**: Open `android/` in Android Studio, requires `google-services.json` (gitignored). compileSdk 35, minSdk 26, JVM target 17
- Both require a Firebase project with Firestore, Auth, and Analytics enabled

## Conventions

- iOS: Each TCA feature groups State, Action, Reducer, and View in a single Swift file
- Android: Screen and ViewModel are in separate files under `ui/<feature>/`
- Shared UI components go in `ui/components/` (Android) or as standalone files (iOS)
- Utility extensions: `Color+Utils`, `Date+Utils`, `Font+Utils`, `GeoPoint+Utils`, `Image+Utils`, `UUID+Utils` (iOS)
- Theme files: `Color.kt`, `Theme.kt`, `Type.kt` (Android)
- Custom fonts: `Early GameBoy.ttf`, `Bangers-Regular.ttf` (retro gaming aesthetic)
- Color palette: CRBeige, CROrange, CRPink (custom brand colors)
- Location writes are throttled (5s intervals) to limit Firestore usage
