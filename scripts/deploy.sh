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
  local live code removed
  firebase functions:list --project "$alias" --json > /dev/null 2>&1 || { echo "FAILED: cannot list the live functions on $alias; run firebase login --reauth"; exit 1; }
  live=$(firebase functions:list --project "$alias" --json | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{const r=JSON.parse(s);console.log((r.result||[]).map(f=>f.id).join("\n"))})' | LC_ALL=C sort)
  code=$(cd functions && node -e 'console.log(Object.keys(require("./lib/index.js")).join("\n"))' | LC_ALL=C sort)
  removed=$(LC_ALL=C comm -23 <(echo "$live") <(echo "$code"))
  [[ -z "$removed" ]] || { echo "Refusing to deploy: live functions missing from the code would be deleted: $removed"; exit 1; }
  # --force only acknowledges retry policies here: the check above rules out deletions.
  firebase deploy --project "$alias" --only firestore:rules,firestore:indexes,database,storage,functions,hosting --non-interactive --force
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
