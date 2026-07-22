// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/all-trainings' domain tail for the god-object split (variant A, mechanism A). Names ONLY the
 * app-scope deps NOT covered by the two γ-spine interfaces
 * ([StoreCoreDeps][io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps] {analytics, logger,
 * dispatchers} + [NavigatorDeps][io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps]
 * {navigator}) — the two feature repos + `resourceWrapper` + the qualified `@DefaultDispatcher`.
 *
 * Acquired via `context.appDeps<AllTrainingsDeps>()` and fed into `AllTrainingsGraph.Factory.create(...)`;
 * the signatures are copied verbatim from the app graph's accessor set so `AppGraph`'s existing overrides
 * satisfy them with no new provision. The types are owned by data/core modules `all-trainings` already depends on
 * (`core:data:exercise`, `core:core`), so declaring them here adds no dependency edge and no cycle.
 *
 * `@DefaultDispatcher` is copied verbatim (qualifier included): Metro matches bindings by
 * **(type + qualifier)**, so an unqualified `CoroutineDispatcher` accessor would collide with the other
 * dispatcher bindings and mis-wire silently.
 */
interface AllTrainingsDeps {
    val trainingRepository: TrainingRepository
    val tagRepository: TagRepository
    val resourceWrapper: ResourceWrapper

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher
}
