package io.github.stslex.workeeper.feature.settings.mvi.mapper

import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain

object ThemeModeMapper {

    fun ThemeMode.toDomain(): ThemeModeDomain = when (this) {
        ThemeMode.SYSTEM -> ThemeModeDomain.SYSTEM
        ThemeMode.LIGHT -> ThemeModeDomain.LIGHT
        ThemeMode.DARK -> ThemeModeDomain.DARK
    }

    fun ThemeModeDomain.toUi(): ThemeMode = when (this) {
        ThemeModeDomain.SYSTEM -> ThemeMode.SYSTEM
        ThemeModeDomain.LIGHT -> ThemeMode.LIGHT
        ThemeModeDomain.DARK -> ThemeMode.DARK
    }
}
