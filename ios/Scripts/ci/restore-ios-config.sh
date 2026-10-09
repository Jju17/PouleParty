#!/bin/bash
# Restores the gitignored production Firebase plist and the Mapbox token from CI secrets.
set -euo pipefail

ios_dir="$(cd "$(dirname "$0")/../.." && pwd)"
: "${GOOGLE_SERVICE_INFO_PLIST_PROD_BASE64:?secret GOOGLE_SERVICE_INFO_PLIST_PROD_BASE64 is not set}"
: "${MAPBOX_ACCESS_TOKEN:?secret MAPBOX_ACCESS_TOKEN is not set}"

mkdir -p "$ios_dir/Firebase/Production"
echo "$GOOGLE_SERVICE_INFO_PLIST_PROD_BASE64" | base64 --decode > "$ios_dir/Firebase/Production/GoogleService-Info.plist"
plutil -lint "$ios_dir/Firebase/Production/GoogleService-Info.plist" >/dev/null
echo "MBX_ACCESS_TOKEN = $MAPBOX_ACCESS_TOKEN" > "$ios_dir/Config/Secrets.xcconfig"

echo "iOS production config restored"
