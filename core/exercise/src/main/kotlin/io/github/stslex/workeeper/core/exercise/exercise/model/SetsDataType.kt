package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType

enum class SetsDataType {
    WARM, WORK, FAIL, DROP;

    fun toEntity(): SetsEntityType = when (this) {
        WARM -> SetsEntityType.WARM
        WORK -> SetsEntityType.WORK
        FAIL -> SetsEntityType.FAIL
        DROP -> SetsEntityType.DROP
    }

    companion object {

        internal fun SetsEntityType.toData(): SetsDataType = when (this) {
            SetsEntityType.WARM -> WARM
            SetsEntityType.WORK -> WORK
            SetsEntityType.FAIL -> FAIL
            SetsEntityType.DROP -> DROP
        }
    }
}
