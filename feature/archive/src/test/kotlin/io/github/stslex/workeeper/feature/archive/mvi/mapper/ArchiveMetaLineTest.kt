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
 * The meta line's **composition** — the one thing on this row a picture cannot check.
 *
 * A golden photographs whatever string it is handed and is right about the pixels either way. What
 * it cannot see is that the kind is the *first* token, that the tags are *last*, or that a training
 * says «тренировка» rather than «упражнение» — and the kind token is this screen's entire answer to
 * "what am I looking at", since the row deliberately carries no badge and no leading glyph.
 *
 * §26 "Meta-line order": information first, tags last, because the line does not wrap and what
 * truncates is always the tail.
 *
 * The fake resolves ids to their own names so ordering is assertable without a `Context`; the point
 * is the sequence and the source of each token, not the localised wording.
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

    // ---- the kind token --------------------------------------------------------------------------

    @Test
    fun `an exercise leads with its kind word`() {
        assertEquals(
            "KIND_EXERCISE · SINCE(DAY_MONTH)",
            exercise().toUi(resources).metaLine,
        )
    }

    @Test
    fun `a training leads with the other kind word`() {
        // The two payloads differ by exactly this token. If they ever agree, the row has stopped
        // saying what it is and the segmented control becomes load-bearing for legibility — which
        // is the thing the kind-first rule exists to prevent.
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

    // ---- tags are the tail -----------------------------------------------------------------------

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

    // ---- the date ---------------------------------------------------------------------------------

    @Test
    fun `the date is day-and-month, not a relative span`() {
        // `getAbbreviatedRelativeTime` throws in the fake on purpose: the drawn phrase is
        // «в архиве с <date>», and "since 2 days ago" is not a sentence. Phrasing and formatter
        // have to agree, so this asserts which formatter the line is allowed to reach for.
        assertTrue(exercise().toUi(resources).metaLine.contains("DAY_MONTH"))
    }

    @Test
    fun `a missing timestamp degrades to the bare word rather than a wrong date`() {
        assertEquals("KIND_EXERCISE · ARCHIVED", exercise(archivedAt = 0L).toUi(resources).metaLine)
    }
}
