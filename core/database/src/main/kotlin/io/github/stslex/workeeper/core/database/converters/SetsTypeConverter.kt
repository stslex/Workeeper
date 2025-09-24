package io.github.stslex.workeeper.core.database.converters

import androidx.room.TypeConverter
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType
import kotlinx.serialization.json.Json

object SetsTypeConverter {

    @TypeConverter
    fun toString(value: SetsEntityType?): String = value?.value ?: SetsEntityType.WORK.value

    @TypeConverter
    fun toData(value: String): SetsEntityType = SetsEntityType.Companion.fromValue(value)

    @TypeConverter
    fun toString(value: List<SetsEntity>?): String = Json.encodeToString(value.orEmpty())

    @TypeConverter
    fun fromString(value: String): List<SetsEntity>? = Json.decodeFromString(value)
}
