#!/usr/bin/env bash

set -uo pipefail

run_smoke_gradle() {
  ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
    -Dorg.gradle.jvmargs=-Xmx3g \
    --max-workers=2 \
    --full-stacktrace \
    --continue
}

run_identity_gate() {
  python3 .github/scripts/assert_mvi_device_identities.py
}

smoke_exit_status() {
  local gradle_status="$1"
  local identity_status="$2"

  if (( gradle_status != 0 )); then
    return "$gradle_status"
  fi
  return "$identity_status"
}

main() {
  set +e

  adb logcat > logcat-smoke.txt &

  run_smoke_gradle
  gradle_status=$?

  run_identity_gate
  identity_status=$?

  adb logcat -d > logcat-smoke-final.txt

  smoke_exit_status "$gradle_status" "$identity_status"
  exit $?
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
