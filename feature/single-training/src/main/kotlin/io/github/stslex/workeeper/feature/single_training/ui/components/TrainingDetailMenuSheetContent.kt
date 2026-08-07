// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action

/**
 * Content of the topbar `⋮` sheet — bare `.mitem` rows, `AppBottomSheet` wraps at the call
 * site so the drawing stays goldenable (the `ModalBottomSheet` window is out of Paparazzi's
 * one-window model, §10.4).
 *
 * ED10: `Изменить` is NOT here — it moved to the dock. The menu keeps «В архив» and, when
 * the training is deletable, «Удалить навсегда». Archive carries no leading icon for the
 * same reason the exercise menu's rows don't: [AppIcons] ships only glyphs transcribed
 * verbatim from the mockups, and no target is drawn for this menu.
 */
@Composable
internal fun TrainingDetailMenuSheetContent(
    canPermanentlyDelete: Boolean,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppSheetItem(
            title = stringResource(R.string.feature_training_detail_archive),
            onClick = { consume(Action.Click.OnArchiveClick) },
            modifier = Modifier.testTag("TrainingDetailArchiveMenuItem"),
        )
        if (canPermanentlyDelete) {
            AppSheetItem(
                title = stringResource(R.string.feature_training_detail_permanent_delete),
                icon = AppIcons.Trash,
                destructive = true,
                onClick = { consume(Action.Click.OnPermanentDeleteClick) },
                modifier = Modifier.testTag("TrainingDetailPermanentDeleteMenuItem"),
            )
        }
    }
}

@Preview
@Composable
private fun TrainingDetailMenuSheetContentPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        TrainingDetailMenuSheetContent(
            canPermanentlyDelete = true,
            consume = {},
        )
    }
}
