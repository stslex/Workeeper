package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.runtime.Stable
import com.arkivanov.decompose.ComponentContext

@Stable
interface DialogComponent : ComponentContext {

    fun dismiss()
}