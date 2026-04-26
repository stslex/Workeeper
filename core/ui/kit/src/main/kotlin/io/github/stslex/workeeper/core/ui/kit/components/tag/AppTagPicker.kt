package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppTagPicker(
    selectedTags: Set<String>,
    availableTags: Set<String>,
    onTagsChange: (Set<String>) -> Unit,
    onTagCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, availableTags) {
        if (query.isBlank()) availableTags
        else availableTags.filter { it.startsWith(query, ignoreCase = true) }.toSet()
    }
    val canCreate = remember(query, availableTags) {
        query.isNotBlank() && availableTags.none { it.equals(query, ignoreCase = true) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier1, AppUi.shapes.medium)
            .padding(AppDimension.cardPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        AppTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search tags",
            leadingIcon = Icons.Default.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            filtered.forEach { tag ->
                val selected = tag in selectedTags
                AppTagChip.Selectable(
                    label = tag,
                    selected = selected,
                    onSelectedChange = { isSelected ->
                        onTagsChange(
                            if (isSelected) selectedTags + tag else selectedTags - tag,
                        )
                    },
                )
            }
            if (canCreate) {
                AppButton.Tertiary(
                    text = "+ Create '$query'",
                    onClick = {
                        onTagCreate(query)
                        query = ""
                    },
                    size = AppButtonSize.SMALL,
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppTagPickerPreview() {
    AppTheme {
        var selected by remember { mutableStateOf(setOf("Push")) }
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            AppTagPicker(
                selectedTags = selected,
                availableTags = setOf("Push", "Pull", "Legs", "Core"),
                onTagsChange = { selected = it },
                onTagCreate = {},
            )
        }
    }
}
