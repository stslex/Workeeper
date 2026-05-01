package io.github.stslex.workeeper.core.ui.kit.utils

import io.github.stslex.workeeper.core.core.utils.NumUiUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NumUiUtilsImpl @Inject constructor() : NumUiUtils {

    override fun roundThousand(value: Double): Double = NumUiUtils.roundThousand(value)
}
