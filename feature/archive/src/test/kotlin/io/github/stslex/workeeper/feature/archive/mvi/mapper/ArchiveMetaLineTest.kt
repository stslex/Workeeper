// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveUiMapper.toUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The meta line's composition — kind first, tags last (§26), the one thing a golden cannot check.
 * The fake resolves ids to their own names so ordering is assertable without a `Context`.
 */
internal class ArchiveMetaLineTest {

    private val resources = object : ResourceWrapper {
        override fun getString(id: Int, vararg args: Any): String = when (id) {
            R.string.feature_archive_kind_exercise -> "KIND_EXERCISE"
            R.string.feature_archive_kind_training -> "KIND_TRAINING"
            R.string.feature_archive_meta_separator -> "·"
            R.string.feature_archive_label_archived -> "ARCHIVED"
            R.string.feature_archive_label_archived_since_format -> "SINCE(${args[0]})"
            else -> error("unexpected string id in the meta line: $id")
        }

        override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): String =
            error("the meta line uses no plurals")

        override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String =
            error("the drawn phrase is «в архиве с <date>»; a relative span cannot follow «с»")

        override fun formatMediumDate(timestamp: Long): String = error("unused")

        override fun formatDayMonth(timestamp: Long): String = "DAY_MONTH"
    }

    private fun exercise(
        tags: List<String> = emptyList(),
        archivedAt: Long = 1_720_000_000_000L,
    ) = ArchivedItem.Exercise(
        uuid = "uuid",
        name = "Румынская тяга",
        tags = tags,
        archivedAt = archivedAt,
        type = ExerciseTypeDomain.WEIGHTED,
    )

    private fun training(
        tags: List<String> = emptyList(),
        archivedAt: Long = 1_720_000_000_000L,
    ) = ArchivedItem.Training(
        uuid = "uuid",
        name = "Верх",
        tags = tags,
        archivedAt = archivedAt,
        exerciseCount = 8,
    )

    @Test
    fun `an exercise leads with its kind word`() {
        assertEquals(
            "KIND_EXERCISE · SINCE(DAY_MONTH)",
            exercise().toUi(resources).metaLine,
        )
    }

    @Test
    fun `a training leads with the other kind word`() {
        // The two payloads differ by exactly this token.
        assertEquals(
            "KIND_TRAINING · SINCE(DAY_MONTH)",
            training().toUi(resources).metaLine,
        )
    }

    @Test
    fun `the kind is first, ahead of the date`() {
        val line = exercise().toUi(resources).metaLine
        assertTrue(
            line.indexOf("KIND_EXERCISE") < line.indexOf("SINCE"),
            "kind must lead the line so it survives truncation: $line",
        )
    }

    @Test
    fun `tags come last, after the date`() {
        val line = exercise(tags = listOf("спина", "бицепс")).toUi(resources).metaLine
        assertEquals("KIND_EXERCISE · SINCE(DAY_MONTH) · спина · бицепс", line)
        assertTrue(line.indexOf("SINCE") < line.indexOf("спина"))
    }

    @Test
    fun `no tags leaves no dangling separator`() {
        val line = exercise(tags = emptyList()).toUi(resources).metaLine
        assertTrue(!line.endsWith("·"), "trailing separator on a tagless row: $line")
    }

    @Test
    fun `the date is day-and-month, not a relative span`() {
        // The fake throws on `getAbbreviatedRelativeTime`: "since 2 days ago" is not a sentence.
        assertTrue(exercise().toUi(resources).metaLine.contains("DAY_MONTH"))
    }

    @Test
    fun `a missing timestamp degrades to the bare word rather than a wrong date`() {
        assertEquals("KIND_EXERCISE · ARCHIVED", exercise(archivedAt = 0L).toUi(resources).metaLine)
    }
}
