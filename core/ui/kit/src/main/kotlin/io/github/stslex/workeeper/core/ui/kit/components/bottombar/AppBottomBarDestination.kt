package io.github.stslex.workeeper.core.ui.kit.components.bottombar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppBottomBarDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOME(label = "Home", icon = Icons.Default.Home),
    TRAININGS(label = "Trainings", icon = Icons.AutoMirrored.Filled.List),
    EXERCISES(label = "Exercises", icon = Icons.Default.FitnessCenter),
}
