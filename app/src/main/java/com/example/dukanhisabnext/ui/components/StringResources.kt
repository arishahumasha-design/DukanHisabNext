package com.example.dukanhisabnext.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as composeStringResource
import com.example.dukanhisabnext.util.quantityTextWithCurrency
import com.example.dukanhisabnext.util.textWithCurrency
import com.example.dukanhisabnext.util.withCurrency

// The app's own stringResource functions. Every screen imports THESE instead of androidx.compose.ui.res's,
// so a text from strings.xml always shows the currency chosen in Settings: the strings are written with
// the sign "৳" (and the token "{max}") and withCurrency() swaps them (see util/AppCurrency.kt).
// The swap is done on the TEMPLATE, before the arguments are put in, so an argument that names another
// currency on purpose (the restore and currency dialogs) is never changed. PdfExport does the same.

@Composable
fun stringResource(id: Int): String = composeStringResource(id).withCurrency()

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String {
    LocalConfiguration.current // read again when the configuration changes, like androidx's stringResource
    return LocalContext.current.resources.textWithCurrency(id, *formatArgs)
}

@Composable
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String {
    LocalConfiguration.current
    return LocalContext.current.resources.quantityTextWithCurrency(id, count, *formatArgs)
}
