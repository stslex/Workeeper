package io.github.stslex.workeeper.core.exercise.exercise.model

import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity

enum class SetsDataType {
    WARM, WORK, FAIL, DROP;

    fun toEntity(): SetTypeEntity = when (this) {
        WARM -> SetTypeEntity.WARM
        WORK -> SetTypeEntity.WORK
        FAIL -> SetTypeEntity.FAIL
        DROP -> SetTypeEntity.DROP
    }

    companion object {

        internal fun SetTypeEntity.toData(): SetsDataType = when (this) {
            SetTypeEntity.WARM -> WARM
            SetTypeEntity.WORK -> WORK
            SetTypeEntity.FAIL -> FAIL
            SetTypeEntity.DROP -> DROP
        }
    }
}
