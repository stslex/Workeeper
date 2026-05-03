package io.github.stslex.workeeper.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.converters.UuidConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagDao
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagDao
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagDao
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingDao
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity

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
    version = 6,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class, PlanSetsConverter::class)
internal abstract class AppDatabase : RoomDatabase() {

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
