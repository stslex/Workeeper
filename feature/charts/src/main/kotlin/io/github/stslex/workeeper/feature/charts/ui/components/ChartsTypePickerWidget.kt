package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.ui.mvi.model.ChartsType

@Composable
internal fun ChartsTypePickerWidget(
    selectedType: ChartsType,
    onClick: (ChartsType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(AppDimension.Button.big),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChartsType.entries.forEach { chartsType ->
            ChartsTypeItem(
                modifier = Modifier
                    .weight(1f),
                item = chartsType,
                isSelected = chartsType == selectedType,
                onClick = { onClick(chartsType) },
            )
        }
    }
}

@Composable
internal fun ChartsTypeItem(
    item: ChartsType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(300),
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(300),
    )

    val textSize by animateFloatAsState(
        targetValue = if (isSelected) {
            24f
        } else {
            18f
        },
        animationSpec = tween(300),
    )

    ElevatedCard(
        modifier = modifier
            .padding(horizontal = AppDimension.Padding.medium)
            .height(AppDimension.Button.medium),
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = backgroundColor,
            contentColor = textColor,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AppDimension.Elevation.medium,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppDimension.Padding.medium),
        ) {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .align(Alignment.Center),
                text = stringResource(item.labelRes),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = textSize.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
@Preview(device = "spec:width=411dp,height=891dp", showSystemUi = true)
private fun ChartsTypePickerWidgetPreview() {
    AppTheme {
        var selectedType by remember { mutableStateOf(ChartsType.TRAINING) }
        ChartsTypePickerWidget(
            selectedType = selectedType,
            onClick = {
                selectedType = it
            },
        )
    }
}
