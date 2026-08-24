// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.export.mapper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [WorkoutExportMapper.epochToIso] to literal strings: the export is a persisted
 * artifact, so its timestamp rendering is an interface, not an implementation detail.
 */
internal class WorkoutExportMapperEpochToIsoTest {

    @Test
    fun `whole second epoch renders without fractional digits`() {
        assertEquals("2026-06-26T10:42:23Z", WorkoutExportMapper.epochToIso(1_782_470_543_000L))
    }

    @Test
    fun `epoch zero renders as the unix origin`() {
        assertEquals("1970-01-01T00:00:00Z", WorkoutExportMapper.epochToIso(0L))
    }

    @Test
    fun `sub-second remainder renders exactly three fractional digits`() {
        assertEquals("1970-01-01T00:00:00.999Z", WorkoutExportMapper.epochToIso(999L))
        assertEquals("1970-01-01T00:00:01.500Z", WorkoutExportMapper.epochToIso(1_500L))
    }

    @Test
    fun `negative epoch borrows from the previous second`() {
        assertEquals("1969-12-31T23:59:59.999Z", WorkoutExportMapper.epochToIso(-1L))
    }
}
