# Session handoff — AppGraphContract split

> **Read this first, then `git log --oneline d54129dd..HEAD`.** Work paused; resumes in a FRESH session
> (both the Claude Code session and the maintainer's Claude session restart with zero in-context memory).
> This file is the complete state snapshot so tomorrow starts from "read + git log", not reconstruction.
>
> **Branch:** `feature/metro-batch` · **tip:** `8697f5e8` · **PR:** #176 (`feature/metro-batch` → `dev`), CI GREEN.
> Every SHA/claim below was verified against source + git at write time.

---

## ✅ DONE

### The migration (complete)
The flat 32-accessor `AppGraphContract` god-object is **deleted**; all **15 readers** migrated to narrow
per-consumer dep interfaces. `AppGraph` (in `app/app/di/AppGraph.kt`) now implements **15 interfaces**:

- **2 spine** — `StoreCoreDeps` (in `core:ui:mvi`, {analyticsHolder, loggerHolder, storeDispatchers}) +
  `NavigatorDeps` (in `core:ui:navigation`, {navigator}).
- **11 feature XDeps** (domain tails, each declared in its own feature module): `AllTrainingsDeps`,
  `AllExercisesDeps`, `ArchiveDeps`, `ExerciseChartDeps`, `PlanEditorDeps`, `PastSessionDeps`,
  `ExerciseDeps`, `SingleTrainingDeps`, `LiveWorkoutDeps`, `HomeDeps`, `SettingsDeps`.
- **2 framework** — `RecoveryDeps` (in `feature/recovery`) + `BackupWorkerDeps` (in
  `core/data/backup/worker`).

(AppDialog uses `StoreCoreDeps` only, ImageViewer uses `StoreCoreDeps` + `NavigatorDeps` — no XDeps; that's
why 13 feature readers but only 11 feature XDeps.)

### Acquisition mechanism
- **13 feature-side readers:** `context.appDeps<T>()` — a reified `Context` extension + `AppDepsHolder`
  interface, both in `core:ui:mvi`. `BaseApplication` implements `AppDepsHolder` returning `appGraph`
  (typed as `Any`; re-narrowed by the single `as T` cast, safe by construction since `AppGraph : T`).
- **2 framework readers, TYPED point-holders** (return the interface, not `Any` — no reified, no unchecked
  cast): `RecoveryDepsHolder` (in `feature/recovery`) and `BackupWorkerDepsHolder` (in
  `core/data/backup/worker`). Reason: the Worker is in the DATA layer and MUST NOT depend on `core:ui:mvi`
  (data→ui inversion), so it cannot use the mvi-homed `appDeps<T>()`; Recovery uses zero mvi symbols and
  reached `appDeps` only transitively via `core:di` (now deleted), so it too takes a typed holder.
  `BaseApplication` implements BOTH holders (returning `appGraph`, a compile-checked upcast).
- **`BaseApplication` final supertypes (7 holders):** `AppGraphOwner`, `AppDepsHolder`, `RecoveryDepsHolder`,
  `BackupWorkerDepsHolder`, `AppDialogPublisherHolder`, `AppDialogInternalsHolder` (+ `Application()`,
  `Configuration.Provider`). `AppGraphContractHolder` was surgically removed (single supertype dropped).

### `core:di` deleted
The `core:di` module is **deleted entirely** — it was Hilt-coexistence scaffolding (born `fa80d330` "Step 6
(prep): introduce core/di AppGraphContract + Context.appGraphContract() accessor", the
`EntryPointAccessors.fromApplication(...)` replacement). Its 3 files (`AppGraphContract`,
`AppGraphContractHolder`, `AppGraphContractAccessor`) + `build.gradle.kts` are gone; removed from
`settings.gradle.kts` + every `implementation/api(project(":core:di"))`. **Area-3** (its 8 over-exposed
`api` edges) dissolved with the module. `git grep AppGraphContract` and `git grep :core:di` == **0**.

### Dispatcher qualifiers (divergence was real — preserved verbatim per reader)
Each reader's dispatcher accessor carries the exact qualifier its `Graph.Factory.create()` reads (derived
from source, never by inertia): `@DefaultDispatcher` (most), **`@IODispatcher`** (PastSession, + Settings),
**`@MainImmediateDispatcher`** (Exercise, SingleTraining). Settings reads TWO (`@DefaultDispatcher` +
`@IODispatcher`); Exercise/SingleTraining read TWO (`@DefaultDispatcher` + `@MainImmediateDispatcher`).
Multi-dispatcher DISTINCTNESS (the pair resolves to different instances, no cross-wire) is proven by each
reader's existing `*GraphBridgeTest` where ≥2 dispatchers.

### Delivery to PR #176
Merged `cleanup/appgraphcontract-split` into `feature/metro-batch` via **`merge --no-ff`** (merge commit
**`ae9483b7`**) — the **20 strangler commits are preserved, NOT squashed** (per-commit bisect-green +
revertability is the whole discipline). Pushed to origin.

### Pre-existing baseline lint — fixed with ZERO suppression
PR CI was red ONLY on `:core:ui:kit:lintReportDebug` — 5 `PrivateResource` errors on
`com_google_android_gms_fonts_certs*` (from `ui-text-google-fonts:1.12.0-beta02`, which began bundling
same-named PRIVATE arrays). **Pre-existing baseline** (the pre-migration tip `d54129dd` was already CI-red
on this exact step; `git diff d54129dd..HEAD -- core/ui/kit/` was empty). NOT a migration regression.
- `9d221c92` — first fix: `tools:override` + `tools:ignore="PrivateResource"` (maintainer rejected the
  suppression).
- `8697f5e8` — final: **renamed** the app's cert arrays to unique `app_gms_fonts_certs*` (no library-name
  collision → no `PrivateResource` at all) and **removed all `tools:override` + `tools:ignore`**. Cert
  base64 content **byte-identical** (sha256 verified: dev `cbd984b564917bed`/1596, prod
  `db3d6931264caec1`/1460; `git diff` shows zero cert-line changes). `AppTypography.kt` reference updated to
  `R.array.app_gms_fonts_certs`.
- Version-bump route was BLOCKED: `ui-text-google-fonts` is in the atomic version group
  `androidx.compose.ui`, BOM-managed by `compose-bom-alpha` — no standalone pin possible. Delete-the-file
  broke compile (the library's private arrays aren't R-visible to the consuming module).

### PR #176 CI — FULLY GREEN
`assembleDebug` ✅ + `detekt` ✅ + `lintDebug` ✅ + `testDebugUnitTest` ✅ (**1026 tasks, 0 failures**).
CI history: `d54129dd` fail → `ae9483b7` fail (baseline lint) → `9d221c92` success → **`8697f5e8` success**.
(Unit tests RUN and pass in CI — they were skipped on the red runs only because the lint step aborted the
job first.)

---

## VERIFICATION MODEL USED (so tomorrow does not relitigate it)
- **Metro resolves graph accessors BY TYPE, not name.** So per-reader known-NEGATIVE = `git grep
  AppGraphContract` in the module == 0; known-POSITIVE = **STRUCTURAL** accessor-set == the Gate-0
  consumed-set (NOT "it compiles" — a wrong-but-present accessor still compiles).
- **Cast-safety** of every `appDeps<T>()` / typed holder is **by construction**: `AppGraph : T` is a
  compile-verified is-relationship; assemble is NOT cited as the cast proof.
- **Coverage was script-verified pre-deletion:** the union of the 15 interfaces' accessors == the Gate-0
  **30-item** consumed list EXACTLY. The 2 dead accessors (`appReinitializer`, `liveDatabaseLocator`) were
  dropped — their bindings survive via ctor `@Inject` (NavigatorEventBus / RestoreRecoveryCoordinator /
  StartupMigrationCoordinator), untouched.
- **Gates run SERIAL** — concurrent `--rerun-tasks` builds race on shared build dirs (a C4 finding:
  `Could not delete .../caches-jvm`). **`detekt --no-daemon`** avoids the stale-ruleset false-green.
  Every commit is bisect-green and `git revert`-clean.

---

## ❌ NOT DONE — OPEN GATES (tomorrow, in order)

1. **MAINTAINER ON-DEVICE PASS** — the gate before ANY merge to `dev`. Two RUNTIME paths unit tests do NOT
   cover:
   - **(a) Recovery + Worker typed-holder upcast on a live `Application`** — the backup/restore paths; the
     2 readers whose code actually changed. Unit tests exercise the factory/graph, not the
     `applicationContext as RecoveryDepsHolder` / `BackupWorkerDepsHolder` runtime cast.
   - **(b) Google Fonts loading** — `GoogleFont.Provider` validates the cert arrays at RUNTIME. Cert content
     is byte-identical so no behavioral change is expected, but the on-device font load is the definitive
     check.
   - Note: `ui_tests.yml` is `workflow_dispatch`-only → UI tests do NOT run on PR CI. The on-device pass
     substitutes.
2. **Independent review agents (Codex / Gemini)** — a SEPARATE pass AFTER the on-device pass, then the
   maintainer's own review.
3. **Merge PR #176 → `dev`** — ONLY after 1 + 2 pass. `dev` is reached via PR only, never a direct push.

---

## HOUSEKEEPING TAILS (not blockers — do when convenient)
- **Delete throwaway branch `spike/metro-kmp-extension`** — variant A (explicit dep-list injection) went the
  full distance; the `@GraphExtension` fallback was never used. (Branch still exists locally + likely on
  origin.)
- **`documentation/tech-debt.md`: add a candidate detekt rule** — "a `@ContributesBinding(AppScope)` impl
  must be `public`". An `internal` contribution silently fails to aggregate cross-module (a false-green);
  the existing `ContributesBindingScopeRule` checks the scope ARG, not visibility, so it does NOT catch this.
  (Gate-0 bonus finding.)

---

## TOOLCHAIN NOTE (correct any stale assumption)
Committed on this branch: **Metro 1.3.2, Kotlin 2.4.10, coroutines 1.11.0, compose-bom-alpha 2026.07.00.**
The prior "Metro 1.1.1 pinned / Kotlin capped 2.3.x / CMP caps 2.3.x / Kotlin-bump = a ~695-LOC Detekt
cascade" assumptions are **OBSOLETE / proven dead** (Step-0 of the spike ran detekt-with-custom-rules + iOS
compile green on 2.4.10). Do not plan around the old caps.

---

*Handoff written docs-only; no source/DI/build change. Resume: read this + `git log`, then the on-device pass.*
