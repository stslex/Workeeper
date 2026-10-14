# Wear OS Phase 2 — exercise list, any set, skip and return (protocol v2)

**Status:** approved by the owner on 2026-10-11, ready for implementation. Written 2026-10-04 and
re-verified on 2026-10-10; §2 says which decisions were locked before it and which it proposed.

- **Specification base:** `dev` at `99f10601`. Evidence is `path:line` there. The draft was
  written at `89af9bf8`; release 1.53.1 (PR-S2, live sync D13 to D15) shipped on 2026-10-09 and is
  on `dev`, and every row of §3 was re-verified at `99f10601`. Discovery (§14) re-verifies every
  row at the then-current `dev` head, and pure line drift is recorded, not a STOP.
- **Visual source of truth:** [the approved Phase 2 mockup](../mockups/wear-phase-2.html)
  (owner approval 2026-10-04, landed in `89af9bf8`), variants *Set choice A* and *Skip A*. Its
  layout, sizes, colors, typography and copy bind; where this file and the mockup disagree, the
  implementation stops.
- **Amends:** [Phase 1](wear-phase-1-active-workout-tile.md) (§2.2 non-goals "picking another
  exercise" and "skipping an exercise"; §3 state flow; §4 becomes the watch's default selection;
  §5.2 target validation; §9 protocol surface), [paired transport](wear-paired-transport.md)
  (every statement that the protocol is v1 or that a command completes "the current set") and
  [live sync](wear-live-sync.md) (§13 "Phase 2 ... and its protocol v2"). Everything else there
  stays in force: transport, origins O1–O6, the change signal, the bounded cache and its TTL,
  receipts, the allowlist of four Data Layer files.
- **Delivery:** two Android-only PRs into `dev`, then release 1.54.0 (§12). PR-P2A: protocol v2,
  the phone, the watch state, Tile and ongoing indicator, the Phase 1 controller kept working on
  v2, the privacy policy and `product.md`. PR-P2B: the watch UI of the mockup, copy, gates and
  store screenshots.

The owner's field tests of 1.52.2 (2026-10-01) and 1.53.0 (2026-10-04) both reported: "there is
no choice of exercise on the watch, and no way to skip one". Phase 1 excluded both by design
(Phase 1 §2.2). This increment adds them. Skipping a set, which live sync §13 also lists for Phase
2, needs no command: sets have no execution order, so the owner chose that the watch can pick any
incomplete set of any exercise instead, which D3 implements.

## 1. Goal and success criteria

With phone 1.54.0 and watch 1.54.0-wear, during a workout started on the phone, the watch:

- opens on the controller of the set it has selected, with the session's exercise list one swipe
  back (D1);
- lets the user pick any exercise of the session and any incomplete set of it, complete that set,
  and see the next one selected (D2, D3, D8);
- lets the user skip an exercise and return a skipped one to the session, with a confirmation, and
  the open phone screen shows either change without re-entry (D4, D6, V4);
- shows a finished exercise's sets read-only (D5);
- keeps its selection across process death, and shows it on the Tile (D8, D9).

Success means all of:

1. every host gate of §10 is green at each commit of both PRs, and each named mutation went RED,
   then GREEN;
2. release 1.54.0 is live on both tracks through the existing pipeline, both deploy lanes end in
   `RESULT PASS`, and the policy text of Appendix A is live;
3. the owner's physical checklist (§12.3) passes, or its failures are reported with evidence.

Items 1 and 2 are the implementer's. Item 3 is the owner's and is the only evidence that closes
this increment.

## 2. Decisions

L: locked by the owner. D1–D9 were locked by the mockup approval of 2026-10-04 and are recorded
in the project note of that day. V1–V7 were proposed to the owner on 2026-10-04 and taken as
accepted with his "давай дальше"; approving this file confirms them. P: proposed by this
specification and approved with it.

**Watch UX (mockup).**

| Id | | Decision |
|---|---|---|
| D1 | L | The app opens on the controller of the watch's selected set; the exercise list is one swipe back. |
| D2 | L | After the last set of an exercise, the selection moves to the first exercise in phone order that is neither finished nor skipped (the Phase 1 target rule, §5.3 rule R). The list stays one swipe back. |
| D3 | L | A specific set is chosen on a Sets screen, opened from the exercise name at the top of the controller or from the Sets row at the end of its scroll. No set pager. |
| D4 | L | Skip lives at the end of the controller: an icon-only Skip button beside the Sets row (its name is spoken), then a confirmation whose edge button skips and whose swipe back cancels. |
| D5 | L | Tapping a finished exercise opens its Sets read-only, with "Edit on your phone". |
| D6 | L | Tapping a skipped exercise asks "Return to session?" (the phone's wording); the edge button returns it, swipe back cancels. |
| D7 | L | At rest, the set position lives in the time text arc ("2/3 · 10:09"); no extra header line. |
| D8 | L | The selection is per device. Completing the selected set anywhere moves it to the first incomplete set of the same exercise; a finished, skipped or removed exercise falls back to rule R. The selection survives process death in the bounded watch cache. |
| D9 | L | The Tile and the ongoing indicator follow the watch's selection. |

**Protocol and privacy.**

| Id | | Decision |
|---|---|---|
| V1 | L | **Protocol v2 replaces v1.** `SCHEMA_VERSION` becomes 2 on both sides; neither side keeps a v1 decoder. Breaking compatibility is cheap while the watch app is on `wear:internal` only. Paths and capabilities do not change (§5.1). |
| V2 | L | **The snapshot becomes a session outline:** every performed exercise in phone order, skipped ones included, each with its set rows; a problem (no set rows, values outside the watch domain) is flagged on the exercise or the set instead of replacing the whole snapshot (§5.2). |
| V3 | L | **The lease is scoped to the session and its revision, not to a target.** The command names its target, and the phone validates that target against the outline at that revision (§6.2, §6.3). |
| V4 | L | **Skip and return are watch commands, and the open phone screen applies them live**, as it applies a set the watch completed (§6.5). |
| V5 | L | **The 16 KiB envelope cap stays.** The outline's size is pinned by codec tests; a larger session falls back to `PayloadTooLarge` (§5.7). |
| V6 | L | **The public policy (EN and RU) and `product.md` change** to name what the outline and the new command carry (§9, Appendices A and B). |
| V7 | L | **Two Workeeper watches in one workout stay a known residual** (live sync D12): the lease rework of V3 does not change per-node retirement (§11). |

The display-only snapshot pushed with the change signal (the owner's "B" of 2026-10-04) is not in
this increment. Its precondition is that origin O6 works on the owner's devices, which the 1.53.0
field test did not show. A paired-emulator run of the 1.53.0 code passed it on 2026-10-06 (live
sync §15); the owner's physical check of 1.53.1 (live sync §12.3) is still open. "B" gets its own
specification after that check passes (§13).

**Proposed here.**

| Id | | Decision |
|---|---|---|
| P1 | P | **One generic command envelope** with two bodies, `complete_set` and `set_exercise_skipped`, and one response (§5.2). Retries, receipts, single flight and the reducer's command pipeline stay one mechanism. |
| P2 | P | **FingerprintV2** with the command kind in its header; `complete_set` keeps V1's field tags (§5.6). |
| P3 | P | **Version answer.** The v2 phone answers a request of any other schema version with a fixed v2 envelope, so a watch still on v1 shows "Update Workeeper on phone and watch" instead of "Couldn't reach your phone" (§5.1). |
| P4 | P | **Receipts compare the command id first** and parse the stored fingerprint only for the same command, so a v1 receipt left in the database never rejects a v2 command (§6.3, F7). |
| P5 | P | **State-driven navigation:** a destination stack in the watch store, each level dismissed by swipe or Back, no new navigation dependency (§8.1). |
| P6 | P | **One draft, bound to the selected set:** changing the selection or the revision drops it (§7.3). |
| P7 | P | **The phone accepts a skip of any exercise that is not skipped**, as its own toggle does; the watch offers Skip only on the controller, for the selected exercise (§6.3, §8.4). |
| P8 | P | **A set whose planned values are outside the watch domain is "blocked":** shown, selectable, not completable on the watch, as Phase 1 §4 rule 5 treated a target (§5.2, §5.3, §8.4, §8.5). |
| P9 | P | **The entry rule (D1) waits for an outline.** The store starts with the list plus the controller when a selection exists; otherwise with the list and an entry pending, and the first admitted outline with a selection completes it. A Tile, ongoing-indicator or launcher start of a running app (`onNewIntent`) applies the rule again; a configuration change does not (§8.1). |
| P10 | P | **Store screenshots are regenerated** in PR-P2B from the new fixtures (§12.1). |
| P11 | P | **An exercise without sets can be skipped from the watch:** its controller ("Add a set on your phone") ends with a labelled Skip row (the labelled Skip element of the mockup's rejected pager variant), so rule R does not keep opening an exercise the user will not do (§8.4). |
| P12 | P | **Rule R stops at an exercise without sets, and such an exercise keeps the workout from being complete**, as Phase 1 §4 rules 4 and 6 say and D2 names. The mockup's prototype has no such exercise; its simplified `phase1Target` would pass over it (§5.3). |
| P13 | P | **Exercise progress counts finished exercises out of the exercises that are not skipped**, as the phone's header and Phase 1's `totalExercises` do. The mockup's header counted skipped ones in the total ("1 of 6" in its typical fixture becomes "1 of 5"); the layout does not change (§8.3). |
| P14 | P | **The phone's skip menu sends the intent it showed.** `OnSkipExercise(uuid)` becomes `OnSkipExercise(uuid, skipped)` and does nothing when the exercise is already in that state, so a watch skip that arrives while the menu is open is not undone by the tap (§6.5). |
| P15 | P | **Outline budget.** An outline is sent only while its snapshot response fits 16,384 bytes minus a 512-byte command headroom, so every command response for that outline fits too (§5.7). |
| P16 | P | **The watch's screen stack is reconciled with every admitted outline:** screens about an exercise that is gone or changed state are dropped, no selection leaves the list only, a changed selection closes the editor (§8.1). |
| P17 | P | **After Return, the list is centered on the returned exercise.** The prototype meant this (anchor `row`) but passed no row and rendered the list at the top (§8.1). |

## 3. Verified facts at the specification base

The implementer re-verifies each row before editing (§14). A contradiction that changes the
design is a STOP. A `...` in a path stands for the source-set root and the package directories
below it, such as `src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/`; the root is
`src/commonMain/kotlin/` for the `core/data/*` modules. Every abbreviated path is unique by its
suffix within its module.

| Id | Fact | Evidence |
|---|---|---|
| F1 | `SCHEMA_VERSION` is 1, the envelope cap 16,384 bytes, a display name at most 512 UTF-8 bytes. | `core/wear-protocol/src/main/kotlin/io/github/stslex/workeeper/core/wear/protocol/WearProtocol.kt:7-9` |
| F2 | The v1 snapshot carries one target: `ActiveWithTarget.target`. `PhoneActionRequired` names one exercise (`NoSetRows`, `UnsupportedNumericValues`) or none (`PayloadTooLarge`). The only write is `complete_current_set`, whose body already names `performedExerciseUuid` and `setPosition`. | `.../protocol/WireModels.kt:32-64,81-110,130-151,184-206` |
| F3 | The watch decodes any `schemaVersion` other than 1 as `ProtocolMismatch(UnsupportedSchemaVersion)`; the phone drops a request of another version and answers with empty bytes. | `.../protocol/WearProtocolCodec.kt:82-106,125-136,161-177`; `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/PhoneWearRpcHandler.kt:108,117-118` |
| F4 | A v1 watch shows `ProtocolMismatch` as "Update Workeeper on phone and watch", with every control disabled; a v2 watch that gets empty bytes shows "Couldn't reach your phone". | `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/state/WatchWorkoutReducer.kt:240-251`; `.../wear/state/WatchInteractionEligibility.kt:17`; `.../wear/transport/WatchTransportCoordinator.kt:344,373,378-379,411-412`; `.../wear/ui/WearSurfaceModel.kt:91-93,102-111,116-117`; `.../wear/ui/WearCopy.kt:32-33`; `app/wear/src/main/res/values/strings.xml:10-11` |
| F5 | The snapshot builder drops skipped exercises, builds each exercise's expected positions as plan indices ∪ performed positions, and returns at the first exercise that is not complete: as the target, or as `PhoneActionRequired` for the whole session when it has no expected position or its planned values fall outside the watch domain. Plans come from the training plan or, for ad-hoc trainings and missing plans, `last_adhoc_sets`. | `feature/wear-bridge/.../PhoneWorkoutSnapshotBuilder.kt:35-138,152-179,201-225` |
| F6 | The phone lease is bound to one target and one revision; there is one slot per (node, session); every `DbTransitionRunner.mutate` commit retires every slot. | `feature/wear-bridge/.../WearMutationLeaseStore.kt:27-40,62-85,171-179,212-216` |
| F7 | The command gateway checks, in order: epoch, session, receipt, revision, performed exercise (missing or skipped is `TargetChanged`), lease, canonical target (`matchesCanonicalTarget`), types, values. The receipt check parses the stored fingerprint (`UnsupportedFingerprintVersionException` → `ProtocolRejected(UNSUPPORTED_FINGERPRINT_VERSION)`) before it compares command ids. Authority is granted only for `ActiveWithTarget`. The write-failure path and the publication-loss path compare the target too. | `feature/wear-bridge/.../PhoneWorkoutBridgeImpl.kt:170-364` (`:252`, `:304`), `:366-395` (`:372-383` before `:384`), `:397-434`, `:451-472`, `:660-668`, `:807-824` |
| F8 | FingerprintV1 hashes 12 (stable intent) or 14 (delivery attempt) tagged fields; the value is 34 bytes, a 2-byte encoding version and a SHA-256 digest. Known-answer vectors are pinned in a host test and in a phone and a watch device test. | `.../protocol/FingerprintV1.kt:14-17,39-67,75-116`; `core/wear-protocol/src/test/.../FingerprintV1Test.kt:13-43`; `feature/wear-bridge/src/androidTest/.../FingerprintV1PhoneDeviceTest.kt:18-45,85-94`; `app/wear/src/androidTest/.../FingerprintV1WatchDeviceTest.kt:18-35,85-94` |
| F9 | The phone skips and returns an exercise with one toggle that decides `skipped = status != SKIPPED` when tapped, `PerformedExerciseDao.setSkipped` through `transition.mutate`, flag only (sets and drafts stay), with no confirmation. Copy: "Skip exercise" / "Пропустить упражнение", "Return to session" / "Вернуть в сессию", status "skipped" / "пропущено". | `core/data/database/.../session/PerformedExerciseDao.kt:44-45`; `core/data/exercise/.../session/PerformedExerciseRepositoryImpl.kt:37-39`; `feature/live-workout/.../mvi/handler/ClickHandler.kt:363-377`; `feature/live-workout/src/main/res/values/strings.xml:15,24-25`; `values-ru/strings.xml:12,20-21` |
| F10 | Trigger `wear_performed_update_revision` raises `wear_revision` and clears the receipt on `UPDATE OF ... skipped` of an in-progress session's performed exercise. | `core/data/database/.../wear/WearSyncStorage.kt:181-193` |
| F11 | `ExternalSetWrites` carries completed sets only, from one publisher (the bridge, after commit, under `coordinatorMutex`) to one collector (the live-workout store). The store applies a write through `applySetMarked`, patches an open undo snapshot, ignores an absent exercise, and replays writes received during a load. Nothing else reaches the open screen live. Since 1.53.1 every state write of the store goes through `updateState`, a compare-and-set that may run its update function again, so no update function has a side effect and `coverage.receive` stays outside it (live sync D14, the GUARD at `CommonHandler.kt:38-46`). | `core/data/exercise/.../session/ExternalSetWrites.kt:17-48`; `PhoneWorkoutBridgeImpl.kt:104-124`; `feature/live-workout/.../mvi/handler/CommonHandler.kt:38-46,152-177`; `.../mvi/handler/ExternalWriteCoverage.kt:13-48`; `.../mvi/mapper/LiveSetMutator.kt:100-135` |
| F12 | The phone's skip mutator `applySkipToggle(state, uuid, skipped)` sets the status to SKIPPED or PENDING from the `skipped` it is given, keeps performed sets and drafts, and recomputes statuses, counts and progress; the toggle decision (`skipped = status != SKIPPED`) is the click handler's (F9). | `feature/live-workout/.../mvi/mapper/LiveSetMutator.kt:269-287` |
| F13 | The phone's load-time done rule: expected positions = plan indices ∪ performed positions; an exercise is done when every expected position has a performed set; skipped is never done. | `feature/live-workout/.../mvi/mapper/ExerciseDoneRule.kt:31-44` |
| F14 | An extra set added on the phone is a draft until it is completed; only completed sets are rows. | `feature/live-workout/.../mvi/mapper/LiveSetMutator.kt:195-226`; `core/data/exercise/.../session/SetRepositoryImpl.kt:127-142` |
| F15 | The watch's authority is bound to a `TargetKey`; a command must match the authority's target; an admitted snapshot with another target invalidates a pending command. | `app/wear/.../state/ReducerModels.kt:23-26,47-69`; `WatchWorkoutReducer.kt:419,544-582,678-719` |
| F16 | The watch owner keeps one pending draft keyed by (source, target) and builds the command from the snapshot's target. | `app/wear/.../runtime/WatchRuntimeOwner.kt:212-247,345-428` |
| F17 | The watch UI is one surface: the controller, with the numeric editor swapped in by store state. There is no navigation component; the only `BackHandler` is the editor's. | `app/wear/.../ui/WearControllerScreen.kt:81-136,687`; `.../mvi/store/WearStore.kt:14-45`; `build-logic/convention/.../ConfigureWearApplication.kt:64-86` |
| F18 | The Tile shows one exercise and "Set N of M"; its launch, the ongoing indicator's intent and `MainActivity` carry no target. | `app/wear/.../tile/WorkoutTileRenderer.kt:67-125`; `.../tile/WorkoutTileLayout.kt:24-31`; `.../ongoing/AndroidOngoingNotification.kt:99-106`; `.../MainActivity.kt:104-112` |
| F19 | The ongoing indicator is posted only for a fresh granted lease, and its lease signature includes `targetKeyOrNull()`. Its text is static. | `app/wear/.../ongoing/WatchOngoingCoordinator.kt:57-91,227-255`; `.../ongoing/AndroidOngoingNotification.kt:53-57,96-128` |
| F20 | The cache frame is version 1 with three flags; its payload is an encoded response decoded with `decodeForWatch`; a frame or payload it cannot decode is deleted as corrupt. | `app/wear/.../cache/CacheFraming.kt:78-108,140-144`; `.../cache/WatchSnapshotCache.kt:122-188` |
| F21 | Every watch string must be rendered by a fixture, EN and RU name sets must match, and every default-locale (EN) value has at least 3 characters; the length rule has no exemption, and the named exemptions (`UNREACHABLE_BY_DESIGN`) skip only the rendered check. Substituted format strings are matched on their literal segments, and the gate says it must become exact if a new string shares every literal segment of one of them. Every `WearStore` Action and Event leaf needs a registry entry whose `toString()` leaks no value; `ControllerAction` is not covered, and its `SetReps` and `SetWeight` print their values. | `app/wear/src/test/.../ui/WearStringCoverageGateTest.kt:55-64,80-171` (`:123-132`, `:140`, `:362-366`); `app/wear/src/test/.../mvi/WearStoreActionRegistryTest.kt:42-97`; `app/wear/.../runtime/ControllerAction.kt:6-12` |
| F22 | The emulator acceptance harness (16 cells) and its mutation manifests match exact source text of the controller and handlers. | `documentation/wear-emulator-acceptance/acceptance_model.py:23-49`; `ui_mutations.json`, `style-device-mutations.json`, `style-mvi-mutations.json` |
| F23 | The policy says the phone sends the watch "only what the watch needs to show the current set", and the watch sends back a completed set; `product.md` authorizes "the minimum active workout snapshot and completion commands" for "Phase 1". | `docs/index.md:20-26,144-151`; `documentation/product.md:250-255,277-289` |
| F24 | Every release ships the phone to production and the Wear bundle to `wear:internal`, from one TOML version; the Wear job runs after the phone upload and its failure never undoes the phone release. | `documentation/release-flow.md:20,122-133,195-197` |
| F25 | Wear Compose is 1.6.2 (Material 3). | `gradle/libs.versions.toml:83` |
| F26 | The RPC handler takes the work lease and checks the transport kill switch before it decodes a request. | `feature/wear-bridge/.../transport/PhoneWearRpcHandler.kt:53-66,92-93` |
| F27 | A command response's only size fallback is in `prepareCommandResponse` (a `RetryableTemporaryFailure` that does not fit becomes `TargetChanged`); `readOnlyCommandResponse` asserts `check(fits)`, so an oversized read-only replacement throws, and the watch gets empty bytes. | `PhoneWorkoutBridgeImpl.kt:506-521,626-641`; `feature/wear-bridge/.../transport/PhoneWearRpcHandler.kt:41-44` |
| F28 | The watch store is a ViewModel retained across configuration changes. | `core/ui/mvi/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/mvi/processor/MetroStoreProcessor.kt:10-24` |
| F29 | The watch's surface mapper throws when completion is disabled for a reason it does not list. | `app/wear/.../ui/WearSurfaceModel.kt:190-198` |

## 4. Architecture

```text
phone                                                              watch
─────                                                              ─────
Room rows ── PhoneWorkoutSnapshotBuilder v2 ── outline (§5.3)
                     │
PhoneWorkoutBridgeImpl ── getActiveWorkout ──────── outline + session lease ──▶ reducer: display, authority (no target)
        │            └─ executeCommand ◀── watch_command(complete_set | set_exercise_skipped, target) ── owner: selection (D8), draft
        │                    │ writes the set or the skip flag, receipt, after commit:            │
        └─ ExternalSessionWrites (SetMarked | SkipChanged) ──▶ live-workout screen          cache v2 (snapshot + selection)
                                                                                           UI stack: List › Controller › Sets / Confirm
PhoneChangeNotifier (unchanged) ═══ CHANGED_PATH ═══▶ O6 handshake (unchanged)             Tile, ongoing indicator (follow selection)
```

The transport, the origins, the change signal, the allowlist and the cache's TTL rules do not
change. The protocol module's pure rules (`OutlineRules`, §5.3) are shared by the phone's
validation and the watch's selection and eligibility, so both sides read the outline the same
way.

## 5. Protocol v2

### 5.1 Versioning and compatibility (V1, P3)

- `WearProtocol.SCHEMA_VERSION` becomes 2. Every other constant of `WearProtocol` keeps its value,
  including both capabilities and both paths: the version lives only in `schemaVersion`. Keeping the
  RPC path is what lets a v2 phone answer an old watch at all; G9, G10 and G12 do not change.
- **Version answer (P3).** `WearProtocolCodec.decodeForPhone` returns a new
  `PhoneDecodeResult.UnsupportedVersion` for a request that is at most 16,384 bytes, strict UTF-8, a
  JSON object, has a canonical integer `schemaVersion` other than 2 and a string `operation`.
  `PhoneWearRpcHandler` keeps its order (F26): the work lease, the transport kill switch, then
  decoding; an `UnsupportedVersion` is answered with the constant `WearProtocolCodec.VERSION_ANSWER`,
  the UTF-8 bytes of exactly

  ```json
  {"operation":"unsupported_version","schemaVersion":2}
  ```

  and logged `rpc version -> unsupported: in N B, out M B, T ms`. It never calls the bridge and
  writes nothing. Every other undecodable request keeps getting empty bytes, and a closed kill
  switch keeps answering empty bytes before any decoding.

| Phone | Watch | Behavior |
|---|---|---|
| 1.53.x (v1) | 1.54.0 (v2) | The v1 phone drops v2 requests (F3): the watch shows "Couldn't reach your phone" with Retry until the phone updates. This is the usual window: the `wear:internal` release reaches the watch soon after the upload, while the production release waits for review and auto-update (ASM-P1). A residual (§11); the owner updates the phone first (§12.3). |
| 1.54.0 (v2) | 1.53.x (v1) | The phone answers every watch request with the version answer; the v1 watch decodes it as `UnsupportedSchemaVersion` and shows "Update Workeeper on phone and watch", read-only (F3, F4). Happens when the Wear upload failed or has not reached the watch; recovery: re-run the Wear deploy (release-flow §8.7). The change signal still reaches the watch, and its handshake gets the same answer. |
| 1.54.0 | 1.54.0 | v2. |

**ASM-P1**, verified after release, not before: an internal-testing release reaches the watch before
the production release reaches the phone. If false, the second row is the usual window instead.

### 5.2 Wire models

`core/wear-protocol/.../WireModels.kt` after the change. The codec settings, the strict decoding
rules and the discriminators (`operation` for envelopes, `state` for payloads, `kind` elsewhere)
do not change. Removed: `ActiveWithTarget`, `ActiveTarget`, `PhoneActionRequired`,
`PhoneActionReason`, `WorkoutComplete`, `CompleteCurrentSetRequest`, `CompleteCurrentSetBody`,
`CompleteCurrentSetResponse`. Unchanged: `GetActiveWorkoutRequest`,
`ActiveWorkoutSnapshotResponse`, `SnapshotData`, `MutationAuthority`,
`MutationUnavailableReason`, `BoundedDisplayName`, `ExerciseTypeWire`, `SetTypeWire`,
`NumericField`, `InvalidValueReason`, `ImmutableTypeField`, `ProtocolRejectionReason`.

```kotlin
@Serializable @SerialName("watch_command")
data class WatchCommandRequest(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionUuid: CanonicalUuid,
    val sessionRevision: Long,
    val mutationLeaseId: CanonicalUuid,
    val mutationLeaseGeneration: Long,
    val body: WatchCommandBody,
) : WearEnvelope

@Serializable @SerialName("watch_command_response")
data class WatchCommandResponse(
    override val schemaVersion: Int,
    override val correlationId: CanonicalUuid,
    val commandId: CanonicalUuid,
    val outcome: CommandOutcome,          // v1's CompleteCommandOutcome renamed, same variants and serial names
    val replacement: SnapshotData,
) : WearEnvelope

@Serializable @JsonClassDiscriminator("kind")
sealed interface WatchCommandBody {
    val performedExerciseUuid: CanonicalUuid

    @Serializable @SerialName("complete_set")
    data class CompleteSet(
        override val performedExerciseUuid: CanonicalUuid,
        val setPosition: Int,
        val reps: Int,
        val weightHundredthsKg: Int?,
        val exerciseType: ExerciseTypeWire,
        val setType: SetTypeWire,
    ) : WatchCommandBody

    @Serializable @SerialName("set_exercise_skipped")
    data class SetExerciseSkipped(
        override val performedExerciseUuid: CanonicalUuid,
        val skipped: Boolean,             // true: skip; false: return to the session
    ) : WatchCommandBody
}

@Serializable @JsonClassDiscriminator("state")
sealed interface SnapshotPayload {
    @Serializable @SerialName("no_session") data object NoSession : SnapshotPayload

    @Serializable @SerialName("active")
    data class Active(
        val sessionUuid: CanonicalUuid,
        val sessionRevision: Long,        // >= 0
        val trainingName: BoundedDisplayName,
        val exercises: List<OutlineExercise>,   // phone order, skipped included, distinct uuids
        val mutationAuthority: MutationAuthority,
    ) : SnapshotPayload

    @Serializable @SerialName("payload_too_large")
    data class PayloadTooLarge(val sessionUuid: CanonicalUuid, val sessionRevision: Long) : SnapshotPayload
}

@Serializable
data class OutlineExercise(
    val performedExerciseUuid: CanonicalUuid,
    val name: BoundedDisplayName,
    val exerciseType: ExerciseTypeWire,
    val skipped: Boolean,
    val sets: List<OutlineSet>,           // strictly ascending positions; may be empty
)

@Serializable @JsonClassDiscriminator("kind")
sealed interface OutlineSet {
    val position: Int                     // >= 0
    val setType: SetTypeWire

    /** Not done; its planned values are inside the watch domain, so the watch may complete it. */
    @Serializable @SerialName("open")
    data class Open(override val position: Int, override val setType: SetTypeWire,
        val reps: Int, val weightHundredthsKg: Int?) : OutlineSet   // reps 0..999; weight null or 0..99_999

    /** Done. Values are display-only; a value outside the watch domain is null and shown as "—". */
    @Serializable @SerialName("done")
    data class Done(override val position: Int, override val setType: SetTypeWire,
        val reps: Int?, val weightHundredthsKg: Int?) : OutlineSet

    /** Not done, and a planned value is outside the watch domain (P8): shown, not completable here. */
    @Serializable @SerialName("blocked")
    data class Blocked(override val position: Int, override val setType: SetTypeWire,
        val field: NumericField) : OutlineSet
}
```

Constructor checks (`require`, as v1's models do): revisions and positions non-negative; an
exercise's positions strictly ascending; the outline's `performedExerciseUuid` values distinct;
`Open.reps` in `0..MAX_WEAR_REPS`; a non-null weight in `0..MAX_WEAR_WEIGHT_HUNDREDTHS_KG`; a
`WEIGHTLESS` exercise has no non-null weight in any set; `Done.reps`, when non-null, in
`0..MAX_WEAR_REPS`.

### 5.3 Outline rules (`OutlineRules`, shared)

A new pure object in `core/wear-protocol`, used by the phone's validation and by the watch's
selection, eligibility and pairing:

- An exercise is **finished** when it is not skipped, has at least one set, and every set is `Done`.
- Its **status**: `SKIPPED` when skipped; `FINISHED` when finished; `NO_SETS` when it has no set;
  `OPEN` otherwise (it has an `Open` or `Blocked` set).
- Its **first incomplete set**: the `Open` or `Blocked` set with the lowest position.
- **Rule R** (`defaultSelection`, Phase 1 §4 over the outline): the first exercise in order whose
  status is `OPEN` or `NO_SETS`; the selection is its first incomplete set, or no position for
  `NO_SETS`. No such exercise: no selection. An exercise with no sets stops the search (P12).
- **Workout complete**: no exercise is `OPEN` or `NO_SETS` (an empty outline included, as Phase 1
  showed 0 of 0 as complete).
- **Exercise progress**: finished exercises out of the exercises that are not skipped (P13).
- On a `PayloadTooLarge` payload nothing is `completable` or `skippable`.
- `completable(outline, uuid, position)`: the exercise exists, is not skipped, and has an `Open`
  set at that position.
- `skippable(outline, uuid, skipped)`: the exercise exists and its `skipped` differs from the
  requested one.

The phone's finished rule equals its load-time done rule (F13): shared behavior vectors run
`ExerciseDoneRule` and the outline builder over the same planned, performed, skipped, empty,
sparse-position and extra-set fixtures (Phase 1 §4); a mismatch is a STOP.

### 5.4 Commands and outcomes

`CommandOutcome` keeps v1's variants and meanings. The phone's gateway, in order (v1's order with
the target rules of V3; F7):

1. epoch differs: `StaleRevision`;
2. no active session: `NoActiveSession`;
3. another session: `StaleRevision`;
4. receipt (P4): only when the stored receipt's command id equals the request's is the stored
   fingerprint parsed; an unsupported fingerprint version is
   `ProtocolRejected(UNSUPPORTED_FINGERPRINT_VERSION)`, a different fingerprint
   `ProtocolRejected(COMMAND_FINGERPRINT_MISMATCH)`, a match at the current epoch and revision
   `AlreadyApplied`; a receipt of another command is ignored;
5. revision differs: `StaleRevision`;
6. the performed exercise is not in the session: `TargetChanged`;
7. lease admission (§6.2): `AuthorizationExpired`, or `ProtocolRejected(COMMAND_FINGERPRINT_MISMATCH)`;
8. body:
   - `complete_set`: not `completable` (skipped exercise, unknown position, a `Done` or `Blocked`
     set): `TargetChanged`; exercise type differs from the outline's:
     `ImmutableTypeMismatch(EXERCISE_TYPE)`; set type differs from the outline set's:
     `ImmutableTypeMismatch(SET_TYPE)`; `CommandValidation` fails: `InvalidValues`; else the set is
     written;
   - `set_exercise_skipped`: not `skippable`: `TargetChanged`; else the flag is written;
9. after the write the revision has advanced, the receipt is stored at the new revision, and the
   outcome is `Applied` with a fresh outline and lease.

`InvalidValues` and `ImmutableTypeMismatch` exist only for `complete_set`.

### 5.5 Pairing

`ProtocolPairingValidator` (both sides):

| Outcome | Allowed replacement |
|---|---|
| `NoActiveSession` | `NoSession` |
| `TargetChanged` | `Active` or `PayloadTooLarge` |
| `InvalidValues`, `ImmutableTypeMismatch` | `Active` with `Unavailable` authority |
| `ProtocolRejected` | `NoSession`, `PayloadTooLarge`, or `Active` with `Unavailable` |
| `RetryableTemporaryFailure` | `Active` with `Granted` authority |
| `Applied`, `AlreadyApplied`, `AuthorizationExpired`, `StaleRevision` | any |

When the replacement for `InvalidValues`, `ImmutableTypeMismatch` or `RetryableTemporaryFailure`
would be `PayloadTooLarge`, the phone sends `TargetChanged` instead, as v1 already does for
`RetryableTemporaryFailure` (F27). With the budget of §5.7 this happens only when the session grew
between the outline the watch holds and the response.

The watch also requires (`responsePairingIsValid`): `StaleRevision` with a replacement of another
source; `NoActiveSession` with the same epoch; `TargetChanged` with the same source and a
replacement where the command's target is no longer `completable` or `skippable` (always true for
`PayloadTooLarge`); `InvalidValues` and `ImmutableTypeMismatch` only answering a `complete_set`.
Anything else closes the command as a protocol mismatch, as today.

### 5.6 FingerprintV2 (P2)

`FingerprintV1.kt` is replaced by `FingerprintV2.kt`. The preimage is:

| Bytes | Content |
|---|---|
| 4 | magic `57 4B 57 46` ("WKWF", unchanged) |
| 2 | encoding version, `00 02` |
| 1 | purpose: `01` stable intent, `02` delivery attempt |
| 1 | command kind: `01` complete_set, `02` set_exercise_skipped |
| 2 | field count |
| … | fields in ascending tag order: tag (1 byte), length (Int32), value |

| Tag | complete_set | set_exercise_skipped | Value |
|---|---|---|---|
| `01` | source node id | source node id | UTF-8, not empty |
| `02` | schemaVersion | schemaVersion | Int32 |
| `03` | commandId | commandId | 16 bytes |
| `04` | databaseEpoch | databaseEpoch | 16 bytes |
| `05` | sessionUuid | sessionUuid | 16 bytes |
| `06` | sessionRevision | sessionRevision | Int64 |
| `07` | performedExerciseUuid | performedExerciseUuid | 16 bytes |
| `08` | setPosition (Int32) | skipped (1 byte: `00` return, `01` skip) | |
| `09` | reps (Int32) | lease id (attempt only, 16 bytes) | |
| `0a` | weight (`00`, or `01` + Int32) | lease generation (attempt only, Int64) | |
| `0b` | exerciseType (1 byte) | — | |
| `0c` | setType (1 byte) | — | |
| `0d` | lease id (attempt only) | — | |
| `0e` | lease generation (attempt only) | — | |

Field counts: complete_set 12 (stable) and 14 (attempt), set_exercise_skipped 8 and 10. The value is
34 bytes: `00 02` and the SHA-256 digest of the preimage. `FingerprintValue.parse` accepts only
version 2; `isCanonicalPreimage` validates per kind. complete_set keeps V1's tags, so only the header
differs. Known-answer vectors, one per kind and purpose, are generated by the implementation,
checked in the PR by an independent computation of the same preimage (a short script, its output in
the PR body), and pinned where V1's were (F8).

### 5.7 Size (V5, P15)

- `WearProtocol` gains `COMMAND_HEADROOM_BYTES = 512` and
  `OUTLINE_BUDGET_BYTES = MAX_ENVELOPE_BYTES - COMMAND_HEADROOM_BYTES` (15,872).
- The phone sends an `Active` outline only when an `ActiveWorkoutSnapshotResponse` carrying it with
  granted authority encodes to at most `OUTLINE_BUDGET_BYTES`, measured with the widest values of
  every authority field (a 19-digit lease generation and the 120,000 ms window), so the decision
  does not depend on the lease. Otherwise it sends `PayloadTooLarge`. Every response, snapshot or
  command, granted or read-only, builds its replacement through that one check, so a command
  response for an outline inside the budget always fits: it adds at most the command id and the
  largest outcome, far below 512 bytes. No `check(fits)` on a response path can fail (F27).

Estimated by a script that mirrors the JSON of §5.2, with granted authority; two reviews reproduced
the figures within 2 %. The implementation's codec decides, and discovery measures it (§14 Q6):

| Session | Bytes | Result |
|---|---|---|
| Mockup "typical": 6 exercises, 19 sets, Cyrillic names | about 3,300 | sent |
| 12 × 4 sets, 61-character Cyrillic names, maximum values | about 7,800 | sent |
| 20 × 5, same names and values | about 14,300 | sent |
| 20 × 6, same | about 15,900 | `PayloadTooLarge` |
| 14 × 4, 512-byte names | about 15,000 | sent |
| 25 × 6, same as 20 × 5 | about 19,800 | `PayloadTooLarge` |

Each set costs about 84 bytes, and each exercise about 260 more with such a name. Codec tests pin:
the 20 × 5 stress outline is sent, and its largest command response (the widest outcome, granted
authority) fits; an outline within 64 bytes under the budget is sent and its largest command response
fits; one just over the budget becomes `PayloadTooLarge`; a 30 × 5 outline falls back; the fallback
stays under `MAX_PAYLOAD_TOO_LARGE_FALLBACK_BYTES`. If the 20 × 5 stress outline exceeds the budget
with the real codec: STOP, and the owner chooses shorter set keys or a smaller promise.

### 5.8 Change signal

Unchanged (live sync §5 and §7.3): content-free, the watch ignores its payload, and the phone's
notifier and origin O6 stay as release 1.53.1 ships them (the notifier with live sync D13 and D15).
A skip or a return changes `wear_revision` (F10), so it is signalled like a set.

## 6. Phone side

### 6.1 Outline builder (V2)

`PhoneWorkoutSnapshotBuilder.build(databaseEpoch)` keeps its inputs, its transaction and
`loadPlans` (F5) and builds the outline instead of a target:

1. No active session: `NoSession`.
2. Every performed exercise of the session, ordered by `position`, skipped ones included.
3. Per exercise, the expected positions are plan indices ∪ performed positions, ascending (as
   today). For each position:
   - a performed set exists: `Done(position, row type, reps, weight)`, each value converted on its
     own by the watch-domain rule for that field; a value that does not convert is null.
     `snapshotNumeric` returns at the first failing field (it checks reps before weight), so the
     builder uses two per-field helpers extracted from it, and `snapshotNumeric` is rebuilt on them;
   - otherwise the plan row at that position (it must exist, as today's `requireNotNull` says):
     `snapshotNumeric(exercise type, plan reps, plan weight)`; `Value` gives
     `Open(position, plan type, reps, weight)`, `Unsupported(field)` gives
     `Blocked(position, plan type, field)`.
4. `Active(session uuid, wear_revision, training name, exercises, Unavailable)`; the bridge grants
   authority per response (§6.2).
5. `payloadTooLarge(base)` returns `PayloadTooLarge(session uuid, revision)` for any session state,
   and every response path applies the budget of §5.7 through one function before it adds
   authority.

Names stay `BoundedDisplayName.from(...)`. A phone draft and a row-count override are never read
(Phase 1 §4): only persisted rows count.

### 6.2 Lease (V3)

- `PendingMutationLease` and the admission presentation lose `performedExerciseUuid` and
  `setPosition`. `matches` compares epoch, revision, lease id and generation.
- `allocateCandidate` takes an `Active` outline, and a lease is offered with every `Active`
  response that fits, whatever its exercises' states: an all-finished outline still allows
  returning a skipped exercise.
- Unchanged: one slot per (node, session), the 120 s window, one command per lease, at most two
  deliveries per attempt, the retry binding, `publishIfCurrent`, and retirement of every slot after
  every `mutate` commit (V7).

### 6.3 Commands

`PhoneWorkoutBridge.completeCurrentSet` becomes `executeCommand(authenticatedSourceNodeId,
request: WatchCommandRequest): WatchCommandResponse`, under the same `coordinatorMutex`, response
deduplication and `WatchKnownRevisions` recording: `shown` takes the `WatchCommandResponse` it
returns, and `sessionIdentityOrNull()` (`PhoneWorkoutSnapshotBuilder.kt:248-253`) gives `Active`
and `PayloadTooLarge` their session and revision, so live sync D7 keeps working. `processCommand`
follows §5.4.

- Writers, both inside the command's `transition.mutate`: `WearSetMutationWriter` (unchanged,
  `upsertByTarget`) and a new `fun interface WearSkipMutationWriter { suspend fun write(
  performedExerciseUuid: Uuid, skipped: Boolean) }` whose Room implementation calls
  `PerformedExerciseDao.setSkipped` (F9). After either write: `check` that the revision advanced
  (trigger F10 for a skip), then the receipt.
- After `mutate` returned `Applied`, still under `coordinatorMutex`: the bridge publishes
  `ExternalSessionWrite.SetMarked` (the values as written, as today) or
  `ExternalSessionWrite.SkipChanged(session, exercise, skipped)` (§6.5). `AlreadyApplied`, every
  rejection and the write-failure path publish nothing.
- Write failure (`SQLiteException`): re-read at the request's epoch, session and revision; if the
  command is still `completable` or `skippable`, `RetryableTemporaryFailure` with the retry binding
  (as today); otherwise `StaleRevision`, `NoActiveSession` or `TargetChanged`.
- Publication loss (`terminalOutcomeAfterPublicationLoss`): another epoch, session or revision gives
  `StaleRevision`, no session `NoActiveSession`, the same `AuthorizationExpired`. The target
  comparison goes with V3.
- Size: the replacement of every response goes through the budget check (§5.7); an outcome that
  needs an `Active` replacement but gets `PayloadTooLarge` is sent as `TargetChanged` (§5.5).
  `readOnlyCommandResponse` keeps its `check(fits)`, which the budget now guarantees.
- Receipts (P4): `receiptOutcome` compares `receiptCommandId` with the request's first and returns
  null for another command, so a V1 receipt left by 1.53.x never rejects a v2 command.
- `protocolRejected` (a correlated rejection of a body that failed strict numeric decoding):
  unchanged, for the new envelope.
- P7: a skip of any exercise that is not skipped is accepted, as the phone's own toggle accepts it.

### 6.4 RPC handler and codec

- `decodeForPhone`: `get_active_workout` as today; `watch_command`: the routing tuple as today; a
  `complete_set` body keeps the strict integer checks of `reps` and `weightHundredthsKg` that turn
  a bad encoding into the correlated `INVALID_NUMERIC_ENCODING` rejection; a
  `set_exercise_skipped` body needs a JSON boolean `skipped`, else `Dropped`; another schema
  version: `UnsupportedVersion` (§5.1), after the work lease and the kill switch, as every request
  (F26).
- `PhoneWearRpcHandler` dispatches to `getActiveWorkout`, `executeCommand`, `protocolRejected`, the
  version answer, or empty bytes. Log operations: `get_active_workout`,
  `watch_command:complete_set`, `watch_command:set_exercise_skipped`, `protocol_rejected`,
  `version`; result class, byte counts and time only (transport §7.9).

### 6.5 Live-workout screen (V4)

- `core/data/exercise/.../session/ExternalSetWrites.kt` becomes `ExternalSessionWrites` (same
  app-scoped `MutableSharedFlow(replay = 0, extraBufferCapacity = 64)`, same `tryEmit` and refusal
  log), publishing

  ```kotlin
  sealed interface ExternalSessionWrite {
      val sessionUuid: String
      val performedExerciseUuid: String
      data class SetMarked(/* today's ExternalSetWrite fields */) : ExternalSessionWrite
      data class SkipChanged(override val sessionUuid: String,
          override val performedExerciseUuid: String, val skipped: Boolean) : ExternalSessionWrite
  }
  ```

  The rename reaches `LiveWorkoutGraph.externalSetWrites`, `AppGraph.externalSetWrites` and
  `LiveWorkoutExtensionIdentityTest` (`app/app`), which pins that the live-workout extension holds
  the parent's instance.
- The interactor's flow filters by session and maps both kinds to the domain model; the store's
  subscription, its restart on every `Init`, and the load bookkeeping (`ExternalWriteCoverage`,
  live sync D10) carry both kinds, in arrival order. Both kinds take the path `applyExternalWrite`
  takes today (`CommonHandler.kt:173-177`): `coverage.receive` first, outside the update, then
  `updateState` with a pure update function (live sync D14; F11).
- `SkipChanged` is applied like a set write (live sync §6.4 A to C):
  - **A.** The exercise is in `State.exercises`: a new `LiveSetMutator.applySkipped(state, uuid,
    skipped)` returns the state unchanged when the exercise is already in the requested skip state,
    and otherwise delegates to `applySkipToggle(state, uuid, skipped)` (F12), which sets the
    status explicitly (SKIPPED, or PENDING when returned), keeps performed sets and drafts, and
    recomputes statuses, counts and progress. Both are pure functions of the state, with no side
    effect, because a compare-and-set may run them again (live sync D14). An open undo window's
    snapshot holding the exercise gets the same patch.
  - **B.** The exercise is only in an open undo window's snapshot: the snapshot is patched.
  - **C.** Neither: ignored; the next load shows the database.
- Nothing else in `State` changes: dialogs, sheets (an open exercise menu shows the new label),
  drafts, row overrides, expansions, the timer. No re-read, no Action, Event, haptic or navigation.
- P14: the exercise menu's action becomes `Action.Click.OnSkipExercise(uuid, skipped)`, carrying
  the intent of the label it showed; `processSkipExerciseToggle` writes that value and does nothing
  when the exercise already has it. Without this, a watch skip arriving while the menu is open would
  turn the user's tap into a return.

## 7. Watch side: state

### 7.1 Display and authority

- `WatchDisplayState`: `Loading`, `NoSession`, `Active(snapshot, freshness)`,
  `PayloadTooLarge(snapshot)`, `ProtocolMismatch`. "Workout complete" is derived from the outline
  (§5.3), not a display state.
- `LocalMutationAuthority.Available(leaseId, leaseGeneration, source, effectiveDeadlineMs)`: no
  target. `AttemptBound` carries the command's `CommandTarget` (`CompleteSet(TargetKey)` or
  `SetSkipped(uuid, skipped)`) for the in-flight display only.
- `WatchWorkoutReducer`:
  - `applySnapshot` installs `Available` for an admitted `Active` outline with a granted lease, any
    exercise states;
  - `issueCommand` accepts a command when the authority is `Available` and inside its deadline, the
    command's epoch, session, revision and lease equal the authority's, no command is open, and
    `OutlineRules` allows its target on the admitted outline;
  - `invalidateObsoleteCommand`, `currentMatches` and `retryableSuccessorMatches` compare sources
    only: the database triggers cover every input of the outline builder (F10 and live sync F1), so
    the same source is the same outline. A `PayloadTooLarge` snapshot never matches a command's
    source;
  - `responsePairingIsValid` follows §5.5;
  - `restoreDisplayOnly` strips authority from a restored `Active` outline, as today.
- `SnapshotAdmissionPolicy` is unchanged, with `Active` where it read `ActiveWithTarget`.
- `WatchInteractionEligibility` gains the selection: completion needs `Available`, no open command,
  no refresh required, an `Open` selected set and valid draft values; skip and return need
  `Available`, no open command, no refresh required and `skippable`.

### 7.2 Selection (D8)

`WatchSelection(performedExerciseUuid, setPosition: Int?)`, owned by `WatchRuntimeOwner` and
published in `WatchRuntimeSnapshot.selection`.

On every admitted `Active` outline, of any origin and the restored cache included,
`reconcileSelection` runs:

1. no selection, or an outline of another session: rule R;
2. the selected exercise is gone, skipped or finished: rule R;
3. the selected position is still `Open` or `Blocked`: kept;
4. otherwise (the set is now `Done`, its position is gone, or the exercise had no sets and now has
   some): the first incomplete set of the same exercise.

User selection: opening an exercise from the list selects its first incomplete set; picking a set
on Sets selects that set (only `Open` and `Blocked` rows can be picked). Skip and return change the
selection only through the next outline (rules 1 and 2), as in the mockup's flow. `NoSession`
clears the selection; `PayloadTooLarge` keeps it until an outline arrives.

While a command is open, the selection may still change. The in-flight state ("Sending…", Retry) is
shown only where the selection, or a list row, is the command's target; completion, skip and return
stay disabled until the command closes (§7.1).

### 7.3 Draft (P6)

One `PendingDraft(source, target: TargetKey, values)`, on the selected `Open` set. It is dropped
when the selection changes, when the source changes (F18 of live sync), or when it equals the set's
planned values. Completion sends the draft, or the planned values.

### 7.4 Commands from the watch

- `ControllerAction` gains `SelectExercise(uuid)`, `SelectSet(uuid, position)`,
  `SkipExercise(uuid)` and `ReturnExercise(uuid)`; `CompleteSet` and `AdjustDraft` act on the
  selection. The registry gate does not cover `ControllerAction` (F21): every new action, and the
  existing `AdjustDraft`, `SetReps` and `SetWeight`, get a `toString()` naming the type only,
  pinned by a unit test.
- `CompleteSet` builds a `complete_set` body from the selected `Open` set (its types from the
  outline) and the draft; skip and return build `set_exercise_skipped`.
- `WatchTransportCoordinator` encodes `WatchCommandRequest` from the reducer's command, decodes
  `WatchCommandResponse`, and logs `response:<Outcome>:<admission>`. Single flight, coalescing,
  late tokens, retry preservation and O1–O6 do not change.

### 7.5 Cache v2

- `CACHE_SCHEMA_VERSION` 2 and a new flag `FLAG_SELECTION = 0x08`. When set, after the optional
  ongoing deadline: the selected exercise's 16-byte UUID, a presence byte (`00` no position, `01`
  then an Int32 position). `MAX_FRAME_BYTES` grows by 21.
- A version-1 frame, an unknown flag or a short selection is corrupt and deleted, as today (F20).
  No migration: the first launch after the update starts from `Loading`. A cached v1 payload fails
  `decodeForWatch` and is deleted the same way.
- `WatchSnapshotCache.updateSelection(selection)` rewrites the record with its payload, metadata and
  the new selection; the owner calls it on every selection change while a record exists, and every
  snapshot write carries the current selection.

### 7.6 Tile and ongoing indicator (D9)

- Tile lines for an `Active` outline: a selected `Open` set: training, exercise,
  `set_progress(ordinal, total)` (the set's index among the exercise's sets, plus one),
  `exercise_progress(finished, total)`; a selected `Blocked` set: training, exercise,
  `edit_set_on_phone`; a selection without a position: training, exercise, `add_set_on_phone`; no
  selection: today's workout-complete lines. Other states as today. The Tile's resources version
  becomes `wear_phase_2_v1`.
- The ongoing indicator's `LeaseSignature` drops the target: it is posted for a fresh granted lease
  on any `Active` outline. Its text and channel stay; its tap opens the app as P9 says.

## 8. Watch side: UI (PR-P2B)

The mockup is normative (status block); its control names are *Set choice* and *Skip*, variant A of
each. Where this section names a mockup screen (L1–L3, C1–C2, S1–S2, K1, U1 the return
confirmation, E1 the list with everything finished), its measured layout binds at 192 and 240 dp,
in EN and RU, at font scales 1.0 and the largest, for the typical and stress fixtures.

### 8.1 Navigation (P5, P9)

- `WearStore.State` gains `destinations: List<WearDestination>`; the last one is shown. The numeric
  editor stays the `editor` overlay of the Controller.
- `WearDestination`: `List`, `Controller`, `Sets(performedExerciseUuid)`,
  `ConfirmSkip(performedExerciseUuid)`, `ConfirmReturn(performedExerciseUuid)`. The Controller
  always shows the selection (§7.2).
- Session-level surfaces (Loading, NoSession, PayloadTooLarge, ProtocolMismatch, a retryable
  command's Retry, the link-failure Retry) are shown instead of the stack while they hold. The stack
  stays underneath and shows again when an `Active` outline of the same session returns; an outline
  of another session applies the entry rule. Back or swipe on a session-level surface closes the
  activity: it never pops a stack the user cannot see.
- **Entry rule (P9).** The store is a ViewModel (F28), so its initial stack is set when it is
  created, not on every `onCreate`: `[List, Controller]` when the runtime has a selection,
  otherwise `[List]` with an entry pending. While an entry is pending, the first admitted `Active`
  outline that yields a selection turns the stack into `[List, Controller]` and clears it; an
  outline without one clears it and leaves `[List]`. `MainActivity.onNewIntent` (Tile, ongoing
  indicator or launcher while the app runs) dispatches an entry action that applies the same rule.
  A configuration change keeps the stack.
- **Reconciliation (P16).** After every admitted outline, before it is shown:
  - `Sets(x)`, `ConfirmSkip(x)` and `ConfirmReturn(x)` are dropped when `x` is gone;
  - `ConfirmReturn(x)` is dropped when `x` is no longer skipped, `ConfirmSkip(x)` when `x` is skipped
    or finished;
  - with no selection the stack becomes `[List]`, whatever is on top (the prototype's
    `completeSelected`);
  - the editor closes when the selected set changed.
- Transitions, as in the mockup's prototype:

| From | Event | Result |
|---|---|---|
| List | row of an `OPEN` or `NO_SETS` exercise | `SelectExercise`; push Controller |
| List | row of a `FINISHED` exercise | push Sets(uuid), read-only |
| List | row of a `SKIPPED` exercise | push ConfirmReturn(uuid) |
| Controller | exercise name, or the Sets row | push Sets(selected exercise) |
| Sets | an `Open` or `Blocked` row | `SelectSet`; pop to the Controller, or replace Sets with a Controller when none is beneath (a read-only Sets whose exercise became open again) |
| Controller | Skip | push ConfirmSkip(selected exercise) |
| ConfirmSkip | edge button, command issued | `SkipExercise`; stack `[List]`, centered on the selection |
| ConfirmReturn | edge button, command issued | `ReturnExercise`; stack `[List]`, centered on that row (P17) |
| ConfirmSkip, ConfirmReturn | edge button, command not issued | the stack stays; the reason shows (§8.6) |
| any | swipe or Back | the editor closes if open, else pop; at `[List]` the activity finishes |

- Each pushed level is dismissed by a swipe that shows the level below as its background, and by
  Back. Built from the swipe-to-dismiss box the editor already uses; no
  `androidx.wear.compose:compose-navigation` dependency.

### 8.2 Common

`ScreenScaffold` with `TimeText`, a vertically scrolling column with `rotaryScrollable` and
`requestFocusOnHierarchyActive` (as the controller does today), `WearPalette` and the theme's
typography (`rememberWearTypography`), every touch target at least 48 dp. Each new screen is a pure
Composable of a presentation model mapped by `WearPresentationMapper`, rendered by a fixture in
`SyntheticSurfaceFixtures`.

### 8.3 List (L1–L3)

- Header: the training name (`titleMedium`, up to 3 lines) and `exercise_progress(finished,
  not skipped)` (`bodySmall`, P13). Workout complete (§5.3): `workout_complete` and
  `finish_on_phone` instead (E1).
- One row per exercise in phone order: at least 52 dp tall, radius 14 dp, full width; the name
  (`titleSmall`, up to 3 lines); a second line by status:
  - `OPEN`: the exercise's set pills (up to 8, in the bucket ranges `WearSetScaleSlot` computes; a
    bucket is filled when every set in it is done, which `WearSetScaleSlot`'s ordered `last < current`
    rule cannot express; on the selected row the bucket holding the selected set is outlined) and
    "done/total";
  - `FINISHED`: the same pills dimmed, a check and "done/total"; inactive fill;
  - `SKIPPED`: the skip icon and `wear_skipped`; outline only, no fill;
  - `NO_SETS`: `wear_no_sets`.
  The selected row has a 1.5 dp border in the primary text color. While a skip or return of that
  exercise is in flight, its second line shows `complete_reason_sending`.
- Entered by Back from the Controller, the list is centered on the selected row (L2); on entry with
  no selection, at the top.

### 8.4 Controller (C1–C2)

Today's `ActiveScaffold`, with:

- the time text leading with the selected set's "ordinal/total" and " · " before the time (D7);
- the exercise name at the top (primary context) acting as a button that opens Sets;
- at the end of its scroll, one row: the Sets row (`wear_sets`, the exercise's pills with the
  selected set outlined, a chevron) and beside it an icon-only Skip button, 48 dp, spoken as
  `wear_skip_exercise` (D3, D4);
- the Complete edge button acting on the selection.

By selection: an `Open` set as today; a `Blocked` set: both value cards show "—" (the outline
carries no values for it), Complete is disabled with a new `CompletionUnavailableReason.BLOCKED`
whose text is `edit_set_on_phone`; an exercise without sets: today's instruction surface with
`add_set_on_phone` and a new reason `NO_SETS`, and at the end of its scroll a full-width labelled
Skip row (P11). Both new reasons are added to the surface mapper's list, which throws for an
unlisted one (F29). The exercise name at the top keeps opening Sets whatever text the primary
context shows. The in-flight state of a command shows only when the command's target is the
selected set or exercise (§7.2). Skip is offered only for the selected exercise, which is never
finished or skipped.

### 8.5 Sets (S1–S2)

- Header: the exercise name (up to 3 lines) and `wear_sets_done(done, total)`.
- One row per set: its number, under it the set-type mark for warm-up, failure and drop sets
  (`wear_set_mark_warm`, `wear_set_mark_fail`, `wear_set_mark_drop`; none for a work set); the
  weight with `kg` for a weighted exercise and "× reps"; a check on a done set. Done rows have the
  inactive fill and cannot be tapped; the selected row has the 1.5 dp border; a `Blocked` row and a
  done value outside the domain show "—".
- A finished exercise: every row read-only and, after the rows, `wear_edit_on_phone` (D5).
- Opened on the selected set (S1) or at the top.

### 8.6 Confirmations (K1)

The title (`wear_skip_exercise_question` or `wear_return_question`), the exercise name, and an edge
button with the skip or the return glyph (K1, U1). Swipe back cancels. The edge button is enabled
when §7.1 allows the command; otherwise it is disabled, the reason shows as a visible line under the
exercise name in today's `complete_reason_*` text, and the button's spoken state says it is
disabled, as the Complete button's `complete_set_disabled_description` does today.

### 8.7 Ambient

`WearAmbientSummary` shows the selection, on any destination: the exercise, "ordinal/total", the
values, the status line and the draft line, as today for the target. No selection: today's
workout-complete lines.

### 8.8 Copy

New strings, EN and RU, in `app/wear/src/main/res/values{,-ru}/strings.xml`:

| Name | EN | RU |
|---|---|---|
| `wear_sets` | Sets | Подходы |
| `wear_sets_done` | %1$d of %2$d done | Выполнено %1$d из %2$d |
| `wear_skipped` | Skipped | Пропущено |
| `wear_no_sets` | No sets | Нет подходов |
| `wear_skip_exercise` | Skip exercise | Пропустить упражнение |
| `wear_skip_exercise_question` | Skip exercise? | Пропустить упражнение? |
| `wear_return_question` | Return to session? | Вернуть в сессию? |
| `wear_edit_on_phone` | Edit on your phone | Изменить можно на телефоне |
| `wear_set_mark_warm` | W | Р |
| `wear_set_mark_fail` | F | О |
| `wear_set_mark_drop` | D | Д |
| `wear_set_type_work` | Working set | Рабочий подход |
| `wear_set_type_warm` | Warm-up set | Разминочный подход |
| `wear_set_type_fail` | Failure set | Подход до отказа |
| `wear_set_type_drop` | Drop set | Дроп-сет |
| `wear_set_row_description` | Set %1$d of %2$d, %3$s | Подход %1$d из %2$d, %3$s |
| `wear_set_done` | Done | Выполнен |
| `wear_selected` | Selected | Выбрано |
| `wear_exercise_row_description` | %1$s, sets done: %2$d of %3$d | %1$s, выполнено подходов: %2$d из %3$d |

The skip and return wording is the phone's (F9). Reused: `exercise_progress`, `set_progress`,
`workout_complete`, `finish_on_phone`, `add_set_on_phone`, `edit_set_on_phone`, `weight_value`,
`ambient_reps`, `complete_reason_*`.

The string gate needs two changes, both test code listed in the PR (F21):

- its 3-character rule has no exemption, and the three set-type marks are one letter by design: the
  gate gains a separate, named allowlist for exactly these three names, with that reason; their
  rows' spoken text uses the `wear_set_type_*` words;
- its literal-segment matching would credit `set_progress` to `wear_set_row_description`,
  `wear_sets_done` to `wear_exercise_row_description`, and `wear_skip_exercise` to
  `wear_skip_exercise_question`: as the gate's own comment requires, its matcher becomes exact, and
  a test neutralises each of those three renderers in turn and sees the gate name it.

### 8.9 Accessibility

Icon-only buttons have spoken names; list rows and set rows are buttons whose descriptions use the
row strings above (a skipped row: its name and `wear_skipped`; a selected or done set: the state
words); the time text's position is also spoken in the details as `set_progress`; every scrollable
screen takes rotary input; the first-view gates (§10.1) cover each new screen.

## 9. Privacy, product and listing (V6)

- `docs/index.md`: Appendix A replaces the English and the Russian Wear paragraphs and both "Last
  updated" lines, nothing else. This is an owner-authorized exception to the repository lock, like
  transport D1. It lands in PR-P2A, the PR that starts sending the outline, and goes live when the
  release merges into `master`: Pages publishes from `master` (transport ASM-4; its ledger shows the
  policy served six minutes after the `master` merge, `wear-paired-transport.md`, ledger row "Policy
  live (§9.1)"; §14 Q4).
- Data safety: the implementer compares the form with the new text and records the verdict. The
  expected verdict is no change: the same kinds of data as today, between the user's own devices,
  with end-to-end encrypted relay (transport §9.3). A needed change is an owner action, not a STOP.
- `documentation/product.md`: Appendix B, in PR-P2A.
- The Wear listing has no text (only screenshots); the phone listing does not mention the watch.
  Screenshots: P10.
- Logs: no new field beyond operation, result class, byte counts and time; the selection, names and
  values are never logged.

## 10. Test contract

### 10.1 Host tests (new or changed)

`core:wear-protocol`:

- codec: every v2 envelope and body round-trips and re-encodes to the same bytes; strict decoding
  rejects unknown keys, wrong discriminators, a float `reps` and a string `skipped`; every v1
  operation name is unknown; a schema-1 request decodes on the phone as `UnsupportedVersion`;
  `VERSION_ANSWER` equals the pinned bytes, and a v1-style check of it (object, integer
  `schemaVersion` other than 1) is a schema mismatch;
- each constructor check of §5.2;
- the pairing matrix of §5.5: every outcome against every payload kind and authority;
- size (§5.7): the typical fixture; the 20 × 5 stress outline is sent and its largest command
  response fits; an outline just under the budget is sent and its largest command response fits;
  one just over becomes `PayloadTooLarge`; 30 × 5 falls back; the fallback stays under
  `MAX_PAYLOAD_TOO_LARGE_FALLBACK_BYTES`; the budget check gives the same answer for any lease;
- FingerprintV2: the known-answer vectors per kind and purpose; canonical preimage validation
  (header, per-kind tags and sizes, no trailing bytes); `parse` rejects version 1; a `complete_set`
  and a `set_exercise_skipped` with equal common fields never share a digest;
- `OutlineRules`: status, first incomplete set, rule R (a `NO_SETS` exercise stops the search,
  skipped ones are passed over, nothing left gives none), workout complete (an empty outline
  included), `completable`, `skippable`.

`:feature:wear-bridge` (the in-memory database with `prepareWearSyncStorage`):

- outline builder: phone order with skipped exercises; plan ∪ performed positions; `Done` values
  from rows, a value outside the domain null; `Open` from the plan; `Blocked` for each field; a
  `NO_SETS` exercise; ad-hoc and missing plans; a performed position beyond the plan; the shared
  done-rule vectors of §5.3;
- lease: a handshake for an all-finished outline grants; a command for any `Open` set at the
  lease's revision is admitted; a phone write retires the lease;
- `complete_set`: every branch of §5.4, each asserting the outcome, the replacement's shape and
  authority, and whether a set was written; `Applied` advances the revision, stores the receipt and
  publishes one `SetMarked` after `mutate` returned (the transition-wrapper pattern of live sync
  §10.1); an exact replay is `AlreadyApplied` and publishes nothing;
- `set_exercise_skipped`: skip and return are `Applied` (flag written, revision advanced by the
  trigger, receipt, one `SkipChanged` after `mutate` returned); skipping a skipped exercise and
  returning one that is not skipped are `TargetChanged`; a finished exercise can be skipped (P7); a
  replay is `AlreadyApplied` and publishes nothing;
- receipts (P4): a stored V1 receipt of another command does not affect a v2 command; the same
  command id with an unparsable fingerprint version is
  `ProtocolRejected(UNSUPPORTED_FINGERPRINT_VERSION)`;
- write failure, for each kind: `RetryableTemporaryFailure` while still applicable, else the other
  outcomes; publication loss at the same revision: `AuthorizationExpired`;
- size on response paths: a session that grows past the budget between the handshake and a command
  gets `PayloadTooLarge` replacements on every path, an `InvalidValues` or
  `ImmutableTypeMismatch` becomes `TargetChanged`, and no path throws;
- RPC handler: a v1 `get_active_workout` and a v1 `complete_current_set` get exactly
  `VERSION_ANSWER` and never reach the bridge; a malformed request still gets empty bytes; a
  `set_exercise_skipped` with a string `skipped` is dropped;
- notifier: with v2 responses, a granting handshake still sends nothing (M-S1) and the commanding
  watch is still not signalled (D7 of live sync), for both command kinds. The live sync signal
  tests (`PhoneWorkoutBridgeSignalTest`, `PhoneChangeNotifierTest`, both extended by PR-S2) move to
  `executeCommand` and v2 fixtures with their assertions, except two cases built on v1's
  target-bound lease: in `rejections publish nothing` and `TargetChanged publishes nothing` the
  command for the next planned set is now admitted (V3), so it aims instead at a target that is not
  `completable` (an unknown or `Done` position, or a skipped exercise), and `bindSyntheticLease`
  loses its target fields.

`:feature:live-workout`:

- `SkipChanged` cases A, B and C; applying it twice equals once; a skip received during an `Init`
  load or a `processReload` is shown after the load; for the same input, the phone's own toggle and
  an external skip give the same state; nothing else in `State` changes; another session's skip is
  filtered out; live sync D14's atomic-writes test (`CommonHandlerExternalWritesTest`) also emits
  a `SkipChanged`, and every store write stays a compare-and-set;
- P14: with the menu open on "Skip exercise", a watch skip arrives, then the user taps: the exercise
  stays skipped and nothing is written.

`:app:wear`:

- reducer: authority without a target; a command for any completable target is issued, a command
  for a non-completable one is rejected; invalidation on a source change only; a `PayloadTooLarge`
  snapshot never matches a command's source; §5.5's watch rules, `TargetChanged` with a
  `PayloadTooLarge` replacement included; retry rebinding without a target comparison;
- selection: every rule of §7.2, `SelectExercise` and `SelectSet`, the bodies skip and return send,
  the draft dropped on a selection change; with a command open, a changed selection shows no
  in-flight state and cannot complete;
- `ControllerAction`: every leaf's `toString()` names its type only;
- persistence: the selection survives `restore()` from the cache (process death) and is reconciled
  against the restored outline;
- cache: a v2 frame round-trips with no selection, a selection with a position and one without; a
  version-1 frame and an unknown flag are corrupt and deleted; the frame bounds;
- coordinator: both command kinds are sent and labelled; the existing single-flight and O6 tests
  pass with only their fixtures moved to v2: `ReducerTestFixtures.active` (also the default of
  `RuntimeTestEnvironment.accept`), and `phoneAnswers` and `FakePhoneLink.commands` in
  `TransportTestHarness.kt`;
- eligibility: completion needs `Available`, no open command, no refresh required, an `Open`
  selected set and valid draft values; skip and return need `Available`, no open command, no
  refresh required and `skippable` (§7.1);
- Tile: the lines for an `Open` set, a `Blocked` set, no position and no selection;
- ongoing indicator: posted for a fresh granted lease on an all-finished outline;
- MVI: a registry entry with a value-free `toString()` for every new Action; every row of §8.1's
  table; the entry rule: a store created with a selection, a store created during `Loading` whose
  first outline completes the pending entry, an outline without a selection, a new intent, and a
  configuration change that keeps the stack; every reconciliation rule of §8.1; Back on a
  session-level surface closes the activity;
- UI gates: first view at scroll 0 for L1, L2, C1, S1, S2, K1, U1 and E1 at 192 and 240 dp, EN and
  RU, font scale 1.0 and the largest, typical and stress fixtures; the C2 row fully visible after
  scrolling to the end; the `Blocked` and `NO_SETS` controllers; the time text's lead; the string
  gate with the new fixtures, its exact matcher and its three-name allowlist.

### 10.2 Named mutations

Each is applied, run RED with the command and exit code recorded, restored, and run GREEN, with
`--rerun-tasks --no-build-cache --no-daemon`; detekt and tests run as separate invocations; every
gate reports its input count.

| Id | Mutation | Must fail |
|---|---|---|
| M-P1 | the builder drops skipped exercises | outline order test |
| M-P2 | a `Done` set carries plan values | done-values test |
| M-P3 | an unsupported plan value becomes `Open` | blocked test |
| M-P4 | lease admission compares a target again | any-open-set admission test |
| M-P5 | a lease is offered only when an `Open` set exists | all-finished grant test |
| M-P6 | `complete_set` accepts a `Done` position | done-set test |
| M-P7 | `complete_set` accepts a skipped exercise | skipped-exercise test |
| M-P8 | a skip of a skipped exercise is applied | skip-of-skipped test |
| M-P9 | `SkipChanged` is published inside the transaction | transition-wrapper test |
| M-P10 | `SkipChanged` is published for `AlreadyApplied` | replay test |
| M-P11 | the stored fingerprint is parsed before the command ids are compared | V1-receipt test |
| M-P12 | the version answer is replaced by empty bytes | v1-request test |
| M-P13 | the fingerprint header omits the kind byte | known-answer vectors and the preimage-header test |
| M-P14 | the validator pairs `TargetChanged` with `NoSession` | pairing matrix |
| M-P15 | the watch authority keeps a target | any-set command test |
| M-P16 | the selection stays on a set the phone completed | phone-completed-set test |
| M-P17 | rule R passes over a `NO_SETS` exercise | rule R vector |
| M-P18 | the selection is not written to the cache | process-death test |
| M-P19 | a version-1 cache frame is accepted | cache-version test, whose fixture wraps a v2 payload in a version-1 header |
| M-P20 | the Tile shows the first open set instead of the selection | Tile test |
| M-P21 | ConfirmSkip leaves the Controller on the stack | navigation test |
| M-P22 | Back at `[List]` is ignored | navigation test |
| M-P23 | the live-workout store ignores `SkipChanged` | apply test A |
| M-P24 | `SkipChanged` is applied as a toggle | idempotence test |
| M-P25 | an oversized outline is sent without the fallback | size test |
| M-P26 | skip is allowed without `Available` authority | eligibility test |
| M-P27 | the ongoing indicator is posted only when the outline has an `Open` set | all-finished ongoing test |
| M-P28 | the draft survives a selection change | draft test |
| M-P29 | the stack is not reconciled after an outline | reconciliation tests |
| M-P30 | the entry rule runs once at creation with no pending entry | `Loading`-at-creation entry test |
| M-P31 | the phone's skip menu toggles instead of sending its intent | P14 race test |
| M-P32 | the budget check uses `MAX_ENVELOPE_BYTES` | near-budget command-response test |
| M-P33 | the string gate's matcher stays literal-segment | gate self-test of §8.8 |
| M-P34 | the store applies a `SkipChanged` through `updateStateImmediate` | atomic-writes test (live sync D14) |

Every earlier named mutation whose test or production file these PRs change (Phase 1, transport,
and live sync M-S1 to M-S45, M-S32 as its amendment of 2026-10-03 redefines it, plus the
supplementary M-SX1 and M-SX2 of its ledger) is listed in the PR and run again.

### 10.3 Gates

Unchanged and green at every commit: root `testDebugUnitTest`, `lintDebug`, `detekt` (zero new
suppressions; the string gate's three exemptions of §8.8 are test configuration, listed in the PR),
`assembleDebug`, `assembleDebugAndroidTest`, both release bundles with every applicable check of
G1–G12, the mockup gate, the transport gate script and its self-test, `ReleaseRuntimeBoundaryTest`,
every Wear emulator suite CI runs, and the PR's `KMP iOS kit smoke` check (`ExternalSessionWrites`
stays in `core/data/exercise`'s `commonMain`). The acceptance harness gains the new fixtures, and
its mutation manifests' anchors follow the changed source (F22); each changed anchor is listed in
the PR. The fingerprint device tests (phone and watch) run with the V2 vectors where an emulator is
available, else they are recorded UNVERIFIED.

Test names added or renamed in `feature/live-workout` contain none of the characters Kotlin/Native
2.4.10 rejects in a declaration name (`. ; , ( ) [ ] { } / < > : \ $ & ~ * ? # | § % @`): #321
removed every such name from that feature's tests so that its KMP migration needs no
`INVALID_CHARACTERS_NATIVE_ERROR` suppression (KMP Phase 7.9 D3). The topology gate checks test
names only for migrated features, so the PR lists the new names and the check that found none.

### 10.4 Device evidence (implementer)

On a Wear emulator: the acceptance harness's new cells. On the paired emulators of live sync §15
(2026-10-06: phone `Pixel_9_Pro_XL`, watch `Wear_OS_Large_Round`, both API 36), if they are still
paired: the baseline handshake; list → controller → complete; skip and return with the phone's
live-workout screen open; the selection after `am kill` of the watch process; a 1.53.x watch build
against the new phone shows "Update Workeeper on phone and watch". Optional, recorded; it never
replaces §12.3.

## 11. Failure modes

| Situation | Watch | Phone | Data |
|---|---|---|---|
| Phone v1, watch v2: the usual window after a release (ASM-P1) | "Couldn't reach your phone" with Retry until the phone updates (residual) | drops v2 requests | none |
| Phone v2, watch v1: the Wear upload failed or has not arrived | "Update Workeeper on phone and watch", read-only; recovery: re-run the Wear deploy | answers every request with the version answer | none |
| The session grows past the budget during a command | "Open this workout on your phone"; the command ends as `TargetChanged` or `StaleRevision` | `PayloadTooLarge` replacement | none |
| Outline over the cap | "Open this workout on your phone" | `PayloadTooLarge` | none |
| The phone completes the selected set | selection moves to the first incomplete set of that exercise | — | none |
| The phone skips, removes or finishes the selected exercise | selection falls back to rule R; a Controller on top stays and shows it | — | none |
| The phone edits a plan and positions change | the draft is dropped (new revision); a vanished position moves the selection to the first incomplete set | — | none |
| The first unfinished exercise has no sets | the app opens on "Add a set on your phone" with a labelled Skip (P11); the list allows any other exercise | — | none |
| A planned value outside the watch domain | that set is `Blocked`: shown, selectable, "Edit the current set on your phone" | — | none |
| Skip or return while the phone screen is open | — | the card's status changes live; its sets and drafts stay | the flag only |
| Skip times out or the phone is unreachable | Retry, as for a set | — | none until applied |
| A second Workeeper watch (V7) | every `mutate` commit, handshakes included, retires every lease, so each watch's activity retires the other's; the other's next command fails once and the tap after succeeds | — | none |
| A watch skip arrives while the phone's exercise menu is open | — | the menu shows the new label; a tap on the old one does nothing (P14) | the flag only |
| Watch process killed with a selection | restored from the cache and reconciled | — | none |
| Cache from 1.53.x after the update | deleted as corrupt; `Loading`, then a handshake | — | none |
| A V1 receipt left on the phone | ignored for every new command (P4) | — | none |
| Empty session (no exercises) | "Workout complete", as Phase 1 showed 0 of 0 | — | none |

## 12. Delivery, release and acceptance

### 12.1 Order

1. The owner approves this file. It lands on `dev` (a direct push; specifications take no PR)
   together with one status line in Phase 1, transport and live sync: "Amended on `<date>` by [Wear
   Phase 2](wear-phase-2-exercise-list.md) (protocol v2)."
2. PR-P2A into `dev`: §5, §6, §7, the Phase 1 controller kept working on the selection (no list
   yet; Skip not offered), Appendices A and B. Suggested commits: the protocol (models, codec,
   FingerprintV2, `OutlineRules`); the phone (builder, lease, commands, RPC handler); the
   live-workout skip; the watch state (reducer, owner, selection, cache, Tile, ongoing); the
   documents. The owner merges.
3. PR-P2B into `dev`, stacked on PR-P2A until it merges: §8, the copy, the gates, the acceptance
   harness, and the regenerated store screenshots (P10). The owner merges.
4. `android_build_unified.yml` on `dev` with `execute_unit_tests=true`, green; then release 1.54.0
   through `cut_release.yml` and `android_deploy_prod.yml` as release-flow §6.1 describes: phone to
   production, Wear to `wear:internal`. The `production` environment has no protection rule (live
   sync §15, 2026-10-03), so the deploy does not stop for the approval release-flow §6.1
   describes: dispatching it releases.

### 12.2 Implementer evidence after release

The versions on both tracks from the run logs; both deploy lanes `RESULT PASS`; the new policy text
served by Pages; ledger rows in §15.

### 12.3 Owner physical checklist

Setup: Play installs only. Update the phone first. Before P2-1 the owner writes the installed
version of both devices into the report, as the device shows it (watch: Settings → Apps →
Workeeper; phone: the app's Settings → About), 1.54.0 on both. "No pending update" does not
establish a version: a device that was never offered the update shows none either (live sync
§12.3). A watch on 1.54.0 with the phone still on 1.53.x shows "Couldn't reach your phone" (§5.1).
Then a workout started on the phone with at least four exercises, one with a warm-up set.

| Id | Step | Expected |
|---|---|---|
| P2-1 | Open the watch app from the Tile | the controller of the first incomplete set; "N/M · time" in the time arc |
| P2-2 | Swipe back | the exercise list, centered on the selected exercise; swipe back again closes the app |
| P2-3 | Tap another unfinished exercise | its controller, on its first incomplete set |
| P2-4 | Tap the exercise name, pick the third set, complete it | the controller returns to the first incomplete set of that exercise |
| P2-5 | Complete the last set of an exercise on the watch | the next unfinished exercise in phone order is selected |
| P2-6 | Phone live-workout screen open; skip an exercise on the watch (Skip, then the edge button) | the watch shows the list with that exercise skipped; the phone card shows skipped within a few seconds, without re-entry |
| P2-7 | Tap the skipped exercise, return it | the list shows it again; the phone card too |
| P2-8 | Tap a finished exercise | its sets, read-only, "Edit on your phone" |
| P2-9 | On the phone, complete the set the watch has selected | the watch's selection moves to the next incomplete set of that exercise |
| P2-10 | On the phone, skip the exercise the watch has selected | the watch selects the next unfinished exercise |
| P2-11 | Swipe the app away, wait a minute, tap the Tile | the same selection as before |
| P2-12 | The Tile | shows the selected exercise and set |
| P2-13 | Finish every exercise | "Workout complete", "Finish on your phone" |
| P2-14 | Live sync S1–S12 | still pass, or are reported as 1.53.1 left them |

The owner reports pass or fail per row with a note, and a screenshot for a failure.

## 13. Out of scope

The display-only snapshot pushed with the change signal ("B", own specification after O6 works on
device); the start-of-workout watch notification (own mini-specification); adding or removing sets,
editing a done set, changing a set's type, undoing a completed set, reordering, starting or
finishing a session from the watch; per-node lease retirement (V7); a v1 fallback in the v2 watch
for an old phone; `TransformingLazyColumn` morphing; a consumer for the reducer's haptic events; the
phone screen's CURRENT marker following the watch.

## 14. Discovery (before editing) and STOP conditions

Discovery rows, each with command, evidence and verdict:

- **Q1** F1–F29 at the current `dev` head. Pure line drift is recorded.
- **Q2** Wear Compose 1.6.2 (F25), from the resolved sources or `javap`: the `TimeText` content
  API for a leading text and separator; a swipe-to-dismiss box with background content; an edge
  button in a confirmation screen.
- **Q3** The string gate's exemption mechanism, the action registry, the acceptance harness's
  fixture mechanism, and every mutation-manifest anchor the two PRs will change (listed).
- **Q4** Where Pages publishes from: transport ASM-4 is recorded as an assumption, consistent with
  its ledger (`wear-paired-transport.md`, ledger row "Policy live (§9.1)"); confirm it from the
  repository's Pages settings or a `gh api repos/stslex/Workeeper/pages` read.
- **Q5** Where the shared done-rule vectors of §5.3 live, and the exact `ExerciseDoneRule` entry
  point.
- **Q6** The real codec's size for the §5.7 fixtures, both response types. The 20 × 5 stress
  outline not fitting a `WatchCommandResponse` is a STOP.
- **Q7** The live-workout store still writes its state only through `updateState` (live sync D14;
  no `updateStateImmediate` in the store), and `applyExternalWrite` is still the one entry for watch
  writes (§6.5, F11).

STOP and report when:

- a fact in §3 is contradicted in a way that changes the design (not line drift);
- the work would change something this file does not name: another wire model, the change signal,
  the database schema, a permission, the allowlist, the capabilities or paths, or the cache TTL;
- the 20 × 5 stress outline does not fit (Q6);
- the phone's done rule and the outline's finished rule disagree on a shared vector;
- a gate can only be opened by a suppression or a wildcard; a named mutation cannot be made RED;
- a bundle identity gate fails;
- an Appendix OLD block does not occur exactly once, or `product.md`'s result hash differs while its
  base hash matched;
- the mockup and this file disagree on something the implementation must decide;
- anything would claim physical acceptance or a measured constant.

## 15. Implementation ledger

Append-only. One row per PR, release step and acceptance item.

| Date | Item | Commit or run | Evidence |
|---|---|---|---|
| 2026-10-13 | PR-P2A (§12.1 step 2): protocol v2, the phone, the watch state | PR #331; head before this row `a3997543` | Per-commit gate green on C1 `3761c484`, C3 `176c0121`, C4 `35aadf80`, C5 `af678aa4`, C6 `1da618e5`, C7 `6ecc15f1`, C8 `80ef1cac` and C9 `a3997543` (C2 folded into C3, DEV-1): 2598 build tasks executed each, up to 2190 + 1028 + 51 unit tests, detekt 0 findings, 456 Paparazzi goldens with 0 changes, both bundles `RESULT PASS (12/12)`, the swap control `G5 FAIL`; C5's one red was a memory-pressure timeout of `WearOverflowGateTest` (store variant), its task rerun alone 359/359. Mutations: named 29 of 29 RED then GREEN (M-P25 after C9's stronger size test); earlier and manifest cases 193 rerun RED then GREEN, 277 untouched, 0 retired, 1 not rebuildable (`ambient-editor-retains-selection-after-ineligible-wake`); manifest anchors 160 of 160 once. FingerprintV2 device tests 1/1 phone and 1/1 watch on API 36 AVDs. Pages: `legacy`, from `master:/docs`. Data safety (DEV-2): no new declaration expected (transport §9.3, P4); the form comparison is UNVERIFIED, the owner's. The two nits of C5: §12.1 step 1's `<date>` in backticks; both `wear-paired-transport.md:700` citations now cite its ledger row "Policy live (§9.1)". |

## 16. Sources

- [The approved Phase 2 mockup](../mockups/wear-phase-2.html) and its project note of 2026-10-04.
- [Phase 1](wear-phase-1-active-workout-tile.md) §2.2, §3, §4, §5.2, §9; [paired
  transport](wear-paired-transport.md) §7.9, §9.3, Appendix A; [live sync](wear-live-sync.md) §5,
  §6.4, §12.3, §15, D7, D10, D12, D14.
- [release-flow.md](../release-flow.md), [ci-cd.md](../ci-cd.md).
- [KMP Phase 7.9](kmp-phase-7-9-all-trainings-feature.md) D3 (Native-legal test names).

## Appendix A. `docs/index.md` (V6)

Base `sha256` at `99f10601`, unchanged since `89af9bf8`:
`43633f03d808cf7228952b791c08a0d223eb5b2942aff446820d133124e293ae`.
Four replacements, each OLD block occurring exactly once. Each block is the lines strictly between
its fence lines, joined with "\n". The two "Last updated" lines take the date of the commit that
lands this change, in their existing formats (`4 October 2026`, `4 октября 2026 г.`). With both
"Last updated" lines deleted (each line and its newline), the base hashes to
`99138834b1f05b7bf4c4f0c0c70e61abcfa9e627c26d3edf12765ea76745021a` and the result to
`cc7fb2e665f5308b7e2f7eb54718024879855739a5b447b6f5b44e617c713dce`.

**A1 OLD (English, lines 20 to 26)**

```text
If you install Workeeper on a Wear OS watch paired with your phone, the phone app sends the watch
only what the watch needs to show the current set of a workout that is active on the phone: the
workout and exercise names, the set number and type, the weight and reps, the workout progress, and
technical identifiers. When you complete a set on the watch, the watch sends that set's weight,
reps and type, with technical identifiers, back to the phone, which records the set. Your workout
history stays on the phone. The watch keeps only a temporary copy of the current state: the app
stops using it after 24 hours or after the watch restarts, and deletes it the next time it runs.
```

**A1 NEW**

```text
If you install Workeeper on a Wear OS watch paired with your phone, the phone app sends the watch
only what the watch needs to show a workout that is active on the phone: the workout name; the names
of its exercises, including skipped ones, and whether each is skipped and uses weight; for each
exercise, its sets with their number, type, weight and reps and whether they are done; and technical
identifiers. When you complete a set on the watch, the watch sends that set's weight, reps and type
back to the phone, which records the set. When you skip an exercise on the watch or return it to the
workout, the watch tells the phone which exercise. Both go with technical identifiers. Your past
workouts stay on the phone. The watch keeps only a temporary copy of the current state, including
the set you chose on the watch: the app stops using it after 24 hours or after the watch restarts,
and deletes it the next time it runs.
```

**A2 OLD (Russian, lines 144 to 151)**

```text
Если вы установите Workeeper на часы с Wear OS, сопряжённые с вашим телефоном, приложение на
телефоне будет передавать часам только то, что нужно для показа текущего подхода тренировки, начатой
на телефоне: названия тренировки и упражнения, номер и тип подхода, вес и количество повторений,
прогресс тренировки и технические идентификаторы. Когда вы завершаете подход на часах, часы
отправляют на телефон вес, количество повторений и тип этого подхода вместе с техническими
идентификаторами, и телефон записывает подход. История тренировок остаётся на телефоне. Часы хранят
только временную копию текущего состояния: приложение перестаёт её использовать через 24 часа или
после перезагрузки часов и удаляет её при следующем запуске.
```

**A2 NEW**

```text
Если вы установите Workeeper на часы с Wear OS, сопряжённые с вашим телефоном, приложение на
телефоне будет передавать часам только то, что нужно для показа тренировки, начатой на телефоне:
название тренировки; названия её упражнений, включая пропущенные, с отметками о пропуске и об
использовании веса; для каждого упражнения его подходы с номером, типом, весом, количеством
повторений и отметкой о выполнении; технические идентификаторы. Когда вы завершаете подход на часах,
часы отправляют на телефон вес, количество повторений и тип этого подхода, и телефон записывает
подход. Когда вы пропускаете упражнение на часах или возвращаете его в тренировку, часы сообщают
телефону, какое это упражнение. И то и другое передаётся вместе с техническими идентификаторами.
Прошедшие тренировки остаются на телефоне. Часы хранят только временную копию текущего состояния, в
том числе выбранный на часах подход: приложение перестаёт её использовать через 24 часа или после
перезагрузки часов и удаляет её при следующем запуске.
```

**A3** `Last updated: **29 September 2026**` (line 115) and **A4**
`Последнее обновление: **29 сентября 2026 г.**` (line 246): the date only.

## Appendix B. `documentation/product.md` (V6)

Base `sha256` at `99f10601`, unchanged since `89af9bf8`:
`e1fca7daa689982e16fd2b97e88d7163759dd3dc537b9ee4fbc58766ad6aa33a`.
Two replacements, each OLD block occurring exactly once; the result's `sha256` is
`c5736555f486d6d315ad844d4a4a78a1ac2aeede62733a2f6ae477201e8d4d10`.

**B1 OLD (lines 250 to 255)**

```text
An Android-only Wear OS companion is approved as a separate product and
delivery workstream. Phase 1 is deliberately narrow: a glanceable Tile shows
the state of an already-active phone workout and opens one minimal watch screen
for editing and completing the current set. The phone remains the only workout
database and durable source of truth; the watch may hold only the bounded,
app-private display cache defined by the Phase 1 specification.
```

**B1 NEW**

```text
An Android-only Wear OS companion is approved as a separate product and
delivery workstream. Phase 1 is deliberately narrow: a glanceable Tile shows
the state of an already-active phone workout and opens one minimal watch screen
for editing and completing the current set. Phase 2 adds the active workout's
exercise list on the watch: choosing an exercise and any incomplete set of it,
and skipping an exercise or returning it to the workout
([Wear Phase 2](feature-specs/wear-phase-2-exercise-list.md)). The phone
remains the only workout database and durable source of truth; the watch may
hold only the bounded, app-private display cache defined by the Phase 1 and
Phase 2 specifications.
```

**B2 OLD (lines 277 to 289)**

```text
- **No account-backed or Workeeper-operated cloud sync.** Workout data stays on
  the phone by default. The sole approved cross-device exception is Phase 1 of
  the personally paired Wear OS companion: after the required product and
  public privacy disclosures are live, it may transfer the minimum active
  workout snapshot and completion commands between the user's phone and watch,
  let the phone send the watch a content-free change signal so that the watch
  asks again ([Wear live sync](feature-specs/wear-live-sync.md)), and retain
  the bounded watch cache defined by its specification. The owner authorized
  Google-owned end-to-end encrypted Data Layer relay transit for it on
  2026-09-29, together with its public disclosure
  ([Wear paired transport](feature-specs/wear-paired-transport.md)); no other
  phone/watch route is authorized. Manual export / import may be added later,
  but is not a v1 commitment.
```

**B2 NEW**

```text
- **No account-backed or Workeeper-operated cloud sync.** Workout data stays on
  the phone by default. The sole approved cross-device exception is the
  personally paired Wear OS companion: after the required product and public
  privacy disclosures are live, it may transfer the active workout's outline
  and the watch's commands (completing a set, skipping an exercise or returning
  it) between the user's phone and watch
  ([Wear Phase 2](feature-specs/wear-phase-2-exercise-list.md)), let the phone
  send the watch a content-free change signal so that the watch asks again
  ([Wear live sync](feature-specs/wear-live-sync.md)), and retain the bounded
  watch cache defined by its specifications. The owner authorized Google-owned
  end-to-end encrypted Data Layer relay transit for it on 2026-09-29, together
  with its public disclosure
  ([Wear paired transport](feature-specs/wear-paired-transport.md)); no other
  phone/watch route is authorized. Manual export / import may be added later,
  but is not a v1 commitment.
```
