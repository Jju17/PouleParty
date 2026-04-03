# PouleParty Android - Agent Instructions

## Architecture: MVVM + Hilt + Jetpack Compose

Standard Android architecture: each screen has a `Screen` composable and a `ViewModel`. ViewModels are injected by Hilt and expose a `StateFlow<UiState>` collected by the screen.

### Navigation

Compose Navigation with a `NavHost` in `navigation/AppNavigation.kt`. Routes are string-based with arguments (e.g., `chicken_map/{gameId}`). Navigation callbacks are passed down from `AppNavigation` to each screen composable.

Auth happens at the navigation root level in a `LaunchedEffect` — anonymous Firebase sign-in + FCM token save. The app waits for auth before rendering any route.

Start destination: `home` if onboarding is completed (checked via SharedPreferences), otherwise `onboarding`.

### Dependency Injection

Hilt with a single `AppModule` providing singletons: `FirebaseAuth`, `FirebaseFirestore`, `FusedLocationProviderClient`, `SharedPreferences`, and the Mapbox access token.

Repositories are `@Singleton` classes injected into ViewModels:
- **FirestoreRepository** — all Firestore CRUD + realtime streams via `callbackFlow`. Write operations use `withRetry`. Chicken/hunter location writes are fire-and-forget (no suspend, no await).
- **LocationRepository** — `FusedLocationProviderClient` wrapper. Exposes `locationFlow(): Flow<Point>` with 5s interval and 10m displacement filter.

### How to Add a New Screen

1. Create `ui/<feature>/<Feature>Screen.kt` (composable) and `ui/<feature>/<Feature>ViewModel.kt`
2. ViewModel: `@HiltViewModel class FooViewModel @Inject constructor(...)` with a `_uiState: MutableStateFlow<UiState>`
3. Screen: `@Composable fun FooScreen(onNavigateBack: () -> Unit, ...)` — receives navigation callbacks as parameters
4. Add route to `Routes` object in `AppNavigation.kt` and wire it in the `NavHost`
5. If the screen needs a `gameId`, it comes from `SavedStateHandle` in the ViewModel

### BaseMapViewModel

Both `ChickenMapViewModel` and `HunterMapViewModel` extend `BaseMapViewModel`, which extracts shared logic:
- Stream job lifecycle management
- Power-up proximity detection and collection (with dedup to prevent double-collect)
- Cross-player power-up activation detection
- Notification display helpers

### Shared Pure Logic

`ui/GameTimerHelper.kt` contains all pure game logic functions (zone check, countdown, radius update, drift, interpolation, winner detection). These mirror `Components/GameTimerLogic.swift` on iOS and **must stay in sync**.

`ui/PowerUpSpawnHelper.kt` handles deterministic power-up generation — mirrors iOS `PowerUpSpawnLogic.swift`.

`ui/RoadSnapService.kt` snaps coordinates to roads via Mapbox Directions API.

### Key Difference from iOS: Enum Serialization

Android stores `gameMod` and `status` as **raw strings** in the `Game` data class (e.g., `"followTheChicken"`, `"waiting"`), because Firestore's `toObject()` deserialization works with strings. Access the typed enum via computed properties: `game.gameModEnum`, `game.gameStatusEnum`. Same pattern for `PowerUp.type` → `PowerUp.typeEnum`.

iOS uses Swift enums with `Codable` directly, so they serialize/deserialize as strings automatically.

### Key Files to Know

| Area | Where to look |
|------|---------------|
| App entry | `PoulePartyApp.kt`, `MainActivity.kt` |
| Navigation | `navigation/AppNavigation.kt` |
| All models | `model/` directory |
| All Firestore ops | `data/FirestoreRepository.kt` |
| Location | `data/LocationRepository.kt` |
| Game logic (pure) | `ui/GameTimerHelper.kt` |
| Colors | `ui/theme/Color.kt` |
| Theme | `ui/theme/Theme.kt` |
| Constants | `AppConstants.kt` |
| Shared UI components | `ui/components/` |
| Base map ViewModel | `ui/BaseMapViewModel.kt` |

### Testing

Unit tests use JUnit 4 + MockK + kotlinx-coroutines-test. Pattern:
- Mock repositories with `mockk<FirestoreRepository>(relaxed = true)`
- Use `coEvery { repo.someMethod() } returns ...` for stubbing
- Test ViewModels by collecting UiState emissions

Tests are in `test/java/dev/rahier/pouleparty/` split into `model/` (data class tests) and `ui/` (ViewModel tests).

### Build

```bash
cd android && ./gradlew assembleDebug          # Build
cd android && ./gradlew test                    # All tests
cd android && ./gradlew testDebugUnitTest --tests "dev.rahier.pouleparty.GameTest"  # Specific test
```

Requires `JAVA_HOME` pointing to Android Studio's bundled JDK. Product flavors: `staging` and `production`, each with their own `google-services.json`.

### Mapbox

Uses `maps-compose-ndk27` variant (for 16KB page size on Android 15+). Access token stored in `res/values/strings.xml` as `mapbox_access_token` and provided via Hilt `@Named("mapboxAccessToken")`.
