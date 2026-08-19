# Post-Metro architecture cleanup — read-only discovery report

**HEAD SHA:** `5941d663ff360b9de38a35769a947e814d1cb5c3`
**Branch:** `feature/metro-batch` — confirmed this SHA **is** the branch tip
(`git rev-parse HEAD == git rev-parse feature/metro-batch`).
**Mode:** READ-ONLY. No source was edited; no commits, branch, or worktree changes were made.
**Date:** 2026-07-21.

> **Method.** Every factual claim below is backed by `path:line` + a verbatim snippet read from
> **current** source on this HEAD. Claims are labelled **[resolved]** (proven by structure / compiler /
> artifact inspection), **[text-match]** (grep only), or **[version-gated]** (depends on a catalog
> version, verified against the artifact on the classpath). Nothing here is sourced from commit
> messages, the PR description, KDoc, or prior summaries — where KDoc contradicts the code, that is
> flagged explicitly (§7).
>
> **Contamination note.** The repo contains two **stale git worktrees** under
> `.claude/worktrees/agent-ad355a0f7c2aaf2e0/` and `.claude/worktrees/agent-a070633479f49fb12/` that
> still hold the **old Hilt** source. Every sweep in this report excludes both `*/.claude/worktrees/*`
> and `*/build/*`. Any Hilt hit in those trees is **not** current source and is not cited as such.

---

## 0. TL;DR — per-area verdicts

| Area | Verdict | Gated on |
|---|---|---|
| **Precondition — Hilt excision** | **Excised from SOURCE + BUILD; NOT from the runtime classpath** | plain **dagger-core 2.57.2** is a transitive runtime dep of `firebase-perf` (not Hilt) |
| **1 — fold `:core:core-android` → `:core:core/androidMain`** | **BLOCKED** | one unproven toolchain fact: *does the Metro compiler plugin process a KMP `androidMain` source set?* No in-repo precedent. Everything else is clean. |
| **2a — collapse dispatcher qualifier expect/actual** | **UNBLOCKED** (post-Hilt) | prerequisite: apply the Metro plugin to `:core:core` (currently has none). Metro 1.3.2 ships a commonMain-safe `@Qualifier`. |
| **2b — move the IO dispatcher provider to commonMain** | **BLOCKED** by coroutines API topology, **independent of DI** | `Dispatchers.IO` is not on the common `Dispatchers` object (it lives in `concurrentMain`) |
| **3 — `:core:di` `api()` over-exposure** | **No clean demotion exists** — all 8 `api` edges are load-bearing | the real lever is *splitting* `AppGraphContract`, not demoting edges |
| **4 — remove the flat `AppGraphContract` accessor surface** | **UNBLOCKED on capability** — it is a design choice, not a capability gap | **Metro 1.3.2 ships `@GraphExtension`** (verified from the jar); premise "1.1.1 pinned" is **stale** |

**Four stated premises are contradicted by current source** (details §7): the Metro version (1.1.1 → **1.3.2**), the `:core:core-android` build KDoc ("Hilt `@Module`s / `@InstallIn`" → Metro `@BindingContainer`), the dispatcher-consumer count ("~44" → **74 sites / 57 files**), and the framing that the IO split is a Hilt/DI concern (it is a coroutines-API concern).

---

## PRECONDITION — Hilt-excision state (gates Areas 1 & 2)

### Source & build config: Hilt fully excised — [resolved]

- **Zero live Hilt annotations** on any declaration. `@HiltAndroidApp / @AndroidEntryPoint /
  @HiltViewModel / @HiltWorker / @InstallIn / @EntryPoint` appear in real source **only inside
  comments** describing the removed era:
  - `app/app/src/main/java/io/github/stslex/workeeper/AppRootViewModel.kt:14` — `// … plain ViewModel (was the last @HiltViewModel).`
  - `core/ui/mvi/src/androidTest/…/AppFeatureProbe.kt:66` — `* … de-Hilt'd. The former \`@HiltViewModel\` …`
  - `core/data/backup/worker/src/test/…/WorkerTestFactory.kt:18` — `* Mirrors the binding that the @HiltWorker code generator produces …`
- **Zero `import dagger.hilt.*` and zero `import dagger.*`** in real source.
- Every DI annotation resolves to **`dev.zacsweers.metro.*`** (spot-checked across `core/ui/kit`,
  `core/core-android`, `core/data`, `app/app`, `feature/settings`): e.g.
  `core/core-android/…/di/DispatchersBindingContainer.kt:4-6` imports
  `dev.zacsweers.metro.{BindingContainer, ContributesTo, Provides}`.
- **No Hilt/Dagger Gradle plugin** in any build script. Root `build.gradle.kts:2-14` plugins block:
  `application / kotlin / library / serialization / ksp / room / composeCompiler / robolectric.junit5 /
  gms / firebaseCrashlytics / firebasePerf / detekt` — no hilt. The catalog
  (`gradle/libs.versions.toml`) has **no hilt/dagger alias** in `[plugins]` or `[libraries]`; the sole
  DI plugin is `metro = { id = "dev.zacsweers.metro", version.ref = "metro" }` (`:214`), `metro = "1.3.2"` (`:78`).

### The only surviving JSR-330 — [resolved]

- `import javax.inject.Qualifier` exists in **exactly 4 files** — the `androidMain` **actual annotation
  classes** for the dispatcher qualifiers:
  `core/core/src/androidMain/…/di/{IODispatcher,DefaultDispatcher,MainDispatcher,MainImmediateDispatcher}.kt:3`.
  Each is `@Qualifier @Retention(AnnotationRetention.BINARY) actual annotation class …` — this is
  **JSR-330, not Hilt**.
- It resolves through the **bare** artifact `javax-inject = javax.inject:javax.inject:1`
  (`gradle/libs.versions.toml:175`), declared `api(libs.javax.inject)` in `core/core/build.gradle.kts:45`
  (comment `:44`: *"Was pulled transitively via hilt.android; now the bare javax.inject artifact, Hilt gone."*).
  The convention plugin adds it to every Android module (`build-logic/convention/…/KotlinAndroid.kt:97-101`)
  so Metro's `includeJavax()` can read the 4 qualifiers.
- (The 6 other `import javax.inject.*` grep hits are inside **triple-quoted lint-rule test fixtures** in
  `lint-rules/…/*Test.kt` — string literals, not compiled imports.)

### Runtime classpath: NOT Hilt-free — [resolved] ⚠️

- **Plain Dagger-core `com.google.dagger:dagger:2.57.2` (NOT Hilt) is on the app runtime classpath**,
  pulled **transitively at `runtime` scope by `firebase-perf`**. The `firebase-perf-22.0.6.pom` in the
  gradle cache declares `<groupId>com.google.dagger</groupId><artifactId>dagger</artifactId><version>2.57.2</version><scope>runtime</scope>`.
  All three flavors depend on firebase-perf: `app/app/build.gradle.kts:73`, `app/dev/build.gradle.kts:14`,
  `app/store/build.gradle.kts:11` (`implementation(libs.google.firebase.perf)`). `dagger-2.57.2.jar` is
  present in `~/.gradle/caches/…/com.google.dagger/dagger/2.57.2/`.
- This is **Dagger's JSR-330 runtime used internally by Firebase**, wholly unrelated to the project's DI.
  The Hilt jars also present in the shared gradle cache (`hilt-android/2.59.2`, etc.) are **not referenced
  by any build script or catalog entry** — stale cache residue.

**Precondition conclusion:** Hilt is **fully excised from project SOURCE and BUILD CONFIG** (provable
now). Hilt is **not** on the runtime classpath either — but **plain dagger-core is**, transitively via
firebase-perf. That transitive dagger-core is inert w.r.t. the project's Metro DI and does **not** re-block
Areas 1 & 2. *Caveat:* "no dagger on the resolved runtime classpath" was inferred from declared deps + the
firebase-perf POM, not from a live `:app:<flavor>:dependencies` dump (no builds were run in this read-only pass).

**⇒ Areas 1 & 2 are UNBLOCKED by the Hilt precondition.**

---

## AREA 1 — `:core:core-android` redundancy

### Inventory — [resolved]

`core/core-android/src` holds **11 main files + 1 test**, all under package
`io.github.stslex.workeeper.core.core.*` in a **plain `src/main/kotlin` layout** (not a KMP source set):

| File | Declaration |
|---|---|
| `di/AppScope.kt:23` | `abstract class AppScope private constructor()` — bare marker, no Metro import |
| `di/DispatchersBindingContainer.kt:22-24` | `@BindingContainer @ContributesTo(AppScope::class) object` — 4 `@Provides @SingleIn(AppScope) @<Qualifier>` funcs → `Dispatchers.Main/.Main.immediate/.Default/.IO` |
| `di/ResourceWrapperBindingContainer.kt:24-32` | `@BindingContainer @ContributesTo(AppScope::class) object` — `@Provides … provideResourceWrapper(context): ResourceWrapper = AndroidResourceWrapper(context)` |
| `platform/AndroidAppReinitializer.kt:30-33` | `@ContributesBinding(AppScope) @SingleIn(AppScope) @Inject class … : AppReinitializer` |
| `platform/AndroidPlatformInfoProvider.kt` | same annotation trio `: PlatformInfoProvider` |
| `platform/AndroidTempFileProvider.kt` | same annotation trio `: TempFileProvider` |
| `platform/TempFileProvider.kt:17` | `interface TempFileProvider` (the interface itself lives here, **not** in commonMain) |
| `resources/AndroidResourceWrapper.kt:11` | `class AndroidResourceWrapper(context) : ResourceWrapper` — plain, bound by the container |
| `images/ImageStorageImpl.kt:26` | `class ImageStorageImpl(context, ioDispatcher) : ImageStorage` — plain |
| `images/ImageStorageFactory.kt:25` | top-level `fun buildImageStorage(…): ImageStorage` — plain factory (not `@Provides`) |
| `time/RelativeTimeFormat.kt:11` | top-level `fun formatRelativeTime(…): String` (uses `android.text.format.DateUtils`) |
| `src/test/…/images/ImageStorageImplTest.kt` | Robolectric JUnit5 test |

`build.gradle.kts`: applies `convention.androidLibrary` **+ the Metro plugin** with
`metro { interop { includeJavax() } }` (`:2,7,10-14`). Deps: `api(project(":core:core"))`,
`implementation(libs.kermit)`, `implementation(libs.kotlinx.datetime)`. **No Hilt plugin** (none exists).

### What forces it to be a separate `com.android.library`? — [resolved]

- **Nothing android-library-only remains on disk.** `find core/core-android -name AndroidManifest.xml -o -path '*/res/*'`
  → **empty**. `core/core-android/src/main/` contains only `kotlin/`. `buildConfig=true` is set generically
  for every android module by `configureKotlinAndroid` (`KotlinAndroid.kt:61`), not a core-android-specific need.
- The **real** forcing reason is toolchain, per the convention-plugin KDoc: AGP 9 rejects legacy
  `com.android.library` + `kotlin-multiplatform`, and the Metro Gradle plugin is currently only wired onto
  `com.android.library` modules. `:core:core` (the KMP module) does **not** apply Metro.

### The iOS-no-leak invariant is preserved under fold-in — [resolved]

- `:core:core` targets `android()` + `iosSimulatorArm64()` (`core/core/build.gradle.kts:13,21`).
  `androidMain` is compiled **only** for the android target, never for `iosSimulatorArm64` — proven by
  layout: the dispatcher qualifiers exist as `expect` (commonMain) + **two different actuals** in
  `androidMain` (carries `@javax.inject.Qualifier`) and `iosMain` (plain), which only compiles because iOS
  takes `iosMain`, not `androidMain`.
- `AppScope`'s own KDoc (`AppScope.kt:14-17`) states the invariant: keep it **out of `commonMain`** because
  commonMain compiles to iOS. **Placing `AppScope` + the Android impls in `core/core/androidMain` (NOT
  commonMain) preserves it** — androidMain never enters the iOS binary.

### Consumers & symbol resolution — [resolved]

15 real modules depend on `:core:core-android` (`core/core` does **NOT** — its 3 build-script hits are
comment lines only; **no cycle**): `app/app`, `core/data/backup/{google-drive,scheduling,worker}`,
`core/data/{database,dataStore,exercise}`, `core/di`, `core/ui/{kit,mvi,test-utils}`,
`feature/{app-dialogs/impl,home,recovery,settings}`.
Because core-android **already reuses the identical `io.github.stslex.workeeper.core.core.*` package**,
repointing consumers at `:core:core` would resolve the **same FQCNs with zero import changes**. The
commonMain interfaces (`AppReinitializer`, `ResourceWrapper`, `PlatformInfoProvider`, `ImageStorage`)
already live in `:core:core`; only the Android impls + `AppScope` + the 2 `@BindingContainer`s would move
into `androidMain`.

### BLOCKING CHECK — [resolved]: unproven in this repo

- **No KMP module in the repo applies the Metro plugin.** All 24 Metro-applying modules are
  `com.android.library`/`application`. The **only** `convention.kmpLibrary` module is `:core:core`, and it
  does **not** apply Metro. Every one of the ~205 Metro-annotation-bearing real-source files lives under
  `/src/main/` (186) or `/src/test/` (19) — **zero** under `androidMain`/`commonMain`/`iosMain`.
- Therefore *"the Metro compiler plugin processes a KMP module's `androidMain` source set for cross-module
  `@ContributesBinding`/`@BindingContainer` aggregation"* is an **UNVERIFIED assumption** — there is no
  compiling in-repo precedent to de-risk it. (Confirmed by the adversarial `metro-androidMain` verifier:
  intersection {KMP} ∩ {metro} = ∅.)

**AREA 1 VERDICT: blocked-on-metro-KMP-androidMain-processing.** Structurally the fold is trivial (no
manifest/res, identical package = zero import changes, no cycle, invariant preserved) and **unblocked on
every other axis**. It hinges solely on one throwaway spike: apply Metro to `:core:core`, move one impl
(e.g. `AndroidTempFileProvider`) into `androidMain`, confirm it aggregates into `AppGraph`.

---

## AREA 2 — dispatcher expect/actual necessity

### (a) Qualifier annotations — collapse **UNBLOCKED** post-Hilt — [resolved]

- All four qualifiers are a **three-way expect/actual split** in `:core:core`: bare
  `expect annotation class X()` (commonMain), a **javax-carrying** actual (androidMain), a **plain** actual
  (iosMain). Example: `commonMain/…/di/IODispatcher.kt:9 expect annotation class IODispatcher()`;
  `androidMain/…/di/IODispatcher.kt:3,7 import javax.inject.Qualifier … actual annotation class IODispatcher`;
  `iosMain/…/di/IODispatcher.kt:4 actual annotation class IODispatcher` (no javax).
- **The `@javax.inject.Qualifier` meta-annotation is the ONLY platform difference** between the android and
  ios actuals — everything else (package, name, `@Retention(BINARY)`) is identical. Post-Hilt, javax is the
  sole reason for the split.
- **Metro 1.3.2 ships a commonMain-safe `dev.zacsweers.metro.Qualifier`** — verified from the artifact:
  `runtime-jvm-1.3.2.jar` contains `dev/zacsweers/metro/Qualifier.class`, and the multiplatform
  `dev.zacsweers.metro:runtime` `.module` publishes both a `metadataApiElements → common` variant and an
  `iosSimulatorArm64ApiElements-published` variant. So the 4 expect/actual pairs **can collapse to a single
  plain commonMain `@dev.zacsweers.metro.Qualifier annotation class` each**, and `includeJavax()` (present
  in ~17 module `metro{}` blocks) can then be dropped.
- **One prerequisite:** `:core:core` currently applies **no Metro plugin** (only `convention.kmpLibrary`),
  and no module declares `implementation(metro.runtime)` (the compiler plugin auto-adds it). Using a
  Metro-native qualifier in commonMain **requires first applying the Metro plugin to `:core:core`** — which
  is the same unproven "Metro-on-KMP" toolchain question as Area 1. (Config change, not a code blocker; but
  it couples Area 2a to Area 1's spike.)

### (b) The IO dispatcher provider — **BLOCKED** by coroutines API topology, not DI — [version-gated]

- The dispatcher **values** are provided in **exactly one production place**:
  `core/core-android/…/di/DispatchersBindingContainer.kt:26-44` — a single `@BindingContainer` object with 4
  plain `@Provides` funcs (`Dispatchers.Main/.Main.immediate/.Default/.IO`). It is **not** expect/actual and
  lives in the Android-only `com.android.library`. **There is no iOS dispatcher provider anywhere** — iOS
  dispatcher provision is currently unimplemented (the app is android-only in practice).
- **Version-gated fact (coroutines = 1.11.0, `gradle/libs.versions.toml:21`):** `Dispatchers.IO` is **not a
  member of the common `Dispatchers` object**. Artifact-confirmed from the sources jar: the common
  `Dispatchers` (`commonMain/Dispatchers.common.kt`) exposes only `Default`/`Main`/`Unconfined`;
  `Dispatchers.IO` is declared as `public expect val Dispatchers.IO` in the **`concurrentMain`** intermediate
  set (`concurrentMain/Dispatchers.kt:46`), with a `nativeMain` actual (`nativeMain/Dispatchers.kt:49`).
- **`:core:core`'s source-set topology** is `commonMain + androidMain + iosMain + androidHostTest` — **no
  `concurrent`/jvm+native intermediate set** (targets `android()` + `iosSimulatorArm64()` only). So a
  provider placed in `commonMain` **could not reference `Dispatchers.IO`** for this version — it would need a
  `concurrentMain`-style intermediate set, or per-platform (android/ios) actuals — **for API-availability,
  not for Hilt.** The current android-only placement is correct on both counts.

> **Correction (from adversarial verification).** An earlier deep-dive claimed "Dispatchers.IO is JVM-only."
> That is **refuted** by the artifact: `Dispatchers.IO` is `concurrentMain` (JVM **+ Native**), so it *does*
> exist on iOS via a native actual. The precise, load-bearing statement is the one above: **it is not on the
> common `Dispatchers` object**, so a `commonMain` provider can't reach it — but a `concurrentMain`/`iosMain`
> provider can. The blocker is real; the "JVM-only" phrasing was not.

### (c) Consumers — [resolved]

- Real dispatcher-qualifier **application sites** (main source; worktrees/build/tests + the 12 definition
  files + comment lines excluded): **74 sites across 57 files** — `@DefaultDispatcher` 34, `@IODispatcher`
  29, `@MainImmediateDispatcher` 10, `@MainDispatcher` **1**. (Same order of magnitude as the PR's "~44",
  which likely counted only ctor-param injection sites, excluding the graph-accessor declarations.)
- **`@MainDispatcher` has ZERO real consumers** — its only non-definition occurrence is its own `@Provides`.
  It is provided but never injected; a candidate to drop during any collapse.
- If the qualifier collapses to a **single commonMain annotation with the same package + name**
  (`io.github.stslex.workeeper.core.core.di.<Qualifier>`), **consumer imports do not change** — every
  consumer already imports from that package (the expect declaration's).

**AREA 2 VERDICT:** **(a) annotation collapse = UNBLOCKED** post-Hilt (javax is the sole platform
difference; Metro 1.3.2 has a commonMain-safe `@Qualifier`), gated only on the shared "apply Metro to
`:core:core`" prerequisite. **(b) IO provider common-vs-platform = BLOCKED** by the coroutines-1.11.0 API
topology (`Dispatchers.IO` not on the common object; no intermediate concurrent set), **independent of the
DI framework** — the IO provider must stay in an android/JVM (or a future concurrent/ios) source set
regardless of the qualifier collapse.

---

## AREA 3 — `:core:di` `api()` leakage

### Full dependency set — [resolved]

`core/di/build.gradle.kts:16-25` declares **exactly 8 deps, all `api(project(...))`**, no
implementation/compileOnly/external-lib: `core:core`, `core:core-android`, `core:ui:mvi`,
`core:ui:navigation`, `core:data:exercise`, `core:data:backup:api`, `core:data:database`,
`core:data:dataStore`. The module has only 3 source files (`AppGraphContract`, `AppGraphContractAccessor`,
`AppGraphContractHolder`) and **zero `dev.zacsweers.metro` references** — it is a plain interface seam.

### Every `api` edge is load-bearing — [resolved]

Each module contributes ≥1 **type** to the public signature of `AppGraphContract` (a `val` return type or a
qualifier annotation), so there is **no signature-orphaned dependency** and **no clean demotion candidate**:

| `api` module | Justifying public type(s) in `AppGraphContract` |
|---|---|
| `core:core` | `ImageStorage`, `PlatformInfoProvider`, `AppReinitializer`, `ResourceWrapper` **+** the `@Default/@IO/@MainImmediate` dispatcher qualifiers (`expect annotation class` in commonMain — **owned by `:core:core`, not core-android**) |
| `core:core-android` | **exactly one type: `TempFileProvider`** (`AppGraphContract.kt:74`) |
| `core:ui:mvi` | `AnalyticsHolder`, `LoggerHolder`, `StoreDispatchers` |
| `core:ui:navigation` | `Navigator` |
| `core:data:exercise` | 8 repositories + `SessionConflictResolver` |
| `core:data:backup:api` | `BackupAuth`, `BackupStorage`, `SnapshotExportRunner`, `RestoreStateRepository`, `AutoBackupController`, `BackupPreferencesRepository`, `BackupNotificationHelper`, `RecoveryDiagnosticsExporter` |
| `core:data:database` | `DatabaseSnapshotProvider`, `LiveDatabaseLocator` |
| `core:data:dataStore` | `CommonDataStore` |

- **Refines a task premise:** the dispatcher qualifiers `AppGraphContract` imports come from **`:core:core`**
  (commonMain `expect` + actuals), **not `:core:core-android`**. And `api(core:core-android)` is **not**
  orphaned — it is justified by exactly one type, `TempFileProvider`.
- **Fragility flag:** `TempFileProvider`'s own KDoc (`TempFileProvider.kt:11-15`) says the interface is
  *"expected to be **removed**, not reshaped"* once temp-file orchestration moves to the data layer. When
  that lands, `api(core:core-android)` becomes the **first genuine `implementation`-demotion candidate** —
  but at HEAD it is load-bearing.

### Transitive-leak map & blast radius — [resolved]

- **16 modules depend on `:core:di`.** Only `app/app` and `feature/settings` redeclare every module they
  need; the other **14 rely on `:core:di` `api()` transitivity** for 2–5 of the 8 modules they never declare
  themselves. Widest gap: `feature/recovery` and `core:data:backup:worker` reference `AppGraphContract` but
  touch **none** of `core:ui:mvi`'s types, yet receive them transitively.
- Consumers name by `import` only `core:ui:mvi` (`Feature`/`StoreProcessor`/`rememberMetroStoreProcessor`) +
  `core:ui:navigation` (`Screen`) types; every repository/holder/dispatcher is read as `graph.xxx`
  **without importing the type** (inferred from the `AppGraphContract` member signature). This is why the
  transitive leak is silent — and why an `api → implementation` demotion would break compiles through the
  interface-member-resolution rule, not through named imports, forcing **every `AppGraphContract` consumer**
  to redeclare every module the interface names.

> **Open sub-question not settled (read-only):** whether Kotlin 2.4.10 requires **all** `AppGraphContract`
> member types resolvable on a consumer's compile classpath when it references the interface + a subset of
> members. This determines whether `api` is *strictly* mandatory or whether a partial consumer could compile
> with a referenced-members-only subset. Settling it needs a real `./gradlew compile` demotion experiment.

**AREA 3 VERDICT:** **No `api` edge is safely blanket-demotable** — all 8 are load-bearing via the
`AppGraphContract` public surface, and any demotion is repo-wide (14 consumers would each need new explicit
declarations). The architecturally clean lever is **not** demotion but **splitting `AppGraphContract` into
narrower per-consumer interfaces** (e.g. a `WorkerGraphContract` exposing only backup/db accessors, a
`FeatureGraphContract` exposing mvi/navigation/repo accessors), each api'd from a smaller `di` sub-module so
each consumer sees only the types it uses. This couples directly to Area 4 (§CROSS-CUTTING).

---

## AREA 4 — `AppGraphContract` / `AppGraph` flat surface vs Metro subgraphs

### Current mechanism — FLAT CONTRACT + BOUND-INSTANCE HANDOFF — [resolved]

- **`AppGraphContract` (core:di)** is a **plain, un-annotated** interface enumerating **32 accessor `val`s**
  (3 qualifier-annotated dispatchers among them). It has **no Metro annotation**
  (`AppGraphContract.kt:54 interface AppGraphContract {`).
- **`AppGraph` (app/app/di)** is the **single** `@DependencyGraph(scope = AppScope::class)` and it
  `: AppGraphContract`. It **re-declares all 32 contract accessors as `override val`** and adds **13**
  feature-tier `val`s (`numUiUtils`, `navigatorEventBus`, `activityHolder(+Producer)`, the 4 `appDialog*`,
  `accountDataStore`, `statsRepository`, the 3 recovery-cluster nodes) + 1 `@Provides` → **45 accessors
  total**. Construction has **3 `create()` roots** (`applicationContext`, `appDatabase`, `imageStorage`);
  everything else derives.
- **There are NO Metro child/extension graphs.** `@GraphExtension`/`@ContributesGraphExtension` appear
  **nowhere** in real source — the single grep hit is a **comment** in `ArchiveGraph.kt:28` explaining why
  archive deliberately has none.
- The **15 per-feature `@DependencyGraph`s** (`{all-exercises, all-trainings, archive, exercise-chart,
  exercise, home, image-viewer, live-workout, past-session, plan-editor, settings, single-training,
  app-dialogs/impl}` + `AppGraph`) are **standalone graphs** in their own feature scopes. Each acquires
  app-scoped deps by:
  1. reading the flat contract via `context.appGraphContract()`, then
  2. passing each accessor **positionally as a `@Provides` bound instance** into
     `createGraphFactory<XGraph.Factory>().create(...)`.

  Representative (`feature/settings/…/di/SettingsFeature.kt:39-60`): `val graph = context.appGraphContract()`
  → `createGraphFactory<SettingsGraph.Factory>().create(navigator = graph.navigator, … ioDispatcher =
  graph.ioDispatcher, context = …)`. This is mechanism **(iii) accessor read + (ii) bound-instance handoff**
  — **not** (i) Hilt EntryPoints, **not** (iv) a Metro child/extension graph.

### Aggregation is already done; only the cross-graph handoff forces the contract — [resolved]

- App-scope **bindings are already contribution-aggregated**: **~37 `@ContributesBinding(AppScope)` annotation
  applications across ~35 impl files** + 2 `@BindingContainer @ContributesTo(AppScope)` containers, all
  auto-aggregated by `@DependencyGraph(AppScope)`. The ownership/binding half is pure contribution.
- **What still forces the flat CONTRACT surface:** a binding contributed to `AppScope` is only visible
  **inside `AppGraph`**. Because feature graphs do **not extend** `AppGraph` (no `@GraphExtension`), the only
  way to hand an app binding into a sibling feature graph is to (a) expose it as an accessor `val` on
  `AppGraph`/`AppGraphContract`, then (b) read it via `appGraphContract()` and re-list it as a `create()`
  bound instance. The **32 duplicated `override val`s + the per-feature `create()` bound-instance lists** are
  exactly this cross-graph handoff workaround.

### Metro 1.3.2 capability audit (gates the redesign) — [resolved from the artifact]

- **The premise "Metro 1.1.1 is PINNED" is stale.** `gradle/libs.versions.toml:78 metro = "1.3.2"`. (The
  "1.1.1" string survives only in historical KDoc, e.g. `AppScope.kt:7`.)
- **Metro 1.3.2 SHIPS graph-extension capability**, verified by inspecting the jar on the classpath
  (`~/.gradle/…/dev.zacsweers.metro/runtime-jvm/1.3.2/…/runtime-jvm-1.3.2.jar`):
  - `unzip -l` lists `dev/zacsweers/metro/GraphExtension.class` **and** `GraphExtension$Factory.class`
    (alongside `DependencyGraph.class` / `DependencyGraph$Factory.class`).
  - The **sources jar** (`GraphExtension.kt`) documents: *"graph extensions extend a parent graph … and
    contain a superset of bindings that includes both the parent graph(s) as well as their own"* and *"Graph
    extensions … implicitly inherit their parents' scopes."* Its members are
    `scope / additionalScopes / excludes / bindingContainers`.
  - Cross-module contribution is via **`@GraphExtension` + `@ContributesTo(AppScope)` on the extension's
    `@GraphExtension.Factory`** (the "Contributing Graph Extensions" section) — there is **no separate
    `@ContributesGraphExtension` annotation** in 1.3.2 (it folded into `@GraphExtension` + `@ContributesTo`),
    and **no `isExtendable` flag** on `@DependencyGraph`.
- **Consequence:** a feature `@DependencyGraph` **can** become a `@GraphExtension` of `AppGraph`, inheriting
  all `AppScope` bindings **without re-declaring them** — which means the flat `AppGraphContract` accessor
  surface (and the per-feature `create()` bound-instance re-listing) **could be removed** in favor of
  extension aggregation. **The capability exists in the artifact; the repo simply does not use it yet.**

### Blast radius — [resolved]

Production readers of the app-scope accessor surface: **13 via `appGraphContract()`** (12 `*Feature.kt` +
`RecoveryActivity.kt:69,71` + `MetroWorkerFactory.kt:26`), and **3 via the internal `.appGraph`**
(`App.kt:70`, `MainActivity.kt:23-25`, `BaseApplication.kt` several). Under an extension model, each
`*Feature.kt` `create(...)` bound-instance list and the 32 duplicated `override val`s collapse into a
`@GraphExtension(FeatureScope)` that inherits `AppScope` bindings.

**AREA 4 VERDICT: UNBLOCKED on capability** — removing the flat contract in favor of true parent/child
extension graphs is a **design choice, not a capability gap** in Metro 1.3.2. What remains open is design +
a live extension-build spike (see decisions below); nothing in the *toolchain* blocks it.

---

## CROSS-CUTTING

### Interdependency matrix

| ↓ affects → | Precon | Area 1 (fold core-android) | Area 2 (dispatchers) | Area 3 (`:core:di` api) | Area 4 (flat contract) |
|---|---|---|---|---|---|
| **Precon (Hilt gone)** | — | **gates** (enables) | **gates** (enables) | independent | independent |
| **Area 1** | — | — | **shares blocker**: both need "Metro processes KMP `androidMain`" (Area 2a's commonMain `@Qualifier` also needs Metro on `:core:core`) | Area 1 moving `TempFileProvider` into `:core:core/androidMain` changes which module the `api(core:core-android)` edge points to | `AppScope` placement is common to both: fold must keep `AppScope` in `androidMain`, and a §4 redesign must keep it out of commonMain |
| **Area 2** | — | (see Area 1) | — | Area 2a moving the qualifiers to a commonMain `@Qualifier` changes 3 `AppGraphContract` accessor annotations, but keeps package/name → no consumer import change | qualifier annotations are 3 of the 32 flat-contract accessors |
| **Area 3** | — | — | — | — | **tightly coupled**: §4's extension redesign would **delete** `AppGraphContract` (or shrink it drastically), which **dissolves** Area 3's over-exposed `api` surface — the two are the same problem from opposite ends. Area 3's "split into per-consumer contracts" is the *incremental* version of §4's "replace with extension graphs". |
| **Area 4** | — | — | — | — | — |

Key couplings, concretely:
- **Areas 1 + 2a share one blocker** — both need the Metro plugin to work on the KMP module `:core:core`
  (Area 1: to host the `@BindingContainer`s in `androidMain`; Area 2a: to use `@dev.zacsweers.metro.Qualifier`
  in `commonMain`). A single spike de-risks both.
- **Area 4 ⊇ Area 3.** A full extension-graph redesign removes the flat `AppGraphContract`, which is the
  *source* of Area 3's `api` over-exposure. If Area 4 proceeds, Area 3 is largely moot; if only Area 3
  proceeds, splitting the contract is the incremental step toward Area 4.
- **`AppScope` placement couples Areas 1 & 4** — both must keep `AppScope` in an Android-only source set
  (`core-android` today, `core/core/androidMain` after fold) and out of commonMain.

### Enabled-only-by-Hilt-being-gone vs independent

| Fix | Classification |
|---|---|
| Area 1 fold-in | **Enabled by Hilt gone** — the split existed because "a KMP module can't run the Hilt plugin"; with Hilt gone the only remaining reason is the (untested) Metro-on-KMP-androidMain question. |
| Area 2a qualifier collapse | **Enabled by Hilt gone** — the expect/actual split existed solely to carry `@javax.inject.Qualifier` for Hilt; post-Hilt a Metro-native `@Qualifier` collapses it. |
| Area 2b IO-provider placement | **Independent of Hilt** — blocked by the coroutines API, would be blocked with or without Hilt. |
| Area 3 api demotion / split | **Independent of Hilt** — it is a Gradle-configuration / interface-shape concern; the flat contract is a Metro-era artifact, not a Hilt one. |
| Area 4 extension graphs | **Independent of Hilt** — enabled by **Metro 1.3.2's `@GraphExtension`**, not by Hilt's removal (the flat contract is a Metro-design choice). |

### Irreversible-boundary / CI flags

- **None of the four cleanups touches the irreversible boundary.** No schema/DB change (the Room schema and
  migrations are untouched), no restore/backup behavior change (Area 3/4 reshape *how* backup types are
  *exposed* through DI, never the backup logic), and all work targets `feature/metro-batch`, not `dev`/`master`.
- **The pre-existing `[Registered]` CI/lint failure is decoupled — [resolved].** `[Registered]` is an
  **Android Lint** built-in false-positive (`@SuppressLint("Registered")` on the flavor `Application` classes
  `app/dev/…/DevMobileApp.kt:9` and `app/store/…/StoreMobileApp.kt:10`), triggered by the abstract
  `BaseApplication` + `tools:replace` manifest override. It is scoped to the flavor `Application` classes +
  `BaseApplication`/`TestApplication` — **none of which any of the four cleanups modify.** So the four fixes
  are independent of that CI failure. *(Coupling confirmed structurally; a live CI run was not executed.)*

### Premise-contradiction ledger (§7)

Every place current source contradicts a stated premise, with evidence:

1. **Metro version.** Premise (Area 4): *"METRO 1.1.1 … version is PINNED."* → **`gradle/libs.versions.toml:78 metro = "1.3.2"`.** The only "1.1.1" in source is historical KDoc (`AppScope.kt:7`). This is decisive for Area 4: 1.3.2 has `@GraphExtension`.
2. **`:core:core-android` build KDoc.** Premise implied by its own KDoc: *"hosts every Hilt `@Module` (CoreModule / PlatformModule / ImageStorageModule)"* and *"its `@InstallIn(SingletonComponent)` modules aggregate into the single app Dagger graph"* (`core-android/build.gradle.kts:6,16-25`). → **No Hilt exists.** The actual files are Metro `@BindingContainer @ContributesTo(AppScope)` (`DispatchersBindingContainer.kt:22`, `ResourceWrapperBindingContainer.kt:24`) + `@ContributesBinding` impls; no `CoreModule`/`PlatformModule`/`ImageStorageModule` file exists in real source (only in the stale worktrees).
3. **Dispatcher-consumer count.** Premise (Area 2): *"~44 consumers."* → **74 application sites across 57 files** (`@DefaultDispatcher 34 / @IODispatcher 29 / @MainImmediateDispatcher 10 / @MainDispatcher 1`). Same order of magnitude; the precise figure differs (the "~44" likely excludes graph-accessor declarations).
4. **IO-provider framing.** Premise (Area 2): the split is a Hilt/DI concern to be collapsed once Hilt is gone. → The IO **provider** is a single non-expect/actual `@Provides` in Android-only `core-android`; the only expect/actual is the **qualifier annotation**, split for `@javax.inject.Qualifier`. The IO **placement** blocker is the **coroutines API** (`Dispatchers.IO` not on the common object), **not** DI.
5. **Convention-plugin KDoc.** `KmpLibraryConventionPlugin.kt:13-16` says `configureKotlinAndroid` *"force-applies Hilt to every Android module."* → `KotlinAndroid.kt:46-48` applies only `robolectric-junit5`; `:97-101` adds bare `javax-inject`. **No Hilt plugin is applied anywhere.**
6. **`core:di` build KDoc.** `core/di/build.gradle.kts:8` describes the seam as *"replacing today's `EntryPointAccessors.fromApplication(..., *HiltEntryPoint)`"* — describes the **old** state; current consumers are fully Metro.
7. **`AppGraphContractHolder` KDoc** (`core/di/…/AppGraphContractHolder.kt:14`): *"Add-only (P-CONTRACT): no consumer calls it yet."* → **Stale.** `appGraphContract()` is called by 13 production readers and `BaseApplication.kt:68` implements the holder.

### Adversarial-verification reconciliation

Four riskiest claims were independently re-checked against source; corrections folded into the areas above:
- **`hilt-gone` → CONFIRMED.** Hilt gone from source + build; exactly one `javax.inject:1` dep survives, used by the 4 dispatcher-qualifier actuals. (Runtime dagger-core caveat is captured in the Precondition.)
- **`metro-androidMain` → CONFIRMED.** {KMP} ∩ {metro-applying} = ∅. Area 1's blocker stands.
- **`io-needs-actual` → PARTIAL / corrected.** The "Dispatchers.IO is JVM-only" phrasing was **refuted** (it is `concurrentMain` = JVM+Native). The load-bearing fact — "not on the common `Dispatchers` object, so a commonMain provider can't reach it" — **holds**. Area 2(b) above uses the corrected statement.
- **`coredi-no-metro` → CONFIRMED.** `core:di` has zero Metro references; the "metro dep, if any, is safe to demote" parenthetical is vacuous — there is no metro dep in `core:di` to demote.

---

## Per-area final line + maintainer decisions

### Area 1 — **BLOCKED-on: "Metro compiler processes a KMP `androidMain` source set"**
Everything else is unblocked (no manifest/res, identical package = zero import changes, no cycle, iOS-no-leak
invariant preserved). **Maintainer decisions:**
- Run a throwaway spike: apply Metro to `:core:core`, move one impl (e.g. `AndroidTempFileProvider`) into
  `androidMain`, confirm it aggregates into `AppGraph` cross-module. *(De-risks Area 2a too.)*
- If green: decide the mechanical placement (all Android impls + `AppScope` + the 2 `@BindingContainer`s →
  `core/core/androidMain`; interfaces stay in `commonMain`; `ImageStorageImplTest` → `androidHostTest`),
  and whether to add `metro { interop { includeJavax() } }` to `:core:core` (needed until Area 2a lands).
- Decide: repoint the 15 consumers `:core:core-android → :core:core` directly (viable with zero source
  edits since packages are identical), or keep a temporary shim.

### Area 2 — **(a) UNBLOCKED; (b) BLOCKED-on: coroutines API topology (independent of DI)**
**Maintainer decisions:**
- (a) Whether `:core:core` should take the Metro plugin (its build KDoc currently declares it deliberately
  "pure Kotlin, no DI plugin") to allow a commonMain `@dev.zacsweers.metro.Qualifier` — same spike as Area 1.
- (a) Whether to keep four qualifiers or drop `@MainDispatcher` (zero real consumers) during the collapse.
- (b) Whether an iOS dispatcher story is even in scope now (currently unimplemented; android-only IO provider
  is fine). If iOS is ever wired, an `iosMain`/`concurrentMain` provider must be authored — a **separate**
  work item from the qualifier collapse.

### Area 3 — **No clean demotion; the lever is contract-splitting**
All 8 `api` edges are load-bearing; blast radius of any `api→implementation` demotion is repo-wide (14
consumers). **Maintainer decisions:**
- Decide whether to reduce the transitive `api` surface by **splitting `AppGraphContract`** into narrower
  per-consumer interfaces (e.g. `WorkerGraphContract`, `FeatureGraphContract`) rather than demoting edges —
  and whether that is worth doing *independently* or folded into the Area 4 redesign (which would remove the
  contract entirely).
- If a demotion is ever attempted, first settle the compiler question with a real `./gradlew compile`: does
  Kotlin 2.4.10 require **all** `AppGraphContract` member types on a partial consumer's classpath?
- Track `TempFileProvider`'s documented removal as the first genuine `implementation`-demotion trigger for
  `api(core:core-android)`.

### Area 4 — **UNBLOCKED on capability (design choice, not a capability gap)**
Metro 1.3.2 ships `@GraphExtension`; the flat `AppGraphContract` accessor surface (32 duplicated
`override val`s + per-feature `create()` bound-instance lists) **can** be replaced by feature graphs that
extend `AppGraph`. **Maintainer decisions:**
- Whether to convert the 15 feature `@DependencyGraph`s into `@GraphExtension`s of `AppGraph` (inheriting
  `AppScope` bindings, deleting the flat contract) **or** keep the current flat create()-factory adoption.
- If extension graphs: whether cross-module contribution uses `@GraphExtension(FeatureScope) +
  @ContributesTo(AppScope)` on each factory (so the extension is generated in `:app`) — this needs a
  per-feature module-dependency check not performed here.
- Whether the 3 `create()` roots (`applicationContext`, `appDatabase`, `imageStorage`) remain factory
  bound-instances or move under the extension-graph parent scope.
- Run one live extension-build spike (convert a single feature graph to `@GraphExtension`) — capability
  presence in the jar is proven, but a live extension compile against the real `AppScope` binding set was not
  attempted in this read-only pass.

---

*Report produced by a read-only verification-first discovery pass (11 subagents: 5 area deep-dives + a
Metro-1.3.2 classpath capability probe + 4 adversarial verifiers, cross-checked by the orchestrator against
primary source). No source, commits, or git state were modified.*
