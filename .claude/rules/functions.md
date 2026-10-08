# Cloud Functions

TypeScript 7, Node 24, firebase-functions v2, firebase-admin modular API, region `europe-west1`.

## Commands

```bash
cd functions
npm run verify        # typecheck (sources, tests, scripts), oxlint, vitest
npm run test:rules    # Firestore, RTDB and Storage rules on the emulators (ports 8180, 9100, 9299)
npm run parity:golden # regenerate parity/golden.json after changing shared maths
npm run build
```

## Structure

- `src/index.ts` only initialises the app and re-exports functions.
- `config.ts`: `REGION`, `db()`, `CALLABLE_OPTIONS` (App Check enforced through the `ENFORCE_APP_CHECK` parameter, `maxInstances`), `apiError(status, code, message)`, `requireUid`, `requireString`.
- Callables throw `apiError` with a stable `details.code` from `ApiErrorCode`; both apps translate that code. Add a new code to the type, to Android `ApiErrorCode` + strings and to iOS `ApiErrorCode` + catalogue in the same change.
- Lifecycle: `gameTriggers.ts` (create, update, delete), `lifecycleTasks.ts` (planned Cloud Tasks with deterministic ids and a manifest), `launchGame.ts`, `outOfZone.ts`, `powerUpSpawnTask.ts`, `notifications.ts`, `maintenance.ts` (daily purge, side-effect replay).
- Roles and gameplay: `roles.ts`, `gameMaster.ts`, `gameplay.ts`, `powerUps.ts`, `validation.ts`.
- Payments: `registrations.ts`, `email/`, `sheets.ts`, `events.ts` (event table, the only place an event date lives).
- `scripts/` holds admin scripts run with `npx tsx`; they need `FIREBASE_PROJECT_ID` and `CONFIRM_PROD=yes` for production, and are not deployed.

## Rules

- Every state change a client must not forge happens here with the admin SDK, in a transaction when it reads before writing.
- Paid side effects (email, sheet) never fail the webhook; failures go to `/failedSideEffects` and are replayed.
- No PII in logs: log ids, never emails or names.
- Cloud Task ids are deterministic and `task-already-exists` is success: every handler is idempotent.
