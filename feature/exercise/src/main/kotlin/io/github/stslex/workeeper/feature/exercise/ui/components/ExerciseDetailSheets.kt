// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

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
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

/**
 * Content of the topbar `⋮` sheet — bare `.mitem` rows, `AppBottomSheet` wraps at the call
 * site so the drawing stays goldenable (the `ModalBottomSheet` window is out of Paparazzi's
 * one-window model, §10.4).
 *
 * The permanent-delete row renders only when [canPermanentlyDelete] — same condition the
 * v2.4 `DropdownMenu` applied. Edit and Archive carry no leading icon: [AppIcons] ships
 * only glyphs transcribed verbatim from the mockups, and the mockup draws no target for
 * this menu — inventing a pencil or box glyph would break that provenance rule. The
 * destructive row reuses the transcribed Trash.
 */
@Composable
internal fun ExerciseDetailMenuSheetContent(
    canPermanentlyDelete: Boolean,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppSheetItem(
            title = stringResource(R.string.feature_exercise_detail_edit),
            onClick = { consume(Action.Click.OnEditClick) },
            modifier = Modifier.testTag("ExerciseDetailMenu_Edit"),
        )
        AppSheetItem(
            title = stringResource(R.string.feature_exercise_detail_archive),
            onClick = { consume(Action.Click.OnArchiveMenuClick) },
            modifier = Modifier.testTag("ExerciseDetailMenu_Archive"),
        )
        if (canPermanentlyDelete) {
            AppSheetItem(
                title = stringResource(R.string.feature_exercise_detail_permanent_delete),
                icon = AppIcons.Trash,
                destructive = true,
                onClick = { consume(Action.Click.OnPermanentDeleteMenuClick) },
                modifier = Modifier.testTag("ExerciseDetailMenu_PermanentDelete"),
            )
        }
    }
}

@Preview
@Composable
private fun ExerciseDetailMenuSheetContentPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseDetailMenuSheetContent(
            canPermanentlyDelete = true,
            consume = {},
        )
    }
}
