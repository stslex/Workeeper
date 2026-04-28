// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.resources

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

interface ResourceWrapper {

    fun getString(@StringRes id: Int, vararg args: Any): String

    fun getQuantityString(@PluralsRes id: Int, quantity: Int, vararg args: Any): String

    fun getAbbreviatedRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String

    fun formatMediumDate(timestamp: Long): String
}
