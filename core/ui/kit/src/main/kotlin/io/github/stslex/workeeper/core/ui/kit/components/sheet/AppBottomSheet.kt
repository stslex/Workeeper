package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.coroutines.flow.first

/**
 * The v3 sheet window: `surfaceTier3`, 32dp top radius, 36x4 grab handle. [expandedOnly] opens
 * at full height; [onSettled] fires once expanded, and is kept knowingly without a consumer.
 *
 * GUARD: the sheet pads for the IME but its window is never resized, so content that can outgrow
 * the space above the keyboard must bound its own height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    expandedOnly: Boolean = false,
    onSettled: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expandedOnly)
    onSettled?.let { settled ->
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
            settled()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(
            topStart = AppDimension.Radius.big,
            topEnd = AppDimension.Radius.big,
        ),
        containerColor = AppUi.colors.surfaceTier3,
        contentColor = AppUi.colors.textPrimary,
        dragHandle = { SheetGrabHandle() },
    ) {
        Column(
            modifier = Modifier.padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                bottom = AppDimension.Space.xxl,
            ),
            content = content,
        )
    }
}

/** `.grab` — 36×4, 2dp radius, centred, 8dp above and 16dp below (mockup 10/20px). */
@Composable
private fun SheetGrabHandle() {
    Box(
        modifier = Modifier
            .padding(top = AppDimension.Space.sm, bottom = AppDimension.Space.lg)
            .size(width = GRAB_WIDTH, height = GRAB_HEIGHT)
            .clip(RoundedCornerShape(GRAB_RADIUS))
            .background(AppUi.colors.borderDefault),
    )
}

private val GRAB_WIDTH = 36.dp
private val GRAB_HEIGHT = 4.dp
private val GRAB_RADIUS = 2.dp

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppBottomSheetPreview() {
    AppTheme {
        AppBottomSheet(onDismiss = {}) {
            Text("Sheet content", style = AppUi.typography.titleMedium)
        }
    }
}
