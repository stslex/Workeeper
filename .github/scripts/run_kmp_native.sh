#!/usr/bin/env bash

set -euo pipefail
flags=(--continue --rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain)

case "${1:?choose simulator or device}" in
  simulator)
    : "${WORKEEPER_DATABASE_TEST_SIMULATOR:?owned test simulator required}"
    : "${WORKEEPER_INSTALL_EPOCH_NONCE:?fresh run nonce required}"
    selection=simulator-tasks
    marker=mark-simulator
    flags+=(--no-parallel)
    ;;
  device)
    selection=device-tasks
    marker=mark-device
    ;;
  *)
    printf 'Unknown Native verification phase %s\n' "$1" >&2
    exit 2
    ;;
esac

task_list="$(python3 .github/scripts/kmp_native_model.py "$selection")"
test -n "$task_list"
tasks=()
while IFS= read -r task; do
  tasks+=("$task")
done <<< "$task_list"
test "${#tasks[@]}" -gt 0
python3 .github/scripts/kmp_native_model.py "$marker"

exec ./gradlew "${tasks[@]}" "${flags[@]}"
