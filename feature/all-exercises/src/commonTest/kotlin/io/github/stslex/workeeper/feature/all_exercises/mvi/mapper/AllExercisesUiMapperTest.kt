// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.mvi.mapper

import io.github.stslex.workeeper.core.ui.kit.strings.StringResolver
import io.github.stslex.workeeper.feature.all_exercises.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseListItemDomain
import io.github.stslex.workeeper.feature.all_exercises.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.composeFooterLabel
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.toBlockedArchiveDialog
import io.github.stslex.workeeper.feature.all_exercises.mvi.mapper.AllExercisesUiMapper.toUi
import io.github.stslex.workeeper.feature.all_exercises.resources.Res
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_more
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_blocked_archive_used_in_format
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_bulk_archive_success
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_footer_separator
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_days
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_hours
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_just_now
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_last_trained_minutes
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_linked_trainings_count
import io.github.stslex.workeeper.feature.all_exercises.resources.feature_all_exercises_session_count
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class AllExercisesUiMapperTest {

    private val stringResolver = MapperStringResolver()

    @Test
    fun `composeFooterLabel hides every segment when all values are zero or null`() = runTest {
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = null,
        )
        assertEquals("", result)
    }

    @Test
    fun `composeFooterLabel renders all three segments when present`() = runTest {
        val now = 10_000_000_000L
        val fourDaysAgo = now - 4L * 24L * 60L * 60L * 1000L
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 12,
            linkedTrainingsCount = 3,
            lastTrainedAt = fourDaysAgo,
            nowMillis = now,
        )
        assertEquals("12 sessions · in 3 trainings · last 4d ago", result)
    }

    @Test
    fun `composeFooterLabel hides session segment when count is zero`() = runTest {
        val now = 10_000_000_000L
        val twoHoursAgo = now - 2L * 60L * 60L * 1000L
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 0,
            linkedTrainingsCount = 2,
            lastTrainedAt = twoHoursAgo,
            nowMillis = now,
        )
        assertEquals("in 2 trainings · last 2h ago", result)
    }

    @Test
    fun `composeFooterLabel hides linked-trainings segment when count is zero`() = runTest {
        val now = 10_000_000_000L
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 5,
            linkedTrainingsCount = 0,
            lastTrainedAt = null,
            nowMillis = now,
        )
        assertEquals("5 sessions", result)
    }

    @Test
    fun `composeFooterLabel hides last-trained segment when value is null`() = runTest {
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 1,
            linkedTrainingsCount = 1,
            lastTrainedAt = null,
        )
        assertEquals("1 sessions · in 1 trainings", result)
    }

    @Test
    fun `composeFooterLabel just-now segment when delta below one minute`() = runTest {
        val now = 10_000_000_000L
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = now - 30_000L,
            nowMillis = now,
        )
        assertEquals("last just now", result)
    }

    @Test
    fun `composeFooterLabel minute granularity for sub-hour deltas`() = runTest {
        val now = 10_000_000_000L
        val result = composeFooterLabel(
            stringResolver = stringResolver,
            sessionCount = 0,
            linkedTrainingsCount = 0,
            lastTrainedAt = now - 15L * 60L * 1000L,
            nowMillis = now,
        )
        assertEquals("last 15m ago", result)
    }

    @Test
    fun `ExerciseListItemDomain toUi propagates non-zero stats into the model`() = runTest {
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
        val ui = item.toUi(stringResolver = stringResolver, nowMillis = now)
        assertEquals("uuid-1", ui.uuid)
        assertEquals(12, ui.sessionCount)
        assertEquals(3, ui.linkedTrainingsCount)
        assertEquals(fourDaysAgo, ui.lastTrainedAt)
        assertEquals("12 sessions · in 3 trainings · last 4d ago", ui.footerLabel)
        assertEquals(listOf("Push", "Chest"), ui.tags.toList())
    }

    @Test
    fun `ExerciseListItemDomain toUi yields empty footer when stats are absent`() = runTest {
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
        val ui = item.toUi(stringResolver = stringResolver)
        assertEquals("", ui.footerLabel)
        assertTrue(ui.tags.isEmpty())
    }

    @Test
    fun `toBlockedArchiveDialog truncates trainings and sets archived summary on partial block`() = runTest {
        val result = BulkArchiveResult(
            archivedCount = 1,
            blocked = listOf(
                BulkArchiveResult.BlockedExerciseDomain(
                    name = "Bench",
                    activeTrainings = listOf("Push", "Pull", "Legs", "Upper"),
                ),
            ),
        )

        val dialog = result.toBlockedArchiveDialog(stringResolver)

        assertEquals("1 archived", dialog.archivedSummary)
        assertEquals(1, dialog.items.size)
        assertEquals("Bench", dialog.items.first().exerciseName)
        // 4 trainings, first two shown, remaining two folded into "+2 more".
        assertEquals("used in Push, Pull +2 more", dialog.items.first().trainingsLabel)
    }

    @Test
    fun `toBlockedArchiveDialog omits archived summary and truncation when nothing archived`() = runTest {
        val result = BulkArchiveResult(
            archivedCount = 0,
            blocked = listOf(
                BulkArchiveResult.BlockedExerciseDomain(
                    name = "Bench",
                    activeTrainings = listOf("Push", "Legs"),
                ),
            ),
        )

        val dialog = result.toBlockedArchiveDialog(stringResolver)

        assertNull(dialog.archivedSummary)
        assertEquals("used in Push, Legs", dialog.items.first().trainingsLabel)
    }
}

/**
 * The strict resolver: today's stub strings, built from the quantity (never from a plural's format
 * arguments), for exactly the mapper's resources, and only when a read passes what production
 * passes. Any other resource or argument shape throws.
 */
private class MapperStringResolver : StringResolver {

    override suspend fun getString(resource: StringResource, vararg formatArgs: Any): String =
        when (resource) {
            Res.string.feature_all_exercises_footer_separator -> {
                check(formatArgs.isEmpty()) { "footer_separator takes no arguments" }
                "·"
            }

            Res.string.feature_all_exercises_last_trained_just_now -> {
                check(formatArgs.isEmpty()) { "last_trained_just_now takes no arguments" }
                "last just now"
            }

            Res.string.feature_all_exercises_blocked_archive_used_in_format -> {
                check(formatArgs.size == 1) { "used_in_format takes exactly one argument" }
                "used in ${formatArgs.single()}"
            }

            else -> error("the mapper must not read $resource")
        }

    override suspend fun getPluralString(
        resource: PluralStringResource,
        quantity: Int,
        vararg formatArgs: Any,
    ): String {
        check(formatArgs.toList() == listOf(quantity)) {
            "a plural read passes its quantity as its one format argument; got ${formatArgs.toList()}"
        }
        return when (resource) {
            Res.plurals.feature_all_exercises_session_count -> "$quantity sessions"
            Res.plurals.feature_all_exercises_linked_trainings_count -> "in $quantity trainings"
            Res.plurals.feature_all_exercises_last_trained_minutes -> "last ${quantity}m ago"
            Res.plurals.feature_all_exercises_last_trained_hours -> "last ${quantity}h ago"
            Res.plurals.feature_all_exercises_last_trained_days -> "last ${quantity}d ago"
            Res.plurals.feature_all_exercises_bulk_archive_success -> "$quantity archived"
            Res.plurals.feature_all_exercises_blocked_archive_more -> "+$quantity more"
            else -> error("the mapper must not read $resource")
        }
    }
}
