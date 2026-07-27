// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.testfixtures

import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.PrRuleFixture.PrScenario
import org.jetbrains.annotations.TestOnly
import kotlin.uuid.Uuid

/**
 * Writes a [PrScenario] into a [RepositoryTestEnv] as real rows, and hands back the set UUID
 * each candidate label was given so assertions can name a winner rather than compare
 * weight/reps tuples (two candidates in the tie scenarios are identical on those).
 *
 * Sessions are seeded [SessionStateEntity.IN_PROGRESS] by default so a test can assert the
 * two-phase contract: while the session is unfinished the SQL sites see nothing and only
 * `PrComparator` can answer; [finishAllSessions] then flips them and the SQL sites must
 * arrive at the same answer.
 */
@TestOnly
class PrRuleDbSeeder(private val env: RepositoryTestEnv) {

    data class Seeded(
        val scenario: PrScenario,
        val exerciseUuid: Uuid,
        val setUuidByLabel: Map<String, Uuid>,
        val sessionUuidByFinishedAt: Map<Long, Uuid>,
    ) {
        /** The set UUID the rule must name, or null when the scenario expects no holder. */
        val expectedHolderSetUuid: Uuid? get() = scenario.expectedHolder?.let(setUuidByLabel::getValue)

        fun labelOf(setUuid: Uuid?): String? =
            setUuidByLabel.entries.firstOrNull { it.value == setUuid }?.key
    }

    suspend fun seed(
        scenario: PrScenario,
        state: SessionStateEntity = SessionStateEntity.IN_PROGRESS,
    ): Seeded {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Training-$trainingUuid",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = exerciseUuid,
                name = "Exercise-$exerciseUuid",
                type = if (scenario.isWeightless) {
                    ExerciseTypeEntity.WEIGHTLESS
                } else {
                    ExerciseTypeEntity.WEIGHTED
                },
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        val setUuidByLabel = mutableMapOf<String, Uuid>()
        val sessionUuidByFinishedAt = mutableMapOf<Long, Uuid>()

        // Candidates sharing a finishedAt share a session — that is what makes `position`
        // reachable as a tiebreak instead of being masked by finished_at.
        scenario.candidates.groupBy { it.finishedAt }.forEach { (finishedAt, candidates) ->
            val sessionUuid = Uuid.random()
            sessionUuidByFinishedAt[finishedAt] = sessionUuid
            env.sessionDao.insert(
                SessionEntity(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = state,
                    startedAt = 0L,
                    finishedAt = if (state == SessionStateEntity.FINISHED) finishedAt else null,
                ),
            )
            val performedUuid = Uuid.random()
            env.performedExerciseDao.insert(
                listOf(
                    PerformedExerciseEntity(
                        uuid = performedUuid,
                        sessionUuid = sessionUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                        skipped = false,
                    ),
                ),
            )
            candidates.forEach { candidate ->
                val setUuid = Uuid.random()
                setUuidByLabel[candidate.label] = setUuid
                env.setDao.insert(
                    SetEntity(
                        uuid = setUuid,
                        performedExerciseUuid = performedUuid,
                        position = candidate.position,
                        reps = candidate.reps,
                        weight = candidate.weight,
                        type = SetTypeEntity.WORK,
                    ),
                )
            }
        }

        return Seeded(
            scenario = scenario,
            exerciseUuid = exerciseUuid,
            setUuidByLabel = setUuidByLabel,
            sessionUuidByFinishedAt = sessionUuidByFinishedAt,
        )
    }

    /** Flips every session seeded for [seeded] to FINISHED at its candidate's `finishedAt`. */
    suspend fun finishAllSessions(seeded: Seeded) {
        seeded.sessionUuidByFinishedAt.forEach { (finishedAt, sessionUuid) ->
            env.sessionDao.finishSession(uuid = sessionUuid, finishedAt = finishedAt)
        }
    }
}
