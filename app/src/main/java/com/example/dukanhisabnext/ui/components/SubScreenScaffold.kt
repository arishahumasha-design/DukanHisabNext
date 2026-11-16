package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The frame for screens opened from the More tab: a top bar with a back arrow (the shared [TopBar],
 * 22sp title), and optionally a floating button. The outer app frame already keeps clear of the
 * status bar and the bottom bar, so this frame asks for no extra system padding (WindowInsets(0)).
 */
/** Bottom padding for a list that has a floating button over it: the last row can scroll fully clear of the button. */
val FabListBottomPadding = 96.dp

@Composable
fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = { TopBar(title = title, onBack = onBack, actions = actions) },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets(0),
        content = content
    )
}
