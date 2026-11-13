package com.example.dukanhisabnext.ui.products

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.dukanhisabnext.ui.theme.AppTheme
import androidx.compose.ui.graphics.Color
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.StockMovementType

/** The Bengali word for each unit (from strings.xml). */
fun ProductUnit.labelRes(): Int = when (this) {
    ProductUnit.PIECE -> R.string.unit_piece
    ProductUnit.KG -> R.string.unit_kg
    ProductUnit.LITRE -> R.string.unit_litre
    ProductUnit.PACKET -> R.string.unit_packet
    ProductUnit.DOZEN -> R.string.unit_dozen
}

/** The Bengali name for each kind of stock movement. */
fun StockMovementType.labelRes(): Int = when (this) {
    StockMovementType.OPENING -> R.string.movement_opening
    StockMovementType.PURCHASE -> R.string.movement_purchase
    StockMovementType.SALE -> R.string.movement_sale
    StockMovementType.ADJUSTMENT -> R.string.movement_adjustment
    StockMovementType.SALE_CANCEL -> R.string.movement_sale_cancel
}

/** Negative stock is a warning (red); stock at or under the low-stock level is a softer one (amber). */
enum class StockStatus { NORMAL, LOW, NEGATIVE }

fun stockStatus(stock: Long, lowStockLevel: Long?): StockStatus = when {
    stock < 0 -> StockStatus.NEGATIVE
    lowStockLevel != null && stock <= lowStockLevel -> StockStatus.LOW
    else -> StockStatus.NORMAL
}

@Composable
fun StockStatus.color(): Color = when (this) {
    StockStatus.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
    StockStatus.NEGATIVE -> MaterialTheme.colorScheme.error
    StockStatus.LOW -> AppTheme.colors.amberText
}
