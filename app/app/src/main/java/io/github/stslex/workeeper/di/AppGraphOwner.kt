// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

/**
 * Minimal coupling boundary between the Android [android.app.Application] and the Metro [AppGraph],
 * so consumers depend on `AppGraph` and never on a concrete Application type.
 */
internal interface AppGraphOwner {
    val appGraph: AppGraph
}
