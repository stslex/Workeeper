// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("MagicNumber")
internal class AllExercisesUiMapperTest {

    private val resourceWrapper = mockk<ResourceWrapper>().apply {
        every {
            getString(R.string.feature_all_exercises_footer_separator)
        } returns "·"
        every {
            getQuantityString(
                R.plurals.feature_all_exercises_session_count,
                any<Int>(),
                any(),
            )
        } answers {
            val count = secondArg<Int>()
            "$count sessions"
        }
        every {
            getQuantityString(
                R.plurals.feature_all_exercises_linked_trainings_count,
                any<Int>(),
                any(),
            )
        } answers {
            val count = secondArg<Int>()
            "in $count trainings"
        }
        every {
            getString(R.string.feature_all_exercises_last_trained_just_now)
        } returns "last just now"
        every {
            getQuantityString(
                R.plurals.feature_all_exercises_last_trained_minutes,
                any<Int>(),
                any(),
            )
        } answers {
            val count = secondArg<Int>()
            "last ${count}m ago"
        }
        every {
            getQuantityString(
                R.plurals.feature_all_exercises_last_trained_hours,
                any<Int>(),
                any(),
            )
        } answers {
            val count = secondArg<Int>()
            "last ${count}h ago"
        }
        every {
            getQuantityString(
                R.plurals.feature_all_exercises_last_trained_days,
                any<Int>(),
                any(),
            )
        } answers {
            val count = secondArg<Int>()
            "last ${count}d ago"
        }
    }

    @Test
    fun `composeFooterLabel hides every segment when all values are zero or null`() {
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = null,
        )
        assertEquals("", result)
    }

    @Test
    fun `composeFooterLabel renders all three segments when present`() {
        val now = 10_000_000_000L
        val fourDaysAgo = now - 4L * 24L * 60L * 60L * 1000L
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 12,
            linkedTrainingsCount = 3,
            lastTrainedAt = fourDaysAgo,
            nowMillis = now,
        )
        assertEquals("12 sessions · in 3 trainings · last 4d ago", result)
    }

    @Test
    fun `composeFooterLabel hides session segment when count is zero`() {
        val now = 10_000_000_000L
        val twoHoursAgo = now - 2L * 60L * 60L * 1000L
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 0,
            linkedTrainingsCount = 2,
            lastTrainedAt = twoHoursAgo,
            nowMillis = now,
        )
        assertEquals("in 2 trainings · last 2h ago", result)
    }

    @Test
    fun `composeFooterLabel hides linked-trainings segment when count is zero`() {
        val now = 10_000_000_000L
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 5,
            linkedTrainingsCount = 0,
            lastTrainedAt = null,
            nowMillis = now,
        )
        assertEquals("5 sessions", result)
    }

    @Test
    fun `composeFooterLabel hides last-trained segment when value is null`() {
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 1,
            linkedTrainingsCount = 1,
            lastTrainedAt = null,
        )
        assertEquals("1 sessions · in 1 trainings", result)
    }

    @Test
    fun `composeFooterLabel just-now segment when delta below one minute`() {
        val now = 10_000_000_000L
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = now - 30_000L,
            nowMillis = now,
        )
        assertEquals("last just now", result)
    }

    @Test
    fun `composeFooterLabel minute granularity for sub-hour deltas`() {
        val now = 10_000_000_000L
        val result = composeFooterLabel(
            resourceWrapper = resourceWrapper,
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = now - 15L * 60L * 1000L,
            nowMillis = now,
        )
        assertEquals("last 15m ago", result)
    }

    @Test
    fun `ExerciseListItemDomain toUi propagates non-zero stats into the model`() {
        val now = 10_000_000_000L
        val fourDaysAgo = now - 4L * 24L * 60L * 60L * 1000L
        val item = ExerciseListItemDomain(
            exercise = ExerciseDomain(
                uuid = "uuid-1",
                name = "Bench press",
                type = ExerciseTypeDomain.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
            ),
            tags = listOf("Push", "Chest"),
            sessionCount = 12,
            linkedTrainingsCount = 3,
            lastTrainedAt = fourDaysAgo,
        )
        val ui = item.toUi(resourceWrapper = resourceWrapper, nowMillis = now)
        assertEquals("uuid-1", ui.uuid)
        assertEquals(12, ui.sessionCount)
        assertEquals(3, ui.linkedTrainingsCount)
        assertEquals(fourDaysAgo, ui.lastTrainedAt)
        assertEquals("12 sessions · in 3 trainings · last 4d ago", ui.footerLabel)
        assertEquals(listOf("Push", "Chest"), ui.tags.toList())
    }

    @Test
    fun `ExerciseListItemDomain toUi yields empty footer when stats are absent`() {
        val item = ExerciseListItemDomain(
            exercise = ExerciseDomain(
                uuid = "uuid-2",
                name = "Pull-up",
                type = ExerciseTypeDomain.WEIGHTLESS,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
            ),
            tags = emptyList(),
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = null,
        )
        val ui = item.toUi(resourceWrapper = resourceWrapper)
        assertEquals("", ui.footerLabel)
        assertTrue(ui.tags.isEmpty())
    }
}
