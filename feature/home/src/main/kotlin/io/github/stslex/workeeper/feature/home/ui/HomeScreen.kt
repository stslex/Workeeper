// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
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
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.components.collectAsItems
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.paging.ListBody
import io.github.stslex.workeeper.core.ui.kit.components.paging.listBody
import io.github.stslex.workeeper.core.ui.kit.components.paging.rememberDeferredSurface
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec
import io.github.stslex.workeeper.core.ui.kit.theme.continuityPositionalSpec
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.Action
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.ui.components.ActiveSessionBanner
import io.github.stslex.workeeper.feature.home.ui.components.HomeListSurface
import io.github.stslex.workeeper.feature.home.ui.components.HomeStartCard
import io.github.stslex.workeeper.feature.home.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.home.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.home.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.home.ui.components.RecentSessionRow
import io.github.stslex.workeeper.feature.home.ui.components.homeListSurface
import io.github.stslex.workeeper.feature.home.ui.components.pagingTailKind
import kotlinx.coroutines.flow.flowOf

/**
 * Home: top bar, active-session banner or start card, and the paged recent-session list.
 * See documentation/feature-specs/home-and-past-session.md.
 */
@Composable
internal fun HomeScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
    activeSessionModifier: Modifier = Modifier,
) {
    val recent = state.pagingUiState.collectAsItems()
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
                    modifier = Modifier.testTag("HomeSettingsButton"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.feature_home_settings),
                    )
                }
            },
        )
        if (state.isLoading) {
            // Gates on the ACTIVE-SESSION flow only; the list's own loading is drawn in place.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { AppLoadingIndicator() }
        } else {
            HomeBody(
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
private fun HomeBody(
    state: State,
    recent: LazyPagingItems<RecentSessionItem>,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
    activeSessionModifier: Modifier = Modifier,
) {
    // GUARD: compute here, never inside `emptyRegion` — the deferral holds only by staying in
    // composition, and that item is removed the instant the list has rows.
    val surface = rememberDeferredSurface(
        surface = homeListSurface(itemCount = recent.itemCount, loadState = recent.loadState),
        loadingSurface = HomeListSurface.LOADING,
    )

    LazyColumn(
        modifier = modifier.testTag("HomeList"),
        // Rows are full-bleed and rule themselves; the banner and start card pad themselves.
        contentPadding = PaddingValues(bottom = AppDimension.Space.md),
    ) {
        state.activeSession?.let { session ->
            item(key = "active") {
                ActiveSessionBanner(
                    info = session,
                    modifier = activeSessionModifier.padding(
                        horizontal = AppDimension.screenEdge,
                        vertical = AppDimension.Space.md,
                    ),
                    onClick = { consume(Action.Click.OnActiveSessionClick) },
                )
            }
        }
        if (state.showStartCta) {
            item(key = "start") {
                HomeStartCard(
                    mode = state.startCardMode,
                    body = state.startCardBody,
                    modifier = Modifier.padding(
                        horizontal = AppDimension.screenEdge,
                        vertical = AppDimension.Space.md,
                    ),
                    // Which training this starts is decided in `ClickHandler` off the body.
                    onStartClick = { consume(Action.Click.OnStartActionClick) },
                    // Opens the picker whatever the mode is, so it keeps its own action.
                    onOtherTrainingClick = { consume(Action.Click.OnStartTrainingClick) },
                    onModeClick = { consume(Action.Click.OnModeLabelClick) },
                )
            }
        }
        // GUARD: gate on the DEFERRED verdict, not `itemCount` — during the minimum hold the rows
        // have arrived but would draw under the footer. The banner and start card are not the list.
        if (listBody(surface, HomeListSurface.CONTENT) == ListBody.ROWS) {
            items(
                count = recent.itemCount,
                key = { index -> recent.peek(index)?.sessionUuid ?: "recent_$index" },
            ) { index ->
                recent[index]?.let { item ->
                    RecentSessionRow(
                        // Continuity motion: positional placement spec, alpha fades.
                        modifier = Modifier.animateItem(
                            fadeInSpec = continuityAlphaSpec(),
                            placementSpec = continuityPositionalSpec(),
                            fadeOutSpec = continuityAlphaSpec(),
                        ),
                        item = item,
                        // The last row drops its rule so the list does not end on a hairline.
                        showDivider = index < recent.itemCount - 1,
                        onClick = {
                            consume(
                                Action.Click.OnRecentSessionClick(sessionUuid = item.sessionUuid),
                            )
                        },
                    )
                }
            }
        }
        pagingTail(items = recent, onRetry = { recent.retry() })
        emptyRegion(items = recent, surface = surface)
    }
}

/**
 * The append tail. Which tail is drawn is [pagingTailKind]'s decision, asserted directly because
 * a golden cannot reach an append state.
 */
private fun LazyListScope.pagingTail(
    items: LazyPagingItems<RecentSessionItem>,
    onRetry: () -> Unit,
) {
    when (pagingTailKind(items.loadState.append)) {
        PagingTailKind.LOADING -> item(key = "paging_loading") { PagingLoadingFooter() }
        PagingTailKind.ERROR -> item(key = "paging_error") { PagingErrorFooter(onRetry = onRetry) }
        PagingTailKind.NONE -> Unit
    }
}

/**
 * What the recent band draws with no rows — [homeListSurface]'s verdicts, as an item inside the
 * list so nothing moves when row 1 lands. The crossfade excludes CONTENT and LOADING.
 */
private fun LazyListScope.emptyRegion(
    items: LazyPagingItems<RecentSessionItem>,
    surface: HomeListSurface?,
) {
    // GUARD: take the verdict as a parameter; re-deriving it drops both the post-load LOADING
    // report and the `null` returned while the deferral window is open.
    if (surface == null || surface == HomeListSurface.CONTENT) return
    item(key = "empty_region") {
        if (surface == HomeListSurface.LOADING) {
            // Under 140ms nothing is drawn; past 140ms the spinner stays up for at least 260ms.
            PagingLoadingFooter(modifier = Modifier.fillMaxWidth())
            return@item
        }
        val spec = continuityAlphaSpec<Float>()
        AnimatedContent(
            targetState = surface,
            transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) using null },
            label = "home-empty-region",
        ) { verdict ->
            when (verdict) {
                HomeListSurface.CONTENT, HomeListSurface.LOADING -> Unit

                HomeListSurface.REFRESH_ERROR -> PagingErrorFooter(
                    modifier = Modifier.testTag("HomeColdOpenError"),
                    onRetry = { items.retry() },
                    reason = stringResource(R.string.feature_home_refresh_error),
                    ruled = false,
                )

                HomeListSurface.EMPTY -> AppEmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppDimension.Space.xl)
                        .testTag("HomeEmptyState"),
                    headline = stringResource(R.string.feature_home_empty_headline),
                    supportingText = stringResource(R.string.feature_home_empty_supporting),
                    icon = AppIcons.Trainings,
                    // No action: the start card directly above carries the same CTA.
                    actionLabel = null,
                    onAction = null,
                )
            }
        }
    }
}

/**
 * Preview state over a fixed [PagingData]; `startCardMode` is set explicitly because `State.init`
 * leaves it null until DataStore answers.
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
    startCardMode = StartCardModeUi.WEEK,
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
                        trainingName = "Верх (с подтягиваниями)",
                        isAdhoc = false,
                        finishedAtRelativeLabel = "вчера",
                        durationLabel = "47:12",
                        statsLabel = "5 упражнений · 18 подходов",
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
