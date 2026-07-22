// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/exercise's domain tail for the god-object split (variant A, mechanism A). Names ONLY the
 * app-scope deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics, logger,
 * dispatchers} + `NavigatorDeps` {navigator}) — five feature repos + `imageStorage` + `resourceWrapper`
 * + the TWO qualified dispatchers.
 *
 * TWO DISPATCHERS (the multi-dispatcher case): exercise reads BOTH **`@DefaultDispatcher`** AND
 * **`@MainImmediateDispatcher`** (verified from `ExerciseGraph.Factory.create`). Both are
 * `CoroutineDispatcher`, so each MUST carry its own qualifier verbatim — Metro matches by
 * (type + qualifier); an unqualified or mis-qualified accessor would collide/mis-wire the pair silently
 * (a wrong-but-present qualifier still compiles, since AppGraph exposes every dispatcher qualifier
 * distinctly). `ExerciseGraphBridgeTest` asserts the pair resolves to distinct instances with no cross-wire.
 *
 * NOTE: the app `Context` param on `ExerciseGraph.Factory.create` is NOT here — it is sourced directly
 * from `LocalContext` in the feature, never from the app graph, so it stays a plain `create(...)` arg.
 *
 * Acquired via `context.appDeps<ExerciseDeps>()`. Types are owned by `core:data:exercise` / `core:core`
 * (already depended on) — no new edge, no cycle.
 */
interface ExerciseDeps {
    val exerciseRepository: ExerciseRepository
    val tagRepository: TagRepository
    val imageStorage: ImageStorage
    val personalRecordRepository: PersonalRecordRepository
    val sessionRepository: SessionRepository
    val trainingRepository: TrainingRepository
    val resourceWrapper: ResourceWrapper

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher
}
