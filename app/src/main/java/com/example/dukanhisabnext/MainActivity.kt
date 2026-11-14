package com.example.dukanhisabnext

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.dukanhisabnext.ui.navigation.AppNavigation
import com.example.dukanhisabnext.ui.theme.DukanHisabNextTheme
import com.example.dukanhisabnext.util.AppCurrency
import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentCurrency
import com.example.dukanhisabnext.util.CurrentLanguage
import com.example.dukanhisabnext.util.withLanguage

class MainActivity : ComponentActivity() {

    /**
     * Gives this screen the app's language (Bengali by default, or the one chosen in Settings), so
     * Material's own texts (the date picker, dialogs, accessibility labels) follow it even when the
     * phone is set to another language. This works on every Android version. It runs again each time
     * the screen is recreated, which is how a language change in Settings takes effect.
     * Locale.setDefault is NOT called on purpose: number parsing and formatting in the rest of the
     * code keep the normal digits.
     */
    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as? DokanHisabApp
        val language = app?.displaySettings?.resolveAtStart() ?: CurrentLanguage.value
        super.attachBaseContext(newBase.withLanguage(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+: also tell the system the app's language, so the phone's per-app language setting shows it.
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = getSystemService(android.app.LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty) {
                manager.applicationLocales = LocaleList.forLanguageTags(CurrentLanguage.value.tag)
            }
        }
        enableEdgeToEdge()
        setContent {
            DukanHisabNextTheme {
                AppNavigation()
            }
        }
    }

    /**
     * The user chose a language in Settings: remember it, then recreate the screen so every text, digit and
     * date is drawn again in the new language. On Android 13+ the system's per-app language is set too (that
     * recreates the screen by itself); on older phones this does it.
     */
    fun switchLanguage(language: AppLanguage) {
        if (language == CurrentLanguage.value) return
        (applicationContext as DokanHisabApp).displaySettings.chooseLanguage(language)
        if (Build.VERSION.SDK_INT >= 33) {
            getSystemService(android.app.LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            recreate()
        }
    }

    /** The user chose a currency in Settings: only the symbol and the grouping change. Redraw everything. */
    fun switchCurrency(currency: AppCurrency) {
        if (currency == CurrentCurrency.value) return
        (applicationContext as DokanHisabApp).displaySettings.chooseCurrency(currency)
        recreate()
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    DukanHisabNextTheme {
        AppNavigation()
    }
}
