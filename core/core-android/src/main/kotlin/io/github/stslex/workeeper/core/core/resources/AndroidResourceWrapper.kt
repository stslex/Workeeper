// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.resources

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidResourceWrapper(
    private val context: Context,
) : ResourceWrapper {

    override fun getString(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun getQuantityString(@PluralsRes id: Int, quantity: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, quantity, *args)

    override fun getAbbreviatedRelativeTime(timestamp: Long, now: Long): String = DateUtils
        .getRelativeTimeSpanString(
            timestamp,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
        .toString()

    override fun formatMediumDate(timestamp: Long): String = DateFormat
        .getDateInstance(DateFormat.MEDIUM)
        .format(Date(timestamp))

    // getBestDateTimePattern orders day and month per locale (ru "d MMMM" → «22 июля»,
    // en "MMMM d" → "July 22"); a hardcoded pattern would freeze one order for all.
    override fun formatDayMonth(timestamp: Long): String = SimpleDateFormat(
        android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMMM"),
        Locale.getDefault(),
    ).format(Date(timestamp))
}
