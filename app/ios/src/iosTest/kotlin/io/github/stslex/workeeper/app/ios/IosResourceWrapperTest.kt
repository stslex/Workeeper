// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.stslex.workeeper.app.ios

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.App
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyleNamed
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleAbbreviated
import platform.Foundation.NSThread
import platform.Foundation.NSTimeZone
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.timeZoneWithName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class IosResourceWrapperTest {
    @Test
    fun absoluteDatesUseLocaleZoneAndEpochMilliseconds() {
        val instant = millis("2026-07-22T00:30:00Z")
        for ((locale, utc, losAngeles) in listOf(
            Triple("en_US", "July 22", "July 21"),
            Triple("ru_RU", "22 июля", "21 июля"),
        )) {
            for ((zone, day, expected) in listOf(
                Triple("UTC", 22L, utc),
                Triple("America/Los_Angeles", 21L, losAngeles),
            )) {
                val wrapper = wrapper(locale, zone)
                assertText(expected, wrapper.formatDayMonth(instant))
                assertText(expectedMedium(locale, zone, 2026, 7, day), wrapper.formatMediumDate(instant))
            }
        }
        assertText("Dec 31, 1969", wrapper("en_US", "UTC").formatMediumDate(-1))
        assertText("December 31", wrapper("en_US", "UTC").formatDayMonth(-1))
        val buddhist = wrapper("en_US@calendar=buddhist", "UTC")
        assertText("July 22", buddhist.formatDayMonth(instant))
        val medium = buddhist.formatMediumDate(instant)
        assertText(expectedMedium("en_US@calendar=buddhist", "UTC", 2026, 7, 22), medium)
        assertTrue(medium.contains("2026"), medium)
        assertFalse(medium.contains("2569"), medium)
    }

    @Test
    fun relativeTimeUsesSuppliedNowAndMinutePrecision() {
        val now = millis("2020-04-12T12:00:00Z")
        for (locale in listOf("en_US", "ru_RU")) {
            val wrapper = wrapper(locale, "UTC")
            for (delta in listOf(0L, -59_999L, 59_999L)) {
                assertText(expectedRelative(locale, "second", 0), wrapper.getAbbreviatedRelativeTime(now + delta, now))
            }
            for ((delta, unit, count) in listOf(
                Triple(60_000L, "minute", 1L),
                Triple(3_599_999L, "minute", 59L),
                Triple(3_600_000L, "hour", 1L),
                Triple(86_399_999L, "hour", 23L),
            )) {
                for (direction in listOf(-1L, 1L)) {
                    assertText(
                        expectedRelative(locale, unit, count * direction),
                        wrapper.getAbbreviatedRelativeTime(now + delta * direction, now),
                    )
                }
            }
            assertText(expectedRelative(locale, "minute", 1), wrapper.getAbbreviatedRelativeTime(now, now - 60_000))
            assertText(expectedRelative(locale, "minute", -1), wrapper.getAbbreviatedRelativeTime(now, now + 60_000))
        }
    }

    @Test
    fun relativeDaysAndAbsoluteFallbackUseLocalCalendar() {
        val now = millis("2026-03-07T17:00:00Z")
        val afterDst = millis("2026-03-09T16:00:00Z")
        assertEquals(47 * 3_600_000L, afterDst - now)
        for (locale in listOf("en_US", "ru_RU")) {
            val wrapper = wrapper(locale, "America/New_York")
            assertText(expectedRelative(locale, "day", 2), wrapper.getAbbreviatedRelativeTime(afterDst, now))
            assertText(expectedRelative(locale, "day", -2), wrapper.getAbbreviatedRelativeTime(now, afterDst))
            val clock = millis("2026-07-15T16:00:00Z")
            for (direction in listOf(-1L, 1L)) {
                assertText(
                    expectedRelative(locale, "day", direction),
                    wrapper.getAbbreviatedRelativeTime(clock + direction * 86_400_000, clock),
                )
                assertText(
                    expectedRelative(locale, "day", if (direction > 0) 7 else -7),
                    wrapper.getAbbreviatedRelativeTime(clock + direction * 604_799_999, clock),
                )
                assertText(
                    expectedAbsolute(locale, "America/New_York", 2026, 7, if (direction > 0) 22 else 8, "MMMd"),
                    wrapper.getAbbreviatedRelativeTime(clock + direction * 604_800_000, clock),
                )
            }
            val losAngeles = wrapper(locale, "America/Los_Angeles")
            assertText(
                expectedAbsolute(locale, "America/Los_Angeles", 2026, 12, 31, "MMMd"),
                losAngeles.getAbbreviatedRelativeTime(millis("2027-01-01T00:30:00Z"), millis("2026-12-22T00:30:00Z")),
            )
            assertText(
                expectedAbsolute(locale, "America/Los_Angeles", 2025, 12, 31, "yMMMd"),
                losAngeles.getAbbreviatedRelativeTime(millis("2026-01-01T00:30:00Z"), millis("2026-01-10T00:30:00Z")),
            )
        }
    }

    @Test
    fun oneWrapperTracksSettingsAcrossCallers() = runBlocking {
        assertTrue(NSThread.isMainThread)
        val settings = MutableStateFlow(IosDateSettings("en_US", "UTC"))
        val wrapper = IosResourceWrapper { settings.value }
        val instants = listOf(millis("2026-07-22T00:30:00Z"), -1L, millis("2027-01-01T07:30:00Z"))
        for ((configuration, expected) in listOf(
            IosDateSettings("en_US", "UTC") to listOf("July 22", "December 31", "January 1"),
            IosDateSettings("ru_RU", "America/Los_Angeles") to listOf("21 июля", "31 декабря", "31 декабря"),
        )) {
            settings.value = configuration
            val civilDates = if (configuration.timeZoneName == "UTC") {
                listOf(Triple(2026L, 7L, 22L), Triple(1969L, 12L, 31L), Triple(2027L, 1L, 1L))
            } else {
                listOf(Triple(2026L, 7L, 21L), Triple(1969L, 12L, 31L), Triple(2026L, 12L, 31L))
            }
            val mediumDates = civilDates.map { (year, month, day) ->
                expectedMedium(configuration.localeIdentifier, configuration.timeZoneName, year, month, day)
            }
            val relative = expectedRelative(configuration.localeIdentifier, "minute", -1)
            fun verify(index: Int, instant: Long) {
                assertText(expected[index], wrapper.formatDayMonth(instant))
                assertText(mediumDates[index], wrapper.formatMediumDate(instant))
                assertText(relative, wrapper.getAbbreviatedRelativeTime(instant, instant + 60_000))
            }
            coroutineScope {
                val ready = Channel<Unit>(instants.size)
                val start = CompletableDeferred<Unit>()
                val workers = instants.mapIndexed { index, instant ->
                    async(Dispatchers.Default) {
                        ready.send(Unit)
                        start.await()
                        assertFalse(NSThread.isMainThread)
                        verify(index, instant)
                    }
                }
                repeat(instants.size) { ready.receive() }
                start.complete(Unit)
                instants.forEachIndexed { index, instant ->
                    assertTrue(NSThread.isMainThread)
                    verify(index, instant)
                }
                workers.awaitAll()
            }
        }
    }

    @Test
    fun androidResourceIdsFailExplicitly() {
        val wrapper = buildIosResourceWrapper()
        val stringError = assertFailsWith<UnsupportedOperationException> { wrapper.getString(720, "synthetic") }
        val pluralError = assertFailsWith<UnsupportedOperationException> {
            wrapper.getQuantityString(
                721,
                2,
                "synthetic",
            )
        }
        assertTrue(stringError.message.orEmpty().contains("720"), stringError.message)
        assertTrue(pluralError.message.orEmpty().contains("721"), pluralError.message)
        for ((method, error) in listOf("getString" to stringError, "getQuantityString" to pluralError)) {
            assertTrue(error.message.orEmpty().contains(method), error.message)
            assertTrue(error.message.orEmpty().contains("Android resource IDs"), error.message)
        }
        assertTrue(wrapper.formatDayMonth(0).isNotBlank())
        assertTrue(wrapper.formatMediumDate(0).isNotBlank())
        assertTrue(wrapper.getAbbreviatedRelativeTime(0, 0).isNotBlank())
    }

    @Test
    fun admittedRootRendersPublishedDialogDates() = runComposeUiTest {
        val fixture = IosGraphFixture()
        val graph = fixture.graph()
        val owner = GraphTestOwner()
        val generations = GraphTestGenerations(owner)
        val visible = mutableStateOf(true)
        val dialogs = listOf("2023-11-14T12:00:00Z", "2022-04-15T12:00:00Z").map { date ->
            AppDialog.RestoreSuccess(
                restoredAtEpochMs = millis(date),
                previousVersionAvailable = false,
                terminalOwner = RestoreOwnerId(Uuid.random().toString()),
            )
        }
        val bodies = dialogs.map { dialog ->
            "Your data was restored from a backup dated ${expectedCurrentMedium(dialog.restoredAtEpochMs)}."
        }
        assertTrue(bodies[0] != bodies[1])
        try {
            setContent { if (visible.value) App(generations, { graph }, {}) }
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            for ((index, dialog) in dialogs.withIndex()) {
                graph.appDialogRepository.publish(dialog)
                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithText("Restore complete", useUnmergedTree = true).fetchSemanticsNodes().size == 1
                }
                onNodeWithText(bodies[index], useUnmergedTree = true).assertIsDisplayed()
                onNodeWithText(bodies[1 - index], useUnmergedTree = true).assertDoesNotExist()
                onNodeWithText("Nov 2, 2026", substring = true, useUnmergedTree = true).assertDoesNotExist()
                graph.appDialogRepository.dismiss(dialog)
                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithText("Restore complete", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
                }
                onNodeWithText(bodies[index], useUnmergedTree = true).assertDoesNotExist()
            }
        } finally {
            dialogs.forEach { graph.appDialogRepository.dismiss(it) }
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.clear() }
            fixture.close()
        }
        assertEquals(1, generations.admitted.size)
        assertEquals(generations.admitted, generations.released)
    }
}

private fun wrapper(locale: String, zone: String) = IosResourceWrapper { IosDateSettings(locale, zone) }

private fun millis(value: String) = Instant.parse(value).toEpochMilliseconds()

private fun expectedCurrentMedium(timestamp: Long): String = NSDateFormatter().apply {
    locale = NSLocale(NSLocale.autoupdatingCurrentLocale.localeIdentifier)
    calendar = NSCalendar(NSCalendarIdentifierGregorian)
    timeZone = checkNotNull(NSTimeZone.timeZoneWithName(NSTimeZone.localTimeZone.name))
    dateStyle = NSDateFormatterMediumStyle
    timeStyle = NSDateFormatterNoStyle
}.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp.toDouble() / 1000.0))

private fun assertText(expected: String, actual: String) {
    println("Foundation expected=[$expected] actual=[$actual]")
    fun String.spaces() = replace('\u00a0', ' ').replace('\u202f', ' ')
    assertEquals(expected.spaces(), actual.spaces())
}

private fun expectedRelative(locale: String, unit: String, value: Long): String {
    val components = NSDateComponents().apply {
        when (unit) {
            "second" -> second = value
            "minute" -> minute = value
            "hour" -> hour = value
            "day" -> day = value
            else -> error("Unknown fixture unit $unit")
        }
    }
    return NSRelativeDateTimeFormatter().apply {
        this.locale = NSLocale(locale)
        unitsStyle = NSRelativeDateTimeFormatterUnitsStyleAbbreviated
        dateTimeStyle = NSRelativeDateTimeFormatterStyleNamed
    }.localizedStringFromDateComponents(components)
}

private fun expectedMedium(locale: String, zone: String, year: Long, month: Long, day: Long) =
    expectedAbsolute(locale, zone, year, month, day, null)

private fun expectedAbsolute(
    locale: String,
    zone: String,
    year: Long,
    month: Long,
    day: Long,
    template: String?,
): String {
    val calendar = NSCalendar(NSCalendarIdentifierGregorian).apply {
        timeZone = checkNotNull(NSTimeZone.timeZoneWithName(zone))
    }
    val civilDate = checkNotNull(
        calendar.dateFromComponents(
            NSDateComponents().apply {
                this.year = year
                this.month = month
                this.day = day
                hour = 12
            },
        ),
    )
    return NSDateFormatter().apply {
        this.locale = NSLocale(locale)
        this.calendar = calendar
        timeZone = calendar.timeZone
        timeStyle = NSDateFormatterNoStyle
        if (template == null) dateStyle = NSDateFormatterMediumStyle else setLocalizedDateFormatFromTemplate(template)
    }.stringFromDate(civilDate)
}
