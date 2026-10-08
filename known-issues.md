# Known issues

The living list of what is still open. Close an item by deleting it in the commit that fixes it; git keeps the history.

## Before the next production deploy

- **App Check is enforced by default** on every callable (`ENFORCE_APP_CHECK=true`). Check the App Check metrics for both apps and the web site first; set the parameter to false in `functions/.env.<project>` only as a temporary escape hatch.
- **Rotate the Mapbox public token**: it was committed in Android `strings.xml` and iOS `Info.plist`. Restrict the new token to the app identifiers.
- **Run `infra/apply.sh`** on staging and production (backups, proof lifecycle, secrets check).
- **First CI run**: confirm the macOS runner's Xcode can build the project.

## Open from the October 2026 review

Larger refactors left for later; the app behaves correctly without them.

- iOS maps share their zone, countdown and game-over logic, but `ZoneSchedule` and `GameLifecycle` are not yet child features (IOS-02). Home and Settings still talk to their parents through state, not delegate actions (IOS-12). Sheets and alerts are separate booleans instead of one presentation enum (IOS-13).
- iOS has no `Domain/` folder; the challenge catalogue stays in `Models/` (IOS-16). `Registration` and `hunterName` keep their old names until a data-model release can rename them on both apps (IOS-19).
- Proof videos are capped by size on both apps; there is no 720p re-encode yet (IOS-20).
- Android `GameMasterMapViewModel` does not extend `BaseMapViewModel` (AND-09). Not every screen has a light and dark `@Preview` (AND-19).
- Tests: several older fixtures still read the real clock (TST-05); `ApiClient.testValue` returns no-ops and many Android tests use relaxed mocks, so an unexpected dependency call does not fail a test (TST-07); older TCA stores run with exhaustivity off (TST-14); no accessibility UI test on iOS (TST-16).
- Icons and emojis are not yet compared across platforms (UX-13). iOS spacing and button tokens are not extracted (UX-24).
- iOS strict concurrency is `targeted`; `complete` needs a Sendable pass over the whole app.

## Kept for installed app versions

Remove once a minimum app version forces everyone past the October 2026 builds.

- Firestore rules still allow the old join lookup (a `games` query by `gameCode` with `limit <= 1`), the old client-side power-up collection on the power-up document and the old Android `gameCode` field update.
- The found code and the referee code stay 4 digits because older builds only accept 4 digits. Brute force is contained by progressive locks, a hard cap per player and a game-wide counter for referee codes.
- Older builds register push with FCM tokens, current builds with Firebase installation ids. The server sends through the `tokens` field, which accepts both during Firebase's migration; switch to the `fid` field once old builds are gone.

## Accepted trade-offs

- The server out-of-zone penalty only sees hunters whose position is shared (chicken can see hunters, or a referee joined). Other hunters still rely on the client report, which the server validates.
- `zone/schedule` exposes future circles, including the final zone, to participants. Releasing circles one by one breaks with zone freeze and manual launch timing.
- The `roles` map stays on the game document (excluded from indexes). Moving membership to a subcollection would need a data migration for little gain at current sizes.
- Referee code brute force through new anonymous accounts is limited by the game-wide counter, not by account age.
- Web account deletion requests are scrubbed by hand within 30 days; in-app deletion is immediate.

## Tooling

- Android Mapbox SDK stays on 11.20.2, which downloads without a token. Newer versions need a secret token (`sk.`, `DOWNLOADS:READ` scope) as `MAPBOX_DOWNLOADS_TOKEN` in `~/.gradle/gradle.properties` and as a GitHub secret; a public `pk.` token gets a 403. Composables mixing map and UI content suppress `COMPOSE_APPLIER_CALL_MISMATCH` until then.
- Gradle 9.8.1 prints a deprecation (`Configuration.setVisible`) that comes from AGP 9.4.1 itself.
- Android `targetSdk` is 36, the Play requirement. Moving to 37 needs a device test pass for the new behaviour changes.
- Web and functions lint with oxlint: TypeScript 7 no longer exposes the compiler API that typescript-eslint needs.
