// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import android.content.Context
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SettingsScope::class)
interface SettingsAndroidDeps {
    val appContext: Context
}
