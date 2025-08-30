package io.github.stslex.workeeper.core.core.model

abstract class IgnoreError(
    message: String? = null,
    cause: Throwable? = null
) : AppError(
    message = message,
    cause = cause
)