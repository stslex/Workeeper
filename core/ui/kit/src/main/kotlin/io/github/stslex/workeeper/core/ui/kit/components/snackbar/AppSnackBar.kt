package io.github.stslex.workeeper.core.ui.kit.components.snackbar

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
fun AppSnackBar(
    state: SnackbarHostState,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        modifier = modifier
            .padding(AppDimension.Padding.medium),
        shape = MaterialTheme.shapes.extraLarge,

        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        actionOnNewLine = true,
        action = {
            state.currentSnackbarData?.visuals?.actionLabel?.let { actionText ->
                OutlinedButton(
                    modifier = Modifier.padding(
                        horizontal = AppDimension.Padding.small,
                        vertical = AppDimension.Padding.small,
                    ),
                    onClick = onActionClick,
                    border = BorderStroke(
                        AppDimension.Border.medium,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text(
                        text = actionText,
                    )
                }
            }
        },
        dismissAction = {
            if (state.currentSnackbarData?.visuals?.withDismissAction == true) {
                OutlinedButton(
                    modifier = Modifier.padding(
                        horizontal = AppDimension.Padding.medium,
                        vertical = AppDimension.Padding.small,
                    ),
                    onClick = {
                        state.currentSnackbarData?.dismiss()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.core_ui_kit_snack_bar_dismiss),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        content = {
            state.currentSnackbarData?.visuals?.message?.let { text ->
                Text(
                    modifier = Modifier.padding(AppDimension.Padding.small),
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
    )
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
private fun AppSnackBarPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val snackBarHostState = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                snackBarHostState.showSnackbar(
                    message = "Delete this note?",
                    actionLabel = "Delete",
                    withDismissAction = true,
                )
            }

            AppSnackBar(
                state = snackBarHostState,
                onActionClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            )
        }
    }
}
