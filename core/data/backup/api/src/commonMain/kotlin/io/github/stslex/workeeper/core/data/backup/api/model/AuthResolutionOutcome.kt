// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.api.model

/**
 * Opaque, platform-neutral handle for the result of an interactive sign-in
 * resolution, handed back from the UI edge into the data layer via
 * [io.github.stslex.workeeper.core.data.backup.api.BackupAuth.completeSignIn].
 *
 * [platform] carries the platform-specific result the impl unpacks to finish sign-in
 * (Android: the result `Intent`, or `null` when the user cancelled the launched
 * flow). It is deliberately nullable so the cancellation case survives the trip
 * without a sentinel — the Android impl treats a null (or non-`Intent`) [platform] as
 * a cancelled/failed resolution. Only the mvi-handler edge wraps it and only the impl
 * unpacks it; the API surface and domain layer pass it straight through, which keeps
 * both free of `android.*`.
 *
 * Symmetric with [AuthResolution], the data→UI half of the same flow.
 *
 * KMP note: at the multiplatform split this becomes an `expect value class` whose
 * Android `actual` wraps the result `Intent?`. iOS is single-phase (it never emits
 * [SignInResult.NeedsResolution]), so `completeSignIn` is never called there.
 */
class AuthResolutionOutcome(val platform: Any?)
