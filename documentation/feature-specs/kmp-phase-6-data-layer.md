# KMP phase 6 — the data layer goes multiplatform

Phase 6 of the KMP/CMP migration. Independent of the app/UI axis: it touches no composition, no
navigation, no startup path. Android ships continuously; `dev` is cuttable at every commit.

Arc position: 0 release + nav oracle ✅ · 1 Nav3 ✅ · 2 conventions ✅ (#227) · 3 core collapse ✅
(#228) · golden harness ✅ (#229) · 4 extract `app:common` ✅ (#230, #231) · **6 data layer ← this
document** · 5 startup processor (deferred, see "Why 6 before 5") · 7 UI stack to CMP + iosApp.

Everything below headed **measured** was verified against the tree on 2026-08-16 at
`dev@60482a998`. Everything headed **decided** is a choice with its reasoning; everything headed
**deferred** names work this phase deliberately does not do.

---

## Why 6 before 5

Two reasons, one of which pays off on Android alone.

1. **The bundled SQLite driver.** Room 3's `BundledSQLiteDriver` ships one SQLite build instead of
   the per-OEM, per-API-level system one — a class of device-specific bug that stops existing rather
   than being chased. See "The driver decision" for why this phase ports *without* taking that flip,
   and why the flip is a separate, independently revertable commit.
2. **Phase 5 rebuilds the graph, and three `@SingleIn(AppScope)` holders could not survive a second
   one.** That is closed by §1 below, which is shipped ahead of the KMP work because it needs the
   same module edge the conversion needs anyway.

---

## §0 Baseline, measured

| Fact | Value | How |
|---|---|---|
| `core:data:database` src/main | 54 `.kt` / 3472 LOC | `find … -name '*.kt' \| wc -l` |
| …of which touch `android.*`/`java.*` | **5** (9 import lines) | `grep -rn '^import android\.\|^import java\.'` |
| …src/test | 25 files / 122 `@Test`, 21 Robolectric | grep |
| …src/androidTest | 5 files / 28 `@Test`, all `@Regression` | grep |
| …src/testFixtures | 3 files, consumed by **3 modules / 19 files / 192 `@Test`** | grep |
| Migrations registered | **1** (`Migration6`, 5→6); `MIN_SUPPORTED_SCHEMA_VERSION` = 5 | `MigrationsRegistry.kt` |
| Exported schemas | 6 (`1.json`…`6.json`) | `ls schemas/` |
| `core:data:exercise` src/main | 42 files; **6** carry platform imports | grep |
| `core:data:dataStore` src/main | 5 files; Context needed in **1** (`DataStoreProvider`) | grep |
| `core:data:backup:api` src/main | 24 files; **2** platform imports | grep |
| `kotlin.uuid.Uuid` in `core/data` | 83 files; `java.util.UUID` **0** | grep |
| `java.time` in `core/data` | **2** files, 1 of them production | grep |
| `okio` anywhere in the repo | **0** | grep |
| Modules on `convention.kmpLibrary` | **1** (`core:core`) | grep |

**Room is already on Room 3** (`androidx.room3` 3.0.0 / `androidx.sqlite` 2.7.0) — no
`androidx.room.*` import remains. The port is KMP-shape, not a Room upgrade.

**Every dependency the conversion needs publishes `iosSimulatorArm64`** — verified by fetching the
Gradle Module Metadata for each, not by assumption:

| Artifact | iosSimulatorArm64 published |
|---|---|
| `androidx.room3:room3-runtime:3.0.0` | yes |
| `androidx.room3:room3-paging:3.0.0` | yes |
| `androidx.sqlite:sqlite-bundled:2.7.0` | yes |
| `androidx.paging:paging-common:3.5.0` | yes |
| `androidx.datastore:datastore-preferences:1.2.1` | yes |

`androidx.paging.PagingSource` lives in **paging-common** (confirmed by listing `classes.jar` inside
the `paging-common` AAR), so the 18 `PagingSource`-returning DAO methods and the 11
`Flow<PagingData<T>>` repository signatures are portable without changing a single consumer import.
The catalog's `androidx-paging-runtime` (Android-only, `paging-runtime-ktx`) is what must move.

---

## §1 The three DataStore stragglers — SHIPPED (PR A)

`BackupPreferencesRepositoryImpl` (`backup_scheduling_prefs`), `RestoreStateRepositoryImpl`
(`restore_state_prefs`), `AppDialogRepository` (`app_dialogs_prefs`). Each was `@SingleIn(AppScope)`
while minting its own `DataStore` via `PreferenceDataStoreFactory.create`, bypassing
`DataStoreProvider`'s static, process-lifetime memoization. Full mechanism, evidence and unit-test
consequences: `documentation/tech-debt.md` → "DataStore singleton bypass" (now ✅ RESOLVED).

**The hard gate — file-path identity — passed, proven from the androidx sources.**
`Context.preferencesDataStoreFile(name)` = `dataStoreFile("$name.preferences_pb")` =
`File(this.applicationContext.filesDir, "datastore/$fileName")`. The extension resolves
`applicationContext` itself, so the direct route and the provider route give the identical `File`.
Same file before and after: a fix, not a migration.

**Proven red first:** 6 tests / 3 failures against the unfixed tree, each throwing the production
`IllegalStateException: There are multiple DataStores active for the same file: …`. Then 8/8 green.

---

## §2 The conversion order is forced, not chosen — measured

`core:data:exercise` imports 78 symbols from `core.data.database` across 30 of its 42 main files, and
`core:data:database` depends on `:core:data:backup:api`. So:

```
backup:api  →  database  →  exercise
```

`core:data:dataStore` is off this chain entirely (nothing in it depends on the other three), so it
converts independently and first — it is the smallest module with a real `expect/actual` in it, which
makes it the right place to meet the toolchain.

**Decided increments, one PR each, each independently shippable and Android-releasable:**

- **A. Stragglers** (shipped) — no KMP, clears the Phase 5 precondition.
- **B. `core:data:dataStore` → KMP** (shipped, see §2.1) — the platform seam is the store *path*, not
  the store.
- **C. `core:data:backup:api` → KMP** (shipped) — 22 files commonMain, 2 androidMain. See §4.
- **D. `core:data:database` → KMP** — the centrepiece. See §3.
- **E. `core:data:exercise` → KMP** — mechanical once D lands, plus the one genuine behaviour change
  in §5.

### §2.1 What PR B settled — including one invariant this repo had recorded backwards

Nine files: `commonMain` holds the entire store surface (`CommonDataStore(Impl)`, `BaseDataStore`,
the assisted `DataStoreProvider`/`Factory`), `androidMain` and `iosMain` hold one class each.

**The seam is an interface, not `expect`/`actual`.** An `expect`/`actual` pair must agree on its
constructor signature, and Android's resolver needs a `Context` while iOS's needs nothing — so
`expect` would have forced Android to reach for a global `Context` holder to satisfy a shape iOS
could also satisfy. `DataStorePathResolver` is an interface with `@ContributesBinding` on the Android
side, the same interface-shaped seam `ResourceWrapper` and `ImageStorage` already use. **Worth
carrying to phase 7: `expect`/`actual` is the wrong tool whenever the platforms differ in what they
need *injected*, which for a UI stack will be most of the time.**

**The Android resolver delegates to `Context.preferencesDataStoreFile` rather than rebuilding the
path.** One definition of where user data lives, owned by the library. Pinned by
`AndroidDataStorePathResolverTest` over all five shipped store names.

**The memoization is a CAS loop, not `ConcurrentHashMap` + `synchronized`** — neither exists in
Kotlin/Native. `kotlin.concurrent.atomics.AtomicReference` over an immutable map (stdlib 2.4.10;
`kotlin.concurrent.Volatile` was already used in `core:core` commonMain as precedent). Losing the
race costs a discarded `DataStore` and nothing else, because DataStore opens its file lazily in
`FileStorage.createConnection()` — an instance that never wins the CAS is never opened and leaks no
file handle. That property is what makes lock-free safe here; it is not a general licence.

**Correction — `@ContributesBinding` DOES work from `commonMain`.** `core:core`'s `AppScope` KDoc
recorded the opposite as an invariant ("every `@ContributesBinding(AppScope::class)` SITE must live
in an Android-compiled source set — never `commonMain`/`iosMain`"), with the reason "those
annotations require the Metro compiler plugin, which `core:core` does not apply" — and `core:core`
**does** apply it, since phase 3. Measured: `CommonDataStoreImpl` is `@ContributesBinding` in
`commonMain` and `:app:app:assembleDebug` resolves it; **mutation M-CM1** (delete the annotation)
reds with `[Metro/MissingBinding] No binding found for CommonDataStore`, so the green is real rather
than a pass over an unrequested binding. The mechanism is mundane — `commonMain` sources are part of
the Android compilation, which is where the plugin runs. KDoc corrected in place.

**This is the single most load-bearing finding here for phase 7.** Had the recorded invariant been
believed, every feature's `Store`/handler/mapper binding would have been forced into `androidMain`
during the CMP conversion, which would have made the shared UI layer un-shareable in exactly the way
the migration exists to avoid.

Two smaller traps, both of which cost a run here.

**Robolectric's sandbox does not cover property initializers.** Under the tech.apter JUnit 5 bridge
the Robolectric environment is installed around `@BeforeEach` and `@Test`, but **not** around
construction of the test instance. So

```kotlin
private val context = ApplicationProvider.getApplicationContext<Application>()   // dies
```

fails with `IllegalStateException: No instrumentation registered! Must run under a registering
instrumentation` — an error that reads like a broken subject rather than a misplaced call. Acquire
the context in `@BeforeEach`. Every existing Robolectric test in this repo happens to do that
already, so the rule was invisible until a new one did not. **Phase 7 converts many Robolectric
tests; expect to meet this.**

**And the meta-lesson attached to it:** that failure mode made a *mutation* look like it worked. The
new path test was red for this reason before it was ever green, so its first "mutation reds it" run
proved nothing — the mutation and the bug produced the same two failures. Establish the green
baseline for a NEW test before mutating it, not just for the code under test. Fixed, then re-proven
green → red → green.

**iOS:** `NSFileManager.URLForDirectory(…)` takes an `NSError` out-parameter whose `CPointer` type
drags `@ExperimentalForeignApi` into the file. The list-returning
`URLsForDirectory(directory, inDomains)` is pure Foundation and needs no opt-in.

---

## §3 What `core:data:database` needs, and the four gaps that are not in the plan

The Room half is well-understood and small: an `expect object AppDatabaseConstructor :
RoomDatabaseConstructor<AppDatabase>` in commonMain, `@ConstructedBy` on `@Database`, and the
replacement of **15** reflective `AppDatabase::class.java` builder sites (1 production, 6 src/test, 5
src/androidTest, 1 testFixtures, 1 database-test, 1 app/app androidTest) with the generic
`Room.databaseBuilder<AppDatabase>(…)` form. Room KMP also requires `setQueryCoroutineContext(…)`,
which is currently set nowhere. Only 5 of 54 main files move to `androidMain`: `AppDatabaseFactory`
and the three-file `snapshot/` package (which uses `android.database.sqlite.SQLiteDatabase` directly,
twice, deliberately outside Room), plus `WorkoutExportMapper`'s `java.time.Instant`.

The four gaps below are **not** in the phase plan as handed over, and each is load-bearing.

### 3.1 `testFixtures` does not exist on KMP — 192 tests hang off it

`core:data:database` is the repo's only `testFixtures` producer. Its 3 files (`RepositoryTestEnv`,
`PrRuleFixture`, `PrRuleDbSeeder`) are consumed by `core:data:exercise` (185 `@Test`),
`feature:exercise-chart` (4) and `feature:exercise` (4) — **19 files, 192 `@Test` total**. Phase 2
measured `testFixtures { }` as an unresolved reference on the AGP-KMP DSL.

**Decided:** re-home them into a real module before the conversion, following the
`core:ui:golden-harness` precedent (#229) and the sibling `core:data:database-test` that already
exists for exactly this reason. The re-home is its own commit inside PR D, landing green before the
plugin swap, so a bisect never lands on a commit where 192 tests do not compile.

### 3.2 The KMP convention has no device-test variant, and CI would not notice

`KmpLibraryConventionPlugin` never calls `withDeviceTest` — so a KMP module today has no
`androidDeviceTest` source set and no `androidDeviceTestRuntimeClasspath`. `core:data:database` has
**5 instrumented classes / 28 `@Test`**, including the only `MigrationTestHelper` suite.

Phase 4 already wrote the diagnosis down at
`ConfigureInstrumentedSuiteGate.kt` → `ANDROID_TEST_ASSEMBLE_TASKS`: CI runs
`./gradlew assembleDebugAndroidTest`, the AGP-KMP APK task is `assembleAndroidDeviceTest`, and
"the first instrumented-test module to convert needs an `assembleDebugAndroidTest →
assembleAndroidDeviceTest` alias in the KMP convention … That alias belongs with the conversion that
needs it." **`core:data:database` is that module.** Without the alias the APK is never built, the
classpath gate never runs, and the 28 tests vanish from CI **green** — the arc's recurring failure
shape, pre-identified.

**Decided:** the alias lands in PR D and is proven in both directions (task graph present/absent via
`--dry-run`, and the suite counted before and after).

### 3.3 The instrumented source directory name is load-bearing

The gate scans exactly `src/androidTest/kotlin`, `src/androidTest/java`, `src/androidDeviceTest/kotlin`.
The older KMP spelling `src/androidInstrumentedTest` is **absent** — and `detekt.yml`'s own excludes
still mention it three times. Landing instrumented sources under the wrong name makes
`detektAndroidTestSuite` go NO-SOURCE and `verifyInstrumentedSuiteClasspath` report *0 instrumented
source files* and **pass**: the exact vacuous green the gate exists to prevent.

**Decided:** `src/androidDeviceTest/kotlin`, and PR D prints the input count both tasks saw.

### 3.4 `detekt.yml` has zero `androidDeviceTest` excludes

Measured: `androidDeviceTest` appears **0** times in `detekt.yml`, while `androidHostTest` appears on
12 lines. Every rule whose excludes were hand-extended for KMP host tests will judge
`src/androidDeviceTest` as production code — backtick test names trip `FunctionNaming`,
`TooGenericExceptionCaught` fires, and so on. Adding device tests reds detekt for reasons unrelated
to the tests.

**Decided:** extend the excludes in PR D, in the same commit as the source-set move.

### 3.5 `RoomLibraryConventionPlugin` cannot be applied to a KMP module as written

It adds `room-compiler` to the configuration literally named `ksp` and `room-testing` to
`androidTestImplementation`. Neither exists on an AGP-KMP module; the measured spellings (probe P7)
are `kspAndroid` / `kspIosSimulatorArm64` and `androidDeviceTestImplementation`. It also adds the
Android-only `androidx-paging-runtime` unconditionally.

**Decided:** the convention gains a KMP branch rather than a forked plugin, so a single plugin id
keeps describing "this module uses Room" — and `paging-runtime` becomes `paging-common`.

---

## §4 `backup:api` is not interface-only — correction to the plan

The plan lists it among the "clean non-UI modules". Measured, it is 22 of 24 files clean and **two
Android/JVM types sit in its public API**:

- `java.io.File` — `BackupStorage.uploadBackup(dbFile: File, …)` / `downloadBackup(…, target: File)`.
- `android.net.Uri` — `RecoveryDiagnosticsExporter`, twice.

Nine modules depend on `backup:api`, so changing these is a nine-module ripple, and there is no okio
in the repo to change them *to*.

**Decided, and shipped in PR C:** the pure surface (models, errors, results, `BackupPreferences`,
`BackupSchedule`, the restore contracts — the part Phase 7's settings UI needs from commonMain) goes
to `commonMain`; `BackupStorage` + `RecoveryDiagnosticsExporter` stay in `androidMain` until a real
iOS backup implementation makes the right shape observable rather than guessed. Inventing `expect`
wrappers for a platform with no implementation would be speculative API churn across nine modules.
`okio.Path` is the obvious eventual candidate for `File` (okio is on the graph from PR B), but
`android.net.Uri` has none: it is a SAF handle, and iOS shares documents by a different model.

**Split measured: 22 files commonMain / 2 androidMain.** The whole module has exactly four external
imports — `android.net.Uri`, `java.io.File`, `Flow`, `StateFlow` — and **zero** references to
`core:core`, whose `implementation` dependency it had declared and never used. Dropped.

**A KMP module with no androidx dependency cannot resolve library-contributed lint issue ids — and
that is a hard error.** `backup:api` ended the conversion depending on `coroutines-core` alone, and
`:core:data:backup:api:lintAndroidMain` then failed with

```
lint-rules/lint.xml:126: Error: Unknown issue id "FragmentTagUsage" [UnknownIssueId]
```

`FragmentTagUsage` ships in **androidx.fragment's own lint registry**, not in lint's built-ins, so it
resolved only while the module transitively saw androidx — which, as a classic Android library, it
always did via the convention's blanket `implementation` deps. Strip those and the shared `lint.xml`
becomes unparseable *for that module*.

Fixed at the root rather than suppressed: the entry is **deleted**, because this repo has **zero**
Fragments (grep across all Kotlin and Gradle sources returns nothing — it is Compose + Nav3), so the
check could never fire. Disabling `UnknownIssueId` instead would have blinded the whole repo to real
typos in that file, and adding an androidx dependency to satisfy a lint id would be absurd.
`ValidFragment` and `CommitTransaction` sit adjacent in the same file and are lint built-ins, so they
resolve everywhere and stay. Verified after the fix on all three shapes: the stripped KMP module, the
androidx-carrying KMP module, and `:app:app`.

**Phase 7 will meet this repeatedly**, because converting a module is exactly what strips its
accidental androidx closure. The failure names the lint id, not the missing dependency, so it reads
as a config error rather than a conversion consequence. If another library-contributed id turns up in
`lint.xml` later, delete it or move it behind the module that owns the dependency — do not reach for
`UnknownIssueId`.

**Correction to the convention's own guidance, found here.** `KmpLibraryConventionPlugin`'s KDoc says
a module with no host tests opts out per-module with
`failOnNoDiscoveredTests.set(false)`. Measured: **not needed when the source set is absent
entirely** — `testAndroidHostTest` is then `NO-SOURCE` and never runs, so the flag never applies.
`backup:api` builds green without the opt-out. The flag matters only when `androidHostTest` sources
exist but discover no tests. Phase 7 converts modules with no host tests; do not paste in dead
config on the strength of that KDoc. (This spec briefly carried the same wrong claim, written into a
build script comment before it was probed.)

---

## §5 `core:data:exercise` — the plan's "domain layer" does not exist

**Correction.** The plan converts "the domain layer of `core:data:exercise`", described as measured
clean — zero `android.*` under `domain/`. Measured: **`core/data/exercise` contains no `domain/`
directory, and neither does any module under `core/data/`.** Domain layers in this repo live in
`feature/<name>/domain/` (148 files across 14 modules). The "zero `android.*` in `domain/`"
measurement was taken over **zero files** — the green-over-no-inputs shape §4 of the phase brief
warns about, occurring inside the brief itself.

What is actually there: 42 files, 6 with platform imports — but those 6 are the three paged
repository *interfaces* and their impls, i.e. the module's whole public surface. 15 of the 16
platform imports are `androidx.paging`, which is portable (§0). The 16th is the real one:

```kotlin
} catch (_: SQLiteConstraintException) {   // ExerciseRepositoryImpl.kt:117
    return@transition SaveResult.DuplicateName
}
```

`android.database.sqlite.SQLiteConstraintException` here is **control flow, not a type name**: the
duplicate-name branch exists only because that exception is thrown. Under a Room 3 driver the
constraint violation arrives as a different type, the `catch` silently stops matching, and the
exception escapes — a behaviour change with no compile error.

**Decided:** PR E converts this catch to the driver-level `androidx.sqlite.SQLiteException` with a
constraint-code check, gated by a test that inserts a duplicate name and asserts
`SaveResult.DuplicateName` — written red against the converted code first.

Also measured and to be dropped in PR E: `core:data:exercise` declares `androidx.compose.runtime` +
the Compose BOM with **zero** Compose usages, and `androidx.room3:room3-runtime` with **zero** Room
references. Sizing this module from its build script overstates its coupling.

---

## §6 The driver decision — decided

`sqlite-framework` publishes an iOS variant, so converting to KMP does **not** require changing which
driver Android uses. Two options existed:

- **(a)** `BundledSQLiteDriver` everywhere — the uniform-SQLite payoff.
- **(b)** `AndroidSQLiteDriver` on Android, bundled on iOS — zero Android behaviour change.

**Decided: port on (b), then flip to (a) as its own commit with its own gate.** The bundled driver is
a *different SQLite build* from the framework one; that is the point and also the risk.
`SessionDao.kt:76-80` already documents avoiding `ROW_NUMBER()` because minSdk 28 ships SQLite 3.22,
which is direct evidence that this codebase's SQL is written against the system SQLite version.
Fusing the flip into the port makes a SQLite-behaviour regression and a source-set regression
indistinguishable under bisect, and makes the safe half unrevertable without the risky half.

**The migration suite is the gate for the flip**, per the phase brief, and Robolectric is not
admissible as its oracle: `AtomicRollbackDeviceTest.kt:32-37` records Robolectric giving a false
**negative** on transaction rollback *twice*. Evidence for the flip will be the instrumented
`AppDatabaseMigrationTest` (real `MigrationTestHelper`, real device) run under both drivers, plus a
5→6 migration applied to a real v5 database on device under the bundled driver.

**Schema-hash hazard to carry into PR D:** `6.json`'s `identityHash` must not move. Any incidental
change to an entity, column or index declaration during the port silently invalidates
`runMigrationsAndValidate` and would need a new migration for shipped users. The 9-name
`EXPECTED_V6_TABLES` set in `AppDatabaseMigrationTest.kt:264-274` is a hand-maintained mirror of
`6.json` and will not self-correct.

---

## §7 Deferred, with reasons

- **`java.io.File` → okio.** The data-layer surface is 9 main-source files, three with `File` in
  *public interface signatures* spanning `backup:api`, `database`, `google-drive` and
  `feature:recovery` — a multi-module API break, larger than it looks, exactly as the brief allowed
  for. Filed, not forced.

  **But the dependency half of that cost disappears in PR B, and this is worth knowing before anyone
  re-prices the rider.** okio has 0 occurrences in the repo today, yet the commonMain DataStore API
  is `PreferenceDataStoreFactory.createWithPath(produceFile: () -> okio.Path)` — read from the
  `datastore-preferences-core` 1.2.1 sources; the `() -> File` overload is `jvmAndroidMain`-only. So
  converting `core:data:dataStore` brings okio into the graph as a *requirement of DataStore on KMP*,
  not as a choice. After PR B the remaining cost of the `File` rider is purely the nine-module
  signature change, with no new third-party dependency to justify.
- **`java.time` → kotlinx-datetime.** The rider is real but almost entirely outside this phase: of
  **16** files (not 15 — one uses fully-qualified `java.time.LocalDate` with no import, so every
  import-based sweep, including this brief's, misses it), **14 are `feature:exercise-chart`** and
  belong to Phase 7. `core/data` has 2, one of them production: `WorkoutExportMapper.kt:38`,
  `Instant.ofEpochMilli(epochMs).toString()`. That single line is taken in PR D; the rest is Phase 7's.
  Helpfully, there are **zero** `DateTimeFormatter` uses repo-wide — locale formatting already lives
  behind `ResourceWrapper` — so no formatter port is implied.
- **The silent flow `onError`.** Measured and written up in `documentation/tech-debt.md`; not fixed
  here. It is in `core:core`, outside this phase's layer, and cannot be gated in both directions
  without a logging seam that does not exist. **It matters to this phase as a risk, not a task:** 21
  of 22 production flow collections report nothing, so a data-layer conversion that changes flow
  error shape produces screens stuck on default state with no signal in logcat, Crashlytics, or CI.
  Every increment here must assert observable effects, never the absence of an exception.

---

## §8 Notes for Phase 7, which reads this

- **Metro contributions work from `commonMain`** (§2.1, measured with a reddening mutation). The
  invariant this repo had written down said otherwise and was wrong; believing it would have pinned
  every feature binding to `androidMain` and defeated the point of a shared UI layer.
- **Prefer an injected interface over `expect`/`actual` whenever the platforms differ in their
  dependencies**, not just their implementations (§2.1). `expect`/`actual` classes must agree on
  constructor shape, which pushes Android toward global holders for things it should be injecting.
- **Aliases are the KMP tax, and they are per-task.** `testDebugUnitTest`, `assembleDebug`,
  `lintDebug` already exist in the convention; `assembleDebugAndroidTest` arrives in PR D. Phase 7
  needs one more that nothing has needed yet: **CI runs `verifyPaparazziDebug` unconditionally and
  the KMP convention registers no such task**. 13 modules apply the golden gate. That is the same
  vanish shape, already loaded.
- **A KMP module silently loses everything `configureKotlinAndroid` provides** — BuildConfig, the
  `local.properties` → BuildConfig injection, core-library desugaring, the four blanket
  `implementation` deps, Robolectric/mockk/androidx-test on the unit-test classpath, and the
  `-Xjvm-default=all` / `-XXLanguage:+PropertyParamAnnotationDefaultTargetMode` flags. `core:core`
  re-added two by hand. Phase 7 converts ~30 modules; budget for this per module.
- **Converting a module strips its accidental androidx closure, and the shared `lint.xml` notices.**
  A library-contributed lint issue id (one shipped in an androidx artifact's own lint registry rather
  than lint's built-ins) becomes `Unknown issue id … [UnknownIssueId]` — a hard `lint` failure —
  the moment a module stops depending on the artifact that contributed it. Hit in §4 with
  `FragmentTagUsage`. Delete the dead id; never reach for `UnknownIssueId`.
- **Robolectric on KMP needs three things the convention does not give you** (the two deps plus
  `junit.platform.launcher.interceptors.enabled`), and it is a per-module concern by design. 21 of
  25 database unit tests and 16 of 20 exercise tests are Robolectric-gated. Add a fourth thing:
  **never call `ApplicationProvider` from a property initializer** — the sandbox does not cover
  instance construction (§2.1).
- **`failOnNoDiscoveredTests` diverges**: Gradle's default `true` on KMP, explicitly `false` on the
  Android convention. Tests moved to a `commonTest` source set — which the convention neither creates
  nor wires — will red rather than vanish. That is the good direction, but the cause is not obvious.
  A module with NO host-test sources at all needs no opt-out, contrary to the convention's KDoc
  (§4): the task is `NO-SOURCE` and the flag never applies.
- **Two KDoc claims in this area are already wrong.** `KmpLibraryConventionPlugin.kt:26-27` names a
  sibling module `core:core-android` that Phase 3 deleted, and `detekt.yml:231-232` attributes the
  KMP detekt `source.setFrom` to `core/core/build.gradle.kts`, which has no detekt block at all.
  Verify against code.
