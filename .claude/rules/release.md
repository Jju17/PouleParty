# Releasing

1. Bump iOS `MARKETING_VERSION` and Android `versionName` together, always to the same value. iOS `CURRENT_PROJECT_VERSION` restarts at 1 on a new marketing version; Android `versionCode` only grows.
2. Add the version to `CHANGELOG.md` (Added, Changed, Fixed; iOS and Android versions; everything since the last release from `git log`).
3. Check the iOS 1024 px icons have no alpha channel (`sips -g hasAlpha`); flatten them if needed. `ITSAppUsesNonExemptEncryption = false` stays in `Info.plist`.
4. Build: iOS `xcodebuild archive -scheme PouleParty -configuration Release -destination 'generic/platform=iOS'` from `ios/` without `-archivePath`, then open the newest archive in Organizer; Android `./gradlew bundleProductionRelease`.
5. Store copy in `RELEASE_NOTES.md`, one section per release starting with the "do not paste the summary" warning:
   - App Store Connect "What's New": plain text per locale, 4000 characters maximum, never mention Android or Google Play.
   - App Store Connect "Promotional Text": 170 characters per locale, evergreen.
   - Google Play "Release notes": `<en-US>`, `<fr-FR>`, `<nl-NL>` blocks in one field, 500 characters per locale (aim for 450).
   - App Review notes whenever a permission string or prompt changed, with the path to reach it.
6. Ship the backend changes with `scripts/deploy.sh` (staging, checks, production). Skip it when `functions/`, the rules and `web/` did not change since the last deploy.
7. Upload the archive and the bundle, paste the store copy.
