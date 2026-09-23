# Wear ambient and ongoing lifecycle

**Status:** the authorized synthetic runtime/ongoing stage has executed host acceptance
in §6: 110 intended negative controls, 100 protected identities and fresh restored/repository
gates. Release remains read-only. Physical acceptance, calibrated device constants and
real workout transfer/acknowledgement remain open; the privacy gate is unchanged.

[Wear UI completion](wear-ui-completion.md) owns the delivery sequence and visibility
criteria. [Phase 1 §8](wear-phase-1-active-workout-tile.md#8-lifecycle-and-ongoing-surface)
owns lifecycle policy and physical acceptance. Its privacy/transport gate remains closed.

## 1. One process owner and the release boundary

Activity and Tile resolve the same `WatchRuntimeFactory` instance. `WatchRuntimeOwner`
serializes reducer transitions, canonical cache replacement, draft overlays, ongoing
effects and surface publication behind one lock. Consumers observe its read-only surface
and ongoing-status flows; they do not observe the reducer candidate during persistence.

Only the debug factory constructs this mutable owner, `DebugSnapshotDriver`, the Android
notification adapter and an explicitly uncalibrated `OngoingPolicy`. Synthetic scenario
commands enter through a new explicit debug intent. Activity recreation and notification
permission results do not replay a scenario. Valid fixtures use correlated protocol
snapshots; fixtures that cannot represent a valid canonical protocol payload remain
nonmutating static previews. No Data Layer sender/listener or real phone payload is wired.

The release factory returns one read-only facade over `WatchProcessState`, rejects all
UI mutations and synthetic scenarios, reports no ongoing lifecycle, and selects no
reconnect policy. The release source-set test must establish that `DebugSnapshotDriver`
is absent from that classpath. A debug-only branch in a shared test is insufficient.

A completion action obtains an existing reducer request token and fingerprint, then
enters command-in-flight state. It does not change the completed-set ordinal, fabricate
`Applied`, or produce a success haptic. Command-response lifecycle renewal is not wired:
the reducer's existing Unit-returning response API first needs a typed accepted-publication
result. No UI inference may substitute for phone acknowledgement or snapshot admission.

## 2. Publication, drafts and recovery

Accepted canonical snapshots and their framing metadata are committed atomically before
the candidate surface is published. Draft edits stay in memory, preserve explicit null
weight, and are keyed to database epoch, session identity/revision and target. A compatible
fresh handshake preserves the draft. A changed source/target, terminal state, or matching
canonical values clears it. Cache payloads never contain the draft overlay.

`restoreDisplayOnly` initializes a pristine reducer only after the shared guarded cache
reader accepts the record. It strips any grant defensively, initializes source/revision
admission metadata, installs retired authority, and clears draft/command state. This does
not relax unsolicited-response admission and does not manufacture a network request.
The retained process owner separately reconciles its in-memory draft against the recovered
snapshot: database epoch, session identity/revision and target key must match, and the
canonical reps/weight must not already equal the draft. Genuine absent, invalid or expired
cache, NoSession and other terminal payloads clear the overlay. A new process starts without
an in-memory overlay and restores canonical display only; unsent edits are never persisted.

Cache and platform operations may consume the remaining mutation window. The owner checks
expiry around publication and actions. Publication resolves the final platform ongoing
status and publishes that status before its final authority-expiry check and any required
surface rebuild. If the lookup consumes the remaining authority interval, wake and Tile
reads return read-only values synchronously; editor restoration cannot rely on a later
scheduled callback to revoke expired editing. This is a publication-order guarantee, not
an atomic clock snapshot against arbitrarily advancing concurrent observers.

The single one-shot deadline job chooses the next authority, ongoing or known cache
boundary; an overdue boundary, including cache TTL reached during publication, remains
an immediate callback rather than being discarded. This job is not a ticker, an alarm
or the mechanism for notification removal after process death.

Each guarded event snapshots the already-published pending draft at entry. On Exception,
the owner rolls that draft back before publishing the previous surface's values with
editing, completion and retry disabled and clearing the retention claim. A failed edit
must not appear during later recovery, including when the prior weight was explicitly null.
The failed canonical successor is not exposed. The owner removes the pending job and latches
recovery. Before the next event it creates fresh reducer/cache/coordinator instances and
reads the durable record again: an atomic write may have succeeded before throwing, leaving
old in-memory metadata unsafe to reuse.

Compatible unsent values and their marker survive recovery in that same retained owner,
including repeated restoration failures, without restoring mutation authority. A guarded
cache read returning `Absent(IO_FAILURE)` is retried as a failure rather than treated as
empty storage: keep the previous disabled projection, entry draft and recovery latch until
a later event can read again. Successful recovery applies the reconciliation above.
An initial restore failure retains this same safe owner for later recovery instead of
escaping the constructor. None of this stores drafts across process death. UI boundaries
contain `Exception` once without replaying the event; fatal `Error` propagates. System Back
and user exit have no runtime precondition.

Live display TTL uses its retained monotonic receive baseline. Invalid/expired records
pass through the same guarded reader, which rejects them before payload decoding; wall
clock never grants authority or retention. A restored payload-free `NoSession` tombstone
exposes no receive timestamp, so its access-time TTL is rechecked on wake. This preserves
Phase 1's access-time guarantee without promising physical deletion while the process sleeps.

## 3. Ambient presentation

`AndroidWearAmbientProvider` adapts `AmbientLifecycleObserver` to an injectable
`WearAmbientProvider`; `WearAmbientController` contains event handling independently of
Android. Entry, system update and exit synchronously refresh runtime freshness before
publishing the ambient transition. The display clock uses wall time sampled on those
events; mutation and retention deadlines use elapsed realtime. Ambient creates no authority.

The ambient branch replaces the entire interactive subtree with `WearAmbientSummary`.
Controller/editor click handlers, BackHandler and rotary focus handlers are absent from
that branch. A saveable-state holder retains the controller scroll/editor state, while
the owner retains the draft. On wake, the editor returns only if the current model permits
editing that field; otherwise it closes and controller focus returns normally. Activity
`setContent` runs once, and resume/new intent refresh the model without resetting that tree.

The summary paints time, short context, exact progress, reps, applicable weight, status and
an explicit unsent-value marker. Only context may ellipsize; the spoken description keeps
the full context and progress. White text is painted on black, low-bit mode disables text
antialiasing/subpixel rendering/dithering, and burn-in protection shifts content within a
bounded two-pixel offset when system events arrive. No per-second loop, animation, acquired
app wake lock or foreground service is added. The library-required ambient manifest
permission is distinct from acquiring a custom freshness wake lock.

The host contract checks at least 85% black pixels, low-bit output, safe painted bounds,
EN/RU at 192/240dp and font scales 1.0/1.24, and actual numeric-row raster against an
independently derived expected string. A nonblank image or semantics-only oracle cannot
prove that the numeric row was painted. Both editors, retained scroll, root-delivered rotary
and authority loss have transition tests. Host transitions do not prove physical ambient
callbacks, Activity recreation or target-watch behavior. The low-power requirements follow
[Android's ambient guidance](https://developer.android.com/training/wearables/always-on).

## 4. Ongoing deadline and crash ordering

`WatchOngoingCoordinator` receives the accepted reduction, resulting reducer state,
canonical response, elapsed receive time and boot count. A wire grant alone is insufficient:
active/fresh display, lease/source/target/deadline agreement and a positive unexpired
admitted window must all match. Policy is injected and has no production default.

One absolute elapsed-realtime stop deadline is stored in the atomic cache header and the
system notification's extras. Each post/update computes its remaining interval again;
zero or negative remainder cancels. Disconnect can only shorten the deadline. Repeated
updates, reconnect callbacks, cache reads, permission changes and restart cannot renew it.

| Transition | Required order and crash boundary |
| --- | --- |
| New or extended fresh lifecycle | Persist complete canonical record/header, then post; storage time is deducted before the post |
| Fresh grant whose deadline is earlier than an existing notification | Shorten that existing system notification first, persist the new record, then perform the fresh post; preflight denial/missing result permits no fresh post in that call |
| Disconnect with a live lifecycle | Shorten/update the system notification first; if expired, denied or missing, cancel first. Then atomically persist DISCONNECTED with the shortened or cleared deadline; preserve payload and receive baseline |
| Disconnect without an ongoing lifecycle | Update existing admitted cache connection to DISCONNECTED with no deadline, without posting or cancelling; never create a cache record merely from disconnect |
| Terminal state | Cancel before exposing terminal state; an accepted NoSession must durably replace the old active record with its tombstone |
| Ordinary update | Replace the same notification ID only if it exists; clamp against its existing absolute deadline and recompute remaining time |
| Process restore | Use min(cache deadline, surviving system deadline); never post or reinstall cached mutation authority |
| Expiry or denied permission | Cancel, clear lifecycle/header, expose no scheduled-retention claim |

The platform-first shortening rule includes a fresh grant shortened by its effective
mutation window. Persisting that earlier header first could leave an older, longer system
timeout after a crash. The inverse cut also matters: if platform shortening succeeds but
the cache write fails, restoration must clamp to the shorter surviving platform deadline.
Connection persistence does not depend on a notification being present or permitted.
The coordinator keeps the last successfully published cache receive baseline, or the
baseline of a validated display-only restore, separately from ongoing lifecycle state.
A disconnect timestamp in the future is rejected; one predating that baseline is ignored.
Without an admitted baseline, disconnect creates no record. Metadata updates preserve the
canonical payload, receive time, boot count and effective mutation window exactly; they
cannot renew authority, cache age or retention.

The cache API only shortens/removes a deadline; only fresh snapshot replacement may install
or extend one. The same adapter handles the cache reader's expiry cancellation.

`AndroidOngoingNotification` uses pinned `wear-ongoing:1.1.0`, rebuilds the notification
with `OngoingActivity.apply`, and does not reuse `OngoingActivity.update` with an old relative
timeout. Constructor and access checks do not post. The LOW channel, public EN/RU copy and
static icon contain no workout payload. An immutable PendingIntent targets MainActivity
with `SINGLE_TOP` for the app's single Activity; there are no notification mutation actions.
`CLEAR_TOP` and `NEW_TASK` are omitted to preserve Wear Recents behavior. The Wear-only
Fragment constraint selects 1.3.6 instead of the ambient library's transitive 1.2.4,
providing the Activity Result permission API's required compatibility without changing
the pinned Wear Compose or ambient versions.

The adapter checks presence and clamps again after construction. Android offers no atomic
compare-and-update-if-present API: system removal between the final presence lookup and
`notify` can race with replacement. The absolute deadline still bounds that replacement.
Host absence tests must not be presented as proof that this concurrent OS race is impossible.

## 5. Notification denial and recovery UI

Access checks distinguish API 33+ POST_NOTIFICATIONS permission, application notification
enablement and channel importance. Denial leaves otherwise valid ordinary editing and
completion available. Scrollable details explain that disabled notifications can leave no
workout shortcut and that the watch may return to its watch face; the user can continue
in the current screen. Only its user-operated action requests permission or opens settings.
A saved requested-before flag supports permanent-denial routing across Activity recreation.

Permission results and return from settings refresh runtime/access only. They do not
issue a handshake, replay a fixture, post a notification or extend authority. If permission
returns with no lifecycle, status becomes Inactive rather than sticky PermissionDenied;
a separate inactive-shortcut notice explains the possible return to the watch face and
invites the user to continue in the current screen. A later newly accepted fresh response
may start a lifecycle. Neither notice promises foreground retention.

## 6. Executed host evidence

The current integrated synthetic runtime/ongoing candidate passed the complete serial
cohort below. Full-suite counts come from archived XML, with zero failures/errors/skips.
**110 named controls** each killed an intended assertion and restored source bytes exactly.
The exact-method map covers **100/100 concrete identities** across **85 distinct declared
protective methods, including the extended G9 check**. Shared API28/API33 and EN/RU
contracts expand into concrete identities; 99 occur in each debug flavor and one belongs
to the release source set. No incidental failing method earns coverage.
Five existing owner methods and five coordinator methods were strengthened without
adding identities. Their additional 13 controls cover compatible same-process drafts,
failed-edit rollback, cache-read I/O retries and DISCONNECTED persistence without an
ongoing lifecycle. Restored draft values stay read-only; no draft is persisted across
process death, and connection updates cannot renew cache age or retention.
One further ordering control protects the renamed existing synchronous-wake method: a
final platform lookup that crosses the authority deadline must return disabled controls
before any scheduled callback runs. It adds no test identity or physical timing claim.
Three additional AtomicFile controls protect two shared methods on API28 and API33,
adding four concrete identities. A valid framed cache with only its supported legacy
`.bak` file present restores canonical values read-only into a new runtime owner, drops
the previous owner's unsent draft, and preserves the surviving notification's absolute
deadline without a restore post/update. A subsequent wake recomputes the remaining
notification timeout. The other shared method distinguishes true absence from an
existing unreadable record: absence returns null, while I/O failure remains retryable
`IO_FAILURE`. These host cases exercise real AtomicFile storage and the notification
adapter under Robolectric. They do not measure physical process death, OEM retention,
or imply that every modern platform write normally uses the legacy backup path.
One additional owner control strengthens the existing completion method without adding
an identity: local edits are marked unsent before submission, while their matching open
command retains the exact submitted values and suppresses the marker afterward. The
owner preserves the mapper's submission-aware result rather than replacing it merely
because its in-memory overlay remains present. Completion still enters in-flight state,
preserves the fingerprint, rejects a duplicate issue and fabricates no acknowledgement.

| Executed stage | Archived XML scope | Executed task summary |
| --- | --- | --- |
| `baseline-analysis` | build/static checks | `100 actionable tasks: 100 executed` |
| `baseline-debug` | app/wear:testDevDebugUnitTest: 196 tests / 54 classes; app/wear:testStoreDebugUnitTest: 196 tests / 54 classes | `68 actionable tasks: 68 executed` |
| `baseline-release` | app/wear:testDevReleaseUnitTest: 1 tests / 1 classes | `38 actionable tasks: 38 executed` |
| `restored-analysis` | build/static checks | `100 actionable tasks: 100 executed` |
| `restored-debug` | app/wear:testDevDebugUnitTest: 196 tests / 54 classes; app/wear:testStoreDebugUnitTest: 196 tests / 54 classes | `68 actionable tasks: 68 executed` |
| `restored-release` | app/wear:testDevReleaseUnitTest: 1 tests / 1 classes | `38 actionable tasks: 38 executed` |
| `root-commit` | app/wear:testDevDebugUnitTest: 196 tests / 54 classes; app/wear:testStoreDebugUnitTest: 196 tests / 54 classes | `2331 actionable tasks: 2331 executed` |
| `root-phase` | lint-rules:test: 138 tests / 15 classes | `2400 actionable tasks: 2400 executed` |

The release baseline/restoration executes only
`ReleaseRuntimeBoundaryTest.releaseRejectsSyntheticEventsAndExcludesTheirSourceClass()`
in `testDevReleaseUnitTest`. It proves the read-only factory's rejection and absence of
the synthetic source class from that host classpath. It is not a release-device UI test.
Explicit release host commands and the two release controls include command-local
`-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`; global variant configuration
is unchanged. `assembleStoreRelease` remains a separate packaging/build result.

Fresh phase-exit Paparazzi XML, in addition to lint-rules XML, records every raw
testcase occurrence. Legacy parameterized display labels may repeat and are not treated
as unique method identities. Receipts retain the raw XML path/hash, one-based testcase
ordinal, class, unmodified display label/time and counters. Wear, lint-rules and intended
mutation-method identity checks remain strict.

The Paparazzi occurrence totals are:

| Module | Zero-failure XML |
| --- | --- |
| `core/ui/kit` | 498 tests / 43 classes |
| `core/ui/plan-editor` | 37 tests / 4 classes |
| `core/ui/start-mode` | 3 tests / 2 classes |
| `feature/all-exercises` | 118 tests / 11 classes |
| `feature/all-trainings` | 99 tests / 10 classes |
| `feature/archive` | 39 tests / 6 classes |
| `feature/exercise` | 168 tests / 13 classes |
| `feature/exercise-chart` | 106 tests / 10 classes |
| `feature/home` | 113 tests / 14 classes |
| `feature/live-workout` | 252 tests / 26 classes |
| `feature/past-session` | 71 tests / 8 classes |
| `feature/settings` | 132 tests / 11 classes |
| `feature/single-training` | 70 tests / 6 classes |

The following table is derived from saved exact intended failures, not a prepared mapping.
Mutation task summaries observed: `37 actionable tasks: 37 executed`, `38 actionable tasks: 38 executed`.

| Executed negative control | Observed intended failing method |
| --- | --- |
| `fresh-post-before-cache` | WatchOngoingCoordinatorTest.failed fresh cache publication cannot expose a new notification()<br>WatchOngoingCoordinatorTest.fresh admitted active snapshot persists its deadline before posting() |
| `rejected-admission-published` | WatchOngoingCoordinatorTest.rejected reduction cannot start or replace a lifecycle() |
| `fresh-wire-grant-without-local-authority` | WatchOngoingCoordinatorTest.wire grant without fresh admitted authority never starts() |
| `disconnect-keeps-longer-deadline` | WatchOngoingCoordinatorTest.disconnect shortens notification before atomically changing its cache header()<br>WatchOngoingCoordinatorTest.repeated disconnect refresh and connected silence never extend the deadline() |
| `refresh-reuses-original-interval` | WatchOngoingCoordinatorTest.freshness expiry retains only the original bounded grace interval()<br>WatchOngoingCoordinatorTest.repeated disconnect refresh and connected silence never extend the deadline()<br>WatchOngoingCoordinatorTest.surviving notification refresh after restart uses only remaining interval() |
| `terminal-display-does-not-cancel` | WatchOngoingCoordinatorTest.all non-active display states immediately cancel the existing notification() |
| `restart-posts-missing-notification` | WatchOngoingCoordinatorTest.restart restores read-only cache and never creates a missing notification() |
| `post-permission-denial-not-handled` | WatchOngoingCoordinatorTest.permission revoked during posting clears the already-persisted deadline() |
| `expired-refresh-does-not-clear-lifecycle` | WatchOngoingCoordinatorTest.freshness expiry retains only the original bounded grace interval() |
| `restore-trusts-later-cache-deadline` | WatchOngoingCoordinatorTest.failed shortened cache publication cannot extend the surviving system timeout on restart() |
| `cache-header-allows-extension` | WatchOngoingCoordinatorTest.cache header API refuses extension or installation from a display-only record() |
| `fresh-post-ignores-cache-latency` | WatchOngoingCoordinatorTest.time spent publishing cache is deducted before the system timeout is set() |
| `stale-display-renews-ongoing` | WatchOngoingCoordinatorTest.stale display with a granted wire payload cannot renew ongoing() |
| `expired-admission-still-starts-grace` | WatchOngoingCoordinatorTest.unavailable and already expired snapshots persist without ongoing deadline() |
| `old-disconnect-shortens-successor` | WatchOngoingCoordinatorTest.disconnect predating a fresh snapshot cannot shorten the new lifecycle() |
| `fresh-successor-cannot-renew` | WatchOngoingCoordinatorTest.fresh correlated successor can start a new lifecycle after grace() |
| `expired-status-still-claims-retention` | WatchOngoingCoordinatorTest.system timeout expires without a coordinator tick() |
| `no-session-keeps-protocol-payload` | WatchOngoingCoordinatorTest.terminal no-session cancels before publishing a payload-free tombstone() |
| `denial-status-hidden-as-inactive` | WatchOngoingCoordinatorTest.permission denial preserves ordinary cache but does not claim ongoing retention() |
| `cache-restores-wire-authority` | WatchOngoingCoordinatorTest.restart restores read-only cache and never creates a missing notification() |
| `refresh-creates-missing-notification` | WatchOngoingCoordinatorTest.missing notification during update is never recreated() |
| `disconnect-cache-written-before-system` | WatchOngoingCoordinatorTest.disconnect shortens notification before atomically changing its cache header()<br>WatchOngoingCoordinatorTest.failed shortened cache publication cannot extend the surviving system timeout on restart() |
| `shorter-fresh-grant-persisted-before-platform-shortening` | WatchOngoingCoordinatorTest.shorter fresh grant bounds the previous notification before cache publication()<br>WatchOngoingCoordinatorTest.shorter fresh grant stays bounded through either cache publication crash cut() |
| `shorter-fresh-grant-ignores-preflight-denial` | WatchOngoingCoordinatorTest.shorter fresh grant preflight denial clears retention without posting() |
| `restored-permission-keeps-sticky-denial` | WatchOngoingCoordinatorTest.restored permission clears denial status without posting or renewing() |
| `shorter-fresh-grant-ignores-preflight-removal` | WatchOngoingCoordinatorTest.shorter fresh grant cannot recreate a notification removed during preflight() |
| `android-constructor-posts-implicitly` | AndroidOngoingNotificationApi28Test.constructionAndPermissionInspectionDoNotPost()<br>AndroidOngoingNotificationApi33Test.constructionAndPermissionInspectionDoNotPost() |
| `android-post-resets-relative-timeout` | AndroidOngoingNotificationApi28Test.everyPublicationRecalculatesTheRealSystemTimeout()<br>AndroidOngoingNotificationApi33Test.everyPublicationRecalculatesTheRealSystemTimeout() |
| `android-update-extends-surviving-deadline` | AndroidOngoingNotificationApi28Test.recoveredAdapterClampsAnUpdateToTheSurvivingShorterDeadline()<br>AndroidOngoingNotificationApi33Test.recoveredAdapterClampsAnUpdateToTheSurvivingShorterDeadline() |
| `android-update-recreates-missing-id` | AndroidOngoingNotificationApi28Test.updatesDoNotCreateOrResurrectAMissingNotification()<br>AndroidOngoingNotificationApi33Test.updatesDoNotCreateOrResurrectAMissingNotification() |
| `android-posts-zero-unlimited-timeout` | AndroidOngoingNotificationApi28Test.expiredDeadlineCancelsInsteadOfPostingAnUnlimitedTimeout()<br>AndroidOngoingNotificationApi33Test.expiredDeadlineCancelsInsteadOfPostingAnUnlimitedTimeout() |
| `android-recovers-from-relative-timeout` | AndroidOngoingNotificationApi28Test.missingAbsoluteDeadlineCannotBeRecoveredFromRelativeTimeout()<br>AndroidOngoingNotificationApi33Test.missingAbsoluteDeadlineCannotBeRecoveredFromRelativeTimeout() |
| `android-ignores-app-notification-block` | AndroidOngoingNotificationApi28Test.blockedApplicationCancelsAndReportsPermissionDenial()<br>AndroidOngoingNotificationApi33Test.blockedApplicationCancelsAndReportsPermissionDenial() |
| `android-ignores-blocked-channel` | AndroidOngoingNotificationApi28Test.blockedChannelIsPreservedAndCannotClaimRetention()<br>AndroidOngoingNotificationApi33Test.blockedChannelIsPreservedAndCannotClaimRetention() |
| `android-ignores-runtime-permission-status` | AndroidOngoingNotificationApi33Test.runtimePermissionIsRequiredOnlyOnApi33AndLater() |
| `android-cancel-removes-unrelated-notification` | AndroidOngoingNotificationApi28Test.cancellationRemovesOnlyTheCoordinatorsNotification()<br>AndroidOngoingNotificationApi33Test.cancellationRemovesOnlyTheCoordinatorsNotification() |
| `android-reentry-loses-single-top` | AndroidOngoingNotificationApi28Test.workoutNotificationHasAnImmutableReentryIntentAndNoMutationActions()<br>AndroidOngoingNotificationApi33Test.workoutNotificationHasAnImmutableReentryIntentAndNoMutationActions() |
| `android-ongoing-metadata-omitted` | AndroidOngoingNotificationApi28Test.workoutNotificationHasAnImmutableReentryIntentAndNoMutationActions()<br>AndroidOngoingNotificationApi33Test.workoutNotificationHasAnImmutableReentryIntentAndNoMutationActions() |
| `runtime-publish-before-durable` | WatchRuntimeOwnerTest.admittedSnapshotIsDurableAndNotifiedBeforeSurfacePublication() |
| `runtime-skip-post-write-expiry` | WatchRuntimeOwnerTest.diskWriteConsumingMutationWindowCannotPublishFreshControls() |
| `runtime-null-overlay-fallback` | WatchRuntimeOwnerTest.draftsAreMemoryOnlyAndExplicitNullWeightIsPreserved() |
| `runtime-clear-compatible-draft` | WatchRuntimeOwnerTest.compatibleHandshakePreservesDraftButSourceChangeClearsIt() |
| `runtime-retain-obsolete-draft` | WatchRuntimeOwnerTest.compatibleHandshakePreservesDraftButSourceChangeClearsIt() |
| `runtime-complete-without-binding` | DebugSnapshotDriverTest.completeSetHasNoFakeSuccessAndTerminalIsAnExplicitScenario() |
| `runtime-skip-failed-instance-recovery` | WatchRuntimeOwnerTest.writeFailureAfterAtomicPublicationRecoversDurableSuccessorReadOnly() |
| `runtime-enable-readonly-ui` | WatchRuntimeOwnerTest.processRestoreIsCanonicalReadOnlyAndDoesNotRepostOngoing() |
| `runtime-hide-notification-denial` | WatchRuntimeOwnerTest.notificationDenialIsPublishedWithoutRemovingValidMutationAuthority() |
| `runtime-drop-deadline-job` | WatchRuntimeOwnerTest.deadlineCallbackExpiresAuthorityAndThenStopsOngoingWithoutPolling() |
| `runtime-admit-unregistered-response` | WatchRuntimeOwnerTest.rejectedCorrelationCannotWriteCacheOrOngoing() |
| `runtime-retain-ttl-expired-display` | WatchRuntimeOwnerTest.liveDisplayCacheTtlClearsVisibleValuesBeforeWakeReturns() |
| `debug-driver-correlation-lost` | DebugSnapshotDriverTest.fixtureUsesCorrelatedAdmissionAndKeepsCanonicalCache() |
| `debug-driver-command-auto-terminal` | DebugSnapshotDriverTest.commandFixtureRemainsInFlightUntilExplicitTerminal() |
| `debug-driver-ignore-terminal-event` | DebugSnapshotDriverTest.completeSetHasNoFakeSuccessAndTerminalIsAnExplicitScenario() |
| `debug-driver-expiry-clock-stationary` | DebugSnapshotDriverTest.explicitExpiryAndRefreshUseOneInjectedClock() |
| `debug-driver-coerce-invalid-fixture` | DebugSnapshotDriverTest.outOfProtocolRangeFixtureIsNotForgedIntoCanonicalSnapshot() |
| `debug-driver-drop-phone-action` | DebugSnapshotDriverTest.readOnlyProtocolFixturesUseTheSameOwner() |
| `release-accept-synthetic-entry` | ReleaseRuntimeBoundaryTest.releaseRejectsSyntheticEventsAndExcludesTheirSourceClass() |
| `release-include-debug-source-marker` | ReleaseRuntimeBoundaryTest.releaseRejectsSyntheticEventsAndExcludesTheirSourceClass() |
| `runtime-drop-overdue-boundaries` | WatchRuntimeOwnerTest.publicationCrossingCacheTtlSchedulesImmediateInvalidation() |
| `runtime-failure-keeps-editable-ui` | WatchRuntimeOwnerTest.disconnectWriteFailurePublishesOnlyPreviousValuesAsReadOnly()<br>WatchRuntimeOwnerTest.expiryPlatformFailureCannotLeaveThePreviousEditorEnabled() |
| `runtime-failure-keeps-retention-claim` | WatchRuntimeOwnerTest.disconnectWriteFailurePublishesOnlyPreviousValuesAsReadOnly() |
| `runtime-failure-exposes-unpublished-successor` | WatchRuntimeOwnerTest.writeFailureAfterAtomicPublicationRecoversDurableSuccessorReadOnly() |
| `runtime-constructor-propagates-restore-failure` | WatchRuntimeOwnerTest.initialRestoreFailureRetainsOneRecoverableReadOnlyOwner() |
| `runtime-cache-restore-uses-unsolicited` | WatchRuntimeOwnerTest.processRestoreIsCanonicalReadOnlyAndDoesNotRepostOngoing() |
| `runtime-cache-restore-omits-admission-metadata` | WatchWorkoutReducerCacheRestoreTest.restoredSourceMetadataRejectsOlderRevisions() |
| `runtime-cache-restore-retains-payload-grant` | WatchWorkoutReducerCacheRestoreTest.restoredGrantIsStrippedAndCannotIssueACommand() |
| `runtime-cache-restore-overwrites-live-state` | WatchWorkoutReducerCacheRestoreTest.cacheRestoreCannotReplaceAnIssuedOrAlreadyRestoredReducer() |
| `ongoing-denial-notice-hidden` | WearOngoingNoticeRuTest.deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction()<br>WearOngoingNoticeTest.deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction() |
| `ongoing-enable-callback-dropped` | WearOngoingNoticeRuTest.deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction()<br>WearOngoingNoticeTest.deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction() |
| `ongoing-grant-still-shows-permission-denial` | WearOngoingNoticeRuTest.deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction()<br>WearOngoingNoticeTest.deniedNotificationKeepsControllerUsableAndRequiresExplicitEnableAction() |
| `ongoing-channel-blockage-ignored` | AndroidWearNotificationAccessApi28Test.accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting()<br>AndroidWearNotificationAccessApi33Test.accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting()<br>WearNotificationAccessTest.blockedApplicationOrChannelUsesSettingsEvenWhenRuntimePermissionIsGranted() |
| `ongoing-permanent-denial-requests-again` | AndroidWearNotificationAccessApi33Test.accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting()<br>WearNotificationAccessTest.firstPermissionRequestAndRationaleAreUserActionsButPermanentDenialUsesSettings() |
| `ongoing-api28-demands-runtime-permission` | AndroidWearNotificationAccessApi28Test.accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting() |
| `ongoing-settings-target-wrong-package` | AndroidWearNotificationAccessApi28Test.accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting()<br>AndroidWearNotificationAccessApi33Test.accessInspectionUsesPlatformVersionAndChannelStateWithoutPosting() |
| `ongoing-g9-missing-title` | WearStringCoverageGateTest.every Wear string resource is rendered by at least one fixture |
| `ongoing-g9-missing-content` | WearStringCoverageGateTest.every Wear string resource is rendered by at least one fixture |
| `ongoing-g9-missing-channel` | WearStringCoverageGateTest.every Wear string resource is rendered by at least one fixture |
| `ongoing-g9-denial-copy-unrendered` | WearStringCoverageGateTest.every Wear string resource is rendered by at least one fixture |
| `ongoing-ui-event-failure-escapes` | WearRuntimeUiEventTest.failedRuntimeEventIsContainedWithoutAutomaticRetry() |
| `ongoing-ui-event-retried-automatically` | WearRuntimeUiEventTest.failedRuntimeEventIsContainedWithoutAutomaticRetry()<br>WearRuntimeUiEventTest.fatalErrorPropagatesWithoutAutomaticRetry()<br>WearRuntimeUiEventTest.successfulEventResultIsPreserved() |
| `ongoing-ui-event-swallows-fatal-error` | WearRuntimeUiEventTest.fatalErrorPropagatesWithoutAutomaticRetry() |
| `runtime-completion-loses-explicit-null-draft` | WatchRuntimeOwnerTest.completionIssuesFingerprintAndInFlightWithoutInventingAcknowledgement() |
| `runtime-exact-lease-deadline-remains-editable` | WatchRuntimeOwnerTest.exactLeaseBoundaryRejectsActionAndKeepsUnsubmittedValues() |
| `runtime-disconnect-skips-platform-clamp` | WatchRuntimeOwnerTest.disconnectClampsOngoingBeforePublishingReadOnlyDraft() |
| `runtime-canonical-match-keeps-draft-marker` | WatchRuntimeOwnerTest.canonicalValuesMatchingDraftClearItsUnsubmittedSignal() |
| `runtime-premature-publication-terminal-and-failure-cuts` | WatchRuntimeOwnerTest.notificationFailureAfterPostRequiresRecoveryAndNeverPublishesFreshCandidate()<br>WatchRuntimeOwnerTest.terminalSnapshotCancelsAndPersistsBeforePublication()<br>WatchRuntimeOwnerTest.writeFailureBeforeAtomicPublicationDoesNotPublishCandidate() |
| `runtime-restored-tombstone-never-expires` | WatchRuntimeOwnerTest.noSessionTombstoneRestoresWithoutInventedIdentityAndExpiresOnWake() |
| `runtime-restore-accepts-foreign-boot-cache` | WatchRuntimeOwnerTest.bootMismatchDropsCachedDisplayAndLease() |
| `runtime-locale-change-ignored` | WatchRuntimeOwnerTest.localeChangeRebuildsFormattedValuesWithoutLosingDraft() |
| `runtime-restored-display-never-reauthorizes` | WatchWorkoutReducerCacheRestoreTest.onlyACorrelatedFreshHandshakeCanAuthorizeRestoredValues() |
| `runtime-unsolicited-empty-source-admitted` | WatchWorkoutReducerCacheRestoreTest.unsolicitedAdmissionStillRejectsAnEmptyReducerAndForeignRestoredSource() |
| `android-api28-incorrectly-requires-runtime-permission` | AndroidOngoingNotificationApi28Test.runtimePermissionIsRequiredOnlyOnApi33AndLater() |
| `runtime-recovery-drops-compatible-draft` | WatchRuntimeOwnerTest.disconnectWriteFailurePublishesOnlyPreviousValuesAsReadOnly()<br>WatchRuntimeOwnerTest.expiryPlatformFailureCannotLeaveThePreviousEditorEnabled() |
| `runtime-failed-action-publishes-uncommitted-draft` | WatchRuntimeOwnerTest.notificationFailureAfterPostRequiresRecoveryAndNeverPublishesFreshCandidate() |
| `runtime-recovery-skips-durable-draft-reconciliation` | WatchRuntimeOwnerTest.writeFailureAfterAtomicPublicationRecoversDurableSuccessorReadOnly() |
| `runtime-recovery-treats-read-io-failure-as-empty` | WatchRuntimeOwnerTest.expiryPlatformFailureCannotLeaveThePreviousEditorEnabled() |
| `disconnect-without-lifecycle-drops-cache-connection` | WatchOngoingCoordinatorTest.freshness expiry retains only the original bounded grace interval()<br>WatchOngoingCoordinatorTest.missing notification during update is never recreated()<br>WatchOngoingCoordinatorTest.permission denial preserves ordinary cache but does not claim ongoing retention() |
| `disconnect-accepted-cache-baseline-is-lost` | WatchOngoingCoordinatorTest.permission denial preserves ordinary cache but does not claim ongoing retention() |
| `disconnect-restored-cache-baseline-is-lost` | WatchOngoingCoordinatorTest.disconnect predating a fresh snapshot cannot shorten the new lifecycle() |
| `disconnect-without-lifecycle-applies-stale-event` | WatchOngoingCoordinatorTest.disconnect predating a fresh snapshot cannot shorten the new lifecycle() |
| `disconnect-future-event-is-accepted` | WatchOngoingCoordinatorTest.permission denial preserves ordinary cache but does not claim ongoing retention() |
| `disconnect-expiry-drops-connection` | WatchOngoingCoordinatorTest.disconnect shortens notification before atomically changing its cache header() |
| `disconnect-permission-preflight-drops-connection` | WatchOngoingCoordinatorTest.disconnect shortens notification before atomically changing its cache header() |
| `disconnect-update-denial-drops-connection` | WatchOngoingCoordinatorTest.disconnect shortens notification before atomically changing its cache header() |
| `disconnect-update-missing-drops-connection` | WatchOngoingCoordinatorTest.disconnect shortens notification before atomically changing its cache header() |
| `runtime-final-platform-status-after-authority-expiry` | WatchRuntimeOwnerTest.platformStatusCrossingLeaseDeadlineIsReadOnlyBeforeWakeReturns() |
| `atomic-cache-skip-backup-recovery` | WatchAtomicCacheRecoveryApi28Test.backupOnlyRestartRestoresCanonicalDisplayWithoutRenewingNotification()<br>WatchAtomicCacheRecoveryApi33Test.backupOnlyRestartRestoresCanonicalDisplayWithoutRenewingNotification() |
| `atomic-cache-unreadable-as-empty` | WatchAtomicCacheRecoveryApi28Test.trueAbsenceIsEmptyButUnreadableRecordRemainsAnIoFailure()<br>WatchAtomicCacheRecoveryApi33Test.trueAbsenceIsEmptyButUnreadableRecordRemainsAnIoFailure() |
| `atomic-cache-absence-as-io-failure` | WatchAtomicCacheRecoveryApi28Test.trueAbsenceIsEmptyButUnreadableRecordRemainsAnIoFailure()<br>WatchAtomicCacheRecoveryApi33Test.trueAbsenceIsEmptyButUnreadableRecordRemainsAnIoFailure() |
| `runtime-inflight-draft-overrides-submission-marker` | WatchRuntimeOwnerTest.completionIssuesFingerprintAndInFlightWithoutInventingAcknowledgement() |

Both initial and restored proof reports are COMPLETE: 110/110 controls, 100/100 intended
identities and 100/100 zero-failure baseline identities. Baseline, restored and final root
Wear debug XML have the same exact method sets. Every proof gate ran serially with
`--rerun-tasks --no-build-cache --no-configuration-cache`. The intervening `clean` is
housekeeping, not a proof gate. No earlier interrupted ongoing cohort supplies gate
credit to this run. Earlier UI/ambient ledgers retain their own source scope;
they are not relabeled as evidence for the added lifecycle mechanisms.

Evidence run: `20260923T083830.996457Z` on `6ee1b4aaecfeadfb98c54eb914a9ab1efea0a109` plus the recorded full source-byte
snapshot. `/private/tmp/wear-ongoing-evidence-v6/runs/20260923T083830.996457Z` retains `pipeline.json`, `source-sha256.json`, frozen input manifests,
commands/timestamps, stage artifact hashes, actual XML, before/mutant bytes and both proof
reports. Selected attempts are those named in stage receipts; failed attempts remain
historical. The finalizer checks current HEAD/branch, all candidate bytes, stage artifacts,
source/test hashes, exact intended XML failures and serial task summaries before updating
only the documented current-status/evidence sections. Runnable control manifests are local
evidence artifacts; this ledger does not claim they were committed or shipped.

The preceding ambient increment's own matrix/control ledger stays unchanged. Full debug
regression retains all 98 lower-PR identities: the original 92 plus three methods for
system time preference/draft projection, the editor-wake method and two submitted-draft
methods. Their historical 24-, 6-, 4- and 8-control campaigns stay unchanged and were not rerun by this
lifecycle cohort. Those methods remain base regression coverage; the 100-identity
lifecycle map does not claim their separate negative controls. The full debug XML
contains the exact 196-identity union of the retained base and 99 protected debug
identities, with the existing G9 identity shared between those sets. This cohort adds
no physical watch evidence. Retention, actual Activity reuse, radio reconnect timing, process-death
notification removal and OEM timeout tolerance still require §7's target-device probes.
No real phone transport, mutation acknowledgement or calibrated production constant is
claimed by these host results.

### 6.1 Mutation recipe amendments within the v6 cohort

Three original recipes did not establish the intended negative control. Their unsuccessful
attempts remain in the archive; none counts as an intended assertion kill.

| Control | Original result and cause | Reviewed recipe correction |
| --- | --- | --- |
| `ongoing-api28-demands-runtime-permission` (73) | GREEN: changing the API threshold still called pinned Core 1.19.0's pre-33 `ContextCompat` compatibility path, which returned GRANTED because application notifications were enabled | Change `!requiresPermission || permissionCheck` to `requiresPermission && permissionCheck`; deny the pre-33 branch while retaining the API 33 permission-check behavior |
| `runtime-restored-display-never-reauthorizes` (90) | INVALID: leading `false &&` made the nullable comparison's right-hand side unreachable and prevented the required K2 smart cast | Keep `effectiveWindow != null && effectiveWindow > 0L` first and append `&& false`; retain the intended denial of fresh authority installation |
| `android-api28-incorrectly-requires-runtime-permission` (92) | GREEN: replacing the pre-33 bypass with `false ||` still received GRANTED from the same compatibility path | Use `SDK_INT >= TIRAMISU && permissionCheck`; deny API 28 while preserving API 33 behavior |

These were recipe corrections, not application fixes or weakened test oracles. They changed
only the named recipe's replacement/anchor and matching harness arguments. Production and
test bytes, source hashes, intended method mappings, selectors, validator and coverage
counts were unchanged between restored attempts. The original compile failure and
equivalent mutants are not evidence of a missing production behavior.

The same cohort,
`/private/tmp/wear-ongoing-evidence-v6/runs/20260923T083830.996457Z`, resumed through its
existing strict `--refresh-control` path. It required the exact failed stage and unchanged
checkout/source identity, revalidated all preceding complete receipts and intended kills,
then reran that control and every subsequent stage. The 73, 90 and 92 resumes retained
72, 89 and 91 preceding valid controls respectively. No v5 result was imported.

The archived `definition-revisions/` entries are `20260923T093823.876449Z` (73),
`20260923T095545.236494Z` (90) and `20260923T095938.085994Z` (92). They retain the old
manifests, flattened cases and unchanged audit before replacement; unsuccessful attempt
directories remain separate. Recipe 92 was applied on top of the adopted 90 definition,
preserving that earlier correction. The final §6 proof identifies the corrected recipes'
actual intended assertion kills and restored gates; standalone diagnostic probes are not
substituted for those cohort records. This history adds no physical-device or real-transport
acceptance claim.

## 7. Physical and final Phase 1 acceptance

The [Android platform guidance](https://developer.android.com/training/wearables/always-on#prevent-returning-to-the-watch-face-with-an-ongoing-activity-or-live-update)
describes prevention of automatic return to the watch face with Ongoing Activity on
Wear OS 5 and later. Verify that behavior on the target device while allowing deliberate
user exit, then verify return through the watch-face indicator to the expected Activity
and editor/controller state. On older supported versions, record the return affordance
and ambient behavior separately; do not infer the same foreground-retention guarantee.
Record OS/API/targetSdk, build type, package/signature and scenario for both groups.

The target watch must establish reconnect timing and the maximum notification-removal
tolerance required by Phase 1 §8. The debug policy is explicitly uncalibrated and cannot
close that acceptance condition. No measured production constant is defined here.

Kill the watch process before freshness loss and again after earlier disconnect. Observe
system notification removal externally by the persisted absolute deadline plus the measured
tolerance, without app restart. Repeat permission denial/restoration, low-bit/burn-in
behavior, both editor wake paths and authority expiry on the supported devices. PendingIntent
flags and Robolectric timeout fields do not establish those physical results.

Real transport, phone confirmation, disclosure/route decisions and final Phase 1 acceptance
remain blocked by the existing privacy and paired-device gates. Public privacy files, phone
schema and mutation-authority rules are unchanged by this stage.
