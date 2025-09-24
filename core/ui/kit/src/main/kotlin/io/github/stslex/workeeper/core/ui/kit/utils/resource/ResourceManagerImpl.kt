package io.github.stslex.workeeper.core.ui.kit.utils.resource

import android.content.Context
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolder
import org.koin.core.annotation.Single
import java.util.Locale

@Single
internal class ResourceManagerImpl(
    private val fallbackContext: Context,
    private val activityHolder: ActivityHolder,
) : ResourceManager {

    private val context: Context
        get() = activityHolder.activity ?: fallbackContext

    override val locale: Locale
        get() = context.resources.configuration.locales.get(0)
            ?: throw IllegalStateException("Locale not found")
}
