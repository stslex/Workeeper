# KMP PR-R — `StringResolver`: Compose resources outside composition

**Status:** SPECIFIED — implemented by the prompt that lands this document (maintainer GO, 2026-10-12).

**Discovery baseline:** dev @ 7007cfe8be42e05bf6d352c7b34fdc542468bc1a (2026-10-12, merge of #327).

**First consumer:** Phase 7.10, `feature:all-exercises`. This PR adds no consumer.

---

## 0. Authority and stable rules

### 0.1 Authority order

1. live origin/dev at the implementation base;
2. AGENTS.md, CLAUDE.md, documentation/architecture.md, testing.md, ci-cd.md, lint-rules.md;
3. this document;
4. the checked-in source, resources, tests and CI oracles at the implementation base.

### 0.2 Stable rules

- Every load-bearing Gradle invocation runs after `./gradlew --stop` with
  `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain` and quotes its
  `N actionable tasks: N executed` line. detekt and tests run as separate invocations.
- Only JUnit XML written after the run's recorded start is evidence. Every gate reports its input
  count; a walk over zero items is a failure, not a pass.
- Commits are signed and GitHub-verified; the only trailer is `Co-Authored-By`; no session
  identifier appears in a commit, the PR or a document. The implementer never merges.
- No suppression of any kind. No golden is recorded and no PNG blob changes. No catalog
  (`strings.xml`), build file or version catalog changes.

## 1. Why

Shared (KMP) modules keep their copy in Compose Resources. Composables read it with
`stringResource` / `pluralStringResource`. Code outside composition — handlers and mappers — has
only Compose Multiplatform's top-level suspend functions `getString` and `getPluralString`:

- they are not injectable, so a handler or mapper test cannot replace them;
- on Android they read the system environment — `Locale.getDefault()` for the language and
  `Resources.getSystem().configuration` for theme and density (CMP 1.11.1,
  `ResourceEnvironment.android.kt`) — so the host tests that read them today run under Robolectric
  or layoutlib (for example `CommonHandlerComposeResourceTest` / `CommonHandlerComposeResourceRuTest`,
  `PermanentDeleteUndoLabelRuTest`, `ExerciseDeferredDeleteDbTest` and `LiveSetRowSemanticsTest`
  under Robolectric, `SetColumnAlignmentGateTest` on layoutlib), and a plain-JVM handler test
  avoids them;
- `ResourceWrapper` (core:core) addresses Android `@StringRes` / `@PluralsRes` `Int` ids and cannot
  address a `StringResource`.

Phases 7.8 and 7.9 worked around the gap: copy moved to the graph or into a suspend mapper that no
host test reaches (kmp-phase-7-9-all-trainings-feature.md, Sections 5.2 and 5.3). The next target,
`feature:all-exercises`, cannot take that route: 14 of its 66 non-golden unit identities run through copy
resolution on a mocked `ResourceWrapper` (the 11 of `AllExercisesUiMapperTest` assert the text; 3
of `ClickHandlerTest` assert the bulk-archive event and the dialog that carries the text), and its
blocked-archive dialog carries ready text in `State`. This PR adds the missing seam: an
injectable interface over the same two CMP functions, faked in tests like `ResourceWrapper` is today.

## 2. Decisions (locked by the maintainer, 2026-10-12)

| Id | Decision |
| --- | --- |
| R1 | The seam is `StringResolver`, owned by `core:ui:kit` `commonMain`, package `io.github.stslex.workeeper.core.ui.kit.strings`. Kit already exposes `api(libs.cmp.components.resources)` and contributes to `AppScope`. The name avoids the substring `ResourceWrapper`, which the topology gate rejects in shared feature sources (the generic `FeatureEntry` check "must not read ResourceWrapper" and the plan-editor forbidden tokens). |
| R2 | The API mirrors CMP's non-composable functions: `getString(resource, vararg formatArgs)` and `getPluralString(resource, quantity, vararg formatArgs)`, both `suspend`. With no `formatArgs` the implementation calls CMP's no-argument overload (catalog text verbatim); otherwise the vararg overload. |
| R3 | One implementation, `ComposeStringResolver`: public, `@Inject`, `@SingleIn(AppScope::class)`, `@ContributesBinding(AppScope::class)` with the project `AppScope`. |
| R4 | No consumer in this PR. The six files that call CMP's top-level functions outside composition stay as they are — archive (`ArchivePagingHandler.kt`, `ArchiveGraph.kt`), all-trainings (`TrainingListItemMapper.kt`, `AllTrainingsGraph.kt`), exercise (`ClickHandler.kt`) and live-workout (`CommonHandler.kt`); a debt row records them (Section 9). |
| R5 | Plural delegation is compiled but not executed here: kit's catalog has no plural (54 strings, 0 plurals), and the plural owners (archive, all-trainings) are pinned file-by-file by the topology gate. Its first execution is 7.10's mapper identities (all-exercises owns 9 plurals); that is an exit obligation of 7.10. |
| R6 | Tests: kit Android host under Robolectric (EN and RU), kit Native (EN), one app-graph identity test, and the Native oracle pins the new kit tuple. No shared fake or fixtures module: feature tests write in-file fakes, as 7.9 did. |
| R7 | Documentation: Section 9's six documents state how shared modules read copy. |

Two consequences of R1 and R2, recorded so the first consumer is not surprised:

- The name contains `StringRes`, which is one of the topology gate's `FEATURE_PAYLOAD_TOKENS`.
  That check reads only domain files and each entry's declared `payload_files` (for all-trainings:
  `AllTrainingsStore.kt`, `TrainingListItemUi.kt`, `TagUiModel.kt`), where the resolver is forbidden
  anyway, so a hit there is a correct failure.
- `getString(` and `getPluralString(` are already `FEATURE_STATE_LAMBDA_SIDE_EFFECTS` tokens, so a
  resolver call inside an `updateState` / `updateStateImmediate` lambda in a shared feature entry's
  `mvi/handler/` files is caught by the existing gate without a new rule; the gate reads no other
  files for that check.

## 3. Bounded exit claim

The implementation may prove only this:

- `core:ui:kit` `commonMain` declares `StringResolver` and `ComposeStringResolver`; the module
  compiles for Android, iosSimulatorArm64 and iosArm64;
- on the Android host (Robolectric), the real resolver returns the exact EN and RU catalog text of
  Section 5.1 for a plain string, a one-argument string, a two-argument string in argument order,
  and a placeholder string read without arguments;
- on Native, the same four EN results;
- `AppGraph` binds `StringResolver` to `ComposeStringResolver`, one instance per graph;
- the Native oracle pins the new kit tuple;
- no production code reads the resolver; Android behaviour, every catalog and every PNG blob are
  unchanged;
- the documents of Section 9 describe how shared modules read copy.

Not claimed: plural resolution (R5); locale-list behaviour (Section 8).

## 4. Contract

### 4.1 Interface

Normative signatures; KDoc wording is free:

    package io.github.stslex.workeeper.core.ui.kit.strings

    import org.jetbrains.compose.resources.PluralStringResource
    import org.jetbrains.compose.resources.StringResource

    interface StringResolver {

        suspend fun getString(resource: StringResource, vararg formatArgs: Any): String

        suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String
    }

The KDoc states: it mirrors CMP's non-composable functions; it reads the system resource
environment, so it picks the primary locale only and formats arguments with `toString()`; only
positional placeholders (`%1$s`, `%1$d`) are filled (architecture.md → Localization); it is never
called inside a State lambda and never injected into domain code; tests fake it.

### 4.2 Implementation

    @ContributesBinding(AppScope::class)
    @SingleIn(AppScope::class)
    @Inject
    class ComposeStringResolver : StringResolver {

        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
            if (formatArgs.isEmpty()) {
                composeGetString(resource)
            } else {
                composeGetString(resource, *formatArgs)
            }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = if (formatArgs.isEmpty()) {
            composeGetPluralString(resource, quantity)
        } else {
            composeGetPluralString(resource, quantity, *formatArgs)
        }
    }

with `import org.jetbrains.compose.resources.getString as composeGetString` and
`import org.jetbrains.compose.resources.getPluralString as composeGetPluralString`, and
`AppScope` imported from `io.github.stslex.workeeper.core.core.di` (`ContributesBindingScopeRule`
rejects Metro's built-in `AppScope`).

Two GUARD comments are required in the file:

- the aliases are load-bearing: inside this class an unqualified `getString(resource, *formatArgs)`
  resolves to the member itself, because members win over imports, and recurses;
- the class stays public: an internal contribution does not aggregate into `AppGraph` (the same
  guard as `ResourceWrapperBindingContainer` and `ActivityHolderImpl`).

No dispatcher change. CMP loads a catalog from assets on its first read in a process and serves
later reads from its cache (kmp-phase-7-9-all-trainings-feature.md, Section 5.2); the caller's
threading is exactly what a direct CMP call has today.

### 4.3 App graph accessor

`app/app/.../di/AppGraph.kt` gains, directly after `watchKnownRevisions`:

    /**
     * Compose-resource strings for code outside composition (kmp-string-resolver.md). Its only
     * reader here is the graph identity test, `StringResolverIdentityTest`.
     */
    val stringResolver: StringResolver

and the matching import. `externalSetWrites` and `watchKnownRevisions` are the precedent for an
accessor whose only reader is a graph test.

## 5. Tests

### 5.1 Android host

`core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/ComposeStringResolverTest.kt`
holds two classes, the shape of `CommonHandlerComposeResourceTest.kt` (the Robolectric JUnit 5
extension forbids method-level `@Config`, so the Russian half is a second class in the same file):

- `ComposeStringResolverTest` — `@ExtendWith(RobolectricExtension::class)`, `@Config(sdk = [33])`,
  one method `resolvesTheEnglishCatalog`;
- `ComposeStringResolverRuTest` — `@ExtendWith(RobolectricExtension::class)`,
  `@Config(sdk = [33], qualifiers = "ru")`, one method `resolvesTheRussianCatalog`.

JUnit 5, no composition. Each method resolves the four rows inside one `runBlocking` and then
asserts them with `assertAll`, so every failing row is reported, not only the first:

| Call on `ComposeStringResolver()` | EN | RU |
| --- | --- | --- |
| `getString(Res.string.core_ui_kit_action_cancel)` | `Cancel` | `Отмена` |
| `getString(Res.string.core_ui_kit_discard_session_confirm_title_format, "Push Day")` | `Delete Push Day?` | `Удалить «Push Day»?` |
| `getString(Res.string.core_ui_kit_active_session_conflict_body_format, "Push Day", "12 min")` | `Push Day — 12 min` | `Push Day — 12 min` |
| `getString(Res.string.core_ui_kit_discard_session_confirm_title_format)` | `Delete %1$s?` | `Удалить «%1$s»?` |

The values are the catalog's (`values/strings.xml`, `values-ru/strings.xml`); the third row proves
argument order, the fourth the no-argument branch of R2.

### 5.2 Native

`core/ui/kit/src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/ComposeStringResolverIosTest.kt`:
kotlin.test, one method `resolvesTheEnglishCatalog` with a block body, `runBlocking` (precedent:
`IosKitSceneSmokeTest`), asserting the EN column of Section 5.1.

`assert_kmp_ios_smoke.py` gains, after the existing kit identity:

    native(
        "io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolverIosTest",
        "resolvesTheEnglishCatalog",
    ),

so the kit validator requires two tuples. Additional passing cases were already allowed; pinning
the tuple makes a deleted or renamed test a failure.

### 5.3 App graph

`app/app/src/test/kotlin/io/github/stslex/workeeper/di/StringResolverIdentityTest.kt`, built like
`WatchKnownRevisionsIdentityTest` (the same `createGraphFactory<AppGraph.Factory>().create(…)` call
with relaxed MockK roots). Exactly two methods:

- `` `app graph binds the Compose string resolver` `` —
  `assertInstanceOf(ComposeStringResolver::class.java, graph.stringResolver)`;
- `` `two reads give the one app-scoped resolver` `` — `assertSame` on two reads of one graph.

### 5.4 Names

Kit test names are camelCase without spaces (iosTest compiles for Native). App test names may use
backticks; app/app is JVM only.

## 6. Known-negative controls

Each: fresh GREEN → one mutation → the named verdict quoted from fresh output → byte-exact
restoration → `git status --porcelain` empty → fresh GREEN. Mutations go through
`documentation/mockups/mutation_harness.py` in one-shot mode (AGENTS.md → "Mutating the tree: use
the harness"), with `--no-configuration-cache --console=plain` passed inside `--task` (the harness
adds only `--rerun-tasks --no-build-cache`). The harness cannot run a Python oracle, so K6 uses the
AGENTS.md exception: a scratchpad copy restored with `cp`. The harness scores a compile failure as
`INVALID`, so K4's required verdict is `INVALID` with the compile error quoted; everywhere else a
compile-invalid mutation is a failed control, never a RED.

| Id | Mutation | Task | Required verdict |
| --- | --- | --- | --- |
| K1 | `ComposeStringResolver.getString` returns `""` without calling CMP | `:core:ui:kit:testAndroidHostTest --tests *ComposeStringResolver*`, then `:core:ui:kit:iosSimulatorArm64Test` | RED: both host methods fail on all four rows; Native `resolvesTheEnglishCatalog` fails |
| K2 | `getString` passes `formatArgs.reversedArray()` to CMP | the same two | RED: host fails on the two-argument row only, in both classes; Native fails |
| K3 | `getString` always calls the no-argument overload | `:core:ui:kit:testAndroidHostTest --tests *ComposeStringResolver*` | RED: the one- and two-argument rows fail in both classes |
| K4 | `ComposeStringResolver` becomes `internal` | `:app:app:compileDebugKotlin` | `INVALID`, with a compile error that names `ComposeStringResolver` or `StringResolver` quoted |
| K5 | `@SingleIn(AppScope::class)` removed from `ComposeStringResolver` | `:app:app:testDebugUnitTest --tests *StringResolverIdentityTest*` | RED: `two reads give the one app-scoped resolver` fails |
| K6 | the iOS test method renamed | the full nine-module Native command, then `assert_kmp_ios_smoke.py` | the oracle fails on the kit tuple only |

Observation O1, recorded and not scored: drop the empty-arguments branch of `getString` (always the
vararg overload), run the harness on `:core:ui:kit:testAndroidHostTest --tests
*ComposeStringResolver*` without `--expect`, and record the fourth row's result in both classes —
text or exception. It tells 7.10 whether CMP's vararg path with no arguments equals its
no-argument overload.

## 7. Gates

### 7.1 Entry

- Base = live origin/dev; it contains 7007cfe8b. The latest `Android CI/CD - Unified Build and
  Tests` push run on dev is green (record its id); if it is red, STOP.
- No Section 9 path changed since 7007cfe8b, except this document landing.
- Baseline, stable flags: `:core:ui:kit:testAndroidHostTest` (record the case count from fresh
  XML), `:app:app:testDebugUnitTest` (record the case count), the nine-module Native command from
  `android_build_unified.yml` with `--continue`, then `python3 .github/scripts/assert_kmp_ios_smoke.py`
  (kit verifies 1 identity).

### 7.2 Focused, per commit

After commit 1 — the contribution already aggregates into `AppGraph`, so the app graph is part of
this commit's proof:

    ./gradlew :core:ui:kit:testAndroidHostTest      # baseline + 2; both class/method names in fresh XML
    ./gradlew <nine-module iosSimulatorArm64Test> --continue
    python3 .github/scripts/assert_kmp_ios_smoke.py # kit verifies 2 identities
    ./gradlew <nine-module linkDebugTestIosArm64> --continue   # 9/9 test.kexe
    ./gradlew :app:app:testDebugUnitTest            # = baseline

After commit 2:

    ./gradlew :app:app:testDebugUnitTest            # baseline + 2; StringResolverIdentityTest 2/2 by name

detekt runs alone after each commit.

### 7.3 Repository gates, each in its own invocation

    ./gradlew assembleDebug lintDebug testDebugUnitTest --continue
    ./gradlew detekt --continue
    ./gradlew assembleDebugAndroidTest
    ./gradlew verifyPaparazziDebug
    ./gradlew :lint-rules:test
    python3 .github/scripts/assert_kmp_ui_source_topology.py
    python3 documentation/personal_data_gate.py -v

Device suites are not required: no runtime path reads the binding. The PR says so.

### 7.4 Diff

After commit 2: exactly the 7 code paths of Section 9 (the five new files, `AppGraph.kt`,
`assert_kmp_ios_smoke.py`). After commit 3: exactly the 14 paths. Never a `.png`, `strings.xml`,
`*.gradle.kts` or `gradle/libs.versions.toml` path.

## 8. Out of scope

- Moving the six files of R4 onto the resolver (a debt row records them).
- Date and time formatting: `ResourceWrapper.formatDayMonth`, `formatMediumDate` and
  `getAbbreviatedRelativeTime` stay where they are.
- A shared fake or a test-fixtures module.
- A lint rule for Compose resources in domain code; the topology gate already covers shared
  features.
- Locale-list behaviour or a per-app locale (a debt row records it; the remedy is a maintainer
  decision).
- The skills that describe `R` / `ResourceWrapper` (`add-feature`, `app-dialogs-pattern`,
  `compose-state-discipline`, `mvi-dialog-state`, `refactor-with-mvi-rules`); the first consumer
  updates what it uses.

## 9. Exact change boundary — 14 paths

New (5):

    core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/StringResolver.kt
    core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/ComposeStringResolver.kt
    core/ui/kit/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/ComposeStringResolverTest.kt
    core/ui/kit/src/iosTest/kotlin/io/github/stslex/workeeper/core/ui/kit/strings/ComposeStringResolverIosTest.kt
    app/app/src/test/kotlin/io/github/stslex/workeeper/di/StringResolverIdentityTest.kt

Modified (9):

    app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt
    .github/scripts/assert_kmp_ios_smoke.py
    documentation/architecture.md
    documentation/testing.md
    documentation/ci-cd.md
    documentation/tech-debt.md
    AGENTS.md
    CLAUDE.md
    documentation/feature-specs/kmp-string-resolver.md   (Status and Section 13 only)

The rule the documents state is keyed by the catalog a string lives in, not by the module kind:

- an Android `R` string: `stringResource(R.string.*)` in composition, `ResourceWrapper` outside it;
- a Compose-resource string (`Res.string.*` / `Res.plurals.*`, in any module, including kit strings
  read by Android-only features): `org.jetbrains.compose.resources.stringResource` /
  `pluralStringResource` in composition, an injected `StringResolver` outside it — never CMP's
  top-level `getString` / `getPluralString` (the six files of R4 predate the rule; debt row).

What each document gains:

- **architecture.md** — (a) the `core/ui/kit` bullet of the module list names `StringResolver`;
  (b) the dialog-state bullet "Display strings are pre-resolved in the handler via
  `ResourceWrapper`" adds `StringResolver` for Compose-resource strings; (c) both sentences of
  "State mutation discipline" that name `ResourceWrapper` add `StringResolver`; (d) the domain bullet
  "never injects `ResourceWrapper`" adds `StringResolver`; (e) Localization states the rule above,
  the shared-module layout (catalogs under `src/commonMain/composeResources/values{,-ru}/strings.xml`,
  a generated `Res` per module through `compose.resources { packageOfResClass = … }`), and the
  primary-locale property with a pointer to its debt row; "Adding a new feature" gains the
  shared-module variant of its four steps.
- **AGENTS.md** and **CLAUDE.md** — the domain-layer bullet states the rule above in one sentence
  per catalog; the domain layer injects neither `ResourceWrapper` nor `StringResolver`.
- **testing.md** — (a) "`app:app` graph and runtime host tests" gains a bullet: `AppGraph.stringResolver`
  has no production reader and exists for `StringResolverIdentityTest`; (b) "Deliberate coverage
  boundaries" gains "Shared string resolver": host EN and RU through Robolectric (`qualifiers = "ru"`
  on a second class, as `CommonHandlerComposeResourceTest.kt` does), Native EN, plural delegation
  unexecuted until 7.10, feature tests fake the interface in-file by switching on the generated
  resource objects.
- **ci-cd.md** — the "KMP iOS kit smoke job" section: the `:core:ui:kit` parenthetical becomes
  "resource-backed Compose scene and the `StringResolver` catalog reads", and the validator
  paragraph names the kit validator's two tuples.
- **tech-debt.md** — (a) the all-trainings UI Mapping Boundary row's last sentence: the common
  resolver now exists; paying the row down also changes `all_trainings_copy_contract`, which pins
  the semantic event; (b) a new section "Shared (KMP) string resources", directly after "UI Mapping
  Boundary Debt", with two rows: 🟢 the six files of R4, which call CMP's top-level functions
  outside composition, predate `StringResolver`; 🟡 Compose resources choose by the primary locale only and format arguments
  with `toString()` (kmp-phase-7-9-all-trainings-feature.md, Section 5.7), which now holds for
  every Compose-resource string and for `StringResolver`; the remedy (a locale-list-aware
  environment or a per-app locale) is a maintainer decision. Neither row adds a
  `TODO(tech-debt)` marker in code in this PR, because the files are outside Section 9; markers
  come with the pay-down.

## 10. STOP conditions

Stop, report what was measured, and widen nothing, if:

- the base lacks 7007cfe8b, the latest dev push run is red, or a Section 9 path drifted;
- the Robolectric read fails for an environment reason (a missing resource, an uninitialised
  Android context, a hang): do not move the test, change the runner or add configuration;
- `resolvesTheRussianCatalog` returns English text: do not change the qualifier mechanism, reorder
  tests or work around a cache;
- the Native read differs from the EN column;
- Metro rejects the `commonMain` contribution on any target, or `AppGraph` needs more than the
  accessor;
- detekt reports anything, or any suppression becomes necessary;
- a control of Section 6 does not produce its required verdict;
- a gate needs a build-file, catalog or version change.

## 11. Signed, bisect-green commit plan

1. **feat(kit): resolve Compose resources outside composition** — `StringResolver.kt`,
   `ComposeStringResolver.kt`, `ComposeStringResolverTest.kt`, `ComposeStringResolverIosTest.kt`,
   `assert_kmp_ios_smoke.py`.
2. **feat(app): pin the app-scoped StringResolver binding** — `AppGraph.kt`,
   `StringResolverIdentityTest.kt`.
3. **docs: document StringResolver and record its evidence** — the six documents of Section 9 and
   this document's Status and Section 13. The Status line becomes
   `**Status:** COMPLETE — implemented by the PR whose merge brought this line to dev.`, which is
   true on dev only once the maintainer merges.

Review fixes are new signed commits inside Section 9. Nothing is amended, squashed or reordered.

## 12. Exit criteria

Section 3 proven; Sections 6 and 7 evidenced; one PR into dev, CI green on its head, `@claude
review` requested and every finding classified (correct / already decided / wrong / new) before any
fix; the maintainer merges.

## 13. Implementation evidence

Filled by commit 3: base SHA; commits 1 and 2 with the signed-commits skill's local `gpgsig`
check (not `git verify-commit`, which needs an allowed-signers file), and commit 3 as "this commit"
(as kmp-phase-7-9 Section 19.2 did); the gate table with executed-task lines and
case counts; the control table with each verdict quoted; O1's result; deviations, if any. GitHub's
signature verification, the CI run ids and the review classification go in the PR body and the
report, because they cannot exist before this commit does.
