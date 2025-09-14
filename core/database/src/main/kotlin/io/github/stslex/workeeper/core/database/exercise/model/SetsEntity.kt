package io.github.stslex.workeeper.core.database.exercise.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SetsEntity(
    @SerialName("uuid")
    val uuid: Uuid,
    @SerialName("reps")
    val reps: Int,
    @SerialName("weight")
    val weight: Double,
    @SerialName("type")
    val type: SetsEntityType
)