// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/home's domain tail for the god-object split (variant A, mechanism A). Names ONLY the app-scope
 * deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics, logger, dispatchers} +
 * `NavigatorDeps` {navigator}) — two feature repos + `sessionConflictResolver` + `resourceWrapper` + the
 * qualified dispatcher.
 *
 * DISPATCHER: home reads a single **`@DefaultDispatcher`** (verified from `HomeGraph.Factory.create`).
 * The qualifier is copied VERBATIM: Metro matches by (type + qualifier), so an unqualified accessor would
 * collide with the other dispatcher bindings.
 *
 * Acquired via `context.appDeps<HomeDeps>()`. Types are owned by `core:data:exercise` / `core:core`
 * (already depended on) — no new edge, no cycle.
 */
interface HomeDeps {
    val trainingRepository: TrainingRepository
    val sessionRepository: SessionRepository
    val sessionConflictResolver: SessionConflictResolver
    val resourceWrapper: ResourceWrapper

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher
}
