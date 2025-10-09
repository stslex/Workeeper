package io.github.stslex.workeeper.core.ui.kit.components.dialogs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseAppDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { },
) {
    val size = LocalWindowInfo.current.containerDpSize
    val isLandscape = size.width > size.height

    val maxHeight = if (isLandscape) size.height * 0.5f else size.height * 0.6f
    val minWidth = if (isLandscape) size.width * 0.3f else size.width * 0.5f
    val minHeight = if (isLandscape) size.height * 0.4f else size.height * 0.2f

    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Box(
            modifier = modifier
                .padding(AppDimension.Padding.medium)
                .sizeIn(
                    minWidth = minWidth,
                    minHeight = minHeight,
                    maxWidth = size.width,
                    maxHeight = maxHeight,
                )
                .clip(MaterialTheme.shapes.extraLargeIncreased)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(AppDimension.Padding.large),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(
    showSystemUi = true,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:parent=pixel_5,orientation=landscape",
)
private fun BaseAppDialogPreview(
    @PreviewParameter(PreviewTextProvider::class)
    text: String,
) {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            BaseAppDialog(
                onDismissRequest = {},
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private class PreviewTextProvider : PreviewParameterProvider<String> {
    private val veryLongTextForDialog = """
        This is a very long text for dialog. This is done to test how the dialog behaves when there is a lot of text to display. 
        The quick brown fox jumps over the lazy dog. The quick brown fox jumps over the lazy dog. 
        The quick brown fox jumps over the lazy dog. The quick brown fox jumps over the lazy dog.
    """.trimIndent()

    override val values: Sequence<String> = sequenceOf(
        veryLongTextForDialog,
        "This is a simple dialog",
    )
}
