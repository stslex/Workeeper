# KMP Phase 8 — Launch the permanent iOS app

**Status:** Proposed for maintainer review. Discovery revision: [`dev@4106c85b`](https://github.com/stslex/Workeeper/commit/4106c85be17f479e081c4e5072501b18b79310b3), after [#363](https://github.com/stslex/Workeeper/pull/363) incorporated [#364](https://github.com/stslex/Workeeper/pull/364). Implementation requires Ilya’s explicit GO.

**Exit condition.** From a clean checkout, build and install a repository-owned app on an arm64 iOS simulator. A hosted XCTest observes its real window. An XCUITest opens Home and Trainings, creates an exercise and a training template through the production UI, terminates the process and finds that template with the same exercise after relaunch. Android remains releasable.

## Findings at the discovery revision

| Boundary | What exists | What Phase 8 must supply |
| --- | --- | --- |
| Apple product | [`:app:ios`](../../app/ios/build.gradle.kts) is a Metro/Compose library. The [convention](../../build-logic/convention/src/main/kotlin/KmpLibraryConventionPlugin.kt) already owns `iosArm64` and `iosSimulatorArm64`. The repository tree has no Swift app, shared Xcode scheme, framework declaration or production controller entry point. | One installable app linked directly to this module. The earlier [probe](kmp-phase-2-probes.md) is historical evidence. |
| Shared UI | [`App`](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/AppRoot.kt) accepts `generationsHolder`, `resolveDeps` and `onOpenRecovery`. [`UiAdmissionGate`](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/app/common/runtime/UiAdmissionGate.kt) admits before graph resolution and supports atomic retirement through the shared root/holder contract. | A process owner, a scene/controller and a real recovery destination. Navigation 3, the graph’s navigator and the existing feature Stores continue to own application navigation. |
| Apple graph | [`buildIosAppGraph`](../../app/ios/src/iosMain/kotlin/io/github/stslex/workeeper/app/ios/IosAppGraph.kt) borrows seven inputs. Its [Native test](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosAppGraphTest.kt) constructs 13 real Stores. The training-list test inserts rows via a DAO before mounting Compose. | Supply production inputs and exercise the installed product. The [test fixture](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosGraphFixture.kt) removes its owned database directory and supplies a recording restart host and throwing image methods. It stays in `iosTest`. |
| Persistence | [Room](kmp-phase-7-20a-ios-room-database.md) already provides `buildIosAppDatabase()`, the bundled driver and migrations. The existing [installation epoch](kmp-phase-7-20c-ios-install-epoch.md) and [date formatter](kmp-phase-7-20d-ios-date-formatting.md) have production factories. | Own the Room handle, force its first open, handle failure, and connect these factories. |
| Platform actions | [Image source launcher](../../core/ui/kit/src/iosMain/kotlin/io/github/stslex/workeeper/core/ui/kit/media/ImageSourceLauncher.ios.kt) currently drops camera, library, permission and Settings requests. The [Settings external link](../../feature/settings/src/iosMain/kotlin/io/github/stslex/workeeper/feature/settings/ui/platform/SettingsLinkLauncher.ios.kt) already uses `LocalUriHandler`. [Settings backup](kmp-phase-7-18-settings-feature.md) is unavailable on Apple. | Implement the four missing actions, durable image storage and permission reads. Exercise an existing external link. Preserve the backup capability decision. |
| Template creation | [Trainings → Create](../../feature/all-trainings/src/commonMain/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/NavigationHandler.kt) opens `Screen.Training(uuid = null)`. The [save handler](../../feature/single-training/src/commonMain/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandler.kt) requires a name and at least one exercise, writes the template and its plans, then navigates back. | On an empty install, create a named exercise through the Exercises screen and select it in the training editor’s picker. `PlanEditor` is a different route. |
| CI | The [identity manifest](../../gradle/kmp-native-identities.json) contains 983 simulator cases across 21 modules. The [green #363 run](https://github.com/stslex/Workeeper/actions/runs/45426676002) exercised the simulator and arm64 device **test executables**. [Verification ownership](kmp-verification-ownership.md) defines the Gradle model and the evidence aggregate. | Add an installed-app result on the same resolved source. Retain the separate Native proofs and Android/Wear coverage. |

The [ordered migration plan](kmp-phase-7-7-plan-editor-feature.md) places the permanent host after shared `:app:common` and prefers direct integration. The [version catalog](../../gradle/libs.versions.toml) at entry pins Kotlin 2.4.10, Compose Multiplatform 1.11.1, Metro 1.3.2 and Room 3.0.0, with [Gradle 9.6.1](../../gradle/wrapper/gradle-wrapper.properties). The [existing workflow](../../.github/workflows/android_build_unified.yml) selects Xcode 26.6 on `macos-26` with JDK 21. Record the actual Xcode build and simulator OS in results.

## Implementation order

These are work items within one phase and one implementation PR. Each row produces an observable result.

| Order | Work | Completion evidence |
| --- | --- | --- |
| 1 | Add the Xcode product, shared scheme and direct framework integration. | Clean checkout builds a Debug `.app`, imports the intended module and packages Compose resources. |
| 2 | Bind the database, generation, recovery callback and shared root to the application lifecycle. | The real window reaches Home. Tests cover first-open failure, admission, disposal and same-database refresh. |
| 3 | Supply Apple media storage, permission and presenters. | A selected image survives relaunch. Cancellation, denial and unavailability release the request and preserve the previous image. |
| 4 | Exercise the installable product from XCTest and XCUITest. | Both named tests pass. The persistence test fails at the relaunch assertion when its database is deliberately made process-local. |
| 5 | Add the simulator app check to CI and run regression gates. | The checked source, app, tests and `.xcresult` agree. Android, Wear and existing Native checks pass on that revision. |

### Build and package the product

Check in `iosApp/Workeeper.xcodeproj` with an app target `Workeeper`, a shared `Workeeper` scheme, an app-hosted `WorkeeperTests` target and a `WorkeeperUITests` target. A fresh clone must open and build without generating a project by hand. Use UIKit’s normal application/scene lifecycle, one supported scene and a development bundle identifier shared by local and CI builds. Simulator execution requires no distribution team, signing key or provisioning profile.

Declare a static framework with base name `WorkeeperKit` on the two **existing** Kotlin/Native targets in `:app:ios`. Swift imports the module and installs one public `UIViewController` factory implemented with `ComposeUIViewController`. Keep graph, Store and DAO construction in Kotlin. Keep the existing host dependency boundary and avoid transitive framework exports. The app links the static framework once. Run `:app:ios:embedAndSignAppleFrameworkForXcode` from the repository root in the Xcode target **before Compile Sources**, and resolve `WorkeeperKit` from `$(SRCROOT)/../app/ios/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`. Set `ENABLE_USER_SCRIPT_SANDBOXING=NO` on the app target for this build phase. A Run from Xcode must work without a prior Gradle build, a manually copied framework or an untracked project.

Use a deployment target supported by the pinned dependencies and record the chosen minimum with a successful link. Set `CADisableMinimumFrameDurationOnPhone` and provide the camera usage description. Check resources in the **installed** bundle and resolve representative shared and feature strings, a drawable and a bundled design-token font from its resolver. Compare the two [target names](../../gradle/kmp-native-targets.txt) and use the existing [resource contracts](../../gradle/kmp-resource-contracts.json) as the inventory.

A clean checkout has a repository-specific prerequisite. Android signing configuration is evaluated even for Native tasks. In a disposable checkout, reuse the [Native CI procedure](../../.github/workflows/android_build_unified.yml) to create an ephemeral JKS with a workspace-relative `storeFile` and `keystore.properties`. Document the ordinary Xcode/JDK/Android SDK prerequisites and this step in the implementation. Preserve any developer’s existing signing files. Neither real Android release credentials nor Apple distribution credentials are needed for this gate.

### Own the root and its dependencies

| `buildIosAppGraph` input | Apple owner and contract |
| --- | --- |
| `database` | The process host calls `buildIosAppDatabase()` off Main for `Application Support/Workeeper/app.db`. Before publication, force a bounded read such as `trainingDao.getById(probeUuid)` to open Room and run its schema checks. Never seed the application database to make startup succeed. |
| `lifetime` | One fresh `AppScopeLifetime` and `ViewModelStoreOwner` per generation. Retain them outside Swift view reconstruction and Compose recomposition. |
| `imageStorage` | A sandbox-backed implementation of the existing [`ImageStorage` contract](../../core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/images/ImageStorage.kt). See below. |
| `cameraPermission` | Read the current AVFoundation video authorization status in `isGranted()`. Ask for access only after a user action, through the launcher. |
| `resources` | Use `buildIosResourceWrapper()` with system locale/time zone. Common string resources continue through the Compose resource resolver. Keep the existing contract on integer Android resource IDs. |
| `installEpochSource` | Use `buildIosInstallEpochSource()`. Preserve its separate backup-excluded `WorkeeperInstallation` directory and the existing DataStore location/cache. |
| `reinitializationHost` | A process-owned `AppReinitializationHost` serializes graph-only requests through one submission point. Repeated requests may coalesce. A completed request produces a fresh admitted generation over the **same open database**. |

Start with an unpublished generation. Publish its ID, owner and fully built graph together on Main. `resolveDeps` may obtain only the admitted generation’s instance. `AppUiPhase.Transitioning` renders while no generation is available. An open/query/graph failure must release any candidate-owned resources, preserve all files and expose an actionable, DB-independent startup error with safe retry. Wire `onOpenRecovery` to a visible host-owned surface that can explain the state without opening the DB. Do not substitute a success screen for an unresolved recovery condition.

View disappearance, backgrounding and presentation of a system picker keep the process generation alive. For graph-only refresh, stop admitting new work on the outgoing generation, publish the transition, wait for the UI’s exact grants to retire, drain admitted DB users and fence stale callbacks. Clear its view-model store on Main, then cancel and join the lifetime before publishing the successor. Reuse the same DB. Close a final, explicitly disposed DB only after its users are drained. Correctness across ordinary process death cannot rely on a termination callback. On timeout or partial failure, retain owned resources until safe, resume the old generation only if still usable, or show the DB-independent error. No overlapping owners, unbounded waits or close-under-reader path. Follow the [existing host obligations](kmp-phase-5-startup-processor.md#213-phase-7-obligations-current) and [shared UI admission](kmp-phase-7-20e-shared-ui-admission.md).

### Supply reachable Apple actions

- Implement save, temp capture, delete, modification time and startup temp cleanup. Decode and downsample off Main, honor orientation, max edge `1280` and JPEG quality `85`. Replace atomically within the owned directory. A read/encode/write failure returns the existing typed failure and keeps the previous good file. Cleanup affects only owned abandoned temps. Keep Room rows, other exercises and the installation epoch intact.
- `ImageSaveResult.Success.absolutePath` and the existing Coil image viewer consume file references. Produce a currently loadable app-owned file, keep persisted references resolvable if the sandbox root changes, and validate the real exercise preview and viewer after relaunch. Resolve an older app-owned reference at the Apple boundary if necessary. Scope resolution to the owned image directory, preserve Android path semantics and avoid a schema change.
- Implement the existing `ImageSourceLauncher` operations in the Apple actual. Present a single image-only system photo picker from the active scene, supply a camera flow writing to the requested capture reference, map permission results and open the app’s Settings page on request. Keep pending delegates alive through dismissal. An `NSItemProvider` file URL can expire when its completion handler returns: copy into owned staging while valid, then decode asynchronously. Photo selection uses only the selected item.
- Issue the appropriate success, cancellation or permission callback once per active request on Main. Dismissal, repeated taps, denial, restricted access, a missing camera and a retired presenter must neither leave a live Store waiting nor dispatch into a successor. Check availability before presenting the camera. On a simulator exercise the unavailable outcome. Verify an existing Settings URL can be handed to the system. The disabled Apple backup/auth path stays disabled.

## Acceptance and controls

| Proof | Required observation | Failing control |
| --- | --- | --- |
| `WorkeeperTests/HostWindowTests/testSharedRootIsAttachedToKeyWindow` | Inspect the running application’s foreground scene, visible key window and attached production controller with non-zero bounds. Assert the actual controller/view relationship. XCUITest separately identifies shared Home. | In an isolated checkout substitute an empty root controller. The application still launches, but the host/controller or Home assertion fails. |
| `WorkeeperUITests/TrainingPersistenceTests/testTemplateSurvivesProcessRelaunch` | On a newly installed bundle, launch at Home → Trainings and verify the run-unique template is absent. Use Exercises → Create to save a run-unique exercise. Return to Trainings → Create, enter the name, add the exact exercise in the picker, confirm and save. Wait for the exact list row. Call `XCUIApplication.terminate()`, await `.notRunning`, then `launch()` the same installed bundle. From Home open Trainings, assert exactly one matching row, open it and verify the same exercise. | On a separate disposable build use fresh per-process storage. The initial UI save and list assertion must still pass. Only the post-relaunch lookup should fail. Restore production code and rerun green. |
| Startup, lifetime and admission | Successful first query, same-DB refresh, fresh owners, refused retired generation with zero graph resolution, late callbacks fenced and duplicate requests serialized. Drive `onOpenRecovery` and observe the DB-independent destination. | In an isolated unsupported-version database retain the original sentinel and version after the failed first query. Hold a UI grant/DB user through a refresh deadline and prove the handle stays usable until released. |
| Images and permissions | Add a known photo to the test simulator, use the system picker and exercise editor, save and reopen the image after process relaunch. Check dimensions/format and compare identifiable content. An Apple storage test also resolves an image using an old sandbox prefix. | Cancel picking, test denied/unavailable camera and inject failed replacement on owned temporary data. Verify one terminal outcome and the previous file/image. Camera capture on hardware belongs to Phase 10. |

Use accessibility identities or labels observed in the shipping Compose tree with XCTest. If a production first-run dialog appears, dismiss it through its visible action. An Android/Compose `testTag` alone is insufficient proof of an iOS selector. Do not seed through DAOs, inject a root with launch arguments, deep-link around navigation, reinstall between launches or rely on fixed sleeps. Use predicate waits with deadlines and preserve the failure reason. Run destructive/mutated controls only on disposable fixtures and restore the exact production sources.

### Reproducible commands and CI ownership

These are implementation gates. Run on the resolved implementation checkout, with Xcode 26.6, an available iOS runtime, an **owned** booted arm64 simulator in `APP_UDID`, and an absolute fresh `RUN_DIR`. Provision and remove only the simulator created by the run. Use a different simulator from the existing Native database fixture. Log the scheme, bundle ID, simulator UDID/runtime, tool versions, source commit/tree and test identities.

```bash
set -euo pipefail
RUN_DIR="$(mktemp -d)"
G=(--rerun-tasks --no-build-cache --no-configuration-cache)
X=(-project iosApp/Workeeper.xcodeproj -scheme Workeeper -configuration Debug
   -destination "platform=iOS Simulator,id=$APP_UDID"
   -derivedDataPath "$RUN_DIR/DerivedData" -parallel-testing-enabled NO)
xcodebuild -version
xcodebuild -list -project iosApp/Workeeper.xcodeproj
./gradlew :verifyKmpMigrationContracts :exportKmpVerificationModel verifyKmpNativeTargets "${G[@]}"
xcodebuild "${X[@]}" WORKKEEPER_VERIFY_GRADLE=YES build-for-testing
xcodebuild "${X[@]}" -resultBundlePath "$RUN_DIR/Workeeper.xcresult" test-without-building
xcrun xcresulttool get test-results summary --path "$RUN_DIR/Workeeper.xcresult"
xcrun xcresulttool get test-results tests --path "$RUN_DIR/Workeeper.xcresult"
```

Implement the proposed `WORKKEEPER_VERIFY_GRADLE` Xcode setting so the **invoked Gradle embed task** receives all three `G` flags for evidence builds. Keep ordinary Xcode runs incremental. Run the full scheme, including installed resource and media tests, and require both named identities from the acceptance table on the intended device with zero skips/failures. For triage, pass `-only-testing:<target>/<class>/<method>` with the exact identity from the table and a new result bundle. Record storage/lifecycle and negative-control outcomes separately. Builds, previews, screenshots, cached output, zero discovered tests and endlessly retried assertions cannot establish these results.

Add a dedicated macOS simulator app job to the [unified workflow](../../.github/workflows/android_build_unified.yml). Use the same resolved SHA/tree as its Native siblings. Preserve the existing required `KMP iOS kit smoke` check and make acceptance depend on the successful app job and its exact-source result as well as the existing simulator/device evidence. Retain `.xcresult`, command output, exact test names/counts and version/device information. Reject missing, stale or wrong-source evidence, including on selective reruns. Leave resource caching/ownership of the other macOS jobs intact.

Keep the existing [Native task runner](../../.github/scripts/run_kmp_native.sh) in both `simulator` and `device` modes. The unified workflow separately runs the [two-process epoch proof](../../.github/scripts/prove_ios_install_epoch_relaunch.py), the [synchronization proof](../../.github/scripts/prove_ios_install_epoch_sync.py) and the [Native evidence collector/aggregate](../../.github/scripts/kmp_native_evidence.py). Preserve these checks and the device-link verification. Preserve every baseline identity and add new tests to the owning manifest where applicable. Run Android gates with the same `G` flags; keep Detekt separate:

```bash
./gradlew assembleDebug assembleDebugAndroidTest "${G[@]}"
./gradlew lintDebug "${G[@]}"
./gradlew testDebugUnitTest -PwearUnitTestFlavors=store "${G[@]}"
./gradlew verifyPaparazziDebug :lint-rules:test "${G[@]}"
./gradlew detekt "${G[@]}"
```

Preserve the shipping Wear and release-runtime boundary checks, Android device Smoke/Regression when shared paths change, the seven tracked Room schemas and 456 Paparazzi goldens at entry. Use [testing](../testing.md) and [CI](../ci-cd.md) for the active selectors and tooling. Report Gradle execution summaries, fresh nonzero case counts, device coverage and any unmeasured gate. Unexpected pixel changes, a new suppression/baseline or a non-executed load-bearing task requires investigation.

## Handoff and scope limits

Phase 8 ends at the installed simulator proof. **Phase 9** owns general background database leases, local file replacement, durable journal preflight and crash recovery before/after commit. **Phase 10** qualifies camera, library and a complete workout on a physical iPhone (T1). **Phase 11** owns signing the distribution archive, TestFlight and App Store readiness (T2). User-facing iOS backup/Google Drive remains a separate post-T1 product decision.

Stop for a maintainer decision if a required path needs a new database schema, replacement/recovery policy, a different framework integration or a shipping Android behavior change. Stop at an unexpected destructive open, lost write, false recovery success, stale-generation access, missing test identity or a failing mandatory gate. Record the exact cause and last safe state. Independent final review and Ilya’s merge follow a successful implementation report.

Platform references: [Kotlin direct integration](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html), [Compose with UIKit](https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html), [Compose resources](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html), [Apple camera authorization](https://developer.apple.com/documentation/avfoundation/requesting-authorization-to-capture-and-save-media), [Apple temporary file representations](https://developer.apple.com/documentation/foundation/nsitemprovider/loadfilerepresentation%28fortypeidentifier%3Acompletionhandler%3A%29), [XCUIApplication](https://developer.apple.com/documentation/xcuiautomation/xcuiapplication).
