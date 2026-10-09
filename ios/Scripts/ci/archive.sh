#!/bin/bash
# Archives PouleParty for the App Store with cloud-managed signing.
# Usage: archive.sh <build number> <archive path>
set -euo pipefail

build_number="${1:?usage: archive.sh <build number> <archive path>}"
archive_path="${2:?missing archive path}"
ios_dir="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=asc-auth.sh
source "$ios_dir/Scripts/ci/asc-auth.sh"

defaults write com.apple.dt.Xcode IDESkipPackagePluginFingerprintValidatation -bool YES

xcodebuild archive \
  -project "$ios_dir/PouleParty.xcodeproj" \
  -scheme PouleParty \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath "$archive_path" \
  -derivedDataPath "$ios_dir/../DerivedData" \
  -skipPackagePluginValidation -skipMacroValidation \
  "${authentication[@]}" \
  CURRENT_PROJECT_VERSION="$build_number" \
  COMPILER_INDEX_STORE_ENABLE=NO

echo "Archived PouleParty ($build_number)"
