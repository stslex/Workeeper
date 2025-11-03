package io.github.stslex.workeeper.core.core.utils

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DateTimeUtil {

    private const val PERCENT_DEF = 100
    private const val DATE_SYMBOL_PADDING = 2

    @OptIn(ExperimentalTime::class)
    fun formatMillis(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val day = dateTime.date.day.toString().padStart(DATE_SYMBOL_PADDING, '0')
        val month = dateTime.date.month.number.toString().padStart(DATE_SYMBOL_PADDING, '0')
        val year = (dateTime.date.year % PERCENT_DEF).toString().padStart(DATE_SYMBOL_PADDING, '0')

        return "$day/$month/$year"
    }
}
