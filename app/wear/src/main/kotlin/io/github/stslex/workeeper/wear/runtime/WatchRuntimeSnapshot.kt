package io.github.stslex.workeeper.wear.runtime

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
) {
    override fun toString(): String = "WatchRuntimeSnapshot"
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
