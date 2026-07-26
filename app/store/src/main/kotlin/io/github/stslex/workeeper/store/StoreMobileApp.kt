package io.github.stslex.workeeper.store

import android.annotation.SuppressLint
import io.github.stslex.workeeper.BaseApplication
import io.github.stslex.workeeper.BuildConfig

// Registered false-positive: this concrete Application IS the merged manifest's <application android:name>
// (a variant tools:replace override of the abstract BaseApplication base); lint's Registered check does not
// resolve the override and flags it anyway. Suppressed narrowly here — do not disable Registered globally.
@SuppressLint("Registered")
class StoreMobileApp : BaseApplication() {

    override val isDebugLoggingAllow: Boolean = BuildConfig.DEBUG
}
