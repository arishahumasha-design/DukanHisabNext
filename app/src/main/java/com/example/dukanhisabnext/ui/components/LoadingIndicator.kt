package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.R
import kotlinx.coroutines.delay

/**
 * A small, quiet progress ring shown where a screen has nothing to show YET (its data is loading).
 * It appears only after a quarter of a second, so a fast load (the usual case) shows nothing at
 * all and never flickers. The caller gives the size (for example fillMaxSize()).
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_DELAY_MS)
        visible = true
    }
    val description = stringResource(R.string.loading)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (visible) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp).semantics { contentDescription = description },
                strokeWidth = 3.dp
            )
        }
    }
}

/** How long a screen may stay empty before the ring appears. */
const val LOADING_DELAY_MS: Long = 250L
