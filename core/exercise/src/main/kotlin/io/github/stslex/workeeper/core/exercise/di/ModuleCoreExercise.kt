package io.github.stslex.workeeper.core.exercise.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import io.github.stslex.workeeper.core.database.ModuleCoreDatabase
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [
        ModuleCoreDatabase::class,
        ModuleCore::class,
    ],
)
@ComponentScan("io.github.stslex.workeeper.core.exercise")
class ModuleCoreExercise
