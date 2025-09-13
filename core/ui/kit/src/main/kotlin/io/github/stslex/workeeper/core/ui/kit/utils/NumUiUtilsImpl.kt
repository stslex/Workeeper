package io.github.stslex.workeeper.core.ui.kit.utils

import io.github.stslex.workeeper.core.core.utils.NumUiUtils.roundThousand
import io.github.stslex.workeeper.core.ui.kit.utils.resource.ResourceManager
import org.koin.core.annotation.Single

@Single
class NumUiUtilsImpl(
    private val resourceManager: ResourceManager
) : NumUiUtils {

    override fun roundThousand(value: Double): Double = roundThousand(
        value = value,
        locale = resourceManager.locale
    )
}