package io.github.stslex.workeeper.core.ui.kit.utils

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun OnKeyboardVisible(
    keyboardStateChange: (visible: Boolean) -> Unit,
) {
    val view = LocalView.current
    val viewTreeObserver = view.viewTreeObserver
    DisposableEffect(Unit) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val isKeyboardOpen = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime())
                ?: false
            keyboardStateChange(isKeyboardOpen)
        }
        viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            keyboardStateChange(false)
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
}
