# KMP verification ownership

Entry is `6422ca7fc7d1d3b73665678ad91be83ba9995593` from PR #363. The migration is in progress. The Gradle model covers source membership, target and task wiring, and declared dependency edges. The external checks cover the Native invocation plan and its evidence. Other rows describe their intended owners and controls. Keep the remaining KMP UI topology gate active until its remaining predicates have executed replacements.

The KMP script covers core UI kit and navigation via their Native evidence, plus start-mode, plan-editor UI, image-viewer, plan-editor feature, archive, all-trainings, all-exercises, past-session, home, exercise-chart, single-training, exercise, live-workout, both app-dialogs modules, settings, app:common, core:data:database and app:ios. The MVI script separately covers core:ui:mvi and the retired lifetime lookup across production. Android app integration additionally covers app:app and the Dev and Store shipping dependency graphs.

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
| Per-feature copy/media checks, exact catalog lookups, app-dialogs reason keys | Published formatted copy and media requests, logical mapping of all failure codes, typed actions and rows | Existing portable handler tests, Native feature scenes and Paparazzi, strengthen only gaps | Swap format arguments or failure key, break image request or event routing | Equivalent string resolver call helper | K |
| Image-viewer/plan-editor/feature root factories and extension identity clauses | Required non-null factory arguments, correct owner, no fallback service lookup, creation in retained store and correct navigation/root mounting | Compilation, scoped Detekt, actual factory and composition tests | Resolve eagerly or from wrong graph, construct store repeatedly | Extract a helper with identical resolution timing | D and K |
| Plan-editor, all-trainings and all-exercises BackHandler contracts | Route system back to the correct action only in the intended state | Production graph/device or Native Compose tests using supported back-dispatch entry | Invert enablement or dispatch wrong action | Extract callback or reorder arguments | K |
| `check_recovery_launch_boundary` and `check_composition_root` | Late refusal performs zero resolution, exact grant released on disposal/abandonment, navigation callback remains current, decorators and generation state preserve lifecycle behavior | Existing Native root/Android host and device cases, add targeted lifecycle/recovery tests where needed | Resolve before admission, drop one release path, reorder decorators or capture stale callback | Extract equivalent region helper | K and I for host source hashes |
| `settings_contract` | Available backup observes all three streams. Unavailable backup avoids backend work, hides all guarded surfaces and preserves ordinary actions. Android launchers, formatters and backup policy stay intact. | Existing availability and Native scene cases, Android device/host tests, model/PSI platform ownership | Enable an unavailable dialog, drop an observer or callback, move Android dependency to common | Lift equivalent guard or adapter helper | G, D, K with I for migration-time source hashes subject to proving covered behavior |
| `check_apple_host_graph` graph and factory fragments | Production graph borrows database and lifetime, exposes all factories and preserves qualified dispatcher and resource behavior | Existing production Apple graph tests and JVM/Native compilation | Close borrowed database or cancel another lifetime, break a factory or dispatcher binding | Rename an internal binding helper | K |
| `check_apple_host_graph` installation epoch tokens | Off-main storage, real exclusion readback, malformed/link preservation, exclusive publication, candidate sync before publication and directory sync | Six existing Native cases and separate relaunch for observable storage. Investigate a direct Native control for ordered sync. | Drop exclusion, overwrite incumbent, or omit pre-publication file sync | Change helper names or equivalent POSIX operation | K/E for observed storage. A temporary simulator syscall observer distinguished an omitted candidate sync. Integration into the required Native checks is pending. |
| Test source method lists, oracle row checks and workflow script spelling | Every required identity really executes under its task and module, all results fresh and valid, distinct processes and 21 fresh IOS links | Reviewed identity manifest, verified Gradle model export, shared JUnit oracle, binary platform and relaunch validation | Missing, stale, empty, duplicate, skipped, failed, malformed or wrong-platform evidence | Equivalent workflow syntax with identical task execution and checks | E and I for source/workflow spelling |
| Both Kotlin/Native cache AWK blocks | Same Kotlin/target cache key and fail-closed ordering before restore, without full Gradle configuration | Shared declarative bootstrap inputs independently checked against evaluated targets | Remove a native target from bootstrap or model, duplicate a policy entry | Declare equivalent Gradle targets through a helper or lambda | G |

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
simulator executable, device-link task and device executable. A missing module, duplicate row
or missing terminal task fails the export or the consumer. The [test commands](../testing.md#running-tests)
use the same launcher and result checks as CI.

The [Native target input](../../gradle/kmp-native-targets.txt) feeds the KMP convention and both
CI cache lookups. Gradle compares the input with configured targets after cache restore.
The cache key keeps the Kotlin catalog version and sorts the target identifiers.

The [source-set roster](../../gradle/kmp-migration-source-sets.json) describes which sets must
have tracked Kotlin in the selected projects. Verification checks file membership in actual
compilations, so a legitimate configured custom root passes. The dependency task inspects direct
declarations in the required configurations without resolving the runtime graphs.

The Gradle source checks, `MviSharedPlatformRule`, the existing MVI ABI tests and the executed
identity gates replace `assert_mvi_source_topology.py`. Phase 7.3 references to the script describe
the original verification.
