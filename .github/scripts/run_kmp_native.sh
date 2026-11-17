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

set +e
./gradlew "${tasks[@]}" "${flags[@]}" 2>&1 | tee "build/kmp-verification/$1-gradle.log"
process_exits=("${PIPESTATUS[@]}")
set -e
python3 - "$1" "${process_exits[@]}" "${tasks[@]}" "${flags[@]}" <<'RECEIPT'
import json
from pathlib import Path
import sys

role, gradle_exit, log_exit, *arguments = sys.argv[1:]
exit_code = int(gradle_exit) or int(log_exit)
Path(f"build/kmp-verification/{role}-execution.json").write_text(json.dumps({
    "role": role, "exit_code": exit_code, "gradle_exit": int(gradle_exit),
    "log_exit": int(log_exit), "arguments": arguments,
}, indent=2) + "\n")
sys.exit(exit_code)
RECEIPT
