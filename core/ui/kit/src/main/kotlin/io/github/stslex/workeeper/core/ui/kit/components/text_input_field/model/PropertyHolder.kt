package io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil

@Stable
sealed class PropertyHolder<T : Any>(
    initialValue: T?,
    defaultValue: T,
    private val validate: (T) -> Boolean,
) {
    private var valueState: MutableState<T> = mutableStateOf(initialValue ?: defaultValue)
    private val isValidState: MutableState<Boolean> = mutableStateOf(validate(valueState.value))

    var value: T
        get() = valueState.value
        set(value) {
            if (valueState.value != value) {
                isValidState.value = validate(value)
                valueState.value = value
            }
        }

    val isValid: Boolean get() = isValidState.value

    val uiValue: String get() = toStringMap(value)

    protected open val toStringMap: (T?) -> String = { it?.toString().orEmpty() }

    @Stable
    data class StringProperty(
        val initialValue: String? = null,
        val defaultValue: String = "",
        val validate: (String) -> Boolean = { it.isNotBlank() },
    ) : PropertyHolder<String>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    )

    @Stable
    data class IntProperty(
        val initialValue: Int? = null,
        val defaultValue: Int = 0,
        val validate: (Int) -> Boolean = { it >= 0 },
    ) : PropertyHolder<Int>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    )

    @Stable
    data class DoubleProperty(
        val initialValue: Double? = null,
        val defaultValue: Double = 0.0,
        val validate: (Double) -> Boolean = { it >= 0.0 },
    ) : PropertyHolder<Double>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    )

    @Stable
    data class LongProperty(
        val initialValue: Long? = null,
        val defaultValue: Long = 0L,
        val validate: (Long) -> Boolean = { it >= 0L },
    ) : PropertyHolder<Long>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    )

    @Stable
    data class DateProperty(
        val initialValue: Long? = null,
        val defaultValue: Long = 0L,
        val validate: (Long) -> Boolean = { it >= 0L },
    ) : PropertyHolder<Long>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    ) {

        override val toStringMap: (Long?) -> String = { timestamp ->
            timestamp?.let { DateTimeUtil.formatMillis(it) }.orEmpty()
        }
    }

    companion object {

        fun <T : Any, THolder : PropertyHolder<T>> THolder.update(property: T): THolder = apply {
            value = property
        }
    }
}
