# KMP Phase 7.20C — persist the iOS installation epoch

Proposed for maintainer review against [`dev@6b642755`](https://github.com/stslex/Workeeper/commit/6b6427558f9f8c308ddc6c1ab6e939dd8f025565), the merge of [the Apple Metro graph, #357](https://github.com/stslex/Workeeper/pull/357).

## Outcome

Give the Apple host a production `InstallEpochSource`. Two processes using the same installation must read the same token. If dialog preferences from installation A are presented to a graph using a newly created installation B, B clears A's pending restore dialog, keeps an ordinary preference and can publish its own dialog.

The shared root mounts app-dialogs on every admitted generation. Its repository reads the epoch when collection starts, even when Settings hides backup. This makes installation identity an independently deliverable host dependency. The implementation needs neither a Swift application nor an active backup backend.

## Evidence on the entry ref

| Source | Consequence for this stage |
| --- | --- |
| [Apple graph](../../app/ios/src/iosMain/kotlin/io/github/stslex/workeeper/app/ios/IosAppGraph.kt) and [7.20B ownership](kmp-phase-7-20b-ios-metro-graph.md#production-graph-and-ownership) | The graph borrows the database and generation lifetime. Its caller supplies five mandatory services, including the epoch source. Keep that construction API. |
| [Graph fixture](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosGraphFixture.kt) and [graph tests](../../app/ios/src/iosTest/kotlin/io/github/stslex/workeeper/app/ios/IosAppGraphTest.kt) | The current recreation test uses a hard-coded epoch. It proves repository behavior in one process. The fixture also removes its own `Application Support/Workeeper` database directory at teardown. |
| [InstallEpochSource](../../core/data/backup/api/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/backup/api/restore/InstallEpochSource.kt), [wire types](../../core/data/backup/api/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/backup/api/restore/RestoreAttempt.kt) and [Android owner](../../core/data/database/src/androidMain/kotlin/io/github/stslex/workeeper/core/data/database/snapshot/RestoreRecoveryFilesImpl.kt) | The contract atomically creates or reads a stable token. The common `RestoreOwnerId` validates its spelling. Android publishes a random UUID without replacing an existing winner. |
| [Shared root](../../app/common/src/commonMain/kotlin/io/github/stslex/workeeper/AppRoot.kt), [AppDialogRepository](../../feature/app-dialogs/impl/src/commonMain/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/data/AppDialogRepository.kt) and [existing repository tests](../../feature/app-dialogs/impl/src/commonTest/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/data/AppDialogRepositoryTest.kt) | Reconciliation already clears foreign restore flags in a DataStore edit. The existing foreign-install case provides two constant epochs. Exercise the same consumer through the production graph with two real sources. |

## Storage and ownership

- Add `buildIosInstallEpochSource(): InstallEpochSource` in the `iosMain` source set of `:app:ios`. An internal constructor or factory may accept an Application Support root for isolated tests. Production resolves Foundation's user-domain Application Support URL. Construction is free of I/O. The suspending operation moves filesystem work off Main, including first resolution. A Main caller must be able to use it.
- Use one private sibling of the database directory, `Application Support/WorkeeperInstallation/install_epoch`. Mark **only** `WorkeeperInstallation` with `NSURLIsExcludedFromBackupKey` and verify the value on a fresh URL before accepting an epoch. Keep the mark valid on reopen. The database directory, Documents, DataStore and the Application Support root retain their backup policy. Apple describes exclusion as guidance to the system. Actual iCloud, Finder and device-transfer behavior requires later device evidence. [Apple backup guidance](https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup)
- Write a random v4 UUID as 36 lowercase ASCII bytes. Parse existing bytes through the common wire type. Distinguish absence from unreadable, malformed, non-regular or linked storage. Preserve invalid canonical bytes and external symlink targets, report the failure and permit retry after the underlying problem is resolved. Never mint a replacement because a read, validation or backup-attribute operation failed. Keep actual tokens out of production logs.
- Publish a complete candidate once. Use an exclusive, uniquely owned temporary file in the same directory, complete and sync the write, then publish atomically only if the final name is absent. A losing caller reopens the winner. Close descriptors, remove only this attempt's temporary entry and propagate I/O failures and cancellation. A previously abandoned candidate must never become authoritative. Probe the supported Darwin publication and directory-sync semantics on the pinned toolchain. An exclusive hard link is one candidate. An ordinary overwriting rename or an instance-local lock cannot enforce this contract. [Darwin link(2)](https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man2/link.2.html)
- Pass the implementation explicitly to `buildIosAppGraph` in the new integration case. Keep the graph, its four other host inputs, the common epoch API and the Android owner intact. Storage lifecycle belongs to the installation across graph generations.

## P0 and Native acceptance

Before extending the graph fixture or CI, prove publication of a complete candidate, a deterministic collision with an incumbent, a fresh attribute read and reopen on an owned Apple simulator. Link the same production path for `iosArm64`. Stop if the supported filesystem or the pinned Kotlin/Native bindings cannot provide the required behavior. Report the path, operation and errno or Foundation error without including the token.

Register these six `@Test` methods under `io.github.stslex.workeeper.app.ios.IosInstallEpochSourceTest` before inspecting generated XML. Use isolated test-owned roots for destructive/error fixtures. Preserve the seven existing graph methods and their assertions.

| Method | Passing observation |
| --- | --- |
| `publicSourceUsesExcludedInstallationDirectory` | Call the public entry from Main. The result matches the regular file at the exact Foundation path. Read the directory exclusion from a new URL and compare the parent and a sibling's backup attributes before and after. Record off-Main evidence at the I/O boundary. |
| `independentCallersConvergeOnFirstUse` | Gate at least eight independent source instances on one empty root, release them together on concurrent workers and compare every return with the on-disk value. A fresh instance reopens it. |
| `publicationKeepsAnExistingWinner` | Force two distinct valid candidates past the absence decision, or exercise the same production publication function with an incumbent already installed. The loser observes the incumbent. Its bytes and file identity survive. This case must deterministically detect an overwriting publication. |
| `invalidStorageIsPreserved` | Cover truncated/invalid bytes, canonical-path directory, symlinked file and symlinked private directory. Each fails without rotating the token, changing a sentinel outside the root or falling back elsewhere. Also cover inability to establish or verify the exclusion mark through a controlled failure. |
| `orphanCandidateIsNeverAdopted` | Leave a valid-looking partial candidate and an unrelated sentinel before first use. The final epoch differs from the partial and passes common validation. Reopen succeeds and unrelated content survives. |
| `foreignEpochClearsOnlyRestoreDialogs` | Under graph A publish a uniquely owned synthetic dialog and set a theme preference. A fresh source for root A reads the same pending dialog. Using the same real DataStore, switch to graph B backed by a distinct empty epoch root. Assert A and B differ on disk, B emits no old dialog, `restore_dialog_install_epoch` records B, and the seeded pending flag/owner are absent. The theme survives. Publish, observe and dismiss a distinct B dialog. |

For graph B, using the same preference store models transfer of preferences alone. Read raw preferences through the existing process-cached provider. Never copy or unlink a live DataStore file or create a second live store over it. Dispose owners, join each generation and let the fixture close its database once all borrowers finish. Restrict default-path writes to a newly provisioned simulator. Match `WORKEEPER_DATABASE_TEST_SIMULATOR` with `SIMULATOR_UDID` and validate the sandbox in each process. Refuse pre-existing directories unless the current run established ownership. Device cleanup is limited to the UUID created for this run.

**Relaunch proof.** Run the public-path case in two separate Kotlin/Native test processes against the same owned simulator and sandbox. Start the first with an absent token or prove that the current run created it there. Let that process exit before starting the second. Each must execute the exact passing case and compare its own return with the bytes. Bind the two fresh receipts to one run nonce, separate launch IDs and distinct PIDs. Compare UDID, canonical path and digest of the synthetic fixture file. Keep filtered XML/receipts separate from the full suite. Two process launches prove persistence across an ordinary process exit. Power-loss durability and an installed Swift app relaunch require different tests.

## Verification and review

Extend the existing [Native oracle](../../.github/scripts/assert_kmp_ios_smoke.py) and [source topology check](../../.github/scripts/assert_kmp_ui_source_topology.py). The latter currently pins an exact three-file host manifest and an exact seven-method oracle row. Add the production source, tests and six tuples explicitly. Retain the seven existing tuples, every other module's expectations, the 13 factory assertions, freshness checks and stable `KMP iOS kit smoke` job. In the [workflow](../../.github/workflows/android_build_unified.yml), run the two-process proof with an owned simulator and retain both receipts, including diagnostics on failure. Reusing the freshly linked simulator test executable avoids repeating a full build. Preserve the complete suite XML and all 21 device links.

Run two independent, exactly restored behavioral mutations. Allow publication to overwrite a valid incumbent and require the collision assertion to fail. Separately pass source A into graph B while leaving its independently verified B file intact and require the foreign-dialog assertion to fail. For each, record focused GREEN, compiled and executed RED, then restored GREEN with the repository mutation harness. Verify the validators accept complete fixtures and reject a missing new case, stale XML and reused or mismatched relaunch receipts.

Run the following in Bash from the repository root. Set `ENTRY_SHA` to the accepted implementation base and `EVIDENCE_DIR` to an absolute, fresh, run-specific evidence directory outside tracked sources. Provision the owned simulator and supported Android emulator first. Capture matching entry identities, schema/golden hashes and the two resolved release dependency reports before edits. Archive each gate's outputs before the next one can overwrite them. Run the two-process proof separately and preserve its actual invocations.

```bash
set -euo pipefail
: "${EVIDENCE_DIR:?set a fresh evidence directory}"
G=(--rerun-tasks --no-build-cache --no-configuration-cache --console=plain)
M=(core:ui:kit core:ui:navigation core:ui:mvi core:ui:start-mode core:ui:plan-editor
   feature:image-viewer feature:plan-editor feature:archive feature:all-trainings
   feature:all-exercises feature:past-session feature:home feature:exercise-chart
   feature:single-training feature:exercise feature:live-workout feature:app-dialogs:impl
   feature:settings app:common core:data:database app:ios)
S=()
D=()
for m in "${M[@]}"; do
  S+=(":${m}:iosSimulatorArm64Test")
  D+=(":${m}:linkDebugTestIosArm64")
done
./gradlew :app:ios:iosSimulatorArm64Test --tests '*IosInstallEpochSourceTest*' "${G[@]}"
./gradlew :app:ios:linkDebugTestIosArm64 "${G[@]}"
mkdir -p "$EVIDENCE_DIR/before-full-suite"
for m in "${M[@]}"; do
  module_dir=$(printf '%s' "$m" | tr ':' '/')
  xml_dir="$module_dir/build/test-results/iosSimulatorArm64Test"
  if [[ -d "$xml_dir" ]]; then mv "$xml_dir" "$EVIDENCE_DIR/before-full-suite/$m"; fi
done
for module_dir in feature/settings app/common core/data/database app/ios; do
  mkdir -p "$module_dir/build"
  touch "$module_dir/build/native-test-start"
done
./gradlew "${S[@]}" "${G[@]}" --no-parallel --continue
python3 .github/scripts/assert_kmp_ios_smoke.py
touch core/data/database/build/device-link-start
./gradlew "${D[@]}" "${G[@]}" --continue
linked=0
for m in "${M[@]}"; do
  module_dir=$(printf '%s' "$m" | tr ':' '/')
  binary="$module_dir/build/bin/iosArm64/debugTest/test.kexe"
  [[ -s "$binary" && "$binary" -nt core/data/database/build/device-link-start ]]
  xcrun vtool -show-build "$binary" | awk '$1 == "platform" && $2 == "IOS" { ok=1 } END { exit !ok }'
  linked=$((linked + 1))
done
[[ "$linked" -eq 21 ]]
printf '%s/21 fresh IOS device links\n' "$linked"
./gradlew assembleDebug lintDebug testDebugUnitTest :core:ui:mvi:testAndroidHostTest :app:common:testAndroidHostTest :core:data:database:testAndroidHostTest "${G[@]}" --continue
python3 .github/scripts/assert_mvi_host_identities.py
./gradlew assembleDebugAndroidTest "${G[@]}" --continue
./gradlew verifyPaparazziDebug :lint-rules:test "${G[@]}" --continue
./gradlew detekt "${G[@]}" --continue
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke "${G[@]}" --continue
python3 .github/scripts/assert_mvi_device_identities.py
# Archive Smoke XML before Regression overwrites it.
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression "${G[@]}" --continue
python3 .github/scripts/assert_kmp_ui_source_topology.py
python3 .github/scripts/assert_mvi_source_topology.py
python3 documentation/personal_data_gate.py -v
python3 documentation/mockups/shell_gate.py --base "${ENTRY_SHA:?set accepted implementation base}" -v
for app in dev store; do
  ./gradlew ":app:$app:dependencies" --configuration releaseRuntimeClasspath "${G[@]}" > "$EVIDENCE_DIR/$app-release-runtime.txt"
done
```

At the discovery ref #357 reported 963 Native cases across 21 modules. With an identical entry inventory the expected total is **969**, of which **13** belong to `:app:ios`. Filtered and relaunch executions are separate receipts. Compare exact task/class/method/status multisets. Android reference counts are 3,398 host cases, 44 Smoke with three known skips, and 90 Regression with zero skips. Check the seven schema exports, 456 golden PNGs and both phone release runtime dependency trees against entry. Record actual commands, exit codes, task counts, nonzero inputs, identities, artifacts and unverified hardware behavior. Cached, up-to-date, skipped or empty target work does not establish a pass.

**Edit boundary.** Apple production/tests under `app/ios`, the two validators, the scoped relaunch helper and its CI step/artifacts, this spec's evidence and focused architecture/testing/CI notes. Separate specifications own media/camera, date formatting, runtime replacement and recovery, Swift/Xcode packaging and App Store acceptance. Settings retains its unavailable iOS backup capability. Room, shared dialog policy, Android/Wear production, dependency pins, signing/release settings and privacy pages are outside the edit boundary.

Implementation starts after this spec lands in `dev` and Ilya gives GO. Stop on an unsupported publication primitive, unsafe device ownership, inability to distinguish the two processes, an unexplained identity/skip change, schema/golden movement, new suppressions/baselines or a missing mandatory gate. Use signed Conventional Commits. Open one implementation PR into `dev`, then obtain independent review of its exact head. Ilya owns the merge.


## Implementation evidence

Recorded on 2026-11-03 against accepted entry `e85c5b3ee8f34ef28cdf440ba1ccc4c70fe352ce`.
PR #358 was merged into dev; its head was `d1b4a2e495e34171c1116bcdf3ba274cfda3baaf`.
The accepted spec blob matched dev exactly: `13b60709efa13de0061b81ee099855bc5dea67f5`.

The isolated implementation worktree is `.claude/worktrees/kmp-7-20c-ios-install-epoch`.
Receipts are relative to the ignored, run-owned archive
`.claude/phase720c-evidence-20261103/`. Each JSON receipt retains the exact command,
UTC start/end, elapsed time, exit code and raw log. Full Native, Android-host, Smoke and
Regression XML and identity inventories are archived separately. Entry inventories came
from the preceding implementation's actual XML/archived receipts; its source differs from
the accepted entry only by this specification. Android host inventory includes all 65 custom
`verifyEmulatorAcceptanceRunner` cases, not only directories whose names start with `test`.

Host: Apple silicon, macOS 27.0, Xcode 27.0 (27A266a), JDK 21.0.11, Kotlin 2.4.10,
Gradle 9.6.1. The owned simulator uses iOS 26.5; Android uses Pixel_6_API_34,
API 34 / arm64-v8a. Long Native/Android gates ran on AC with an open lid.
A closed-lid interruption returned 143 and was excluded: its XML was archived and all
21 simulator tasks were restarted with fresh markers.

### Executed local gates

Every Gradle row used `--rerun-tasks --no-build-cache --no-configuration-cache --console=plain`.
The start/duration column is UTC and seconds. These are debug/test builds.

| Gate | Gradle summary | Observation | UTC start; duration | Receipt |
| --- | --- | --- | --- | --- |
| Storage P0 simulator | `350 actionable tasks: 350 executed` | 5 passing cases; no skips | 12:43:24; 100.4s | `p0-owned-storage.json` |
| Storage P0 device link | `349 actionable tasks: 349 executed` | debugTest IOS link; no device execution | 12:45:52; 78.7s | `p0-device-link.json` |
| Focused storage/consumer | `350 actionable tasks: 350 executed` | 6 passing cases; no skips | 12:48:47; 68.7s | `focused-six.json` |
| All 21 simulator tasks | `584 actionable tasks: 584 executed` | 969 passing cases; no skips; exact 963 retained + 6 added | 13:05:10; 349.8s | `native-full-resumed.json` |
| All 21 device links | `563 actionable tasks: 563 executed` | 21/21 fresh nonempty IOS executables; archived and hash-checked | 13:12:08; 130.4s | `device-links-full.json` |
| Android build, Lint and host tests | `3149 actionable tasks: 3149 executed` | 3398 cases; zero skips; exact entry identities retained | 13:15:07; 729.8s | `android-build-host.json` |
| Device-test compilation | `2210 actionable tasks: 2210 executed` | Compilation only | 13:29:21; 229.1s | `android-device-compile.json` |
| Paparazzi and lint-rules | `638 actionable tasks: 638 executed` | 1938 fresh passing cases; no skips | 13:33:10; 104.3s | `paparazzi-lint-rules.json` |
| Separate Detekt | `64 actionable tasks: 64 executed` | No rewriting | 13:34:55; 33.4s | `detekt-full.json` |
| Android Smoke | `2171 actionable tasks: 2171 executed` | 44 cases; the same 3 known skips; exact identities retained | 13:35:29; 336.3s | `smoke.json` |
| Android Regression | `2171 actionable tasks: 2171 executed` | 90 cases; zero skips; exact identities retained | 13:41:05; 348.0s | `regression.json` |

The complete Native oracle, MVI host/device oracles, source topology, MVI source topology,
privacy gate and rendered mockup gate passed. The mockup reports 11 passed / 0 failed.
See `native-oracle.json`, `mvi-host-oracle.json`, `smoke-oracle.json` and
`exit-source-gates.json`. Both shipping release runtime trees are identical to entry,
contain no `:app:ios` edge, and each dependency report states
`5 actionable tasks: 5 executed`. All seven schema exports and 456 golden PNGs are
byte-identical to entry; `final-input-comparison.json` records the comparisons.
Actionlint has only its unchanged entry SC2129 diagnostic and is not claimed green.

### Relaunch and controls

The current run created the public-path token during P0. The final freshly linked executable
then ran the public case in two sequential Native processes, one exact passing case per launch.
Nonce `01a72767-c19d-4970-8878-086eef7fa02f` matched in both.
PIDs were `4045` and `4061`; launch IDs were
`2cc15eca-7b6c-4968-bd7f-744c4e030125` and
`ea1165d3-eef6-4360-99c3-05cfdd462dbf`.
Expected and actual simulator UUIDs matched
`8FEB2A4D-2A76-4E43-A4CF-49C617C0C02C`.
The canonical installation path was equal, and both file digests were
`b43044f8c89fc1e55ae8a176ae800fa3189d37a5e5c21b01e0881fb6d5cd1b3c`.
The first process exited before the second began. Receipts and filtered logs are in
`native-relaunch-receipts/`, outside full XML; the simulator binary is IOSSIMULATOR,
while all 21 device binaries report IOS.

Two separate disposable worktrees executed the repository mutation harness. Every before,
mutated and restored invocation reports `350 actionable tasks: 350 executed`, with one exact
case per invocation. The mutated Gradle invocations exit 1; the harness exits 0 for expected RED.

| Mutation | Intended executed failure | Restored observation | Receipts |
| --- | --- | --- | --- |
| Remove the incumbent before publication | `publicationKeepsAnExistingWinner` returns the new candidate instead of the incumbent | Exact bytes/mode restored; focused GREEN | `control-publisher-r2/`, `control-publisher-r2-*.json` |
| Supply source A to graph B | `foreignEpochClearsOnlyRestoreDialogs` fails the foreign-dialog assertion after confirming B's distinct on-disk epoch | Exact bytes/mode restored; focused GREEN | `control-foreign/`, `control-foreign-*.json` |

Raw commands, failures, XML and all three stage binaries were preserved before both clean
control worktrees were removed without force. The original publisher control also produced
the correct RED; its receipt collector initially matched escaped XML instead of the decoded
message. That diagnostic remains in `control-publisher/`; the complete control was repeated
with the corrected collector. No production change was needed.

Synthetic complete validator fixtures passed. A missing new case, stale XML, a reused PID
and a mismatched simulator UUID each failed; exact restoration passed again.
See `validator-controls.json` and `check_validator_controls.py`. These fixture checks are
separate from executed Native test counts. The previous twenty oracle dictionaries, seven
graph identities/assertions and thirteen factory assertions remain intact.

### Evidence boundaries

Simulator execution proves storage, ordinary process-exit persistence and synthetic transfer
of existing preferences to a new installation root. IOS device binaries prove linking.
Physical-device execution, an installed Swift app relaunch, power-loss durability and actual
iCloud/Finder/device-transfer behavior remain pending separate acceptance.

The archive's `evidence-index.json` hashes the identity inventories, binary manifest,
input comparisons, relaunch receipts and control results.
Index SHA-256: `146a9f50f1838194caa46618bcc74159a912c6d5eeb6a869853df1b28b07b81e`.
