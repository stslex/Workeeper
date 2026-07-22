// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/archive's domain tail for the god-object split (variant A, mechanism A). Names ONLY the
 * app-scope deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics, logger,
 * dispatchers} + `NavigatorDeps` {navigator}) — the two feature repos + `resourceWrapper` + the qualified
 * `@DefaultDispatcher`.
 *
 * Acquired via `context.appDeps<ArchiveDeps>()` and fed into `ArchiveGraph.Factory.create(...)`; signatures
 * copied verbatim from the app graph's existing accessor set so `AppGraph`'s existing overrides satisfy them
 * with no new provision. Types are owned by `core:data:exercise` / `core:core` (already depended on) — no new edge, no
 * cycle. `@DefaultDispatcher` is copied verbatim: Metro matches by (type + qualifier), so an unqualified
 * `CoroutineDispatcher` accessor would collide with the other dispatcher bindings.
 */
interface ArchiveDeps {
    val exerciseRepository: ExerciseRepository
    val trainingRepository: TrainingRepository
    val resourceWrapper: ResourceWrapper

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher
}
