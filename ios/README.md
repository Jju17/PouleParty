# iOS — SwiftUI + TCA

PouleParty iOS app built with SwiftUI and The Composable Architecture (TCA).

## Requirements

- Xcode 16.2+
- iOS 17.0+ deployment target
- `GoogleService-Info.plist` in `Firebase/Staging/` and `Firebase/Production/` (gitignored)
- Mapbox access token in `Info.plist` (`MBXAccessToken`)

## Build & run

```bash
xcodebuild -scheme PouleParty -configuration Debug build
xcodebuild -scheme PoulePartyTests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.4' test
xcodebuild -scheme PoulePartySnapshotTests -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' test
```

Firebase config is copied at build time via a "Copy Firebase Config" build phase (Staging for Debug, Production for Release).

## Project structure

```
ios/
├── PouleParty/
│   ├── App/                    # Entry point, AppDelegate, Constants, ProfanityFilter
│   ├── Features/               # TCA features (12 reducers, each with State/Action/View)
│   ├── Models/                 # Game, PowerUp, Registration, Winner, LiveActivityModels
│   ├── Clients/                # TCA dependencies (ApiClient, LocationClient, AuthClient, etc.)
│   ├── Components/             # Shared UI + pure game logic (GameTimerLogic, PowerUpSpawnLogic)
│   ├── Extensions/             # Color+Utils, Font+Utils, GeoPoint+Utils
│   └── Resources/              # Fonts, audio, Assets.xcassets, Launch Screen
├── PoulePartyTests/            # Unit tests (Swift Testing + TCA TestStore)
├── PoulePartySnapshotTests/    # UI snapshot tests (swift-snapshot-testing)
├── PoulePartyWidgets/          # Live Activities (Lock Screen + Dynamic Island)
├── Firebase/                   # GoogleService-Info.plist (Staging/ + Production/)
├── Localizable.xcstrings       # Translations (en, fr, nl)
└── *.gpx                       # Location test files (Brussels, Namur, Lille, etc.)
```

## Architecture

Every feature is a `@Reducer` struct containing `State`, `Action`, reducer `body`, and the SwiftUI `View` in the same file.

**Root navigation** (`AppFeature`): state machine with enum cases `.onboarding`, `.home`, `.chickenMap`, `.hunterMap`, `.victory`.

**Dependency injection**: TCA's `@Dependency` system. Each client has a live and test implementation.

### Features (12)

| Feature | File | Purpose |
|---|---|---|
| App | `App.swift` | Root state machine |
| Home | `Home.swift` | Game menu, active game detection |
| Onboarding | `Onboarding.swift` | Tutorial slides |
| GameCreation | `GameCreation.swift` | Multi-step game creation form |
| ChickenMap | `ChickenMap.swift` | Chicken gameplay (map, timer, power-ups) |
| HunterMap | `HunterMap.swift` | Hunter gameplay (map, code entry, power-ups) |
| ChickenConfig | `ChickenConfig.swift` | Chicken setup |
| JoinFlow | `JoinFlow.swift` | Hunter join + registration flow |
| PlanSelection | `PlanSelection.swift` | Pricing plan picker |
| Victory | `Victory.swift` | End game leaderboard |
| Settings | `Settings.swift` | App settings |
| GameRules | `GameRules.swift` | Rules display |

### Clients (dependencies)

| Client | Purpose |
|---|---|
| `ApiClient` | All Firestore operations + realtime streams (`withRetry`, 3 attempts) |
| `LocationClient` | CoreLocation wrapper, `startTracking() -> AsyncStream` |
| `AuthClient` | Firebase anonymous auth |
| `LiveActivityClient` | ActivityKit Live Activities |
| `NotificationClient` | UNUserNotificationCenter |
| `FCMTokenManager` | FCM token storage |

## Shared pure logic

These files have **no dependencies** and must match their Android equivalents exactly:

| File | Purpose |
|---|---|
| `GameTimerLogic.swift` | Zone checks, countdown, radius updates, drift, winner/power-up detection, seeded random |
| `PowerUpSpawnLogic.swift` | Reference implementation of deterministic power-up generation. Kept in sync with `functions/src/powerUpSpawn.ts` for cross-platform parity tests — **not called at runtime** since spawning moved server-side in April 2026. |

## Dependencies (SPM)

| Package | Version |
|---|---|
| swift-composable-architecture | 1.25.3 |
| swift-sharing | 2.8.0 |
| swift-snapshot-testing | 1.19.2 |
| firebase-ios-sdk | 12.11.0 |
| mapbox-maps-ios | 11.20.2 |
| turf-swift | 4.0.0 |

## Game model

Nested structs matching the Firestore document structure:

```swift
Game {
    timing: Timing { start, end, headStartMinutes }
    zone: Zone { center, finalCenter, radius, shrinkIntervalMinutes, shrinkMetersPerUpdate, driftSeed }
    pricing: Pricing { model, pricePerPlayer, deposit, commission }
    registration: GameRegistration { required, closesMinutesBefore }
    powerUps: GamePowerUps { enabled, enabledTypes, activeEffects: ActiveEffects { ... } }
}
```

Computed properties and `findLastUpdate()` are in `Game+Computed.swift`.

## Localization

3 languages: English, French, Dutch. All strings in `Localizable.xcstrings` (JSON format). Add translations for all 3 when adding user-facing text.

## Fonts

- **Bangers** — always use the `BangerText()` view helper (direct `.font(.custom(...))` causes clipping)
- **Early GameBoy** — use `.font(.gameboy(size:))`

## Live Activities

Lock Screen and Dynamic Island widgets in `PoulePartyWidgets/`. Shows game phase, timer, zone status during active gameplay.

## Tests

- **Unit tests** (`PoulePartyTests/`): 11 files, ~185 tests. TCA TestStore for features, direct tests for pure logic.
- **Snapshot tests** (`PoulePartySnapshotTests/`): UI regression tests for GameCreation screens.
- **Test plan**: `PoulePartyTests.xctestplan`
