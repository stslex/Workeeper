package io.github.stslex.workeeper.feature.home.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Scope

@Module
@ComponentScan("io.github.stslex.workeeper.feature.home")
@Scope(HomeScope::class)
class ModuleFeatureHome