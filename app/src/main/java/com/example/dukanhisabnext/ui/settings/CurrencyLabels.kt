package com.example.dukanhisabnext.ui.settings

import androidx.compose.runtime.Composable
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.ui.components.stringResource
import com.example.dukanhisabnext.util.AppCurrency

/** The name of a currency (from strings.xml), for the list. */
fun AppCurrency.nameRes(): Int = when (this) {
    AppCurrency.BDT -> R.string.currency_name_bdt
    AppCurrency.INR -> R.string.currency_name_inr
    AppCurrency.PKR -> R.string.currency_name_pkr
    AppCurrency.NPR -> R.string.currency_name_npr
    AppCurrency.USD -> R.string.currency_name_usd
    AppCurrency.EUR -> R.string.currency_name_eur
    AppCurrency.GBP -> R.string.currency_name_gbp
    AppCurrency.SAR -> R.string.currency_name_sar
    AppCurrency.AED -> R.string.currency_name_aed
    AppCurrency.QAR -> R.string.currency_name_qar
    AppCurrency.KWD -> R.string.currency_name_kwd
    AppCurrency.OMR -> R.string.currency_name_omr
    AppCurrency.MYR -> R.string.currency_name_myr
    AppCurrency.SGD -> R.string.currency_name_sgd
    AppCurrency.CAD -> R.string.currency_name_cad
    AppCurrency.AUD -> R.string.currency_name_aud
}


/** "₹ · Indian rupee (INR)": how a currency is named in Settings and in the restore dialog. */
@Composable
fun currencyLabel(currency: AppCurrency): String = "${currency.symbol} · ${stringResource(currency.nameRes())} (${currency.code})"
