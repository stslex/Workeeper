# C1 report — spine interfaces (StoreCoreDeps + NavigatorDeps), AppGraph implements (additive)

**Branch:** `cleanup/appgraphcontract-split` · **C1 commit:** `f1fe1a02` · **base:** `d54129dd` (tip of `feature/metro-batch`).
Variant A, spine variant γ. Additive only — no reader migrated, nothing deleted, `AppGraphContract` intact.

## Clean-base confirmation
- Branched `cleanup/appgraphcontract-split` from the committed tip `d54129dd`; `git reset --hard d54129dd` discarded all tracked spike modifications; removed the 3 spike-specific untracked items (`SpikeProbeBindingContainer.kt`, `core/core/src/concurrentMain/`, `documentation/spike-metro-kmp-extension.md`).
- **Tracked tree was clean before editing** (0 tracked-change lines). Three untracked files remained — `KMP_C1_RESULTS.md`, `documentation/metro-cleanup-discovery.md`, `iosApp/` — all pre-existing / orthogonal to C1 and NOT included in the commit (staged C1 files explicitly).

## Navigation-dep case (which held)
- **`:app` already depends on `core:ui:navigation` directly** — `app/app/build.gradle.kts:42 implementation(project(":core:ui:navigation"))`. So `AppGraph` can implement `NavigatorDeps` (from `core:ui:navigation`) with **NO build.gradle change**. Gate-0's "might be missing" hypothesis did not hold; no dep added.

## The two interfaces (signatures copied verbatim from AppGraphContract)
- **`StoreCoreDeps`** — `core/ui/mvi/src/main/.../core/ui/mvi/di/StoreCoreDeps.kt` (package `…core.ui.mvi.di`):
  `analyticsHolder: AnalyticsHolder`, `loggerHolder: LoggerHolder`, `storeDispatchers: StoreDispatchers`. No `navigator` (variant γ). All 3 types owned by `core:ui:mvi` → same-module, no dep change.
- **`NavigatorDeps`** — `core/ui/navigation/src/main/.../core/ui/navigation/NavigatorDeps.kt` (package `…core.ui.navigation`): `navigator: Navigator`. Owned same-module.

## AppGraph change (additive — diff)
```
-internal interface AppGraph : AppGraphContract {
+internal interface AppGraph : AppGraphContract, StoreCoreDeps, NavigatorDeps {
```
+ 2 imports (`StoreCoreDeps`, `NavigatorDeps`). **No new accessor** — AppGraph's existing
`override val analyticsHolder/loggerHolder/storeDispatchers/navigator` satisfy the new supertypes.
Full C1 diff = 3 files, +44/−1. `AppGraphContract` NOT touched (verified `git diff d54129dd..f1fe1a02`).

## Gate results (all `--rerun-tasks --no-build-cache`, executed, never cached)
| Gate | Result |
|---|---|
| `:app:dev:assembleDebug` | ✅ BUILD SUCCESSFUL, **638 tasks executed** |
| `detekt --no-daemon` (incl. custom lint-rules) | ✅ BUILD SUCCESSFUL, **37 tasks**, zero violations/suppressions |
| `:core:ui:mvi` + `:core:ui:navigation` + `:app:app` `testDebugUnitTest` | ✅ BUILD SUCCESSFUL, **574 tasks** |

## Proof anchors
- **Known-POSITIVE:** assemble green; `AppGraph : AppGraphContract, StoreCoreDeps, NavigatorDeps` compiles with the supertype-list change only — no new accessor on AppGraph (diff confirms).
- **Known-NEGATIVE (non-vacuous):** the spec's suggested rename (`analyticsHolder`→`analyticsHolderZZZ`) did **NOT** break the build (Metro resolves graph accessors **by TYPE**, not by name — a renamed accessor of the same type `AnalyticsHolder` still resolves from AppGraph's binding). So a rename is not a valid non-vacuity test here. Substituted a **valid** negative: added an accessor of an **unbound type** (`val zzzUnboundProbe: StoreCoreDepsNegativeProbe`) to `StoreCoreDeps` → `:app:dev:compileDebugKotlin` **FAILED** with `e: [Metro/MissingBinding] No binding found for StoreCoreDepsNegativeProbe … requested at AppGraph.zzzUnboundProbe … Encountered while processing declaration 'StoreCoreDeps.zzzUnboundProbe'`. This proves AppGraph genuinely implements `StoreCoreDeps` and resolves its members through the graph (non-vacuous). Reverted after.

## Reversibility
- `AppGraphContract` intact; no reader/feature graph/RecoveryActivity/MetroWorkerFactory/`create(...)` touched.
- `git revert f1fe1a02` applies cleanly (verified, then aborted to restore C1).

## STOP
C1 green and committed. **No reader migration started** — that is the next gated step (C3…), awaiting maintainer review. Note for the migration phase: because Metro resolves accessors by type, the migration's per-module known-negative should be `git grep AppGraphContract == 0` in the module (as specified), not an accessor-name check.
