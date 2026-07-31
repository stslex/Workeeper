// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
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
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun HomeScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
    activeSessionModifier: Modifier = Modifier,
) {
    val recent = state.pagingUiState().collectAsLazyPagingItems()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("HomeScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(R.string.feature_home_title),
            actions = {
                IconButton(
                    onClick = { consume(Action.Click.OnChartsClick) },
                    modifier = Modifier.testTag("HomeChartsButton"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QueryStats,
                        contentDescription = stringResource(R.string.feature_home_charts),
                    )
                }
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
        // Transitional shape, and deliberately still the old one. This commit changes the QUERY
        // (a Pager replaces a ten-row snapshot) and nothing else, so the screen keeps its v2.4
        // appearance and its existing three-branch gate — the `#s-list` extraction, `listSurface`'s
        // five verdicts and the kit's paging footers arrive in the next commit. Splitting it this
        // way is the point: a bisect across a mixed commit cannot separate a broken query from a
        // broken screen.
        val recentSettled = recent.loadState.refresh !is LoadState.Loading
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { AppLoadingIndicator() }

            state.activeSession == null && recent.itemCount == 0 && recentSettled -> EmptyContent(
                onStart = { consume(Action.Click.OnStartTrainingClick) },
                modifier = Modifier.fillMaxSize(),
            )

            else -> ListContent(
                state = state,
                recent = recent,
                consume = consume,
                activeSessionModifier = activeSessionModifier,
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
    recent: LazyPagingItems<RecentSessionItem>,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
    activeSessionModifier: Modifier = Modifier,
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
                    modifier = activeSessionModifier,
                    onClick = { consume(Action.Click.OnActiveSessionClick) },
                )
            }
        }
        if (state.showStartCta) {
            item(key = "start") {
                HomeStartCard(onClick = { consume(Action.Click.OnStartTrainingClick) })
            }
        }
        items(
            count = recent.itemCount,
            key = { index -> recent.peek(index)?.sessionUuid ?: index },
        ) { index ->
            val item = recent[index] ?: return@items
            RecentSessionRow(
                item = item,
                onClick = {
                    consume(Action.Click.OnRecentSessionClick(sessionUuid = item.sessionUuid))
                },
            )
        }
    }
}

/**
 * Preview state. `State.INITIAL` is gone — the state now carries a `PagingUiState`, which is a
 * flow factory and cannot be a constant — so previews build one over a fixed [PagingData].
 */
private fun previewState(
    activeSession: State.ActiveSessionInfo? = null,
    isActiveLoaded: Boolean = true,
    nowMillis: Long = 0L,
    items: List<RecentSessionItem> = emptyList(),
): State = State.init(
    pagingUiState = PagingUiState { flowOf(PagingData.from(items)) },
).copy(
    activeSession = activeSession,
    isActiveLoaded = isActiveLoaded,
    nowMillis = nowMillis,
)

@Preview
@Composable
private fun HomeScreenEmptyLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeScreen(state = previewState(), consume = {})
    }
}

@Preview
@Composable
private fun HomeScreenWithSessionPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeScreen(
            state = previewState(
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
            state = previewState(
                items = listOf(
                    RecentSessionItem(
                        sessionUuid = "s1",
                        trainingName = "Push day",
                        isAdhoc = false,
                        finishedAtRelativeLabel = "Yesterday",
                        durationLabel = "47:12",
                        statsLabel = "5 exercises \u00b7 18 sets",
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
        HomeScreen(state = previewState(isActiveLoaded = false), consume = {})
    }
}
