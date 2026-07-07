// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.resources

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.text.DateFormat
import java.util.Date

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
}
