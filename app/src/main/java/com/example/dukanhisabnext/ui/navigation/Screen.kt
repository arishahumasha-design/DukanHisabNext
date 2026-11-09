package com.example.dukanhisabnext.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.dukanhisabnext.R

// One entry per bottom-bar tab.
// route = the internal name of the screen (never shown to the user)
// labelRes = id of the Bengali tab name in strings.xml
// icon = built-in Material icon (core set only)
enum class Screen(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Sell("sell_graph", R.string.tab_sell, Icons.Filled.ShoppingCart),
    Khata("khata_graph", R.string.tab_khata, Icons.Filled.Person),
    More("more_graph", R.string.tab_more, Icons.Filled.Menu)
}
