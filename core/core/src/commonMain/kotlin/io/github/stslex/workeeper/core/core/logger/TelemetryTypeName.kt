package io.github.stslex.workeeper.core.core.logger

/**
 * The telemetry label of a value: its type name, never its content.
 *
 * GUARD: never `toString()` into Analytics or the Crashlytics log. Store actions and events carry
 * what the user entered (names, weights, reps), and a data class prints every field
 * (wear-paired-transport.md §9.2). Store action and event names survive R8 through the
 * `-keepnames` rules in proguard/firebase-crashlytics.pro.
 */
fun telemetryTypeName(value: Any): String = value::class.simpleName ?: UNNAMED_TYPE

/**
 * A local-only debounce key for a line or event whose label replaced [value]: equal exactly when
 * `value.toString()` is equal, so the Firebase holders drop the same repeats they dropped when the
 * content itself was sent (wear-paired-transport.md §9.2 item 6: no telemetry removed).
 *
 * GUARD: the key only feeds the in-memory debounce; it never reaches a sink as data. A hash of a
 * short entered value is reversible by enumeration.
 */
fun telemetryDedupeKey(value: Any?): Int = value.toString().hashCode()

private const val UNNAMED_TYPE = "Unnamed"
