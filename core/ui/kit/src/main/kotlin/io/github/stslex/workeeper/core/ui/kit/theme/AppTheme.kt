package io.github.stslex.workeeper.core.ui.kit.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

@Composable
fun AppTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val localActivity = LocalActivity.current

    val colorScheme = remember(isDarkTheme) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (isDarkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }

            isDarkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
    }
    LaunchedEffect(isDarkTheme) {
        localActivity?.window?.let { window ->
            WindowCompat.getInsetsController(
                window,
                window.decorView,
            ).isAppearanceLightStatusBars = isDarkTheme.not()
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
