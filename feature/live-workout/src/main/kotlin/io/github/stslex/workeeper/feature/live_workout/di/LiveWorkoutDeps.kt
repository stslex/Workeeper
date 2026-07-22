// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/live-workout's domain tail for the god-object split (variant A, mechanism A). Names ONLY the
 * app-scope deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics, logger,
 * dispatchers} + `NavigatorDeps` {navigator}) — seven feature repos + `resourceWrapper` + the qualified
 * dispatcher.
 *
 * DISPATCHER: live-workout reads a single **`@DefaultDispatcher`** (verified from
 * `LiveWorkoutGraph.Factory.create` — one dispatcher, no @IODispatcher / @MainImmediateDispatcher).
 * The qualifier is copied VERBATIM: Metro matches by (type + qualifier), so an unqualified accessor would
 * collide with the other dispatcher bindings.
 *
 * Acquired via `context.appDeps<LiveWorkoutDeps>()`. Types are owned by `core:data:exercise` /
 * `core:core` (already depended on) — no new edge, no cycle.
 */
interface LiveWorkoutDeps {
    val exerciseRepository: ExerciseRepository
    val performedExerciseRepository: PerformedExerciseRepository
    val personalRecordRepository: PersonalRecordRepository
    val sessionRepository: SessionRepository
    val setRepository: SetRepository
    val trainingExerciseRepository: TrainingExerciseRepository
    val trainingRepository: TrainingRepository
    val resourceWrapper: ResourceWrapper

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher
}
