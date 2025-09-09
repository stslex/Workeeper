package io.github.stslex.workeeper.core.core.logger

internal class AppLoggerCreator(
    private val tag: String
) : Logger {

    override fun d(message: String) {
        Log.d(
            tag = tag,
            message = message
        )
    }

    override fun e(
        throwable: Throwable,
        message: String?
    ) {
        Log.e(
            tag = tag,
            throwable = throwable,
            message = message ?: throwable.message.orEmpty(),
        )
    }

    override fun i(message: String) {
        Log.i(
            tag = tag,
            message = message
        )
    }

    override fun v(message: String) {
        Log.v(
            tag = tag,
            message = message
        )
    }

    override fun w(message: String) {
        Log.w(
            tag = tag,
            message = message,
            throwable = null
        )
    }

    override fun w(message: String, throwable: Throwable) {
        Log.w(
            tag = tag,
            message = message,
            throwable = throwable
        )
    }

}