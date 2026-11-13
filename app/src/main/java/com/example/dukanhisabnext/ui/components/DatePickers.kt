package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ledger.dayText
import com.example.dukanhisabnext.ledger.pickerMillisToDate
import java.time.LocalDate

// The date pickers shared by the Khata tab, the khata entry form and the expense screens.

/**
 * A date picker in a dialog. [selectable] decides which days can be chosen (never a future day).
 * [title] null = the picker's own title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    title: String?,
    confirmText: String,
    initial: LocalDate,
    selectable: (Long) -> Boolean,
    lastYear: Int,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = pickerStartFor(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = selectable(utcTimeMillis)
            override fun isSelectableYear(year: Int): Boolean = year <= lastYear
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(pickerMillisToDate(it)) }
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    ) {
        if (title == null) {
            DatePicker(state = state)
        } else {
            DatePicker(
                state = state,
                title = {
                    Text(
                        title,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
        }
    }
}

/**
 * The two-step "তারিখ বাছুন": first a "from" picker ("কোন তারিখ থেকে?", button "পরের ধাপ"), then a "to"
 * picker ("কোন তারিখ পর্যন্ত?", button "ঠিক আছে"). Neither offers a future day, and "to" cannot be
 * before "from". Cancelling at any step closes it ([onDismiss]); [onConfirm] gets both days.
 * The step survives rotation. Show it while the caller's "open" flag is true, and clear the flag in
 * both callbacks. [initialFrom] / [initialTo] are the days already chosen (or null).
 */
@Composable
fun DateRangePickerFlow(
    today: LocalDate,
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onConfirm: (from: LocalDate, to: LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var stage by rememberSaveable { mutableStateOf(1) }          // 1 = "from", 2 = "to"
    var pickedFrom by rememberSaveable { mutableStateOf<Long?>(null) } // the "from" day (epoch day) while the second picker is open

    val from = pickedFrom?.let { LocalDate.ofEpochDay(it) }
    if (stage == 2 && from != null) {
        DayPickerDialog(
            title = stringResource(R.string.khata_pick_to_title),
            confirmText = stringResource(R.string.ledger_date_ok),
            initial = toPickerInitial(from, initialTo),
            selectable = { isSelectableToDay(it, from, today) },
            lastYear = today.year,
            onConfirm = { to -> onConfirm(from, to) },
            onDismiss = onDismiss
        )
    } else {
        DayPickerDialog(
            title = stringResource(R.string.khata_pick_from_title),
            confirmText = stringResource(R.string.khata_pick_next),
            initial = fromPickerInitial(initialFrom, today),
            selectable = { isSelectableFromDay(it, today) },
            lastYear = today.year,
            onConfirm = {
                pickedFrom = it.toEpochDay()
                stage = 2
            },
            onDismiss = onDismiss
        )
    }
}

/** The date box of a form: shows the day (Bengali digits); tapping it opens a calendar that offers no future day. */
@Composable
fun DayField(
    date: LocalDate,
    today: LocalDate,
    enabled: Boolean,
    errorText: String?,
    onDateChange: (LocalDate) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }

    // A read-only text field (the same style as every field); a finished tap opens the calendar.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource, enabled) {
        interactionSource.interactions.collect { interaction ->
            if (enabled && interaction is PressInteraction.Release) open = true
        }
    }
    AppTextField(
        value = dayText(date),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(R.string.ledger_date_label)) },
        trailingIcon = { Icon(painterResource(R.drawable.ic_calendar), contentDescription = null) },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth()
    )

    if (open) {
        DayPickerDialog(
            title = null,
            confirmText = stringResource(R.string.ledger_date_ok),
            initial = date,
            selectable = { com.example.dukanhisabnext.ledger.isSelectableDay(it, today) },
            lastYear = today.year,
            onConfirm = {
                onDateChange(it)
                open = false
            },
            onDismiss = { open = false }
        )
    }
}
