package io.github.stslex.workeeper.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import io.github.stslex.workeeper.core.database.ModuleCoreDatabase
import io.github.stslex.workeeper.core.ui.kit.di.ModuleCoreUiUtils
import io.github.stslex.workeeper.feature.home.di.ModuleFeatureHome
import org.koin.ksp.generated.module

val appModules = listOf(
    ModuleCore().module,
    ModuleCoreUiUtils().module,
    ModuleCoreDatabase().module,
    ModuleFeatureHome().module
)