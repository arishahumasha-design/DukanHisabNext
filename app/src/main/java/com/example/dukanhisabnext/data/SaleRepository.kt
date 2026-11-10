package com.example.dukanhisabnext.data

import androidx.room.withTransaction
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.util.CartLine
import com.example.dukanhisabnext.util.ReceiptNumber
import com.example.dukanhisabnext.util.SaleAmounts
import com.example.dukanhisabnext.util.SaleCalculator
import com.example.dukanhisabnext.util.SaleCheck
import com.example.dukanhisabnext.util.SaleError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One cart line as the cart holds it. [unitPrice] may differ from the product's price (edited). */
data class SaleDraftLine(
    val productId: Long,
    val quantity: Long,  // thousandths
    val unitPrice: Long  // paisa per 1 unit
)

/**
 * Everything needed to save a sale.
 * [draftId] is made once per cart. If the same cart is saved twice (a double tap),
 * the second save returns the first result instead of saving again.
 */
data class SaleDraft(
    val draftId: String,
    val lines: List<SaleDraftLine>,
    val discount: Long,        // paisa
    val deliveryCharge: Long,  // paisa
    val received: Long,        // paisa, what the customer handed over
    val paymentMethod: PaymentMethod,
    val customerId: Long?,
    val note: String? = null
)

/** What happened when we tried to save. */
sealed interface SaleResult {
    data class Saved(val saleId: Long, val receiptNo: String, val amounts: SaleAmounts) : SaleResult

    /** A money/cart rule was broken (empty cart, discount too large, due without customer...). */
    data class Invalid(val errors: Set<SaleError>) : SaleResult

    /** These products are archived or no longer exist. Nothing was saved. */
    data class ProductsUnavailable(val productIds: List<Long>) : SaleResult

    /** The chosen customer does not exist. Nothing was saved. */
    data object CustomerNotFound : SaleResult
}

/** The steps of a save, in order. Tests use them to simulate a crash between steps. */
enum class SaveStep { SALE_SAVED, ITEMS_SAVED, STOCK_SAVED, LEDGER_SAVED }

/**
 * Saves a sale: Sale + SaleItems + stock movements + (if there is a due) one ledger entry,
 * all inside ONE database transaction. If anything fails, nothing at all is saved
 * (SPEC.md section 5).
 *
 * [clock] gives "now" in UTC millis (tests pass their own).
 * [afterStep] does nothing in the app. Tests use it to throw an error between two steps
 * and prove that the transaction then saves nothing.
 */
class SaleRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val afterStep: (SaveStep) -> Unit = {}
) {
    private val saleDao = db.saleDao()
    private val productDao = db.productDao()
    private val customerDao = db.customerDao()
    private val stockDao = db.stockMovementDao()
    private val ledgerDao = db.ledgerDao()

    /** Only one save runs at a time in this app (a lock, like a single door). */
    private val saveLock = Mutex()

    /** Carts already saved, by draftId, so a double tap returns the same sale. Small and in memory. */
    private val savedDrafts = object : LinkedHashMap<String, SaleResult.Saved>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SaleResult.Saved>?) =
            size > 100
    }

    /** A saved sale, for showing it again (the receipt). */
    suspend fun getSale(id: Long): Sale? = saleDao.getSale(id)

    suspend fun completeSale(draft: SaleDraft): SaleResult = saveLock.withLock {
        // Double tap: this cart was already saved -> give back the same result, save nothing.
        savedDrafts[draft.draftId]?.let { return@withLock it }

        val result = db.withTransaction { saveInsideTransaction(draft) }
        if (result is SaleResult.Saved) savedDrafts[draft.draftId] = result
        result
    }

    /**
     * Runs inside the transaction. Returning a refusal before the first insert writes nothing.
     * Throwing at any point makes SQLite undo every insert made so far.
     */
    private suspend fun saveInsideTransaction(draft: SaleDraft): SaleResult {
        // 1. Read the products again, now. Archived or missing ones are refused.
        val productIds = draft.lines.map { it.productId }.distinct()
        val products = productDao.getProducts(productIds).associateBy { it.id }
        val unavailable = productIds.filter { id -> products[id]?.isActive != true }
        if (unavailable.isNotEmpty()) return SaleResult.ProductsUnavailable(unavailable)

        // 2. The customer, if one was chosen, must exist.
        if (draft.customerId != null && customerDao.getById(draft.customerId) == null) {
            return SaleResult.CustomerNotFound
        }

        // 3. All money rules, with the same calculator the screens use.
        val calculation = SaleCalculator.check(
            lines = draft.lines.map { CartLine(it.quantity, it.unitPrice) },
            discount = draft.discount,
            deliveryCharge = draft.deliveryCharge,
            received = draft.received,
            hasCustomer = draft.customerId != null
        )
        val amounts = when (calculation) {
            is SaleCheck.Invalid -> return SaleResult.Invalid(calculation.errors)
            is SaleCheck.Valid -> calculation.amounts
        }
        check(amounts.isConsistent()) { "Sale amounts do not add up: $amounts" } // never expected

        // 4. The receipt number: today's highest counter + 1 (as a number).
        val now = clock()
        val receiptNo = nextFreeReceiptNo(now)

        // 5. The sale itself.
        val saleId = saleDao.insertSale(
            Sale(
                receiptNo = receiptNo,
                customerId = draft.customerId,
                subtotal = amounts.subtotal,
                discount = amounts.discount,
                deliveryCharge = amounts.deliveryCharge,
                total = amounts.total,
                paidAmount = amounts.paidAmount,
                paymentMethod = draft.paymentMethod,
                dueAmount = amounts.dueAmount,
                note = draft.note,
                createdAt = now
            )
        )
        afterStep(SaveStep.SALE_SAVED)

        // 6. The lines, with snapshots taken NOW: name and cost from the product,
        //    price from the cart (an edited price changes only this sale, never the product).
        saleDao.insertItems(
            draft.lines.mapIndexed { index, line ->
                val product = products.getValue(line.productId)
                SaleItem(
                    saleId = saleId,
                    productId = line.productId,
                    productNameSnapshot = product.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    costPriceSnapshot = product.costPrice,
                    lineTotal = amounts.lineTotals[index]
                )
            }
        )
        afterStep(SaveStep.ITEMS_SAVED)

        // 7. Stock goes DOWN: one negative movement per line. Stock may go below zero (SPEC).
        stockDao.insertAll(
            draft.lines.map { line ->
                StockMovement(
                    productId = line.productId,
                    type = StockMovementType.SALE,
                    quantity = -line.quantity,
                    referenceSaleId = saleId,
                    createdAt = now
                )
            }
        )
        afterStep(SaveStep.STOCK_SAVED)

        // 8. A due goes into the customer's khata.
        if (amounts.dueAmount > 0) {
            ledgerDao.insert(
                LedgerEntry(
                    customerId = draft.customerId!!, // the calculator made sure there is one
                    type = LedgerEntryType.SALE_DUE,
                    amount = amounts.dueAmount,
                    saleId = saleId,
                    entryDate = now,
                    createdAt = now
                )
            )
            afterStep(SaveStep.LEDGER_SAVED)
        }

        return SaleResult.Saved(saleId, receiptNo, amounts)
    }

    /**
     * Today's next receipt number. If, against all odds, it is already taken
     * (for example after a strange clock change), we simply move to the next counter.
     * The unique index on receiptNo is the last safety net.
     */
    private suspend fun nextFreeReceiptNo(now: Long): String {
        val prefix = ReceiptNumber.datePrefix(now)
        var candidate = ReceiptNumber.next(prefix, saleDao.receiptNumbersOfDay(prefix))
        while (saleDao.countWithReceiptNo(candidate) > 0) {
            val counter = ReceiptNumber.counterOf(candidate, prefix)!!
            candidate = ReceiptNumber.build(prefix, counter + 1)
        }
        return candidate
    }
}
