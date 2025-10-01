package io.github.stslex.workeeper.core.ui.kit.components.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
fun SearchPagingWidget(
    query: String,
    onQueryChange: (value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = {
            Text(
                text = stringResource(R.string.core_ui_kit_field_search),
            )
        },
    )
}

@Composable
@Preview
private fun SearchWidgetPreview() {
    AppTheme {
        SearchPagingWidget(
            query = "",
            onQueryChange = {},
        )
    }
}
