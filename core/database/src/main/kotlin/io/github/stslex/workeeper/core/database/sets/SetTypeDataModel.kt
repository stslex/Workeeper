// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.sets

import io.github.stslex.workeeper.core.database.session.model.SetTypeEntity
import kotlinx.serialization.Serializable

@Serializable
enum class SetTypeDataModel {
    WARMUP,
    WORK,
    FAILURE,
    DROP,
    ;

    fun toEntity(): SetTypeEntity = when (this) {
        WARMUP -> SetTypeEntity.WARM
        WORK -> SetTypeEntity.WORK
        FAILURE -> SetTypeEntity.FAIL
        DROP -> SetTypeEntity.DROP
    }

    companion object {

        fun SetTypeEntity.toPlanModel(): SetTypeDataModel = when (this) {
            SetTypeEntity.WARM -> WARMUP
            SetTypeEntity.WORK -> WORK
            SetTypeEntity.FAIL -> FAILURE
            SetTypeEntity.DROP -> DROP
        }
    }
}
