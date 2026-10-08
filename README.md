# PouleParty

A location-based hide-and-seek game for iOS and Android. One player is the **Chicken** and runs; the **Hunters** chase it inside a zone that shrinks over time on a real map. Optional **GameMasters** referee the game and validate challenge proofs.

| Mode | Stored value | In short |
|---|---|---|
| Follow the chicken | `followTheChicken` | The zone follows the chicken's live position; hunters see it inside the zone. |
| Stay in the zone | `stayInTheZone` | A fixed zone shrinks and drifts towards a final point; positions stay hidden except during a radar ping. |

## Repository

| Folder | What lives there |
|---|---|
| `ios/` | SwiftUI app built with The Composable Architecture |
| `android/` | Jetpack Compose app, MVVM with Hilt |
| `functions/` | Cloud Functions (TypeScript, Node 24): game lifecycle, roles, power-ups, payments, notifications |
| `web/` | Landing page and paid registration form (React 19, Vite) |
| `parity/` | Golden vectors generated from the server, replayed by the iOS, Android and server tests |
| `firestore.rules`, `database.rules.json`, `storage.rules` | Security rules, covered by emulator tests in `functions/test/rules` |
| `scripts/` | `deploy.sh` (the only path to production) and the commit message check |
| `infra/` | Declared infrastructure: backups, retention, service accounts |
| `docs/` | Data model, design system, runbook |

## Quick start

```bash
# Cloud Functions: typecheck, lint, unit tests, then the rules suite on the emulators
cd functions && npm ci && npm run verify && npm run test:rules

# Web
cd web && npm ci && npm run verify

# Android (JDK 21): tests and strict lint
cd android && ./gradlew testStagingDebugUnitTest lintStagingDebug

# iOS
cd ios && xcodebuild test -scheme PoulePartyTests -destination 'platform=iOS Simulator,name=iPhone 17'
```

Local builds need the Firebase config files, which stay out of git. For tests, the placeholders in `ci/firebase/` are enough: copy them to `android/app/google-services.json` and `ios/Firebase/Staging/GoogleService-Info.plist`. The Android build reads `MAPBOX_ACCESS_TOKEN` from `android/local.properties`.

## Shipping

- Every push and pull request runs `.github/workflows/verify.yml` on all four targets.
- Backend changes ship with `scripts/deploy.sh`: it refuses a dirty tree or a branch other than `main`, replays the gates, deploys staging first, checks the live pages, then asks before production.
- Mobile builds go through Bitrise (`bitrise.yml`) and the store consoles; see `.claude/rules/release.md`.

## Documentation

- `CLAUDE.md`: how the game and the code fit together, and the rules every change follows.
- `docs/data-model.md`: Firestore, Realtime Database and Storage layout.
- `docs/design-system.md`: fonts, colours, contrast rules and visual effects.
- `docs/runbook.md`: backups, restore, incident steps.
- `known-issues.md`: what is still open and why.
- `CHANGELOG.md`, `RELEASE_NOTES.md`: release history and store copy.
