package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.feature.home.ui.model.HomeTabs
import kotlinx.coroutines.launch

@Composable
internal fun HomeToolbarWidget(
    pagerState: PagerState,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        modifier = modifier,
        selectedTabIndex = pagerState.currentPage,
        indicator = { listTabPosition ->
            listTabPosition
                .getOrNull(pagerState.currentPage)
                ?.let { position ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(
                            currentTabPosition = position,
                        )
                    )
                }
        },
        tabs = {
            HomeScreenTabContent(
                pagerState = pagerState,
                onClick = onTabClick,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenTabContent(
    pagerState: PagerState,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    HomeTabs.entries.forEachIndexed { index, tab ->
        Tab(
            modifier = modifier,
            text = {
                Text(
                    text = stringResource(id = tab.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            selected = pagerState.currentPage == index,
            onClick = remember {
                {
                    scope.launch {
                        if (pagerState.currentPage == index) {
                            onClick(index)
                        } else {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                }
            }
        )
    }
}