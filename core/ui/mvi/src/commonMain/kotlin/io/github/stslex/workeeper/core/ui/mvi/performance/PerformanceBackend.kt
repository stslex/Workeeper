package io.github.stslex.workeeper.core.ui.mvi.performance

internal interface PerformanceBackend {

    fun process(action: RecordAction)
}

internal expect val platformPerformanceBackend: PerformanceBackend
