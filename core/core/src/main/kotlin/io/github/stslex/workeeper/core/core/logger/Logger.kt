package io.github.stslex.workeeper.core.core.logger

interface Logger {

    fun e(
        throwable: Throwable,
        message: String? = null
    )

    fun e(message: String)

    fun d(message: String)

    fun i(message: String)

    fun v(message: String)
}
