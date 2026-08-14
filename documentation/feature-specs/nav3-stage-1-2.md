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
edits.** Four classes, `RouteReachabilityTest` at 15/15. If a test has to change
to accommodate this stage, the contract changed behaviour and the change is
wrong.

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

Why on the destination rather than on the call: `Screen.PlanEditor`'s KDoc
claimed three consumers and had exactly one — undetected for months, found only
by grep. With the type declared on the destination, a wrong-typed read does not
compile, and "who reads this" becomes a question for the compiler instead of a
comment.

### 2.2 Project-owned registration DSL

`navComponentScreen` / `navComponentScreenWithState` (4 overloads in
`core:ui:mvi`) currently extend `NavGraphBuilder`. Under Nav3 the receiver
becomes `EntryProviderBuilder`.

Introduce a project-owned builder so the **12 call sites** never name either
receiver. In 1.3 the DSL is re-pointed at `entryProvider` and the call sites do
not move.

### 2.3 `AnimatedContentScope` leaves the content-lambda signature

Today the content lambda's receiver is Nav2's `AnimatedContentScope`, supplied
by `composable {}`'s scope. Under Nav3 it arrives via
`LocalNavAnimatedContentScope`. Remove it from the signature; expose it through
an accessor.

Documented exception: this puts a CompositionLocal in the navigation path, which
the project's own rule otherwise forbids. **The rule targets `Navigator`.** The
animation scope is not the navigator, and Nav3 delivers it this way by design.
Record the exception in `documentation/architecture.md` so it does not later
read as a violation. Shared-element call sites — 7 locations — consume the scope
through the accessor from this stage on.

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
4. Delete `SaveHandlerAttr` and the `Pair<String, Any?>` overload of `popBack`.
   Nothing may reference them by now — that is the proof step 1–3 were complete.
5. Project-owned registration DSL; the 12 call sites move to it.
6. `AnimatedContentScope` out of the signature; accessor in; 7 shared-element
   sites updated.
7. `documentation/architecture.md` — the CompositionLocal exception.

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

---

## 6. Exit criteria

All four required.

**A — the 1.1 oracle green, unedited.** `RouteReachabilityTest` 15/15 plus the
other three stage-1.1 classes. `git diff` against #221's HEAD shows **zero**
changes under `app/app/src/androidTest` — except under option (b), where exactly
one line in `NavPaths` changes and is named in the PR body.

**B — no `androidx.navigation` import outside `core:ui:navigation` and
`core:ui:mvi`.** After this stage the library is confined to its two
implementation modules; features and `:app:app` reach it only through project
API. Evidence: grep output, enumerated. This is the measurable definition of
"the contract hides the library", and it is what makes 1.3 small.

**C — the pinned expected-failure list unchanged**: `ApplicationBottomBarTest`
4/4 and nothing else. Any new failure is this stage's doing.

**D — full gate**, standard convention: `--rerun-tasks --no-build-cache
--no-configuration-cache`, detekt as a separate invocation, `verifyPaparazziDebug`
(446 goldens — §2.3 touches 7 shared-element call sites, so goldens moving is a
live possibility here, not a formality), plus bisect-green on every commit.

---

## 7. Note carried from 1.1 — read before writing tests

`AppCoroutineScopeImpl.launch(flow, …)` applies `.catch { onError(it) }`, so any
flow error inside an MVI Store is swallowed. Consequence for this stage: if a
result flow breaks during the migration, **no test will throw** — the screen
will silently show default state. A retention or restoration test can go
vacuously green.

Assert on the observable effect (the originating screen reflects the save), never
on the absence of an exception.
