// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Minimal `@AndroidEntryPoint` host activity for feature integration tests.
 *
 * Tests pair this with `createAndroidComposeRule<TestActivity>()` and call
 * `composeRule.setContent { ... }` to mount the feature graph or composable under test.
 * Hilt scopes ViewModels to this activity, so feature-level Hilt VM factories resolve
 * the same way they do under `MainActivity` in production.
 */
@AndroidEntryPoint
class TestActivity : ComponentActivity()
