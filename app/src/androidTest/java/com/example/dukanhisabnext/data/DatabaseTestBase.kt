package com.example.dukanhisabnext.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.dukanhisabnext.data.entity.Customer
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.Product
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.data.entity.Sale
import com.example.dukanhisabnext.testing.IsolatedContext
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before

/**
 * Shared setup for DAO tests. Each test gets a brand-new database that lives
 * only in memory, so tests never touch the real shop data on the phone.
 */
abstract class DatabaseTestBase {
    protected lateinit var db: AppDatabase

    /**
     * The context of every test built on this class: its files, cache and databases are in a
     * temporary folder (cache/isolated-phone-tests), never the app's real ones (IsolatedContext).
     */
    protected lateinit var testContext: IsolatedContext

    // A fixed "now" so results never depend on when the test runs.
    protected val now = 1_762_700_000_000L

    @Before
    fun createDb() {
        testContext = IsolatedContext.create(ApplicationProvider.getApplicationContext(), "db-" + javaClass.simpleName)
        db = Room.inMemoryDatabaseBuilder(testContext, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
        testContext.cleanUp()
    }

    // ---- Small helpers to create test rows ----

    protected suspend fun addProduct(name: String, categoryId: Long? = null): Long =
        db.productDao().insertProduct(
            Product(
                name = name,
                categoryId = categoryId,
                unit = ProductUnit.PIECE,
                sellPrice = 1_000,
                createdAt = now,
                updatedAt = now
            )
        )

    protected suspend fun addCustomer(name: String, phone: String? = null): Long =
        db.customerDao().insert(Customer(name = name, phone = phone, createdAt = now, updatedAt = now))

    protected fun sale(receiptNo: String, customerId: Long? = null, createdAt: Long = now) = Sale(
        receiptNo = receiptNo,
        customerId = customerId,
        subtotal = 10_000,
        discount = 0,
        deliveryCharge = 0,
        total = 10_000,
        paidAmount = 10_000,
        paymentMethod = PaymentMethod.CASH,
        dueAmount = 0,
        createdAt = createdAt
    )

    /** How many rows a table has, read straight from SQLite (for "nothing was saved" checks). */
    protected fun countRows(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    /** Passes only if [block] is rejected by the database (foreign key, unique index...). */
    protected suspend fun assertConstraintFails(block: suspend () -> Unit) {
        try {
            block()
        } catch (expected: SQLiteConstraintException) {
            return
        }
        fail("Expected the database to reject this write, but it was saved")
    }
}
