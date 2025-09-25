package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(
    includes = [ModuleCore::class],
)
@ComponentScan("io.github.stslex.workeeper.core.ui.mvi")
class ModuleCoreMvi
