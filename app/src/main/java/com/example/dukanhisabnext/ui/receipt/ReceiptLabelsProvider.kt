package com.example.dukanhisabnext.ui.receipt

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.dukanhisabnext.R
import com.example.dukanhisabnext.data.entity.WarrantyUnit
import com.example.dukanhisabnext.ui.products.labelRes
import androidx.compose.ui.res.stringArrayResource
import com.example.dukanhisabnext.cancel.CancelDialogLabels
import com.example.dukanhisabnext.data.entity.PaymentMethod
import com.example.dukanhisabnext.data.entity.ProductUnit
import com.example.dukanhisabnext.receipt.ReceiptLabels
import com.example.dukanhisabnext.ui.products.labelRes
import com.example.dukanhisabnext.util.DayPeriod

/** The words of the cancel confirm dialog, from strings.xml. */
@Composable
fun cancelDialogLabels(): CancelDialogLabels {
    val stockBack = stringResource(R.string.cancel_stock_back)
    val refund = stringResource(R.string.cancel_refund, "{amount}")
    val dueReduced = stringResource(R.string.cancel_due_reduced, "{amount}")
    val advance = stringResource(R.string.cancel_advance_warning, "{amount}")
    return CancelDialogLabels(
        stockBack = stockBack,
        refund = { refund.replace("{amount}", it) },
        dueReduced = { dueReduced.replace("{amount}", it) },
        advance = { advance.replace("{amount}", it) }
    )
}

/**
 * The receipt words from strings.xml, handed to the pure receipt code. Used both to draw the
 * receipt on the screen and to build the text that is shared, so the two always say the same.
 */
@Composable
fun receiptLabels(): ReceiptLabels {
    val units: Map<ProductUnit, String> = ProductUnit.entries.associateWith { stringResource(it.labelRes()) }
    val methods: Map<PaymentMethod, String> = mapOf(
        PaymentMethod.CASH to stringResource(R.string.receipt_method_cash),
        PaymentMethod.BKASH to stringResource(R.string.receipt_method_bkash),
        PaymentMethod.NAGAD to stringResource(R.string.receipt_method_nagad),
        PaymentMethod.BANK to stringResource(R.string.receipt_method_bank),
        PaymentMethod.OTHER to stringResource(R.string.receipt_method_other)
    )
    val periods: Map<DayPeriod, String> = mapOf(
        DayPeriod.MORNING to stringResource(R.string.period_morning),
        DayPeriod.NOON to stringResource(R.string.period_noon),
        DayPeriod.AFTERNOON to stringResource(R.string.period_afternoon),
        DayPeriod.EVENING to stringResource(R.string.period_evening),
        DayPeriod.NIGHT to stringResource(R.string.period_night)
    )

    val warrantyUnits: Map<WarrantyUnit, String> = WarrantyUnit.entries.associateWith { stringResource(it.labelRes()) }
    val months = stringArrayResource(R.array.months_bn).toList()
    val untilTemplate = stringResource(R.string.warranty_until, "{day}")
    val captionTemplate = stringResource(R.string.receipt_warranty, "{period}", "{until}")

    return ReceiptLabels(
        shopPlaceholder = stringResource(R.string.receipt_shop_placeholder),
        mobile = stringResource(R.string.receipt_mobile),
        receiptNo = stringResource(R.string.receipt_no),
        date = stringResource(R.string.receipt_date),
        customer = stringResource(R.string.receipt_customer),
        subtotal = stringResource(R.string.receipt_subtotal),
        discount = stringResource(R.string.receipt_discount),
        delivery = stringResource(R.string.receipt_delivery),
        grandTotal = stringResource(R.string.receipt_grand_total),
        paid = stringResource(R.string.receipt_paid),
        change = stringResource(R.string.receipt_change),
        saleDue = stringResource(R.string.receipt_sale_due),
        customerTotalDue = stringResource(R.string.receipt_customer_total_due),
        note = stringResource(R.string.receipt_note),
        cancelledNotice = stringResource(R.string.cancelled_notice),
        cancelReason = stringResource(R.string.cancel_reason_prefix),
        taka = stringResource(R.string.unit_taka),
        unit = { units.getValue(it) },
        method = { methods.getValue(it) },
        period = { periods.getValue(it) },
        warrantyUnit = { warrantyUnits.getValue(it) },
        monthNames = months,
        warrantyUntil = { day -> untilTemplate.replace("{day}", day) },
        warrantyCaption = { period, until -> captionTemplate.replace("{period}", period).replace("{until}", until) }
    )
}
