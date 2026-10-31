// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.fixtures

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.ExercisePlanWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

internal class SettingsExerciseRepositoryFake : ExerciseRepository {
    var archivedCount: Flow<Int> = flowOf(0)
    override val exercises: Nothing get() = error("Unexpected exercises")

    override fun getUniqueExercises(query: String): Nothing = error("Unexpected ExerciseRepository.getUniqueExercises")

    override suspend fun getExercisesByUuid(uuids: List<String>): Nothing = error(
        "Unexpected ExerciseRepository.getExercisesByUuid",
    )

    override suspend fun getAdhocPlans(uuids: List<String>): Nothing = error(
        "Unexpected ExerciseRepository.getAdhocPlans",
    )

    override suspend fun getExercise(uuid: String): Nothing = error("Unexpected ExerciseRepository.getExercise")

    override suspend fun saveItem(item: ExerciseChangeDataModel): Nothing = error(
        "Unexpected ExerciseRepository.saveItem",
    )

    override suspend fun createInlineAdhocExercise(name: String): Nothing = error(
        "Unexpected ExerciseRepository.createInlineAdhocExercise",
    )

    override suspend fun getAdhocPlan(exerciseUuid: String): Nothing = error(
        "Unexpected ExerciseRepository.getAdhocPlan",
    )

    override suspend fun setAdhocPlan(exerciseUuid: String, planSets: List<PlanSetDataModel>?): Nothing = error(
        "Unexpected ExerciseRepository.setAdhocPlan",
    )

    override suspend fun setExerciseType(exerciseUuid: String, type: ExerciseTypeDataModel): Nothing = error(
        "Unexpected ExerciseRepository.setExerciseType",
    )

    override suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String): Nothing = error(
        "Unexpected ExerciseRepository.clearWeightsFromAllPlansForExercise",
    )

    override suspend fun deleteItem(uuid: String): Nothing = error("Unexpected ExerciseRepository.deleteItem")

    override suspend fun searchActiveExercises(query: String, excludeUuids: Set<String>): Nothing = error(
        "Unexpected ExerciseRepository.searchActiveExercises",
    )

    override suspend fun deleteAllItems(uuids: List<Uuid>): Nothing = error(
        "Unexpected ExerciseRepository.deleteAllItems",
    )

    override suspend fun archive(uuid: String): Nothing = error("Unexpected ExerciseRepository.archive")

    override suspend fun restore(uuid: String): Nothing = error("Unexpected ExerciseRepository.restore")

    override suspend fun permanentDelete(uuid: String): Nothing = error("Unexpected ExerciseRepository.permanentDelete")

    override suspend fun canArchive(uuid: String): Nothing = error("Unexpected ExerciseRepository.canArchive")

    override suspend fun canPermanentlyDeleteImmediately(uuid: String): Nothing = error(
        "Unexpected ExerciseRepository.canPermanentlyDeleteImmediately",
    )

    override suspend fun getActiveTrainingsUsing(exerciseUuid: String): Nothing = error(
        "Unexpected ExerciseRepository.getActiveTrainingsUsing",
    )

    override fun pagedArchived(): Nothing = error("Unexpected ExerciseRepository.pagedArchived")

    override fun observeArchivedCount(): Flow<Int> = archivedCount

    override suspend fun countSessionsUsing(exerciseUuid: String): Nothing = error(
        "Unexpected ExerciseRepository.countSessionsUsing",
    )

    override fun observeLinkedTrainingsCount(exerciseUuid: String): Nothing = error(
        "Unexpected ExerciseRepository.observeLinkedTrainingsCount",
    )

    override fun observeLastTrainedAt(exerciseUuid: String): Nothing = error(
        "Unexpected ExerciseRepository.observeLastTrainedAt",
    )

    override fun pagedActiveByTags(tagUuids: Set<String>): Nothing = error(
        "Unexpected ExerciseRepository.pagedActiveByTags",
    )

    override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing = error(
        "Unexpected ExerciseRepository.pagedActiveWithStats",
    )

    override suspend fun getRecentHistory(exerciseUuid: String, limit: Int): Nothing = error(
        "Unexpected ExerciseRepository.getRecentHistory",
    )

    override suspend fun getLastTrainedExerciseUuid(): Nothing = error(
        "Unexpected ExerciseRepository.getLastTrainedExerciseUuid",
    )

    override suspend fun getRecentlyTrainedExercises(): Nothing = error(
        "Unexpected ExerciseRepository.getRecentlyTrainedExercises",
    )

    override suspend fun getLabels(exerciseUuid: String): Nothing = error("Unexpected ExerciseRepository.getLabels")

    override suspend fun bulkArchive(uuids: Set<String>): Nothing = error("Unexpected ExerciseRepository.bulkArchive")

    override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing = error(
        "Unexpected ExerciseRepository.bulkPermanentDelete",
    )

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing = error(
        "Unexpected ExerciseRepository.canBulkPermanentDelete",
    )
}

internal class SettingsTrainingRepositoryFake : TrainingRepository {
    var archivedCount: Flow<Int> = flowOf(0)

    override fun getTrainingsUnique(query: String): Nothing = error("Unexpected TrainingRepository.getTrainingsUnique")

    override suspend fun updateTraining(training: TrainingChangeDataModel): Nothing = error(
        "Unexpected TrainingRepository.updateTraining",
    )

    override suspend fun updateTrainingWithPlans(
        training: TrainingChangeDataModel,
        plans: List<ExercisePlanWrite>,
    ): Nothing = error(
        "Unexpected TrainingRepository.updateTrainingWithPlans",
    )

    override suspend fun updateName(uuid: String, name: String): Nothing = error(
        "Unexpected TrainingRepository.updateName",
    )

    override suspend fun removeTraining(uuid: String): Nothing = error("Unexpected TrainingRepository.removeTraining")

    override suspend fun getTraining(uuid: String): Nothing = error("Unexpected TrainingRepository.getTraining")

    override fun subscribeForTraining(uuid: String): Nothing = error(
        "Unexpected TrainingRepository.subscribeForTraining",
    )

    override suspend fun removeAll(uuids: List<String>): Nothing = error("Unexpected TrainingRepository.removeAll")

    override suspend fun archive(uuid: String): Nothing = error("Unexpected TrainingRepository.archive")

    override suspend fun restore(uuid: String): Nothing = error("Unexpected TrainingRepository.restore")

    override suspend fun permanentDelete(uuid: String): Nothing = error("Unexpected TrainingRepository.permanentDelete")

    override fun pagedArchived(): Nothing = error("Unexpected TrainingRepository.pagedArchived")

    override fun observeArchivedCount(): Flow<Int> = archivedCount

    override suspend fun countSessionsUsing(trainingUuid: String): Nothing = error(
        "Unexpected TrainingRepository.countSessionsUsing",
    )

    override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing = error(
        "Unexpected TrainingRepository.pagedActiveWithStats",
    )

    override fun observeRecentTemplates(limit: Int): Nothing = error(
        "Unexpected TrainingRepository.observeRecentTemplates",
    )

    override fun observeMostForgottenTemplate(): Nothing = error(
        "Unexpected TrainingRepository.observeMostForgottenTemplate",
    )

    override suspend fun bulkArchive(uuids: Set<String>): Nothing = error("Unexpected TrainingRepository.bulkArchive")

    override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing = error(
        "Unexpected TrainingRepository.bulkPermanentDelete",
    )

    override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing = error(
        "Unexpected TrainingRepository.canBulkPermanentDelete",
    )
}
