package io.github.stslex.workeeper.core.core.logger

import co.touchlab.kermit.mutableLoggerConfigInit
import co.touchlab.kermit.platformLogWriter
import co.touchlab.kermit.Logger as KLogger

open class Log private constructor(
    private val tag: String
) : Logger {

    private val logger = object : KLogger(
        config = mutableLoggerConfigInit(listOf(platformLogWriter())),
        tag = tag
    ) {}

    override fun e(throwable: Throwable, message: String?) {
        FirebaseCrashlyticsHolder.recordException(throwable, tag)
        if (isLogging.not()) return
        logger.e(
            throwable = throwable,
            messageString = message ?: throwable.message.orEmpty(),
        )
    }

    override fun d(message: String) {
        if (isLogging.not()) return
        logger.d(message)
    }

    override fun d(e: Throwable, message: String) {
        if (isLogging.not()) return
        logger.d(message, e)
    }

    override fun d(e: Throwable, message: () -> String) {
        if (isLogging.not()) return
        logger.d(e) { message() }
    }

    override fun d(message: () -> String) {
        if (isLogging.not()) return
        logger.d { message() }
    }

    override fun i(message: String) {
        FirebaseCrashlyticsHolder.log("$tag: $message")
        if (isLogging.not()) return
        logger.i(message)
    }

    override fun i(message: () -> String) {
        FirebaseCrashlyticsHolder.log("$tag: ${message()}")
        if (isLogging.not()) return
        logger.i { message() }
    }

    override fun v(message: String) {
        if (isLogging.not()) return
        logger.v(message)
    }

    override fun v(message: () -> String) {
        if (isLogging.not()) return
        logger.v { message() }
    }

    override fun w(message: String) {
        FirebaseCrashlyticsHolder.log("$tag: $message")
        if (isLogging.not()) return
        logger.w(
            tag = tag,
            messageString = message
        )
    }

    override fun w(message: () -> String) {
        FirebaseCrashlyticsHolder.log("$tag: ${message()}")
        if (isLogging.not()) return
        logger.w { message() }
    }

    override fun w(throwable: Throwable, message: () -> String) {
        FirebaseCrashlyticsHolder.recordException(throwable, "$tag: ${message()}")
        if (isLogging.not()) return
        logger.w(throwable) { message() }
    }

    override fun w(message: String, throwable: Throwable) {
        FirebaseCrashlyticsHolder.recordException(throwable, "$tag: $message")
        if (isLogging.not()) return
        logger.w(
            tag = tag,
            messageString = message
        )
    }

    companion object : Log("AtTen") {

        var isLogging: Boolean = true

        fun tag(tag: String): Logger = Log(tag)
    }

}