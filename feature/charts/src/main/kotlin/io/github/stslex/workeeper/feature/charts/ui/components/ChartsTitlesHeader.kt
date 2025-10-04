package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun ChartsTitlesHeader(
    chartTitles: ImmutableList<String>,
    selectedIndex: Int,
    onSelectTitle: (Int) -> Unit,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
            )
            .padding(AppDimension.Padding.medium),
        state = state,
    ) {
        items(
            count = chartTitles.size,
            key = { index -> chartTitles[index] },
        ) { index ->
            val item = chartTitles[index]
            val containerColor by animateColorAsState(
                targetValue = if (selectedIndex == index) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                label = "container color animation",
                animationSpec = tween(AppUi.uiFeatures.defaultAnimationDuration),
            )
            val contentColor by animateColorAsState(
                targetValue = if (selectedIndex == index) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "Content color animation",
                animationSpec = tween(AppUi.uiFeatures.defaultAnimationDuration),
            )
            Card(
                modifier = Modifier
                    .padding(AppDimension.Padding.medium),
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
                onClick = {
                    onSelectTitle(index)
                },
            ) {
                Text(
                    modifier = Modifier
                        .padding(AppDimension.Padding.medium),
                    text = item,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
@Preview
private fun ChartsTitlesHeaderPreview() {
    AppTheme {
        ChartsTitlesHeader(
            chartTitles = persistentListOf("Daily", "Weekly", "Monthly", "Yearly"),
            selectedIndex = 0,
            onSelectTitle = {},
            state = rememberLazyListState(),
            modifier = Modifier
                .padding(AppDimension.Padding.large),
        )
    }
}
