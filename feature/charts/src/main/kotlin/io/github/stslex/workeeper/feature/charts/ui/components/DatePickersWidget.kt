package io.github.stslex.workeeper.feature.charts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.charts.R

@Composable
internal fun DatePickersWidget(
    startDate: PropertyHolder.DateProperty,
    endDate: PropertyHolder.DateProperty,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimension.Padding.medium),
    ) {
        Text(
            text = stringResource(R.string.feature_all_charts_label_date_rage),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = AppDimension.Padding.medium),
        ) {
            OutlinedCard(
                onClick = onStartDateClick,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    modifier = Modifier.padding(AppDimension.Padding.medium),
                    text = startDate.uiValue,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedCard(
                onClick = onEndDateClick,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    modifier = Modifier.padding(AppDimension.Padding.medium),
                    text = endDate.uiValue,
                )
            }
        }
    }
}

@Preview
@Composable
private fun DatePickersPreview() {
    AppTheme {
        val singleDay = 24 * 60 * 60 * 1000
        val startDate = System.currentTimeMillis() - (7L * singleDay)
        val endDate = System.currentTimeMillis() // 7 days default
        Box(
            modifier = Modifier.background(
                MaterialTheme.colorScheme.background,
            ),
        ) {
            DatePickersWidget(
                startDate = PropertyHolder.DateProperty(startDate),
                endDate = PropertyHolder.DateProperty(endDate),
                onStartDateClick = {},
                onEndDateClick = {},
                modifier = Modifier,
            )
        }
    }
}
