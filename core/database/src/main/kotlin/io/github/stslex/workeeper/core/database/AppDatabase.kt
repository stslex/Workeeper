package io.github.stslex.workeeper.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.stslex.workeeper.core.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.database.converters.UuidConverter
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.database.session.SessionEntity
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.database.session.model.SetEntity
import io.github.stslex.workeeper.core.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.database.tag.TagDao
import io.github.stslex.workeeper.core.database.tag.TagEntity
import io.github.stslex.workeeper.core.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.database.tag.TrainingTagEntity
import io.github.stslex.workeeper.core.database.training.TrainingDao
import io.github.stslex.workeeper.core.database.training.TrainingEntity
import io.github.stslex.workeeper.core.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.database.training.TrainingExerciseEntity

@Database(
    entities = [
        TrainingEntity::class,
        TrainingExerciseEntity::class,
        ExerciseEntity::class,
        SessionEntity::class,
        PerformedExerciseEntity::class,
        SetEntity::class,
        TagEntity::class,
        ExerciseTagEntity::class,
        TrainingTagEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, PlanSetsConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val trainingDao: TrainingDao
    abstract val trainingExerciseDao: TrainingExerciseDao
    abstract val exerciseDao: ExerciseDao
    abstract val sessionDao: SessionDao
    abstract val performedExerciseDao: PerformedExerciseDao
    abstract val setDao: SetDao
    abstract val tagDao: TagDao
    abstract val exerciseTagDao: ExerciseTagDao
    abstract val trainingTagDao: TrainingTagDao

    companion object {

        const val NAME = "app.db"
    }
}
