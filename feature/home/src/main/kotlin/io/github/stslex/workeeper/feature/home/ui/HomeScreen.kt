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
 * Home, extracted against the drawn shell — and the extraction stops in four places.
 *
 * **Home has no drawing.** `pass2d.html` carries eleven `.screen` sections (`#s-live`, `#s-chart`,
 * `#s-ex`, `#s-past`, `#s-set`, `#s-empty`, `#s-list`, `#s-nav`, `#s-topbar`, `#s-arch`, `#s-band`) and not
 * one of them is this
 * screen; `#s-empty`'s three glyph-tile blocks are all-trainings', the chart's and all-exercises'.
 * So Home is a **derived** screen in §24's sense — it composes the shell drawn once in `#s-list`
 * and `#s-nav` — and everything outside that shell is undrawn and left alone.
 *
 * ## What the shell reaches, and is therefore rebuilt here
 *
 * - the recent-session **row** — `#s-list` `.row`, the skeleton's third payload (`RecentSessionRow`);
 * - the **paging tails** — `.pfoot` and `.perr`, via `pagingTailKind` and the kit's footers;
 * - the **cold-open** states — `#s-empty`'s last two frames, the spinner and the unruled `.perr`
 *   where row 1 will land, via `homeListSurface`.
 *
 * ## What is undrawn, and stops here rather than being invented past
 *
 * Four regions keep their v2.4 treatment and are photographed rather than derived: the
 * **active-session banner**, the **start card**, the **top bar's contents** (both Material
 * glyphs included — a deliberate non-fix) and the **empty state's copy**. The one thing
 * changed is the empty state's glyph, `Icons.Filled.FitnessCenter` → `AppIcons.Trainings`,
 * because a filled Material glyph in a system that has none is the defect this arc removes;
 * recorded as a derivation the pass may overrule.
 *
 * **Why each is undrawn, and what the mockup pass owes on them, is §26's "THE MOCKUP PASS"
 * row** — including that `#s-list`'s `.row.live` is a *row* treatment and cannot yield a
 * banner. Do not re-derive it here; do not invent past it.
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
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.feature_home_settings),
                    )
                }
            },
        )
        if (state.isLoading) {
            // The ACTIVE-SESSION flow only. It is one emission away and decides whether a banner or
            // a start card sits above the list, so drawing the list under an unknown answer would
            // move it a frame later. The list's own loading is `HomeListSurface.LOADING` and is
            // drawn in place, not here.
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
    // Computed HERE rather than inside `emptyRegion`: the deferral holds by staying in
    // composition after loading ends, and the empty region's item is removed the instant the list
    // has rows — so a call sited inside it would leave composition exactly when the hold was meant
    // to begin. See `rememberDeferredSurface`.
    val surface = rememberDeferredSurface(
        surface = homeListSurface(itemCount = recent.itemCount, loadState = recent.loadState),
        loadingSurface = HomeListSurface.LOADING,
    )

    LazyColumn(
        modifier = modifier.testTag("HomeList"),
        // The rows are full-bleed and rule themselves (`#s-list` `.row`), so the list adds no
        // horizontal padding and no inter-item spacing. The banner and the start card are cards
        // and pad themselves. Home draws no FAB, so there is no 88dp clearance here — §24 measured
        // that already: only all-trainings and all-exercises draw one, and they are the same two.
        // The nav bar's inset is the host's, globally.
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
                    // One action, carrying nothing. Which training the primary button starts
                    // is §3.4's rule, and it is decided in `ClickHandler` off the body in
                    // state — the branch used to sit here, which put a decision in the graph
                    // and made a fifth mode a two-site change.
                    onStartClick = { consume(Action.Click.OnStartActionClick) },
                    // The `.setbar` way out is NOT that decision: it opens the picker
                    // whatever the mode is, which is why it keeps its own action.
                    onOtherTrainingClick = { consume(Action.Click.OnStartTrainingClick) },
                    onModeClick = { consume(Action.Click.OnModeLabelClick) },
                )
            }
        }
        // Gated on the DEFERRED verdict, not on `itemCount`: during the minimum hold the verdict is
        // still LOADING while the rows have already arrived, and a list that emits them anyway
        // draws them under the footer for the rest of the hold. The banner and the start card are
        // not part of this — they are not the list.
        // Gated on the DEFERRED verdict, not on `itemCount`: during the minimum hold the verdict
        // is still LOADING while the rows have already arrived, and a list that emits them anyway
        // draws them under the footer for the rest of the hold. The banner and the start card are
        // not part of this — they are not the list.
        if (listBody(surface, HomeListSurface.CONTENT) == ListBody.ROWS) {
            items(
                count = recent.itemCount,
                key = { index -> recent.peek(index)?.sessionUuid ?: "recent_$index" },
            ) { index ->
                recent[index]?.let { item ->
                    RecentSessionRow(
                        // §26, continuity motion. A finished session arrives at the head of this
                        // list the moment a workout ends, while the banner above it disappears — so
                        // every row below moves in the same frame. Pure transit, no character: the
                        // placement spec is positional and both fades are alpha, which is the split
                        // stated as plainly as one call can state it.
                        modifier = Modifier.animateItem(
                            fadeInSpec = continuityAlphaSpec(),
                            placementSpec = continuityPositionalSpec(),
                            fadeOutSpec = continuityAlphaSpec(),
                        ),
                        item = item,
                        // The drawing removes the last row's rule (`.frame .row:last-of-type`), so
                        // the list does not end on a hairline into empty space.
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
 * The append tail — §26 "Paging tails", three states and two drawings.
 *
 * Dispatch only. Which tail is drawn is [pagingTailKind]'s decision and is asserted directly,
 * because a golden cannot reach an append state: Paparazzi renders one frame of a source that
 * never appends (§27).
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
 * What the recent band draws when it has no rows — [homeListSurface]'s four verdicts.
 *
 * It is an **item inside the list**, not a `Box` behind it, and that is the drawing's own answer:
 * `#s-empty`'s cold-open frame puts the spinner "на месте первой строки" — where row 1 will land —
 * precisely so nothing moves when the page arrives. On Home that position is below the banner and
 * the start card rather than at the top of the screen, which is the same rule applied to a body
 * that has content above its list.
 *
 * **The crossfade excludes CONTENT and LOADING**, exactly as `ListSurface.crossfades` does and for
 * the measured reason recorded there: `collectAsLazyPagingItems()` always begins at `itemCount = 0`
 * with `refresh = Loading`, so every whole-screen golden composes `LOADING` first and reaches its
 * real verdict a frame later — and a single-frame harness photographs the transient. Keying the
 * `AnimatedContent` only on the drawn blocks makes it mount fresh at its real verdict, with current
 * and target equal and no transition to catch.
 */
private fun LazyListScope.emptyRegion(
    items: LazyPagingItems<RecentSessionItem>,
    surface: HomeListSurface?,
) {
    // The verdict is passed in, not recomputed: `rememberDeferredSurface` reports LOADING for as
    // long as the spinner must stay up, which is AFTER the data has stopped loading, and `null`
    // while the deferral window is open. Re-deriving it here would take the raw verdict and drop
    // both — the item leaves the list the moment the rows arrive.
    if (surface == null || surface == HomeListSurface.CONTENT) return
    item(key = "empty_region") {
        if (surface == HomeListSurface.LOADING) {
            // A load under 140ms draws nothing at all and the outgoing frame persists, which is
            // what stops the flash; one that gets past 140ms stays up for at least 260ms, which is
            // what stops a 141ms load flashing the spinner for 1ms instead.
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
                    // No action, deliberately. The start card directly above carries the same CTA,
                    // and `AppEmptyState` draws a button only when label AND handler are non-null —
                    // the mechanism §26's own empty-state row cites as load-bearing. Two identical
                    // buttons one above the other is the redundancy the count-bearing FAB was
                    // rejected for.
                    actionLabel = null,
                    onAction = null,
                )
            }
        }
    }
}

/**
 * Preview state. `State.INITIAL` is gone — the state carries a `PagingUiState`, which is a flow
 * factory and cannot be a constant — so previews build one over a fixed [PagingData].
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
