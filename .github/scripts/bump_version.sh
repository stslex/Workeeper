#!/bin/bash
#
# Bump versionName and versionCode in gradle/libs.versions.toml.
#
# Usage:
#   bump_version.sh --mode=release   # X.Y.Z -> X.(Y+1).0, code +1
#   bump_version.sh --mode=hotfix    # X.Y.Z -> X.Y.(Z+1), code +1
#
# Reads and writes the TOML at <project>/gradle/libs.versions.toml, where
# <project> is the directory two levels above this script. The path can be
# overridden via WORKEEPER_VERSION_FILE for testing.
#
# On success, prints to stdout in a parseable form:
#   version_name=X.Y.Z
#   version_code=N
#
# Exits non-zero with a diagnostic on stderr if:
#   - mode is missing or invalid
#   - the TOML file is missing
#   - versionName does not match X.Y.Z
#   - versionCode is not a valid integer
#   - the post-write verification fails (the original file is then restored)

set -euo pipefail

usage() {
    echo "Usage: $0 --mode=release|hotfix" >&2
    exit 2
}

mode=""
for arg in "$@"; do
    case "$arg" in
        --mode=release|--mode=hotfix)
            mode="${arg#--mode=}"
            ;;
        --mode=*)
            echo "Error: invalid mode '${arg#--mode=}'. Expected 'release' or 'hotfix'." >&2
            exit 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Error: unrecognised argument '$arg'." >&2
            usage
            ;;
    esac
done

if [[ -z "$mode" ]]; then
    usage
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/../.." && pwd)"
version_file="${WORKEEPER_VERSION_FILE:-$project_dir/gradle/libs.versions.toml}"

if [[ ! -f "$version_file" ]]; then
    echo "Error: TOML file not found: $version_file" >&2
    exit 1
fi

read_field() {
    local field="$1"
    awk -F'"' -v key="^${field} = \"" '$0 ~ key { print $2; exit }' "$version_file"
}

current_name="$(read_field versionName)"
current_code="$(read_field versionCode)"

if [[ -z "$current_name" ]]; then
    echo "Error: could not read versionName from $version_file" >&2
    exit 1
fi
if [[ -z "$current_code" ]]; then
    echo "Error: could not read versionCode from $version_file" >&2
    exit 1
fi

if [[ ! "$current_name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Error: versionName '$current_name' must match X.Y.Z (three integer components separated by dots)." >&2
    exit 1
fi
major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

if [[ ! "$current_code" =~ ^[0-9]+$ ]]; then
    echo "Error: versionCode '$current_code' is not a valid integer." >&2
    exit 1
fi

case "$mode" in
    release) new_name="${major}.$((minor + 1)).0" ;;
    hotfix)  new_name="${major}.${minor}.$((patch + 1))" ;;
esac
new_code=$((current_code + 1))

backup="$(mktemp)"
trap 'rm -f "$backup"' EXIT
cp "$version_file" "$backup"

tmp="$(mktemp)"
if ! sed -E \
        -e "s|^versionName = \".*\"\$|versionName = \"${new_name}\"|" \
        -e "s|^versionCode = \".*\"\$|versionCode = \"${new_code}\"|" \
        "$version_file" > "$tmp"; then
    rm -f "$tmp"
    echo "Error: sed failed while rewriting $version_file." >&2
    exit 1
fi

mv "$tmp" "$version_file"

verify_name="$(read_field versionName)"
verify_code="$(read_field versionCode)"

if [[ "$verify_name" != "$new_name" || "$verify_code" != "$new_code" ]]; then
    cp "$backup" "$version_file"
    echo "Error: post-write verification failed (expected versionName=${new_name}, versionCode=${new_code}; read versionName=${verify_name}, versionCode=${verify_code}). Original file restored." >&2
    exit 1
fi

echo "version_name=${new_name}"
echo "version_code=${new_code}"
