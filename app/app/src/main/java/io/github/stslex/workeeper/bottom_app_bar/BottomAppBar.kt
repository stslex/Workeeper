package io.github.stslex.workeeper.bottom_app_bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
internal fun WorkeeperBottomAppBar(
    selectedItem: State<BottomBarItem?>,
    modifier: Modifier = Modifier,
    onItemClick: (BottomBarItem) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    BottomAppBar(
        modifier = modifier
    ) {
        BottomBarItem.entries.forEachIndexed { index, bottomBarItem ->
            BottomAppBarItem(
                modifier = Modifier.weight(1f),
                titleRes = bottomBarItem.titleRes,
                selected = selectedItem.value == bottomBarItem
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onItemClick(bottomBarItem)
            }
            if (index != BottomBarItem.entries.lastIndex) {
                Spacer(Modifier.width(AppDimension.Padding.medium))
            }
        }
    }
}

@Composable
private fun BottomAppBarItem(
    titleRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceDim
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    )
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = stringResource(titleRes),
        )
    }
}

@Composable
@Preview
private fun WorkeeperBottomAppBarPreview() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            WorkeeperBottomAppBar(
                onItemClick = {

                },
                selectedItem = remember {
                    mutableStateOf(BottomBarItem.EXERCISES)
                }
            )
        }
    }
}