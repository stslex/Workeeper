package io.github.stslex.workeeper.feature.charts.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import io.github.stslex.workeeper.core.dataStore.di.ModuleCoreStore
import io.github.stslex.workeeper.core.exercise.di.ModuleCoreExercise
import io.github.stslex.workeeper.core.ui.mvi.di.ModuleCoreMvi
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        ModuleCore::class,
        ModuleCoreMvi::class,
        ModuleCoreExercise::class,
        ModuleCoreStore::class,
    ],
)
@ComponentScan("io.github.stslex.workeeper.feature.charts")
class ModuleFeatureCharts
