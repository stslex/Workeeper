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

private const val UNNAMED_TYPE = "Unnamed"
