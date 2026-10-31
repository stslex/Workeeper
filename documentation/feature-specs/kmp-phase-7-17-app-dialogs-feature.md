# KMP Phase 7.17 — feature:app-dialogs, api and impl, become a shared feature entry

**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.

**Discovery baseline:** dev @ a4bcd440dc7420c80d16d9423990491f5a12fd4a (2026-10-29 05:06 +03:00, the
merge of #346, Phase 7.16 Part C). Its parents are `6d88cedcd` (the Part C SPEC commit) and
`fb648ab9a` (the PR head). Its push run `40804398303` concluded success, all five jobs.

**Template:** [Phase 7.16](kmp-phase-7-16-live-workout-feature.md), Part C (Sections C0–C17). Where
this document names a 7.16 procedure as inherited, it applies with the substitutions of Section 0.3.
Everything else is stated here.

**Measured by:** a static discovery of 2026-10-29 on git data, greps and scripts, and a cloud
prototype of all seven commits, with nothing compiled or run. The prototype is the reference patch
set of Appendix A. Its gate outputs, its Python-scored controls and its exit census were run on the
prototype (Sections 10, 13 and 14); that is a dry run, not evidence. The implementation's fresh runs
are the evidence.

---

## 0. Authority, rules, substitutions

### 0.1 Authority order

1. The maintainer's decisions D1–D7 (GO, 2026-10-29), as Section 1 records them.
2. This document: Sections 0–18 and Appendices A and B.
3. The Phase 7.16 specification's Part C, for each procedure this document names as inherited.
4. testing.md, ci-cd.md, architecture.md, AGENTS.md and CLAUDE.md.
5. The checked-in source, resources, tests and CI scripts at the implementation base.

A conflict between this document and a lower source is resolved here. A conflict this document does
not resolve is a STOP (Section 15).

### 0.2 Stable rules

7.16 Sections C0.2, B0.2 and 0.2 apply, with these changes and additions:

- **The move is the change.** Both modules move to the shared conventions, the catalogs become
  private Compose resources, and the code that cannot compile in common changes as Sections 5.1–5.7
  say. No behaviour changes beyond Section 3.
- **The reference patches are the implementation.** Each commit applies its patch from Appendix A,
  verifies the result's blob digest, and changes a line only under the repair rule of Section 0.4.
- **Build files, workflow, CI scripts.** Exactly the paths of Section 7 change.
  `gradle/libs.versions.toml` and `settings.gradle.kts` do not.
- **Catalogs.** Both move byte-identical (R100 in patch 5), `tools:ignore` and XML comments
  included (S6).
- **Suppressions.** No new suppression of any kind. The one production suppression,
  `@Suppress("UNCHECKED_CAST")` on the processor cast in `AppDialogFeature`, stays. No Native
  test-name suppression is added: S2 renames instead.
- **Goldens.** The modules have none, and none is recorded.
- **Census method.** 7.16 Section B0.2's: a call is read from its name to its closing parenthesis,
  across lines, with comments and string literals blanked.
- **Test helpers.** A test class's helper types are nested in it. A top-level test declaration's
  name is unique across the module's whole host compilation (commonTest and androidHostTest compile
  together).
- **Controls.** Every identity this phase adds or changes has a known-negative control that turns it
  RED, and so does every new gate rule (Section 13).
- **Lint.** Android Lint is compared with LINT_BASELINE: a finding the baseline lacks, or one on a
  Section 7 path, is a STOP.
- **Copying from this document.** A block copied from a fence ends at its last code line; the
  fence's trailing blank line is not part of it.
- **One test run before each commit.** The module's tests run on the staged tree before each commit,
  and the commit's gates then run on the committed tree.

### 0.3 Substitutions for the inherited 7.16 procedures

live-workout → app-dialogs; `feature/live-workout` → `feature/app-dialogs/impl` (and
`feature/app-dialogs/api` where the api is meant); `LiveWorkoutFeature` / `LiveWorkoutGraph` /
`createLiveWorkoutGraph` / `liveWorkoutGraphFactory` / `liveWorkoutGraph` → `AppDialogFeature` /
`AppDialogGraph` / `createAppDialogGraph` / `appDialogGraphFactory` / `AppDialogHost`; the sixteen
modules of the KMP iOS kit smoke job → the seventeen of Section 10.5; three Context readers before
and two after → two before and one after.

### 0.4 The repair rule

The reference patches were never compiled. A patch that applies but does not compile, or fails a
gate, is repaired only like this:

- **(a) Repair and record.** An import added, removed or reordered; a compile error fixed by a
  spelling that keeps the same call and the same meaning (a named argument, an explicit type, a
  qualified name); detekt or ktlint formatting; a wrapped line. The repair keeps every Section 10
  anchor, every identity's name, actions and assertions, the boundary of Section 7, and the gate
  output of Section 10.3. It goes into the same commit before the commit is made, and Section 19
  records it with its diff and the changed blob ids.
- **(b) STOP-and-amend,** with the compiler or test output: anything else. That includes a
  behaviour change, a changed identity, a new path, a gate datum, a build edge, the D5 store
  (Section 9.3) and the scene's mechanism (Section 9.4).

A failure found after its commit is fixed in a new signed commit inside Section 7, and every gate
that the fix can reach re-runs.

---

## 1. Decisions (D1–D7), as they bind

| Id | Decision |
| --- | --- |
| D1 | This phase moves `feature:app-dialogs:api` and `feature:app-dialogs:impl` to the shared conventions. settings and recovery import the api in production and impl is on app/common's path, so both come before app/common in any order; impl's seams are small (D3, D4). settings is Phase 7.18. |
| D2 | One PR into dev, seven signed, bisect-green commits (Section 16). |
| D3 | The dialogs' medium date leaves composition. `AppDialogRepoHandler` maps each emission to `dialog to dialog.toDateLabel(resourceWrapper)` in the flow's `.map { }`, before `.launch { }`, and writes both into State (`dateLabel` beside `current`). The mapper calls `ResourceWrapper.formatMediumDate`; the two dated dialogs read the label; `DialogDateFormatter` is deleted. On Android the expression is unchanged: `AndroidResourceWrapper.formatMediumDate` is `DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))`, as `DialogDateFormatter` was. |
| D4 | A commonMain `InstallEpochSource` (`suspend fun installEpoch(): InstallEpoch`) in backup:api. The androidMain `RestoreRecoveryFiles` extends it, `RestoreRecoveryFilesImpl` gains a third `@ContributesBinding`, and the repository's `@Inject` constructor takes it. |
| D5 | `AppDialogRepositoryTest` moves to commonTest on the real store: `PreferenceDataStoreFactory.createWithPath` over one fresh file per test in okio's `FileSystem.SYSTEM_TEMPORARY_DIRECTORY`, on JVM and Native, its 16 identities unchanged. If Native misbehaves, STOP-and-amend; the fallback is androidHostTest on a `File` (the discovery's option C). |
| D6 | The topology gate's `FeatureEntry` gains two additive fields with byte-identical defaults — `mount` (`"nav_host"` or `"app_root"`) and `app_aggregation` (`"implementation"` or `"api"`) — and `golden_test` may be empty. Every earlier entry's checks and failure text stay byte-identical, proved as 7.11 D2 and 7.16 D7 proved theirs (Section 10.0). |
| D7 | `Action.RepoAction.Publish` and `Action.RepoAction.Dismiss`, which have no producer, move unchanged and are recorded in tech-debt.md (Section 18). |

## 2. Spec-level items and corrections

### 2.1 S1–S10, accepted by the GO

| Id | Item |
| --- | --- |
| S1 | The api goes to `convention.kmpLibrary`; its five files move byte-identical into commonMain; its unused core:core edge goes. No consumer changes. |
| S2 | Two names carry characters Kotlin/Native rejects and are renamed (Section 9.1). The other 37 base names stay. |
| S3 | No MockK and no new production seam: the handler and observer suites run the real repository and observer (Section 9.2). |
| S4 | The failure-reason map returns `StringResource`, the same twelve codes onto the same ten keys. Three Light/Dark pairs become ThemeMode preview subjects; `RestoreSuccessDialog`'s three previews are pinned by their arguments (Section 5.7). |
| S5 | The root flow: `internal class AppDialogFeature(private val factory: AppDialogGraph.Factory)`, `AppDialogHost(factory)`, `AppRootDeps.appDialogGraphFactory` and AppGraph's override, `App.kt` passing `deps.appDialogGraphFactory`, app/common's edge as `api`, `EXPECTED_APP_DEPS_READERS` 2 → 1 (settings remains), and the identity test off `asContribution`. |
| S6 | Both catalogs move byte-identical, `tools:ignore` included, as live-workout's did. |
| S7 | The five suites go to commonTest with D3's new identities; the device class goes to androidDeviceTest; `DialogStateAcrossGenerationsTest` stays in app:app and runs in the device gates; recovery's `ForeignInstallTransferIntegrationTest` compiles unchanged, because its `RestoreRecoveryFilesImpl` is an `InstallEpochSource`. |
| S8 | An iOS scene renders every variant and dispatches every button (Section 9.4). |
| S9 | The PR-M review's exact-match fix for `mutation_harness.py`'s `_reused_outputs` rides in this PR as its own commit (Section 10.6). |
| S10 | The documents of Section 7.4. `.claude/skills/app-dialogs-pattern.md` stays untouched (7.10 D4) and is recorded (Section 18). |

### 2.2 Corrections to the discovery

- **XC1.** The discovery's "1 bare preview" is `@Preview(name = "No previous version",
  showBackground = true)` on `RestoreSuccessDialogNoUndoPreview`, an argumented one. It stays as it
  is and its file is pinned by `preview_inventory`, not `bare_previews` (Section 5.7).
- **XC2.** D6 needs a second field. app:app's edge on impl is `api(project(":feature:app-dialogs:impl"))`,
  and the gate required `implementation` for every entry, so `app_aggregation` joins `mount`.
- **XC3.** S3 is refined: the handler and observer suites run the real repository over an in-memory
  `DataStore<Preferences>` nested in each suite, not over D5's file store. A file store completes on
  its own threads, which `advanceUntilIdle()` cannot see, so the routing suites would race. Five
  MockK verifications become effects on the store (Section 9.2).
- **XC4.** D5's mechanism, measured in okio's sources: `FileSystem.SYSTEM_TEMPORARY_DIRECTORY` is a
  member of the common `FileSystem` companion, while `FileSystem.SYSTEM` is a companion member only
  on JVM and Native; common code reaches it only through a separately imported extension, which
  this phase does not use. So the common suite builds its paths from the temporary directory and
  deletes through `internal expect fun deleteStoreFile(path: Path)`, with one `actual` per platform
  calling the member. okio is not a direct dependency: it reaches the classpath through DataStore's
  `createWithPath(() -> okio.Path)`, as core:data:dataStore's commonMain already uses it. The
  store's scope is `Dispatchers.Default + Job()`.
- **XC5.** S9's stated reason was weak: no 7.17 control trips the substring defect. It rides along
  because it was accepted. The review's literal fix (`task_path == needle`) breaks unqualified task
  names (`detekt`, `testDebugUnitTest`), so the fix matches a qualified needle exactly and an
  unqualified one by its last path segment (Section 10.6).
- **XC6.** The discovery's "eleven entries" are nine `FeatureEntry` entries; plan-editor and
  image-viewer have their own checkers.
- **XC7.** S8 is refined: the scene drives the production `AppDialogHostContent` and the production
  date mapper from test-held State, as the 7.11–7.16 scenes drive their screens. The Store needs the
  app graph's dispatchers and lifetime; its wiring is proven by the four extension identities and
  the app journeys of Section 12.

## 3. Observable differences

- **OC1.** The dialogs' strings now resolve through Compose resources, which choose by the primary
  system locale only (Phase 7.9 Section 5.7). On a device whose first locale has no catalog, the
  dialogs fall back to the EN catalog where Android resources walked the locale list. EN and RU
  devices see what they saw.
- **OC2.** None in the date. The expression is the same (D3), and `MainActivity` absorbs locale
  changes without recreation (7.16 XC1), so the old `remember(epoch)` and the new State field both
  keep their value until the next emission.
- **OC3.** None in the Store's lifetime. `AppDialogFeature` is now built per generation from the
  generation's factory, under `remember(factory)`; the Store is still retained by
  `rememberMetroStoreProcessor` in the generation's `ViewModelStoreOwner`.

## 4. Bounded exit claim

At exit:

- `feature:app-dialogs:api` is a KMP library: five files in commonMain, compiled for Android,
  `iosArm64` and `iosSimulatorArm64`, with no Android type.
- `feature:app-dialogs:impl` is a shared feature entry mounted at the app root: production in
  commonMain for the same three targets; private Compose resources; the factory flow of Section 5.5;
  no `Context`, no `java.*`, no `android.*`, and no date formatted in composition (Section 14).
- Its six portable suites, 43 exact identities, run in commonTest on the Android host and on the iOS
  simulator; the scene adds one Native identity (Section 9).
- The device class's 10 identities run from androidDeviceTest; the 4 extension identities reach the
  graph through `AppRootDeps`.
- The topology gate holds the module to `APP_DIALOGS_ENTRY` (Section 10.2) through D6's fields; the
  Native oracle requires 44 exact tuples among seventeen modules; the KMP iOS kit smoke job runs and
  links seventeen modules.
- The Context readers outside shared entries drop from 2 to 1 (settings).

Not claimed: anything Section 9.6 lists, an iOS host's bindings (`InstallEpochSource`,
`ResourceWrapper`), or a device run of the Native code.

## 5. Contracts

Paths below are relative to `feature/app-dialogs/impl/src/<set>/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/`
unless they start with `src/`, `app/`, `core/`, `feature/`, `.github/` or `documentation/`.

### 5.1 The api (S1, commit 3)

The five files (`model/AppDialog.kt`, `model/AppDialogUserAction.kt`, `model/AppDialogUserChoice.kt`,
`observer/AppDialogObserver.kt`, `publisher/AppDialogPublisher.kt`) move from `src/main` to
`src/commonMain` byte-identical (R100 in patch 3). The build file becomes:

```kotlin
plugins {
    alias(libs.plugins.convention.kmpLibrary)
}

// The cross-feature dialog catalog's contract: its models, the publisher and the observer, in pure
// Kotlin over backup:api's common restore types (Phase 7.17 S1).
kotlin {
    sourceSets {
        commonMain.dependencies {
            // AppDialog's variants carry RestoreOwnerId, UndoRef and BackupErrorCode.
            implementation(project(":core:data:backup:api"))
            // AppDialogObserver's Flow.
            implementation(libs.coroutines.core)
        }
    }
}
```

The consumers keep their own edges: app:app `api`, settings and recovery `implementation`.

### 5.2 D4 — the install-epoch seam (commit 3)

- New `core/data/backup/api/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/backup/api/restore/InstallEpochSource.kt`:
  `interface InstallEpochSource { suspend fun installEpoch(): InstallEpoch }`.
- `RestoreRecoveryFiles` (backup:api androidMain) becomes `interface RestoreRecoveryFiles :
  InstallEpochSource`, and its `installEpoch()` gains `override`.
- `RestoreRecoveryFilesImpl` (core:data:database androidMain) gains
  `@ContributesBinding(AppScope::class, binding = binding<InstallEpochSource>())` beside its two
  bindings.
- `AppDialogRepository`'s `@Inject` constructor takes `installEpochSource: InstallEpochSource` in
  place of `RestoreRecoveryFiles` and passes `installEpochSource::installEpoch`.

### 5.3 D3 — the date in State (commit 4, still in `src/main`)

- `AppDialogStore.State` gains `val dateLabel: String`; `EMPTY` is `State(current = null,
  dateLabel = "")`.
- New `mvi/mapper/AppDialogDateMapper.kt`: `internal fun AppDialog?.toDateLabel(resourceWrapper:
  ResourceWrapper): String` — a restore success formats `restoredAtEpochMs`, an undo confirmation
  `originalDataDateEpochMs`, and a failure, an undo success or `null` give `""`.
- `AppDialogRepoHandler` takes `resourceWrapper: ResourceWrapper` after the repository, maps the
  flow before the launch, and writes `it.copy(current = dialog, dateLabel = dateLabel)`.
- `AppDialogHost` passes `dateLabel = state.dateLabel`; `AppDialogHostContent` takes `dateLabel`
  and hands it to `RestoreSuccessDialog` and `UndoRestoreConfirmationDialog`, which read it in
  place of their `remember { formatMediumDate(…) }` blocks.
- `ui/DialogDateFormatter.kt` is deleted.

### 5.4 The shared module (commit 5)

`src` holds 34 paths at exit:

| Source set | Paths |
| --- | --- |
| commonMain | 21 Kotlin files and the two catalogs. 15 Kotlin files move byte-identical; `di/AppDialogFeature.kt`, `ui/AppDialogHost.kt` and the four `ui/*Dialog.kt` change (Sections 5.5–5.7) |
| commonTest | the six suites and `data/TestStoreFile.kt` (D5's `expect`) |
| androidHostTest | `data/TestStoreFile.android.kt` |
| iosTest | `data/TestStoreFile.ios.kt` and `AppDialogFeatureSceneIosTest.kt` |
| androidDeviceTest | `ui/AppDialogHostContentTest.kt`, byte-identical |

`src/main`, `src/test` and `src/androidTest` are gone. The build file is Section 8's.

### 5.5 The root flow (S5, commit 5)

- `AppDialogFeature` is `internal class AppDialogFeature(private val factory: AppDialogGraph.Factory)
  : AppFeature<AppDialogStoreProcessor>()`. Its `processor()` creates the graph extension only
  inside `rememberMetroStoreProcessor<AppDialogStoreImpl> { factory.createAppDialogGraph().appDialogStore }`.
- `AppDialogHost(factory: AppDialogGraph.Factory,)` builds `remember(factory) { AppDialogFeature(factory) }`.
- `App.kt` calls `AppDialogHost(factory = deps.appDialogGraphFactory)` in `AppGenerationContent`,
  where it called `AppDialogHost()`; `AppNavigationHost` does not change.
- `AppRootDeps` declares `val appDialogGraphFactory: AppDialogGraph.Factory`; `AppGraph` overrides
  it.
- app/common's edge becomes `api(project(":feature:app-dialogs:impl"))`; app:app keeps its `api`
  edge.
- `AppDialogExtensionIdentityTest`'s helper becomes `private fun AppGraph.appDialogs():
  AppDialogGraph = appDialogGraphFactory.createAppDialogGraph()`; its four names stay.

### 5.6 Resources (S4, S6, commit 5)

- The catalogs: `src/main/res/values{,-ru}/strings.xml` → `src/commonMain/composeResources/values{,-ru}/strings.xml`,
  byte-identical: 27 strings per locale, prefix `app_dialog_`, four positional `%1$s` per locale,
  `tools:ignore="DuplicateStrings"` EN 6 / RU 3.
- `packageOfResClass = "io.github.stslex.workeeper.feature.app_dialogs.impl.resources"`; the
  generated `Res` stays internal.
- Every `stringResource(R.string.x, …)` becomes CMP's `stringResource(Res.string.x, …)`, each key
  imported from the resources package.
- `RestoreFailureDialog`'s `@StringRes private fun BackupErrorCode.toReasonStringRes(): Int` becomes
  `private fun BackupErrorCode.toReasonStringRes(): StringResource`, branch for branch.

### 5.7 Previews (S4, commit 5)

Nine `@Preview` annotations stay nine, and `uiMode` goes:

- `RestoreFailureDialog`, `UndoRestoreConfirmationDialog` and `UndoRestoreSuccessDialog` are
  ThemeMode subjects: `@Preview(name = "Light", showBackground = true)` on `<Subject>LightPreview()`
  calling `<Subject>Preview(themeMode = ThemeMode.LIGHT)`, Dark likewise, and a private
  `<Subject>Preview(themeMode: ThemeMode)` wrapping `AppTheme(themeMode = themeMode)`.
- `RestoreSuccessDialog` keeps three: its with-undo pair becomes the
  `RestoreSuccessDialogWithUndo{Light,Dark}Preview` subject shape, and `@Preview(name = "No previous
  version", showBackground = true)` stays on `RestoreSuccessDialogNoUndoPreview` with `AppTheme { }`.

## 6. Non-goals

settings (7.18), recovery and app/common; deleting D7's actions; any new `AppDialog` variant; the
Activity-scoped wording outside app-dialogs.md's mounting section; the skills file; iOS bindings
for `InstallEpochSource` and `ResourceWrapper`; a portable date formatter; a runtime RU test.

## 7. Exact change boundary

### 7.1 The modules

- `feature/app-dialogs/api/`: 6 tracked paths before and after. Five move (R100), the build file
  changes.
- `feature/app-dialogs/impl/`: 30 tracked paths before, 35 after (the build file and Section 5.4's
  34). With `--no-renames`: 29 deleted, 34 added, the build file modified.

### 7.2 Outside the modules — 17 paths and this document

| Path | Commit |
| --- | --- |
| `.github/scripts/assert_kmp_ui_source_topology.py` | 1, 3, 5, 6 |
| `documentation/mockups/mutation_harness.py` | 2 |
| `core/data/backup/api/src/commonMain/.../restore/InstallEpochSource.kt` (added) | 3 |
| `core/data/backup/api/src/androidMain/.../restore/RestoreRecoveryFiles.kt` | 3 |
| `core/data/database/src/androidMain/.../snapshot/RestoreRecoveryFilesImpl.kt` | 3 |
| `app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt` | 5 |
| `app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt` | 5 |
| `app/common/build.gradle.kts` | 5 |
| `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt` | 5 |
| `app/app/src/test/kotlin/io/github/stslex/workeeper/di/AppDialogExtensionIdentityTest.kt` | 5 |
| `.github/scripts/assert_kmp_ios_smoke.py` | 6 |
| `.github/workflows/android_build_unified.yml` | 6 |
| `documentation/architecture.md` | 7 |
| `documentation/ci-cd.md` | 7 |
| `documentation/testing.md` | 7 |
| `documentation/tech-debt.md` | 7 |
| `documentation/feature-specs/app-dialogs.md` | 7 |
| this document (Status and Section 19) | 7 |

No lint-rules path changes: `ActiveSurfaceSingleReaderRule`'s readers name live-workout only.

### 7.3 Per commit

With `git diff --no-renames --name-status <parent> <commit>`:

| Commit | Paths | A | D | M | `--numstat` sum |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 | 1 | 0 | 0 | 1 | +71 −27 |
| 2 | 1 | 0 | 0 | 1 | +15 −3 |
| 3 | 16 | 6 | 5 | 5 | +176 −137 |
| 4 | 11 | 2 | 1 | 8 | +175 −38 |
| 5 | 71 | 34 | 30 | 7 | +3052 −2315 |
| 6 | 3 | 0 | 0 | 3 | +383 −7 |
| 7 | 5, and this document | 0 | 0 | 5 | +124 −60, and this document |

The PR from the base: 92 paths (40 A, 34 D, 18 M) and this document (M).

### 7.4 What each document gains (commit 7)

- **architecture.md:** after the shape-B item, the app-root `AppFeature` paragraph (app-dialogs its
  one user, its factory from the admitted generation); the Context-seam paragraph names
  app-dialogs and `AppGraph.appDialogGraphFactory` and ends "Exactly 1 feature Context reader
  remains: settings."
- **ci-cd.md:** the table row, the KMP-shaped module list (`:feature:app-dialogs:api` and
  `:feature:app-dialogs:impl`), the iOS job's module description, the verdict list, the validator
  paragraph (44 target tuples, "All seventeen result directories") and the link paragraph (`N/17`).
- **testing.md:** a new "Shared app-dialogs feature" section before "Shared string resolver"; the
  iOS commands for 17 modules and `17/17`.
- **tech-debt.md:** the two `src/main` links re-pointed at `src/commonMain`; app-dialogs named in
  three rows (the typed processor's suppressions, the portable date formatter, the RU runtime
  coverage); a new section "`feature:app-dialogs` carried items" with D7's row and the
  Activity-scoped wording's row.
- **feature-specs/app-dialogs.md:** "AppDialogHost mounting" rewritten for the generation-owned
  factory flow and the generation-scoped Store; the "Action surface" State gains `dateLabel`.
- **This document:** the Status line becomes "**Status:** COMPLETE — implemented by the PR whose
  merge brought this line to dev." and Section 19 gets its record. Nothing else in it changes.

Patch 7 carries every documentation change except this document's two.

## 8. The exit build file of impl (normative)

```kotlin
plugins {
    alias(libs.plugins.convention.kmpComposeLibrary)
    // Screen-less, mounted at the app root (no route, no nav-host registration) and golden-free.
    alias(libs.plugins.metro)
}

compose.resources {
    packageOfResClass = "io.github.stslex.workeeper.feature.app_dialogs.impl.resources"
}

// Metro reads javax.inject qualifiers so the inherited app-scope qualifiers keep their keys.
metro {
    interop {
        includeJavax()
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // No public signature names its types: the graph's AppScope annotations reach the app
            // through the app's own core:core edge, and ResourceWrapper only the internal handler.
            implementation(project(":core:core"))
            // The dialog primitives and the theme; no public declaration names a kit type.
            implementation(project(":core:ui:kit"))
            // `api`: AppDialogStore, its handler store and its Store impl extend the MVI contracts.
            api(project(":core:ui:mvi"))
            // `api`: the repository implements AppDialogPublisher and State carries AppDialog.
            api(project(":feature:app-dialogs:api"))
            // `api`: the repository's @Inject constructor takes InstallEpochSource (D4) and a
            // DataStoreProviderFactory.
            api(project(":core:data:backup:api"))
            api(project(":core:data:dataStore"))
            // The repository's DataStore<Preferences>, through the common preferences API only.
            implementation(libs.androidx.datastore.core)
            implementation(libs.androidx.datastore.preferences.core)
            // `api`: currentDialog is a Flow and the observer returns a SharedFlow.
            api(libs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutine.test)
        }

        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.cmp.ui.test)
        }
    }
}

dependencies {
    "androidDeviceTestImplementation"(libs.bundles.android.test)
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.junit4)
    // GUARD: ui-test-manifest is versionless in the catalog; classic modules resolve it through
    // the convention's compose BOM, which the KMP convention does not add — so add it here.
    "androidDeviceTestImplementation"(platform(libs.androidx.compose.bom))
    "androidDeviceTestImplementation"(libs.androidx.compose.ui.test.manifest)
    // GUARD: carries the @Smoke / @Regression annotations — without this edge androidx.test
    // silently drops ui_tests.yml's filter. Enforced by `verifyInstrumentedSuiteClasspath`.
    "androidDeviceTestImplementation"(project(":core:ui:test-utils"))
}
```

No `androidMain` or `iosMain` source set, no Paparazzi, no golden gate, and no MockK, Robolectric,
`testImplementation` or `debugImplementation` line. After commit 5, the module's Gradle metadata
publishes the Android, `iosArm64` and `iosSimulatorArm64` variants, and a classic consumer
(app/common, app:app, settings, recovery) resolves the Android one.

## 9. Tests and the Native scene

### 9.1 The port — five suites into commonTest, and S2

Every base identity keeps its name, its actions and its assertions; only the fakes around it and
the assertion library change. `org.junit.jupiter` → `kotlin.test`.

- `AppDialogRepositoryTest` (16): D5's store (Section 9.3). S2 renames `publish then dismiss then
  publish same variant succeeds (flag was cleared)` → `publish then dismiss then publish same
  variant succeeds — the flag was cleared`.
- `AppDialogResolverTest` (12): `assertEquals(AppDialog.RestoreSuccess::class.java,
  AppDialogResolver(prefs)!!.javaClass)` → `assertEquals(AppDialog.RestoreSuccess::class,
  AppDialogResolver(prefs)!!::class)`.
- `AppDialogRepoHandlerTest` (4 + D3's 1), `ChooseHandlerTest` (2), `AppDialogObserverImplTest`
  (5): Section 9.2. S2 renames `late subscriber does NOT receive past emissions (replay = 0, no
  persisted-choice replay)` → `late subscriber does NOT receive past emissions — replay = 0 and no
  persisted-choice replay`; `assert(emitJob.isCompleted) { … }` → `assertTrue(emitJob.isCompleted,
  …)`.
- `AppDialogDateMapperTest` (3, added in commit 4) moves as it is.

### 9.2 S3 — the routing suites on the real repository

Each of `AppDialogRepoHandlerTest`, `ChooseHandlerTest` and `AppDialogObserverImplTest` nests an
`InMemoryPreferences : DataStore<Preferences>` — a `MutableStateFlow(emptyPreferences())` whose
`updateData` applies the transform in place — and builds `AppDialogRepository(InMemoryPreferences())
{ EPOCH }`. The handler suites nest the base's `TestStore` with two changes — an `initial` State
parameter, and flow collection in `backgroundScope`, because the real repository's flow never
completes — and a no-op `SilentLogger` (the relaxed mock's behaviour). The five MockK
verifications become effects:

| Identity | Base check | Exit check |
| --- | --- | --- |
| `Publish forwards dialog to repository` | `coVerify(exactly = 1) { repository.publish(dialog) }` | `repository.currentDialog.first()` is the dialog |
| `Dismiss forwards dialog to repository` | `coVerify(exactly = 1) { repository.dismiss(dialog) }` | the dialog is published first, then `currentDialog.first()` is `null` |
| `Choose emits the choice to the observer` | `coVerify(exactly = 1) { observer.emit(choice) }` | a `backgroundScope` subscriber receives exactly `[choice]` |
| `Choose does NOT dismiss the dialog — the consumer is responsible` | `coVerify(exactly = 0) { observer.acknowledgeReaction(any()) }` | the dialog is published first and is still `currentDialog.first()` |
| `acknowledgeReaction delegates to repository dismiss` | `coVerify(exactly = 1) { repository.dismiss(dialog) }` | the dialog is published first, then `currentDialog.first()` is `null` |

The two Observe identities read the real repository: `Observe updates state when repository emits
a dialog` publishes first; `Observe sets state to null when repository emits null` seeds the
`TestStore` with a stale `State(current = AppDialog.UndoRestoreSuccess(), dateLabel = "stale")`, so
a handler that dropped the null would leave it showing.

### 9.3 D3's identities and D5's store

D3 adds four identities, all in commit 4:

- `AppDialogRepoHandlerTest`: `Observe writes the dialog's formatted date beside it` — an undo
  confirmation at 2 000 ms through a `ResourceWrapper` whose `formatMediumDate` answers
  `"medium:$timestamp"` gives `State(current = dialog, dateLabel = "medium:2000")`.
- `AppDialogDateMapperTest`: `a restore success labels the date its backup was restored`, `an undo
  confirmation labels the date of the data it brings back`, and `a failure or an undo success or no
  dialog labels nothing and formats nothing`, on a recording `ResourceWrapper`.

D5: each `AppDialogRepositoryTest` identity opens `PreferenceDataStoreFactory.createWithPath(scope =
CoroutineScope(Dispatchers.Default + Job())) { storeFile }` over
`FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "app_dialogs_${Uuid.random()}.preferences_pb"`, cancels the
scope after the identity and calls `deleteStoreFile(storeFile)`. Every path is fresh, so DataStore's
one-instance-per-file rule never meets a second store. This is the repository's first real DataStore
in commonTest and its first on Native.

### 9.4 The Native scene

`AppDialogFeatureSceneIosTest.resourcesDatesVariantsAndChoicesRenderAndDispatch`, in iosTest:

1. The EN catalog in file order through one real `ComposeStringResolver`: the 27 strings as
   stored, then the four placeholder entries filled (`Nov 14, 2023`, `no network`, `Apr 15, 2022`).
2. The production `toDateLabel` with a stubbed `formatMediumDate`, the scene's only test double
   (1 700 000 000 000 → `Nov 14, 2023`, 1 650 000 000 000 → `Apr 15, 2022`, anything else throws):
   the two successes and the confirmation dated, a failure, the undo success and `null` empty.
3. The production `AppDialogHostContent` under `AppTheme`, from test-held State, `dateLabel` from
   the mapper: nothing; the success with a previous version (its dated body, Undo restore, OK); the
   success without one (its short dated body, no Undo restore); the failure for each of the twelve
   `BackupErrorCode`s in an order where no two neighbours share a reason, each body carrying its EN
   reason, then Export diagnostics, Report and OK on the last; the undo confirmation (its dated
   body, Undo restore, Cancel); the undo success (its body, OK).
4. The nine choices as one exact ordered list: RequestUndo, Acknowledge, Acknowledge,
   ExportDiagnostics, Report, Acknowledge, ConfirmUndo, Cancel, Acknowledge.

Settling is bounded: ten steps of one virtual second. Every expected string is the moved EN
catalog's; none is derived from the running code.

### 9.5 Identities at exit — 43 portable, 1 Native, 10 device, 4 extension

The portable names are exactly `APP_DIALOGS_ENTRY.test_names` in patch 6 (repository 16, resolver
12, repo handler 5, observer 5, date mapper 3, choose handler 2); the Native one is Section 9.4's;
the device class's 10 and `AppDialogExtensionIdentityTest`'s 4 keep their base names. The host
compilation runs the 43 in 6 suites; the Native XML holds 44 passing cases in 7 suites.

### 9.6 What this does not cover

RU values, which the gate pins statically; `AppDialogHost` itself, whose two State reads the gate
pins (Section 10.2); the Store and its graph, which `AppDialogExtensionIdentityTest` and the app
journeys of Section 12 cover on Android; the iosArm64 test binaries, which are linked, never run.

## 10. CI ownership

### 10.0 D6 — `mount` and `app_aggregation` (commit 1)

Patch 1 changes the topology script only (+71 −27): the two fields after `state_mutators`, an
optional `golden_test` (with an empty one, `golden_methods` must be empty), the mount branch (for
`app_root`: App.kt must call `{graph_function}(factory = deps.{factory_accessor})` once, the graph
file must hold `factory: {Graph}.Factory,` and `remember(factory) { {Feature}(factory) }` once, and
`AppNavigationHost` must not name the accessor), and the app:app edge read through
`app_aggregation`. The defaults keep every earlier entry byte-identical.

**The proof,** on commit 1's tree, under every Python: `diffproof.py` (Appendix B) runs the base
script (`old_gate.py`) and the new one on the same tree.

| Row | Mutation | Required, both scripts, identical |
| --- | --- | --- |
| GREEN | none | exit 0, 990 lines, sha256 `7271a2b1655ca7f294ed45ae2fa188c26dfd634c70071f5b737da8c6adc7f8d4` |
| M1 | AppNavigationHost: `archiveGraph(` loses its `factory = archiveGraphFactory,` line | RED: `feature:archive: exact root-factory flow requires 'archiveGraph(factory = archiveGraphFactory,' once in app/common/.../AppNavigationHost.kt` |
| M2 | app/app/build.gradle.kts: `implementation(project(":feature:home"))` → `api(…)` | RED: `app:app: direct home aggregation edge must remain implementation` |
| M3 | PastSessionGoldenTest: `fun screenLoaded(` → `fun screenLoadedProbe(` | RED: the golden-methods line |
| Q1 | App.kt: `// AppDialogHost(factory = deps.archiveGraphFactory)` after `AppDialogHost()` | GREEN |

Each mutation is restored byte-exactly (sha256 and mode), and a fresh GREEN after the last equals
the first. The script prints `PROOF OK`.

### 10.1 MODULES and the reader set (commits 3 and 5)

- Commit 3 adds `"feature:app-dialogs:api"` to `MODULES`: its five commonMain paths,
  `kotlin_source_sets = {"commonMain"}`, `resource_dirs = set()`.
- Commit 5 adds `"feature:app-dialogs:impl"`: Section 5.4's 34 paths, `kotlin_source_sets =
  {"commonMain", "commonTest", "androidHostTest", "androidDeviceTest", "iosTest"}`, both
  `composeResources` directories; and `EXPECTED_APP_DEPS_READERS` loses app-dialogs, keeping
  settings alone.

### 10.2 `APP_DIALOGS_ENTRY` and `app_dialogs_contract` (commit 6)

Patch 6 adds the entry before `FEATURE_ENTRIES` and appends it there. Its data:

| Field | Value |
| --- | --- |
| module / short_name / root | `feature:app-dialogs:impl` / `app-dialogs` / `feature/app-dialogs/impl` |
| build_fragments | every plugin, resource-package, interop and dependency line of Section 8, each once |
| kotlin_test_count | 2 |
| catalog | the 27 (tag, name, EN, RU) rows in file order |
| resource_prefix | `app_dialog_` |
| payload_files | `{"AppDialogStore.kt"}` (with `domain/AppDialogResolver.kt` by the domain rule) |
| store_file / state_fields | `mvi/store/AppDialogStore.kt` / `(("current", "AppDialog?"), ("dateLabel", "String"))` |
| resource_wrapper_readers | `mvi/handler/AppDialogRepoHandler.kt`, `mvi/mapper/AppDialogDateMapper.kt` |
| resource_wrapper_dates | `{"mvi/mapper/AppDialogDateMapper.kt": {"formatMediumDate": 2}}` |
| preview_subjects | `RestoreFailureDialog`, `UndoRestoreConfirmationDialog`, `UndoRestoreSuccessDialog` |
| preview_inventory | `ui/RestoreSuccessDialog.kt`: `name = "Light", showBackground = true`, `name = "Dark", showBackground = true`, `name = "No previous version", showBackground = true` |
| suppressions | `AppDialogFeature.kt`: `"UNCHECKED_CAST"` |
| test_names | Section 9.5's 43 and the scene |
| golden_test / golden_methods | `""` / `[]` |
| device_test / device_fragments | `src/androidDeviceTest/.../ui/AppDialogHostContentTest.kt` / `@Smoke` with its `@RunWith` line, and the ten `fun …()` declarations |
| feature_class / graph_interface / graph_function | `AppDialogFeature` / `AppDialogGraph` / `AppDialogHost` |
| factory_accessor / create_method / store_impl / store_accessor | `appDialogGraphFactory` / `createAppDialogGraph` / `AppDialogStoreImpl` / `appDialogStore` |
| feature_file / graph_file | `di/AppDialogFeature.kt` / `ui/AppDialogHost.kt` |
| identity_test / identity_fragments | `AppDialogExtensionIdentityTest.kt` / Section 5.5's helper |
| identity_names | the four base names |
| identity_accessor | `val (?:store\|extension\|first) = (?:buildAppGraph\(\)\|appGraph)\.appDialogs\(\)`, which counts 4 |
| mount / app_aggregation | `"app_root"` / `"api"` |
| extra_checks | `(app_dialogs_contract,)` |

`app_dialogs_contract` holds three things the shared checks do not:

- **S4's map.** The `when` of `toReasonStringRes` maps exactly the twelve codes onto the ten keys of
  `APP_DIALOGS_REASON_KEYS` (InsufficientLocalStorage, StorageCapacityUnavailable and Io onto
  `…_reason_io`).
- **D3's path,** each fragment once, comments blanked: the mapper's three branches, and
  `AppDialogHost`'s `current = state.current,` and `dateLabel = state.dateLabel,`.
- **The edge set.** The build file's project edges are exactly core:core, core:data:backup:api,
  core:data:dataStore, core:ui:kit, core:ui:mvi, core:ui:test-utils and feature:app-dialogs:api.

### 10.3 The topology gate's output per commit

Under every Python the host has, byte-identical, exit 0, empty stderr:

| After | Lines | sha256 |
| --- | ---: | --- |
| base, 1, 2 | 990 | `7271a2b1655ca7f294ed45ae2fa188c26dfd634c70071f5b737da8c6adc7f8d4` |
| 3, 4 | 996 | `3203da7840da5d081ecb45161839b1c4a5ecd0d05c1bfc87eb9e05fb83832510` |
| 5 | 1033 | `2f2dc0f914b7e821d40d3c1fa8c70f6b819a3afbf409cd48b11bab44be4f4197` |
| 6, 7 | 1034 | `ce7dfb815d27a058daba246b1beb9cc069368491b977f6fbb743025c3a171522` |

Commit 3 adds the api's block (6 lines). Commit 5 adds the impl's block (35) and its two catalogs'
placeholder lines (2), and changes two: `2 remaining Context.appDeps readers` → `1 remaining`, and
`28 commonMain catalogs walked` → `30`. Commit 6 adds the entry's verdict line.

### 10.4 The Native oracle (commit 6)

`assert_kmp_ios_smoke.py` gains the `feature:app-dialogs:impl` entry, results directory
`feature/app-dialogs/impl/build/test-results/iosSimulatorArm64Test`, 44 tuples: the six suites'
43 names (16/12/5/2/3/5) and the scene, UTF-8.

### 10.5 The workflow (commit 6)

The KMP iOS kit smoke job: `:feature:app-dialogs:impl:iosSimulatorArm64Test` after live-workout in
the native step; `:feature:app-dialogs:impl:linkDebugTestIosArm64` in the link step;
`feature/app-dialogs/impl` in the binary loop, which prints `$linked/17 linked`; its results
directory in the upload; the header comment names app-dialogs and seventeen modules. The api has no
test and joins no list.

### 10.6 S9 — the harness (commit 2)

`_reused_outputs` reads each `> Task` line's path (`parts[2]`): a qualified needle (`:m:task`) must
equal it, an unqualified one (`detekt`) matches any path ending in `:needle`. `harness_reuse_check.py`
(Appendix B) runs eight synthetic logs: on commit 2's harness all eight hold; on the base harness S1
(`:lint-rules:test` against `testClasses`) and S4 (`detekt` against `detektMain`) are wrong, which is
the fix's RED control.

## 11. Entry gate (before any edit)

1. The base is origin/dev after Phase 0 (this document's commit); record its SHA.
2. The latest push run of "Android CI/CD - Unified Build and Tests" on dev: id, head and conclusion
   (`40804398303` for `a4bcd440d` concluded success). A red run is a STOP.
3. Drift: no Section 7 path changed since `a4bcd440d` except this document's landing. List every
   commit since then that touches one.
4. Each patch's sha256 and Appendix A's agree, and `git apply --check` accepts patch 1 on the base.
5. `census.py` on the base exits 1, every item's tree value equal to its base value (Section 14).
6. Baselines, each per 7.16's R4 and R10, with fresh XML:
   - the topology script under every Python, byte-identical (990 lines, `7271a2b1…`, exit 0, empty
     stderr), kept as BASE_GATE_OUTPUT, and the script saved aside as `old_gate.py` (sha256
     `b07bfaa412465041cc475e851af3b84fd4f2bac154366c75b2a08c180c681c7f`);
   - `assert_mvi_source_topology.py`, kept as BASE_MVI_TOPOLOGY;
   - `./gradlew assembleDebug lintDebug testDebugUnitTest --continue`: every module/task row's
     suites, cases and names (HOST_BASELINE; app-dialogs:impl 5 suites and 39 cases), and every
     module's lint findings by id, path and line text (LINT_BASELINE);
   - `:lint-rules:test`;
   - the sixteen-module Native command and `assert_kmp_ios_smoke.py` (NATIVE_BASELINE), and the
     sixteen-module link (`16/16`);
   - on the recorded serial: `:feature:app-dialogs:impl:connectedDebugAndroidTest` (10 cases) and
     APP_CLASSES, the five app classes that drive the root mount through a generation —
     `DialogStateAcrossGenerationsTest` (1), `AppScopeDataStoreSingletonTest` (6),
     `UiAdmissionRaceTest` (3), `UiGenerationSwapTest` (1) and `RuntimeGenerationSwapDeviceTest` (1),
     12 cases — run as

         ANDROID_SERIAL=<serial> ./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.DialogStateAcrossGenerationsTest,io.github.stslex.workeeper.app.AppScopeDataStoreSingletonTest,io.github.stslex.workeeper.app.UiAdmissionRaceTest,io.github.stslex.workeeper.app.UiGenerationSwapTest,io.github.stslex.workeeper.app.RuntimeGenerationSwapDeviceTest --max-workers=2 --continue

## 12. Gates

### 12.1 Per commit

Each per 7.16's R4 and R10, with fresh XML, after the commit:

- **1.** The topology script = BASE_GATE_OUTPUT under every Python; Section 10.0's proof; detekt
  alone.
- **2.** The topology script = BASE_GATE_OUTPUT; `harness_reuse_check.py` 8 cases, 0 wrong on MAIN
  and S1 and S4 wrong on a copy of the base harness; detekt alone.
- **3.** The topology script: 996 lines, `3203da78…`; `:feature:app-dialogs:api:assembleDebug` (its
  iOS klibs included); `:core:data:backup:api:assembleDebug`; `testDebugUnitTest` of
  core:data:database, core:data:backup:scheduling, feature:app-dialogs:impl (5 suites, 39 cases),
  feature:recovery and app:app (every module that names `RestoreRecoveryFiles` or `InstallEpoch`),
  each equal to HOST_BASELINE; `:feature:settings:assembleDebug`; `:app:dev:assembleDebug` (Metro
  validates the graph and the third binding there); detekt alone.
- **4.** The topology script = commit 3's; `:feature:app-dialogs:impl:testDebugUnitTest` (6 suites,
  43 cases: the base 39 and D3's 4); `:app:dev:assembleDebug`; on the serial the module's device
  class (10); detekt alone.
- **5.** The topology script: 1033 lines, `2f2dc0f9…`; `:feature:app-dialogs:api:assembleDebug` and
  `:feature:app-dialogs:impl:assembleDebug`; `:feature:app-dialogs:impl:testAndroidHostTest` (6
  suites, 43 cases), `iosSimulatorArm64Test` (7 suites, 44 cases: Section 9.5's names and the scene)
  and `linkDebugTestIosArm64`; `:app:common:assembleDebug` and `:app:dev:assembleDebug`; app:app's
  host tests with the 4 `AppDialogExtensionIdentityTest` cases; feature:recovery's and
  feature:settings' `testDebugUnitTest`, equal to HOST_BASELINE; `:lint-rules:test`; on the serial
  the module's device class (10) and APP_CLASSES (12); the census with exactly E19, E20 and E21 off
  (commit 6's items); detekt alone.
- **6.** The topology script: 1034 lines, `ce7dfb81…`; the seventeen-module Native command
  (`--continue`) and the oracle: 17 modules, app-dialogs' `44 executed / 0 skipped / 0 failed / 0
  errored — verified 44 exact identities`; the seventeen-module link (`17/17`); the census exits 0;
  detekt alone.
- **7.** The topology script = commit 6's; `assert_mvi_source_topology.py` = BASE_MVI_TOPOLOGY;
  `python3 documentation/personal_data_gate.py -v`; detekt alone; the diff check of Section 17.

### 12.2 The repository, Native and device gates (commit 6's tree, after Sections 13 and 14)

1. `./gradlew assembleDebug lintDebug testDebugUnitTest --continue`: every module equal to
   HOST_BASELINE except feature:app-dialogs:impl (5 → 6 suites, 39 → 43 cases), 0 failures; every
   module's lint findings compared with LINT_BASELINE (Section 0.2). Then `:lint-rules:test`,
   `assert_mvi_source_topology.py` = BASE_MVI_TOPOLOGY, the topology script = commit 6's under every
   Python, and detekt alone, each in its own invocation.
2. The seventeen-module Native command, the oracle and the link, as commit 6's gate.
3. On the recorded serial: the module's device class (10) and APP_CLASSES (12); then testing.md's
   canonical Smoke and Regression suites, in `run_smoke_ui_tests.sh`'s and `ui_tests.yml`'s form,
   with `assert_mvi_device_identities.py` after Smoke as the Smoke script runs it. Both compare
   with Phase 7.16 Part C's record (its Section C17.6), name for name and outcome for outcome:
   Smoke 12 suites, 44 cases, the 3 documented `pendingFeatureRewrite` skips, 0 failures, and 2
   verified device identities; Regression 27 suites, 88 cases, 0 failures. A new failure is a STOP
   with the test and its steps; the test is not edited.
4. The logcat sweep around each suite of step 3: `logcat -c`, a background `logcat -v threadtime`
   writer to a file with its PID recorded, the suite, the writer stopped by that PID. Every line of
   priority `E` whose tag starts with `SCREEN_` is reported by tag, with counts and the first stack
   of each; an `E/SCREEN_AppDialog` line is a STOP (the Store's tag, `SCREEN_` + `AppDialog`).

## 13. Known-negative controls (on commit 6's tree)

### 13.1 Python-scored: K1–K20 (`k_rows.py`)

Each row mutates one file of MAIN's tree from a saved copy, runs the topology script under `python3`,
restores the bytes, and checks sha256 and mode. It passes when the script exits 1 and prints exactly
the row's failure lines, as many and in order, each beginning with its prefix: `k_rows.py` holds
the exact prefixes, which the table abbreviates. After the last row a fresh GREEN equals commit 6's
output and the tracked porcelain is empty. The prototype ran all twenty as required.

| Row | Mutation | Required lines (`feature:app-dialogs:impl: ` prefix dropped) |
| --- | --- | --- |
| K1 | AppDialogHost: `remember(factory) { AppDialogFeature(factory) }` → `AppDialogFeature(factory)` | `exact root-factory flow requires 'remember(factory) { AppDialogFeature(factory) }' once in …/ui/AppDialogHost.kt` |
| K2 | App.kt: `deps.appDialogGraphFactory` → `deps.homeGraphFactory` | `exact root-factory flow requires 'AppDialogHost(factory = deps.appDialogGraphFactory)' once in …/App.kt` |
| K3 | AppNavigationHost: a comment `// appDialogGraphFactory` | `mounted at the app root, so …/AppNavigationHost.kt must not name appDialogGraphFactory` |
| K4 | app/app: impl edge `api` → `implementation` | `app:app: direct app-dialogs aggregation edge must remain api` |
| K5 | app/common: impl edge `api` → `implementation` | `app:common: app-dialogs edge must be exactly one api dependency`; `app:common: app-dialogs edge must not remain implementation` |
| K6 | AppDialogFeature: a comment `// LocalContext.current.appDeps<AppDialogGraph.Factory>()` | `remaining Context.appDeps readers are not the exact 1 unported entries; expected=…`; `…/AppDialogFeature.kt contains forbidden platform/lookup API 'LocalContext'`; `… 'appDeps<'` |
| K7 | State: `val dateLabel: String,` → `String?` | `Store State must stay semantic with exactly (('current', 'AppDialog?'), ('dateLabel', 'String')); actual=…'String?'…` |
| K8 | RepoHandler: the label computed inside `updateState` from `resourceWrapper` | `updateState in …/AppDialogRepoHandler.kt references 'resourceWrapper' inside the State lambda; resolve copy before it` |
| K9 | Mapper: the confirmation branch → `""` | `…/AppDialogDateMapper.kt must read the ResourceWrapper.formatMediumDate date exactly 2 time(s); found 1`; `D3's date path requires 'is AppDialog.UndoRestoreConfirmation -> …' exactly once …` |
| K10 | RestoreFailureDialog: `Io` → `…_reason_unknown` | `the failure reason map must stay exactly twelve codes onto ten keys; expected=…` |
| K11 | impl build: `implementation(project(":core:data:database"))` added | `project edges must be exactly [...]; actual=[…, 'core:data:database', …]` |
| K12 | Identity test: the helper back on `asContribution` | `exact root-factory flow requires 'private fun AppGraph.appDialogs()…' once in …/AppDialogExtensionIdentityTest.kt`; `extension identities must not bypass AppRootDeps via asContribution` |
| K13 | RestoreSuccessDialog: `uiMode = 0x21` on "No previous version" | `…/RestoreSuccessDialog.kt contains forbidden platform/lookup API 'uiMode'`; `RestoreSuccessDialog.kt preview inventory must be exactly […]; actual=…` |
| K14 | AppDialogHost: `dateLabel = state.dateLabel,` → `dateLabel = "",` | `D3's date path requires 'dateLabel = state.dateLabel,' exactly once in ui/AppDialogHost.kt` |
| K15 | The scene renamed `resourcesDatesAndChoicesRenderAndDispatch` | `exact test identities drifted in src/iosTest/…/AppDialogFeatureSceneIosTest.kt; missing=[…], unexpected=[…]` |
| K16 | AppDialogRepositoryTest: `@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")` | `suppressions must be exactly the one inherited production and zero Native test-name annotations; …` |
| K17 | AppDialogObserverImplTest: `import io.mockk.mockk` | `…/AppDialogObserverImplTest.kt contains forbidden test API 'io.mockk'` |
| K18 | RU catalog: `Отмена` → `Отменить` | `exact private CMP catalog mismatch in …/values-ru/strings.xml; mismatched=[…]…` |
| K19 | An extra file beside the api's model | `feature:app-dialogs:api: source manifest mismatch; missing=[], extra=[…/AppDialogExtra.kt]` |
| K20 | RestoreFailureDialog: the Dark preview's annotation removed | `exactly nine portable previews are required; found 8`; `RestoreFailureDialog.kt preview contract requires '@Preview(name = "Dark", showBackground = true)' exactly once` |

### 13.2 Gradle-scored: G1–G15 (`g_rows.py`)

Each row runs through `documentation/mockups/mutation_harness.py` one-shot with `--expect RED`,
keeping its flags. TH is `:feature:app-dialogs:impl:testAndroidHostTest`; TB is TH plus
`:feature:app-dialogs:impl:iosSimulatorArm64Test --continue`; each carries `--no-configuration-cache
--console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx4g` inside `--task`. The RED set is read
from the fresh XML of each scored task and must be exactly the row's; every other identity of the
task passes.

| Row | Mutation | Task | RED exactly |
| --- | --- | --- | --- |
| G1 | RepoHandler writes `it.copy(current = dialog)` | TH | `Observe writes the dialog's formatted date beside it` |
| G2 | Mapper: `formatMediumDate(restoredAtEpochMs + 1)` | TB | host: `a restore success labels the date its backup was restored`; Native: it and the scene |
| G3 | Mapper: the confirmation branch → `""` | TH | `an undo confirmation labels the date of the data it brings back`; `Observe writes the dialog's formatted date beside it` |
| G4 | Mapper: the dateless branch → `resourceWrapper.formatMediumDate(0L)` | TH | `a failure or an undo success or no dialog labels nothing and formats nothing` |
| G5 | ChooseHandler: the emit → `Unit` | TH | `Choose emits the choice to the observer` |
| G6 | ChooseHandler: `observer.acknowledgeReaction(action.dialog)` after the emit | TH | `Choose does NOT dismiss the dialog — the consumer is responsible` |
| G7 | ObserverImpl: `acknowledgeReaction` → `Unit` | TH | `acknowledgeReaction delegates to repository dismiss` |
| G8 | RepoHandler: the publish → `Unit` | TH | `Publish forwards dialog to repository` |
| G9 | RepoHandler: the dismiss → `Unit` | TH | `Dismiss forwards dialog to repository` |
| G10 | RepoHandler: `if (dialog == null) return@launch` first in the collector | TH | `Observe sets state to null when repository emits null` |
| G11 | ObserverImpl: with no subscriber, `emit` awaits cancellation | TH | `emit with no subscriber buffers up to capacity without suspending`; `Choose does NOT dismiss the dialog — the consumer is responsible` (after runTest's 60 s timeout) |
| G12 | Repository: a restore success's `restoredAtEpochMs` written `+ 1` | TB | host and Native each: repository `publish RestoreSuccess surfaces the variant with payload`, `new terminal owner replaces identical payload and stale dismiss preserves it`, `new owned terminal payload replaces a stale pending payload`, `dismiss clears only the named variant's flags`, `dismiss the only pending variant emits null`, `dedup is per-variant — pending RestoreFailure does not block RestoreSuccess publish`, `publish then dismiss then publish same variant succeeds — the flag was cleared`; repo handler `Observe updates state when repository emits a dialog` |
| G13 | ObserverImpl: `replay = 1` | TH | `late subscriber does NOT receive past emissions — replay = 0 and no persisted-choice replay` |
| G14 | Resolver: the undo success outranks the restore success | TH | `RestoreSuccess wins when RestoreFailure is absent` |
| G15 | HostContent: Undo restore dispatches `Acknowledge` | TB | host: none; Native: the scene |

Every new or changed identity is here: D3's four (G1–G4), the scene (G2, G15), the five effect
checks (G5–G9), the two Observe identities (G10, G12), the `assertTrue` port (G11), the two renames
(G12, G13) and the class assertion (G14). G12 also proves D5's store live on both platforms.

### 13.3 The oracle: O1–O2 (`o_rows.py`)

On a scratch mirror of the seventeen fresh result directories, with MAIN's oracle run from the
mirror: the unmutated mirror is GREEN; **O1** drops the scene's testcase from app-dialogs' copy →
RED, `feature:app-dialogs:impl: expected exactly one testcase with
classname='io.github.stslex.workeeper.feature.app_dialogs.impl.AppDialogFeatureSceneIosTest'
name='resourcesDatesVariantsAndChoicesRenderAndDispatch[iosSimulatorArm64]', found 0; …`; **O2**
renames the date mapper's classname → RED naming its three missing tuples.

## 14. The exit census (E1–E22)

`census.py` (Appendix B) measures each item on the base commit (git objects) and on the tree
(`git ls-files`, so build outputs never count), multi-line, comments and strings blanked where a
count reads code. It exits 0 when the base column equals BASE and the tree column equals EXIT.

| Item | Base | Exit |
| --- | --- | --- |
| E1 api source sets | `{main: 5}` | `{commonMain: 5}` |
| E2 api plugin | `convention.androidLibrary` | `convention.kmpLibrary` |
| E3 impl source sets | `{androidTest: 1, main: 23, test: 5}` | `{androidDeviceTest: 1, androidHostTest: 1, commonMain: 23, commonTest: 7, iosTest: 2}` |
| E4 impl plugin | `convention.composeLibrary` | `convention.kmpComposeLibrary` |
| E5 catalog blobs (EN, RU) | `eb21921800ae`, `d7f3c6797a7e` | the same |
| E6 portable identities (count, name digest) | 39, `b1e9c0b98356a58f` | 44, `4666f71428554a91` |
| E7 device identities | 10, `82e9964f235c9a1b` | the same |
| E8 Native-rejected names | 2 | 0 |
| E9 platform reads in production (`android.`, `java.`, `javax.`, `androidx.annotation`, `androidx.compose.ui.res` imports, `R.string`/`R.plurals`) | 39 | 0 |
| E10 MockK test files | 3 | 0 |
| E11 `Context.appDeps` readers, repository-wide | 2 | 1 |
| E12 `asContribution` in the identity test | 2 | 0 |
| E13 `RestoreRecoveryFiles` in feature/app-dialogs | 2 | 0 |
| E14 `InstallEpochSource` declared / bound | 0 / 0 | 1 / 1 |
| E15 `formatMediumDate(` in impl ui / `resourceWrapper.formatMediumDate(` in impl mappers | 3 / 0 | 0 / 2 |
| E16 `@Preview(` / `uiMode` in impl production | 9 / 4 | 9 / 0 |
| E17 App.kt `AppDialogHost()` / `AppDialogHost(factory = deps.appDialogGraphFactory)` | 1 / 0 | 0 / 1 |
| E18 app/common's impl edge | `implementation` | `api` |
| E19 iOS job: test lines / link lines / the `$linked/N` echo | 16 / 16 / 16 | 17 / 17 / 17 |
| E20 oracle modules | 16 | 17 |
| E21 gate `FEATURE_ENTRIES` / `MODULES` | 9 / 13 | 10 / 15 |
| E22 harness exact task match | 0 | 1 |

E6's digest is the sha256 (first 16 hex) of the sorted `Class#name\n` lines. On the base tree the
census exits 1 with every item's tree value equal to its base value (E5 and E7 are equal by design).

**The census controls Y1–Y3** (`y_rows.py`), each on a saved copy restored byte-exactly: **Y1**
`import io.mockk.mockk` in `AppDialogObserverImplTest` → exit 1 naming E10 alone; **Y2** RU
`Отмена` → `Отменить` → E5 alone; **Y3** a second `AppDialogHost()` call in App.kt → E17 alone. The
restored tree's census exits 0.

## 15. STOP conditions

Stop at once with a measured note when:

- this document's hash differs from the prompt's, `a4bcd440d` is not an ancestor of origin/dev, or
  this document already exists on dev;
- MAIN has tracked changes at Phase 0, or the guarded push fails a guard;
- signing fails;
- Xcode's first launch is unfinished or no iOS simulator runtime is installed;
- drift touches a Section 7 path, or a patch's sha256 differs from Appendix A;
- a patch does not apply, or the applied commit's blob digest differs from Appendix A's before any
  repair;
- the census's base column differs from Section 14, or a gate output differs from Section 10.3;
- a repair falls outside Section 0.4(a);
- D5 misbehaves on Native: `AppDialogRepositoryTest` fails on `iosSimulatorArm64Test` while it
  passes on the host, or DataStore rejects the temporary path (STOP-and-amend; the fallback is
  option C, androidHostTest on a `File`);
- the scene cannot reach a dialog's nodes on the simulator (STOP-and-amend);
- Metro rejects the graph at `:app:dev:assembleDebug` (a missing `InstallEpochSource` or
  `appDialogGraphFactory` binding);
- a classic consumer (app/common, app:app, settings, recovery) does not resolve the KMP api or impl;
- a host case outside app-dialogs changes count or outcome against HOST_BASELINE, or a lint finding
  is new or on a Section 7 path;
- a control's verdict differs from its row, or a mutation meant for a compiled detector does not
  compile;
- a device install fails on a signature mismatch, or the logcat sweep shows a `SCREEN_AppDialog`
  error line;
- any CI job on a PR head is red;
- a path outside Section 7 changes.

A STOP never widens the PR.

## 16. Signed, bisect-green commit plan

| # | Subject | Patch |
| ---: | --- | --- |
| 1 | `ci(kmp): let a feature entry mount at the app root without goldens` | c1 |
| 2 | `chore(harness): match the mutated task by its path, not as a substring` | c2 |
| 3 | `refactor(kmp): share the app-dialogs api and its install-epoch seam` | c3 |
| 4 | `refactor(app-dialogs): format the dialog dates into State before the write` | c4 |
| 5 | `refactor(kmp): share the app-dialogs feature entry (Phase 7.17)` | c5 |
| 6 | `ci(kmp): hold the app-dialogs entry to its exact contract on the gate and on Native` | c6 |
| 7 | `docs(kmp): record Phase 7.17 evidence` | c7, and this document's Status and Section 19 |

Each commit is signed through the signed-commits skill, carries `Co-Authored-By` as its only
trailer, and passes its Section 12 gates.

## 17. Exit criteria

- The PR into dev is open, non-draft and mergeable (`CLEAN`), auto-merge is off, every required
  context is green on the final head, and `@claude review` is requested.
- `git diff --no-renames --name-status <base>..HEAD` lists exactly Section 7's paths: 92 and this
  document. Commits 3 and 5 show 5 and 18 R100 moves under `-M`, and the PR as a whole 17 (the
  others changed in commit 3 or 4 first); both catalogs keep the base blobs `eb21921800ae…` and
  `d7f3c6797a7e…`.
- Section 19 holds the record. The maintainer merges.

**The owner's device check (after the merge; not a gate):** on the dev build, a Drive restore shows
the success dialog with its date; Undo restore shows the confirmation with its date, then the undo
success; EN and RU; and a restore failure, if one can be forced.

## 18. Carried out of 7.17

- **Q1, for 7.18 (settings):** what iOS v1 does about Drive backup and restore. The lean is that
  iOS v1 ships without it: settings' backup section and its restore domain stay in androidMain
  behind an interface, and iOS binds "unavailable" and hides the section.
- **D7:** `RepoAction.Publish` and `RepoAction.Dismiss` — tech-debt.md, "`feature:app-dialogs`
  carried items", first row.
- **The Activity-scoped wording** outside the mounting section (app-dialogs.md, lint-rules.md, two
  KDocs) and the skills file — the same section, second row.
- **OC1**, the primary-locale property — tech-debt.md's existing `StringResolver` row covers it.

## 19. Implementation evidence

Recorded for the seven-commit implementation on one checkout, without a worktree or a delegated agent. Every accepted Gradle gate first stopped only this run's daemon registry, then used `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`. Host, Native and link commands used `-Dorg.gradle.jvmargs=-Xmx4g --max-workers=2`; Android device commands used `-Dorg.gradle.jvmargs=-Xmx3g --max-workers=2 --full-stacktrace --continue`. Detekt ran separately from tests. Every quoted summary has only executed actionable tasks. XML was admitted only when its modification time was after that gate's recorded start, and copied aside immediately. Snapshot labels below identify the preserved raw log, command metadata and XML copy; XML paths are repository-relative originals.

`N17` and `L17` mean exactly the seventeen task paths, in order, in the workflow's Native-test and device-link steps (Section 10.5), prefixed by `./gradlew` and the common flags above; the workflow also supplies `--full-stacktrace --continue`. `N16` / `L16` are the base workflow's same lists before their final app-dialogs entry. Native executables use debugTest; application builds and device suites target debug variants.

### 19.1 Base, commits and reference integrity

- Base: `d1fa60dac68380b32f69de9f48ce2e603c5ce7d2`, the one-file specification landing on dev, parent `a4bcd440dc7420c80d16d9423990491f5a12fd4a`. The specification blob was `c2ee4413d24185fc80c9edc76b1762b4c09daf6a`; its initial SHA-256 was `6eff3440bd640c1b3b0ae58746fd09d485dd9abf9f4da50090eb201c03d7e7cd`. The guarded dev push belonged to the prior run; this implementation made no further dev push.
- Base GitHub signature: `verified: true`, reason `valid`, verified at `2026-10-29T14:24:19Z` (raw GitHub Git-commit API).
- Commit 1: `c8ecc0c52621006a9842294c12b1e4e3560f8a09`, `ci(kmp): let a feature entry mount at the app root without goldens`; local `gpgsig` header verified.
- Commit 2: `883bab9b9fc2c88d9daf30ae68759b8b7d71e6d5`, `chore(harness): match the mutated task by its path, not as a substring`; local `gpgsig` header verified.
- Commit 3: `f460fa4a5cb174ca7d403f2a0dd08afe34167f4e`, `refactor(kmp): share the app-dialogs api and its install-epoch seam`; local `gpgsig` header verified.
- Commit 4: `fe262bad24a8e3faf60d59c6f9ed8da2df68c0bf`, `refactor(app-dialogs): format the dialog dates into State before the write`; local `gpgsig` header verified.
- Commit 5: `e326f7d74c3f31714ff5021a584f37a1990501e7`, `refactor(kmp): share the app-dialogs feature entry (Phase 7.17)`; local `gpgsig` header verified.
- Commit 6: `bdd7bce7bac4b18492d01ebd41544145f7a939d2`, `ci(kmp): hold the app-dialogs entry to its exact contract on the gate and on Native`; local `gpgsig` header verified.
- Commit 7 is this signed evidence commit. Its final SHA, authoritative GitHub signature verification for every commit, PR/CI job IDs, final required contexts and review-request URL are checked after publication and reported in the maintainer handoff; they are not inferred from local signing.
- All fifteen original kit files matched `SHA256SUMS`; that manifest's SHA-256 remained `0e6e29c72cf56de4fcf42351aeec89c1ecfe9c96278eb69ba07edfc3d0710be6`. The original kit was not rewritten. Each original reference digest was verified before repair; the approved replacement references and their blob changes are recorded in Section 19.8. Original c6 and c7 still applied without rejects, three-way application or hand merging.

### 19.2 Entry gate

The latest unified dev push run at entry was `40804398303`, head `a4bcd440dc7420c80d16d9423990491f5a12fd4a`, success. Drift to the base touched only this specification. The base census had the required twenty non-exit items, E5 and E7 already at exit, and no base drift. The six topology outputs were byte-identical: 990 lines, SHA-256 `7271a2b1655ca7f294ed45ae2fa188c26dfd634c70071f5b737da8c6adc7f8d4`; MVI topology was retained as `BASE_MVI_TOPOLOGY`. The saved old topology gate's SHA-256 was `b07bfaa412465041cc475e851af3b84fd4f2bac154366c75b2a08c180c681c7f`.

| Gate / XML snapshot label | Command (common flags above) | Gradle evidence | Fresh XML |
| --- | --- | --- | --- |
| `p2/host` | `./gradlew assembleDebug lintDebug testDebugUnitTest` | `2841 actionable tasks: 2841 executed` | 474 suites / 3385 cases / 0 skipped; per-module XML roots in the tables below |
| `p2/lint-rules` | `./gradlew :lint-rules:test` | `9 actionable tasks: 9 executed` | 16 suites / 151 cases / 0 skipped; `lint-rules/build/test-results/test/TEST-*.xml` |
| `p2/native` | `N16` | `434 actionable tasks: 434 executed` | 115 suites / 862 cases / 0 skipped; per-module XML roots in the tables below |
| `p2/link` | `L16` | `418 actionable tasks: 418 executed` | no test XML expected |
| `p2/device-dialogs` | `./gradlew :feature:app-dialogs:impl:connectedDebugAndroidTest` | `227 actionable tasks: 227 executed` | 1 suites / 10 cases / 0 skipped; `feature/app-dialogs/impl/build/outputs/androidTest-results/connected/debug/TEST-*.xml` |
| `p2/device-app` | `./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.DialogStateAcrossGenerationsTest,io.github.stslex.workeeper.app.AppScopeDataStoreSingletonTest,io.github.stslex.workeeper.app.UiAdmissionRaceTest,io.github.stslex.workeeper.app.UiGenerationSwapTest,io.github.stslex.workeeper.app.RuntimeGenerationSwapDeviceTest` | `759 actionable tasks: 759 executed` | 5 suites / 12 cases / 0 skipped; `app/app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` |

HOST_BASELINE contained 34 module/task rows, 474 suites and 3,385 cases, all passing. LINT_BASELINE contained 77 XML reports and 480 dependency-version advisory occurrences (36 `AndroidGradlePluginVersion`, 240 `GradleDependency`, 204 `NewerVersionAvailable`), no errors. The Native baseline held 115 suites / 862 cases in sixteen modules; its exact oracle passed. All sixteen device-target test executables were fresh. The two Android baseline gates held the ten dialog identities and twelve APP_CLASSES identities with no skips or failures.

### 19.3 Per-commit gates and gate-equivalence proof

Commit 1's canonical `diffproof.py` completed on its committed tree under all six Python paths. Every GREEN pair matched the entry bytes; M1, M2 and M3 each returned 1 identically in old and new gates; Q1 returned 0 identically. The final fresh GREEN matched the first; bytes, modes and tracked porcelain were restored. The retained transcript ends:
```text
fresh GREEN after the last row equal: True
PROOF OK
```

Commit 2's `harness_reuse_check.py` gave `8 cases, 0 wrong` on the new harness. Its base-harness control gave `8 cases, 2 wrong`, exactly S1 (`:lint-rules:test` versus `testClasses`) and S4 (`detekt` versus `detektMain`). No other row differed.

| Gate / XML snapshot label | Command (common flags above) | Gradle evidence | Fresh XML |
| --- | --- | --- | --- |
| `c1/pre-tests` | `./gradlew :feature:app-dialogs:impl:testDebugUnitTest` | `156 actionable tasks: 156 executed` | 5 suites / 39 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c1/pre-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c1/post-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c2/pre-tests` | `./gradlew :feature:app-dialogs:impl:testDebugUnitTest` | `156 actionable tasks: 156 executed` | 5 suites / 39 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c2/pre-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c2/post-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c3/pre-tests` | `./gradlew :feature:app-dialogs:api:assembleDebug :feature:app-dialogs:impl:testDebugUnitTest :app:dev:assembleDebug` | `734 actionable tasks: 734 executed` | 5 suites / 39 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c3/pre-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c3/post-tests` | `./gradlew :feature:app-dialogs:api:assembleDebug :core:data:backup:api:assembleDebug :core:data:database:testDebugUnitTest :core:data:backup:scheduling:testDebugUnitTest :feature:app-dialogs:impl:testDebugUnitTest :feature:recovery:testDebugUnitTest :app:app:testDebugUnitTest :feature:settings:assembleDebug :app:dev:assembleDebug` | `809 actionable tasks: 809 executed` | 71 suites / 550 cases / 0 skipped; per-module XML roots in the tables below |
| `c3/post-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c4/pre-tests` | `./gradlew :feature:app-dialogs:impl:testDebugUnitTest :app:dev:assembleDebug` | `695 actionable tasks: 695 executed` | 6 suites / 43 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c4/pre-tests-amendment` | `./gradlew :feature:app-dialogs:impl:testDebugUnitTest :app:dev:assembleDebug` | `695 actionable tasks: 695 executed` | 6 suites / 43 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c4/pre-detekt-amendment` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c4/post-tests` | `./gradlew :feature:app-dialogs:impl:testDebugUnitTest :app:dev:assembleDebug` | `695 actionable tasks: 695 executed` | 6 suites / 43 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c4/device-dialogs` | `./gradlew :feature:app-dialogs:impl:connectedDebugAndroidTest` | `217 actionable tasks: 217 executed` | 1 suites / 10 cases / 0 skipped; `feature/app-dialogs/impl/build/outputs/androidTest-results/connected/debug/TEST-*.xml` |
| `c4/post-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c5/variants-after-dependencies` | `./gradlew :feature:app-dialogs:impl:dependencies :feature:app-dialogs:impl:outgoingVariants --no-parallel --stacktrace` | `6 actionable tasks: 6 executed` | no test XML expected |
| `c5/consumers-green` | `./gradlew :app:app:dependencyInsight --configuration debugCompileClasspath --dependency app-dialogs :app:common:dependencyInsight --configuration debugCompileClasspath --dependency app-dialogs :feature:settings:dependencyInsight --configuration debugCompileClasspath --dependency app-dialogs :feature:recovery:dependencyInsight --configuration debugCompileClasspath --dependency app-dialogs` | `8 actionable tasks: 8 executed` | no test XML expected |
| `c5/early-native-amendment` | `./gradlew :feature:app-dialogs:impl:testAndroidHostTest :feature:app-dialogs:impl:iosSimulatorArm64Test` | `233 actionable tasks: 233 executed` | 13 suites / 87 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/iosSimulatorArm64Test/TEST-*.xml`; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml` |
| `c5/pre-app` | `./gradlew :app:dev:assembleDebug :app:app:testDebugUnitTest --tests '*AppDialogExtensionIdentityTest'` | `698 actionable tasks: 698 executed` | 1 suites / 4 cases / 0 skipped; `app/app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c5/pre-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c5/post-host` | `./gradlew :feature:app-dialogs:api:assembleDebug :feature:app-dialogs:impl:assembleDebug :feature:app-dialogs:impl:testAndroidHostTest :app:common:assembleDebug :app:dev:assembleDebug :app:app:testDebugUnitTest :feature:recovery:testDebugUnitTest :feature:settings:testDebugUnitTest` | `999 actionable tasks: 999 executed` | 47 suites / 459 cases / 0 skipped; `app/app/build/test-results/testDebugUnitTest/TEST-*.xml`; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml`; `feature/recovery/build/test-results/testDebugUnitTest/TEST-*.xml`; `feature/settings/build/test-results/testDebugUnitTest/TEST-*.xml` |
| `c5/post-native-link` | `./gradlew :feature:app-dialogs:impl:iosSimulatorArm64Test :feature:app-dialogs:impl:linkDebugTestIosArm64` | `167 actionable tasks: 167 executed` | 7 suites / 44 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/iosSimulatorArm64Test/TEST-*.xml` |
| `c5/lint-rules` | `./gradlew :lint-rules:test` | `9 actionable tasks: 9 executed` | 16 suites / 151 cases / 0 skipped; `lint-rules/build/test-results/test/TEST-*.xml` |
| `c5/device-dialogs` | `./gradlew :feature:app-dialogs:impl:connectedDebugAndroidTest` | `217 actionable tasks: 217 executed` | 1 suites / 10 cases / 0 skipped; `feature/app-dialogs/impl/build/outputs/androidTest-results/connected/androidMain/TEST-*.xml` |
| `c5/device-app` | `./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.DialogStateAcrossGenerationsTest,io.github.stslex.workeeper.app.AppScopeDataStoreSingletonTest,io.github.stslex.workeeper.app.UiAdmissionRaceTest,io.github.stslex.workeeper.app.UiGenerationSwapTest,io.github.stslex.workeeper.app.RuntimeGenerationSwapDeviceTest` | `752 actionable tasks: 752 executed` | 5 suites / 12 cases / 0 skipped; `app/app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` |
| `c5/post-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c6/pre-tests` | `./gradlew :feature:app-dialogs:impl:testAndroidHostTest` | `148 actionable tasks: 148 executed` | 6 suites / 43 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml` |
| `c6/pre-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `c6/native` | `N17` | `467 actionable tasks: 467 executed` | 122 suites / 906 cases / 0 skipped; per-module XML roots in the tables below |
| `c6/link` | `L17` | `450 actionable tasks: 450 executed` | no test XML expected |
| `c6/post-detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |

Topology after each commit was byte-identical under all six Python paths:

| Commits | Lines | SHA-256 |
| --- | ---: | --- |
| 1–2 | 990 | `7271a2b1655ca7f294ed45ae2fa188c26dfd634c70071f5b737da8c6adc7f8d4` |
| 3–4 | 996 | `3203da7840da5d081ecb45161839b1c4a5ecd0d05c1bfc87eb9e05fb83832510` |
| 5 | 1033 | `2f2dc0f914b7e821d40d3c1fa8c70f6b819a3afbf409cd48b11bab44be4f4197` |
| 6 | 1034 | `ce7dfb815d27a058daba246b1beb9cc069368491b977f6fbb743025c3a171522` |

Commit 3's five consumer rows were name/outcome-identical to HOST_BASELINE (71 suites / 550 cases). Commit 4 retained all 39 base identities and added exactly D3's four; its ten device identities remained unchanged. Commit 5's 416 app/settings/recovery identities matched their baseline exactly; all 43 portable and 44 Native app-dialogs identities were checked by full class and method name, with only S2's two specified renames. The four extension identities and both device inventories matched their base names and outcomes. Its outgoing variants listed Android, iosArm64 and iosSimulatorArm64; every Section 8 classic compile/test classpath selected `androidApiElements` with platform `androidJvm`. Native collection and real file DataStore were proven before committing the conversion.

### 19.4 Known-negative controls

All controls ran on commit 6's tree. The Python-scored tools ran under `python3` only, in the required O → K → Y order before any Gradle mutation could replace the fresh seventeen-module XML. G controls used the unchanged canonical tool and mutation harness; a logging-only external Python hook preserved their complete Gradle output and fresh XML. Every G row additionally matched the full 43/44 identity inventory, its exact expected RED set and an executed-only summary. Every mutated file's bytes and mode, the commit and tracked cleanliness were checked on restoration.

Oracle controls:
```text
GREEN mirror exit 0
O1 exit 1 RED as required ["  feature:app-dialogs:impl: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.app_dialogs.impl.AppDialogFeatureSceneIosTest' name='resourcesDatesVariantsAndChoicesRenderAndDispatch[iosSimul"]
O2 exit 1 RED as required ["  feature:app-dialogs:impl: expected exactly one testcase with classname='io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.mapper.AppDialogDateMapperTest' name='a restore success labels the date its backup was res"]
```

**K1:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: exact root-factory flow requires 'remember(factory) { AppDialogFeature(factory) }' once in feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHost.kt
```

**K2:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: exact root-factory flow requires 'AppDialogHost(factory = deps.appDialogGraphFactory)' once in app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt
```

**K3:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: mounted at the app root, so app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt must not name appDialogGraphFactory
```

**K4:** exit 1, RED as required; quoted topology failures:
```text
app:app: direct app-dialogs aggregation edge must remain api
```

**K5:** exit 1, RED as required; quoted topology failures:
```text
app:common: app-dialogs edge must be exactly one api dependency
app:common: app-dialogs edge must not remain implementation
```

**K6:** exit 1, RED as required; quoted topology failures:
```text
remaining Context.appDeps readers are not the exact 1 unported entries; expected={PosixPath('feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/di/SettingsFeature.kt'): ['SettingsGraph.Factory']}, actual={PosixPath('feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt'): ['AppDialogGraph.Factory'], PosixPath('feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/di/SettingsFeature.kt'): ['SettingsGraph.Factory']}
feature:app-dialogs:impl: src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt contains forbidden platform/lookup API 'LocalContext'
feature:app-dialogs:impl: src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt contains forbidden platform/lookup API 'appDeps<'
```

**K7:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: Store State must stay semantic with exactly (('current', 'AppDialog?'), ('dateLabel', 'String')); actual=(('current', 'AppDialog?'), ('dateLabel', 'String?'))
```

**K8:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: updateState in src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt references 'resourceWrapper' inside the State lambda; resolve copy before it
```

**K9:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/mapper/AppDialogDateMapper.kt must read the ResourceWrapper.formatMediumDate date exactly 2 time(s); found 1
feature:app-dialogs:impl: D3's date path requires 'is AppDialog.UndoRestoreConfirmation -> resourceWrapper.formatMediumDate(originalDataDateEpochMs)' exactly once in mvi/mapper/AppDialogDateMapper.kt
```

**K10:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: the failure reason map must stay exactly twelve codes onto ten keys; expected={'NotAuthenticated': ['app_dialog_restore_failure_reason_not_authenticated'], 'NetworkUnavailable': ['app_dialog_restore_failure_reason_network_unavailable'], 'AuthRevoked': ['app_dialog_restore_failure_reason_auth_revoked'], 'MissingRequiredScope': ['app_dialog_restore_failure_reason_missing_required_scope'], 'StorageQuotaExceeded': ['app_dialog_restore_failure_reason_storage_quota_exceeded'], 'InsufficientLocalStorage': ['app_dialog_restore_failure_reason_io'], 'StorageCapacityUnavailable': ['app_dialog_restore_failure_reason_io'], 'CorruptedBackup': ['app_dialog_restore_failure_reason_corrupted_backup'], 'SchemaTooNew': ['app_dialog_restore_failure_reason_schema_too_new'], 'MissingMigrationPath': ['app_dialog_restore_failure_reason_missing_migration_path'], 'Io': ['app_dialog_restore_failure_reason_io'], 'Unknown': ['app_dialog_restore_failure_reason_unknown']}, actual={'NotAuthenticated': ['app_dialog_restore_failure_reason_not_authenticated'], 'NetworkUnavailable': ['app_dialog_restore_failure_reason_network_unavailable'], 'AuthRevoked': ['app_dialog_restore_failure_reason_auth_revoked'], 'MissingRequiredScope': ['app_dialog_restore_failure_reason_missing_required_scope'], 'StorageQuotaExceeded': ['app_dialog_restore_failure_reason_storage_quota_exceeded'], 'InsufficientLocalStorage': ['app_dialog_restore_failure_reason_io'], 'StorageCapacityUnavailable': ['app_dialog_restore_failure_reason_io'], 'CorruptedBackup': ['app_dialog_restore_failure_reason_corrupted_backup'], 'SchemaTooNew': ['app_dialog_restore_failure_reason_schema_too_new'], 'MissingMigrationPath': ['app_dialog_restore_failure_reason_missing_migration_path'], 'Io': ['app_dialog_restore_failure_reason_unknown'], 'Unknown': ['app_dialog_restore_failure_reason_unknown']}
```

**K11:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: project edges must be exactly ['core:core', 'core:data:backup:api', 'core:data:dataStore', 'core:ui:kit', 'core:ui:mvi', 'core:ui:test-utils', 'feature:app-dialogs:api']; actual=['core:core', 'core:data:backup:api', 'core:data:dataStore', 'core:data:database', 'core:ui:kit', 'core:ui:mvi', 'core:ui:test-utils', 'feature:app-dialogs:api']
```

**K12:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: exact root-factory flow requires 'private fun AppGraph.appDialogs(): AppDialogGraph = appDialogGraphFactory.createAppDialogGraph()' once in app/app/src/test/kotlin/io/github/stslex/workeeper/di/AppDialogExtensionIdentityTest.kt
feature:app-dialogs:impl: extension identities must not bypass AppRootDeps via asContribution
```

**K13:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/RestoreSuccessDialog.kt contains forbidden platform/lookup API 'uiMode'
feature:app-dialogs:impl: RestoreSuccessDialog.kt preview inventory must be exactly ['name = "Light", showBackground = true', 'name = "Dark", showBackground = true', 'name = "No previous version", showBackground = true']; actual=['name = "Light", showBackground = true', 'name = "Dark", showBackground = true', 'name = "No previous version", showBackground = true, uiMode = 0x21']
```

**K14:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: D3's date path requires 'dateLabel = state.dateLabel,' exactly once in ui/AppDialogHost.kt
```

**K15:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: exact test identities drifted in src/iosTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/AppDialogFeatureSceneIosTest.kt; missing=['resourcesDatesVariantsAndChoicesRenderAndDispatch'], unexpected=['resourcesDatesAndChoicesRenderAndDispatch']
```

**K16:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: suppressions must be exactly the one inherited production and zero Native test-name annotations; expected={'src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt': ['"UNCHECKED_CAST"']}, actual={'src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/di/AppDialogFeature.kt': ['"UNCHECKED_CAST"'], 'src/commonTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/data/AppDialogRepositoryTest.kt': ['"INVALID_CHARACTERS_NATIVE_ERROR"']}
```

**K17:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: src/commonTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/observer/AppDialogObserverImplTest.kt contains forbidden test API 'io.mockk'
```

**K18:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: exact private CMP catalog mismatch in feature/app-dialogs/impl/src/commonMain/composeResources/values-ru/strings.xml; mismatched=["app_dialog_undo_restore_confirmation_cancel: expected='Отмена', actual=('string', 'app_dialog_undo_restore_confirmation_cancel', 'Отменить')"], unexpected=[], order=exact
```

**K19:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:api: source manifest mismatch; missing=[], extra=['src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/api/model/AppDialogExtra.kt']
```

**K20:** exit 1, RED as required; quoted topology failures:
```text
feature:app-dialogs:impl: exactly nine portable previews are required; found 8
feature:app-dialogs:impl: RestoreFailureDialog.kt preview contract requires '@Preview(name = "Dark", showBackground = true)' exactly once
```

After K20: `fresh GREEN equal; tracked porcelain empty; 20 rows, 0 off`.

Census controls:
```text
Y1 rc 1 names ['E10 MockK test files: base=3 tree=1 -> NOT AT EXIT'] -> as required
Y2 rc 1 names ["E5 catalog blobs: base=('eb21921800ae', 'd7f3c6797a7e') tree=('eb21921800ae', 'c79064155c14') -> NOT AT EXIT"] -> as required
Y3 rc 1 names ['E17 App.kt mounts old / new: base=(1, 0) tree=(1, 1) -> NOT AT EXIT'] -> as required
restored tree census exit 0
tracked porcelain empty
```

All fifteen G anchors matched exactly once before execution. The unmutated TH and TB commands were the exact `g_rows.py` definitions, with R4's forced-execution flags added. Both were GREEN before and after the fifteen rows; TH had 6 suites / 43 cases, TB additionally 7 Native suites / 44 cases, all passing:

| Gate / XML snapshot label | Command (common flags above) | Gradle evidence | Fresh XML |
| --- | --- | --- | --- |
| `controls/pre-TH` | `./gradlew :feature:app-dialogs:impl:testAndroidHostTest` | `148 actionable tasks: 148 executed` | 6 suites / 43 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml` |
| `controls/pre-TB` | `./gradlew :feature:app-dialogs:impl:testAndroidHostTest :feature:app-dialogs:impl:iosSimulatorArm64Test` | `233 actionable tasks: 233 executed` | 13 suites / 87 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/iosSimulatorArm64Test/TEST-*.xml`; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml` |
| `controls/post-TH` | `./gradlew :feature:app-dialogs:impl:testAndroidHostTest` | `148 actionable tasks: 148 executed` | 6 suites / 43 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml` |
| `controls/post-TB` | `./gradlew :feature:app-dialogs:impl:testAndroidHostTest :feature:app-dialogs:impl:iosSimulatorArm64Test` | `233 actionable tasks: 233 executed` | 13 suites / 87 cases / 0 skipped; `feature/app-dialogs/impl/build/test-results/iosSimulatorArm64Test/TEST-*.xml`; `feature/app-dialogs/impl/build/test-results/testAndroidHostTest/TEST-*.xml` |

**G1:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G1: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogRepoHandlerTest', "Observe writes the dialog's formatted date beside it")] of 43 cases
1 row(s), 0 off
```

**G2:** `233 actionable tasks: 233 executed`; mutation restored, exact inventory and RED set verified:
```text
G2: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/mapper/AppDialogDateMapper.kt -> RED (3 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogDateMapperTest', 'a restore success labels the date its backup was restored')] of 43 cases
  native: RED exactly [('AppDialogDateMapperTest', 'a restore success labels the date its backup was restored'), ('AppDialogFeatureSceneIosTest', 'resourcesDatesVariantsAndChoicesRenderAndDispatch')] of 44 cases
1 row(s), 0 off
```

**G3:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G3: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/mapper/AppDialogDateMapper.kt -> RED (2 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogDateMapperTest', 'an undo confirmation labels the date of the data it brings back'), ('AppDialogRepoHandlerTest', "Observe writes the dialog's formatted date beside it")] of 43 cases
1 row(s), 0 off
```

**G4:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G4: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/mapper/AppDialogDateMapper.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogDateMapperTest', 'a failure or an undo success or no dialog labels nothing and formats nothing')] of 43 cases
1 row(s), 0 off
```

**G5:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G5: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/ChooseHandler.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('ChooseHandlerTest', 'Choose emits the choice to the observer')] of 43 cases
1 row(s), 0 off
```

**G6:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G6: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/ChooseHandler.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('ChooseHandlerTest', 'Choose does NOT dismiss the dialog — the consumer is responsible')] of 43 cases
1 row(s), 0 off
```

**G7:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G7: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/observer/AppDialogObserverImpl.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogObserverImplTest', 'acknowledgeReaction delegates to repository dismiss')] of 43 cases
1 row(s), 0 off
```

**G8:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G8: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogRepoHandlerTest', 'Publish forwards dialog to repository')] of 43 cases
1 row(s), 0 off
```

**G9:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G9: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogRepoHandlerTest', 'Dismiss forwards dialog to repository')] of 43 cases
1 row(s), 0 off
```

**G10:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G10: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandler.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogRepoHandlerTest', 'Observe sets state to null when repository emits null')] of 43 cases
1 row(s), 0 off
```

**G11:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G11: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/observer/AppDialogObserverImpl.kt -> RED (2 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogObserverImplTest', 'emit with no subscriber buffers up to capacity without suspending'), ('ChooseHandlerTest', 'Choose does NOT dismiss the dialog — the consumer is responsible')] of 43 cases
1 row(s), 0 off
```

**G12:** `233 actionable tasks: 233 executed`; mutation restored, exact inventory and RED set verified:
```text
G12: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/data/AppDialogRepository.kt -> RED (16 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogRepoHandlerTest', 'Observe updates state when repository emits a dialog'), ('AppDialogRepositoryTest', 'dedup is per-variant — pending RestoreFailure does not block RestoreSuccess publish'), ('AppDialogRepositoryTest', "dismiss clears only the named variant's flags"), ('AppDialogRepositoryTest', 'dismiss the only pending variant emits null'), ('AppDialogRepositoryTest', 'new owned terminal payload replaces a stale pending payload'), ('AppDialogRepositoryTest', 'new terminal owner replaces identical payload and stale dismiss preserves it'), ('AppDialogRepositoryTest', 'publish RestoreSuccess surfaces the variant with payload'), ('AppDialogRepositoryTest', 'publish then dismiss then publish same variant succeeds — the flag was cleared')] of 43 cases
  native: RED exactly [('AppDialogRepoHandlerTest', 'Observe updates state when repository emits a dialog'), ('AppDialogRepositoryTest', 'dedup is per-variant — pending RestoreFailure does not block RestoreSuccess publish'), ('AppDialogRepositoryTest', "dismiss clears only the named variant's flags"), ('AppDialogRepositoryTest', 'dismiss the only pending variant emits null'), ('AppDialogRepositoryTest', 'new owned terminal payload replaces a stale pending payload'), ('AppDialogRepositoryTest', 'new terminal owner replaces identical payload and stale dismiss preserves it'), ('AppDialogRepositoryTest', 'publish RestoreSuccess surfaces the variant with payload'), ('AppDialogRepositoryTest', 'publish then dismiss then publish same variant succeeds — the flag was cleared')] of 44 cases
1 row(s), 0 off
```

**G13:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G13: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/observer/AppDialogObserverImpl.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogObserverImplTest', 'late subscriber does NOT receive past emissions — replay = 0 and no persisted-choice replay')] of 43 cases
1 row(s), 0 off
```

**G14:** `148 actionable tasks: 148 executed`; mutation restored, exact inventory and RED set verified:
```text
G14: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/domain/AppDialogResolver.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [('AppDialogResolverTest', 'RestoreSuccess wins when RestoreFailure is absent')] of 43 cases
1 row(s), 0 off
```

**G15:** `233 actionable tasks: 233 executed`; mutation restored, exact inventory and RED set verified:
```text
G15: harness exit 0: ['=== one-shot: feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt -> RED (1 test(s))', '      expected RED: OK']
  host: RED exactly [] of 43 cases
  native: RED exactly [('AppDialogFeatureSceneIosTest', 'resourcesDatesVariantsAndChoicesRenderAndDispatch')] of 44 cases
1 row(s), 0 off
```

Every O/K/Y/G row matched its required verdict. No compile failure, reused task output, missing XML, extra failed identity or failed restoration was accepted as RED.

### 19.5 Exit census and boundary

Commit 5's census had exactly E19, E20 and E21 off. On commit 6, and again after Y3, the census exited 0 with all twenty-two rows at exit:
```text
E1 api source sets: base={'main': 5} tree={'commonMain': 5} -> ok
E2 api plugin: base='convention.androidLibrary' tree='convention.kmpLibrary' -> ok
E3 impl source sets: base={'androidTest': 1, 'main': 23, 'test': 5} tree={'androidDeviceTest': 1, 'androidHostTest': 1, 'commonMain': 23, 'commonTest': 7, 'iosTest': 2} -> ok
E4 impl plugin: base='convention.composeLibrary' tree='convention.kmpComposeLibrary' -> ok
E5 catalog blobs: base=('eb21921800ae', 'd7f3c6797a7e') tree=('eb21921800ae', 'd7f3c6797a7e') -> ok
E6 portable identities: base=(39, 'b1e9c0b98356a58f') tree=(44, '4666f71428554a91') -> ok
E7 device identities: base=(10, '82e9964f235c9a1b') tree=(10, '82e9964f235c9a1b') -> ok
E8 Native-rejected names: base=2 tree=0 -> ok
E9 platform reads in production: base=39 tree=0 -> ok
E10 MockK test files: base=3 tree=0 -> ok
E11 Context.appDeps readers: base=2 tree=1 -> ok
E12 asContribution in the identity test: base=2 tree=0 -> ok
E13 RestoreRecoveryFiles in app-dialogs: base=2 tree=0 -> ok
E14 InstallEpochSource declared / bound: base=(0, 0) tree=(1, 1) -> ok
E15 formatMediumDate in ui / mapper: base=(3, 0) tree=(0, 2) -> ok
E16 previews / uiMode: base=(9, 4) tree=(9, 0) -> ok
E17 App.kt mounts old / new: base=(1, 0) tree=(0, 1) -> ok
E18 app:common impl edge: base=['implementation'] tree=['api'] -> ok
E19 iOS job: tests / links / $linked/N: base=(16, 16, ['16']) tree=(17, 17, ['17']) -> ok
E20 oracle modules: base=16 tree=17 -> ok
E21 gate entries / modules: base=(9, 13) tree=(10, 15) -> ok
E22 harness exact task match: base=0 tree=1 -> ok
0 item(s) off
```

Commit 3 had five R100 API moves and commit 5 eighteen R100 moves. The final implementation retains seventeen R100 moves against the base; the module's 36 paths became 41. Both catalog blobs remain `eb21921800ae…` and `d7f3c6797a7e…`. No golden was recorded and no PNG blob changed. No suppression was added. The final seven-commit diff check requires exactly 40 A / 34 D / 18 M, plus this specification as M (93 paths); it is repeated after this commit.

### 19.6 Repository, Native and device exit gates

| Gate / XML snapshot label | Command (common flags above) | Gradle evidence | Fresh XML |
| --- | --- | --- | --- |
| `exit/host` | `./gradlew assembleDebug lintDebug testDebugUnitTest` | `2885 actionable tasks: 2885 executed` | 475 suites / 3389 cases / 0 skipped; per-module XML roots in the tables below |
| `exit/lint-rules` | `./gradlew :lint-rules:test` | `9 actionable tasks: 9 executed` | 16 suites / 151 cases / 0 skipped; `lint-rules/build/test-results/test/TEST-*.xml` |
| `exit/detekt` | `./gradlew detekt` | `63 actionable tasks: 63 executed` | no test XML expected |
| `exit/native` | `N17` | `467 actionable tasks: 467 executed` | 122 suites / 906 cases / 0 skipped; per-module XML roots in the tables below |
| `exit/link` | `L17` | `450 actionable tasks: 450 executed` | no test XML expected |
| `exit/device-dialogs` | `./gradlew :feature:app-dialogs:impl:connectedDebugAndroidTest` | `217 actionable tasks: 217 executed` | 1 suites / 10 cases / 0 skipped; `feature/app-dialogs/impl/build/outputs/androidTest-results/connected/androidMain/TEST-*.xml` |
| `exit/device-app` | `./gradlew :app:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.stslex.workeeper.app.DialogStateAcrossGenerationsTest,io.github.stslex.workeeper.app.AppScopeDataStoreSingletonTest,io.github.stslex.workeeper.app.UiAdmissionRaceTest,io.github.stslex.workeeper.app.UiGenerationSwapTest,io.github.stslex.workeeper.app.RuntimeGenerationSwapDeviceTest` | `752 actionable tasks: 752 executed` | 5 suites / 12 cases / 0 skipped; `app/app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` |
| `exit/smoke` | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke` | `2116 actionable tasks: 2116 executed` | 12 suites / 44 cases / 3 skipped; per-module XML roots in the tables below |
| `exit/regression` | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression` | `2116 actionable tasks: 2116 executed` | 27 suites / 88 cases / 0 skipped; per-module XML roots in the tables below |

The repository gate had 475 suites / 3389 cases. All 33 unaffected module/task rows matched HOST_BASELINE by exact identity and outcome. Only app-dialogs changed: 5 → 6 suites, 39 → 43 cases, with the two required renames. All 77 fresh lint reports were compared by module, finding ID, severity, source path and source-line text: the same 480 baseline advisory occurrences, no errors and no finding on a changed Section 7 path. MVI topology remained byte-identical to BASE_MVI_TOPOLOGY; topology remained the six-path 1034-line output above.

| Host XML root (`TEST-*.xml`) | Base suites/cases | Exit suites/cases |
| --- | ---: | ---: |
| `app/app/build/test-results/testDebugUnitTest` | 23/200 | 23/200 |
| `app/common/build/test-results/testDebugUnitTest` | 4/37 | 4/37 |
| `app/wear/build/test-results/testDevDebugUnitTest` | 94/360 | 94/360 |
| `app/wear/build/test-results/testStoreDebugUnitTest` | 94/360 | 94/360 |
| `app/wear/build/test-results/verifyEmulatorAcceptanceRunner` | 1/65 | 1/65 |
| `core/core/build/test-results/testAndroidHostTest` | 9/46 | 9/46 |
| `core/data/backup/api/build/test-results/testAndroidHostTest` | 1/1 | 1/1 |
| `core/data/backup/google-drive/build/test-results/testDebugUnitTest` | 11/109 | 11/109 |
| `core/data/backup/scheduling/build/test-results/testDebugUnitTest` | 2/49 | 2/49 |
| `core/data/backup/worker/build/test-results/testDebugUnitTest` | 3/27 | 3/27 |
| `core/data/dataStore/build/test-results/testAndroidHostTest` | 2/5 | 2/5 |
| `core/data/database/build/test-results/testAndroidHostTest` | 33/166 | 33/166 |
| `core/data/exercise/build/test-results/testAndroidHostTest` | 20/222 | 20/222 |
| `core/ui/kit/build/test-results/testAndroidHostTest` | 30/423 | 30/423 |
| `core/ui/mvi/build/test-results/testAndroidHostTest` | 8/38 | 8/38 |
| `core/ui/navigation/build/test-results/testAndroidHostTest` | 1/1 | 1/1 |
| `core/ui/plan-editor/build/test-results/testAndroidHostTest` | 1/19 | 1/19 |
| `core/ui/start-mode/build/test-results/testAndroidHostTest` | 1/1 | 1/1 |
| `core/wear-protocol/build/test-results/test` | 8/51 | 8/51 |
| `feature/all-exercises/build/test-results/testAndroidHostTest` | 10/66 | 10/66 |
| `feature/all-trainings/build/test-results/testAndroidHostTest` | 9/49 | 9/49 |
| `feature/app-dialogs/impl/build/test-results/testAndroidHostTest` | 5/39 | 6/43 |
| `feature/archive/build/test-results/testAndroidHostTest` | 5/25 | 5/25 |
| `feature/exercise-chart/build/test-results/testAndroidHostTest` | 9/72 | 9/72 |
| `feature/exercise/build/test-results/testAndroidHostTest` | 10/142 | 10/142 |
| `feature/home/build/test-results/testAndroidHostTest` | 12/70 | 12/70 |
| `feature/image-viewer/build/test-results/testAndroidHostTest` | 3/12 | 3/12 |
| `feature/live-workout/build/test-results/testAndroidHostTest` | 21/235 | 21/235 |
| `feature/past-session/build/test-results/testAndroidHostTest` | 7/41 | 7/41 |
| `feature/plan-editor/build/test-results/testAndroidHostTest` | 6/42 | 6/42 |
| `feature/recovery/build/test-results/testDebugUnitTest` | 8/96 | 8/96 |
| `feature/settings/build/test-results/testDebugUnitTest` | 10/120 | 10/120 |
| `feature/single-training/build/test-results/testAndroidHostTest` | 4/62 | 4/62 |
| `feature/wear-bridge/build/test-results/testDebugUnitTest` | 9/134 | 9/134 |

| Native XML root (`TEST-*.xml`) | Suites/cases |
| --- | ---: |
| `core/ui/kit/build/test-results/iosSimulatorArm64Test` | 2/2 |
| `core/ui/mvi/build/test-results/iosSimulatorArm64Test` | 5/23 |
| `core/ui/navigation/build/test-results/iosSimulatorArm64Test` | 1/1 |
| `core/ui/plan-editor/build/test-results/iosSimulatorArm64Test` | 2/20 |
| `core/ui/start-mode/build/test-results/iosSimulatorArm64Test` | 2/2 |
| `feature/all-exercises/build/test-results/iosSimulatorArm64Test` | 11/67 |
| `feature/all-trainings/build/test-results/iosSimulatorArm64Test` | 10/50 |
| `feature/app-dialogs/impl/build/test-results/iosSimulatorArm64Test` | 7/44 |
| `feature/archive/build/test-results/iosSimulatorArm64Test` | 6/26 |
| `feature/exercise-chart/build/test-results/iosSimulatorArm64Test` | 9/69 |
| `feature/exercise/build/test-results/iosSimulatorArm64Test` | 11/143 |
| `feature/home/build/test-results/iosSimulatorArm64Test` | 14/72 |
| `feature/image-viewer/build/test-results/iosSimulatorArm64Test` | 4/13 |
| `feature/live-workout/build/test-results/iosSimulatorArm64Test` | 19/227 |
| `feature/past-session/build/test-results/iosSimulatorArm64Test` | 7/41 |
| `feature/plan-editor/build/test-results/iosSimulatorArm64Test` | 7/43 |
| `feature/single-training/build/test-results/iosSimulatorArm64Test` | 5/63 |

The final Native oracle verified seventeen modules, including `feature:app-dialogs:impl: 44 executed / 0 skipped / 0 failed / 0 errored — verified 44 exact identities`. All sixteen unaffected modules also matched NATIVE_BASELINE in full. Device-target executables were checked individually for existence and modification time after the final link start: `17/17 linked`.

Android gates used only the recorded API-34 `emulator-5554`, with animations disabled and keyguard dismissed; instrumentation, rotation, animation scales and package update times were sampled before and after each gate. Around every exit suite a dedicated threadtime logcat writer was started after clearing logcat and stopped by its recorded PID. The exact Smoke/Regression name inventory was read from the base git objects and annotation selection, including method-level Regression; its 12/44/3 and 27/88/0 suites/cases/skips match Phase 7.16 Section C17.6's executed record. Current fresh XML matched every tuple and outcome; the previous run's raw XML was not available on this host. The three Smoke skips remain `pendingFeatureRewrite` in archive, all-trainings and all-exercises. `assert_mvi_device_identities.py` verified the same two MVI device identities after Smoke.

- `device-dialogs` logcat: no E-priority `SCREEN_` lines.
- `device-app` logcat: no E-priority `SCREEN_` lines.
- `smoke` logcat: no E-priority `SCREEN_` lines.
- `regression` logcat: no E-priority `SCREEN_` lines.

Commit 7 applies the five documentation reference paths, changes only this specification's Status and Section 19, and repeats the topology, MVI, personal-data and separate Detekt gates. The mandatory module test also runs on its staged tree. These final documentation-commit checks and the post-commit path/signature checks are recorded in the maintainer handoff because their final commit object does not exist while this section is being written.

### 19.7 Environment

- Xcode 27.0 (27A266a), first launch complete; installed iOS runtimes 18.0, 18.2, 18.6 and 26.5. CI selects Xcode 26.6. JDK 21.0.11 (JBR). SDK and existing local signing/configuration files were used in place; their contents were not printed, copied or moved.
- Python paths: `/Users/stslex/.local/bin/python3.11` 3.11.16; `/opt/homebrew/bin/python3` 3.15.2; `/opt/homebrew/bin/python3.13` 3.13.18; `/opt/homebrew/bin/python3.14` 3.14.10; `/opt/homebrew/bin/python3.15` 3.15.2; `/usr/bin/python3` 3.9.6. The two 3.15 paths were both executed even though they resolve to the same interpreter.
- AC power and open lid were rechecked before resuming; `caffeinate -dims` covered accepted work. The entry power interruptions are described below. Only owned Android emulator PIDs were stopped; the emulator was shut down between device and heavy Native work. Final emulator/caffeinate shutdown is checked again before requesting review.
- Every Gradle invocation, mutation harness child and commit hook used this run's isolated daemon registry. Before each load-bearing gate, Gradle clients/daemon command lines and cwd were sampled; no competing device-capable client was admitted. Free disk, `vm_stat` and swap were sampled around every load-bearing Gradle gate. No disk-full recovery was required. Spotlight indexing was enabled and left unchanged.

### 19.8 Approved repairs and deviations

1. C4's reference built and passed 43 tests, but executed Detekt reported `UnusedParameter` for `UndoRestoreConfirmationDialog`'s `dialog` parameter. The maintainer approved a Section 0.4(b) amendment: remove that unused renderer parameter, its host/preview arguments and two obsolete preview imports; preserve the host's dialog-capturing callbacks. The matching change was carried through c5's commonMain move. C4's original digest was checked before repair; the original c4/c5 chain was also replayed and digest-checked in a temporary index before applying the approved replacement c5. No user file or real index was changed by that proposal replay.

Repair snapshot `c4/approved-repair.json`:
```diff
--- a/feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt
+++ b/feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt
@@ -36,7 +36,6 @@
         )
 
         is AppDialog.UndoRestoreConfirmation -> UndoRestoreConfirmationDialog(
-            dialog = dialog,
             dateLabel = dateLabel,
             onConfirm = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.ConfirmUndo)) },
             onCancel = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Cancel)) },
--- a/feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt
+++ b/feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt
@@ -6,8 +6,6 @@
 import androidx.compose.ui.res.stringResource
 import androidx.compose.ui.tooling.preview.Preview
 import androidx.compose.ui.window.DialogProperties
-import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
-import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
 import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
 import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
 import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
@@ -22,7 +20,6 @@
  */
 @Composable
 internal fun UndoRestoreConfirmationDialog(
-    dialog: AppDialog.UndoRestoreConfirmation,
     dateLabel: String,
     onConfirm: () -> Unit,
     onCancel: () -> Unit,
@@ -51,12 +48,6 @@
 private fun UndoRestoreConfirmationDialogPreview() {
     AppTheme {
         UndoRestoreConfirmationDialog(
-            dialog = AppDialog.UndoRestoreConfirmation(
-                undoRef = UndoRef(
-                    RestoreOwnerId("00000000-0000-4000-8000-000000000009"),
-                ),
-                originalDataDateEpochMs = 1_700_000_000_000L,
-            ),
             dateLabel = "Nov 14, 2023",
             onConfirm = {},
             onCancel = {},
```

- `feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt`: `f39770af2c8b8ed581f1eebd4b39e64e25e0543f` → `1e4d872089d8a3a40fb57ea3b5a46d2874821594`.
- `feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt`: `fd0a3d16d0fbead1c0e3d158f07a30d591a84496` → `ba9e3698fe11395bc30e7f75d04890fa018637ac`.

Repair snapshot `c5/approved-repair.json`:
```diff
diff --git a/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt b/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt
index f39770af2c8b8ed581f1eebd4b39e64e25e0543f..1e4d872089d8a3a40fb57ea3b5a46d2874821594 100644
--- a/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt
+++ b/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt
@@ -36,7 +36,6 @@ internal fun AppDialogHostContent(
         )
 
         is AppDialog.UndoRestoreConfirmation -> UndoRestoreConfirmationDialog(
-            dialog = dialog,
             dateLabel = dateLabel,
             onConfirm = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.ConfirmUndo)) },
             onCancel = { onChoice(AppDialogUserChoice(dialog, AppDialogUserAction.Cancel)) },
diff --git a/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt b/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt
index af508b6b83d50cbad8006d43e43a3ee052cde870..4f44c71277e95e562cb4717a10d4be6fa13816fa 100644
--- a/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt
+++ b/feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt
@@ -4,8 +4,6 @@ package io.github.stslex.workeeper.feature.app_dialogs.impl.ui
 import androidx.compose.runtime.Composable
 import androidx.compose.ui.tooling.preview.Preview
 import androidx.compose.ui.window.DialogProperties
-import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
-import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
 import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmationDialog
 import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
 import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
@@ -26,7 +24,6 @@ import org.jetbrains.compose.resources.stringResource
  */
 @Composable
 internal fun UndoRestoreConfirmationDialog(
-    dialog: AppDialog.UndoRestoreConfirmation,
     dateLabel: String,
     onConfirm: () -> Unit,
     onCancel: () -> Unit,
@@ -65,12 +62,6 @@ private fun UndoRestoreConfirmationDialogDarkPreview() {
 private fun UndoRestoreConfirmationDialogPreview(themeMode: ThemeMode) {
     AppTheme(themeMode = themeMode) {
         UndoRestoreConfirmationDialog(
-            dialog = AppDialog.UndoRestoreConfirmation(
-                undoRef = UndoRef(
-                    RestoreOwnerId("00000000-0000-4000-8000-000000000009"),
-                ),
-                originalDataDateEpochMs = 1_700_000_000_000L,
-            ),
             dateLabel = "Nov 14, 2023",
             onConfirm = {},
             onCancel = {},
```

- `feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/AppDialogHostContent.kt`: `f39770af2c8b8ed581f1eebd4b39e64e25e0543f` → `1e4d872089d8a3a40fb57ea3b5a46d2874821594`.
- `feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/ui/UndoRestoreConfirmationDialog.kt`: `af508b6b83d50cbad8006d43e43a3ee052cde870` → `4f44c71277e95e562cb4717a10d4be6fa13816fa`.

2. C5's first early gate reported `234 actionable tasks: 234 executed` and compiled on both targets, but the same three Observe identities failed on host and Native. All sixteen real file DataStore identities and the Native scene passed. Their background collector had not run: `advanceUntilIdle()` stops when only `backgroundScope` work remains. The maintainer approved replacing that scheduler step by `runCurrent()` only in the three Observe tests, plus its import. Names, repository setup, production calls, assertions, D5 and the scene were preserved. See the [coroutines backgroundScope contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/kotlinx.coroutines.test/-test-scope/background-scope.html). The repeated early gate reported `233 actionable tasks: 233 executed`, host 43/43 and Native 44/44; every subsequent positive gate and required negative control used the repaired tree.

```diff
--- a/feature/app-dialogs/impl/src/commonTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandlerTest.kt
+++ b/feature/app-dialogs/impl/src/commonTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandlerTest.kt
@@ -31,6 +31,7 @@
 import kotlinx.coroutines.test.TestDispatcher
 import kotlinx.coroutines.test.TestScope
 import kotlinx.coroutines.test.advanceUntilIdle
+import kotlinx.coroutines.test.runCurrent
 import kotlinx.coroutines.test.runTest
 import kotlinx.coroutines.withContext
 import kotlin.test.Test
@@ -74,7 +75,7 @@
         val handler = AppDialogRepoHandler(repository, resources, store)
 
         handler.invoke(Action.RepoAction.Observe)
-        advanceUntilIdle()
+        runCurrent()
 
         assertEquals(dialog, store.state.value.current)
     }
@@ -91,7 +92,7 @@
         val handler = AppDialogRepoHandler(repository, resources, store)
 
         handler.invoke(Action.RepoAction.Observe)
-        advanceUntilIdle()
+        runCurrent()
 
         assertNull(store.state.value.current)
     }
@@ -108,7 +109,7 @@
         val handler = AppDialogRepoHandler(repository, resources, store)
 
         handler.invoke(Action.RepoAction.Observe)
-        advanceUntilIdle()
+        runCurrent()
 
         assertEquals(State(current = dialog, dateLabel = "medium:2000"), store.state.value)
     }
```

`feature/app-dialogs/impl/src/commonTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/handler/AppDialogRepoHandlerTest.kt`: `c6cc7b0251672d950b50113702d815fa7c4f91b5` → `4d4f31e81fa2ef23e749c94ba1db2c588bb281b6`.

| Approved reference | Patch SHA-256 | Staged digest | No-renames numstat |
| --- | --- | --- | --- |
| c4 | `1a680290aaf21e9cbaa385745bfa997d224229b2dc594922fc82209a3e3a4c93` | `6ca0195e21132dc6fe8b917fa702b524a76bbb5167dfb2f3059ebd943a745a16` | +175 −48 |
| c5 after renderer repair | `be1aa217f5e0cd2b8570751e8e900e8b7701cb8f6c61e34c98f91881777456b9` | `6e84f28e371e88842f6ed34bf5409b451657a59752ceffa85a319005d7df6817` | +3042 −2305 |
| c5 after scheduler repair | `9a9854c338b903172a790055a8ae607c43e5d1331e05b9982209f42a1bac65eb` | `2004870315c807001c89b527fa943c757878f4f2b642ecbbac3d2b8a51d7f079` | +3043 −2305 |

3. The c3 pre-commit hook inherited project-default Gradle heap/workers (8g/4); all evidence gates explicitly used 4g/2 or device 3g/2. Future commit commands also constrain the hook through GRADLE_OPTS.
4. Gradle outgoingVariants threw ConcurrentModificationException in ConfigurationReportModelFactory on both app-dialogs and unchanged live-workout, even with --no-parallel. With the standard dependencies report first in the same invocation, the unmodified outgoingVariants passed (6/6 executed). No project or gate script changed.
5. Power preflight stopped earlier device attempts. One shell wrapper continued to boot an emulator after its preceding preflight failed because the commands were separated by a newline. That owned emulator and caffeinate were stopped immediately; no device Gradle gate started in that attempt. The replacement Python preflight checked three samples and aborted before spawning anything on failure. Accepted device results came only from the corrected path.
6. The unchanged canonical K tool's final internal `git status` uses its explicit MAIN cwd instead of a `-C` argument. Its caller additionally checked `git -C MAIN` before and after; no kit file was rewritten to change the tool's command.

7. The final documentation pre-test attempt stopped in preflight with AC power present and `AppleClamshellState = Yes`, before Gradle started. The owned caffeinate and isolated daemons were stopped; the Android emulator was already down. After the maintainer reopened the lid, three AC/open-lid samples passed and caffeinate was restarted. The unexecuted attempt was archived separately and contributes no green gate evidence.

### 19.9 Verification limits and handoff

No physical iOS device was used: iosArm64 test binaries were linked, never executed. The Native scene covers production renderers, EN resources, the date mapper with the specified date stub and all choices; it does not instantiate the app-root Store/Metro graph. Android extension and generation device tests cover that graph. RU values are pinned statically, and `AppDialogHost`'s two State reads and root-factory flow are pinned statically. The owner's post-merge dev-device check remains Section 17's non-gate: Drive restore success with its date, Undo confirmation with its date and undo success, EN and RU, and a restore failure if one can be forced. Section 18's Q1/settings policy, two dead actions, Activity-scoped wording, skills file and primary-locale item remain carried decisions. The maintainer merges; this implementation neither merges nor enables auto-merge.

---

## Appendix A — the reference patches

Delivered beside this document as `kmp-phase-7-17/patches/c1.patch` … `c7.patch`
(`git diff -M --full-index` of each prototype commit, applied with `git apply --index`). After
applying a patch and before any repair, the staged change's digest is

    git diff --cached --no-renames --raw --no-abbrev | awk '{print $4, $5, $6}' | LC_ALL=C sort | shasum -a 256

(commit 7's over the five documentation paths, this document excluded).

| Patch | Lines | sha256 of the patch | Digest of the staged change |
| --- | ---: | --- | --- |
| c1 | 149 | `2efb2cb9ddebb5b324d23637fbc7f56967415398fc0c751cb45e45eeb53dfdf9` | `bb08b93137605602db4c7d2861bd13b22375774033884ed8d5667a4bda3984fd` |
| c2 | 32 | `650fc84fc4c99af5807e761d8325f834d55fefd8b45c561449f2088fd9a60b1f` | `babe59a936fee43bd03d8f314ed018ae7ff6ea34df0587720345ffa5a4115bff` |
| c3 | 155 | `8b9b0f249de8bb2271bc986550c3a6508ca02a1fef882b05166659567cdb22e3` | `8730fc08223809171e7e3bb62affe4c008f1a740d4fe14a67b6fd31c77637336` |
| c4 | 526 | `0701a4eb1d455eeebf510df8d8f3c91a10bc1a93353763bc1215ecaf2eb6984f` | `d059f5974cf13c52a71eee0828c9cdb3e285347ae506e4294fe866b44f866ee2` |
| c5 | 2039 | `53a9b34b48301897c4f03aaf311b27072fe3e1ad5e253d5cf3ec802fe324ec96` | `3179fbff5900057b2ae482c6f0659d53fb17d7464eaf342c92166c4feae5da4a` |
| c6 | 471 | `99148337916e7e0309b54acd08a9181f81693f9cf93de18873ed91a631b1c410` | `33bf476d9de0bc4550c36a329c7500c1d7b406e6061ced0779c701f3833b8a8a` |
| c7 | 360 | `5b0547f3fbe3f066b9237b17314214ecac336af772b14cac5e20e56b294d71fd` | `e4c1cd7593ed3aa564c6978cab96b5bfccebf942f32ab1a1b27164b06b6de59b` |

The seven applied in order to `a4bcd440d` plus this document reproduce the prototype's trees.

## Appendix B — the run's tools

Delivered beside this document as `kmp-phase-7-17/tools/` and never committed; the sha256 of every
patch and tool is in `kmp-phase-7-17/SHA256SUMS`.

| Tool | Section |
| --- | --- |
| `diffproof.py`, `c1_rows.json` | 10.0 |
| `harness_reuse_check.py` | 10.6, 12 |
| `k_rows.py` | 13.1 |
| `g_rows.py` | 13.2 (`--dry-run` checks the fifteen anchors) |
| `o_rows.py` | 13.3 |
| `census.py`, `y_rows.py` | 14 |
