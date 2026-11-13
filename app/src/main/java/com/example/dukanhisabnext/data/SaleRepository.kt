package com.example.dukanhisabnext.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.dukanhisabnext.cancel.CancelReasonCheck
import com.example.dukanhisabnext.cancel.checkCancelReason
import com.example.dukanhisabnext.cancel.dueReversalEntry
import com.example.dukanhisabnext.cancel.stockBackMovements
import com.example.dukanhisabnext.data.dao.SalesListSummaryRow
import com.example.dukanhisabnext.data.dao.SalesSearchParams
import com.example.dukanhisabnext.data.dao.SalesTotalsRow
import com.example.dukanhisabnext.data.entity.SaleCancellation
import com.example.dukanhisabnext.data.entity.LedgerEntry
import com.example.dukanhisabnext.data.entity.LedgerEntryType
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.data.entity.SaleItem
import com.example.dukanhisabnext.data.entity.StockMovement
import com.example.dukanhisabnext.data.entity.StockMovementType
import com.example.dukanhisabnext.data.dao.SaleListRow
import com.example.dukanhisabnext.util.CartLine
import com.example.dukanhisabnext.util.DhakaTime
import com.example.dukanhisabnext.util.ReceiptNumber
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.sale.SaleDayProblem
import com.example.dukanhisabnext.sale.checkSaleDay
import com.example.dukanhisabnext.sale.saleDateFor
import com.example.dukanhisabnext.util.SaleError
import com.example.dukanhisabnext.warranty.checkWarranty
import com.example.dukanhisabnext.warranty.warrantyUntil
import java.time.LocalDate
import com.example.dukanhisabnext.util.SaleAmounts
import com.example.dukanhisabnext.util.SaleCalculator
import com.example.dukanhisabnext.util.SaleCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One cart line as the cart holds it. [unitPrice] may differ from the product's price (edited). */
data class SaleDraftLine(
    val productId: Long,
    val quantity: Long,  // thousandths
    val unitPrice: Long, // paisa per 1 unit
    /** Version 5: this line's warranty (both null = none). The last day is worked out when saving. */
    val warrantyValue: Int? = null,
    val warrantyUnit: WarrantyUnit? = null
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
    val note: String? = null,
    /** Version 5: the Dhaka day the sale belongs to; null = today (the day of saving). */
    val saleDay: LocalDate? = null
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

/** What happened when we tried to cancel a sale. */
sealed interface CancelResult {
    /** The sale is now cancelled: stock came back, and its due (if any) was taken away. */
    data class Cancelled(val cancellation: SaleCancellation) : CancelResult

    /** It was already cancelled (a double tap or a retry). Nothing was changed. */
    data class AlreadyCancelled(val cancellation: SaleCancellation) : CancelResult

    /** There is no sale with this id. Nothing was changed. */
    data object SaleNotFound : CancelResult

    /** The reason is longer than 200 characters. Nothing was changed. */
    data object ReasonTooLong : CancelResult
}

/** The steps of a cancellation, in order. Tests use them to simulate a crash between steps. */
enum class CancelStep { CANCELLATION_SAVED, STOCK_RETURNED, DUE_REVERSED }

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
    private val afterStep: (SaveStep) -> Unit = {},
    private val afterCancelStep: (CancelStep) -> Unit = {},
    private val journal: SaleDraftJournal = InMemorySaleDraftJournal()
) {
    private val saleDao = db.saleDao()
    private val productDao = db.productDao()
    private val customerDao = db.customerDao()
    private val stockDao = db.stockMovementDao()
    private val ledgerDao = db.ledgerDao()
    private val cancellationDao = db.saleCancellationDao()

    /** Only one save runs at a time in this app (a lock, like a single door). */
    private val saveLock = Mutex()

    /** Carts already saved, by draftId, so a double tap returns the same sale. Small and in memory. */
    private val savedDrafts = object : LinkedHashMap<String, SaleResult.Saved>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SaleResult.Saved>?) =
            size > 100
    }

    /** The sales list for a time window, newest first, with customer name and mobile. Read-only. */
    fun observeSalesList(range: DhakaTime.TimeRange): Flow<List<SaleListRow>> =
        saleDao.observeList(range.startMillis, range.endMillisExclusive)

    /** One page of the sales list screen (search done by the database), newest first, at most [limit] rows. */
    fun observeSalesPage(range: DhakaTime.TimeRange, search: SalesSearchParams, limit: Int): Flow<List<SaleListRow>> =
        saleDao.observeListPage(
            range.startMillis, range.endMillisExclusive,
            search.query, search.compactQuery, search.digits, search.digitsWithout88, limit
        )

    /** The count and total line of the sales list for the WHOLE window and search. */
    fun observeSalesPageSummary(range: DhakaTime.TimeRange, search: SalesSearchParams): Flow<SalesListSummaryRow> =
        saleDao.observeListSummary(
            range.startMillis, range.endMillisExclusive,
            search.query, search.compactQuery, search.digits, search.digitsWithout88
        )

    /** The number of saved sales (all time). */
    fun observeSalesCount(): Flow<Int> = saleDao.observeCount()

    /** Count and total of the sales in a window, WITHOUT cancelled sales (for reports). */
    fun observeActiveTotals(range: DhakaTime.TimeRange): Flow<SalesTotalsRow> =
        saleDao.observeActiveTotals(range.startMillis, range.endMillisExclusive)

    /** A saved sale, for showing it again (the receipt). */
    suspend fun getSale(id: Long): Sale? = saleDao.getSale(id)

    /** The cancellation of a sale, or null if it is not cancelled. */
    suspend fun getCancellation(saleId: Long): SaleCancellation? = cancellationDao.getForSale(saleId)

    /** Live: the receipt shows its "বাতিল" banner as soon as the sale is cancelled. */
    fun observeCancellation(saleId: Long): Flow<SaleCancellation?> = cancellationDao.observeForSale(saleId)

    // =====================================================================================
    // Cancel a sale (বিক্রি বাতিল)
    // =====================================================================================

    /**
     * Cancels a sale in ONE transaction: records the cancellation, brings back the stock of every
     * item (SALE_CANCEL, positive), and if the sale left a due, takes exactly that due away with a
     * CORRECTION_MINUS ledger entry. The sale row is never changed and its receipt number is never
     * reused. If any step fails, nothing is saved.
     *
     * Cancel only once: a check inside the transaction, plus the unique index on saleId, turn a
     * double tap or a retry into [CancelResult.AlreadyCancelled] (nothing changes the second time).
     * [reason] is optional; it is trimmed and may be at most 200 characters.
     */
    suspend fun cancelSale(saleId: Long, reason: String?): CancelResult {
        val cleanReason = when (val checked = checkCancelReason(reason)) {
            CancelReasonCheck.TooLong -> return CancelResult.ReasonTooLong
            is CancelReasonCheck.Ok -> checked.reason
        }
        // The same lock as saving: one write at a time in this app.
        return saveLock.withLock {
            try {
                db.withTransaction { cancelInsideTransaction(saleId, cleanReason) }
            } catch (e: SQLiteConstraintException) {
                // Two cancels raced past the check (for example from two screens at once). The
                // unique index stopped the second; its transaction was rolled back. Report it calmly.
                val existing = cancellationDao.getForSale(saleId) ?: throw e
                CancelResult.AlreadyCancelled(existing)
            }
        }
    }

    private suspend fun cancelInsideTransaction(saleId: Long, reason: String?): CancelResult {
        // 1. The sale must exist, and must not be cancelled yet.
        val sale = saleDao.getSale(saleId) ?: return CancelResult.SaleNotFound
        cancellationDao.getForSale(saleId)?.let { return CancelResult.AlreadyCancelled(it) }

        val now = clock()

        // 2. The cancellation record. A second one for the same sale would break the unique index.
        val cancellation = SaleCancellation(saleId = saleId, reason = reason, createdAt = now)
        val cancellationId = cancellationDao.insert(cancellation)
        afterCancelStep(CancelStep.CANCELLATION_SAVED)

        // 3. Stock comes back for every item (also for archived products, and also when the
        //    sale had pushed the stock below zero: the sold quantity is simply added back).
        stockDao.insertAll(stockBackMovements(saleId, saleDao.getItems(saleId), now))
        afterCancelStep(CancelStep.STOCK_RETURNED)

        // 4. The sale's due (if any) is taken away again: exactly that amount, linked to the sale.
        dueReversalEntry(sale, now)?.let { entry ->
            ledgerDao.insert(entry)
            afterCancelStep(CancelStep.DUE_REVERSED)
        }

        return CancelResult.Cancelled(cancellation.copy(id = cancellationId))
    }

    suspend fun completeSale(draft: SaleDraft): SaleResult = saveLock.withLock {
        // Double tap: this cart was already saved -> give back the same result, save nothing.
        savedDrafts[draft.draftId]?.let { return@withLock it }
        // After a restart the memory above is empty, but the journal file remembers the moment of
        // this cart's last attempt: if a sale was saved at that moment, it is this cart's sale.
        findEarlierSave(draft)?.let { saved ->
            savedDrafts[draft.draftId] = saved
            return@withLock saved
        }

        // The moment of THIS attempt is written to the disk before anything is saved.
        val now = clock()
        withContext(Dispatchers.IO) { journal.recordAttempt(draft.draftId, now) }

        val result = db.withTransaction { saveInsideTransaction(draft, now) }
        if (result is SaleResult.Saved) savedDrafts[draft.draftId] = result
        result
    }

    /**
     * The sale an earlier attempt of this cart saved (the app was killed before the cart was
     * emptied), or null. It must have been saved at exactly the recorded moment AND have the total
     * this cart adds up to.
     */
    private suspend fun findEarlierSave(draft: SaleDraft): SaleResult.Saved? {
        val at = journal.attemptOf(draft.draftId) ?: return null
        val check = SaleCalculator.check(
            lines = draft.lines.map { CartLine(it.quantity, it.unitPrice) },
            discount = draft.discount,
            deliveryCharge = draft.deliveryCharge,
            received = draft.received,
            hasCustomer = draft.customerId != null
        ) as? SaleCheck.Valid ?: return null
        val sale = saleDao.findSaleAt(at, check.amounts.total) ?: return null
        return SaleResult.Saved(sale.id, sale.receiptNo, check.amounts)
    }

    /**
     * Runs inside the transaction. Returning a refusal before the first insert writes nothing.
     * Throwing at any point makes SQLite undo every insert made so far.
     */
    private suspend fun saveInsideTransaction(draft: SaleDraft, now: Long): SaleResult {
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

        // 3b. The sale date (version 5): never a future day, never before 1 Jan 2020. Today is
        //     "now"; an earlier day is that day at the current time of day (saleDateFor).
        val day = draft.saleDay ?: DhakaTime.localDate(now)
        when (checkSaleDay(day, DhakaTime.localDate(now))) {
            SaleDayProblem.FUTURE -> return SaleResult.Invalid(setOf(SaleError.FUTURE_DATE))
            SaleDayProblem.BEFORE_MIN -> return SaleResult.Invalid(setOf(SaleError.DATE_BEFORE_MIN))
            null -> Unit
        }
        val saleDate = saleDateFor(day, now)

        // 3c. Every line warranty must be complete and within its limits.
        if (draft.lines.any { checkWarranty(it.warrantyValue, it.warrantyUnit) != null }) {
            return SaleResult.Invalid(setOf(SaleError.WARRANTY_INVALID))
        }

        // 4. The receipt number: the SALE DAY's highest counter + 1 (as a number). A back-dated
        //    sale gets the next free number of its own day. [now] (createdAt) was recorded in the
        //    journal before the transaction, so a retry after a crash can find this sale.
        val receiptNo = nextFreeReceiptNo(saleDate)

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
                createdAt = now,
                saleDate = saleDate
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
                    lineTotal = amounts.lineTotals[index],
                    // The warranty snapshot: counted from the sale day, never changed later.
                    warrantyValue = line.warrantyValue,
                    warrantyUnit = line.warrantyUnit,
                    warrantyUntil = line.warrantyValue?.let { value ->
                        warrantyUntil(day, value, line.warrantyUnit!!).toEpochDay()
                    }
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
                    createdAt = saleDate // a SALE movement is dated by the sale day (version 5)
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
                    entryDate = saleDate, // the sale's day; createdAt stays the real moment
                    createdAt = now
                )
            )
            afterStep(SaveStep.LEDGER_SAVED)
        }

        return SaleResult.Saved(saleId, receiptNo, amounts)
    }

    /**
     * The next receipt number of the sale's day ([saleDate]). If, against all odds, it is already
     * taken (for example after a strange clock change), we simply move to the next counter.
     * The unique index on receiptNo is the last safety net.
     */
    private suspend fun nextFreeReceiptNo(saleDate: Long): String {
        val prefix = ReceiptNumber.datePrefix(saleDate)
        var candidate = ReceiptNumber.next(prefix, saleDao.receiptNumbersOfDay(prefix))
        while (saleDao.countWithReceiptNo(candidate) > 0) {
            val counter = ReceiptNumber.counterOf(candidate, prefix)!!
            candidate = ReceiptNumber.build(prefix, counter + 1)
        }
        return candidate
    }
}
