package io.github.stslex.workeeper.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import io.github.stslex.workeeper.core.dataStore.di.ModuleCoreStore
import io.github.stslex.workeeper.core.database.ModuleCoreDatabase
import io.github.stslex.workeeper.core.exercise.di.ModuleCoreExercise
import io.github.stslex.workeeper.core.ui.kit.di.ModuleCoreUiUtils
import io.github.stslex.workeeper.feature.all_exercises.di.ModuleFeatureAllExercises
import io.github.stslex.workeeper.feature.all_trainings.di.ModuleFeatureAllTrainings
import io.github.stslex.workeeper.feature.charts.di.ModuleFeatureCharts
import io.github.stslex.workeeper.feature.exercise.di.ModuleFeatureExercise
import org.koin.ksp.generated.module

val appModules = listOf(
    ModuleCore().module,
    ModuleCoreUiUtils().module,
    ModuleCoreDatabase().module,
    ModuleCoreExercise().module,
    ModuleCoreStore().module,
    ModuleFeatureExercise().module,
    ModuleFeatureAllExercises().module,
    ModuleFeatureCharts().module,
    ModuleFeatureAllTrainings().module,
)