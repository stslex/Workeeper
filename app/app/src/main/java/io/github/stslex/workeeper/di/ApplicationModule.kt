package io.github.stslex.workeeper.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import io.github.stslex.workeeper.core.dataStore.di.ModuleCoreStore
import io.github.stslex.workeeper.core.database.ModuleCoreDatabase
import io.github.stslex.workeeper.core.exercise.di.ModuleCoreExercise
import io.github.stslex.workeeper.core.ui.kit.di.ModuleCoreUiUtils
import io.github.stslex.workeeper.core.ui.mvi.di.ModuleCoreMvi
import io.github.stslex.workeeper.feature.all_exercises.di.ModuleFeatureAllExercises
import io.github.stslex.workeeper.feature.all_trainings.di.ModuleFeatureAllTrainings
import io.github.stslex.workeeper.feature.charts.di.ModuleFeatureCharts
import io.github.stslex.workeeper.feature.exercise.di.ModuleFeatureExercise
import io.github.stslex.workeeper.feature.single_training.di.ModuleFeatureSingleTraining
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        ModuleCore::class,
        ModuleCoreUiUtils::class,
        ModuleCoreDatabase::class,
        ModuleCoreExercise::class,
        ModuleCoreStore::class,
        ModuleCoreMvi::class,
        ModuleFeatureExercise::class,
        ModuleFeatureAllExercises::class,
        ModuleFeatureCharts::class,
        ModuleFeatureAllTrainings::class,
        ModuleFeatureSingleTraining::class,
    ],
)
@ComponentScan("io.github.stslex.workeeper")
class ApplicationModule
