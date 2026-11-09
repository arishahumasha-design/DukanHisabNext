package com.example.dukanhisabnext.util

import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)

/** UTC millis -> Bengali date in Dhaka time, for example "১০/১১/২০২৬". */
fun formatDate(epochMillis: Long): String =
    toBengaliDigits(DAY_MONTH_YEAR.format(DhakaTime.localDate(epochMillis)))
