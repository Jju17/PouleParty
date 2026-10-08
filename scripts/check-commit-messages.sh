#!/usr/bin/env bash
# Checks every commit subject in a range against the repository message rules.
# Usage: scripts/check-commit-messages.sh <revision-range>   (e.g. origin/main..HEAD)
set -euo pipefail
range="${1:?revision range required}"
status=0
while IFS= read -r sha; do
  subject=$(git log -1 --format=%s "$sha")
  body=$(git log -1 --format=%b "$sha")
  problems=()
  (( ${#subject} > 90 )) && problems+=("subject longer than 90 characters")
  [[ -n "${body//[[:space:]]/}" ]] && problems+=("message has a body: keep it to one line")
  printf '%s\n%s' "$subject" "$body" | grep -q $'\xe2\x80\x94' && problems+=("contains an em dash")
  printf '%s\n%s' "$subject" "$body" | grep -qiE 'co-authored-by|generated with|claude ?(code|\.ai|opus|sonnet|fable)' && problems+=("contains attribution")
  printf '%s' "$subject" | grep -qE '\b[A-Z][A-Z0-9]+-[0-9]+\b' && problems+=("contains a ticket key")
  if (( ${#problems[@]} )); then
    status=1
    for p in "${problems[@]}"; do echo "${sha:0:7} $p: $subject"; done
  fi
done < <(git rev-list "$range")
exit $status
