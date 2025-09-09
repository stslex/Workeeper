package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.exercise.data.model.DateProperty
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.home.R

@Composable
internal fun DatePickersWidget(
    startDate: DateProperty,
    endDate: DateProperty,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimension.Padding.medium)
            .padding(horizontal = AppDimension.Padding.medium)
    ) {
        Card(
            onClick = onStartDateClick
        ) {

            Text(
                modifier = Modifier.padding(AppDimension.Padding.medium),
                text = stringResource(R.string.home_date_picker_start_text, startDate.converted)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Card(
            onClick = onEndDateClick
        ) {
            Text(
                modifier = Modifier.padding(AppDimension.Padding.medium),
                text = stringResource(R.string.home_date_picker_end_text, endDate.converted)
            )
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
        DatePickersWidget(
            startDate = DateProperty.new(startDate),
            endDate = DateProperty.new(endDate),
            onStartDateClick = {},
            onEndDateClick = {},
            modifier = Modifier
        )
    }
}