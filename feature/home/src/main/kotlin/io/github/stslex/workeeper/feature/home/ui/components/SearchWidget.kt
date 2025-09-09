package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.R

@Composable
internal fun SearchWidget(
    query: String,
    onQueryChange: (value: String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        modifier = modifier
            .fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = {
            Text(
                text = stringResource(R.string.home_all_search_label)
            )
        }
    )
}

@Composable
@Preview
private fun SearchWidgetPreview() {
    AppTheme {
        SearchWidget(
            query = "",
            onQueryChange = {}
        )
    }
}
