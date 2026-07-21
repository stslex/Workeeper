# NEXT — AppGraphContract split, resume pointer

**Read `spec.md` first** (this folder) — it has the full plan + the corrected verification model.

## Where we are
- **C1 DONE** — committed `f1fe1a02` on branch `cleanup/appgraphcontract-split` (base `d54129dd` = tip of
  `feature/metro-batch`). Additive spine interfaces (`StoreCoreDeps` in `core:ui:mvi`, `NavigatorDeps` in
  `core:ui:navigation`); `AppGraph` implements both; `AppGraphContract` intact; all gates green; revert-clean.
- Nothing else migrated. The god-object is untouched.

## Next step: C3 — migrate the FIRST reader
**`AppDialog`** (`feature/app-dialogs/impl/.../di/AppDialogFeature.kt`). Rationale: it consumes
`StoreCoreDeps` ONLY (`analyticsHolder`, `loggerHolder`, `storeDispatchers`) — no `navigator`, no domain
tail — the cleanest first migration; validates single-interface resolution. Its `AppDialogGraph.Factory`
currently takes those 3 as positional bound instances read off `context.appGraphContract()`; replace with
`storeCoreDeps = graph` composition.

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
