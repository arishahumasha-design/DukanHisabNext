package com.example.dukanhisabnext

import android.content.Context
import android.content.res.Configuration
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
import java.util.Locale

/** The language of the whole app: Bengali (Bangladesh), whatever the phone's own language is. */
private const val APP_LANGUAGE_TAG = "bn-BD"

class MainActivity : ComponentActivity() {

    /**
     * Gives this screen a Bengali configuration, so Material's own texts (the date picker, dialogs,
     * accessibility labels) are Bengali even when the phone is set to English. This works on every
     * Android version. Locale.setDefault is NOT called on purpose: number parsing and formatting in
     * the rest of the code keep the normal digits.
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(APP_LANGUAGE_TAG))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+: also tell the system the app's language (the per-app language setting shows Bengali).
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = getSystemService(android.app.LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty) {
                manager.applicationLocales = LocaleList.forLanguageTags(APP_LANGUAGE_TAG)
            }
        }
        enableEdgeToEdge()
        setContent {
            DukanHisabNextTheme {
                AppNavigation()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    DukanHisabNextTheme {
        AppNavigation()
    }
}
