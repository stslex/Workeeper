// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Opaque platform-neutral handle for a completed sign-in resolution, UI edge -> data layer
 * (Android: the result `Intent`, `null` when the user cancelled). Only the impl unpacks it.
 */
class AuthResolutionOutcome(val platform: Any?)
