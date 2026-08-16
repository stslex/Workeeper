# KMP Phase 3 — core:core-android collapses into core:core

Decision record for Phase 3 of the KMP/CMP migration (arc: 0 release ✅ · 1 Nav3 ✅ ·
2 conventions ✅ · **3 core collapse** · 4 app:common · 5 startup processor · 6 data
layer · 7 UI/CMP). Decisions here were locked before implementation; the PR implements
this spec.

## Entry gate — the Phase 2 verification debt (closed 2026-08-16)

PR #227 removed accompanist and appcompat from the catalog **and** from
`ComposeAndroid.kt`, which had been wiring both into all 21 compose modules. The
seven-gate battery assembled androidTest but never executed it. Before any Phase 3
work, both halves were verified at dev@a8bc127a5:

- **Instrumented oracle:** full `@Regression` suite on `nav_regression_api34`
  (API 34, arm64): **79 tests, 0 failures, 0 errors, 0 skipped.** Composition fully
  reconciled: app:app 35, core:data:database 28, feature:all-exercises 1
  (method-level `@Regression`; the class is `@Smoke`), plus 15 tests that ran through
  the annotation-filter bypass documented in
  [tech-debt.md → Instrumented annotation filter](../tech-debt.md). Zero failures
  matches the post-#223 pin (zero expected failures; the draft-wipe defect is pinned
  by a GREEN test — `editorDraftIsDiscardedByTheImageViewerRoundTrip` passed).
- **Source-level appcompat arrival paths** (source, not build intermediates):
  `Theme.Workeeper` parents are `android:Theme.Material.NoActionBar` /
  `android:Theme.Material.Light.NoActionBar` — platform themes, chain terminates in
  the framework. The only `android:theme` in source manifests is the application-level
  `Theme.Workeeper` (RecoveryActivity inherits it) and test-utils' platform theme.
  `MainActivity` and `RecoveryActivity` extend `ComponentActivity` (androidx.activity);
  `BaseApplication` extends `android.app.Application`. Zero appcompat/accompanist
  source references outside a documenting comment in `themes.xml`.

## Why the collapse

`core:core`'s build-file claim "pure-Kotlin KMP module" was already false: it has an
`androidMain` (Firebase holder actuals, dispatcher-qualifier actuals carrying
`@javax.inject.Qualifier`) and `androidHostTest`. The real split line was Metro —
core:core deliberately did not apply the plugin, so binding implementations lived in
the sibling. P5 measured Metro aggregating from a KMP module's androidMain (54/54
graph identity tests, red direction proven), removing the blocking unknown.

The decisive argument: `actual` must live in the same Gradle module as its `expect`.
While core:core-android is a sibling, the platform seams can never use expect/actual —
they are locked into interface + DI binding while their iOS counterparts would use
expect/actual in `core:core/iosMain`. Same concept, two mechanisms, split by platform.

## Measured inventory (corrects the planning numbers)

- **Consumer edges: 3, not 5.** `implementation(project(":core:core-android"))` exists
  in exactly `app/app` (Metro aggregation + `buildImageStorage`), `feature/settings`
  (`TempFileProvider`), `feature/home` (`formatRelativeTime`). `feature/recovery` and
  `core/ui/test-utils` mention the module in **comments only** — both resolve
  Android impls through the app graph / fakes and carry no edge.
- **Seams: 6 files, not 5.** The five named seams plus `time/RelativeTimeFormat.kt`
  (`formatRelativeTime`), whose body is byte-identical to
  `AndroidResourceWrapper.getAbbreviatedRelativeTime` and whose sole consumer is
  `HomeUiMapper` — it is the entire reason for the feature:home edge.
- All three edges are `implementation`; no test-configuration edges exist. No module
  outside the three references any core-android class name in code (KDoc only).

## Seam decisions

| Seam | Decision | Why |
|---|---|---|
| `PlatformInfoProvider` | **expect/actual class** | Pure platform-value provider (version name/code, device model). The iOS actual is trivially implementable now (`NSBundle` info dictionary, `UIDevice.model`) and is included compile-verified; its KDoc keeps the Build.MODEL ≠ UIDevice.model semantics note. DI simplifies: the `@ContributesBinding` interface binding is replaced by injecting the class (`@SingleIn(AppScope)` + `@Inject` on the Android actual's constructor). |
| `AppReinitializer` | **expect/actual class** | Single platform primitive. Android actual keeps the launch-intent + `Runtime.getRuntime().exit(0)` restart verbatim. The iOS actual is a loud `TODO()` — Apple rejects `exit()`, so the likely iOS shape is an **in-process graph rebuild**; that redesign belongs to Phase 5 and is recorded in the expect KDoc, together with the reason it is not trivial: three `@SingleIn(AppScope)` classes (`BackupPreferencesRepositoryImpl`, `RestoreStateRepositoryImpl`, `AppDialogRepository`) bypass `DataStoreProvider`'s memoization and throw on a second graph — a throw swallowed by `AppCoroutineScopeImpl.launch`'s `.catch`, so the symptom is missing data, not a crash. |
| `ResourceWrapper` | **not converted** — interface stays in commonMain; `AndroidResourceWrapper` + its `@BindingContainer` move to androidMain | The API is `Int`-resource-id-shaped (Android `R.*` semantics). Phase 7's CMP resource model (`Res.*`) reshapes this API itself; an iOS actual written today could not implement `getString(id: Int)` meaningfully. Converting now would manufacture a dead actual and force a second conversion in Phase 7. |
| `ImageStorage` | **not converted** — interface stays in commonMain; `ImageStorageImpl` + `buildImageStorage` move to androidMain | The interface is the app graph's `create()` bound-instance root, the permanent test-override seam (`MetroTestRule` swaps `FakeImageStorage`); that requires a fakeable type. The construction seam is platform-typed (`Context`), so an `expect` factory cannot share a signature. The recorded duplicate-binding hazard stands: adding a Metro binding for `ImageStorage` fails the dup-binding check against the `create()` root. |
| `TempFileProvider` | **not converted** — interface + impl + binding move to androidMain, Android-only | `java.io.File` does not exist on Kotlin/Native, and the interface's own KDoc slates it for **removal** (when temp-file orchestration moves to the data layer), explicitly not for reshaping into a neutral path abstraction. Converting would contradict a recorded decision. |
| `formatRelativeTime` | **deleted** | Byte-identical duplicate of `ResourceWrapper.getAbbreviatedRelativeTime` (same `DateUtils.getRelativeTimeSpanString` call, same flags). `HomeUiMapper` already receives a `ResourceWrapper` in every call site that also calls the free function — it switches to the interface method (note the argument-order swap: free fn is `(now, event)`, interface is `(timestamp, now)`). This dissolves the feature:home edge rationale entirely. |

Dispatcher qualifiers and Firebase holders are already expect/actual (annotation
classes / objects) and are untouched. `DispatchersBindingContainer` moves to
androidMain exactly as P5 proved — it stays androidMain (not commonMain) because the
qualifier annotations carry `@javax.inject.Qualifier` only in their Android actuals;
on iOS they are plain annotations, so a commonMain container would produce four
unqualified `CoroutineDispatcher` bindings. The iOS graph is Phase 5+ work.

## Mechanics

- `core/core/build.gradle.kts` gains `alias(libs.plugins.metro)` +
  `metro { interop { includeJavax() } }` (P5-proven) and androidMain
  `implementation(libs.androidx.core.ktx)` — the classic Android convention injected
  it into every module (`KotlinAndroid.kt`), which is how `FileProvider` /
  `@StringRes` resolved in core-android; the KMP convention does not, so the
  dependency becomes explicit.
- `ImageStorageImplTest` moves to `androidHostTest`. The KMP convention already
  provides JUnit 5 + platform launcher + extension autodetection +
  `isIncludeAndroidResources`; the module adds
  `"androidHostTestImplementation"(robolectric, robolectric-junit5-extension,
  androidx-test)` — the catalog comment on `robolectric-junit5-extension` names
  exactly this use. Side effect: the test now runs under
  `failOnNoDiscoveredTests = true` (KMP convention default) instead of the classic
  convention's `false`.
- Consumer imports do not change: core-android kept `io.github.stslex.workeeper.core.core.*`
  packages precisely so the collapse is import-transparent.
- `core:core-android` is deleted at the end; `settings.gradle.kts` include + comment
  removed; the three edges drop (all three consumers already hold a direct
  `implementation(project(":core:core"))`).

## Commit plan (each bisect-green, Android releasable)

1. Metro on core:core + `DispatchersBindingContainer` → androidMain (P5 replay).
2. `ResourceWrapperBindingContainer`, `AndroidResourceWrapper`, `ImageStorageImpl`,
   `buildImageStorage`, `TempFileProvider` + `AndroidTempFileProvider` → androidMain;
   `ImageStorageImplTest` → androidHostTest. core-android still compiles (it re-exports
   core:core via `api`), so consumers stay green through transitivity.
3. `PlatformInfoProvider` + `AppReinitializer` expect/actual conversion (impl classes
   dissolve into androidMain actuals; iosMain actuals added).
4. `formatRelativeTime` deleted; `HomeUiMapper` switches to the injected
   `ResourceWrapper`; feature:home edge dropped.
5. Remaining edges dropped (app:app, feature:settings); `core:core-android` deleted;
   settings.gradle.kts + stale comments cleaned.

## Gates and mutations

All gates run with `--rerun-tasks --no-build-cache --no-configuration-cache`, detekt
as a separate invocation, executed-task and input-file counts reported. The
aggregation gate is app:app's unit suite (the 54 DI identity tests + the rest, 72
`@Test` methods). Named mutations, applied and reverted, never committed:

- **M-A** (aggregation liveness): comment out `@ContributesTo(AppScope::class)` on
  `DispatchersBindingContainer` in its new androidMain home → app:app compile/identity
  tests must go red.
- **M-B** (iOS gate liveness): delete an iosMain `actual` → `:core:core:assemble`
  (iOS klib compile) must go red.

Predictions stated before measuring: Metro processing of `actual class` +
`@Inject` constructor is unproven by the probe battery (P5 covered a moved
`@BindingContainer` object, not an actual class). If it fails, the fallback that
preserves the expect/actual seam is a `@Provides` function in an androidMain binding
container constructing the actual. mockk final-class mocking (4 sites:
`mockk<PlatformInfoProvider>` ×3, `mockk<AppReinitializer>` ×1) is expected to work
via the inline agent already in the test bundle.

## Accepted loss (recorded decision, not a side effect)

The explicit `:core:core-android` edges currently signal "this module names an
Android-only type". That signal disappears: after the collapse, an Android-only type
in core:core androidMain is reachable from any module with a core:core edge, and
nothing in the build graph marks the platform-boundedness. This is temporary
bookkeeping — those types need iOS implementations anyway, and the edge dissolves by
construction as seams convert — but between now and then, misuse is caught only at
iOS compile time (a commonMain consumer cannot see androidMain symbols; an
Android-module consumer can see them without a declared marker edge).
