#!/usr/bin/env bash
# The only path to production for the backend: rules, functions and hosting.
# Usage: scripts/deploy.sh [staging|production|both]   (default: both, staging first)
set -euo pipefail
cd "$(dirname "$0")/.."

target="${1:-both}"
case "$target" in staging|production|both) ;; *) echo "usage: $0 [staging|production|both]"; exit 64 ;; esac

[[ -z "$(git status --porcelain)" ]] || { echo "Refusing to deploy: the working tree has uncommitted changes."; exit 1; }
[[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || { echo "Refusing to deploy: not on main."; exit 1; }
git fetch --quiet origin main
[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || { echo "Refusing to deploy: HEAD is not origin/main."; exit 1; }

echo "== Gate: functions"
(cd functions && npm ci --silent && npm run verify && npm run build && npm run test:rules) || { echo "FAILED: functions gate"; exit 1; }
echo "== Gate: web"
(cd web && npm ci --silent && npm run verify) || { echo "FAILED: web gate"; exit 1; }

deploy() {
  local alias="$1" site
  [[ "$alias" == "production" ]] && site="https://pouleparty.be" || site="https://pouleparty-ba586.web.app"
  echo "== Deploying to $alias"
  firebase deploy --project "$alias" --only firestore:rules,firestore:indexes,database,storage,functions,hosting --non-interactive
  for url in "$site/" "$site/fr/inscription"; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$url")
    [[ "$code" == "200" ]] || { echo "FAILED: $url answered $code after deploy"; exit 1; }
  done
  echo "== $alias is live"
}

[[ "$target" == "production" ]] || deploy staging
if [[ "$target" != "staging" ]]; then
  read -r -p "Staging is live. Deploy to production? Type 'production' to continue: " answer
  [[ "$answer" == "production" ]] || { echo "Production deploy cancelled."; exit 1; }
  deploy production
fi
