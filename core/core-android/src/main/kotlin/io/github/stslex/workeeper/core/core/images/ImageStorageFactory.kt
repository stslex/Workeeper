// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Non-Hilt construction of the [ImageStorage] `create()` bound-instance root. App-Scope Collapse
 * Step 6 (P-IMGROOT), STAGED add-only.
 *
 * A plain top-level factory (mirrors `buildAppDatabase` / app/app's `buildAppGraph`) — NOT a Metro
 * `@Provides`/`@ContributesBinding`: `ImageStorage` already enters the app graph as a
 * `create(imageStorage = ...)` bound instance (a permanent test-override root, 5c Option A'), so a Metro
 * binding here would DUPLICATE it and fail Metro's duplicate-binding check. A plain function contributes
 * to no graph — zero dup-binding risk; authored, compiles, not yet wired.
 *
 * Both ctor deps are graph-resolvable at the cut: `context` = the `create(applicationContext)` root,
 * `ioDispatcher` = the graph's `DispatchersBindingContainer` `@IODispatcher`.
 *
 * **STAGED, not the live feed.** `ImageStorageModule` (Hilt) is still the prod binding, bridge-read into
 * `create()` by `BaseApplication`. The atomic cut swaps that feed to call this factory (and deletes the
 * Hilt `@Provides`). The androidTest `FakeImageStorage` swap stays a `create()` bound-instance override
 * (never this factory), so the fake path is untouched.
 */
fun buildImageStorage(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
): ImageStorage = ImageStorageImpl(context, ioDispatcher)
