#!/usr/bin/env bash
set -euo pipefail

# Reads markdown changelog from stdin, filters to key categories, trims per item,
# then writes a Play Store-friendly plain-text changelog limited to 500 chars.
#
# Env:
#   VERSION_CODE (required) - used to name the output file
#   OUT_DIR (optional) - output dir for Play notes (default fastlane/metadata/android/en-US/changelogs)

OUT_DIR=${OUT_DIR:-fastlane/metadata/android/en-US/changelogs}
if [[ -z "${VERSION_CODE:-}" ]]; then
  echo "VERSION_CODE env is required" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"

tmp_in=$(mktemp)
cat > "$tmp_in"

# Debug: Show what we received
echo "Debug: Raw input received:" >&2
cat "$tmp_in" >&2
echo "Debug: End of raw input" >&2

# Keep only bullets for allowed categories and transform them
# Normalize list markers to start with '- ' and strip markdown
mapfile -t lines < <(sed -E \
  -e 's/\r//g' \
  -e 's/^#+[[:space:]]*//g' \
  -e 's/^\s*[*-]\s*/- /g' \
  -e 's/`([^`]*)`/\1/g' \
  -e 's/!\[([^]]*)\]\([^)]+\)/\1/g' \
  -e 's/\[([^]]+)\]\([^)]+\)/\1/g' \
  "$tmp_in" | awk 'BEGIN{RS="\n"} {print}' )

filtered=()
for l in "${lines[@]}"; do
  # Skip empty lines
  [[ -z "$l" ]] && continue

  # Transform conventional commits to emoji format and filter allowed types
  if [[ "$l" =~ ^-\ feat.*:\ (.*) ]]; then
    l="- ✨ ${BASH_REMATCH[1]}"
  elif [[ "$l" =~ ^-\ fix.*:\ (.*) ]]; then
    l="- 🐛 ${BASH_REMATCH[1]}"
  elif [[ "$l" =~ ^-\ perf.*:\ (.*) ]]; then
    l="- ⚡ ${BASH_REMATCH[1]}"
  elif [[ "$l" =~ ^-\ refactor.*:\ (.*) ]]; then
    l="- ♻️ ${BASH_REMATCH[1]}"
  elif [[ "$l" =~ ^-\ security.*:\ (.*) ]]; then
    l="- 🔒 ${BASH_REMATCH[1]}"
  elif [[ "$l" =~ ^-\ (deps|dependencies).*:\ (.*) ]]; then
    l="- 📦 ${BASH_REMATCH[2]}"
  else
    # Skip commits that don't match allowed types
    continue
  fi

  # Remove trailing spaces
  l=$(echo "$l" | sed -E 's/[[:space:]]+$//')
  # Enforce max per-item length (120 chars)
  if (( ${#l} > 120 )); then
    l="${l:0:117}…"
  fi
  filtered+=("$l")
done

# Debug: Print what we found
echo "Debug: Found ${#filtered[@]} changelog items after filtering" >&2
for item in "${filtered[@]}"; do
  echo "Debug: $item" >&2
done

# Fallback if nothing remains
if (( ${#filtered[@]} == 0 )); then
  echo "Debug: No filtered items found, using fallback" >&2
  filtered+=("- 🛠 Bug fixes and performance improvements")
fi

# Assemble up to 500 chars total
limit=500
total=0
out_lines=()
for l in "${filtered[@]}"; do
  # +1 for newline when joined
  next=$(( total + ${#l} + 1 ))
  if (( next > limit )); then
    out_lines+=("…")
    break
  fi
  out_lines+=("$l")
  total=$next
done

out_file="$OUT_DIR/${VERSION_CODE}.txt"
printf "%s\n" "${out_lines[@]}" > "$out_file"
echo "Wrote Play changelog to $out_file (chars: $total)"

