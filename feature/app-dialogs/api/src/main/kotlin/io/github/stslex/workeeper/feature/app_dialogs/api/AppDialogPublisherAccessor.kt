// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api

import android.content.Context
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher

/** The single entry point to obtain the app-scoped [AppDialogPublisher] from any `Context`. */
fun Context.appDialogPublisher(): AppDialogPublisher =
    (applicationContext as AppDialogPublisherHolder).appDialogPublisher
