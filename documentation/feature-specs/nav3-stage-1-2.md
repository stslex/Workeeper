# Stage 1.2 — API contracts (still Nav2 underneath)

**Arc:** Navigation 3 migration, stage 2 of 3. One PR, one CC session.
**Branch:** `feature/nav-api-contracts`, cut from `dev` after #221 merges.
**Predecessor:** stage 1.1 (#221) — the behavioural oracle.
**Successor:** stage 1.3 — the atomic Nav2 → Nav3 swap.

---

## 1. Purpose

Move every navigation-library-shaped construct behind project-owned API, while
Nav2 is still the implementation. Each change lands as its own bisectable
commit. By the time 1.3 runs, the diff is the DSL implementation, the host, and
the retention wiring — not 12 call sites.

**Exit criterion, and the whole point: the 1.1 oracle stays green with zero
edits.** Four classes, `RouteReachabilityTest` at 15/15 — though only
`RouteReachabilityTest` existed at #221; see §6 A's post-merge note. If a test
has to change to accommodate this stage, the contract changed behaviour and the
change is wrong.

The Nav2 → Nav3 swap is atomic — two navigation systems cannot drive one host,
so 1.3 has no internal bisect. Everything movable out of that step moves here.

---

## 2. Locked decisions

### 2.1 Typed results on the destination

`popBack(vararg previousStackAttr: Pair<String, Any?>)` is `savedStateHandle`'s
shape — string keys, `Any?` values, a default for absence. That transport does
not exist in Nav3.

**Replace with: the result type declared on the destination.**

- A marker — `ScreenWithResult<T>` — carried only by destinations that actually
  produce a result. Today exactly two: `Screen.PlanEditor` (`Boolean`) and the
  image-request result currently keyed as `exerciseImageRequestAttr` (`String`).
  The other ten destinations stay plain `Screen` and gain nothing.
- Returning with a result is a typed command; the type comes from the
  destination, not from the call site.
- **Reading is nullable. `null` means "no result".** No `Cancelled` case, no
  sealed wrapper. Measured justification: today `planEditorSavedAttr` defaults to
  `false` and `exerciseImageRequestAttr` to `null` — "did not save" and "pressed
  back" are already the same state, and no consumer distinguishes them. A sealed
  result would split what nothing reads apart and add branching to ten sites for
  zero information.
- `SaveHandlerAttr` is deleted, not renamed. String keys and `Any?` disappear
  entirely; the type lives on `Screen`.

Why on the destination rather than on the call: live-workout is the only producer
of `Screen.PlanEditor` and the only consumer of its result — one
`PlanEditor.Existing(` construction site in production, in live-workout's
`NavigationHandler`. The pre-1.2 prose named three navigators into the route and
went uncorrected for months; only a grep could check it. With the type declared
on the destination, a wrong-typed read does not compile, and "who reads this"
becomes a question for the compiler instead of a comment.

### 2.2 Project-owned registration DSL

`navComponentScreen` / `navComponentScreenWithState` (4 overloads in
`core:ui:mvi`) currently extend `NavGraphBuilder`. Under Nav3 the receiver
becomes `EntryProviderBuilder`.

Introduce a project-owned builder so the **12 call sites** never name either
receiver. In 1.3 the DSL is re-pointed at `entryProvider` and the call sites do
not move.

**Correction, measured in Phase 1.** The 12 are **not** 12 uniform helper
calls: 11 are `navComponentScreen*` invocations, and the 12th is
`PlanEditorGraph.kt:30`, which calls `navScreen<Screen.PlanEditor.Existing>`
directly and builds its processor by hand inside the content composable. Commit
5 covers **both shapes** — the direct call moves onto the DSL too rather than
being exempted, because §6 B counts its `NavGraphBuilder` receiver like any
other. It moves cleanly: the hand-built `PlanEditorFeature.processor(screen)` is
exactly what the `FeatureAssisted` overload already does.

### 2.3 `AnimatedContentScope` leaves the content-lambda signature

Today the content lambda's receiver is Nav2's `AnimatedContentScope`, supplied
by `composable {}`'s scope. Under Nav3 it arrives via
`LocalNavAnimatedContentScope`. Remove it from the signature.

**Correction, measured in Phase 1 (2026-08-14).** This section originally
claimed "7 shared-element call sites" consume the scope. **There are zero.**
The figure came from a grep on
`SharedTransitionScope|SharedTransitionLayout|AnimatedContentScope|sharedElement`
— that counts *type mentions*, and every hit was a declaration, not motion
code. Repo-wide there is not one `sharedElement(`, `sharedBounds(`,
`animatedEnterExit(`, or `rememberSharedContentState(` call in production
source. No content lambda anywhere reads its `AnimatedContentScope` receiver.

Three consequences:

- **No accessor is needed, and none is added.** An accessor exists to serve
  call sites; there are none to serve. Adding one now would ship an untested
  API on a speculative need, and 1.3 would have to keep it working. When a
  shared-element transition is actually written, that change introduces the
  accessor and its first consumer together.
- **The CompositionLocal exception is therefore not taken.** §4 commit 7 still
  records the reasoning in `documentation/architecture.md` — the rule targets
  `Navigator`, the animation scope is not the navigator, and Nav3 delivers it
  via `LocalNavAnimatedContentScope` by design — so 1.3 can act on it without
  re-deriving it. It is recorded as a *decision for when it is needed*, not as
  an exception now in force.
- **Commit 6 is subtractive only:** the unused receiver leaves four
  `core:ui:mvi` overloads and two `core:ui:navigation` primitives, and the five
  unused `sharedTransitionScope: SharedTransitionScope` parameters go with it.
  No call site updates.

Side win: deleting `LiveWorkoutGraph`'s `@Suppress("UnusedParameter")` — the
suppression existed only to silence one of those five parameters.

The Paparazzi warning in §6 D is defused at its root: a scope nothing renders
from cannot move a golden. The run still happens and the count is still
reported; a moved golden would mean something other than this stage moved it.

### 2.4 Result consumption moves into the Store

`ExerciseGraph.kt:65` reads the image-request result inline in the graph
composable via `.getStateFlow`. It moves behind the new contract **and into the
Store as an `Action`** — not merely re-pointed at the new API.

Rationale: the project's canonical pattern is that graph composables consume
only UI events (haptics, external links, back-handler triggers). A result read
is state, and state belongs in the Store. This is the one place in 1.2 that
touches a feature rather than only the contract; it is in scope because leaving
it means 1.3 has to move it under the atomic step.

`LiveWorkoutGraph.kt:29` (`planEditorSavedAttr`) gets the same treatment.

---

## 3. Out of scope

- Any Nav3 code or dependency. Nav2 remains the implementation throughout.
- `Navigator.restartApp()` / `openRecovery()` — the two impure members. They are
  Android-only and belong to the CMP phase, not here.
- The a11y fix to `AppNavBar` and the `checkAppClosed()` race — own PR, before 1.3.
- The three remaining DataStore stragglers — each needs a new module edge.
- `AllTrainingsItemRow_<uuid>` tag move — see §5.
- `ui_tests.yml`, build-logic.

---

## 4. Commit plan

Each commit compiles, passes the full gate independently, and leaves the 1.1
oracle green.

1. `ScreenWithResult<T>` + the typed return command; `SaveHandlerAttr` still
   present, unused.
2. Migrate `planEditorSavedAttr` to the typed contract; consumption into the
   `LiveWorkout` Store.
3. Migrate the image-request result; consumption out of `ExerciseGraph` and into
   the `Exercise` Store.
4. Delete `SaveHandlerAttr` and the `Pair<String, Any?>` parameter of `popBack`.
   Nothing may reference them by now — that is the proof step 1–3 were complete.

   **Correction, measured in Phase 1.** `Navigator` declares exactly *one*
   `popBack`, not two: `popBack(vararg previousStackAttr: Pair<String, Any?>)`.
   There is no overload to delete — the commit removes the parameter, leaving
   `popBack()`. The proof property is unchanged and is stated as a grep:
   after this commit **neither `Pair<String, Any?>` nor `SaveHandlerAttr`
   appears anywhere in the repo.** Both greps are pasted, empty, in the PR body.

   `SavedStateHandleNavigationResultTest` is the one artifact that tests the
   deleted transport directly. It **migrates rather than dies**: it is the only
   characterization of result transport, and at 1.3 it becomes the witness that
   the typed contract survived a transport swap. It is rewritten to assert the
   *contract* — produce a result through the new API, read it through the new
   API, assert the value — and renamed. If it still named `SavedStateHandle`
   afterwards it would die at 1.3 with the mechanism, which is the whole reason
   for keeping it.
5. Project-owned registration DSL; the 12 call sites move to it — **both
   shapes**, see §2.2.
6. `AnimatedContentScope` out of the signature. No accessor, no call-site
   updates, and the five unused `sharedTransitionScope` parameters go too —
   see §2.3.
7. `documentation/architecture.md` — the CompositionLocal decision, recorded
   for 1.3 rather than taken now (§2.3). **`AGENTS.md` and
   `.claude/skills/refactor-with-mvi-rules.md` are updated in the same commit**:
   both currently document `setAttrDefaultValue` and the in-graph
   `SavedStateHandle` read as the canonical pattern. A skill file that teaches
   a deleted pattern is worse than no skill file — the next session follows it.

---

## 5. Optional rider — decide before starting

`AllTrainingsItemName_<uuid>` / `AllTrainingsItemMeta_<uuid>` sit on a child
`Text` inside a merged row, so they are unreachable to `onNodeWithTag` while the
click action lives on the untagged parent. Filed in tech-debt from 1.1; the fix
is `AllTrainingsItemRow_<uuid>` on the `rowModifier` chain, and **exactly one
call site changes** (`NavPaths.openTraining`).

Including it here means editing the 1.1 oracle, which contradicts §1's exit
criterion. Two options:

- **(a)** leave it filed; the oracle keeps the name selector and the debt
  survives into 1.3. Recommended — the exit criterion is worth more than the
  cleanup.
- **(b)** take it as the **final** commit, after the exit criterion has been
  demonstrated on the untouched oracle, with the one-line test change called out
  explicitly in the PR body.

Ilya decides. Default is (a).

> **Settled: (a).** The oracle kept the name selector, no `AllTrainingsItemRow_<uuid>`
> tag was added, and the tech-debt entry stayed open with its before-1.3 deadline.

---

## 6. Exit criteria

All four required.

**A — the 1.1 oracle green, unedited.** `RouteReachabilityTest` 15/15 plus the
other three stage-1.1 classes. `git diff` against #221's HEAD shows **zero**
changes under `app/app/src/androidTest` — except under option (b), where exactly
one line in `NavPaths` changes and is named in the PR body.

> **As merged, both sentences overstated what existed.** The oracle #221 shipped
> was `RouteReachabilityTest` alone — `StoreRetentionTest` and
> `BackStackStateRestorationTest` landed later, in #224, and `NavigationResultTest`
> is this stage's own addition. And the diff was not zero: the merged stage
> **added** `NavigationResultTest.kt` under that path, extended `NavPaths`, and
> the DSL sweep touched the two named-exclusion scaffolding tests
> (`NavGraphScope(this)` wrapping). The unedited-oracle property held for the
> class that existed: `RouteReachabilityTest` is byte-identical to #221's.

**B — no `androidx.navigation` import outside `core:ui:navigation` and
`core:ui:mvi`.** After this stage the library is confined to its two
implementation modules; features and `:app:app` reach it only through project
API. Evidence: grep output, enumerated. This is the measurable definition of
"the contract hides the library", and it is what makes 1.3 small.

**Measured baseline (Phase 1, at `dev`): 26 imports across 22 files** —
`:app:app` 9 (5 main + 4 androidTest), `core/ui/navigation` 4, `core/ui/mvi` 1,
and **one `NavGraphBuilder` import in each of the 12 feature modules**. Those
12 are the criterion's work and go to **zero**.

> **Measured post-merge: 13 imports across 9 files.** The 12 feature imports
> went to zero, and so did `core:ui:mvi`'s one — the criterion met beyond its
> letter. Survivors: `core/ui/navigation` 2 files (`NavGraphScope.kt`,
> `NavigatorHolder.kt`), the 5 named `:app:app` main files, and the 2 androidTest
> exclusions. Post-1.3, `androidx.navigation` imports are gone **entirely** —
> the tree names only `androidx.navigation3`, confined to `core:ui:navigation`,
> the `:app:app` host pair, and the test hosts.

**Scope boundary on `:app:app`, stated rather than quietly missed.** The 5
remaining main-source imports are the host and the command bridge —
`App.kt` (`rememberNavController`), `AppNavigationHost.kt` (`NavHost`),
`NavigatorExt.kt`, `BottomBarNavigationListener.kt`,
`ClearFocusOnDestinationChanged.kt`. Emptying those means moving the host, and
§1 assigns the host to **1.3** ("the diff is the DSL implementation, the host,
and the retention wiring"). Doing it here would move 1.3's largest piece into
1.2 under a criterion meant to shrink 1.3, and would do it without the Nav3
host it has to become. They stay, deliberately, and are enumerated in the PR
body. The 4 androidTest imports are the two pre-existing named exclusions from
1.1 (§3 of #221), untouched.

So B is met in full for every feature module — the thing that makes the 1.3
call-site diff empty — and the residue is named, bounded, and owned by 1.3.

**C — the pinned expected-failure list unchanged**: `ApplicationBottomBarTest`
4/4 and nothing else. Any new failure is this stage's doing.

**D — full gate**, standard convention: `--rerun-tasks --no-build-cache
--no-configuration-cache`, detekt as a separate invocation, `verifyPaparazziDebug`
(446 goldens), plus bisect-green on every commit. Every invocation reports its
`N actionable tasks: N executed` line — `from cache` / `up-to-date` on a gate
run means the flags did not take.

On Paparazzi: §2.3's correction removes the reason this was called a live
possibility — there are no shared-element call sites, and a scope nothing
renders from cannot move a golden. The run is still mandatory and the count is
still reported. **If a golden moves, STOP and report the diff; do not run
`recordPaparazziDebug` under any circumstances.** A moved golden would mean
something other than this stage's contract change moved it, which is worth more
as a signal than as a re-recorded baseline.

---

## 7. Note carried from 1.1 — read before writing tests

`AppCoroutineScopeImpl.launch(flow, …)` applies `.catch { onError(it) }`, so any
flow error inside an MVI Store is swallowed. Consequence for this stage: if a
result flow breaks during the migration, **no test will throw** — the screen
will silently show default state. A retention or restoration test can go
vacuously green.

Assert on the observable effect (the originating screen reflects the save), never
on the absence of an exception.
