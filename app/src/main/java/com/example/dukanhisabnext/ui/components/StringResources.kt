package com.example.dukanhisabnext.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource as composePluralStringResource
import androidx.compose.ui.res.stringResource as composeStringResource
import com.example.dukanhisabnext.util.withCurrency

// The app's own stringResource functions. Every screen imports THESE instead of androidx.compose.ui.res's,
// so a text from strings.xml always shows the currency chosen in Settings: the strings are written with
// the sign "৳" (and the token "{max}") and withCurrency() swaps them (see util/AppCurrency.kt).
// PdfExport does the same for the PDF documents.

@Composable
fun stringResource(id: Int): String = composeStringResource(id).withCurrency()

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String = composeStringResource(id, *formatArgs).withCurrency()

@Composable
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any): String =
    composePluralStringResource(id, count, *formatArgs).withCurrency()
