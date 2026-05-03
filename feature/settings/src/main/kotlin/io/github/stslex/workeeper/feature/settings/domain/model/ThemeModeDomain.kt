package io.github.stslex.workeeper.feature.settings.domain.model

enum class ThemeModeDomain(val value: String) {
    LIGHT("LIGHT"),
    DARK("DARK"),
    SYSTEM("SYSTEM"),
    ;

    companion object {

        fun fromValue(
            value: String,
        ): ThemeModeDomain = entries
            .firstOrNull { it.value == value }
            ?: SYSTEM
    }
}
