# Changelog

All notable changes to PouleParty are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/). Versions follow [Semantic Versioning](https://semver.org/).

---

## [1.13.1], 2026-05-25 (approved by Apple App Review)

**iOS**: 1.13.1 (8) · **Android**: 1.13.1 (42) · **Functions**: unchanged from (5) deploy · **Firestore rules**: unchanged · **Web**: unchanged

> Build (8) / (42) addresses the (5) rejection on Guidelines 2.1 (Information Needed) and 2.1(a) (App Completeness). 5.1.5 and 3.1.1 were resolved in (5) and Apple did not flag them on the (5) review — only 2.1 / 2.1(a) remain. iOS builds (6) and (7) were local rehearsals that caught regressions before upload: (6) → (7) fixed a SwiftUI long-press gesture bug; (7) → (8) fixed two iOS-only Demo Mode bugs (a `doneGame.id` collision triggering Game Over on the Hunter tab + the page-indicator dots obscuring the chicken map countdown). See the (8) section in `RELEASE_NOTES.md` for the details. (6) and (7) were never uploaded to ASC.

### Fixed

- **Create Party button no longer hangs on devices without a GPS fix (Guideline 2.1(a)).** `HomeFeature.chickenConfigLocationRequested` (`ios/PouleParty/Features/Home/Home.swift`) used to `for await coordinate in locationClient.startTracking() { break }` — on iPad Air Wi-Fi (no GPS hardware) the stream never emitted, the wizard never opened, and the button looked dead. The fix races the location wait against a 3-second `clock.sleep`; if no coordinate arrives in 3s, `initialLocationResolved(nil)` fires anyway and the wizard opens with the Brussels default. Android had no equivalent bug — `LocationRepository.getLastLocation()` already returns `null` instead of suspending when no fix is available.
- **Long-press on START / Create Party no longer dismisses its own alert (iOS only).** SwiftUI `Button` + `.simultaneousGesture(LongPressGesture)` fires BOTH the long-press's `onEnded` at 1.5s AND the Button's tap on touch-up release, so long-pressing START opened the demo-code alert then immediately re-fired `startButtonTapped`, dismissing the alert and opening the JoinFlow sheet underneath. Same regression existed on Create Party long-press (admin-code flow, PP-45) but was never reported because admin code is reachable only via the modal it opens. Fix: `HomeFeature.State.suppressNextTap: Bool` set on either `*LongPressed` action, checked at the top of `*Tapped` cases (eats the trailing tap), auto-cleared after 600 ms via a `clock.sleep` cancel-safe Run effect in case the touch never produces a tap (drag-out). Android uses Compose `combinedClickable` which arbitrates click + long-click natively — no equivalent bug.
- **Demo Mode no longer mis-fires "Game Over" on the Hunter tab (iOS only).** `MockDemoData.doneGame` was built as a mutated copy of `liveGame` and inherited the same `id`. The demo `gameConfigStream` switched on `gameId == doneGame.id`, which matched every demo tab's `liveGame.id` subscription — HunterMap (and GameMasterMap) received `status = .done` and triggered the `"The game has ended!"` alert in `HunterMap.swift:768`. Fix: give `doneGame` a distinct id (`"DEMO0000000000000000000000000DONE"`); only the Victory tab passes that id, the live tabs keep `liveGame.id` and the stream correctly yields the in-progress game.
- **Demo Mode tab navigation is now discoverable + the chicken map countdown is unobstructed (iOS only).** The TabView's `.page(indexDisplayMode: .always)` style rendered four tiny white dots at the bottom-center that (a) didn't read as "swipe between tabs" to a non-iOS-power-user reviewer (Apple Review feedback for (5) was specifically that they couldn't find the app's features) and (b) overlaid the chicken map's "Map update in:" countdown HUD. Replaced with a `Picker(.segmented)` at the top of the demo screen showing `🐔 Chicken / 🏃 Hunter / 👁 GM / 🏆 Victory` labels — explicit + tappable + pulls the dots out of the map area so the countdown is fully visible. Android demo already used `ScrollableTabRow` with visible labels — no change there.

### Added

- **Demo mode for Apple App Review (Guideline 2.1).** Apple Review couldn't see the app's features in (5) without a real game code / real-time multiplayer / GPS hardware ("We were unable to sign in when asked for Game Code"). PouleParty uses anonymous Firebase Auth, so a demo account is not possible — the alternative Apple allows is "a demonstration mode that shows all of the features and functionality available in the app". Built as a code-gated entry point on both platforms: long-press the START button on Home (1.5s) → masked code field → enter `appreview` → opens a tabbed Demo Mode screen showing Chicken Map / Hunter Map / GameMaster Map / Victory with mocked dependencies (canned `ApiClient` + `LocationClient` + `UserClient` on iOS; dedicated demo composables reusing shared building blocks on Android, since the live screens are tightly coupled to Hilt + Firestore). The reviewer can pan / zoom the map, see chicken + hunter markers, see pre-placed power-ups, see the countdown timer, see the GM observer view, and see the Victory leaderboard — all without a real game, real GPS, or real other players. "Quit Demo" exits back to Home. The entry point is invisible to regular users (long-press + code) — same obfuscation pattern as the existing admin code (`jujurahier`). New iOS files: `Models/DemoCode.swift`, `Features/DemoMode/DemoMode.swift`, `Features/DemoMode/MockDemoData.swift`, `Features/DemoMode/DemoDependencies.swift`. New Android files: `model/DemoCode.kt`, `ui/demo/DemoModeScreen.kt`, `ui/demo/DemoMockData.kt`. Strings (`Localizable.xcstrings` + Android `strings.xml` × 3 locales) updated.

> Second re-submission patch for the 1.13.0 → 1.13.1 (3) double rejection on Guidelines 5.1.5 (Location) and 3.1.1 (IAP). The (3) build's surface-level fixes (skippable onboarding, neutral CTA copy, App Review Notes paragraph) were not enough; Apple still flagged both items on the reviewer's iPad Air 11-inch (M3). The (4) build addresses the root causes structurally on both sides.

### Changed

- **Explicit ask to App Review for a GPS-capable test device (Guideline 5.1.5).** We initially tried to add `UIRequiredDeviceCapabilities = ["gps"]` to `Info.plist` in build (4), but App Store Connect rejected the upload with a QA1623 warning: existing 1.13.0 users on Wi-Fi iPads would be orphaned on update, which the store rules forbid. Build (5) drops the key entirely. Instead, the App Review Notes now lead with "PLEASE REVIEW ON A GPS-CAPABLE DEVICE" and explain that the (3) review failed because iPad Air 11" M3 is likely a Wi-Fi model with no GPS. For a future fresh major version where we want to filter Wi-Fi iPads from day one, the capability must be declared on the first ever build of that marketing line.
- **Location requests deferred all the way to the screen that needs a coordinate (Guideline 5.1.5).** The (3) build still called `requestWhenInUse()` on `createPartyTapped`, `createPartyLongPressed`, and `startButtonTapped` (Home), which prompted the system permission dialog before the user even saw a screen that needed GPS. The (4) build strips those auth chains entirely on both iOS (`HomeFeature` — three actions now just send `chickenConfigLocationRequested` / `adminCodeAlertRequested` / `joinFlowAuthorized` directly) and Android (`HomeViewModel` — `PendingPermissionAction` enum + the launcher routing it triggered are gone). The system prompt now fires only when the wizard reaches a zone-placement step or the hunter map mounts.

### Removed

- **In-app event-registration validation gate (Guideline 3.1.1).** Apple kept calling the 6-char validation code "game tickets" despite the Stripe Checkout product rewrite + App Review Notes paragraph shipped in (3). The defensible answer is to make the iOS and Android binaries completely unaware of the Stripe payment. Removed: iOS `JoinFlowFeature` cases `validationCodeEntry` / `submittingValidationCode` / `resolvingDeeplink` / `deeplinkGameNotYetReady` / `deeplinkInvalidCode` (and every backing state field, action, reducer case, and SwiftUI view); Android `JoinFlowState.ValidationCodeEntry` / `ResolvingDeeplink` / `DeeplinkGameNotYetReady` / `DeeplinkInvalidCode` (+ `HomeViewModel` / `HomeIntent` / `JoinFlowBottomSheet` analogues); `ApiClient.validateRegistrationCode` + `lookupGameByValidationCode` and `FirestoreRepository` equivalents; `Game.registrationBatchId` field on both platforms; the `validateRegistrationCode` + `lookupGameByValidationCode` Cloud Functions (deleted from `functions/src/registrations.ts` and the `functions/src/index.ts` exports — both `firebase functions:delete` runs on staging + prod). Every hunter, paid or not, joins via the 6-char gameCode like any other game; the wristband desk on D-Day verifies the 6-char validation code physically against the Google Sheet.
- **Universal Link / App Link deeplink machinery.** Apple checks the entitlement at install time; once the AASA is dropped, the previous `pouleparty.be/join?code=…` URL stops opening the app. Removed: iOS `com.apple.developer.associated-domains` (`applinks:pouleparty.be` + `applinks:pouleparty-ba586.web.app`) from `PouleParty.entitlements`; `.onOpenURL` + `.onContinueUserActivity` + `.onReceive(.pouleDeeplink)` handlers from `PoulePartyApp.swift`; `handleDeeplink` method; `Notification.Name.pouleDeeplink` + the `application(_:continue:)` AppDelegate hook; `NSUserActivityTypes` from `Info.plist`. Android `<intent-filter android:autoVerify="true">` for `pouleparty.be/join` from `AndroidManifest.xml`; `MainActivity.handleDeeplink` + `onNewIntent`; the entire `data/DeeplinkBus.kt` singleton. Web `.well-known/apple-app-site-association` + `.well-known/assetlinks.json` + their `firebase.json` `Content-Type` headers.
- **`AppFeature.deeplinkValidationCodeReceived` + `HomeFeature.deeplinkValidationCodeReceived` (iOS) and the equivalent `HomeIntent.DeeplinkValidationCodeReceived` (Android).** These were the cross-cutting wires from the URL handler down to the JoinFlow, dead once the deeplink machinery and the JoinFlow's resolution states are gone.

### Fixed

- **Confirmation email reframed as wristband-pickup token, not an in-app join button.** `functions/src/email/registrationConfirmation.ts` rewritten in FR / EN / NL: the CTA button + Universal Link, the "tap to join the game" paragraph, and the `fallback` instructions that referenced the in-app validation flow are all gone. The 6-char code is now presented as `YOUR ENTRY CODE` / `TON CODE D'ENTRÉE` / `JE TOEGANGSCODE` with body copy directing the user to show it at the start bar on D-Day to claim their wristband, welcome drink, and the game code announced on site.

Re-submission patch for the 1.13.0 App Store rejection (Guideline 5.1.5 Location + Guideline 3.1.1 In-App Purchase). Loosens the onboarding to be entirely skippable (location requested contextually at gameplay, nickname auto-generated when blank), makes the D-Day inscription page explicit that the 12€ fee buys a real-world physical event (not digital content), and ships three orthogonal Home cleanups requested in passing.

### Changed

- **Onboarding is fully skippable (Guideline 5.1.5).** Every slide of the onboarding can now be tapped past without granting any permission or typing any text. The previous hard gates (Always location at page 3, non-empty nickname at page 5, defensive re-check at the final page) are removed. Apple flagged the original behavior as "the app is not functional when Background Location Services are disabled" two reviews in a row (1.12.x and 1.13.0); now the user reaches Home with literally zero side effects if they choose to. Same gate removal on Android for `ACCESS_FINE_LOCATION` and the nickname field. The only remaining onboarding gate is profanity on a manually-typed nickname (silent otherwise).
- **Location is requested contextually at Create / Start / Long-press (Guideline 5.1.5).** When the user actually needs location to do something (start as Chicken, join a game, open admin mode), the app calls `requestWhenInUse()` first. If granted → proceed. If denied or already-denied → bail to the existing "Location Required" alert with the "Open Settings" CTA. iOS does this via a `.run` effect chain that funnels into new continuation actions (`joinFlowAuthorized`, `adminCodeAlertRequested`); Android extends the existing `PendingPermissionAction` enum and launcher to route long-press through the system permission dialog as well.
- **Random nickname when the user skips the field.** New shared helper (`Helpers/RandomNickname.swift` + `util/RandomNickname.kt`) generates `AdjectiveNoun##` pseudonyms from a ~38-adjective / ~36-noun wordlist (animal-themed, leaning on the chicken motif). The TextField on the nickname slide pre-fills with whatever the user types but falls back to `RandomNickname.generate()` at onboarding completion if left blank. No external dependency.
- **D-Day inscription page is explicit about the physical event (Guideline 3.1.1).** The web inscription page (`pouleparty.be/inscription`, `/registration`, `/inschrijving`) now states upfront that the 12€ fee is a real-world in-person event registration: location (Ixelles, Brussels), date and time (Saturday June 6, 2026, 8:30 PM), what's included physically (welcome drink at the start bar, physical wristband, food and real-world prizes at the final spot), and a clarifying line that the mobile app is only used as a GPS tool during the live event. The Stripe Checkout product description and Stripe product name mirror the same framing ("Inscription événement physique 06/06/2026 Ixelles", "Inscription événement en présentiel"). FR / EN / NL all updated.
- **Location pre-prompt copy softened.** The "Without it, the game can't work. No location = no map = no fun." onboarding string (and its FR / NL translations) is replaced with a neutral "PouleParty uses your GPS during a game so the Chicken and Hunters can find each other on the map. You can grant access now, or wait. We'll ask again when you start your first game." matching the new contextual-prompt flow.

### Removed

- **"Admin mode" visible button on Home.** The visible "Admin mode" button under the Create Party CTA was discoverable to App Store reviewers and surfaced an internal-only entry point. It is gone. The admin code modal is now triggered exclusively by a 1.5-second long-press on the Create Party button itself — same flow, same `AdminCode.value = "jujurahier"` constant, just hidden from casual inspection.
- **Debug map preview entry point + screen.** Long-pressing Create Party used to spawn a preset `stayInTheZone` game with every future shrunk circle pre-rendered as a debug visualization. The whole path is removed: `DebugMapSetupView` / `DebugMapSetupScreen` files, `NavigateToChickenMapDebug` / `NavigateToDebugMapConfig` effects, `chickenDebugGameStarted` action, `isDebugPreview` / `zonePreviewCircles` flags on `ChickenMapFeature` and `HunterMapFeature`, and the Android `chicken_map/{gameId}?debug={debug}` nav route. `computeDebugShiftedCircles` stays — the GameCreation wizard recap (PP-13) still uses it.
- **"Want to create a party?" Home CTA.** The placeholder link that opened the localized `pouleparty.be/creer-une-partie` page is gone, along with the `CreatePartyURL.swift` / `CreatePartyUrl.kt` model + tests, the `webCreatePartyTapped` action / `WebCreatePartyTapped` intent, and the `OpenWebUrl` effect. The Create Party button itself is unchanged.
- **One-free-game-per-day cap.** The old `countFreeGamesToday` Firestore query (`/games where creatorId == X and timing.start >= startOfDay`) plus the "Daily limit reached" alert it surfaced are gone. Anyone can create as many free games as they want. The `ApiClient.countFreeGamesToday` dependency, `FirestoreRepository.countFreeGamesToday`, the `dailyFreeLimitChecked` TCA action, and the `daily_limit_reached` strings are all removed.

---

## [1.13.0], 2026-05-19

**iOS**: 1.13.0 (1) · **Android**: 1.13.0 (37) · **Functions**: 7 new callables / triggers, full redeploy (staging + prod) · **Firestore rules**: redeploy (staging + prod) · **Web**: full redeploy (staging + prod) with App Check, paid registration form, Privacy + Terms rewrite

D-Day prep release ahead of 06/06/2026. Ships the **PP-23 / PP-24 / PP-26 / PP-66 / PP-86 / PP-87 / PP-88 GameMaster epic**, the **PP-11 → PP-15 zone-setup wizard refactor**, the **PP-52 paid event registration pipeline** (web form → Stripe → Resend email → Google Sheet → mobile JoinFlow via Universal Link / App Link), and a two-week store-compliance + security audit. The audit closes 15 CRITICAL + HIGH findings from `audit.md` and 32 BLOCKING + HIGH findings from `store-audit.md` covering Apple Guideline 5.1.1, Google Play Data Safety, EU CRD Art. 8(2) / 16(l), GDPR Art. 13 / 17 / 20, and Firebase App Check rollout (Play Integrity + App Attest + reCAPTCHA Enterprise).

### Added

- **GameMaster role (PP-23, PP-24, PP-26, PP-66, PP-86, PP-87, PP-88).** Up to N validators per game, distinct from the chicken and hunters. The creator sets a 4-digit `gameMasterPassword` at game creation; anyone with the code calls `joinAsGameMaster` and lands in `gameMasterIds`. Validators get a dedicated observer map (`GameMasterMapFeature` on iOS, `GameMasterMapScreen` on Android) that streams the chicken's position, every hunter, and every spawned power-up in read-only mode. They never write their own GPS and cannot collect / activate power-ups. The password lives in `/games/{gameId}/private/` (admin-SDK-only); only `gameMasterIds` is on the public Game doc. PP-86 adds a designate-the-chicken action on the GameMaster drawer (only while `status == waiting`).
- **Zone setup wizard refactor (PP-11 / PP-12 / PP-13 / PP-14 / PP-15).** The game-creation wizard splits zone setup into `startZoneSetup` (place the start pin) and `finalZoneSetup` (place the final pin, `stayInTheZone` only). In `followTheChicken` a 3-button size picker (Small 500m / Medium 1000m / Large 2000m) replaces the radius slider. A Shuffle button on the recap step re-derives the drift seed. The recap (PP-13) is now backed by the server-authoritative `computeZoneConfiguration` Cloud Function (PP-69).
- **`computeZoneConfiguration` Cloud Function (PP-69).** Single source of truth for the wizard zone math: takes the two pins + game mode + duration + `forceNewSeed` flag, returns `initialRadius`, `driftSeed`, `shrinkIntervalMinutes` / `shrinkMetersPerUpdate`, and the precomputed shrink schedule (`circles[]`). iOS calls it via `ApiClient.computeZoneConfiguration`, Android via `FirestoreRepository.computeZoneConfiguration`. Parity tests in `functions/test/zoneCalculation.test.ts`, `ZoneCalculationTests.swift`, and `ZoneCalculationTest.kt` pin the same goldens.
- **Paid event registration pipeline (PP-52).** Web form at `pouleparty.be/inscription` (`/registration` EN, `/inschrijving` NL) collects player+team info, opens a Stripe Checkout Session (12 € × teamSize EUR), and on `checkout.session.completed` sends a localized Resend email + appends a row to the D-Day Google Sheet. The email's CTA points at `https://pouleparty.be/join?code=ABCDEF` — Universal Link (iOS `.onOpenURL` primary, `.onContinueUserActivity` defense-in-depth) and App Link (Android `<intent-filter android:autoVerify="true">`) open the app and trigger a 3-step JoinFlow resolution: `gameReady` (skip validation), `gameNotYetCreated` (friendly "party not open" screen), `invalidCode` (error + mailto fallback). Refunds via `charge.refunded` webhook flip `paid: false`.
- **Out-of-zone penalty (PP-36, PP-37).** When the chicken is outside the active zone, they accumulate -1 point per 5 seconds. Shared evaluator across iOS / Android. Parity tests cover both platforms.
- **End-game leaderboard CTA (PP-18) + overtime red label (PP-17) + end-game stays on map (PP-19, PP-16).** Players see results inline rather than being yanked to a separate screen at gameOver; the leaderboard is a tap away.
- **Power-ups simplification (PP-35).** Wizard ships `zoneFreeze` + `zonePreview` enabled by default; the other 4 stay in code and the spawner. Pure helper `availablePowerUpTypes(for:)` strip-filters incompatible types in `stayInTheZone` (invisibility / decoy / jammer get no card, not greyed out). Mirrors `filterEnabledTypesServer` in `functions/src/powerUpSpawn.ts`.

### Changed

- **In-app registration dropped (PP-90).** Anyone with the gameCode can join at any time. The `/games/{id}/registrations/{userId}` subcollection is still written (teamName, joinedAt) so the GameMaster can pick a chicken from the team-name list, but the registration-required gate is gone. Toggle `requiresRegistration` removed from the wizard and from both clients.
- **Invisibility power-up refactor (PP-87).** The chicken now keeps writing positions during Invisibility with an `invisible: Bool` flag set on `chickenLocations/latest`. Hunters filter the marker out client-side; the GameMaster observer map ignores the flag and shows everything. Replaces the pre-PP-87 "stop writing entirely" approach which was racy with multi-consumer location updates.
- **Hunter positions broadcast when at least one GameMaster has joined (PP-24 Phase B).** Hunters write to `hunterLocations/{hunterId}` whenever `game.gameMasterIds.isNotEmpty()`, regardless of `chickenCanSeeHunters` or `gameMode`. Lets validators see everyone in `stayInTheZone` mode.

### Security & store compliance (`audit.md` + `store-audit.md`)

- **`foundCode` moved off the public Game doc (CRIT-2).** Now lives in `/games/{id}/private/security` (admin-SDK-only subcollection). New callables `submitFoundCode` (constant-time compare via `crypto.timingSafeEqual`, transactional `winners` append) and `getFoundCode` (chicken-only) replace direct field reads. Firestore rules add `allow read, write: if false` on the `/private/**` path. Winner self-declaration is no longer possible client-side.
- **`/eventRegistrations` PII locked behind callables (CRIT-1).** Reads are denied at the rules level (`allow read, create, update, delete: if false`). New callables `validateRegistrationCode` and `lookupGameByValidationCode` expose only `{valid: bool}` or `{type, gameId, batchId}` — never PII. The mobile JoinFlow consumes these from `ApiClient.lookupGameByValidationCode` / `FirestoreRepository.lookupGameByValidationCode`.
- **App Check rollout (CRIT-4).** `createPendingRegistration` verifies the `X-Firebase-AppCheck` header via `admin.appCheck().verifyToken()` (manual verification since `onRequest` doesn't support `enforceAppCheck`). Clients ship App Check init: iOS `AppAttestProvider` (release) + `AppCheckDebugProvider` (debug), Android `PlayIntegrityAppCheckProviderFactory` (release) + `DebugAppCheckProviderFactory` (debug), web reCAPTCHA Enterprise (staging + prod site keys). Monitoring-only today; enforce flips when Owner acceptance lands on `pouleparty-prod` Cloud project.
- **Stripe webhook hardening (HIGH-4, HIGH-5).** `createPendingRegistration` passes `idempotencyKey: checkout-${registrationId}` to defuse double-charge on retries. `confirmRegistrationPayment` cross-checks `payment_status`, `currency`, `mode`, and `amount_total === teamSize × UNIT_PRICE_CENTS` inside the Firestore transaction before flipping `paid: true`. Side effects (Resend + Sheets) run after the flip with independent try/catch + log-and-continue so a re-delivery hitting the idempotency guard doesn't lose the email.
- **Form security (CRIT-4).** Honeypot field (renamed from `company` to `nicknameAlt` after Chrome autofill defeated the original), batchId allowlist (`game-06-06-2026` is the only accepted value), email regex tightened to block `\r\n` header injection on the Resend HTTP API, phone regex bounded, `crypto.randomInt` instead of `Math.random()` for the 6-char code, transactional code-uniqueness reservation, max-length caps on every free-text field.
- **Cloud Tasks cleanup (HIGH-3).** `onGameDeleted` trigger calls Cloud Tasks REST API `queues.delete` for the queue that holds the game's scheduled lifecycle tasks. Prevents orphan tasks firing on deleted docs and prevents the previous race where deleting a game during `transitionGameStatus` could leave the doc half-mutated.
- **iOS `LiveLocationManager` rewrite (HIGH-iOS-2).** Multi-consumer-safe with `continuations: [UUID: AsyncStream<…>.Continuation]`, NSLock-protected, ref-counted `startUpdatingLocation`. Replaces the previous single-consumer pattern that crashed when two map screens were alive simultaneously (chicken + GameMaster).
- **Apple Privacy Manifest aligned with App Store Connect.** `PrivacyInfo.xcprivacy` declares 6 data types matching the ASC App Privacy nutrition label: Other User Content (nickname, was misclassified as Name), Precise Location, User ID, Device ID, Product Interaction (Analytics), Crash Data (Not Linked). Widget extension gets its own minimal `PrivacyInfo.xcprivacy` (no collection). Stale `Resources/PrivacyInfo.xcprivacy` deleted.
- **iOS `Info.plist` deduplicated.** `INFOPLIST_KEY_NSLocation*` duplicates removed from `project.pbxproj`; macOS-only sandbox keys (`ENABLE_APP_SANDBOX`, `ENABLE_HARDENED_RUNTIME`, `ENABLE_USER_SELECTED_FILES`) removed. Widget extension `IPHONEOS_DEPLOYMENT_TARGET` bumped 26.2 → 17.0 so Live Activities work on iOS 17 / 18 / 19. `APPATTEST_ENVIRONMENT` build setting injected per config (`development` Debug / `production` Release) with matching entitlement.
- **Settings "Delete My Data" → "Delete Account" (Apple Guideline 5.1.1(v)).** Wording aligned with Apple's rejected-build feedback. The action now calls `deleteUserAccount` (new Cloud Function) which scrubs `/users/{uid}`, `/games/*/registrations/{uid}`, `/games/*/challengeCompletions/{uid}`, and `/games/*/hunterLocations/{uid}` before deleting the Firebase Auth user. iOS and Android both surface the renamed label with localized FR / NL.
- **Android infrastructure (audit + store-audit).** `data_extraction_rules.xml` locks cloud backup + D2D for Android 12+. `<intent-filter android:autoVerify="true">` adds `www.pouleparty.be` so deep links resolve from both hostnames. Network security config gets a `<debug-overrides>` block for emulator (10.0.2.2 + localhost). Location disclosure strings (en/fr/nl) name Firebase Firestore + the real-time sharing flow + the stop condition. App Check `firebase-appcheck-playintegrity` + `firebase-appcheck-debug` added.
- **Web Privacy + Terms rewritten.** Stripe / Resend / Google Sheets disclosures added; CRD Art. 16(l) opt-out clause for the paid event (no 14-day withdrawal on date-specific events); GDPR Art. 13 disclosures aligned. Inscription page surfaces the consent obligation above the Pay button (no separate checkbox, Belgian clickwrap doctrine).
- **DMARC `p=quarantine`.** `_dmarc.pouleparty.be` upgraded with `rua=mailto:dmarc@pouleparty.be; aspf=r; adkim=s`. OVH forwarder routes the aggregate XML reports to `julien@rahier.dev`. mail-tester 10/10.
- **Google Sheet split staging / prod.** `GOOGLE_SHEET_ID` secret rotated on staging to a dedicated test spreadsheet so Stripe-test inscriptions stop polluting the live D-Day roster.
- **AASA + assetlinks.json verified.** Both files served as `Content-Type: application/json` on prod. assetlinks.json carries the 2 SHA-256 fingerprints (App Signing + Upload key).
- **Localization 100% on iOS.** `Localizable.xcstrings` now reports 100% FR + 100% NL (was 82% / 79%). 84 translations added with consistent tutoiement FR + je-vorm NL. 10 French-keyed entries (`"Annuler"`, `"Erreur"`, `"Désigner la poule"`, etc.) standardized to English keys in Swift + xcstrings. 19 orphan keys removed. 7 emoji/format-only keys marked `shouldTranslate: false`.
- **`ITSAppUsesNonExemptEncryption = false`** pre-declared in `Info.plist` so App Store Connect skips the App Encryption Documentation prompt at every upload.

### Fixed

- **iOS duplicate-winner crash.** `Dictionary uniquingKeysWith` on the Victory + Challenges leaderboards defuses the rare race where two clients submit the same `foundCode` within the same Firestore tick and both appear in `winners[]` momentarily before the dedup transaction lands.
- **Android out-of-zone penalty parity.** Routed through the shared evaluator instead of the previous Android-specific path that diverged from iOS by ±1pt over long games.
- **Android deeplink mid-game drop.** Tapping a `https://pouleparty.be/join?code=…` link from inside an active game no longer yanks the player out of the chickenMap / hunterMap / gameMasterMap / victory screen.

### Internal

- **Parity tests** for: zone setup (PP-64), simplified power-ups + out-of-zone penalty (PP-37), GameMaster role (PP-66), end-game stays on map (PP-19), `computeZoneConfiguration` (PP-69).
- **Mapbox token rotated** to a new key with restricted scope; old token will be revoked once the 1.13.0 build is widely deployed.
- **Stripe live-mode webhook on prod** (`sk_live_…` secret + `whsec_…` live webhook), staging remains test-mode.

---

## [1.12.0], 2026-05-12

**iOS**: 1.12.0 (1) · **Android**: 1.12.0 (36) · **Functions**: full purge (Stripe deletes + index redeploy, staging + prod) · **Firestore rules**: redeploy (staging + prod)

Closes the **PP-9 epic**. Stripe paid plans and every artefact around them are gone from the codebase, both clients, the backend, the security rules, the Apple entitlements, and Google Secret Manager. PouleParty is now free for every party, with an opt-in admin mode for organizers who need larger games. Plus a smoother game creation flow on both platforms.

### Added

- **Admin mode for parties up to 500 players (PP-45).** A new "Mode admin" entry point on Home opens a secure input (SecureField on iOS, `PasswordVisualTransformation` on Android). Entering the hardcoded `jujurahier` code unlocks a wizard variant that lifts the `maxPlayers` cap from 5 to 500. The flag (`isAdminCreation`) is written to the Game doc and enforced server-side by the new `firestore.rules` create clause: `maxPlayers <= 5 || (isAdminCreation == true && maxPlayers <= 500)`. No real auth; obfuscation only.
- **Tap-to-edit player count in the game creation wizard (PP-45).** The MaxPlayers step was previously a hold-down stepper, hostile for the 500-player range. iOS uses a `ZStack` with an overlapping `BangerText` (non-hit-testable while the field has focus) and a `TextField` always mounted underneath. Android uses a `BasicTextField` controlled by a `KeyboardType.Number` field. Both clamp to the active range and fall back to the previous value on invalid input.
- **Localized "Want to create a party?" landing pages on the web (PP-46).** Three new routes (`/create-a-party`, `/creer-une-partie`, `/een-feestje-organiseren`), one per locale. The locale is pinned by the URL slug via a `useEffect` that overrides `setLocale()` based on `useLocation().pathname`, ignoring the visitor's stored preference. The mobile apps build the URL from the device language (`Locale.current.language.languageCode` on iOS, `Locale.getDefault().language` on Android) and open it in the system browser via the new Home "Envie de créer une partie ?" button.

### Removed

- **All Stripe paid game modes (PP-9 epic: PP-42 through PP-47).** Both clients no longer ship the Stripe SDK, `PaymentSheet`, `PaymentConfiguration`, the promo redemption path, or the post-payment confirmation screen. The Cloud Functions `createCreatorPaymentSheet`, `createHunterPaymentSheet`, `validatePromoCode`, `redeemFreeCreation`, `stripeWebhook`, and `cleanupAbandonedPendingGames` are deleted from staging and prod (`functions:delete` then `deploy --only functions`). `functions/src/stripe.ts` and 5 Stripe test files are gone; the `stripe` npm dependency is uninstalled. Firestore rules drop the `paymentEvents` and `rateLimits` collections, the `stripeCustomerId` field on `/users`, the `pending_payment` and `payment_failed` status branches, the `paid` field on `Registration`, and the `pricing` block on Game. Secret Manager `STRIPE_SECRET_KEY` (latest) and `STRIPE_WEBHOOK_SECRET` (latest + v1) destroyed on both `pouleparty-ba586` and `pouleparty-prod`. The `com.apple.developer.in-app-payments` entitlement, the `merchant.dev.rahier.pouleparty` merchant ID, the `NSCameraUsageDescription` purpose string (only ever used for Stripe card scan), the `StripePublishableKey` Info.plist entry, and the `pouleparty://` `CFBundleURLTypes` (Bancontact redirect) are all gone. The Privacy policy + Terms of use lose the `thirdPartyStripe*` keys, the entire `Pricing & Payments` section, and "Stripe customer reference" / "Payment records" from the data-deleted / data-kept lists, in all three locales. `lastUpdated` bumped to 2026-05-12.
- **Web event registration form (PP-46).** The `/register` page for the April 23 in-person event is gone now that the event has passed (345 lines, `web/src/pages/Register.tsx`). The new "create a party" pages replace the entry point.

### Changed

- **`onGameCreated` / `onGameUpdated` Cloud Functions simplified.** With no more `pending_payment` status to chase, `onGameCreated` always schedules lifecycle tasks unconditionally, and `onGameUpdated` drops the entire `pending_payment → waiting` Stripe webhook branch. The `shouldScheduleOnCreate`, `shouldScheduleOnUpdate`, and `selectStaleGamesForPurge` helpers that wrapped the old conditional logic are deleted. Unused `firebase-functions/v2` imports (`onCall`, `HttpsError`, `onSchedule`, `logger`, `defineString`) removed.

---

## [1.11.5], 2026-05-09

**iOS**: 1.11.5 (1) · **Android**: 1.11.5 (35) · **Functions**: `sendGameNotification` redeploy (staging + prod) for the post-`endDate` notification gate · **Firestore rules**: unchanged

Three bug fixes from the In Progress queue. No new features.

### Fixed

- **Android challenges list scrolls cleanly inside the bottom sheet
  again (PP-38).** The Material3 `ModalBottomSheet` auto-wires its
  `NestedScrollConnection` to the nearest scrollable descendant, so a
  `Box` wrapper between the sheet and the `LazyColumn` was breaking
  the path: the sheet ate the scroll gestures and a swipe near the
  bottom sometimes dismissed instead of scrolling. Removed the wrapper
  and dropped the `fillMaxHeight(0.95f)` trick (the unused 5 % at the
  bottom of the sheet was a phantom drag area for swipe-to-dismiss).
  Modifier passes through to the `LazyColumn` directly now. Same fix
  applied to the Leaderboard tab.
- **Background music actually pauses when the app loses focus (iOS +
  Android, PP-39).** iOS used `AVAudioSession.ambient` (correct: not a
  background-audio app) but had no `scenePhase` observer, so the
  player kept ticking down a session that was already silenced.
  Android used `MediaPlayer` without a lifecycle observer for the
  same reason. Both platforms now pause on backgrounding and resume
  on foregrounding (respecting the existing mute toggle). Android
  uses `ON_STOP` / `ON_START`, not `ON_PAUSE` / `ON_RESUME` — the
  latter pair fires every time the notification shade is pulled and
  would cut the music for nothing.
- **Last-tick `zone_shrink` notifications no longer fire after the
  game is over (PP-40).** `transitionGameStatus` is itself a Cloud
  Task scheduled at `timing.end`. When a `zone_shrink` task happened
  to fire at the same instant, the notif handler saw `status` still
  `"inProgress"` (the transition hadn't run yet) and went out
  milliseconds after the user already saw "Game Over". Added an
  `endTimestamp <= now` gate alongside the existing `status === "done"`
  check. PP-84 (Sprint 2) will rework Cloud Task scheduling with
  deterministic IDs + cancel-on-cleanup; this is the immediate
  defensive guard.

---

## [1.11.4], 2026-04-26

**iOS**: 1.11.4 (1) · **Android**: 1.11.4 (34) · **Functions**: `spawnPowerUpBatch` redeploy (staging + prod) — drift algo touches `interpolateZoneCenterServer` / `deterministicDriftCenterServer`, so power-up snap-to-roads must agree with the new circle math · **Firestore rules**: unchanged

Two tracks this release: a geometry rewrite of the `stayInTheZone`
drift center, and an Android-only onboarding hotfix that left users
stranded on the location slide.

### Fixed

- **Android onboarding background-location loop (Android 11+).** On
  API 30 and higher, `RequestPermission(ACCESS_BACKGROUND_LOCATION)`
  is silently denied by the OS without showing any UI — the launcher
  callback fires immediately with `false`, the screen state never
  flips, and the in-slide "Continue" button does nothing. The Next
  button stays grey because it's gated on `hasBackgroundLocation`,
  so the user is permanently stuck. Now on R+ the button opens the
  app's Settings page directly (where "Allow all the time" actually
  exists); the `ON_RESUME` lifecycle observer already handled the
  return trip, so no other plumbing changed. API 29 (Android 10)
  still uses the runtime dialog because there it works.
- **Android onboarding bottom buttons clipped by gesture bar.** The
  screen uses `enableEdgeToEdge()` but the bottom controls Column
  had no `navigationBarsPadding()`, so the dots + Next button were
  partially hidden behind the system gesture / 3-button nav bar on
  phones that show one. Added the inset and trimmed the static
  bottom padding.
- **Drift now keeps the final-zone disk inside every shrunk circle,
  not just its center (iOS + Android + server).** The previous
  `deterministicDriftCenter` implementation bounded the random
  offset by `newRadius − dist(base, finalCenter) − safety`, which
  guaranteed `finalCenter ∈ disk(driftedCenter, newRadius)` but said
  nothing about the 50 m glow disk *around* `finalCenter`. Late-game
  shrinks could drift far enough that the glow disk poked out of
  the active zone, breaking the "the chicken's safe spot is always
  inside the playable area" invariant the UI promises. Replaced
  with rejection sampling against
  `disk(initial, R₀ − rᵢ) ∩ disk(final, rᵢ − 50 − safety)` — the
  whole final zone now fits inside every drifted circle by
  construction. 32 splitmix64-seeded attempts, deterministic
  fallback on the base→final line if rejection exhausts. Also
  decoupled successive shrinks: each candidate is sampled relative
  to the **initial** zone, not the previous drifted center, so
  overlapping circles are now allowed (only the two product
  invariants matter — inside start zone, contains final zone).

### Added

- **Debug map preview (iOS + Android, internal).** Long-press the
  Create Party button on the home screen to skip the wizard and
  drop straight onto the chicken map with a preset
  `stayInTheZone` game (start in 1 min, 1 h long, current location
  or Brussels fallback). Renders every future drifted circle at
  once with a stable rainbow palette so drift / shrink visuals can
  be eyeballed across the whole game in a single screen. Same
  palette order on both platforms for side-by-side parity checks.
  Not advertised to users; this is a dev tool that piggy-backs on
  the existing `ChickenMapConfigFeature` (iOS) /
  `DebugMapSetupScreen` (Android) so the QA loop is one tap, not
  five wizard steps. New strings: "Debug Preview" / "Launch".

### Changed

- **`GameTimerHelper` / `GameTimerLogic` parity refresh.** The drift
  rewrite forced a re-walk of every shrink-step computation; the
  pure-logic helpers were normalized so iOS and Android share the
  exact same call signatures and intermediate constants
  (`finalZoneRadiusMeters` = 50, `maxDriftAttempts` = 32). Parity
  golden tests updated accordingly — the new fixtures cover the
  rejection branch, the fallback branch, and the
  `newRadius ≥ oldRadius` no-drift branch.

---

## [1.11.3], 2026-04-23

**iOS**: 1.11.3 (1) · **Android**: 1.11.3 (33) · **Functions**: `createCreatorPaymentSheet` redeploy (staging + prod, 2026-04-23, landed in 1.11.2 cycle) · **Firestore rules**: unchanged

Same payload as the never-submitted 1.11.2 — clean version number for the
App Store resubmission cycle after the 1.11.1 (1) rejection. No
functional delta vs the 1.11.2 content already described below; this
entry exists so the store binary and the CHANGELOG line up.

Two tracks this release:

1. **Live-test follow-up.** A hunter sitting still on a bench showed up
   frozen on the chicken's map for minutes at a time. Root cause was
   the same on both platforms — the location write was driven off
   each GPS emission, and the 10 m distance filter (CoreLocation on
   iOS, `setMinUpdateDistanceMeters` on Android FusedLocation) means
   a non-moving player produces no emissions and therefore no writes.
   The chicken's `hunterLocationsStream` / `hunterLocationFlow` are
   already `addSnapshotListener`-based, so any fresh write propagates
   instantly — the fix is exclusively on the hunter-side write
   cadence.
2. **App Store Connect 1.11.1 (1) rejection fallout** (Submission
   `1e2b360a-c71b-4dda-bb32-ce6b0fc9cf4d`, reviewed 2026-04-23 on
   iPhone 17 Pro Max). Three findings: (i) 3.1.1 IAP — Apple read the
   Stripe paid game modes as "paid digital content"; the real answer
   is 3.1.3(e) (physical real-world service consumed outside the app)
   plus 3.1.3(d) (organizer ↔ player meetup). Stripe is the
   *correct* flow here, no code change — addressed exclusively via
   the App Review Notes rewrite in `RELEASE_NOTES.md` so a
   re-review team can see the reasoning without opening the app.
   (ii) 5.1.1(iv) — the pre-permission onboarding slide had a
   custom "Allow Location Access" / "Allow Always" button that
   precedes the system location prompt, which Apple reads as
   pre-conditioning the user's answer. Relabelled to neutral
   "Continue" on both platforms. (iii) 2.1 — Apple asked for a
   physical-device demo video showing the Apple Pay button + purchase
   flow. Delivered out-of-band as a screen recording, linked from the
   updated App Review Notes.

### Fixed

- **Hunter writes decoupled from GPS emissions (iOS + Android).** The
  single `for await coordinate in startTracking()` /
  `locationFlow().collect` loop that both updated state AND wrote to
  Firestore is split in two:
  1. **Tracker** — keeps its old job of pushing each new GPS fix into
     `state.userLocation` / `_uiState.userLocation` (needed for zone
     checks + power-up proximity) and, on iOS, into a shared
     `LockIsolated<CLLocationCoordinate2D?>`. Never calls
     `setHunterLocation` directly anymore.
  2. **Writer** — a dedicated periodic coroutine (`clock.timer` on
     iOS, `while (coroutineContext.isActive) { delay(…) }` on Android)
     that re-broadcasts the latest cached location every
     `locationThrottleSeconds` / `LOCATION_THROTTLE_MS` (= 5 s). Only
     started when `chickenCanSeeHunters` is true. A stationary hunter
     now refreshes on the chicken's map on schedule.
- **Foreground-resume refresh (iOS + Android).** Both platforms can
  suspend the writer coroutine when the app backgrounds — iOS
  particularly aggressive when CoreLocation has nothing to report
  (stationary user + 10 m filter). 1.11.2 adds a one-shot write on the
  next resume so the chicken doesn't see a stale marker until the
  first scheduled tick lands. iOS: `.onChange(of: scenePhase) ==
  .active` in `HunterMapView` → new `.view(.appBecameActive)` reducer
  action. Android: `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)`
  in `HunterMapScreen` → new `HunterMapIntent.AppResumed` handler.
  Both paths are guarded on `chickenCanSeeHunters` + `hasGameStarted`
  + non-empty player id; any gate failure is a silent no-op.
- **Onboarding location-permission button relabelled "Continue"
  (iOS + Android).** Apple rejected 1.11.1 (1) under 5.1.1(iv) because
  the custom in-app button preceding the system location prompt was
  labelled "Allow Location Access" / "Allow Always" — reviewers
  interpret that as pre-conditioning the user's answer to the system
  dialog. iOS `OnboardingSlides.swift` + Android
  `res/values*/strings.xml` (`allow_always`, `allow_location_access`
  keys, kept as-is so the diff is value-only) now render "Continue" /
  "Continuer" / "Doorgaan". Keys intentionally *not* renamed — the
  button text is what App Review reads, the key only exists for
  string lookups and changing it would churn every call site for no
  review benefit. In-line comments flag the 1.11.1 rejection so this
  doesn't get reverted in a later cleanup pass.
- **Server — `createCreatorPaymentSheet` surfaced "INTERNAL" on two
  separate paths when a promo code was applied.** Both reproduced with
  `APPLE_REVIEW_99` on a default min Forfait (6 × 3 € = 18 €). Deployed
  to both projects on 2026-04-23 ahead of the next App Store
  resubmission so the reviewer can actually complete the flow shown
  in the demo video.
  - **Stripe EUR minimum.** After 99 %-off the total lands at 18 cents.
    Stripe `paymentIntents.create` throws `StripeInvalidRequestError:
    Amount must be at least 50 cents`, and the Firebase runtime wraps
    the unhandled throw into `HttpsError('internal', 'INTERNAL')` so
    the user sees a bare "INTERNAL" alert when tapping Payer. New
    exported `clampToStripeMinimumCentsEur` helper floors any positive
    amount below 50 cents to exactly 50 cents before the PaymentIntent
    is created. 100 %-off still routes through `redeemFreeCreation`
    (zero passes through the clamp unchanged). Covered by 5 new tests
    in `functions/test/stripe-amount.test.ts`.
  - **Firestore undefined-value rejection in `payment.promoDiscount`.**
    Exposed by the clamp fix above — once the Stripe call no longer
    failed first, the next write did: `applyPromoCode` returns
    `{ finalAmount, percentOff?, amountOff? }` (one of the two is
    always `undefined` depending on the coupon shape), and the
    handler was building `promoInfo = { percentOff: applied.percentOff,
    amountOff: applied.amountOff }` — for a percent-off coupon that
    produces `{ percentOff: 99, amountOff: undefined }`, and
    `gameRef.set(…)` throws `Cannot use "undefined" as a Firestore
    value (found in field "payment.promoDiscount.amountOff")`. Fixed
    by conditionally writing only the defined side of the XOR. The
    `redeemFreeCreation` path already used `?? null` when writing the
    same object so it was never affected.

### Notes

- **No change to the chicken side.** `ChickenMap.swift` +
  `ChickenMapViewModel.kt` already consume `hunterLocationsStream` /
  `hunterLocationFlow` via `addSnapshotListener` /
  `callbackFlow { addSnapshotListener(…) }`. New hunter writes push
  into the chicken's UI immediately — so the fix needed was only on
  the writer side.
- **Distance filter left at 10 m on both platforms.** Dropping it to 0
  would keep the app resident longer in background (iOS
  `UIBackgroundModes = location` keeps the process alive only while
  CoreLocation has fresh data to deliver), but the battery cost over
  an hour-long game isn't justified for what 1.11.2 is trying to fix.
  If a future bug-report says "hunter invisible for minutes in
  background with screen off", that's the next dial to turn — not
  now.
- **`LOCATION_THROTTLE_MS` / `locationThrottleSeconds` kept at 5 s.**
  Trade-off is Firestore write cost vs. how fresh the chicken's map
  feels. 5 s is unchanged; we're just honouring it now even for
  stationary players.

### Breaking / migration notes

None. New actions (`.view(.appBecameActive)`, `HunterMapIntent.AppResumed`)
are additive. The tracker/writer split is invisible to every call site
outside `.onTask` / `loadGame`.

### Deployment steps

No server deploy. Functions, Firestore rules, and web are untouched.

```bash
# iOS
cd ios && xcodebuild archive -scheme PouleParty -configuration Release \
  -destination 'generic/platform=iOS'
open "$(ls -dt ~/Library/Developer/Xcode/Archives/*/PouleParty*.xcarchive | head -1)"

# Android
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew bundleProductionRelease
```

---

## [1.11.1], 2026-04-23

**iOS**: 1.11.1 (1) · **Android**: 1.11.1 (31) · **Functions**: unchanged · **Firestore rules**: unchanged

Three bugs surfaced during the 1.11.0 live-test window, all closed here:
(a) a `stayInTheZone` hunter couldn't collect a visibly-close Zone Preview
power-up (silent failure on iOS, no banner, no log trail); (b) the same
hunter was shown the "return to the zone" red overlay while standing
inside the visible circle; (c) the hunter's marker on the chicken's
screen stayed stuck at the initial position for far too long. (a) was
masked by an iOS/Android parity gap in the collect UX; (b) and (c) are
iOS-reported bugs with matching Android gaps the audit confirmed.

### Fixed

- **iOS — silent collect failures.** `HunterMap.swift` + `ChickenMap.swift`
  caught the `collectPowerUp` error into `logger.error(…)` and stopped
  there: no banner, no retry signal, no indication to the player that
  anything was even attempted. Android has shown a "Collected: X!" /
  "Failed to collect power-up" toast since the power-up rollout
  (`BaseMapViewModel.checkPowerUpProximity`). Both iOS screens now
  dispatch `.powerUps(.collectSucceeded(powerUp))` / `.collectFailed(…)`
  in the `.run` closure, which drives the existing notification banner
  + a 2 s auto-clear. Failure banners surface the exact same UX Android
  users got — so if the bug reproduces on a patched iOS build, the
  player sees it immediately and we can triage from live feedback
  instead of Xcode-only logs.
- **iOS — 1 Hz collect transaction spam.** The timer tick fires every
  second. A stationary hunter inside the disc was dispatching a fresh
  `apiClient.collectPowerUp` transaction on every tick — 30+ parallel
  transactions per 30 s stay. The first one should win and the rest
  fail against `resource.data.collectedBy == null`, but in practice it
  means retry backoffs stack, analytics fires multiple times, and if
  the first one hit a transient error the log is drowned in later
  `already collected` noise. Added `collectingIds: Set<String>` to
  `MapPowerUpsFeature.State` and an atomic check-and-insert in both
  parent `.powerUpCollected` handlers — one transaction per power-up
  per player, lifetime of the effect. Matches the Android
  `BaseMapViewModel.collectingPowerUpIds` guard introduced alongside
  the original server-authoritative refactor.
- **iOS — "inside the zone" flagged as "outside" in `stayInTheZone`**
  (Bug 2). `HunterMap.swift:newLocationFetched` handler overwrote
  `state.mapCircle.center` with the chicken's broadcasted position on
  every `chickenLocationStream` emit, regardless of game mode. In
  `stayInTheZone` the zone center is the deterministic drifted center
  computed in `.gameUpdated` / `processRadiusUpdate` — a stray chicken
  broadcast (a radar-ping write, or a stale `chickenLocations/latest`
  doc the Firestore listener replayed on connect) pulled the circle
  centre onto the chicken's position, so the zone check fired against
  the chicken's position rather than the real zone and flagged the
  hunter as "outside" even standing inside the visible circle. The
  `ChickenMap` version of the same handler already had the gate; added
  to `HunterMap` to match. Android `HunterMapViewModel.streamChickenLocation`
  had the same parity gap (collect overwrote `circleCenter` unconditionally) —
  both platforms now short-circuit the update in `stayInTheZone`.
- **Android — chicken's own location overwrote the drifted zone centre
  in `stayInTheZone`**. Parity bug flushed out by the audit while
  fixing Bug 2. iOS `ChickenMap.swift:newLocationFetched` has gated
  this since 1.9, but Android's `ChickenMapViewModel.trackChickenOwnLocation`
  unconditionally set `circleCenter = own-GPS` each tick. In the
  `followTheChicken` flow this is correct — in `stayInTheZone` it meant
  the chicken's visible zone silently followed them around until the
  next scheduled shrink recomputed the drift. Gated by
  `gameModEnum != STAY_IN_THE_ZONE` to match iOS.
- **iOS + Android — hunter's position stuck on chicken's map for the
  first 5 s after the game starts** (Bug 3). The initial `setHunterLocation`
  / `setChickenLocation` write was gated behind
  `locationClient.lastLocation()` / `locationRepository.getLastLocation()`
  returning a cached GPS fix. If it returned `nil` (no fix yet at
  game-start time — common when the app has just been opened on the
  field), the throttle cursor was already armed at `Date.now` / `Date()`,
  so the very first coordinate from `startTracking()` / `locationFlow()`
  was blocked by the 5 s throttle. Combined with CoreLocation's / Fused
  Location's 10 m distance filter, a small 2-device test on a quiet
  street could go 10–20 s before either device broadcast a single
  position — looking exactly like "the other player's marker is stuck".
  Both platforms now keep `lastWrite` at epoch (`.distantPast` / `Date(0L)`)
  until a successful write happens, so the first coord the OS emits
  from `startTracking` / `locationFlow` writes immediately and the
  markers appear the moment GPS locks on.

### Added

- **iOS diagnostic logging on collect attempts.** `HunterMap` + `ChickenMap`
  log `info`-level entries around each collect: user ↔ power-up distance
  (m), power-up type, collector id, and the transaction outcome
  (`Collected …` / `Failed to collect …` with the full error). Filter
  by category `HunterMap` / `ChickenMap` in Console.app to see exactly
  what the client attempted and why it resolved the way it did. No PII
  beyond the anonymous Firebase UID which was already in our logs.
- **`MapPowerUpsFeatureTests.swift`** — 5 new tests covering the
  `collectStarted` / `collectSucceeded` / `collectFailed` actions,
  their interaction with `collectingIds`, and the "only-the-matching-id"
  removal contract for the dedup set.
- **`HunterMapFeatureTests.swift`** — 2 regression tests for Bug 2:
  `newLocationFetchedUpdatesMapCircleInFollowTheChicken` pins the
  correct behaviour; `newLocationFetchedDoesNotMoveMapCircleInStayInTheZone`
  asserts a stray chicken broadcast no longer hijacks the zone centre.
- **`HunterMapViewModelBehaviorTest.kt`** — 3 regression tests:
  `chicken broadcast in stayInTheZone does NOT move circleCenter`,
  `chicken broadcast in followTheChicken DOES move circleCenter`, and
  `first locationFlow emit writes immediately when getLastLocation is
  null` (Bug 3 parity check).

### Android

Behavioural changes landed: `HunterMapViewModel.streamChickenLocation`
short-circuits in `stayInTheZone`, `ChickenMapViewModel.trackChickenOwnLocation`
gates `circleCenter` by mode, and both `trackHunterSelfLocation`
/ chicken tracking init their throttle cursor at epoch so the first
emit writes immediately. Version stays locked with iOS at 1.11.1 (31)
per the cross-platform marketing-version rule in `CLAUDE.md`.

### Deployment steps

No server deploy. Functions, Firestore rules, and web are all untouched.

```bash
# iOS
cd ios && xcodebuild archive -scheme PouleParty -configuration Release \
  -destination 'generic/platform=iOS'
open "$(ls -dt ~/Library/Developer/Xcode/Archives/*/PouleParty*.xcarchive | head -1)"

# Android
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew bundleProductionRelease
```

### Breaking / migration notes

None. `MapPowerUpsFeature.State.collectingIds` defaults to `[]`, so
existing callers and tests that construct `State()` keep compiling.
The new `collectStarted` / `collectSucceeded` / `collectFailed` actions
are additive — no existing action case moved or changed semantics.

---

## [1.11.0], 2026-04-23

**iOS**: 1.11.0 (2) · **Android**: 1.11.0 (30) · **Functions**: full redeploy (`index` + `stripe`) · **Firestore rules**: redeploy

### Hotfixes before upload

- **Server `sanitiseGamePayload` accepted empty name**. The 0..80 loosening
  was deployed as a hotfix to staging + prod on 2026-04-23 13:45 after a
  user report (POULETEST 100%-off promo rejected with "name must be 1..80
  chars" because the client never populates `game.name`). A follow-up
  refactor tracked in `TODO.md` → "auto-remplir `game.name` avec
  `"Game {code}"`" will make this explicit instead of a silent empty
  string reaching the server.
- **Android R8 strip of `PowerUp` no-arg constructor**. ProGuard rule only
  covered `dev.rahier.pouleparty.model.**`, but `PowerUp` lives under
  `dev.rahier.pouleparty.powerups.model.**` — so every release build before
  1.11.0 (30) saw `toObject(PowerUp::class.java)` fail on the obfuscated
  class with `java.lang.RuntimeException: Could not deserialize object.
  Class bp3 does not define a no-argument constructor` (Crashlytics issue
  b050a0b8). 1.10.0's `safeToObject<T>()` wrapper caught the throw so the
  process no longer crashed, but power-ups silently stopped rendering in
  release. ProGuard rule extended to cover both model packages + a comment
  explaining the past incident so nobody re-narrows the rule. Android build
  bumped to 1.11.0 (30) to ship this.

Second defensive pass focused on the Join / Rejoin flow + a phase-aware Home banner (upcoming vs in-progress) + server-side deferred Cloud Tasks scheduling so cancelled Forfait games stop enqueuing 10-100 no-op tasks.

### Added

- **Phase-aware Home banner (iOS + Android)**. The banner now distinguishes `.inProgress` from `.upcoming` via a new `GamePhase` enum. In-progress games show "Game in progress" + "Rejoin"; upcoming games (`status == waiting`, `startDate > now`) show "Next game" + "Open" (chicken) / "Join" (hunter) + a relative "Starting in …" countdown. `findActiveGame` now returns `(Game, Role, Phase)` and prioritises inProgress over upcoming (earliest startDate among upcoming).
- **Dismissed-active-game persistence as a Set (iOS + Android)**. Both platforms persist dismissed game ids cross-session (iOS `@Shared(.dismissedActiveGameIds)`, Android `PREF_DISMISSED_ACTIVE_GAME_IDS` `StringSet`). Rejoin-tap clears only the current id so other dismissed games stay hidden; a phase flip resurfaces the banner.
- **Cloud Tasks deferred scheduling**. `onGameCreated` only schedules lifecycle tasks if `status == waiting` (free games); Forfait games created in `pending_payment` defer to `onGameUpdated` on the `pending_payment → waiting` transition from the Stripe webhook. Zero wasted Cloud Tasks for a cancelled PaymentSheet.
- **Self-join block**. Firestore rule `!request.resource.data.hunterIds.hasAny([resource.data.creatorId])` blocks a user from being in both `creatorId` and `hunterIds`. iOS + Android clients short-circuit in `findGameByCode` / `validateCode`.
- **Hunter Caution webhook verification (iOS + Android)**. After PaymentSheet completes, the client polls `findRegistration(gameId, uid)` every 3 s for 30 s. On timeout: optimistic `pendingRegistration` cleared + alert "Payment verification failed — contact organizer, deposit safe".
- **Orphan game doc cleanup on PaymentSheet cancel/fail (iOS + Android)**. Creator Forfait `.canceled` / `.failed` now `deleteConfig(gameId)` immediately to avoid ghost "Paiement" entries in My Games. Firestore rules `allow delete` extended to `pending_payment` / `payment_failed`.
- **Extended `cleanupAbandonedPendingGames`**. Scheduled purge now covers both `pending_payment` and `payment_failed` games older than 24 h.
- **`sanitiseGamePayload` hardened**. Adds `name` 1..80 chars trim, `gameMode` whitelist, `chickenCanSeeHunters` boolean, `pricing.commission` 0..100, `registration` full shape check, `powerUps` full shape check, `timing.startMillis` must be within `[now − 5 min, now + 365 days]`. Exports `shouldScheduleOnCreate` / `shouldScheduleOnUpdate` / `selectStaleGamesForPurge` for unit testing.
- **FCM `sendEachForMulticast` batched to 500 tokens**. Prevents silent failures for games with > 500 recipients.
- **Webhook dedup stale window bumped to 30 min** (was 5). Covers cold-deploy windows without double-processing.
- **7-day TTL on `PendingRegistration` (iOS + Android)**. Zombie banners from games that ended weeks ago are auto-cleared on Home re-appear.

### Fixed

- **iOS + Android — `pending_payment` / `payment_failed` games could be joined silently**. Both platforms now surface a visible alert instead of the silent no-op on `.joinGame` / `joinValidatedGame` / `rejoinActiveGame`. Android `rejoinActiveGame` refetches the game config before navigating to catch mid-banner transitions.
- **Android `findActiveGame` concurrency race on `onResume`**. Guarded by `activeGameCheckInFlight: Job?` so a second call during an in-flight one is a no-op.
- **Android `validateCode` race on fast re-type**. Previous job is cancelled on each re-type; `CancellationException` doesn't flip the UI into `NetworkError`.
- **Android `onJoinSheetDismissed` didn't cancel the in-flight validation**. Now cancels `validateCodeJob` on dismiss.
- **Android `teamName` trim race**. `trimStart()` at input time + `trim()` at submit.
- **iOS `Home.destination(.joinFlow(.registered))`** runs a 30 s verification poll parallel to the navigation; clears optimistic `pendingRegistration` + surfaces alert on timeout.
- **iOS `RejoinGameBanner` hardcoded "Game in progress"** for both phases; now derives title + CTA from `phase` + `role`. Countdown renders only for `.upcoming`.
- **iOS + Android — `withRetry` shift cap** at 20 so a future `maxRetries` > 63 can't overflow `UInt64` / `Long`.

### Changed

- **iOS `ApiClient.findActiveGame`** signature: `(String) async throws -> (Game, GameRole, GamePhase)?`.
- **Android `FirestoreRepository.findActiveGame`** signature: `suspend fun findActiveGame(userId): ActiveGameResult?`.
- **iOS `HomeFeature.State.activeGamePhase: GamePhase?`** + mirrored action.
- **Android `HomeUiState.activeGamePhase: GamePhase?`** + new `isShowingPaymentVerificationFailed` flag + `HomeIntent.PaymentVerificationDismissed`.
- **Stripe webhook `claimWebhookEvent` `staleAfterMs`** 5 min → 30 min.

### Test coverage

Functions: **212 tests** (was 186). Adds `lifecycle-scheduling.test.ts` (26 pure-function tests).

iOS: **TEST SUCCEEDED** on the full suite. Adds `JoinFlowFeatureTests.swift` (6), `HomeJoinFlowIntegrationTests.swift` (8), `HomeActivePhaseTests.swift` (5), `PaymentFeatureTests.swift` (7) — 26 new tests.

Android: **BUILD SUCCESSFUL** on the full suite. Adds `HomeViewModelJoinFlowTest.kt` (8), `HomeActivePhaseTest.kt` (5), `PaymentViewModelCancelTest.kt` (6) — 19 new tests.

### Deployment steps

```bash
cd functions && npm run build
firebase deploy --only firestore:rules,functions --project pouleparty-ba586
firebase deploy --only firestore:rules,functions --project pouleparty-prod
```

*Already applied* to staging + prod on 2026-04-23 during release prep.

### Breaking / migration notes

- No breaking changes. The new Firestore rule `!hasAny([creatorId])` only applies to new `hunterIds` writes; pre-existing docs are untouched.
- Android legacy `PREF_DISMISSED_ACTIVE_GAME_ID` (single string) kept in `AppConstants.kt` for binary compat on install upgrades; superseded by the new `PREF_DISMISSED_ACTIVE_GAME_IDS` `StringSet`.

---

## [1.10.0], 2026-04-22

**iOS**: 1.10.0 (1) · **Android**: 1.10.0 (28) · **Functions**: full redeploy (stripe + index)

Defensive-code pass. No new features — a full codebase audit (iOS TCA, Android MVVM, cross-platform parity, Cloud Functions / Stripe / Firestore rules, concurrency & lifecycle) surfaced ~20 crash/bug/race risks; this release closes all of them. Heartbeat retries survive a flaky network, the chicken actually keeps broadcasting when backgrounded, double-tapping "I found the chicken" can't enrol you twice, abandoned `pending_payment` games get purged nightly, and schema drift on any Firestore read can't crash the Android app anymore.

### Fixed

- **Android — crashes on any schema drift across 9 Firestore reads.** `FirestoreRepository.kt` had nine `.toObject(X::class.java)` call sites that bypass the `safeToObject<T>()` guard introduced at 1.9.1. Any type mismatch (new required field shipped before the client update, a HashMap where a native type was expected, etc.) threw synchronously from Firestore's executor thread and killed the process. All nine call sites (`getConfig`, `findActiveGame` hunter + chicken, `findGameByCode`, `findRegistration`, `fetchAllRegistrations`, `fetchMyGames` created + joined, `fetchPartyPlansConfig`, `markChallengeCompleted`'s transaction snapshot, `challengeCompletionsStream`) now go through `safeToObject<T>()` — decode failures log with the operation name + doc id and yield `null` so the caller sees an empty result instead of a crash.
- **Android — single-doc Firestore reads could hang the UI indefinitely on bad connectivity**. `getConfig`, `findGameByCode`, `findRegistration`, `fetchPartyPlansConfig` called `.get().await()` with no timeout. On a dying network the spinner stayed up forever. All four now wrap in `withTimeoutOrNull(15 s)` + log + return `null` so the UI can surface an error.
- **iOS — heartbeat failures were swallowed, making a healthy chicken look offline.** `ApiClient.updateHeartbeat` was a fire-and-forget `.updateData` with no retry, and a single transient write failure meant the next heartbeat wouldn't land for 30 s — long enough for every hunter's client to flip `game.isChickenDisconnected` to true. Now wrapped in `withRetry("updateHeartbeat(…)")` (3 attempts, exponential backoff) matching `collectPowerUp` / `activatePowerUp` / `addWinner`. Call site in `ChickenMap.swift` updated to `try await`.
- **iOS — chicken appeared frozen on every hunter's map the moment the app was backgrounded.** `Info.plist` already declared `UIBackgroundModes = location`, but `CLLocationManager.allowsBackgroundLocationUpdates` was never flipped on, so CoreLocation stopped delivering coordinates as soon as the screen locked. Now set in `LocationClient.refreshBackgroundCapability()` as soon as `.authorizedAlways` is granted (and re-checked on every `startTracking()` and authorization change). Also set `pausesLocationUpdatesAutomatically = false` so a stationary chicken hiding still broadcasts position during a radar-ping / jammer window.
- **iOS + Android — chicken's position stayed stale for up to 5 s after invisibility expired.** The location flow throttles writes to one per 5 s. When invisibility ended, the next arriving GPS coordinate still had to wait out the throttle window before hitting Firestore, so every hunter saw a ghost at the chicken's last pre-invisibility position. Both platforms now detect the `isInvisible: true → false` transition in their location collect loops and reset the throttle cursor (`lastWrite = .distantPast` on iOS, `lastWrite = Date(0L)` on Android) so the very next coord is broadcast immediately.
- **iOS + Android — double-tapping "Enter found code" could register the same hunter twice in `winners[]`.** `FieldValue.arrayUnion` dedupes object equality, but each submit builds a fresh `Winner` with `Timestamp.now()` — two submits = two entries = `winners.size > hunterIds.size` = game ends early. Both platforms now raise an `isSubmittingWinner` flag while the `addWinner` write is in flight, guard the submit button + retry path against re-entry, and clear the flag on success / failure.
- **Server — belt-and-braces winner dedup in `onGameUpdated`.** Even if a client shipped without the debounce above, a hunter's entry is now deduplicated server-side on the next game update (dedupe by `hunterId`, drop entries with a missing id, write the cleaned array back). The legitimate "new winner" count is now computed on unique hunter ids rather than array-length deltas, so the dedup write can't be misread as "winners count went down" and short-circuit the notification path. Incidentally fixed the "A hunter" fallback: the handler used to read `newWinner.name`, a field the clients never write; now reads `newWinner.hunterName`, matching the iOS + Android contract.
- **Server — hunter could pay a deposit after the registration deadline.** `createHunterPaymentSheet` read `game.status`, `game.pricing.model`, and `game.pricing.deposit` but not `game.registration.closesMinutesBefore`. The Firestore rule enforced the deadline only at client-side registration creation time; the admin-SDK callable path bypassed it. Now re-validates `now > start − closesMinutesBefore × 60 s` and throws `failed-precondition` before creating the PaymentIntent.
- **Server — Forfait games left in `pending_payment` were never cleaned up.** When the Stripe webhook never delivered (dropped event, rotated secret, infrastructure outage), the pre-created game doc stayed stranded, invisible to the creator, and accumulated silently. New `cleanupAbandonedPendingGames` scheduled function (every 24 h, `Europe/Brussels`) deletes any game whose `status == "pending_payment"` and whose `lastHeartbeat` (set once at creation) is more than 24 h old. Bounded at 500 deletions per run to stay under Firestore's batch limit; the backlog is absorbed across daily runs.
- **iOS — decoder failure logs collapsed `DecodingError` into "data couldn't be read"** on `ChickenLocation`, `HunterLocation`, `PowerUp` streams. `Game` had already been switched to `String(describing: error)` at 1.9.1; the other three now match. Future schema drifts surface with the exact coding path + missing key.
- **iOS parity — jammer bucketing on 32-bit Swift Int semantics.** `applyJammerNoise` used `Int(now.timeIntervalSince1970)` — on iOS `Int` is 64-bit in practice, but the Android sibling uses an explicit `Long` and the type-width coupling was load-bearing. Switched to explicit `Int64` for the bucket + XOR, removes any platform-dependent edge case.
- **Android — 4 `payment_confirmed_*` keys in `values/strings.xml` had French values instead of English.** English-locale users saw "Partie créée !" / "Tu es inscrit !" on the payment success screen. All four (`payment_confirmed_title_creator`, `…_title_hunter`, `…_subtitle_creator`, `…_subtitle_hunter`) now carry English source values; the existing French + Dutch translations are unchanged.
- **iOS — untranslated `Couldn't register your win…` alert copy.** Added FR + NL translations and flipped the `%@ %@:%@` format-string entry out of `state: "new"`. `Localizable.xcstrings` is back at 100 % coverage with zero pending review.
- **iOS — location purpose strings were overridden by placeholder values shipped in `project.pbxproj`** (root cause of Apple's 1.9.0 rejection under guideline 5.1.1(ii), Submission ID 4e4dbb9c…). Xcode's `GENERATE_INFOPLIST_FILE = YES` was merging the descriptive "For example, …" strings from the physical `Info.plist` with `INFOPLIST_KEY_NSLocation*` entries in the project file that still carried legacy placeholders ("This app need your location to provide best feature based on location", "Need location"). The project-file values take precedence during the build, so all previous releases shipped the placeholder copy to the store despite the physical plist being correct since 1.8.1. 1.10.0 rewrites all four `INFOPLIST_KEY_NSLocation*` (Debug + Release × WhenInUse + Always + legacy iOS 10 keys) to match the intent-driven copy with a concrete in-app example, as guideline 5.1.1(ii) requires.

### Added

- **`functions/test/stripe-webhook-handler.test.ts`** — 8 integration tests for `handlePaymentIntentSucceeded`: Forfait `pending_payment → waiting` flip (with `paymentIntentId` + `paidAt`), idempotent no-op when already flipped, missing game doc, hunter deposit registration write with `merge: true`, and four guard paths (missing kind / gameId / firebaseUid / unknown kind). `handlePaymentIntentSucceeded` refactored to accept an injectable `dbInstance` so the test can drive it without booting firebase-admin.
- **`functions/test/stripe-amount.test.ts`** — 8 tests pinning `computeCreatorAmountCents` against `pricePerPlayer × maxPlayers` for Forfait (with edge cases: single player, zero players, 50×10 € high end), zero for Caution regardless of deposit size, and zero for Free mode. Exported `computeCreatorAmountCents` so the formula is contract-testable.
- **`functions/test/parity.test.ts` — 4 new test blocks** for large-seed safety (`driftSeed` near `Int32.MAX`, at `Number.MAX_SAFE_INTEGER`, `generatePowerUpsServer` with giant seed + batchIndex), determinism across repeated calls, and zone-freeze seed invariance (same nominal `batchIndex` + same `driftSeed` → same power-up ids regardless of radius, proving the effective-vs-nominal batchIndex split preserves deterministic PRNG anchoring).

### Changed

- **Android `FirestoreRepository.kt`** — nine `.toObject(X::class.java)` → `safeToObject<X>(doc, operation)`; added `withTimeoutOrNull(15 s)` around four single-doc `.get().await()` calls; imported `withTimeoutOrNull`.
- **iOS `ApiClient.updateHeartbeat`** — signature changed from `(String) throws -> Void` to `(String) async throws -> Void`. The only caller (`ChickenMap.swift:404`) now uses `try await`.

### Test coverage

Functions **186 tests** (was 140 at 1.9.1) · iOS: 607 tests in 22 suites, 0 warnings · Android: 0 warnings, existing suite passes.

### Deployment steps

```bash
cd functions && npm run build
firebase deploy --only functions --project pouleparty-ba586
firebase deploy --only functions --project pouleparty-prod
# firestore.rules unchanged vs 1.9.1 — skip rules deploy
```

---

## [1.9.1], 2026-04-22

**iOS**: 1.9.1 (1) · **Android**: 1.9.1 (27) · **Functions**: `createCreatorPaymentSheet` + `redeemFreeCreation` redeployed to staging + prod

Server + Android client hotfix for the 1.9.0 Android crash on any Forfait / promo-created game (`zone.center` HashMap instead of `GeoPoint`). iOS ships the aligned marketing version with a smaller set of internal hardening (PaymentConfirmation gameId fix, Firestore-style IDs for free games, decode-failure logging).

### Fixed
- **Android — crash on every streamed Forfait / promo-created game doc** (`Fatal Exception: java.lang.RuntimeException: Failed to convert value of type java.util.HashMap to GeoPoint (found in field 'zone.center')`). Root cause was server-side: `functions/src/stripe.ts#materialiseGameDoc` spread `g.zone` directly, so `zone.center` / `zone.finalCenter` landed in Firestore as plain maps instead of `GeoPoint` native values. Kotlin's `CustomClassMapper.convertGeoPoint` throws on the wire-level mismatch and the exception bubbled up uncaught from the Firestore executor thread inside `FirestoreRepository.gameConfigFlow`'s `addSnapshotListener` callback. Two-part fix:
  - **Server (`functions/src/stripe.ts`)** — `materialiseGameDoc` now writes `new GeoPoint(lat, lng)` instances for `zone.center` + `zone.finalCenter`. `sanitiseGamePayload` rejects malformed zones at the gate (NaN, Infinity, out-of-range lat/lng, non-numeric values, missing fields, non-object zone, radius > 50 000, start ≥ end, etc.) before they can reach Firestore. `zone.finalCenter` is now correctly optional (required for `stayInTheZone`, `null` for `followTheChicken`). `materialiseGameDoc` + `sanitiseGamePayload` both exported so tests can drive them directly.
  - **Android (`FirestoreRepository.gameConfigFlow`)** — `runCatching` wraps `snapshot.toObject(Game::class.java)` with structured logging (gameId + `snapshot.exists()` + full stack trace). Mirrors iOS's `ApiClient.gameConfigStream` try/catch. Future schema drifts log + yield `null` instead of tearing down the app.
- **iOS — `PaymentConfirmation` screen streamed the wrong gameId after a Forfait payment**. The Cloud Function `createCreatorPaymentSheet` creates the game doc at its own Firestore auto-ID and ignores any client-supplied id. The iOS flow was forwarding `state.game` (which still carried the client-side UUID) to the confirmation screen, so `gameConfigStream(state.game.id)` listened on an orphan doc that never existed. Fixed by patching `state.$game.id = gameId` in `GameCreation.swift` before handing the snapshot to the confirmation screen (required making `Game.id` a `var`). Side-effect: the confirmation screen's status badge now correctly flips from `pending_payment → waiting` as soon as the Stripe webhook fires.
- **iOS — cryptic decode failures on `gameConfigStream`**. `ApiClient.gameConfigStream` used to log `"Failed to decode Game config: The data couldn't be read because it is missing."` — the `localizedDescription` of `DecodingError` collapses key-not-found errors into a useless string. Now logs the full `DecodingError` (coding path + missing key) + `gameId` + `snapshot.exists`, which is what surfaced the `zone.center` shape mismatch in real-device logs.

### Changed
- **Firestore-style game IDs everywhere (iOS + Android)**. Free-mode games used to be created with client-side UUIDs (iOS `uuid().uuidString` = uppercase hyphenated, Android `UUID.randomUUID().toString()` = lowercase hyphenated) while Cloud-Function-created games used Firestore auto-IDs (20-char alphanumeric). Three formats in one collection. Unified: both clients now call `Firestore.firestore().collection("games").document().documentID` / `FirebaseFirestore.getInstance().collection("games").document().id`, matching the server format. Existing games stay on their old IDs — only new games converge.
- **iOS `Game.id` is now `var`** (was `let`). Required for the `PaymentConfirmation` fix above. No external consumer relied on the immutability, the change is source-compatible.
- **iOS — translations catch-up.** 42 translation units added across 16 `Localizable.xcstrings` keys that had been shipped in English / French on the payment + UGC report flows without their FR / NL counterparts. All locale tabs now at 100% coverage, zero `state: "new"` entries.

### Added
- **`functions/test/stripe-zone.test.ts`** — 66 unit tests covering `sanitiseGamePayload` (zone/timing/pricing edge cases: NaN, Infinity, type coercion, missing fields, bounds) and `materialiseGameDoc` (GeoPoint instance checks, Timestamp instance checks, null finalCenter round-trip, 9 boundary coordinates including null island / poles / IDL east + west / Easter Island / sub-degree precision).
- **`ios/PoulePartyTests/GameZoneCodableTests.swift`** — 10 iOS tests driving the real `Firestore.Decoder()` with the shape `materialiseGameDoc` writes: happy-path decode, explicit null finalCenter, missing finalCenter key, full `Game` decode, permissive plain-map fallback (documenting the iOS-vs-Android asymmetry that made iOS survive the 1.9.0 bug), encode/decode round-trip, 9 paramétré boundary coordinates matching the server tests for cross-platform parity.

### Cross-platform contract pinned
- Server writes `new GeoPoint(lat, lng)` → wire type `Value.geoPointValue` (not `Value.mapValue`).
- iOS `Firestore.Decoder()` reads it as `FirebaseFirestore.GeoPoint`.
- Android `CustomClassMapper.convertGeoPoint` reads it as `com.google.firebase.firestore.GeoPoint`.
- Web (landing page) does not read game docs.
- Single source of truth, verified by 76 new tests across server + iOS.

### Test coverage
Functions **140 tests** (was 74 at 1.9.0) · iOS: added `GameZoneCodableTests` with 10 tests · Android: build clean + existing suite passes.

### Deployment steps
```bash
cd functions && npm run build
firebase deploy --only functions:createCreatorPaymentSheet,functions:redeemFreeCreation --project pouleparty-ba586
firebase deploy --only functions:createCreatorPaymentSheet,functions:redeemFreeCreation --project pouleparty-prod
cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew bundleProductionRelease
# upload app-production-release.aab to Play Console with the 1.9.1 release notes from RELEASE_NOTES.md
```

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
