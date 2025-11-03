package io.github.stslex.workeeper.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.stslex.workeeper.core.database.converters.SetsTypeConverter
import io.github.stslex.workeeper.core.database.converters.StringConverter
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntity
import io.github.stslex.workeeper.core.database.exercise.model.SetsEntityType
import kotlin.uuid.Uuid

val MIGRATION_1_2 = object : Migration(1, 2) {

    @Suppress("TYPE_INTERSECTION_AS_REIFIED_WARNING")
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE exercises_table_new (
                uuid TEXT PRIMARY KEY NOT NULL,
                training_uuid TEXT,
                labels TEXT NOT NULL,
                sets TEXT NOT NULL,
                name TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """,
        )

        db.execSQL(
            """
            CREATE TABLE training_table (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                exercises TEXT NOT NULL,
                labels TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """,
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS training_labels_table (
                label TEXT PRIMARY KEY NOT NULL
            )
        """,
        )

        db.query("SELECT * FROM exercises_table").use { cursor ->
            while (cursor.moveToNext()) {
                val setsIndex = cursor.getColumnIndex("sets")
                val exerciseUuidIndex = cursor.getColumnIndex("uuid")
                val exerciseRepsIndex = cursor.getColumnIndex("reps")
                val exerciseWeightIndex = cursor.getColumnIndex("weight")
                val nameIndex = cursor.getColumnIndex("name")
                val timestampIndex = cursor.getColumnIndex("timestamp")

                if (setsIndex >= 0 && exerciseWeightIndex >= 0 &&
                    exerciseUuidIndex >= 0 && exerciseRepsIndex >= 0 &&
                    nameIndex >= 0 && timestampIndex >= 0
                ) {

                    val sets = cursor.getInt(setsIndex)
                    val exerciseUuid = cursor.getString(exerciseUuidIndex)
                    val exerciseReps = cursor.getInt(exerciseRepsIndex)
                    val exerciseWeight = cursor.getDouble(exerciseWeightIndex)
                    val name = cursor.getString(nameIndex)
                    val timestamp = cursor.getLong(timestampIndex)

                    val setsType = SetsEntityType.Companion.defaultType
                    val setsList = Array(sets) {
                        SetsEntity(
                            uuid = Uuid.random(),
                            reps = exerciseReps,
                            weight = exerciseWeight,
                            type = setsType,
                        )
                    }.toList()

                    db.execSQL(
                        "INSERT INTO exercises_table_new (uuid, training_uuid, labels, sets, name, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(
                            exerciseUuid,
                            null,
                            StringConverter.listToString(emptyList()),
                            SetsTypeConverter.toString(setsList),
                            name,
                            timestamp,
                        ),
                    )
                }
            }
        }

        db.execSQL("DROP TABLE exercises_table")
        db.execSQL("ALTER TABLE exercises_table_new RENAME TO exercises_table")
    }
}
