package io.github.stslex.workeeper.core.ui.mvi.performance

/** GUARD: iOS telemetry is explicitly no-op until a real platform backend is implemented. */
internal actual val platformPerformanceBackend: PerformanceBackend = NoOpPerformanceBackend

internal object NoOpPerformanceBackend : PerformanceBackend {

    override fun process(action: RecordAction) = Unit
}
