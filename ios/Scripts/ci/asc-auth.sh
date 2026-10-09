#!/bin/bash
# shellcheck disable=SC2034
# Sourced by the archive and upload scripts: writes the App Store Connect key and sets the xcodebuild auth flags.
: "${APP_STORE_CONNECT_API_KEY_ID:?APP_STORE_CONNECT_API_KEY_ID is not set}"
: "${APP_STORE_CONNECT_API_ISSUER_ID:?APP_STORE_CONNECT_API_ISSUER_ID is not set}"
: "${APP_STORE_CONNECT_API_KEY_P8:?APP_STORE_CONNECT_API_KEY_P8 is not set}"

key_dir="$(mktemp -d)"
trap 'rm -rf "$key_dir"' EXIT
key_path="$key_dir/AuthKey_${APP_STORE_CONNECT_API_KEY_ID}.p8"
printf '%s\n' "$APP_STORE_CONNECT_API_KEY_P8" > "$key_path"

authentication=(
  -allowProvisioningUpdates
  -authenticationKeyPath "$key_path"
  -authenticationKeyID "$APP_STORE_CONNECT_API_KEY_ID"
  -authenticationKeyIssuerID "$APP_STORE_CONNECT_API_ISSUER_ID"
)
