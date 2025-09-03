package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseButtonsRow
import io.github.stslex.workeeper.feature.exercise.ui.components.ExercisedColumn
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseFeatureWidget(
    consume: (action: Action) -> Unit,
    state: State,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHostWidget(
                onActionClick = {
                    consume(Action.Click.ConfirmedDelete)
                },
                snackbarHostState = snackbarHostState
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(AppDimension.Padding.big)
        ) {

            ExerciseButtonsRow(
                isDeleteVisible = state.uuid.isNullOrBlank().not(),
                onCancelClick = { consume(Action.Click.Cancel) },
                onConfirmClick = { consume(Action.Click.Save) },
                onDeleteClick = { consume(Action.Click.Delete) }
            )

            Spacer(Modifier.height(AppDimension.Padding.big))

            ExercisedColumn(
                modifier = Modifier
                    .padding(vertical = AppDimension.Padding.medium),
                state = state,
                consume = consume
            )

        }
    }
}

@Composable
private fun SnackbarHostWidget(
    snackbarHostState: SnackbarHostState,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = snackbarHostState
    ) { data ->
        Snackbar(
            modifier = modifier
                .padding(AppDimension.Padding.small),
            action = {
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier.padding(horizontal = AppDimension.Padding.small),
                    colors = ButtonDefaults.textButtonColors()
                        .copy(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                ) {
                    Text("Delete")
                }
            },
            actionContentColor =MaterialTheme.colorScheme.errorContainer ,
            dismissActionContentColor = MaterialTheme.colorScheme.primaryContainer,
            actionOnNewLine = true,
            dismissAction = {
                TextButton(
                    modifier = Modifier.padding(horizontal = AppDimension.Padding.small),
                    onClick = {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    },
                    colors = ButtonDefaults.textButtonColors()
                        .copy(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                ) {
                    Text("Cancel")
                }
            }
        ) {
            Text(data.visuals.message)
        }
    }
}

@Composable
@Preview(showSystemUi = false, showBackground = false)
private fun ExerciseFeatureWidgetPreview() {
    AppTheme {
        ExerciseFeatureWidget(
            consume = {},
            state = State(),
            modifier = Modifier,
            snackbarHostState = SnackbarHostState()
        )
    }
}