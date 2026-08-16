// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import android.text.format.DateUtils
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toPickerItems
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toRecentItem
import io.github.stslex.workeeper.feature.home.mvi.mapper.HomeUiMapper.toUi
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class HomeUiMapperTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = when (id) {
            R.string.feature_home_recent_adhoc_label -> "Ad-hoc workout"
            R.string.feature_home_recent_stats_format -> "${args[0]} · ${args[1]}"
            R.string.feature_home_start_week_day_mon -> "Mo"
            R.string.feature_home_start_week_day_tue -> "Tu"
            R.string.feature_home_start_week_day_wed -> "We"
            R.string.feature_home_start_week_day_thu -> "Th"
            R.string.feature_home_start_week_day_fri -> "Fr"
            R.string.feature_home_start_week_day_sat -> "Sa"
            R.string.feature_home_start_week_day_sun -> "Su"
            R.string.feature_home_start_empty_sessions -> "No workouts yet"
            R.string.feature_home_start_empty_tags -> "No tagged exercises"
            R.string.feature_home_picker_empty -> "No templates yet"
            R.string.feature_home_start_never_run -> "never yet"
            R.string.feature_home_start_groups_footnote -> "days since the group's last workout"
            else -> error("Unexpected string id: $id")
        }

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String = when (id) {
            R.plurals.feature_home_recent_exercises_count -> {
                if (quantity == 1) "$quantity exercise" else "$quantity exercises"
            }

            R.plurals.feature_home_recent_sets_count -> {
                if (quantity == 1) "$quantity set" else "$quantity sets"
            }

            R.plurals.feature_home_start_week_sessions_count -> {
                if (quantity == 1) "workout" else "workouts"
            }

            R.plurals.feature_home_start_days_count -> {
                if (quantity == 1) "day" else "days"
            }

            R.plurals.feature_home_start_days_idle_count -> {
                if (quantity == 1) "$quantity day" else "$quantity days"
            }

            else -> error("Unexpected plural id: $id")
        }

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

    @Test
    fun `recent mapper uses adhoc label and pluralized stats`() {
        val nowMillis = 10 * DateUtils.MINUTE_IN_MILLIS

        val item = RecentSessionDomain(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            trainingName = "Ignored for adhoc",
            isAdhoc = true,
            startedAt = 0L,
            finishedAt = 5 * DateUtils.MINUTE_IN_MILLIS,
            exerciseCount = 1,
            setCount = 2,
        ).toRecentItem(nowMillis = nowMillis, resourceWrapper = resources)

        assertEquals("Ad-hoc workout", item.trainingName)
        assertEquals(
            "rel:${5 * DateUtils.MINUTE_IN_MILLIS}@$nowMillis",
            item.finishedAtRelativeLabel,
        )
        assertEquals(formatElapsedDuration(5 * DateUtils.MINUTE_IN_MILLIS), item.durationLabel)
        assertEquals("1 exercise · 2 sets", item.statsLabel)
    }

    @Test
    fun `recent mapper preserves template name and mixed count labels`() {
        val nowMillis = 20 * DateUtils.MINUTE_IN_MILLIS

        val item = RecentSessionDomain(
            sessionUuid = "session-2",
            trainingUuid = "training-2",
            trainingName = "Push Day",
            isAdhoc = false,
            startedAt = 2 * DateUtils.MINUTE_IN_MILLIS,
            finishedAt = 17 * DateUtils.MINUTE_IN_MILLIS,
            exerciseCount = 3,
            setCount = 1,
        ).toRecentItem(nowMillis = nowMillis, resourceWrapper = resources)

        assertEquals("Push Day", item.trainingName)
        assertEquals(formatElapsedDuration(15 * DateUtils.MINUTE_IN_MILLIS), item.durationLabel)
        assertEquals("3 exercises · 1 set", item.statsLabel)
    }

    @Test
    fun `picker mapper handles recently used and never used templates`() {
        val nowMillis = 2 * DateUtils.HOUR_IN_MILLIS

        val items = listOf(
            TrainingListItemDomain(
                uuid = "training-1",
                name = "Push Day",
                exerciseCount = 4,
                lastSessionAt = DateUtils.HOUR_IN_MILLIS,
            ),
            TrainingListItemDomain(
                uuid = "training-2",
                name = "Leg Day",
                exerciseCount = 2,
                lastSessionAt = null,
            ),
        ).toPickerItems(nowMillis = nowMillis, resourceWrapper = resources)

        assertEquals("Push Day", items[0].name)
        assertEquals("4 exercises", items[0].exerciseCountLabel)
        assertEquals(
            "rel:${DateUtils.HOUR_IN_MILLIS}@$nowMillis",
            items[0].lastSessionRelativeLabel,
        )

        assertEquals("Leg Day", items[1].name)
        assertEquals("2 exercises", items[1].exerciseCountLabel)
        assertNull(items[1].lastSessionRelativeLabel)
    }

    @Test
    fun `week mapper renders count as digits, resolves the unit plural, fills trained days`() {
        val week = WeekReadoutDomain(
            sessionsThisWeek = 3,
            trainedDayIndexes = setOf(0, 2, 4),
        ).toUi(resources)

        assertEquals("3", week.sessionsCountLabel)
        assertEquals("workouts", week.sessionsUnitLabel)
        assertEquals(listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"), week.days.map { it.label })
        assertEquals(
            listOf(true, false, true, false, true, false, false),
            week.days.map { it.isFilled },
        )
    }

    @Test
    fun `week mapper with no sessions renders zero over seven unfilled pills`() {
        val week = WeekReadoutDomain(
            sessionsThisWeek = 0,
            trainedDayIndexes = emptySet(),
        ).toUi(resources)

        assertEquals("0", week.sessionsCountLabel)
        assertEquals(7, week.days.size)
        assertEquals(0, week.days.count { it.isFilled })
    }

    @Test
    fun `week mapper uses the singular unit for one session`() {
        val week = WeekReadoutDomain(
            sessionsThisWeek = 1,
            trainedDayIndexes = setOf(6),
        ).toUi(resources)

        assertEquals("1", week.sessionsCountLabel)
        assertEquals("workout", week.sessionsUnitLabel)
        assertEquals(
            listOf(false, false, false, false, false, false, true),
            week.days.map { it.isFilled },
        )
    }

    // ---- start card readout → body -------------------------------------------------------

    @Test
    fun `days-since readout renders digits, unit and the name-date anchor`() {
        val body = StartCardReadoutDomain.DaysSince(
            daysSince = 4,
            lastTrainingName = "Ноги",
            lastIsAdhoc = false,
            lastFinishedAt = 0L,
        ).toUi(resources) as StartCardBodyUi.DaysSince

        assertEquals("4", body.daysCountLabel)
        assertEquals("days", body.daysUnitLabel)
        assertEquals("Ноги · ${DateTimeUtil.formatMillis(0L)}", body.anchorLabel)
    }

    @Test
    fun `days-since readout for an adhoc session anchors on the adhoc label`() {
        val body = StartCardReadoutDomain.DaysSince(
            daysSince = 1,
            lastTrainingName = "ignored",
            lastIsAdhoc = true,
            lastFinishedAt = 0L,
        ).toUi(resources) as StartCardBodyUi.DaysSince

        assertEquals("day", body.daysUnitLabel)
        assertEquals("Ad-hoc workout · ${DateTimeUtil.formatMillis(0L)}", body.anchorLabel)
    }

    @Test
    fun `tag-idle readout scales bars against the longest idle and keeps bare counts`() {
        val body = StartCardReadoutDomain.TagIdle(
            entries = listOf(
                StartCardReadoutDomain.TagIdle.Entry(name = "спина", daysIdle = 14),
                StartCardReadoutDomain.TagIdle.Entry(name = "грудь", daysIdle = 7),
                StartCardReadoutDomain.TagIdle.Entry(name = "ноги", daysIdle = 0),
            ),
        ).toUi(resources) as StartCardBodyUi.TagIdle

        assertEquals(listOf("спина", "грудь", "ноги"), body.rows.map { it.name })
        assertEquals(listOf(1f, 0.5f, 0f), body.rows.map { it.barFraction })
        assertEquals(listOf("14", "7", "0"), body.rows.map { it.daysCountLabel })
        assertEquals("days since the group's last workout", body.footnoteLabel)
    }

    @Test
    fun `tag-idle readout with all groups trained today draws empty bars, not NaN`() {
        val body = StartCardReadoutDomain.TagIdle(
            entries = listOf(
                StartCardReadoutDomain.TagIdle.Entry(name = "спина", daysIdle = 0),
            ),
        ).toUi(resources) as StartCardBodyUi.TagIdle

        assertEquals(0f, body.rows.single().barFraction)
    }

    @Test
    fun `forgotten readout joins days idle and composition`() {
        val body = StartCardReadoutDomain.Forgotten(
            trainingUuid = "t1",
            trainingName = "Спина и бицепс",
            daysIdle = 21,
            exerciseCount = 6,
        ).toUi(resources) as StartCardBodyUi.Forgotten

        assertEquals("t1", body.trainingUuid)
        assertEquals("Спина и бицепс", body.trainingName)
        assertEquals("21 days · 6 exercises", body.metaLabel)
    }

    @Test
    fun `forgotten readout for a never-run template says so instead of a day count`() {
        val body = StartCardReadoutDomain.Forgotten(
            trainingUuid = "t1",
            trainingName = "Новый план",
            daysIdle = null,
            exerciseCount = 3,
        ).toUi(resources) as StartCardBodyUi.Forgotten

        assertEquals("never yet · 3 exercises", body.metaLabel)
    }

    @Test
    fun `each empty readout carries its own mode's copy`() {
        assertEquals(
            "No workouts yet",
            (StartCardReadoutDomain.NoSessions.toUi(resources) as StartCardBodyUi.Empty).message,
        )
        assertEquals(
            "No tagged exercises",
            (StartCardReadoutDomain.NoTaggedHistory.toUi(resources) as StartCardBodyUi.Empty).message,
        )
        assertEquals(
            "No templates yet",
            (StartCardReadoutDomain.NoTemplates.toUi(resources) as StartCardBodyUi.Empty).message,
        )
    }

    @Test
    fun `week readout wrapped in the sealed type maps through the week mapper`() {
        val body = StartCardReadoutDomain.Week(
            WeekReadoutDomain(sessionsThisWeek = 2, trainedDayIndexes = setOf(1)),
        ).toUi(resources) as StartCardBodyUi.Week

        assertEquals("2", body.sessionsCountLabel)
        assertEquals(
            listOf(false, true, false, false, false, false, false),
            body.days.map { it.isFilled },
        )
    }
}
