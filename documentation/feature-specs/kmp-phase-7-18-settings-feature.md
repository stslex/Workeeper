# KMP Phase 7.18 — shared Settings with deferred iOS backup

**Status:** IMPLEMENTED — local exit evidence and separate review accepted; PR CI tracked in #349.

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

### 12.4 Shared-source implementation and immutable inventories

The original 51 production files map to 39 common files and 12 Android files. The final
105-file manifest contains 44 `commonMain`, 18 `androidMain`, four `iosMain`, 11
`commonTest`, nine `androidHostTest`, four `iosTest`, one `androidDeviceTest` Kotlin files,
two catalogs and 12 PNGs. The topology gate pins this exact set, the ten protected Android
source blobs, dependency ownership and only the two local launcher expect declarations.
There are no common/native backup-backend imports or Context factory readers.

The common graph uses the required factory. The route's existing component wrapper is
expanded locally to `navScreen<Screen.Settings>` so its composable body can
`remember(factory) { SettingsFeature(factory) }`; the feature still retains one Store and
uses the existing processor/owner lifecycle. This is a bounded repair to the specification's
non-composable registration example. The separate reviewer confirmed equivalence. The root
call-site change is precisely Section 12.1's approved single argument.

Android keeps its auth/backup/scheduling/restore/undo implementation; the ten protected files
remain byte-identical. The date mapper keeps Android time/date behavior and resolves moved
resources through `StringResolver` in the existing coroutine path. Public marker auth handles
carry internal Android wrappers without copying their underlying objects. The iOS production
binding supplies the dependency-free unavailable handler; real-Store initialization and all
19 directly dispatched backup actions are exercised. The iOS auth launcher never calls back.

Catalog comparison against the entry inputs is byte-exact after replacing six EN and twelve
RU bare `%d` placeholders with `%1$d`. Complete final catalog SHA256 values are
`7000bdad9431d90a349ce4fee8e5b3b940ca53d61d51aa6089e16936f10be7c3` (EN) and
`84cbcdf78caef2992f8ed3d2269be1d0298079f36c85b954d93db9cf031909e3` (RU).
No golden was recorded. All files below moved to `src/androidHostTest/snapshots/images/`
with the exact original basename and Git blob:

| PNG basename (common prefix `io.github.stslex.workeeper.feature.settings.golden_SettingsGoldenTest_`) | Git blob |
| --- | --- |
| `screenAuthPaused_dark.png` | `686e2f876f9bc1db28874ff9f4b27a4041f3a426` |
| `screenAuthPaused_light.png` | `fe996f6077a57d9ee0ab28a39610c8e32d330ede` |
| `screenRestoreOverlayCompleted_dark.png` | `96cdcd7ad31152c7caebd696b705269bda40e349` |
| `screenRestoreOverlayCompleted_light.png` | `958cd96d7fa4765cf22798df31bade59f0f7f2df` |
| `screenRestoreOverlayRestoring_dark.png` | `2f775d4d33042bb45298522d21d99ea7e514fb66` |
| `screenRestoreOverlayRestoring_light.png` | `38d8279de19d94b611d751e6f5f47acee41041e5` |
| `screenSignedIn_dark.png` | `cb74e7211a956431de1570018144965d35e20395` |
| `screenSignedIn_light.png` | `420ef8c15eee0106c16b17637e96d5e014dd8fc9` |
| `screenSignedOut_dark.png` | `261a4648352931c491e149a50d4028242274e751` |
| `screenSignedOut_light.png` | `142319655e31d9b6253ded28ae202b9d4e2e3048` |
| `screenThemeDark_dark.png` | `6f8260b7c31e943331b40082d6b216f3b81d7106` |
| `screenThemeDark_light.png` | `2b8cc8c272668ab6ac9230788cd454f8706aabd8` |

### 12.5 Negative-control evidence and restoration

Ten Gradle mutations were run through the repository mutation harness, with the four forced
execution flags on every underlying Gradle invocation. Each harness result is RED from an
executed assertion, not compilation, linking, `NO-SOURCE` or cached output. Each Android
control reports `212 actionable tasks: 212 executed`; each Native control reports
`133 actionable tasks: 133 executed`. The wrapper saves command/start/end/exit, raw Gradle
log and fresh failing XML, then asserts restored source bytes and mode. Recorded restored
SHA256 values match the implemented source.

| Independent mutation | Executed failing observation |
| --- | --- |
| Send observers while unavailable | `unavailableBackupInitializesNoObservers` |
| Remove available `ObservePreferences` startup | `availableBackupInitializesAllThreeObservers` |
| Make the iOS handler available | Production graph and all-action availability assertions |
| Throw in the unavailable handler | `allBackupActionsLeaveStateAndEventsUnchanged` |
| Remove restore-dialog guard | Native unavailable-surface absence assertion |
| Remove sign-out-dialog guard | Native unavailable-surface absence assertion |
| Remove frequency-picker guard | Native unavailable-surface absence assertion |
| Remove backup-section guard | Native general-action and unavailable-surface assertions |
| Remove restore-overlay guard | Native unavailable-surface absence assertion |
| Lose the Android result Intent | `authResultPreservesIntentIdentity` |

Eight safe Python source controls reject removing the public root factory, simulator task,
device-link task, binary-loop module or Native oracle identity, adding an availability default,
adding MockK to commonMain, and adding a third expect declaration. Each snapshots bytes/mode,
restores in `finally`, asserts restoration and reruns the scoped checker GREEN. All ten old
`FeatureEntry` positives and the existing Home nullable-factory negative return byte-identical
old/new outcomes and failure messages.

The Native XML parser was tested separately using copied fixture results: positive, dropped
identity, duplicate, empty, skipped-only, stale XML and missing freshness marker. Every
negative is rejected; every restored fixture is accepted with exact byte/mode/timestamp
restoration. These parser fixtures are not test-execution evidence. The previous 17 module
expectation structures remain unchanged.

After all mutations, source topology and MVI checks exit 0. The intermediate restored
Settings run exits 0 with `365 actionable tasks: 365 executed`; its Android XML was later
overwritten, so that run is not used as the retained host-XML proof. The reviewer classified
this retention gap as correct-and-new. A final complete Settings build/lint/host/golden/device
compile/Native/link batch closes it: `716 actionable tasks: 716 executed`, exit 0. Its XML was
archived immediately before another Gradle invocation: exactly 126 non-golden host identities,
12 golden invocations and 31 Native identities, all fresh and passing with zero skips.

### 12.6 Executed exit gates (2026-10-31)

The source implementation is signed commit `4ae670d518fb9271bea7959c823a6b80d16205fb`,
following signed seam commit `6c8cf1dac358c4607f538494d25602b48b5dcc18`.
The final documentation-only commit is identified by the PR head and signature receipt;
embedding its own SHA in its contents is not possible. Every Gradle command in this table
includes `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
Commands, UTC start/end/exit, raw task logs and exact-identity XML inventories are retained
under the receipt names below, separately from historical or parser-fixture output.

| Receipt | Command/task ownership | Exit | Exact executed-task summary | Fresh test evidence |
| --- | --- | ---: | --- | --- |
| `exit-visual-rules` | `verifyPaparazziDebug :lint-rules:test --continue` | 0 | `638 actionable tasks: 638 executed` | 1938 passing cases in 187 fresh XML files, including Settings 126+12; the whole snapshot also contains historical XML, which is excluded |
| `exit-repository` | `assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest :core:ui:mvi:testAndroidHostTest --continue` | 0 | `4231 actionable tasks: 4231 executed` | 3395 passing cases in 477 fresh XML files; Settings 126 and app identity four; no skips |
| `exit-detekt` | `detekt --continue` | 0 | `63 actionable tasks: 63 executed` | Separate static-analysis invocation; custom-rule tests ran first |
| `exit-settings-final` | Section 9.2 Settings build/lint/host/golden/device compile plus Native simulator/link tasks, `--continue` | 0 | `716 actionable tasks: 716 executed` | 169 passing cases in 21 fresh XML files: 126 host +12 golden +31 Native; no skips |
| `exit-native18` | All 18 `iosSimulatorArm64Test` tasks from the updated workflow, `--continue` | 0 | `491 actionable tasks: 491 executed` | 937 passing cases in 130 fresh XML files across 18 modules; no skips; exact Native oracle exit 0 |
| `exit-link18` | All 18 `linkDebugTestIosArm64` tasks from that workflow, `--continue` | 0 | `473 actionable tasks: 473 executed` | 18/18 binaries exist with fresh mtimes and recorded SHA256; linking only |
| `exit-wear-release` | `:app:wear:testStoreReleaseUnitTest -Pandroid.onlyEnableUnitTestForTheTestedBuildType=false --tests '*ReleaseRuntimeBoundaryTest*'` | 0 | `107 actionable tasks: 107 executed` | Three passing exact release-boundary identities; no skips |
| `retry-smoke` | `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke --continue --no-parallel --max-workers=2` | 0 | `2116 actionable tasks: 2116 executed` | 44 cases: 41 PASS, three unchanged pendingFeatureRewrite skips; Settings 2/2 and MVI 2/2 executed; no SCREEN_ errors |

MVI source and host identity assertions, complete shared topology, privacy scan and Wear
transport normal gate exit 0; the Wear self-test passes 53 controls. Mockup appearance passes
11/11 checks; its `f52462c7` known negative exits 1 with seven meaningful failures. The local
system Python 3.9 cannot parse two existing gate scripts; the already installed Python 3.13
runs them unchanged. The existing Chrome executable is invoked through a temporary absolute
launcher for the browser probe, with no gate/global configuration changes.

The first Smoke attempt exits 1 (`2116 actionable tasks: 2116 executed`): QEMU's hang detector
terminates the emulator, then UTP reports offline/missing device. That run is not green and
its partial XML/logcat/crash report are retained. A cold start of the same AVD also hangs;
the owned unresponsive QEMU ignores SIGTERM and is terminated before replacement. The same
API 34 AVD then boots with software rendering, Vulkan disabled and preserved disk data.
The complete retry uses serial project execution and two Gradle workers, as recorded above;
no app source, test assertion, SDK/toolchain version, suppression or baseline changes.
The exact initial hang cause is not established beyond the emulator's captured stack.

The accepted C2 CI run is [941](https://github.com/stslex/Workeeper/actions/runs/41200738809):
Build and Unit Tests, KMP iOS kit smoke and Release bundle identity all succeed. Its companion
mockup run [701](https://github.com/stslex/Workeeper/actions/runs/41200738789) succeeds.
These are CI statuses, separate from the forced local execution evidence above.

Bounded compilation repairs included the existing CMP BackHandler opt-in, `StringResource`
private UI labels, portable fake signatures and test-only iOS plain dispatcher qualifiers.
No global convention, suppression, baseline or version was changed to resolve them.

Regression and the final documentation/signature/review supplement remain pending at this
intermediate ledger point. The append below records their eventual result.

### 12.7 Final device, review and handoff supplement

The full Regression invocation uses the same annotation selector as Section 9.2 and all four
forced flags, plus `--continue --no-parallel --max-workers=2`. It exits 0:
`2116 actionable tasks: 2116 executed`. Its 88 exact cases are fresh, with zero failures,
errors or skips. The twelve required app generation/dialog identities each occur once and
pass, including `greenGate_restoreThenRollback_coherentGenerationHandover`. Room migration,
atomic rollback, invalidation, same-instance reopening and round-trip device cases pass.
Both device suites retain separate XML/identity inventories and logcat; neither contains an
`E SCREEN_` error. Smoke's three pre-existing `pendingFeatureRewrite` skips remain explicit.

The final preservation comparison confirms the ten Android blobs, full twelve-PNG map and
both normalized catalogs; exact Counter equality for the 120+6 host cases, unchanged four
app extension XML identities, two executed Settings device methods, twelve app device cases
and all 31 predeclared Native identities. No golden was recorded.

| Created source SHA | GitHub verification | Independently re-read |
| --- | --- | --- |
| `6c8cf1dac358c4607f538494d25602b48b5dcc18` | `verified=true`, `reason=valid`, `verified_at=2026-10-31T11:27:19Z` | Yes, via the GitHub connector |
| `4ae670d518fb9271bea7959c823a6b80d16205fb` | `verified=true`, `reason=valid`, `verified_at=2026-10-31T12:20:39Z` | Yes, via the GitHub connector |

The documentation-only commit is checked immediately after creation and on GitHub after its
fast-forward push; its SHA/verification timestamp live in the final signature receipt and
[implementation PR #349](https://github.com/stslex/Workeeper/pull/349), avoiding a self-SHA.
No merge, auto-merge, force push or push to dev/master is part of this handoff.

A separate `spec_review` agent role reviewed the source, exact assertions/inventories,
Android semantics, iOS production aggregation/inertness, State/modal discipline, scope,
negative controls, freshness, signatures and canonical documentation. It independently
parsed the archived XML, reran the existing Python identity parser against archived Native
results, checked binary hashes/timestamps and read source signatures/CI from GitHub.
Its source review found no unresolved defect; the accepted scope and evidence findings
and their resolutions are recorded in Sections 12.1, 12.3 and 12.5.
That role accepted the final Regression/documentation supplement: all 88 fresh cases,
twelve required app identities and 2708 archived artifact size/mode/SHA256 records checked;
no unresolved source or evidence finding remains. The documentation SHA's GitHub signature
and final PR-head CI are checked separately after this commit is created.

Full commands, statuses, task summaries, exact baseline/final identities, source/blob/catalog
maps, fresh and failed XML, mutation logs/restoration assertions and device logcat are preserved
in the local ignored artifact archive `.claude/worktrees/phase718-evidence-20261031/` in the
main checkout (`INDEX.md` and `manifest-sha256.json`). This is an evidence directory, not a
second checkout. Timestamped inventories distinguish fresh cases from older files copied into
whole-report snapshots. Parser fixtures and failed environment runs remain labeled separately.
CI also uploads the Native XML and freshness marker through the unchanged stable job.

Limits: iOS backup remains intentionally unavailable; no iOS app host/backend or physical iOS
execution is introduced. Real interactive Google account/Drive operations were not manually
performed; Android's preserved behavior is supported by the existing host, golden and device
contracts. PR CI/review after the documentation head is reported on the PR; C2's accepted CI
statuses above are not relabeled as a later head's result. Final acceptance/merge remains with
the maintainer.
