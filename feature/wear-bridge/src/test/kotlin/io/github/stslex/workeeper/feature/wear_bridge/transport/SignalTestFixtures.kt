// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge.transport

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.feature.wear_bridge.WatchStateKey
import kotlinx.coroutines.test.TestScope
import java.util.Collections
import kotlin.uuid.Uuid

/** A Data Layer that records every call; each one is a Play services call in production. */
internal class FakeNudgeLink : WatchNudgeLink {
    var watches: List<String> = listOf(WATCH_A)
    val signals: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Thrown by the next lookup, as a failed capability Task does. */
    var failNextLookup: Throwable? = null

    override suspend fun reachableWatches(): List<String> {
        failNextLookup?.let { failure ->
            failNextLookup = null
            throw failure
        }
        return watches
    }

    override suspend fun signal(nodeId: String) {
        signals += nodeId
    }

    companion object {
        const val WATCH_A = "watch-node-a-7f3a"
        const val WATCH_B = "watch-node-b-7f3a"
    }
}

/** Every line a logger received, to check what telemetry would carry (transport §7.9). */
internal class SignalLogger : Logger {
    val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
    override fun e(throwable: Throwable, message: String?) { lines += message.orEmpty() }
    override fun d(message: String) { lines += message }
    override fun d(e: Throwable, message: String) { lines += message }
    override fun d(e: Throwable, message: () -> String) { lines += message() }
    override fun d(message: () -> String) { lines += message() }
    override fun i(message: String) { lines += message }
    override fun i(message: () -> String) { lines += message() }
    override fun v(message: String) { lines += message }
    override fun v(message: () -> String) { lines += message() }
    override fun w(message: String) { lines += message }
    override fun w(message: () -> String) { lines += message() }
    override fun w(message: String, throwable: Throwable) { lines += message }
    override fun w(throwable: Throwable, message: () -> String) { lines += message() }
}

/** The raw keys the notifier's query emitted, before any operator: Room re-emits on every write. */
internal class KeyTap {
    private val seen: MutableList<WatchStateKey?> = Collections.synchronizedList(mutableListOf())

    fun record(key: WatchStateKey?) {
        seen += key
    }

    fun snapshot(): List<WatchStateKey?> = synchronized(seen) { seen.toList() }
}

/**
 * Runs the test scheduler until [condition] holds over what [tap] saw. Room re-runs the query on its
 * own threads in real time; the notifier's operators then run on the scheduler, in virtual time.
 */
internal fun TestScope.awaitKeys(tap: KeyTap, condition: (List<WatchStateKey?>) -> Boolean) {
    val deadline = System.nanoTime() + KEY_WAIT_NANOS
    while (true) {
        testScheduler.runCurrent()
        if (condition(tap.snapshot())) return
        check(System.nanoTime() < deadline) { "the key query emitted nothing matching in time: ${tap.snapshot()}" }
        Thread.sleep(KEY_POLL_MS)
    }
}

internal data class SignalSeed(
    val trainingUuid: Uuid,
    val sessionUuid: Uuid,
    val performedUuid: Uuid,
)

/** One active workout with a planned set, so a handshake has a target and grants authority (F2). */
internal suspend fun AppDatabase.seedActiveWorkout(): SignalSeed {
    val training = TrainingEntity(
        name = "Strength",
        description = null,
        isAdhoc = false,
        archived = false,
        createdAt = 1,
        archivedAt = null,
    )
    trainingDao.insert(training)
    val exercise = ExerciseEntity(
        name = "Deadlift",
        type = ExerciseTypeEntity.WEIGHTED,
        description = null,
        imagePath = null,
        archived = false,
        createdAt = 1,
        archivedAt = null,
        lastAdhocSets = null,
    )
    exerciseDao.insert(exercise)
    trainingExerciseDao.insert(
        listOf(
            TrainingExerciseEntity(
                trainingUuid = training.uuid,
                exerciseUuid = exercise.uuid,
                position = 0,
                planSets = PlanSetsConverter.toJson(
                    listOf(
                        PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                        PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                    ),
                ),
            ),
        ),
    )
    val session = SessionEntity(
        trainingUuid = training.uuid,
        state = SessionStateEntity.IN_PROGRESS,
        startedAt = 1,
        finishedAt = null,
    )
    sessionDao.insert(session)
    val performed = PerformedExerciseEntity(
        sessionUuid = session.uuid,
        exerciseUuid = exercise.uuid,
        position = 0,
        skipped = false,
    )
    performedExerciseDao.insert(listOf(performed))
    return SignalSeed(training.uuid, session.uuid, performed.uuid)
}

private const val KEY_WAIT_NANOS = 10_000_000_000L
private const val KEY_POLL_MS = 5L
