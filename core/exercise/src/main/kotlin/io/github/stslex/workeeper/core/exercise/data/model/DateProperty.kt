package io.github.stslex.workeeper.core.exercise.data.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.exercise.utils.DateTimeUtil

@Stable
data class DateProperty(
    val timestamp: Long,
    val converted: String
) {

    companion object {

        fun new(timestamp: Long) = DateProperty(
            timestamp = timestamp,
            converted = DateTimeUtil.formatMillis(timestamp)
        )
    }
}