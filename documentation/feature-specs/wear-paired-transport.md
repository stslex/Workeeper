# Wear OS paired transport — Phase 1 increment 5a

**Status:** approved by the owner on 2026-09-29, ready for implementation.

**Amended on 2026-09-30** by owner decision after the PR-P review: store listing line 15 (D8, F19,
Appendix B3/B4) and the Privacy Sandbox advertising permissions (F25, §9.2 item 4, §10.2, §14).

- **Specification base:** `dev` at `e7e662442`. The release-pipeline stack (#297, #298, #299)
  merges before this work starts. It touches no protocol, runtime, bridge or gate source this
  specification relies on, but it shifts line numbers in `gradle/libs.versions.toml` (F2, F20) and
  edits documentation that PR-T updates later (`ci-cd.md`, `release-flow.md`,
  `wear-emulator-acceptance/README.md`).
- **Extends:** [Wear OS Phase 1](wear-phase-1-active-workout-tile.md) (protocol, reducer, cache,
  lifecycle, gates) and [Wear release pipeline](wear-release-pipeline.md) (delivery of 1.52.0).
- **Delivery:** three Android-only PRs into `dev` (PR-L telemetry redaction, PR-P privacy copy,
  PR-T transport), then release 1.52.0: phone to production, watch to `wear:internal`.

This specification connects the finished Phase 1 phone authority and watch runtime through the
Wear OS Data Layer, closes both Phase 1 privacy gates by owner decision, and publishes a privacy
policy that matches what the apps actually do. It does not change the Phase 1 protocol: wire
models, codec, fingerprints, reducer admission rules and phone validation stay as specified there.
Where this document and the Phase 1 specification disagree about protocol semantics, the Phase 1
specification wins and the implementation stops.

## 1. Goal and success criteria

The owner installs Workeeper 1.52.0 on his phone (Play production) and 1.52.0-wear on his watch
(Play `wear:internal`), starts a workout on the phone, and from the watch sees the current set,
edits weight and reps, and completes it, with the phone database showing exactly that set. The
same works when the phone's Bluetooth is off and both devices are online (relay route). The
public privacy policy describes every place workout data can go, and it is true.

Success means all of:

1. every host gate in §10 is green at each merged head, with each named mutation shown RED;
2. release 1.52.0 is live on both tracks through the existing pipeline;
3. the live privacy page is Appendix A;
4. the owner's physical checklist (§12.3) passes, or its failures are reported with evidence.

Items 1 to 3 are the implementer's. Item 4 is the owner's and is the only evidence that closes
this increment.

## 2. Owner decisions (locked)

| Id | Decision |
|---|---|
| D1 | **Gate 1 closed.** `docs/index.md` is replaced by the policy in Appendix A (English and Russian): the paired-device disclosure plus every other place workout data goes today. The owner approved that exact text on 2026-09-29. This is an owner-authorized, text-exact exception to the repository lock on `docs/index.md`; `docs/_config.yml` stays untouched. |
| D2 | **Gate 2 closed: permit end-to-end encrypted Data Layer relay.** Direct-only transport is not required. `Node.isNearby` is used only to prefer a direct node, never as a privacy guarantee. |
| D3 | **Transport:** `MessageClient` request/response (`sendRequest` on the watch, `WearableListenerService.onRequest` on the phone). No `DataClient`, no `ChannelClient`, no data items, no assets: nothing persists in the Data Layer. §5.5 defines the only permitted fallback. |
| D4 | **Pull only.** The watch asks, the phone answers. The phone never initiates a transfer. Phone-initiated change notification is a follow-up (§13). |
| D5 | **Scope of release 1.52.0:** transport enabled in both artifacts; phone to production, watch to `wear:internal` only. Wear production, the Wear listing text (Appendix A of the release-pipeline spec) and the reconnect-window calibration of Phase 1 §8 stay out of scope. |
| D6 | **Provisional constants** (§7.8) ship in 1.52.0 for internal testing. They are labeled PROVISIONAL in code and are not Phase 1 production lifecycle acceptance. |
| D7 | **Physical testing uses Play installs only.** A sideloaded APK is signed with the upload key, while Play installs carry the Play app-signing key. A mismatched pair cannot talk over the Data Layer, and replacing a Play install with a differently signed APK requires an uninstall, which deletes the phone's workout database. |
| D8 | **Store listing:** lines 14 and 15 of the phone `full_description.txt` (en-US, ru-RU) change per Appendix B. No Wear OS feature text is added to the listing. |
| D9 | **Telemetry hygiene (PR-L).** Store actions and events currently reach Google Analytics and Crashlytics as `toString()`, which includes names, weights and reps the user entered (F21). They are reduced to stable type names, and the audit of §9.2 removes every other entered value the app adds to telemetry. Advertising ID collection is turned off in both apps, which show no ads (F25). Appendix A's telemetry statements depend on this. |
| D10 | **Product copy follows reality:** `documentation/product.md` is corrected per Appendix C (paired watch, Drive backup, device backup, and the telemetry that exists). |

## 3. Verified facts at the specification base

Evidence is `path:line` at `e7e662442` unless stated otherwise. The implementer re-verifies each
row before editing (§14); pure line drift is recorded, a contradiction is a STOP.

| Id | Fact | Evidence |
|---|---|---|
| F1 | The phone already advertises the capability `workeeper_phone_active_workout_v1`; the resource comment says discovery is payload-free and authorized while the gates are open. | `feature/wear-bridge/src/main/res/values/wear_capabilities.xml:5,9` |
| F2 | Both artifacts already depend on `play-services-wearable` 20.0.1 and `kotlinx-coroutines-play-services`. | `feature/wear-bridge/build.gradle.kts:19-20`, `app/wear/build.gradle.kts:67-68`, `gradle/libs.versions.toml:76` (79 after the stack) |
| F3 | The phone authority is `PhoneWorkoutBridge` with `getActiveWorkout`, `completeCurrentSet`, `protocolRejected`, each taking the authenticated source node id. | `feature/wear-bridge/.../PhoneWorkoutBridge.kt:12-30` |
| F4 | A generation-bound admission seam for listeners exists: `WearBridgeWorkDepsHolder.awaitWearBridgeWorkLease` / `withWearBridgeWorkLease`; the phone `BaseApplication` implements the holder and `AppGraph` extends `WearBridgeDeps`. | `feature/wear-bridge/.../WearBridgeWorkLease.kt:10-25`; `app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt:54,109-110`; `app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt:56` |
| F5 | The bridge reports both gates open: `transportStatus = {PRIVACY_DISCLOSURE_REQUIRED, TRANSPORT_POLICY_REQUIRED}`. | `feature/wear-bridge/.../PhoneWorkoutBridgeImpl.kt:50-53`; `WearPayloadTransportStatus.kt:5-8` |
| F6 | The codec is complete for both sides: `encode` enforces 16,384 bytes and outcome/replacement pairing; `decodeForWatch` fails closed with `DecodeFailure`; `decodeForPhone` returns `Success`, `CorrelatedProtocolRejection` or `Dropped`. The operation travels inside the envelope. | `core/wear-protocol/.../WearProtocolCodec.kt:67-80,82-107,112-123`; `WireModels.kt:10-15` |
| F7 | The watch owner exposes `issueHandshake`, `receiveSnapshot`, `disconnected` and `onAction`, but no entry point for command responses, transport timeouts, retries or undecodable responses. `Retry` maps to `RefreshRequested`. | `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/runtime/WatchRuntimeOwner.kt:109-132,134-168` |
| F8 | The reducer already implements those semantics: `receiveUnsolicited`, `receiveCommandResponse` (returns `Unit`), `onTransportTimeout`, `issueTimeoutRetry`, `issueTypedRetry`, `markDisconnected` (retires any attempt binding), with `MAX_DELIVERY_ATTEMPTS = 2`. `issueHandshake` retires the current authority and clears `refreshRequired`. | `app/wear/.../state/WatchWorkoutReducer.kt:57-72,158,169,228,269,279,289-293,657` |
| F9 | Neither fingerprints nor the watch node id travel on the wire. The watch fingerprints commands locally with `identity.sourceNodeId` (attempt binding); the phone recomputes its own from the authenticated node id. | `WireModels.kt:34-44`; `WatchRuntimeOwner.kt:302-317`; `PhoneWorkoutBridgeImpl.kt:86-94,751` |
| F10 | The UI discards action results: `WearInteractor.perform` ignores what `onAction` returns. | `app/wear/.../domain/WearInteractor.kt:24-26` |
| F11 | The release runtime is `ReadOnlyWatchRuntime`; debug uses `WatchRuntimeOwner` with `DebugSnapshotDriver` and `DEBUG_UNCALIBRATED_RECONNECT_WINDOW_MS = 240_000L`. | `app/wear/src/release/.../runtime/WatchRuntimeFactory.kt:12-60`; `app/wear/src/debug/.../runtime/WatchRuntimeFactory.kt:34-51`; `DebugSnapshotDriver.kt:174` |
| F12 | `ReleaseRuntimeBoundaryTest` pins the read-only release runtime and the absence of synthetic sources; CI runs it on `storeRelease`. | `app/wear/src/testRelease/.../ReleaseRuntimeBoundaryTest.kt:29-62`; `.github/workflows/android_build_unified.yml:289-295` |
| F13 | The transport ban has three layers: detekt `ForbiddenImport` (three globs, "without an allowlist"), `WearDataLayerApiRule`, and `.github/scripts/assert_wear_transport_gate.py` (text scan of every tracked Kotlin file except `lint-rules/`, suppression ban, no `.java`), run in CI. | `lint-rules/detekt.yml:442-460,756-761`; `assert_wear_transport_gate.py:58-72`; `android_build_unified.yml:176-177` |
| F14 | The watch app is non-standalone and declares no Data Layer service; the phone library has no manifest. | `app/wear/src/main/AndroidManifest.xml:21-23`; no `feature/wear-bridge/src/main/AndroidManifest.xml` |
| F15 | The activity refreshes the runtime on create, resume and new intent; `onWake` also runs on ambient updates, scheduler deadlines and Tile frames, so it is not an entry signal. The Tile says transport wiring is absent. | `app/wear/.../MainActivity.kt:41,44,59-62,68`; `WatchRuntimeOwner.kt:87-99,326`; `app/wear/.../tile/WorkoutTileService.kt:13` |
| F16 | The retry surface appears for `TIMED_OUT_RETRYABLE` and `RETRY_READY`. | `app/wear/.../state/WatchInteractionEligibility.kt:18,38` |
| F17 | The public privacy page says workout data "is stored locally on your device and is never shared with third parties" and carries "Effective date: September 2025". It is served at `https://stslex.github.io/Workeeper/` (fetched 2026-09-29, same content). `CLAUDE.md` and `AGENTS.md` lock the file for agents. | `docs/index.md:15-16,62,85-86,129`; `feature/settings/.../about/AboutLinks.kt:8`; `CLAUDE.md:148`; `AGENTS.md:426` |
| F18 | Shipped and undisclosed: Google Drive backup (full database copies in the user's Drive `appdata` folder, three kept, email and profile read, no client-side encryption) and the optional AI assistant snapshot (plain JSON of all workouts in a visible `Workeeper/` Drive folder, `drive.file` scope, deleted on toggle-off or sign-out). | `documentation/feature-specs/backup.md:9-13,35-36`; `documentation/feature-specs/drive-ai-export.md:7-16,43-46`; `core/data/backup/.../DriveAuthScopes.kt:12-29`; `BackupConstants.kt:11`; `feature/settings/src/main/res/values/strings.xml:76-77` |
| F19 | The phone listing says "All data is stored locally on your device." and "No registration required, no personal data collected." | `fastlane/metadata/android/en-US/full_description.txt:14-15`; `ru-RU/full_description.txt:14-15` |
| F20 | The version is 1.51.0 (code 52) on `dev` and `master`; the release is 1.52.0 (code 53, Wear 1000053). | `gradle/libs.versions.toml:13-14` (16-17 after the stack) |
| F21 | Every store action and event reaches Google Analytics as the `action`/`event` parameter `toString()`, and every consumed action and sent event becomes a Crashlytics breadcrumb, even with logging off. Actions carry entered values (exercise and training names, weights, reps). Only `WearStore` overrides `toString()`. Class names of store actions are not kept by R8, so a plain `::class` name is obfuscated in release builds. | `core/ui/mvi/.../BaseStore.kt:121-127,153-166`; `core/ui/mvi/.../holders/StoreAnalytics.kt:12-28`; `core/core/.../logger/FirebaseEvent.kt:17-24`; `core/core/src/androidMain/.../FirebaseAnalyticsHolder.kt:19-25`; `core/core/.../logger/Log.kt:28-91`; `app/wear/.../mvi/store/WearStore.kt:25,28,39` |
| F22 | Both apps ship Firebase Crashlytics, Analytics and Performance; no in-app telemetry switch exists (product.md lists a crash-reporting toggle that is not implemented). | `app/wear/build.gradle.kts:63-66`; `build-logic/.../ConfigureApplication.kt:23-31`; `documentation/product.md:25-26,241,297` |
| F23 | The phone app allows Android backup and excludes only one preferences file, so the workout database is in device backups; the Wear app disables backup. | `app/app/src/main/AndroidManifest.xml:12-15`; `app/app/src/main/res/xml/data_extraction_rules.xml`; `app/app/src/main/res/xml/backup_rules.xml`; `app/wear/src/main/AndroidManifest.xml` (`allowBackup="false"`); `app/wear/src/main/res/xml/data_extraction_rules.xml` |
| F24 | Both release builds use the shared R8 rules (`proguard/proguard-rules.pro`, including the generic kotlinx-serialization rules), with no explicit keep for `core.wear.protocol`; the codec has never run in a minified release build. | `build-logic/.../ConfigureWearApplication.kt:48`; `proguard/proguard-rules.pro:1-11` |
| F25 | `firebase-analytics` collects the advertising ID by default and brings the `com.google.android.gms.permission.AD_ID` permission through its dependencies, together with the Privacy Sandbox permissions `android.permission.ACCESS_ADSERVICES_AD_ID` and `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` (seen in the release bundles, 2026-09-30); no manifest in the repository removes them or sets `google_analytics_adid_collection_enabled`. | `app/wear/build.gradle.kts:64`; `gradle/libs.versions.toml:265`; repository-wide search; Google Analytics help ("collects the Advertising ID by default"); the bundle check is D7 |
| F26 | The recovery screen lets the user share a raw copy of the data and a diagnostic file through Android's share sheet, and a failed restore offers the diagnostic share in a dialog. | `feature/recovery/.../RecoveryActivity.kt:122-133,166-180,194-195`; `feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/RestoreDialogChoiceObserver.kt:167-180` |
| F27 | Backups and AI snapshots also carry the app version and device model; snapshot deletion on toggle-off and sign-out is best-effort. The account preferences (email, name) live in the app's files and are not excluded from device backup. | `core/data/backup/api/.../model/BackupManifest.kt:5-10`; `core/data/database/.../export/model/WorkoutExportDto.kt:20-24`; `documentation/feature-specs/drive-ai-export.md:46`; `app/app/src/main/res/xml/data_extraction_rules.xml` |

Platform facts (documentation, 2026-09-29):

| Id | Fact | Source |
|---|---|---|
| P1 | "Data is automatically routed through Google Cloud when Bluetooth is unavailable. All data transferred through Google Cloud is end-to-end encrypted." Package name and signature must match across devices. | Data Layer overview |
| P2 | `MessageClient` supports "a request-and-response communication model using `sendRequest()`"; messages need connected nodes and have no built-in retry. `DataClient` items persist indefinitely and are backed up to the cloud. | Data Layer client types |
| P3 | `WearableListenerService` callbacks run on a background thread; the service must be `exported="true"`, and the official sample marks it `tools:ignore="ExportedService"`. The events page lists `DATA_CHANGED`, `MESSAGE_RECEIVED`, `CAPABILITY_CHANGED`, `CHANNEL_EVENT`. | Handle Data Layer events |
| P4 | Play Data safety: "User data that is sent off device, but that is unreadable by you or anyone other than the sender and recipient as a result of end-to-end encryption does not need to be disclosed." "Collect" means transmitting data off the device, including by SDKs. | Play Console Help, Data safety |

Assumptions the implementer verifies in discovery (§14) before writing transport code:

| Id | Assumption | If false |
|---|---|---|
| ASM-1 | In the resolved `play-services-wearable` 20.0.1: `MessageClient.sendRequest(String, String, byte[]): Task<byte[]>` exists; `WearableListenerService.onRequest(String, String, byte[]): Task<byte[]>` (nullable) exists; the manifest action for it is `com.google.android.gms.wearable.REQUEST_RECEIVED`. | Use the fallback of §5.5. Not a STOP. |
| ASM-2 | `WearableListenerService` accepts binder calls only from Google Play services, so the exported service is not callable by other local apps. | Record UNVERIFIED; the handler's routing (§6.2) and the bridge's validation are the control either way. |
| ASM-3 | detekt 1.23.8 honours rule-level `excludes` for `ForbiddenImport` and for the custom `WearDataLayerApiRule`. | Add an exact-path option to the custom rule, with tests (§8). |
| ASM-4 | GitHub Pages publishes `docs/` from `master`, so the new policy goes live when the release PR merges. | If it publishes from `dev`, it goes live when PR-P merges; record which. |

## 4. Architecture

```text
watch                                                       phone
─────                                                       ─────
MainActivity / Tile / ongoing
      │ actions, origin triggers (§7.4)
ConnectedWatchRuntime ── WatchRuntimeOwner (reducer, cache, ongoing; unchanged rules)
      │ tokens / responses
WatchTransportCoordinator  (single flight, late tokens, follow-up rule, budget)
      │ bytes
WearLink ── PlayServicesWearLink ═══ MessageClient.sendRequest ═══▶ WearRpcListenerService.onRequest
                        (Bluetooth direct, or Google relay, E2E encrypted)      │ lease (F4)
                                                                   PhoneWearRpcHandler
                                                                          │
                                                                   PhoneWorkoutBridge (unchanged)
```

Only `PlayServicesWearLink` and `WearRpcListenerService` name the Data Layer API (§8). Everything
else is plain Kotlin and host-tested with fakes.

## 5. Wire and transport contract

### 5.1 Constants

Added to `core/wear-protocol` `WearProtocol` (shared by both artifacts):

- `PHONE_CAPABILITY = "workeeper_phone_active_workout_v1"` (equals F1; a phone test pins the
  resource to the constant);
- `RPC_PATH = "/workeeper/wear/v1/rpc"`.

### 5.2 Discovery and node choice (watch)

1. For every request, query `CapabilityClient.getCapability(PHONE_CAPABILITY, FILTER_REACHABLE)`.
2. Choose a node with `isNearby == true` if any, otherwise any reachable node; ties break on the
   smallest node id. Relay through a non-nearby node is permitted by D2.
3. No reachable node: the request is not sent (§7.3 step 3).
4. The watch's own node id (`NodeClient.getLocalNode`) is resolved with the first request and kept
   for the process. It feeds only the local command fingerprint (F9); it is never sent.

### 5.3 Request and response

- Request bytes are `WearProtocolCodec.encode(GetActiveWorkoutRequest | CompleteCurrentSetRequest)`.
  `CompleteCurrentSetRequest` is built field for field from the owner's `FingerprintCommand` and the
  request token's correlation id; nothing is recomputed on the watch.
- The watch sends `MessageClient.sendRequest(nodeId, RPC_PATH, bytes)` and awaits the `Task`.
- A **non-empty** response is exactly one encoded `ActiveWorkoutSnapshotResponse` or
  `CompleteCurrentSetResponse`. An **empty** response means "no semantic response" (the Phase 1
  "drop"): the phone found nothing it may answer. The watch treats an empty response, a failed
  `Task` and a local timeout identically (§7.3).
- The phone never answers with anything other than those two shapes or empty.

### 5.4 Timeouts and sizes

| Constant | Value | Where | Meaning |
|---|---|---|---|
| `REQUEST_TIMEOUT_MS` | 10,000 | watch | Local deadline per request, around node lookup and `sendRequest`. PROVISIONAL. |
| `PHONE_ADMISSION_TIMEOUT_MS` | 5,000 | phone | Deadline for lease admission only; then empty. Once admitted, the bridge call runs to completion (§6.2). |
| `MAX_ENVELOPE_BYTES` | 16,384 | both (existing) | Enforced by the codec; far below the Data Layer message limit. |

A watch timeout while the phone is still working is the Phase 1 "lost acknowledgement": the
explicit retry converges through the receipt (`AlreadyApplied`) or `AuthorizationExpired`, never a
duplicate row.

### 5.5 Fallback when ASM-1 fails

Only if discovery shows that `WearableListenerService` cannot receive `sendRequest` calls in
20.0.1: use a message pair with the same bytes and the same empty-means-drop rule.

- Watch → phone: `sendMessage(nodeId, "/workeeper/wear/v1/request", requestBytes)`.
- Phone: `onMessageReceived` (action `com.google.android.gms.wearable.MESSAGE_RECEIVED`, exact
  path) runs the same handler and, for a non-empty answer, sends
  `sendMessage(sourceNodeId, "/workeeper/wear/v1/response", responseBytes)`.
- Watch: a runtime `MessageClient` listener on the response path, registered with the first
  request for the process lifetime, hands bytes to the coordinator, which matches them to the
  in-flight request by correlation id. A response with no matching in-flight request is ignored.

The fallback keeps the same two allowlisted files, the same timeouts and the same tests; the PR
records which variant shipped and why.

## 6. Phone side (`feature/wear-bridge`)

### 6.1 Components

- `feature/wear-bridge/src/main/AndroidManifest.xml`: declares `WearRpcListenerService`,
  `exported="true"` with `tools:ignore="ExportedService"` (P3), one intent filter: the verified
  action (ASM-1), `scheme="wear"`, `host="*"`, `path` equal to `RPC_PATH` exactly (not a prefix).
- `.../feature/wear_bridge/transport/WearRpcListenerService.kt`: the only phone file that names the
  Data Layer. It holds the action string as a constant that tests reference (no test may spell the
  Data Layer package, §8). It checks the path, obtains the bridge through the lease (F4) and runs
  the handler on a process-lifetime coroutine scope (`SupervisorJob`, background dispatcher),
  returning the result as a `Task`. The scope is not cancelled in `onDestroy`, because Play
  services may unbind the service before the `Task` completes. An unknown path returns `null` (not
  handled).
- `.../feature/wear_bridge/transport/PhoneWearRpcHandler.kt`: pure Kotlin, no Android or Data
  Layer types.

### 6.2 Handler contract

Input: authenticated source node id (from the Data Layer), request bytes, a `PhoneWorkoutBridge`.
Output: response bytes, possibly empty.

1. `bridge.transportStatus` not empty → empty. This keeps the enum as a compile-time kill switch.
2. `WearProtocolCodec.decodeForPhone(bytes, sourceNodeId)`:
   - `Success(GetActiveWorkoutRequest)` → `bridge.getActiveWorkout` → encode;
   - `Success(CompleteCurrentSetRequest)` → `bridge.completeCurrentSet` → encode;
   - `CorrelatedProtocolRejection(routing, reason)` → `bridge.protocolRejected` → encode;
   - `Dropped` → empty.
3. Any exception other than cancellation → empty, logged by exception class only.
4. Admission: `awaitWearBridgeWorkLease()` under `PHONE_ADMISSION_TIMEOUT_MS`; `null`, timeout, or
   an application that is not a `WearBridgeWorkDepsHolder` → empty. Once a lease is held, the
   bridge call and the encoding run to completion without cancellation, and the lease is released
   on every path. The bridge publishes its process-memory lease state after its database
   transaction, so a cancellation between the two must be impossible. A lease that is granted
   after the admission timeout has fired must be released at once: a leaked lease blocks every
   later restore's worker drain. A plain `withTimeout { awaitWearBridgeWorkLease() }` has exactly
   that race and is not acceptable.

The handler adds no validation of its own beyond this routing: authentication, idempotency, lease
and receipt rules stay in the bridge.

### 6.3 Gate state

`PhoneWorkoutBridgeImpl.transportStatus` becomes `emptySet()`. The enum and the check in §6.2
step 1 stay, with KDoc pointing at the Phase 1 §6.1 closure record. The capability resource comment
(F1) is updated to say transport is authorized by that record.

### 6.4 Non-changes

No new permission, no database schema or migration change, no change to `PhoneWorkoutBridgeImpl`
beyond `transportStatus`, no phone UI change. A phone without a paired Workeeper watch never runs
this code: Google Play services starts the service only for a request from the same package and
signature (P1).

## 7. Watch side (`app/wear`)

### 7.1 Components

- `.../wear/transport/WearLink.kt`: the interface the coordinator uses (local node id, reachable
  phones with `isNearby`, one request with a result of bytes or a classified failure, and a
  reachability observer). Exact shape is the implementer's.
- `.../wear/transport/PlayServicesWearLink.kt`: the only watch file that names the Data Layer.
- `.../wear/transport/WatchTransportCoordinator.kt`: pure Kotlin; owns sending, timeouts,
  triggers, the follow-up rule and the budget.
- A connected `WatchRuntime` (for example `ConnectedWatchRuntime`) that delegates to the owner and
  hands `CommandIssued` / `RefreshRequested` results and origin triggers to the coordinator.
- `WatchRuntimeOwner` additions (§7.5) and the release factory (§7.7).

Debug variants keep `DebugSnapshotDriver` and never construct the link, so every existing
synthetic and emulator acceptance suite keeps its meaning. Both release variants (`devRelease`,
`storeRelease`) use the real transport.

### 7.2 Coordinator invariants

- **Single flight:** at most one request in flight; the rest wait in FIFO order.
- **Late handshake tokens:** a refresh is queued as an intent and receives its handshake token
  from the owner only when it starts, never while another request is in flight. Issuing a
  handshake retires the current authority, including a command's attempt binding (F8, Phase 1 §3).
- **Retry preservation:** while the visible command is `TIMED_OUT_RETRYABLE` or `RETRY_READY` and
  its binding is inside its deadline, no automatic refresh starts. This is checked when the
  refresh would start, not only when it was requested; a suppressed automatic refresh is dropped.
  The user's `Retry` decides.
- **Coalescing:** a refresh is dropped when a handshake is already queued or in flight. Commands
  are never dropped or merged.
- **Finite follow-ups:** every request belongs to a chain started by one origin (§7.4). A chain
  holds at most one automatic follow-up handshake; a follow-up never starts another.
- **Budget:** at most 6 automatic handshakes per rolling 60 s (`AUTO_REFRESH_BUDGET`,
  PROVISIONAL), as a last breaker. A user action is not counted and not limited.
- **No polling:** no timer, alarm, wake lock or loop issues requests; every request has an origin.

### 7.3 Request lifecycle

1. The owner issues the token (`issueHandshake` when a refresh starts, or `CommandIssued` from
   `onAction`); the owner remains the only source of correlation ids, generations and
   fingerprints.
2. The coordinator encodes the request (§5.3).
3. No reachable phone: a handshake reports unreachable to the owner (§7.5); a command reports a
   transport timeout for its correlation immediately.
4. Otherwise send and wait up to `REQUEST_TIMEOUT_MS`.
5. Classify:
   - non-empty bytes → `decodeForWatch`:
     - `Success` with the request's correlation id and the matching type: snapshot →
       `receiveSnapshot`; command response → `receiveCommandResponse`;
     - `Success` with the request's correlation id but the other type → protocol failure for that
       correlation (Phase 1: a shape change for one correlation is a protocol failure);
     - `Success` with another correlation id → treated as no response (Phase 1 ignores unknown
       correlations);
     - `ProtocolMismatch(failure)` → protocol failure for the request's correlation;
   - empty bytes, `Task` failure or timeout → handshake: unanswered; command: transport timeout.
6. Then apply the follow-up rule of §7.4 once.

### 7.4 Triggers

Origins (each may start a chain):

| Id | Origin | Condition |
|---|---|---|
| O1 | Controller becomes interactive | a dedicated signal from `MainActivity`: resumed while not ambient, or ambient exit. `onWake` is not an origin (F15). |
| O2 | Tile request | after rendering, and only if no handshake completed (success or failure) within `TILE_REFRESH_MIN_AGE_MS` = 60,000 (PROVISIONAL) |
| O3 | Phone reachability changes from none to some | controller interactive |
| O4 | Local mutation authority expires (fresh → stale) | controller interactive; once per expiry |
| O5 | User action | `CompleteSet` (command) or `Retry` (§7.5) |

Follow-up (at most one per chain, never from a follow-up): one handshake when, after a request of
the chain completes, `refreshRequired` has turned true or an accepted snapshot is
`ActiveWithTarget` + `Unavailable` (Phase 1 §3: "immediately requests a correlated handshake").
No follow-up while the display is protocol mismatch, and none for a chain started by O2 or O4 once
the controller is no longer interactive.

A phone that always answers `Unavailable` therefore costs at most two handshakes per origin, and
nothing without a new origin.

### 7.5 Owner additions

All run inside the owner's serialized `transition`, with the same schema-version and boot checks
as `receiveSnapshot`. Reducer and ongoing changes are allowed only as new entry points or return
values that expose or reuse existing transitions; admission, ordering, attempt, fingerprint and
lifecycle rules do not change, and existing reducer tests stay green except where they assert a
changed return type.

- `receiveCommandResponse(response)`: reducer `receiveCommandResponse` at the current monotonic
  time, returning its attached-snapshot reduction; when that snapshot is accepted, cache, ongoing
  and tombstone handling follow the same path as an accepted handshake snapshot.
- `transportTimeout(correlationId)`: reducer `onTransportTimeout`.
- `handshakeUnanswered(correlationId, phoneReachable)`: unreachable → the existing `disconnected()`
  behavior (which retires any attempt binding, F8); reachable but unanswered → a link status only.
  Neither creates authority.
- `protocolFailure(correlationId, failure)`: fail closed per Phase 1 §9: protocol-mismatch display,
  authority retired, draft cleared, a pending command with that correlation closed without retry,
  and the ongoing surface stopped (Phase 1 §8).
- `Retry`: `TIMED_OUT_RETRYABLE` → `issueTimeoutRetry`; `RETRY_READY` → `issueTypedRetry`; an
  issued retry returns `CommandIssued` with the command's current fingerprint and a new
  correlation id. A rejected retry returns `RefreshRequested` (today's behavior). One logical
  command never exceeds two attempts (F8).
- A link status (unknown, reachable, unreachable, unanswered) on `WatchRuntimeSnapshot`. A
  `Loading` display after a failed handshake must not load forever: unreachable maps to the
  `DISCONNECTED` ("Phone unavailable") surface and unanswered to `RETRYABLE_ERROR`, both with a
  `Retry` that issues a refresh. If the `DISCONNECTED` surface cannot render without a snapshot,
  both use `RETRYABLE_ERROR` and the PR says so. Existing strings only; the string-coverage,
  overflow and first-view gates stay green.

### 7.6 Identity

In release, `RuntimeIdentity.sourceNodeId` is the local node id resolved by §5.2 step 4. A command
always follows an accepted handshake, so the id is resolved by then; if it is not, `CompleteSet` is
rejected. Debug keeps `"synthetic-watch"`.

### 7.7 Release factory and build

`app/wear/src/release/.../runtime/WatchRuntimeFactory.kt` builds, once per process:

- `WatchRuntimeOwner` with `AtomicFileRecordStorage(noBackupFilesDir/"watch_snapshot")`,
  `ElapsedRealtimeClock(SystemClock::elapsedRealtime)`, the `BOOT_COUNT` provider,
  `AndroidOngoingNotification`, `OngoingPolicy(RELEASE_PROVISIONAL_RECONNECT_WINDOW_MS)`, random
  `CanonicalUuid` ids, and a coroutine deadline scheduler (the debug one, moved to `main`);
- `PlayServicesWearLink`, `WatchTransportCoordinator`, the connected runtime, and
  `observeWorkoutTile`.

Neither the factory nor the link calls Google Play services before the first request, so the
factory constructs under Robolectric and on a watch without Play services (every request then
fails as unreachable). The first request also registers the reachability observer, kept for the
process lifetime: reachability lost → owner `disconnected()`; reachability gained → O3.
`handleDebugScenario` stays `false`; no synthetic class is reachable from release.
`ReadOnlyWatchRuntime` and any code only it used are deleted.

R8: `proguard/proguard-rules.pro` gains
`-keep class io.github.stslex.workeeper.core.wear.protocol.** { *; }`, with a comment modeled on
the existing export-DTO rule (F24).

### 7.8 Provisional constants

| Constant | Value | Why this value |
|---|---|---|
| `RELEASE_PROVISIONAL_RECONNECT_WINDOW_MS` | 270,000 | Phase 1 §8: at least the documented four-minute reconnection interval plus a margin (30 s). Unmeasured. |
| `REQUEST_TIMEOUT_MS` | 10,000 | Covers a cold phone start plus relay latency; two attempts stay inside the 120 s mutation window. |
| `TILE_REFRESH_MIN_AGE_MS` | 60,000 | The platform's Tile refresh throttle; prevents a render → refresh → update → render loop. |
| `AUTO_REFRESH_BUDGET` | 6 per 60 s | Last breaker only; the follow-up rule already bounds chains. |

Each carries a KDoc saying PROVISIONAL, internal testing only, and pointing at this section. None
closes the Phase 1 §8 STOP on measured reconnect behavior.

### 7.9 Logging

Transport logs on both sides carry only: operation name, result class, byte counts and elapsed
milliseconds. Never payload bytes, names, UUIDs, weights, reps or node ids. Crashlytics custom keys,
breadcrumbs and non-fatal messages follow the same rule.

## 8. Opening the gate

The allowlist is exactly these two files:

1. `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/WearRpcListenerService.kt`
2. `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/transport/PlayServicesWearLink.kt`

Every layer keeps its ban for every other file:

1. **detekt `ForbiddenImport`:** the three globs stay. The two files are excluded by rule-level
   `excludes` entries of the form `**/<the full repository path above>`: detekt matches absolute
   paths, so the leading `**/` is required, and nothing else in the pattern may be a wildcard.
   The GUARD comment is rewritten to point at this section and to say that the allowlist is two
   files and that widening it is a privacy decision. The two files thereby also leave
   ForbiddenImport's other entries; review covers that.
2. **`WearDataLayerApiRule`:** the same two `excludes` entries. If ASM-3 fails, add an exact-path
   option to the rule with its own tests instead.
3. **`assert_wear_transport_gate.py`:** the exact-path authority. Check 1 gains an exemption set
   holding the two repository paths as exact strings (no prefixes, no globs); the `lint-rules/`
   exemption stays as it is; checks 2 and 3 are unchanged, so a suppression inside an allowlisted
   file still fails. `--self-test` gains the cases of §10.3.
4. **Tests never spell the Data Layer package**, including action strings: they reference
   constants declared in the allowlisted files, or `WearProtocol` constants.
5. **Documentation** made false by the change is updated: `documentation/lint-rules.md`
   (`WearDataLayerApiRule`), `documentation/ci-cd.md` (gate, release boundary),
   `documentation/architecture.md` and `documentation/features.md` where they describe Wear,
   Phase 1 §8.1 ("Release remains read-only"), `wear-lifecycle-ui.md`, `wear-style-mvi.md`,
   `documentation/wear-emulator-acceptance/README.md`. The PR lists each file and the sentence it
   corrected.

## 9. Privacy copy and telemetry

### 9.1 Copy

- `docs/index.md`: replaced by Appendix A and verified by hash. This is the only exception to the
  lock (F17) and covers only this text.
- Phone listing: Appendix B. Product copy: Appendix C. Phase 1 record: Appendix D, landed with
  this specification.
- **Live evidence:** after the release PR merges (or after PR-P merges, if Pages publishes from
  `dev`), fetch `https://stslex.github.io/Workeeper/` and record that it contains the headings
  "Wear OS companion app", "Crash reports, performance and usage statistics" and
  "Приложение для Wear OS". Allow 30 minutes for the Pages build.
- Appendix A states facts about 1.52.0 (no entered content in the app's telemetry, advertising ID
  off), so PR-L merges before PR-P and both ship in the same release.

### 9.2 PR-L: telemetry hygiene

1. `StoreAnalytics.logAction` / `logEvent` send the action or event **type name** only, never
   `toString()`. The name must survive R8 (for example a `-keepnames` rule for implementers of
   `io.github.stslex.workeeper.core.ui.mvi.Store$Action` and `Store$Event`), shown with the
   release mapping file. Lifecycle logging is unchanged.
2. Every `BaseStore` breadcrumb that interpolates an action or event ("consume", "consume skipped",
   "sendEvent", the event-buffer warning) carries the type name only.
3. Audit every `Log`/logger call, Crashlytics breadcrumb, custom key and non-fatal message in the
   production source sets of `app/`, `feature/` and `core/`. Any that can carry an entered value
   (names, notes, tags, weights, reps, dates the user typed, file names) is reduced to a type name,
   a count or a fixed label. The PR lists every call site examined and the decision. Exception
   messages produced by libraries are out of reach; Appendix A says so.
4. Advertising ID off in both apps: each application manifest (`app/store`, `app/dev`, `app/wear`;
   `app/app` is a library they consume) sets `google_analytics_adid_collection_enabled` to `false`
   and removes `com.google.android.gms.permission.AD_ID` with `tools:node="remove"` (the Wear
   manifest needs the `tools` namespace declared). The meta-data is required as well: below
   Android 13 the advertising ID is readable without the permission. `assert_play_bundle.py`
   gains a check G8, "no `AD_ID` permission in the base manifest", for both roles, with self-test
   fixtures. G8 sees only the store phone and Wear bundles; `app/dev` is covered by review.
   Amendment 2026-09-30: the same manifests also remove `android.permission.ACCESS_ADSERVICES_AD_ID`
   and `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`, and the
   `android.adservices.AD_SERVICES_CONFIG` property when the merged manifest has one; G8 checks
   all three permissions. PR-L had already merged, so this ships as a separate commit in PR-T.
5. Tests: a store action and an event carrying a sentinel string and sentinel numbers; the
   analytics parameters and every breadcrumb contain neither.
6. No telemetry is added or removed; only its content changes, and the advertising ID stops.

### 9.3 Owner steps outside the repository

- **Data safety:** compare the form with Appendix A. Expected: no new declaration for Wear (P4:
  relay transit is end-to-end encrypted; the direct route runs between the user's own devices).
  Confirm independently of this increment: App activity (usage events), App info and performance
  (crash logs, diagnostics), Device or other IDs (Firebase installation identifiers), and whether
  the Drive backup and the AI assistant snapshot, which leave the device without end-to-end
  encryption into the user's own Drive, need a declaration.
- **Advertising ID declaration** (App content): after 1.52.0 is live, the apps no longer use the
  advertising ID.
- **Optional:** a Google Analytics data-deletion request for the `action` and `event` parameters
  collected before 1.52.0, and the shortest data-retention setting.

## 10. Test contract

### 10.1 Host tests (new)

Phone (`:feature:wear-bridge`, JUnit 5, Robolectric where Android types are needed):

- handler: each `decodeForPhone` branch; empty for `Dropped`, a non-empty `transportStatus`, a
  `null` lease, an exception, and the admission timeout; an admitted call that outlives the
  admission timeout still completes and releases its lease; a lease granted just after the
  timeout is released at once;
- handler end to end on the in-memory database (`:core:data:database-test`) with the real
  `PhoneWorkoutBridgeImpl`: handshake bytes → snapshot bytes that `decodeForWatch` accepts; a
  complete command built from that snapshot → `Applied` and one written set; the same attempt
  replayed → the same response and no second row;
- manifest: a `PackageManager` query for the service's action constant with data
  `wear://any/workeeper/wear/v1/rpc` resolves to the service; `wear://any/workeeper/wear/v1/rpcx`
  does not (or an equivalent check of the merged manifest);
- capability: `R.array.android_wear_capabilities` equals `[WearProtocol.PHONE_CAPABILITY]`.

Watch (`:app:wear`, `testDebugUnitTest`, with a fake `WearLink` that serves bytes produced by the
real codec):

- handshake success; from `Loading`, no reachable phone and an unanswered handshake each reach the
  surface §7.5 assigns, with a working `Retry`; undecodable bytes and a wrong type for the
  request's correlation → protocol mismatch, ongoing stopped, no retry; another correlation id →
  no response;
- command `Applied`: set advances, ongoing and cache updated from the attached snapshot;
- timeout → `Retry` → second attempt with the same `commandId` and a new correlation id; second
  timeout → abandoned, one follow-up refresh, never a third attempt;
- typed retryable outcome with an accepted successor → `Retry` → rebound attempt;
- a refresh requested while a command is in flight gets its token only after the command finishes;
  a queued automatic refresh that would start while the command is retryable is dropped;
- single flight, coalescing, the follow-up rule (a phone that always answers `Unavailable`: exactly
  two handshakes per origin and none afterwards), the budget, O2 minimum age, no origin in ambient;
- node choice prefers `isNearby`; the release identity reports the resolved local node id;
- transport logs contain none of the forbidden fields (§7.9).

Release (`testStoreReleaseUnitTest --tests '*ReleaseRuntimeBoundaryTest*'`): the release runtime
is the connected owner-backed runtime; synthetic classes and the acceptance receiver stay absent;
`handleDebugScenario` is `false`; with an empty cache the Tile declares no freshness interval.

Telemetry (PR-L): §9.2 items 4 and 5.

### 10.2 Named mutations

Each is applied, run RED with the command and exit code recorded, restored, and run GREEN.

| Id | Mutation | Must fail |
|---|---|---|
| M-L1 | `StoreAnalytics` sends `toString()` again | sentinel analytics test |
| M-L2 | the consume breadcrumb interpolates the action again | sentinel breadcrumb test |
| M-L3 | the `sendEvent` breadcrumb interpolates the event again | sentinel breadcrumb test |
| M-L4 | the `AD_ID` removal is dropped from `app/store` (and, separately, from `app/wear`) | bundle gate G8 (PR CI) |
| M-L5 | the `ACCESS_ADSERVICES_AD_ID` removal is dropped from `app/store` | bundle gate G8 |
| M-T1 | handler ignores `transportStatus` | kill-switch test |
| M-T2 | handler answers a `Dropped` request with a snapshot | handler branch test |
| M-T3 | the admission timeout wraps the bridge call | outlives-timeout test |
| M-T3b | a lease granted after the admission timeout is not released | late-lease test |
| M-T4 | manifest filter uses `pathPrefix="/workeeper/"` | manifest test (`rpcx` resolves) |
| M-T5 | capability resource renamed | capability test |
| M-T6 | coordinator allows two requests in flight | single-flight test |
| M-T7 | refresh token issued at enqueue time | late-token test |
| M-T8 | retry suppression checked only at request time | queued-refresh test |
| M-T9 | a follow-up may start another follow-up | always-`Unavailable` test |
| M-T10 | owner `Retry` issues a third attempt | attempt-bound test |
| M-T11 | empty response mapped to protocol mismatch | unanswered test |
| M-T12 | another correlation id mapped to protocol mismatch | correlation test |
| M-T13 | Tile origin ignores the minimum age | Tile loop test |
| M-T14 | node choice ignores `isNearby` | node-choice test |
| M-T15 | release factory returns the read-only runtime | `ReleaseRuntimeBoundaryTest` |
| M-T16 | a third file imports `com.google.android.gms.wearable` | detekt and the gate script |
| M-T17 | `@Suppress("WearDataLayerApiRule")` in an allowlisted file | gate script check 2 |
| M-T18 | allowlist entry turned into a directory prefix | gate script self-test |
| M-T19 | a transport log line includes the payload | logging test |

### 10.3 Gate self-test additions

`assert_wear_transport_gate.py --self-test` proves: an allowlisted file naming the package passes;
a sibling file in the same directory fails; a file whose path merely ends with an allowlisted path
fails; a suppression inside an allowlisted file fails.

### 10.4 Existing gates

Unchanged and green: root `testDebugUnitTest`, `lintDebug`, `detekt` (zero new suppressions; the
single `tools:ignore="ExportedService"` in the manifest is lint, documented by P3, and listed in
the PR), `assembleDebug`, `assembleDebugAndroidTest`, both release bundles and the bundle identity
gate, the mockup gate, the gate script, `ReleaseRuntimeBoundaryTest`, and every Wear emulator
acceptance suite that CI runs.

### 10.5 Device evidence

A paired phone and Wear emulator, or a minified build on an emulator, is optional. If attempted,
record the steps and results; it never blocks and never substitutes for §12.3.

## 11. Failure modes

| Situation | Watch | Phone | Data |
|---|---|---|---|
| Phone app older than 1.52.0 or missing | no capable node: "Phone unavailable" | — | none |
| Bluetooth off, both online | relay node used | answers normally | normal |
| No connectivity at all | unreachable; ongoing stops after the reconnect window | — | none |
| Request or response lost | timeout → one explicit resend → abandon and one refresh | may have applied | receipt makes the resend `AlreadyApplied`; no duplicate row |
| Reachability flaps during a command | `disconnected()` retires the attempt binding; `Retry` becomes a refresh | may have applied | the refresh shows the truth |
| Phone restoring a backup | empty answer → unanswered | lease not admitted | none |
| Watch process killed mid-command | cache restores display-only; no authority | may have applied | next handshake shows the truth |
| Incompatible schema | protocol mismatch, mutation disabled, no follow-up | drops | none |
| Phone app cold | first request slower; it may time out once | started by Play services | normal |
| Watch re-paired (new node id) | new id after restart | typed rejection of old leases | none |

## 12. Release and acceptance

### 12.1 Order

1. The release-pipeline stack (#297 → #298 → #299) is merged.
2. This specification lands on `dev` with Appendix D.
3. PR-L (§9.2) is merged; PR-T (§5 to §8, §10) may be developed in parallel.
4. PR-P (Appendix A, B, C) is merged after PR-L.
5. PR-T is merged.
6. `android_build_unified.yml` is dispatched on `dev` with `execute_unit_tests=true` and is green
   (CI does not run on pushes to `dev`).
7. 1.52.0 is cut and deployed through `cut_release.yml` and `android_deploy_prod.yml` exactly as
   [release-flow.md](../release-flow.md) §6.1 describes; the owner approves the `production`
   environment; recovery follows release-flow.md §8.

### 12.2 Implementer evidence after release

- Play shows 1.52.0 (53) on production and `1.52.0-wear` (1000053) on `wear:internal` (from the
  run logs).
- A re-run of the Wear job ends in SKIP.
- The live privacy page check of §9.1.
- Ledger rows in §15 and in the release-pipeline spec §14.

### 12.3 Owner physical checklist

Setup: phone 1.52.0 from Play production (review can take hours), watch `1.52.0-wear` from Play on
the watch (the owner is a tester of the Wear internal track), both from Play (D7). Notifications
allowed on the watch. For P7 the watch needs Wi-Fi or LTE and its companion app's cloud connection
enabled.

| Id | Step | Expected |
|---|---|---|
| P1 | No workout on the phone; open the watch app | "Start a workout on your phone" within a few seconds |
| P2 | Start a workout on the phone; open the watch app | training, exercise, set X of Y, weight and reps |
| P3 | Change reps and weight on the watch, tap Complete set | the phone shows that set completed with those values; the watch moves to the next set |
| P4 | Complete a set on the phone, then reopen the watch app | the watch shows the new current set |
| P5 | Add the Workeeper Tile | shows the current workout; tap opens the controller |
| P6 | Leave the watch alone during an active workout | ongoing indicator on the watch face; it disappears about 6 to 7 minutes after the last watch refresh (120 s mutation window + 270 s provisional reconnect window); note the actual time |
| P7 | Phone Bluetooth off, both online; repeat P3 | works, possibly slower (relay); a first attempt may time out and succeed on Retry |
| P8 | Watch in airplane mode; open the app | "Phone unavailable", Complete disabled; back online and reopened → current set |
| P9 | Swipe the phone app away; repeat P3 | works (Play services starts the phone app) |
| P10 | Finish the workout on the phone; reopen the watch app | no-session state; ongoing indicator gone |
| P11 | Restart the watch; open the app | no stale workout shown after reboot; then current state |
| P12 | After the session, check the phone history | exactly the sets completed, no duplicates, including those done during P7 to P9 |

The owner reports pass or fail per row with a note (and a screenshot for a failure). Failures feed
a 1.52.x fix.

## 13. Out of scope

Wear production and its listing text; phone-initiated change notification (push); measured
reconnect window and notification timeout tolerance (Phase 1 §8); any protocol, schema or
fingerprint change; Health, sensors, watchOS, KMP; Data safety edits (§9.3 is the owner's); an
in-app telemetry switch; a Wear APK on GitHub Releases; the disabled beta workflow.

## 14. Discovery (before editing) and STOP conditions

Discovery rows, each with command, evidence and verdict:

- **D1** ASM-1 by `javap` on the resolved AAR's `classes.jar` (`MessageClient`,
  `MessageClient$RpcService`, `WearableListenerService`) and a string search for the
  `REQUEST_RECEIVED` action in that jar or in the official reference; the verdict selects §5.3 or
  §5.5.
- **D2** ASM-2 by the same means; UNVERIFIED is acceptable.
- **D3** ASM-3 by a throwaway local run (not committed).
- **D4** ASM-4 by `gh api repos/stslex/Workeeper/pages --jq '.source'`.
- **D5** F1 to F27 re-verified at the current `dev` head.
- **D6** The base sha256 of each file in Appendices A to C.
- **D7** F25 on the artifacts: `bundletool dump manifest` of both release bundles (or the merged
  release manifests) shows which advertising permissions are present before the change.

STOP and report when:

- a fact in §3 is contradicted in a way that changes the design (not line drift);
- neither §5.3 nor §5.5 is supported by the resolved library;
- the new `docs/index.md` differs from Appendix A's result hash; or an OLD block of Appendix B or
  C does not occur exactly once, or its result hash differs while the base hash matched;
- the work would change a wire model, the codec, a fingerprint, reducer admission, phone
  validation, the database schema, or a permission (removing the advertising permissions per
  §9.2 item 4 excepted);
- a gate can only be opened by a wildcard other than detekt's leading `**/`, by a directory
  prefix, or by a suppression;
- the telemetry audit of §9.2 finds entered content it cannot remove without a behavior change,
  or an advertising permission (`AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`)
  survives into a release bundle;
- a named mutation cannot be made RED;
- the phone or Wear bundle identity gate fails;
- anything would claim physical acceptance or a measured constant.

## 15. Implementation ledger

Append-only. One row per PR, release step and acceptance item.

| Date | Item | Commit or run | Evidence |
|---|---|---|---|
| 2026-09-30 | Specification on `dev` | cb9ba666 | This file at sha256 `ebb75ccd…`, and Appendix D in the Phase 1 specification. The release-pipeline stack merged first: #297 → 8303c4ae, #298 → c128a3ca, #299 → 20dd4abd. |
| 2026-09-30 | PR-L #300: telemetry hygiene | 9b900470 (head ac81cc56); run 36685260433 | Store actions and events reach telemetry as type names, with a local exact-text dedupe key. Action and event names are kept in R8. The `AD_ID` permission is removed and advertising-ID collection is off (G8). Every CI build-job gate passed without the build cache, and M-L1 to M-L13 went RED then GREEN. Codex raised three findings, all fixed: history in a comment, the debounce undercount, and the hash-collision key. |
| 2026-09-30 | Owner amendment: listing line 15, Privacy Sandbox permissions | 5f71f53f | sha256 `841bd61b…`. Changes D8, F19, F25, §9.2 item 4, §10.2 (M-L5), §14 and Appendix B3/B4, after the PR-P review. |
| 2026-09-30 | PR-P #301: policy, listing, product copy | 41bb0c85 (head 47737acc); run 36694784196 | Appendices A, B1 to B4 and C applied; every result hash matches. Codex: the `docs/index.md` lock was answered as already decided (D1, run authorization A2); listing line 15 led to the amendment. |
| 2026-09-30 | PR-T #302: paired transport | 7237c12f (head c19e668a); run 36704826608 | §5 to §8 and the amended §9.2 item 4 (G8 checks all three advertising permissions). At the final head: 3050 unit tests, 93 lint tasks and 1707 screenshot tests green, and both bundles pass G8. 28 mutations (M-T1 to M-T25, M-T3b, M-L5) went RED then GREEN. The independent review's 18 findings were classified in the PR. Codex: four findings fixed (two on observer registration, the link status on capability loss, KDoc history); one answered as wrong, citing §7.7. |
| 2026-09-30 | Release 1.52.0, phone | runs 36734395534, 36766141762, 36783501185; tag `release-v.1.52.0` → c019f701; #303 → cfbd1c8e | The production upload of versionCode 53 was committed at 22:17:23Z. Two earlier attempts stopped before any commit to Play. The UI-test SDK setup was fixed in #304; supply's memoized `metadata_path` was fixed in #305 and in c019f701 on the release branch. |
| 2026-09-30 | Release 1.52.0, Wear | run 36783501185 (`deploy_wear`) | Failed before upload. Play lists `wear:internal` with no releases, which crashed `PlayState.tracks`; fixed in #307. The tag's commit could not carry the fix, so the owner chose a 1.52.1 hotfix. |
| 2026-09-30 | Policy live (§9.1) | Pages builds of cfbd1c8e and 96a2588e | https://stslex.github.io/Workeeper/ served the three §9.1 headings at 22:23:28Z, 6 minutes after the master merge, and again at 2026-10-01 05:09Z. |
| 2026-10-01 | Hotfix 1.52.1, phone and Wear | cut run 36809449963; deploy run 36811500839; Wear run 36817361794; tag `release-v.1.52.1` → 26ad9868; #308 → 96a2588e | The full pipeline ran, smoke UI tests included. Phone versionCode 54 was committed to production at 04:47:45Z. Wear 1000054 went to `wear:internal` at 05:03:24Z: DECISION UPLOAD, after the expected Wear DRIFT was overridden on the tag dispatch (run authorization A3, release-flow.md §8.10). Re-running the `deploy_wear` job (attempt 2) ended in DECISION SKIP. |

## 16. Sources

- Data Layer overview: https://developer.android.com/training/wearables/data/overview
- Data Layer client types: https://developer.android.com/training/wearables/data/client-types
- Handle Data Layer events: https://developer.android.com/training/wearables/data/events
- `MessageClient` reference: https://developers.google.com/android/reference/com/google/android/gms/wearable/MessageClient
- `WearableListenerService` reference: https://developers.google.com/android/reference/com/google/android/gms/wearable/WearableListenerService
- Play Data safety guidance: https://support.google.com/googleplay/android-developer/answer/10787469
- Phase 1 specification: [wear-phase-1-active-workout-tile.md](wear-phase-1-active-workout-tile.md)
- Release pipeline: [wear-release-pipeline.md](wear-release-pipeline.md), [release-flow.md](../release-flow.md)

## Appendix A. Public privacy policy (`docs/index.md`), owner-approved

The owner approved this text on 2026-09-29 by handing this specification to the implementer. It
replaces the whole of `docs/index.md`: the new file is the lines strictly between the two fence
lines below, joined with "\n", plus one final "\n". `docs/_config.yml` is not touched.

**A — the complete new `docs/index.md`**

```text
## Privacy Policy (English)

**Workeeper** is a free, open-source app built by **StSlex**. This SERVICE is provided at no cost
and is intended for use as is.

This page explains what information the Workeeper app for Android phones and its Wear OS companion
app handle, where that information goes, and why.

By using the Service, you agree to the terms described in this Privacy Policy. We do not collect or
share any personal information except as described here.

### Your workout data

The workouts, exercises, sets and other entries you create are stored **locally on your phone**.
The app has no user accounts and no server of its own. Your workout data leaves your phone only in
the cases described below.

### Wear OS companion app

If you install Workeeper on a Wear OS watch paired with your phone, the phone app sends the watch
only what the watch needs to show the current set of a workout that is active on the phone: the
workout and exercise names, the set number and type, the weight and reps, the workout progress, and
technical identifiers. When you complete a set on the watch, the watch sends that set's weight,
reps and type, with technical identifiers, back to the phone, which records the set. Your workout
history stays on the phone. The watch keeps only a temporary copy of the current state: the app
stops using it after 24 hours or after the watch restarts, and deletes it the next time it runs.

This data moves only between your phone and your watch, through the Wear OS Data Layer of Google
Play services. When the devices are connected by Bluetooth, the data normally goes directly between
them. Otherwise, Google Play services may relay it through Google's servers; Google states that
such transfers are end-to-end encrypted. The data is never sent to the developer.

### Google Drive backup (optional)

If you sign in with your Google account in the app's backup settings, the app saves copies of your
workout database to the hidden app-data folder of your own Google Drive, daily by default or on the
schedule you choose, keeps the three most recent copies, and can restore your data from them. If
you also turn on the AI assistant snapshot, the app saves a readable copy of your workouts (a JSON
file) to a visible "Workeeper" folder in your Google Drive, so that an AI assistant you give access
to your Drive can read it. When you turn that option off or sign out, the app tries to delete these
snapshot files; you can also delete the folder in Google Drive yourself. Each backup and snapshot
also records the app version and the device model. To show which account is connected, the app
reads the account's email address and name and stores them with the app's data on your phone.

These copies are stored by Google in your account and are protected by Google Drive's encryption
in transit and at rest; the app does not add its own encryption. The developer never receives them.
You can stop backups and disconnect your account in the app's settings at any time, and delete the
stored backups in your Google Drive settings.

### Sharing data after an error

If opening or restoring your data fails, the app lets you share a copy of your data or a diagnostic
file through Android's share menu. The file goes only to the app or person you choose.

### Android device backup

If backup is turned on in your phone's settings, Android can include the phone app's data,
including your workouts, in your device backup to your Google account, as it does for other apps.
That backup is managed by Android and Google under your account; the developer never receives it.
The Wear OS app's data is excluded from device backup.

### Crash reports, performance and usage statistics

The phone and watch apps use Google Firebase services:

- **Firebase Crashlytics** collects crash and error reports: the error details, device model,
  operating system version, app version, and a short log of the app's recent steps.
- **Firebase Performance Monitoring** collects performance data, such as app start-up and screen
  rendering times and the duration of the app's network requests.
- **Google Analytics for Firebase** collects usage events, such as which screens are opened and
  which actions are used.

These services may also collect identifiers of the app installation and the device's IP address,
as described in Google's policies. Starting with version 1.52.0, the app turns off the collection
of the advertising ID and does not add the names, weights, reps or other workout content you enter
to usage events or crash-report logs. Earlier versions could include such entries and collected the
advertising ID; updating the app stops both. The text of an error can occasionally contain a
fragment of the data involved in it. The developer uses this information only to fix problems and
improve the app.

- [Google Privacy Policy](https://policies.google.com/privacy)
- [Firebase privacy and security](https://firebase.google.com/support/privacy)

### Cookies

The app itself does not use cookies. However, third-party services integrated in the app may use
cookies to improve their functionality.

### Service Providers

We may engage third-party companies for the following purposes:

- To facilitate our Service
- To provide the Service on our behalf
- To perform Service-related activities
- To assist in analyzing app usage

These third parties have access only to the information necessary to perform their tasks and are
obligated not to use it for any other purpose.

### Security

We value your trust in providing information. No method of electronic storage or transmission is
100% secure, and we cannot guarantee absolute security.

### Children’s Privacy

The app is not intended for children under 13. We do not knowingly collect data from children. If
you believe your child has provided personal information, please contact us for immediate removal.

### Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Changes will be posted on this page.

Last updated: **29 September 2026**

### Contact Us

If you have any questions or suggestions about this Privacy Policy, contact us:

📧 **stslex93@gmail.com**

---

## Политика конфиденциальности (Русский)

**Workeeper** — бесплатное приложение с открытым исходным кодом, созданное **StSlex**. Этот Сервис
предоставляется бесплатно и предназначен для использования «как есть».

Эта страница объясняет, какую информацию обрабатывают приложение Workeeper для телефонов Android и
его приложение-компаньон для Wear OS, куда эта информация передаётся и зачем.

Используя приложение, вы соглашаетесь с условиями данной Политики конфиденциальности. Мы не
собираем и не передаём персональные данные, кроме случаев, описанных ниже.

### Данные о тренировках

Тренировки, упражнения, подходы и другие записи, которые вы создаёте, хранятся **локально на вашем
телефоне**. У приложения нет учётных записей пользователей и собственного сервера. Данные о
тренировках покидают телефон только в случаях, описанных ниже.

### Приложение для Wear OS

Если вы установите Workeeper на часы с Wear OS, сопряжённые с вашим телефоном, приложение на
телефоне будет передавать часам только то, что нужно для показа текущего подхода тренировки, начатой
на телефоне: названия тренировки и упражнения, номер и тип подхода, вес и количество повторений,
прогресс тренировки и технические идентификаторы. Когда вы завершаете подход на часах, часы
отправляют на телефон вес, количество повторений и тип этого подхода вместе с техническими
идентификаторами, и телефон записывает подход. История тренировок остаётся на телефоне. Часы хранят
только временную копию текущего состояния: приложение перестаёт её использовать через 24 часа или
после перезагрузки часов и удаляет её при следующем запуске.

Эти данные передаются только между вашим телефоном и вашими часами через Wear OS Data Layer
(компонент сервисов Google Play). Когда устройства соединены по Bluetooth, данные обычно передаются
напрямую между ними. В остальных случаях сервисы Google Play могут передавать их через серверы
Google; по заявлению Google, такая передача защищена сквозным шифрованием. Разработчику эти данные
никогда не передаются.

### Резервное копирование на Google Диск (по желанию)

Если вы войдёте в аккаунт Google в настройках резервного копирования приложения, приложение будет
сохранять копии базы данных тренировок в скрытую папку данных приложения на вашем Google Диске (по
умолчанию ежедневно или по выбранному вами расписанию), хранить три последние копии и сможет
восстанавливать из них ваши данные. Если вы также включите «Снимок для ИИ-ассистента», приложение
будет сохранять читаемую копию ваших тренировок (файл JSON) в видимую папку «Workeeper» на вашем
Google Диске, чтобы её мог прочитать ИИ-ассистент, которому вы дадите доступ к своему Диску. Когда
вы отключаете эту функцию или выходите из аккаунта, приложение пытается удалить эти файлы; вы
также можете сами удалить эту папку на Google Диске. В каждой резервной копии и каждом снимке также
записываются версия приложения и модель устройства. Чтобы показать, какой аккаунт подключён,
приложение считывает адрес электронной почты и имя аккаунта и хранит их вместе с данными
приложения на вашем телефоне.

Эти копии хранятся у Google в вашем аккаунте и защищены шифрованием Google Диска при передаче и
хранении; приложение не добавляет собственного шифрования. Разработчик никогда их не получает. Вы
можете в любой момент отключить резервное копирование и отвязать аккаунт в настройках приложения, а
сохранённые копии удалить в настройках Google Диска.

### Отправка данных после ошибки

Если открыть или восстановить ваши данные не удаётся, приложение позволяет отправить копию данных
или диагностический файл через меню «Поделиться» Android. Файл получит только выбранное вами
приложение или человек.

### Резервное копирование Android

Если в настройках телефона включено резервное копирование, Android может включать данные
приложения на телефоне, в том числе ваши тренировки, в резервную копию устройства в вашем аккаунте
Google, как и данные других приложений. Этой резервной копией управляют Android и Google в рамках
вашего аккаунта; разработчик её не получает. Данные приложения для Wear OS в резервную копию
устройства не включаются.

### Отчёты о сбоях, производительность и статистика использования

Приложения для телефона и часов используют сервисы Google Firebase:

- **Firebase Crashlytics** собирает отчёты о сбоях и ошибках: сведения об ошибке, модель
  устройства, версию операционной системы, версию приложения и короткий журнал последних действий
  приложения.
- **Firebase Performance Monitoring** собирает данные о производительности, например время запуска
  приложения и отрисовки экранов и длительность сетевых запросов приложения.
- **Google Analytics for Firebase** собирает события использования, например какие экраны
  открываются и какие действия используются.

Эти сервисы также могут собирать идентификаторы установки приложения и IP-адрес устройства, как
описано в правилах Google. Начиная с версии 1.52.0 приложение отключает сбор рекламного
идентификатора и не добавляет в события использования и журналы отчётов о сбоях названия, вес,
количество повторений и другое содержимое тренировок, которое вы вводите. Более ранние версии могли
включать такие записи и собирали рекламный идентификатор; обновление приложения прекращает и то, и
другое. Текст ошибки иногда может содержать фрагмент данных, с которыми она связана. Разработчик
использует эту информацию только для исправления ошибок и улучшения приложения.

- [Политика конфиденциальности Google](https://policies.google.com/privacy?hl=ru)
- [Конфиденциальность и безопасность Firebase](https://firebase.google.com/support/privacy)

### Cookies

Само приложение не использует cookies. Однако сторонние сервисы могут их применять для улучшения
работы.

### Сторонние сервисы

Мы можем привлекать сторонние компании для:

- Обеспечения работы приложения
- Анализа использования
- Улучшения качества сервиса

Эти третьи стороны имеют доступ только к информации, необходимой для выполнения их задач, и
обязаны не использовать её для иных целей.

### Безопасность

Мы ценим доверие пользователей. Ни один способ хранения или передачи данных через Интернет не
является абсолютно безопасным, и мы не можем гарантировать абсолютную безопасность.

### Конфиденциальность детей

Приложение не предназначено для детей младше 13 лет. Мы сознательно не собираем данные о детях.
Если ваш ребёнок предоставил персональную информацию, свяжитесь с нами для её удаления.

### Изменения политики

Мы можем обновлять настоящую Политику конфиденциальности. Все изменения будут опубликованы на этой
странице.

Последнее обновление: **29 сентября 2026 г.**

### Контакты

Если у вас есть вопросы или предложения по данной Политике конфиденциальности:

📧 **stslex93@gmail.com**
```

| File | Base sha256 | Result sha256 |
|---|---|---|
| `docs/index.md` | `f722a8d8555a14b974a1b985ab22170521749e2c0dda72cdfcc9ebafebbf7cf4` | `43633f03d808cf7228952b791c08a0d223eb5b2942aff446820d133124e293ae` |

## Appendix B. Store listing line

Lines 14 (B1, B2) and 15 (B3, B4) of each phone `full_description.txt`. Only the text below changes;
the two trailing spaces of each line stay. B3 and B4 were added on 2026-09-30: "no personal data
collected" contradicts the Firebase identifiers that Appendix A discloses.

**B1 OLD** (`fastlane/metadata/android/en-US/full_description.txt`)

```text
💡 All data is stored locally on your device.
```

**B1 NEW**

```text
💡 Your workout data is stored on your device; the app has no server of its own.
```

**B2 OLD** (`fastlane/metadata/android/ru-RU/full_description.txt`)

```text
💡 Данные хранятся локально на вашем устройстве.
```

**B2 NEW**

```text
💡 Данные о тренировках хранятся на вашем устройстве; у приложения нет собственного сервера.
```

**B3 OLD** (`fastlane/metadata/android/en-US/full_description.txt`)

```text
No registration required, no personal data collected.
```

**B3 NEW**

```text
No registration or account required.
```

**B4 OLD** (`fastlane/metadata/android/ru-RU/full_description.txt`)

```text
Приложение не требует регистрации и не собирает личные данные.
```

**B4 NEW**

```text
Приложение не требует регистрации и учётной записи.
```

| File | Base sha256 | Result sha256 |
|---|---|---|
| en-US | `935a38157187181937ef4281bfd168f4cae2fc9eb31a192048bc49165f7d3508` | `af46b2fc60370880e5e21e2387f5bec31bc8ebc72bc43796b18bf5029c4e1054` |
| ru-RU | `944919fda8b667a886737785da111664cf083f999515bdaf7eb324aca558b560` | `e9b9b5668372a56ba9a4a1057bf00a6ea04b43a45d3a47dc33a7da6c2d7d0604` |

## Appendix C. Product copy (`documentation/product.md`)

Four exact-substring edits, C1 to C4; each OLD block must occur exactly once.

**C1 OLD** (Android Wear OS companion section)

```text
Health Connect, Samsung Health, or a general wearable platform. Implementation
also remains subject to both the paired-device disclosure gate and the transport
gate in
[the Phase 1 Wear specification](feature-specs/wear-phase-1-active-workout-tile.md):
the existing privacy promise and Play Store copy must be reconciled explicitly
before any workout payload crosses from the phone to the watch.
```

**C1 NEW**

```text
Health Connect, Samsung Health, or a general wearable platform. Both privacy
gates of
[the Phase 1 Wear specification](feature-specs/wear-phase-1-active-workout-tile.md)
closed on 2026-09-29 by owner decision, recorded in
[Wear paired transport](feature-specs/wear-paired-transport.md): the public
privacy policy describes the paired phone/watch transfer, the bounded watch
cache, and possible end-to-end encrypted Google relay transit, and workout
payloads cross between phone and watch only through the transport that
specification defines.
```

**C2 OLD** (Non-goals, cloud sync bullet)

```text
  and retain the bounded watch cache defined by its specification. Any
  Google-owned end-to-end encrypted Data Layer relay transit is a separate
  owner decision and disclosure; it is never authorized implicitly. Manual
  export / import may be added later, but is not a v1 commitment.
```

**C2 NEW**

```text
  and retain the bounded watch cache defined by its specification. The owner
  authorized Google-owned end-to-end encrypted Data Layer relay transit for it
  on 2026-09-29, together with its public disclosure
  ([Wear paired transport](feature-specs/wear-paired-transport.md)); no other
  phone/watch route is authorized. Manual export / import may be added later,
  but is not a v1 commitment.
```

**C3 OLD** (Positioning, offline bullet)

```text
- **Fully offline.** All data lives on the device. There is no account,
  no server, no cloud sync, no telemetry beyond crash reporting.
```

**C3 NEW**

```text
- **Fully offline.** Workout data lives on the phone, and the app works
  without a network. There is no account, no server and no cloud sync of our
  own; workout data leaves the phone only through a paired Wear OS watch, a
  backup in the user's own Google Drive, Android's device backup, or a file the
  user shares after a data error. Telemetry is limited to crash reports,
  performance data and usage events, and the app adds no entered workout
  content to them.
```

**C4 OLD** (Non-goals, telemetry bullet)

```text
- **No analytics or telemetry** beyond crash reporting.
```

**C4 NEW**

```text
- **No analytics or telemetry** beyond crash reports, performance data and
  usage events, to which the app adds no entered workout content.
```

| File | Base sha256 | Result sha256 |
|---|---|---|
| `documentation/product.md` | `7b6c9d1bda66709a5f758e3ef407d7ee626f2e4d7a567c0b7c8e4a28d8e0ec8c` | `06080084da368f908f80ebbd646f13339495b8079ee0475cf89e936f07494b32` |

## Appendix D. Phase 1 gate closure record

Applied to `documentation/feature-specs/wear-phase-1-active-workout-tile.md` in the commit that
lands this specification.

**D1 OLD** (Status paragraph)

```text
This authorization permits test/debug synthetic sources only. Privacy and real-payload
transport remain blocked until their decisions and entry gates are closed; physical-device
acceptance and final Phase 1 acceptance remain open.
```

**D1 NEW**

```text
This authorization permits test/debug synthetic sources only. Both privacy gates closed on
2026-09-29 (§6.1), and real-payload transport is specified in
[Wear paired transport](wear-paired-transport.md). Physical-device acceptance and final Phase 1
acceptance remain open.
```

**D2**: immediately before the anchor line `## 7. Android-only module boundary`, insert the
following section, followed by one blank line.

**D2 section**

```text
### 6.1 Gate closure record

Both gates closed on 2026-09-29 by owner decision, recorded in
[Wear paired transport](wear-paired-transport.md) §2:

- Gate 1: the owner approved the public privacy policy in
  [Wear paired transport](wear-paired-transport.md) Appendix A. It replaces
  `docs/index.md` through an owner-authorized, text-exact exception to the
  repository lock, and the release that enables transport is accepted only
  after the live privacy page carries it.
- Gate 2: the owner selected **Permit end-to-end encrypted Data Layer relay**.
  The transport uses `MessageClient` only, never `DataClient`.

Workout payloads may cross the device boundary only through the two files that
[Wear paired transport](wear-paired-transport.md) §8 allowlists; every other
source file stays under the three-layer ban. This record closes neither
physical-device acceptance, nor the reconnect-window calibration of §8, nor
final Phase 1 acceptance.
```

| File | Base sha256 | Result sha256 |
|---|---|---|
| Phase 1 specification | `40ade3fe5adaf0db4fe4c3f70629ca2d562324c587b45c1c0e18fec074c6e73e` | `5c680a38edddf6e0c4d3c0d50288a3057c01eb1b84e36acf754acefaf1637c60` |
