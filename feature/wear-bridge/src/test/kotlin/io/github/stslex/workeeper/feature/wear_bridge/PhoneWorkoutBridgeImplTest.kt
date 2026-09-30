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
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetBody
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ImmutableTypeField
import io.github.stslex.workeeper.core.wear.protocol.InvalidValueReason
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.PhoneActionReason
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
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

            val active = bridge.snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget

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

        val payload = bridge.snapshot().snapshot.payload as SnapshotPayload.PhoneActionRequired
        val reason = payload.reason as PhoneActionReason.NoSetRows

        assertEquals(seed.performedUuids.first().toString(), reason.performedExerciseUuid.value)
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

            val payload = bridge.snapshot().snapshot.payload

            when (scenario.expectedBridgeState) {
                WorkoutTargetParityFixture.BridgeState.ACTIVE_TARGET -> assertEquals(
                    scenario.expectedTargetPosition,
                    (payload as SnapshotPayload.ActiveWithTarget).target.setPosition,
                    scenario.name,
                )
                WorkoutTargetParityFixture.BridgeState.PHONE_ACTION_REQUIRED -> assertTrue(
                    (payload as SnapshotPayload.PhoneActionRequired).reason is PhoneActionReason.NoSetRows,
                    scenario.name,
                )
                WorkoutTargetParityFixture.BridgeState.WORKOUT_COMPLETE -> assertTrue(
                    payload is SnapshotPayload.WorkoutComplete,
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
            val weightless = bridge.snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget
            assertNull(weightless.target.weightHundredthsKg)

            resetEnvironment()
            val seed = seedSession(plans = listOf(plan(-0.0, 9)))
            val unsupported = bridge.snapshot().snapshot.payload as SnapshotPayload.PhoneActionRequired
            val reason = unsupported.reason as PhoneActionReason.UnsupportedNumericValues
            assertEquals(NumericField.WEIGHT, reason.field)
            assertEquals(seed.performedUuids.single().toString(), reason.performedExerciseUuid.value)
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

        val payload = bridge.snapshot().snapshot.payload as SnapshotPayload.PhoneActionRequired
        val reason = payload.reason as PhoneActionReason.UnsupportedNumericValues

        assertEquals(NumericField.WEIGHT, reason.field)
        assertEquals(seed.performedUuids.first().toString(), reason.performedExerciseUuid.value)
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
        val command = commandFrom(snapshot.snapshot.payload as SnapshotPayload.ActiveWithTarget)

        val applied = bridge.completeCurrentSet(SOURCE_NODE, command)
        val sameDelivery = bridge.completeCurrentSet(SOURCE_NODE, command)
        val replay = bridge.completeCurrentSet(
            SOURCE_NODE,
            command.copy(correlationId = CanonicalUuid.random()),
        )
        val sync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(CompleteCommandOutcome.Applied, applied.outcome)
        assertEquals(applied, sameDelivery)
        assertEquals(CompleteCommandOutcome.AlreadyApplied, replay.outcome)
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertEquals(command.commandId.value, sync.receiptCommandId)
        assertEquals(34, sync.receiptAttemptFingerprint?.size)
        assertEquals(sync.revision, sync.receiptRevision)
        assertTrue(applied.replacement.payload is SnapshotPayload.WorkoutComplete)
    }

    @Test
    fun `reused correlation with changed command body is rejected instead of replaying success`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val original = commandFrom(bridge.activePayload())
            val applied = bridge.completeCurrentSet(SOURCE_NODE, original)
            val changed = original.copy(
                body = original.body.copy(reps = original.body.reps + 1),
            )

            val rejected = bridge.completeCurrentSet(SOURCE_NODE, changed)

            assertEquals(
                CompleteCommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                ),
                rejected.outcome,
            )
            assertEquals(original.correlationId, rejected.correlationId)
            assertEquals(original.commandId, rejected.commandId)
            assertTrue(
                (rejected.replacement.payload as SnapshotPayload.ActiveWithTarget)
                    .mutationAuthority is MutationAuthority.Unavailable,
            )
            assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertEquals(CompleteCommandOutcome.Applied, applied.outcome)
        }

    @Test
    fun `different commands for one target have one applied winner and one stale loser`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.activePayload()
        val first = commandFrom(active)
        val second = commandFrom(active).copy(correlationId = CanonicalUuid.random())

        val responses = coroutineScope {
            listOf(
                async { bridge.completeCurrentSet(SOURCE_NODE, first) },
                async { bridge.completeCurrentSet(SOURCE_NODE, second) },
            ).awaitAll()
        }
        val sync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(1, responses.count { it.outcome == CompleteCommandOutcome.Applied })
        assertEquals(1, responses.count { it.outcome == CompleteCommandOutcome.StaleRevision })
        assertTrue(responses.all { it.replacement.payload is SnapshotPayload.WorkoutComplete })
        assertTrue(
            responses.all { response ->
                (response.replacement.payload as SnapshotPayload.WorkoutComplete).sessionRevision ==
                    sync.revision
            },
        )
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `different concurrent commands and a phone race persist at most one target row`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget
        val first = commandFrom(active)
        val second = commandFrom(active).copy(correlationId = CanonicalUuid.random())

        val responses = coroutineScope {
            listOf(
                async { bridge.completeCurrentSet(SOURCE_NODE, first) },
                async { bridge.completeCurrentSet(SOURCE_NODE, second) },
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
                response.outcome == CompleteCommandOutcome.Applied ||
                    response.outcome == CompleteCommandOutcome.StaleRevision
            },
        )
        assertTrue(responses.count { it.outcome == CompleteCommandOutcome.Applied } <= 1)
        val converged = bridge.snapshot().snapshot.payload as SnapshotPayload.WorkoutComplete
        val sync = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        assertEquals(sync.revision, converged.sessionRevision)
        assertEquals(1, database.setDao.getByPerformedExercise(seed.performedUuids.single()).size)
    }

    @Test
    fun `complete then phone undo rejects the original command after ABA`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())
        assertEquals(CompleteCommandOutcome.Applied, bridge.completeCurrentSet(SOURCE_NODE, command).outcome)
        env.transition.mutate {
            database.setDao.deleteByPerformedAndPosition(seed.performedUuids.single(), 0)
        }

        val replay = bridge.completeCurrentSet(
            SOURCE_NODE,
            command.copy(correlationId = CanonicalUuid.random()),
        )

        assertEquals(CompleteCommandOutcome.StaleRevision, replay.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertNull(database.wearSyncDao.getSessionSync(seed.sessionUuid)?.receiptCommandId)
    }

    @Test
    fun `lease admits at 119999ms and rejects at the exact 120000ms boundary`() = runTest {
        seedSession(plans = listOf(plan(100.0, 5)))
        val accepted = commandFrom(bridge.activePayload())
        clock.now = WearProtocol.MAX_MUTATION_WINDOW_MS - 1
        assertEquals(
            CompleteCommandOutcome.Applied,
            bridge.completeCurrentSet(SOURCE_NODE, accepted).outcome,
        )

        resetEnvironment()
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val expired = commandFrom(bridge.activePayload())
        clock.now = WearProtocol.MAX_MUTATION_WINDOW_MS
        val response = bridge.completeCurrentSet(SOURCE_NODE, expired)
        assertEquals(CompleteCommandOutcome.AuthorizationExpired, response.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

        val successor = response.replacement.payload as SnapshotPayload.ActiveWithTarget
        val successorAuthority = successor.mutationAuthority as MutationAuthority.Granted
        val forbiddenRebind = expired.copy(
            correlationId = CanonicalUuid.random(),
            mutationLeaseId = successorAuthority.mutationLeaseId,
            mutationLeaseGeneration = successorAuthority.mutationLeaseGeneration,
        )
        assertEquals(
            CompleteCommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
            ),
            bridge.completeCurrentSet(SOURCE_NODE, forbiddenRebind).outcome,
        )
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `phone process restart loses leases and allocates a durable higher successor`() = runTest {
        seedSession(plans = listOf(plan(100.0, 5)))
        val before = bridge.snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget
        val beforeAuthority = before.mutationAuthority as MutationAuthority.Granted

        leaseStore = WearMutationLeaseStore(env.transition)
        bridge = newBridge()
        val after = bridge.snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget
        val afterAuthority = after.mutationAuthority as MutationAuthority.Granted
        val oldCommand = commandFrom(before)

        assertTrue(afterAuthority.mutationLeaseGeneration > beforeAuthority.mutationLeaseGeneration)
        assertNotEquals(beforeAuthority.mutationLeaseId, afterAuthority.mutationLeaseId)
        assertEquals(
            CompleteCommandOutcome.AuthorizationExpired,
            bridge.completeCurrentSet(SOURCE_NODE, oldCommand).outcome,
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
            val firstPayload = first.snapshot.payload as SnapshotPayload.ActiveWithTarget
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
                val active = response.snapshot.payload as SnapshotPayload.ActiveWithTarget
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

            val oldResponse = bridge.completeCurrentSet(SOURCE_NODE, commandFrom(firstPayload))
            assertEquals(CompleteCommandOutcome.AuthorizationExpired, oldResponse.outcome)
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
            val payload = response.snapshot.payload as SnapshotPayload.ActiveWithTarget

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

            val response = racingBridge.completeCurrentSet(SOURCE_NODE, command)
            val replacement = response.replacement.payload as SnapshotPayload.ActiveWithTarget

            assertEquals(CompleteCommandOutcome.Applied, response.outcome)
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

            val response = racingBridge.completeCurrentSet(SOURCE_NODE, command)
            val replacement = response.replacement.payload as SnapshotPayload.ActiveWithTarget

            assertEquals(CompleteCommandOutcome.StaleRevision, response.outcome)
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
                    body = current.body.copy(performedExerciseUuid = CanonicalUuid.random()),
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
                .completeCurrentSet(SOURCE_NODE, command)
            val replacement = response.replacement.payload as SnapshotPayload.ActiveWithTarget

            assertEquals(CompleteCommandOutcome.StaleRevision, response.outcome)
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
                    body = current.body.copy(performedExerciseUuid = CanonicalUuid.random()),
                )
            }
            val racingTransition = OneShotInvokeRaceTransition(env.transition) {
                env.transition.mutate {
                    database.sessionDao.finishSession(seed.sessionUuid, finishedAt = 2)
                }
            }

            val response = newBridge(transition = racingTransition)
                .completeCurrentSet(SOURCE_NODE, command)

            assertEquals(CompleteCommandOutcome.NoActiveSession, response.outcome)
            assertTrue(response.replacement.payload is SnapshotPayload.NoSession)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            WearProtocolCodec.encode(response)
        }

    @Test
    fun `wrong source node cannot consume another node lease`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val active = bridge.activePayload()
        val command = commandFrom(active)

        val response = bridge.completeCurrentSet("different-watch-node", command)

        assertEquals(CompleteCommandOutcome.AuthorizationExpired, response.outcome)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, command.sessionUuid))
    }

    @Test
    fun `exact durable receipt remains idempotent after the original lease expires`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
        val command = commandFrom(bridge.activePayload())
        val applied = bridge.completeCurrentSet(SOURCE_NODE, command)
        val afterApplied = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        clock.now = WearProtocol.MAX_MUTATION_WINDOW_MS

        val replay = bridge.completeCurrentSet(
            SOURCE_NODE,
            command.copy(correlationId = CanonicalUuid.random()),
        )
        val afterReplay = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(CompleteCommandOutcome.Applied, applied.outcome)
        assertEquals(CompleteCommandOutcome.AlreadyApplied, replay.outcome)
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
                body = commandFrom(active).body.copy(reps = 0),
            )

            val invalidResponse = bridge.completeCurrentSet(SOURCE_NODE, invalid)
            val afterInvalid = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            assertEquals(
                CompleteCommandOutcome.InvalidValues(NumericField.REPS, InvalidValueReason.BELOW_MINIMUM),
                invalidResponse.outcome,
            )
            assertTrue(
                (invalidResponse.replacement.payload as SnapshotPayload.ActiveWithTarget)
                    .mutationAuthority is MutationAuthority.Unavailable,
            )
            assertSyncUnchanged(before, afterInvalid)
            assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, active.sessionUuid))

            val refreshed = bridge.snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget
            val mismatch = commandFrom(refreshed).copy(
                body = commandFrom(refreshed).body.copy(
                    exerciseType = ExerciseTypeWire.WEIGHTLESS,
                ),
            )
            val mismatchResponse = bridge.completeCurrentSet(SOURCE_NODE, mismatch)
            assertEquals(
                CompleteCommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE),
                mismatchResponse.outcome,
            )
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertFalse(
                (mismatchResponse.replacement.payload as SnapshotPayload.ActiveWithTarget)
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
            body = original.body.copy(setType = SetTypeWire.FAIL),
        )

        val response = bridge.completeCurrentSet(SOURCE_NODE, mismatch)
        val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(
            CompleteCommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.SET_TYPE),
            response.outcome,
        )
        assertSyncUnchanged(before, after)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
        assertTrue(
            (response.replacement.payload as SnapshotPayload.ActiveWithTarget)
                .mutationAuthority is MutationAuthority.Unavailable,
        )
        assertNull(leaseStore.activeLeaseForTest(SOURCE_NODE, active.sessionUuid))
    }

    @Test
    fun `terminal command id cannot bind to a later generic successor lease`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val firstActive = bridge.activePayload()
        val valid = commandFrom(firstActive)
        val invalid = valid.copy(body = valid.body.copy(reps = 0))
        val invalidResponse = bridge.completeCurrentSet(SOURCE_NODE, invalid)
        assertEquals(
            CompleteCommandOutcome.InvalidValues(
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

        val rejected = bridge.completeCurrentSet(SOURCE_NODE, genericRebind)
        val afterRebind = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(
            CompleteCommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
            ),
            rejected.outcome,
        )
        assertSyncUnchanged(beforeRebind, afterRebind)
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

        val corrected = commandFrom(bridge.activePayload())
        assertNotEquals(valid.commandId, corrected.commandId)
        assertEquals(
            CompleteCommandOutcome.Applied,
            bridge.completeCurrentSet(SOURCE_NODE, corrected).outcome,
        )
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `all numeric gateway boundaries return typed outcomes without invalid writes`() = runTest {
        val invalidCases = listOf(
            InvalidCommandCase(
                reps = 0,
                expected = CompleteCommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
            ),
            InvalidCommandCase(
                reps = -1,
                expected = CompleteCommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
            ),
            InvalidCommandCase(
                reps = 1_000,
                expected = CompleteCommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.ABOVE_MAXIMUM,
                ),
            ),
            InvalidCommandCase(
                weightHundredthsKg = -1,
                expected = CompleteCommandOutcome.InvalidValues(
                    NumericField.WEIGHT,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
            ),
            InvalidCommandCase(
                weightHundredthsKg = 100_000,
                expected = CompleteCommandOutcome.InvalidValues(
                    NumericField.WEIGHT,
                    InvalidValueReason.ABOVE_MAXIMUM,
                ),
            ),
            InvalidCommandCase(
                weightHundredthsKg = 0,
                commandExerciseType = ExerciseTypeWire.WEIGHTLESS,
                seedExerciseType = ExerciseTypeEntity.WEIGHTLESS,
                expected = CompleteCommandOutcome.InvalidValues(
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
                body = original.body.copy(
                    reps = case.reps ?: 5,
                    weightHundredthsKg = case.weightHundredthsKg,
                    exerciseType = case.commandExerciseType,
                ),
            )
            val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

            val response = bridge.completeCurrentSet(SOURCE_NODE, command)
            val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

            assertEquals(case.expected, response.outcome, "invalid case $case")
            assertSyncUnchanged(before, after)
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
            assertTrue(
                (response.replacement.payload as SnapshotPayload.ActiveWithTarget)
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
                body = original.body.copy(
                    reps = case.reps,
                    weightHundredthsKg = case.weightHundredthsKg,
                    exerciseType = case.exerciseType.toWire(),
                ),
            )

            val response = bridge.completeCurrentSet(SOURCE_NODE, command)
            val row = database.setDao.getByPerformedExercise(seed.performedUuids.single()).single()

            assertEquals(CompleteCommandOutcome.Applied, response.outcome, "valid case $case")
            assertEquals(case.reps, row.reps)
            assertEquals(case.weightHundredthsKg?.toDouble()?.div(100.0), row.weight)
        }
    }

    @Test
    fun `database epoch and active identity ordering precede revision and receipts`() = runTest {
        val sessionA = seedSession(plans = listOf(plan(100.0, 5)))
        val commandA = commandFrom(bridge.activePayload())
        val wrongEpoch = bridge.completeCurrentSet(
            SOURCE_NODE,
            commandA.copy(
                correlationId = CanonicalUuid.random(),
                databaseEpoch = CanonicalUuid.random(),
            ),
        )
        assertEquals(CompleteCommandOutcome.StaleRevision, wrongEpoch.outcome)

        database.sessionDao.finishSession(sessionA.sessionUuid, finishedAt = 2)
        val noSession = bridge.completeCurrentSet(
            SOURCE_NODE,
            commandA.copy(correlationId = CanonicalUuid.random()),
        )
        assertEquals(CompleteCommandOutcome.NoActiveSession, noSession.outcome)
        assertTrue(noSession.replacement.payload is SnapshotPayload.NoSession)

        val sessionB = seedSession(
            plans = listOf(plan(60.0, 10)),
            trainingName = "Strength B",
            exerciseName = "Press",
        )
        val delayedA = bridge.completeCurrentSet(
            SOURCE_NODE,
            commandA.copy(correlationId = CanonicalUuid.random()),
        )
        val replacementB = delayedA.replacement.payload as SnapshotPayload.ActiveWithTarget
        assertEquals(CompleteCommandOutcome.StaleRevision, delayedA.outcome)
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
                body = wrongBase.body.copy(performedExerciseUuid = CanonicalUuid.random()),
            )
            val wrongResponse = bridge.completeCurrentSet(SOURCE_NODE, wrongExercise)
            val wrongReplacement =
                wrongResponse.replacement.payload as SnapshotPayload.ActiveWithTarget
            assertEquals(CompleteCommandOutcome.TargetChanged, wrongResponse.outcome)
            assertEquals(
                wrongSeed.performedUuids.single().toString(),
                wrongReplacement.target.performedExerciseUuid.value,
            )

            resetEnvironment()
            seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val stalePositionBase = commandFrom(bridge.activePayload())
            val stalePosition = bindSyntheticLease(
                stalePositionBase.copy(
                    body = stalePositionBase.body.copy(setPosition = 1),
                ),
            )
            val movedResponse = bridge.completeCurrentSet(SOURCE_NODE, stalePosition)
            val movedReplacement =
                movedResponse.replacement.payload as SnapshotPayload.ActiveWithTarget
            assertEquals(CompleteCommandOutcome.TargetChanged, movedResponse.outcome)
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
            val completedResponse = bridge.completeCurrentSet(SOURCE_NODE, completedRequest)
            assertEquals(CompleteCommandOutcome.TargetChanged, completedResponse.outcome)
            assertTrue(completedResponse.replacement.payload is SnapshotPayload.WorkoutComplete)

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
            val emptyResponse = bridge.completeCurrentSet(SOURCE_NODE, emptyRequest)
            val emptyReplacement =
                emptyResponse.replacement.payload as SnapshotPayload.PhoneActionRequired
            assertEquals(CompleteCommandOutcome.TargetChanged, emptyResponse.outcome)
            assertTrue(emptyReplacement.reason is PhoneActionReason.NoSetRows)
        }

    @Test
    fun `revision mismatch wins after the submitted exercise becomes skipped`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())

        env.transition.mutate {
            database.performedExerciseDao.setSkipped(seed.performedUuids.single(), true)
        }
        val response = bridge.completeCurrentSet(SOURCE_NODE, command)

        assertEquals(CompleteCommandOutcome.StaleRevision, response.outcome)
        assertTrue(response.replacement.payload is SnapshotPayload.WorkoutComplete)
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
            val planResponse = bridge.completeCurrentSet(SOURCE_NODE, planCommand)
            val planReplacement = planResponse.replacement.payload as SnapshotPayload.ActiveWithTarget
            assertEquals(CompleteCommandOutcome.StaleRevision, planResponse.outcome)
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
            val typeResponse = bridge.completeCurrentSet(SOURCE_NODE, typeCommand)
            val typeReplacement = typeResponse.replacement.payload as SnapshotPayload.ActiveWithTarget
            assertEquals(CompleteCommandOutcome.StaleRevision, typeResponse.outcome)
            assertEquals(ExerciseTypeWire.WEIGHTLESS, typeReplacement.target.exerciseType)
            assertNull(typeReplacement.target.weightHundredthsKg)
            assertEquals(0, database.setDao.countByPerformedExercise(typeSeed.performedUuids.single()))
        }

    @Test
    fun `unsupported durable receipt fails closed without rewriting any authority byte`() = runTest {
        val seed = seedSession(plans = listOf(plan(100.0, 5)))
        val command = commandFrom(bridge.activePayload())
        val beforeReceipt = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
        val unsupported = ByteArray(34).also {
            it[0] = 0
            it[1] = 2
        }
        val epoch = requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch
        assertEquals(
            1,
            database.wearSyncDao.storeReceipt(
                sessionUuid = seed.sessionUuid,
                commandId = CanonicalUuid.random().value,
                attemptFingerprint = unsupported,
                databaseEpoch = epoch,
                revision = beforeReceipt.revision,
            ),
        )
        val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        val response = bridge.completeCurrentSet(SOURCE_NODE, command)
        val after = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))

        assertEquals(
            CompleteCommandOutcome.ProtocolRejected(
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
            body = original.body.copy(
                exerciseType = ExerciseTypeWire.WEIGHTLESS,
                setType = SetTypeWire.FAIL,
                weightHundredthsKg = null,
            ),
        )

        val response = bridge.completeCurrentSet(SOURCE_NODE, changed)

        assertEquals(
            CompleteCommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE),
            response.outcome,
        )
        assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `correlated protocol rejection preserves receipt revision and generation byte for byte`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5), plan(101.0, 6)))
            val first = commandFrom(bridge.activePayload())
            val applied = bridge.completeCurrentSet(SOURCE_NODE, first)
            val second = applied.replacement.payload as SnapshotPayload.ActiveWithTarget
            val authority = second.mutationAuthority as MutationAuthority.Granted
            val before = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            val beforeRows = database.setDao.getByPerformedExercise(seed.performedUuids.single())

            val response = bridge.protocolRejected(
                SOURCE_NODE,
                CompleteCommandRouting(
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
                CompleteCommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
                ),
                response.outcome,
            )
            assertTrue(
                (response.replacement.payload as SnapshotPayload.ActiveWithTarget)
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

        val retryable = bridge.completeCurrentSet(SOURCE_NODE, first)
        val retryPayload = retryable.replacement.payload as SnapshotPayload.ActiveWithTarget
        val successor = retryPayload.mutationAuthority as MutationAuthority.Granted
        flaky.fail = false
        val retry = first.copy(
            correlationId = CanonicalUuid.random(),
            mutationLeaseId = successor.mutationLeaseId,
            mutationLeaseGeneration = successor.mutationLeaseGeneration,
        )
        val applied = bridge.completeCurrentSet(SOURCE_NODE, retry)

        assertEquals(CompleteCommandOutcome.RetryableTemporaryFailure, retryable.outcome)
        assertEquals(CompleteCommandOutcome.Applied, applied.outcome)
        assertEquals(1, database.setDao.countByPerformedExercise(seed.performedUuids.single()))
    }

    @Test
    fun `retry successor rejects changed intent and old command cannot use a generic rebind`() =
        runTest {
            val seed = seedSession(plans = listOf(plan(100.0, 5)))
            val flaky = FlakyWriter(RoomWearSetMutationWriter(database))
            bridge = newBridge(flaky)
            val first = commandFrom(bridge.activePayload())
            val retryable = bridge.completeCurrentSet(SOURCE_NODE, first)
            val retryPayload = retryable.replacement.payload as SnapshotPayload.ActiveWithTarget
            val retryAuthority = retryPayload.mutationAuthority as MutationAuthority.Granted
            val beforeChanged = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            val changedIntent = first.copy(
                correlationId = CanonicalUuid.random(),
                mutationLeaseId = retryAuthority.mutationLeaseId,
                mutationLeaseGeneration = retryAuthority.mutationLeaseGeneration,
                body = first.body.copy(reps = first.body.reps + 1),
            )

            val changedResponse = bridge.completeCurrentSet(SOURCE_NODE, changedIntent)
            val afterChanged = requireNotNull(database.wearSyncDao.getSessionSync(seed.sessionUuid))
            assertEquals(
                CompleteCommandOutcome.ProtocolRejected(
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
            val reboundResponse = bridge.completeCurrentSet(SOURCE_NODE, oldCommandRebound)
            assertEquals(
                CompleteCommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                ),
                reboundResponse.outcome,
            )
            assertEquals(0, database.setDao.countByPerformedExercise(seed.performedUuids.single()))

            flaky.fail = false
            val newCommand = commandFrom(bridge.activePayload())
            assertNotEquals(first.commandId, newCommand.commandId)
            assertEquals(
                CompleteCommandOutcome.Applied,
                bridge.completeCurrentSet(SOURCE_NODE, newCommand).outcome,
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
                mutationWriter = RoomWearSetMutationWriter(database),
            )

            val response = divergent.completeCurrentSet(SOURCE_NODE, request)

            assertEquals(CompleteCommandOutcome.Applied, response.outcome)
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

    /** Only `wearSyncDao` is interposed; every other read the bridge makes stays on the real db. */
    private fun databaseWithSyncDao(dao: WearSyncDao): AppDatabase = mockk<AppDatabase> {
        every { wearSyncDao } returns dao
        every { performedExerciseDao } returns database.performedExerciseDao
        every { setDao } returns database.setDao
    }

    private fun newBridge(
        writer: WearSetMutationWriter = RoomWearSetMutationWriter(database),
        transition: DbTransitionRunner = env.transition,
    ): PhoneWorkoutBridgeImpl = PhoneWorkoutBridgeImpl(
        database = database,
        transition = transition,
        snapshotBuilder = PhoneWorkoutSnapshotBuilder(database),
        leaseStore = leaseStore,
        clock = clock,
        mutationWriter = writer,
    )

    private suspend fun PhoneWorkoutBridgeImpl.snapshot() = getActiveWorkout(
        SOURCE_NODE,
        GetActiveWorkoutRequest(WearProtocol.SCHEMA_VERSION, CanonicalUuid.random()),
    )

    private suspend fun PhoneWorkoutBridgeImpl.activePayload(): SnapshotPayload.ActiveWithTarget =
        snapshot().snapshot.payload as SnapshotPayload.ActiveWithTarget

    private suspend fun commandFrom(
        active: SnapshotPayload.ActiveWithTarget,
    ): CompleteCurrentSetRequest {
        val authority = active.mutationAuthority as MutationAuthority.Granted
        return CompleteCurrentSetRequest(
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
            body = CompleteCurrentSetBody(
                performedExerciseUuid = active.target.performedExerciseUuid,
                setPosition = active.target.setPosition,
                reps = active.target.reps.coerceAtLeast(1),
                weightHundredthsKg = active.target.weightHundredthsKg,
                exerciseType = active.target.exerciseType,
                setType = active.target.setType,
            ),
        )
    }

    /** Installs a valid current-version lease for a deliberately non-canonical target fixture. */
    private suspend fun bindSyntheticLease(
        request: CompleteCurrentSetRequest,
    ): CompleteCurrentSetRequest {
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
                    performedExerciseUuid = bound.body.performedExerciseUuid,
                    setPosition = bound.body.setPosition,
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
        val expected: CompleteCommandOutcome,
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
        override suspend fun write(value: WearSetWrite) {
            if (fail) throw SQLiteException("synthetic busy")
            delegate.write(value)
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
