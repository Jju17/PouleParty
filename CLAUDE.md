# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is this project?

A cross-platform (iOS + Android) location-based mobile game. One player is the **Chicken** (Poule) who must evade **Hunters** within a shrinking circular zone on a real map. Positions are synced in real-time via Firebase Firestore.

There's also a **web** landing page (React/Vite) and **Cloud Functions** backend for game lifecycle management and push notifications.

## Game Concepts

### Roles
- **Chicken** (creator): Creates the game, defines the zone, runs and hides. Shows a 4-digit "found code" to hunters who physically find them.
- **Hunters**: Join via a 6-character game code. Must physically find the chicken and enter the found code to win.

### Game Modes
- **Follow the Chicken** (`followTheChicken`): The zone circle follows the chicken's live GPS. Hunters see where the chicken is (within the zone). Chicken doesn't see hunters unless `chickenCanSeeHunters` is enabled.
- **Stay in the Zone** (`stayInTheZone`): Fixed zone that shrinks and drifts deterministically. No position sharing at all (except via Radar Ping power-up).

### Zone Mechanics
The zone is a circle that shrinks periodically. The shrink interval and amount are configurable. In "Stay in the Zone" mode, the center drifts deterministically using a seed stored in the game document — every client computes the same drift. There's also a "final zone" point: as the radius shrinks, the center interpolates linearly from start to final position.

### Power-Ups
6 types, split between chicken and hunter. **Server-authoritative spawning** (since April 2026): a Cloud Function (`spawnPowerUpBatch`) generates deterministically from the game's `zone.driftSeed`, snaps to nearest roads via Mapbox API, and writes docs directly — clients only consume via `powerUpsStream`. Initial batch (5) fires at `timing.start`; periodic batches (2) fire at each zone shrink. Collected by proximity (30m). Some have timed effects tracked as timestamps in `powerUps.activeEffects` on the game document. In `stayInTheZone` mode, position-dependent chicken power-ups (invisibility, decoy, jammer) are automatically disabled server-side.

**Activation is also atomic** — clients call `ApiClient.activatePowerUp` / `FirestoreRepository.activatePowerUp` with an optional `activeEffectField`. The implementation uses a Firestore transaction to update both the power-up doc (`activatedAt`, `expiresAt`) and the game doc (`powerUps.activeEffects.<field>`) in one commit, so the state can't half-apply.

### Game Lifecycle
1. Chicken creates game → Firestore document created → Cloud Functions schedule status transitions and notifications via Cloud Tasks
2. Game starts at `timing.start` (status: waiting → inProgress)
3. Hunters get a head start delay (`timing.headStartMinutes`) before the hunt begins
4. Zone shrinks periodically until collapse or `timing.end`
5. Hunters who find the chicken enter the found code → added to `winners` array
6. Game ends when time runs out, zone collapses, chicken cancels, or all hunters find the chicken

### Pricing Modes (Stripe)

Three pricing modes drive when money changes hands:

| Mode | Value | Creator pays | Hunters pay | Promo codes | Where created |
|------|-------|--------------|-------------|-------------|---------------|
| **Free** | `free` | ❌ | ❌ | ❌ | Client (direct Firestore write) |
| **Forfait** | `flat` | ✅ `pricePerPlayer × maxPlayers` at creation | ❌ | ✅ (creator-applied at payment) | Server-only via `createCreatorPaymentSheet` / `redeemFreeCreation`; webhook flips `pending_payment` → `waiting` |
| **Caution** | `deposit` | ❌ *(for now — creator fee deferred)* | ✅ `pricing.deposit` at registration (mandatory) | ❌ | Game doc: client. Registration: server-only via `createHunterPaymentSheet` + webhook |

**Stripe integration**:
- Mobile SDKs: `StripePaymentSheet` (iOS SPM), `com.stripe:stripe-android` (Android). Drop-in UI supports **Bancontact + card** for EUR/BE via `automatic_payment_methods`.
- Debug/Release publishable keys: iOS build settings (`STRIPE_PUBLISHABLE_KEY` in pbxproj, exposed via Info.plist); Android `buildConfigField` per `staging` / `production` flavor.
- Secrets (server-side): Firebase secrets `STRIPE_SECRET_KEY` + `STRIPE_WEBHOOK_SECRET`, separate values per project (`pouleparty-ba586` test, `pouleparty-prod` live).
- Bancontact redirect: iOS URL scheme `pouleparty://stripe-redirect` in Info.plist + `StripeAPI.handleURLCallback` in AppDelegate; Android handled automatically by the SDK.
- Promo codes: created in Stripe Dashboard, validated server-side via `validatePromoCode` (rate-limited per user). 100%-off codes skip PaymentSheet entirely and create the game via `redeemFreeCreation`.
- Webhook dedup: `paymentEvents/{eventId}` Firestore doc; retries are idempotent.
- Firestore rules enforce: Forfait games only creatable via admin SDK; Caution registrations only creatable via admin SDK; `users.stripeCustomerId` is admin-SDK-only; `paymentEvents` / `rateLimits` are fully locked.

## Build & run

### iOS

```bash
xcodebuild -scheme PouleParty -configuration Debug build
xcodebuild -scheme PoulePartyTests test
xcodebuild -scheme PoulePartyTests test -only-testing:PoulePartyTests/GameTests
```

Requires `GoogleService-Info.plist` in `ios/Firebase/` (gitignored). GPX files in `ios/` simulate locations in the Simulator.

### Android

```bash
cd android && ./gradlew assembleDebug
cd android && ./gradlew test
cd android && ./gradlew testDebugUnitTest --tests "dev.rahier.pouleparty.GameTest"
```

compileSdk 35, minSdk 26, JVM target 17. Requires `google-services.json` (gitignored). Product flavors: `staging` and `production`.

### Cloud Functions

```bash
cd functions && npm run build && firebase deploy --only functions
```

### Web

```bash
cd web && npm run dev
```

## Tech stack

| Concern | iOS | Android |
|---|---|---|
| UI | SwiftUI | Jetpack Compose + Material 3 |
| Architecture | TCA (Composable Architecture) | MVVM + Hilt DI |
| Maps | Mapbox Maps SDK | Mapbox Maps Compose SDK |
| Location | CoreLocation | FusedLocationProvider (Play Services) |
| Async | AsyncStream / async-await | Kotlin Coroutines + Flow |
| Backend | Firebase Firestore, Auth, Analytics, Messaging | Firebase Firestore, Auth, Analytics, Messaging |

## Firestore data model

```
/games/{gameId}
  ├── id, name, maxPlayers, gameMode, chickenCanSeeHunters
  ├── foundCode, hunterIds, status, winners, creatorId, lastHeartbeat
  ├── timing: { start, end, headStartMinutes }
  ├── zone: { center, finalCenter, radius, shrinkIntervalMinutes, shrinkMetersPerUpdate, driftSeed }
  ├── pricing: { model, pricePerPlayer, deposit, commission }
  ├── registration: { required, closesMinutesBefore }
  ├── powerUps: { enabled, enabledTypes, activeEffects: { invisibility, zoneFreeze, radarPing, decoy, jammer } }
  ├── payment: { provider, amountCents, currency, promoCodeId, baseAmountCents, paymentIntentId, paidAt }
  │                                  → Only for Forfait games (created server-side)
  ├── /chickenLocations/latest     → Single doc, overwritten each position update
  ├── /hunterLocations/{hunterId}  → One doc per hunter, overwritten
  ├── /powerUps/{powerUpId}        → One doc per spawned power-up (collected/activated state)
  ├── /registrations/{userId}      → One doc per registered hunter (teamName, paid, joinedAt)
  └── /challengeCompletions/{hunterId} → One doc per hunter who has completed at least one challenge (completedChallengeIds, totalPoints, teamName)

/challenges/{challengeId}          → Global, dev-managed challenge catalog (title, body, points, lastUpdated)
/users/{userId}                    → User profile (nickname, FCM token, platform, updatedAt, stripeCustomerId [admin-only])
/registrations/{docId}             → Event registration (admin-only, used by web)
/paymentEvents/{eventId}           → Stripe webhook dedupe (admin-only)
/rateLimits/{key}                  → Server-side rate-limit counters, e.g. `promo_{uid}` (admin-only)
```

The `gameCode` is derived from the document ID (first 6 chars, uppercased).

## Cross-platform parity

**This is critical**: iOS and Android must produce identical results for all game logic. Several pure functions are duplicated across platforms and must stay in sync:

- **Zone computation**: `findLastUpdate()`, `deterministicDriftCenter()`, `interpolateZoneCenter()`, `processRadiusUpdate()`
- **Seeded random**: Uses splitmix64-style PRNG with unsigned shifts (used at runtime for jammer noise)
- **Game timer logic**: Countdown, zone check, winner detection, power-up activation detection
- **Normal mode settings**: `calculateNormalModeSettings()` — auto-computes shrink params from duration
- **Power-up spawning reference**: `generatePowerUps()` exists on iOS + Android as a reference mirror of the authoritative TS implementation in `functions/src/powerUpSpawn.ts`. Not called at runtime but kept so parity tests catch accidental drift. The TS port — `generatePowerUpsServer`, `interpolateZoneCenterServer`, `deterministicDriftCenterServer`, `filterEnabledTypesServer` — has its own unit tests under `functions/test/`.

When modifying any of these, **always update both platforms** (and the TS version when applicable) and verify with unit tests that outputs match for the same inputs.

## Authentication

Anonymous Firebase Auth. Users don't create accounts — a UID is generated on first launch and persists. The chicken's UID is stored as `creatorId`, hunters are identified by their UID in `hunterIds`. User profiles (nickname, FCM token, platform) are saved to `/users/{userId}`.

## Location tracking

- 10m minimum distance filter, 5s write throttle to Firestore
- Chicken writes to `chickenLocations/latest` (simple overwrite, not a growing collection)
- Hunter writes only when `chickenCanSeeHunters` is enabled
- Location tracking is gated behind game start times (no early position leaking)
- Power-ups affect location: Invisibility stops writes, Jammer adds ~200m noise, Radar Ping forces writes even in stayInTheZone

## Security rules

See `firestore.rules`. Key principle: the creator has full control over their game, hunters can only update `hunterIds`, `winners`, and `powerUps` (active effects) fields. Subcollection writes are restricted by role. **Power-up creation is denied to all clients** (`allow create: if false`) — only the Cloud Function (which bypasses rules via admin SDK) can spawn them. Power-up collection uses transactions to prevent double-collection. Registration creation enforces the `registration.closesMinutesBefore` deadline if set.

## Conventions

- **Design assets**: All design work must use the assets from `/Assets` (fonts, images). A design direction (DA) reference page is available at `/Assets/index.html` — always consult it for visual guidelines.
- Custom fonts: `Early GameBoy` (retro pixel) and `Bangers` (display/headings) — sourced from `/Assets`
- Color palette: CRBeige, CROrange (#FE6A00), CRPink (#EF0778) — consistent hex values across platforms
- Dark mode: fully supported everywhere
- Profanity filter on nicknames (FR + EN, with leetspeak detection)
- Game codes: 6 chars, found codes: 4 digits
- All Firestore write operations use retry logic (3 attempts, exponential backoff)
- When adding a new feature, keep parity between iOS and Android implementations
- **Language**: All code, comments, variable/function names, and string literals in the codebase must be in English. No French or other languages in the source code.
- No CI/CD pipelines or linting tools configured

## Adding a new feature

1. **Both platforms** — implement on iOS AND Android with identical behavior
2. **Determinism** — if it involves game state computation, both platforms must produce the same result
3. **Firestore rules** — if it touches new fields, update `firestore.rules`
4. **Cloud Functions** — if it needs server-side scheduling or validation
5. **Tests** — both platforms have unit tests for models and game logic
6. **Documentation** — update `README.md`, `CLAUDE.md` (Firestore model, game concepts, conventions), and `.claude/rules/*.md` (iOS/Android/Functions guides) to reflect any new or changed functionality, field names, or architecture

### Preparing a release

When asked to prepare a release or create a build:

1. **Bump versions** — increment `versionCode`/`versionName` in `android/app/build.gradle.kts` and `CURRENT_PROJECT_VERSION`/`MARKETING_VERSION` in the Xcode project
2. **Update CHANGELOG.md** — add a new section at the top with the version number, date, and all changes grouped by Added/Changed/Fixed. Include iOS and Android version info. Review git log since last release to capture everything.
3. **Pre-flight iOS icons** — App Store rejects icons with an alpha channel (error 90717). Check the 1024×1024 icons in `ios/PouleParty/Resources/Assets.xcassets/AppIcon.appiconset/` with `sips -g hasAlpha <file>.png`. If any return `hasAlpha: yes`, flatten them first (e.g. via Pillow: open as RGBA, paste onto an RGB background — black for Dark variants, white for Default — and save back).
   - **Export compliance** is pre-declared in `ios/PouleParty/Info.plist` via `ITSAppUsesNonExemptEncryption = false`. App uses only Apple-standard crypto (HTTPS/Firebase), which is exempt. Keep this key — it skips the App Encryption Documentation prompt at every App Store Connect upload.
4. **Build release artifacts**:
   - **iOS**: `xcodebuild archive -scheme PouleParty -configuration Release -destination 'generic/platform=iOS'` — **do NOT pass `-archivePath`** unless you manually move the archive into `~/Library/Developer/Xcode/Archives/YYYY-MM-DD/` afterwards; Xcode Organizer only scans that default path, so a custom path makes the archive invisible in the UI. Running from the `ios/` directory ensures xcodebuild finds the project. After archiving, run `open "$(ls -dt ~/Library/Developer/Xcode/Archives/*/PouleParty*.xcarchive | head -1)"` so Xcode pops directly on the fresh archive inside Organizer — saves the user from hunting for it manually.
   - **Android**: `cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew bundleProductionRelease` — AAB lands at `android/app/build/outputs/bundle/productionRelease/app-production-release.aab`.
5. **Write store copy** — prepare localized text in `RELEASE_NOTES.md`. **The two stores expect different formats, do not cross-paste.**
   - **App Store Connect** — **plain text, one block per locale tab** (English/French/Dutch). **No** XML-style `<en>…</en>` tags. Use markdown headers like `**English (U.S.)**` in `RELEASE_NOTES.md` to label each block; the ASC UI has its own locale tabs in the top-right of the version page.
   - **App Store Connect — "What's New in This Version"** — **never mention "Android", "Google Play", or any other platform**. Apple rejects under guideline 2.3.10. Build 1.8.1 (1) was rejected for exactly this reason.
   - **App Store Connect — "Promotional Text"** — iOS-only field, **170 characters max per locale**, editable anytime without a new submission. Keep it evergreen (the game pitch), swap for campaigns. Always verify the length with `python3 -c "print(len('...'))"` before committing.
   - **Google Play Console** — **"Release notes" field** uses the multi-locale tag format `<en-US>…</en-US>` / `<fr-FR>…</fr-FR>` / `<nl-NL>…</nl-NL>`, **all three in the same field**. Android mentions are fine here.
   - **RELEASE_NOTES.md structure** (since 1.8.1, do not revert): each release section starts with a `> ⚠️ Do not paste the Summary paragraph` warning, then an internal `**Summary (internal, do not paste):**` paragraph, then labelled paste targets with headers that name the exact store + field (`## 📱 App Store Connect — field "What's New in This Version"`, `## 📱 App Store Connect — field "Promotional Text"`, `## 🤖 Google Play Console — field "Release notes"`, `## 📝 App Store Connect — field "App Review Information → Notes"`). The old `## What's New` header was ambiguous and got pasted into ASC — do not reintroduce it.
6. **Write App Review Notes** (App Store Connect, "App Review Information → Notes" field) — **mandatory whenever the release touches the paid flows (Forfait / Caution / promo codes) or new sensitive permissions**. Always include:
   - Step-by-step reproduction to reach the Stripe PaymentSheet so the reviewer can verify Apple Pay integration: "tap Create Party → choose Forfait → complete wizard → Pay"; "tap Start → enter a Caution game code → Pay".
   - The fact that the Apple Pay button is hidden by the Stripe SDK when the device has no Apple Pay wallet (by design, not a bug) — reviewers occasionally test on a wallet-less device.
   - A working **99 %-off promotion code** (`APPLE_REVIEW_99`, see `.claude/rules/ios.md`) so the reviewer can reach the PaymentSheet for a negligible amount without completing a real charge. **Do not offer 100 %-off** — that short-circuits the sheet via `redeemFreeCreation` and the reviewer can never see the Apple Pay button.
   - Merchant ID (`merchant.dev.rahier.pouleparty`) and entitlement (`com.apple.developer.in-app-payments`) for reference.
   - For changes to `NSLocation*UsageDescription`, a one-liner explaining what the new strings say and that tracking stops when the game ends.
7. **Deploy server-side changes.** This is part of the release, not something to leave for the user: a mobile build that hits an un-deployed backend handler will 404 or crash in prod. Check diffs and deploy what moved:
   - `git diff HEAD functions/src/` → if non-empty, `cd functions && npm run build && firebase deploy --only functions --project pouleparty-ba586 && firebase deploy --only functions --project pouleparty-prod`. Both projects, always, staging first so a bad change is caught before prod.
   - `git diff HEAD firestore.rules` → if non-empty, `firebase deploy --only firestore:rules --project pouleparty-ba586 && firebase deploy --only firestore:rules --project pouleparty-prod`.
   - `git diff HEAD web/` → if non-empty and touching user-facing copy (Terms, Privacy), deploy web hosting per `.claude/rules/web.md`.
   - Skip silently when the diff is empty. No-op deploys are not worth the confirmation.
8. **List next steps.** Only truly manual things remain: upload the iOS archive from Organizer to App Store Connect, upload the AAB to Play Console production, paste the per-locale blocks from `RELEASE_NOTES.md` into each store field, and (first paid release only) create the `APPLE_REVIEW_99` coupon in Stripe Dashboard if it doesn't exist yet.

### Zero Warnings Policy

**The codebase must compile with zero warnings on both iOS and Android.** After every build:
- If Xcode logs contain warnings → fix them immediately, even if unrelated to the current task.
- If Gradle/Android Studio logs contain warnings → fix them immediately, even if unrelated to the current task.

This includes deprecation warnings, unused variable warnings, type inference warnings, and any other compiler/linter warnings. No warning is acceptable.
