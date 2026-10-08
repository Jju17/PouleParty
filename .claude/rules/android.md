# Android

Kotlin, Jetpack Compose, MVVM with Hilt. JDK 21, AGP 9 with built-in Kotlin, KSP, compileSdk 37, targetSdk 36, minSdk 26. Flavours `staging` and `production` (Firebase config in `app/src/<flavour>/google-services.json`, not committed).

## Commands

```bash
cd android
./gradlew testStagingDebugUnitTest lintStagingDebug   # the gate; Kotlin warnings and lint warnings fail the build
./gradlew koverXmlReportStagingDebug                   # coverage
./gradlew bundleProductionRelease                      # needs MAPBOX_ACCESS_TOKEN and the keystore
```

## Structure

- `data/`: one interface per backend boundary, Firebase implementations bound in `di/DataModule`.
  - `GameRepository` (Firestore documents and streams), `PresenceRepository` (RTDB positions and presence), `GameFunctions` (every callable, failures as `ApiException(ApiErrorCode)`), `ChallengeSubmissionRepository` (proof upload), `UserProfileRepository`, `PushRegistrar` (push registration by installation id).
  - Reads throw; `null` only means absent. Streams resubscribe after a listener error (`resubscribeOnError`) and keep their last value.
  - Pure parsers and selectors live next to them and are unit tested (`decodeZoneCircles`, `selectActiveGame`, `parseSubmitFoundCode`, `apiErrorCodeFor`).
- `model/`: Firestore models. Firestore maps a Kotlin `isX` property to `x`: use `@PropertyName` for any `is` field shared with iOS, and `@get:Exclude` on computed properties.
- `ui/<feature>/`: `XScreen` (Compose, no logic), `XViewModel` (state as `XUiState`, `onIntent(XIntent)`, one-shot `XEffect`).
- `ui/common/`: `UiText` (view models emit resources, screens resolve them), `LoadState` + `LoadStateScreen`, `ApiErrorMessages` (`errorMessageRes()`), `ActionErrorDialog`, `rememberReducedMotion`, `rememberIsOnline`, `teamNameOrDefault`, `currentLocale`.
- `ui/components/`: shared composables (`PrimaryButton`, `SecondaryButton`, `DangerButton`, `ZoneOverlay`, `FinalZoneOutline`, markers).
- `ui/theme/`: palette, `AccentText` for orange text (AA on beige), `GradientFire`, `Spacing`, `MapOverlayOffsets`, `MinTouchTarget`.
- `util/`: dates in Brussels time and the user's locale (`formatDateTime`, `formatTime`, `startOfToday`), proof media preparation.

## Rules

- Every visible string comes from `res/values*/strings.xml`, in English, French and Dutch together; quantities use `<plurals>`. `StringResourcesParityTest` checks key parity, no em dash, British spelling and untranslated Dutch.
- A failed load shows `LoadStateScreen` with retry; a failed action shows the translated reason. Never an empty list or the mock game.
- Mapbox content composables carry `@MapboxMapComposable`; functions mixing map and UI content suppress `COMPOSE_APPLIER_CALL_MISMATCH` (library and compiler disagree until Mapbox is upgraded).
- Unit tests run without Android stubs: `android.util.Log` has a JVM replacement in `src/test/java/android/util`, anything else is injected. Fixtures live in `src/test` (`Game.mock` is test-only).
- The Mapbox public token comes from `MAPBOX_ACCESS_TOKEN` (local.properties or environment) through `resValue`; release builds refuse to start without it.
