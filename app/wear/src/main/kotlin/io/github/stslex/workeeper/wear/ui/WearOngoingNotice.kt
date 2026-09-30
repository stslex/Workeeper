// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ongoing.OngoingStatus

internal enum class WearOngoingNotice { NOTIFICATIONS_DISABLED, SHORTCUT_INACTIVE }

internal fun ongoingNotice(
    model: WearSurfaceModel,
    status: OngoingStatus,
    notificationsEnabled: Boolean,
): WearOngoingNotice? = when {
    !model.controlsVisible -> null
    !notificationsEnabled -> WearOngoingNotice.NOTIFICATIONS_DISABLED
    status is OngoingStatus.Scheduled -> null
    else -> WearOngoingNotice.SHORTCUT_INACTIVE
}

@Composable
internal fun OngoingNotice(notice: WearOngoingNotice?, onEnableNotifications: () -> Unit) {
    if (notice == null) return
    Column(modifier = Modifier.fillMaxWidth().testTag("ongoing_notice")) {
        Text(
            text = stringResource(
                when (notice) {
                    WearOngoingNotice.NOTIFICATIONS_DISABLED -> R.string.ongoing_notifications_disabled
                    WearOngoingNotice.SHORTCUT_INACTIVE -> R.string.ongoing_shortcut_inactive
                },
            ),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = WearPalette.textSecondary,
            modifier = Modifier.fillMaxWidth().testTag("ongoing_notice_text"),
        )
        if (notice == WearOngoingNotice.NOTIFICATIONS_DISABLED) {
            Button(
                onClick = onEnableNotifications,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("enable_notifications"),
            ) {
                Text(
                    text = stringResource(R.string.ongoing_enable_notifications),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
