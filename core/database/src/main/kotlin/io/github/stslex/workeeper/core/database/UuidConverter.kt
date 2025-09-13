package io.github.stslex.workeeper.core.database

import androidx.room.TypeConverter
import kotlin.uuid.Uuid

internal object UuidConverter {

    @TypeConverter
    fun toString(value: Uuid?): String = value?.toString() ?: Uuid.Companion.random().toString()

    @TypeConverter
    fun toUuid(value: String): Uuid = Uuid.Companion.parse(value)
}