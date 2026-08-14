# Technical Debt Register

This document tracks known debt that should be addressed after functional delivery. It is a **living ratchet**: entries are added when debt is incurred, removed when paid down, and audited periodically against reality (last full audit: 2026-04-28 via dual-model triangulation; v2.0 stage updates applied 2026-04-28).

Each tracked location should carry a `TODO(tech-debt): <category> — <ref>` marker in code so debt is grep-able during development.

## How to read this document

- **Severity** is informal: 🔴 critical for release, 🟡 medium (polish/cleanup), 🟢 low (architectural hygiene).
- **Status** indicates current state. ACTIVE = work to do; PARKED = intentionally deferred to a known horizon.

---

## UI Mapping Boundary Debt

**Rule:** UI composables and graph files render already mapped, localized, and formatted state. Mapping and localization shaping happen in handler / state-mapper layers. See [architecture.md → UI types vs domain types](architecture.md).

| Severity | Location | Description |
|---|---|---|
| 🟢 | [feature/archive/.../ui/ArchiveGraph.kt](../feature/archive/src/main/kotlin/io/github/stslex/workeeper/feature/archive/ui/ArchiveGraph.kt) | Snackbar templates (`restoredTemplate.format(event.item.name)`) substituted in graph. Should be pre-formatted; event payload should carry the ready string. |
| 🟢 | [feature/exercise/.../ui/ExerciseGraph.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt) | `Event.ShowImageError` → `when (event.errorType) { ... }` shaping in graph. Move to mapper or carry resolved message in the event itself. |
| 🟢 | [feature/single-training/.../ui/SingleTrainingGraph.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt) | Discard-dialog title/body strings still chosen in graph. Push to state or to event payload. |
| 🟢 | [feature/home/.../ui/components/ActiveSessionBanner.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/ActiveSessionBanner.kt) | Concatenation `stringResource(label) + " · " + stringResource(progress)` in composable. Pre-format full label in `HomeUiMapper`. |
| 🟢 | [feature/home/.../ui/components/RecentSessionRow.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/RecentSessionRow.kt) | String interpolation `"${item.finishedAtRelativeLabel} · ${item.durationLabel}"` in composable. Add a single combined label to `RecentSessionItem`. |
| 🟢 | [feature/home/.../ui/components/TrainingPickerSheet.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/TrainingPickerSheet.kt) | `listOfNotNull(...).joinToString(" · ")` in composable. Same pattern — pre-format in mapper. |
| 🟢 | [feature/past-session/.../ui/PastSessionGraph.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionGraph.kt) | `Event.ShowError` → `when (event.errorType) { ... }` shaping in graph. Same fix as ExerciseGraph. |
| 🟢 | [feature/past-session/.../ui/PastSessionScreen.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionScreen.kt) | Error headline `when (errorType) { ... }` in composable. Push message into `Phase.Error` payload. |

---

## Schema Migration Debt

| Severity | Location | Description |
|---|---|---|
| 🟢 | [core/data/database/.../migration/MigrationsRegistry.kt](../core/data/database/src/main/kotlin/io/github/stslex/workeeper/core/data/database/migration/MigrationsRegistry.kt) | **Pre-Play-Store schema history.** No `Migration(1, 2)` / `(2, 3)` / `(3, 4)` / `(4, 5)` registered — schemas v1-v4 predate the Play Store release and previous debug builds were never published, so destructive resets were acceptable during pre-production development. `MIN_SUPPORTED_SCHEMA_VERSION` is derived from `MIGRATIONS` (currently 5) and the registry-completeness test only walks from that point forward. The `fallbackToDestructiveMigration*` clause is intentionally absent on the Room builder — a v1-v4 install hitting a v6 code path now fails closed (pre-restore → `BackupError.MissingMigrationPath`; startup → routes to Scenario 2 RecoveryActivity once that ships in PR-C/D/E). **Trigger to act:** a community user reports installing a very old debug build and wanting to migrate their data forward — unlikely scenario; until then, the destructive-fallback removal is the correct trade. Adding the older `Migration_X_Y` objects later updates `MIN_SUPPORTED_SCHEMA_VERSION` automatically. |

---

## Room 2→3 cross-version upgrade proof — manual, NOT in the automated suite

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/data/database/.../Room3RoundTripDeviceTest.kt](../core/data/database/src/androidTest/kotlin/io/github/stslex/workeeper/core/data/database/Room3RoundTripDeviceTest.kt) | **Two distinct guarantees — one automated, one manual.** (1) COVERED repeatably by `Room3RoundTripDeviceTest` (normal `connectedAndroidTest`): Room 3 round-trips the production schema on a real file — write → close → re-open a fresh `AppDatabase` on the same file → read exact values → PagingSource DAO → transactional write persists (self-seeding; known-negative proves the read can observe absence). (2) NOT automated: that a file written *specifically by the Room 2.8.4 runtime* is readable by Room 3 — the real Play cross-version upgrade path. Proven ONCE manually on 2026-07-18 (Room-2 write APK + Room-3 read APK, 3/3, plus a real dev-app launch on the Room-2 file with zero Room integrity/migration/driver exceptions), but it is NOT in the suite because it requires a cross-branch, two-APK, seeded-file dance that `connectedAndroidTest`'s auto-uninstall defeats. **To re-run the cross-version proof before the final land:** (a) on a Room-2 tip author a test that writes the real file-backed `app.db` via the production builder with known values, `./gradlew :core:data:database:installDebugAndroidTest` then `adb shell am instrument -w -e class <FQN> io.github.stslex.workeeper.core.data.database.test/androidx.test.runner.AndroidJUnitRunner`; (b) `adb shell run-as io.github.stslex.workeeper.core.data.database.test ls -l databases/` → confirm `app.db` + size (the "before"); (c) switch to the Room-3 tip, `installDebugAndroidTest` (install -r, NO uninstall), `run-as ls -l` AGAIN and confirm `app.db` survived byte-identical (the ★ vacuity gate — if gone, the test is vacuous, STOP); (d) `am instrument` a Room-3 read test asserting the exact Room-2 values. Do NOT use `connectedAndroidTest` for this — it uninstalls the test APK and wipes the file. **Trigger to act:** before the final ff-merge, if the cross-version proof is wanted fresh; or if `installDebugAndroidTest`/AGP behaviour changes. |

---

## androidTest navigation-library coupling — two named detekt exclusions (nav3 stage 1.1)

**Rule:** nothing under `app/app/src/androidTest` imports `androidx.navigation*`. The instrumented
navigation oracle reaches the app through the semantics tree and through Room only, so the same
suite survives the Nav2 → Nav3 swap without edits. Enforced by `:app:app:detektAndroidTestNavigation`
(its own `Detekt` task over `src/androidTest/{kotlin,java}`, config `lint-rules/detekt-androidtest.yml`)
— the plain `detekt` task cannot see that source set at all.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [app/app/.../androidTest/.../ExerciseCreatePersistenceTest.kt](../app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/ExerciseCreatePersistenceTest.kt) | Mounts its own `NavHost` + `rememberNavController()` inside `setContent` as scaffolding for a DI / persistence assertion (it reads `exerciseDao` back after a Store→Room write). Not part of the navigation oracle. **Named path exclusion, not a baseline** — a baseline rots silently, an exclusion is visible in the config it weakens. **Revisit at stage 1.3 — `NavHost` disappears** and the scaffolding has to be rewritten anyway; that is the moment to move it onto `MainActivity` or delete the bespoke host. |
| 🟡 | [app/app/.../androidTest/.../AllTrainingsExtensionDbVisibilityTest.kt](../app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/AllTrainingsExtensionDbVisibilityTest.kt) | Same shape and same reason: a bespoke `NavHost` mounting `allTrainingsGraph` to prove the graph extension reads the parent in-memory database. **Revisit at stage 1.3.** |

Refactoring either onto `MainActivity` was outside the stage 1.1 scope fence, which added tests only.
Both exclusions are load-bearing rather than decorative: removing them from the config reds the task
on exactly these two files (4 import lines), which is how they were verified.

---

## Bottom-bar selection semantics are not published — `ApplicationBottomBarTest` is 4/4 red (nav3 stage 1.1, 2026-08-14)

**Not the flaky-teardown entry below.** That one is a race in `checkAppClosed()` affecting one test
intermittently. This is a deterministic, every-run failure of all four, on a different assertion, and
the two must not be conflated when triaging.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/ui/kit/.../navbar/AppNavBar.kt](../core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/components/navbar/AppNavBar.kt) ↔ [app/app/.../ApplicationBottomBarTest.kt](../app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/ApplicationBottomBarTest.kt) | **`AppNavBar` publishes no `Selected` semantics at all, so every selection assertion in `ApplicationBottomBarTest` fails.** **Mechanism:** each item is a `Box` carrying `Modifier.clickable(interactionSource, indication = null)` and a `testTag` — there is no `Modifier.selectable`, no `Role.Tab`, and no `NavigationBarItem`. `SemanticsProperties.Selected` is therefore never written, and `assertIsSelected()` / `assertIsNotSelected()` cannot pass on any item, selected or not. The selected item is expressed **visually only** (pill offset + icon tint), which is also an accessibility defect: TalkBack has no way to announce which destination is current. **Proof:** grep on the component returns zero hits for `selectable`, `Role.`, `NavigationBarItem` and `semantics`; the test class has exactly **4** `@Test` methods and all four reach `checkSelectedBottomAppBar` (three via `checkScreenOpen`, plus `navigateToExercisesTrainingsAndBack` directly), so the failure count is 4 by construction, with assertion text `Failed to assert the following: (Selected = 'true')`. **Pre-existing, not introduced by the nav3 branch:** `AppNavBar.kt` is blob `149108b8` at `HEAD`, at `bcf70b63` and at `origin/dev` — byte-identical across all three. **Unblock condition: restore the semantics in PRODUCTION** — `Modifier.selectable(selected = …, role = Role.Tab)` on the item box, or adopt `NavigationBarItem`. **Do NOT "fix" this by editing the test**: the assertions are correct and the a11y gap is real; weakening them converts a production defect into a silent one. **Pinned expectation — at stage 1.3 this is mechanical:** exactly these **4** failures, all in `ApplicationBottomBarTest`, all with the assertion string above, and **nothing else**, means the instrumented suite is clean. Any fifth failure, or a different assertion string, is new and must be triaged rather than waved through. **Deadline: before stage 1.3.** |

**Masking dependency — the a11y fix must not land without a decision (added 2026-08-14).**
`navigateToExercisesAndBack` is BOTH one of the four deterministically red tests pinned above AND the
carrier of the intermittent `checkAppClosed()` teardown race filed in its own entry below. Today the
flake is **masked**: the test already fails deterministically on the selection assertion, so the race
cannot produce a distinguishable extra failure and the pin holds trivially. The a11y fix — scheduled
before stage 1.3 — turns this class green and **unmasks** the race. From that moment "exactly these
failures and nothing else" starts breaking by chance, and 1.3 triage becomes ambiguous in exactly the
way this pin exists to prevent. Two ways out; the a11y PR ships with one of them chosen (Ilya's call,
recorded here so that PR cannot close this entry without it):
**(a)** fix the teardown race in the same PR as the a11y fix, so the class is genuinely deterministic
when 1.3 triages against it; or
**(b)** re-pin with the flake as a named, expected intermittent that does not invalidate a run — with
an explicit rerun rule (e.g. a single retry of exactly that test, anything else is a finding).

---

## DataStore singleton bypass — three remaining stragglers (nav3 stage 1.1, 2026-08-14)

**Rule:** a `DataStore` is a per-file singleton. `DataStoreProvider` enforces this with a **static**
`ConcurrentHashMap<String, DataStore<Preferences>>` in its companion — memoized per file name for the
**lifetime of the process**, not the lifetime of the DI graph. Any class that calls
`PreferenceDataStoreFactory.create { … }` itself bypasses that map.

**Why this surfaces now.** `MetroTestRule` installs a fresh `AppGraph` per test, so every
`@SingleIn(AppScope)` holder is rebuilt. A bypassing holder builds a *second* `DataStore` over the
same file, and DataStore 1.1+ throws `IllegalStateException: There are multiple DataStores active for
the same file: …` rather than sharing. **The failure is second-touch, not first-touch:** the first
test to reach the surface passes and the next one throws, which reads as flakiness if the mechanism
is not known.

**Proof this is real and not theoretical:** the identical failure is already observed on the fourth
member of this family, `AccountDataStoreImpl` — `RouteReachabilityTest.archiveOpensFromSettingsAndSettingsReturns`
throws it through `DriveBackupAuth`'s `observeAccount` collector on
`…/files/datastore/backup_account_prefs.preferences_pb`. That one is fixed separately; its module
already depends on `:core:data:dataStore`, so it needs no build change.

**Why these three are not fixed alongside it:** each lives in a module with **no dependency edge on
`:core:data:dataStore`** (`core/data/backup/scheduling` and `feature/app-dialogs/impl` build scripts
both lack it, verified). Adding a module edge is a build-graph change, out of a test-only commit's
scope fence.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/data/backup/scheduling/.../BackupPreferencesRepositoryImpl.kt:48](../core/data/backup/scheduling/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/scheduling/BackupPreferencesRepositoryImpl.kt) | File `backup_scheduling_prefs`. **Reachable from `AppGraph` directly** — `AppGraph.kt:140` exposes `backupPreferencesRepository`. |
| 🟡 | [core/data/backup/scheduling/.../RestoreStateRepositoryImpl.kt:53](../core/data/backup/scheduling/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/scheduling/RestoreStateRepositoryImpl.kt) | File `restore_state_prefs`. Not a direct `AppGraph` accessor; reached through the restore flow. |
| 🟡 | [feature/app-dialogs/impl/.../AppDialogRepository.kt:51](../feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/data/AppDialogRepository.kt) | File `app_dialogs_prefs`. **Reachable from `AppGraph` directly** — `AppGraph.kt:168` exposes `appDialogRepository`. |

**Consequence to expect.** Two of the three hang off `AppGraph` accessors, so **any future
instrumented test that touches Settings' backup section twice, or that raises an app dialog twice,
hits the identical `IllegalStateException`** — including the stage 1.2/1.3 additions to this very
suite (`StoreRetentionTest`, `BackStackStateRestorationTest`). Read such a failure as this entry, not
as a navigation regression.

**Unblock condition:** add the `:core:data:dataStore` edge to each module and route the store through
`DataStoreProvider` (extend `BaseDataStore`, or inject the provider) so the static memoization
applies. **Deadline: before stage 1.3** — the suite grows there, and the failure count grows with it.

---

## Two of stage 1.1's four oracle classes were never written (nav3 stage 1.1, 2026-08-14)

Stage 1.1's spec specified **four** classes: `RouteReachabilityTest`, `StoreRetentionTest`,
`NavigationResultTest` and `BackStackStateRestorationTest`. #221 shipped only the first — the
Mac-side prompt renumbered its Phase 3 as "close the four gaps", colliding with the spec's own
3a–3d (the four test classes), and three dropped without anyone noticing. The 28-test baseline
at `dev` shows it: `RouteReachabilityTest` 15, and no sign of the other three.

`NavigationResultTest` was written in stage 1.2 (PR #222) because that stage rewrote both result
flows and nothing covered them. **The other two remain missing and are due before 1.3**, because
they guard 1.3's own concern — the entry-scoping decorator — not 1.2's:

| Class | What it must pin | Why before 1.3 |
|---|---|---|
| `StoreRetentionTest` | a Store survives a round trip to another destination and back, and is destroyed when its destination leaves the back stack for good | 1.3 replaces the mechanism that scopes Stores to entries; without this, a Store silently recreated per visit looks identical to one correctly retained |
| `BackStackStateRestorationTest` | back-stack depth and per-entry state survive process death / configuration change | Nav3's back stack is app-owned state rather than library-owned; nothing currently fails if restoration regresses |

**`StoreRetentionTest` has a specific first question, already surfaced.** `NavigationResultTest`'s
plan-editor half was mutation-tested in PR #222 and does **not** discriminate: with
the reload behind `Action.Common.PlanResultReceived` removed, the session still comes back showing
the newly saved plan. `loadSession` is a one-shot read, so something re-runs it — most likely
`Action.Common.Init`, which would mean the LiveWorkout Store is **not** retained across the
PlanEditor round trip. If that is so, `processReload`'s `withExpansionCarriedFrom(previous)`
preserves state that was already lost, and the reload itself may be redundant. Settle this in
`StoreRetentionTest`; do not assume either answer.

**Consequence to hold in mind meanwhile:** no test in the suite would currently catch a broken
`Screen.PlanEditor` result flow. Per the `.catch { onError(it) }` swallow in
`AppCoroutineScopeImpl`, that failure is silent — the screen shows default state and every test
stays green.

## `AllTrainingsItemName_*` / `AllTrainingsItemMeta_*` are not row handles (nav3 stage 1.1, 2026-08-14)

| Severity | Location | Description |
|---|---|---|
| 🟢 | [core/ui/kit/.../list/AppListRow.kt:115](../core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/components/list/AppListRow.kt) ↔ [feature/all-trainings/.../TrainingRow.kt:79](../feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/TrainingRow.kt) | **These two tags look like per-row selectors and cannot be used as one.** **Mechanism:** `AppListRow` applies `nameTestTag` / `metaTestTag` to the name and meta **`Text`s** (`AppListRow.kt:115` and `:123`), which are descendants of the inner `Row`. The click arrives on that `Row` via the `rowModifier` seam — `TrainingRow.kt:77` passes `combinedClickable(onClick, onLongClick)` — and `Modifier.clickable` merges descendant semantics, so the child's tag is **absent from the merged tree** that `onNodeWithTag` queries by default, while the node that actually carries the click action **has no tag of its own**. Net effect: the tag is unreachable for a click, and `useUnmergedTree = true` would find the `Text` but clicking it would not dispatch the row's handler. **Proof — measured, not reasoned:** selecting by tag times out on a row that is demonstrably on screen; the same row, same timing, responds to a text selector. **Current workaround:** `NavPaths.openTraining` clicks by unique seeded name and carries this explanation at its call site. **Unblock condition:** add a row-level `testTag` on the `rowModifier` chain (alongside `combinedClickable`, i.e. on the node that owns the click) — `AllTrainingsItemRow_<uuid>`. Exactly **one** call site changes: `NavPaths.openTraining`. The existing name/meta tags are still legitimate for asserting *text content*; they are simply not handles. **Deadline: before stage 1.3** — `StoreRetentionTest` and `BackStackStateRestorationTest` both need to open list rows, and each one written against a text selector is another call site to unpick later. |

---

## Release signing material is required at configuration time — every unsigned task fails without `keystore.properties` (nav3 stage 1.1, 2026-08-14)

| Severity | Location | Description |
|---|---|---|
| 🟡 | [build-logic/convention/.../ConfigureApplication.kt:95](../build-logic/convention/src/main/kotlin/io/github/stslex/workeeper/ConfigureApplication.kt) | **A machine without release signing material cannot run a debug build, Paparazzi, or unit tests.** **Mechanism:** `configureSigning` runs at plugin-apply time and assigns `keystoreProperties.getProperty("keyAlias")` (and friends) into both the `release` **and `debug`** signing configs. `gradleKeystoreProperties` returns an **empty** `Properties` when `keystore.properties` is absent, so every `getProperty` call returns null and the AGP setter throws at **configuration** — before any task runs, for tasks that never sign anything (`assembleDebug`, `verifyPaparazziDebug`, `testDebugUnitTest` all die identically). **The failure message names neither the file nor the remedy:** `An exception occurred applying plugin request [id: 'workeeper.android.application.dev'] > getProperty(...) must not be null`. **Proof — measured, 2026-08-14:** fresh clone at `bcf70b63` on a new machine, `verifyPaparazziDebug` → exactly that configuration failure; after restoring `keystore.properties` + `keystore.jks`, the same invocation is green (631/631 tasks). Every new contributor and every fresh machine hits this and has to reverse-engineer it. **Unblock condition:** make the keystore lookup lazy — resolve the properties inside a `provider {}` (or guard on `localProperties.isFile` and skip/stub the signing config with a clear warning naming `keystore.properties`), so resolution fails only when a task that actually signs runs. **Out of the nav3 stage 1.1 scope fence** — `build-logic` is fenced; filed here instead of fixed. **Deadline: none pinned** — first ripe `build-logic` PR; until then it taxes every fresh checkout. |

---

## Flaky UI test — ApplicationBottomBarTest.navigateToExercisesAndBack

| Severity | Location | Description |
|---|---|---|
| 🟡 | [app/app/.../ApplicationBottomBarTest.kt](../app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/ApplicationBottomBarTest.kt) | **`navigateToExercisesAndBack` is intermittently flaky: 1/3 fail on the room3 branch, 0/2 on the Room-2 baseline; it failed once under heavy emulator load (full suites took 12–36 min) and passed 4 consecutive times since.** Sample too small to conclude pre-existing vs environmental — do NOT read this as "proven pre-existing". The mechanism is a race by construction: `checkAppClosed()` calls `assertDoesNotExist(AppRoot)` immediately after `Espresso.pressBack()`, with no wait for the activity-finish/recompose to settle. It loads a PagingSource from Room on the way to Exercises, so it is not Room-free, but the failure signature (AppRoot still present right after back) is a teardown-timing race, not a data error. **Do NOT add retries or arbitrary waits as a "fix"** — if hardened, gate on an idling resource / `waitUntil` for AppRoot's absence, not `Thread.sleep`. **Trigger to act:** it fails again on a non-loaded machine, or a UI-test-stability pass is scheduled. **Coupled to the bottom-bar selection entry above:** while that defect stands, this race is masked — the test already fails deterministically before teardown matters. The a11y fix unmasks it; see the masking-dependency note in that entry (option (a) there resolves this entry in the same PR). |

---

## Robolectric is not a valid oracle for transaction / async-child rollback semantics

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/data/database/.../testfixtures/RepositoryTestEnv.kt](../core/data/database/src/testFixtures/kotlin/io/github/stslex/workeeper/core/data/database/testfixtures/RepositoryTestEnv.kt) ↔ [AtomicRollbackDeviceTest.kt](../core/data/database/src/androidTest/kotlin/io/github/stslex/workeeper/core/data/database/AtomicRollbackDeviceTest.kt) | **Robolectric's shadow in-memory SQLite gives FALSE NEGATIVES on transaction rollback when writes happen in `async {}` / `coroutineScope` children.** Established empirically during the Room 2→3 investigation: a Robolectric probe reported that concurrent-`async`-child writes inside `withTransaction {}` did NOT roll back on a throw — **three times** (rounds 6, 8, and a sequential variant). The **real device** (`AtomicRollbackDeviceTest`, file-backed DB) proves all four shapes (control / known-negative / shape-A `asyncScope` / shape-B concurrent `async`) roll back correctly under Room 2. So the Robolectric result was an artifact of its single-connection shadow SQLite, not a production bug. **Rule:** any test asserting transaction atomicity, rollback, or async-child-in-transaction behaviour MUST be androidTest + file-backed, never Robolectric + in-memory. `RepositoryTestEnv`'s own KDoc already hedges ("single-connection in-memory SQLite that Robolectric provides"). **Flagged, not fixed here:** [SessionRepositoryImplFinishAtomicDbTest.kt:221](../core/data/exercise/src/test/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryImplFinishAtomicDbTest.kt) (`rolls back … when an inner write throws`) passes on Robolectric today, but its oracle is now known-weak for exactly this assertion class — it happens to pass because `finishSessionAtomic`'s `asyncScope` writers are sequential (shape A), the shape Robolectric handles. Do NOT move or rewrite it in this phase; a future pass should relocate it (or an equivalent) to androidTest. **Trigger to act:** any new atomicity/rollback assertion is proposed on Robolectric, or the migration off Robolectric for DB tests is scheduled. |

---

## Palette slot names describe v2 tiers, not v3 roles

| Severity | Location | Description |
|---|---|---|
| 🟢 | [core/ui/kit/.../theme/AppColors.kt](../core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/theme/AppColors.kt) | **Renaming debt, deliberately incurred.** v3 step 3 kept the v2 slot names (`surfaceTier0..4`, `accentTintedBackground`) and mapped them onto v3 tokens in KDoc rather than renaming, because renaming is a thousand-line mechanical diff with no pixel behind it. The cost surfaced in step 4: `surfaceTier4` and `accentTintedBackground` both carry the v3 `raise` hex in its *utility* role — progress track, selected tag, hover — while the name `raise` reads as "elevated surface", which is a different thing entirely and is **not** what either slot does. A reader who goes looking for "the raised surface" finds two slots that are not it. Compounding it, `object Icon` (5 properties) and `object Button` (4) have **zero readers** repo-wide and shadow the live flat `icon*`/`height*` scale — `Icon.small = 16.dp` sits next to `iconSm = 18.dp` with 0 and 29 readers respectively, kept alive only by `@Suppress("unused")` on the object. **Trigger to act:** the next time a palette or dimension change touches these files for its own reasons — rename to role names (`base`/`sec`/`slab`/`field`/`raise`) and delete the two dead scales in the same pass. Do not do it as a standalone PR; the diff is large and the review value is near zero on its own. |

---

## Reactive Aggregations

| Severity | Location | Description |
|---|---|---|
| ✅ RESOLVED | [feature/exercise-chart](../feature/exercise-chart/) | **Heavy-aggregation re-execution policy** (parked from v2.1). The v2.2 chart consumer chooses one-shot reads over a `Flow` subscription: the screen reads `getHistoryByExercise` once on entry / preset change / picker change and buckets in Kotlin. No persistent subscription means no spurious recomputation when other sessions log sets. The "if a cache is needed, cache at the consumer side" guidance was effectively answered by binding the data to `State` instead. See [feature-specs/v2.2-exercise-charts.md → Architectural notes](feature-specs/v2.2-exercise-charts.md#architectural-notes). |
| 🟡 | [feature/exercise-chart/.../mvi/mapper/ExerciseChartUiMapper.kt](../feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapper.kt) | **Per-day max-of-day collapse loses information** when the user does two sessions on one calendar date — only the higher set's session is reachable from the tooltip. v2.2 ships max-of-day for simplicity; follow-up is to render two points per day (each session's best set, both anchored to the day's X with a small jitter / vertical marker). **Trigger to act:** user reports that double-session days are surprising. |
| 🟢 | [feature/exercise-chart/.../mvi/handler/CommonHandler.kt](../feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandler.kt) | **Window filtering happens client-side**, not in SQL. The mapper drops sets older than the active preset's start. Acceptable at v2.2 data sizes (~hundreds of rows per exercise); if profiling shows the read is slow for >2 years of dense history (>5000 rows per exercise), add a `:sinceMillis` overload to `SessionDao.getHistoryByExercise` and pass it from the handler. **Trigger to act:** load time exceeds ~150ms on a mid-range device. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecord](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Two parallel implementations of the same comparator (Kotlin object-level and SQL `ORDER BY`). The Kotlin path is needed at session finish where the comparison happens against an immutable in-memory snapshot. If the comparator definition changes (e.g. tiebreak rule), both must be updated together. Acceptable duplication; covered by `PrComparatorTest`. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecordsBatch](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Spec called for a parity test that seeds Room and asserts both `bestOf(...)` and the DAO pick the same set. Not implemented because Room test setup in `core/exercise/test` is cross-module; the test would need to live alongside `androidTest` infrastructure. **Trigger to act:** comparator semantics change (e.g. tiebreak rule). |
| 🟢 | [feature/live-workout/.../domain/LiveWorkoutInteractorImpl.kt:70-86](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt) | Sequential (not parallel) per-entity queries — `loadSession` does N per-exercise calls (`getAdhocPlan` / `getPlan` / `setRepository.getByPerformedExercise`) in a loop. One-shot at session open, low frequency. Cheapest fix: wrap with `asyncMap` from [`core/core/coroutine/CoroutineExt.kt`](../core/core/src/main/kotlin/io/github/stslex/workeeper/core/core/coroutine/CoroutineExt.kt). Not blocking. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt:130-143](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | Sequential per-entity queries — `getSessionDetail` does N `setDao.getByPerformedExercise` calls inside `withTransaction`. One-shot at Past session open, low frequency. Same fix shape (`asyncMap`). Not blocking. |

---

## Dialog State Discipline — follow-ups

Items deferred from the dialog-state-discipline PR (see [compose-state-discipline.md → Rule 4](compose-state-discipline.md), [architecture.md → State / Action / Event conventions](architecture.md), and the [`mvi-dialog-state`](../.claude/skills/mvi-dialog-state.md) skill).

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/live-workout/.../mvi/store/LiveWorkoutStore.kt](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt), [feature/exercise/.../mvi/store/ExerciseStore.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStore.kt), [feature/single-training/.../mvi/store/SingleTrainingStore.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStore.kt) | **`Event.ShowError(message: String)` payload shape inconsistency.** The architecture doc prescribes `Event.ShowError(type: ErrorType)` with the localized resource resolved in the graph; live-workout, exercise, and single-training instead carry a pre-resolved `String message`. Pre-existing minor inconsistency — out of scope for the dialog/sheet rule migration. **Trigger to act:** next pass that touches `Event.ShowError` in any of these features. |
| 🟡 | (cross-cutting — every feature with a `dialogState`) | **`dialogState` is not round-tripped through `SavedStateHandle`.** Configuration changes survive (same VM-scoped store). Process death does not — a dialog open at the moment Android reclaims the process disappears on resume. The Rule 4 known-limitation note acknowledges this; round-tripping critical dialogs needs a per-feature decision (which dialog payloads are worth `Bundle`-encoding) and a `BaseStore` extension or per-store `SavedStateHandle` wiring. **Trigger to act:** user-visible report of "I had a confirm dialog open, the app got killed, and the action got abandoned." |
| 🟡 | [feature/past-session/.../mvi/store/PastSessionStore.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/store/PastSessionStore.kt) `deleteDialogVisible: Boolean` | **Single dialog still expressed as a `Boolean` flag.** `PastSessionStore.State.deleteDialogVisible: Boolean` plus `ClickHandler.updateState { it.copy(deleteDialogVisible = ...) }` and `if (state.deleteDialogVisible) { ... }` in `PastSessionScreen.kt` predate the dialog-state-discipline rule. A single nullable / Boolean is still acceptable for one-dialog screens per the [`mvi-dialog-state`](../.claude/skills/mvi-dialog-state.md) skill, so this is not a hard violation today. **Trigger to act:** the moment a second dialog (e.g. a discard-changes confirm or set-delete confirm) gets added to PastSession — migrate to sealed `DialogState` *in that PR*, not as a separate cleanup. |
| 🟡 | [feature/settings/.../ui/components/SignOutConfirmationDialog.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/components/SignOutConfirmationDialog.kt), [feature/settings/.../ui/components/RestoreConfirmationDialog.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/components/RestoreConfirmationDialog.kt) | **Existing feature-local confirmation dialogs not yet migrated to `AppConfirmationDialog` generic.** `SignOutConfirmationDialog` and `RestoreConfirmationDialog` in `feature/settings` hand-roll their chrome predating the generic `AppConfirmationDialog` in `core/ui/kit` introduced alongside the App Dialogs work (see [feature-specs/app-dialogs.md](feature-specs/app-dialogs.md)). Both still drive their visibility through the feature-local `SettingsStore.State.dialogState: DialogState` sealed type — they are **not** candidates for the cross-feature `AppDialog` catalog, only for the chrome migration. **Fix path:** replace each Composable's body with an `AppConfirmationDialog(title, body, confirmLabel, dismissLabel, isDestructive, onConfirm, onDismiss)` invocation; keep the feature-local sealed `DialogState` variant; pass the existing string resources through unchanged. **Trigger to act:** next polish pass on Settings UI, or any chrome regression that would otherwise be fixed twice (once in each hand-rolled dialog). |

---

## Phase B (App Dialogs / Recovery) — follow-ups

Items deferred from the Phase B app-dialogs re-architecture (see
[feature-specs/app-dialogs.md](feature-specs/app-dialogs.md) and the
Phase B commit chain `56c4f1b1..5d858445`). The layered MVI rewrite,
`AppDialogObserver` transient-signal contract, uniform dismiss-after
ordering, and the `UndoRestoreOutcome` gate against case-b silent
failure all landed; the items below are explicitly out-of-scope for
that phase and tracked here for the next phase.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/recovery/.../RestoreDialogChoiceObserver.kt `handleUndoConfirmation`](../feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/RestoreDialogChoiceObserver.kt) ↔ [feature/app-dialogs/api/.../model/AppDialog.kt](../feature/app-dialogs/api/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/api/model/AppDialog.kt) | **No in-flow `UndoRestoreFailure` dialog variant for case-b IO errors.** When `coordinator.performUndoRestore()` returns `UndoRestoreOutcome.IoFailure` the observer intentionally does NOT call `acknowledgeReaction(dialog)` — the existing `UndoRestoreConfirmation` dialog stays visible so the user sees the reaction did not complete and can re-tap (a transient IO error often succeeds on retry). The current UX surface is "the dialog you just confirmed is still here" — implicit and minimal but not explanatory. A dedicated `AppDialog.UndoRestoreFailure(reason)` variant would surface "Undo failed: <reason>, retry?" with explicit Retry / Cancel buttons and let the user choose explicitly. **Fix path:** add the variant to the api catalog, render branch in `AppDialogHostContent`, persisted `pending_undo_restore_failure_*` keys in `AppDialogKeys`, priority slot in `AppDialogResolver`, and switch the IoFailure branch in `RestoreDialogChoiceObserver` from "keep current dialog" to "publish UndoRestoreFailure + acknowledge UndoRestoreConfirmation". The `feature/recovery` extraction blocker has cleared — the new variant's consumer-side reaction slots into `RestoreDialogChoiceObserver` alongside the other recovery consumers. **Trigger to act:** ready for the next scheduled recovery / app-dialogs PR, OR earlier if telemetry shows the IoFailure path firing meaningfully often (suggests transient errors are common in the wild). |
| 🟢 | [lint-rules/.../MviActionNamingRule.kt:32-41](../lint-rules/src/main/kotlin/io/github/stslex/workeeper/lint_rules/MviActionNamingRule.kt) | **`MviActionNamingRule` is over-broad on leaf data classes inside a sealed `Action` parent.** The rule fires `Action class 'X' should be sealed class or interface` on ANY class whose name ends in `"Action"` — it cannot distinguish the outer sealed parent (correctly enforced) from a leaf `data class` nested inside one (also correct MVI shape). Surfaced during Phase B C4 when the spec's `Action.UserAction(dialog, action)` collided with the rule; worked around by renaming to `Action.Choose`. The codebase convention is descriptive-noun leaves (`Action.Click`, `Action.Init`, `Action.Backup.SignIn`), so the workaround matches existing style — but the rule still has the wrong shape for any future case where `"*Action"` is the natural leaf name. **Fix path:** add `parentSealedInterfaceName == "Action"` short-circuit before the sealed-class report — a nested `data class` whose enclosing class is itself a sealed `Action` parent is by definition fine. **Trigger to act:** another collision lands (a future feature wants `Action.UserAction`-style leaf naming and the spec rejects the `Choose`-style rename), or during the next lint-rules audit pass. |

---

## State Mutation Discipline

**Rule:** `BaseStore.updateState` and `updateStateImmediate` lambdas should perform pure state transformation only — given `current`, return a copy. Mapping, formatting, and any work involving `ResourceWrapper` or domain-to-UI conversions runs *before* the lambda body. See [architecture.md → State mutation discipline](architecture.md) and the [`compose-state-discipline`](../.claude/skills/compose-state-discipline.md) skill.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/home/.../mvi/handler/CommonHandler.kt:36-39](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/CommonHandler.kt) | Mapping inside `updateStateImmediate` lambda — `row?.toUi(now, resourceWrapper)` runs on Main.immediate every active-session emit. Hoist out before the lambda. |
| 🟡 | [feature/all-exercises/.../mvi/handler/PagingHandler.kt:49-52](../feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/PagingHandler.kt) | Mapping `tags.map { it.toTagUi() }.toImmutableList()` inside `updateStateImmediate` lambda. Same fix shape as above. |
| 🟡 | [feature/all-trainings/.../mvi/handler/PagingHandler.kt:51-54](../feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/PagingHandler.kt) | Same pattern as the home / all-exercises rows. |
| 🟡 | [feature/single-training/.../mvi/handler/CommonHandler.kt:55-56](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandler.kt) | Same pattern as the home / all-exercises rows. |

---

## Live workout — release-phase hot-fix follow-ups

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/live-workout/.../domain/LiveWorkoutInteractorImpl.kt loadSession](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt) | Read-time `trainingPlan ?: exerciseRepository.getAdhocPlan(...)` fallback exists because we don't backfill old data via migration in this commit. When the next schema bump lands (with the proper Migration framework now in place — see Migration Policy in [architecture.md](architecture.md) → Room database), include a one-shot backfill: `UPDATE training_exercise_table SET plan_sets = (SELECT plan_sets FROM exercise_table WHERE exercise_table.uuid = training_exercise_table.exercise_uuid) WHERE plan_sets IS NULL`. After that, drop the runtime fallback. |
| ✅ RESOLVED | [feature/live-workout/.../mvi/mapper/ExerciseDoneRule.kt](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/ExerciseDoneRule.kt) | Status derivation logic was duplicated between `LiveWorkoutMapper.toUiList` (initial load) and `StateStatusMapper.recomputeOnly` (post-mutation recompute), with the load path still using the legacy `plan.isEmpty() → performed.any { it.isDone }` shortcut. Both callers now route through `ExerciseDoneRule.isDoneLoad` / `isDoneLive`. The two entry points share an `expectedPositions` union; the live variant additionally folds `visibleSets.indices` so typed-but-unchecked drafts keep the row CURRENT. See [feature-specs/live-workout.md → Load vs live status](feature-specs/live-workout.md#load-vs-live-status--exercisedonerule). |
| ✅ RESOLVED | [feature/live-workout/.../mvi/](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/) | **Live-workout draft seed and visible-row merge centralized** (lock-in for the LiveSetRow reset class of bugs). Visible-row resolution (`performed > draft > plan > fallback`) is computed once in the MVI mapper and exposed as `LiveExerciseUiModel.visibleSets`; `LiveExerciseCard` no longer accepts `setDrafts` and no longer imports `Store.State.DraftKey`. Draft seed/update goes through a single helper (`mvi/handler/LiveWorkoutDraftExt.kt`) so type / weight / reps edits all preserve the unrelated fields from the current visible row. Behavior tests covering every field-preservation pair and the resolver priority live in `LiveSetDraftBehaviorTest.kt` and `LiveSetVisibleRowsResolverTest.kt`. See [architecture.md → Source-of-truth merging belongs to mappers](architecture.md) and [feature-specs/live-workout.md → Set draft and visible row architecture](feature-specs/live-workout.md). |
| 🟢 | [feature/live-workout/.../mvi/store/LiveWorkoutStore.kt activeExerciseUuids](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt) | Active-set state is ephemeral — resets on app background/restore. If users complain about losing parallel state, persist via a new column on `performed_exercise_table` or session-scoped DataStore. Not blocking. |

---

## v2.4 Design foundation — follow-ups

Items deferred from the v2.4 PR (see `documentation/feature-specs/v2.4-design-foundation.md` Sections 6 / 7). The kit primitives, theme tokens, plan editor screen, list-screen reworks, chart footer fix, and DAO queries (F1, F2) all landed; remaining surface is tracked here for a follow-up PR.

| Severity | Location | Description |
|---|---|---|
| 🟡 | feature/live-workout/.../mvi/store/, ui/components/ | **Live workout drag-to-reorder + snackbar undo** (spec 5.4 partial). The follow-up commits closed the PlanEditor route migration, AppCheckmarkButton, AppTooltip on chip, and three-dots menu offset fix. Drag-to-reorder for exercises and inner sets via the new `ReorderableLazyListState` / `ReorderableColumnState`, plus snackbar-undo for set delete (D5 replace policy), are still deferred. The new kit primitives exist and compile; the wiring is the work. |

| 🟡 | feature/past-session/.../ui/components/PastSetEditRow.kt | **PastSession set-delete with snackbar undo** (spec 5.7 partial). Drag-to-reorder, total-kg removal, the PR badge explainer dialog, stable column widths (PR slot reserves 56dp), removed leading accent stripe, and the explicit drag-handle icon all landed; structural set-delete with snackbar+Undo policy (D5) remains paired with the live-workout work. |
| 🟢 | feature/exercise-chart/.../ui/components/ChartTooltipPopup.kt | **Chart tooltip rewrite from subcompose to coordinate-based draw** (spec 5.6). Footer overflow fix (the user-visible Russian regression) landed; the structural tooltip rewrite is a separate larger refactor. Existing `SubcomposeLayout` tooltip still functions. |
| 🟢 | feature/home/.../ui/components/TrainingPickerSheet.kt | **Templates picker → full-screen route** (spec 5.8 / E8). Not yet landed. Requires a new `Screen.TemplatesPicker` route, a TemplatesPickerScreen with `LargeTopAppBar` + search, and migrating the home tap from `consume(...sheet open)` to `Action.Navigation.OpenTemplatesPicker`. |
| 🟢 | (multiple touched files) | **`AppDimension.Padding` migration sweep** (spec B1). Padding is `@Deprecated` and emits warnings on call sites. Step 13 of v2.4 was opportunistic — touched files migrated as work landed. Remaining call sites continue to compile with deprecation warnings. **Trigger to act:** v2.7 tech-debt ratchet, or earlier if drift detected. |
| 🟢 | core/ui/plan-editor/.../mvi/store/PlanEditorStoreImpl.kt | **Snackbar undo for set-delete** (spec D5). The new PlanEditorScreen currently uses the existing immediate-delete behavior. Replacing with snackbar-undo is grouped with the live-workout snackbar-undo work above so both editor surfaces share the policy. |
| 🟢 | core/ui/plan-editor/.../PlanEditorBody.kt | **Plan editor drag-to-reorder** (spec 5.4 partial). The kit primitives (`reorderableColumnItem` + `reorderableColumnDragHandle` with live displacement preview) exist and ship in `core/ui/kit`; PlanEditorBody still renders a non-reorderable `forEachIndexed` loop. Migration mirrors PastExerciseCard's wiring — pass `dragHandleModifier` through PlanEditorRow with the trailing DragHandle icon. |
| 🟢 | feature/exercise/.../ExerciseEditScreen.kt | **ExerciseEditScreen rework** (v2.4.x deferred — separate spec next round). Inline plan section landed in v1.41 release-blocker fix (renders `PlanEditorBody(scrollable = false)` for `Mode.Edit(isCreate = true)`). Image+name unification and full layout overhaul are still pending. |
| 🟡 | feature/exercise/.../ui/mvi/handler/ClickHandler.kt processAdhocPlanEditorAction | **Exercise create-flow plan persistence — process-death loss.** The inline plan editor used during exercise create-mode mutates `state.adhocPlan` in memory; persistence happens only on Save via `ExerciseChangeDomain.lastAdhocSets`. A process kill mid-edit loses the in-flight draft. Identical semantics to the pre-`ad117f3a` `AppPlanEditor` bottom sheet — not a regression, but a known limitation. **Fix path:** introduce a draft row in `exercise_table` (or a sibling `exercise_draft_table`) keyed by a stable client-generated UUID, restored on screen entry, deleted on Cancel/Save. Requires schema migration, DAO filter audit (every `is_adhoc = 0` query must also filter drafts), `UNIQUE(name)` workaround, and an orphan-cleanup worker. **Trigger to act:** user-reported draft loss after a process death, or when DB-draft work is otherwise prioritized. |
| 🟢 | feature/exercise/.../ExerciseDetailScreen.kt | **TopBar collapsing animation feel on ExerciseDetail** — pending user clarification on whether it is a bug or perceived discomfort. Track here so the question is not lost. |

---

## v2.3 Quick start workout — follow-ups

Items deferred from the v2.3 PR (per spec Section 10). Track here so the v2.7 ratchet pass can pick them up.

| Severity | Location | Description |
|---|---|---|
| 🟢 | feature/exercise/.../ExerciseInteractorImpl, feature/live-workout/.../LiveWorkoutInteractorImpl | **Track Now / Quick start UI unification** (deferred to v2.7). Both flows now share the data layer (`SessionRepository.createAdhocSession`, `discardAdhocSession`) but stay as separate UI flows. UI-layer convergence is its own refactor. |
| 🟢 | feature/live-workout/.../mvi/handler/ | **Live workout feature module decomposition** (deferred to v2.7). `feature/live-workout` accumulated significant complexity through v2.1 (PR detection), v2.2 (chart hook), v2.3 (mid-session add, name edit, empty-finish dialog). `ExercisePickerHandler` was already split off via the `PlanEditAction`-style wrapper to keep ClickHandler from bloating; further decomposition (e.g. NameEditHandler, EmptyFinishHandler) is candidate. |
| 🟡 | feature/live-workout/.../mvi/handler/ExercisePickerHandler.kt `addExerciseFlow` | **PR snapshot fetch failure mode telemetry** (new in v2.3). When `fetchPrSnapshotForExercise` fails for a library pick, the exercise is still added to the session and the in-moment PR badge is suppressed (degraded mode silent failure). If telemetry shows this firing often, the user-facing UX needs revisit. |
| 🟡 | feature/live-workout/src/androidTest/ | **Mid-session add UI in instrumented tests** (deferred to v2.7). Per project policy (UI flow tests as dedicated test-coverage PRs), no androidTest landed in v2.3. The blank-init Quick start flow + picker bottom sheet + empty-finish discard cascade need smoke coverage. |
| 🟢 | core/database/.../exercise/ExerciseDao.kt + ExerciseRepositoryImpl.createInlineAdhocExercise | **`ExerciseEntity.isAdhoc` cleanup of stale graduated rows** (deferred, monitoring). After many cycles of inline create → graduate, the library may accumulate poorly-named single-use exercises. No action in v2.3; revisit if user-facing exercise-list pruning becomes a need. |

---

## Remaining from PR #78

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/exercise/.../personal_record/PersonalRecordRepository.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/personal_record/PersonalRecordRepository.kt) | `observePersonalRecords(uuidsByType)` is a combine-of-N flow — N separate Room subscriptions. KDoc marks it as one-shot only, but there is no compile-time guard. Callers must use `firstOrNull()` or `getPersonalRecord`. Long-lived subscribers must use `observePersonalRecordsBatch` / `observePrSetUuids`. Consider removing from the public interface or converting to `suspend fun` to make the one-shot contract enforced. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt `finishSessionAtomic`](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | Double dispatcher switch: outer `withContext(ioDispatcher)` wraps `transition {}` which already does `withContext(ioDispatcher)`. Redundant context switch; clean up when touching this method next. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt `groupBySession()`](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | `sortedByDescending { it.finishedAt }` is a redundant O(N log N) pass — the DAO query already returns `ORDER BY sn.finished_at DESC` and `groupBy` preserves insertion order. Remove the sort. |

---

## Spec-vs-Reality Drift

Items where shipped behaviour diverges from what specs originally asked for. Surfaced by the 2026-04-28 audit.

| Severity | Spec | Item | Reality |
|---|---|---|---|
| 🟡 | exercises.md | "Phantom shims removed" | `TrainingDataModel.labels` and `TrainingDataModel.exerciseUuids` still present and populated by repo. Cleanup. |
| 🟡 | exercises.md | "`pagedActiveByTags(Set<String>)` AND semantics" | Shipped uses `IN (:tagUuids)` (OR semantics). The deprecated AND-semantics query was removed as dead code; OR is intentional and remains the supported behaviour — locked decision in v2.0 spec. |
| ✅ RESOLVED | exercises.md | "Canonical NavigationHandler with `@Inject Navigator`" | Resolved in the navigation-lifecycle PR (PR #143). All feature `NavigationHandler` classes are now `@ViewModelScoped @Inject Navigator` constructor-injected; the old `Component.create(navigator, screen)` factory pattern is gone. Route arguments enter the Store via Dagger assisted injection (`@Assisted screen: Screen.<X>`) instead of through a `Component<Screen>` subclass. The `MviHandlerConstructorRule` literal-name exemption for `NavigationHandler` is now redundant — it remains in the rule source for back-compat but new code does not rely on it. See [architecture.md → Navigation](architecture.md#navigation) for the canonical pattern. |
| 🟡 | exercises.md, trainings.md, live-workout.md | "Haptics emitted for every Click action" | Several dismiss / undo / cancel paths bypass haptic emission. Specifically: `processUndoArchive`, `processCancelPermanentDelete`, `processBulkDeleteDismiss` in all-exercises; `processBulkDeleteDismiss` in all-trainings; dismiss handlers and done-card header expansion in live-workout. |
| 🟡 | trainings.md, live-workout.md | "Composable `@Previews` for every public/internal Composable" | `AllTrainingsScreen`, `TrainingDetailScreen`, `TrainingEditScreen` expose internals without `@Preview`. `TrainingRow` lacks active/inactive permutations. `live-workout` is fully covered (verified). |

---

## androidTest Coverage Gap

Five stub files with `TODO(feature-rewrite-tests)` markers carry an `@Ignore`d placeholder method — skipped via `@Ignore` placeholder, real coverage tracked in #93. (`SettingsScreenTest.kt`, listed below, was filled with real tests in a prior PR; row kept for traceability.) Created during initial Stage rewrites (5.1 / 5.2 / 5.3) under the assumption tests would be filled once the smoke harness stabilised. v2.0 stage scheduled the fill-in work; remaining stubs are tracked in the v2.0 spec and addressed in their own PRs.

| Severity | Location | Stage |
|---|---|---|
| 🟡 | [feature/settings/.../SettingsScreenTest.kt](../feature/settings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/settings/SettingsScreenTest.kt) | 5.1 |
| 🟡 | [feature/archive/.../ArchiveScreenTest.kt](../feature/archive/src/androidTest/kotlin/io/github/stslex/workeeper/feature/archive/ArchiveScreenTest.kt) | 5.1 |
| 🟡 | [feature/all-exercises/.../AllExercisesScreenTest.kt](../feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt) | 5.2 |
| 🟡 | [feature/exercise/.../ExerciseScreenTest.kt](../feature/exercise/src/androidTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt) | 5.2 |
| 🟡 | [feature/all-trainings/.../AllTrainingsScreenTest.kt](../feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt) | 5.3 |
| 🟡 | [feature/single-training/.../SingleTrainingScreenTest.kt](../feature/single-training/src/androidTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt) | 5.3 |

**Plan:** address as a dedicated test-coverage PR after v2 stabilises. Don't try to fill in feature PRs.

### `BaseApplication.onCreate` bootstrap chain — zero androidTest coverage (App-Scope Collapse Step 6, Phase 3.4)

The consolidated `:app:app` androidTest harness boots
[`TestApplication`](../app/app/src/androidTest/kotlin/io/github/stslex/workeeper/harness/TestApplication.kt),
a `BaseApplication` subclass that overrides `onCreateGraphBootstrap()` to a **no-op**. That means the
production `onCreate` bootstrap chain — the recovery pre-flight (`handlePostRestoreLaunch` /
`checkAndRouteOrProceed`, run under `runBlocking` before `MainActivity`), the orphaned-image-temp-file
cleanup, and the app-dialog observer subscribe-before-`MainActivity` (`bootstrapAppDialogObserver`) — has
**zero instrumented coverage**. `TestApplication` must skip it: `Application.onCreate` fires at process
start, before any test's `@Before` installs a graph via `MetroTestRule`, so running the graph-touching
bootstrap there would force graph construction with the wrong roots.

- **This is NOT a regression.** Pre-cut, every `@HiltAndroidTest` booted `dagger.hilt.android.testing.HiltTestApplication`,
  which is **not** a `BaseApplication` and never ran this bootstrap chain either — the chain has *never*
  had androidTest coverage. Phase 3.4 makes the gap *visible* (a named override), it does not create it.
- **Covered only by the on-device restore cycle** — the manual/device restore-gate baseline
  (`metro-batch-anchor` reference), not by any automated instrumented test.
- **Trigger to act:** if the bootstrap chain grows behaviour worth guarding (e.g. a new pre-flight
  scenario), add a dedicated `:app:app` androidTest that constructs a `TestApplication`, installs a graph
  via `MetroTestRule`, then invokes the bootstrap explicitly — rather than relying on `onCreate`.
- **Still open after Phase 3.6.** The relocated `ExerciseCreatePersistenceTest` restored the *feature*
  create→persist seam (UI→Store→repository→Room over an in-memory DB); it does **not** touch this
  `onCreate` bootstrap chain. This gap is specifically about `onCreateGraphBootstrap()` being a no-op and
  is unchanged by that restore.

---

## Firebase-transitive dagger-core on the app runtime classpath (App-Scope Collapse Step 6, Phase 5)

After the Hilt dependency excision (Phase 5), **zero `hilt` artifacts** remain on any compile or runtime
classpath (`./gradlew :app:dev:dependencies --configuration debugRuntimeClasspath | grep -i hilt` is
empty). One `com.google.dagger:dagger:2.57.2` remains on the app runtime classpaths — this is
**dagger-core (the JSR-330 DI runtime), NOT Hilt** — pulled transitively by
`com.google.firebase:firebase-sessions:3.0.5` (Firebase uses Dagger internally).

- **ACCEPTED, not a Step-6 leftover.** It predates the Hilt→Metro migration — Firebase always shipped its
  own Dagger; removing our Hilt never had any bearing on it. `grep -i hilt` is the correct success check
  (empty everywhere); `grep -i "hilt\|dagger"` will always find this one Firebase-owned line.
- **Not ours to remove.** Firebase (analytics / crashlytics / performance) is a required production
  dependency; forcing `dagger` out via a `firebase` `exclude` risks breaking Firebase Sessions at runtime.
- **Trigger to revisit:** only if Firebase drops its internal Dagger usage (then the line disappears on its
  own), or if a future audit explicitly decides to exclude it and validates Firebase still works.

## Stale Hilt-generated Java footgun when switching to a de-Hilt'd branch (App-Scope Collapse Step 6)

Switching into this branch (or any branch where a module's androidTest was de-Hilt'd) with a **warm
`build/` dir** can fail `:core:ui:mvi:compileDebugAndroidTestJavaWithJavac` (or another module's
equivalent) on **orphaned Hilt-generated Java** — e.g. `AppFeatureScopeTest_TestComponentDataSupplier.java`
referencing `DaggerDefault_HiltComponents_SingletonC`. Cause: once a test drops `@HiltAndroidTest`, that
module's `kspDebugAndroidTestKotlin` becomes `SKIPPED`, and a **SKIPPED KSP task does not delete** the
`.java` files a prior run generated into `build/generated/ksp/debugAndroidTest/`; `--rerun-tasks` re-runs
the compile (which reads the stale files) but not the cleanup.

- **Remedy:** `./gradlew clean` (the files are gitignored build output — clean wipes them).
- **CI is unaffected** — it always builds from a clean checkout with no pre-existing `build/`.
- Applies to local incremental builds only; it is a build-hygiene artifact, not a code defect.

---

## Navigation lifecycle — RESOLVED in PR #143

The "stale `NavController` after activity recreation crashes navigation" class of
bugs that shipped before `master` is closed by the navigation-lifecycle refactor.
The architecture now strictly separates navigation **decisions** (Store/Handler
layer, depends on `Navigator`) from navigation **execution** (App/UI bridge,
operates on the composition-scoped `NavController` from
`rememberNavController()`).

What changed:

- `NavigatorEventBus` (`@Singleton`, controller-free) replaced the old controller-
  backed `NavigatorImpl` / `NavigationHolderController` / `NavigationHolderImpl`
  trio. It exposes only `Navigator` (producer) and `NavigatorReceiver` (consumer)
  interfaces over a `SharedFlow<NavCommand>`.
- `NavigatorExt.NavigationEventBusSetup` (composable) collects commands keyed on
  the current `NavController` via `LaunchedEffect(navController)` so the executor
  rebinds on every recomposition / activity recreation. The bus instance survives;
  the executor is per-composition.
- `App.kt` owns `rememberNavController()` and creates the `NavigatorHolder`
  composition-scoped via `remember(navController)`.
- `RootComponentImpl`, `LocalRootComponent`, `LocalNavigator`, and the
  `Component.create(navigator, screen)` factory pattern are all removed. Route
  arguments enter the Store via Dagger assisted injection
  (`@Assisted screen: Screen.<X>`).
- All feature `NavigationHandler`s are `@ViewModelScoped @Inject Navigator`.
- `Screen.PlanEditor.planEditorSavedAttr` flows through
  `navigator.popBack(planEditorSavedAttr.toPairValue(true))` and is consumed in
  the previous screen's graph composable via `navComponentScreenWithState` +
  `stateHandle.getStateFlow(...).collectAsState()`. Consumers reset the flag via
  `stateHandle.setAttrDefaultValue(...)` so re-entry does not retrigger.

  > **Superseded by Nav3 stage 1.2.** This bullet records what PR #143 shipped and is
  > kept as history. `SaveHandlerAttr` and the attr-based transport no longer exist:
  > the result type is declared on the destination (`ScreenWithResult<R>`), produced
  > with `navigator.popBackWithResult(...)`, and consumed via `NavResults.OnResult`,
  > which clears as part of delivering. See
  > [architecture.md → Navigation results](architecture.md#navigation-results).

Verification requirements (live in test code, not docs):

- `NavigatorEventBusTest` covers `navTo` / `replaceTo` / `popBack` emission shape
  and order on the singleton bus.
- `NavigationLifecycleRegressionTest` covers a stale-bridge → fresh-bridge handover.
  It verifies that the bus remains usable across detach / re-attach: commands
  emitted with no executor attached do not crash or block the bus, and commands
  emitted **after** a fresh executor subscribes are observed by that executor.
  The bus uses `MutableSharedFlow(replay = 0, extraBufferCapacity = 64)` and
  intentionally does not guarantee replay of commands emitted before subscription
  — the production bridge attaches via `LaunchedEffect(navController)` before any
  decision-side emit can happen for that composition, so pre-subscription emits
  are not part of the lifecycle contract.
- Per-feature `NavigationHandlerTest` classes verify each `Action.Navigation.<X>`
  branch dispatches the matching `navigator.*` call, with `Navigator` mocked.
- Per-feature route-arg Store tests (`feature/exercise`, `feature/live-workout`,
  `feature/single-training`) verify the `@Assisted screen` value lands in
  `state.value` initial fields.
- `app/dev/.../NavigationLifecycleRegressionTest.kt` (instrumented `@Regression`)
  recreates `MainActivity` mid-flight and asserts that subsequent bottom-bar
  navigation calls land on the correct destination through the freshly-bound
  bridge.

### Test gaps deferred to a follow-up (instrumentation)

The following scenarios are part of the manual QA checklist below but are NOT
yet automated because the `app/dev` instrumentation harness only navigates
within bottom-bar destinations — it has no helpers for seeding DB rows
(Exercise / Training / PerformedExercise) and no shared fixtures for
detail-screen → PlanEditor flows. Adding them would require new test
infrastructure comparable in size to the rest of this PR. **Trigger to act:**
next PR that adds a real-DB instrumentation fixture (similar to the
`RepositoryTestEnv` approach for unit tests).

| Scenario | Status |
|---|---|
| Exercise detail → PlanEditor save → previous screen reload exactly once | manual |
| SingleTraining → PlanEditor save → previous screen reload exactly once | manual |
| LiveWorkout → PlanEditor save → previous screen reload exactly once | manual |
| LiveWorkout finish session → `replaceTo` lands on PastSession; back does not return to finished LiveWorkout | manual |

Documented at [architecture.md → Navigation](architecture.md#navigation),
[lint-rules.md → MetroScopeRule scope expectations](lint-rules.md#scope-expectations-for-the-navigation-layer),
and the lifecycle-safe navigation refactor section in
[`refactor-with-mvi-rules`](../.claude/skills/refactor-with-mvi-rules.md).

---

## Domain model boundary — RESOLVED

Migrated in the domain-model-migration PR. Every feature now declares
its own `*Domain` types under `feature/<X>/domain/model/`; data → domain
mapping lives in `feature/<X>/domain/mapper/`; domain → ui mapping
lives in `feature/<X>/mvi/mapper/`. Sealed result types are extracted
to standalone files. The `DomainLayerPurityRule` and
`DomainLayerNoUiRule` Detekt rules guard the boundary at error
severity. See [architecture.md → Domain model
layer](architecture.md#domain-model-layer) for the convention.

---

## Backup integrations

Full feature documentation: [feature-specs/backup.md](feature-specs/backup.md).
Entries below are the active follow-ups; the spec links back here from its
**Out of scope** and **Status** sections.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/settings/.../domain/BackupInteractorImpl.kt restoreLatest](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt) | **v1 restore is latest-only.** No picker UI; `restoreLatest()` always picks the first entry from `BackupStorage.listBackups()` (newest). `Action.Backup.RequestRestore` surfaces a single `RestoreConfirmationUi` for the latest. See [backup.md → Out of scope](feature-specs/backup.md#out-of-scope-and-decisions). **Trigger to act:** v1.1 spec or first user request to roll back to an older backup. **Fix path:** add a picker bottom sheet driven from `RequestRestore`, list all summaries via a new domain query, and route selection back as `Action.Backup.ConfirmRestoreFor(remoteId)` (split out from `ConfirmRestore`). |
| 🟡 | [feature/settings/.../domain/BackupInteractorImpl.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt) ↔ [core/data/backup/google-drive](../core/data/backup/google-drive/) | **No client-side encryption at rest.** Backup payload is unencrypted in Drive `appdata`; Drive's at-rest encryption + HTTPS in transit only. See [backup.md → Out of scope](feature-specs/backup.md#out-of-scope-and-decisions). **Fix path:** Tink envelope encryption with a master key in Android Keystore, applied to the snapshot file before upload and reversed on download. New manifest field (`encryption_version`) to gate restore. **Trigger to act:** when sensitive-data classification changes (e.g. user-reported personal notes attached to exercises) or when a self-hosted backend lands (where the at-rest property is no longer Google's). |
| 🟢 | [core/data/backup/worker/.../notification/BackupNotificationHelper.kt](../core/data/backup/worker/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/worker/notification/BackupNotificationHelper.kt) | **Auth-paused notification has no Settings deep link.** Tap on the persistent "Auto-backup paused" notification launches the main activity (`getLaunchIntentForPackage`) without deep-linking to Settings. The user must navigate manually. **Fix path:** pass an extra (e.g. `Intent.putExtra("open_settings", true)`) and consume it in `MainActivity` to dispatch `navigator.navTo(Screen.Settings)` on first frame. Requires reading the intent in the host module without retaining `Activity`/`Context` in the store. **Trigger to act:** user reports finding the notification but missing the settings banner. |
| 🟢 | [feature/settings/.../mvi/mapper/BackupPreferencesUiMapper.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/mvi/mapper/BackupPreferencesUiMapper.kt) | **"Next backup" line empty on WorkManager < 2.8 or before first schedule lands.** `nextBackupText` is derived from `WorkInfo.nextScheduleTimeMillis` which only surfaces a value after the periodic work has been enqueued and WorkManager has computed the next slot. Before that, the line is omitted. A more user-friendly fallback computes the expected next slot from `lastSuccessAtEpochMs + interval`. **Trigger to act:** v1.1 polish or user feedback that the line "sometimes appears, sometimes doesn't". |
| 🟢 | [feature/settings/.../mvi/handler/BackupClickHandler.kt `bootstrapOrRehydrate`](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/BackupClickHandler.kt) | **`autoBackupBootstrapped` flag set before `schedulePeriodic` completes.** If `schedulePeriodic` or `enqueueOneTime` fails after the flag is committed, subsequent app launches will skip the first-sign-in bootstrap and the user will never see the "Auto-backup enabled, daily" snackbar. The trade-off is intentional — retrying the bootstrap indefinitely would re-fire the snackbar on every re-sign-in, which is worse UX. **Fix path if needed:** add a `bootstrapSchedulingCompleted` flag separate from the snackbar-shown flag. **Trigger to act:** telemetry shows bootstrap failures > 1% of first-sign-ins. |
| ✅ RESOLVED | [core/data/backup/google-drive/.../auth/DriveAuthTokenProvider.kt `refreshTokenFromGms`](../core/data/backup/google-drive/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/google_drive/auth/DriveAuthTokenProvider.kt) | **Diagnostic Part-1 logging carried through Part-2 fix and the appProperties split.** Diagnostic logging dropped after manual verification (200 OK from Drive with per-field `appProperties` upload, restore confirmation populated end-to-end, sign-out → sign-in cycle shows consent screen with no stale-cache 401); production code now logs only at warning (`Log.w` on null-token silent refresh) and error (`Log.e` on `authorize()` throwing) levels. |
| ✅ RESOLVED | [core/data/backup/google-drive/.../auth/DriveAuthTokenProvider.kt](../core/data/backup/google-drive/src/main/kotlin/io/github/stslex/workeeper/core/data/backup/google_drive/auth/DriveAuthTokenProvider.kt) | **Token fetch caching.** `DriveAuthTokenProvider.currentToken()` now reads the cached token from `AccountDataStore` first (50-minute TTL set at sign-in / `completeSignIn` time) and only falls back to silent `authorize()` on cache miss / expiry. The Drive HTTP path no longer pays a GMS round trip per request. Covered by `DriveAuthTokenProviderTest`. See [backup.md → Token caching](feature-specs/backup.md#token-caching). |
| ✅ RESOLVED | [feature/settings/.../domain/BackupInteractorImpl.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/BackupInteractorImpl.kt), [feature/settings/.../domain/mapper/BackupDomainMapper.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/mapper/BackupDomainMapper.kt) | **`BackupManifest` import workaround.** During PR 4 the `DomainLayerPurityRule` flagged `import core.data.backup.api.model.BackupManifest` in `BackupInteractorImpl`, so manifest construction was routed through a `BackupDomainMapper.buildManifest(...)` factory and the impl relied on type inference to avoid the import. The rule has since been extended to exempt `core.data.<feature>.api.*` submodules (see [lint-rules.md → DomainLayerPurityRule](lint-rules.md#domainlayerpurityrule)); the impl now imports `BackupManifest` directly and the factory has been removed. |

---

## App-Scope Collapse (Hilt→Metro) — Step-6 status (no assisted blockers remain)

Tracked on the `feature/metro-batch` branch (App-Scope Collapse: the final DI migration moving the
app-scope Hilt graph to Metro). Steps 1–5 migrate bindings under a reversible dual-path; **Step 6** is
the single irreversible cut that drops `@HiltAndroidApp` and removes Hilt from the app graph entirely.

**The assisted→Metro mechanic EXISTS** (proven at `589777d9`): Metro 1.1.1 has native assisted injection —
a hand-written `@AssistedFactory` interface converted to `dev.zacsweers.metro.*`, `generateAssistedFactories`
left off, Metro generates the factory impl. **No Metro bump, no Kotlin-2.4.0 upgrade, no cascade into the
~695-LOC custom Detekt rules.** Any earlier "no assisted→Metro mechanic" / `interop { includeDagger() }`
framing is stale — deleted.

**The two entries once tracked here are SEPARATE, unrelated problems** (never an assisted capability gap):
**CommonDataStore is DONE** (below, resolved at `589777d9`); **ImageStorage is not an assisted problem** —
it is a permanent `create()` bound-instance root (§Test-override root in the execution spec), with only a
bounded prod-side construction tail left for Step 6.

| Severity | Location | Description |
|---|---|---|
| ✅ RESOLVED (`589777d9`) | [DataStoreProviderFactory.kt](../core/data/dataStore/src/main/kotlin/io/github/stslex/workeeper/core/data/dataStore/core/DataStoreProviderFactory.kt) ↔ [CommonDataStoreImpl.kt](../core/data/dataStore/src/main/kotlin/io/github/stslex/workeeper/core/data/dataStore/store/CommonDataStoreImpl.kt) | **`CommonDataStore` — Metro-owned, DONE at `589777d9`.** Migrated via Metro-native assisted: the `dagger.assisted.*` trio was converted to `dev.zacsweers.metro.*` (`DataStoreProviderFactory.kt:3` `import dev.zacsweers.metro.AssistedFactory`; `DataStoreProvider` `@AssistedInject` via `dev.zacsweers.metro`), `generateAssistedFactories` left off so Metro generates the factory impl. `CommonDataStoreImpl` is now `@ContributesBinding(AppScope::class)` + `@SingleIn(AppScope)` on the now-**public** class (`CommonDataStoreImpl.kt:24-25`); the produced `DataStoreProvider` stays **unscoped** (Metro forbids scoping assisted types), the app-scoped singleton lives on the consumer. `core/data/dataStore` applies the Metro plugin **alongside** the convention's Hilt-KSP with no opt-out — after the assisted conversion no `dagger.assisted.*` remains for Hilt-KSP, so the two processors coexist (§D10 in the execution spec). No Metro bump, no residual dual-processor collision. Live consumers (`AppRootViewModel` + `feature/settings`) resolve it through the app-scope graph. |
| 🟢 bounded Step-6 tail | [core/core-android/.../images/ImageStorageImpl.kt](../core/core-android/src/main/kotlin/io/github/stslex/workeeper/core/core/images/ImageStorageImpl.kt) ↔ [AppGraphSourceModule.kt](../app/app/src/main/java/io/github/stslex/workeeper/di/AppGraphSourceModule.kt) | **`ImageStorage` — permanent `create()` bound-instance root; NOT an assisted blocker, NOT a flip.** `ImageStorageImpl` is a plain `@Inject constructor(@ApplicationContext Context, @IODispatcher CoroutineDispatcher)` (`ImageStorageImpl.kt:30`) — **zero `@Assisted`**. It is deliberately **NOT** `@ContributesBinding`-flipped: an androidTest fake expressed as a contribution never merges into the main-compiled `@DependencyGraph` (`core:ui:test-utils` is `androidTestImplementation`-only, off the app main classpath), so a contribution flip would silently fall back to the real file-I/O impl — a false-green. Resolution (execution spec §Test-override root, 5c Option A′): `ImageStorage` **stays a permanent `create()` bound-instance root** — the graph owns it, tests inject `FakeImageStorage()` via `create()`. **Remaining bounded Step-6 task (NOT a blocker, NOT a rename):** at the atomic cut the *prod-side construction* of `ImageStorageImpl` moves from Hilt ownership (currently fed into `create()` via `AppGraphSourceModule`, `@TestInstallIn`-swappable) to a Metro/manual factory — someone non-Hilt must construct the prod `ImageStorageImpl` and pass it to `create()`. The **test path already survives** (`TestAppGraphModule` calls `createGraphFactory<AppGraph.Factory>().create(...)` directly, proven). **Count that matters — reproducible snapshot @ `7c8b9400`, the androidTest suites that actually resolve/assert on `ImageStorage` (the swap that must keep working):** `git grep -l 'ImageStorage' -- '*/src/androidTest/*.kt' \| wc -l` → **2** (`AppGraphAdoptBackSeamTest`, `ImageStorageFakeAwarenessTest`). Distinct broader surfaces (do not conflate): `@HiltAndroidTest` suites = **8** (`git grep -l '@HiltAndroidTest' -- '*/src/androidTest/*.kt' \| wc -l`); androidTest files importing `core:ui:test-utils` = **16** (`git grep -l 'import io.github.stslex.workeeper.core.ui.test' -- '*/src/androidTest/*.kt' \| wc -l`). The earlier "15 suites" figure was unanchored memory — do not use it. **Trigger to act:** Step-6 atomic cut (prod-construction owner migration); orthogonal to the DB-cascade DI flip. |

---

## Metro `@GraphExtension` migration — internal-constructor dependency on IR-level visibility

Tracked on `spike/graph-extension-all-trainings` (feature graphs → contributed `@GraphExtension`).

The per-feature public-API surface is minimised (all-trainings: 11 declarations, not the 14 ceiling)
by keeping the store's handler dependencies **internal**: `AllTrainingsStoreImpl` is a `public` class
with an **`internal` primary constructor** (`@Inject class AllTrainingsStoreImpl internal
constructor(navigationHandler: NavigationHandler, ...)`), so `NavigationHandler` / `PagingHandler` /
`ClickHandler` never become public API.

**The dependency:** `:app` generates the extension impl and constructs the store by calling that
`internal` constructor of another Gradle module. This works because Metro emits the constructor call in
**IR, after the frontend visibility checks** — and Kotlin `internal` constructors are emitted `public`
in bytecode, so there is no runtime barrier. It is a dependency on `internal` *not* being enforced at
Metro's codegen layer.

- **Blast radius:** every ported feature (13 at arc completion) that uses the internal-constructor
  pattern to keep handlers internal.
- **Detection is LOUD, compile-time:** if a Kotlin/Metro change ever enforced `internal` at the IR
  call site, `:app:app:compileDebugKotlin` would fail — not a runtime failure.
- **Rollback:** make the store's primary constructor `public` across all ported features (reverts the
  3-handler saving; surface returns to the 14-ceiling shape). Mechanical, no behavior change.
- **Axis:** watch on the **Kotlin bump** specifically (frontend/IR visibility semantics), not the
  Metro bump alone.

---

## v2.0 Foundations Stage — closed entries

The v2.0 stage addressed the following items. They are listed here for traceability before they roll into the next audit cleanup.

- ✅ `feature/exercise/.../mvi/handler/ClickHandler.kt:163` Track now CTA stub replaced with a real flow that creates an ad-hoc training and opens Live workout via `SessionConflictResolver`.
- ✅ `feature/exercise/.../ui/ExerciseDetailScreen.kt` now renders `state.adhocPlanSummaryLabel` between the description and history sections.
- ✅ `LiveWorkoutInteractorImpl.finishSession` now delegates to `SessionRepository.finishSessionAtomic`, which wraps plan updates + state transition in a single `database.withTransaction { ... }`. `runCatching` + compensating-rollback removed.
- ✅ DAO unit tests added for `TrainingDao.pagedActiveWithStats`, `pagedActiveWithStatsByTags`, and `SessionDao.observeAnyActiveSession` plus the three new aggregation queries (`getPersonalRecord`, `getBestSessionVolumes`, `pagedHistoryByExercise`).
- ✅ Active session conflict modal (`core/ui/kit/.../ActiveSessionConflictDialog.kt`) shared by Home Start CTA, Training detail Start session, and Exercise detail Track now.
- ✅ Live workout overflow Delete session option + `DiscardSessionConfirmDialog` confirm flow.

---

## Resolved (kept for diff visibility, will be removed in next audit)

These were tracked as debt in earlier versions of this doc. Verified resolved by 2026-04-28 audit.

- ✅ `feature/all-trainings/.../ui/components/RelativeTimeFormatter.kt` — file deleted; logic now lives in `TrainingListItemMapper`.
- ✅ `feature/all-trainings/.../ui/AllTrainingsGraph.kt` blocked-name shaping — moved to `ClickHandler` with `ResourceWrapper`.
- ✅ `feature/all-exercises/.../ui/AllExercisesGraph.kt` blocked-name shaping — moved to `ClickHandler` with `ResourceWrapper`.
- ✅ `feature/exercise/.../ui/ExerciseEditScreen.kt` plan summary — `state.adhocPlanSummaryLabel` pre-formatted.
- ✅ `feature/exercise/.../ui/components/ExerciseHistoryRow.kt` date and sets — pre-formatted via `ExerciseUiMapper`.
- ✅ `feature/single-training/.../ui/components/TrainingHistoryRow.kt` date — pre-formatted via `CommonHandler`.
- ✅ `feature/live-workout/.../ui/components/LiveExerciseCard.kt` status-line — `exercise.statusLabel` pre-formatted in `LiveWorkoutMapper`.
- ✅ `feature/settings/.../ui/ArchiveGraph.kt` timestamp formatting — moved to `ArchiveUiMapper`. (Note: snackbar template substitution remains as a separate, smaller debt — see UI Mapping Boundary table above.)

---

## Component death candidates (v3 stage 4, 2026-07-29)

- `AppSection` — ruled lists won on three consecutive screens (exercise detail, settings, history); `SettingsSection` is already deleted (#191). When the derived screens stop consuming it, delete rather than restyle.
