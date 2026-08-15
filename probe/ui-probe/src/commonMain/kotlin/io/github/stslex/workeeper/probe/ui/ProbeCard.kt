// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.probe.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The probe subject: one composable in commonMain with zero repo dependencies. Deliberately
 * uses material3 + foundation so the golden exercises real CMP artifacts, not just runtime.
 */
@Composable
fun ProbeCard(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Text(
            text = "probe: $label",
            modifier = Modifier.padding(16.dp),
        )
    }
}
