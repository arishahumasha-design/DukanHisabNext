package com.example.dukanhisabnext.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dukanhisabnext.DokanHisabApp

/**
 * Creates (or finds again, after rotation) a ViewModel for the current screen.
 * [create] receives the app (which owns the repositories) and the screen's route
 * arguments, such as the product id.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (app: DokanHisabApp, args: SavedStateHandle) -> VM
): VM {
    val app = LocalContext.current.applicationContext as DokanHisabApp
    return viewModel(initializer = { create(app, createSavedStateHandle()) })
}
