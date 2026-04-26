// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerStore
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.ArchiveStore.State

internal interface ArchiveHandlerStore : HandlerStore<State, Action, Event>
