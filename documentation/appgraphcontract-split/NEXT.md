# NEXT — AppGraphContract split — ✅ MIGRATION COMPLETE

> **▶ RESUME HERE → read [`HANDOFF.md`](HANDOFF.md) first**, then `git log --oneline d54129dd..HEAD`.
> It is the complete state snapshot for the fresh (zero-memory) session: what's done (with SHAs), the
> verification model, the open gates, and toolchain notes.

**STATUS: DONE + delivered.** All 15 readers migrated; the `AppGraphContract` god-object + module `core:di`
deleted. Delivered to **PR #176** (`feature/metro-batch` → `dev`) — **CI GREEN** (tip `8697f5e8`). The
strangler is finished. **Next action = maintainer's ON-DEVICE PASS** (NOT more DI work): the Recovery/Worker
typed-holder runtime upcast + Google Fonts cert load are the two runtime paths unit tests don't cover — see
`HANDOFF.md` §"Open gates". Then independent review, then merge → `dev` (via PR only). History below is
retained for provenance.

## Final shape (as landed)
- **Acquisition mechanism:** `AppDepsHolder` + `inline fun <reified T> Context.appDeps(): T` in
  `core:ui:mvi`; `BaseApplication` implements `AppDepsHolder`. The 13 feature-side readers use
  `context.appDeps<XDeps>()`.
- **Framework readers (2), typed point-acquisition (no `core:ui:mvi` edge):** `RecoveryActivity` via
  `RecoveryDepsHolder`/`RecoveryDeps` (in `feature/recovery`); `MetroWorkerFactory` via
  `BackupWorkerDepsHolder`/`BackupWorkerDeps` (in `core/data/backup/worker`, data→ui inversion respected).
  `BaseApplication` implements both typed holders.
- **AppGraph** implements the 15 replacement interfaces (2 spine `StoreCoreDeps`/`NavigatorDeps` + 11
  feature `XDeps` + `RecoveryDeps` + `BackupWorkerDeps`); `AppGraphContract` is gone from its supertypes.
- **Deleted:** `AppGraphContract` + `AppGraphContractHolder` + `AppGraphContractAccessor` + module
  `core:di` (Area-3's 8 `api` edges dissolved with it). 2 dead accessors (`appReinitializer`,
  `liveDatabaseLocator`) dropped (bindings survive via ctor `@Inject`).
- **`app/app`** gained a direct `api(project(":core:ui:mvi"))` (+ `api(feature:recovery)` +
  `api(core:data:backup:worker)`) so the flavor apps see the holder supertypes after `core:di`'s deletion.

## Where we are (history)
- **C1 DONE** — committed `f1fe1a02` on branch `cleanup/appgraphcontract-split` (base `d54129dd` = tip of
  `feature/metro-batch`). Additive spine interfaces (`StoreCoreDeps` in `core:ui:mvi`, `NavigatorDeps` in
  `core:ui:navigation`); `AppGraph` implements both; `AppGraphContract` intact; all gates green; revert-clean.
- **Docs synced** — `spec.md` + this file carry the **acquisition mechanism** (`appDeps<T>()` in
  `core:ui:mvi`) and the **untyped-registry disambiguation** (see `spec.md` §"Acquisition mechanism" and
  Non-goals). ACQUISITION (`appDeps<T>()`) and INJECTION (`create(...)`) are orthogonal; `appDeps<T>()`
  FEEDS `create(...)`, it does not replace it.
- Nothing else migrated. The god-object is untouched.

## Next step: C3 — migrate the FIRST reader
**`AppDialog`** (`feature/app-dialogs/impl/.../di/AppDialogFeature.kt`). Rationale: it consumes
`StoreCoreDeps` ONLY (`analyticsHolder`, `loggerHolder`, `storeDispatchers`) — no `navigator`, no domain
tail — the cleanest first migration; validates BOTH the acquisition mechanism and single-interface
injection. Its `AppDialogGraph.Factory` currently takes those 3 as positional bound instances read off
`context.appGraphContract()`; replace with **acquisition + injection**:
```kotlin
val deps = context.appDeps<StoreCoreDeps>()
createGraphFactory<AppDialogGraph.Factory>().create(
    analyticsHolder = deps.analyticsHolder,
    loggerHolder = deps.loggerHolder,
    storeDispatchers = deps.storeDispatchers,
    // + this feature's own impl-internal deps via appDialogInternals()
)
```
C3 also introduces the acquisition seam itself (`AppDepsHolder` + `appDeps<T>()` in `core:ui:mvi`,
`BaseApplication : AppDepsHolder`) — reused by every later reader.

## Then, in order (cheapest → hardest)
1. `AppDialog` — StoreCoreDeps only
2. `ImageViewer` — StoreCoreDeps + NavigatorDeps
3. the five size-8: `AllExercises`, `AllTrainings`, `Archive`, `ExerciseChart`, `PlanEditor`
   (StoreCoreDeps + NavigatorDeps + per-feature `XDeps` incl. `@DefaultDispatcher`/`ResourceWrapper`)
4. the assisted/larger: `Home`, `PastSession`, `Exercise`, `SingleTraining`, `LiveWorkout`
5. `Settings` — StoreCoreDeps + NavigatorDeps + `SettingsDeps` (folds its backup slice)
6. `RecoveryActivity` (+`RecoveryDeps`, 2 accessors) and `MetroWorkerFactory` (+`BackupWorkerDeps`, 6)
7. C(n+1): drop the 2 dead accessors (`appReinitializer`, `liveDatabaseLocator`)
8. C(last): delete `AppGraphContract` + holder + accessor + module `core:di`

## Load-bearing inputs (this folder)
- `gate0-discovery.md` §0.4 — the **15-reader → composition MAP** (exact consumed-set per reader).
- `gate0-discovery.md` §0.3 — the **30-item coverage list** (the union all new interfaces must equal).
- `dependency-matrix.md` — the raw ✓-matrix.

## Verification reminders (from C1's finding)
- Metro resolves accessors **by TYPE, not name** → the name-rename known-negative is vacuous. Use:
  per-reader negative = `git grep AppGraphContract` in module == 0; positive = accessor set == the Gate-0
  map (structural, not "it compiles"); non-vacuity = unbound-type accessor → `[Metro/MissingBinding]`.
- Dispatcher accessors MUST carry their qualifier annotation verbatim (Metro matches type + qualifier).

## Housekeeping
- The throwaway `spike/metro-kmp-extension` branch can be deleted — all its decisions are now captured in
  `spec.md` + `gate0-discovery.md`. Do NOT delete without maintainer confirmation.
