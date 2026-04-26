package io.github.stslex.workeeper.core.ui.kit.components.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initialDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis,
        initialDisplayMode = DisplayMode.Picker,
    )

    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let(onDateSelected)
    }

    DatePickerDialog(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .clip(AppUi.shapes.large),
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppDatePickerDialogPreview() {
    AppTheme {
        val time by remember { mutableLongStateOf(System.currentTimeMillis()) }
        AppDatePickerDialog(
            initialDateMillis = time,
            onDateSelected = {},
            onDismiss = {},
        )
    }
}
