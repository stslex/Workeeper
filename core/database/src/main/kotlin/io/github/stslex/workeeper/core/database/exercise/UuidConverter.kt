package io.github.stslex.workeeper.core.database.exercise

import androidx.room.TypeConverter
import java.util.UUID

internal object UuidConverter {

    @TypeConverter
    fun toString(value: UUID?): String = value?.toString() ?: UUID.randomUUID().toString()

    @TypeConverter
    fun toUuid(value: String): UUID = UUID.fromString(value)
}