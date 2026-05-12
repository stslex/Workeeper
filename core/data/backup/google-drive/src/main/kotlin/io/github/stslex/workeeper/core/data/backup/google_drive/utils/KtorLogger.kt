package io.github.stslex.workeeper.core.data.backup.google_drive.utils

import io.github.stslex.workeeper.core.core.logger.Log
import io.ktor.client.plugins.logging.Logger

internal object KtorLogger : Logger {

    private val logger = Log.tag(TAG)
    const val TAG = "KtorLogger"

    override fun log(message: String) {
        logger.v { message }
    }
}
