package com.example.dukanhisabnext.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.dukanhisabnext.R

// One entry per bottom-bar tab.
// route = the internal name of the screen (never shown to the user)
// labelRes = id of the Bengali tab name in strings.xml
// icon = our own line icon (res/drawable/ic_*.xml)
enum class Screen(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val icon: Int
) {
    Home("home", R.string.tab_home, R.drawable.ic_home),
    Sell("sell_graph", R.string.tab_sell, R.drawable.ic_sell),
    Khata("khata_graph", R.string.tab_khata, R.drawable.ic_khata),
    More("more_graph", R.string.tab_more, R.drawable.ic_more);

    /** The tab's first screen (what a re-tap of the tab goes back to). */
    val startRoute: String
        get() = when (this) {
            Home -> route
            Sell -> Routes.SELL
            Khata -> Routes.KHATA
            More -> Routes.MORE
        }
}

/** What a tap on a bottom-bar tab does. */
enum class TabAction {
    /** Open the tab on its first screen; screens it had open before are not brought back. */
    OPEN_FRESH,

    /** Open the tab and bring back the screen it had open before (the tab remembers its place). */
    OPEN_RESTORING,

    /** The tab is already open: go back to its first screen. */
    POP_TO_START
}

/**
 * The rule for a tap on [target]. [currentTab] = the tab the shown screen belongs to (null for a screen
 * that is in no tab, such as the shop list opened from Home).
 * The Sell tab always starts fresh (never an old cart, payment or receipt screen); the other tabs keep
 * their place; a tap on the open tab goes back to its first screen.
 */
fun tabAction(target: Screen, currentTab: Screen?): TabAction = when {
    target == currentTab -> TabAction.POP_TO_START
    target == Screen.Sell -> TabAction.OPEN_FRESH
    else -> TabAction.OPEN_RESTORING
}
