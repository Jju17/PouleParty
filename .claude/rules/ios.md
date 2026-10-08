# iOS

SwiftUI with The Composable Architecture. iOS 17+, Firebase 13, Mapbox Maps. Versions, deployment target and strict concurrency (`targeted`) live in `ios/Config/Versions.xcconfig`, shared by every target.

## Commands

```bash
cd ios
xcodebuild -scheme PoulePartyTests -destination 'platform=iOS Simulator,name=iPhone 17' test   # unit tests (Swift Testing + TestStore)
xcodebuild -scheme PoulePartySnapshotTests -destination 'platform=iOS Simulator,name=iPhone 16' test
swiftlint lint --strict                                                                          # zero warnings, as in CI
```

Local builds need `Firebase/Staging/GoogleService-Info.plist` (placeholder in `ci/firebase/`) and `Config/Secrets.xcconfig` copied from `Config/Secrets.xcconfig.example` with the Mapbox public token.

## Structure

- `Clients/`: one TCA dependency per boundary.
  - `ApiClient` (interface) with `ApiClient+Live` (Firestore, RTDB, callables) and `ApiClient+Test`; payload decoding in `ApiDecoding`, pure and tested.
  - Callable failures become `ApiError` with an `ApiErrorCode` read from `details.code`; screens show `error.userMessage`, never the raw server message.
  - Streams go through `resubscribingStream`: a listener error retries with a capped delay and keeps the last value.
  - `UserClient` (nickname, push registration, account deletion), `LocationClient`, `NotificationClient`, `HapticClient`, `RemoteConfigClient`, `LiveActivityClient`, `AnalyticsClient`, and `now` for the wall clock.
- `Features/<Feature>/`: reducer, view and sub-views. Views hold no network call and no game rule.
- `Components/GameLogic/`: pure game logic (`GameTimerLogic`, `ChallengeProgress`, `PowerUpAvailability`, `ZoneScheduleLoader`). `ParityReference/` holds the mirrors kept only for the wizard preview and parity tests.
- `Components/Map/`: overlays shared by the chicken, hunter and GameMaster maps, including `DebugQAPanel`.
- `Extensions/`: palette (`Color+Utils`, with `accentText` for orange text and `errorText`), fonts scaled with Dynamic Type.
- `Simulation/`: GPX routes for the Simulator.

## Rules

- Every visible string is a key in `Localizable.xcstrings`, translated to French and Dutch in the same change.
- Dates and times display in Brussels time (`AppConstants.gameTimeZone`); reducers read time from `@Dependency(\.now)` or `continuousClock`, never `Date()` or `Task.sleep`.
- A failed load shows `LoadErrorBanner` with retry; a failed action shows the translated reason, announced to VoiceOver (`announcedError`). Never an empty list.
- Animations check `accessibilityReduceMotion`; text uses `Font.banger` / `Font.gameboy`, which follow Dynamic Type.
- Orange text uses `Color.accentText` (AA on beige), never `Color.CROrange`; `CROrange` stays for fills, icons and large decorative shapes.
