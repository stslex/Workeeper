package io.github.stslex.workeeper.feature.home.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
internal fun ChartsWidget(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Text("Charts")
    }
}

@Composable
@Preview
private fun ChartsWidgetPreview() {
    AppTheme {
        ChartsWidget()
    }
}