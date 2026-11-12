package com.example.dukanhisabnext.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a big amount asks "ঠিক আছে?" before saving (the thresholds are the numbers in ConfirmRules.kt). */
class ConfirmRulesTest {

    @Test
    fun theThresholdsAreTheOnesInTheSpec() {
        assertEquals(10_000_00L, LEDGER_CONFIRM_PAISA)       // ৳10,000
        assertEquals(5_000_00L, EXPENSE_CONFIRM_PAISA)       // ৳5,000
        assertEquals(1_000_000L, STOCK_CONFIRM_QUANTITY)     // 1,000 units (thousandths)
        assertTrue(EXPENSE_CONFIRM_PAISA < LEDGER_CONFIRM_PAISA)
    }

    @Test
    fun aLedgerAmountAsksFromTenThousandTaka() {
        assertFalse(needsMoneyConfirm(9_999_99, LEDGER_CONFIRM_PAISA))
        assertTrue(needsMoneyConfirm(10_000_00, LEDGER_CONFIRM_PAISA))
        assertTrue(needsMoneyConfirm(50_000_00, LEDGER_CONFIRM_PAISA))
        assertFalse("a normal entry never asks", needsMoneyConfirm(50_000, LEDGER_CONFIRM_PAISA))
    }

    @Test
    fun anExpenseAsksFromFiveThousandTaka() {
        assertFalse(needsMoneyConfirm(4_999_99, EXPENSE_CONFIRM_PAISA))
        assertTrue(needsMoneyConfirm(5_000_00, EXPENSE_CONFIRM_PAISA))
        assertFalse(needsMoneyConfirm(500_00, EXPENSE_CONFIRM_PAISA))
    }

    @Test
    fun anEmptyOrUnreadableAmountIsLeftToTheNormalError() {
        assertFalse(needsMoneyConfirm(null, LEDGER_CONFIRM_PAISA))
        assertFalse(needsMoneyConfirm(null, EXPENSE_CONFIRM_PAISA))
        assertFalse(needsMoneyConfirm(0, EXPENSE_CONFIRM_PAISA))
    }

    // ---- stock ----

    @Test
    fun aThousandUnitsOrMoreAlwaysAsks() {
        assertFalse(needsStockConfirm(999_999, null))
        assertTrue(needsStockConfirm(1_000_000, null))
        assertTrue(needsStockConfirm(5_000_000, 40_000_000)) // even with a huge stock already
        assertTrue("the direction does not matter", needsStockConfirm(-1_000_000, 2_000_000))
    }

    @Test
    fun aChangeTenTimesTheStockNowAsksEvenWhenSmall() {
        assertTrue(needsStockConfirm(200_000, 20_000))   // 200 units into a stock of 20 (10x): an extra zero
        assertFalse(needsStockConfirm(199_999, 20_000))  // just under 10x
        assertFalse(needsStockConfirm(100_000, 20_000))  // 100 units is 5x
        assertTrue(needsStockConfirm(-300_000, 25_000))  // a count 300 below a stock of 25: also asks
    }

    @Test
    fun aNewOrEmptyProductNeverAsksForASmallChange() {
        assertFalse(needsStockConfirm(50_000, 0))        // first purchase of 50
        assertFalse(needsStockConfirm(50_000, null))     // stock not known yet
        assertFalse(needsStockConfirm(50_000, -3_000))   // negative stock: not "ten times"
        assertFalse("below 100 units the relative rule is off", needsStockConfirm(90_000, 1_000)) // 90 units into a stock of 1
    }

    @Test
    fun noChangeAsksNothing() {
        assertFalse(needsStockConfirm(null, 10_000))
        assertFalse(needsStockConfirm(0, 10_000))
    }
}
