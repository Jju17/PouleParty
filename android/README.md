# Android, Jetpack Compose + MVVM + Hilt

PouleParty Android app built with Jetpack Compose, MVVM architecture, and Hilt dependency injection.

## Requirements

- Android Studio with JDK 21
- compileSdk 37, minSdk 26, targetSdk 36
- `google-services.json` in `app/src/staging/` and `app/src/production/` (gitignored, one per flavour)
- `MAPBOX_DOWNLOADS_TOKEN` (secret `sk.` token with `DOWNLOADS:READ`) in `~/.gradle/gradle.properties`, only once the Mapbox SDK moves past 11.20.2
- `MAPBOX_ACCESS_TOKEN` (public `pk.` token, restricted to the app package in the Mapbox console) in `local.properties` or the environment; release builds refuse to start without it

## Build & run

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JDK 21
cd android && ./gradlew assembleStagingDebug
cd android && ./gradlew testStagingDebugUnitTest lintStagingDebug
cd android && ./gradlew testStagingDebugUnitTest --tests "dev.rahier.pouleparty.model.GameTest"
```

**Product flavors:** `staging` and `production` (differ only in Firebase config).
**Build variants:** `stagingDebug`, `stagingRelease`, `productionDebug`, `productionRelease`.

## Structure and conventions

See `../.claude/rules/android.md`.
