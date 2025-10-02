package io.github.stslex.workeeper.dev

import dagger.hilt.android.HiltAndroidApp
import io.github.stslex.workeeper.BaseApplication

@HiltAndroidApp
class DevMobileApp : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = true
}
