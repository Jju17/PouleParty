# Android — Jetpack Compose + MVVM + Hilt

PouleParty Android app built with Jetpack Compose, MVVM architecture, and Hilt dependency injection.

## Requirements

- Android Studio with JDK 17
- compileSdk 35, minSdk 26, targetSdk 35
- `google-services.json` in `app/src/debug/` (staging) and `app/src/production/` (production) — gitignored
- `MAPBOX_DOWNLOADS_TOKEN` in `gradle.properties` or `local.properties`
- Mapbox access token in `res/values/strings.xml`

## Build & run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android && ./gradlew assembleDebug
cd android && ./gradlew test
cd android && ./gradlew testDebugUnitTest --tests "dev.rahier.pouleparty.model.GameTest"
```

**Product flavors:** `staging` and `production` (differ only in Firebase config).
**Build variants:** `stagingDebug`, `stagingRelease`, `productionDebug`, `productionRelease`.

## Project structure

```
android/app/src/main/java/dev/rahier/pouleparty/
├── AppConstants.kt                  # All magic numbers, Firestore collections, preferences
├── MainActivity.kt                  # @AndroidEntryPoint, splash screen, Compose entry
├── PoulePartyApp.kt                 # @HiltAndroidApp, Firebase init, FCM channels
├── PouleFCMService.kt               # Firebase Cloud Messaging handler
├── di/
│   └── AppModule.kt                 # Hilt @Module (Firebase, Location, SharedPrefs, Mapbox)
├── data/
│   ├── FirestoreRepository.kt       # All Firestore CRUD + realtime streams, withRetry
│   └── LocationRepository.kt        # FusedLocationProviderClient wrapper, locationFlow()
├── model/
│   ├── Game.kt                      # Nested data classes (Timing, Zone, Pricing, etc.)
│   ├── GameMod.kt, GameStatus.kt, PricingModel.kt  # Enums with firestoreValue
│   ├── PowerUp.kt                   # PowerUp + PowerUpType enum (6 types)
│   ├── Registration.kt, Winner.kt, ChickenLocation.kt, HunterLocation.kt
│   └── GameSettings.kt              # calculateNormalModeSettings()
├── navigation/
│   └── AppNavigation.kt             # Compose NavHost, routes, anonymous auth + FCM
└── ui/
    ├── BaseMapViewModel.kt          # Abstract VM shared by Chicken/Hunter map
    ├── GameTimerHelper.kt           # Pure logic (must match iOS GameTimerLogic.swift)
    ├── PowerUpSpawnHelper.kt        # Pure logic (must match iOS PowerUpSpawnLogic.swift)
    ├── RoadSnapService.kt           # Mapbox Directions API road snapping
    ├── components/                  # 12 reusable UI components
    ├── theme/                       # Color.kt (66 colors), Theme.kt, Type.kt
    ├── home/                        # HomeScreen, HomeViewModel, JoinFlowState
    ├── onboarding/                  # OnboardingScreen, OnboardingViewModel
    ├── gamecreation/                # GameCreationScreen, GameCreationViewModel, steps
    ├── chickenconfig/               # ChickenConfig + ChickenMapConfig + PowerUpSelection
    ├── chickenmap/                  # ChickenMapScreen, ChickenMapViewModel
    ├── huntermap/                   # HunterMapScreen, HunterMapViewModel
    ├── victory/                     # VictoryScreen, VictoryViewModel
    ├── planselection/               # PlanSelectionScreen, PlanSelectionViewModel
    ├── settings/                    # SettingsScreen, SettingsViewModel
    └── rules/                       # GameRulesScreen (stateless)
```

## Architecture

**MVVM pattern:** Each screen has a `@Composable` Screen + `@HiltViewModel` ViewModel exposing `StateFlow<UiState>`.

**Navigation:** Compose Navigation with string-based routes and arguments in `AppNavigation.kt`.

**DI:** Single `AppModule` providing singletons (FirebaseAuth, Firestore, FusedLocationProvider, SharedPreferences, Mapbox token).

### ViewModels (11)

| ViewModel | Extends BaseMapVM? | Purpose |
|---|---|---|
| HomeViewModel | No | Game menu, join flow, active game detection |
| OnboardingViewModel | No | Tutorial slides |
| GameCreationViewModel | No | Multi-step game creation |
| ChickenConfigViewModel | No | Chicken setup |
| ChickenMapConfigViewModel | No | Map/zone configuration |
| PlanSelectionViewModel | No | Pricing plan picker |
| ChickenMapViewModel | Yes | Chicken gameplay (map, timer, power-ups) |
| HunterMapViewModel | Yes | Hunter gameplay (map, code entry, power-ups) |
| VictoryViewModel | No | End game leaderboard |
| SettingsViewModel | No | App settings |

### BaseMapViewModel

Abstract base for `ChickenMapViewModel` and `HunterMapViewModel`. Provides:
- Stream lifecycle management (`streamJobs`)
- Power-up proximity checks and collection
- Cross-player power-up detection
- Notification helpers

## Game model

Nested data classes matching the Firestore document structure:

```kotlin
Game(
    timing: Timing,              // start, end, headStartMinutes
    zone: Zone,                  // center, finalCenter, radius, shrinkIntervalMinutes, shrinkMetersPerUpdate, driftSeed
    pricing: Pricing,            // model, pricePerPlayer, deposit, commission
    registration: GameRegistration, // required, closesMinutesBefore
    powerUps: GamePowerUps       // enabled, enabledTypes, activeEffects: ActiveEffects
)
```

**Enum serialization:** `gameMode` and `status` are raw strings for Firestore `toObject()`. Use `game.gameModEnum` / `game.gameStatusEnum` for typed access.

**Mutations:** Nested `.copy()` — `game.copy(zone = game.zone.copy(radius = 1000.0))`

## Shared pure logic

These files must match their iOS equivalents exactly:

| File | iOS equivalent | Purpose |
|---|---|---|
| `GameTimerHelper.kt` | `GameTimerLogic.swift` | Zone checks, countdown, radius updates, drift, winner/power-up detection |
| `PowerUpSpawnHelper.kt` | `PowerUpSpawnLogic.swift` | Deterministic power-up generation |

## Dependencies

| Dependency | Version |
|---|---|
| Kotlin | 2.2.10 |
| Compose BOM | 2024.12.01 |
| Hilt | 2.53.1 |
| Firebase BOM | 33.7.0 |
| Mapbox Maps | 11.20.2 (ndk27) |
| Play Services Location | 21.3.0 |
| MockK | 1.13.13 |
| Coroutines Test | 1.9.0 |

## Localization

3 languages: English (`values/strings.xml`), French (`values-fr/strings.xml`), Dutch (`values-nl/strings.xml`). Add translations in all 3 when adding user-facing text.

## Tests

- **Framework:** JUnit 4 + MockK + kotlinx-coroutines-test
- **Location:** `app/src/test/java/dev/rahier/pouleparty/` (21 test files)
- **Pattern:** Mock repositories with `mockk<FirestoreRepository>(relaxed = true)`, stub with `coEvery`
- **Run:** `./gradlew test`

## Release build

- R8 minification + resource shrinking enabled
- ProGuard rules keep model classes, Firebase, Hilt, Play Services
- Signing via `keystore.properties` (not in repo)

## App info

- **Package:** `dev.rahier.pouleparty`
- **Application ID:** `dev.rahier.pouleparty2`
- **Version:** 1.1.3 (code 8)
