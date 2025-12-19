#!/usr/bin/env bash
set -euo pipefail

# Generates changelog from git commits between two tags/refs
# Supports both GitHub (full changelog) and Play Store (filtered) formats
#
# Usage:
#   generate_changelog.sh <from_ref> <to_ref> <format> [version_code]
#
# Parameters:
#   from_ref    - Starting git ref (tag, commit, etc.)
#   to_ref      - Ending git ref (tag, commit, etc.)
#   format      - "github" or "play"
#   version_code - Required for play format, used for output filename
#
# Output:
#   - GitHub format: Outputs to stdout
#   - Play format: Writes to fastlane/metadata/android/en-US/changelogs/{version_code}.txt

FROM_REF="${1:-}"
TO_REF="${2:-}"
FORMAT="${3:-github}"
VERSION_CODE="${4:-}"

if [[ -z "$FROM_REF" || -z "$TO_REF" ]]; then
  echo "Usage: $0 <from_ref> <to_ref> <format> [version_code]" >&2
  echo "Formats: github, play" >&2
  exit 1
fi

if [[ "$FORMAT" == "play" && -z "$VERSION_CODE" ]]; then
  echo "VERSION_CODE is required for play format" >&2
  exit 1
fi

# Get commits between refs
mapfile -t commits < <(git log --oneline --no-merges "$FROM_REF..$TO_REF" | sed 's/^[a-f0-9]* //')

if (( ${#commits[@]} == 0 )); then
  if [[ "$FORMAT" == "play" ]]; then
    mkdir -p "fastlane/metadata/android/en-US/changelogs"
    echo "- 🛠 Bug fixes and performance improvements" > "fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"
    echo "No commits found, created fallback changelog"
  else
    echo "## 🚀 Release $TO_REF"
    echo ""
    echo "### ✨ What's Changed"
    echo "- 🛠 Bug fixes and performance improvements"
    echo ""
    echo "— Generated from commits between $FROM_REF and $TO_REF."
  fi
  exit 0
fi

# Process commits
filtered_commits=()
all_commits=()

for commit in "${commits[@]}"; do
  # Transform conventional commits to emoji format
  if [[ "$commit" =~ ^feat.*:\ (.*) ]]; then
    emoji_commit="✨ ${BASH_REMATCH[1]}"
    filtered_commits+=("- $emoji_commit")
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^fix.*:\ (.*) ]]; then
    emoji_commit="🐛 ${BASH_REMATCH[1]}"
    filtered_commits+=("- $emoji_commit")
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^perf.*:\ (.*) ]]; then
    emoji_commit="⚡ ${BASH_REMATCH[1]}"
    filtered_commits+=("- $emoji_commit")
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^refactor.*:\ (.*) ]]; then
    emoji_commit="♻️ ${BASH_REMATCH[1]}"
    filtered_commits+=("- $emoji_commit")
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^security.*:\ (.*) ]]; then
    emoji_commit="🔒 ${BASH_REMATCH[1]}"
    filtered_commits+=("- $emoji_commit")
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^(deps|dependencies).*:\ (.*) ]]; then
    emoji_commit="📦 ${BASH_REMATCH[2]}"
    filtered_commits+=("- $emoji_commit")
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^docs.*:\ (.*) ]]; then
    emoji_commit="📝 ${BASH_REMATCH[1]}"
    all_commits+=("- $emoji_commit")
  elif [[ "$commit" =~ ^(build|ci|chore|style|test).*:\ (.*) ]]; then
    emoji_commit="🔧 ${BASH_REMATCH[2]}"
    all_commits+=("- $emoji_commit")
  else
    # Non-conventional commit, include in GitHub but not Play Store
    all_commits+=("- $commit")
  fi
done

if [[ "$FORMAT" == "github" ]]; then
  # GitHub format - show all commits
  echo "## 🚀 Release $TO_REF"
  echo ""
  echo "### ✨ What's Changed"

  if (( ${#all_commits[@]} == 0 )); then
    echo "- 🛠 Bug fixes and performance improvements"
  else
    printf "%s\n" "${all_commits[@]}"
  fi

  echo ""
  echo "— Generated from commits between $FROM_REF and $TO_REF."

elif [[ "$FORMAT" == "play" ]]; then
  # Play Store format - filtered commits only
  mkdir -p "fastlane/metadata/android/en-US/changelogs"
  OUTPUT_FILE="fastlane/metadata/android/en-US/changelogs/${VERSION_CODE}.txt"

  if (( ${#filtered_commits[@]} == 0 )); then
    echo "- Bug fixes and performance improvements" > "$OUTPUT_FILE"
    echo "No relevant commits found, created fallback changelog"
  else
    # Play Store limit is 500 characters (bytes)
    # We use 490 as safe limit to account for newlines and encoding
    CHAR_LIMIT=490
    total=0
    out_lines=()

    for line in "${filtered_commits[@]}"; do
      # Calculate byte length for proper UTF-8 handling
      line_bytes=$(echo -n "$line" | wc -c)
      # +1 for newline when joined
      next=$(( total + line_bytes + 1 ))
      if (( next > CHAR_LIMIT )); then
        break
      fi
      out_lines+=("$line")
      total=$next
    done

    # If no lines fit, add a truncated version of the first line
    if (( ${#out_lines[@]} == 0 )); then
      first_line="${filtered_commits[0]}"
      # Truncate to ~480 bytes to be safe
      truncated=$(echo -n "$first_line" | cut -c1-480)
      out_lines+=("$truncated...")
    fi

    printf "%s\n" "${out_lines[@]}" > "$OUTPUT_FILE"

    # Verify final file size and truncate if needed
    file_bytes=$(wc -c < "$OUTPUT_FILE")
    if (( file_bytes > 500 )); then
      # Truncate file to 497 bytes and add "..."
      head -c 497 "$OUTPUT_FILE" > "${OUTPUT_FILE}.tmp"
      echo -n "..." >> "${OUTPUT_FILE}.tmp"
      mv "${OUTPUT_FILE}.tmp" "$OUTPUT_FILE"
      echo "Warning: Changelog truncated to 500 bytes"
    fi

    final_size=$(wc -c < "$OUTPUT_FILE")
    echo "Created Play changelog with ${#out_lines[@]} items (${final_size} bytes)"
  fi
fi