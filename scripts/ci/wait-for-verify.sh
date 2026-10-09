#!/usr/bin/env bash
# Waits for the Verify workflow run of a commit and fails unless it succeeded.
# Usage: scripts/ci/wait-for-verify.sh <commit sha>   (needs GH_TOKEN with actions:read)
set -euo pipefail

sha="${1:?usage: wait-for-verify.sh <commit sha>}"
deadline=$((SECONDS + 45 * 60))

while true; do
  run=$(gh run list --workflow verify.yml --commit "$sha" --event push --limit 1 --json status,conclusion,url --jq '.[0] // empty')
  if [[ -z "$run" ]]; then
    echo "No Verify run for $sha: push the commit to main before tagging it."
    exit 1
  fi
  status=$(jq -r .status <<<"$run")
  conclusion=$(jq -r .conclusion <<<"$run")
  if [[ "$status" == "completed" ]]; then
    [[ "$conclusion" == "success" ]] || { echo "Verify for $sha ended with $conclusion: $(jq -r .url <<<"$run")"; exit 1; }
    echo "Verify passed for $sha"
    exit 0
  fi
  (( SECONDS < deadline )) || { echo "Verify for $sha still $status after 45 minutes"; exit 1; }
  echo "Verify for $sha is $status, waiting"
  sleep 30
done
