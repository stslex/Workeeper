package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import androidx.compose.runtime.Stable

@Stable
data class SetsProperty(
    val uuid: String,
    val reps: Int,
    val weight: Double,
    val type: SetType
)
