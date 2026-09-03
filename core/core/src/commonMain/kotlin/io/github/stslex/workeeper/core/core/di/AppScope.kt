// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

/**
 * Metro app-scope marker, the analogue of Hilt's `@Singleton` tier. An inert token with no Metro or
 * Android import, so it compiles to every KMP target. See the Phase-6 data-layer spec §2.1.
 */
abstract class AppScope private constructor()
