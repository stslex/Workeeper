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

# Keep only bullets for allowed categories (emoji): ✨, 🐛, ⚡, 📦
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
  # Only keep bullet lines starting with an allowed emoji
  if [[ "$l" =~ ^-\ (✨|🐛|⚡|📦)\  ]]; then
    # Drop double spaces after emoji to a single space
    l=$(echo "$l" | sed -E 's/^(-\s*[✨🐛⚡📦])\s+/\1 /')
    # Remove trailing spaces
    l=$(echo "$l" | sed -E 's/[[:space:]]+$//')
    # Enforce max per-item length (120 chars)
    if (( ${#l} > 120 )); then
      l="${l:0:117}…"
    fi
    filtered+=("$l")
  fi
done

# Fallback if nothing remains
if (( ${#filtered[@]} == 0 )); then
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

