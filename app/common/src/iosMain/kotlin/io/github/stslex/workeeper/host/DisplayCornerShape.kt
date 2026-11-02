// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension

@Composable
internal actual fun displayCornerShape(): Shape = AbsoluteRoundedCornerShape(AppDimension.Radius.big)
