// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.boot

/**
 * Marker for this module's `AppDialog` reactor: reading it on the app graph eagerly constructs
 * the observer, arming its subscriber before any dispatch. See feature-specs/app-dialogs.md.
 */
interface RecoveryBootstrap
