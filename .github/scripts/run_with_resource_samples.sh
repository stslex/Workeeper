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

sample() {
  {
    echo "--- $(date -u +%FT%TZ) $(uptime 2>/dev/null || true)"
    free -m 2>/dev/null || true
    vmstat 1 2 2>/dev/null | tail -1 || true
    df -h / 2>/dev/null || true
    timeout 5 du -sh /tmp 2>/dev/null || true
    cat /proc/pressure/cpu /proc/pressure/memory /proc/pressure/io 2>/dev/null || true
    ps -eo pid,rss,etimes,args --sort=-rss 2>/dev/null | head -9 | cut -c1-160 || true
  } 2>&1 | sed 's/^/[res] /'
}

( while true; do sample; sleep "$interval"; done ) &
sampler=$!
trap 'kill "$sampler" 2>/dev/null; wait "$sampler" 2>/dev/null' EXIT

"$@"
