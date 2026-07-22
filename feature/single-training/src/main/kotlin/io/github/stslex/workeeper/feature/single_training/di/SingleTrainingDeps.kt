// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/single-training's domain tail for the god-object split (variant A, mechanism A). Names ONLY the
 * app-scope deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics, logger,
 * dispatchers} + `NavigatorDeps` {navigator}) — five feature repos + `sessionConflictResolver` +
 * `resourceWrapper` + the TWO qualified dispatchers.
 *
 * TWO DISPATCHERS (the multi-dispatcher case): single-training reads BOTH **`@DefaultDispatcher`** AND
 * **`@MainImmediateDispatcher`** (verified from `SingleTrainingGraph.Factory.create`). Both are
 * `CoroutineDispatcher`, so each MUST carry its own qualifier verbatim — Metro matches by
 * (type + qualifier); a mis-qualified accessor would collide/mis-wire the pair silently (a wrong-but-
 * present qualifier still compiles, since AppGraph exposes every dispatcher qualifier distinctly).
 * `SingleTrainingGraphBridgeTest` asserts the pair resolves to distinct instances with no cross-wire.
 *
 * Acquired via `context.appDeps<SingleTrainingDeps>()`. Types are owned by `core:data:exercise` /
 * `core:core` (already depended on) — no new edge, no cycle.
 */
interface SingleTrainingDeps {
    val trainingRepository: TrainingRepository
    val trainingExerciseRepository: TrainingExerciseRepository
    val exerciseRepository: ExerciseRepository
    val tagRepository: TagRepository
    val sessionRepository: SessionRepository
    val sessionConflictResolver: SessionConflictResolver
    val resourceWrapper: ResourceWrapper

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher
}
