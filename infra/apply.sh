#!/usr/bin/env bash
# Applies the infrastructure that firebase deploy does not manage. Idempotent.
# Usage: infra/apply.sh <firebase-project-id>
set -euo pipefail
project="${1:?project id required, e.g. pouleparty-ba586 or pouleparty-prod}"
bucket="${BUCKET:-$(gcloud storage buckets list --project "$project" --format='value(name)' | grep -E 'firebasestorage\.app$|appspot\.com$' | head -1)}"
[[ -n "$bucket" ]] || { echo "No Firebase Storage bucket found; pass BUCKET=<name>"; exit 1; }

echo "== Firestore daily backups, kept 14 days"
if ! gcloud firestore backups schedules list --project "$project" --database='(default)' --format='value(name)' | grep -q .; then
  gcloud firestore backups schedules create --project "$project" --database='(default)' --recurrence=daily --retention=14d
fi

echo "== Proof photos and videos deleted after 45 days (safety net behind the 30-day game purge)"
gcloud storage buckets update "gs://$bucket" --lifecycle-file="$(dirname "$0")/storage-lifecycle.json" --project "$project"

echo "== Secrets the functions expect (values are set by hand, never committed)"
for secret in STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET RESEND_API_KEY GOOGLE_SHEET_ID MAPBOX_ACCESS_TOKEN; do
  if gcloud secrets describe "$secret" --project "$project" >/dev/null 2>&1; then
    echo "   ok  $secret"
  else
    echo "   MISSING  $secret  (firebase functions:secrets:set $secret --project $project)"
  fi
done
