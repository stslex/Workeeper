package io.github.stslex.workeeper.core.core.logger

/**
 * A local-only debounce key for a line or event whose label replaced a value: equal exactly when
 * the value's `toString()` is equal, so the Firebase holders drop only the repeats they dropped
 * when the content itself was sent (wear-paired-transport.md §9.2 item 6: no telemetry removed).
 * Equality compares the text, never a hash of it: two payloads can share a `String.hashCode()`.
 *
 * GUARD: the key only feeds the in-memory debounce, which compares it by equality; it never
 * reaches a sink as data, and [toString] prints no content.
 */
class TelemetryDedupeKey internal constructor(private val payload: String) {

    override fun equals(other: Any?): Boolean = other is TelemetryDedupeKey && other.payload == payload

    override fun hashCode(): Int = payload.hashCode()

    override fun toString(): String = "TelemetryDedupeKey"
}

/** The [TelemetryDedupeKey] of [value]. */
fun telemetryDedupeKey(value: Any?): TelemetryDedupeKey = TelemetryDedupeKey(value.toString())
