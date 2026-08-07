// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.core.ui.kit.golden.GoldenTheme
import io.github.stslex.workeeper.core.ui.kit.golden.LOCALE_RU
import io.github.stslex.workeeper.core.ui.kit.golden.golden
import io.github.stslex.workeeper.core.ui.kit.golden.goldenSubject
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.model.TagIdleRowUi
import io.github.stslex.workeeper.feature.home.mvi.model.WeekDayUi
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import io.github.stslex.workeeper.feature.home.ui.HomeScreen
import io.github.stslex.workeeper.feature.home.ui.components.ActiveSessionBanner
import io.github.stslex.workeeper.feature.home.ui.components.HomeStartCard
import io.github.stslex.workeeper.feature.home.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.home.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.home.ui.components.RecentSessionRow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The Home golden suite — **recorded from zero**, which is the reason it exists at this size.
 *
 * **These are an AFTER-picture only — there is no before.** §24 makes recording one an entry
 * condition of a rework PR and it was not met: the plugin, the gate wiring and all 26 PNGs landed
 * in the same commit as the row and screen rewrite. Not repaired retroactively, because a "before"
 * produced after the fact is a reconstruction and labelling one as a baseline is worse than having
 * none. What they *are* is the baseline for everything after — including the four undrawn regions
 * the mockup pass will rule on, which is why the banner and the start card are photographed.
 *
 * The harness comes from `core:ui:kit`'s testFixtures, so device config, tolerance and canvas width
 * are the same numbers the three siblings are measured at and cannot drift.
 *
 * ## Whole surface, both themes, transients as pairs
 *
 * Components: the recent row in its four payload states (short, ad-hoc, a name that truncates, a
 * name that clamps to two lines), both paging tails, the banner, the start card. Whole screens: the
 * four verdicts `homeListSurface` can return, plus a populated list and a list under a running
 * session.
 *
 * The row's pairs are the point rather than the count. **Truncating and clamping are photographed
 * separately** because they are different failures: a meta line that truncates proves the tail is
 * what disappears (§26 "Meta-line order"), and a name that clamps proves the 88dp row does not grow
 * — `min-height` holding every row to one size is the property the whole skeleton rests on, and one
 * picture of a short name cannot show it.
 *
 * ## The Cyrillic pair, and why it is on this screen's row specifically
 *
 * Home's meta line is the longest in the app — *when · how long · how much*, four tokens after the
 * stats string expands — and it is the only one whose tokens all arrive pre-formatted from a
 * `ResourceWrapper`. [recentRowRussian] renders it at `values-ru` so the truncation point is
 * measured in the language that ships rather than in the harness's `en` default.
 *
 * ## What these pictures deliberately do NOT gate
 *
 * Everything mid-transit, per §27. `homeListSurface`'s verdict selection, the append tail's
 * selection and the meta line's token order each have a direct assertion instead
 * (`HomeListSurfaceTest`, `HomePagingTailKindTest`, `RecentMetaLineTest`) — a golden covers *what a
 * surface looks like* and never *when it is shown*, and those are two gates of which only one is a
 * picture.
 */
internal class HomeGoldenTest {

    // ---- fixtures --------------------------------------------------------------------------------

    private val short = RecentSessionItem(
        sessionUuid = "s1",
        trainingName = "Ноги",
        isAdhoc = false,
        finishedAtRelativeLabel = "вчера",
        durationLabel = "47:12",
        statsLabel = "5 упражнений · 18 подходов",
    )

    private val adhoc = RecentSessionItem(
        sessionUuid = "s2",
        trainingName = "Свободная тренировка",
        isAdhoc = true,
        finishedAtRelativeLabel = "3 дня назад",
        durationLabel = "22:04",
        statsLabel = "2 упражнения · 6 подходов",
    )

    /** The meta line runs past the gutter; the TAIL is what must disappear, not the head. */
    private val truncating = RecentSessionItem(
        sessionUuid = "s3",
        trainingName = "Спина и бицепс",
        isAdhoc = false,
        finishedAtRelativeLabel = "в прошлый понедельник",
        durationLabel = "1:47:12",
        statsLabel = "11 упражнений · 42 подхода",
    )

    /** Two lines and an ellipsis, and the row must still be 88dp. */
    private val clamped = RecentSessionItem(
        sessionUuid = "s4",
        trainingName = "Верх тела с подтягиваниями, тягой и жимом стоя — полная программа",
        isAdhoc = false,
        finishedAtRelativeLabel = "неделю назад",
        durationLabel = "58:30",
        statsLabel = "9 упражнений · 31 подход",
    )

    private val session = State.ActiveSessionInfo(
        sessionUuid = "live",
        trainingUuid = "t1",
        trainingName = "Ноги и плечи",
        startedAt = 0L,
        doneCount = 2,
        totalCount = 5,
        elapsedDurationLabel = "12:04",
    )

    /** The «Неделя» readout, mid-week: three sessions, Mon/Wed/Fri filled. */
    private val week = StartCardBodyUi.Week(
        sessionsCountLabel = "3",
        sessionsUnitLabel = "тренировки",
        days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").mapIndexed { index, label ->
            WeekDayUi(label = label, isFilled = index == 0 || index == 2 || index == 4)
        }.toImmutableList(),
    )

    /** The same readout with nothing logged — what an empty history's card actually shows. */
    private val zeroWeek = week.copy(
        sessionsCountLabel = "0",
        sessionsUnitLabel = "тренировок",
        days = week.days.map { it.copy(isFilled = false) }.toImmutableList(),
    )

    private fun state(
        items: List<RecentSessionItem>,
        activeSession: State.ActiveSessionInfo? = null,
    ) = State.init(
        pagingUiState = PagingUiState { flowOf(PagingData.from(items)) },
    ).copy(activeSession = activeSession, isActiveLoaded = true, startCardBody = week)

    /**
     * **`PagingData.from` never settles inside one Paparazzi frame**, so an empty screen built with
     * it photographs the LOADING spinner and not the verdict the golden is named for.
     *
     * This is not a precaution copied across — it is the mechanism §27 records twice: once as
     * "a whole-screen golden named for a state can photograph a different state entirely", and once
     * as the reason `ListSurface.crossfades` excludes `LOADING`. Every settled empty state below
     * states its load states outright.
     */
    private fun pagingState(refresh: LoadState) = state(emptyList()).copy(
        startCardBody = zeroWeek,
        pagingUiState = PagingUiState {
            flowOf(
                PagingData.empty(
                    sourceLoadStates = LoadStates(
                        refresh = refresh,
                        prepend = LoadState.NotLoading(false),
                        append = LoadState.NotLoading(false),
                    ),
                ),
            )
        },
    )

    // ---- components ------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun recentRow(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        Column { Rows() }
    }

    /**
     * The same four rows at `values-ru`. The row's own strings are fixture-side, so what this pair
     * actually moves is the **resource-backed** part of the surface — and on this screen that is
     * everything the mapper produced. See the suite KDoc.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun recentRowRussian(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme, locale = LOCALE_RU) { Column { Rows() } }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun pagingLoadingTail(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) { PagingLoadingFooter() }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun pagingErrorTail(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) { PagingErrorFooter(onRetry = {}) }

    /**
     * The cold-open error, which is the append tail's `.perr` **unruled and moved** to row 1's
     * position (§26). Paired with [pagingErrorTail] deliberately: the rule and the reason string
     * are the only two things that may differ, and a single picture of either cannot show that.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun coldOpenError(theme: GoldenTheme, testInfo: TestInfo) = goldenSubject(testInfo, theme) {
        PagingErrorFooter(
            onRetry = {},
            reason = stringResource(R.string.feature_home_refresh_error),
            ruled = false,
        )
    }

    /** Undrawn region 1 — photographed so the mockup pass has a before-picture of what it rules. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun activeSessionBanner(theme: GoldenTheme, testInfo: TestInfo) =
        goldenSubject(testInfo, theme) { ActiveSessionBanner(info = session, onClick = {}) }

    /**
     * The rebuilt shell with its default readout (home-start-card.md §2, §3.1) — no longer an
     * undrawn region: the card is now specified, and this pair is its record. The lift's
     * shadow needs the page around the subject, hence the padding inside the frame.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCard(theme: GoldenTheme, testInfo: TestInfo) =
        startCardGolden(testInfo, theme, StartCardModeUi.WEEK, week)

    /** §3.2 — the gap with its name-and-date anchor. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardDaysSince(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.DAYS_SINCE_LAST,
        StartCardBodyUi.DaysSince(
            daysCountLabel = "4",
            daysUnitLabel = "дня",
            anchorLabel = "Ноги и плечи · 03/08/26",
        ),
    )

    /** §3.3 — three tags, bars proportional to idleness, «дней» once in the footnote. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardTagIdle(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.LAGGING_GROUPS,
        StartCardBodyUi.TagIdle(
            rows = persistentListOf(
                TagIdleRowUi(name = "спина", barFraction = 1f, daysCountLabel = "14"),
                TagIdleRowUi(name = "грудь", barFraction = 0.5f, daysCountLabel = "7"),
                TagIdleRowUi(name = "ноги", barFraction = 0.14f, daysCountLabel = "2"),
            ),
            footnoteLabel = "дней с последней тренировки группы",
        ),
    )

    /** §3.4 — name over the meta line, and the `.setbar` foot with «Другая тренировка». */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardForgotten(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.FORGOTTEN_TRAINING,
        StartCardBodyUi.Forgotten(
            trainingUuid = "t1",
            trainingName = "Спина и бицепс",
            metaLabel = "21 день · 6 упражнений",
        ),
    )

    /** HD1's witness — the never-run template ranks first and says «ещё ни разу». */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardForgottenNeverRun(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.FORGOTTEN_TRAINING,
        StartCardBodyUi.Forgotten(
            trainingUuid = "t2",
            trainingName = "Новый план",
            metaLabel = "ещё ни разу · 3 упражнения",
        ),
    )

    /**
     * HD2–HD4 — each mode's own empty state, mode still selected in the head, the plain
     * «Начать» still offered. Week and days-since share the no-sessions copy (one piece of
     * news) but not a picture: the head label differs.
     */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardEmptyWeek(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.WEEK,
        StartCardBodyUi.Empty(message = "Ещё ни одной тренировки"),
    )

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardEmptyDaysSince(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.DAYS_SINCE_LAST,
        StartCardBodyUi.Empty(message = "Ещё ни одной тренировки"),
    )

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardEmptyTags(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.LAGGING_GROUPS,
        StartCardBodyUi.Empty(
            message = "Упражнения без тегов — отмечайте группы, и они появятся здесь",
        ),
    )

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun startCardEmptyTemplates(theme: GoldenTheme, testInfo: TestInfo) = startCardGolden(
        testInfo,
        theme,
        StartCardModeUi.FORGOTTEN_TRAINING,
        StartCardBodyUi.Empty(message = "Пока нет шаблонов"),
    )

    private fun startCardGolden(
        testInfo: TestInfo,
        theme: GoldenTheme,
        mode: StartCardModeUi,
        body: StartCardBodyUi,
    ) = goldenSubject(testInfo, theme) {
        HomeStartCard(
            mode = mode,
            body = body,
            onStartClick = {},
            onOtherTrainingClick = {},
            modifier = Modifier.padding(AppDimension.screenEdge),
        )
    }

    // ---- whole surface: every verdict the selector can return -------------------------------------

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenList(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        HomeScreen(state = state(listOf(short, adhoc, truncating, clamped)), consume = {})
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenWithActiveSession(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        HomeScreen(
            state = state(listOf(short, adhoc), activeSession = session),
            consume = {},
        )
    }

    /** `HomeListSurface.EMPTY` — settled, no history. No action on the block; the card has it. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenEmptyRegion(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        HomeScreen(state = pagingState(LoadState.NotLoading(true)), consume = {})
    }

    /** `HomeListSurface.LOADING` — the spinner where row 1 will land, not a centred loader. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenColdOpenLoading(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        HomeScreen(state = pagingState(LoadState.Loading), consume = {})
    }

    /** `HomeListSurface.REFRESH_ERROR` — B22's fourth region, drawn. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenColdOpenError(theme: GoldenTheme, testInfo: TestInfo) = golden(testInfo, theme) {
        HomeScreen(
            state = pagingState(LoadState.Error(IllegalStateException("boom"))),
            consume = {},
        )
    }

    /** The active-session flow itself unsettled: the whole body waits, the top bar does not. */
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun screenActiveSessionLoading(theme: GoldenTheme, testInfo: TestInfo) =
        golden(testInfo, theme) {
            HomeScreen(state = state(emptyList()).copy(isActiveLoaded = false), consume = {})
        }

    @Composable
    private fun Rows() {
        RecentSessionRow(item = short, showDivider = true, onClick = {})
        RecentSessionRow(item = adhoc, showDivider = true, onClick = {})
        RecentSessionRow(item = truncating, showDivider = true, onClick = {})
        RecentSessionRow(item = clamped, showDivider = false, onClick = {})
    }
}
