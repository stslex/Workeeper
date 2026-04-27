// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore
import kotlinx.coroutines.flow.Flow

internal interface HomeInteractor {

    fun observeActiveSession(): Flow<HomeStore.State.ActiveSessionInfo?>
}
