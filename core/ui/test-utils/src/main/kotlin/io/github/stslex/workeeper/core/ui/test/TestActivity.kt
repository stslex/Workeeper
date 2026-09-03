// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.test

import androidx.activity.ComponentActivity

/**
 * Minimal host activity for feature integration tests; pair with
 * `createAndroidComposeRule<TestActivity>()`. Its `ViewModelStore` retains Metro-resolved Stores.
 */
class TestActivity : ComponentActivity()
