# Cross-platform parity

iOS and Android must produce identical results. The server is the reference; `parity/golden.json` is generated from it (`cd functions && npm run parity:golden`) and replayed by `SharedParityGoldenTests` (iOS), `SharedParityGoldenTest` (Android) and `parityGolden.test.ts`.

Kept in sync on the three code bases:

- Distance: one haversine with an Earth radius of 6,371,000 m (`distanceMeters`).
- Seeded randomness: standard splitmix64 (advance, then mix), used at runtime for the jammer noise.
- Active circle selection (`selectActiveCircle`, `zoneRenderState`): the runtime zone path.
- Wizard preview mirrors (`computeZoneRadius`, `pickInitialZoneCenter`, `interpolateZoneCenter`, `deterministicDriftCenter`, `calculateNormalModeSettings`) and the power-up spawn mirror (`generatePowerUps`, 32-bit arithmetic like the server). They only feed the wizard recap and the parity tests.
- Timer helpers: countdown, game over by time (`now >= end`), winner detection.

When one of these changes, change all platforms in the same branch, regenerate the golden file and keep every parity test green. Copy and labels follow the shared glossary: the same English sentence on both apps, translated identically in French and Dutch.
