# Known issues

The living list of what is still open. Close an item by deleting it in the commit that fixes it; git keeps the history.

## Before the next production deploy

- **App Check is enforced by default** on every callable (`ENFORCE_APP_CHECK=true`). Check the App Check metrics for both apps and the web site first; set the parameter to false in `functions/.env.<project>` only as a temporary escape hatch.
- **Rotate the Mapbox public token**: it was committed in Android `strings.xml` and iOS `Info.plist`. Restrict the new token to the app identifiers.
- **Run `infra/apply.sh`** on staging and production (backups, proof lifecycle, secrets check).
- **First CI run**: confirm the macOS runner's Xcode can build the project and add the `MAPBOX_DOWNLOADS_TOKEN` repository secret for Android.

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

- Android Mapbox SDK stays on 11.20.2: newer versions need the Mapbox downloads token, not available on this machine. Composables mixing map and UI content suppress `COMPOSE_APPLIER_CALL_MISMATCH` until then.
- Gradle 9.8.1 prints a deprecation (`Configuration.setVisible`) that comes from AGP 9.4.1 itself.
- Android `targetSdk` is 36, the Play requirement. Moving to 37 needs a device test pass for the new behaviour changes.
- Web and functions lint with oxlint: TypeScript 7 no longer exposes the compiler API that typescript-eslint needs.
