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
 * The v3 sheet window (extraction §1.9): every sheet sits on `--field` (**`surfaceTier3`**)
 * with a **32dp** top radius (mockup 26px) and the drawn 36×4 grab handle — Material's
 * default pill and the old tier1/14dp window were v2.4 leftovers. Content padding follows
 * the mockup's `10px 20px 32px` on the ladder: the handle block carries the top, the
 * content column takes `screenEdge` sides and `xxl` bottom.
 *
 * [expandedOnly] opens the sheet at full height with no half stop. [onSettled] fires once the
 * sheet has ARRIVED at expanded — the sequencing point a caller needs to take focus without
 * racing the enter animation (requesting focus on departure raises the IME into a sheet that
 * is still translating). Both are plain Boolean/lambda rather than the experimental
 * `SheetState`, so no call site is forced to opt in; every existing sheet is byte-unchanged
 * at `expandedOnly = false`, which is the exact call this file made before.
 *
 * ## Insets are not handled here, deliberately
 *
 * `ModalBottomSheet`'s own `contentWindowInsets` defaults to `safeDrawing.only(Bottom + Top)`,
 * and `safeDrawing` includes the IME — the content already gets bottom padding equal to the
 * keyboard. Verified on device (API 35, portrait, an 820px IME: the sheet reflowed above it
 * unaided). What that padding cannot do is make oversized content fit: on API 30+ Material
 * sets this window to `SOFT_INPUT_ADJUST_NOTHING` (`ModalBottomSheet.android.kt`), so the
 * window is never resized and anything taller than the space left above the keyboard is
 * simply covered — measured in landscape, where the search field vanished entirely. Content
 * that can outgrow that space must bound itself; see the exercise picker.
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
