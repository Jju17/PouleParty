# PouleParty iOS - Agent Instructions

## Architecture: TCA (The Composable Architecture)

The entire app uses Point-Free's Composable Architecture. Every feature follows the same pattern: a `@Reducer` struct containing `State`, `Action`, and a `body` reducer, with the SwiftUI view in the same file.

### Navigation Model

The app is a **state machine** driven by `AppFeature`, which holds an enum state:

```
AppFeature.State (enum):
├── .onboarding → .home (on completion)
├── .home → .chickenMap | .hunterMap | .victory
├── .chickenMap → .home | .victory
├── .hunterMap → .home | .victory
└── .victory → .home
```

There's no `NavigationStack` at the root — transitions happen by swapping the enum case in `AppFeature`. Each feature that needs sub-navigation (like Home → ChickenConfig, or ChickenConfig → MapConfig) uses TCA's destination pattern (`@Presents`) or `NavigationStack` path internally.

### Dependency Injection

TCA's built-in `@Dependency` system. Each external dependency is a struct conforming to `DependencyKey` with a `liveValue` (real implementation) and `TestDependencyKey` with a `testValue` (stubs for testing).

Key dependencies:
- **ApiClient** — all Firestore operations. Streams use `AsyncStream` wrapping `addSnapshotListener`. Write operations use `withRetry`.
- **LocationClient** — CoreLocation wrapper. Single `LiveLocationManager` instance. Exposes `startTracking() -> AsyncStream<CLLocationCoordinate2D>`.
- **UserClient** — Firebase Auth (anonymous sign-in, account deletion, FCM token).
- **NotificationClient** — `UNUserNotificationCenter` wrapper.
- **LiveActivityClient** — ActivityKit Live Activities (lock screen / Dynamic Island during games).

### How to Add a New Feature

1. Create a file in `Features/` with a `@Reducer` struct containing `State`, `Action`, reducer `body`, and the SwiftUI `View`
2. If it's a new root-level screen, add a case to `AppFeature.State` enum and handle transitions in `AppFeature.body`
3. If it's a child destination (sheet/alert/push), use the `@Presents var destination: Destination.State?` pattern inside the parent feature
4. Add `testValue` stubs for any new dependencies

### How Effects Work in Map Screens

Both `ChickenMapFeature` and `HunterMapFeature` start multiple concurrent effects in their `.onTask` action:
- A 1-second timer tick (for countdown/radius updates)
- Game config stream (Firestore listener)
- Location tracking stream (gated behind start time)
- Power-up stream (if enabled)
- Power-up spawning (chicken only, initial batch)

These are merged with `.merge(effects)`. Some effects use `LockIsolated` to share mutable state (like active power-up timestamps) across concurrent closures.

### Shared Pure Logic

`Components/GameTimerLogic.swift` contains all pure functions shared between chicken and hunter map features: zone checking, countdown evaluation, radius updates, deterministic drift, winner detection, power-up activation detection, jammer noise, and seeded random.

`Components/PowerUpSpawnLogic.swift` handles deterministic power-up generation and road snapping.

These files have **no TCA dependency** — they're pure functions testable in isolation and must match their Android equivalents exactly.

### Key Files to Know

| Area | Where to look |
|------|---------------|
| App entry | `App/PoulePartyApp.swift`, `App/AppDelegate.swift` |
| Root navigation | `Features/App.swift` |
| All models | `Models/` directory |
| All Firestore ops | `Clients/ApiClient.swift` |
| Game logic (pure) | `Components/GameTimerLogic.swift` |
| Colors & theme | `Extensions/Color+Utils.swift` |
| Fonts | `Extensions/Font+Utils.swift` |
| Constants | `App/Constants.swift` |
| Live Activities | `PoulePartyWidgets/` target |

### Testing

Tests use TCA's `TestStore`. The pattern is:
1. Create a `TestStore` with initial state and overridden dependencies
2. Send actions, assert state changes with `store.send(.action) { $0.someField = expected }`
3. Receive effects with `store.receive(.effectAction)`

Test files are in `PoulePartyTests/`. There's a test plan at `PoulePartyTests.xctestplan`.

### Dependencies (SPM)

- `ComposableArchitecture` + `Sharing` (Point-Free)
- Firebase (Firestore, Auth, Analytics, Messaging)
- MapboxMaps
- Standard Apple frameworks (CoreLocation, ActivityKit, MapKit for address search)

### Build

```bash
xcodebuild -scheme PouleParty -configuration Debug build
xcodebuild -scheme PoulePartyTests test
```

Requires `GoogleService-Info.plist` in `ios/Firebase/` (gitignored, separate files for Staging and Production).
