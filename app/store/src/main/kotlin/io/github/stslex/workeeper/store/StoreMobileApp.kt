package io.github.stslex.workeeper.store

import io.github.stslex.workeeper.BuildConfig
import io.github.stslex.workeeper.BaseApplication

class StoreMobileApp : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = BuildConfig.DEBUG
}