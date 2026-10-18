// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil
import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toPickerItems
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toRecentItem
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toUi
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.resources.Res
import io.github.stslex.workeeper.feature.home.resources.feature_home_picker_empty
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_adhoc_label
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_exercises_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_sets_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_recent_stats_format
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_days_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_days_idle_count
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_empty_sessions
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_empty_tags
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_groups_footnote
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_never_run
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_fri
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_mon
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_sat
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_sun
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_thu
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_tue
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_day_wed
import io.github.stslex.workeeper.feature.home.resources.feature_home_start_week_sessions_count
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `DateUtils.MINUTE_IN_MILLIS` and `HOUR_IN_MILLIS`, without Robolectric. */
private const val MINUTE_IN_MILLIS = 60_000L
private const val HOUR_IN_MILLIS = 3_600_000L

internal class HomeUiMapperTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String =
            error("HomeUiMapper reads no copy through ResourceWrapper; id $id")

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("HomeUiMapper reads no plural through ResourceWrapper; id $id")

        // Argument-keyed sentinel: pins that the mapper passes (timestamp, now) in that
        // order — the free-function predecessor took (now, event), so a mechanical swap
        // that kept the old order would produce "rel:<now>@<timestamp>" and fail.
        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            "rel:$timestamp@$now"

        override fun formatMediumDate(timestamp: Long): String =
            error("Not used in HomeUiMapperTest")

        override fun formatDayMonth(timestamp: Long): String =
            error("Not used in HomeUiMapperTest")
    }

    /**
     * Today's stub strings through the strict resolver, and only for the arguments production
     * passes: none for plain copy and the two unit words, the two for the stats format, and the
     * count for the three `%1$d` plurals. Any other resource or argument shape throws.
     */
    private val strings = object : StringResolver {
        override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String {
            if (resource == Res.string.feature_home_recent_stats_format) {
                check(formatArgs.size == 2) { "recent_stats_format takes exactly two arguments" }
                return "${formatArgs[0]} · ${formatArgs[1]}"
            }
            check(formatArgs.isEmpty()) { "$resource takes no arguments; got ${formatArgs.toList()}" }
            return when (resource) {
                Res.string.feature_home_recent_adhoc_label -> "Ad-hoc workout"
                Res.string.feature_home_start_week_day_mon -> "Mo"
                Res.string.feature_home_start_week_day_tue -> "Tu"
                Res.string.feature_home_start_week_day_wed -> "We"
                Res.string.feature_home_start_week_day_thu -> "Th"
                Res.string.feature_home_start_week_day_fri -> "Fr"
                Res.string.feature_home_start_week_day_sat -> "Sa"
                Res.string.feature_home_start_week_day_sun -> "Su"
                Res.string.feature_home_start_empty_sessions -> "No workouts yet"
                Res.string.feature_home_start_empty_tags -> "No tagged exercises"
                Res.string.feature_home_picker_empty -> "No templates yet"
                Res.string.feature_home_start_never_run -> "never yet"
                Res.string.feature_home_start_groups_footnote -> "days since the group's last workout"
                else -> error("Unexpected string resource: $resource")
            }
        }

        override suspend fun getPluralString(
            resource: PluralStringResource,
            quantity: Int,
            vararg formatArgs: Any,
        ): String = when (resource) {
            Res.plurals.feature_home_recent_exercises_count -> {
                checkCount(resource, quantity, formatArgs)
                if (quantity == 1) "$quantity exercise" else "$quantity exercises"
            }

            Res.plurals.feature_home_recent_sets_count -> {
                checkCount(resource, quantity, formatArgs)
                if (quantity == 1) "$quantity set" else "$quantity sets"
            }

            Res.plurals.feature_home_start_week_sessions_count -> {
                checkUnitWord(resource, formatArgs)
                if (quantity == 1) "workout" else "workouts"
            }

            Res.plurals.feature_home_start_days_count -> {
                checkUnitWord(resource, formatArgs)
                if (quantity == 1) "day" else "days"
            }

            Res.plurals.feature_home_start_days_idle_count -> {
                checkCount(resource, quantity, formatArgs)
                if (quantity == 1) "$quantity day" else "$quantity days"
            }

            else -> error("Unexpected plural resource: $resource")
        }

        /** A `%1$d` plural passes its count as its one format argument. */
        private fun checkCount(resource: PluralStringResource, quantity: Int, formatArgs: Array<out Any>) {
            check(formatArgs.toList() == listOf(quantity)) {
                "$resource passes its quantity as its one format argument; got ${formatArgs.toList()}"
            }
        }

        /** A unit-word plural carries no placeholder, so it passes no format argument. */
        private fun checkUnitWord(resource: PluralStringResource, formatArgs: Array<out Any>) {
            check(formatArgs.isEmpty()) {
                "$resource is a unit word and takes no format argument; got ${formatArgs.toList()}"
            }
        }
    }

    @Test
    fun `recent mapper uses adhoc label and pluralized stats`() = runTest {
        val nowMillis = 10 * MINUTE_IN_MILLIS

        val item = RecentSessionDomain(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            trainingName = "Ignored for adhoc",
            isAdhoc = true,
            startedAt = 0L,
            finishedAt = 5 * MINUTE_IN_MILLIS,
            exerciseCount = 1,
            setCount = 2,
        ).toRecentItem(nowMillis = nowMillis, resourceWrapper = resources, stringResolver = strings)

        assertEquals("Ad-hoc workout", item.trainingName)
        assertEquals(
            "rel:${5 * MINUTE_IN_MILLIS}@$nowMillis",
            item.finishedAtRelativeLabel,
        )
        assertEquals(formatElapsedDuration(5 * MINUTE_IN_MILLIS), item.durationLabel)
        assertEquals("1 exercise · 2 sets", item.statsLabel)
    }

    @Test
    fun `recent mapper preserves template name and mixed count labels`() = runTest {
        val nowMillis = 20 * MINUTE_IN_MILLIS

        val item = RecentSessionDomain(
            sessionUuid = "session-2",
            trainingUuid = "training-2",
            trainingName = "Push Day",
            isAdhoc = false,
            startedAt = 2 * MINUTE_IN_MILLIS,
            finishedAt = 17 * MINUTE_IN_MILLIS,
            exerciseCount = 3,
            setCount = 1,
        ).toRecentItem(nowMillis = nowMillis, resourceWrapper = resources, stringResolver = strings)

        assertEquals("Push Day", item.trainingName)
        assertEquals(formatElapsedDuration(15 * MINUTE_IN_MILLIS), item.durationLabel)
        assertEquals("3 exercises · 1 set", item.statsLabel)
    }

    @Test
    fun `picker mapper handles recently used and never used templates`() = runTest {
        val nowMillis = 2 * HOUR_IN_MILLIS

        val items = listOf(
            TrainingListItemDomain(
                uuid = "training-1",
                name = "Push Day",
                exerciseCount = 4,
                lastSessionAt = HOUR_IN_MILLIS,
            ),
            TrainingListItemDomain(
                uuid = "training-2",
                name = "Leg Day",
                exerciseCount = 2,
                lastSessionAt = null,
            ),
        ).toPickerItems(nowMillis = nowMillis, resourceWrapper = resources, stringResolver = strings)

        assertEquals("Push Day", items[0].name)
        assertEquals("4 exercises", items[0].exerciseCountLabel)
        assertEquals(
            "rel:$HOUR_IN_MILLIS@$nowMillis",
            items[0].lastSessionRelativeLabel,
        )

        assertEquals("Leg Day", items[1].name)
        assertEquals("2 exercises", items[1].exerciseCountLabel)
        assertNull(items[1].lastSessionRelativeLabel)
    }

    @Test
    fun `week mapper renders count as digits resolves the unit plural fills trained days`() = runTest {
        val week = WeekReadoutDomain(
            sessionsThisWeek = 3,
            trainedDayIndexes = setOf(0, 2, 4),
        ).toUi(strings)

        assertEquals("3", week.sessionsCountLabel)
        assertEquals("workouts", week.sessionsUnitLabel)
        assertEquals(listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"), week.days.map { it.label })
        assertEquals(
            listOf(true, false, true, false, true, false, false),
            week.days.map { it.isFilled },
        )
    }

    @Test
    fun `week mapper with no sessions renders zero over seven unfilled pills`() = runTest {
        val week = WeekReadoutDomain(
            sessionsThisWeek = 0,
            trainedDayIndexes = emptySet(),
        ).toUi(strings)

        assertEquals("0", week.sessionsCountLabel)
        assertEquals(7, week.days.size)
        assertEquals(0, week.days.count { it.isFilled })
    }

    @Test
    fun `week mapper uses the singular unit for one session`() = runTest {
        val week = WeekReadoutDomain(
            sessionsThisWeek = 1,
            trainedDayIndexes = setOf(6),
        ).toUi(strings)

        assertEquals("1", week.sessionsCountLabel)
        assertEquals("workout", week.sessionsUnitLabel)
        assertEquals(
            listOf(false, false, false, false, false, false, true),
            week.days.map { it.isFilled },
        )
    }

    // ---- start card readout → body -------------------------------------------------------

    @Test
    fun `days-since readout renders digits unit and the name-date anchor`() = runTest {
        val body = StartCardReadoutDomain.DaysSince(
            daysSince = 4,
            lastTrainingName = "Ноги",
            lastIsAdhoc = false,
            lastFinishedAt = 0L,
        ).toUi(strings) as StartCardBodyUi.DaysSince

        assertEquals("4", body.daysCountLabel)
        assertEquals("days", body.daysUnitLabel)
        assertEquals("Ноги · ${DateTimeUtil.formatMillis(0L)}", body.anchorLabel)
    }

    @Test
    fun `days-since readout for an adhoc session anchors on the adhoc label`() = runTest {
        val body = StartCardReadoutDomain.DaysSince(
            daysSince = 1,
            lastTrainingName = "ignored",
            lastIsAdhoc = true,
            lastFinishedAt = 0L,
        ).toUi(strings) as StartCardBodyUi.DaysSince

        assertEquals("day", body.daysUnitLabel)
        assertEquals("Ad-hoc workout · ${DateTimeUtil.formatMillis(0L)}", body.anchorLabel)
    }

    @Test
    fun `tag-idle readout scales bars against the longest idle and keeps bare counts`() = runTest {
        val body = StartCardReadoutDomain.TagIdle(
            entries = listOf(
                StartCardReadoutDomain.TagIdle.Entry(name = "спина", daysIdle = 14),
                StartCardReadoutDomain.TagIdle.Entry(name = "грудь", daysIdle = 7),
                StartCardReadoutDomain.TagIdle.Entry(name = "ноги", daysIdle = 0),
            ),
        ).toUi(strings) as StartCardBodyUi.TagIdle

        assertEquals(listOf("спина", "грудь", "ноги"), body.rows.map { it.name })
        assertEquals(listOf(1f, 0.5f, 0f), body.rows.map { it.barFraction })
        assertEquals(listOf("14", "7", "0"), body.rows.map { it.daysCountLabel })
        assertEquals("days since the group's last workout", body.footnoteLabel)
    }

    @Test
    fun `tag-idle readout with all groups trained today draws empty bars not NaN`() = runTest {
        val body = StartCardReadoutDomain.TagIdle(
            entries = listOf(
                StartCardReadoutDomain.TagIdle.Entry(name = "спина", daysIdle = 0),
            ),
        ).toUi(strings) as StartCardBodyUi.TagIdle

        assertEquals(0f, body.rows.single().barFraction)
    }

    @Test
    fun `forgotten readout joins days idle and composition`() = runTest {
        val body = StartCardReadoutDomain.Forgotten(
            trainingUuid = "t1",
            trainingName = "Спина и бицепс",
            daysIdle = 21,
            exerciseCount = 6,
        ).toUi(strings) as StartCardBodyUi.Forgotten

        assertEquals("t1", body.trainingUuid)
        assertEquals("Спина и бицепс", body.trainingName)
        assertEquals("21 days · 6 exercises", body.metaLabel)
    }

    @Test
    fun `forgotten readout for a never-run template says so instead of a day count`() = runTest {
        val body = StartCardReadoutDomain.Forgotten(
            trainingUuid = "t1",
            trainingName = "Новый план",
            daysIdle = null,
            exerciseCount = 3,
        ).toUi(strings) as StartCardBodyUi.Forgotten

        assertEquals("never yet · 3 exercises", body.metaLabel)
    }

    @Test
    fun `each empty readout carries its own mode's copy`() = runTest {
        assertEquals(
            "No workouts yet",
            (StartCardReadoutDomain.NoSessions.toUi(strings) as StartCardBodyUi.Empty).message,
        )
        assertEquals(
            "No tagged exercises",
            (StartCardReadoutDomain.NoTaggedHistory.toUi(strings) as StartCardBodyUi.Empty).message,
        )
        assertEquals(
            "No templates yet",
            (StartCardReadoutDomain.NoTemplates.toUi(strings) as StartCardBodyUi.Empty).message,
        )
    }

    @Test
    fun `week readout wrapped in the sealed type maps through the week mapper`() = runTest {
        val body = StartCardReadoutDomain.Week(
            WeekReadoutDomain(sessionsThisWeek = 2, trainedDayIndexes = setOf(1)),
        ).toUi(strings) as StartCardBodyUi.Week

        assertEquals("2", body.sessionsCountLabel)
        assertEquals(
            listOf(false, true, false, false, false, false, false),
            body.days.map { it.isFilled },
        )
    }
}
