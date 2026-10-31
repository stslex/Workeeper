# KMP Phase 7.18 — shared Settings with deferred iOS backup

**Status:** IN PROGRESS — implementation authorized; exit evidence and review pending.

**Discovery base:** `dev` at `179e1c7df95e40912f0fb6c7eea8a3142b1c8b8a`
(Phase 7.17, PR [#347](https://github.com/stslex/Workeeper/pull/347)).
The inventories below come from that tree. They are source observations, not fresh build or
runtime evidence. No reference implementation was compiled while preparing this document.

**Outcome:** `:feature:settings` becomes the next shared feature entry. Android keeps its
working backup, authentication, scheduling, restore and undo behavior. iOS gets the general
Settings surface and an explicitly unavailable backup capability. Real iOS backup is planned
after the KMP migration; implementing it is not a prerequisite for this phase.

## 1. Authority and product decision

Read [AGENTS.md](../../AGENTS.md), then the
[Phase 2 probes](kmp-phase-2-probes.md),
[Phase 6 conventions](kmp-phase-6-data-layer.md),
[Phase 7.17](kmp-phase-7-17-app-dialogs-feature.md),
[architecture](../architecture.md), [testing](../testing.md),
[CI](../ci-cd.md), [lint rules](../lint-rules.md),
[backup](backup.md) and [recovery](backup-recovery.md).
Apply the relevant recipes under `.claude/skills/`, including signing, state discipline,
handler tests, UI tests and modal state. Existing shared feature source overrides older
Android-only scaffolding examples in recipes.

The maintainer's current decision is:

- A platform-specific unavailable implementation and capability flag are acceptable temporarily.
- Android backup must continue working throughout the migration.
- iOS backup will be implemented after the full KMP migration.
- This does not decide the feature set or release date of an iOS App Store v1.

This supersedes only the product-scope interpretation of Phase 7.17 Section 18 Q1.
It does not authorize a new backup backend, an iOS host, or changes to recovery semantics.
Sections 3–10 propose the implementation contract for review. Approval of this document
fixes that contract; an explicit implementation GO starts coding. The specification PR
contains this document only. The execution prompt is transient and is not repository content.

Keep Metro, Navigation 3, UI → Domain → Data, dedicated mappers, immutable UI models and
copy-only State updates. No new suppression, baseline entry, toolchain upgrade or dependency
version change is authorized. Do not merge PRs, enable auto-merge, release, or push to
`dev`/`master`; the maintainer merges.

## 2. Observed starting point and bounded exit

| Item | Discovery base | Required exit |
| --- | --- | --- |
| Settings module | Android `convention.composeLibrary` | Existing KMP Compose convention |
| Feature entry | Singleton reads `Context.appDeps<SettingsGraph.Factory>()` | Explicit factory through `AppRootDeps` |
| Production Kotlin | 51 files under `src/main/kotlin` | 39 existing files shared; 12 existing backup files Android; small platform adapters added |
| Backup dependency of Store | Concrete `BackupClickHandler` | Common `SettingsBackupHandler` contract |
| Backup initialization | Three observers always started | Started only when the selected handler is available |
| Public auth transport | Android `Intent?` and `IntentSender` | Typed opaque common handles; Android wrappers |
| Resource catalogs | EN and RU, 68 entries each | CMP catalogs, identical content except positional placeholders |
| Non-golden host tests | 10 suites, 120 source test methods | Preserve all 120 identities/assertions; add the specified coverage |
| Settings goldens | 6 methods × light/dark = 12 PNGs | Same 12 names and bytes at the new source-set location |
| Settings device tests | 2 `@Smoke` tests | Same behavior and identities under `androidDeviceTest` |
| App extension tests | 4 Settings identity tests | Preserve all four meanings, including Android Context identity |
| iOS smoke workflow | 17 module rows | 18 rows, adding Settings without weakening the existing 17 |
| Shared feature entries | 12 | 13, including Settings |

The phase proves a shared Settings feature and its Native production graph/rendering boundary.
It does not prove a complete iOS application, physical-device execution, real iOS backup, or
App Store readiness. Recovery and `app/common` remain separate subsequent migration work.
Do not turn this phase into their conversion.

## 3. Capability and Store contract

Introduce one internal common contract:

```kotlin
internal interface SettingsBackupHandler : Handler<SettingsStore.Action.Backup> {
    val isAvailable: Boolean
}
```

The selected implementation owns availability. It is constant for the lifetime of a feature
graph. There is no independently injected Boolean, platform-name branch, remote feature flag,
or general capability registry.

- **Android:** `BackupClickHandler` implements the contract and returns `true`. Preserve its
  feature scope and all existing action handling.
- **iOS:** a stateless `UnavailableSettingsBackupHandler` object returns `false`; its
  `invoke` returns `Unit` without work. Bind it through a Settings-scoped Metro provider.
  It has no repository, backup, scheduler, file, Context or Store dependency. A stateless
  object does not need an invented constructor dependency to satisfy handler-class rules.
- Use platform source-set Metro contributions/binding containers. Do not add another DI system,
  a service locator, or a manually constructed production Store. The common graph must not bind
  Android `BackupInteractorImpl`.

Add immutable `backupAvailable: Boolean` to `SettingsStore.State`.
Make it an explicit argument of `State.initial`, without a default that can silently enable
backup on iOS. Production takes it from the injected handler. Existing Android golden,
preview and test fixtures explicitly use `true`; unavailable fixtures explicitly use `false`.

`SettingsStoreImpl` injects the common handler and uses it for every `Action.Backup`.
Always initialize `Action.Paging.Init`. Only an available handler receives the existing
ordered trio `ObserveAuth`, `ObservePreferences`, `ObserveRestoreState`.
An unavailable handler receives none of those during normal initialization.

Directly dispatching any backup action to the unavailable implementation must remain inert:
no loading, event, success message, dialog, flow subscription, scheduled work or file access.
The existing 19 action variants remain accounted for:

`ObserveAuth`, `ObservePreferences`, `ObserveRestoreState`, `RequestRevertLastRestore`,
`SignIn`, `HandleAuthResult`, `RequestSignOut`, `ConfirmSignOut`,
`DismissSignOutConfirmation`, `CreateBackup`, `RequestRestore`, `ConfirmRestore`,
`DismissRestoreDialog`, `LoadBackupList`, `OpenFrequencyPicker`,
`DismissFrequencyPicker`, `SaveFrequency`, `UpdateFrequencyPickerSelection`,
`ToggleAiExport`.

Preserve the null-before-first-emission semantics of `startCardMode` and archived counts.
Keep the existing sealed `DialogState`; no independent dialog Booleans or dialog Events.
Compute values before State-update lambdas; the lambdas only copy State.

## 4. Shared UI and platform operations

### 4.1 Visibility

Move the Settings screen and its existing backup renderers to common source.

When `backupAvailable == false`, render no backup section, sign-in action, backup frequency
sheet, restore/sign-out confirmation, or restore progress/completion overlay. Guard every
backup surface, including a hostile/stale fixture containing a backup dialog or restore state.
Do not hide the start-card-mode sheet: it is an ordinary Settings action.

Theme, start-card mode, archive navigation, version information, back navigation, links and
haptics retain their behavior. Android remains visually identical. Add nonvisual test tags only
where needed to assert visibility; do not redesign or change copy, spacing or typography.

### 4.2 Auth transport

Replace Android types in the common Action/Event contract with two small public marker
interfaces, `SettingsAuthRequest` and `SettingsAuthResult`. They must be public because
the enclosing Store API is public. Do not expose `Any`, Android types, or data-layer auth types
through the shared UI contract.

Android-only internal wrappers carry the original `IntentSender` and nullable `Intent`.
A dedicated Android mapper performs wrapping/unwrapping; it preserves identity and
cancellation/null behavior. Keep `HandleAuthResult` and `AuthResolutionRequested` names.
Do not change the existing interpretation of Activity result codes.

Use a local common launcher interface and a composable `expect/actual` factory,
`rememberSettingsAuthLauncher(onResult)`:

- Android retains `StartIntentSenderForResult`, `IntentSenderRequest` and the existing
  callback-to-Action flow.
- iOS returns an inert launcher. Calling it neither launches anything nor invokes the callback,
  fabricates an auth result, or reports success.

Keep platform handles transient. Do not put launchers, Context, Activity or platform handles
in State, repositories, retained cross-platform graph nodes or persisted data.

### 4.3 External links and formatting

Use a Settings-local platform launcher for external links. Android keeps `ACTION_VIEW`,
`FLAG_ACTIVITY_NEW_TASK` and the existing best-effort failure behavior. iOS uses the available
Compose `LocalUriHandler`; general links must not become no-ops with backup.

Leave `BackupDateMapper`, `BackupPreferencesUiMapper` and `BackupUiMapper` on Android.
Preserve DateUtils, date style, locale/time-zone behavior, current-time semantics and
`Formatter.formatShortFileSize`. Replace Settings Android-resource access in
`BackupDateMapper` with injected `StringResolver`; propagate suspension only through
the existing coroutine call path. Do not invent an iOS backup formatter.

The graph forwards events/actions and delegates platform operations. New conversions belong
in dedicated mappers. Existing unrelated composition shaping is carried debt, not permission
for a localization or State redesign.

## 5. Source sets, dependencies and root factory

Use the established KMP Compose convention, Metro qualifier interop and private Compose
resource package `io.github.stslex.workeeper.feature.settings.resources`.
Use the current convention aliases and configurations; do not change root build logic or
toolchain pins.

The following 12 existing production files stay Android-only, retaining their package paths
under `src/androidMain/kotlin/io/github/stslex/workeeper/feature/settings/`:

| Subdirectory | Files |
| --- | --- |
| `domain/` | `BackupInteractor.kt`, `BackupInteractorImpl.kt` |
| `domain/mapper/` | `BackupDomainMapper.kt` |
| `domain/model/` | `AccountDomain.kt`, `BackupAuthDomain.kt`, `BackupSummaryDomain.kt`, `SignInOutcomeDomain.kt` |
| `domain/usecase/` | `RestoreLatestBackupUseCase.kt` |
| `mvi/handler/` | `BackupClickHandler.kt` |
| `mvi/mapper/` | `BackupDateMapper.kt`, `BackupPreferencesUiMapper.kt`, `BackupUiMapper.kt` |

Move the other 39 existing production Kotlin files to `commonMain`, changing only what the
contracts require: imports, resources, previews, factory wiring, capability and platform seams.
Keep the first eight files in the table byte-identical to the base; the handler/date mapper
need boundary adaptations, while the other two UI mappers should move byte-identically.
Any necessary additional semantic edit to these ten byte-preserved files requires a spec
amendment. New Android/iOS platform adapters and test fixtures remain local to Settings.

Dependency ownership:

- `commonMain`: `core:core`, `core:data:dataStore`, `core:data:exercise`,
  `core:ui:kit`, `core:ui:start-mode`, `core:ui:mvi`, `core:ui:navigation`.
- `androidMain`: existing `core:data:backup:api`, `core:data:database`,
  `feature:app-dialogs:api` edges and Android-only activity/result support as supplied
  by the existing conventions. No new external library is required.
- `commonTest`: Kotlin test and existing coroutine test support; handwritten fakes.
- `androidHostTest`: existing MockK/Robolectric/Paparazzi support, golden harness and
  Android UI test dependencies.
- `iosTest`: existing CMP test support and Settings-local fixtures.
- `androidDeviceTest`: existing Android test bundle, Compose test and `core:ui:test-utils`.

Do not move Drive, Google auth, WorkManager, restore runtime, file replacement or SQLite
orchestration into common Settings. Existing shared APIs elsewhere do not authorize a new
production dependency from the unavailable implementation.

Root flow:

1. Add `settingsGraphFactory: SettingsGraph.Factory` to `AppRootDeps`.
2. Implement it in `AppGraph` through the existing `asContribution<SettingsGraph.Factory>()`
   pattern; keep the direct app Settings aggregation edge.
3. Remember the factory from deps in `AppNavigationHost`, like the other shared features.
4. Accept the factory in `settingsGraph`; remember a `SettingsFeature(factory)` instance.
5. Replace the singleton/Context lookup with the injected factory. Read the Store accessor
   exactly once per created extension; preserve retention and handler-store identity.
6. Change the `app/common` Settings dependency from `implementation` to `api`, since
   its public `AppRootDeps` now exposes the factory.

Keep qualified dispatcher identity accessors in the common graph.
Move the test-facing Context accessor to an Android-only Settings-scope contribution and
assert the same application Context in the existing identity test. A GraphExtension's
contributed interface may require an explicit cast; do not assume the root-graph
`asContribution` helper works on the extension. Compile and test the real generated graph.

After the move, the topology gate's approved feature `Context.appDeps` reader set is empty.
Do not delete the core helper or refactor unrelated callers outside this boundary.

## 6. Resources, previews and golden preservation

Move both catalogs to `src/commonMain/composeResources/values{,-ru}/strings.xml`.
Each contains 65 strings and three plurals. Preserve every key, translation, plural quantity
and comment, with exactly this allowed textual normalization:

- EN: six unnumbered `%d` occurrences become `%1$d`.
- RU: twelve unnumbered `%d` occurrences become `%1$d`.

Verify each target catalog equals its base catalog after that substitution. Do not treat
catalog blob equality as the criterion after the permitted 18 substitutions.
Use CMP `stringResource`/`pluralStringResource` in composition and injected
`StringResolver` outside it; never top-level CMP `getString` in feature code.
Pin plural formatting for 1, 2 and 5 and both catalogs' arguments/quantities.

Use current shared preview conventions and preserve every existing preview subject and
light/dark coverage. The current shared resource stack selects the primary locale and falls
back to EN. Record that inherited limitation explicitly; do not claim parity for arbitrary
Android locale-list fallback or change global locale infrastructure here.

Move the 12 PNGs from `src/test/snapshots/images` to
`src/androidHostTest/snapshots/images` with the same basenames and Git blob IDs.
The six golden method names remain `screenSignedOut`, `screenSignedIn`,
`screenThemeDark`, `screenAuthPaused`, `screenRestoreOverlayRestoring`,
`screenRestoreOverlayCompleted`, each in light and dark.

Compare the complete old/new basename→blob maps, including cardinality. Do not record
goldens. A pixel difference is a STOP for maintainer review, not permission to accept a new PNG.

## 7. Test contract

### 7.1 Preserve the existing suite

Port these five suites to `commonTest`, keeping all 22 original test names and assertions:

| Suite | Methods |
| --- | ---: |
| `SettingsInteractorImplTest` | 8 |
| `SettingsClickHandlerTest` | 7 |
| `SettingsInputHandlerTest` | 2 |
| `SettingsNavigationHandlerTest` | 2 |
| `StartCardModeMapperTest` | 3 |

Replace MockK with local fakes where required for common compilation. For the existing
`PlatformInfoProvider` dependency, a test-only expect/actual fixture is permitted; do not add a
production version-provider abstraction just to mock a final platform class. Test names are
source identities, not numbers that can be replaced by unrelated tests.
The existing click test whose name mentions the frequency picker uses a sign-out-dialog fixture;
preserve its current assertion and record that discrepancy rather than silently changing coverage.

Keep these five suites in `androidHostTest`, preserving their 98 identities/assertions:

| Suite | Methods |
| --- | ---: |
| `BackupInteractorImplTest` | 32 |
| `BackupClickHandlerTest` | 52 |
| `BackupDateMapperTest` | 8 |
| `BackupUiMapperTest` | 5 |
| `SettingsStartCardModeSheetTest` | 1 |

Only adapt fixtures for the typed auth handles and StringResolver as necessary.
Keep the 12 golden invocations and two device tests separately accounted for.
Preserve all four `SettingsExtensionIdentityTest` meanings. Its host Play-services failure
is an existing dependency-reachability oracle, not proof of successful Store construction.
Preserve and execute the app's existing 12 generation/dialog-state device identities.

### 7.2 New behavior coverage

Use these literal names for new tests. Register their fully qualified identities before
measuring XML; the first observed output must not become its own expected oracle.

**Common, `SettingsBackupAvailabilityTest`, two methods:**

- `availableBackupInitializesAllThreeObservers`: initialize the real Store with an
  available recording handler; assert all three observer actions exactly once/in order,
  plus the ordinary Settings initialization.
- `unavailableBackupInitializesNoObservers`: initialize the real Store with an unavailable
  handler that fails if invoked; prove ordinary Settings initialization still works.

These tests must exercise the production Store lifecycle, not a copied list-building helper.

**Android, `SettingsPlatformAdaptersTest`, four methods:**

- `authRequestPreservesIntentSenderIdentity`.
- `authResultPreservesIntentIdentity`.
- `cancelledAuthResultPreservesNull`.
- `androidHandlerRemainsAvailable`.

**iOS, `SettingsUnavailableBackupTest`, three methods:**

- `productionGraphConstructsSettingsWithUnavailableBackup`: construct Settings through
  its real Metro factory and production iOS binding, obtain the Store once, initialize it,
  and assert unavailable State. A test parent may supply non-backup repository/runtime fakes;
  it must not replace the Settings factory, Store, backup binding or create fake backup deps.
- `allBackupActionsLeaveStateAndEventsUnchanged`: after ordinary initialization settles,
  dispatch samples for all 19 backup variants through the real Store; assert unchanged State
  and no backup events. Pin the action sample inventory so a newly added action cannot escape.
- `unavailableAuthLauncherNeverCallsBack`: obtain and invoke the actual iOS launcher in a
  Compose test scene and assert no callback or fabricated success.

**iOS, `SettingsNativeSceneTest`, four methods:**

- `settingsWithoutBackupKeepsGeneralActions`: render production Settings and exercise
  theme, archive, start-card mode and back action emission with backup absent.
- `unavailableBackupHidesSectionDialogsAndOverlay`: assert absence for every backup
  dialog/overlay variant even with hostile fixture State; the general mode sheet still opens.
- `startCardModeWaitsForThePersistedPreference`: preserve the existing unknown-before-load
  and persisted-value behavior on Native.
- `sharedBackupContentRendersWithAnEnabledFixture`: exercise the actual shared backup
  renderers/resources across signed-out, signed-in, paused and restore states with a
  test-only available State. This does not enable a production iOS backend.

The required non-golden host inventory is 126 methods (120 preserved + two common + four
Android). The required Native Settings inventory is 31 (22 ported + two common + seven iOS).
Additional meaningful tests are allowed; register them explicitly and report the final exact
identity sets. No skipped/disabled tests, success on `NO-SOURCE`, count-only oracle, or
replacement of production scenes with reimplemented test UI.

### 7.3 Regression boundaries

Exercise current Android sign-in/cancel/sign-out, manual backup, schedule changes, AI-export
scope handling, list/restore confirmation, progress/completion and undo routes through the
preserved tests and smoke flow. Preserve bootstrap versus rehydrate, scheduler cancellation,
auth scope checks and app-dialog publication.

Run the existing Room/restore/generation device regressions even though production database
algorithms are not changed. An unavailable account or manual Drive path is reported as
unverified; do not label a host fake or a linked Native binary as an end-to-end backup test.

## 8. CI ownership and negative controls

Extend only these existing gate owners:

- `.github/scripts/assert_kmp_ui_source_topology.py`: add Settings manifests and
  `FeatureEntry`, resource/preview/test identities, platform seam and availability guards,
  exact factory flow, dependency ownership and empty approved appDeps-reader set.
- `.github/scripts/assert_kmp_ios_smoke.py`: append Settings with all 31 literal Native
  identities (and any explicitly added tests), using the existing platform/classname format.
  Keep the existing 17 module expectations unchanged.
- `.github/workflows/android_build_unified.yml`: add Settings simulator execution, device
  test linking, binary check and result upload to the existing `KMP iOS kit smoke` job.
  Update the linked-module denominator from 17 to 18. Keep its stable job name.

Use the existing `FeatureEntry` mechanism and scoped extra checks. If its schema needs an
additive field, old defaults, outcomes and failure messages must stay byte-identical,
demonstrated on unchanged positive and negative fixtures. No parser replacement or broad
gate refactor. The workflow and oracle must reject missing, stale, empty, skipped-only,
duplicated or incomplete result sets according to the current identity-gate rules.

Prove the following known negatives on the implemented tree, then rerun their restored gates:

| Mutation | Required failing observation |
| --- | --- |
| Send backup observers when unavailable | Real-Store common test fails |
| Remove one available startup observer | Real-Store common test fails |
| Make the iOS binding available | Production graph/scene availability assertion fails |
| Make the unavailable handler throw when invoked | All-action inertness test fails |
| Remove a backup section/dialog/overlay visibility guard, each independently | Native absence assertion fails |
| Replace the correct auth wrapper result with null or a different handle | Android mapper identity test fails |
| Reintroduce Context factory lookup or remove the public root factory edge | Topology/root wiring check fails |
| Remove Settings Native workflow task, link check, or oracle identity | Corresponding workflow/oracle check fails |
| Drop or duplicate a Settings test from a copied result fixture | Exact-identity gate rejects it |

For mutations use [mutation_harness.py](../mockups/mutation_harness.py), with fresh forced
Gradle execution where applicable. A compile failure is INVALID, not a meaningful RED.
For Python/file-fixture checks use the existing safe harness facilities or byte-and-mode
snapshot/restore in a finally block when that harness cannot score the check. Never restore
via `git checkout`, `git restore`, `reset` or `clean`. Confirm restored bytes and rerun green.
Do not weaken a test or gate to accommodate a failed negative control.

## 9. Verification protocol

### 9.1 Entry and evidence rules

Before editing implementation, fetch the actual target `dev`, inspect the approved spec and
diff it against the discovery base. If intervening work changes these contracts or inventories,
report the concrete conflict before implementation. An unrelated advance does not require a
new product decision.

Record the checkout SHA, spec blob, dirty-tree status, JDK/Gradle/Kotlin/Xcode/simulator/device
versions and signing setup. Preserve unrelated work. If isolation is needed, use the repository's
`.claude/worktrees/` policy; never a sibling or `/tmp` checkout.

Run fresh entry topology, Settings host tests/goldens, app Settings identity tests, and relevant
Android build/lint checks before editing. Save the 120 identity baseline, 12 PNG blob map and
catalog normalization comparison inputs. A failing baseline is classified, not silently fixed.

Every load-bearing Gradle invocation uses:

```text
--rerun-tasks --no-build-cache --no-configuration-cache --console=plain
```

Run Detekt separately from test/build batches. Preserve the command, start/end time, exit
status, full task summary and fresh XML. The Gradle summary must contain only executed
actionable tasks; cached/up-to-date work cannot be presented as executed evidence.
Require nonzero expected test identities. Clean/remove or timestamp-check old reports before
admitting them. When piping logs, preserve the Gradle exit code with pipefail.
Never reuse the Phase 7.17 evidence as a measurement of this branch.

### 9.2 Required exit gates

The commands below show task ownership; append all four flags above to every Gradle command.

```bash
python3 .github/scripts/assert_kmp_ui_source_topology.py
python3 .github/scripts/assert_mvi_source_topology.py

./gradlew :feature:settings:assembleDebug :feature:settings:lintDebug \
  :feature:settings:testAndroidHostTest :feature:settings:verifyPaparazziDebug \
  :feature:settings:assembleDebugAndroidTest

./gradlew :app:app:testDebugUnitTest \
  --tests 'io.github.stslex.workeeper.di.SettingsExtensionIdentityTest'

./gradlew :feature:settings:iosSimulatorArm64Test \
  :feature:settings:linkDebugTestIosArm64

./gradlew assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest --continue
./gradlew detekt --continue
```

Then execute the full updated 18-module Native simulator/link task lists from the workflow,
check all 18 `test.kexe` outputs and run:

```bash
python3 .github/scripts/assert_kmp_ios_smoke.py
```

The identity gate is meaningful only after all required fresh XML exists. The Settings-only
Native command is not a substitute for the full final workflow gate.
`linkDebugTestIosArm64` is linking evidence, never physical-device execution.

Run Android Smoke and Regression on an available supported emulator/device, appending the
same forced-execution flags:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  --continue
python3 .github/scripts/assert_mvi_device_identities.py

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression \
  --continue
```

Preserve Smoke XML before Regression overwrites it. Run the identity assertion against the
Smoke output and check the Settings two/app generation twelve identities separately.
The current `run_smoke_ui_tests.sh` does not append the required flags itself; its mere
successful invocation is not the forced-execution evidence required here.

Run any further mandatory checks from the current canonical testing/CI documents.
Do not repeatedly broaden successful test runs without a new change or unresolved concern.
On a host without Apple tooling, finish safe Android/static work and provide the exact pending
Native gate commands; the phase remains incomplete until an appropriate runner executes them.

## 10. Change boundary and implementation sequence

Allowed implementation paths:

- `feature/settings/**`, including source-set moves, local adapters, test fixtures and tests.
- `app/common/build.gradle.kts`.
- `app/common/src/main/kotlin/io/github/stslex/workeeper/app/common/di/AppRootDeps.kt`.
- `app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt`.
- `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt`.
- `app/app/src/test/kotlin/io/github/stslex/workeeper/di/SettingsExtensionIdentityTest.kt`.
- The three CI files named in Section 8.
- This specification's evidence/decision ledger, and minimal truthful updates to
  `documentation/architecture.md`, `documentation/testing.md`,
  `documentation/ci-cd.md`, `documentation/tech-debt.md`,
  `documentation/feature-specs/backup.md`.

No changes to core production modules, recovery algorithms, database schema, navigation routes,
root build conventions, dependency versions, global lint rules, baselines, Graphify snapshots,
`docs/index.md`, or `docs/_config.yml`. No new module, cloud service, iOS app host,
Google-on-iOS integration, backup format or shared generic platform container.

Use a small sequence of signed, bisect-green commits:

1. Establish typed auth/capability seams and their Android behavior under the current source
   layout; preserve the Android visual and runtime baseline.
2. Move Settings to shared source sets, add the unavailable iOS binding/launchers, root-factory
   wiring, test ports and required topology/oracle/workflow changes together so the tree stays
   testable. Split this further only at genuinely green boundaries.
3. Complete negative controls, full exit gates, evidence and canonical documentation.

This is a contract specification, not a frozen generated patch. Local import, test-fixture,
private helper and build-configuration repairs inside the allowed paths are permitted when
they preserve the contract and all identities. Record them with evidence.
Do not use this allowance to expand product behavior or reduce gates.

## 11. STOP conditions and review handoff

Stop the affected implementation and report the concrete blocker if:

- the approved base/spec is incompatible with intervening work;
- Android backup/auth/restore behavior or a golden pixel must change;
- a new suppression, baseline, version, core-production edit or scope expansion is required;
- Native construction still needs a backup backend, Android object, or fake production binding;
- the unavailable path starts work, emits success, or blocks ordinary Settings;
- test identities disappear, a negative control cannot fail meaningfully, or freshness cannot
  be demonstrated;
- signing fails or any created commit is permanently unverified by GitHub;
- required tooling or services prevent a load-bearing gate from running.

Continue independent safe work when possible, but do not mark the phase complete or conceal
an incomplete gate. Missing tooling is not a successful gate. A semantic change requires a
documented decision before dependent work; ordinary bounded repairs do not need repeated approval.

Before implementation handoff, require a separate review role/session to inspect the diff
and evidence independently. It must check Android compatibility, all unavailable paths,
production Metro aggregation, immutable State/modal rules, exact test preservation,
golden bytes, CI negative controls, signatures and scope. Do not label the implementer's own
final pass an independent review.

Open the implementation PR into `dev`, with the actual base/head SHAs, verification limits,
review findings and all gate results. Classify review findings before changing code.
Do not merge or enable auto-merge. Explicitly separate source observations, executed tests,
linked binaries, manual checks and still-pending checks.

## 12. Exit evidence ledger

Implementation appends evidence here; do not replace the discovery record or manufacture green
results. Until the following rows are filled and reviewed, status remains SPECIFICATION or
IN PROGRESS.

| Evidence | Required record |
| --- | --- |
| Base and scope | Actual base/head/spec blob, changed paths, approved deviations |
| Platform contract | Android available, iOS unavailable, initialization and all-action assertions |
| Source sets and dependencies | Exact file census; ten preserved Android source blobs; no platform leaks |
| Resources and goldens | EN/RU normalization comparison; complete unchanged 12-PNG blob map |
| Test preservation | Baseline and final exact identities, counts, freshness and no skips |
| Android | Host, app identity, lint/build, Smoke, Regression and restore/generation results |
| Native | Settings graph/scenes; full 18-module simulator XML; 18 device binaries linked |
| Negative controls | Mutation, command, meaningful failure, restored bytes, restored green |
| Static analysis | Topology, MVI, Detekt and required canonical checks |
| Signatures | Every created SHA, GitHub verified=true, reason and verification timestamp |
| Review | Separate reviewer/session, findings, fixes and remaining limitations |

## 13. Deferred work

- Implement real iOS backup after KMP migration, through a separately reviewed backend and
  platform/auth/storage specification. Reuse the shared Settings contract/UI when appropriate.
  Availability becomes true only with a real implementation and its own acceptance evidence.
- Discover the remaining recovery and `app/common` boundaries, then the iOS host separately.
- Retain the existing primary-locale fallback limitation and unrelated Settings formatting debt.
- Do not remove the existing unused `Navigation.RestartApp` action or perform unrelated cleanup
  under this migration.

### 12.1 Accepted implementation amendment — root call site (2026-10-31)

The maintainer approved adding
`app/common/src/main/kotlin/io/github/stslex/workeeper/App.kt` to Section 10's allowed paths,
limited to passing `settingsGraphFactory = deps.settingsGraphFactory` to the existing
`AppNavigationHost` call. On the actual base, that host receives each feature factory as a
parameter and does not receive `AppRootDeps`; the one admitted root-deps read stays in `App.kt`.
Section 5's factory flow follows that existing parameter path. No second root lookup is added.

### 12.2 Implementation base and entry evidence (2026-10-31)

Implementation starts at signed `origin/dev`
`a06afc577a48e7bd63c43f49c9a4570de1f04586`; accepted specification blob
`d7053bf94b5c7fb829e53bf86bb94158e2abfb37`. GitHub reports `verified=true`,
`reason=valid`, `verified_at=2026-10-31T11:00:48Z`. The unsigned review-source commit was
not used as the branch base. The main checkout was clean on `feature/kmp-app-dialogs-entry`;
implementation is isolated in `.claude/worktrees/kmp-settings` on `feature/kmp-settings`.
The only change from discovery base `179e1c7df95e40912f0fb6c7eea8a3142b1c8b8a` to the
implementation base is this specification.

Entry topology and MVI source checks exited 0. With all four Section 9 flags:

| Entry command (after `./gradlew`) | Exit | Executed summary / fresh identities |
| --- | ---: | --- |
| `:feature:settings:assembleDebug :feature:settings:lintDebug :feature:settings:testDebugUnitTest` | 0 | `349 actionable tasks: 349 executed`; 120 exact non-golden cases; no lint findings |
| `:feature:settings:verifyPaparazziDebug` | 0 | `211 actionable tasks: 211 executed`; 12/12 golden invocations |
| `:app:app:testDebugUnitTest --tests io.github.stslex.workeeper.di.SettingsExtensionIdentityTest` | 0 | `591 actionable tasks: 591 executed`; four identities |

The first two configuration attempts failed because the isolated worktree lacked ignored
local signing inputs (`getProperty(...) must not be null`, then `keystore.jks is inValid`).
Existing ignored local properties, keystore and Firebase files were copied without exposing
values; no convention or tracked configuration was changed. These failures are not test evidence.
Baseline XML, the 120 source identities, 12 basename-to-blob map and catalog inputs were saved
before any implementation edit.

Environment: JBR/OpenJDK 21.0.11; Gradle 9.6.1; project Kotlin 2.4.10, Metro 1.3.2;
Xcode 27.0 (27A266a), installed iOS 26.5 simulator runtime; Android CLI's existing
Pixel_6_API_34 emulator started as `emulator-5554`. Local Git signing is enabled with SSH.
No toolchain version or global configuration was changed.

### 12.3 First-step implementation and review

The first step adds typed UI auth handles and the dedicated identity-preserving Android
mapper; `SettingsBackupHandler` owns availability, State derives it from the selected handler,
and Store startup conditionally sends the ordered backup observer trio. The five UI guards
cover the section, three backup dialog branches and restore overlay; start-card mode stays
independent. Existing Android fixtures explicitly pass `backupAvailable=true`.

The 120 original exact host XML identities remain, with the six Section 7.2 identities added;
the four app identity meanings remain. Source comparisons found all ten protected Android
files and all twelve PNG blobs unchanged. A separate `spec_review` reviewer role independently
checked these preservation sets and the first-step diff.

| Review finding | Classification | Resolution |
| --- | --- | --- |
| The bounded path list omitted the actual root factory call site | correct-and-new | Maintainer accepted Section 12.1's narrow `App.kt` amendment |
| An internal contributed Settings binding interface did not aggregate into the app graph | correct | Reproduced `[Metro/MissingBinding]` for `SettingsBackupHandler`; a public contributed interface with an internal companion provider preserves the internal contract and passes the real app graph/four identity tests (`591 actionable tasks: 591 executed`) |

The first new-fixture compilation caught a multiline `State.initial` call missing its explicit
availability argument. It was repaired locally without changing an identity or assertion.
Fresh first-step repository build/lint/unit execution exited 0 with
`2885 actionable tasks: 2885 executed`. Fresh Settings visual verification and custom-rule tests
exited 0 with `216 actionable tasks: 216 executed`, including 12/12 unchanged goldens.
Topology and MVI source checks exited 0. Full exit gates, negative controls, final signatures,
Native/device evidence and final independent review remain pending.

Separate repository Detekt exited 0: `63 actionable tasks: 63 executed`.
