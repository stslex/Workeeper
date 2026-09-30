#!/usr/bin/env bash
# Run a command while sampling host resources into the same log, so a runner that dies mid-step
# (a "shutdown signal" cancels every later step, and `failure()` is false on cancellation) still
# leaves the samples in the step log that GitHub keeps. Every sample line carries a `[res]` prefix.
#
#   bash .github/scripts/run_with_resource_samples.sh ./gradlew testDebugUnitTest --full-stacktrace
#
# The command's exit code is the script's exit code. Sampling every RESOURCE_SAMPLE_INTERVAL
# seconds (default 15); each block: uptime, free -m, one vmstat row (si/so/wa/st), df -h /,
# du -sh /tmp (capped at 5 s; the JVM's default temp dir on Linux), PSI for cpu/memory/io where
# the kernel exposes it, and the eight largest processes by RSS. Tools missing on the host (macOS
# lacks free/vmstat/PSI/timeout) are skipped, never fatal.
set -u
interval="${RESOURCE_SAMPLE_INTERVAL:-15}"

# A java process is named by what it runs, not by its command line (which a 160-character cut ends
# inside the classpath): main class or jar, -Xmx, and for a Gradle worker the task it serves.
# Gradle hands every worker -Dorg.gradle.internal.worker.tmpdir=<module>/build/tmp/<task>/work.
# `ps` joins arguments with spaces, so a path with a space splits into words; only a dotted class
# name counts as the main class.
describe_java() {
  local words word main="" xmx="-" task="-" skip="" dir
  local class_name='^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+$'
  read -ra words <<< "$1"
  for word in "${words[@]:1}"; do
    case "$skip" in
      value) skip=""; continue ;;
      jar) main="-jar ${word##*/}"; break ;;
    esac
    case "$word" in
      -cp|-classpath|--class-path|-p|--module-path|--add-opens|--add-exports|--add-reads|--add-modules|\
      --patch-module|--limit-modules|--upgrade-module-path) skip=value ;;
      -jar) skip=jar ;;
      -Xmx*) xmx="${word#-Xmx}" ;;
      -Dorg.gradle.internal.worker.tmpdir=*/build/tmp/*/work)
        dir="${word#*=}"
        task="${dir%/work}"
        task="${task##*/}"
        dir="${dir%/build/tmp/*}"
        dir="${dir#"$PWD"}"
        dir="${dir#/}"
        task=":${dir//\//:}:$task" ;;
      -*|@*) ;;
      *) if [[ "$word" =~ $class_name ]]; then main="$word"; break; fi ;;
    esac
  done
  echo "java main=${main:--} xmx=$xmx task=$task"
}

# Linux procps reports elapsed seconds (etimes); BSD ps on macOS (local stand-ins) has only etime and
# still prints the other columns when a keyword is unknown, so probe once instead of falling back.
elapsed_column=etimes
ps -o etimes= -p $$ > /dev/null 2>&1 || elapsed_column=etime

processes() {
  echo "    PID       RSS  ELAPSED COMMAND"
  ps -Ao "pid=,rss=,${elapsed_column}=,args=" \
    | sort -k2,2nr | head -8 | while read -r pid rss elapsed cmd; do
      case "${cmd%% *}" in
        java|*/java) printf '%7s %9s %8s %s\n' "$pid" "$rss" "$elapsed" "$(describe_java "$cmd")" ;;
        *) printf '%7s %9s %8s %s\n' "$pid" "$rss" "$elapsed" "$cmd" | cut -c1-160 ;;
      esac
    done
}

sample() {
  {
    echo "--- $(date -u +%FT%TZ) $(uptime 2>/dev/null || true)"
    free -m 2>/dev/null || true
    vmstat 1 2 2>/dev/null | tail -1 || true
    df -h / 2>/dev/null || true
    timeout 5 du -sh /tmp 2>/dev/null || true
    cat /proc/pressure/cpu /proc/pressure/memory /proc/pressure/io 2>/dev/null || true
    processes 2>/dev/null || true
  } 2>&1 | sed 's/^/[res] /'
}

( while true; do sample; sleep "$interval"; done ) &
sampler=$!
trap 'kill "$sampler" 2>/dev/null; wait "$sampler" 2>/dev/null' EXIT

"$@"
