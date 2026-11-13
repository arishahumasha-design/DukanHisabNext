package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.Spacing

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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        PrimaryButton(
            text = label,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m).fillMaxWidth()
        )
    }
}

/** A white card that groups some fields of a form (16dp inside, 12dp between the fields). */
@Composable
fun FormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    com.example.dukanhisabnext.ui.theme.AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content
        )
    }
}
