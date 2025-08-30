package io.github.stslex.workeeper.core.core.model

data class UnresolveError(
    override val cause: Throwable? = null,
    override val message: String? = null,
) : AppError(
    message = message,
    cause = cause
)