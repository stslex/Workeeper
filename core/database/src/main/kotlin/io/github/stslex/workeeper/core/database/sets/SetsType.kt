package io.github.stslex.workeeper.core.database.sets

import kotlinx.serialization.Serializable

@Serializable
enum class SetsType(
    val value: String
) {
    WARM("warm"),
    WORK("work"),
    FAIL("fail"),
    DROP("drop");

    companion object {

        internal val defaultType = WORK


        internal fun fromValue(value: String?): SetsType = SetsType.entries
            .firstOrNull { it.value == value }
            ?: defaultType
    }
}