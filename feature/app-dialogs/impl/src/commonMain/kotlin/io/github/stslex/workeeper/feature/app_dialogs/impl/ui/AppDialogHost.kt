// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogFeature
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogGraph
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action

/**
 * Renders the currently-pending `AppDialog` above every navigation destination, mounted in
 * `App.kt` as a sibling of the nav host with the admitted generation's [factory]. Button taps
 * dispatch [Action.Choose]; the host never clears flags itself.
 */
@Composable
fun AppDialogHost(
    factory: AppDialogGraph.Factory,
) {
    val feature = remember(factory) { AppDialogFeature(factory) }
    val processor = feature.processor()
    val state by processor.state
    AppDialogHostContent(
        current = state.current,
        dateLabel = state.dateLabel,
        onChoice = { choice -> processor.consume(Action.Choose(choice.dialog, choice.action)) },
    )
}
