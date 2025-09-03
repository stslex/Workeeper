package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseTimeWidget(
    time: Long,
    process: (Long) -> Unit
) {

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = time,
        initialDisplayMode = DisplayMode.Input,
    )

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { process(it) }
    }

    DatePicker(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.medium)),
        state = datePickerState,
        showModeToggle = true
    )
}

@Composable
@Preview
private fun ExerciseTimePreview() {
    AppTheme {
        val time by remember {
            mutableLongStateOf(System.currentTimeMillis())
        }
        ExerciseTimeWidget(time) { }
    }
}