package io.github.stslex.workeeper.core.ui.kit.utils.resource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ResourceManagerImpl @Inject constructor(
    @ApplicationContext private val fallbackContext: Context,
    private val activityHolder: ActivityHolder,
) : ResourceManager {

    private val context: Context
        get() = activityHolder.activity ?: fallbackContext

    override val locale: Locale
        get() = context.resources.configuration.locales.get(0)
            ?: throw IllegalStateException("Locale not found")
}
