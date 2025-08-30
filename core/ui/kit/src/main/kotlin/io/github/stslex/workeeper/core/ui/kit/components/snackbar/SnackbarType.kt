package io.github.stslex.workeeper.core.ui.kit.components.snackbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

enum class SnackbarType(
    val label: String,
    val imageVector: ImageVector,
    val contentDescription: String
) {

    ERROR(
        label = "error",
        imageVector = Icons.Default.Warning,
        contentDescription = "Error"
    ),
    SUCCESS(
        label = "success",
        imageVector = Icons.Default.Done,
        contentDescription = "Success"
    ),
    INFO(
        label = "info",
        imageVector = Icons.Default.Info,
        contentDescription = "Info"
    );

    companion object {

        fun getByAction(
            actionLabel: String?
        ): SnackbarType? = entries.firstOrNull { type ->
            type.label == actionLabel
        }
    }
}