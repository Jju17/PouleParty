# Releasing

1. Bump iOS `MARKETING_VERSION` (`ios/Config/Versions.xcconfig`) and Android `versionName` together, always to the same value. The iOS build number is set by CI (1000 + run number); Android `versionCode` only grows.
2. Add the version to `CHANGELOG.md` (Added, Changed, Fixed; iOS and Android versions; everything since the last release from `git log`).
3. Check the iOS 1024 px icons have no alpha channel (`sips -g hasAlpha`); flatten them if needed. `ITSAppUsesNonExemptEncryption = false` stays in `Info.plist`.
4. Build:
   - iOS: once the version bump is on `main`, push the tag `ios-v<version>` (for example `git tag ios-v1.15.0 && git push origin ios-v1.15.0`). `.github/workflows/deploy-ios.yml` refuses a tag that differs from the committed iOS or Android version, runs every gate, archives with cloud-managed signing and uploads to TestFlight. Secrets: `GOOGLE_SERVICE_INFO_PLIST_PROD_BASE64`, `MAPBOX_ACCESS_TOKEN`, `APP_STORE_CONNECT_API_KEY_ID`, `APP_STORE_CONNECT_API_ISSUER_ID`, `APP_STORE_CONNECT_API_KEY_P8`.
   - Android: `./gradlew bundleProductionRelease` (Bitrise `deploy_play_store` until the Play account change).
5. Store copy in `RELEASE_NOTES.md`, one section per release starting with the "do not paste the summary" warning:
   - App Store Connect "What's New": plain text per locale, 4000 characters maximum, never mention Android or Google Play.
   - App Store Connect "Promotional Text": 170 characters per locale, evergreen.
   - Google Play "Release notes": `<en-US>`, `<fr-FR>`, `<nl-NL>` blocks in one field, 500 characters per locale (aim for 450).
   - App Review notes whenever a permission string or prompt changed, with the path to reach it.
6. Ship the backend changes with `scripts/deploy.sh` (staging, checks, production). Skip it when `functions/`, the rules and `web/` did not change since the last deploy.
7. Submit the TestFlight build for review in App Store Connect, upload the Android bundle, paste the store copy.
