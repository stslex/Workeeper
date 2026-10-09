# Wear OS live sync — Phase 1 increment 5b (releases 1.53.0 and 1.53.1)

**Status:** approved by the owner on 2026-10-01, ready for implementation.

**Amended on 2026-10-02** by owner decision after the PR-S discovery: F1, F2, F5, F6, F12, F19, F21
and F25 are corrected and F28 is added; D10 to D12 record the decisions; the problem table, §4,
§5.2, §6.2 to §6.4, §7.5, §8, §10, §11, §13 and §14 follow from them.

**Amended on 2026-10-03** by owner decision after the PR #313 review: F29 to F31 are added; D13 and
D14 record the decisions; §1, §5.3, §6.2, §6.4, §7.3, §10, §11, §12.4, §13 and §14 follow from
them. PR-S2 delivers them (§12.4).

**Amended on 2026-10-06** by owner decision after the owner's field test of 1.53.0 and a paired
emulator run of its code: F32 is added; D15 records the decision; the title, §1, §2, §5.3, §5.4,
§10, §11, §12.3, §12.4, §14 and §15 follow from it. PR-S2 delivers it, and hotfix 1.53.1 ships
PR-S2 (§12.4).

- **Specification base:** `dev` at `b1945ee1` (hotfix 1.52.2 merged, `master` synced). Evidence
  is `path:line` there. The facts the 2026-10-02 amendment corrects or adds were verified at
  `481ee294`, which moves none of the lines this document cites. The facts the 2026-10-03
  amendment adds, and every line it cites, were verified at `4c1d5217` (`dev` after release 1.53.0,
  PR-S merged). F32, and every line the 2026-10-06 amendment cites, were verified at `f64e13f3`
  (`dev` on 2026-10-06).
- **Amends:** [Wear OS paired transport](wear-paired-transport.md) (increment 5a). Superseded
  there, as §2 states: D4 ("pull only"); §8 and the clauses that call its two transport files the
  only Data Layer files (§4, §5.5, §6.1, §7.1); the phone-UI and bridge clauses of §6.4 ("no
  change to `PhoneWorkoutBridgeImpl` beyond `transportStatus`", "no phone UI change"); the "no
  polling" invariant of §7.2; the push item of §13. Everything else there and in
  [Phase 1](wear-phase-1-active-workout-tile.md) stays in force: wire models, codec,
  fingerprints, reducer admission, phone validation, lease and receipt rules do not change. Where
  this document and either of those disagree about protocol semantics, they win and the
  implementation stops.
- **Delivery:** one Android-only PR into `dev` (PR-S), then release 1.53.0 through the existing
  pipeline: phone to production, watch to `wear:internal`. The 2026-10-03 amendment adds PR-S2
  into `dev`; the 2026-10-06 amendment ships it as hotfix 1.53.1, cut from `master` (§12.4).

The owner's field test of 1.52.2 on 2026-10-01 found four problems. This increment fixes three:

| # | Observed | Cause | Here |
|---|---|---|---|
| 1 | A set completed on the watch shows on the open phone screen only after re-entering it | The live-workout screen reads the session only when it is entered (F12) | §6.4 |
| 2 | A set completed, or a workout started, on the phone shows on the watch only after re-entering or tapping it | Pull only: the watch asks only on its own origins O1–O5; a tap in ambient is O1 (F16) | §5, §6.1–§6.3, §7.1–§7.4 |
| 3 | Back from the controller shows the same screen; a second back leaves | A Tile tap while the app runs stacks a second `MainActivity` (F19) | §7.5 |
| 4 | No way to pick the exercise or skip a set or an exercise on the watch | Phase 1 §4 by design | §13 (Phase 2) |

## 1. Goal and success criteria

With phone 1.53.0 or later and watch 1.53.0-wear or later, during an active workout:

- a set completed on the phone shows on the watch within a few seconds, whether the watch app is
  open, in ambient, or closed (Tile and ongoing indicator);
- starting and finishing a workout on the phone shows on the watch the same way;
- a set completed on the watch shows on the open phone live-workout screen within a few seconds,
  without disturbing anything the user has open or typed on either device;
- one swipe back from the watch controller leaves the app.

Success means all of:

1. every host gate of §10 is green at each commit of PR-S, of PR-S2 and of the hotfix branch
   (§12.4), and each named mutation went RED, then GREEN;
2. release 1.53.0, and hotfix 1.53.1 after it, are live on both tracks through the existing
   pipeline, and their deploy lanes end in `RESULT PASS` with every applicable check of G1–G12
   passing;
3. the owner's physical checklist (§12.3) passes, or its failures are reported with evidence.

Items 1 and 2 are the implementer's. Item 3 is the owner's and is the only evidence that closes this
increment.

## 2. Owner decisions

L: locked by the owner's GO of 2026-10-01 on the field-test diagnosis, of 2026-10-02 on the PR-S
discovery (D10 to D12), of 2026-10-03 on the PR #313 review (D13, D14), or of 2026-10-04 on the
1.53.0 field test (D15, whose value the emulator run of 2026-10-06 backs). P: proposed by this
specification and approved with it on 2026-10-01, or with its 2026-10-03 amendment (the extension
of D14).

| Id | | Decision |
|---|---|---|
| D1 | L | **Phone → watch change signal; supersedes transport D4.** When the active session's `(uuid, wear_revision)` changes, the phone sends a content-free message on `CHANGED_PATH` to each reachable node that advertises `WATCH_CAPABILITY` and does not already hold that state (D7). The signal carries no workout data. The watch answers it with an ordinary correlated handshake, so workout data still moves only when the watch asks. A pushed snapshot would not save that round trip: Phase 1 §3 already forbids an unsolicited snapshot to start a session, apply `NoSession` or install a lease, and makes it trigger one correlated refresh (F24). |
| D2 | L | **The watch receives the signal with its app closed.** A manifest `WearableListenerService` hands it to the process runtime, so the Tile and the ongoing indicator update without the app. While the app runs, ambient included, the same path refreshes the controller. The signal never opens the watch app. |
| D3 | L | **The Data Layer allowlist grows from two files to four** (§8), superseding transport §8: one phone sender, one watch listener. Neither new file carries a workout payload; workout data still crosses only through the two RPC files. This is the privacy decision. The public policy text stays unchanged (§9). |
| D4 | L, mechanism P | **The phone live-workout screen shows a set the watch completed without re-entry.** Expanded cards and the drafts of other sets stay; a draft of the same set yields to the watch's values. Mechanism (P): the screen applies the set the bridge wrote, through the mutator the phone's own completion uses (F14), and does not re-read the session. A re-read replaces the whole state (every reload keeps only expansions, F12) and would overwrite an optimistic phone mark whose write is still in flight (F13). |
| D5 | L | **The watch `MainActivity` gets `android:launchMode="singleTop"`** (§7.5). |
| D6 | P | **The new origin O6 has its own limiter instead of `AUTO_REFRESH_BUDGET`, and an O6 that cannot start yet waits instead of being dropped** (§7.3). The budget drops what it refuses (F16): under rapid phone edits a dropped last change leaves the watch stale until some other origin, which is field-test problem 2 again. O6 cannot loop, because a handshake never changes `wear_revision` (F2); M-S1 proves it. An O6 handshake that fails (phone unreachable or silent) is not retried, as for every other origin; the next signal or O1–O5 recovers. |
| D7 | P | **The phone does not signal a watch that already holds the current state.** The bridge remembers, per watch node, the session and revision of the last snapshot it answered that node with (F26), and the notifier skips such nodes. Without this, every set completed on the watch would be signalled back to the same watch, and the handshake that answers a signal retires the watch's authority for one round trip (F25): a moment after Complete set, the controls would go disabled and an open editor of the next set would close. A per-node entry in phone memory is cheaper than a revision inside the signal, which would need a new wire model. |
| D8 | P | **`documentation/product.md` gains one clause**, so the non-goal's Wear exception names the signal (Appendix A). |
| D9 | P | **The new constants are provisional** (§5.3, §7.3) and follow the labelling rule of transport §7.8. |
| D10 | L | **Every load of the live-workout screen keeps the watch's sets** (§6.4). The screen reloads the session each time it returns to composition, and after a plan-editor save `processReload` runs alongside that reload (F12). Each load applies again the watch writes received since it started, and every `Init` restarts the subscription. `processReload` stays: removing it would make correctness depend on `Init` re-running, which `tech-debt.md` proposes to stop. |
| D11 | L | **The notifier survives its database closing** (§6.2, §6.3). The Android restore path closes the database while the generation, and so the notifier, is still alive (F6); the notifier catches the failure of its query, logs it by class and ends. The restore path does not change. |
| D12 | L | **Two Workeeper watches in one workout remain a known residual** (§11). A handshake from either watch retires the other's lease (F5), so its next Complete set fails once (F28); D7 makes this follow every set. Lease rules stay as Phase 1 defines them; Phase 2's lease rework is where to revisit it. |
| D13 | L | **The notifier's failure handling is one guard around its whole collection, not a `catch` operator in the chain** (§6.2). A `catch` operator handles an upstream failure only while the downstream has not failed (F30): after `conflate` it rethrew a query failure that arrived while the collector was busy (a lookup, a send, the minimum interval), and before `conflate`, where PR-S put it as an accepted deviation, it is correct only through a library internal: the conflated channel's send never fails while its producer runs. The guard lets only the notifier's own cancellation propagate and logs any other failure by class, so D11 holds by construction. |
| D14 | L, extension P | **No state write of the live-workout screen erases another** (§6.4). The timer ticks on the work dispatcher with a read of the state followed by a write, so a tick that read the state before the main thread applied a watch set writes that set away, and nothing shows it again until the screen is re-entered (F29). The owner's GO makes the tick atomic (`updateState`, a compare-and-set that retries on the newer state). Proposed with this amendment: the store's other three read-then-write updates (a watch write, a load's result, the load-failure flags) become atomic too, because on the main thread they can erase an update the exercise picker makes on the work dispatcher (F29). No non-atomic write then remains in the store. |
| D15 | L | **The phone's settle time drops from 500 ms to 150 ms (§5.3), and PR-S2 ships as hotfix 1.53.1 (§12.4).** In the owner's field test of 1.53.0 the watch followed the phone only after the owner's own action; a paired-emulator run of the same code then passed S1, S2, S3, S7 and S8 (§15, 2026-10-06), so under emulator conditions the code does not explain the field result, and the watch's installed version was not recorded (§12.3 now records it). In that run each phone action measured (start, complete a set, skip, return, add an exercise, finish) produced exactly one distinct key in each of three runs, and editing a completed set one key per step: no measured action needs the settle to merge keys. The settle was 500 of the 894 ms from a phone tap to the watch's accepted snapshot. 150 ms keeps a margin for an action that commits in several transactions. The constant stays provisional (D9): an emulator run is not a physical measurement. |

## 3. Verified facts at the specification base

The implementer re-verifies each row before editing (§14). Pure line drift is recorded; a
contradiction that changes the design is a STOP.

| Id | Fact | Evidence |
|---|---|---|
| F1 | Triggers raise `session_table.wear_revision`, and clear the receipt, of an in-progress session when one of its sets is inserted, deleted, or updated in its exercise, position, reps, weight or type; when one of its performed exercises is inserted, deleted, or updated in its session, exercise, position or `skipped`; and on training, plan and exercise edits that touch it. A separate trigger raises it on any update of a session's training, state, start or finish, whatever its state. No trigger watches `wear_lease_generation` or a receipt column. | `core/data/database/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/database/wear/WearSyncStorage.kt:116-315` (`wear_session_update_revision` at 208) |
| F2 | `getActiveWorkout` runs inside `transition.mutate`. A handshake writes only when it grants authority (an active session with a target, in a response that fits): it then increments `wear_lease_generation` and leaves `wear_revision` alone. Any other handshake writes nothing. | `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/PhoneWorkoutBridgeImpl.kt:59-77,443-463,518-530`; `core/data/database/.../wear/WearSyncDao.kt:53-61` |
| F3 | `SessionDao.observeActive()` emits the whole row. Room invalidates per table, so a `session_table` query re-emits on every write to that table, a handshake's lease write included. | `core/data/database/.../session/SessionDao.kt:79-80`; `.../session/SessionEntity.kt:39-50` |
| F4 | An applied watch command writes one set inside `transition.mutate`, checks that the revision advanced and stores the receipt at the new revision. `completeCurrentSet` holds `coordinatorMutex` throughout. | `PhoneWorkoutBridgeImpl.kt:79-128,326-357` |
| F5 | Every commit through `DbTransitionRunner.mutate` runs the after-commit listeners. `WearMutationLeaseStore` registers `retireAll` there, so every `mutate` commit, a watch handshake included, retires the leases of every watch node. A write through the runner's plain `invoke`, such as `TagRepositoryImpl.add`, runs no listener. | `core/data/database/.../di/DbCascadeBindingContainer.kt:44-64`; `feature/wear-bridge/.../WearMutationLeaseStore.kt:62-64,212-216`; `core/data/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/exercise/tags/TagRepositoryImpl.kt:41` |
| F6 | Generation-scoped background work starts in `armPostPreflight` on `lifetime.childScope(ioDispatcher)`: a supervisor scope with no exception handler, so an exception that escapes a coroutine there reaches the thread's default handler. `warmQueryPlanner` skips a generation that routes to recovery and runs its work inside `runCatching`; a live-database open failure records the recovery decision before `armPostPreflight` runs. An in-process rebuild tears the outgoing generation down (store clear, then the lifetime cancelled and joined) before its database closes. The Android restore path (`RestartProcess`) does not: after quiescence it closes the database with the generation lifetime still active, replaces the file and restarts the process. | `app/app/src/main/java/io/github/stslex/workeeper/runtime/StartupProcessor.kt:190-202,237-265`; `core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/coroutine/scope/AppScopeLifetime.kt:15-27`; `feature/recovery/src/main/kotlin/io/github/stslex/workeeper/feature/recovery/domain/StartupMigrationCoordinator.kt:83-92`; `app/app/.../runtime/GenerationQuiescer.kt:23,55-67`; `app/app/.../runtime/AppRuntime.kt:311,531-546,563`; `app/app/.../runtime/ReplacementMechanics.kt:320-345` |
| F7 | The phone advertises `workeeper_phone_active_workout_v1` and the watch `workeeper_watch_active_workout_v1`. `WearProtocol` names only the phone's, and no Kotlin code references the watch's. | `feature/wear-bridge/src/main/res/values/wear_capabilities.xml:12`; `app/wear/src/main/res/values/strings.xml:44`; `core/wear-protocol/src/main/kotlin/io/github/stslex/workeeper/core/wear/protocol/WearProtocol.kt:20,23` |
| F8 | The phone manifest declares one Data Layer service, `WearRpcListenerService`, for `REQUEST_RECEIVED` on the exact RPC path. | `feature/wear-bridge/src/main/AndroidManifest.xml:9-20` |
| F9 | The watch manifest declares no Data Layer service. `MainActivity` has no `launchMode` and has `taskAffinity=""`. | `app/wear/src/main/AndroidManifest.xml:44-52` |
| F10 | The Data Layer allowlist is enforced in three layers, each listing the same two exact paths, and is described in several documents (§8 lists them). | `lint-rules/detekt.yml:448-467,764-773`; `.github/scripts/assert_wear_transport_gate.py:84-88,593-602`; `documentation/lint-rules.md:694-705` |
| F11 | The bundle identity gate runs G1–G11. G10 checks the phone's RPC listener and is not applicable to Wear. | `.github/scripts/assert_play_bundle.py:12-40,329-367`; `documentation/ci-cd.md:163-183` |
| F12 | The live-workout screen loads the session in `Init`, creating it first when the route has none. Its store is retained across navigation, but `rememberStoreProcessor` disposes the store when the screen leaves composition, which cancels its scope, and initializes it again when the screen returns, which runs `Init` again; `tech-debt.md` records this for every store. So every return to the screen reloads the session, and a return from the plan editor after a save also runs `processReload` alongside that reload. Each reload replaces the screen state and keeps only `expandedExerciseUuids`. The timer survives a return only because `Init` restarts it, cancelling a running one first; `app-dialogs` starts its subscription in its initial action for the same reason. | `feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/CommonHandler.kt:33-35,39-104,106-107`; `.../mvi/mapper/LiveWorkoutMapper.kt:241-249`; `.../ui/LiveWorkoutGraph.kt:25-26`; `core/ui/mvi/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/mvi/BaseStore.kt:92-113`; `core/ui/mvi/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/mvi/processor/StoreProcessor.kt:73-80`; `feature/app-dialogs/impl/src/main/kotlin/io/github/stslex/workeeper/feature/app_dialogs/impl/mvi/store/AppDialogStoreImpl.kt:41`; `documentation/tech-debt.md:270-274` |
| F13 | The phone's own completion is optimistic: the state flips first and the write is launched after it. | `.../mvi/handler/ClickHandler.kt:178-198` |
| F14 | `applySetMarked` removes the draft of that set, upserts the performed set with its record flag, and recomputes statuses, visible rows, counts and progress. Visible rows never hide a performed position. | `.../mvi/mapper/LiveSetMutator.kt:54-97`; `.../mvi/mapper/StateStatusMapper.kt:20-27`; `LiveWorkoutMapper.kt:182-207`; `.../mvi/mapper/LiveSetRowsResolver.kt:35` |
| F15 | The screen state holds ephemeral fields: drafts, row overrides, an undo window, explicitly started exercises, expansions, dialogs and sheets. An undo window keeps a restore snapshot of exercises, drafts and overrides. Deleting an exercise is soft until its undo window closes. | `.../mvi/store/LiveWorkoutStore.kt:25-75`; `.../mvi/store/PendingUndo.kt:16-24`; `.../mvi/handler/DialogClickHandler.kt:55-57,95` |
| F16 | The watch origins are O1–O5. A refresh is dropped while another is queued or in flight; an automatic one is dropped at start under retry preservation or when `AUTO_REFRESH_BUDGET` (6 per 60 s) is spent. TILE and AUTHORITY_EXPIRED chains get no follow-up while the controller is not interactive. | `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/transport/WatchTransportCoordinator.kt:35-50,128-138,202-209,230-245,355-370`; `.../transport/TransportConstants.kt:18-26` |
| F17 | `WatchRuntimeFactory.get` builds the release runtime once per process and starts the Tile observer with it. The observer requests a Tile update whenever the rendered content changes. An accepted snapshot posts or cancels the ongoing indicator with no foreground requirement. | `app/wear/src/release/kotlin/io/github/stslex/workeeper/wear/runtime/WatchRuntimeFactory.kt:28-62`; `app/wear/.../tile/AndroidWorkoutTileUpdates.kt:12-24`; `.../tile/WatchTileCoordinator.kt:28-36`; `.../ongoing/WatchOngoingCoordinator.kt:57-91` |
| F18 | A watch draft survives a new snapshot only with the same source version (database epoch, identity, session revision) and the same target. | `app/wear/.../runtime/WatchRuntimeOwner.kt:199-210,364-374`; `.../state/ReducerModels.kt:17-26` |
| F19 | A Tile tap starts `MainActivity` through a protolayout `LaunchAction`, which carries package and class only and cannot set intent flags. The ongoing indicator uses `FLAG_ACTIVITY_SINGLE_TOP`. `MainActivity` answers `onNewIntent` with a local re-read (`onWake`, which is not an origin); a handshake comes from O1 when the activity resumes. | `app/wear/.../tile/WorkoutTileLayout.kt:24-31`; `.../ongoing/AndroidOngoingNotification.kt:99-105`; `app/wear/.../MainActivity.kt:58,83-88,95-99` |
| F20 | The watch numeric editor closes on swipe or back and applies every step to the current target at once. | `app/wear/.../ui/WearControllerScreen.kt:675-700` |
| F21 | The public policy names what the phone sends the watch and what the watch sends back, and says the data moves only between the user's phone and watch through the Data Layer, possibly relayed by Google. It makes no promise about which side starts a transfer. | `docs/index.md:18-31,142-157` |
| F22 | `product.md` allows the companion to transfer "the minimum active workout snapshot and completion commands", and no other phone/watch route. | `documentation/product.md:277-287` |
| F23 | The version is 1.52.2 (code 55). | `gradle/libs.versions.toml:16-17` |
| F24 | Phase 1 §3: an unsolicited snapshot may update read-only display state only; it cannot introduce a session, apply `NoSession`, install a lease or reset mutation freshness, and it triggers one correlated refresh. | `documentation/feature-specs/wear-phase-1-active-workout-tile.md:325-329` |
| F25 | Issuing a handshake token retires any watch authority until the answer is applied. Editing and completion need `Available` authority, and outside ambient the controller drops an open numeric editor when its controls become disabled. | `app/wear/.../state/WatchWorkoutReducer.kt:57-61,591-595`; `.../state/WatchInteractionEligibility.kt:27-31`; `app/wear/.../mvi/handler/CommonHandler.kt:40-44` |
| F26 | Every bridge call carries the authenticated source node id, and every bridge response carries a snapshot whose `sessionIdentityOrNull()` gives the session and revision it shows (null for no session). | `PhoneWorkoutBridgeImpl.kt:59-62,79-82`; `feature/wear-bridge/.../PhoneWorkoutSnapshotBuilder.kt:248` |
| F27 | The in-memory database of `RepositoryTestEnv` installs no Wear triggers; tests that need them call `prepareWearSyncStorage`. | `core/data/database-test/src/main/kotlin/io/github/stslex/workeeper/core/data/database/testfixtures/RepositoryTestEnv.kt:25-31`; `feature/wear-bridge/src/test/.../PhoneWorkoutBridgeImplTest.kt:81` |
| F28 | On `AuthorizationExpired` the phone answers with a replacement snapshot that grants a fresh lease when the target exists and the response fits. The watch closes the command with an error haptic and keeps the draft while the target is unchanged, so the next tap can succeed. | `PhoneWorkoutBridgeImpl.kt:269-272,466-516`; `app/wear/.../state/WatchWorkoutReducer.kt:464-465,488-490` |
| F29 | The live-workout timer runs on the store's work dispatcher, `Dispatchers.Default` in production, and each tick writes through `updateStateImmediate`: a read of the state, then an emit, with no compare-and-set. Watch writes, load results and the load-failure flags apply on the main thread through the same call. `updateState` is a compare-and-set (`MutableStateFlow.update`). The four `updateStateImmediate` calls are all in `CommonHandler`, and only the timer's runs off the main thread; everything else in `mvi/handler/` writes through `updateState` (57 calls in five files), some of it on the work dispatcher inside `launch` bodies, such as the exercise picker's updates. | `feature/live-workout/.../mvi/handler/CommonHandler.kt:85,133,167,173-189`; `.../mvi/handler/ExercisePickerHandler.kt:155-182,262-273`; `core/ui/mvi/.../BaseStore.kt:92-98,144-150`; `core/core/src/commonMain/.../coroutine/scope/AppCoroutineScopeImpl.kt:47-72`; `core/core/src/androidMain/kotlin/io/github/stslex/workeeper/core/core/di/DispatchersBindingContainer.kt:29-32` |
| F30 | In kotlinx.coroutines 1.11.0, `catch` handles an upstream failure only when the downstream recorded no failure of its own; when the downstream failed first or concurrently, a cancellation of its scope included, `catch` rethrows the upstream failure, or the downstream one when the upstream failure is a cancellation. | `gradle/libs.versions.toml:23`; kotlinx.coroutines 1.11.0 `kotlinx-coroutines-core/common/src/flow/operators/Errors.kt:146-208` (`catchImpl`) |
| F31 | The store's `Flow.launch` helper collects through `flowOn(work dispatcher)`, so a collection started through it becomes active only after a dispatch. The live-workout subscription is instead a child coroutine started undispatched on the main-immediate dispatcher. | `core/core/src/commonMain/.../coroutine/scope/AppCoroutineScopeImpl.kt:74-86`; `CommonHandler.kt:152-162` |
| F32 | `CHANGE_SETTLE_MS` is 500, and its KDoc calls it unmeasured. Every notifier test reads it through the constant; the only fixed timings in those tests are `BURST_STEP_MS` (50 ms, two steps in the three-writes test) and `MID_INTERVAL_MS` (100 ms, a point inside the minimum interval). | `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/PhoneChangeNotifier.kt:26-30`; `feature/wear-bridge/src/test/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/PhoneChangeNotifierTest.kt:137-198,480-485` |

Assumptions the implementer verifies in discovery (§14) before writing code:

| Id | Assumption | If false |
|---|---|---|
| ASM-5 | In the resolved `play-services-wearable` 20.0.1, `MessageClient.ACTION_MESSAGE_RECEIVED` is `com.google.android.gms.wearable.MESSAGE_RECEIVED`, and `MessageClient.sendMessage(String, String, byte[]): Task<Integer>`, `WearableListenerService.onMessageReceived(MessageEvent)` and `MessageEvent.getPath()` exist. | STOP: a one-way signal has no fallback. |
| ASM-6 | Google Play services starts the watch app's process to deliver `MESSAGE_RECEIVED` to a manifest listener whose filter matches, as it does for `REQUEST_RECEIVED` on the phone (transport §3 P3). | Owner check S3 decides. If it fails, the Tile and the indicator update only while the app runs; record it, not a STOP. |
| ASM-7 | Room re-emits a `session_table` query after an update of that table made by another trigger's statement. | STOP: the phone signal would miss set writes. Proven by a committed host test (§10.1), not a throwaway run. |
| ASM-8 | An empty `ByteArray` is a valid `sendMessage` payload. | Send the single byte `0x00`, ignore the payload on the watch, and say so in the PR. |

## 4. Architecture

```text
phone                                                                watch
─────                                                                ─────
any write (screen, bridge) ─ trigger ─▶ session_table.wear_revision
WearSyncDao.observeActiveWearKey(): (uuid, revision)?
        │ distinct, first value dropped, settle, minimum interval
PhoneChangeNotifier (generation lifetime) ── skips nodes ── WatchKnownRevisions ◀── bridge records
        │                                                    (node → session, revision answered)
PlayServicesWatchNudgeLink ══ sendMessage(CHANGED_PATH, 0x00) ═══▶ PhoneChangeListenerService
                                                                        │ WatchRuntimeFactory.get(...).onPhoneChanged()
                                                                  WatchTransportCoordinator: O6
                                                                        │ correlated handshake (transport §5, unchanged)
WearRpcListenerService ◀═════════════ sendRequest(RPC_PATH) ════════════╛
        │
PhoneWorkoutBridge ── Applied ──▶ ExternalSetWrites (in-process) ──▶ live-workout screen: applySetMarked
```

Only `PlayServicesWatchNudgeLink` and `PhoneChangeListenerService` are new Data Layer files (§8).
Everything else is plain Kotlin and host-tested with fakes.

## 5. Signal contract

### 5.1 Constants

Added to `core/wear-protocol` `WearProtocol`:

- `WATCH_CAPABILITY = "workeeper_watch_active_workout_v1"` (equals F7; a watch test pins the
  resource to the constant);
- `CHANGED_PATH = "/workeeper/wear/v1/changed"`.

### 5.2 Message

- Phone to watch only. Its meaning is "the active workout may have changed; ask". The payload is
  the single byte `0x00`, because discovery left ASM-8 unproven; the watch ignores it. The signal
  never carries a name, number, identifier or revision.
- Destination: each node returned by `CapabilityClient.getCapability(WATCH_CAPABILITY,
  FILTER_REACHABLE)`, except a node that already holds the current state (§6.2). Relay through a
  non-nearby node is permitted, as for the RPC (transport D2).
- No response, no retry, no acknowledgement. A lost signal is recovered by the next signal or by
  O1–O5.
- The watch matches the path exactly: the manifest filter uses `path`, never `pathPrefix`.

### 5.3 Phone timing (PROVISIONAL)

| Constant | Value | Meaning |
|---|---|---|
| `CHANGE_SETTLE_MS` | 150 (500 in 1.53.0; D15) | Quiet time after the last key change before a signal; one user action that commits in several transactions sends one signal. |
| `CHANGE_MIN_INTERVAL_MS` | 2,000 | Minimum time between two rounds (§6.2), a round that signals no watch included; changes inside it go out in one round at its end. |

An isolated change is signalled about 0.15 s after its commit, or at the end of a running interval
if that is later, and a burst about 0.15 s after its last change. There is at most one round per
2 s, and the last change is always sent.

### 5.4 Compatibility

| Phone | Watch | Behavior |
|---|---|---|
| 1.53.0 | 1.52.2 | The watch has no listener for the path; nothing happens. Pull only, as today. |
| 1.52.2 | 1.53.0 | No signals. Pull only, as today. |

1.53.1 behaves as 1.53.0 in this table, on either device. No protocol or schema version changes.

## 6. Phone side

### 6.1 Change key

`WearSyncDao.observeActiveWearKey(): Flow<ActiveWearKeyRow?>`:

```sql
SELECT uuid AS session_uuid, wear_revision FROM session_table WHERE state = 'IN_PROGRESS' LIMIT 1
```

The key is exactly `(session uuid, wear_revision)`, or null. The query re-emits the same key on
every `session_table` write, a handshake's lease write included (F3); only a new distinct key is a
change. A key that includes any other column (the lease generation, a receipt column, the whole
row) makes every watch handshake signal the watch again (F2, F3): an endless phone ↔ watch loop.
M-S1 proves that a handshake sends nothing.

No schema change: a new query does not change the exported Room schema.

### 6.2 Notifier and known revisions

**Known revisions.**
`feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/WatchKnownRevisions.kt`,
pure Kotlin, app-scoped, in process memory only and thread-safe: per watch node id, the key of the
last snapshot the bridge answered that node with (session uuid and revision, or no session).
Entries are never persisted or logged; at most 16 are kept, least recently written first out. The
bridge writes them (§6.4); the notifier reads them.

**Notifier.**
`feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/PhoneChangeNotifier.kt`,
pure Kotlin with no Android or Data Layer types, together with the interface it sends through:

```kotlin
interface WatchNudgeLink {
    /** Node ids of the reachable watches that advertise WATCH_CAPABILITY. */
    suspend fun reachableWatches(): List<String>

    /** Sends the CHANGED_PATH message, payload 0x00, to one node. */
    suspend fun signal(nodeId: String)
}
```

The notifier's whole behavior (D13):

```kotlin
guarded {                      // only the notifier's own cancellation propagates
    keys.distinctUntilChanged()
        .drop(1)               // the generation's first value is a baseline, not a change
        .debounce(CHANGE_SETTLE_MS)
        .conflate()
        .collect { key ->
            signalStale(key)   // one round: every reachable watch whose known key differs
            delay(CHANGE_MIN_INTERVAL_MS)
        }
}                              // any other failure: stopped(failure), e.g. a closed database (F6)
```

- A node with no known entry counts as stale. The known entries are read when the signal is sent,
  not when the change arrived.
- A round is one call of `signalStale`. The minimum interval follows every round, one that signals
  no watch included (§5.3).
- A failed lookup or send is logged by exception class only and is not retried; the next change
  signals again.
- No exception leaves the notifier (D11, D13). The guard around the whole collection is its only
  failure handling; the chain has no `catch` operator (F30). The notifier's own cancellation
  propagates as cancellation. Any other failure ends the notifier for its generation and is logged
  by class (`stopped`, the line `signal stopped: <class>`). A key-query failure that is not a
  cancellation ends it at once, wherever it arrives: before the first value, while a change
  settles, during a round, or inside the minimum interval. A `CancellationException` from the key
  query that is not the notifier's own cancels no running round or interval: the notifier ends
  when it next asks for a key, after a key already waiting is signalled. `signalStale` catches and
  logs its own failures. The guard catches through `runCatching`, as the file's `attempt` helper
  does with the same cancellation rule, because detekt's `TooGenericExceptionCaught` is active and
  nothing is suppressed. The Android restore path closes the database under the query (F6), so
  this end is expected.
- Logs carry only the word `signal`, the result class, the counts of reachable and signalled nodes,
  and elapsed milliseconds. Never a node id (transport §7.9).

### 6.3 Link and arming

- `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/PlayServicesWatchNudgeLink.kt`
  is the only new phone file that names the Data Layer. Its clients are lazy, so nothing calls
  Google Play services before the first signal. `reachableWatches` looks up `WATCH_CAPABILITY`
  with `FILTER_REACHABLE`; `signal` sends `CHANGED_PATH` with the payload `0x00`.
- The notifier is armed once per generation in `StartupProcessor.armPostPreflight`, on
  `lifetime.childScope(ioDispatcher)` (F6), behind the same recovery check as `warmQueryPlanner`
  (`StartupProcessor.kt:259`) and without its low-RAM check. An in-process rebuild cancels it with
  the lifetime before the database closes. The Android restore path closes the database under it
  and restarts the process (F6): the notifier then ends through the failure handling of §6.2, or
  stays suspended until the process ends. The notifier, the link and the known revisions are
  app-scoped bindings of the generation graph; `armPostPreflight` reaches the notifier as
  `AppGraph.phoneChangeNotifier`, and the link takes the application `Context` that
  `AppGraph.Factory` binds (Q4).
- No foreground service, no WorkManager, no wake lock. The notifier runs while the phone process
  runs, which is whenever the phone writes.

### 6.4 Live-workout screen (D4) and the bridge

**The in-process signal.**
`core/data/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/exercise/session/ExternalSetWrites.kt`:
an app-scoped `ExternalSetWrites` with `fun publish(write: ExternalSetWrite)` and
`val writes: Flow<ExternalSetWrite>`, backed by a `MutableSharedFlow(replay = 0,
extraBufferCapacity = 64)`. `publish` uses `tryEmit` and logs a refusal by class.
`ExternalSetWrite(sessionUuid, performedExerciseUuid, position, weight: Double?, reps: Int,
type: SetsDataType)` carries the values exactly as written. `SessionRepository` does not change.
The mapping from `SetTypeEntity` into `SetsDataType` is internal to `core:data:exercise`, so the
bridge maps with its own exhaustive `when`, pinned by a test over every `SetTypeEntity` value (Q4).

**Bridge.** `PhoneWorkoutBridgeImpl` makes exactly two changes besides its constructor:

1. Every response it returns, for a handshake, a command or a protocol rejection, records the key
   of the snapshot that response carries (`sessionIdentityOrNull()` of the snapshot or of the
   replacement, F26) in `WatchKnownRevisions` for the authenticated source node. It is recorded
   after the transaction, from the response actually returned, after any read-only refresh; never
   from a prepared response.
2. `completeCurrentSet` publishes one `ExternalSetWrite` for the set `processCommand` wrote, with
   the values of that `WearSetWrite` (F4), after `transition.mutate` has returned an `Applied`
   result: never inside the transaction. `AlreadyApplied`, every rejection and the write-failure
   path publish nothing. Publication runs under `coordinatorMutex`, so writes publish in commit
   order.

**Interactor.** `LiveWorkoutInteractor.observeExternalSetWrites(sessionUuid: String):
Flow<ExternalSetDomain>` filters by session and maps to the domain model.

**Store (D10).** Every `Init` starts the subscription, as it starts the timer (F12): the store's
scope ends whenever the screen leaves composition, and `Init` runs again when it returns. A
subscription still running is cancelled first, as `startTimer` cancels a running timer, so two
never run together. `Init` subscribes as soon as the session uuid is known (after `createSession`
when the screen creates the session) and starts its load only once the subscription is active
(`onSubscription`, or a collector started undispatched), so no write can fall between a load's
read and the subscription. A GUARD comment at the subscription says: if `Init` stops running on
each return (the latch `tech-debt.md` proposes), the subscription and the timer must move to the
return path, and the return-to-screen test fails until they do. The chain maps each write to its
UI model (`ExternalSetUiModel`) and is collected in a child coroutine started undispatched on the
main-immediate dispatcher, not through the store's `Flow.launch` helper, which collects on the work
dispatcher and so becomes active only after a dispatch (F31).

**Loads.** The `Init` load and `processReload` are covered the same way:

1. Before a load's database read starts, the handler records how many writes it has received so
   far.
2. A write received while any load is in flight is applied at once (A to C below) and also kept.
3. When a load's result is applied, the kept writes received after that load started are applied
   again, in order, right after it and in the same state update. Applying a write the load
   already contains changes nothing.
4. A load that fails or is cancelled covers nothing. When no load is in flight, the kept writes
   are cleared.

This bookkeeping runs where the state updates run: on the store's main-immediate dispatcher (the
subscription, which is collected there, and both loads' result callbacks), or inside a state
update (`updateState`, D14). No mutable collection is shared across dispatchers.

**Applying one write:**

- **A.** The exercise is in `State.exercises`: `setMutator.applySetMarked(latest,
  performedExerciseUuid, position, set)` (F14). If an undo window is open and its snapshot holds
  the exercise, the same patch is applied to a state built from the snapshot (its exercises,
  drafts and overrides), and the window keeps the result, so an undo restores the earlier screen
  plus the watch's set.
- **B.** The exercise is only in an open undo window's snapshot (a soft-deleted exercise, F15):
  applied to the snapshot only.
- **C.** The exercise is in neither: ignored. The next load shows the database.

Applying a write changes nothing else in `State`: dialogs, sheets, other drafts, row overrides,
explicitly started exercises, expansions, the timer, the name draft and the in-flight flags stay
as they are. No re-read of the session, no new Action, no Event, no haptic. Nothing here
navigates; navigation stays the canonical pattern (`Action.Navigation` consumed by the feature's
`NavigationHandler`, `Navigator` injected).

**Atomic writes (D14).** Every state write of the live-workout store goes through `updateState`:
the timer's tick, a watch write, a load's result and the load-failure flags included. A
compare-and-set retries on the newer state, so no write erases one made meanwhile on another thread
(F29). No update function has a side effect, so a retry repeats nothing: `coverage.receive` stays
outside it, and `coverage.since` only reads. No `updateStateImmediate` remains in the store, and a
GUARD in `CommonHandler` says why.

## 7. Watch side

### 7.1 Listener

- `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/transport/PhoneChangeListenerService.kt`
  is the only new watch file that names the Data Layer. In `onMessageReceived`, a path other than
  `CHANGED_PATH` returns at once; otherwise it calls
  `WatchRuntimeFactory.get(applicationContext).onPhoneChanged()` and returns. In a cold process that
  call builds the runtime and its Tile observer on the calling thread, as an Activity start does;
  the listener adds no work of its own.
- Manifest: `exported="true"` with `tools:ignore="ExportedService"`, no permission, and one intent
  filter: the verified action (ASM-5), held as a constant in this file for tests, `scheme="wear"`,
  `host="*"`, `path` equal to `CHANGED_PATH`.
- Debug variants keep the default no-op runtime method, so every synthetic and emulator suite keeps
  its meaning.

### 7.2 Runtime

`WatchRuntime.onPhoneChanged()` gets a default no-op body, like `onControllerInteractive`.
`ConnectedWatchRuntime` forwards it to `transport.onPhoneChanged()`.

### 7.3 Origin O6 (D6)

| Id | Origin | Condition |
|---|---|---|
| O6 | Phone change signal | None: it runs while interactive, in ambient, and with no Activity at all. |

O6 has its own token bucket (PROVISIONAL): `PHONE_CHANGE_BURST` = 10 tokens, one regained every
`PHONE_CHANGE_REFILL_MS` = 10,000. O6 is neither counted against nor limited by
`AUTO_REFRESH_BUDGET`. Rules, all on the coordinator's single-threaded scope:

1. `onPhoneChanged()` sets `phoneChangePending` and tries to serve it.
2. Issuing any handshake token, from any origin, clears `phoneChangePending`: that request starts
   after the signal arrived, and the phone signals only after its commit, so the answer includes the
   change. A handshake already in flight when the signal arrives does not clear it.
3. Serving does nothing while a refresh is queued (rule 2 clears the flag when it starts) or any
   request is in flight. Otherwise it checks, before enqueueing anything:
   - retry preservation holds: nothing is enqueued; the deferral (rule 5) is armed for the
     binding's deadline;
   - the bucket is empty: nothing is enqueued; the deferral is armed for the next token;
   - otherwise it takes a token and enqueues one O6 refresh.
   If that refresh is still dropped at start, or its token cannot be issued, the flag is cleared and
   the drop is logged; the next signal or O1–O5 recovers the change. A blocked O6 is never enqueued,
   so the scope goes idle until the deferral fires.
4. Serving is attempted on every signal, after every request completion (after the follow-up rule),
   whenever a queued refresh of another origin is dropped at start, and when the deferral fires.
5. Deferral: at most one is armed; it fires once and tries to serve. It is the coordinator's only
   timer and exists only while a change is pending. This amends transport §7.2 "No polling": every
   request still has an origin, and the deferral only delays an O6 that already arrived. Its delay
   is derived from the coordinator's `clock`, so tests drive it with the existing test clock. The
   coordinator's KDoc invariants are updated to say so. The wait is computed from `clock` (elapsed
   real time), while the deferral's timer stops when the watch sleeps: after a sleep the deferral
   fires late, never early, and serves at once, because serving computes the wait again; a signal,
   a completion or a drop meanwhile serves it sooner. A process reclaimed meanwhile loses the
   deferral, and the next signal or O1–O5 recovers (§11).
6. Follow-up: as transport §7.4; an O6 chain's follow-up is an ordinary automatic follow-up under
   the existing budget, and an O6 chain gets none while the controller is not interactive, like
   TILE and AUTHORITY_EXPIRED.
7. An O6 handshake that fails (unreachable, unanswered) is not retried.
8. Everything else is unchanged: single flight, late tokens, commands never dropped or merged, and
   no handshake while a command attempt is in flight.

The O6 bookkeeping (flag, bucket, deferral) may live in a small class the coordinator owns if
detekt's size thresholds require it. No suppressions.

What the user sees:

- App interactive or in ambient: the controller updates in place through the owner's snapshot flow.
  For the round trip of an O6 handshake the authority is retired (F25): the controls are disabled
  and, outside ambient, an open numeric editor closes. D7 keeps this from happening after the
  user's own Complete set; it still happens when the phone really changed something while the user
  edits on the watch.
- App closed: the listener starts the process, the release factory builds the runtime and the Tile
  observer (F17), and the accepted snapshot updates the cache, requests a Tile update and posts or
  cancels the ongoing indicator. The O2 minimum age (60 s) then keeps the Tile render from starting
  a second handshake.
- A watch draft survives an O6 handshake at an unchanged revision and is cleared when the revision
  changed (F18).

### 7.4 Logging

O6 adds the origin label `phone_changed` to the existing transport log lines and nothing else.

### 7.5 Back stack (D5)

`android:launchMode="singleTop"` on `MainActivity`; `taskAffinity=""` stays.

A Tile tap starts `MainActivity` with an explicit component and no action. When the running task's
root came from the launcher (MAIN, LAUNCHER) the two intents differ, and Android adds a second
instance on top of the first. The ongoing indicator avoids that with `FLAG_ACTIVITY_SINGLE_TOP`; a
protolayout `LaunchAction` cannot set flags (F19), so the manifest must. With `singleTop` the running
instance receives `onNewIntent` instead, and the handshake comes from O1 when it resumes (F19).

A swipe back that closes the numeric editor and returns to the controller is by design (F20) and
does not change.

## 8. Allowlist (D3)

The allowlist becomes exactly these four files:

1. `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/WearRpcListenerService.kt` (existing)
2. `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/transport/PlayServicesWearLink.kt` (existing)
3. `feature/wear-bridge/src/main/kotlin/io/github/stslex/workeeper/feature/wear_bridge/transport/PlayServicesWatchNudgeLink.kt`
4. `app/wear/src/main/kotlin/io/github/stslex/workeeper/wear/transport/PhoneChangeListenerService.kt`

Every layer changes the way transport §8 changed it, and every normative mention follows:

- the `ForbiddenImport` and `WearDataLayerApiRule` `excludes` in `lint-rules/detekt.yml` (exact
  paths behind the leading `**/`) and the GUARD comments there;
- `assert_wear_transport_gate.py`: `TRANSPORT_ALLOWLIST`, the pinned self-test literal, the module
  docstring and the messages that say "two";
- the GUARD comments of the two existing allowlisted files and of `ReleaseRuntimeBoundaryTest.kt`
  (`app/wear/src/testRelease/.../ReleaseRuntimeBoundaryTest.kt:32`);
- `documentation/lint-rules.md` (both passages, `:694-705` and `:810-814`) and
  `documentation/ci-cd.md:79-82`;
- every comment or document that cites transport §8 as the allowlist's authority now cites this
  section, including `detekt.yml:449,461,769`, the gate script and `lint-rules.md:702`.

The implementer greps for the two existing file names, "two files", "exactly two", "§ 8" and
"section 8" near the Data Layer, and the PR lists every hit with its decision: updated, superseded,
or a dated record. Dated records keep their wording: the Phase 1 §6.1 closure record
(`wear-phase-1-active-workout-tile.md:1011-1012`, which stays true because workout payloads still
cross only through the two RPC files), transport Appendix D, and every ledger. Transport's normative
"only file" clauses are superseded by the status paragraph of §9, not edited.

A Data Layer file lands in the same commit as its allowlist entries. Tests never spell the Data
Layer package; they use constants declared in the allowlisted files or in `WearProtocol`.

**Bundle gate G12** (`assert_play_bundle.py`, `documentation/ci-cd.md`). Wear: exactly one
`<service>` has an intent filter with the action `com.google.android.gms.wearable.MESSAGE_RECEIVED`.
It is `android:exported="true"`, neither it nor the application sets `android:enabled` to anything
but `true`, neither declares an `android:permission`, and the filter's data is exactly scheme
`wear`, host `*` and path `/workeeper/wear/v1/changed`, with no `pathPrefix`, `pathPattern` or other
data attribute. Not applicable to phone. The change touches the module docstring, `CHECKS`,
`evaluate`, the self-test cases and the `ci-cd.md` table and its introduction (`:167`). In the
self-test, G12 is `N/A` in every phone-role case and `SKIP` wherever G1 fails, as G10 is; the Wear
manifest fixture gains the listener, and a phone bundle checked as Wear fails G12. Self-test
fixtures follow G10's. G12 lands in the commit that adds the watch listener. G9's Wear GUARD
comment points at `WearProtocol.WATCH_CAPABILITY`.

## 9. Privacy and copy

- `docs/index.md` does not change. Checked against F21 sentence by sentence: the signal carries
  none of the listed data, adds no destination, and moves only between the user's phone and watch
  through the Data Layer, relay included. The Data safety form does not change either: the signal
  carries no user data, and the reasoning of transport §9.3 holds.
- `documentation/product.md`: Appendix A (D8).
- The transport specification's status block gains this paragraph after the 2026-09-30 amendment,
  and nothing else in it changes:

  > **Amended on 2026-10-01** by owner decision after the 1.52.2 field test: D4; §8 and the
  > clauses that call its two transport files the only Data Layer files (§4, §5.5, §6.1, §7.1); the
  > phone-UI and bridge clauses of §6.4; the "no polling" invariant of §7.2; and the push item of
  > §13 are superseded by [Wear live sync](wear-live-sync.md).

## 10. Test contract

### 10.1 Host tests (new)

Phone (`:feature:wear-bridge`, `:app:app`, `:feature:live-workout`; the in-memory database of
`:core:data:database-test` wherever data is involved, with `prepareWearSyncStorage` in the setup
(F27); virtual time for the notifier):

- key flow: a set insert, update and delete through the phone's repository each produce a new
  distinct key for the active session (ASM-7); a handshake that grants authority through the real
  `PhoneWorkoutBridgeImpl` (only such a handshake writes, F2) produces no new distinct key;
- notifier with a fake link: arming with an active session sends nothing; one set write sends
  exactly one signal, `CHANGE_SETTLE_MS` after it; three writes within 100 ms send one; a write
  inside the minimum interval is sent once at its end; a granting handshake through the real bridge
  sends nothing; an applied watch command sends nothing to the commanding node and one signal to a
  second reachable watch that holds an older state; a session start and a finish each send one; a
  failing link is logged by class and the next change still signals; cancelling the lifetime stops
  it; a key flow that throws (standing in for the closed database of F6) is logged by class and
  ends the notifier, and nothing reaches a recording `CoroutineExceptionHandler` installed on its
  scope. Closing the real in-memory database under a running notifier is also run once and its
  outcome recorded (Room may throw or stay suspended); the same no-escape assertion holds either
  way, but it is not a mutation target;
- startup: armed for a generation that proceeds, not armed for one that routes to recovery;
- bridge: every returned response records its snapshot's key for the source node, after the
  transaction and after any read-only refresh; `Applied` publishes exactly one write with the
  written values, and only after `mutate` returned, checked through a transition-runner wrapper
  that records whether a publication happened inside a transaction (the pattern of
  `PhoneWorkoutBridgeImplTest.kt:1570-1580`); an exact replay (`AlreadyApplied`) publishes nothing;
  every rejection and the write-failure path publish nothing;
- live-workout store: each of A to C of §6.4; every `Init` load starts only after the subscription
  is active, and a write emitted between the two is applied; a write delivered between a load's
  read and its apply is shown after the load, for the `Init` load and for `processReload`; a
  return to the screen (dispose, then `Init` again) without a save, then a write, shows the write;
  a return with a save, with the two loads applied in either order, keeps a write delivered during
  them; a repeated `Init` without a dispose leaves one active subscription (the fake interactor
  counts collectors); a write for another session is filtered out by the interactor; after a
  patch the open dialog, the sheet, other rows' drafts, row overrides, explicitly started
  exercises and expansions are unchanged, and the same set's draft is replaced by the watch's
  values.

Watch (`:app:wear`):

- coordinator: an O6 while idle starts one handshake, and so does one while not interactive; an O6
  during an in-flight handshake starts exactly one more after it; an O6 while a refresh is queued
  starts no extra one; an O6 during an in-flight command starts one after the command; under retry
  preservation, nothing is enqueued until the binding's deadline, then one handshake starts; with
  the budget spent (six automatic handshakes within 60 s) an O6 still starts; bucket: eleven
  signals in quick succession, each after the previous handshake completed, start ten handshakes
  without waiting and the eleventh one refill period after the first, with no other origin, and
  while it waits the coordinator runs no work; an O6 handshake that finds no reachable phone is not
  retried; an O6 chain answered `Unavailable` gets one follow-up while interactive and none while
  not; a draft survives an O6 handshake at the same revision and is cleared at a new one;
- release boundary: the release factory's runtime forwards `onPhoneChanged` to the coordinator;
- manifest: a `PackageManager` query for the listener's action constant with data
  `wear://any/workeeper/wear/v1/changed` resolves to the service, and
  `wear://any/workeeper/wear/v1/changedx` does not (or an equivalent check of the merged manifest);
- capability: `R.array.android_wear_capabilities` equals `[WearProtocol.WATCH_CAPABILITY]`;
- launch mode: `MainActivity`'s `ActivityInfo.launchMode` is `LAUNCH_SINGLE_TOP`.

Added by the 2026-10-03 amendment (PR-S2), phone:

- notifier guard (D13): the key flow fails before its first value, while a change settles, while a
  lookup is suspended, while a send is suspended, and inside the minimum interval; each time
  exactly one `signal stopped: <class>` line is logged, nothing reaches the recording handler, and
  nothing is signalled afterwards. A `CancellationException` thrown by the key flow while the
  notifier is active, before its first value or while a change settles, is logged and ends it the
  same way. Cancelling the generation lifetime ends the notifier with no `signal stopped` line;
- bridge, cache hits: for each entry point (handshake, command, protocol rejection), record another
  key for the source node, send the identical request again, and the node holds the cached
  response's key;
- bridge, command refresh: a command whose lease publication loses a race to a phone edit, set up
  as the handshake case at `PhoneWorkoutBridgeSignalTest.kt:107` but armed after the handshake that
  grants the command's lease (the hook fires at the first plain transaction), records the key of
  the read-only refresh it returns, never the prepared one;
- bridge, rejections: one case per rejection outcome `completeCurrentSet` can return, each
  asserting its exact outcome and that nothing is published: `StaleRevision`, `NoActiveSession`,
  `TargetChanged` (a lease bound to the moved target, as `bindSyntheticLease` binds one in
  `PhoneWorkoutBridgeImplTest`), `AuthorizationExpired`, `ProtocolRejected` (for example the same
  command id with another attempt fingerprint after an Applied write), `InvalidValues` and
  `ImmutableTypeMismatch`. The existing test (`PhoneWorkoutBridgeSignalTest.kt:217-233`) covers
  `StaleRevision` and, through its moved position, `AuthorizationExpired`, without asserting
  either outcome;
- graph: two reads of `AppGraph.watchKnownRevisions` give the same instance, so the bridge and the
  notifier share one (the pattern of `LiveWorkoutExtensionIdentityTest.kt:97`). For this,
  `WatchKnownRevisions` may become a public class whose members stay internal, and `AppGraph`
  exposes it with a KDoc naming this test as its only reader, as it does `externalSetWrites`
  (`AppGraph.kt:154-158`). No reflection;
- live-workout store, session creation: `Init` with no session in the route subscribes once the
  session exists and before its load reads, and shows a later write, for both creation paths (a
  plan's `startSession` and Quick start's `createAdhocSession`);
- live-workout store, atomic writes (D14): a recording store (for example the test's `BaseStore`
  overriding `updateStateImmediate`) records no `updateStateImmediate` call across `Init`, a load, a
  watch write, a failed load and at least two timer ticks, and the state's `nowMillis` advances at
  least twice;
- test infrastructure: a real-time thread can no longer corrupt the queue of the `ManualDispatcher`
  at `CommonHandlerExternalWritesTest.kt:352-358`: the queue is thread-safe (preferred), or the
  dispatcher implements `Delay` on the test dispatcher.

Watch:

- coordinator, rule 4: a signal arrives while a command is in flight and an O1 is queued behind
  it; the command times out, the O1 is dropped as retry-preserved, and exactly one O6 handshake
  starts at the binding's deadline;
- coordinator, rule 3: with the controller not interactive, an O6 whose token cannot be issued (the
  owner's token issue fails once, through a test-only hook of `RuntimeTestEnvironment` like its
  `failNextRead`) is dropped once with `no token`; no handshake starts until the next signal, which
  starts one;
- coordinator, rule 6: with the automatic budget spent, an O6 chain answered `Unavailable` while
  interactive gets no follow-up;
- coordinator, rule 5: while an O6 waits for a token and nothing else is due, the coordinator does
  no work until the deferral fires, once: counted, for example, by a clock that counts its reads (a
  deferral that polls reads it on every wake-up) or by a dispatcher that counts dispatches;
- coordinator, refill start: one O6 at t0 and nine more 4 s later, each after the previous
  handshake completed, empty the bucket; an eleventh signal right after them starts at t0 + 10 s,
  not at t0 + 14 s;
- limiter (`PhoneChangeLimiter`, unit): a partial refill period is kept: after ten takes at t0, a
  token taken at t0 + 15 s leaves the next one at t0 + 20 s.

Added by the 2026-10-06 amendment (PR-S2), phone:

- settle (D15): one set write sends no signal 149 ms after it and exactly one 150 ms after it, with
  both times written as literals in the test, never through `CHANGE_SETTLE_MS`. Every existing
  notifier test keeps reading the constant (F32) and passes unchanged.

Gates: `assert_wear_transport_gate.py --self-test` covers all four paths with the existing cases
(allowlisted file passes; sibling, path-suffix and directory-prefix fail; a suppression inside an
allowlisted file fails); `assert_play_bundle.py --self-test` gains the G12 fixtures.

### 10.2 Named mutations

Each is applied, run RED with the command and exit code recorded, restored, and run GREEN. Every
run uses `--rerun-tasks --no-build-cache --no-daemon`; detekt and tests run as separate invocations;
every gate reports its input count.

| Id | Mutation | Must fail |
|---|---|---|
| M-S1 | the key includes `wear_lease_generation` (or the flow emits the whole row) | handshake-sends-nothing test |
| M-S2 | `drop(1)` removed | baseline test |
| M-S3 | debounce removed | three-writes test |
| M-S4 | minimum interval removed | minimum-interval test |
| M-S5 | notifier armed for a generation that routes to recovery | startup test |
| M-S6 | notifier launched outside the generation lifetime | lifetime-cancel test |
| M-S7 | the bridge publishes inside the transaction | transition-wrapper test |
| M-S8 | the bridge publishes for `AlreadyApplied` | replay test |
| M-S9 | the interactor stops filtering by session | session-filter test |
| M-S10 | the `Init` load does not apply again the writes received since it started | load-race test (`Init`) |
| M-S11 | the store re-reads the session (`processReload`) instead of patching | unchanged-ephemeral-state test |
| M-S12 | the patch skips the undo snapshot | undo test |
| M-S13 | an O6 during an in-flight handshake is dropped | in-flight test |
| M-S14 | a later-started handshake does not clear `phoneChangePending` | queued-refresh test |
| M-S15 | O6 counted against `AUTO_REFRESH_BUDGET` | spent-budget test |
| M-S16 | O6 requires the controller to be interactive | not-interactive test |
| M-S17 | an O6 follow-up allowed while not interactive | follow-up test |
| M-S18 | O6 ignores retry preservation | retry test |
| M-S19 | no deferral armed when the bucket is empty | bucket test |
| M-S20 | `ConnectedWatchRuntime` does not forward `onPhoneChanged` | release boundary test |
| M-S21 | the listener filter uses `pathPrefix="/workeeper/"` | watch manifest test (`changedx` resolves) |
| M-S22 | the watch capability resource renamed | capability test |
| M-S23 | `launchMode` removed | launch-mode test |
| M-S24 | a fifth file imports `com.google.android.gms.wearable` | detekt and the gate script |
| M-S25 | the watch listener removed from a release-manifest fixture | G12 self-test |
| M-S26 | the bridge does not record the key for command responses | echo test (the commanding node is signalled) |
| M-S27 | the notifier ignores known keys | two-watch test |
| M-S28 | a load starts before the subscription is active | subscription-race test |
| M-S29 | a blocked O6 is enqueued anyway | bucket test (the coordinator keeps running work while it waits) |
| M-S30 | `processReload` does not apply again the writes received since it started | load-race test (`processReload`) and the return-with-save test |
| M-S31 | the subscription starts only on the store's first `Init` | return-to-screen test |
| M-S32 | the notifier's guard removed; the chain has no `catch` operator (D13) | key-flow-failure tests (an exception reaches the handler; for the foreign cancellation, no `signal stopped` line) |
| M-S33 | a running subscription is not cancelled before `Init` starts another | single-collector test |
| M-S34 | a dedupe-cache hit returns without recording its key; applied at each of the three cache-hit returns (`PhoneWorkoutBridgeImpl.kt:70,103,149`) in turn | the cache-hit test of that entry point |
| M-S35 | a command records its prepared response instead of the one it returns (`PhoneWorkoutBridgeImpl.kt:135`) | command-refresh test |
| M-S36 | `@SingleIn` removed from `WatchKnownRevisions` | graph identity test |
| M-S37 | a refresh of another origin dropped at start does not serve the pending change (the `else` branch at `WatchTransportCoordinator.kt:301` removed) | rule 4 test |
| M-S38 | an O6 whose token cannot be issued keeps its change pending (`WatchTransportCoordinator.kt:272` removed) | rule 3 test |
| M-S39 | an O6 chain's follow-up is exempt from the budget (`&& !followUp` removed at `WatchTransportCoordinator.kt:505`) | rule 6 test |
| M-S40 | the deferral re-arms every millisecond (`PhoneChangeLimiter.kt:45` returns 1 while the bucket is empty) | rule 5 test |
| M-S41 | the refill period restarts at every take (`PhoneChangeLimiter.kt:52` without its condition) | refill-start test and the limiter test |
| M-S42 | `Init` subscribes only when the route carries a session | session-creation test |
| M-S43 | a state write of the store goes back to `updateStateImmediate`; applied to the timer's tick and to the watch write in turn | atomic-writes test |
| M-S44 | the notifier's guard treats its own cancellation as a failure | guard test (cancelling the lifetime logs `signal stopped`) |
| M-S45 | `CHANGE_SETTLE_MS` back to 500 (D15), and in a second run 100 | settle test |

### 10.3 Existing gates

Unchanged and green: root `testDebugUnitTest`, `lintDebug`, `detekt` (zero new suppressions; the
new `tools:ignore="ExportedService"` is lint, documented by transport §3 P3, and listed in the PR),
`assembleDebug`, `assembleDebugAndroidTest`, both release bundles with every applicable check of
G1–G12, the mockup gate, the transport gate script, `ReleaseRuntimeBoundaryTest`, and every Wear
emulator suite CI runs. No Paparazzi golden changes: no UI change is intended, so a golden diff is a
STOP.

### 10.4 Device evidence (implementer)

- **E1, back stack.** On a Wear emulator: `am start -W -a android.intent.action.MAIN -c
  android.intent.category.LAUNCHER -n <package>/<MainActivity>`, then `am start -W -n
  <package>/<MainActivity> -f 0x10000000` (the Tile's shape), then `dumpsys activity activities`
  for the task. Expected: two `MainActivity` records on the base commit (the known negative), one
  with the fix. Required when a Wear emulator is available; otherwise recorded as UNVERIFIED, and
  S9 decides.
- A paired phone and watch emulator run of S1 to S5 is optional. It never replaces §12.3.

## 11. Failure modes

| Situation | Watch | Phone | Data |
|---|---|---|---|
| Phone 1.53.0, watch 1.52.2 | pull only, as today | signals go nowhere | none |
| Phone 1.52.2, watch 1.53.0 | pull only, as today | — | none |
| A signal is lost | stale until the next signal or O1–O5 | — | none |
| A response to the watch is lost after the phone recorded its key | stale; the watch's own timeout, Retry or next origin recovers | no signal for that state, since it believes the watch holds it | none |
| Signal during an in-flight handshake | one O6 handshake after it | — | none |
| Sustained phone edits | ten O6 handshakes at once, then one per 10 s; the last change is always served | at most one signal per 2 s; the last change is always sent | none |
| Set completed on the watch | no extra handshake (D7) | no signal to that watch; a second watch holding an older state is signalled | none |
| Two Workeeper watches in one workout (D12) | each set completed on watch A signals watch B, whose handshake retires A's lease (F5); A's next Complete set fails once with an error haptic and keeps its values (F28), and the next tap succeeds; the same holds the other way | — | none |
| An O6 handshake fails (unreachable, unanswered) | not retried; the next signal or O1–O5 recovers | — | none |
| Process started by a signal is reclaimed before its handshake ends | Tile and indicator stale until the next origin | — | none |
| Phone restores a backup | stale until the next origin, whose answer carries the rotated epoch | the restore closes the database under the notifier (F6); its query may fail, which it logs by class, and the process restarts; the new generation re-arms the notifier with empty known revisions, and its first value is a baseline | none |
| Phone routes to recovery | handshakes go unanswered, as today | notifier not armed | none |
| Phone without Play services, or with no watch | — | lookup fails or finds no node; logged by class | none |
| Notifications denied on the watch | controller and Tile update; no indicator | — | none |
| The phone changes while the user edits on the watch | for one round trip the controls are disabled and an open editor closes (F25) | — | none |
| A phone draft is in the set the watch completes | — | the row shows the watch's values | the watch's values |
| The phone screen returns (from the plan editor or elsewhere) while the watch completes sets | — | the screen reloads as today (F12) and keeps every watch set received since the reload started (D10) | none |
| Both devices complete the same set within about 100 ms | — | the screen may keep the earlier writer's values until re-entry | last writer wins, as today |
| Watch set during an undo window, then Undo | — | the screen restores the earlier state plus the watch set; a re-upsert compensation of the same position overwrites it | last writer wins |
| Watch set in an exercise soft-deleted on the phone | — | kept in the undo snapshot; closing the window deletes the exercise with it | deleted with the exercise |
| Three sets completed on one watch within about 2.2 s (2.5 s in 1.53.0) | the watch may be signalled once anyway: for that handshake's round trip the controls are disabled and an open editor closes (F25) | the second set's key waits out the interval that the first set's round started; the third set makes the bridge record a newer key for the watch, so the waiting round, which compares with the second key, signals it (D7 compares with the round's key, not the newest; §13) | none |
| A signal arrives after the watch issued a command and before the coordinator received it, and the command then times out | the O6 is dropped as retry-preserved instead of waiting for the binding's deadline (§7.3 rule 3; an exception to D6); Retry, the next signal or O1–O5 recovers | — | none |
| The phone's signal is served while the watch issues a command (any origin can do this; O6 arrives unprompted, so it is likelier) | the handshake token retires the attempt just issued, so the set is not sent; the next tap succeeds after the answer | — | none |
| The watch sleeps while an O6 waits for its deferral | served when the deferral fires after that much awake time, or sooner at the next signal, completion or O1–O5; never early (§7.3 rule 5); a process reclaimed meanwhile loses it until the next signal or O1–O5; no alarm or wake lock | — | none |
| Discard on the empty-finish dialog after the watch completed a set | — | the dialog was decided at Finish, before the watch set; Discard deletes the session with that set (since 1.52; §13) | the watch set is deleted |

## 12. Release and acceptance

### 12.1 Order

1. The owner approves this specification. It lands on `dev` together with the transport status
   paragraph of §9 (a direct push; specifications take no PR).
2. PR-S into `dev`, bisect-green per commit. Suggested commit order: the back-stack fix; the
   `WearProtocol` constants with the watch capability test; the phone key, known revisions,
   notifier, link, arming and their allowlist entries; the watch listener, O6, their allowlist
   entries and G12; the in-process write signal, the bridge and the live-workout store;
   documentation. The owner merges.
3. `android_build_unified.yml` is dispatched on `dev` with `execute_unit_tests=true` and is green.
4. 1.53.0 is cut and deployed through `cut_release.yml` and `android_deploy_prod.yml` exactly as
   [release-flow.md](../release-flow.md) §6.1 describes: versionCode 56, Wear 1000056; phone to
   production, Wear to `wear:internal`; the owner approves the `production` environment.

### 12.2 Implementer evidence after release

- Play shows 1.53.0 (56) on production and `1.53.0-wear` (1000056) on `wear:internal`, from the
  run logs.
- Both deploy lanes end in `RESULT PASS`, with G12 PASS on the Wear lane and N/A on the phone lane;
  a re-run of the Wear job ends in SKIP.
- Ledger rows in §15.

### 12.3 Owner physical checklist

Setup as transport §12.3: Play installs only (D7 there), notifications allowed on the watch.
Before S1 the owner writes the installed Workeeper version of both devices into the report, as the
device shows it (the app's App info page or the Play Store's "About this app"). "No pending update"
does not establish a version: a device that was never offered the update shows none either. A
watch older than 1.53.0-wear has no listener for the signal and fails S1, S2, S3, S7 and S8 by
design (§5.4).

| Id | Step | Expected |
|---|---|---|
| S1 | Watch app open; complete a set on the phone | the watch shows the next set within about 3 s, untouched |
| S2 | Watch app in ambient (wrist down); complete a set on the phone | the ambient screen shows the next set within about 3 s |
| S3 | Watch app swiped away, Tile added; start a workout on the phone | within a few seconds the Tile shows it and the ongoing indicator is on the watch face |
| S4 | Phone live-workout screen open, one card expanded, a value typed in another row; complete a set on the watch | the phone shows that set done within about 3 s; the card stays expanded and the typed value stays |
| S5 | On the watch, complete a set and at once open the next set's reps editor; wait 5 s | the editor stays open and its buttons stay enabled |
| S6 | Type a value on the phone in the row the watch completes next; complete it on the watch | the phone row shows the watch's values |
| S7 | Complete five sets on the phone within about 10 s while watching the watch | the watch ends on the right set within about 5 s of the last tap |
| S8 | Finish the workout on the phone | within a few seconds the watch shows no workout and the indicator is gone, without reopening |
| S9 | Open the app from the launcher, go to the watch face, tap the Tile, swipe back once | the watch face, not the app |
| S10 | Phone Bluetooth off, both online; repeat S1 and S4 | works, possibly slower |
| S11 | After the session, the phone history | exactly the sets completed, no duplicates |
| S12 | Transport P1 to P12 | still pass; P4 and P10 no longer need a reopen |

The owner reports pass or fail per row with a note, and a screenshot for a failure. Failures feed a
1.53.x fix.

### 12.4 PR-S2 and hotfix 1.53.1 (amendments of 2026-10-03 and 2026-10-06)

1. Each amendment lands on `dev` as its own direct push (specifications take no PR).
2. PR-S2 into `dev`, bisect-green per commit: D13 and its tests, D14 and its test, D15 and its
   test, the other tests §10.1 adds for PR-S2, and the documentation below. Its new named mutations
   are M-S32 as redefined and M-S34 to M-S45; every other named mutation whose test or production
   file PR-S2 changes runs again. No commit mixes a change under `documentation/` with a change
   anywhere else, so that step 3 can take every other commit unchanged. §12.1 step 3 follows the
   merge.
3. PR-S2 ships as hotfix 1.53.1 (D15) through [release-flow.md](../release-flow.md) §6.2, because
   `dev` holds unreleased changes unrelated to it (#312, #317 and #318 at `f64e13f3`):
   - `cut_release.yml` with `mode: hotfix` cuts `release/release-v.1.53.1` from `master`: 1.53.1,
     versionCode 57, Wear 1000057 (release-flow.md §4.5);
   - every PR-S2 commit that changes anything outside `documentation/` is cherry-picked onto that
     branch, in order. The same cherry-picks are first rehearsed on `master` before the cut, gated
     per commit as on `dev`; a hotfix commit whose tree equals its rehearsal commit's except
     `gradle/libs.versions.toml` takes over that commit's gate evidence;
   - `android_deploy_prod.yml` then runs on the branch: phone to production, Wear to
     `wear:internal`. The `production` environment has no protection rule (§15, 2026-10-03), so the
     deploy does not wait for an approval;
   - §12.2's evidence and ledger rows follow, with 1.53.1, 57 and 1000057. The sync of `master` into
     `dev` then carries the cherry-picked commits, whose changes `dev` already holds.
4. The owner runs §12.3 on 1.53.1, both versions recorded. If S1, S2, S3, S7 and S8 pass, the
   signal is accepted. If they fail, the cause lies either outside what the emulator run of 1.53.0
   covered (§15, 2026-10-06: Bluetooth, relay, the Play-signed builds) or in what 1.53.1 changed
   (D13, D15), and a run on the physical devices with logging builds is specified next.

Documentation and comments in PR-S2, none of which changes behavior:

- `PhoneChangeNotifier.kt:26-29`: the `CHANGE_SETTLE_MS` KDoc names D15 and stays PROVISIONAL;
  "Unmeasured" becomes "not measured on a physical device";
- `PhoneChangeNotifier.kt:32-36`: the `CHANGE_MIN_INTERVAL_MS` KDoc speaks of rounds (§5.3);
  `:77-86`: the KDoc describes the guard (D13), not the place of a `catch`;
- `WatchTransportCoordinator.kt:91-97`: "served only while nothing is queued or in flight" becomes
  "served only while no refresh is queued and no request is in flight", and the awake-time clause of
  §7.3 rule 5 is added;
- `ReleaseRuntimeBoundaryTest.kt`: the class KDoc (`:37-38`) no longer says that nothing asserts on
  the link, and names the O6 test's dependence on `@Order(1)` and on the runtime the factory keeps
  for the process; one blank line separates the two tests (`:64-65`);
- `documentation/ci-cd.md:75-78`: the storeRelease boundary step also proves that the release
  factory's runtime forwards the phone's change signal (the release boundary test, M-S20's named
  test, runs only there); `:183`, G9: the Wear item names `WearProtocol.WATCH_CAPABILITY`;
- `assert_wear_transport_gate.py`: the messages at `:615-616` and `:634` say "Data Layer
  allowlist" and the one at `:631` says "on the Data Layer allowlist", instead of "transport
  allowlist" and "transport-allowlisted"; the docstring's short line at `:52` is rewrapped;
- `WearDataLayerApiRule.kt`: the issue description (`:41-43`) and the report message (`:109-111`)
  say that only the allowlisted files of §8 may name the Data Layer, instead of citing a privacy
  review that has not happened; the report message keeps `$FORBIDDEN_PACKAGE`, which
  `WearDataLayerApiRuleTest.kt:29` pins;
- the PR-S2 body lists the compiler suppression PR-S added in a test
  (`CommonHandlerExternalWritesTest.kt:101`, the idiom of `BaseStore.kt:122`). PR-S2 adds no
  suppression of any kind; an `@OptIn` in a test, for example to `InternalCoroutinesApi` for a
  `Delay`, is not one.

## 13. Out of scope

Phase 2 watch UX (the exercise list, picking the exercise, skipping a set or an exercise) and its
protocol v2; a revision inside the signal; keeping the watch controls enabled during a handshake;
a watch status that tells "phone not found" from "phone didn't answer"; the re-entry mechanism of
`BaseStore` (`tech-debt.md`); the teardown order of the restore path; per-node lease retirement
(D12); skipping a watch that already holds the newest key, which would end the rapid-set signal of
§11; making the in-flight-command check atomic with issuing a handshake token, for every origin
(the two command races of §11); Discard on the empty-finish dialog after a watch set (§11), where
re-checking that the session is still empty or closing the dialog when a watch write lands is the
owner's choice; Wear production; measured constants; the release backlog (rulesets, signing of the
bump commit, the fastlane `beta` lane, the Actions cache, Data safety review, the `production`
approval that `release-flow.md` §6.1 describes and the environment does not enforce).

## 14. Discovery (before editing) and STOP conditions

Discovery rows, each with command, evidence and verdict:

- **Q1** ASM-5 by `javap` on the resolved AAR's `classes.jar` (`MessageClient`,
  `WearableListenerService`, `MessageEvent`) and a string search for the action. No fallback.
- **Q2** ASM-8 by the same means or the reference; otherwise the one-byte payload.
- **Q3** F1 to F28 re-verified at the current `dev` head.
- **Q4** The graph accessor `armPostPreflight` uses for the notifier, the application `Context`
  binding for the link (§6.3), and where the bridge maps into `SetsDataType` (§6.4).
- **Q5** ASM-7: the key-flow test of §10.1 is written and run first; RED is a STOP.
- **Q6** (PR-S2) F29 to F31, and every line the 2026-10-03 amendment cites, re-verified at the
  current `dev` head; pure line drift is recorded.
- **Q7** (PR-S2, hotfix) F32 re-verified at the current `dev` head; `master` still at the
  `release-v.1.53.0` merge with 1.53.0 / 56; and the files PR-S2 changes outside
  `documentation/` compared between `master` and `dev`, so that a commit that would not
  cherry-pick cleanly is known before the hotfix is cut.

STOP and report when:

- ASM-5 or ASM-7 fails;
- a fact in §3 is contradicted in a way that changes the design (not line drift);
- the work would change a wire model, the codec, a fingerprint, reducer admission, phone
  validation, the database schema or a permission;
- a gate can only be opened by a wildcard other than detekt's leading `**/`, a directory prefix or
  a suppression;
- a named mutation cannot be made RED;
- a PR-S2 test needs reflection, or a production change beyond D13, D14, D15, the documentation
  of §12.4 and the graph item of §10.1 (`WatchKnownRevisions` public with internal members, the
  `AppGraph.watchKnownRevisions` accessor and its KDoc);
- a PR-S2 commit does not cherry-pick cleanly onto the hotfix branch, or the hotfix branch would
  need any change that is not a PR-S2 commit or the version bump;
- a Paparazzi golden changes;
- a bundle identity gate fails;
- the `product.md` OLD block of Appendix A does not occur exactly once, or the result hash differs
  while the base hash matched;
- anything would claim physical acceptance or a measured constant.

## 15. Implementation ledger

Append-only. One row per PR, release step and acceptance item.

| Date | Item | Commit or run | Evidence |
|---|---|---|---|
| 2026-10-02 | §12.1 step 1: this specification on `dev`, with the transport status paragraph of §9 | e8a9395f | Direct push. The copy's sha256 `65765213…` equals the approved source file; the transport specification changed only by the §9 paragraph. GitHub verified the signature. |
| 2026-10-02 | Amendment after PR-S discovery: D10 to D12, the Q2 and Q4 answers, the G12 self-test SKIP rule | 1e663164 | Direct push. The copy's sha256 `97f90f8a…` equals the owner's file. GitHub verified the signature. |
| 2026-10-02 | PR-S #313: the phone's change signal, origin O6, the live-workout screen's watch sets, `singleTop`, the four-file allowlist, G12 | head 18bdee78 (9 commits); run 37048223696 | All checks green on 18bdee78: Build and Unit Tests (2,960 tests), KMP iOS kit smoke, Release bundle identity (G12 PASS on Wear, N/A on phone), Mockup Appearance Gate. Measured locally: 36 named-mutation runs RED then GREEN, every head gate green. An independent review found four gaps, fixed before opening (17e07a26, 95b7dc2c, 18bdee78). The review bot did not review: its usage limit was reached. Every commit GitHub-verified. The owner merges. |
| 2026-10-03 | PR-S #313 merged into `dev` | merge 31639c8c (head a23da290); runs 37048223696, 37051454031 | Merged by the owner at 08:25:00Z after every check was green on a23da290: Build and Unit Tests, KMP iOS kit smoke, Release bundle identity (run 37051454031), Mockup Appearance Gate (runs 37051453998, 37053159215). Review: the Codex bot did not run (usage limit); an independent review by Claude replaced it, with no blocker or major finding, its minor findings going to PR-S2. The owner accepted the §6.2 deviation (`.catch` before `.conflate()`); this specification's listing follows with PR-S2. |
| 2026-10-03 | §12.1 step 3: `android_build_unified.yml` on `dev` with `execute_unit_tests=true` | run 37109655585 on 31639c8c | Success: Build and Unit Tests, KMP iOS kit smoke, Release bundle identity. The run reports "All 2 960 tests pass" (3 275 runs, 462 files). |
| 2026-10-03 | §12.1 step 4: release 1.53.0, phone | cut run 37111834530; deploy run 37116431535; tag `release-v.1.53.0` → 805e4266; #314 → 6bf23319 | Cut from `dev` at 31639c8c (bump 805e4266, 1.53.0 / 56). Release PR #314 green (runs 37111847144, 37111847148). Deploy: bundle identity `RESULT PASS (12/12 checks ran on 1 bundle)`, G12 N/A; listing `RESULT OK: 13 items, no drift`; versionCode 56 uploaded to `production`, "Successfully finished the upload to Google Play" at 11:26:34Z. Tag pushed, #314 merged into `master` at 11:26:38Z. APK release run 37119666743, asset `store-release.apk`. No approval was asked: the `production` environment has no protection rules. |
| 2026-10-03 | §12.1 step 4: release 1.53.0, Wear | run 37116431535, job `deploy_wear` (111193229894; re-run 111202630071) | Bundle identity `RESULT PASS (12/12 checks ran on 1 bundle)`, G12 PASS (`PhoneChangeListenerService`); listing `RESULT OK: 1 items, no drift`; `DECISION UPLOAD` at 11:31:23Z, versionCode 1000056 to `wear:internal`, upload finished at 11:31:49Z. The job's re-run (attempt 2) ended in `DECISION SKIP` at 12:34:00Z: 1000056 already on `wear:internal`. |
| 2026-10-03 | Sync `master` → `dev` | #316 → 1fa564fe | Opened by run 37119669998 on 6bf23319; checks green on that head (runs 37119683167, 37119683162); merged by the owner at 12:26:42Z. Physical devices are unverified until the owner's §12.3 checklist. |
| 2026-10-04 | §12.3, owner, 1.53.0 | — | Reported by the owner: a phone change showed on the watch only after the owner's own action (reopening the app, or the screen turning off and on), never by itself; the other rows were not reported one by one. The installed watch version was not recorded; a watch on 1.52.2-wear behaves exactly so (§5.4). |
| 2026-10-06 | §10.4: paired-emulator run of the 1.53.0 code, by the implementer | no commit; tag `release-v.1.53.0` (805e4266), `dev` flavor release builds 56 and 1000056 | Phone `Pixel_9_Pro_XL` (API 36, Google Play image) and watch `Wear_OS_Large_Round` (API 36), paired through Android Studio's assistant with the Pixel Watch app; Play services 25.30.31 and 25.11.34; one connected peer on each side. From the phone tap to the watch's accepted snapshot: S1 894, 873 and 919 ms, of which the settle is 500 ms; S7 five taps, five signals 2.01–2.23 s apart, the watch on the right set 2.07 s after the last tap; S2 in ambient 860 ms, the watch dozing throughout; S3 with the watch's process killed (`am kill`): the listener started it, finish 1.51 s, start 1.08 s (ASM-6 holds on the emulator); S8 1.11 s. With a temporary, uncommitted log line per distinct key: start, complete a set, skip, return, add an exercise and finish each gave exactly one distinct key in each of three runs; editing a completed set, one key per step. Not physical evidence. |
| 2026-10-06 | Amendment after the PR #313 review: F29 to F31, D13 and D14, the PR-S2 tests and named mutations, §12.4 | da6f3695 | Direct push to `dev`. The copy's sha256 `8e792501…` equals the owner's file. GitHub verified the signature. |
| 2026-10-06 | Amendment for hotfix 1.53.1: F32, D15, the settle test, M-S45, §12.3 and §12.4, the §10.4 row | c4abab00 | Direct push to `dev`. The copy's sha256 `7203dca9…` equals the owner's file. GitHub verified the signature. |
| 2026-10-07 | §12.4 step 2: PR-S2, D13 to D15 and the PR-S2 tests | branch `fix/wear-live-sync-hardening`, head df67e4e9 on c4abab00: 7ab77ac1, df54f9d2, 158ee225, a817f1eb, 8e676deb, d5457ac7, ce09cb93, df67e4e9 | Every commit signed. The hotfix takes all but df67e4e9, the only one under `documentation/`. Per-commit gates, measured (daemon stopped, `--rerun-tasks --no-build-cache --no-daemon`): wear-bridge 92, 93 and 104 tests; wear-bridge and app 302; live-workout 213; Wear dev and store 636; lint-rules 138 and the storeRelease boundary 3; detekt clean on every touched module (`lint-rules` has no detekt task); the transport gate and its 53-case self-test at every commit. RED on the base: the settle test at 500 ms (exit 1) and the atomic-writes test (exit 1, six `updateStateImmediate` calls). Named mutations, 47 runs on df67e4e9, each RED on its named tests, restored byte-exact and GREEN: M-S32, M-S34 at 3 sites, M-S35 to M-S44 with M-S43 at 2 sites, M-S45 at 500 and 100; 29 earlier ones rerun (M-S6 added); M-SX1 supplementary for the rejection tests. Head gates on df67e4e9: assembleDebug (4 APKs), assembleDebugAndroidTest (39 APKs), verifyPaparazziDebug (13 modules, 456 golden cases, no snapshot changed), `:lint-rules:test` 138, detekt (56 tasks, 1 515 files), lintDebug (23 reports, 225 warnings, none in a changed file), testDebugUnitTest (3 086 passed, 65 skipped, 0 failed), the boundary test 3, both release bundles `RESULT PASS (12/12 checks ran on 1 bundle)` with Wear G12 PASS and the swap control exiting 1 on G5, the bundle self-test 70 cases, the transport gate; the mockup gate's rows 1 to 6, 9 and 10 PASS, rows 7 and 8 UNMEASURED locally (no headless browser). |
| 2026-10-07 | PR-S2 #320: independent review and its fixes | e5096f3d, after head 07a1a02c | A fresh reviewer read the diff against this specification alone: no blocker, no should-fix, two NITs, both reproduced and fixed (classification on #320). Correct-and-new: the guard's minimum-interval case now has a change waiting in the interval when the query fails; with PR-S's chain (`catch` before `conflate`) in place of the guard it fails (supplementary M-SX2). Correct: a test KDoc no longer explains that case through `catch`. Gate on e5096f3d: wear-bridge 104 tests, detekt 21 + 2 files, the transport gate. Mutations rerun on e5096f3d, measured: M-S1 to M-S4, M-S7, M-S8, M-S26, M-S27, M-S32, M-S44, M-S45 at both values, M-SX1 and M-SX2, each RED on its named tests and GREEN after the restore. CI on 07a1a02c: run 37624253690 (Build and Unit Tests, KMP iOS kit smoke, Release bundle identity) and run 37624253620 (Mockup Appearance Gate) succeeded. The review bot reported its Codex usage limit and did not review. |
| 2026-10-07 | PR-S2 #320 merged into `dev` | merge ecca15ec (head df8d0443) | Merged by the owner at 17:46:48Z with `--merge --match-head-commit df8d0443…`; the merge's second parent is df8d0443. Checks on that head: run 37629802875 (Build and Unit Tests, KMP iOS kit smoke, Release bundle identity; "All 2 991 tests pass") and run 37629869424 (Mockup Appearance Gate). Review: the independent review's two NITs were fixed in e5096f3d and closed; the review bot reported its Codex usage limit and did not review, so the merge was handed to the owner (A1). Hotfix 1.53.1 takes 7ab77ac1, df54f9d2, 158ee225, a817f1eb, 8e676deb, d5457ac7, ce09cb93 and e5096f3d. `dev` then also took #319 (38982c2e, `ui_tests.yml` and `ci-cd.md` only). |
| 2026-10-07 | §12.1 step 3 for PR-S2: `android_build_unified.yml` on `dev` with `execute_unit_tests=true` | run 37662002150 on 38982c2e | Success: Build and Unit Tests, KMP iOS kit smoke, Release bundle identity. The run reports "All 2 991 tests pass" (3 312 runs, 466 files). It ran on 38982c2e, the merge plus #319, whose tree differs from ecca15ec only in `.github/workflows/ui_tests.yml` and `documentation/ci-cd.md`. Its dispatch cancelled the push run 37661806448 of the same commit (the workflow's concurrency group). |
| 2026-10-09 | §12.4 step 3: hotfix 1.53.1 cut | cut run 37926248148; branch `release/release-v.1.53.1`, bump d827d112; #324 | Dispatched once with `mode=hotfix` after the owner's go, on `master` at 6bf23319 (1.53.0 / 56; no 1.53.1 branch, tag or PR into `master`). The bump `chore: bump to v.1.53.1 (code 57)` changes only `gradle/libs.versions.toml`, to 1.53.1 / 57; the workflow's bump commit is unsigned, as were those of 1.53.0, 1.52.2 and 1.52.1. #324 "Hotfix v.1.53.1" opened into `master`. `dev` had moved to fd5dc415 (#321 to #323), and `git merge-tree` against the rehearsal head then predicted a sync conflict in `CommonHandlerExternalWritesTest.kt`. |
| 2026-10-09 | §12.4 step 3: PR-S2's commits on the hotfix branch | 7ab77ac1 → 23b3dc5a, df54f9d2 → 38ca2d9f, 158ee225 → d83e5c12, a817f1eb → 494d657f, 8e676deb → e48be469, d5457ac7 → 8dc14c59, ce09cb93 → 3eec4f4c, e5096f3d → d229ac4d | `git cherry-pick -x`, signed, no conflict; every commit GitHub-verified; pushed as a fast-forward, d827d112..d229ac4d. The rehearsal came first, on a local branch from 6bf23319 that was never pushed (60165791, 5916e4fa, cd4f827d, c4246907, 35a0dd12, 6a50d08c, 4f28d212, 7f04cf66), with measured gates at every commit: wear-bridge 92, 93, 104 and 104 tests; wear-bridge and app 302; live-workout 213; Wear dev and store 636; lint-rules 138 and the storeRelease boundary 3; detekt on every touched module; the transport gate and its 53-case self-test. Each hotfix commit differs from its rehearsal commit only in `gradle/libs.versions.toml`, so that evidence carries over. The hotfix branch's diff from the bump equals PR-S2's diff outside `documentation/`, file by file without the hunk-header and `index` lines (18 files, 1 038 lines). Head gates on d229ac4d, measured: 1 289 tests (wear-bridge 104, app 198, live-workout 213, Wear dev 318 and store 318, lint-rules 138), the boundary 3, detekt (9 tasks, 348 files), the transport gate and its self-test. At the rehearsal head, the first root `testDebugUnitTest` run failed once, in `WearRotaryControllerTest` (`AppNotIdleException`, store flavor only, a file PR-S2 does not touch); its isolated rerun and the full measured rerun passed (3 171 results, 0 failures). That was reported to the owner before the go. CI on #324 at d229ac4d: PR Guard run 37927414717; run 37927414847, Build and Unit Tests ("All 2 991 tests pass", 2 994 runs in 379 files), KMP iOS kit smoke, and Release bundle identity, `RESULT PASS (12/12 checks ran on 1 bundle)` on both bundles with G12 PASS on Wear and N/A on phone. |
| 2026-10-09 | §12.4 step 3: release 1.53.1, phone | cut run 37926248148; deploy run 37931418561; tag `release-v.1.53.1` → d229ac4d; #324 → 543f7634 | Deploy dispatched with the default inputs, pinned to d229ac4d: guard, build (Build and Unit Tests with "All 2 991 tests pass", 3 312 runs in 466 files; KMP iOS kit smoke; Release bundle identity), Smoke UI Tests, then the phone job: bundle identity `RESULT PASS (12/12 checks ran on 1 bundle)`, G12 N/A; listing `RESULT OK: 13 items, no drift`; versionCode 57 uploaded to `production`, "Successfully finished the upload to Google Play" at 13:44:07Z. Tag pushed at 13:44:09Z; #324 merged into `master` at 13:44:11Z with `--match-head-commit` d229ac4d. APK release run 37938972760, asset `store-release.apk`. No approval was asked, and no recovery was used. |
| 2026-10-09 | §12.4 step 3: release 1.53.1, Wear | run 37931418561, job `deploy_wear` (113848297172; re-run 113850514463) | Bundle identity `RESULT PASS (12/12 checks ran on 1 bundle)`, G12 PASS (`PhoneChangeListenerService`); listing `RESULT OK: 1 items, no drift`; `DECISION UPLOAD` at 13:48:20Z (`wear:internal` held 1000056), versionCode 1000057 to `wear:internal`, upload finished at 13:48:54Z. The job's re-run (attempt 2, that job alone; the phone job kept its first attempt) ended in `DECISION SKIP` at 13:54:42Z: 1000057 already on `wear:internal`. |
| 2026-10-09 | Sync `master` → `dev` | #325, head 543f7634: open, conflicting | Opened by run 37938979345 on 543f7634. GitHub reports it conflicting in `CommonHandlerExternalWritesTest.kt`: three hunks where #321, on `dev` after PR-S2, dropped commas from the names of tests PR-S2 added; the `master` side holds that file exactly as `dev` had it before #321. Predicted at the cut and again after the cherry-picks, and left to the owner. Checks on 543f7634: the `master` push run 37938979322 succeeded (Build and Unit Tests with "All 2 991 tests pass", 3 312 runs in 466 files; KMP iOS kit smoke; Release bundle identity), so no dispatch was needed. Physical devices are unverified until the owner's §12.3 checklist on 1.53.1. |
| 2026-10-09 | Sync `master` → `dev` resolved | #325 → 7ba6b2f6 (head 0a89d1d8) | On the owner's authorization: a signed merge of `dev` (9c7068a0) into the sync branch at 543f7634, 0a89d1d8. Its one conflict, `CommonHandlerExternalWritesTest.kt`, takes `dev`'s whole file, byte for byte; the merge then differs from `dev` only in `gradle/libs.versions.toml`, 1.53.1 / 57. `:feature:live-workout:testDebugUnitTest`, measured: 213 tests, 0 failures. Checks on 0a89d1d8: run 37948107436 (Build and Unit Tests with "All 3 004 tests pass", 3 007 runs in 380 files; KMP iOS kit smoke; Release bundle identity) and run 37948107343 (Mockup Appearance Gate). Merged at 16:11:12Z with `--match-head-commit` 0a89d1d8; `dev` now holds 1.53.1 / 57, and its tree equals 0a89d1d8's. |

## 16. Sources

- [Wear OS paired transport](wear-paired-transport.md) (increment 5a), including its platform
  facts P1 to P4 and §16 sources.
- [Wear OS Phase 1](wear-phase-1-active-workout-tile.md) §3 (unsolicited snapshots) and §4 (the
  current-set rule).
- [release-flow.md](../release-flow.md), [ci-cd.md](../ci-cd.md).

## Appendix A. `documentation/product.md` (D8)

Base `sha256` at `b1945ee1`: `06080084da368f908f80ebbd646f13339495b8079ee0475cf89e936f07494b32`.
The OLD block occurs exactly once (lines 281 to 284); replacing it with the NEW block gives
`sha256` `e1fca7daa689982e16fd2b97e88d7163759dd3dc537b9ee4fbc58766ad6aa33a`. Each block is the
lines strictly between its fence lines, joined with "\n", plus one final "\n".

**OLD**

```text
  workout snapshot and completion commands between the user's phone and watch
  and retain the bounded watch cache defined by its specification. The owner
  authorized Google-owned end-to-end encrypted Data Layer relay transit for it
  on 2026-09-29, together with its public disclosure
```

**NEW**

```text
  workout snapshot and completion commands between the user's phone and watch,
  let the phone send the watch a content-free change signal so that the watch
  asks again ([Wear live sync](feature-specs/wear-live-sync.md)), and retain
  the bounded watch cache defined by its specification. The owner authorized
  Google-owned end-to-end encrypted Data Layer relay transit for it on
  2026-09-29, together with its public disclosure
```
