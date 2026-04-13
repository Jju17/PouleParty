# Changelog

All notable changes to PouleParty are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/). Versions follow [Semantic Versioning](https://semver.org/).

---

## [1.4.1] — 2026-04-13

**iOS**: 1.4.1 (6) · **Android**: 1.4.1 (13)

### Fixed
- **Migration not running on iOS** — `MigrationManager.runIfNeeded()` was called before `FirebaseApp.configure()`, so the Firestore migration silently skipped. Moved to `AppDelegate.didFinishLaunchingWithOptions` after Firebase init.
- **Migration timing on Android** — migration now runs inside the auth `LaunchedEffect` (after sign-in is confirmed), preventing race conditions where `currentUser` could be null.
- **Flaky test** — `updateStartTime strips seconds` no longer depends on current time of day.

---

## [1.4.0] — 2026-04-13

**iOS**: 1.4.0 (5) · **Android**: 1.4.0 (12)

### Added
- **Haptic feedback** on both platforms for key game events: countdown ticks, out-of-zone warning, power-up collected/activated, found code correct/wrong, winner found, game over
- **Screen stays on during gameplay** — the display no longer turns off while on the chicken or hunter map (iOS + Android)
- **User profile on Firestore** — nickname and FCM token are now stored in a `/users/{userId}` collection, enabling server-side nickname lookup and per-player push notifications
- **Power-up map markers on Android** — power-ups now display as colored circles with white Material icons (matching iOS style), replacing plain emojis
- **Migration manager** on both platforms — versioned migration system that runs once per version upgrade; v1.4.0 silently migrates existing users' nickname + FCM token to the new `/users` collection
- **55+ new tests** — MigrationManager version comparison (iOS + Android), nickname sync to Firestore (iOS TCA TestStore), power-up icon mappings (Android)

### Changed
- **Firestore migration**: FCM tokens moved from `/fcmTokens/{userId}` to `/users/{userId}` (also stores nickname and platform)
- **Cloud Functions** updated to read tokens from `/users` collection
- **All FCM token writes now use merge** to avoid overwriting nickname data

### Fixed
- **Leaderboard dark edges on iOS** — fixed gradient background not extending to screen edges on the leaderboard sheet
- **Unused variable warning** in HunterMap.swift (`chickenCanSeeHunters`)

---

## [1.3.0] — 2026-04-11

**iOS**: 1.3.0 (4) · **Android**: 1.3.0 (11)

### Added
- **Dynamic start time**: game start time minimum now adapts to registration settings instead of a fixed 2-hour delay. Open join → `now + 1 min`, registration required → `now + deadline + 5 min`
- **Registration step moved before start time** in the game creation flow so the start time picker always reflects the correct minimum
- **Automatic start date clamping**: the start date is pushed forward if it falls below the minimum — checked on every step transition, registration change, and game start
- **Firebase Crashlytics on Android** (iOS was already using it) — crash reports now collected on both platforms
- **Firebase Analytics custom events** on both platforms: `game_created`, `game_joined`, `game_started`, `game_ended`, `hunter_found_chicken`, `hunter_wrong_code`, `power_up_collected`, `power_up_activated`, `registration_completed`, `onboarding_completed`
- **Push notifications actually displayed on Android**: `PouleFCMService` now builds a proper `NotificationCompat` with title, body, channel, deep-link, big text style — previously only logged
- **Push notification localization keys added to iOS**: `notif_chicken_start_title/body`, `notif_hunter_start_title/body`, `notif_zone_shrink_title/body`, `notif_hunter_found_title/body` in EN/FR/NL — previously notifications arrived with empty titles
- **Safe enum decoding on iOS**: unknown Firestore enum values now fall back to sensible defaults instead of crashing (`GameStatus` → `.waiting`, `GameMode` → `.followTheChicken`, `PricingModel` → `.free`, `PowerUpType` → `.zonePreview`)
- **Cross-platform parity tests** with hardcoded reference values for `seededRandom`, `deterministicDriftCenter`, `interpolateZoneCenter`, `calculateNormalModeSettings`, `generatePowerUps` — any drift between iOS and Android now breaks the tests immediately
- **360+ new unit tests** across both platforms covering game creation, zone logic, power-ups, enum decoding, profanity filter, minimum start date, edge cases

### Changed
- **Registration step order**: moved from step 10 to step 4 (before start time) in game creation (iOS + Android)
- **PRNG alignment**: `seededRandom`, `deterministicDriftCenter`, `generatePowerUps` now use identical arithmetic on both platforms (same constants, same overflow behaviour)
- **`.now` captured once** in `findLastUpdate`, `processRadiusUpdate`, `evaluateCountdown` to prevent clock drift during iteration (iOS + Android)
- **`evaluateCountdown` at t=0**: Android now uses strict `> 0` check matching iOS, both show the completion text at exactly t=0
- **FCM token timestamp on Android** uses `FieldValue.serverTimestamp()` matching iOS (was client-side `Timestamp.now()`)
- **Head start default on Android** aligned to `0` minutes (was `5`), matching iOS
- **`aps-environment` entitlement on iOS** now resolves via `$(APS_ENVIRONMENT)` — `development` for Debug, `production` for Release (was hard-coded `development`)
- **Default FCM notification channel/icon** declared in Android `AndroidManifest.xml`
- **Profanity filter word lists** unified across platforms, duplicates removed
- **20+ Android UI strings** moved from hardcoded to `stringResource()` with FR/NL translations

### Fixed
- **Notifications silently dropped on Android**: `onMessageReceived` did not build a system notification. Fixed with proper `NotificationCompat.Builder` + channel resolution + `titleLocKey`/`bodyLocKey` lookup via `resources.getIdentifier()`
- **Notifications arrived empty on iOS**: localization keys were missing in `Localizable.xcstrings`. Added all 8 keys in EN/FR/NL
- **Firestore security**: chicken/hunter locations and power-ups readable only by game participants (was any authenticated user)
- **Firestore security**: power-up active effects writable only by game participants (was any authenticated user)
- **`seededRandom` Android constant typo**: third splitmix64 constant was wrong (`ecef4715` → `ecceee15`), causing decoy positions to differ between platforms
- **Cloud Functions token cleanup**: now loops over all batches of 30 stale tokens (was truncating to first 30)
- **Cloud Functions capacity race**: `registerForEvent` now uses transactional read for capacity check
- **Cloud Functions zone shrink spam**: `shrinkIntervalMinutes < 1` is now rejected (prevents hundreds of Cloud Tasks)
- **Cloud Functions winner notification**: no longer sent for games already in `done` status
- **Cloud Functions task scheduling**: wrapped in try/catch with error logging
- **Cloud Functions FCM observability**: added `[FCM] "..." → N tokens: X succeeded, Y failed` logs with per-token error codes
- **7 missing French strings** in Android (`game_code_label`, `schedule_label`, `power_ups_label`, `advanced_label`, `head_start_label`, `mode_label`, `power_ups_count_format`)

### Security
- Firestore game creation validation rules added: `radius 1-50000m`, `maxPlayers 1-50`, `start < end`, `headStart >= 0`, `shrinkInterval >= 1`
- `functions/.env.*` added to `.gitignore` so production env files cannot be committed
- Email validation regex tightened in Cloud Functions

---

## [1.2.1] — 2026-04-10

### Added
- **Final zone glow**: chicken always sees the final zone as a green glow circle on the gameplay map (iOS + Android)
- **Zone config description bar**: "Configure the zone" title + contextual subtitle on the map step (iOS, matching Android)
- **Recap view improvements**: added Role, Max Players, End Time, Power-up types, Pricing/Total price on both platforms
- **MapWarmUp utility**: pre-warms Metal device + Mapbox TileStore in background when user enters game creation or join flow (iOS)

### Changed
- **Map config uses Streets style** instead of Standard 3D for faster loading (iOS + Android)
- **Registration closes** only shown in recap when registration is required (was shown even for "Open join")
- **Recap fonts aligned**: both platforms use gameboy size 9 for recap rows
- **Game creation layout** (iOS): header (progress bar + dismiss) and bottom bar (back/next) are now fixed VStack elements, content cannot overlap them
- **Step transitions** (iOS): simplified to `.push(from:)` with `.linear(0.15s)` animation
- **Tapping outside zone in final-zone mode** now re-sets the start zone instead of being ignored (iOS + Android)
- **Map config camera**: initializes viewport from game state instead of default Brussels coordinates (iOS)
- **Map pins** (START/FINAL): added `.selected(true)` + `.ignoreCameraPadding(true)` to prevent disappearing (iOS)
- **Bottom bar styling**: blurred background matching app theme instead of `.thinMaterial` (iOS)
- **Firebase init**: moved `FirebaseApp.configure()` to AppDelegate to fix swizzler warning (iOS)

### Fixed
- **Progress bar stop indicator**: removed orange dot at end of progress bar (Android)
- **`isZoneConfigured`**: now checks `zone.center` and `zone.finalCenter` directly instead of computed properties, fixing false negatives with `@Shared` (iOS)

### Versions
- iOS: 1.2.1 (build 3)
- Android: 1.2.1 (code 10)

---

## [1.2.0] — 2026-04-10

### Added
- **Registration deadline**: chicken can set when registrations close before game start (15 min, 30 min, 1h, 2h, 1 day before, or at game start). Default: 15 minutes before.
- Snapshot tests for registration step (open join, required + deadline picker) and recap with registration info
- README.md per subdirectory (ios/, android/, functions/, web/)

### Changed
- **Firestore model refactor**: flat fields grouped into nested maps (`timing`, `zone`, `pricing`, `registration`, `powerUps.activeEffects`). Field renames: `gameMod` -> `gameMode`, `numberOfPlayers` -> `maxPlayers`, `radiusIntervalUpdate` -> `zone.shrinkIntervalMinutes`, `radiusDeclinePerUpdate` -> `zone.shrinkMetersPerUpdate`, `depositAmount` -> `pricing.deposit`, `commissionPercent` -> `pricing.commission`, 5x `active*Until` -> `powerUps.activeEffects.*`
- CLAUDE.md, README.md, and .claude/rules updated to reflect new model

### Fixed
- **Security**: power-up collection race condition — added `collectedBy == null` check in Firestore rules
- **Security**: event registration capacity bypass — atomic duplicate + capacity check via Firestore transaction
- **Security**: hunter location writes now verify user is in `hunterIds`
- Cloud Functions: Google Sheets API error handling (try-catch instead of silent failure)
- Cloud Functions: input validation for `radiusIntervalUpdate > 0`, timestamp ordering, non-negative head start
- Cloud Functions: hunter name sanitized (max 50 chars) in winner notifications
- Cloud Functions: expanded stale FCM token cleanup (4 error codes instead of 2)
- iOS: removed force unwraps (`activeDecoyUntil!`, `freezeStart!`/`freezeEnd!`)
- iOS: `userId` fallback from empty string to guard-let early return
- iOS: silent `try?` on critical API calls replaced with `do/catch` + error logging
- iOS: `.count > 0` replaced with `.isEmpty`
- iOS: decoy seed type mismatch (Int vs Long) fixed for cross-platform parity
- Android: removed force unwraps (`!!`) in CountdownView, HomeScreen, FirestoreRepository
- Android: `collectingPowerUpIds` race condition fixed with `Collections.synchronizedSet()`
- Android: `HttpURLConnection` leak fixed with `try/finally` + `disconnect()`
- Android: jammer noise constant extracted to `AppConstants.JAMMER_NOISE_DEGREES`
- Android: `"fcmTokens"` hardcoded string replaced with `AppConstants.COLLECTION_FCM_TOKENS`

### Versions
- iOS: 1.2.0 (build 2)
- Android: 1.2.0 (code 9)

---

## [1.1.3] — 2026-04-02

### Added
- Paid game flow (flat pricing + deposit pricing models)
- Game creation configuration flow (multi-step wizard)
- Registration system for paid games (team name, payment tracking)
- Plan selection screen (free, flat, deposit)
- Snapshot tests for game creation screens

### Changed
- Home screen renamed from "Selection" to "Home"
- New paid flow on Android aligned with iOS

### Fixed
- Resume game ("reprendre la partie") bug
- Dark mode issues on Android
- All compiler warnings resolved on both platforms

### Versions
- iOS: 1.1.0 (build 1)
- Android: 1.1.3 (code 8)

---

## [1.0.3] — 2026-03-15

### Added
- Power-ups system (6 types: invisibility, zone freeze, radar ping, decoy, jammer, speed boost)
- Deterministic power-up spawning with road snapping
- Separate start and final zone centers for Stay in the Zone mode
- Event registration on web landing page

### Fixed
- Invisibility location leak on iOS
- Zone freeze not actually freezing
- Atomic power-up collection (prevent double-collection)
- Power-up list safety, ID collisions, notification stacking
- Dark mode gradient issues

### Versions
- iOS: 1.0.3
- Android: 1.0.3
