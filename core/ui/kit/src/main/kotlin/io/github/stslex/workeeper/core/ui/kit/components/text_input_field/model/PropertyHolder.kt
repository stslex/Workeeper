package io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import io.github.stslex.workeeper.core.core.utils.DateTimeUtil

@Stable
sealed class PropertyHolder<T : Any>(
    initialValue: T?,
    defaultValue: T
) {
    private var valueState: MutableState<T> = mutableStateOf(initialValue ?: defaultValue)
    private val isValidState: MutableState<Boolean> = mutableStateOf(false)

    var value: T
        get() = valueState.value
        set(value) {
            isValidState.value = validate(value)
            valueState.value = value
        }

    val isValid: Boolean = isValidState.value

    val uiValue: String get() = toStringMap(value)

    protected abstract val extraValidate: (T) -> Boolean

    protected open val toStringMap: (T?) -> String = { it?.toString().orEmpty() }

    fun validate(property: T?): Boolean = property != null && extraValidate(property)

    @Stable
    data class StringProperty(
        val initialValue: String? = null,
        val defaultValue: String = "",
        override val extraValidate: (String) -> Boolean = { true },
    ) : PropertyHolder<String>(
        initialValue = initialValue,
        defaultValue = defaultValue
    )

    @Stable
    data class IntProperty(
        val initialValue: Int? = null,
        val defaultValue: Int = 0,
        override val extraValidate: (Int) -> Boolean = { true },
    ) : PropertyHolder<Int>(
        initialValue = initialValue,
        defaultValue = defaultValue
    )

    @Stable
    data class DoubleProperty(
        val initialValue: Double? = null,
        val defaultValue: Double = 0.0,
        override val extraValidate: (Double) -> Boolean = { true },
    ) : PropertyHolder<Double>(
        initialValue = initialValue,
        defaultValue = defaultValue
    )

    @Stable
    data class LongProperty(
        val initialValue: Long? = null,
        val defaultValue: Long = 0L,
        override val extraValidate: (Long) -> Boolean = { true },
    ) : PropertyHolder<Long>(
        initialValue = initialValue,
        defaultValue = defaultValue
    )

    @Stable
    data class DateProperty(
        val initialValue: Long? = null,
        val defaultValue: Long = System.currentTimeMillis(),
        override val extraValidate: (Long) -> Boolean = { true },
    ) : PropertyHolder<Long>(
        initialValue = initialValue,
        defaultValue = defaultValue
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