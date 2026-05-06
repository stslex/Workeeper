// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

/**
 * Educates the user on what the PR badge means. Mounted by any screen that wants to
 * surface the explainer when the badge is tapped — replaces the v2.4-pre tooltip
 * that was unreachable via tap and clipped at the right viewport edge.
 */
@Composable
fun PrExplainerDialog(onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(R.string.core_ui_kit_pr_explainer_title),
        body = stringResource(R.string.core_ui_kit_pr_explainer_body),
        confirmLabel = stringResource(R.string.core_ui_kit_pr_explainer_confirm),
        onConfirm = onDismiss,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrExplainerDialogPreview() {
    AppTheme {
        PrExplainerDialog(onDismiss = {})
    }
}
