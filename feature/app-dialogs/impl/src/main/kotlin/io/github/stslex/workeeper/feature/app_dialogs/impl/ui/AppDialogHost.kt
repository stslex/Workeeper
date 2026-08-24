// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogFeature
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action

/**
 * Renders the currently-pending `AppDialog` above every navigation destination, mounted in
 * `App.kt` as a sibling of the nav host. Button taps dispatch [Action.Choose]; the host never
 * clears flags itself.
 */
@Composable
fun AppDialogHost() {
    val processor = AppDialogFeature.processor()
    val state by processor.state
    AppDialogHostContent(
        current = state.current,
        onChoice = { choice -> processor.consume(Action.Choose(choice.dialog, choice.action)) },
    )
}
