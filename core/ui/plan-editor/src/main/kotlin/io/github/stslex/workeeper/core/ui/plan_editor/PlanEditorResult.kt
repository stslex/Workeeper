// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

/**
 * SavedStateHandle key used by the PlanEditor route to signal the previous backstack
 * entry that a save just landed. Callers (Live workout, Exercise detail) observe this
 * key to refresh their plan-driven state on resume. (v2.4 D1.)
 */
const val SAVED_STATE_PLAN_EDITOR_SAVED: String = "plan-editor-saved"
