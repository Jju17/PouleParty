#!/bin/bash
# Archives PouleParty with cloud-managed signing and uploads it to TestFlight.
# Usage: upload-testflight.sh <build number>
# Expects APP_STORE_CONNECT_API_KEY_ID, APP_STORE_CONNECT_API_ISSUER_ID and APP_STORE_CONNECT_API_KEY_P8.
set -euo pipefail

build_number="${1:?usage: upload-testflight.sh <build number>}"
: "${APP_STORE_CONNECT_API_KEY_ID:?APP_STORE_CONNECT_API_KEY_ID is not set}"
: "${APP_STORE_CONNECT_API_ISSUER_ID:?APP_STORE_CONNECT_API_ISSUER_ID is not set}"
: "${APP_STORE_CONNECT_API_KEY_P8:?APP_STORE_CONNECT_API_KEY_P8 is not set}"

ios_dir="$(cd "$(dirname "$0")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT
key_path="$work_dir/AuthKey_${APP_STORE_CONNECT_API_KEY_ID}.p8"
printf '%s\n' "$APP_STORE_CONNECT_API_KEY_P8" > "$key_path"

authentication=(
  -allowProvisioningUpdates
  -authenticationKeyPath "$key_path"
  -authenticationKeyID "$APP_STORE_CONNECT_API_KEY_ID"
  -authenticationKeyIssuerID "$APP_STORE_CONNECT_API_ISSUER_ID"
)

defaults write com.apple.dt.Xcode IDESkipPackagePluginFingerprintValidatation -bool YES

xcodebuild archive \
  -project "$ios_dir/PouleParty.xcodeproj" \
  -scheme PouleParty \
  -configuration Release \
  -destination "generic/platform=iOS" \
  -archivePath "$work_dir/PouleParty.xcarchive" \
  -clonedSourcePackagesDirPath "$ios_dir/../SourcePackages" \
  -skipPackagePluginValidation -skipMacroValidation \
  "${authentication[@]}" \
  CURRENT_PROJECT_VERSION="$build_number"

xcodebuild -exportArchive \
  -archivePath "$work_dir/PouleParty.xcarchive" \
  -exportOptionsPlist "$ios_dir/Scripts/ci/ExportOptions.plist" \
  -exportPath "$work_dir/export" \
  "${authentication[@]}"

echo "PouleParty ($build_number) uploaded to TestFlight"
