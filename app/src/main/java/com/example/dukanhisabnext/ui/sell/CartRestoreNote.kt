package com.example.dukanhisabnext.ui.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.R

/**
 * Shown once on the sell and cart screens when the cart came back after the app was closed but
 * some of its products can no longer be sold (archived or gone): names them, with "ঠিক আছে".
 */
@Composable
fun CartRestoreNote(modifier: Modifier = Modifier) {
    val cart = (LocalContext.current.applicationContext as DokanHisabApp).cartStore
    val removed by cart.removedOnRestart.collectAsStateWithLifecycle()
    if (removed.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.cart_removed_on_restart, removed.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = cart::dismissRemovedNote) { Text(stringResource(R.string.ledger_date_ok)) }
        }
    }
}
