<!-- SPDX-License-Identifier: GPL-3.0-only -->
# Graph-extension migration — session handoff

Branch: `spike/graph-extension-all-trainings` (cut from `cf328bf`; backup `backup/appgraphcontract-split`).

## The arc

Replace the Hilt-strangler DI bridge with Metro `@GraphExtension`. Each of the **13 feature graphs**
becomes a `@GraphExtension(XScope)` whose `@GraphExtension.Factory` carries
`@ContributesTo(AppScope::class)`, so `:app` generates the extension impl and it inherits every
app-scoped binding. End state deletes the **15 `XxxDeps` interfaces**, the **125 `@Provides`** bound
instances, the **30 `override val`** accessors, the **13 `*BridgeTest.kt`**, and the `as T` cast seam.

## ⚠️ THE ARC IS INDIVISIBLE — do not merge a partial port

`AppGraph`'s accessor count and the 15 `XxxDeps` interfaces collapse **only when the last feature is
ported**. After feature #1 (all-trainings) the AppGraph accessor count is **43 → 43** — this is the
**expected** result, not a shortfall: all-trainings' four deps (`trainingRepository`, `tagRepository`,
`resourceWrapper`, `@DefaultDispatcher`) are shared with other still-bridged features, so nothing is
removable yet. Only `AllTrainingsDeps` (whose members are fully covered by siblings) was deletable.

Every intermediate state carries **BOTH** mechanisms simultaneously (bridge `appDeps<XxxDeps>()` for
un-ported features + `appDeps<XxxGraph.Factory>()` extensions for ported ones) and is strictly **more
complex** than either endpoint. **A partially-ported arc must not be merged to `dev`/`master`.** Land
the whole arc or none of it.

## Status

- **DONE:** Phase 0 gate (`c12c44dc`), `AppScope`→commonMain (`2f9c89d8`), all-trainings port
  (`9f17d02a`) + `AllTrainingsDeps` deleted (`197f39b4`), unique-creator fix (`dbfc4852`),
  archive port (`4c184e5e`) + `ArchiveDeps` deleted. 2 of 13 ported; 13 `XxxDeps` supertypes remain
  on `AppGraph` (was 15).
- **REMAINING:** 11 features. Non-goals for the current slice: assisted-store features (`exercise`,
  `live-workout`, `image-viewer`, `plan-editor`) and `MetroWorkerFactory` need a separate acquisition
  decision. NOTE: the assisted set is LARGER than the original non-goal list — measured
  `FeatureAssisted<>` users are `exercise`, `live-workout`, `image-viewer`, `plan-editor`,
  `exercise-chart`, `past-session`, `single-training` (7). Remaining PLAIN (portable now):
  `all-exercises`, `app-dialogs`, `home`, `settings`.

## The proven pattern (per feature)

1. `XxxGraph` → `@GraphExtension(XScope::class)`, public interface; `Factory` →
   `@GraphExtension.Factory` + `@ContributesTo(AppScope::class)`, **zero params** (deps inherited); keep
   feature-local `@Binds`.
   **BINDING NAMING RULE — each contributed `@GraphExtension.Factory` declares a UNIQUELY-NAMED
   creator** (`createAllTrainingsGraph()`, `createArchiveGraph()`, …), never a bare `create()`. Every
   factory is merged into `AppGraph`, so two factories declaring `create()` fail to compile
   (`'fun create(): XGraph' clashes with 'fun create(): YGraph': return types are incompatible`). This
   is invisible with one feature ported and breaks on the second — measured on an N-extension probe.
2. Flip point: `context.appDeps<XxxGraph.Factory>().createXxxGraph().xxxStore` — the existing `as T`
   seam cast, with the uniquely-named creator from rule 1.
   **`asContribution<T>()` is NOT usable** feature-side (needs a statically `@DependencyGraph` receiver;
   the seam is `Any`).
3. **Minimum** visibility, not blanket: the ceiling is a hypothesis per feature — measure the forced set.
   Public = graph(+Factory), storeImpl, store-contract, the `@Binds` interface+impl pairs, and any
   domain/UI model a public interface exposes. **Internal stays internal** via `@Inject class XxxStoreImpl
   internal constructor(...)` (keeps handlers + ctor internal; `:app` calls the internal ctor at IR
   level — see tech-debt.md). Scope marker stays internal.
4. Replace the feature-module `XxxGraphBridgeTest` with an identity test in **`app/app/src/test`** (a
   `@GraphExtension` can't be created standalone): assert the store resolves through the real parent
   AND its app-scoped deps are the SAME instances (`===`).
5. Delete `XxxDeps` + its AppGraph supertype **only** once no sibling still needs its members.

## ⚠️ Stale `app/app/build` is an ARC PROPERTY — wipe build dirs on every branch switch

Extension codegen **concentrates in `:app`**: `AppGraph$Impl` implements every contributed factory, so
one stale `app/app/build` now invalidates **all ported features at once**, and the failure surfaces at
**RUNTIME**, not compile time:

```
java.lang.AbstractMethodError: Receiver class AppGraph$Impl does not define or inherit
'AllTrainingsGraph createAllTrainingsGraph()' of interface AllTrainingsGraph$Factory
```

This is a **structural consequence of the arc**, not a session artifact: today's 13 *root* graphs
distribute that risk across 13 modules; after the arc it is concentrated in one. Observed for real when
switching between `cf328bf` and the spike branch mid-measurement. It is **not** reproducible from a
consistent state (verified: the same ABI change cascades correctly, both compiles EXECUTED, tests
green), so it is a stale-artifact hazard, not an incremental-correctness bug — same family as the
repo's documented "stale Hilt-generated Java footgun when switching branches".

**STANDING RULE for anyone working this arc:**
```bash
find . -maxdepth 4 -type d -name build -not -path "./.git/*" -exec rm -rf {} +
```
after every branch switch (and before any build-time measurement). A `--rerun-tasks` build also clears
it. Symptom to recognise: `AbstractMethodError` on `AppGraph$Impl` naming a factory method that
demonstrably exists in source.

## Base decision — build on `cf328bf`, do NOT rebase onto `d54129d`

**Settled: the AppGraphContract split arc stays.** At `d54129d` the seam is a concrete
`AppGraphContract` in a separate `core:di` module; the generic
`inline fun <reified T> Context.appDeps(): T` over `AppDepsHolder.appDeps(): Any` exists **only from
the split arc onward** — and the extension flip point depends on exactly that generic form. Rebasing it
out would delete the seam this arc needs and force re-inventing it.

So the split arc splits in two:
- **load-bearing, stays:** the seam generalization (`appDeps<T>()` + `AppDepsHolder`), which every
  ported feature uses as `appDeps<XxxGraph.Factory>()`;
- **transient, deleted by this arc:** the 15 narrow `XxxDeps` interfaces (536 LOC).

All ports build on `cf328bf`, as the spike already does. No rebase, no force-push.

## Running build-time table (append one row per ported feature)

Per-feature guard against the untested "13 REAL extensions" cell: the N=0…16 slope probe used synthetic
extensions (2 `@Binds` + trivial accessor), lighter than real features, and flat-on-each-axis does not
prove flat-on-the-product. Measure **clean `:app:app:compileDebugKotlin`** (real clean state:
`rm -rf app/app/build`, `--no-build-cache`, per-task state reported, ≥3 runs) **before and after** each
port. A real slope departure will show by feature 3, not feature 13.

| Extensions in `:app` | After porting | clean `:app:app` median | runs | task state |
|---|---|---|---|---|
| 1 | all-trainings | **1.4s** | 1.7 / 1.3 / 1.4 | EXECUTED, 0 FROM-CACHE |
| 2 | archive | **1.2s** | 1.2 / 1.5 / 1.2 | EXECUTED, 0 FROM-CACHE |

## Measured forced-public surface, per feature (never assumed)

Widen ONE declaration at a time to a compiler fixpoint; record what the compiler actually forced. The
count is a hypothesis per feature, not a work order.

| Feature | Forced-public | `@Binds` | Handlers (all stayed internal) | Composition note |
|---|---|---|---|---|
| all-trainings | **11** | 2 | 3 | 3 domain models forced; UI models were already public |
| archive | **11** | 2 | 5 | forced a UI model (`ArchivedItemUi`) + its own `ExerciseTypeDomain` copy |

**Refuted hypothesis:** handler count does not drive the forced set — archive has 5 handlers vs
all-trainings' 3, and none was forced public. The `@Inject class XStoreImpl internal constructor(...)`
form keeps every handler off the public API. What *does* drive it: the `@Binds` pairs, the accessor
return type, and whichever domain/UI models the now-public interactor/store contract exposes (note each
feature owns a private duplicate of `ExerciseTypeDomain`, so that one recurs).

## Debt the arc makes visible — `ExerciseTypeDomain` × 8 (record only, do NOT fix during the arc)

`ExerciseTypeDomain` is an identical `internal enum class` duplicated in **8 feature modules**
(`all-exercises`, `archive`, `exercise`, `exercise-chart`, `live-workout`, `past-session`,
`plan-editor`, `single-training`) — a deliberate domain-purity duplication, each feature owning its own
copy rather than sharing a `core.data.*` type.

The arc forces each copy **public** as it ports that feature (archive's is already public, commit
`4c184e5e`), so at arc completion there will be **8 public copies of the same enum**. This is
**pre-existing debt the arc merely makes visible**, not debt the arc creates: the duplication is
already there, the arc only changes its visibility.

**Consolidation is a candidate for AFTER the arc, not during** — merging them mid-arc would touch 8
feature modules while both DI mechanisms are live, against the indivisibility rule above.

## The three baseline-RED androidTest modules (enumerated — previously undocumented)

These were referenced across the Step-6 commits as "12 green / same 3 baseline-RED" but were **not
named anywhere in `documentation/`** (no `P-TESTINFRA` marker exists). Recovered from commit
`fa80d330`, which names them verbatim:

> repo-wide `assembleDebugAndroidTest` = 12 green / same 3 baseline-RED (**`core:ui:mvi`,
> `feature:exercise`, `feature:recovery`** — pre-existing `MissingBinding`, P-TESTINFRA's job)

| Module | androidTest dir today | Note |
|---|---|---|
| `core:ui:mvi` | present | pre-existing `MissingBinding` |
| `feature:exercise` | present | pre-existing `MissingBinding` |
| `feature:recovery` | **absent** — no `src/androidTest` | entry is STALE; verify before citing |

Status is **unchanged by the graph-extension arc**: `git diff cf328bf..HEAD` touches no androidTest
source, and repo-wide `compileDebugAndroidTestKotlin` is green (they fail at runtime, not compile).
Next session: verify with
`./gradlew connectedDebugAndroidTest --continue` rather than asserting status by construction.

## KMP open items (next platform axis, NOT this arc)

- `Context.appDeps<T>()` in `core:ui:mvi` is `android.content.Context`-typed and load-bearing (no
  feature-side `asContribution` path exists).
- `DispatchersBindingContainer` lives in `core:core-android`; `Dispatchers.IO` has no Kotlin/Native form.
