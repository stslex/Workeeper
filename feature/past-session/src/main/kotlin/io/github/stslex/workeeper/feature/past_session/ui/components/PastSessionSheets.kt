// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore

/**
 * The topbar `⋮` menu as bare CONTENT — `AppBottomSheet` wraps it at the call site, which keeps
 * the drawing goldenable (§10.4 excludes the window, not the content).
 */
@Composable
internal fun PastSessionMenuSheetContent(
    consume: (PastSessionStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppSheetItem(
            title = stringResource(R.string.feature_past_session_menu_delete_session),
            icon = AppIcons.Trash,
            destructive = true,
            onClick = { consume(PastSessionStore.Action.Click.OnDeleteClick) },
            modifier = Modifier.testTag("PastSessionMenu_Delete"),
        )
    }
}
