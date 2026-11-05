# KMP Phase 7.20E — share UI generation admission

Proposed against [`dev@d7b17fd3d`](https://github.com/stslex/Workeeper/commit/d7b17fd3d49afcdeb5f3d1327459b50607cd6d3a), where [#361](https://github.com/stslex/Workeeper/pull/361) completed iOS workout-date formatting.

## Outcome

A mounted Workeeper screen holds a grant on its graph generation. A host may retire that generation only when its last grant has gone. A late composition for a retired generation must resolve zero graph dependencies. Port the existing Android admission algorithm to `:app:common/commonMain` and exercise it with the production Apple graph and shared Compose root. This covers the UI admission part of the [Phase 5 host obligations](kmp-phase-5-startup-processor.md#213-phase-7-obligations-current).

The Android serving path already relies on this behavior. On Apple the [simulator holder](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosGraphFixture.kt) currently switches a Boolean and records tokens. A production gate here lets the next host use the same retirement rule without first designing a Swift entry point, a database replacement policy or a media service.

## Entry evidence

| Boundary | What the discovery revision does |
| --- | --- |
| [Android gate](../../app/app/src/main/java/io/github/stslex/workeeper/runtime/ReplacementMechanics.kt) | `UiAdmissionGate` keeps live serials and retired IDs in one `MutableStateFlow`. A gate-local JVM `AtomicLong` assigns grant serials. The adjacent `WorkerAdmissionGate` has a separate contract. |
| [Android runtime](../../app/app/src/main/java/io/github/stslex/workeeper/runtime/AppRuntime.kt) and [quiescer](../../app/app/src/main/java/io/github/stslex/workeeper/runtime/GenerationQuiescer.kt) | The runtime grants and releases UI tokens. The quiescer waits for retirement before draining workers and fencing snackbar work. The abort path reopens the outgoing ID. |
| [Shared holder contract](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/app/common/di/AppUiGenerations.kt) and [Compose root](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/AppRoot.kt) | The root acquires a token during composition, resolves its graph only on admission, and releases the same token on forgetting or abandonment. The holder owns publication of the current phase. |
| [Android gate tests](../../app/app/src/test/kotlin/io/github/stslex/workeeper/runtime/UiAdmissionGateTest.kt) and [Apple graph tests](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosAppGraphTest.kt) | Six JVM tests exercise the current algorithm, including 500 admit/retire races. Apple already tests refused and admitted compositions but its holder does not count outstanding grants or perform atomic retirement. |

One behavior needs tightening at the module boundary. Two independently constructed gates can issue the same generation ID and serial. The current release checks those values only. Releasing A's first grant through B can therefore remove B's first grant for the same ID. This follows from the source. P0 must reproduce the collision before claiming a measured failure. The proposal makes no claim that a shipping Android caller has crossed two gate instances.

## Implementation contract

1. Add one concrete `UiAdmissionGate(logger: Logger)` to `app/common/src/commonMain/kotlin/io/github/stslex/workeeper/app/common/runtime/`. Keep the five existing operations and their nonblocking/suspending split. `admit(generationId: Int): AppUiAdmissionToken?` and `release(token: AppUiAdmissionToken)` expose only the existing opaque marker. `admittedCount(id: Int): Int`, `suspend awaitRetired(id: Int, timeoutMillis: Long): Boolean` and `reopen(id: Int)` support the existing runtime. Both host modules must compile against the concrete type.
2. Store a private, identity-equal grant object per successful admission. Allocate the candidate once per call, outside any retried state update. Track membership per generation in the same atomic state as retirement. Derive each verdict from the winning state. Log refusal after the update. A foreign gate's grant, an unrelated marker and a spent grant are safe no-ops. Do not expose a serial, owner registry or methods on the marker. The existing common coroutines dependency supplies the primitive. [`MutableStateFlow.updateAndGet`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/update-and-get.html)
3. Preserve the Android transition semantics. A previously unseen ID starts admittable. Retiring an already retired ID is successful. Waiting for a held ID suspends without blocking Main, dropping grants or fencing another ID. A timed-out wait under a held grant leaves the generation open. Cancelling a pending waiter propagates cancellation and leaves its live grant intact. Reopening an aborted generation makes it admittable again. A late release from before the reopen cannot spend the successor grant. Successful retirement and a concurrently acquired, still-held grant for that same ID are mutually exclusive until an explicit reopen.
4. Remove only the Android `UiAdmissionGate` implementation and its now-unused imports. Wire `AppRuntime` and `GenerationQuiescer` directly to the common class. Adjust the existing JVM test's imports and typed token reference. Preserve all six identities, assertions, iteration count, timeouts, refusal logging and Android transition order. Keep the gate owned by the runtime instance. The published phase or a new singleton must not own grants.
5. Make the Apple `GraphTestGenerations` fixture delegate to the same common gate. The existing observer lists may record actual grants and releases. Drive the current refusal test by retiring its empty ID before publishing it. Publish an open generation for its success path. Retain the independent recording fixture in `:app:common/iosTest` and its abandoned-composition test. The public graph factory, `App`, `AppUiGenerationsHolder` and `AppUiAdmissionToken` stay source-compatible and need no edit.

### P0 and edit boundary

Start in a clean checkout of the accepted spec on `dev`. Inventory `UiAdmissionGate`, its nested `Token`, and every admission, release, retire, reopen and count consumer. Run the six existing JVM cases green. In a disposable worktree at entry, add a focused JVM witness in the original test package. Instantiate A and B, grant the same ID first in each, call `B.release(a)`, and assert B still counts one. Require an executed assertion with expected 1 and actual 0, then verify the original six on restored bytes. A compile or setup failure is inconclusive.

The implementation patch may touch the common gate and its common tests, the three named Android runtime files, the Android gate test, the Apple graph fixture/test, both existing KMP validators, and evidence in this spec. The source topology validator pins the `:app:common` file inventory and the `:app:ios` method list. Extend those entries in place. Keep all other checks. No new module, workflow, dependency, compiler flag or generic admission framework is needed. Worker leases, snackbar fencing, database ownership, restore/backup, Swift packaging and camera/gallery have separate owners and acceptance.

## Acceptance

Register the following identities before reading candidate XML. Keep existing assertions and test identities. Common tests run on the Android host and on Kotlin/Native.

| Test and source set | Passing observation |
| --- | --- |
| `SharedUiAdmissionGateTest.foreignAndRepeatedReleaseCannotSpendAnotherGrant` · `:app:common/commonTest` | Two gates, same ID, first grant in each. Cross-release in both directions and release an unrelated marker. Both counts remain one. Add a second grant to one gate, double-release the first and verify the second still prevents retirement. Release each through its issuer and observe zero. |
| `SharedUiAdmissionGateTest.retireWaitsForTheLastGrantAndReopenIsAbaSafe` · `:app:common/commonTest` | Hold two grants on N. A cancelled waiter and a timed-out waiter leave admission open. Releasing only one grant is insufficient. Independently retire M. Release N's last grant, retire N twice, verify refusal, reopen N, grant again, then release an old grant and verify the new one is still live. |
| `SharedUiAdmissionGateTest.racingAdmissionCannotOutliveSuccessfulRetirement` · `:app:common/commonTest` | Demonstrate both orderings deterministically. Then race admit against retire on independent Default workers behind a start barrier for at least 64 fresh IDs. Keep a winning grant until the retirement verdict is captured. Forbid success plus a live grant, check refusal after success, and check recovery after a held grant is released. Record rounds and outcomes. CI scheduling need not produce both winners in the stress loop. |
| `IosAppGraphTest.mountedRootHoldsAdmissionUntilDisposal` · `:app:ios/iosTest` | Mount the shared `App` with the production graph on the owned simulator. Observe Home and one real outstanding grant. A bounded retire under the mounted screen fails. Publish `Transitioning`, await disposal and zero grants, then successfully retire. Republish the retired ID and verify no Home and no additional dependency resolution. Cross `Transitioning`, reopen and republish the same ID in a fresh phase, observe Home and exactly one additional resolution. Dispose and observe zero before clearing the owner, joining lifetimes and closing the database. |

The shared test package is `io.github.stslex.workeeper.app.common.runtime`. Use explicit barriers and joined workers. Timeouts bound the test and the specified blocked-retirement case. Sleeps, a log message, a fake counter or a manually released Compose token cannot establish retirement safety. The separate simulator [root suite](../../app/common/src/iosTest/kotlin/io/github/stslex/workeeper/AppRootIosTest.kt) already covers abandonment and saved navigation state.

Run two independent behavioral controls with the [mutation harness](../mockups/mutation_harness.py). First make release spend another live grant for the same ID and require the cross-gate assertion to fail. Then allow the Apple fixture to return an untracked token for an admitted region and require the mounted-root outstanding-count assertion to fail. In each case record focused GREEN, compiled and executed assertion RED with the intended identity, and byte-restored GREEN. Keep control patches and XML outside the delivery tree.

## Verification and delivery

Use a task-owned Apple Silicon environment with Xcode, the repo's pinned tools and an owned Android emulator for the connected gates. Verify free space across worktree, Gradle, temporary files and evidence, Gradle signing configuration, simulator boot and the rendered mockup browser before long runs. Use debug/test fixtures outside Git. Stop on missing prerequisites without modifying machine-wide settings.

```bash
set -euo pipefail
G=(--rerun-tasks --no-build-cache --no-configuration-cache --console=plain)
./gradlew :app:app:testDebugUnitTest --tests '*UiAdmissionGateTest' "${G[@]}"
./gradlew :app:common:testAndroidHostTest --tests '*SharedUiAdmissionGateTest*' "${G[@]}"
./gradlew :app:common:iosSimulatorArm64Test --tests '*SharedUiAdmissionGateTest*' "${G[@]}"
./gradlew :app:ios:iosSimulatorArm64Test --tests '*mountedRootHoldsAdmissionUntilDisposal*' "${G[@]}"
```

For the full exit, use the [7.20D verification commands](kmp-phase-7-20d-ios-date-formatting.md#verification-and-handoff) with this phase's accepted `ENTRY_SHA`, a fresh external `EVIDENCE_DIR` and a fresh full-suite simulator. Use the four focused commands above in place of the previous formatter-focused run. Preserve the nonce, four start markers, all 21 simulator tasks, separate two-process epoch proof and 21 device links. Archive focused/entry results before the full run. Capture the real exit code and fresh nonempty XML per task. Device linking proves compilation for hardware. Physical-device and installed-app behavior belong to later acceptance.

| Gate | Required result on the discovery baseline |
| --- | --- |
| Apple simulator and device | 975 existing + 4 new = **979** full simulator cases, zero skips. `:app:common` 11 → 14, `:app:ios` 19 → 20. All 21 device test binaries are fresh and marked `IOS`. Compare full XML identity multisets to entry as well as running the [Native oracle](../../.github/scripts/assert_kmp_ios_smoke.py). |
| Android build, Lint and host | 3,398 existing + 3 common cases = **3,401** with zero skips. Preserve all six original direct JVM gate cases and the runtime tests. Run the MVI host identity check. |
| Android instrumented, UI and static | Compile instrumented tests. Paparazzi/lint-rules retain 1,938 cases. Run Detekt separately. Smoke retains 44 cases with the three recorded skips, Regression retains 90 without skips. Archive Smoke XML and run the MVI device oracle before Regression overwrites its results. |
| Repository evidence | Both existing source-topology scripts, privacy and measured mockup checks pass. Reproduce the historical mockup negative from [CI](../../.github/workflows/mockup_gate.yml). Both phone release runtime dependency trees, all seven tracked Room schema exports and 456 golden PNGs match entry. |

Prove the updated validators accept a complete fresh fixture and reject a missing new identity or stale XML in the touched modules. Keep the [source topology](../../.github/scripts/assert_kmp_ui_source_topology.py) checks narrow. Reuse entry receipts only for identical relevant input blobs, tooling and task selection. Record actual Gradle summaries, identity additions, skips, mutation diagnostics and residuals. Empty, cached or up-to-date target outputs do not discharge a gate.

Implementation starts after this specification is merged into `dev` and Ilya gives explicit GO. Stop on entry drift affecting this contract, a different P0 outcome, a new public host policy or dependency, changed UI/schema/golden bytes, unexpected identities, or a failed mandatory gate. Preserve the failing command and evidence. Deliver the implementation through a separate PR with signed, GitHub-verified Conventional Commits and an independent review of its exact head. Ilya owns merge.

## Implementation evidence

Recorded on 2026-11-04 from accepted entry `30de4a4b4dcef2611411dd6897968755b697d680`,
after #362 merged and explicit maintainer GO. The reviewed head was
`ccabb0a6daa969c7a0df13e4099a8e4756d950b9`; its spec blob and the entry blob both
equal `6db42dc38abf33fd4c6bf44f2ce0c4c4cfa3e153`.
Raw commands, real exits, input hashes, XML and binaries are retained outside the
implementation worktree in the ignored main-checkout-relative archive
`.claude/worktrees/phase720e-evidence-20261104TKgWbS6/`.
Entry receipts from #361 matched all 2,361 relevant inputs per invocation and the
local toolchain. Complete exit identity/status multisets retain those entry cases
and add exactly the four approved Native identities and three Android host identities.

Host: Apple silicon, macOS 27.0, Xcode 27.0 (27A266a), JDK 21.0.11,
Kotlin/Native 2.4.10, Gradle 9.6.1 and iOS 26.5. Gates used debug/test builds,
AC power and explicitly authorized closed-lid operation. Gradle used the existing
synthetic signing fixture with a verified relative path; Git SSH signing was checked
separately. The owned full-run simulator was `6E4191EF-3BD1-43E8-B724-276DA811AD4D`;
connected tests used the sole `emulator-5562`, `Workeeper_phase720e_API34`, API 34/arm64.

P0 first passed the six original JVM cases, then compiled and executed the disposable
foreign-release witness with expected 1 / actual 0. Removing that witness preserved
the original bytes and restored all six green. Each invocation reported
`597 actionable tasks: 597 executed`. The original six assertions, identities,
timeouts and 500-round race remain unchanged apart from imports and the opaque token type.
See `p0-acceptance.json` and the separately archived P0 XML/logs.

| Gate | Exact Gradle summary | Fresh observation |
| --- | --- | --- |
| Original focused JVM cases | `597 actionable tasks: 597 executed` | Six pass, zero skips |
| Focused common Android host | `486 actionable tasks: 486 executed` | Three pass, zero skips |
| Focused common Native | `339 actionable tasks: 339 executed` | Three pass, zero skips |
| Focused mounted root | `350 actionable tasks: 350 executed` | One exact passing identity |
| All 21 simulator suites | `584 actionable tasks: 584 executed` | 975 retained + 4 new = 979, zero skips |
| All 21 device links | `563 actionable tasks: 563 executed` | 21 fresh nonempty IOS executables |
| Android build, Lint and host | `3149 actionable tasks: 3149 executed` | 3,398 retained + 3 new = 3,401, zero skips |
| Instrumented-test compilation | `2210 actionable tasks: 2210 executed` | Repository-wide compilation passes |
| Paparazzi and lint-rules | `638 actionable tasks: 638 executed` | 1,938 retained cases, zero skips |
| Separate Detekt | `64 actionable tasks: 64 executed` | Baselines and suppressions unchanged |
| Connected Smoke | `2171 actionable tasks: 2171 executed` | 44 cases: 41 pass, three retained skips |
| Connected Regression | `2171 actionable tasks: 2171 executed` | 90 retained cases, zero skips |
| Each phone release runtime tree | `5 actionable tasks: 5 executed` | Dev and Store resolved sections match entry |

All Gradle rows use the four forced-execution flags from the verification block.
The full Native multiset contains 14 cases in `app:common` and 20 in `app:ios`.
Both deterministic race orderings and 64 rounds on independent Default workers pass
on Android host and Native. Smoke's only skips are `pendingFeatureRewrite` in
`AllExercisesScreenTest`, `AllTrainingsScreenTest` and `ArchiveScreenTest`.
Fresh Smoke XML and its MVI device identity check were archived before Regression.
Both MVI identity oracles pass.

Both behavioral controls have separate focused GREEN, compiled/executed intended
assertion RED, exact byte/mode restoration and restored GREEN receipts. The foreign
release mutant fails expected 1 / actual 0 in the common case; the untracked-token
fixture mutant fails the mounted-root outstanding-count assertion with the same values.
The respective Gradle summaries remain 486/486 and 350/350 executed throughout.
See `control-release/`, `control-fixture/` and `controls-acceptance.json`.

The ordered epoch helper passed two separate exited Native processes before the
Native oracle, with matching owned path and digest and nonce
`71a609a1-819c-4e01-bee7-242cd50df107`. Those observations and filtered controls add
zero cases to the 979 full-suite count. All 42 simulator/device executables are archived.
Disposable validator copies accept the complete fresh fixture, reject each of the four
missing identities and stale XML in both touched modules, then accept exact restoration.

Both source-topology scripts, privacy and the measured mockup positive pass.
The historical `--target f52462c7` negative exits 1 with check 7 failing at
`width=0px→0px`; the positive reports 11 passed, zero failed. All seven tracked Room
schema exports, 456 golden PNGs and Detekt/Lint baselines match entry byte-for-byte.
See `final-input-comparison.json`, `validator-controls.json` and `mockup-controls.json`.

The first focused root attempt compiled but could not launch because its owned
simulator was shut down. Its failed receipt contains no executed root case and remains
archived separately. Explicitly booting the same simulator restored the exact focused
case without source changes; the subsequent full run used a newly owned simulator.
Simulator execution and IOS target linking are the platform evidence here.
Physical iOS acceptance and a signed installed application remain separate scope.

### Review follow-up

Retirement now preserves its committed result when the deadline fires before the
timed block returns. The cancellation case records the result inside the child.
The mounted-root case checks its final grant count after owner and fixture cleanup.

A deterministic JVM probe using Kotlin 2.4.10 and coroutines 1.11.0 fires the
deadline after the retirement CAS. The call returns true and the next admission
refuses. The cancellation case rejects a scratch mutant that returns false after
caller cancellation. The fixture mutant fails its original grant-count assertion.
The restored case opens a fixture and passes on the same owned simulator.

The forced follow-up runs match every identity and status from the implementation
exit. Native passes 979 cases with `584 actionable tasks: 584 executed`, followed
by the two-process epoch proof and identity oracle. Device linking reports
`563 actionable tasks: 563 executed` and 21 fresh IOS executables. Android
build, Lint and host report `3149 actionable tasks: 3149 executed` and 3,401
passing cases. Paparazzi and lint-rules retain 1,938 cases. Smoke retains 44 cases
with three expected skips, and Regression passes 90. Both original behavioral
mutants and the complete, missing-identity and stale-XML validator controls were
executed again. The seven schemas, 456 goldens, baselines and both resolved
release classpaths match entry.
