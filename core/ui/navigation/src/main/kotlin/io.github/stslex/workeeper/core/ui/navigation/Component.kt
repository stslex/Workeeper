package io.github.stslex.workeeper.core.ui.navigation

interface Component<ScreenType : Screen> {

    val data: ScreenType
}
