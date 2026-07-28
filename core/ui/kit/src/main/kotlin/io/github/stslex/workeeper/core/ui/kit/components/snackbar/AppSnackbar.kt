package io.github.stslex.workeeper.core.ui.kit.components.snackbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.toast.AppToast
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * Every transient message renders through the v3 `.toast` panel (extraction §1.9) — the old
 * inverse-surface Material snackbar was the farthest-from-mockup component the extraction
 * measured. This wrapper only adapts [SnackbarData] to [AppToast]; the panel itself is the
 * shared treatment.
 */
@Composable
fun AppSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    AppToast(
        message = snackbarData.visuals.message,
        actionLabel = snackbarData.visuals.actionLabel,
        onAction = snackbarData.visuals.actionLabel?.let { { snackbarData.performAction() } },
        modifier = modifier.padding(AppDimension.screenEdge),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSnackbarPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppUi.colors.surfaceTier0),
        ) {
            val state = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                state.showSnackbar(
                    message = "Saved",
                    actionLabel = "Undo",
                    withDismissAction = true,
                )
            }
            state.currentSnackbarData?.let {
                AppSnackbar(
                    snackbarData = it,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
