# CLAUDE.md

Guidance for working in this repository. Keep it short: details live in `.claude/rules/` and `docs/`.

## What this is

PouleParty is a location-based hide-and-seek game on iOS and Android. The **chicken** runs, **hunters** chase it inside a shrinking zone on a real map, optional **GameMasters** referee and validate challenge proofs. A web site sells tickets for live events, and Cloud Functions own every rule the clients must not forge.

Read `.claude/rules/game.md` before changing gameplay: roles, modes, the stored zone schedule, power-ups, the lifecycle, manual launch, paid events, admin and QA modes.

## Layout and commands

| Target | Path | Gate |
|---|---|---|
| iOS (SwiftUI, TCA) | `ios/` | `xcodebuild test -scheme PoulePartyTests` and `swiftlint --strict` |
| Android (Compose, Hilt) | `android/` | `./gradlew testStagingDebugUnitTest lintStagingDebug` |
| Cloud Functions | `functions/` | `npm run verify && npm run test:rules` |
| Web | `web/` | `npm run verify` |

Per-target conventions: `.claude/rules/ios.md`, `android.md`, `functions.md`, `web.md`. CI (`.github/workflows/verify.yml`) runs all four gates; commit subjects are checked by `scripts/check-commit-messages.sh`.

## Data

Firestore for games and anything durable, Realtime Database for positions and presence, Storage for proofs. Full layout in `docs/data-model.md`. Key invariants:

- Clients never write `roles`, `winners`, scores, power-up spawns or status transitions other than cancel; callables do, with the admin SDK.
- Callables return failures as `{ code }` in `details`; both apps translate the code, never the server message.
- Finished games are purged after 30 days with everything attached to them.

## Rules that every change follows

- **Parity.** iOS and Android behave identically; shared maths is pinned by the server-generated `parity/golden.json`. See `.claude/rules/parity.md`.
- **Server first.** Security lives in rules and callables, UI checks only hide things. Rules changes ship with emulator tests in `functions/test/rules`.
- **Errors are visible.** A failed load is an error state with retry, a failed action shows the translated reason, a caught error is at least logged with context.
- **Copy.** Every visible string is in the iOS catalogue and the Android resources, in English, French and Dutch together, sentence case, British English, informal Dutch, real plurals, no em dash.
- **Accessibility.** AA contrast (orange text uses the darker accent on beige), labelled icon buttons, 44 pt / 48 dp targets, announced errors, reduced motion respected.
- **Tests.** A logic file ships with its tests; time is injected, never read from the wall clock in logic.
- **Zero warnings.** Compiler and lint warnings fail the Android and web builds and must be fixed on iOS.
- **One path to production.** Backend: `scripts/deploy.sh`. Apps: `.claude/rules/release.md`. Never deploy by hand.

## Where to look

- `known-issues.md`: open items and decisions still pending.
- `docs/runbook.md`: deploys, backups, restore, incidents.
- `docs/design-system.md` and `/Assets/index.html`: fonts (Bangers, Early GameBoy), palette, contrast rules.
- `infra/`: backups, storage lifecycle, secrets checklist.
