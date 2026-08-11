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
 * The topbar `⋮` menu, as bare CONTENT — the window (`AppBottomSheet`: tier3, r32, grab)
 * wraps it at the call site, which keeps the drawing goldenable (§10.4 excludes the window,
 * not the content). Extraction §2.2 draws the glyph and no target; the session screen's
 * `sh-session` supplies the shape: bare items, no title, the destructive one in rust. The
 * only session-level action this screen has is deletion, so the menu holds one item — it
 * exists so the destructive action stops being a bare topbar icon, exactly the v2.4
 * treatment §2.8 flagged.
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
