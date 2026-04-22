# Changelog

All notable changes to PouleParty are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/). Versions follow [Semantic Versioning](https://semver.org/).

---

## [1.9.0], 2026-04-22

**iOS**: 1.9.0 (3) · **Android**: 1.9.0 (26)

Reliability + parity pass. Zero new features visible in the home screen, but a pile of silent bugs that would have bitten scale are now gone: the chicken's jammed position is deterministic across iOS + Android, the Stripe webhook can't double-process a retry, failed victory writes no longer silently ship the hunter to the leaderboard without a server record, Mapbox flakes no longer spawn power-ups in random positions. Onboarding now requires background location on both platforms, matches what the game actually needs to work when the phone is pocketed.

### Fixed

- **Jammer noise is now deterministic and cross-platform-consistent.** iOS used `Double.random(in:)` and Android used `Math.random()`, non-reproducible and impossible to parity-test. Both platforms now compute the offset from `seededRandom(game.zone.driftSeed ^ floor(nowSeconds))`, bucketed per second. Result: identical jitter on the same `(seed, now)`, tests can pin exact values, hunters can't infer the true position by averaging randomness.
- **Stripe webhook dedup race.** Two concurrent deliveries of the same `event.id` could both pass the `.get()` check before either `.set()` landed, producing double hunter registrations from one PaymentIntent. Claim is now atomic via `runTransaction`, with a 3-state machine, `claimed` / `in_flight` (returns 409 so Stripe retries with backoff) / `completed` (returns 200), plus a 5-minute stale-claim window that lets a crashed handler be re-claimed.
- **`addWinner` navigated to victory even on Firestore failure.** Hunter entered the right code, network blipped, client swallowed the error and sent them to the victory screen, server had no record of the win. Both platforms now show a retry alert holding the original `Winner` (same timestamp, same attempt count) so the write can be replayed without drifting analytics. Strings added in EN / FR / NL.
- **`snapToRoad` silently fell back on 4xx.** A persistent 401 / 404 burnt through all retries before failing; transient 5xx / 429 had no retry at all. Rewritten with typed `NonTransientMapboxError` that breaks out of the retry loop, explicit exponential backoff on transient errors (500 ms / 1 s / 2 s), throw-on-exhaust so Cloud Task retries the whole batch instead of persisting unsnapped coordinates.
- **Cross-platform `generatePowerUps` order divergence.** iOS and Android filtered `enabledTypes` through their enum declaration order; the TS server preserved the caller's order. Same `itemSeed` picked a different type at the same position on each side, harmless in prod because clients only consume what the server writes, but dangerous the day anyone reintroduces client-side spawning. Both clients now preserve the caller's order, matching the server. Caught by the new parity tests.
- **iOS `try?` swallowed every `activatePowerUp` error.** Client rendered the effect locally, server never recorded it, `gameConfigStream` had to cancel the optimistic UI on the next tick. Now `do/catch` with `logger.error`, state still reconciled via the stream, but we get the trace in Console.
- **Android ViewModel stream jobs leaked past the screen.** `BaseMapViewModel` never overrode `onCleared`, so Firestore listeners + location flows kept running after the chicken/hunter map was popped. Override added, cancels `streamJobs` + `notificationJob`. Regression guard in `BaseMapViewModelTeardownTest` drives a real `ViewModelStore.clear()` and asserts subscriber counts drop.
- **Android `PaymentScreen` force-unwrapped `state.completionError!!` inside an `if (… != null)` guard.** Smart-cast would have saved it, but a recompose-between-check-and-use could crash. Replaced with `state.completionError?.let`.
- **`MapboxConfigurationException` lookup** of the unused `_ = Date.now` dead assignment removed from `ChickenMap.swift:225`.

### Changed

- **Onboarding now requires "Always" / `ACCESS_BACKGROUND_LOCATION` on both platforms.** Accepting `.authorizedWhenInUse` on iOS or fine-only on Android silently broke `chickenCanSeeHunters` and the `stayInTheZone` radar-ping loop, both assume the chicken keeps broadcasting while the phone is pocketed. All gates tightened to Always: Next button, pager swipe, final "Let's Go" check. Pre-Android-10 fallback in `LocationRepository.hasBackgroundLocationPermission` returns `hasFineLocationPermission` because the permission didn't exist as a separate runtime check back then.
- **Onboarding permissions seeded synchronously.** `OnboardingViewModel.init` now calls `refreshPermissions()` before the first `StateFlow` emission, so re-installs where Always is already granted don't flash the "Allow Location Access" button at the user. A `DisposableEffect` observes `Lifecycle.Event.ON_RESUME` to refresh again, catches the Android 11+ path where background permission opens the Settings app instead of an in-app dialog.
- **Onboarding final "Let's Go" gate is defensive.** Re-checks background location, empty-nickname, and profanity even though each per-page gate would have refused earlier. Protects against state drift (back + clear + swipe) that could otherwise ship the user into the game with a bad nickname or a revoked permission.
- **`testOptions.unitTests.isReturnDefaultValues = true`** added to `android/app/build.gradle.kts` so `android.util.Log` calls inside a `catch` block don't fail unit tests with `Method e not mocked`. Unblocks the Android `HunterMapViewModelBehaviorTest` retry-flow tests.

### Added

- **Cross-platform parity tests** (iOS + Android + Functions). Golden vectors for `seededRandom`, `interpolateZoneCenter`, `deterministicDriftCenter`, `generatePowerUps`, and `applyJammerNoise`, same input tables, same expected output, verbatim across `ios/PoulePartyTests/ParityGoldenTests.swift`, `android/.../ParityGoldenTest.kt`, `functions/test/parity.test.ts`. Covers the `enabledTypes`-order bug above, 1-second jammer buckets, negative seeds, pre-Q fallbacks, `currentRadius > initialRadius` clamp, `2000 → 500` drift jumps, empty / zero-count inputs.
- **`snapToRoad` retry tests** (`functions/test/mapbox.test.ts`): happy path, transient 429 / 500 / 503 / network error with retry, non-transient 4xx with no retry, exhausted retries, exponential-backoff cadence, 200 m sanity check, URL construction.
- **Stripe webhook dedup tests** (`functions/test/stripe-webhook-dedup.test.ts`): first-time claim, already-completed skip, in-flight fresh skip, in-flight stale re-claim, 5-minute boundary, custom window, defensive legacy-doc handling.
- **iOS + Android onboarding tests** for the tightened gate: `.authorizedWhenInUse` now blocked on iOS (regression guard against accidental loosening), `hasFineLocation && !hasBackgroundLocation` blocked on Android, last-page defensive gate, `init`-time permission seeding, lifecycle-refresh simulation.
- **`TODO.md §7 Stripe ↔ Firestore hand-reconciliation`**, documents the missed-webhook recovery path (a scheduled Cloud Function that queries Stripe's `payment_intent.succeeded` events and reconciles games stuck in `pending_payment`). Not implemented yet; the section spells out exactly what needs to ship when we hit that scale.

### Test coverage

iOS **584 tests** (was 559 at 1.8.1) · Android full suite pass · Functions **74 tests** (was 44 at 1.8.1).

---

## [1.8.2] — 2026-04-22 (Android hotfix — iOS unaffected)

**iOS**: unchanged (still 1.8.1 (2)) · **Android**: 1.8.2 (25)

Android-only emergency release. iOS is unaffected and stays at 1.8.1 (2).

### Fixed
- **Android — Mapbox crash on every map screen in release builds** (`MapboxConfigurationException: Using MapView … requires providing a valid access token`). R8 resource-shrinking in release builds was stripping `@string/mapbox_access_token` because no Kotlin code referenced it — Mapbox's by-convention lookup is reflection-style and invisible to the shrinker. Surfaced as a 100 % crash on game creation, join, chicken map, and hunter map on any 1.8.1 (24) install. Introduced silently when the Hilt binding that used to inject the token was removed during the April 2026 server-side power-up refactor. Fix: `PoulePartyApp.onCreate()` now sets `MapboxOptions.accessToken = getString(R.string.mapbox_access_token)` — token applied explicitly before any map composable runs, and the `R.string` reference keeps the resource alive through shrinking.

---

## [1.8.1] — 2026-04-22

**iOS**: 1.8.1 (2) · **Android**: 1.8.1 (24) · **Web**: Terms wording adjusted

Post-payment UX pass + observability fix. Ships the confirmation screen we should have had since 1.7.0 and tightens a promise we couldn't keep in the Terms.

### iOS-only build 2 changes (after 1.8.1 (1) was rejected)
- **Location purpose strings rewritten** to include a concrete usage example per Apple guideline 5.1.1(ii). `NSLocationWhenInUseUsageDescription` now illustrates what a Hunter sees during a game; `NSLocationAlwaysAndWhenInUseUsageDescription` illustrates the Chicken-in-pocket scenario and the "stops when the game ends" guarantee.
- Android binary unchanged (build 24) — iOS-only rebuild.

### Added
- **Post-payment confirmation screen** (iOS + Android, EN/FR/NL). Shown right after a successful Forfait payment, Caution deposit, or 100%-off promo redemption. Full-screen with confetti, game code (tap-to-copy + native share sheet), live countdown to start, real-time status badge that reflects the Firestore `pending_payment → waiting` transition as soon as the Stripe webhook fires, and a clear "Back to home" exit. Replaces the previous UX where a successful paid-creator flow dumped the user back on Home with zero feedback.
  - iOS: new `PaymentConfirmationFeature` (TCA reducer) wired as a root case of `AppFeature.State`, emitted from `HomeFeature` when `GameCreation.paidGameCreated` or `JoinFlow.registered` lands.
  - Android: new route `payment_confirmed/{gameId}/{kind}` with `PaymentConfirmationViewModel` streaming `gameConfigFlow`. `HomeEffect.NavigateToPaymentConfirmed` and `GameCreationScreen.onPaidGameCreated` route the two flows.
  - Tests: 20 TCA `TestStore` tests (iOS) + 37 pure-unit tests (Android) covering webhook transitions (pending→waiting, pending→failed, waiting→done mid-screen), clock skew (start time in the past), hour/day boundaries for the countdown formatter, 10 000-day upper bound, invalid kind-arg parsing, and the `backToHomeTapped` exit being reachable regardless of game status.

### Changed
- **iOS `GameCreationFeature.Action.paidGameCreated`** now carries the full `Game` snapshot (`.paidGameCreated(game:)`) instead of only `gameId`. Internal refactor to let the confirmation screen render without a Firestore round-trip.
- **iOS `ApiClient.gameConfigStream` decode failures** now log the full `DecodingError` (coding path + missing key) plus the `gameId` and `snapshot.exists`, replacing the opaque `"data couldn't be read because it is missing"` that hid schema drift at runtime. No behavioural change, pure observability — surfaced after reviewing real device logs that failed to pinpoint the missing field.
- **Web Terms of Use** — deposit-refund clause softened from "returned in full automatically via Stripe" to "refundable on request via email" to match the current server capability (automatic refund cron not yet implemented). Prevents a materially-wrong promise at App/Play review time. Web-only text update.

---

## [1.8.0] — 2026-04-22

**iOS**: 1.8.0 (4) · **Android**: 1.8.0 (22) · **Firestore rules**: `/reports` rule added · **Web**: `/delete-account` page added, Terms rewritten

Privacy, moderation, and store-compliance release — the code paths that App Store / Play Store reviewers specifically look for.

### Added
- **UGC — "Report player" flow** on both platforms. A flag icon appears next to each non-self row in the Victory leaderboard and in Settings → My Games → past-game leaderboard. Confirming writes to a new admin-SDK-only `/reports/{reportId}` Firestore collection (`reporterId`, `reportedUserId`, `reportedNickname`, `gameId`, `createdAt`). New `ApiClient.reportPlayer` (iOS) and `FirestoreRepository.reportPlayer` (Android). Full EN/FR/NL localization.
- **iOS `NSLocationWhenInUseUsageDescription` + `NSLocationAlwaysAndWhenInUseUsageDescription`** in `Info.plist`. Previously missing despite declaring `UIBackgroundModes = location` → instant rejection on iOS 13+.
- **Android foreground service for background location** — `LocationForegroundService` with `android:foregroundServiceType="location"` declared in the manifest plus `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` permissions. Started/stopped by `LocationRepository.locationFlow` on collect/close so background tracking survives app backgrounding during a game, as required by Play policy on Android 14+.
- **Web `/delete-account` page** (EN/FR/NL) at `pouleparty.be/delete-account` — the web-accessible deletion endpoint Google Play requires since 2024. Explains what is deleted vs. retained (Stripe/accounting legally held), how to request deletion via email, and the 30-day SLA.
- **In-app safety disclaimer** on the final onboarding slide (EN/FR/NL) — traffic, private property, no running where you wouldn't normally run. Covers Apple guideline 1.4 and Google's Health & Safety expectations for location-based games.
- **Terms of Use rewrite** (`pouleparty.be/terms`, EN/FR/NL): explicit **Pricing & Payments** section describing Free / Forfait (service fee for real-world event organizing) / Caution (escrow, no commission, not redistributed to winners). Explicit "No gambling, no prize money". New **UGC & Moderation** and **Physical Safety** sections.

### Changed
- **iOS `registerForRemoteNotifications()` gated on authorization status** (`AppDelegate`). No longer calls at launch before the user has had a chance to consent. On first grant it fires from inside `NotificationClient.requestAuthorization`. Aligns with App Store guideline 4.5.4.
- **Onboarding location-permission copy** (iOS + Android EN/FR/NL) — removed the misleading "we only use it during the game" line that contradicted the declared background-location usage. Replaced with "your position is only shared with other players while a game is in progress".
- **Account deletion (iOS `AuthClient` + Android `SettingsViewModel`)** — the Firestore `/users/{uid}` profile doc is now deleted **before** the Firebase Auth user. The security rule requires `auth.uid == userId`, so the opposite order silently left orphan profile docs behind.

### Fixed
- Android: 2 pre-existing `Icons.Filled.DirectionsWalk` deprecation warnings migrated to `Icons.AutoMirrored.Filled.DirectionsWalk` (zero-warnings policy).

### Security
- New `/reports/{reportId}` Firestore rule: `create` for authenticated users only, `reporterId == auth.uid` enforced, `reportedUserId != reporterId`, required keys validated. `read / update / delete` fully locked (admin-SDK only) so reporters can't spy on each other and targets can't erase reports.

### Verified
- iOS `PrivacyInfo.xcprivacy` audited — only required-reason API used directly is `UserDefaults` (already declared with reason `CA92.1`). Third-party SDKs (Firebase, Stripe, Mapbox) ship their own privacy manifests.

### Notes for reviewers / store submission
- **App Review notes** should explicitly state: the app does not use IAP because (a) the Forfait creator fee is a service fee for organizing a real-world event, and (b) the Caution deposit is escrow refunded to every hunter post-game with no commission and no redistribution to winners. Link to the Terms page (`pouleparty.be/terms`) which documents both.
- Play Console **Data Safety form** must list: precise location, user ID (Firebase UID), device ID (FCM token), nickname, Stripe Customer ID, Firebase Analytics/Crashlytics, Mapbox SDK.
- Play Console **Account deletion URL**: `https://pouleparty.be/delete-account`.

---

## [1.7.1] — 2026-04-22

**iOS**: 1.7.1 (1) · **Android**: 1.7.1 (20)

### Added
- **Apple Pay** support in the PaymentSheet for iOS. Merchant ID `merchant.dev.rahier.pouleparty` registered in the Apple Developer account; `com.apple.developer.in-app-payments` entitlement added; `PaymentSheet.ApplePayConfiguration(merchantId:, merchantCountryCode: "BE")` wired in `PaymentFeature`.
- **Google Pay** support in the PaymentSheet for Android. `PaymentSheet.GooglePayConfiguration(environment, countryCode: "BE", currencyCode: "EUR")` wired in `PaymentScreen`. Environment auto-derived from the publishable key (`pk_test_*` → Test, `pk_live_*` → Production).

---

## [1.7.0] — 2026-04-21

**iOS**: 1.7.0 (12) · **Android**: 1.7.0 (19) · **Functions**: 5 new callables + webhook deployed (staging + prod)

### Added
- **Stripe payments — Forfait & Caution** (`functions/src/stripe.ts`, both mobile apps). Drop-in `PaymentSheet` (iOS + Android) supporting **card** and **Bancontact** (EUR/BE) via `automatic_payment_methods`.
  - **Forfait (`flat`)**: creator pays `pricePerPlayer × maxPlayers` at the end of game creation. Server pre-creates the game doc in `pending_payment`; the webhook flips it to `waiting` on `payment_intent.succeeded`. Supports optional **promo codes** (creator-applied at payment time).
  - **Caution (`deposit`)**: hunters pay the creator-defined deposit at registration. Registration doc is created server-side by the webhook — clients can never write `paid: true`.
- **Promo codes via Stripe Promotion Codes** — validated server-side via `validatePromoCode` (rate-limited 10 attempts/h/user via `rateLimits/promo_{uid}`). 100%-off codes skip PaymentSheet entirely and create the game via `redeemFreeCreation`. Codes are managed from the Stripe Dashboard; no logic duplicated.
- **Webhook dedup + signature verification** — `paymentEvents/{eventId}` Firestore doc prevents replay; `stripe.webhooks.constructEvent` validates every call.
- **New GameStatus states** — `pending_payment` and `payment_failed` on both platforms, displayed as distinct badges in Settings > My Games.
- **iOS**: `StripePaymentSheet` SPM (pinned `24.x`), `StripeClient` dependency, `PaymentFeature` TCA reducer, `paymentSheetBridge` SwiftUI wrapper. URL scheme `pouleparty://stripe-redirect` registered for Bancontact redirect + `StripeAPI.handleURLCallback` wired in AppDelegate.
- **Android**: `com.stripe:stripe-android` 21.8, `StripeRepository`, `PaymentViewModel` (Hilt assisted-inject), `PaymentScreen` Compose overlay. `PaymentConfiguration.init` in `PoulePartyApp.onCreate`. `FirebaseFunctions` DI binding added.
- **Docs**: new "Pricing Modes" section in `CLAUDE.md` with modes matrix + Stripe details. Stripe invariants documented in `.claude/rules/functions.md`.

### Changed
- **Firestore rules**:
  - Forfait game creation (`pricing.model == 'flat'`) blocked for clients; only admin SDK / Cloud Functions can create them.
  - Caution hunter registrations (game `pricing.model == 'deposit'`) blocked for clients; only admin SDK can write.
  - `registrations.paid` is immutable from the client (`request.resource.data.paid == resource.data.paid`).
  - `users.stripeCustomerId` is admin-SDK-only (clients can't preload a spoofed ID).
  - New locked-down collections: `paymentEvents`, `rateLimits`.
- **Android `buildConfig = true`** enabled; `STRIPE_PUBLISHABLE_KEY` is now a `buildConfigField` per flavor (`pk_test_*` for `staging`, `pk_live_*` for `production`).
- **iOS `STRIPE_PUBLISHABLE_KEY`** user-defined build setting per configuration, read at runtime via `Info.plist`.
- **`SwiftUI TextField` focus bug workaround** in `OnboardingNicknameSlide` — dismiss focus on page change so the `ifCaseLet` reducer no longer logs a runtime warning when SwiftUI fires a lingering binding after state transitions.

### Fixed
- **Web registration Google Sheet write** (side-effect of Stripe setup work) — prod project's Cloud Function service account `1047338092854-compute@developer.gserviceaccount.com` was shared on the registration Sheet, and the Sheets API was enabled on prod.
- **Web hosting now points to prod Firestore** (`web/src/firebase.ts`) instead of staging.

---

## [1.6.3] — 2026-04-21

**iOS**: 1.6.3 (11) · **Android**: 1.6.3 (18)

### Fixed
- **Radar Ping invisible in `stayInTheZone` mode** — when a hunter activated Radar Ping, the Chicken's position was never broadcast if the Chicken was standing still. Root cause: writes were only triggered from the CoreLocation/FusedLocation update stream, which has a 10 m distance filter. A stationary Chicken emits no update, therefore no write, therefore no ping. Fixed on both platforms with a dedicated timer-driven broadcast loop (5 s tick, matches `locationThrottleSeconds`). Loop is only scheduled in stayInTheZone, no-ops when ping is inactive. Invisibility safety net added (wins over radar ping, matching followTheChicken behavior).

### Added
- **Pure helper `shouldBroadcastDuringRadarPing`** (iOS `GameTimerLogic.swift`) for the broadcast decision, fully unit-tested.
- **Android tests**: `radarPingBroadcastLoop writes chicken location while ping is active in stayInTheZone`, `does not write when ping is inactive`, `is not scheduled in followTheChicken mode`.
- **iOS tests**: 6 unit tests in `LocationTrackingEffectsTests` covering all edge cases (active/expired/nil ping, invisibility overrides, expiry boundary).

### Audit
Full power-up audit passed. All 6 types (zonePreview, radarPing, invisibility, zoneFreeze, decoy, jammer) verified to apply effects correctly on both platforms, with role-based inventory filtering preventing cross-role collection/activation. Decoy seed (`driftSeed xor decoyTimestamp`) is deterministic across iOS/Android so all hunters see the same fake chicken.

### Changed
- **Power-up code consolidated into a dedicated module on both platforms** (structural refactor, zero behavior change):
  - **iOS**: `ios/PouleParty/Features/PowerUps/{Model,Logic,Feature,UI}/` — groups `PowerUp.swift`, `PowerUpSpawnLogic.swift`, `MapPowerUpsFeature.swift`, and all 7 UI components under one folder. Extracted the power-up map content (`powerUpsMapContent`, `powerUpCollectionOverlay`, `powerUpPulseAlpha`) and markers (`PowerUpMapMarker`, `DecoyMapMarker`) from `MapOverlays.swift`/`MapAnnotations.swift` into dedicated files.
  - **Android**: new `dev.rahier.pouleparty.powerups.{model,logic,ui,selection}` package tree. Every power-up file moved out of `model/`, `ui/gamelogic/`, `ui/components/`, `ui/map/`, `ui/powerupselection/` and re-packaged. 27 import sites updated across production and tests.
  - Commits preserve git history via `git mv`. No functional change — full iOS + Android test suites pass as-is.

---

## [1.6.2] — 2026-04-21

**iOS**: 1.6.2 (10) · **Android**: 1.6.2 (17) · **Functions**: `spawnPowerUpBatch` redeployed (staging + prod)

### Fixed
- **Power-ups invisible on iOS after the server-authoritative refactor** — the 1.6.0 Cloud Function wrote power-up docs without an explicit `id` field (Firestore's document name was meant to carry it). iOS `PowerUp` struct had `let id: String` non-optional, so `doc.data(as: PowerUp.self)` silently threw `keyNotFound: id` on every decode and the map stayed empty. Android was unaffected thanks to its `val id: String = ""` default + `.copy(id = doc.id)` injection.

### Changed
- **Cloud Function `writePowerUpBatch` now writes an explicit `id` field** matching the document name. Makes power-up docs self-contained (survives JSON export, no drift risk since they're only written once by the CF). Symmetrical with Android's model.
- **iOS `ApiClient.powerUpsStream` decode path is defensive** — injects `doc.documentID` into the data map before calling `Firestore.Decoder()`. Handles both new docs (with `id`) and any legacy docs that might linger from 1.6.0 / 1.6.1.

### Added
- **Regression tests** for PowerUp decoding:
  - iOS `PowerUpTests`: decode with explicit `id`, decode after client-side injection, and assert decode fails loudly if `id` is missing AND not injected (prevents silent regression).
  - Android `PowerUpTest`: lock in the data class default-value contract (`id: String = ""`) so no-one breaks the `toObject → copy(id = doc.id)` chain.

---

## [1.6.1] — 2026-04-20

**iOS**: 1.6.1 (9) · **Android**: 1.6.1 (16) · **Web**: NL added

### Added
- **Dutch (NL) locale on the web landing page** — `nl.ts` added with full translation of the home, register, privacy, terms, support sections. Locale switcher in the header now cycles EN → FR → NL → EN. Browser default is auto-detected (`navigator.language`).

### Changed
- **Complete FR + NL translation audit pass across iOS, Android, and web**. ~70 strings fixed for consistency, tone (tutoiement everywhere except legal pages), and idiomatic phrasing:
  - **iOS** (`Localizable.xcstrings`): 4 fully-empty keys filled (`Create a party`, `Daily limit reached`, etc.), ~20 EN-only keys gained FR + NL, `Never mind` FR fixed from the misleading "Autant pour moi" to "Laisse tomber", `Game Code` FR unified to "Code de partie", apostrophe typographique harmonisée (`'` partout), "Ouvrir les paramètres" partout (cohérent avec l'OS), tutoiement appliqué sur notifs + onboarding + alerts ("Trouve-la !", "Reste dedans !", "Active-le", etc.), NL "Join Game" corrigé grammaticalement ("Meedoen aan spel"), "FINAAL" (et non "EINDE"), "1 dag van tevoren", "Bevestigen" au lieu de "Verzenden".
  - **Android** (`values-fr/strings.xml` + `values-nl/strings.xml`) : `app_name` ajouté dans les 2 locales, `never_mind` FR fixé, `enter_found_code` FR passe à "Code de capture" et NL à "Voer de vangstcode in", tutoiement appliqué (`hunt_them_down`, `hunt_subtitle`, `mode_stay_detail3`, `setting_map_desc`, `return_to_zone`, `notif_hunter_start_body`, `notif_zone_shrink_body`), `nominate_subtitle` FR retraduit ("Le futur marié..." au lieu de "L'enterrement de vie de garçon..."), NL infinitifs cohérents (`Adres zoeken`, `Klassement bekijken`, `Opnieuw proberen`), `Contact opnemen met support`, `%d pnt` abréviation correcte, `Inschrijving sluit om` comme label.
  - **Web** (`fr.ts`) : tutoiement propre sur tout le block `register.*` (`Ta mission`, `Retrouve la Poule`, `Réalise des défis`, etc.) ; `privacy.*` et `terms.*` laissés en vouvoiement (ton légal standard).

### Fixed
- **Android `app_name` MissingTranslation warning** — ajouté dans `values-fr/` et `values-nl/`.
- **iOS snapshot baselines regenerated** suite aux ajustements de texte FR (GameCreation screens).

---

## [1.6.0] — 2026-04-20

**iOS**: 1.6.0 (8) · **Android**: 1.6.0 (15)

### Added
- **Power-up collection disc** — power-ups on the map now display a pulsing semi-transparent disc matching the power-up's neon color. The disc grows as you get closer and pulses to invite you to collect it — way more discoverable than tiny flat markers.
- **Auto re-onboarding after Firebase user deletion** — if your anonymous Firebase UID is invalidated (e.g. server-side user purge), the next app launch detects the freshly created UID and sends you back through the onboarding flow so you can re-enter a nickname tied to the new identity.

### Changed
- **Server-authoritative power-up spawning (big refactor)** — clients no longer generate, snap, or write power-ups. A new `spawnPowerUpBatch` Cloud Function handles the full pipeline: deterministic generation from the zone's drift seed, road-snapping via the Mapbox Directions API, and a single atomic Firestore batch write. Tasks are scheduled at game creation — initial batch of 5 at `timing.start`, 2 extra at each zone shrink. Defensive skips for missing/ended games, disabled power-ups, or collapsed zones. `zoneFreeze` is taken into account via an `effectiveBatchIndex` heuristic. Retries are idempotent (`{ merge: true }` preserves client-written `collectedBy` / `activatedAt`). Result: no more missed spawns when the chicken's device is backgrounded, offline, or crashes — and a malicious client can no longer skip or tamper with spawns.
- **Atomic power-up activation** — activating a power-up now runs in a single Firestore transaction that updates both the power-up document (`activatedAt`, `expiresAt`) and the game document (`powerUps.activeEffects.<field>`) at once. No more partial state if one write fails.
- **Firestore security rules** — `/games/{gameId}/powerUps/{id}` now denies all client writes (`allow create: if false`). Only the Cloud Function's admin SDK can spawn power-ups.
- **Firebase SDKs upgraded** — `firebase-functions` 5.x → 7.2.5, `firebase-admin` 12.x → 13.8.0. Fixed 3 high/critical transitive CVEs (protobufjs, path-to-regexp, fast-xml-parser).
- **Shared `Modifier.neonGlow` on Android** — replaces scattered custom shadow code with a single helper supporting SUBTLE/MEDIUM/INTENSE intensities (mirrors the iOS helper exactly). Applied everywhere: power-up badges, FAB, inventory button, zone warnings, FOUND button, game start countdown.
- **Shared `PowerUpsMapOverlay` composable on Android** — factored the power-up marker + disc rendering shared between `ChickenMap` and `HunterMap` into a single component.
- **Silenced `permission-denied` listener errors** — Firestore snapshot listeners no longer log transient auth-token-refresh permission errors as errors; downgraded to debug.
- **Reference-only `generatePowerUps` on clients** — the pure spawn algorithms on iOS (`PowerUpSpawnLogic.swift`) and Android (`PowerUpSpawnHelper.kt`) are no longer called at runtime. They remain as reference implementations kept in sync with `functions/src/powerUpSpawn.ts` so cross-platform parity tests continue to validate the algorithm.

### Fixed
- **Power-ups disappearing from the map** — a regression where `ForEvery` mixed `PolygonAnnotation` and `MapViewAnnotation` silently dropped the view-annotation branch. Now split into two `ForEvery` loops so both render correctly.
- **Icon file size** — 1024×1024 iOS icons re-exported smaller (287 KB / 261 KB, previously ~1.6 MB each) while keeping zero alpha channel.
- **3 flaky Android tests** in `HunterMapViewModelBehaviorTest` — replaced `MutableSharedFlow` + `runTest` + `emit` pattern (prone to coroutine timing races) with `MutableStateFlow` + direct `.value` assignment. Verified stable across 3 consecutive reruns.

### Removed
- **Dead client code** — removed `RoadSnapService.swift` (iOS) and `RoadSnapService.kt` (Android). Road snapping now happens exclusively in the Cloud Function. Hilt `mapboxAccessToken` binding also removed — Mapbox SDK reads the token directly from `res/values/strings.xml` by convention.
- **Error/retry auth screen on Android** — replaced the blocking "Connection failed" screen with the new re-onboarding flow for genuine new users. String resource `connection_failed` removed from EN/FR/NL.

---

## [1.5.0] — 2026-04-17

**iOS**: 1.5.0 (7) · **Android**: 1.5.0 (14)

### Added
- **Challenges feature** — hunters can now complete fun side-challenges during a game (photo proofs shared via WhatsApp, validated manually). New floating action button on the hunter map opens a modal with two tabs:
  - **Challenges tab** — live list of challenges from the new global `/challenges` Firestore collection. Tap "Valider", confirm on the alert, and the row turns neon-green. Points are added to your team.
  - **Leaderboard tab** — live team ranking with gold/silver/bronze podium for the top 3, and your team highlighted.
  - FAB is hidden entirely when no challenges are configured (so it doesn't appear in games without them).
- **"Open Settings" button on location-denied alerts** — both onboarding and Home now let you jump straight to iOS/Android system settings when location permission is denied, instead of showing only "OK".
- **Localized FCM push notifications** — Cloud Functions send localized title/body keys; iOS `Localizable.xcstrings` now includes all `notif_*` keys (previously missing); Android `PouleFCMService` resolves keys with fallback.
- **23 missing iOS localization keys** added (EN/FR/NL) covering permissions prompts, settings labels, and leaderboard copy.

### Changed
- **Major codebase refactor (iOS + Android)** — folder/package reorganization for clarity: iOS `Features/` grouped by feature into subfolders; `Components/` split into `GameLogic/`, `Services/`, `PowerUps/`, `Map/`. Android `ui/` split into `ui/gamelogic/`, `ui/map/`, `ui/chickenmapconfig/`, `ui/powerupselection/`.
- **Removed dead `ChickenConfig` feature** on both platforms — replaced long ago by the multi-step `GameCreation` flow, but orphan code had remained. All related screens, view models, TCA reducers, tests, and navigation routes deleted.
- **Removed "Expert mode" toggle** from both the old `ChickenConfig` and the active `GameCreation` — settings are always auto-computed in normal mode now.
- **Challenges modal header** — manual drag indicator + SwiftUI `ToolbarItem` close button on iOS; Material `ModalBottomSheet` drag handle + title row with close button on Android.

### Fixed
- **Launch Screen logo missing on iOS** — storyboard still referenced a removed `logo` asset; now points to the current `chicken` asset.
- **`Winner.timestamp` type mismatch** — iOS used `Date` while Android used `Timestamp`; unified on Firestore `Timestamp` for byte-for-byte cross-platform parity.
- **Unused localization keys cleanup** — removed 11 orphan keys from iOS `Localizable.xcstrings` and 34 from Android `strings.xml` (EN/FR/NL) left over from the refactor.

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
