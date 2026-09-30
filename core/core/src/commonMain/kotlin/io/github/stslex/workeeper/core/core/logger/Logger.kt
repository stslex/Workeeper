package io.github.stslex.workeeper.core.core.logger

interface Logger {

    fun e(
        throwable: Throwable,
        message: String? = null,
    )

    fun d(message: String)

    fun d(e: Throwable, message: String)

    fun d(e: Throwable, message: () -> String)

    fun d(message: () -> String)

    fun i(message: String)

    fun i(message: () -> String)

    fun v(message: String)

    fun v(message: () -> String)

    fun w(message: String)

    fun w(message: () -> String)

    fun w(message: String, throwable: Throwable)

    fun w(throwable: Throwable, message: () -> String)

    /**
     * [message] with a local-only [dedupeKey] ([telemetryDedupeKey]): the Crashlytics debounce
     * compares the key with the message, and the key never leaves the device.
     */
    fun d(message: String, dedupeKey: TelemetryDedupeKey) = d(message)

    /** See [d] with a dedupe key. */
    fun i(message: String, dedupeKey: TelemetryDedupeKey) = i(message)

    /** See [d] with a dedupe key. */
    fun w(message: String, dedupeKey: TelemetryDedupeKey) = w(message)
}
