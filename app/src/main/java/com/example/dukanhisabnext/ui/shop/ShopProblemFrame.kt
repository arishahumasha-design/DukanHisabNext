package com.example.dukanhisabnext.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.dukanhisabnext.DokanHisabApp
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.shop.ShopHealth
import com.example.dukanhisabnext.ui.backup.BackupScreen
import com.example.dukanhisabnext.ui.components.InfoCard
import com.example.dukanhisabnext.ui.components.PillVariant
import com.example.dukanhisabnext.ui.components.PrimaryButton
import com.example.dukanhisabnext.ui.components.SecondaryButton
import com.example.dukanhisabnext.ui.components.stringResource
import com.example.dukanhisabnext.ui.theme.Spacing

private enum class ProblemPage { PROBLEM, RESTORE, SHOPS, NEW_SHOP }

/**
 * Shown INSTEAD of the app when the open shop's database file is gone or cannot be read. Nothing here opens
 * that database (it would be made new and empty) and nothing deletes it: the owner restores a backup into this
 * shop, or opens (or makes) another shop; once another shop is open, this one can be deleted from the list.
 */
@Composable
fun ShopProblemFrame() {
    val app = LocalContext.current.applicationContext as DokanHisabApp
    var page by rememberSaveable { mutableStateOf(ProblemPage.PROBLEM) }
    var createdId by rememberSaveable { mutableStateOf(0) }
    when (page) {
        ProblemPage.RESTORE -> BackupScreen(onBack = { page = ProblemPage.PROBLEM }, restoreOnly = true)
        ProblemPage.SHOPS -> ShopListScreen(
            onBack = { page = ProblemPage.PROBLEM },
            onNewShop = { page = ProblemPage.NEW_SHOP },
            onEditShop = {},
            createdId = createdId.takeIf { it > 0 },
            onCreatedHandled = { createdId = 0 }
        )
        ProblemPage.NEW_SHOP -> ShopScreen(
            onBack = { page = ProblemPage.SHOPS },
            target = ShopFormTarget.New,
            onCreated = { id ->
                createdId = id
                page = ProblemPage.SHOPS
            },
            // The open shop cannot be read: a new shop starts with empty owner details.
            viewModel = com.example.dukanhisabnext.ui.appViewModel { a, _ ->
                ShopViewModel(a.shopManager, ShopFormTarget.New, openShopId = a.currentShop.id, readOpenProfile = { null })
            }
        )
        ProblemPage.PROBLEM -> Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                val name = shopDisplayName(app.currentShop)
                Text(
                    stringResource(R.string.shop_problem_title, name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                InfoCard(
                    text = stringResource(
                        if (app.shopHealth == ShopHealth.MISSING) R.string.shop_problem_missing else R.string.shop_problem_damaged
                    ),
                    variant = PillVariant.Due
                )
                Text(stringResource(R.string.shop_problem_hint), style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(
                    text = stringResource(R.string.btn_restore_into_shop),
                    onClick = { page = ProblemPage.RESTORE },
                    modifier = Modifier.fillMaxWidth()
                )
                SecondaryButton(
                    text = stringResource(R.string.btn_other_shops),
                    onClick = { page = ProblemPage.SHOPS },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
