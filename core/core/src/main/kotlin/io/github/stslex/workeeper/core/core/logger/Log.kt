package io.github.stslex.workeeper.core.core.logger

import co.touchlab.kermit.Logger
import io.github.stslex.workeeper.core.core.logger.Logger as AppLogger

object Log : AppLogger {

    private const val DEFAULT_TAG = "AtTen"

    var isLogging: Boolean = true

    fun tag(tag: String): AppLogger = AppLoggerCreator(tag)

    fun e(
        throwable: Throwable,
        tag: String? = null,
        message: String? = null
    ) {
        val tag = tag ?: DEFAULT_TAG
        FirebaseCrashlyticsHolder.recordException(throwable, tag)
        if (isLogging.not()) return
        Logger.Companion.e(
            tag = tag,
            throwable = throwable,
            messageString = message ?: throwable.message.orEmpty(),
        )
    }

    fun d(
        message: String,
        tag: String? = null,
    ) {
        if (isLogging.not()) return
        Logger.Companion.d(
            tag = tag ?: DEFAULT_TAG,
            messageString = message
        )
    }

    fun i(
        message: String,
        tag: String? = null,
    ) {
        val tag = tag ?: DEFAULT_TAG
        FirebaseCrashlyticsHolder.log("$tag: $message")
        if (isLogging.not()) return
        Logger.Companion.i(
            tag = tag,
            messageString = message
        )
    }

    fun v(
        message: String,
        tag: String? = null,
    ) {
        if (isLogging.not()) return
        Logger.Companion.v(
            tag = tag ?: DEFAULT_TAG,
            messageString = message
        )
    }

    fun w(
        message: String,
        throwable: Throwable? = null,
        tag: String? = null,
    ) {
        val tag = tag ?: DEFAULT_TAG
        if (throwable != null) {
            FirebaseCrashlyticsHolder.recordException(throwable, "$tag: $message")
        } else {
            FirebaseCrashlyticsHolder.log("$tag: $message")
        }
        if (isLogging.not()) return
        Logger.Companion.w(
            tag = tag,
            messageString = message
        )
    }

    override fun e(throwable: Throwable, message: String?) {
        e(throwable = throwable, tag = DEFAULT_TAG, message = message)
    }

    override fun d(message: String) {
        d(message = message, tag = DEFAULT_TAG)
    }

    override fun i(message: String) {
        i(message = message, tag = DEFAULT_TAG)
    }

    override fun v(message: String) {
        v(message = message, tag = DEFAULT_TAG)
    }

    override fun w(message: String, throwable: Throwable) {
        w(message = message, throwable = throwable, tag = DEFAULT_TAG)
    }

    override fun w(message: String) {
        w(message = message, throwable = null, tag = DEFAULT_TAG)
    }
}