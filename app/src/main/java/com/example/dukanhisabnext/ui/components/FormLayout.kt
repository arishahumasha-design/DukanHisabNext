package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The layout of every form (M9): the fields scroll, and the main button sits in a bar pinned
 * at the bottom, ABOVE the keyboard. The bar is never inside the scrolling part, so the
 * keyboard can never hide it, and a long form never pushes it off the screen.
 *
 * Use it inside SubScreenScaffold { padding -> FormWithSaveBar(padding, saveBar = { SaveBar(...) }) { fields } }.
 */
@Composable
fun FormWithSaveBar(
    padding: PaddingValues,
    saveBar: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        saveBar()
    }
}

/** The pinned bar with the one big main button (disabled while saving, so a double tap saves once). */
@Composable
fun SaveBar(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
