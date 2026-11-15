package com.example.dukanhisabnext

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.lifecycleScope
import com.example.dukanhisabnext.ui.components.SwitchOverlay
import com.example.dukanhisabnext.ui.components.SwitchTransition
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.example.dukanhisabnext.ui.navigation.AppNavigation
import com.example.dukanhisabnext.ui.theme.DukanHisabNextTheme
import com.example.dukanhisabnext.util.AppCurrency
import com.example.dukanhisabnext.util.AppLanguage
import com.example.dukanhisabnext.util.CurrentCurrency
import com.example.dukanhisabnext.util.CurrentLanguage
import com.example.dukanhisabnext.util.languageFromTag
import com.example.dukanhisabnext.util.withLanguage

class MainActivity : ComponentActivity() {

    // The language/currency switch transition (SwitchTransition.kt): the cover over this screen, and a guard against a second tap.
    private var covering by mutableStateOf(false)
    private var switching = false

    /** True once this screen has handed its state to the fresh one (see restartInPlace): it must not clear the ViewModels it gave away. */
    private var handedOver = false

    /** After a switch the fresh screen takes over the old screen's ViewModels (SwitchTransition.adoptViewModelStore). */
    override val viewModelStore: ViewModelStore
        get() = SwitchTransition.adoptViewModelStore(this) ?: super.viewModelStore

    /** Tells ComponentActivity not to clear the ViewModels when this screen is destroyed after the hand-over. */
    override fun isChangingConfigurations(): Boolean = handedOver || super.isChangingConfigurations()

    /**
     * Gives this screen the app's language (Bengali by default, or the one chosen in Settings), so
     * Material's own texts (the date picker, dialogs, accessibility labels) follow it even when the
     * phone is set to another language. This works on every Android version. It runs again each time
     * a screen is created, which is how a language change in Settings takes effect: the switch starts a fresh
     * screen (see [restartInPlace]); a switch in progress names its language (SwitchTransition.pendingLanguage).
     * Locale.setDefault is NOT called on purpose: number parsing and formatting in the rest of the
     * code keep the normal digits.
     */
    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as? DokanHisabApp
        val language = SwitchTransition.pendingLanguage() ?: app?.displaySettings?.resolveAtStart() ?: CurrentLanguage.value
        super.attachBaseContext(newBase.withLanguage(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // After a language/currency switch the old screen's saved state (back stack, form fields) comes along.
        super.onCreate(SwitchTransition.takeState() ?: savedInstanceState)
        // Android 13+: also tell the system the app's language, so the phone's per-app language setting shows it.
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = getSystemService(android.app.LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty) {
                manager.applicationLocales = LocaleList.forLanguageTags(CurrentLanguage.value.tag)
            }
        }
        enableEdgeToEdge()
        // A screen created by a language/currency switch starts under the cover and fades in (null: a normal start).
        val arrivalFadeInAt = SwitchTransition.takeArrival(SystemClock.uptimeMillis())
        setContent {
            DukanHisabNextTheme {
                AppNavigation()
                SwitchOverlay(covering = covering, arrivalFadeInAt = arrivalFadeInAt)
            }
        }
    }

    /**
     * The manifest says configChanges="locale|layoutDirection", so a locale change no longer recreates this screen
     * by itself. The only locale changes that arrive here are ours (already current) and, on Android 13+, one the
     * user makes in the phone's own per-app language setting: that one is taken over like a choice in Settings.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT >= 33 && !switching) {
            val locales = getSystemService(android.app.LocaleManager::class.java).applicationLocales
            val language = if (locales.isEmpty) null else languageFromTag(locales.get(0).toLanguageTag())
            if (language != null && language != CurrentLanguage.value) switchLanguage(language)
        }
    }

    /**
     * Runs [change] (which saves the new choice) behind the switch transition: the cover fades in, the change is
     * made, and a fresh screen replaces this one (restartInPlace). With "remove animations" on there is no cover and
     * no fade: the change is made at once.
     */
    private fun withSwitchTransition(change: () -> Unit) {
        if (switching) return
        switching = true
        val animate = !SwitchTransition.reducedMotion(this)
        val clickedAt = SystemClock.uptimeMillis()
        if (animate) covering = true
        lifecycleScope.launch {
            if (animate) {
                delay(SwitchTransition.FADE_OUT_MS.toLong())
                SwitchTransition.begin(clickedAt)
            }
            change()
            restartInPlace(animate)
        }
    }

    /**
     * Replaces this screen by a fresh MainActivity (so every text, digit and date is drawn again in the new
     * language or currency) WITHOUT the empty frame a recreate leaves: the new one is started first with a short
     * cross-fade and this one finishes after. This screen's saved state (the same state Android keeps for a
     * recreate: the navigation back stack, form fields, scroll positions) is handed over, so the user is on the
     * same screen. The cart is in its own file and is not affected.
     */
    private fun restartInPlace(animate: Boolean) {
        val state = Bundle()
        onSaveInstanceState(state)
        SwitchTransition.handOff(state, CurrentLanguage.value)
        SwitchTransition.handOffViewModelStore(viewModelStore)
        handedOver = true
        val intent = Intent(this, MainActivity::class.java)
        if (animate) {
            val options = ActivityOptions.makeCustomAnimation(this, R.anim.switch_fade_in, R.anim.switch_fade_out)
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
        }
        finish()
    }

    /**
     * The user chose a language in Settings: remember it, tell the system (Android 13+: the phone's per-app
     * language setting), then start the fresh screen in the new language, behind the short switch transition.
     */
    fun switchLanguage(language: AppLanguage) {
        if (language == CurrentLanguage.value) return
        withSwitchTransition {
            (applicationContext as DokanHisabApp).displaySettings.chooseLanguage(language)
            if (Build.VERSION.SDK_INT >= 33) {
                getSystemService(android.app.LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
            }
        }
    }

    /** The user chose a currency in Settings: only the symbol and the grouping change. Redraw everything. */
    fun switchCurrency(currency: AppCurrency) {
        if (currency == CurrentCurrency.value) return
        withSwitchTransition {
            (applicationContext as DokanHisabApp).displaySettings.chooseCurrency(currency)
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
