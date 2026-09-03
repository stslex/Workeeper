// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.navbar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One destination in [AppNavBar] - presentation only: a resolved icon, description and test tag,
 * with no route and no `@StringRes`. The app owns destinations, the kit owns the treatment.
 */
@Immutable
data class AppNavBarItem(
    val icon: ImageVector,
    val contentDescription: String,
    val testTag: String,
)
