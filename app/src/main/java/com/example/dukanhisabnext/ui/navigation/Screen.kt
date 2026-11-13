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
    More("more_graph", R.string.tab_more, R.drawable.ic_more)
}
