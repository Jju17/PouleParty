#!/usr/bin/env bash
# Refuses a release tag whose version differs from the one committed for iOS and Android.
# Usage: scripts/check-release-version.sh <version>
set -euo pipefail
cd "$(dirname "$0")/.."

version="${1:?usage: check-release-version.sh <version>}"
ios_version=$(sed -n 's/^MARKETING_VERSION = //p' ios/Config/Versions.xcconfig)
android_version=$(sed -n 's/^ *versionName = "\(.*\)"$/\1/p' android/app/build.gradle.kts)

[[ "$ios_version" == "$version" ]] || { echo "Tag version $version differs from iOS MARKETING_VERSION $ios_version"; exit 1; }
[[ "$android_version" == "$version" ]] || { echo "Tag version $version differs from Android versionName $android_version"; exit 1; }
echo "Release version $version matches iOS and Android"
