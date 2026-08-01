// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import io.github.stslex.workeeper.core.ui.kit.components.collectAsItems
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppBlockedArchiveDialog
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.fab.AppFAB
import io.github.stslex.workeeper.core.ui.kit.components.paging.rememberDeferredSurface
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.continuityAlphaSpec
import io.github.stslex.workeeper.core.ui.kit.theme.continuityPositionalSpec
import io.github.stslex.workeeper.feature.all_exercises.R
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore.State.SelectionMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ColdOpenError
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ColdOpenLoading
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExerciseRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ExercisesEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.FilteredEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.ListSurface
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingErrorFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingLoadingFooter
import io.github.stslex.workeeper.feature.all_exercises.ui.components.PagingTailKind
import io.github.stslex.workeeper.feature.all_exercises.ui.components.SelectionEmptyState
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TagFilterRow
import io.github.stslex.workeeper.feature.all_exercises.ui.components.TopBarMode
import io.github.stslex.workeeper.feature.all_exercises.ui.components.crossfades
import io.github.stslex.workeeper.feature.all_exercises.ui.components.listSurface
import io.github.stslex.workeeper.feature.all_exercises.ui.components.pagingTailKind
import io.github.stslex.workeeper.feature.all_exercises.ui.components.topBarMode

@Composable
internal fun AllExercisesScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.pagingUiState.collectAsItems()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("AllExercisesScreen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(state = state, consume = consume)
            // The band stays visible during selection (spec C4) so the list does not vertically
            // jump on entering the mode.
            if (state.availableTags.isNotEmpty()) {
                TagFilterRow(
                    tags = state.availableTags,
                    activeTagFilter = state.activeTagFilter,
                    onToggle = { uuid -> consume(Action.Click.OnTagFilterToggle(uuid)) },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ExercisesList(state = state, items = items, consume = consume)
                EmptyRegion(state = state, items = items, consume = consume)
            }
        }
        val isSelecting = state.isSelecting
        // §26 "FAB in selection mode": the morph is SHAPE AND GLYPH ONLY. The fill stays `--max`
        // and the content `--base` throughout — the action is archive, archive is reversible, and
        // `--rust` marks destruction only (§1), so the old `status.error` fill was promising
        // irreversibility for a reversible act and `Icons.Filled.Delete` was following the fill.
        AppFAB(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppDimension.screenEdge)
                .testTag("AllExercisesFab"),
            icon = if (isSelecting) AppIcons.FabArchive else AppIcons.FabPlus,
            contentDescription = stringResource(
                if (isSelecting) R.string.feature_all_exercises_bulk_archive
                else R.string.feature_all_exercises_fab_create,
            ),
            cornerRadius = if (isSelecting) FAB_MORPHED_RADIUS else AppDimension.Radius.medium,
            onClick = {
                if (isSelecting) consume(Action.Click.OnBulkDelete)
                else consume(Action.Click.OnFabClick)
            },
        )
    }

    state.pendingPermanentDelete?.let { pending ->
        AppConfirmDialog(
            title = stringResource(
                R.string.feature_all_exercises_permanent_delete_title,
                pending.name,
            ),
            body = stringResource(R.string.feature_all_exercises_permanent_delete_body),
            impactSummary = stringResource(R.string.feature_all_exercises_permanent_delete_impact),
            confirmLabel = stringResource(R.string.feature_all_exercises_permanent_delete_confirm),
            onConfirm = { consume(Action.Click.OnConfirmPermanentDelete) },
            onDismiss = { consume(Action.Click.OnCancelPermanentDelete) },
        )
    }

    state.pendingBulkDelete?.let { pending ->
        AppConfirmDialog(
            title = stringResource(R.string.feature_all_exercises_bulk_archive_confirm_title),
            body = pluralStringResource(
                R.plurals.feature_all_exercises_bulk_archive_confirm_body,
                pending.count,
                pending.count,
            ),
            impactSummary = stringResource(R.string.feature_all_exercises_bulk_archive_impact),
            confirmLabel = stringResource(R.string.feature_all_exercises_bulk_archive),
            onConfirm = { consume(Action.Click.OnBulkDeleteConfirm) },
            onDismiss = { consume(Action.Click.OnBulkDeleteDismiss) },
        )
    }

    state.blockedArchiveDialog?.let { dialog ->
        AppBlockedArchiveDialog(
            title = stringResource(R.string.feature_all_exercises_blocked_archive_title),
            items = dialog.items,
            archivedSummary = dialog.archivedSummary,
            nextStep = stringResource(R.string.feature_all_exercises_blocked_archive_next_step),
            confirmLabel = stringResource(R.string.feature_all_exercises_blocked_archive_confirm),
            onDismiss = { consume(Action.Click.OnBlockedArchiveDismiss) },
        )
    }
}

/**
 * §26 "Selection mode": the top bar is replaced **whole** — and under §26's continuity-motion row
 * it now crossfades whole, because that is the same sentence read at 260ms instead of at one frame.
 *
 * The close glyph and the archive glyph previously **appeared from nowhere**: on a long press the
 * bar's leading and trailing slots were empty in frame N and occupied in frame N+1, with no path
 * between them, which is exactly the class's membership test. The FAB beside them already morphed.
 *
 * ## Why the whole bar, rather than the two icons
 *
 * Fading the icons in place is the smaller change and the wrong one: the leading slot takes its
 * 48dp the instant it appears, so the title jumps sideways while the glyph fades — the teleport
 * moved rather than removed. Crossfading the two bars superimposes two independent layouts that
 * both sit on `surfaceTier0` with the same height, so nothing shifts and no seam is visible; the
 * mid-frame composites to the resting container colour because **both layers are that colour**,
 * which is why this needs no `fadedOut` treatment and no colour tween at all.
 *
 * ## The count does not animate, and that is asserted
 *
 * [topBarMode] is the transition key, never the title. «Выбрано N» encodes a value, and §26's row
 * excludes values from the class outright — a number in motion is a number being read wrong at
 * every intermediate frame. Keyed on the title, the bar would crossfade on every toggle. Both
 * endpoint goldens are identical either way, so `TopBarModeTest` is the only thing that can see it.
 *
 * [lastSelectionCount] exists solely for the **exit** transition: the mode is already `Off` while
 * the outgoing bar is still fading, so the count it needs no longer exists in state. A plain array
 * rather than a `MutableState` deliberately — it is a cache read during composition, not state, and
 * making it observable would invalidate this bar on every toggle for no rendered difference.
 */
@Composable
private fun ScreenTopBar(
    state: State,
    consume: (Action) -> Unit,
) {
    val mode = state.selectionMode
    val spec = continuityAlphaSpec<Float>()
    val lastSelectionCount = remember { intArrayOf(0) }
    if (mode is SelectionMode.On) lastSelectionCount[0] = mode.selectedUuids.size

    AnimatedContent(
        targetState = topBarMode(mode),
        transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) using null },
        label = "list-top-bar",
    ) { barMode ->
        when (barMode) {
            TopBarMode.SELECTION -> SelectionTopBar(
                count = lastSelectionCount[0],
                consume = consume,
            )

            TopBarMode.RESTING -> AppTopAppBar(
                modifier = Modifier.testTag("AllExercisesTopBar"),
                title = stringResource(R.string.feature_all_exercises_title),
            )
        }
    }
}

/**
 * The replacement bar: close, count, archive. §26 "Selection mode" — count PLUS actions. The
 * archive action was drawn from the start and never built; the FAB is not the only affordance the
 * drawing gives this mode.
 */
@Composable
private fun SelectionTopBar(
    count: Int,
    consume: (Action) -> Unit,
) {
    AppTopAppBar(
        modifier = Modifier.testTag("AllExercisesSelectionTopBar"),
        title = pluralStringResource(
            R.plurals.feature_all_exercises_selected_count,
            count,
            count,
        ),
        navigationIcon = {
            IconButton(
                modifier = Modifier.testTag("AllExercisesSelectionTopBarClose"),
                onClick = { consume(Action.Click.OnSelectionExit) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.feature_all_exercises_selection_close,
                    ),
                )
            }
        },
        actions = {
            IconButton(
                modifier = Modifier.testTag("AllExercisesSelectionTopBarArchive"),
                onClick = { consume(Action.Click.OnBulkDelete) },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = AppIcons.Archive,
                    contentDescription = stringResource(
                        R.string.feature_all_exercises_bulk_archive,
                    ),
                )
            }
        },
    )
}

@Composable
private fun ExercisesList(
    state: State,
    items: LazyPagingItems<ExerciseUiModel>,
    consume: (Action) -> Unit,
) {
    val selectedSet = (state.selectionMode as? SelectionMode.On)?.selectedUuids
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("AllExercisesList"),
        // Full-bleed: the drawn row owns its own gutter and its own rule, so the list adds no
        // horizontal padding and no inter-item spacing. The bottom is FAB clearance and nothing
        // else — §26 "Add action": `16 + 56 + 16` = 88. The navigation bar's inset is the host's,
        // globally, together with the system inset the mockup cannot draw. Without the 88 the tail
        // sits under the button permanently, and on a paged list the tail is live.
        contentPadding = PaddingValues(bottom = LIST_BOTTOM_CLEARANCE),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.uuid ?: "exercise_$index" },
        ) { index ->
            items[index]?.let { item ->
                ExerciseRow(
                    // §26, continuity motion: a row added, removed or reordered used to be in one
                    // place in frame N and another in frame N+1, with no path between them.
                    // Archiving from this screen and restoring from the archive both do it, and the
                    // list is keyed by uuid, so the settle costs one modifier. One shared spec
                    // across all three channels — `spring` is illegal on the two fades, whose alpha
                    // is bounded, and the class uses one curve everywhere by definition.
                    modifier = Modifier.animateItem(
                        fadeInSpec = continuityAlphaSpec(),
                        placementSpec = continuityPositionalSpec(),
                        fadeOutSpec = continuityAlphaSpec(),
                    ),
                    item = item,
                    isSelected = selectedSet?.contains(item.uuid) == true,
                    isSelecting = state.isSelecting,
                    // The drawing removes the last row's rule (`.frame .row:last-of-type`), so the
                    // list does not end on a hairline into empty space.
                    showDivider = index < items.itemCount - 1,
                    onClick = { consume(Action.Click.OnExerciseClick(item.uuid)) },
                    onLongPress = { consume(Action.Click.OnExerciseLongPress(item.uuid)) },
                )
            }
        }
        pagingTail(items = items, onRetry = { items.retry() })
    }
}

/**
 * The empty region — one selector, five verdicts, three of them new.
 *
 * This replaces `isEmptyAndIdle() && !isSelecting`, a single branch in which four different states
 * were living. See [listSurface] for the ordering and the reasoning; the states themselves are
 * drawn in `#s-empty` and ruled by §26 "List states reached by an action".
 *
 * Every verdict renders. [ListSurface.REFRESH_ERROR] was the one that did not — a failed *first*
 * page was B22's last undrawn region, and the last remaining path to a blank frame. It is drawn
 * now, and it is the same `.perr` the append tail uses, moved: placement, not a new treatment.
 *
 * ## The block crossfades — §26, continuity motion
 *
 * Four blocks replacing each other inside a `when` is four things that appeared and disappeared
 * between two frames, which is the class's membership test with nothing left to argue. The pair that
 * moves on this screen's own gesture is `SELECTION_EMPTY` ⇄ `FILTERED_EMPTY`: a tag filter emptied
 * under an active selection, then the mode left with the filter still on. Pure transit, no
 * character, so the class's alpha spec and nothing else.
 *
 * Which verdicts take part, and why two of them do not, is [ListSurface.crossfades] — a named,
 * assertable property rather than an `if` here, because the first cut keyed the transition on all
 * five verdicts and **ten goldens went red**. That direction is the surprise: §27's standing
 * prediction is that adding motion moves zero pixels, and widening a transition key until a settled
 * golden photographs a transient is the one motion change a single frame can see. The property
 * carries the measurement; `ListSurfaceTest` gates it.
 *
 * The `Box` inside the content lambda is not decoration: `AnimatedContent` gives its content an
 * `AnimatedContentScope`, not this function's [BoxScope], so every branch's `Modifier.align` needs
 * a box of its own. `matchParentSize` keeps the region exactly the size it had — the list, not the
 * blocks, is what measures this parent — so no verdict's placement moves. And because the
 * `AnimatedContent` is not composed at all for a non-crossfading verdict, the list never carries an
 * overlay above it.
 */
@Composable
private fun BoxScope.EmptyRegion(
    state: State,
    items: LazyPagingItems<ExerciseUiModel>,
    consume: (Action) -> Unit,
) {
    val filterActive = state.activeTagFilter.isNotEmpty()
    val clearFilter = { consume(Action.Click.OnClearTagFilter) }
    val spec = continuityAlphaSpec<Float>()
    // Deferred, then held for a minimum — see `rememberDeferredSurface`. Branch on what it returns
    // and never on the raw verdict beside it: the hold's whole job is to keep LOADING on screen
    // after the data has stopped loading, and `null` is the deferral window, where nothing draws.
    val surface = rememberDeferredSurface(
        surface = listSurface(
            itemCount = items.itemCount,
            loadState = items.loadState,
            filterActive = filterActive,
            selecting = state.isSelecting,
        ),
        loadingSurface = ListSurface.LOADING,
    ) ?: return
    if (surface.crossfades.not()) {
        if (surface == ListSurface.LOADING) {
            ColdOpenLoading(modifier = Modifier.align(Alignment.TopCenter))
        }
        return
    }

    AnimatedContent(
        modifier = Modifier.matchParentSize(),
        targetState = surface,
        transitionSpec = { fadeIn(spec) togetherWith fadeOut(spec) using null },
        label = "list-empty-region",
    ) { block ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (block) {
                // Neither reaches this lambda — `crossfades` returned above for both. Named rather
                // than folded into an `else` so adding a verdict to the enum breaks here.
                ListSurface.CONTENT, ListSurface.LOADING -> Unit

                ListSurface.REFRESH_ERROR -> ColdOpenError(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onRetry = { items.retry() },
                )

                ListSurface.FIRST_RUN -> ExercisesEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onCreate = { consume(Action.Click.OnEmptyCreate) },
                )

                ListSurface.FILTERED_EMPTY -> FilteredEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onClearFilter = clearFilter,
                )

                ListSurface.SELECTION_EMPTY -> SelectionEmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onClearFilter = clearFilter.takeIf { filterActive },
                )
            }
        }
    }
}

/**
 * §26 "Paging tails": three states, two drawings.
 *
 * Loading is a footer spinner. **Exhausted is no footer at all** — "end of list" states only what is
 * already visible, so the absence is the drawing. Error is the reason plus a retry, because a
 * silently truncated list is indistinguishable from a finished one.
 *
 * None of this existed here: `loadState.append` was never read, so a failed page was a list that
 * quietly stopped.
 *
 * The **decision** lives in `pagingTailKind` rather than here, because no golden can see it: with
 * the branch inlined, deleting the error case left all 30 goldens byte-identical. This function is
 * now only the dispatch.
 */
private fun LazyListScope.pagingTail(
    items: LazyPagingItems<ExerciseUiModel>,
    onRetry: () -> Unit,
) {
    when (pagingTailKind(items.loadState.append)) {
        PagingTailKind.LOADING -> item(key = "paging_loading") { PagingLoadingFooter() }
        PagingTailKind.ERROR -> item(key = "paging_error") { PagingErrorFooter(onRetry = onRetry) }
        PagingTailKind.NONE -> Unit
    }
}

/**
 * §26 "Add action": `16 + 56 + 16` = **88**. FAB clearance and nothing else — the navigation bar's
 * inset is the host's, globally, together with the system inset the mockup cannot draw.
 *
 * Named rather than inlined because **no golden can see it.** `contentPadding.bottom` moves no pixel
 * in an unscrolled frame, and Paparazzi renders one frame of an unscrolled list, so the visual gate
 * is blind to this value however many goldens the suite grows to. `AllExercisesClearanceTest` is the
 * gate instead. §27 carries the measurement that established it.
 */
internal val LIST_BOTTOM_CLEARANCE: Dp =
    AppDimension.screenEdge + AppDimension.heightLg + AppDimension.screenEdge

/** 28dp on a 56dp button — the circle the squircle opens into. */
private val FAB_MORPHED_RADIUS = 28.dp
