package io.github.stslex.workeeper.core.database

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.stslex.workeeper.core.database.sets.SetsType
import kotlin.uuid.Uuid

internal interface Migrations : AutoMigrationSpec {

    class OneToTwo : Migrations {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists uuid")
            db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists exercise_uuid")
            db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists reps")
            db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists weight")
            db.execSQL("ALTER TABLE sets_table ADD COLUMN if not exists type")

            db.query("SELECT * FROM exercises_table").use { cursor ->
                while (cursor.moveToNext()) {
                    val setsIndex = cursor.getColumnIndex("sets")
                    val exerciseUuidIndex = cursor.getColumnIndex("uuid")
                    val exerciseRepsIndex = cursor.getColumnIndex("reps")
                    val exerciseWeightIndex = cursor.getColumnIndex("weight")
                    if (
                        setsIndex >= 0 &&
                        exerciseWeightIndex >= 0 &&
                        exerciseUuidIndex >= 0 &&
                        exerciseRepsIndex >= 0
                    ) {
                        val sets = cursor.getInt(setsIndex)
                        val exerciseUuid = cursor.getString(exerciseUuidIndex)
                        val exerciseReps = cursor.getInt(exerciseRepsIndex)
                        val exerciseWeight = cursor.getDouble(exerciseWeightIndex)
                        val setUuid = Uuid.random().toString()
                        val setType = SetsType.defaultType
                        repeat(sets) {
                            db.execSQL(
                                "INSERT INTO sets_table (uuid, exercise_uuid, reps, weight, type) VALUES (?, ?, ?, ?, ?)",
                                arrayOf(
                                    setUuid,
                                    exerciseUuid,
                                    exerciseReps,
                                    exerciseWeight,
                                    setType.value
                                )
                            )
                        }
                    }
                }
                cursor.close()
            }

            db.execSQL("ALTER TABLE exercises_table DROP COLUMN if exists sets")
            db.execSQL("ALTER TABLE exercises_table DROP COLUMN if exists reps")
            db.execSQL("ALTER TABLE exercises_table DROP COLUMN if exists weight")
            super.onPostMigrate(db)
        }
    }
}