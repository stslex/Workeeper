package io.github.stslex.workeeper.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.stslex.workeeper.core.database.converters.SetsTypeConverter
import io.github.stslex.workeeper.core.database.converters.StringConverter
import io.github.stslex.workeeper.core.database.converters.UuidConverter
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType
import kotlin.uuid.Uuid

val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists uuid")
        db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists exercise_uuid")
        db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists reps")
        db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists weight")
        db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists type")


        val newItems = mutableListOf<ExerciseEntity>()

        db.query("SELECT * FROM exercises_table").use { cursor ->
            while (cursor.moveToNext()) {
                val setsIndex = cursor.getColumnIndex("sets")
                val exerciseUuidIndex = cursor.getColumnIndex("uuid")
                val exerciseRepsIndex = cursor.getColumnIndex("reps")
                val exerciseWeightIndex = cursor.getColumnIndex("weight")
                val nameIndex = cursor.getColumnIndex("name")
                val timestampIndex = cursor.getColumnIndex("timestamp")
                if (
                    setsIndex >= 0 &&
                    exerciseWeightIndex >= 0 &&
                    exerciseUuidIndex >= 0 &&
                    exerciseRepsIndex >= 0 &&
                    nameIndex >= 0 &&
                    timestampIndex >= 0
                ) {
                    val sets = cursor.getInt(setsIndex)
                    val exerciseUuid = cursor.getString(exerciseUuidIndex)
                    val exerciseReps = cursor.getInt(exerciseRepsIndex)
                    val exerciseWeight = cursor.getDouble(exerciseWeightIndex)
                    val name = cursor.getString(nameIndex)
                    val timestamp = cursor.getLong(timestampIndex)

                    val setsType = SetsEntityType.Companion.defaultType

                    val entity = ExerciseEntity(
                        uuid = Uuid.parse(exerciseUuid),
                        trainingUuid = null,
                        labels = emptyList(),
                        sets = Array(sets) {
                            SetsEntity(
                                uuid = Uuid.random(),
                                reps = exerciseReps,
                                weight = exerciseWeight,
                                type = setsType
                            )
                        }.toList(),
                        name = name,
                        timestamp = timestamp,
                    )
                    newItems.add(entity)
                }
            }
            cursor.close()
        }

        db.execSQL("ALTER TABLE exercises_table DROP COLUMN if exists sets")
        db.execSQL("ALTER TABLE exercises_table DROP COLUMN if exists reps")
        db.execSQL("ALTER TABLE exercises_table DROP COLUMN if exists weight")

        db.execSQL("ALTER TABLE exercises_table CREATE COLUMN if not exist sets")
        db.execSQL("ALTER TABLE exercises_table CREATE COLUMN if not exist training_uuid")
        db.execSQL("ALTER TABLE exercises_table CREATE COLUMN if not exist labels")

        newItems.forEach {
            db.execSQL("DELETE FROM exercises_table WHERE uuid = :${it.uuid}")
            db.execSQL(
                "INSERT INTO exercises_table (uuid, trainingUuid, labels, sets, name, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(
                    UuidConverter.toString(it.uuid),
                    UuidConverter.toString(it.trainingUuid),
                    StringConverter.listToString(it.labels),
                    SetsTypeConverter.toString(it.sets),
                    it.name,
                    it.timestamp
                )
            )
        }
        super.migrate(db)
    }

}