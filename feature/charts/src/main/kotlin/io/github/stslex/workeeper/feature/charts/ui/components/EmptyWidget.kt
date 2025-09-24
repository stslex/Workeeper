package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.R

@Composable
internal fun EmptyWidget(
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimension.Padding.big),
    ) {
        Text(
            modifier = Modifier.align(
                Alignment.Center,
            ),
            text = if (query.isEmpty()) {
                stringResource(R.string.feature_all_charts_empty_results)
            } else {
                stringResource(R.string.feature_all_charts_not_found_results)
            },
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
@Preview(showSystemUi = false, showBackground = false, device = "spec:width=411dp,height=891dp")
private fun EmptyWidgetPreview() {
    AppTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            EmptyWidget("")
        }
    }
}
