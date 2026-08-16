# The instrumented suite selector becomes a gate

A carry-over fix from KMP phase 3, done before phase 4 so the navigation oracle is trustworthy
while the composition root moves.

---

## 1. One hole, two opposite signs

`ui_tests.yml` selects its smoke and regression suites by passing
`-e annotation io.github.stslex.workeeper.core.ui.test.annotations.{Smoke,Regression}` to the
instrumentation runner. Two different mistakes break that selection, in opposite directions, and
**neither produces a failing run**.

| sign | precondition | what happens | visible symptom |
|---|---|---|---|
| **Over-inclusion** | annotation class NOT on the test APK's classpath | androidx.test's `TestRequestBuilder` silently DROPS the filter and runs everything | none — extra runtime, mislabeled coverage |
| **Under-inclusion (a vanish)** | annotation class IS on the classpath, test carries no annotation | the filter applies and excludes the test from *both* suites | **none at all** — the test never runs |

Phase 3 found and registered the first. The second was found while fixing it, and had never been
noticed, because the only evidence of a test that never runs is a number nobody had reason to
distrust.

### 1.1 The state of the repo when this started

| module | test-utils on `debugAndroidTestRuntimeClasspath` | suite annotation | effect |
|---|---|---|---|
| `core/ui/kit` | ✗ | none on 5 `@Test` | ran in BOTH suites |
| `feature/app-dialogs/impl` | ✗ | none on 10 `@Test` | ran in BOTH suites |
| `core/ui/mvi` | ✓ | none on 1 `@Test` | ran in **NEITHER** suite |

The third was proven by arithmetic before it was proven by a gate. The pre-fix `@Regression` suite
collected **79**; the annotated set is **64** (app:app 35 + core:data:database 28 +
all-exercises 1 method-level); the two unfiltered modules contribute **15**. 64 + 15 = 79 exactly,
which leaves no room for `core:ui:mvi`'s test in either total.

What that test asserts is not incidental: `AppFeatureScopeTest` pins that a root-mounted
`AppFeature` resolves its Store at the host Activity's `ViewModelStore` — the scope invariant
`StoreRetentionTest`'s isolation mutation is built on. It was unverified in CI for its whole life.

---

## 2. The gate

Two tasks, registered for **every** module by `LintConventionPlugin` — the one hook every convention
plugin applies. Not opted into per module: an opt-in gate is a convention, and forgetting to opt in
is exactly how the defect arrived.

### `detektAndroidTestSuite` — the coverage half

A detekt task over `src/androidTest/{kotlin,java}` running one rule,
`InstrumentedSuiteSelectorRule`, from `lint-rules/detekt-androidtest-suite.yml`
(`buildUponDefaultConfig = false`, so nothing else is active). Every `@Test` must be reachable by
some selector: it, or its declaring class, must carry `@Smoke` or `@Regression`.

It needs its own task because **the plain `detekt` task cannot see this source set** — its source
resolves to `src/main` + `src/test` only, and probing `:app:app` reports 0 files under
`src/androidTest`. The same reason `:app:app`'s pre-existing `detektAndroidTestNavigation` exists;
this follows that precedent, and the rule is `active: false` in the shared `detekt.yml` because
`src/test` unit tests take no suite annotation.

**The import is load-bearing.** The rule runs without type resolution, so it matches by name. A
locally declared `annotation class Smoke` would satisfy a naive name check while leaving the real
filter unsatisfiable — the same false green in a new costume. Coverage is credited only when the
name is bound to `io.github.stslex.workeeper.core.ui.test.annotations` by an import (alias included)
or written fully qualified. Class-level and method-level annotations both count, and both are used
in the repo: `AllExercisesScreenTest` is `@Smoke` at the class with one method also `@Regression`.

### `verifyInstrumentedSuiteClasspath` — the resolvability half

Asserts the annotation class files are present on `debugAndroidTestRuntimeClasspath`, resolved
through an artifact view asking for `android-classes-jar`. It is a **classpath** assertion rather
than a dependency-declaration one on purpose: "does `:core:ui:test-utils` appear in the dependency
block" is a proxy that a `compileOnly` declaration, a configuration rename, or moving the
annotations elsewhere would each quietly falsify. What the runner does is look the class up in the
APK's classloader; what this task does is look the class file up on the classpath that APK is built
from.

It hangs off **`assembleDebugAndroidTest`**, which already resolves that configuration — so it adds
resolution work to no build that was not doing it anyway, runs in CI, and precedes every local
`connectedDebugAndroidTest`. Hanging it off `detekt` instead would make every commit resolve every
module's androidTest dependency graph to learn nothing new.

### Why both

`core:ui:mvi` **passed** the classpath check and **failed** the detekt check. `core/ui/kit` and
`feature/app-dialogs/impl` failed both. Neither half subsumes the other — that is measured, not
argued.

---

## 3. Evidence

All gates run with `--rerun-tasks --no-build-cache --no-configuration-cache`; detekt invoked
separately from tests.

**Red before green.** On the unfixed tree, `detektAndroidTestSuite` reported 5 findings in
`core/ui/kit`, 10 in `feature/app-dialogs/impl`, 1 in `core/ui/mvi`, and passed `feature/settings` —
the exact per-file counts predicted from the source inventory.
`verifyInstrumentedSuiteClasspath` reported 2 missing of 192 scanned in `core/ui/kit` and 2 of 196
in `feature/app-dialogs/impl`, 0 missing in `core:ui:mvi` and `feature:settings`.

**Mutation, on a module that was never touched by the fix** (`feature:settings`, named, reverted,
never committed):

| mutation | gate | result |
|---|---|---|
| M-1: strip class-level `@Smoke` | `detektAndroidTestSuite` | RED, 2 findings |
| M-2: strip the test-utils edge | `verifyInstrumentedSuiteClasspath` | RED, 2 missing of 210 scanned |

**Coverage, with input counts** (§4's rule: a task over zero inputs is green vacuously).
`verifyInstrumentedSuiteClasspath` runs in all 35 modules; 13 report instrumented sources —
`app:app` 17 files, `core:data:database` 5, `core:ui:mvi` 2, `feature:exercise` 2, nine others 1 —
and 22 report 0 and say so. The 13 match an independent `find` of `src/androidTest` directories.

`:lint-rules:test` — 12 tests for `InstrumentedSuiteSelectorRule`, 0 skipped, 0 failures. Repo-wide
`detekt` green, 55/55 executed. `assembleDebugAndroidTest` green, 1873/1873 executed — which also
proves the new `core:ui:kit` → `core:ui:test-utils` androidTest edge is not a dependency cycle
(`test-utils` depends back on `kit`'s main source set; androidTest is a separate compilation, and
`core:ui:mvi` has carried the identical shape since the app-scope collapse).

### 3.1 The gate caught a bug in itself

On its first run `verifyInstrumentedSuiteClasspath` reported `0 instrumented source files` for
modules that plainly had them, and passed. A `ConfigurableFileCollection` built `from` a directory
yields the directory, not its contents, so the task's own `isFile` filter discarded everything — a
gate that was green because it inspected nothing, which is the precise failure class it was written
to catch. It was caught only because the task prints its input count. That is why the line is a
`fileTree` today, and why §4's "report the input file count" rule should apply to the gate as much
as to the thing gated.

---

## 4. Result: the pin moved from 79 to 64

Full `@Regression` suite on `nav_regression_api34` (API 34, arm64), post-fix:
**BUILD SUCCESSFUL in 12m 12s, 1908 actionable tasks: 1908 executed. 64 tests, 0 failures**, across
13 reporting modules (app:app 35, core:data:database 28, all-exercises 1, ten modules 0).

**The pinned failure list is unchanged** — the triage rule pins *zero expected failures*, and it is
still zero. What changed is the count the suite is described by. Quote **64** against this baseline,
not 79.

Every prior claim resting on 79 was measuring 64 annotated tests plus 15 that androidx.test swept in
because it could not load the annotation class. The phase-3 spec that recorded 79 had already named
the bypass and given the 35/28/1 composition separately, so its conclusion (all passed) is
unaffected and its arithmetic is what made this re-measurement checkable; it now carries a
supersession note. A module selecting 0 tests does not fail the run — measured, `Starting 0 tests`
passes — so removing 15 over-included tests from the regression suite could not and did not red it.
