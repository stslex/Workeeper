// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Opaque, platform-neutral handle for an interactive sign-in resolution, carried
 * from the data layer up to the UI edge inside [SignInResult.NeedsResolution].
 *
 * [platform] holds the platform-specific object the UI must act on to complete the
 * flow (Android: the `IntentSender` to launch; iOS never emits this branch). Neither
 * the API surface nor the domain layer ever unpacks it — only the mvi-handler edge,
 * which owns the platform types, downcasts [platform] to the concrete handle. Keeping
 * it as `Any?` is what lets `core.data.backup.api` and the feature domain stay free of
 * `android.*`.
 *
 * Symmetric with [AuthResolutionOutcome], the UI→data half of the same flow.
 *
 * KMP note: at the multiplatform split this becomes an `expect value class` whose
 * Android `actual` wraps `IntentSender` (the mvi-edge downcast migrates verbatim into
 * `androidMain`) and whose iOS `actual` wraps the presenting-context reference.
 */
class AuthResolution(val platform: Any?)
