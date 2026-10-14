// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

/** Stable wire limits shared only by the Android phone and Wear artifacts. */
object WearProtocol {

    const val SCHEMA_VERSION: Int = 2
    const val MAX_DISPLAY_NAME_UTF8_BYTES: Int = 512
    const val MAX_ENVELOPE_BYTES: Int = 16_384
    const val MAX_WEAR_REPS: Int = 999
    const val MAX_WEAR_WEIGHT_HUNDREDTHS_KG: Int = 99_999
    const val MAX_MUTATION_WINDOW_MS: Long = 120_000L
    const val DISPLAY_CACHE_TTL_MS: Long = 86_400_000L
    const val MAX_PAYLOAD_TOO_LARGE_FALLBACK_BYTES: Int = 1_024

    /**
     * What a command response may add to the snapshot response of the same outline: its command id
     * and its outcome, far below this (wear-phase-2-exercise-list.md §5.7, P15).
     */
    const val COMMAND_HEADROOM_BYTES: Int = 512

    /**
     * An outline is sent only while its snapshot response, with the widest granted authority, fits
     * this; otherwise the phone sends `PayloadTooLarge`. Every command response for it then fits too.
     */
    const val OUTLINE_BUDGET_BYTES: Int = MAX_ENVELOPE_BYTES - COMMAND_HEADROOM_BYTES

    /**
     * The capability the phone advertises (feature/wear-bridge `wear_capabilities.xml`, pinned by a
     * phone test) and the watch discovers it by. wear-paired-transport.md §5.1.
     */
    const val PHONE_CAPABILITY: String = "workeeper_phone_active_workout_v1"

    /** The one request/response path, matched exactly on both sides. wear-paired-transport.md §5.1. */
    const val RPC_PATH: String = "/workeeper/wear/v1/rpc"

    /**
     * The capability the watch advertises (app/wear `strings.xml`, pinned by a watch test) and the
     * phone finds it by to send the change signal. wear-live-sync.md §5.1.
     */
    const val WATCH_CAPABILITY: String = "workeeper_watch_active_workout_v1"

    /**
     * The phone-to-watch change signal's path, matched exactly by the watch's listener. The signal
     * carries no workout data. wear-live-sync.md §5.1, §5.2.
     */
    const val CHANGED_PATH: String = "/workeeper/wear/v1/changed"
}
