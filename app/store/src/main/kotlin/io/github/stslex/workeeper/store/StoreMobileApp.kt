package io.github.stslex.workeeper.store

import dagger.hilt.android.HiltAndroidApp
import io.github.stslex.workeeper.BaseApplication
import io.github.stslex.workeeper.BuildConfig

@HiltAndroidApp
class StoreMobileApp : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = BuildConfig.DEBUG
}
