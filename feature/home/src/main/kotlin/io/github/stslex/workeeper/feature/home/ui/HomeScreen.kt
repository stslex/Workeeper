// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.ui.components.ActiveSessionBanner

@Composable
internal fun HomeScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("HomeScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(R.string.feature_home_title),
        )
        when {
            state.isLoading -> AppLoadingIndicator(
                modifier = Modifier.fillMaxSize(),
            )

            state.activeSession != null -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimension.screenEdge),
            ) {
                Spacer(Modifier.height(AppDimension.Space.lg))
                ActiveSessionBanner(
                    info = state.activeSession,
                    nowMillis = state.nowMillis,
                    onClick = { consume(Action.Click.OnActiveSessionClick) },
                )
            }

            else -> AppEmptyState(
                modifier = Modifier.fillMaxSize(),
                headline = stringResource(R.string.feature_home_empty_headline),
                supportingText = stringResource(R.string.feature_home_empty_supporting),
                icon = Icons.Filled.FitnessCenter,
            )
        }
    }
}

@Preview
@Composable
private fun HomeScreenEmptyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            state = State(
                activeSession = null,
                nowMillis = 0L,
                isLoading = false,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenEmptyDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            state = State(
                activeSession = null,
                nowMillis = 0L,
                isLoading = false,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenWithSessionPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            state = State(
                activeSession = io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State.ActiveSessionInfo(
                    sessionUuid = "s",
                    trainingUuid = "t",
                    trainingName = "Push Day",
                    startedAt = 0L,
                    doneCount = 2,
                    totalCount = 5,
                ),
                nowMillis = 12 * 60_000L + 34_000L,
                isLoading = false,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenLoadingPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            state = State.INITIAL,
            consume = {},
        )
    }
}
