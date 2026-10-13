// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import androidx.sqlite.SQLiteException
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.testfixtures.WorkoutTargetParityFixture
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.database.wear.SessionWearSyncRow
import io.github.stslex.workeeper.core.data.database.wear.WearSyncDao
import io.github.stslex.workeeper.core.data.database.wear.prepareWearSyncStorage
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSessionWrites
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CommandRouting
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ImmutableTypeField
import io.github.stslex.workeeper.core.wear.protocol.InvalidValueReason
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSelection
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.OutlineStatus
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandRequest
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.core.wear.protocol.exercise
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class PhoneWorkoutBridgeImplTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var database: AppDatabase
    private lateinit var clock: FakeClock
    private lateinit var leaseStore: WearMutationLeaseStore
    private lateinit var bridge: PhoneWorkoutBridgeImpl

    @BeforeEach
    fun setUp() = openEnvironment()

    private fun openEnvironment() {
        env = RepositoryTestEnv()
        database = env.rawDatabase()
        clock = FakeClock()
        leaseStore = WearMutationLeaseStore(env.transition)
        bridge = newBridge()
        runBlocking { prepareWearSyncStorage(database, rotateDatabaseEpoch = false) }
    }

    private fun resetEnvironment() {
        env.close()
        openEnvironment()
    }

    @AfterEach
    fun tearDown() = env.close()

    @Test
    fun `canonical target uses plan and sparse persisted positions without synthesizing a row`() =
        runTest {
            val seed = seedSession(
                plans = listOf(plan(100.0, 5), plan(101.25, 6), plan(102.5, 7)),
            )
            database.setDao.upsertByTarget(
                uuid = Uuid.random(),
                performedExerciseUuid = seed.performedUuids.single(),
                position = 1,
                reps = 6,
                weight = 101.25,
                type = SetTypeEntity.WORK,
            )

            val active = bridge.snapshot().snapshot.payload as SnapshotPayload.Active

            assertEquals(0, active.target.setPosition)
            assertEquals(1, active.target.setOrdinal)
            assertEquals(3, active.target.totalSets)
            assertEquals(5, active.target.reps)
            assertEquals(10_000, active.target.weightHundredthsKg)
            assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        }

    @Test
    fun `empty first exercise returns NoSetRows and never skips to a later valid target`() = runTest {
        val seed = seedSession(
            plans = emptyList(),
            secondPlans = listOf(plan(50.0, 8)),
        )

        val payload = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
        val selection = requireNotNull(OutlineRules.defaultSelection(payload))

        // wear-phase-2-exercise-list.md §5.2, §5.3 (P12): the outline flags the set-less exercise, and
        // rule R stops at it with no position instead of skipping to the next one.
        assertEquals(seed.performedUuids.first().toString(), selection.performedExerciseUuid.value)
        assertNull(selection.setPosition)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.first()))
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.last()))
    }

    @Test
    fun `shared persisted-state vectors select the same canonical bridge outcome`() = runTest {
        WorkoutTargetParityFixture.SCENARIOS.forEachIndexed { index, scenario ->
            if (index > 0) resetEnvironment()
            val seed = seedSession(
                plans = List(scenario.planSize) { position ->
                    plan(weight = 100.0 + position, reps = 5 + position)
                },
            )
            scenario.performedPositions.forEach { position ->
                database.setDao.upsertByTarget(
                    uuid = Uuid.random(),
                    performedExerciseUuid = seed.performedUuids.single(),
                    position = position,
                    reps = 5,
                    weight = 100.0,
                    type = SetTypeEntity.WORK,
                )
            }
            if (scenario.skipped) {
                database.performedExerciseDao.setSkipped(seed.performedUuids.single(), true)
            }

            val payload = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
            val outlined = requireNotNull(payload.exercise(seed.performedUuids.single().toWireUuid()))

            // wear-phase-2-exercise-list.md §5.3: the outline's finished rule equals the phone's.
            assertEquals(scenario.expectedPhoneDone, OutlineRules.isFinished(outlined), scenario.name)
            when (scenario.expectedBridgeState) {
                WorkoutTargetParityFixture.BridgeState.ACTIVE_TARGET -> assertEquals(
                    scenario.expectedTargetPosition,
                    payload.target.setPosition,
                    scenario.name,
                )
                WorkoutTargetParityFixture.BridgeState.PHONE_ACTION_REQUIRED -> assertEquals(
                    OutlineSelection(outlined.performedExerciseUuid, null),
                    OutlineRules.defaultSelection(payload),
                    scenario.name,
                )
                WorkoutTargetParityFixture.BridgeState.WORKOUT_COMPLETE -> assertTrue(
                    payload.isWorkoutComplete(),
                    scenario.name,
                )
            }
        }
    }

    @Test
    fun `weightless target hides residual weight and unsupported weighted values fail read only`() =
        runTest {
            seedSession(
                plans = listOf(plan(77.0, 9)),
                exerciseType = ExerciseTypeEntity.WEIGHTLESS,
            )
            val weightless = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
            assertNull(weightless.target.weightHundredthsKg)

            resetEnvironment()
            val seed = seedSession(plans = listOf(plan(-0.0, 9)))
            // wear-phase-2-exercise-list.md §5.2 (P8): the set is Blocked on its field and rule R selects it.
            val unsupported = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
            val selection = requireNotNull(OutlineRules.defaultSelection(unsupported))
            val blocked = requireNotNull(unsupported.exercise(selection.performedExerciseUuid))
                .sets.single { it.position == selection.setPosition } as OutlineSet.Blocked
            assertEquals(NumericField.WEIGHT, blocked.field)
            assertEquals(seed.performedUuids.single().toString(), selection.performedExerciseUuid.value)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        }

    @Test
    fun `snapshot numeric conversion is exact and rejects every unsupported double shape`() {
        val weighted = ExerciseTypeEntity.WEIGHTED
        assertEquals(SnapshotNumeric.Value(0, null), snapshotNumeric(weighted, 0, null))
        assertEquals(SnapshotNumeric.Value(1, 0), snapshotNumeric(weighted, 1, 0.0))
        assertEquals(SnapshotNumeric.Value(5, 125), snapshotNumeric(weighted, 5, 1.25))
        assertEquals(SnapshotNumeric.Value(999, 99_999), snapshotNumeric(weighted, 999, 999.99))
        assertEquals(
            SnapshotNumeric.Value(5, null),
            snapshotNumeric(ExerciseTypeEntity.WEIGHTLESS, 5, 777.77),
        )

        listOf(-0.0, -1.0, 1_000.0, 1.234, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
            .forEach { weight ->
                assertEquals(
                    SnapshotNumeric.Unsupported(NumericField.WEIGHT),
                    snapshotNumeric(weighted, 5, weight),
                    "weight=$weight",
                )
            }
        listOf(-1, 1_000).forEach { reps ->
            assertEquals(
                SnapshotNumeric.Unsupported(NumericField.REPS),
                snapshotNumeric(weighted, reps, 100.0),
                "reps=$reps",
            )
        }
    }

    @Test
    fun `unsupported current exercise never skips to a later representable exercise`() = runTest {
        val seed = seedSession(
            plans = listOf(plan(1.234, 5)),
            secondPlans = listOf(plan(50.0, 8)),
        )

        // wear-phase-2-exercise-list.md §5.2 (P8), §5.3: rule R stops at the Blocked set.
        val payload = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
        val selection = requireNotNull(OutlineRules.defaultSelection(payload))
        val blocked = requireNotNull(payload.exercise(selection.performedExerciseUuid))
            .sets.single { it.position == selection.setPosition } as OutlineSet.Blocked

        assertEquals(NumericField.WEIGHT, blocked.field)
        assertEquals(seed.performedUuids.first().toString(), selection.performedExerciseUuid.value)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.first()))
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.last()))
    }

    @Test
    fun `ad hoc workout target uses the exercise fallback plan`() = runTest {
        seedSession(
            plans = listOf(plan(999.0, 999)),
            isAdhoc = true,
            adhocPlans = listOf(plan(42.5, 4)),
        )

        val payload = bridge.activePayload()

        assertEquals(0, payload.target.setPosition)
        assertEquals(4, payload.target.reps)
        assertEquals(4_250, payload.target.weightHundredthsKg)
    }

    @Test
    fun `applied command commits one row one receipt and exact replay is idempotent`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val snapshot = bridge.snapshot()
        val command = commandFrom(snapshot.snapshot.payload as SnapshotPayload.Active)

        val applied = bridge.executeCommand(SOURCE_NODE, command)
        val sameDelivery = bridge.executeCommand(SOURCE_NODE, command)
        val replay = bridge.executeCommand(
            SOURCE_NODE,
            command.copy(correlationId = CanonicalUuid.random()),
        )
        val sync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(CommandOutcome.Applied, applied.outcome)
        assertEquals(applied, sameDelivery)
        assertEquals(CommandOutcome.AlreadyApplied, replay.outcome)
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertEquals(command.commandId.value, sync.receiptCommandId)
        assertEquals(34, sync.receiptAttemptFingerprint?.size)
        assertEquals(sync.revision, sync.receiptRevision)
        assertTrue(applied.replacement.payload.isWorkoutComplete())
    }

    @Test
    fun `reused correlation with changed command body is rejected instead of replaying success`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val original = commandFrom(bridge.activePayload())
            val applied = bridge.executeCommand(SOURCE_NODE, original)
            val changed = original.copy(
                body = original.completeBody().copy(reps = original.completeBody().reps + 1),
            )

            val rejected = bridge.executeCommand(SOURCE_NODE, changed)

            assertEquals(
                CommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                ),
                rejected.outcome,
            )
            assertEquals(original.correlationId, rejected.correlationId)
            assertEquals(original.commandId, rejected.commandId)
            assertTrue(
                (rejected.replacement.payload as SnapshotPayload.Active)
                    .mutationAuthority is MutationAuthority.Unavailable,
            )
            assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertEquals(CommandOutcome.Applied, applied.outcome)
        }

    @Test
    fun `different commands for one target have one applied winner and one stale loser`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.activePayload()
        val first = commandFrom(active)
        val second = commandFrom(active).copy(correlationId = CanonicalUuid.random())

        val responses = coroutineScope {
            listOf(
                async { bridge.executeCommand(SOURCE_NODE, first) },
                async { bridge.executeCommand(SOURCE_NODE, second) },
            ).awaitAll()
        }
        val sync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(1, responses.count { it.outcome == CommandOutcome.Applied })
        assertEquals(1, responses.count { it.outcome == CommandOutcome.StaleRevision })
        assertTrue(responses.all { it.replacement.payload.isWorkoutComplete() })
        assertTrue(
            responses.all { response ->
                (response.replacement.payload as SnapshotPayload.Active).sessionRevision ==
                    sync.revision
            },
        )
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `different concurrent commands and a phone race persist at most one target row`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
        val first = commandFrom(active)
        val second = commandFrom(active).copy(correlationId = CanonicalUuid.random())

        val responses = coroutineScope {
            listOf(
                async { bridge.executeCommand(SOURCE_NODE, first) },
                async { bridge.executeCommand(SOURCE_NODE, second) },
                async {
                    env.transition.mutate {
                        database.setDao.upsertByTarget(
                            Uuid.random(),
                            seed.performedUuids.single(),
                            0,
                            5,
                            100.0,
                            SetTypeEntity.WORK,
                        )
                    }
                    null
                },
            ).awaitAll().filterNotNull()
        }

        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertTrue(
            responses.all { response ->
                response.outcome == CommandOutcome.Applied ||
                    response.outcome == CommandOutcome.StaleRevision
            },
        )
        assertTrue(responses.count { it.outcome == CommandOutcome.Applied } <= 1)
        val converged = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
        assertTrue(converged.isWorkoutComplete())
        val sync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        assertEquals(sync.revision, converged.sessionRevision)
        assertEquals(1, database.setDao.getByPerformedExercise(seed.performedUuids.single()).size)
    }

    @Test
    fun `complete then phone undo rejects the original command after ABA`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())
        assertEquals(CommandOutcome.Applied, bridge.executeCommand(SOURCE_NODE, command).outcome)
        env.transition.mutate {
            database.setDao.deleteByPerformedAndPosition(seed.performedUuids.single(), 0)
        }

        val replay = bridge.executeCommand(
            SOURCE_NODE,
            command.copy(correlationId = CanonicalUuid.random()),
        )

        assertEquals(CommandOutcome.StaleRevision, replay.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)?.receiptCommandId)
    }

    @Test
    fun `lease admits at 119999ms and rejects at the exact 120000ms boundary`() = runTest {
        seedSession(plans = listOf(plan(100.0, 5)))
        val accepted = commandFrom(bridge.activePayload())
        clock.now = WearProtocol.MAX_MUTATION_WINDOW_MS - 1
        assertEquals(
            CommandOutcome.Applied,
            bridge.executeCommand(SOURCE_NODE, accepted).outcome,
        )

        resetEnvironment()
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val expired = commandFrom(bridge.activePayload())
        clock.now = WearProtocol.MAX_MUTATION_WINDOW_MS
        val response = bridge.executeCommand(SOURCE_NODE, expired)
        assertEquals(CommandOutcome.AuthorizationExpired, response.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

        val successor = response.replacement.payload as SnapshotPayload.Active
        val successorAuthority = successor.mutationAuthority as MutationAuthority.Granted
        val forbiddenRebind = expired.copy(
            correlationId = CanonicalUuid.random(),
            mutationLeaseId = successorAuthority.mutationLeaseId,
            mutationLeaseGeneration = successorAuthority.mutationLeaseGeneration,
        )
        assertEquals(
            CommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
            ),
            bridge.executeCommand(SOURCE_NODE, forbiddenRebind).outcome,
        )
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `phone process restart loses leases and allocates a durable higher successor`() = runTest {
        seedSession(plans = listOf(plan(100.0, 5)))
        val before = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
        val beforeAuthority = before.mutationAuthority as MutationAuthority.Granted

        leaseStore = WearMutationLeaseStore(env.transition)
        bridge = newBridge()
        val after = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
        val afterAuthority = after.mutationAuthority as MutationAuthority.Granted
        val oldCommand = commandFrom(before)

        assertTrue(afterAuthority.mutationLeaseGeneration > beforeAuthority.mutationLeaseGeneration)
        assertNotEquals(beforeAuthority.mutationLeaseId, afterAuthority.mutationLeaseId)
        assertEquals(
            CommandOutcome.AuthorizationExpired,
            bridge.executeCommand(SOURCE_NODE, oldCommand).outcome,
        )
    }

    @Test
    fun `distinct handshakes allocate ordered successors while exact replay is byte identical`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val request = GetActiveWorkoutRequest(
                WearProtocol.SCHEMA_VERSION,
                CanonicalUuid.random(),
            )
            val first = bridge.getActiveWorkout(SOURCE_NODE, request)
            val firstPayload = first.snapshot.payload as SnapshotPayload.Active
            val firstAuthority = firstPayload.mutationAuthority as MutationAuthority.Granted
            val firstSync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

            val replay = bridge.getActiveWorkout(SOURCE_NODE, request)
            val replaySync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            assertArrayEquals(WearProtocolCodec.encode(first), WearProtocolCodec.encode(replay))
            assertEquals(firstSync.leaseGeneration, replaySync.leaseGeneration)

            val distinct = coroutineScope {
                listOf(
                    async { bridge.snapshot() },
                    async { bridge.snapshot() },
                ).awaitAll()
            }
            val generations = distinct.map { response ->
                val active = response.snapshot.payload as SnapshotPayload.Active
                (active.mutationAuthority as MutationAuthority.Granted).mutationLeaseGeneration
            }.sorted()
            assertEquals(
                listOf(
                    firstAuthority.mutationLeaseGeneration + 1,
                    firstAuthority.mutationLeaseGeneration + 2,
                ),
                generations,
            )
            assertEquals(
                generations.last(),
                requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
                    .leaseGeneration,
            )

            val oldResponse = bridge.executeCommand(SOURCE_NODE, commandFrom(firstPayload))
            assertEquals(CommandOutcome.AuthorizationExpired, oldResponse.outcome)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        }

    @Test
    fun `lease publication rejects a phone mutation that wins the post-commit gap`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val payload = bridge.activePayload()
        val staleCandidate = requireNotNull(
            leaseStore.activeLeaseForTest(SOURCE_NODE, payload.sessionUuid),
        )

        env.transition.mutate {
            database.trainingExerciseDao.updatePlanSets(
                trainingUuid = seed.trainingUuid,
                exerciseUuid = seed.exerciseUuids.single(),
                planSets = PlanSetsConverter.toJson(listOf(plan(101.25, 6))),
            )
        }

        assertFalse(
            leaseStore.publishIfCurrent(database, env.transition, staleCandidate),
        )
        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, payload.sessionUuid))
    }

    @Test
    fun `snapshot publication race returns current read only state instead of unpublished lease`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val racingTransition = OneShotInvokeRaceTransition(env.transition) {
                env.transition.mutate {
                    database.trainingExerciseDao.updatePlanSets(
                        trainingUuid = seed.trainingUuid,
                        exerciseUuid = seed.exerciseUuids.single(),
                        planSets = PlanSetsConverter.toJson(listOf(plan(101.25, 6))),
                    )
                }
            }
            val racingBridge = newBridge(transition = racingTransition)

            val response = racingBridge.snapshot()
            val payload = response.snapshot.payload as SnapshotPayload.Active

            assertEquals(10_125, payload.target.weightHundredthsKg)
            assertEquals(6, payload.target.reps)
            assertTrue(payload.mutationAuthority is MutationAuthority.Unavailable)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, payload.sessionUuid))
        }

    @Test
    fun `command publication race keeps outcome but returns current read only replacement`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val command = commandFrom(bridge.activePayload())
            val racingTransition = OneShotInvokeRaceTransition(env.transition) {
                env.transition.mutate {
                    database.trainingExerciseDao.updatePlanSets(
                        trainingUuid = seed.trainingUuid,
                        exerciseUuid = seed.exerciseUuids.single(),
                        planSets = PlanSetsConverter.toJson(
                            listOf(plan(100.0, 5), plan(102.25, 7)),
                        ),
                    )
                }
            }
            val racingBridge = newBridge(transition = racingTransition)

            val response = racingBridge.executeCommand(SOURCE_NODE, command)
            val replacement = response.replacement.payload as SnapshotPayload.Active

            assertEquals(CommandOutcome.Applied, response.outcome)
            assertEquals(1, replacement.target.setPosition)
            assertEquals(10_225, replacement.target.weightHundredthsKg)
            assertEquals(7, replacement.target.reps)
            assertTrue(replacement.mutationAuthority is MutationAuthority.Unavailable)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, replacement.sessionUuid))
            assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        }

    @Test
    fun `retryable publication race reclassifies against the current read only replacement`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val command = commandFrom(bridge.activePayload())
            val flaky = FlakyWriter(RoomWearSetMutationWriter(database))
            val racingTransition = OneShotInvokeRaceTransition(env.transition) {
                env.transition.mutate {
                    database.trainingExerciseDao.updatePlanSets(
                        trainingUuid = seed.trainingUuid,
                        exerciseUuid = seed.exerciseUuids.single(),
                        planSets = PlanSetsConverter.toJson(listOf(plan(103.25, 8))),
                    )
                }
            }
            val racingBridge = newBridge(writer = flaky, transition = racingTransition)

            val response = racingBridge.executeCommand(SOURCE_NODE, command)
            val replacement = response.replacement.payload as SnapshotPayload.Active

            assertEquals(CommandOutcome.StaleRevision, response.outcome)
            assertEquals(10_325, replacement.target.weightHundredthsKg)
            assertEquals(8, replacement.target.reps)
            assertTrue(replacement.mutationAuthority is MutationAuthority.Unavailable)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, replacement.sessionUuid))
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            WearProtocolCodec.encode(response)
        }

    @Test
    fun `target changed publication race reclassifies after the phone advances revision`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val active = bridge.activePayload()
            val command = commandFrom(active).let { current ->
                current.copy(
                    body = current.completeBody().copy(performedExerciseUuid = CanonicalUuid.random()),
                )
            }
            val racingTransition = OneShotInvokeRaceTransition(env.transition) {
                env.transition.mutate {
                    database.trainingExerciseDao.updatePlanSets(
                        trainingUuid = seed.trainingUuid,
                        exerciseUuid = seed.exerciseUuids.single(),
                        planSets = PlanSetsConverter.toJson(listOf(plan(104.5, 9))),
                    )
                }
            }

            val response = newBridge(transition = racingTransition)
                .executeCommand(SOURCE_NODE, command)
            val replacement = response.replacement.payload as SnapshotPayload.Active

            assertEquals(CommandOutcome.StaleRevision, response.outcome)
            assertEquals(10_450, replacement.target.weightHundredthsKg)
            assertEquals(9, replacement.target.reps)
            assertTrue(replacement.mutationAuthority is MutationAuthority.Unavailable)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, replacement.sessionUuid))
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            WearProtocolCodec.encode(response)
        }

    @Test
    fun `target changed publication race reclassifies after the phone ends the session`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val active = bridge.activePayload()
            val command = commandFrom(active).let { current ->
                current.copy(
                    body = current.completeBody().copy(performedExerciseUuid = CanonicalUuid.random()),
                )
            }
            val racingTransition = OneShotInvokeRaceTransition(env.transition) {
                env.transition.mutate {
                    database.sessionDao.finishSession(seed.sessionUuid, finishedAt = 2)
                }
            }

            val response = newBridge(transition = racingTransition)
                .executeCommand(SOURCE_NODE, command)

            assertEquals(CommandOutcome.NoActiveSession, response.outcome)
            assertTrue(response.replacement.payload is SnapshotPayload.NoSession)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            WearProtocolCodec.encode(response)
        }

    @Test
    fun `wrong source node cannot consume another node lease`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.activePayload()
        val command = commandFrom(active)

        val response = bridge.executeCommand("different-watch-node", command)

        assertEquals(CommandOutcome.AuthorizationExpired, response.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, command.sessionUuid))
    }

    @Test
    fun `exact durable receipt remains idempotent after the original lease expires`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
        val command = commandFrom(bridge.activePayload())
        val applied = bridge.executeCommand(SOURCE_NODE, command)
        val afterApplied = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        clock.now = WearProtocol.MAX_MUTATION_WINDOW_MS

        val replay = bridge.executeCommand(
            SOURCE_NODE,
            command.copy(correlationId = CanonicalUuid.random()),
        )
        val afterReplay = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(CommandOutcome.Applied, applied.outcome)
        assertEquals(CommandOutcome.AlreadyApplied, replay.outcome)
        assertEquals(afterApplied.revision, afterReplay.revision)
        assertEquals(command.commandId.value, afterReplay.receiptCommandId)
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `invalid values and immutable metadata retire authority with zero database effects`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val active = bridge.activePayload()
            val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            val invalid = commandFrom(active).copy(
                body = commandFrom(active).completeBody().copy(reps = 0),
            )

            val invalidResponse = bridge.executeCommand(SOURCE_NODE, invalid)
            val afterInvalid = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            assertEquals(
                CommandOutcome.InvalidValues(NumericField.REPS, InvalidValueReason.BELOW_MINIMUM),
                invalidResponse.outcome,
            )
            assertTrue(
                (invalidResponse.replacement.payload as SnapshotPayload.Active)
                    .mutationAuthority is MutationAuthority.Unavailable,
            )
            assertSyncUnchanged(before, afterInvalid)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, active.sessionUuid))

            val refreshed = bridge.snapshot().snapshot.payload as SnapshotPayload.Active
            val mismatch = commandFrom(refreshed).copy(
                body = commandFrom(refreshed).completeBody().copy(
                    exerciseType = ExerciseTypeWire.WEIGHTLESS,
                ),
            )
            val mismatchResponse = bridge.executeCommand(SOURCE_NODE, mismatch)
            assertEquals(
                CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE),
                mismatchResponse.outcome,
            )
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertFalse(
                (mismatchResponse.replacement.payload as SnapshotPayload.Active)
                    .mutationAuthority is MutationAuthority.Granted,
            )
        }

    @Test
    fun `set type mismatch is terminal read only and reports its exact immutable field`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.activePayload()
        val original = commandFrom(active)
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        val mismatch = original.copy(
            body = original.completeBody().copy(setType = SetTypeWire.FAIL),
        )

        val response = bridge.executeCommand(SOURCE_NODE, mismatch)
        val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(
            CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.SET_TYPE),
            response.outcome,
        )
        assertSyncUnchanged(before, after)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertTrue(
            (response.replacement.payload as SnapshotPayload.Active)
                .mutationAuthority is MutationAuthority.Unavailable,
        )
        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, active.sessionUuid))
    }

    @Test
    fun `terminal command id cannot bind to a later generic successor lease`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val firstActive = bridge.activePayload()
        val valid = commandFrom(firstActive)
        val invalid = valid.copy(body = valid.completeBody().copy(reps = 0))
        val invalidResponse = bridge.executeCommand(SOURCE_NODE, invalid)
        assertEquals(
            CommandOutcome.InvalidValues(
                NumericField.REPS,
                InvalidValueReason.BELOW_MINIMUM,
            ),
            invalidResponse.outcome,
        )

        val fresh = bridge.activePayload()
        val freshAuthority = fresh.mutationAuthority as MutationAuthority.Granted
        val beforeRebind = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        val genericRebind = invalid.copy(
            correlationId = CanonicalUuid.random(),
            mutationLeaseId = freshAuthority.mutationLeaseId,
            mutationLeaseGeneration = freshAuthority.mutationLeaseGeneration,
        )

        val rejected = bridge.executeCommand(SOURCE_NODE, genericRebind)
        val afterRebind = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(
            CommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
            ),
            rejected.outcome,
        )
        assertSyncUnchanged(beforeRebind, afterRebind)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

        val corrected = commandFrom(bridge.activePayload())
        assertNotEquals(valid.commandId, corrected.commandId)
        assertEquals(
            CommandOutcome.Applied,
            bridge.executeCommand(SOURCE_NODE, corrected).outcome,
        )
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `all numeric gateway boundaries return typed outcomes without invalid writes`() = runTest {
        val invalidCases = listOf(
            InvalidCommandCase(
                reps = 0,
                expected = CommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
            ),
            InvalidCommandCase(
                reps = -1,
                expected = CommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
            ),
            InvalidCommandCase(
                reps = 1_000,
                expected = CommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.ABOVE_MAXIMUM,
                ),
            ),
            InvalidCommandCase(
                weightHundredthsKg = -1,
                expected = CommandOutcome.InvalidValues(
                    NumericField.WEIGHT,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
            ),
            InvalidCommandCase(
                weightHundredthsKg = 100_000,
                expected = CommandOutcome.InvalidValues(
                    NumericField.WEIGHT,
                    InvalidValueReason.ABOVE_MAXIMUM,
                ),
            ),
            InvalidCommandCase(
                weightHundredthsKg = 0,
                commandExerciseType = ExerciseTypeWire.WEIGHTLESS,
                seedExerciseType = ExerciseTypeEntity.WEIGHTLESS,
                expected = CommandOutcome.InvalidValues(
                    NumericField.WEIGHT,
                    InvalidValueReason.MUST_BE_NULL_FOR_WEIGHTLESS,
                ),
            ),
        )

        invalidCases.forEachIndexed { index, case ->
            if (index > 0) resetEnvironment()
            val seed = seedSession(
                plans = listOf(plan(100.0, 5)),
                exerciseType = case.seedExerciseType,
            )
            val active = bridge.activePayload()
            val original = commandFrom(active)
            val command = original.copy(
                body = original.completeBody().copy(
                    reps = case.reps ?: 5,
                    weightHundredthsKg = case.weightHundredthsKg,
                    exerciseType = case.commandExerciseType,
                ),
            )
            val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

            val response = bridge.executeCommand(SOURCE_NODE, command)
            val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

            assertEquals(case.expected, response.outcome, "invalid case $case")
            assertSyncUnchanged(before, after)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertTrue(
                (response.replacement.payload as SnapshotPayload.Active)
                    .mutationAuthority is MutationAuthority.Unavailable,
            )
        }
    }

    @Test
    fun `valid numeric gateway boundaries persist their exact canonical values`() = runTest {
        val validCases = listOf(
            ValidCommandCase(reps = 1, weightHundredthsKg = null),
            ValidCommandCase(reps = 999, weightHundredthsKg = 0),
            ValidCommandCase(reps = 5, weightHundredthsKg = 99_999),
            ValidCommandCase(
                reps = 1,
                weightHundredthsKg = null,
                exerciseType = ExerciseTypeEntity.WEIGHTLESS,
            ),
        )

        validCases.forEachIndexed { index, case ->
            if (index > 0) resetEnvironment()
            val seed = seedSession(
                plans = listOf(plan(100.0, 5)),
                exerciseType = case.exerciseType,
            )
            val active = bridge.activePayload()
            val original = commandFrom(active)
            val command = original.copy(
                body = original.completeBody().copy(
                    reps = case.reps,
                    weightHundredthsKg = case.weightHundredthsKg,
                    exerciseType = case.exerciseType.toWire(),
                ),
            )

            val response = bridge.executeCommand(SOURCE_NODE, command)
            val row = database.setDao.getByPerformedExercise(seed.performedUuids.single()).single()

            assertEquals(CommandOutcome.Applied, response.outcome, "valid case $case")
            assertEquals(case.reps, row.reps)
            assertEquals(case.weightHundredthsKg?.toDouble()?.div(100.0), row.weight)
        }
    }

    @Test
    fun `database epoch and active identity ordering precede revision and receipts`() = runTest {
        val sessionA = seedSession(plans = listOf(plan(100.0, 5)))
        val commandA = commandFrom(bridge.activePayload())
        val wrongEpoch = bridge.executeCommand(
            SOURCE_NODE,
            commandA.copy(
                correlationId = CanonicalUuid.random(),
                databaseEpoch = CanonicalUuid.random(),
            ),
        )
        assertEquals(CommandOutcome.StaleRevision, wrongEpoch.outcome)

        database.sessionDao.finishSession(sessionA.sessionUuid, finishedAt = 2)
        val noSession = bridge.executeCommand(
            SOURCE_NODE,
            commandA.copy(correlationId = CanonicalUuid.random()),
        )
        assertEquals(CommandOutcome.NoActiveSession, noSession.outcome)
        assertTrue(noSession.replacement.payload is SnapshotPayload.NoSession)

        val sessionB = seedSession(
            plans = listOf(plan(60.0, 10)),
            trainingName = "Strength B",
            exerciseName = "Press",
        )
        val delayedA = bridge.executeCommand(
            SOURCE_NODE,
            commandA.copy(correlationId = CanonicalUuid.random()),
        )
        val replacementB = delayedA.replacement.payload as SnapshotPayload.Active
        assertEquals(CommandOutcome.StaleRevision, delayedA.outcome)
        assertEquals(sessionB.sessionUuid.toString(), replacementB.sessionUuid.value)
        assertEquals(0, database.setDao.countByPerformedExercise(sessionA.performedUuids.single()))
        assertEquals(0, database.setDao.countByPerformedExercise(sessionB.performedUuids.single()))
    }

    @Test
    fun `source and target invalidation pairings return the exact canonical replacement`() =
        runTest {
            val wrongSeed = seedSession(plans = listOf(plan(100.0, 5)))
            val wrongBase = commandFrom(bridge.activePayload())
            val wrongExercise = wrongBase.copy(
                body = wrongBase.completeBody().copy(performedExerciseUuid = CanonicalUuid.random()),
            )
            val wrongResponse = bridge.executeCommand(SOURCE_NODE, wrongExercise)
            val wrongReplacement =
                wrongResponse.replacement.payload as SnapshotPayload.Active
            assertEquals(CommandOutcome.TargetChanged, wrongResponse.outcome)
            assertEquals(
                wrongSeed.performedUuids.single().toString(),
                wrongReplacement.target.performedExerciseUuid.value,
            )

            resetEnvironment()
            seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val stalePositionBase = commandFrom(bridge.activePayload())
            val stalePosition = bindSyntheticLease(
                stalePositionBase.copy(
                    body = stalePositionBase.completeBody().copy(setPosition = 1),
                ),
            )
            val movedResponse = bridge.executeCommand(SOURCE_NODE, stalePosition)
            val movedReplacement =
                movedResponse.replacement.payload as SnapshotPayload.Active
            // wear-phase-2-exercise-list.md V3, §5.4: the session lease admits any Open set, so a
            // command for the other open position is applied (v1 answered TargetChanged).
            assertEquals(CommandOutcome.Applied, movedResponse.outcome)
            assertEquals(0, movedReplacement.target.setPosition)

            resetEnvironment()
            val completeSeed = seedSession(plans = listOf(plan(100.0, 5)))
            val completeBase = commandFrom(bridge.activePayload())
            env.transition.mutate {
                database.setDao.upsertByTarget(
                    uuid = Uuid.random(),
                    performedExerciseUuid = completeSeed.performedUuids.single(),
                    position = 0,
                    reps = 5,
                    weight = 100.0,
                    type = SetTypeEntity.WORK,
                )
            }
            val completedRequest = bindSyntheticLease(completeBase)
            val completedResponse = bridge.executeCommand(SOURCE_NODE, completedRequest)
            assertEquals(CommandOutcome.TargetChanged, completedResponse.outcome)
            assertTrue(completedResponse.replacement.payload.isWorkoutComplete())

            resetEnvironment()
            val emptySeed = seedSession(plans = listOf(plan(100.0, 5)))
            val emptyBase = commandFrom(bridge.activePayload())
            env.transition.mutate {
                database.trainingExerciseDao.updatePlanSets(
                    trainingUuid = emptySeed.trainingUuid,
                    exerciseUuid = emptySeed.exerciseUuids.single(),
                    planSets = PlanSetsConverter.toJson(emptyList()),
                )
            }
            val emptyRequest = bindSyntheticLease(emptyBase)
            val emptyResponse = bridge.executeCommand(SOURCE_NODE, emptyRequest)
            val emptyReplacement =
                emptyResponse.replacement.payload as SnapshotPayload.Active
            assertEquals(CommandOutcome.TargetChanged, emptyResponse.outcome)
            // §5.2, §5.3 (P12): the set-less exercise is flagged in the outline and rule R stops at it.
            assertNull(requireNotNull(OutlineRules.defaultSelection(emptyReplacement)).setPosition)
        }

    @Test
    fun `revision mismatch wins after the submitted exercise becomes skipped`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())

        env.transition.mutate {
            database.performedExerciseDao.setSkipped(seed.performedUuids.single(), true)
        }
        val response = bridge.executeCommand(SOURCE_NODE, command)

        assertEquals(CommandOutcome.StaleRevision, response.outcome)
        assertTrue(response.replacement.payload.isWorkoutComplete())
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `plan and exercise type edits retire old authority before a stale command arrives`() =
        runTest {
            val planSeed = seedSession(plans = listOf(plan(100.0, 5)))
            val planCommand = commandFrom(bridge.activePayload())
            val planBefore = requireNotNull(
                database.wearSyncDao.getSessionSync(planSeed.sessionUuid),
            )
            assertTrue(leaseStore.activeLeaseForTest(SOURCE_NODE, planCommand.sessionUuid) != null)

            env.transition { database.sessionDao.getActive() }
            assertTrue(leaseStore.activeLeaseForTest(SOURCE_NODE, planCommand.sessionUuid) != null)
            val failedMutation = runCatching {
                env.transition.mutate { error("synthetic rollback") }
            }
            assertTrue(failedMutation.isFailure)
            assertTrue(leaseStore.activeLeaseForTest(SOURCE_NODE, planCommand.sessionUuid) != null)

            env.transition.mutate {
                database.trainingExerciseDao.updatePlanSets(
                    trainingUuid = planSeed.trainingUuid,
                    exerciseUuid = planSeed.exerciseUuids.single(),
                    planSets = PlanSetsConverter.toJson(listOf(plan(125.25, 8), plan(130.0, 6))),
                )
            }

            val planAfter = requireNotNull(
                database.wearSyncDao.getSessionSync(planSeed.sessionUuid),
            )
            assertTrue(planAfter.revision > planBefore.revision)
            assertNull(planAfter.receiptCommandId)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, planCommand.sessionUuid))
            val planResponse = bridge.executeCommand(SOURCE_NODE, planCommand)
            val planReplacement = planResponse.replacement.payload as SnapshotPayload.Active
            assertEquals(CommandOutcome.StaleRevision, planResponse.outcome)
            assertEquals(0, planReplacement.target.setPosition)
            assertEquals(8, planReplacement.target.reps)
            assertEquals(12_525, planReplacement.target.weightHundredthsKg)
            assertEquals(0, database.setDao.countByPerformedExercise(planSeed.performedUuids.single()))

            resetEnvironment()
            val typeSeed = seedSession(plans = listOf(plan(100.0, 5)))
            val typeCommand = commandFrom(bridge.activePayload())
            val typeBefore = requireNotNull(
                database.wearSyncDao.getSessionSync(typeSeed.sessionUuid),
            )
            assertTrue(leaseStore.activeLeaseForTest(SOURCE_NODE, typeCommand.sessionUuid) != null)

            env.transition.mutate {
                database.exerciseDao.updateType(
                    typeSeed.exerciseUuids.single(),
                    ExerciseTypeEntity.WEIGHTLESS,
                )
            }

            val typeAfter = requireNotNull(
                database.wearSyncDao.getSessionSync(typeSeed.sessionUuid),
            )
            assertTrue(typeAfter.revision > typeBefore.revision)
            assertNull(typeAfter.receiptCommandId)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, typeCommand.sessionUuid))
            val typeResponse = bridge.executeCommand(SOURCE_NODE, typeCommand)
            val typeReplacement = typeResponse.replacement.payload as SnapshotPayload.Active
            assertEquals(CommandOutcome.StaleRevision, typeResponse.outcome)
            assertEquals(ExerciseTypeWire.WEIGHTLESS, typeReplacement.target.exerciseType)
            assertNull(typeReplacement.target.weightHundredthsKg)
            assertEquals(0, database.setDao.countByPerformedExercise(typeSeed.performedUuids.single()))
        }

    @Test
    fun `unsupported durable receipt fails closed without rewriting any authority byte`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())
        val beforeReceipt = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        // P4: the stored fingerprint is parsed only for the same command id; version 1 is unsupported.
        val unsupported = ByteArray(34).also {
            it[0] = 0
            it[1] = 1
        }
        val epoch = requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch
        assertEquals(
            1,
            database.wearSyncDao.storeReceipt(
                sessionUuid = seed.sessionUuid,
                commandId = command.commandId.value,
                attemptFingerprint = unsupported,
                databaseEpoch = epoch,
                revision = beforeReceipt.revision,
            ),
        )
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        val response = bridge.executeCommand(SOURCE_NODE, command)
        val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(
            CommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.UNSUPPORTED_FINGERPRINT_VERSION,
            ),
            response.outcome,
        )
        assertSyncUnchanged(before, after)
        assertArrayEquals(unsupported, after.receiptAttemptFingerprint)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `exercise type wins deterministically when both immutable types differ`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.activePayload()
        val original = commandFrom(active)
        val changed = original.copy(
            body = original.completeBody().copy(
                exerciseType = ExerciseTypeWire.WEIGHTLESS,
                setType = SetTypeWire.FAIL,
                weightHundredthsKg = null,
            ),
        )

        val response = bridge.executeCommand(SOURCE_NODE, changed)

        assertEquals(
            CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE),
            response.outcome,
        )
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `correlated protocol rejection preserves receipt revision and generation byte for byte`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val first = commandFrom(bridge.activePayload())
            val applied = bridge.executeCommand(SOURCE_NODE, first)
            val second = applied.replacement.payload as SnapshotPayload.Active
            val authority = second.mutationAuthority as MutationAuthority.Granted
            val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            val beforeRows = database.setDao.getByPerformedExercise(seed.performedUuids.single())

            val response = bridge.protocolRejected(
                SOURCE_NODE,
                CommandRouting(
                    schemaVersion = WearProtocol.SCHEMA_VERSION,
                    correlationId = CanonicalUuid.random(),
                    commandId = CanonicalUuid.random(),
                    databaseEpoch = applied.replacement.databaseEpoch,
                    sessionUuid = second.sessionUuid,
                    sessionRevision = second.sessionRevision,
                    mutationLeaseId = authority.mutationLeaseId,
                    mutationLeaseGeneration = authority.mutationLeaseGeneration,
                ),
                ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
            )
            val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

            assertEquals(
                CommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
                ),
                response.outcome,
            )
            assertTrue(
                (response.replacement.payload as SnapshotPayload.Active)
                    .mutationAuthority is MutationAuthority.Unavailable,
            )
            assertEquals(before.revision, after.revision)
            assertEquals(before.leaseGeneration, after.leaseGeneration)
            assertEquals(before.receiptCommandId, after.receiptCommandId)
            assertArrayEquals(before.receiptAttemptFingerprint, after.receiptAttemptFingerprint)
            assertEquals(beforeRows, database.setDao.getByPerformedExercise(seed.performedUuids.single()))
        }

    @Test
    fun `temporary write failure issues a retry-bound successor and retry commits once`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val flaky = FlakyWriter(RoomWearSetMutationWriter(database))
        bridge = newBridge(flaky)
        val first = commandFrom(bridge.activePayload())

        val retryable = bridge.executeCommand(SOURCE_NODE, first)
        val retryPayload = retryable.replacement.payload as SnapshotPayload.Active
        val successor = retryPayload.mutationAuthority as MutationAuthority.Granted
        flaky.fail = false
        val retry = first.copy(
            correlationId = CanonicalUuid.random(),
            mutationLeaseId = successor.mutationLeaseId,
            mutationLeaseGeneration = successor.mutationLeaseGeneration,
        )
        val applied = bridge.executeCommand(SOURCE_NODE, retry)

        assertEquals(CommandOutcome.RetryableTemporaryFailure, retryable.outcome)
        assertEquals(CommandOutcome.Applied, applied.outcome)
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `retry successor rejects changed intent and old command cannot use a generic rebind`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val flaky = FlakyWriter(RoomWearSetMutationWriter(database))
            bridge = newBridge(flaky)
            val first = commandFrom(bridge.activePayload())
            val retryable = bridge.executeCommand(SOURCE_NODE, first)
            val retryPayload = retryable.replacement.payload as SnapshotPayload.Active
            val retryAuthority = retryPayload.mutationAuthority as MutationAuthority.Granted
            val beforeChanged = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            val changedIntent = first.copy(
                correlationId = CanonicalUuid.random(),
                mutationLeaseId = retryAuthority.mutationLeaseId,
                mutationLeaseGeneration = retryAuthority.mutationLeaseGeneration,
                body = first.completeBody().copy(reps = first.completeBody().reps + 1),
            )

            val changedResponse = bridge.executeCommand(SOURCE_NODE, changedIntent)
            val afterChanged = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            assertEquals(
                CommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                ),
                changedResponse.outcome,
            )
            assertSyncUnchanged(beforeChanged, afterChanged)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

            val generic = bridge.activePayload()
            val genericAuthority = generic.mutationAuthority as MutationAuthority.Granted
            val oldCommandRebound = first.copy(
                correlationId = CanonicalUuid.random(),
                mutationLeaseId = genericAuthority.mutationLeaseId,
                mutationLeaseGeneration = genericAuthority.mutationLeaseGeneration,
            )
            val reboundResponse = bridge.executeCommand(SOURCE_NODE, oldCommandRebound)
            assertEquals(
                CommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                ),
                reboundResponse.outcome,
            )
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

            flaky.fail = false
            val newCommand = commandFrom(bridge.activePayload())
            assertNotEquals(first.commandId, newCommand.commandId)
            assertEquals(
                CommandOutcome.Applied,
                bridge.executeCommand(SOURCE_NODE, newCommand).outcome,
            )
            assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        }

    @Test
    fun `the command binds to the session the snapshot resolved, not to a second active row`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val request = commandFrom(bridge.activePayload())
            // Nothing enforces that only one session is IN_PROGRESS. The two reads used to be
            // independent unordered `LIMIT 1` queries over that predicate; a real database cannot
            // be made to answer them differently, because SQLite resolves both by rowid, so the
            // disagreement is interposed at the DAO — which is precisely the state the second
            // query made reachable.
            val decoy = seedSession(
                plans = listOf(plan(50.0, 3)),
                trainingName = "Decoy",
                exerciseName = "Row",
            )
            val divergent = PhoneWorkoutBridgeImpl(
                database = databaseWithSyncDao(
                    DivergentActiveSyncDao(database.wearSyncDao, decoy.sessionUuid),
                ),
                transition = env.transition,
                snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
                leaseStore = leaseStore,
                clock = clock,
                writers = WearMutationWriters(
                    RoomWearSetMutationWriter(database),
                    RoomWearSkipMutationWriter(database),
                ),
                knownRevisions = WatchKnownRevisions(),
                externalSessionWrites = ExternalSessionWrites(),
            )

            val response = divergent.executeCommand(SOURCE_NODE, request)

            assertEquals(CommandOutcome.Applied, response.outcome)
            // Revision, receipt and set all landed on the snapshot's session, and the decoy — the
            // row the second query would have returned — was never touched.
            val applied = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            assertEquals(request.commandId.value, applied.receiptCommandId)
            assertEquals(applied.revision, applied.receiptRevision)
            val untouched = requireNotNull(database.wearSyncDao.getSessionSync(decoy.sessionUuid))
            assertNull(untouched.receiptCommandId)
            assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertEquals(0, database.setDao.countByPerformedExercise(decoy.performedUuids.single()))
        }

    // region outline builder (wear-phase-2-exercise-list.md §6.1)

    @Test
    fun `the outline lists every performed exercise in phone order, skipped ones included`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)), secondPlans = listOf(plan(50.0, 8)))
        database.performedExerciseDao.setSkipped(seed.performedUuids.first(), true)

        val outline = bridge.activePayload()

        assertEquals(
            seed.performedUuids.map { it.toString() },
            outline.exercises.map { it.performedExerciseUuid.value },
        )
        assertEquals(listOf(true, false), outline.exercises.map(OutlineExercise::skipped))
        assertEquals(listOf("Deadlift", "Press"), outline.exercises.map { (it.name as BoundedDisplayName.Value).value })
    }

    @Test
    fun `done sets carry the performed values each converted on its own and open sets the plan`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.25, 6), plan(90.0, 7), plan(80.0, 8)))
        val performed = seed.performedUuids.single()
        database.setDao.upsertByTarget(Uuid.random(), performed, 0, reps = 7, weight = 102.5, type = SetTypeEntity.FAIL)
        database.setDao.upsertByTarget(Uuid.random(), performed, 1, reps = 6, weight = 1.234, type = SetTypeEntity.WORK)
        database.setDao.upsertByTarget(Uuid.random(), performed, 2, reps = 1_000, weight = 90.0, SetTypeEntity.WORK)

        val sets = bridge.activePayload().exercises.single().sets

        assertEquals(OutlineSet.Done(0, SetTypeWire.FAIL, reps = 7, weightHundredthsKg = 10_250), sets[0])
        assertEquals(OutlineSet.Done(1, SetTypeWire.WORK, reps = 6, weightHundredthsKg = null), sets[1])
        assertEquals(OutlineSet.Done(2, SetTypeWire.WORK, reps = null, weightHundredthsKg = 9_000), sets[2])
        assertEquals(OutlineSet.Open(3, SetTypeWire.WORK, reps = 8, weightHundredthsKg = 8_000), sets[3])
    }

    @Test
    fun `a planned value outside the watch domain makes a blocked set named by its field`() = runTest {
        seedSession(plans = listOf(plan(100.0, 5), plan(-0.0, 5), plan(50.0, 1_000), plan(1.234, 1_000)))

        val sets = bridge.activePayload().exercises.single().sets

        assertEquals(OutlineSet.Open(0, SetTypeWire.WORK, reps = 5, weightHundredthsKg = 10_000), sets[0])
        assertEquals(OutlineSet.Blocked(1, SetTypeWire.WORK, NumericField.WEIGHT), sets[1])
        assertEquals(OutlineSet.Blocked(2, SetTypeWire.WORK, NumericField.REPS), sets[2])
        assertEquals(OutlineSet.Blocked(3, SetTypeWire.WORK, NumericField.REPS), sets[3], "reps are checked first")
    }

    @Test
    fun `an exercise with no plan and no row has no sets`() = runTest {
        seedSession(plans = emptyList(), secondPlans = listOf(plan(50.0, 8)))

        val outline = bridge.activePayload()

        assertEquals(emptyList<OutlineSet>(), outline.exercises.first().sets)
        assertEquals(OutlineStatus.NO_SETS, OutlineRules.status(outline.exercises.first()))
    }

    @Test
    fun `a missing training plan falls back to the exercise's last ad hoc sets`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)), adhocPlans = listOf(plan(42.5, 4), plan(40.0, 6)))
        database.trainingExerciseDao.updatePlanSets(seed.trainingUuid, seed.exerciseUuids.single(), planSets = null)

        val sets = bridge.activePayload().exercises.single().sets

        assertEquals(
            listOf(
                OutlineSet.Open(0, SetTypeWire.WORK, reps = 4, weightHundredthsKg = 4_250),
                OutlineSet.Open(1, SetTypeWire.WORK, reps = 6, weightHundredthsKg = 4_000),
            ),
            sets,
        )
    }

    @Test
    fun `a performed position beyond the plan joins the plan positions`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val performed = seed.performedUuids.single()
        database.setDao.upsertByTarget(Uuid.random(), performed, 2, reps = 3, weight = 100.0, type = SetTypeEntity.DROP)

        val sets = bridge.activePayload().exercises.single().sets

        assertEquals(listOf(0, 2), sets.map(OutlineSet::position))
        assertTrue(sets[0] is OutlineSet.Open)
        assertEquals(OutlineSet.Done(2, SetTypeWire.DROP, reps = 3, weightHundredthsKg = 10_000), sets[1])
    }

    // endregion

    // region lease (§6.2, V3)

    @Test
    fun `a handshake for an all-finished outline grants a lease`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        database.setDao.upsertByTarget(Uuid.random(), seed.performedUuids.single(), 0, 5, 100.0, SetTypeEntity.WORK)

        val outline = bridge.activePayload()

        assertTrue(outline.isWorkoutComplete())
        assertTrue(outline.mutationAuthority is MutationAuthority.Granted)
        assertTrue(leaseStore.activeLeaseForTest(SOURCE_NODE, outline.sessionUuid) != null)
    }

    @Test
    fun `a command for any open set at the lease's revision is admitted`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6), plan(102.0, 7)))
        val outline = bridge.activePayload()
        val ruleR = commandFrom(outline)
        val third = ruleR.copy(body = ruleR.completeBody().copy(setPosition = 2, reps = 7, weightHundredthsKg = 10_200))

        val response = bridge.executeCommand(SOURCE_NODE, third)

        assertEquals(CommandOutcome.Applied, response.outcome)
        val rows = database.setDao.getByPerformedExercise(seed.performedUuids.single())
        assertEquals(listOf(2), rows.map { it.position })
    }

    @Test
    fun `a phone write of the skip flag retires the lease`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val outline = bridge.activePayload()
        assertTrue(leaseStore.activeLeaseForTest(SOURCE_NODE, outline.sessionUuid) != null)

        env.transition.mutate { database.performedExerciseDao.setSkipped(seed.performedUuids.single(), true) }

        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, outline.sessionUuid))
    }

    // endregion

    // region commands (§5.4)

    @Test
    fun `a complete_set for a done position is TargetChanged and writes nothing`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
        database.setDao.upsertByTarget(Uuid.random(), seed.performedUuids.single(), 0, 5, 100.0, SetTypeEntity.WORK)
        val outline = bridge.activePayload()
        val ruleR = commandFrom(outline)
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        val response = bridge.executeCommand(SOURCE_NODE, ruleR.copy(body = ruleR.completeBody().copy(setPosition = 0)))

        assertEquals(CommandOutcome.TargetChanged, response.outcome)
        assertTrue(response.replacement.payload is SnapshotPayload.Active)
        assertNothingWritten(before, requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)))
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `a complete_set for a skipped exercise is TargetChanged and writes nothing`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)), secondPlans = listOf(plan(50.0, 8)))
        database.performedExerciseDao.setSkipped(seed.performedUuids.first(), true)
        val outline = bridge.activePayload()
        val skipped = outline.exercises.first()
        val open = skipped.sets.single() as OutlineSet.Open
        val command = requestFrom(
            outline,
            WatchCommandBody.CompleteSet(
                skipped.performedExerciseUuid,
                open.position,
                open.reps,
                open.weightHundredthsKg,
                skipped.exerciseType,
                open.setType,
            ),
        )

        val response = bridge.executeCommand(SOURCE_NODE, command)

        assertEquals(CommandOutcome.TargetChanged, response.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.first()))
    }

    @Test
    fun `skip and return are applied, advance the revision, store the receipt and set the flag`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)), secondPlans = listOf(plan(50.0, 8)))
        val first = seed.performedUuids.first()
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        val skip = requestFrom(bridge.activePayload(), WatchCommandBody.SetExerciseSkipped(first.toWireUuid(), true))

        val skipped = bridge.executeCommand(SOURCE_NODE, skip)
        val afterSkip = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(CommandOutcome.Applied, skipped.outcome)
        assertTrue(database.performedExerciseDao.getBySession(seed.sessionUuid).first { it.uuid == first }.skipped)
        assertTrue(afterSkip.revision > before.revision)
        assertEquals(skip.commandId.value, afterSkip.receiptCommandId)
        assertEquals(afterSkip.revision, afterSkip.receiptRevision)
        val replacement = skipped.replacement.payload as SnapshotPayload.Active
        assertTrue(requireNotNull(replacement.exercise(first.toWireUuid())).skipped)
        assertTrue(replacement.mutationAuthority is MutationAuthority.Granted)
        assertEquals(0, database.setDao.countByPerformedExercise(first), "the flag only, no set")

        val giveBack = requestFrom(replacement, WatchCommandBody.SetExerciseSkipped(first.toWireUuid(), false))
        val returned = bridge.executeCommand(SOURCE_NODE, giveBack)

        assertEquals(CommandOutcome.Applied, returned.outcome)
        assertFalse(database.performedExerciseDao.getBySession(seed.sessionUuid).first { it.uuid == first }.skipped)
        assertTrue(requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)).revision > afterSkip.revision)
    }

    @Test
    fun `skipping a skipped exercise and returning one that is not skipped are TargetChanged`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)), secondPlans = listOf(plan(50.0, 8)))
        database.performedExerciseDao.setSkipped(seed.performedUuids.first(), true)
        val outline = bridge.activePayload()
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        val skipSkipped = bridge.executeCommand(
            SOURCE_NODE,
            requestFrom(outline, WatchCommandBody.SetExerciseSkipped(seed.performedUuids.first().toWireUuid(), true)),
        )
        val returnPending = bridge.executeCommand(
            SOURCE_NODE,
            requestFrom(
                bridge.activePayload(),
                WatchCommandBody.SetExerciseSkipped(seed.performedUuids.last().toWireUuid(), false),
            ),
        )

        assertEquals(CommandOutcome.TargetChanged, skipSkipped.outcome)
        assertEquals(CommandOutcome.TargetChanged, returnPending.outcome)
        val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        assertEquals(before.revision, after.revision)
        assertNull(after.receiptCommandId)
        val flags = database.performedExerciseDao.getBySession(seed.sessionUuid).map { it.skipped }
        assertEquals(listOf(true, false), flags)
    }

    @Test
    fun `a finished exercise can be skipped`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        database.setDao.upsertByTarget(Uuid.random(), seed.performedUuids.single(), 0, 5, 100.0, SetTypeEntity.WORK)
        val outline = bridge.activePayload()
        assertTrue(outline.isWorkoutComplete())

        val response = bridge.executeCommand(
            SOURCE_NODE,
            requestFrom(outline, WatchCommandBody.SetExerciseSkipped(seed.performedUuids.single().toWireUuid(), true)),
        )

        assertEquals(CommandOutcome.Applied, response.outcome)
        assertTrue(database.performedExerciseDao.getBySession(seed.sessionUuid).single().skipped)
    }

    @Test
    fun `a replayed skip is AlreadyApplied and changes nothing`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val skip = requestFrom(
            bridge.activePayload(),
            WatchCommandBody.SetExerciseSkipped(seed.performedUuids.single().toWireUuid(), true),
        )
        assertEquals(CommandOutcome.Applied, bridge.executeCommand(SOURCE_NODE, skip).outcome)
        val applied = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        val replay = bridge.executeCommand(SOURCE_NODE, skip.copy(correlationId = CanonicalUuid.random()))

        assertEquals(CommandOutcome.AlreadyApplied, replay.outcome)
        assertNothingWritten(applied, requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)))
        assertTrue(database.performedExerciseDao.getBySession(seed.sessionUuid).single().skipped)
    }

    @Test
    fun `a stored receipt of another command never affects a v2 command, whatever its version`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())
        val current = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        val v1Receipt = ByteArray(34).also { it[1] = 1 }
        database.wearSyncDao.storeReceipt(
            sessionUuid = seed.sessionUuid,
            commandId = CanonicalUuid.random().value,
            attemptFingerprint = v1Receipt,
            databaseEpoch = requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch,
            revision = current.revision,
        )

        val response = bridge.executeCommand(SOURCE_NODE, command)

        assertEquals(CommandOutcome.Applied, response.outcome)
        assertEquals(command.commandId.value, database.wearSyncDao.getSessionSync(seed.sessionUuid)?.receiptCommandId)
    }

    @Test
    fun `a skip whose write fails is retryable while still skippable and its retry is applied once`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val flaky = FlakySkipWriter(RoomWearSkipMutationWriter(database))
        bridge = newBridge(skipWriter = flaky)
        val first = requestFrom(
            bridge.activePayload(),
            WatchCommandBody.SetExerciseSkipped(seed.performedUuids.single().toWireUuid(), true),
        )

        val retryable = bridge.executeCommand(SOURCE_NODE, first)
        val successor = (retryable.replacement.payload as SnapshotPayload.Active).mutationAuthority
            as MutationAuthority.Granted
        flaky.fail = false
        val applied = bridge.executeCommand(
            SOURCE_NODE,
            first.copy(
                correlationId = CanonicalUuid.random(),
                mutationLeaseId = successor.mutationLeaseId,
                mutationLeaseGeneration = successor.mutationLeaseGeneration,
            ),
        )

        assertEquals(CommandOutcome.RetryableTemporaryFailure, retryable.outcome)
        assertEquals(CommandOutcome.Applied, applied.outcome)
        assertTrue(database.performedExerciseDao.getBySession(seed.sessionUuid).single().skipped)
    }

    @Test
    fun `a skip whose write fails after the phone ended the session is NoActiveSession`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val hooked = HookedMutateTransition(env.transition)
        val flaky = FlakySkipWriter(RoomWearSkipMutationWriter(database)).apply {
            onFailure = {
                hooked.beforeNextMutate = {
                    env.transition.mutate { database.sessionDao.finishSession(seed.sessionUuid, finishedAt = 2) }
                }
            }
        }
        bridge = newBridge(transition = hooked, skipWriter = flaky)
        val skip = requestFrom(
            bridge.activePayload(),
            WatchCommandBody.SetExerciseSkipped(seed.performedUuids.single().toWireUuid(), true),
        )

        val response = bridge.executeCommand(SOURCE_NODE, skip)

        assertEquals(CommandOutcome.NoActiveSession, response.outcome)
        assertTrue(response.replacement.payload is SnapshotPayload.NoSession)
        assertFalse(database.performedExerciseDao.getBySession(seed.sessionUuid).single().skipped)
    }

    @Test
    fun `a set whose write fails after a phone edit is StaleRevision`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val hooked = HookedMutateTransition(env.transition)
        val flaky = FlakyWriter(RoomWearSetMutationWriter(database)).apply {
            onFailure = {
                hooked.beforeNextMutate = {
                    env.transition.mutate {
                        database.trainingExerciseDao.updatePlanSets(
                            trainingUuid = seed.trainingUuid,
                            exerciseUuid = seed.exerciseUuids.single(),
                            planSets = PlanSetsConverter.toJson(listOf(plan(105.0, 4))),
                        )
                    }
                }
            }
        }
        bridge = newBridge(writer = flaky, transition = hooked)
        val command = commandFrom(bridge.activePayload())

        val response = bridge.executeCommand(SOURCE_NODE, command)

        assertEquals(CommandOutcome.StaleRevision, response.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `a publication lost at the same revision is AuthorizationExpired`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val outline = bridge.activePayload()
        val unknown = commandFrom(outline).let { it.copy(body = it.completeBody().copy(setPosition = 4)) }
        // Another handshake allocates a later lease generation at the same revision before this
        // command's lease is published.
        val racing = OneShotInvokeRaceTransition(env.transition) {
            env.transition.mutate {
                database.wearSyncDao.incrementLeaseGeneration(seed.sessionUuid, outline.sessionRevision)
            }
        }

        val response = newBridge(transition = racing).executeCommand(SOURCE_NODE, unknown)

        assertEquals(CommandOutcome.AuthorizationExpired, response.outcome)
        val replacement = response.replacement.payload as SnapshotPayload.Active
        assertTrue(replacement.mutationAuthority is MutationAuthority.Unavailable)
    }

    // endregion

    // region size on response paths (§5.7, F27)

    @Test
    fun `a session that grows past the budget gets PayloadTooLarge on every path and no path throws`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val early = commandFrom(bridge.activePayload())
        env.transition.mutate {
            database.trainingExerciseDao.updatePlanSets(
                trainingUuid = seed.trainingUuid,
                exerciseUuid = seed.exerciseUuids.single(),
                planSets = PlanSetsConverter.toJson(List(250) { plan(999.99, 999) }),
            )
        }

        val handshake = bridge.snapshot()
        assertTrue(handshake.snapshot.payload is SnapshotPayload.PayloadTooLarge)
        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, early.sessionUuid))

        val stale = bridge.executeCommand(SOURCE_NODE, early.copy(correlationId = CanonicalUuid.random()))
        assertEquals(CommandOutcome.StaleRevision, stale.outcome)
        assertTrue(stale.replacement.payload is SnapshotPayload.PayloadTooLarge)

        val responses = listOf(
            early.copy(body = early.completeBody().copy(reps = 0)),
            early.copy(
                body = early.completeBody().copy(exerciseType = ExerciseTypeWire.WEIGHTLESS, weightHundredthsKg = null),
            ),
            early.copy(body = WatchCommandBody.SetExerciseSkipped(early.body.performedExerciseUuid, true)),
        ).map { request ->
            // Three independent commands, each on its own fresh lease.
            bridge.executeCommand(SOURCE_NODE, bindSyntheticLease(request.copy(commandId = CanonicalUuid.random())))
        }
        responses.forEach { response ->
            assertEquals(CommandOutcome.TargetChanged, response.outcome, "InvalidValues and ImmutableTypeMismatch too")
            assertTrue(response.replacement.payload is SnapshotPayload.PayloadTooLarge)
            WearProtocolCodec.encode(response)
        }

        val rejected = bridge.protocolRejected(
            SOURCE_NODE,
            CommandRouting(
                schemaVersion = WearProtocol.SCHEMA_VERSION,
                correlationId = CanonicalUuid.random(),
                commandId = CanonicalUuid.random(),
                databaseEpoch = early.databaseEpoch,
                sessionUuid = early.sessionUuid,
                sessionRevision = early.sessionRevision,
                mutationLeaseId = early.mutationLeaseId,
                mutationLeaseGeneration = early.mutationLeaseGeneration,
            ),
            ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
        )
        assertTrue(rejected.replacement.payload is SnapshotPayload.PayloadTooLarge)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertFalse(database.performedExerciseDao.getBySession(seed.sessionUuid).single().skipped)
    }

    // endregion

    /** Only `wearSyncDao` is interposed; every other read the bridge makes stays on the real db. */
    private fun databaseWithSyncDao(dao: WearSyncDao): AppDatabase = mockk<AppDatabase> {
        every { wearSyncDao } returns dao
        every { performedExerciseDao } returns database.performedExerciseDao
        every { setDao } returns database.setDao
    }

    private fun newBridge(
        writer: WearSetMutationWriter = RoomWearSetMutationWriter(database),
        transition: DbTransitionRunner = env.transition,
        skipWriter: WearSkipMutationWriter = RoomWearSkipMutationWriter(database),
    ): PhoneWorkoutBridgeImpl = PhoneWorkoutBridgeImpl(
        database = database,
        transition = transition,
        snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
        leaseStore = leaseStore,
        clock = clock,
        writers = WearMutationWriters(writer, skipWriter),
        knownRevisions = WatchKnownRevisions(),
        externalSessionWrites = ExternalSessionWrites(),
    )

    private suspend fun PhoneWorkoutBridgeImpl.snapshot() = getActiveWorkout(
        SOURCE_NODE,
        GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random()),
    )

    private suspend fun PhoneWorkoutBridgeImpl.activePayload(): SnapshotPayload.Active =
        snapshot().snapshot.payload as SnapshotPayload.Active

    /** A `complete_set` for rule R's set of [active], under its granted session lease. */
    private suspend fun commandFrom(
        active: SnapshotPayload.Active,
    ): WatchCommandRequest = requestFrom(
        active,
        WatchCommandBody.CompleteSet(
            performedExerciseUuid = active.target.performedExerciseUuid,
            setPosition = active.target.setPosition,
            reps = active.target.reps.coerceAtLeast(1),
            weightHundredthsKg = active.target.weightHundredthsKg,
            exerciseType = active.target.exerciseType,
            setType = active.target.setType,
        ),
    )

    /** [body] under [active]'s granted session lease (wear-phase-2-exercise-list.md §6.2). */
    private suspend fun requestFrom(
        active: SnapshotPayload.Active,
        body: WatchCommandBody,
    ): WatchCommandRequest {
        val authority = active.mutationAuthority as MutationAuthority.Granted
        return WatchCommandRequest(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = CanonicalUuid.random(),
            commandId = CanonicalUuid.random(),
            databaseEpoch = CanonicalUuid.parse(
                requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch,
            ),
            sessionUuid = active.sessionUuid,
            sessionRevision = active.sessionRevision,
            mutationLeaseId = authority.mutationLeaseId,
            mutationLeaseGeneration = authority.mutationLeaseGeneration,
            body = body,
        )
    }

    private fun WatchCommandRequest.completeBody(): WatchCommandBody.CompleteSet = body as WatchCommandBody.CompleteSet

    /** Installs a valid current-version session lease for a request built at an earlier revision. */
    private suspend fun bindSyntheticLease(
        request: WatchCommandRequest,
    ): WatchCommandRequest {
        val sync = requireNotNull(
            database.wearSyncDao.getSessionSync(Uuid.parse(request.sessionUuid.value)),
        )
        assertEquals(
            1,
            env.transition.mutate {
                database.wearSyncDao.incrementLeaseGeneration(sync.sessionUuid, sync.revision)
            },
        )
        val current = requireNotNull(database.wearSyncDao.getSessionSync(sync.sessionUuid))
        val leaseId = CanonicalUuid.random()
        val bound = request.copy(
            correlationId = CanonicalUuid.random(),
            databaseEpoch = CanonicalUuid.parse(
                requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch,
            ),
            sessionRevision = current.revision,
            mutationLeaseId = leaseId,
            mutationLeaseGeneration = current.leaseGeneration,
        )
        assertTrue(
            leaseStore.publish(
                PendingMutationLease(
                    sourceNodeId = SOURCE_NODE,
                    sessionUuid = bound.sessionUuid,
                    databaseEpoch = bound.databaseEpoch,
                    sessionRevision = bound.sessionRevision,
                    leaseId = leaseId,
                    leaseGeneration = bound.mutationLeaseGeneration,
                    leaseRemainingAtPhoneSendMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
                    expiresAtPhoneElapsedRealtimeMs = clock.now + WearProtocol.MAX_MUTATION_WINDOW_MS,
                ),
            ),
        )
        return bound
    }

    private suspend fun seedSession(
        plans: List<PlanSetDataModel>,
        secondPlans: List<PlanSetDataModel>? = null,
        exerciseType: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
        trainingName: String = "Strength",
        exerciseName: String = "Deadlift",
        isAdhoc: Boolean = false,
        adhocPlans: List<PlanSetDataModel>? = null,
    ): Seed {
        val training = TrainingEntity(
            name = trainingName,
            description = null,
            isAdhoc = isAdhoc,
            archived = false,
            createdAt = 1,
            archivedAt = null,
        )
        database.trainingDao.insert(training)
        val exercises = buildList {
            add(exercise(exerciseName, exerciseType, adhocPlans))
            if (secondPlans != null) add(exercise("Press", ExerciseTypeEntity.WEIGHTED))
        }
        exercises.forEach { database.exerciseDao.insert(it) }
        database.trainingExerciseDao.insert(
            exercises.mapIndexed { index, exercise ->
                TrainingExerciseEntity(
                    trainingUuid = training.uuid,
                    exerciseUuid = exercise.uuid,
                    position = index,
                    planSets = PlanSetsConverter.toJson(if (index == 0) plans else secondPlans),
                )
            },
        )
        val session = SessionEntity(
            trainingUuid = training.uuid,
            state = SessionStateEntity.IN_PROGRESS,
            startedAt = 1,
            finishedAt = null,
        )
        database.sessionDao.insert(session)
        val performed = exercises.mapIndexed { index, exercise ->
            PerformedExerciseEntity(
                sessionUuid = session.uuid,
                exerciseUuid = exercise.uuid,
                position = index,
                skipped = false,
            )
        }
        database.performedExerciseDao.insert(performed)
        return Seed(
            sessionUuid = session.uuid,
            performedUuids = performed.map(PerformedExerciseEntity::uuid),
            trainingUuid = training.uuid,
            exerciseUuids = exercises.map(ExerciseEntity::uuid),
        )
    }

    private fun exercise(
        name: String,
        type: ExerciseTypeEntity,
        adhocPlans: List<PlanSetDataModel>? = null,
    ) = ExerciseEntity(
        name = name,
        type = type,
        description = null,
        imagePath = null,
        archived = false,
        createdAt = 1,
        archivedAt = null,
        lastAdhocSets = PlanSetsConverter.toJson(adhocPlans),
    )

    private fun plan(weight: Double?, reps: Int) = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = SetTypeDataModel.WORK,
    )

    /** No write and no receipt: the revision and the receipt stay; the replacement may grant a new lease. */
    private fun assertNothingWritten(expected: SessionWearSyncRow, actual: SessionWearSyncRow) {
        assertEquals(expected.revision, actual.revision)
        assertEquals(expected.receiptCommandId, actual.receiptCommandId)
        assertEquals(expected.receiptRevision, actual.receiptRevision)
    }

    private fun assertSyncUnchanged(
        expected: io.github.stslex.workeeper.core.data.database.wear.SessionWearSyncRow,
        actual: io.github.stslex.workeeper.core.data.database.wear.SessionWearSyncRow,
    ) {
        assertEquals(expected.revision, actual.revision)
        assertEquals(expected.leaseGeneration, actual.leaseGeneration)
        assertEquals(expected.receiptCommandId, actual.receiptCommandId)
        if (expected.receiptAttemptFingerprint == null) {
            assertNull(actual.receiptAttemptFingerprint)
        } else {
            assertArrayEquals(expected.receiptAttemptFingerprint, actual.receiptAttemptFingerprint)
        }
    }

    private data class Seed(
        val sessionUuid: Uuid,
        val performedUuids: List<Uuid>,
        val trainingUuid: Uuid,
        val exerciseUuids: List<Uuid>,
    )

    private data class InvalidCommandCase(
        val reps: Int? = null,
        val weightHundredthsKg: Int? = 10_000,
        val commandExerciseType: ExerciseTypeWire = ExerciseTypeWire.WEIGHTED,
        val seedExerciseType: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
        val expected: CommandOutcome,
    )

    private data class ValidCommandCase(
        val reps: Int,
        val weightHundredthsKg: Int?,
        val exerciseType: ExerciseTypeEntity = ExerciseTypeEntity.WEIGHTED,
    )

    /** Answers the unordered active-session query with a DIFFERENT session than `getActive`. */
    private class DivergentActiveSyncDao(
        private val delegate: WearSyncDao,
        private val divergentSessionUuid: Uuid,
    ) : WearSyncDao by delegate {

        override suspend fun getActiveSessionSync(): SessionWearSyncRow? =
            delegate.getSessionSync(divergentSessionUuid)
    }

    private class FakeClock(var now: Long = 0L) : PhoneMonotonicClock {
        override fun elapsedRealtimeMs(): Long = now
    }

    private class FlakyWriter(
        private val delegate: WearSetMutationWriter,
    ) : WearSetMutationWriter {
        var fail = true
        var onFailure: () -> Unit = {}
        override suspend fun write(value: WearSetWrite) {
            if (fail) {
                onFailure()
                throw SQLiteException("synthetic busy")
            }
            delegate.write(value)
        }
    }

    private class FlakySkipWriter(
        private val delegate: WearSkipMutationWriter,
    ) : WearSkipMutationWriter {
        var fail = true
        var onFailure: () -> Unit = {}
        override suspend fun write(performedExerciseUuid: Uuid, skipped: Boolean) {
            if (fail) {
                onFailure()
                throw SQLiteException("synthetic busy")
            }
            delegate.write(performedExerciseUuid, skipped)
        }
    }

    /** Runs [beforeNextMutate] once, before the next `mutate`: between a failed write and its re-read. */
    private class HookedMutateTransition(private val delegate: DbTransitionRunner) : DbTransitionRunner {
        var beforeNextMutate: (suspend () -> Unit)? = null

        override suspend fun <T> invoke(block: suspend CoroutineScope.() -> T): T = delegate(block)

        override suspend fun <T> mutate(block: suspend CoroutineScope.() -> T): T {
            beforeNextMutate?.let { hook ->
                beforeNextMutate = null
                hook()
            }
            return delegate.mutate(block)
        }

        override fun addAfterMutationCommitListener(listener: () -> Unit) {
            delegate.addAfterMutationCommitListener(listener)
        }
    }

    private class OneShotInvokeRaceTransition(
        private val delegate: DbTransitionRunner,
        private val beforeInvoke: suspend () -> Unit,
    ) : DbTransitionRunner {
        private var pending = true

        override suspend fun <T> invoke(block: suspend CoroutineScope.() -> T): T {
            if (pending) {
                pending = false
                beforeInvoke()
            }
            return delegate(block)
        }

        override suspend fun <T> mutate(block: suspend CoroutineScope.() -> T): T =
            delegate.mutate(block)

        override fun addAfterMutationCommitListener(listener: () -> Unit) {
            delegate.addAfterMutationCommitListener(listener)
        }
    }

    private companion object {
        const val SOURCE_NODE: String = "synthetic-watch-node"
    }
}

private fun ExerciseTypeEntity.toWire(): ExerciseTypeWire = when (this) {
    ExerciseTypeEntity.WEIGHTED -> ExerciseTypeWire.WEIGHTED
    ExerciseTypeEntity.WEIGHTLESS -> ExerciseTypeWire.WEIGHTLESS
}
