#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage: create-release-signing.sh [options] OUTPUT_DIRECTORY

Create PuppyCoder's permanent Android release keystore and GitHub Actions
secret values. OUTPUT_DIRECTORY must be outside the Git repository.

Options:
  --alias ALIAS            Key alias (default: puppycoder)
  --github-repo OWNER/REPO Upload the generated values with gh secret set
  -h, --help               Show this help

The output directory will contain:
  puppycoder-release.jks
  github-actions-secrets.env

Both files are sensitive. Back them up securely and never commit them.
EOF
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

key_alias="puppycoder"
github_repo=""
output_directory=""

while (($# > 0)); do
    case "$1" in
        --alias)
            (($# >= 2)) || fail "--alias requires a value"
            key_alias="$2"
            shift 2
            ;;
        --github-repo)
            (($# >= 2)) || fail "--github-repo requires OWNER/REPO"
            github_repo="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            fail "unknown option: $1"
            ;;
        *)
            [[ -z "$output_directory" ]] || fail "only one output directory may be provided"
            output_directory="$1"
            shift
            ;;
    esac
done

[[ -n "$output_directory" ]] || {
    usage >&2
    exit 1
}
[[ "$key_alias" =~ ^[A-Za-z0-9._-]+$ ]] || fail "alias may contain only letters, numbers, dot, underscore, and hyphen"
if [[ -n "$github_repo" ]]; then
    [[ "$github_repo" =~ ^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$ ]] || fail "GitHub repository must use OWNER/REPO"
fi

require_command keytool
require_command openssl
require_command base64
if [[ -n "$github_repo" ]]; then
    require_command gh
fi

umask 077
script_directory="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
repository_root="$(CDPATH= cd -- "$script_directory/../.." && pwd)"
created_output_directory=false
if [[ ! -d "$output_directory" ]]; then
    mkdir -p -- "$output_directory"
    created_output_directory=true
fi
output_directory="$(CDPATH= cd -- "$output_directory" && pwd)"

case "$output_directory" in
    "$repository_root"|"$repository_root"/*)
        if [[ "$created_output_directory" == true ]]; then
            rmdir -- "$output_directory" 2>/dev/null || true
        fi
        fail "output directory must be outside the Git repository"
        ;;
esac

keystore_path="$output_directory/puppycoder-release.jks"
secrets_path="$output_directory/github-actions-secrets.env"
[[ ! -e "$keystore_path" ]] || fail "refusing to overwrite $keystore_path"
[[ ! -e "$secrets_path" ]] || fail "refusing to overwrite $secrets_path"

temporary_directory="$(mktemp -d "$output_directory/.release-signing.XXXXXX")"
temporary_keystore="$temporary_directory/puppycoder-release.jks"
temporary_secrets="$temporary_directory/github-actions-secrets.env"

cleanup() {
    [[ -z "${temporary_keystore:-}" ]] || rm -f -- "$temporary_keystore"
    [[ -z "${temporary_secrets:-}" ]] || rm -f -- "$temporary_secrets"
    [[ -z "${temporary_directory:-}" ]] || rmdir -- "$temporary_directory" 2>/dev/null || true
}
trap cleanup EXIT

keystore_password="$(openssl rand -hex 32)"
key_password="$(openssl rand -hex 32)"

PUPPYCODER_STORE_PASSWORD="$keystore_password" \
PUPPYCODER_KEY_PASSWORD="$key_password" \
keytool -genkeypair \
    -keystore "$temporary_keystore" \
    -storetype JKS \
    -storepass:env PUPPYCODER_STORE_PASSWORD \
    -alias "$key_alias" \
    -keypass:env PUPPYCODER_KEY_PASSWORD \
    -keyalg RSA \
    -keysize 4096 \
    -validity 36500 \
    -dname "CN=PuppyCoder Android, OU=Release, O=PuppyCoder" \
    -noprompt >/dev/null

keystore_base64="$(base64 < "$temporary_keystore" | tr -d '\r\n')"
{
    printf 'ANDROID_KEYSTORE_BASE64=%s\n' "$keystore_base64"
    printf 'ANDROID_KEYSTORE_PASSWORD=%s\n' "$keystore_password"
    printf 'ANDROID_KEY_ALIAS=%s\n' "$key_alias"
    printf 'ANDROID_KEY_PASSWORD=%s\n' "$key_password"
} > "$temporary_secrets"

chmod 600 "$temporary_keystore" "$temporary_secrets"
mv -- "$temporary_keystore" "$keystore_path"
temporary_keystore=""
mv -- "$temporary_secrets" "$secrets_path"
temporary_secrets=""
rmdir -- "$temporary_directory"
temporary_directory=""

if [[ -n "$github_repo" ]]; then
    printf '%s' "$keystore_base64" | gh secret set ANDROID_KEYSTORE_BASE64 --repo "$github_repo"
    printf '%s' "$keystore_password" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$github_repo"
    printf '%s' "$key_alias" | gh secret set ANDROID_KEY_ALIAS --repo "$github_repo"
    printf '%s' "$key_password" | gh secret set ANDROID_KEY_PASSWORD --repo "$github_repo"
    printf 'Uploaded Android signing secrets to %s.\n' "$github_repo"
fi

printf 'Created Android release signing identity.\n'
printf 'Keystore: %s\n' "$keystore_path"
printf 'Recovery values: %s\n' "$secrets_path"
printf 'Back up both files securely. Losing them prevents updates to released APKs.\n'
