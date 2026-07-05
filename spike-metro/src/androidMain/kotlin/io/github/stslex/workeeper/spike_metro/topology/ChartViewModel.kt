package io.github.stslex.workeeper.spike_metro.topology

import androidx.lifecycle.ViewModel

/**
 * Android-only retention layer: the Metro-created Store is held inside an
 * androidx.lifecycle.ViewModel so it survives configuration changes — the role
 * @HiltViewModel plays today. The assisted screen arg is threaded through the
 * Store's @AssistedFactory. iOS skips this entirely (see iosMain).
 */
class ChartViewModel(
    factory: ChartStore.Factory,
    screenId: String,
) : ViewModel() {

    val store: ChartStore = factory.create(screenId)
}
