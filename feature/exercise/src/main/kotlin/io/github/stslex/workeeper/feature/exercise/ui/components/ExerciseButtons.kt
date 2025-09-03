package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import kotlinx.coroutines.delay

@Composable
internal fun ExerciseButtonsRow(
    isDeleteVisible: Boolean,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth()) {
        ConfirmationButton(Icons.Default.Check, onConfirmClick)
        Spacer(Modifier.weight(1f))
        if (isDeleteVisible) {
            ConfirmationButton(Icons.Default.Delete, onDeleteClick)
        }
        Spacer(Modifier.weight(1f))
        ConfirmationButton(Icons.Default.Clear, onCancelClick)
    }
}

@Composable
private fun ConfirmationButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteClick by remember { mutableStateOf(false) }

    var triggerChange by remember { mutableStateOf(false) }

    val floatAnimation by animateFloatAsState(
        targetValue = if (deleteClick) 1f else 0f,
        animationSpec = if (deleteClick) tween(durationMillis = 5_000) else tween(0)
    )

    LaunchedEffect(triggerChange) {
        if (triggerChange) {
            delay(5_000)
            deleteClick = !deleteClick
            triggerChange = false
        }

    }

    AnimatedContent(
        targetState = deleteClick,
        modifier = modifier
            .animateContentSize()
    ) {
        if (it) {
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset.Zero,
                        size = Size(
                            width = size.width * floatAnimation, height = size.height
                        )
                    )
                }
            ) {
                Text("Confirm")
            }
        } else {
            OutlinedIconButton(
                onClick = {
                    deleteClick = !deleteClick
                    triggerChange = true
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
@Preview(device = "id:pixel_4", showSystemUi = true, showBackground = true)
private fun ExerciseButtonsRowPreview() {
    AppTheme {
        Box(
            modifier = Modifier.padding(AppDimension.Padding.large + AppDimension.Padding.large)
        ) {
            ExerciseButtonsRow(
                isDeleteVisible = true,
                onConfirmClick = {},
                onCancelClick = {},
                onDeleteClick = {}
            )
        }
    }
}