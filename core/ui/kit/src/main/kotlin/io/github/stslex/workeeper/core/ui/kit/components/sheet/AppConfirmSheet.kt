// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 confirmation **sheet** — `pass2d.html` `#s-editor` form 3, `.shx` (extraction §7.4).
 *
 * ```html
 * <div class="sheet shx">
 *   <div class="grab"></div><h3>Выйти без сохранения?</h3>
 *   <div class="desc">Несохранённые правки будут потеряны.</div>
 *   <button class="mitem dang">Выйти без сохранения</button>
 *   <button class="mitem">Продолжить правку</button>
 * </div>
 * ```
 *
 * **The drawing has no dialog primitive at all.** It draws sheets twice (`#sh-del`, `#sh-pick`) and
 * a dialog zero times, so §26 turns every modal on the three editors into one of these — six
 * instances across five components. This is the form all of them take.
 *
 * ## Two actions, and the third one is the ruling
 *
 * The plan editor's discard was three: Save / Discard / Continue, in a bespoke `Dialog` that
 * existed because "`AppConfirmDialog` only renders two". **«Сохранить» is removed** — the sheet
 * appears only when there is something to lose, and saving already lives on the form, so the third
 * action offered a second door to a room the user is standing in. That also disposes of the
 * bespoke dialog and of the reason it stood on. The dismiss label is «Продолжить правку», not
 * «Отмена»: the old one read as closing the window rather than as declining to discard.
 *
 * Actions are `.mitem`s ([AppSheetItem]) rather than buttons — a full-width row you tap, which is
 * what the drawing draws and what a sheet's bottom edge affords. The scrim and the drag both route
 * to [onDismiss], so the gentle exit is the one you get by not deciding.
 *
 * ## [emphasis], and why it is a line and not a panel
 *
 * `AppConfirmDialog` puts its impact summary on a `failureBackground` panel. **The drawn sheet has
 * no panel**, so the type-change confirmation — the one caller that carries an impact — renders it
 * as a second paragraph one tier brighter than the body. Emphasis by role rather than by a
 * treatment the drawing does not have, which keeps the information and invents nothing.
 *
 * ## Why the content is split out
 *
 * `ModalBottomSheet` composes into its own window and Paparazzi models one, so a sheet drawn only
 * inside [AppConfirmSheet] has no visual gate at all. [AppConfirmSheetContent] is the same pixels
 * without the window — the `AppConfirmDialogContent` precedent, for the same reason.
 */
@Composable
fun AppConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
    confirmDestructive: Boolean = false,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        AppConfirmSheetContent(
            modifier = modifier,
            title = title,
            body = body,
            emphasis = emphasis,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmDestructive = confirmDestructive,
        )
    }
}

/** [AppConfirmSheet] without the window. Keep it a pure function of its arguments. */
@Composable
fun AppConfirmSheetContent(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
    confirmDestructive: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            // `.sheet h3` — 19px/600 → the section rung, which already carries the weight.
            modifier = Modifier.padding(horizontal = AppDimension.Space.xs),
            text = title,
            style = AppUi.typography.text.section,
            color = AppUi.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(AppDimension.Space.sm))
        Text(
            // `.sheet .desc` — 15px on `--body`.
            modifier = Modifier.padding(horizontal = AppDimension.Space.xs),
            text = body,
            style = AppUi.typography.text.body,
            color = AppUi.colors.textSecondary,
        )
        emphasis?.let { line ->
            Spacer(modifier = Modifier.height(AppDimension.Space.sm))
            Text(
                modifier = Modifier.padding(horizontal = AppDimension.Space.xs),
                text = line,
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(AppDimension.Space.md))
        AppSheetItem(
            modifier = Modifier.testTag(CONFIRM_TAG),
            title = confirmLabel,
            onClick = onConfirm,
            destructive = confirmDestructive,
        )
        AppSheetItem(
            modifier = Modifier.testTag(DISMISS_TAG),
            title = dismissLabel,
            onClick = onDismiss,
        )
    }
}

/** Stable across every caller, so a UI test does not need to know which sheet it is looking at. */
const val CONFIRM_TAG: String = "AppConfirmSheetConfirm"

/** See [CONFIRM_TAG]. */
const val DISMISS_TAG: String = "AppConfirmSheetDismiss"

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppConfirmSheetContentPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier3)
                .padding(AppDimension.screenEdge),
        ) {
            AppConfirmSheetContent(
                title = "Выйти без сохранения?",
                body = "Несохранённые правки будут потеряны.",
                confirmLabel = "Выйти без сохранения",
                dismissLabel = "Продолжить правку",
                onConfirm = {},
                onDismiss = {},
                confirmDestructive = true,
            )
        }
    }
}
