package io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model

enum class TextMode {
    DATE, TEXT, DECIMAL, DOUBLE;

    val isText: Boolean get() = this == TEXT || this == DATE
}