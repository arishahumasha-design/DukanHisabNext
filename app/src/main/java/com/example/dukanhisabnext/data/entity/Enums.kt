package com.example.dukanhisabnext.data.entity

// Room saves enums as their name text (for example "CASH"), so the database
// stays readable. IMPORTANT: never rename an enum value once real data exists,
// or old rows will no longer match.

enum class ProductUnit { PIECE, KG, LITRE, PACKET, DOZEN }

/**
 * SALE_CANCEL: stock coming back because a sale was cancelled (positive quantity, linked to the
 * sale). Room stores the name as text with no fixed list, so adding a value needs no schema change.
 */
enum class StockMovementType { OPENING, PURCHASE, SALE, ADJUSTMENT, SALE_CANCEL }

/** BANK and OTHER exist in the database but are not shown on screen in the MVP. */
enum class PaymentMethod { CASH, BKASH, NAGAD, BANK, OTHER }

/**
 * ADVANCE_RETURNED: the shop gave a customer's advance (অগ্রিম) back in cash, so the balance goes
 * back up towards 0. Stored as text with no fixed list, so adding it needed no schema change.
 * When a type is added, update BALANCE_SUM_SQL (LedgerDao.kt) and signedAmount (LedgerRules.kt).
 */
enum class LedgerEntryType {
    // These increase what the customer owes:
    OPENING_DUE, SALE_DUE, MANUAL_DUE, CORRECTION_PLUS, ADVANCE_RETURNED,

    // These decrease it:
    PAYMENT_RECEIVED, CORRECTION_MINUS
}
