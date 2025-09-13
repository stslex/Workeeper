package io.github.stslex.workeeper.core.database.sets

import androidx.room.TypeConverter

object SetsTypeConverter {

    @TypeConverter
    fun toString(value: SetsType?): String = value?.value ?: SetsType.WORK.value

    @TypeConverter
    fun toUuid(value: String): SetsType = SetsType.fromValue(value)
}