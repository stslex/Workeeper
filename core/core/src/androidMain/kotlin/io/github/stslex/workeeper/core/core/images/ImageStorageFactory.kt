// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Production construction of the [ImageStorage] `create()` bound-instance root, Hilt-free (App-Scope
 * Collapse Step 6, P-IMGROOT). `BaseApplication.appGraph` calls
 * `buildImageStorage(applicationContext, Dispatchers.IO)` and threads the result into `buildAppGraph(...)`
 * as the `imageStorage` root — the only production construction of [ImageStorageImpl].
 *
 * A plain top-level factory (mirrors `buildAppDatabase` / app/app's `buildAppGraph`) — NOT a Metro
 * `@Provides`/`@ContributesBinding`: `ImageStorage` enters the app graph as a `create(imageStorage = ...)`
 * bound instance (a permanent test-override root, 5c Option A'), so a Metro binding here would DUPLICATE
 * it and fail Metro's duplicate-binding check. A plain function contributes to no graph — zero
 * dup-binding risk.
 *
 * Both ctor deps come from the caller, not from the graph: `context` is the same `applicationContext`
 * passed as the `create()` root, and `ioDispatcher` is `Dispatchers.IO` passed directly — at the call
 * site the graph is still under construction, so reading its own `DispatchersBindingContainer`
 * `@IODispatcher` accessor would cycle; that accessor returns the identical stateless process-singleton.
 *
 * The androidTest `FakeImageStorage` swap goes through the `create()` bound instance instead
 * (`MetroTestRule` passes it as the `imageStorage` root) and never calls this factory, so the fake path
 * is untouched.
 */
fun buildImageStorage(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
): ImageStorage = ImageStorageImpl(context, ioDispatcher)
