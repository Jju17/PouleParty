package dev.rahier.pouleparty.ui.gamecreation.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rahier.pouleparty.R
import dev.rahier.pouleparty.ui.gamecreation.StepContainer
import dev.rahier.pouleparty.ui.theme.CROrange
import dev.rahier.pouleparty.ui.theme.bangerStyle
import dev.rahier.pouleparty.ui.theme.gameboyStyle
import dev.rahier.pouleparty.util.startOfToday
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartTimeStep(
    startDate: Date,
    manualStartEnabled: Boolean,
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    onTapTime: () -> Unit,
    onDismissDatePicker: () -> Unit,
    onDismissTimePicker: () -> Unit,
    onDateSelected: (year: Int, month: Int, day: Int) -> Unit,
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onManualStartToggled: (Boolean) -> Unit,
) {
    val displayFormat = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }
    StepContainer(
        title = stringResource(R.string.wizard_start_time_title),
        subtitle = stringResource(R.string.wizard_start_time_subtitle)
    ) {
        val shape = RoundedCornerShape(16.dp)
        Box(
            modifier = Modifier
                .shadow(4.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onTapTime() }
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayFormat.format(startDate),
                style = bangerStyle(28),
                color = CROrange,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = stringResource(R.string.wizard_start_time_tap_to_change),
            style = gameboyStyle(9),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wizard_manual_start_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = manualStartEnabled,
                    onCheckedChange = onManualStartToggled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = CROrange,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    )
                )
            }
            Text(
                text = stringResource(
                    if (manualStartEnabled)
                        R.string.wizard_manual_start_description_on
                    else R.string.wizard_manual_start_description_off
                ),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.time,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val todayStartMs = startOfToday().timeInMillis
                    return utcTimeMillis >= todayStartMs - 24L * 60 * 60 * 1000
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = millis }
                        onDateSelected(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = remember(startDate) { Calendar.getInstance().apply { time = startDate } }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = onDismissTimePicker,
            title = { Text(stringResource(R.string.start_at)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissTimePicker) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
