#!/bin/bash
# Exports an archive and uploads it to TestFlight.
# Usage: upload-testflight.sh <archive path>
set -euo pipefail

archive_path="${1:?usage: upload-testflight.sh <archive path>}"
ios_dir="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=asc-auth.sh
source "$ios_dir/Scripts/ci/asc-auth.sh"

xcodebuild -exportArchive \
  -archivePath "$archive_path" \
  -exportOptionsPlist "$ios_dir/Scripts/ci/ExportOptions.plist" \
  -exportPath "$(mktemp -d)" \
  "${authentication[@]}"

echo "Uploaded to TestFlight"
