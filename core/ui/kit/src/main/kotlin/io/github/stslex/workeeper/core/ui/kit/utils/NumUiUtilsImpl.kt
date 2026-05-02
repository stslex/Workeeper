package io.github.stslex.workeeper.core.ui.kit.utils

import io.github.stslex.workeeper.core.core.utils.NumUiUtils as NumUiUtilsCore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NumUiUtilsImpl @Inject constructor() : NumUiUtils {

    override fun roundThousand(value: Double): Double = NumUiUtilsCore.roundThousand(value)
}
