package io.github.stslex.workeeper.core.database.exercise.model

import kotlinx.serialization.Serializable

@Serializable
enum class SetsEntityType(
    val value: String
) {
    WARM("warm"),
    WORK("work"),
    FAIL("fail"),
    DROP("drop");

    companion object {

        internal val defaultType = WORK


        internal fun fromValue(value: String?): SetsEntityType = entries
            .firstOrNull { it.value == value }
            ?: defaultType
    }
}