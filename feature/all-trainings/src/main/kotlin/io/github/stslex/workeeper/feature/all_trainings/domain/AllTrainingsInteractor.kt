// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.domain

import androidx.paging.PagingData
import io.github.stslex.workeeper.feature.all_trainings.domain.model.BulkArchiveResult
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TagDomain
import io.github.stslex.workeeper.feature.all_trainings.domain.model.TrainingListItemDomain
import kotlinx.coroutines.flow.Flow

interface AllTrainingsInteractor {

    fun observeTrainings(filterTagUuids: Set<String>): Flow<PagingData<TrainingListItemDomain>>

    fun observeAvailableTags(): Flow<List<TagDomain>>

    /**
     * Whether a workout is running right now — **the only thing standing between the empty state's
     * blank-start CTA and a second orphaned session.**
     *
     * `LiveWorkoutInteractor.createAdhocSession` mints a training and an `IN_PROGRESS` session
     * unconditionally: unlike its sibling `startSession`, which reuses an in-progress session for
     * the same training precisely "so re-entry from the Trainings tab does not orphan an active
     * session by spawning a parallel one", the blank branch has no such guard. Every entry point to
     * it must therefore refuse to offer the affordance while a session is running, and `HomeStore`
     * already does exactly that (`showStartCta = activeSession == null`). This screen is the second
     * entry point and needs the same rule; see B27 for the underlying hole.
     *
     * An ad-hoc training is invisible in this screen's own list (`pagedActiveWithStats` filters
     * `is_adhoc = 0`), so the running workout leaves no trace here — which is what made the empty
     * state reachable *while* one was active, and why the flag has to be observed rather than
     * inferred from the rows.
     */
    fun observeHasActiveSession(): Flow<Boolean>

    suspend fun archiveTrainings(uuids: Set<String>): BulkArchiveResult

    suspend fun deleteTrainings(uuids: Set<String>): Int

    suspend fun canPermanentlyDelete(uuids: Set<String>): Boolean
}
