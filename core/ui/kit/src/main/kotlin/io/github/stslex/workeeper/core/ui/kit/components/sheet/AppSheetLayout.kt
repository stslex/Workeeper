// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionDivider
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionRow
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The anatomy of a bottom sheet's **contents**: title, then content.
 *
 * ## Why this is separate from [AppBottomSheet]
 *
 * A sheet is two things stacked: a *window* (the scrim, the drag behaviour, the dismiss plumbing —
 * Material's `ModalBottomSheet`, which [AppBottomSheet] wraps) and a *layout* (what is inside it).
 * Paparazzi models one window, so anything rendered through `ModalBottomSheet` is invisible to the
 * visual gate — the harness says so itself (`GoldenHarness.kt:53-57`).
 *
 * Splitting the layout out is what makes the larger half testable. This composable is an ordinary
 * `Column`; it renders in the main window, so it has goldens. What stays unverifiable is the
 * genuinely window-shaped part: the scrim's opacity, the sheet's entry animation, the drag
 * dismissal, and how tall the sheet settles. Those are on the PR's manual checklist, and that
 * split is the point rather than an omission.
 *
 * ## What the window owns: the scrim AND the grab handle
 *
 * Neither is drawn here, for the same reason. Both are affordances for gestures the *window*
 * handles — the scrim dismisses on tap, the handle affords dragging the sheet — so drawing either
 * one in the content means drawing an affordance for a gesture the content cannot service.
 *
 * The handle is the easier of the two to get wrong, and this layout got it wrong first:
 * `ModalBottomSheet` declares `dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults
 * .DragHandle() }`, and [AppBottomSheet] does not override it. A handle drawn here would therefore
 * be the *second* one in every sheet, sitting under Material's. Read out of the Material 3
 * 1.5.0-alpha15 sources rather than assumed.
 *
 * So the anatomy names four layers and this composable owns the last two: scrim and handle belong
 * to the window; title and content belong here.
 *
 * ## The three forms
 *
 * All three are this layout with a different `content`:
 *  - **item list** — a stack of [AppSectionRow] separated by [AppSectionDivider]; see
 *    [AppSheetMenuContent].
 *  - **button stack** — a confirmation: a sentence, then actions; see [AppSheetConfirmContent].
 *  - **free content** — anything, with a close affordance in the title row ([onClose]).
 */
@Composable
fun AppSheetLayout(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = AppDimension.Space.sm,
                bottom = AppDimension.Space.xl,
            ),
    ) {
        title?.let { text ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimension.screenEdge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = text,
                    style = AppUi.typography.text.section,
                    color = AppUi.colors.textPrimary,
                )
                onClose?.let { close ->
                    IconButton(onClick = close) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconSm),
                            imageVector = Icons.Default.Close,
                            // Labelled, not decorative: when `onClose` is supplied this icon is
                            // the sheet's only close affordance, so a null description leaves a
                            // TalkBack user with an unlabelled button and no way to know what it
                            // does. The glyph carries the whole meaning, which is exactly when a
                            // content description is mandatory rather than noise.
                            contentDescription = stringResource(R.string.core_ui_kit_sheet_close),
                            tint = AppUi.colors.textTertiary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(AppDimension.Space.md))
        }
        content()
    }
}

/** Form (a): a menu. Rows, hairlines between them, nothing below the last one. */
@Composable
fun AppSheetMenuContent(
    items: List<AppSheetMenuItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) AppSectionDivider()
            AppSectionRow(
                title = item.title,
                supporting = item.supporting,
                onClick = item.onClick,
            )
        }
    }
}

/** One row of [AppSheetMenuContent]. */
data class AppSheetMenuItem(
    val title: String,
    val supporting: String? = null,
    val onClick: () -> Unit,
)

/**
 * Form (b): a confirmation. An explanation, then the actions stacked.
 *
 * Confirm sits above dismiss, and dismiss is the quieter of the two — the destructive reading of
 * this layout ("the dangerous button is the big one at the top") is why [confirmDestructive]
 * exists rather than being inferred.
 */
@Composable
fun AppSheetConfirmContent(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmDestructive: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.screenEdge),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Text(
            text = message,
            style = AppUi.typography.text.body,
            color = AppUi.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(AppDimension.Space.xs))
        if (confirmDestructive) {
            AppButton.Destructive(
                modifier = Modifier.fillMaxWidth(),
                text = confirmLabel,
                onClick = onConfirm,
                size = AppButtonSize.MEDIUM,
            )
        } else {
            AppButton.Primary(
                modifier = Modifier.fillMaxWidth(),
                text = confirmLabel,
                onClick = onConfirm,
                size = AppButtonSize.MEDIUM,
            )
        }
        dismissLabel?.let { label ->
            onDismiss?.let { handler ->
                AppButton.Tertiary(
                    modifier = Modifier.fillMaxWidth(),
                    text = label,
                    onClick = handler,
                    size = AppButtonSize.MEDIUM,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSheetLayoutPreview() {
    AppTheme {
        Column(modifier = Modifier.background(AppUi.colors.surfaceTier1)) {
            AppSheetLayout(title = "Set type") {
                AppSheetMenuContent(
                    items = listOf(
                        AppSheetMenuItem(title = "Warm-up", onClick = {}),
                        AppSheetMenuItem(title = "Working set", onClick = {}),
                        AppSheetMenuItem(title = "To failure", onClick = {}),
                    ),
                )
            }
        }
    }
}
