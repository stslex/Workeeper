#!/usr/bin/env python3
"""Controlled-mutation harness: break one thing on purpose, and see whether a gate says so.

§27's discipline in a script. A green from a detector never shown to fire is not evidence, so
before a gate is trusted it must be watched failing on a defect it is supposed to catch.

Run it:

    python3 documentation/mockups/mutation_harness.py             # the registered cases
    python3 documentation/mockups/mutation_harness.py --self-test # prove the harness discriminates

    # ONE-SHOT: the ad-hoc mutation you would otherwise have done by hand
    python3 documentation/mockups/mutation_harness.py \
        --file feature/home/src/.../PagingTailKind.kt \
        --find 'is LoadState.Error -> PagingTailKind.ERROR' \
        --replace 'is LoadState.Error -> PagingTailKind.NONE' \
        --task ':feature:home:verifyPaparazziDebug' \
        --expect RED

## Why one-shot mode exists, and it is not convenience

The registered-`CASES` form is for mutations worth keeping. Proving a *new* gate during a PR is a
different job: five or six mutations, each used once and discarded. Editing `CASES` for those is
more work than `sed -i` followed by `git checkout --`, so **the destructive path was the cheap
path**, and it won — six times, the last of them in a session that had read the rule forbidding it
(see AGENTS.md). A discipline that is more expensive than the thing it forbids is not a
countermeasure, it is a preference.

So the fix is not another warning. It is that `--file/--find/--replace/--task` costs *less* than the
unsafe sequence: one command instead of three, restore guaranteed by `finally`, and the anchor
checked for exactly one match — which `sed -i` silently declines to do. `--expect` makes a shell
loop of mutations fail loudly on the first surprise instead of scrolling past it.

**Nothing in this file knows what HEAD is, in either mode.** That is the property, and it is why
neither mode can lose uncommitted work.

4. **A task name that does not resolve scored as RED.** Gradle exits non-zero when the task does
   not exist; the verdict logic read any non-zero exit with no named test as
   `RED (no named test — check)`, so `--expect RED` reported "OK" for a run in which NOTHING RAN.
   Witnessed rather than imagined: `--task ":$T:testDebugUnitTest"` in **zsh** expands `$T:t` as
   the tail modifier, producing `:feature:exerciseestDebugUnitTest`, and two mutation proofs came
   back green-for-red against a task that does not exist. Same family as (3) — a verdict from a
   run that did not measure what it appears to. Fixed by `_NO_SUCH_TASK_MARKERS`, checked before
   everything else; write `":${T}:task"` and let the harness catch it when you forget.

## The one verdict this harness cannot check for you

`INVALID` covers two ways a run fails to measure what it appears to: the mutation did not compile,
or Gradle reused the task's output. There is a **third**, and it compiles and executes cleanly —
**the mutation changed no observable.** Renaming a role to its own alias, swapping a constant for a
second constant holding the same bytes: the two programs are identical, so the gate is not at fault
for staying green, and `*** GREEN — GATE HOLE ***` is then an accusation against the wrong party.

A green mutation accuses one of two opposite things and only the reader can tell which. Mutate a
**behaviour** (a predicate, a branch, a duration, a value) and green means the suite is blind.
Mutate a **name** and green means nothing happened. **Before believing a GATE HOLE verdict, name the
observable the mutation changed** — if that is hard to do, the difficulty is the finding. §27 carries
the rule and the witness that produced it (`textTertiary` → `textDim`, which is a rename because
both resolve to `*_META`).

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

# Gradle's own wording when a task name does not resolve. Matched on the message rather than on the
# exit code, because the exit code is the very thing that made this look like a red.
_NO_SUCH_TASK_MARKERS = (
    "Cannot locate tasks that match",
    "not found in root project",
    "Project directory",
)

# Mutating a file under lint-rules/ changes the RULE JAR, and the Gradle daemon serves whichever
# jar it loaded first — so detekt judges the edit with stale bytecode and the mutation appears to
# have no effect. `--rerun-tasks` does not defeat this and neither does `--no-build-cache`; only
# stopping the daemon does.
#
# Measured, and it is why this exists: removing `PagingCollectionRule`'s kit-helper exclusion — a
# mutation that unquestionably changes the rule's behaviour — came back
# `*** GREEN — GATE HOLE ***`. Run by hand with `--stop` first, the same mutation reddens
# `:core:ui:kit:detekt` immediately. So the harness's most alarming verdict was produced by a rule
# that never ran, which is the third member of the family it already refuses twice (a mutation that
# did not compile; a task whose output was reused).
#
# Always stopping is a few seconds on every case and removes a whole class of false verdict; the
# alternative — stopping only for `lint-rules/` paths — needs the harness to know which edits are
# rule edits, which is exactly the kind of cleverness that fails quietly.
_RULE_JAR_PREFIX = "lint-rules/"


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
        # See _RULE_JAR_PREFIX: the daemon caches the loaded rule jar, so without this a rule
        # mutation is judged against stale bytecode and scores as a GATE HOLE.
        subprocess.run(["./gradlew", "--stop"], cwd=ROOT, capture_output=True, text=True)
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
    not_found = [l.strip() for l in out.splitlines() if any(m in l for m in _NO_SUCH_TASK_MARKERS)]
    fails = [
        l.strip()
        for l in out.splitlines()
        if "FAILED" in l and "> Task" not in l and "BUILD" not in l
    ]
    reused = _reused_outputs(out, task)

    if not_found:
        # CHECKED FIRST, ahead of every other verdict: Gradle exits non-zero when a task name does
        # not resolve, so a nothing-ran build is indistinguishable from a detector firing by exit
        # code alone. A member of the INVALID family for that reason (§27).
        #
        # The trap this catches most often is a shell one: in **zsh** `":$T:testDebugUnitTest"`
        # expands `$T:t` as the tail history modifier, yielding `:feature:exerciseestDebugUnitTest`.
        # Write `":${T}:testDebugUnitTest"` — and rely on this check rather than on remembering to.
        verdict = "*** INVALID — NO SUCH GRADLE TASK, nothing ran ***"
        detail = not_found[:2]
    elif compile_errs:
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
_APP_KT = "app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt"

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
        ":app:common:testDebugUnitTest",
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


def _one_shot(args: argparse.Namespace) -> int:
    """One ad-hoc mutation, verdict printed, optionally asserted against `--expect`.

    The exit code is the point when `--expect` is given: a `for` loop over five mutations in a
    shell script stops at the first surprise instead of printing it and carrying on.
    """
    missing = [
        flag
        for flag, value in (
            ("--find", args.find),
            ("--replace", args.replace),
            ("--task", args.task),
        )
        if value is None
    ]
    if missing:
        raise SystemExit(f"mutation_harness: --file needs {', '.join(missing)} as well")

    name = args.name or f"one-shot: {args.file}"
    verdict = case(name, args.file, args.find, args.replace, args.task)

    if "SKIPPED" in verdict:
        # An anchor that matched 0 or 2+ times mutated nothing. `sed -i` would have reported
        # success here, which is how a "green" arrives from a run that changed no bytes.
        print("      the tree is untouched — nothing was measured")
        return 2
    if args.expect is None:
        return 0

    ok = args.expect in verdict
    print(f"      expected {args.expect}: {'OK' if ok else '*** UNEXPECTED VERDICT ***'}")
    return 0 if ok else 1


def main() -> int:
    parser = argparse.ArgumentParser(description="Controlled-mutation harness (§27).")
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="run the two calibration cases instead of the registered ones",
    )
    parser.add_argument("--file", help="one-shot: repo-relative path to mutate")
    parser.add_argument("--find", help="one-shot: anchor, must match exactly once")
    parser.add_argument("--replace", help="one-shot: what to put in its place")
    parser.add_argument("--task", help="one-shot: gradle task and args, e.g. ':m:test --tests *X*'")
    parser.add_argument("--name", help="one-shot: label for the verdict line")
    parser.add_argument(
        "--expect",
        choices=["RED", "GREEN", "INVALID"],
        help="one-shot: exit non-zero unless the verdict contains this",
    )
    args = parser.parse_args()

    if args.self_test:
        return _self_test()

    if args.file:
        return _one_shot(args)

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
