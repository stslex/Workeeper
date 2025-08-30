package io.github.stslex.workeeper.di

import io.github.stslex.workeeper.core.core.di.ModuleCore
import io.github.stslex.workeeper.core.ui.kit.di.ModuleCoreUiUtils
import org.koin.ksp.generated.module

val appModules = listOf(
    ModuleCore().module,
    ModuleCoreUiUtils().module
)