package io.github.stslex.workeeper.feature.exercise.ui.mvi.model

import androidx.core.text.isDigitsOnly

data class Property(
    val type: PropertyType,
    val value: String,
    val valid: PropertyValid
) {

    internal fun validation(): PropertyValid = when {
        valid != PropertyValid.VALID -> valid
        else -> validate(value)
    }

    fun validate(value: String): PropertyValid = validate(value, type)

    fun update(value: String) = copy(
        value = value,
        valid = validate(value)
    )

    companion object {

        fun new(
            type: PropertyType,
            value: String = "",
            valid: PropertyValid = PropertyValid.VALID
        ): Property = Property(
            type = type,
            value = value,
            valid = valid
        )

        fun validate(
            value: String,
            type: PropertyType,
        ): PropertyValid {
            if (value.isBlank()) return PropertyValid.EMPTY

            return when (type) {
                PropertyType.NAME -> PropertyValid.VALID
                PropertyType.SETS, PropertyType.REPS -> if (value.isDigitsOnly()) {
                    PropertyValid.VALID
                } else {
                    PropertyValid.SHOULD_BE_NUMBER
                }

                PropertyType.WEIGHT -> if (value.toDoubleOrNull() != null) {
                    PropertyValid.VALID
                } else {
                    PropertyValid.SHOULD_BE_NUMBER
                }
            }
        }

    }
}