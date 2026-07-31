#!/usr/bin/env python3
"""Controlled-mutation harness: break one thing on purpose, and see whether a gate says so.

§27's discipline in a script. A green from a detector never shown to fire is not evidence, so
before a gate is trusted it must be watched failing on a defect it is supposed to catch.

Run it:

    python3 documentation/mockups/mutation_harness.py             # the registered cases
    python3 documentation/mockups/mutation_harness.py --self-test # prove the harness discriminates

## Three things this file got wrong, all found in review, all of the same family

Each was a way of **reporting a verdict from a run that did not happen** — which is precisely the
failure the harness exists to detect, committed by the detector itself.

1. **It defined `case` and called nothing.** Executing it printed nothing and exited 0. A CI step
   or a session log saying "ran the mutation harness" was therefore compatible with no file having
   been mutated and no gate having been run. Fixed by `CASES` plus `main`: an invocation with no
   registered cases is now an explicit error, not a silent success.

2. **`ROOT` was the author's absolute checkout path.** Anywhere else, `case` raised at `read_text`
   or at `cwd=`. Fixed by deriving it from `__file__` — this file is two directories below the
   repository root — with `_repo_root` asserting a known landmark, so a moved file fails loudly
   instead of resolving to something plausible.

3. **The Gradle invocation was cacheable, and a reused output scores as a GATE HOLE.** `./gradlew
   <task>` returns 0 in ~400ms when the task is `UP-TO-DATE`, and the old code read exit 0 as "the
   mutation survived". So the one verdict that says *your gate is broken* could be produced by a
   gate that never ran. Measured, not reasoned: a second identical invocation of
   `:core:ui:kit:testDebugUnitTest --tests '*ContinuityMotionTest*'` reported `UP-TO-DATE` and
   `BUILD SUCCESSFUL in 381ms`. Fixed twice over — `_GRADLE_FLAGS` forces execution, and
   `_reused_outputs` re-reads the log and **refuses a verdict** if the mutated task was reused
   anyway. Belt and braces on purpose: the flag states the intent, the check proves it held.

   This is the repository's own recorded lesson (§27, "`--rerun-tasks` always; `FROM-CACHE` is not
   executed") applied to the file that exists to enforce it.

## Reverting by content snapshot, never by git

Kept from the original, because it was right and cost something to learn: the mutation is undone by
writing back the exact bytes that were read, whether or not they were ever committed. A
`git checkout -- .` revert destroyed uncommitted work five times on this arc. The harness has no
business knowing what HEAD is.
"""

from __future__ import annotations

import argparse
import pathlib
import subprocess
import sys

# --- where we are -------------------------------------------------------------------------------


def _repo_root() -> pathlib.Path:
    """This file lives at `<root>/documentation/mockups/`, so the root is two parents up.

    The landmark assertion is the point: a path derived from `__file__` that silently resolves to
    the wrong directory produces `SKIPPED — anchor matched 0 times` for every case, which reads as
    "the anchors are stale" rather than "the harness is pointed at nothing".
    """
    root = pathlib.Path(__file__).resolve().parent.parent.parent
    if not (root / "settings.gradle.kts").is_file():
        raise SystemExit(
            f"mutation_harness: derived repo root {root} has no settings.gradle.kts — "
            "this file has moved and _repo_root() needs updating",
        )
    return root


ROOT = _repo_root()

# `--rerun-tasks` defeats UP-TO-DATE, `--no-build-cache` defeats FROM-CACHE. Both are needed: they
# are different mechanisms and either one alone leaves the other path open.
_GRADLE_FLAGS = ["--rerun-tasks", "--no-build-cache"]

_REUSE_MARKERS = ("UP-TO-DATE", "FROM-CACHE")


def _reused_outputs(out: str, task: str) -> str | None:
    """Return the offending line if Gradle reported the mutated task as reused, else `None`.

    Reads the task line rather than trusting the flags, because that is the whole lesson: an
    attestation that reads only what it asked for cannot see that it did not get it.
    """
    needle = task.split()[0]
    for line in out.splitlines():
        if line.startswith("> Task ") and needle in line:
            if any(marker in line for marker in _REUSE_MARKERS):
                return line.strip()
    return None


# --- one case -----------------------------------------------------------------------------------


def case(name: str, rel: str, old: str, new: str, task: str) -> str:
    """Mutate one anchor, run one Gradle task, restore, and print a verdict. Returns the verdict."""
    p = ROOT / rel
    before = p.read_text(encoding="utf-8")
    n = before.count(old)
    if n != 1:
        verdict = f"*** SKIPPED — anchor matched {n} times, expected 1 ***"
        print(f"\n=== {name} -> {verdict}")
        sys.stdout.flush()
        return verdict

    try:
        p.write_text(before.replace(old, new), encoding="utf-8")
        r = subprocess.run(
            ["./gradlew", *task.split(), *_GRADLE_FLAGS],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        out = r.stdout + r.stderr
    finally:
        p.write_text(before, encoding="utf-8")
        assert p.read_text(encoding="utf-8") == before, f"RESTORE FAILED for {rel}"

    compile_errs = [l.strip() for l in out.splitlines() if l.startswith("e: ") or "error:" in l]
    fails = [
        l.strip()
        for l in out.splitlines()
        if "FAILED" in l and "> Task" not in l and "BUILD" not in l
    ]
    reused = _reused_outputs(out, task)

    if compile_errs:
        verdict, detail = "*** INVALID — DID NOT COMPILE, proves nothing ***", compile_errs[:4]
    elif reused:
        # Checked BEFORE the green branch, because reuse is exactly how a false GATE HOLE is born.
        verdict = "*** INVALID — TASK OUTPUT REUSED, proves nothing ***"
        detail = [reused]
    elif r.returncode != 0 and fails:
        verdict, detail = f"RED ({len(fails)} test(s))", fails[:8]
    elif r.returncode != 0:
        verdict = "RED (no named test — check)"
        detail = [l.strip() for l in out.splitlines() if "FAIL" in l or "Visual gate" in l][:6]
    else:
        verdict, detail = "*** GREEN — GATE HOLE ***", []

    print(f"\n=== {name} -> {verdict}")
    for d in detail:
        print("     ", d)
    sys.stdout.flush()
    return verdict


# --- the registered cases -------------------------------------------------------------------------

_ALPHA_SPEC = (
    "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/theme/ContinuityMotion.kt"
)

# Each entry: (name, rel, old, new, task).
CASES: list[tuple[str, str, str, str, str]] = [
    (
        "continuity alpha spec collapses back onto `out`",
        _ALPHA_SPEC,
        "    easing = motion.linear,",
        "    easing = motion.out,",
        ":core:ui:kit:testDebugUnitTest --tests *ContinuityMotionTest*",
    ),
    (
        "blank-start CTA stops withdrawing while a session runs (B27's guard)",
        "feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/"
        "mvi/store/AllTrainingsStore.kt",
        "        val showStartBlank: Boolean get() = hasActiveSession.not()",
        "        val showStartBlank: Boolean get() = true",
        ":feature:all-trainings:testDebugUnitTest --tests *StartBlankGateTest*",
    ),
]

# --- the harness proves itself ---------------------------------------------------------------------

# A harness that can only ever say RED is a rubber stamp with a scary font. It has to be shown
# producing BOTH verdicts, on demand, or neither means anything.
#
# **The first attempt at this was wrong, and the self-test is what caught it.** The plan was one
# mutation against two tasks — lengthen the alpha transit and watch `ContinuityMotionTest` go red
# while the goldens stay green, since a duration cannot move a settled endpoint. It came back RED
# for BOTH. The reason is worth recording: in this project `:<module>:verifyPaparazziDebug`
# **depends on** `:<module>:testDebugUnitTest` (Paparazzi 2.x runs goldens as unit tests; confirmed
# on the task graph). So "the visual gate is green" and "the unit gate is green" are not independent
# statements here, and no mutation can separate them by task alone.
#
# So the GREEN case uses a defect that genuinely has no detector: §10.4's own named hole, the
# toast's dismissal wiring. `ToastDurationTest` gates the number and the accessibility application;
# nothing exercises the `withTimeoutOrNull` in `App.kt` that consumes it. Telling the timeout that
# the toast has no action changes what a user experiences and is caught by nothing — which is what
# a real gate hole looks like, and why it is on the hand-check list instead of pretended otherwise.
_APP_KT = "app/app/src/main/java/io/github/stslex/workeeper/App.kt"

_SELF_TEST = [
    (
        "SELF-TEST expect RED   — alpha duration mutation vs the gate that covers it",
        _ALPHA_SPEC,
        "    durationMillis = motion.base,\n    easing = motion.linear,",
        "    durationMillis = motion.slow,\n    easing = motion.linear,",
        ":core:ui:kit:testDebugUnitTest --tests *ContinuityMotionTest*",
        "RED",
    ),
    (
        "SELF-TEST expect GREEN — toast wiring mutation, §10.4's documented hole",
        _APP_KT,
        "hasAction = model.actionLabel != null,",
        "hasAction = false,",
        ":app:app:testDebugUnitTest",
        "GREEN",
    ),
]


def _self_test() -> int:
    print("Proving the harness discriminates. One mutation, two detectors, opposite verdicts.")
    bad = 0
    for name, rel, old, new, task, expect in _SELF_TEST:
        verdict = case(name, rel, old, new, task)
        ok = expect in verdict
        print(f"      expected {expect}: {'OK' if ok else '*** HARNESS FAILED ***'}")
        if not ok:
            bad += 1
    if bad:
        print(f"\n*** {bad} self-test case(s) wrong — do not trust any verdict from this run ***")
    else:
        print("\nHarness discriminates: it can say RED and it can say GREEN, on demand.")
    sys.stdout.flush()
    return 1 if bad else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Controlled-mutation harness (§27).")
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="run the two calibration cases instead of the registered ones",
    )
    args = parser.parse_args()

    if args.self_test:
        return _self_test()

    if not CASES:
        # An empty run is the original defect. It exits non-zero rather than looking like success.
        raise SystemExit(
            "mutation_harness: no cases registered — an invocation that mutates nothing must not "
            "be mistakable for evidence. Add entries to CASES.",
        )

    print(f"Repo root: {ROOT}")
    for entry in CASES:
        case(*entry)
    return 0


if __name__ == "__main__":
    sys.exit(main())
