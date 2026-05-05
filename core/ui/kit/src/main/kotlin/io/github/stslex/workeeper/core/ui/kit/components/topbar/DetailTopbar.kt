package io.github.stslex.workeeper.core.ui.kit.components.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailTopbar(
    title: String,
    actions: ImmutableList<TopbarAction>,
    onBackIconClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        scrollBehavior = scrollBehavior,
        modifier = Modifier.testTag("ExerciseDetailTopBar"),
        title = {
            Text(
                text = title,
                style = AppUi.typography.headlineSmall,
                color = AppUi.colors.textPrimary,
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("DetailTopBarBackButton"),
                onClick = onBackIconClick,
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.core_ui_kit_action_back),
                )
            }
        },
        actions = {

            Box {
                IconButton(
                    modifier = Modifier.testTag("TopbarDetailMenuButton"),
                    onClick = { menuExpanded = true },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.core_ui_kit_more_description),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = AppUi.colors.surfaceTier2,
                ) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            modifier = Modifier.testTag(action.testTag),
                            text = {
                                Text(
                                    text = stringResource(action.titleRes),
                                    style = AppUi.typography.bodyMedium,
                                    color = AppUi.colors.textPrimary,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppUi.colors.surfaceTier0,
            scrolledContainerColor = AppUi.colors.surfaceTier0,
            navigationIconContentColor = AppUi.colors.textPrimary,
            titleContentColor = AppUi.colors.textPrimary,
            actionIconContentColor = AppUi.colors.textPrimary,
        ),
    )
}
