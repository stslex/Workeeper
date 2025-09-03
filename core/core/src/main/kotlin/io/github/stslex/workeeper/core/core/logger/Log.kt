package io.github.stslex.workeeper.core.core.logger

import co.touchlab.kermit.Logger
import io.github.stslex.workeeper.core.core.BuildConfig

import io.github.stslex.workeeper.core.core.logger.Logger as AtTenLogger

object Log : AtTenLogger {

    private const val DEFAULT_TAG = "AtTen"

    fun tag(tag: String): AtTenLogger = AppLoggerCreator(tag)

    fun e(
        throwable: Throwable,
        tag: String? = null,
        message: String? = null
    ) {
        if (BuildConfig.DEBUG.not()) return
        // todo firebase crashlytics
        Logger.Companion.e(
            tag = tag ?: DEFAULT_TAG,
            throwable = throwable,
            messageString = message ?: throwable.message.orEmpty(),
        )
    }

    fun e(
        message: String,
        tag: String? = null,
    ) {
        if (BuildConfig.DEBUG.not()) return
        // todo firebase crashlytics
        Logger.Companion.e(
            tag = tag ?: DEFAULT_TAG,
            messageString = message,
        )
    }

    fun d(
        message: String,
        tag: String? = null,
    ) {
        if (BuildConfig.DEBUG.not()) return
        Logger.Companion.d(
            tag = tag ?: DEFAULT_TAG,
            messageString = message
        )
    }

    fun i(
        message: String,
        tag: String? = null,
    ) {
        if (BuildConfig.DEBUG.not()) return
        Logger.Companion.i(
            tag = tag ?: DEFAULT_TAG,
            messageString = message
        )
    }

    fun v(
        message: String,
        tag: String? = null,
    ) {
        if (BuildConfig.DEBUG.not()) return
        Logger.Companion.v(
            tag = tag ?: DEFAULT_TAG,
            messageString = message
        )
    }

    override fun e(throwable: Throwable, message: String?) {
        e(throwable = throwable, tag = DEFAULT_TAG, message = message)
    }

    override fun e(message: String) {
        e(tag = DEFAULT_TAG, message = message)
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
}