// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import android.text.format.DateUtils
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.data.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingListItem
import io.github.stslex.workeeper.feature.home.R
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
            else -> error("Unexpected string id: $id")
        }

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String = when (id) {
            R.plurals.feature_home_recent_exercises_count -> {
                if (quantity == 1) "$quantity exercise" else "$quantity exercises"
            }

            R.plurals.feature_home_recent_sets_count -> {
                if (quantity == 1) "$quantity set" else "$quantity sets"
            }

            else -> error("Unexpected plural id: $id")
        }

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("Not used in HomeUiMapperTest")

        override fun formatMediumDate(timestamp: Long): String =
            error("Not used in HomeUiMapperTest")
    }

    @Test
    fun `recent mapper uses adhoc label and pluralized stats`() {
        val nowMillis = 10 * DateUtils.MINUTE_IN_MILLIS

        val item = listOf(
            RecentSessionDataModel(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Ignored for adhoc",
                isAdhoc = true,
                startedAt = 0L,
                finishedAt = 5 * DateUtils.MINUTE_IN_MILLIS,
                exerciseCount = 1,
                setCount = 2,
            ),
        ).toRecentItems(nowMillis = nowMillis, resourceWrapper = resources).single()

        assertEquals("Ad-hoc workout", item.trainingName)
        assertEquals(
            DateUtils.getRelativeTimeSpanString(
                5 * DateUtils.MINUTE_IN_MILLIS,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString(),
            item.finishedAtRelativeLabel,
        )
        assertEquals(formatElapsedDuration(5 * DateUtils.MINUTE_IN_MILLIS), item.durationLabel)
        assertEquals("1 exercise · 2 sets", item.statsLabel)
    }

    @Test
    fun `recent mapper preserves template name and mixed count labels`() {
        val nowMillis = 20 * DateUtils.MINUTE_IN_MILLIS

        val item = listOf(
            RecentSessionDataModel(
                sessionUuid = "session-2",
                trainingUuid = "training-2",
                trainingName = "Push Day",
                isAdhoc = false,
                startedAt = 2 * DateUtils.MINUTE_IN_MILLIS,
                finishedAt = 17 * DateUtils.MINUTE_IN_MILLIS,
                exerciseCount = 3,
                setCount = 1,
            ),
        ).toRecentItems(nowMillis = nowMillis, resourceWrapper = resources).single()

        assertEquals("Push Day", item.trainingName)
        assertEquals(formatElapsedDuration(15 * DateUtils.MINUTE_IN_MILLIS), item.durationLabel)
        assertEquals("3 exercises · 1 set", item.statsLabel)
    }

    @Test
    fun `picker mapper handles recently used and never used templates`() {
        val nowMillis = 2 * DateUtils.HOUR_IN_MILLIS

        val items = listOf(
            TrainingListItem(
                data = TrainingDataModel(
                    uuid = "training-1",
                    name = "Push Day",
                    timestamp = 0L,
                ),
                exerciseCount = 4,
                lastSessionAt = DateUtils.HOUR_IN_MILLIS,
                isActive = false,
                activeSessionUuid = null,
                activeSessionStartedAt = null,
            ),
            TrainingListItem(
                data = TrainingDataModel(
                    uuid = "training-2",
                    name = "Leg Day",
                    timestamp = 0L,
                ),
                exerciseCount = 2,
                lastSessionAt = null,
                isActive = false,
                activeSessionUuid = null,
                activeSessionStartedAt = null,
            ),
        ).toPickerItems(nowMillis = nowMillis, resourceWrapper = resources)

        assertEquals("Push Day", items[0].name)
        assertEquals("4 exercises", items[0].exerciseCountLabel)
        assertEquals(
            DateUtils.getRelativeTimeSpanString(
                DateUtils.HOUR_IN_MILLIS,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString(),
            items[0].lastSessionRelativeLabel,
        )

        assertEquals("Leg Day", items[1].name)
        assertEquals("2 exercises", items[1].exerciseCountLabel)
        assertNull(items[1].lastSessionRelativeLabel)
    }
}
