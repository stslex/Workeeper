package io.github.stslex.workeeper.core.exercise.session.model

import io.github.stslex.workeeper.core.database.session.SessionStateEntity

enum class SessionStateDataModel {
    IN_PROGRESS,
    FINISHED,
    ;

    fun toEntity(): SessionStateEntity = when (this) {
        IN_PROGRESS -> SessionStateEntity.IN_PROGRESS
        FINISHED -> SessionStateEntity.FINISHED
    }

    companion object {

        internal fun SessionStateEntity.toData(): SessionStateDataModel = when (this) {
            SessionStateEntity.IN_PROGRESS -> IN_PROGRESS
            SessionStateEntity.FINISHED -> FINISHED
        }
    }
}
