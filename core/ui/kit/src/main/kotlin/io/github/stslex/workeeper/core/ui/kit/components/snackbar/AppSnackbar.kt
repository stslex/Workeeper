package io.github.stslex.workeeper.core.ui.kit.components.snackbar

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import io.github.stslex.workeeper.core.ui.kit.theme.toPx
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

@Composable
fun BoxWithConstraintsScope.AppSnackbarHost(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val width = maxWidth
    AppSnackbarHost(
        snackbarHostState = snackbarHostState,
        width = width,
        modifier = modifier
    )
}

@Composable
fun BoxScope.AppSnackbarHost(
    snackbarHostState: SnackbarHostState,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        modifier = modifier
            .align(Alignment.BottomCenter),
        hostState = snackbarHostState
    ) { snackbarData ->
        val actionLabel = snackbarData.visuals.actionLabel ?: return@SnackbarHost
        val action = SnackbarType.Companion.getByAction(actionLabel) ?: return@SnackbarHost
        when (action) {
            SnackbarType.ERROR -> ErrorSnackbar(
                snackbarHostState = snackbarHostState,
                message = snackbarData.visuals.message,
                width = width,
            )

            SnackbarType.SUCCESS -> SuccessSnackbar(
                snackbarHostState = snackbarHostState,
                snackbarData.visuals.message,
                width = width,
            )

            SnackbarType.INFO -> InfoSnackbar(
                snackbarHostState = snackbarHostState,
                snackbarData.visuals.message,
                width = width,
            )
        }
    }
}

@Composable
fun SuccessSnackbar(
    snackbarHostState: SnackbarHostState,
    message: String,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    AppSnackbar(
        type = SnackbarType.SUCCESS,
        message = message,
        width = width,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@Composable
fun InfoSnackbar(
    snackbarHostState: SnackbarHostState,
    message: String,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    AppSnackbar(
        type = SnackbarType.INFO,
        message = message,
        width = width,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@Composable
fun ErrorSnackbar(
    snackbarHostState: SnackbarHostState,
    message: String,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    AppSnackbar(
        type = SnackbarType.ERROR,
        message = message,
        width = width,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppSnackbar(
    type: SnackbarType,
    message: String,
    width: Dp,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val swipeableState = rememberSwipeableState(SnackbarSwipeState.CENTER)
    val widthPx = width.toPx

    LaunchedEffect(swipeableState) {
        snapshotFlow {
            swipeableState.offset.value
        }
            .filter { value ->
                value == widthPx || value == -widthPx
            }
            .distinctUntilChanged()
            .collect {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
    }

    Snackbar(
        modifier = modifier
            .swipeable(
                state = swipeableState,
                orientation = Orientation.Horizontal,
                anchors = mapOf(
                    -widthPx to SnackbarSwipeState.LEFT,
                    0f to SnackbarSwipeState.CENTER,
                    widthPx to SnackbarSwipeState.RIGHT
                ),
            )
            .offset {
                IntOffset(
                    x = swipeableState.offset.value.roundToInt(),
                    y = 0
                )
            }
            .padding(horizontal = AppDimension.Padding.medium),
        action = {
            TextButton(
                onClick = {
                    snackbarHostState.currentSnackbarData?.performAction()
                }
            ) {
                // TODO repeat for errors???
                Text(text = "OK")
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimension.Padding.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = type.imageVector,
                contentDescription = type.contentDescription
            )
            Spacer(modifier = Modifier.Companion.padding(AppDimension.Padding.medium))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2
            )
        }
    }
}
