#!/usr/bin/env bash
set -euo pipefail

# Resolve project root (two levels up from scripts dir)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"

VERSION_FILE="$ROOT_DIR/gradle/libs.versions.toml"
EN_CHANGELOG_DIR="$ROOT_DIR/fastlane/metadata/android/en-US/changelogs"
RU_CHANGELOG_DIR="$ROOT_DIR/fastlane/metadata/android/ru-RU/changelogs"

if [[ ! -f "$VERSION_FILE" ]]; then
  echo "Version file not found: $VERSION_FILE" >&2
  exit 1
fi

# Extract versionName and versionCode from toml
VERSION_NAME=$(awk -F '"' '/versionName = "/{print $2}' "$VERSION_FILE")
VERSION_CODE=$(awk -F '"' '/versionCode = "/{print $2}' "$VERSION_FILE")

# Allow workflows to influence tag format
TAG_PREFIX="${TAG_PREFIX:-}"
TAG_SUFFIX="${TAG_SUFFIX:-}"
BASE_TAG="v$VERSION_NAME"
TAG_NAME="${TAG_PREFIX}${BASE_TAG}${TAG_SUFFIX}"

# Determine previous tag to diff against
PREV_TAG=$(git describe --tags --abbrev=0 2>/dev/null || true)

TMP_NOTES="$(mktemp)"
{
  echo "${TAG_NAME} – $(date -u +%Y-%m-%d)"
  echo
  if [[ -n "$PREV_TAG" ]]; then
    echo "Changes since $PREV_TAG:"
    echo
    git log --no-merges --pretty=format:'- %s (%h) — %an' "$PREV_TAG..HEAD" || true
  else
    echo "Changes:"
    echo
    git log --no-merges --pretty=format:'- %s (%h) — %an' -n 200 || true
  fi
} > "$TMP_NOTES"

mkdir -p "$EN_CHANGELOG_DIR" "$RU_CHANGELOG_DIR"
cp "$TMP_NOTES" "$EN_CHANGELOG_DIR/${VERSION_CODE}.txt"
cp "$TMP_NOTES" "$RU_CHANGELOG_DIR/${VERSION_CODE}.txt"

# Also write a shared release notes file at repo root
NOTES_PATH="$ROOT_DIR/RELEASE_NOTES.md"
cp "$TMP_NOTES" "$NOTES_PATH"

echo "Generated changelog at:"
echo "  $EN_CHANGELOG_DIR/${VERSION_CODE}.txt"
echo "  $RU_CHANGELOG_DIR/${VERSION_CODE}.txt"

echo "VERSION_NAME=$VERSION_NAME" >> "$GITHUB_OUTPUT" 2>/dev/null || true
echo "VERSION_CODE=$VERSION_CODE" >> "$GITHUB_OUTPUT" 2>/dev/null || true
echo "TAG_NAME=$TAG_NAME" >> "$GITHUB_OUTPUT" 2>/dev/null || true
echo "notes_path=$NOTES_PATH" >> "$GITHUB_OUTPUT" 2>/dev/null || true
