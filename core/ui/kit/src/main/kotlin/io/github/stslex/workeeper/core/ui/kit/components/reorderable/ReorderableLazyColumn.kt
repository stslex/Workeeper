package io.github.stslex.workeeper.core.ui.kit.components.reorderable

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun ReorderableLazyColumn(
    state: ReorderableLazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = state.listState,
        contentPadding = contentPadding,
        content = content,
    )
}

fun Modifier.reorderableDragHandle(
    state: ReorderableLazyListState,
    key: Any,
    index: Int,
    lastIndex: Int,
): Modifier = this
    .then(
        if (state.draggedKey == key) {
            Modifier
                .shadow(elevation = DRAG_SHADOW_ELEVATION_DP.dp, clip = false)
                .graphicsLayer { translationY = state.dragOffsetPx }
        } else {
            Modifier
        },
    )
    .pointerInput(state, key, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key, index) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragCancel() },
        )
    }
    .semantics {
        customActions = listOf(
            CustomAccessibilityAction("Move up") {
                state.moveUp(index)
                true
            },
            CustomAccessibilityAction("Move down") {
                state.moveDown(index, lastIndex)
                true
            },
        )
    }

private const val DRAG_SHADOW_ELEVATION_DP = 8

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReorderableLazyColumnPreview() {
    val items = remember { mutableStateOf(listOf("Squat", "Bench press", "Deadlift", "Row")) }
    val state = rememberReorderableLazyListState { from, to ->
        items.value = items.value.toMutableList().apply {
            add(to, removeAt(from))
        }
    }
    AppTheme {
        ReorderableLazyColumn(
            state = state,
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            items(
                items = items.value,
                key = { it },
            ) { item ->
                Text(
                    modifier = Modifier
                        .animateItem()
                        .reorderableDragHandle(
                            state = state,
                            key = item,
                            index = items.value.indexOf(item),
                            lastIndex = items.value.size - 1,
                        )
                        .background(AppUi.colors.surfaceTier1)
                        .padding(AppDimension.Space.md),
                    text = item,
                    color = AppUi.colors.textPrimary,
                )
            }
        }
    }
}
