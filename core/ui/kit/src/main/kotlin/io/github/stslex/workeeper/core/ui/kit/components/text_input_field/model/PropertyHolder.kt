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
    private val isErrorState: MutableState<Boolean> = mutableStateOf(false)
    private var onChange: (T) -> Unit = {}

    var value: T
        get() = valueState.value
        set(value) {
            if (valueState.value != value) {
                isValidState.value = validate(value)
                valueState.value = value
                onChange(value)
            }
        }

    val isValid: Boolean
        get() {
            isErrorState.value = !isValidState.value
            return isValidState.value
        }

    val isError: Boolean get() = isErrorState.value

    val uiValue: String get() = toStringMap(value)

    protected open val toStringMap: (T?) -> String = { it?.toString().orEmpty() }

    @Stable
    class StringProperty private constructor(
        val initialValue: String? = null,
        val defaultValue: String = "",
        val validate: (String) -> Boolean = { it.isNotBlank() },
    ) : PropertyHolder<String>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StringProperty) return false
            return initialValue == other.initialValue &&
                defaultValue == other.defaultValue &&
                value == other.value
        }

        override fun hashCode(): Int {
            var result = initialValue?.hashCode() ?: 0
            result = 31 * result + defaultValue.hashCode()
            result = 31 * result + validate.hashCode()
            return result
        }

        companion object {

            fun empty() = StringProperty(
                initialValue = "",
                defaultValue = "",
                validate = { it.isNotBlank() },
            )

            fun new(
                initialValue: String? = null,
                defaultValue: String = "",
                validate: (String) -> Boolean = { it.isNotBlank() },
            ): StringProperty = StringProperty(
                initialValue = initialValue,
                defaultValue = defaultValue,
                validate = validate,
            )
        }
    }

    @Stable
    class IntProperty private constructor(
        val initialValue: Int? = null,
        val defaultValue: Int = 0,
        val validate: (Int) -> Boolean = { it >= 0 },
    ) : PropertyHolder<Int>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IntProperty) return false
            return initialValue == other.initialValue &&
                defaultValue == other.defaultValue &&
                value == other.value
        }

        override fun hashCode(): Int {
            var result = initialValue?.hashCode() ?: 0
            result = 31 * result + defaultValue.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }

        companion object {

            fun new(
                initialValue: Int? = null,
                defaultValue: Int = 0,
                validate: (Int) -> Boolean = { it >= 0 },
            ): IntProperty = IntProperty(
                initialValue = initialValue,
                defaultValue = defaultValue,
                validate = validate,
            )
        }
    }

    @Stable
    class DoubleProperty private constructor(
        val initialValue: Double? = null,
        val defaultValue: Double = 0.0,
        val validate: (Double) -> Boolean = { it >= 0.0 },
    ) : PropertyHolder<Double>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DoubleProperty) return false
            return initialValue == other.initialValue &&
                defaultValue == other.defaultValue &&
                value == other.value
        }

        override fun hashCode(): Int {
            var result = initialValue?.hashCode() ?: 0
            result = 31 * result + defaultValue.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }

        companion object {

            fun new(
                initialValue: Double? = null,
                validate: (Double) -> Boolean = { it >= 0.0 },
                defaultValue: Double = 0.0,
            ): DoubleProperty = DoubleProperty(
                initialValue = initialValue,
                defaultValue = defaultValue,
                validate = validate,
            )
        }
    }

    @Stable
    class LongProperty private constructor(
        val initialValue: Long? = null,
        val defaultValue: Long = 0L,
        val validate: (Long) -> Boolean = { it >= 0L },
    ) : PropertyHolder<Long>(
        initialValue = initialValue,
        defaultValue = defaultValue,
        validate = validate,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LongProperty) return false
            return initialValue == other.initialValue &&
                defaultValue == other.defaultValue &&
                value == other.value
        }

        override fun hashCode(): Int {
            var result = initialValue?.hashCode() ?: 0
            result = 31 * result + defaultValue.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }

        companion object {

            fun new(
                initialValue: Long? = null,
                validate: (Long) -> Boolean = { it >= 0L },
                defaultValue: Long = 0L,
            ): LongProperty = LongProperty(
                initialValue = initialValue,
                defaultValue = defaultValue,
                validate = validate,
            )
        }
    }

    @Stable
    class DateProperty private constructor(
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

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DateProperty) return false
            return initialValue == other.initialValue &&
                defaultValue == other.defaultValue &&
                value == other.value
        }

        override fun hashCode(): Int {
            var result = initialValue?.hashCode() ?: 0
            result = 31 * result + defaultValue.hashCode()
            result = 31 * result + value.hashCode()
            return result
        }

        companion object {

            fun now(): DateProperty = DateProperty(
                initialValue = System.currentTimeMillis(),
                defaultValue = System.currentTimeMillis(),
            )

            fun new(
                initialValue: Long? = null,
                validate: (Long) -> Boolean = { it >= 0L },
                defaultValue: Long = 0L,
            ): DateProperty = DateProperty(
                initialValue = initialValue,
                defaultValue = defaultValue,
                validate = validate,
            )
        }
    }

    companion object {

// todo check if this is needed -> maybe replace with classic data class ???
//        fun <T : Any, THolder : PropertyHolder<T>> THolder.update(property: T): THolder = apply {
//            value = property
//        }

        fun <T : Any, THolder : PropertyHolder<T>> THolder.update(
            property: T,
        ): THolder = reset(property)

        @Suppress("UNCHECKED_CAST")
        fun <T : Any, THolder : PropertyHolder<T>> THolder.reset(
            property: T,
        ): THolder = when (this) {
            is DateProperty -> DateProperty.new(property as Long?)
            is DoubleProperty -> DoubleProperty.new(property as Double?)
            is IntProperty -> IntProperty.new(property as Int?)
            is LongProperty -> LongProperty.new(property as Long?)
            is StringProperty -> StringProperty.new(property as String?)
        } as THolder
    }
}
