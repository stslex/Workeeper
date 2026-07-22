// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.di

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/past-session's domain tail for the god-object split (variant A, mechanism A). Names ONLY the
 * app-scope deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics, logger,
 * dispatchers} + `NavigatorDeps` {navigator}) — three feature repos + `resourceWrapper` + the qualified
 * dispatcher.
 *
 * DISPATCHER: PastSession reads **`@IODispatcher`** (NOT `@DefaultDispatcher` — verified from
 * `PastSessionGraph.Factory.create`). Metro matches bindings by (type + qualifier), so the qualifier is
 * copied VERBATIM: a wrong-but-present qualifier (e.g. `@DefaultDispatcher`) would STILL compile — AppGraph
 * exposes every dispatcher qualifier distinctly — but would silently hand the store the WRONG dispatcher.
 *
 * Acquired via `context.appDeps<PastSessionDeps>()` and fed into `PastSessionGraph.Factory.create(...)`.
 * Types are owned by `core:data:exercise` / `core:core` (already depended on) — no new edge, no cycle.
 */
interface PastSessionDeps {
    val sessionRepository: SessionRepository
    val setRepository: SetRepository
    val personalRecordRepository: PersonalRecordRepository
    val resourceWrapper: ResourceWrapper

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher
}
