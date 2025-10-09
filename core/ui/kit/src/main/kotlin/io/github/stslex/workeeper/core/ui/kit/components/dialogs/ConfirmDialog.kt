package io.github.stslex.workeeper.core.ui.kit.components.dialogs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConfirmDialog(
    text: String,
    action: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseAppDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(AppDimension.Padding.big))
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = action,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shapes = ButtonDefaults.shapes(
                        shape = MaterialTheme.shapes.largeIncreased,
                        pressedShape = MaterialTheme.shapes.largeIncreased,
                    ),
                ) {
                    Text(
                        text = "Confirm",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(modifier = Modifier.width(AppDimension.Padding.medium))

                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                            .copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    shapes = ButtonDefaults.shapes(
                        shape = MaterialTheme.shapes.largeIncreased,
                        pressedShape = MaterialTheme.shapes.largeIncreased,
                    ),
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
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
    wallpaper = androidx.compose.ui.tooling.preview.Wallpapers.YELLOW_DOMINATED_EXAMPLE,
)
private fun ConfirmDialogPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ConfirmDialog(
                text = "Are you sure you want to delete this item?",
                action = { },
                onDismissRequest = {},
            )
        }
    }
}
