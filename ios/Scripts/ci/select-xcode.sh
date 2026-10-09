#!/bin/bash
# Switches the CI runner to the newest non-beta Xcode: App Store Connect rejects beta builds.
set -euo pipefail

newest_xcode="$(find /Applications -maxdepth 1 -name 'Xcode_*.app' | grep -vi beta | sort -V | tail -1)"
if [ -z "$newest_xcode" ]; then
  echo "error: no Xcode found in /Applications" >&2
  exit 1
fi
sudo xcode-select --switch "$newest_xcode"
xcodebuild -version
echo "version=$(xcodebuild -version | sed -n 's/^Xcode //p')" >> "${GITHUB_OUTPUT:-/dev/null}"
