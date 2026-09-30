// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.transport

/**
 * PROVISIONAL, internal testing only (wear-paired-transport.md §7.8): the local deadline per
 * request, around node lookup and `sendRequest`. Covers a cold phone start plus relay latency, and
 * two attempts stay inside the 120 s mutation window. Unmeasured.
 */
internal const val REQUEST_TIMEOUT_MS: Long = 10_000L

/**
 * PROVISIONAL, internal testing only (§7.8): a Tile render starts a refresh only if no handshake
 * completed within this age. The platform's Tile refresh throttle; it prevents a
 * render → refresh → update → render loop.
 */
internal const val TILE_REFRESH_MIN_AGE_MS: Long = 60_000L

/**
 * PROVISIONAL, internal testing only (§7.8): at most this many automatic handshakes per
 * [AUTO_REFRESH_WINDOW_MS], as a last breaker. The follow-up rule already bounds every chain; a
 * user action is neither counted nor limited.
 */
internal const val AUTO_REFRESH_BUDGET: Int = 6

/** PROVISIONAL, internal testing only (§7.8): the rolling window of [AUTO_REFRESH_BUDGET]. */
internal const val AUTO_REFRESH_WINDOW_MS: Long = 60_000L
