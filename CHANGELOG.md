# Changelog

All notable changes to PouleParty are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/). Versions follow [Semantic Versioning](https://semver.org/).

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
