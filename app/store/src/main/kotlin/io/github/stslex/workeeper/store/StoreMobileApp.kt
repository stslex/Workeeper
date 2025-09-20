package io.github.stslex.workeeper.store

import io.github.stslex.workeeper.BaseApplication
import io.github.stslex.workeeper.BuildConfig

class StoreMobileApp : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = BuildConfig.DEBUG
}