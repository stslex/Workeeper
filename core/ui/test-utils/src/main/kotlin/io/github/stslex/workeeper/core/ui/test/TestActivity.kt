// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test

import androidx.activity.ComponentActivity

/**
 * Minimal host activity for feature integration tests.
 *
 * Tests pair this with `createAndroidComposeRule<TestActivity>()` and call
 * `composeRule.setContent { ... }` to mount the feature graph or composable under test.
 *
 * Feature Stores resolve through the Metro path (`rememberMetroStoreProcessor`), which retains the Store
 * in the current `LocalViewModelStoreOwner` — this bare `ComponentActivity`'s `ViewModelStore`.
 */
class TestActivity : ComponentActivity()
