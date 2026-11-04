// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.stslex.workeeper.app.ios

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSRelativeDateTimeFormatter
import platform.Foundation.NSRelativeDateTimeFormatterStyleNamed
import platform.Foundation.NSRelativeDateTimeFormatterUnitsStyleAbbreviated
import platform.Foundation.NSTimeZone
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.localeIdentifier
import platform.Foundation.timeZoneWithName

fun buildIosResourceWrapper(): ResourceWrapper = IosResourceWrapper {
    IosDateSettings(NSLocale.autoupdatingCurrentLocale.localeIdentifier, NSTimeZone.localTimeZone.name)
}

internal data class IosDateSettings(val localeIdentifier: String, val timeZoneName: String) {
    val locale: NSLocale get() = NSLocale(localeIdentifier)
    val timeZone: NSTimeZone get() = checkNotNull(NSTimeZone.timeZoneWithName(timeZoneName))

    fun calendar() = NSCalendar(NSCalendarIdentifierGregorian).also {
        it.locale = locale
        it.timeZone = timeZone
    }

    fun dateFormatter() = NSDateFormatter().also {
        it.locale = locale
        it.calendar = calendar()
        it.timeZone = timeZone
        it.timeStyle = NSDateFormatterNoStyle
    }
}

internal class IosResourceWrapper(private val settings: () -> IosDateSettings) : ResourceWrapper {
    override fun getString(id: Int, vararg args: Any): Nothing =
        throw UnsupportedOperationException("getString: Android resource IDs are unsupported on iOS (id=$id)")

    override fun getQuantityString(id: Int, quantity: Int, vararg args: Any): Nothing =
        throw UnsupportedOperationException("getQuantityString: Android resource IDs are unsupported on iOS (id=$id)")

    override fun formatMediumDate(timestamp: Long): String = settings().dateFormatter().run {
        dateStyle = NSDateFormatterMediumStyle
        stringFromDate(timestamp.asDate())
    }

    override fun formatDayMonth(timestamp: Long): String = settings().dateFormatter().run {
        setLocalizedDateFormatFromTemplate("MMMMd")
        stringFromDate(timestamp.asDate())
    }

    override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String {
        val snapshot = settings()
        val calendar = snapshot.calendar()
        val date = timestamp.asDate()
        val reference = now.asDate()
        val future = timestamp > now
        // Unsigned subtraction preserves the full distance between signed epoch endpoints.
        val elapsed = if (future) timestamp.toULong() - now.toULong() else now.toULong() - timestamp.toULong()
        if (elapsed >= WEEK_MILLIS) {
            val year = calendar.component(NSCalendarUnitYear, date)
            val sameYear = year == calendar.component(NSCalendarUnitYear, reference)
            return snapshot.dateFormatter().run {
                setLocalizedDateFormatFromTemplate(if (sameYear) "MMMd" else "yMMMd")
                stringFromDate(date)
            }
        }
        val components = NSDateComponents()
        val direction = if (future) 1L else -1L
        when {
            elapsed < MINUTE_MILLIS -> components.second = 0
            elapsed < HOUR_MILLIS -> components.minute = direction * (elapsed / MINUTE_MILLIS).toLong()
            elapsed < DAY_MILLIS -> components.hour = direction * (elapsed / HOUR_MILLIS).toLong()
            else -> components.day = calendar.components(
                NSCalendarUnitDay,
                calendar.startOfDayForDate(reference),
                calendar.startOfDayForDate(date),
                0u,
            ).day
        }
        return NSRelativeDateTimeFormatter().run {
            locale = snapshot.locale
            this.calendar = calendar
            unitsStyle = NSRelativeDateTimeFormatterUnitsStyleAbbreviated
            dateTimeStyle = NSRelativeDateTimeFormatterStyleNamed
            localizedStringFromDateComponents(components)
        }
    }
}

// Divide as floating point: integer division would turn -1 ms into the start of 1970.
private fun Long.asDate() = NSDate.dateWithTimeIntervalSince1970(toDouble() / MILLIS_PER_SECOND)

private const val MILLIS_PER_SECOND = 1_000.0
private const val MINUTE_MILLIS = 60_000uL
private const val HOUR_MILLIS = 3_600_000uL
private const val DAY_MILLIS = 86_400_000uL
private const val WEEK_MILLIS = 604_800_000uL
