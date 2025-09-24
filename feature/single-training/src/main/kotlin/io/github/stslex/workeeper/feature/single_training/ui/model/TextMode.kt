package io.github.stslex.workeeper.feature.single_training.ui.model

enum class TextMode(
    val isText: Boolean = true,
    val isMenuEnable: Boolean = false,
    val isMenuOpen: Boolean = false,
) {

    TITLE(
        isMenuEnable = true,
    ),
    NUMBER(
        isText = false,
    ),
    DATE,
    PICK_RESULT(
        isMenuEnable = true,
        isMenuOpen = true,
    ),
}
