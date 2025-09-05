package io.github.stslex.workeeper.core.exercise.data.model

import io.github.stslex.workeeper.core.exercise.utils.DateTimeUtil

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