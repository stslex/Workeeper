package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSelection
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus
import io.github.stslex.workeeper.wear.state.WatchReducerState
import java.util.Locale

internal data class WatchRuntimeSnapshot(
    val workout: WatchReducerState = WatchReducerState(),
    val ongoing: OngoingStatus = OngoingStatus.Inactive,
    val noSession: Boolean = false,
    val recoveryRequired: Boolean = false,
    val readOnly: Boolean = false,
    val locale: Locale = Locale.getDefault(),
    val link: LinkStatus = LinkStatus.UNKNOWN,
    /** The set the watch shows and acts on (wear-phase-2-exercise-list.md §7.2); null: none. */
    val selection: WatchSelection? = null,
) {
    override fun toString(): String = "WatchRuntimeSnapshot"
}

/** One exercise of the session and, when it has sets, one incomplete set of it (§7.2). */
internal typealias WatchSelection = OutlineSelection

/**
 * §7.2: the selection after an admitted [snapshot]. An `Active` outline selects rule R's set,
 * `PayloadTooLarge` keeps the selection until an outline arrives, and `NoSession` clears it.
 */
internal fun WatchSelection?.reconciledWith(snapshot: SnapshotData): WatchSelection? =
    when (val payload = snapshot.payload) {
        is SnapshotPayload.Active -> OutlineRules.defaultSelection(payload)
        is SnapshotPayload.PayloadTooLarge -> this
        SnapshotPayload.NoSession -> null
    }

/**
 * What the last request learned about the phone (wear-paired-transport.md §7.5). A status only:
 * it never creates or retires authority.
 */
internal enum class LinkStatus {
    UNKNOWN,
    REACHABLE,

    /** No phone advertising the capability was reachable. */
    UNREACHABLE,

    /** A phone was reachable, but the handshake got no semantic response. */
    UNANSWERED,
    ;

    val failed: Boolean get() = this == UNREACHABLE || this == UNANSWERED
}
