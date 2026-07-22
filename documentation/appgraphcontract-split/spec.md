# Spec — Replace the `AppGraphContract` god-object with composed per-consumer dep interfaces

> **Provenance.** This on-disk spec did not previously exist in the repo (it lived in the maintainer's
> working notes). It is authored here to match the decisions **locked after Gate 0** — the earlier chat
> plan described a pre-Gate-0 shape (a single shared `BackupDeps`, a name-rename known-negative) that Gate 0
> and C1 corrected. Variant **A** (explicit dep-list injection), spine variant **γ**.

---

## ✅ MIGRATION COMPLETE
All 15 readers migrated; the `AppGraphContract` god-object + `AppGraphContractHolder` +
`AppGraphContractAccessor` + module `core:di` are **deleted**. Acquisition = `appDeps<T>()` in `core:ui:mvi`
for the 13 feature readers + typed holders (`RecoveryDeps`/`BackupWorkerDeps`) for the 2 framework readers.
`AppGraph` implements the 15 replacement interfaces. Coverage union == the 30 consumed accessors (verified);
the 2 dead accessors dropped. All gates green (assemble + detekt + full-suite unit tests). See `NEXT.md` for
the final shape. The sections below are the original plan, retained for provenance.

## ▶ RESUME HERE (session entry point)

- **State:** **C1 is DONE and committed at `f1fe1a02`** on branch `cleanup/appgraphcontract-split` (base
  `d54129dd` = tip of `feature/metro-batch`). Additive spine interfaces only; `AppGraphContract` intact;
  all gates green; `git revert`-clean. **Docs synced** (this file + `NEXT.md`) with the acquisition
  mechanism and the untyped-registry disambiguation before C3.
- **Next step:** **C3 — migrate the first reader: `AppDialog`** via `context.appDeps<StoreCoreDeps>()`. It
  consumes `StoreCoreDeps` ONLY (no navigator, no domain tail) → the cleanest first migration; it validates
  BOTH the acquisition mechanism (`appDeps<T>()`, see "Acquisition mechanism" below) and single-interface
  injection. Then `ImageViewer` (+`NavigatorDeps`), then the five size-8 readers, then the assisted/larger
  features, then `Settings`, then `RecoveryActivity` + `MetroWorkerFactory`.
- **Load-bearing inputs (in this folder):**
  - `gate0-discovery.md` — the **32-accessor universe**, the **30-item consumed list**, the **dead-surface
    proof**, and the **15-reader → composition MAP** (the direct input to each migration commit).
  - `dependency-matrix.md` — the raw per-reader ✓-matrix the map derives from.
  - `c1-report.md` — the C1 done-state + gate results + the Metro-resolves-by-type finding.
- **Verification model (READ before C3):** see §"Verification (corrected)" below — Metro resolves graph
  accessors **by type**, so the naive name-rename negative is vacuous; use the structural + unbound-type
  checks specified there.

---

## Problem (one sentence)
The Metro migration replaced Hilt's subcomponent inheritance with standalone feature graphs fed by manual
dep-injection through ONE flat union interface (`AppGraphContract`, 32 accessors), so every one of 15
readers draws a slice from a god-object that knows about everyone; variant A keeps injection but splits
that union into narrow per-consumer interfaces composed from a shared spine.

## Locked decisions (post-Gate-0)
- **KEEP** dep-injection into feature graphs. **DELETE** the god-object. **REPLACE** with composed narrow
  interfaces. **OUT OF SCOPE / rejected:** `@GraphExtension`/subcomponent inheritance, probe-3,
  `core:di`-as-mechanism, an untyped registry **as the dependency-distribution mechanism** (variant B /
  extension factories — those killed typed injection entirely; injection stays). The reified
  **acquisition** accessor `appDeps<T>()` is NOT this rejected registry — see "Acquisition mechanism" and
  the disambiguation under Non-goals.
- Grounded at HEAD `d54129dd`; every count re-verified in Gate 0 against source.
- Target branch `cleanup/appgraphcontract-split`, merged back through normal review, lands BEFORE #176 goes
  to `dev`. No `dev`/`master` involvement.

## Target architecture (final, post-Gate-0)

Two layers — a shared spine + narrow per-consumer tails — composed at each factory.

### Spine — variant γ (two orthogonal interfaces, NO api-promotion). DONE in C1.
- **`StoreCoreDeps`** in `core:ui:mvi`: `analyticsHolder`, `loggerHolder`, `storeDispatchers` (13/15).
- **`NavigatorDeps`** in `core:ui:navigation`: `navigator` (12/15).
- Kept SEPARATE (γ) so a store-infra-only consumer (`AppDialog`) takes `StoreCoreDeps` alone — no
  `navigator`, no `core:ui:navigation` edge, **no api-promotion anywhere**. Consumers that need both
  compose `StoreCoreDeps` + `NavigatorDeps`.
- `AppGraph` (in `:app`) implements both (already true at `f1fe1a02`); `:app` already depends on
  `core:ui:navigation` directly, so no build-script change was needed.

### Backup readers — per-consumer interfaces, NO shared `BackupDeps` (Gate-0 correction)
Gate 0 proved the "backup cluster" is **not a cluster**: its types span **5 modules**
(`core:data:backup:api`, `core:data:dataStore`, `core:data:database`, `core:core`, `core:core-android`),
`core:data:backup:api` cannot name them all without widening, and the 3 consumers read **disjoint slices**
(Recovery 2 / Worker 6 / Settings 11-of-16). Final shape — uniform per-consumer pattern, each interface
declared in its OWN consumer module naming only types that module already depends on (no `:api` widening,
no cycle):
- **`RecoveryDeps`** (2 accessors: `databaseSnapshotProvider`, `recoveryDiagnosticsExporter`) — declared IN
  `feature/recovery`.
- **`BackupWorkerDeps`** (6 accessors: `backupStorage`, `snapshotExportRunner`, `backupNotificationHelper`,
  `autoBackupController`, `backupPreferencesRepository`, `databaseSnapshotProvider`) — declared IN
  `core/data/backup/worker`.
- Settings' backup slice folds into **`SettingsDeps`** (its full 16-minus-spine tail) — declared IN
  `feature/settings`.
- `AppGraph` implements all of them.

### Domain tails — per-consumer `XDeps` (one per feature)
Each feature's own repositories, **plus `@DefaultDispatcher`/`ResourceWrapper` where the feature uses them**
(decision D4): DD (10/15) and RW (10/15) are broad but NOT exclusive to any exact cluster and are
feature-logic deps (not store-infra), so they live in each `XDeps`, NOT a shared tier. **No `BottomBarDeps`.**
Each `XDeps` is declared in its feature module (its accessor types are repos the module already depends on;
Gate 0 confirmed 0 internal repositories).

### Composition at each factory
`create(storeCoreDeps, [navigatorDeps,] [xDeps,] …)` — spine not duplicated, domain narrow, framework
readers (`Recovery`/`Worker`) get only their own interface. `AppGraph` implements every app-scope interface
(it already exposes every accessor via aggregation → "implement" = declare the supertype, no new provision).

### Acquisition mechanism (the missing layer — how a reader physically gets its interface)
**ACQUISITION and INJECTION are two orthogonal concerns, both required.** The interface-composition above
is the *injection* layer (how deps flow into the feature graph factory). It says nothing about *how a
reader obtains the interface object* once `appGraphContract()` is removed. That is the *acquisition* layer,
and it is **mechanism A**, homed in `core:ui:mvi`:

- **`interface AppDepsHolder { fun appDeps(): Any }`** and
  **`inline fun <reified T : Any> Context.appDeps(): T = (applicationContext as AppDepsHolder).appDeps() as T`**
  — both in `core:ui:mvi`. This module is Android-capable, already declares the spine (`StoreCoreDeps`,
  `NavigatorDeps`), and is depended on by all 13 features + `RecoveryActivity` → one home covers all 14
  feature-side readers with **no new module wiring**.
- **`BaseApplication : AppDepsHolder { override fun appDeps(): Any = appGraph }`** (in `:app`). This is the
  same interface-seam idiom the current `AppGraphContractHolder` uses — read the held graph through the
  **interface**, never a concrete-`Application` cast (a `context as BaseApplication` cast
  `ClassCastException`s under a swapped test `Application`).
- **Coverage:** the 14 feature-side readers (13 features + `RecoveryActivity`) all depend on `core:ui:mvi`,
  so they all reach `appDeps<T>()`.
- **Scope carve-out — `MetroWorkerFactory`** (`core/data/backup/worker`) **MUST NOT depend on `core:ui:mvi`**
  (data→ui inversion). It is the ONE reader the mvi-hosted mechanism does not reach; it gets its **own point
  acquisition** when it is migrated (last reader in the queue). Do **not** route the Worker through
  `core:ui:mvi`.

**`appDeps<T>()` FEEDS `create(...)`; it does not replace it.** Acquisition yields ONE narrow interface;
injection then passes its members into the graph factory, fully typed. Per-reader flow (AppDialog, C3):
```kotlin
val deps = context.appDeps<StoreCoreDeps>()
createGraphFactory<AppDialogGraph.Factory>()
    .create(
        analyticsHolder = deps.analyticsHolder,
        loggerHolder = deps.loggerHolder,
        storeDispatchers = deps.storeDispatchers,
        // … + this feature's own impl-internal deps via appDialogInternals()
    )
```

### Deletion (final commit)
`AppGraphContract` + `AppGraphContractHolder` + `AppGraphContractAccessor` + module **`core:di`** are
DELETED **entirely** at the end. Because the acquisition mechanism is homed in `core:ui:mvi` (NOT
`core:di`), `core:di` retains only those three files once every reader is migrated — nothing is left to
keep, so it is **fully deleted, never "gutted to a mechanism."** (Supersedes the earlier
`core:di`-as-mechanism framing — `core:di`-as-mechanism is a rejected non-goal, see below.) Area-3 (the 8
`api` edges of `core:di`) dissolves with the module.

## Coverage arithmetic (completeness gate — from Gate 0)
- Universe = **32** accessors on `AppGraphContract`.
- Consumed union = **30** (see `gate0-discovery.md` §0.3 for the explicit 30-item list).
- Dead = exactly **2**: `appReinitializer` (`:75`), `liveDatabaseLocator` (`:105`) — both proven
  DROPPABLE (their bindings survive via ctor `@Inject`/`@ContributesBinding`; only the unused contract
  accessor goes). `32 − 2 = 30`.
- The UNION of all new interfaces' accessors MUST == exactly these 30 — add none unread, drop none read.

## Non-goals (considered and rejected — do not reintroduce)
- No `@GraphExtension`/inheritance, no probe-3, no `core:di`-as-mechanism.
- **Untyped registry — REJECTED vs ACCEPTED (disambiguation; a reviewer stalled here).** These are
  *different things*:
  - **REJECTED** — an untyped registry as the **dependency-distribution mechanism** (variant B / extension
    factories): `holder.get(key) as Factory` puts untyped resolution on the **load-bearing dependency
    path**, so every dep flows through a stringly/`Any`-keyed lookup. That is what "no untyped registry"
    forbids, and it is out of scope.
  - **ACCEPTED** — a **localized reified acquisition of ONE narrow interface** (`appDeps<T>()`),
    immediately followed by fully-typed `create(...)`. The only untyped point is the single `as T` cast at
    acquisition, **safe by construction** (`AppGraph` implements every narrow interface, so the cast can
    only fail if the interface is unimplemented — a compile-time-visible mistake, not a runtime key miss).
    The domain/injection path stays **fully typed**. This is the accepted acquisition design (mechanism A).
- No change to the ~37 `@ContributesBinding(AppScope)` contributions (the provide side is healthy).
- No change to backup/restore/DB logic, Room schema, or the on-device path — DI-surface reshape only.
- No shared `BackupDeps`, no `BottomBarDeps`, no api-promotion. Not merging to `dev`.

---

## EXECUTION — strangler, one independently-green commit per step
**Principle:** introduce new interfaces ALONGSIDE the god-object (additive — both compile), migrate readers
ONE at a time onto their narrow composition, delete the god-object only when it has zero readers. Every
commit is bisect-green and `git revert`-clean.

**Gate on EVERY commit (executed, never cached):**
`:app:dev:assembleDebug --rerun-tasks --no-build-cache`
· `detekt --no-daemon --rerun-tasks --no-build-cache` (incl. custom `lint-rules`, **zero suppressions**;
`--no-daemon` avoids the stale-ruleset false-green)
· affected-module `testDebugUnitTest --rerun-tasks`.
Conventional commits, English.

### Execution table
- **C1 — `StoreCoreDeps` + `NavigatorDeps`, AppGraph implements (additive).** ✅ **DONE at `f1fe1a02`.**
  Docs synced afterward with the acquisition mechanism + untyped-registry disambiguation (this commit).
- **C2 — introduce the acquisition seam + the backup/domain interfaces as needed per reader.** The
  acquisition seam (`AppDepsHolder` + `appDeps<T>()` in `core:ui:mvi`, `BaseApplication : AppDepsHolder`;
  see "Acquisition mechanism") is introduced in the **first migration commit that needs it (C3)**, then
  reused by every later reader. Each `XDeps`/`RecoveryDeps`/`BackupWorkerDeps` lives in its consumer's
  module; `AppGraph` gains the supertype in the same commit that introduces the interface.
- **C3…Cn — migrate readers, one per commit.** Order (cheapest first):
  `AppDialog` (StoreCoreDeps only) → `ImageViewer` (+NavigatorDeps) → the five size-8
  (`AllExercises`/`AllTrainings`/`Archive`/`ExerciseChart`/`PlanEditor`) → the assisted/larger features
  (`Home`/`PastSession`/`Exercise`/`SingleTraining`/`LiveWorkout`) → `Settings` → `RecoveryActivity` +
  `MetroWorkerFactory`. Per reader: replace its `appGraphContract()` read + positional `create(...)` list
  with **acquisition + injection** — acquire the narrow interface via `context.appDeps<XDeps>()` (the
  Worker uses its own point acquisition, NOT `appDeps`), then feed its members into the typed `create(...)`;
  introduce that reader's `XDeps`/consumer interface (+ AppGraph supertype) in the same commit. One
  reversible commit each.
- **C(n+1) — drop the two dead accessors** (`appReinitializer`, `liveDatabaseLocator`) — Gate 0 proved them
  unreachable-except-via-contract. *Known-negative:* assemble green (nothing referenced them).
- **C(last) — delete `AppGraphContract` + holder + accessor + module `core:di`.** Only when
  `git grep "appGraphContract("` across `feature/**` + `core/**` (excl defs / stale worktrees) = 0. Remove
  `:core:di` from `settings.gradle.kts` and every `implementation(project(":core:di"))`. Area-3's 8 `api`
  edges vanish here.

## Verification (corrected — C1 exposed the flaw: Metro resolves graph accessors BY TYPE, not by name)
Per migrated reader:
- **known-NEGATIVE (migration is real):** `git grep AppGraphContract` in that module == 0.
- **known-POSITIVE (composition is correct) — STRUCTURAL, not "it compiles":** the new interface's accessor
  set == that reader's exact consumed-set from the Gate-0 map (`gate0-discovery.md` §0.4). A compile-green
  graph does NOT prove correctness — Metro's type-only resolution means a graph with the wrong accessor
  composition can still compile. Verify the accessor list against the map.
- **Non-vacuity of an interface (used at introduction):** the naive name-rename negative
  (`analyticsHolder`→`…ZZZ`) is **VACUOUS** — a same-type renamed accessor still resolves by type and the
  build stays green (proven in C1). Instead: add an accessor of an **UNBOUND type** to the interface →
  `[Metro/MissingBinding]` at `AppGraph.<accessor>` proves genuine implementation. Revert after.
- **Dispatcher-qualifier rule (MANDATORY):** any dispatcher accessor on any `XDeps`/consumer interface MUST
  carry its qualifier annotation (`@DefaultDispatcher`/`@IODispatcher`/`@MainImmediateDispatcher`/
  `@MainDispatcher`) **copied VERBATIM** from `AppGraphContract`. Metro matches bindings by **(type +
  qualifier)**; two unqualified `CoroutineDispatcher` accessors would collide/mis-wire silently. Copy the
  full signature (qualifier included), never the bare type.

## Verification specifics (false-green prevention)
- Final: `git grep "AppGraphContract"` and `git grep "appGraphContract("` in real source (exclude
  `*/.claude/worktrees/*`, `*/build/*`, KDoc) = **0**.
- Coverage assertion: `⋃(new interfaces' accessors) == the 30 consumed accessors` from Gate 0 — diff and
  confirm zero delta.
- `detekt --no-daemon` on every gate (the stale-ruleset false-green bit before).
- If the working tree is dirty, read/verify against the git ref `feature/metro-batch`, not the working tree.

## Rollback / reversibility
Additive-first: the god-object survives until the final commit, so any mid-sequence stop leaves a working,
green tree. Every commit reverts cleanly. No irreversible boundary touched (no schema/DB/`dev`).

## Tangential (logged to tech-debt, not this work)
- The `internal`-contribution false-green: an `internal` `@ContributesBinding(AppScope)` silently fails to
  aggregate cross-module (Gate-0 Bonus: 20/20 contributors are effectively public because of this).
  Candidate detekt rule: a `@ContributesBinding` impl must be `public`. `ContributesBindingScopeRule` checks
  the scope arg, not visibility — an uncovered false-green.
