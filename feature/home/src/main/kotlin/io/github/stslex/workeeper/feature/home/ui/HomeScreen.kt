// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.ui.components.ActiveSessionBanner
import io.github.stslex.workeeper.feature.home.ui.components.HomeStartCard
import io.github.stslex.workeeper.feature.home.ui.components.RecentSessionRow
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun HomeScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("HomeScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(R.string.feature_home_title),
            actions = {
                IconButton(
                    onClick = { consume(Action.Click.OnSettingsClick) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.feature_home_settings),
                    )
                }
            },
        )
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { AppLoadingIndicator() }

            state.showEmptyState -> EmptyContent(
                onStart = { consume(Action.Click.OnStartTrainingClick) },
                modifier = Modifier.fillMaxSize(),
            )

            else -> ListContent(
                state = state,
                consume = consume,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun EmptyContent(
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AppEmptyState(
            headline = stringResource(R.string.feature_home_empty_headline),
            supportingText = stringResource(R.string.feature_home_empty_supporting),
            icon = Icons.Filled.FitnessCenter,
            actionLabel = stringResource(R.string.feature_home_start_cta_title),
            onAction = onStart,
        )
    }
}

@Composable
private fun ListContent(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = AppDimension.screenEdge,
            vertical = AppDimension.Space.md,
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        state.activeSession?.let { session ->
            item(key = "active") {
                ActiveSessionBanner(
                    info = session,
                    onClick = { consume(Action.Click.OnActiveSessionClick) },
                )
            }
        }
        if (state.showStartCta) {
            item(key = "start") {
                HomeStartCard(onClick = { consume(Action.Click.OnStartTrainingClick) })
            }
        }
        if (state.showRecentList) {
            items(items = state.recent, key = { it.sessionUuid }) { recent ->
                RecentSessionRow(
                    item = recent,
                    onClick = {
                        consume(Action.Click.OnRecentSessionClick(sessionUuid = recent.sessionUuid))
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenEmptyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(
            state = State.INITIAL.copy(isActiveLoaded = true, isRecentLoaded = true),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenWithSessionPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            state = State.INITIAL.copy(
                activeSession = State.ActiveSessionInfo(
                    sessionUuid = "s",
                    trainingUuid = "t",
                    trainingName = "Push Day",
                    startedAt = 0L,
                    doneCount = 2,
                    totalCount = 5,
                    elapsedDurationLabel = "12:34",
                ),
                nowMillis = 12 * 60_000L + 34_000L,
                isActiveLoaded = true,
                isRecentLoaded = true,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun HomeScreenStartCtaWithRecentPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            state = State.INITIAL.copy(
                isActiveLoaded = true,
                isRecentLoaded = true,
                recent = persistentListOf(
                    RecentSessionItem(
                        sessionUuid = "s1",
                        trainingName = "Push day",
                        isAdhoc = false,
                        finishedAtRelativeLabel = "Yesterday",
                        durationLabel = "47:12",
                        statsLabel = "5 exercises · 18 sets",
                    ),
                ),
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
