# Cloud Functions

Backend for PouleParty: game lifecycle, roles, power-ups, challenges, paid registrations, notifications. TypeScript 7 on Node 24, region `europe-west1`. Conventions and structure: `../.claude/rules/functions.md`.

```bash
npm ci
npm run verify        # typecheck, lint, unit tests
npm run test:rules    # security rules on the Firebase emulators
npm run build
```

Deploy only through `../scripts/deploy.sh`.

## Secrets

`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `RESEND_API_KEY`, `GOOGLE_SHEET_ID`, `MAPBOX_ACCESS_TOKEN`, set per project with `firebase functions:secrets:set <NAME> --project <id>`. A function binds a secret version at deploy time: redeploy after setting a new value.

## Credentials

Deployed functions use the project's default service account through Application Default Credentials. Admin scripts in `scripts/` do the same from a laptop (`gcloud auth application-default login`) and refuse production unless `CONFIRM_PROD=yes`. Never add a service account key to the repository.
