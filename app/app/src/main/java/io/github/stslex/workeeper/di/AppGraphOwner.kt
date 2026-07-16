// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

/**
 * Minimal coupling boundary between the Android [android.app.Application] and the Metro [AppGraph].
 * Exposes ONLY the app graph — nothing else.
 *
 * Reading the graph through THIS interface (rather than casting the Application to the concrete
 * `BaseApplication`) lets:
 *  - prod `BaseApplication` implement it (holds the real graph), and
 *  - instrumented tests provide a test-built [AppGraph],
 * with consumers depending only on `AppGraph`, never on a concrete Application type.
 */
internal interface AppGraphOwner {
    val appGraph: AppGraph
}
