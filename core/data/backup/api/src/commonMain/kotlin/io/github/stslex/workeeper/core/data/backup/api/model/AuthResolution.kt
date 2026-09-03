// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Opaque platform-neutral handle for an interactive sign-in resolution, data layer -> UI edge
 * (Android: the `IntentSender` to launch). Only the mvi-handler edge downcasts [platform].
 */
class AuthResolution(val platform: Any?)
