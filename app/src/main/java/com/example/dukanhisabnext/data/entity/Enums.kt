package com.example.dukanhisabnext.data.entity

// Room saves enums as their name text (for example "CASH"), so the database
// stays readable. IMPORTANT: never rename an enum value once real data exists,
// or old rows will no longer match.

enum class ProductUnit { PIECE, KG, LITRE, PACKET, DOZEN }

enum class StockMovementType { OPENING, PURCHASE, SALE, ADJUSTMENT }

/** BANK and OTHER exist in the database but are not shown on screen in the MVP. */
enum class PaymentMethod { CASH, BKASH, NAGAD, BANK, OTHER }

enum class LedgerEntryType {
    // These increase what the customer owes:
    OPENING_DUE, SALE_DUE, MANUAL_DUE, CORRECTION_PLUS,

    // These decrease it:
    PAYMENT_RECEIVED, CORRECTION_MINUS
}
