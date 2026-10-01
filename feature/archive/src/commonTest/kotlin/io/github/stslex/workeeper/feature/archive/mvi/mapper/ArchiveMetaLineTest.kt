// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")

package io.github.stslex.workeeper.feature.archive.mvi.mapper

import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.archive.mvi.mapper.ArchiveUiMapper.toUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The meta line's composition — kind first, tags last (§26), the one thing a golden cannot check.
 * The copy is named after its own role so ordering is assertable without resources.
 */
internal class ArchiveMetaLineTest {

    private val copy = ArchiveMetaCopy(
        exerciseKind = "KIND_EXERCISE",
        trainingKind = "KIND_TRAINING",
        archived = "ARCHIVED",
        separator = "·",
    )

    /** What the handler hands over: the since-phrase dated by `formatDayMonth`. */
    private val archivedSince = "SINCE(DAY_MONTH)"

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
            exercise().toUi(copy, archivedSince).metaLine,
        )
    }

    @Test
    fun `a training leads with the other kind word`() {
        // The two payloads differ by exactly this token.
        assertEquals(
            "KIND_TRAINING · SINCE(DAY_MONTH)",
            training().toUi(copy, archivedSince).metaLine,
        )
    }

    @Test
    fun `the kind is first, ahead of the date`() {
        val line = exercise().toUi(copy, archivedSince).metaLine
        assertTrue(
            line.indexOf("KIND_EXERCISE") < line.indexOf("SINCE"),
            "kind must lead the line so it survives truncation: $line",
        )
    }

    @Test
    fun `tags come last, after the date`() {
        val line = exercise(tags = listOf("спина", "бицепс")).toUi(copy, archivedSince).metaLine
        assertEquals("KIND_EXERCISE · SINCE(DAY_MONTH) · спина · бицепс", line)
        assertTrue(line.indexOf("SINCE") < line.indexOf("спина"))
    }

    @Test
    fun `no tags leaves no dangling separator`() {
        val line = exercise(tags = emptyList()).toUi(copy, archivedSince).metaLine
        assertTrue(!line.endsWith("·"), "trailing separator on a tagless row: $line")
    }

    @Test
    fun `the date is day-and-month, not a relative span`() {
        // The handler dates the phrase with `formatDayMonth`: "since 2 days ago" is not a sentence.
        assertTrue(exercise().toUi(copy, archivedSince).metaLine.contains("DAY_MONTH"))
    }

    @Test
    fun `a missing timestamp degrades to the bare word rather than a wrong date`() {
        assertEquals(
            "KIND_EXERCISE · ARCHIVED",
            exercise(archivedAt = 0L).toUi(copy, archivedSince).metaLine,
        )
    }
}
