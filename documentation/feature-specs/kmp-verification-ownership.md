# KMP verification ownership

The entry policy is from `6422ca7fc7d1d3b73665678ad91be83ba9995593` in PR #363.
Gradle owns evaluated source, dependency, task and resource contracts; registered Detekt rules
own supported Kotlin structural contracts; compiled Kotlin/Compose/Native tests own behavior.
Small external utilities validate execution, binary and artifact evidence. The former KMP UI
source-topology policy engine and its active CI caller are removed after all replacement controls
below executed, including reviewed copy paths and database readers. Candidate acceptance requires the complete Android matrix and pinned
Xcode 26.6 Native jobs; local ownership controls do not waive those gates.

The retired KMP script covered core UI kit and navigation via their Native evidence, plus start-mode, plan-editor UI, image-viewer, plan-editor feature, archive, all-trainings, all-exercises, past-session, home, exercise-chart, single-training, exercise, live-workout, both app-dialogs modules, settings, app:common, core:data:database and app:ios. The retired MVI script separately covered core:ui:mvi and the retired lifetime lookup across production. Android app integration additionally covers app:app and the Dev and Store shipping dependency graphs.

## Migration map

The source locations use predicate names from the entry revision. G is model/input/artifact, D is registered Detekt, K is compilation or executed Kotlin, E is external execution evidence, and I is an incidental constraint whose replacement is shown.

| Existing check and source location | Protected invariant | Owner and runnable gate | Negative control | Permitted refactor | Disposition |
| --- | --- | --- | --- | --- | --- |
| `check_module`, `MODULES`, MVI `main` 1–3, Apple host manifest | Every tracked source belongs to a real compilation. Required shared, platform, and test roots reach the intended targets. Legacy Kotlin and Android resources cannot be stranded. | `:verifyKmpMigrationContracts` against targets, compilation memberships, configured roots and tracked inputs | Remove an Apple target or required root, insert a tracked orphan | Configure a legitimate custom root | G and I for exact filename censuses |
| Feature/image-viewer/plan-editor build fragments, dependency helpers, Settings/Apple graph build clauses, recovery and root edges | Required and forbidden project/library edges, correct source set and api/implementation exposure, Metro qualifier interop, platform isolation and shipping graph exclusion | Gradle model and evaluated declarations, shipping release dependency comparison | Move an API edge to implementation, add backup or Apple host to a shipping graph | Declare the same dependency through a provider/helper | G |
| Feature build fragments, Phase 2 and MVI §9 and §11 | Host/device test tasks, standalone Lint, actual Detekt input roots, app aggregation, JDK and ABI settings | Model checks and real TestKit fixtures, executed MVI ABI/identity suites | Keep lifecycle alias but disconnect host Test terminal, drop lint plugin, omit Detekt common sources | Equivalent target setup and dependencies in a lambda | G and K |
| Golden methods, golden configuration and device fragments in the feature checks | Verify versus record task selection, host filters, expected golden executions and non-recording, real device selector membership, no unnoticed disables | Existing golden liveness and XML, Gradle/TestKit, Detekt selector rule and connected Smoke/Regression | Disconnect verifier, remove a known golden run, change a suite selector | Move a test to an equivalent configured root | G, D and E |
| `check_plan_editor_resources`, exact catalog and ownership checks, `check_compose_resource_placeholders`, root labels, Settings catalog/PNG blobs | Private resource package/owner, locale values and placeholders, protected PNG bytes, deprecated resource key removal | CMP extension model, structured XML/file checks and retained asset manifest | Publish Res, duplicate key in another owner, change placeholder type or protected PNG | Reorder independent XML entries or equivalent escaping | G with I only for incidental XML serialization outside the byte-protected assets |
| `FORBIDDEN_IMPORT`, feature common tokens, MVI `main` 4–6, special dependency readers | No Android/Java/Firebase leaks into shared and Apple sources, no Context-based generation lookup, prescribed narrowly scoped data readers | Source-set dependency constraints and real cross-platform compilation, Detekt import/declaration/qualified-reference checks, ABI test | Use a real forbidden API via alias or qualified reference; add a forbidden dependency | Mention it in a string or comment | G, D and K |
| `FEATURE_PAYLOAD_TOKENS`, state shape, dialog/error payload shape, navigation and media seams | Semantic immutable State, portable API boundaries, opaque platform payloads, navigation dispatch and existing reviewed exceptions | Existing MVI rules plus focused PSI/type or compiler tests, executed action/handler tests | Put a resource handle or platform payload into State, bypass navigation handler | Rename or reorder same semantic private fields | D and K |
| `state_mutator_contract`, state lambda checks, per-feature copy readers and helper clauses | Resolve resources, dates and UI models at approved boundaries before State writes. Keep known mapper/date allowances scoped. | Existing domain/UI/State rules plus supported resolved call checks and executed mapper/handler tests | Read a StringResolver directly or through a known local helper in a State update | Move prior calculation to an equivalent pure helper | D and K |
| Per-feature preview inventory and suppressions, expected shims and Android placeholder clauses | Portable preview subjects and sanctioned expect/actual seams, reviewed suppression footprint and test annotations | Detekt PSI checks for constructs, compilation and report identities for actual device/preview/test behavior | Add unapproved expect seam, new suppression, or disabled test | Use an equivalent import or formatting | D and K; I for incidental names/layout |
| Per-feature copy/media checks, exact catalog lookups, app-dialogs reason keys | Published formatted copy and media requests, logical mapping of all failure codes, typed actions and rows | Portable handler tests, Native feature scenes and Paparazzi; `ImageViewerSceneIosTest.actualImageRequestRetainsModelContextAndCrossfade` observes the real Coil request | Swap format arguments or failure key, break image request or event routing | Equivalent string resolver call helper | K |
| Image-viewer/plan-editor/feature root factories and extension identity clauses | Required non-null factory arguments, correct owner, no fallback service lookup, creation in retained store and correct navigation/root mounting | `KmpSharedContractsRule` / `KmpFactoryOwnership`, `FeatureGraphOwnershipIosTest`, and `StoreProcessorSceneIosTest` | Resolve eagerly or from wrong graph, lose assisted arguments, construct store repeatedly | Extract a helper with identical resolution timing | D and K |
| Plan-editor, all-trainings and all-exercises BackHandler contracts | Route system back to the correct action only in the intended state | `FeatureGraphOwnershipIosTest.productionGraphsDispatchSystemBackOnlyWhileIntercepting` mounts the real graphs and dispatches through the pinned Native navigation-event input | Invert enablement or dispatch wrong action | Extract callback or reorder arguments | K |
| `check_recovery_launch_boundary` and `check_composition_root` | Late refusal performs zero resolution, exact grant released on disposal/abandonment, navigation callback remains current, decorators and generation state preserve lifecycle behavior | `AppRootIosTest`, `BackStackStateRestorationTest`, `StoreRetentionTest`, `RecoveryLaunchBoundaryTest` and retained Android host cases | Resolve before admission, drop a release path or decorator, restart collection or capture a stale callback | Extract equivalent region helper; equivalent decorator list/order under the observed contracts | K and I for host source hashes |
| `settings_contract` | Available backup observes all three streams. Unavailable backup avoids backend work, hides all guarded surfaces and preserves ordinary actions. Android launchers, formatters and backup policy stay intact. | Existing availability and Native scene cases, Android device/host tests, model/PSI platform ownership | Enable an unavailable dialog, drop an observer or callback, move Android dependency to common | Lift equivalent guard or adapter helper | G, D, K with I for migration-time source hashes subject to proving covered behavior |
| `check_apple_host_graph` graph and factory fragments | Production graph borrows database and lifetime, exposes all factories and preserves qualified dispatcher and resource behavior | Existing production Apple graph tests and JVM/Native compilation | Close borrowed database or cancel another lifetime, break a factory or dispatcher binding | Rename an internal binding helper | K |
| `check_apple_host_graph` installation epoch tokens | Off-main storage, real exclusion readback, malformed/link preservation, exclusive candidate creation/publication, candidate sync before publication and directory sync | `IosInstallEpochSourceTest`, `prove_ios_install_epoch_relaunch.py`, and required `prove_ios_install_epoch_sync.py` against the real Native executable | Drop exclusion, overwrite incumbent, omit or reorder synchronization, write after sync, or remove exclusive/symlink-safe creation | Extract the candidate-sync helper without changing its operations | K/E. The required simulator job integrates the real syscall observer; candidate acceptance on pinned Xcode 26.6 remains required. |
| Test source method lists, oracle row checks and workflow script spelling | Every required identity really executes under its task and module, all results fresh and valid, distinct processes and 21 fresh IOS links | Reviewed identity manifest, verified Gradle model export, shared JUnit oracle, binary platform and relaunch validation | Missing, stale, empty, duplicate, skipped, failed, malformed or wrong-platform evidence | Equivalent workflow syntax with identical task execution and checks | E and I for source/workflow spelling |
| Both Kotlin/Native cache AWK blocks | Same Kotlin/target cache key and fail-closed ordering before restore, without full Gradle configuration | Shared declarative bootstrap inputs independently checked against evaluated targets | Remove a native target from bootstrap or model, duplicate a policy entry | Declare equivalent Gradle targets through a helper or lambda | G |

## Implemented owners

The resource owner is `VerifyKmpResourceContracts`, included in
`:verifyKmpMigrationContracts`. Its reviewed logical catalog and protected asset inputs live in
`gradle/kmp-resource-contracts.json`. It reads evaluated CMP visibility/package and conversion
inputs, including custom roots; it validates the EN/RU values, placeholder types, key ownership,
retired keys, legacy resource stranding and Settings PNG membership/bytes. Independent XML ordering
and equivalent escaping are not policy. The TestKit controls execute the production task.

`WriteKmpContractScope` supplies Detekt with evaluated shared production, Apple production and
shared-test file memberships. It includes intermediate/custom source sets and excludes generated
outputs. `KmpSharedContractsRule` enforces platform references, portable immutable payloads,
resource/date boundaries, reviewed pure mutators and retained feature factory structure.
`KotlinContractNames` expands PSI imports and type aliases; it does not claim compiler overload or
type resolution. Cross-platform compilation and actual runtime cases remain separate owners.
Both new rules use the required-findings context so Kotlin suppression cannot hide a violation.

`KmpDatabaseReaders` checks LiveWorkout and Chart readers across every evaluated consumer
compilation, including host tests. Its database symbol catalog comes from the evaluated database
and database-test production roots. Canonical reader aliases preserve class/file refactors;
reviewed fixture types remain scoped to their individual test owners. Import aliases, qualified
references and package stars resolve against PSI declarations. Same-file lexical bindings retain
precedence, while file-private declarations and their descendants cannot shadow cross-file symbols.
Ambiguous database star references require an explicit import or qualification. Compiler execution
remains mandatory; this supported resolver does not replace Kotlin type or overload resolution.

The six reviewed LiveWorkout State mutators are required independently of their declarations.
Their purity and allowed mapper references follow canonical type aliases through the indexed
common declarations, including same-package targets in separate files. A missing target fails
closed; a class and file rename with the original name retained as a type alias is permitted.
The control compiles that production refactor and runs the module's Detekt and handler case.

`KmpDeclarationContractsRule` compares the module-wide declaration multiset with
`gradle/kmp-declaration-contracts.json`. The scope task loads the independently complete policy,
so deleting a declaration or its file cannot erase its obligation. Semantic previews additionally
use the production subjects in `gradle/kmp-preview-subjects.json`; `KmpPreviewContracts` follows
supported private helpers and content/theme bindings, stops at those production subjects, and
rejects ambiguous or unsupported scaffolding. The core kit retains its annotation inventory,
compilation and rendered scene/golden owners; the previous gate did not constrain its preview bodies.

The runtime additions exercise production boundaries. The image-viewer case captures the actual
Coil request through its supported preview handler and checks model updates, platform context and
crossfade. The Apple feature cases mount all thirteen production wrappers, compare the Store read
by production with the retained ViewModel, force content/factory replacement, and check non-default
assisted arguments. A separate case mounts the real `App` root and observes all thirteen admitted
factories. The back case drives the pinned Compose navigation-event dispatcher, observing parent
fallback, the feature action and selection's return to parent fallback. Fixture teardown advances
the Compose scheduler while canceled Store jobs finish their Main-dispatched cleanup before joining
the generation lifetime.

`SettingsLaunchersTest` uses the real Android adapters with a recording activity-result registry
and context. It checks sender identity, current callbacks, success/cancel payloads, link intent
semantics and a missing activity. The existing chart coherence case now exercises selection changes
both during data loading and while copy resolution is suspended; its Store/resolver fixtures also
reject resource/date work inside State updates. These additions do not alter production application
code or dependencies. Their declared expected totals are 983 Native and 3,402 Android host cases;
final whole-suite comparison remains required. The existing app device harness adds one declared
Regression identity: `app:app` /
`io.github.stslex.workeeper.app.ExerciseMediaGraphTest` /
`productionGraphRoutesMediaEventsAndResults`, expected passing once. The expected Regression
inventory is the unchanged 90 entry identities plus this one (91, no skips); Smoke remains 44 with
its three documented skips. The case mounts the real Exercise graph and Android adapters with a
recording registry/context, using a Store fixture only at the event/action boundary. It does not
claim execution of the external camera or system picker applications.

## Executed replacement controls

The local control ledger contains 143 executed variants: 87 intended negatives and 56 positive
or restored variants. Invalid setup/compile attempts and the initial timed scroll-condition
failure are retained separately and are not counted. Each Gradle run used
`--rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain` and
reported only executed actionable tasks. Native controls used local Xcode 27.0; the candidate's
required Xcode 26.6 CI execution is a separate acceptance obligation.

The runnable owners in the migration map use these production entry points. Test selection for
individual controls is narrower; the full [test matrix](../testing.md#running-tests) retains the
complete reviewed identities.

| Family | Production task or check | Executed negative / positive controls | Tasks executed per variant |
| --- | --- | --- | --- |
| ImageViewer model/context/crossfade | `:feature:image-viewer:iosSimulatorArm64Test` | 2 / 2 | 108 |
| Thirteen retained factories, routes and three BackHandlers | `:app:ios:iosSimulatorArm64Test` | 8 / 3 | 350 |
| Chart data/selection/copy coherence | `:feature:exercise-chart:testAndroidHostTest` | 2 / 2 | 214 |
| Android Settings adapters/current callbacks | `:feature:settings:testAndroidHostTest` | 5 / 2 | 212 |
| Admission, release, generation state and supplied ViewModel owner | `:app:common:iosSimulatorArm64Test` | 5 / 2 | 339 |
| Apple borrowed graph/database/lifetime and dispatchers | `:app:ios:iosSimulatorArm64Test` | 9 / 2 | 350 |
| Epoch off-main work, real exclusion readback and incumbent preservation | `:app:ios:iosSimulatorArm64Test` | 3 / 2 | 350 |
| All twelve AppDialog failure reasons and formatted bodies | `:feature:app-dialogs:impl:iosSimulatorArm64Test` | 2 / 2 | 120 |
| Exercise image requests, model changes and crossfade | `:feature:exercise:iosSimulatorArm64Test` | 2 / 3 | 131 |
| LiveWorkout copy before database/State writes | `:feature:live-workout:testAndroidHostTest` | 1 / 3 | 214 |
| Exactly one camera permission read and no gallery permission read | `:feature:exercise:testAndroidHostTest` | 2 / 3 | 196 |
| All three Settings observers and five unavailable surfaces | `:feature:settings:iosSimulatorArm64Test` | 7 / 2 | 135 |
| Production mutator class/file rename with cross-file canonical alias | `:feature:live-workout:detekt :feature:live-workout:testAndroidHostTest` | 0 / 2; impurity and missing-owner negatives belong to the rule suite | 220 |
| Exercise camera/gallery/permission/settings event routing | `:app:app:connectedDebugAndroidTest` | 4 / 3 | 758 |
| Navigation saveable/Store ownership and recovery collection | `:app:app:connectedDebugAndroidTest` | 3 / 5 | 758 |
| Actual Native epoch open/write/sync/publication ordering | `:app:ios:iosSimulatorArm64Test` and `prove_ios_install_epoch_sync.py` | 9 / 2 | 349 |
| Past Session locale separator | `:feature:past-session:testAndroidHostTest` | 1 / 2 | 196 |
| Home active/recent and conflict copy | `:feature:home:testAndroidHostTest` | 3 / 2 | 204 |
| AllExercises footer/delete/archive copy | `:feature:all-exercises:testAndroidHostTest` | 3 / 2 | 175 |
| SingleTraining confirm/archive/conflict copy | `:feature:single-training:testAndroidHostTest` | 5 / 2 | 196 |
| Exercise confirm/archive/conflict/default-name copy | `:feature:exercise:testAndroidHostTest` | 6 / 2 | 196 |
| LiveWorkout rename/picker error copy | `:feature:live-workout:testAndroidHostTest` | 2 / 2 | 214 |
| Scoped database readers in production and host tests | `:feature:live-workout:detekt` / `:feature:exercise-chart:detekt` with compilation or fixture tests | 3 / 4 | 104, 193, 220 |

The structural owner is exercised by `:lint-rules:test` and the repository's separate `detekt`
gate. Its completed suite has 240 cases, including aliases, custom scopes, suppression resistance,
missing declarations, preview subjects/themes, pure State mutators, database-reader visibility
and permitted refactors. The suite reported `9 actionable tasks: 9 executed`; separate Detekt
reported `87 actionable tasks: 87 executed`. Evaluated Gradle owners run through
`./gradlew -p build-logic :convention:check` (27 TestKit cases, with forced nested execution),
`:verifyKmpMigrationContracts`, `:exportKmpVerificationModel` and `verifyKmpNativeTargets`.
The Native helper suite additionally exercises absent/failed/canceled/skipped children, source
and attempt mismatches, freshness, individual missing artifacts, digest checks and literal
relocated/bracket-path matching. Raw logs, XML, snapshots and receipt digests remain outside Git
in the PR continuation evidence ledger.

### Retired incidental constraints

Exact Kotlin filenames, statement spelling and annotation adjacency are replaced by evaluated
compilation membership, PSI contracts and executed identities. A concrete adjacency control is
`ExerciseFeatureSceneIosTest`: its existing method still executed after adding `@OptIn` between
`@Test` and `fun`, although the retired regular expression reported the method missing.
XML serialization/order is incidental only where the resource owner preserves logical values,
placeholder types, ownership and the explicitly byte-protected assets.

Exact navigation-decorator list syntax and order are incidental for the pinned dependencies and
observed lifecycle contracts. Reversing the two decorators passed all four
`BackStackStateRestorationTest` and all three `StoreRetentionTest` identities. Removing either
still failed the intended scroll-restoration or entity-isolation assertion. Current production
order is unchanged; this result does not assert equivalence for arbitrary future dependencies.
Recovery's current callback and single collector remain separate executed obligations.

Migration-time source hashes are replaced by those semantic owners and the retained full exit
matrix. This change's acceptance additionally compares the protected source/catalog, schema,
golden and analysis-baseline bytes with entry. Historical phase specifications retain the original
commands as implementation records; the [entry policy](https://github.com/stslex/Workeeper/blob/6422ca7fc7d1d3b73665678ad91be83ba9995593/.github/scripts/assert_kmp_ui_source_topology.py)
and this ownership map identify their current replacements.

## Local verification

Run the included build checks, model checks and export from the repository root.

```bash
G=(--rerun-tasks --no-build-cache --no-configuration-cache --full-stacktrace --console=plain)
./gradlew -p build-logic :convention:check "${G[@]}"
./gradlew :verifyKmpMigrationContracts :exportKmpVerificationModel "${G[@]}"
python3 -m unittest discover -s .github/scripts -p 'test_kmp_native_*.py'
python3 -m unittest discover -s .github/scripts -p test_junit_identity.py
```

The export uses the independently reviewed [Native identity roster](../../gradle/kmp-native-identities.json).
Each row maps one required module to its evaluated simulator task, JUnit XML directory,
simulator executable, device-link task and device executable. The workflow selects the
`:app:ios` device executable and simulator result directories for upload from that same
validated model. It escapes these paths so the artifact action matches them literally.
A missing module, duplicate row or missing terminal task fails the export or the consumer.
The [test commands](../testing.md#running-tests) use the same launcher and result checks as CI.

The [Native target input](../../gradle/kmp-native-targets.txt) feeds the KMP convention and both
CI cache lookups. Gradle compares the input with configured targets after cache restore.
The cache key keeps the Kotlin catalog version and sorts the target identifiers.

The [source-set roster](../../gradle/kmp-migration-source-sets.json) describes which sets must
have tracked Kotlin in the selected projects. Verification checks file membership in actual
compilations, so a legitimate configured custom root passes. The dependency task inspects direct
declarations in the required configurations without resolving the runtime graphs.
The aggregate requires source policies for the Native roster plus `:feature:app-dialogs:api`.
It requires dependency policies for those projects plus `:app:app`. Both policy rosters
must match before verification is scheduled. Other convention consumers can list their tasks
without a migration policy.

The Gradle source checks, `MviSharedPlatformRule`, the existing MVI ABI tests and the executed
identity gates replace `assert_mvi_source_topology.py`. Detekt uses the evaluated MVI compilation
inputs to classify custom shared and test roots. Kotlin suppression annotations cannot bypass
this rule. Phase 7.3 references to the script describe the original verification.

Use `--no-daemon` for Detekt mutation controls after changing the rule JAR. Detekt 1.23.8 can
reuse a daemon classloader when the JAR contents change at the same path.

The separately completed Xcode 26.6 source-baseline receipts cover PR #363 at
`6422ca7fc7d1d3b73665678ad91be83ba9995593` only. Every candidate still requires its own
simulator, epoch, synchronization and device-link evidence on Xcode 26.6. Local Xcode 27.0
receipts are supplementary. The baseline's canceled overall simulator job and the predecessor's
unresolved required checks remain distinct from candidate acceptance.
