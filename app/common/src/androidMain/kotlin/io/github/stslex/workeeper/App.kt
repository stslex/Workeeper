// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.app.common.di.AppRootDepsHolder
import io.github.stslex.workeeper.app.common.di.AppUiGenerationsHolder

@Composable
fun App(onOpenRecovery: () -> Unit) {
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext }
    App(
        generationsHolder = application as AppUiGenerationsHolder,
        resolveDeps = { (application as AppRootDepsHolder).appRootDeps() },
        onOpenRecovery = onOpenRecovery,
    )
}
