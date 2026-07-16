// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import android.content.Context

/** The single entry point to obtain app-dialogs/impl's app-scoped singletons from any `Context`. */
fun Context.appDialogInternals(): AppDialogInternalsHolder =
    applicationContext as AppDialogInternalsHolder
