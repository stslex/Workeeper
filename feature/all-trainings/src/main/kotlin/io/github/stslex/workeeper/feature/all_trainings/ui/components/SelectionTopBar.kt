// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.all_trainings.R

@Composable
internal fun SelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopAppBar(
        modifier = modifier.testTag("AllTrainingsSelectionTopBar"),
        title = pluralStringResource(
            R.plurals.feature_all_trainings_selected_count,
            selectedCount,
            selectedCount,
        ),
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("AllTrainingsSelectionTopBarClose"),
                onClick = onClose,
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.feature_all_trainings_selection_close),
                )
            }
        },
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SelectionTopBarPreview() {
    AppTheme {
        SelectionTopBar(selectedCount = 3, onClose = {})
    }
}
