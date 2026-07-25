#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
#
# Build-time re-baseline for the graph-extension arc. ONE command, run it in a FRESH session:
#
#     sh documentation/graph-extension-arc/measure-build-time.sh
#
# WHY THIS EXISTS. The arc's first build-time table compared 3-run medians and read a 0.3s "step"
# between N=3 and N=4. Measuring N=6 at n=9 dissolved it: median 1.155s (LOWER than N=5's 1.204s),
# range 0.959-1.735s, and the first three of those nine would have reported 1.273s. The noise band was
# the size of the effect. Nothing about extension count is currently established, in either direction.
#
# WHAT IT DOES DIFFERENTLY.
#   1. PILOT FIRST, then DERIVES n from the observed variance. n is not assigned — assigning 9 after
#      being wrong with 3 just risks being under-powered twice, and the observed spread (0.78s) may
#      well need more than 9.
#   2. Measures N by COUNTING extensions at each checkout instead of trusting the commit label.
#   3. Reports distributions (median / min / max / spread / n), never a bare median. A point median is
#      not evidence; do not compare two of them and call the difference a trend.
#   4. Verifies each run actually EXECUTED. An UP-TO-DATE or FROM-CACHE task measures nothing and is a
#      silent false green — the arc's recurring failure class (see STANDING RULE 5).
#   5. Merges stderr. Kotlin/Gradle diagnostics go there; capturing stdout alone reports success on a
#      failed build.
#
# HISTORICAL POINTS come from read-only `git worktree` checkouts of the arc's own SHAs, so no branch is
# created, moved, rebased, or pushed. Worktrees are removed on exit.
#
# COVERAGE: N = 1..7 are all committed and wired into SERIES below, so one run produces the whole
# series. Append a line per future port; nothing else needs changing.
#
# WHAT DECIDES THE BATCH. Flat across N=4..7 means the feature-4 step was an artifact and the
# 13-extension endpoint is safe, so the remaining assisted features can be batched. Rising means STOP:
# name the mechanism in :app's merged-graph codegen that costs the time BEFORE extrapolating to 13.
# Extrapolating an unnamed cause is numeric guessing — "associated with N" is not a mechanism.

set -u

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO" || exit 1

OUT="${TMPDIR:-/tmp}/arc-build-time-$$"
mkdir -p "$OUT"
WORKTREES=""

cleanup() {
    for w in $WORKTREES; do
        git worktree remove --force "$w" >/dev/null 2>&1
    done
    git worktree prune >/dev/null 2>&1
}
trap cleanup EXIT INT TERM

# --- series to measure: "<label>|<sha-or-WORKING_TREE>" ---------------------------------------------
# Every SHA below is a PORT commit, so the series varies one thing: extension count. The count at each
# SHA was verified with `git grep -l "@GraphExtension(" <sha> -- feature/` and is re-verified at run
# time by count_extensions(); if a label and its measured count ever disagree, believe the count.
#
# N=7 appears TWICE on purpose. ff1299b1 is the plan-editor port with the four dead XxxDeps interfaces
# still on AppGraph; 8be3bde0 deletes them. Same extension count, different AppGraph supertype list —
# a free control for whether the bridge's residue affects the number at all. If those two rows differ
# materially, the bridge state is a confound in every other row and the series needs re-cutting.
#
# WORKING_TREE measures the current checkout in place (uncommitted work, if any).
SERIES="all-trainings|9f17d02a
archive|4c184e5e
image-viewer|4c7a1a67
settings|d784a510
home|02e90d81
all-exercises|b3272960
plan-editor|ff1299b1
plan-editor-postbridge|8be3bde0
current|WORKING_TREE"

PILOT_RUNS=5
MIN_N=9
MAX_N=40
# Smallest difference worth believing, in seconds. n is derived so the median's standard error is
# comfortably under half of it.
TARGET_SE=0.05

# --------------------------------------------------------------------------------------------------

# Clear every build dir. STANDING RULE 1: stale app/app/build makes AppGraph$Impl miss contributed
# factories and fails at RUNTIME, not compile time.
wipe_builds() {
    find "$1" -maxdepth 4 -type d -name build -not -path "*/.git/*" -exec rm -rf {} + 2>/dev/null
}

# Count contributed extensions actually present, rather than trusting the label.
count_extensions() {
    grep -rl "@GraphExtension(" "$1/feature" --include="*.kt" 2>/dev/null | wc -l | tr -d ' '
}

# One measured run. Echoes the task-execution seconds, or nothing if the run was not a real execution.
one_run() {
    dir="$1"
    rm -rf "$dir/app/app/build"
    ( cd "$dir" && ./gradlew :app:app:compileDebugKotlin --no-build-cache --profile --console=plain ) \
        > "$OUT/run.log" 2>&1

    if ! grep -qE '^> Task :app:app:compileDebugKotlin$' "$OUT/run.log"; then
        echo "SKIP: task did not EXECUTE (UP-TO-DATE / FROM-CACHE / failed)" >&2
        return 1
    fi
    if grep -q 'FROM-CACHE' "$OUT/run.log"; then
        echo "SKIP: FROM-CACHE present" >&2
        return 1
    fi

    python3 - "$dir" <<'PY'
import glob, os, re, sys
reports = sorted(glob.glob(os.path.join(sys.argv[1], 'build/reports/profile/profile-*.html')),
                 key=os.path.getmtime)
if not reports:
    sys.exit(1)
html = open(reports[-1]).read()
m = re.search(r'>Task Execution</td>\s*<td[^>]*>([0-9.]+)s</td>', html)
if not m:
    sys.exit(1)
print(m.group(1))
PY
}

# Collect n samples into a file.
collect() {
    dir="$1"; n="$2"; dest="$3"
    : > "$dest"
    i=1
    while [ "$i" -le "$n" ]; do
        v="$(one_run "$dir")" && [ -n "$v" ] && echo "$v" >> "$dest"
        printf '  sample %s/%s: %s\n' "$i" "$n" "${v:-SKIPPED}"
        i=$((i + 1))
    done
}

stats() {
    python3 - "$1" "$2" <<'PY'
import statistics as st, sys
label = sys.argv[1]
vals = [float(x) for x in open(sys.argv[2]) if x.strip()]
if not vals:
    print(f"{label}: NO VALID SAMPLES"); raise SystemExit
sd = st.stdev(vals) if len(vals) > 1 else 0.0
print(f"{label}: n={len(vals)} median={st.median(vals):.3f} min={min(vals):.3f} "
      f"max={max(vals):.3f} spread={max(vals)-min(vals):.3f} sd={sd:.3f}")
PY
}

echo "=============================================================="
echo " Arc build-time re-baseline — PILOT"
echo "=============================================================="
wipe_builds "$REPO"
echo "warming dependencies (discarded)…"
./gradlew :app:app:compileDebugKotlin --no-build-cache --console=plain >/dev/null 2>&1

echo "pilot: $PILOT_RUNS runs on the working tree"
collect "$REPO" "$PILOT_RUNS" "$OUT/pilot.txt"
stats "pilot" "$OUT/pilot.txt"

N_DERIVED="$(python3 - "$OUT/pilot.txt" "$TARGET_SE" "$MIN_N" "$MAX_N" <<'PY'
import math, statistics as st, sys
vals = [float(x) for x in open(sys.argv[1]) if x.strip()]
target_se, lo, hi = float(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
if len(vals) < 2:
    print(lo); raise SystemExit
sd = st.stdev(vals)
# SE of the median ~= 1.253 * sd / sqrt(n)  ->  n = (1.253 * sd / target_se)^2
n = math.ceil((1.253 * sd / target_se) ** 2)
print(max(lo, min(hi, n)))
PY
)"

echo
echo "derived n = $N_DERIVED  (target SE ${TARGET_SE}s, floor $MIN_N, cap $MAX_N)"
echo "  if this hit the cap, the variance is too high to resolve ${TARGET_SE}s —"
echo "  say so in the HANDOFF rather than reporting a median as if it settled anything."
echo

echo "=============================================================="
echo " Measuring series"
echo "=============================================================="
RESULTS="$OUT/results.txt"
: > "$RESULTS"

echo "$SERIES" | while IFS='|' read -r label ref; do
    [ -z "$label" ] && continue
    if [ "$ref" = "WORKING_TREE" ]; then
        dir="$REPO"
    else
        dir="$OUT/wt-$label"
        if ! git worktree add --detach "$dir" "$ref" >/dev/null 2>&1; then
            echo "!! could not create worktree for $label ($ref) — SKIPPED" | tee -a "$RESULTS"
            continue
        fi
        WORKTREES="$WORKTREES $dir"
        wipe_builds "$dir"
    fi

    ext="$(count_extensions "$dir")"
    echo
    echo "--- $label ($ref) : $ext contributed extensions found ---"
    collect "$dir" "$N_DERIVED" "$OUT/$label.txt"
    stats "N=$ext $label" "$OUT/$label.txt" | tee -a "$RESULTS"
done

echo
echo "=============================================================="
echo " SUMMARY  (task-execution seconds)"
echo "=============================================================="
cat "$RESULTS"
echo
echo "Reading it:"
echo "  * Compare RANGES, not medians. If two rows' ranges overlap, N is not resolved between them."
echo "  * A LOWER median at a HIGHER N means N is not the driver at this resolution."
echo "  * Only if ranges separate cleanly and monotonically is there a slope to extrapolate — and even"
echo "    then, name the mechanism in :app's merged-graph codegen before projecting to 13 extensions."
echo
echo "raw samples: $OUT"
