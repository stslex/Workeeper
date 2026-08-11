// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems

/**
 * The one supported way to collect a [PagingUiState] — **and the `remember` is the whole point.**
 *
 * ## What this exists to make impossible
 *
 * [PagingUiState] is a `fun interface`, so `state.pagingUiState()` *builds a new `Flow`* on every
 * call. `collectAsLazyPagingItems()` keys its own cache on that flow — `remember(this) {
 * LazyPagingItems(this) }` — so a fresh `Flow` instance means a fresh `LazyPagingItems`, and a
 * fresh `LazyPagingItems` starts at `refresh = Loading` with `itemCount = 0`
 * (`InitialLoadStates`, paging-compose 3.5.0 `LazyPagingItems.kt:174`). Calling the fun-interface
 * inline in a composable therefore resets the list to *loading* on **every recomposition**.
 *
 * That is not a theoretical hazard. Three screens wrote
 * `remember(state.pagingUiState) { state.pagingUiState() }` and Home wrote
 * `state.pagingUiState().collectAsLazyPagingItems()`.
 *
 * **Measured on device on a `debug` build**, with a workout running — Home recomposes once a second
 * on the session timer. The build type is stated because AGENTS.md requires it of any performance
 * number, and it splits this evidence in two: the **rebuild count is structural** — a `fun
 * interface` invocation allocates a new `Flow` under R8 exactly as it does without it, and the
 * recomposition that triggers it is a Compose fact, not an optimiser one — while the **23 ms blank
 * is a debug duration and is not a shipping claim.** Release would blank for less; it would still
 * blank, once a second, and that is what the `remember` removes.
 *
 * ```
 *   flow BUILT      t=...892435
 *   verdict=LOADING n=0   t=...892436   (+1ms)
 *   verdict=CONTENT n=1   t=...892459   (+23ms)
 * ```
 *
 * 13 rebuilds in 12 seconds, each blanking the list to the paging spinner for ~23 ms on that build
 * — a visible flash once a second, for as long as a workout runs, on the app's primary screen. The
 * three screens that wrapped it composed **twice on entry and never again**.
 *
 * ## Why a helper rather than a lint rule alone
 *
 * A rule flags the mistake; a helper makes it unrepresentable. Three call sites out of four got it
 * right by copying, and copying is exactly what failed on the fourth — so the correct fix is that
 * there is nothing left to copy incorrectly. `PagingCollectionRule` bans the raw
 * `collectAsLazyPagingItems` outside this file so the unsafe path cannot come back; the two
 * together are the guard, and neither is sufficient alone.
 *
 * The `remember` key is the [PagingUiState] **instance**, not the flow it returns. That instance is
 * created once in the feature's paging handler and carried through every `State.copy()`, so it is
 * stable across the state changes that recompose the screen — which is precisely what the flow it
 * builds is not.
 */
@Composable
fun <T : Any> PagingUiState<PagingData<T>>.collectAsItems(): LazyPagingItems<T> =
    remember(this) { this() }.collectAsLazyPagingItems()
