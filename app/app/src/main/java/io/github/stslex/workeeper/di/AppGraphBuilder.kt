// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao

/**
 * The SINGLE construction site for the app-scope [AppGraph] (App-Scope Collapse — Phase B3, V.2
 * cleanup). Both real callers — `BaseApplication.appGraph` (prod) and [AppGraphSourceModule]'s test
 * fallback — delegate here, so the `create(...)` argument list is threaded in exactly ONE place.
 *
 * App-Scope Collapse Step 3 (C2, bridge-scaffold): the exercise-repo db-cascade substrate — 9 Room DAOs +
 * [DbTransitionRunner] + [ImageStorage] — is BRIDGE-READ from Hilt as bound instances (they stay Hilt-owned;
 * the db-cascade is Step-5-fenced, ImageStorage is the C1 carveout). Callers pull the Hilt-BOUND instances
 * (never construct) so `@TestInstallIn`-swapped `FakeImageStorage` still resolves in tests. Cycle-free since
 * C2-h' dissolved the `@IO`→`appGraph` back-edge (`DbTransitionRunner`/`ImageStorageImpl` need `@IO`, now a
 * direct Hilt `Dispatchers.IO`). Unconsumed until the repo flips land (C2 commit 2 consumes them). The
 * `@DefaultDispatcher`/`@MainImmediateDispatcher` bridge was retired in PF.1; `@IODispatcher` is NOT bridged.
 */
@Suppress("LongParameterList")
internal fun buildAppGraph(
    applicationContext: Context,
    exerciseDao: ExerciseDao,
    exerciseTagDao: ExerciseTagDao,
    performedExerciseDao: PerformedExerciseDao,
    sessionDao: SessionDao,
    setDao: SetDao,
    tagDao: TagDao,
    trainingDao: TrainingDao,
    trainingExerciseDao: TrainingExerciseDao,
    trainingTagDao: TrainingTagDao,
    dbTransitionRunner: DbTransitionRunner,
    imageStorage: ImageStorage,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    applicationContext = applicationContext,
    exerciseDao = exerciseDao,
    exerciseTagDao = exerciseTagDao,
    performedExerciseDao = performedExerciseDao,
    sessionDao = sessionDao,
    setDao = setDao,
    tagDao = tagDao,
    trainingDao = trainingDao,
    trainingExerciseDao = trainingExerciseDao,
    trainingTagDao = trainingTagDao,
    dbTransitionRunner = dbTransitionRunner,
    imageStorage = imageStorage,
)
