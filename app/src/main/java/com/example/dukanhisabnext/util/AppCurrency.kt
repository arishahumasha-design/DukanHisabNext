package com.example.dukanhisabnext.util

/**
 * The currency the app SHOWS. It only changes the symbol in front of an amount and how its digits are
 * grouped. It never converts anything: the database keeps the same numbers (paisa / cents) whatever is
 * chosen, so switching from ৳ to ₹ turns "৳ 1,500" into "₹ 1,500", not into another amount.
 *
 * [lakhGrouping]: true writes 1,00,000 (the last three digits, then pairs: Bangladesh, India, Pakistan,
 * Nepal); false writes 100,000 (groups of three). Every currency has two decimals, like the paisa.
 */
enum class AppCurrency(val code: String, val symbol: String, val lakhGrouping: Boolean) {
    BDT("BDT", "৳", true),
    INR("INR", "₹", true),
    PKR("PKR", "Rs", true),
    NPR("NPR", "Rs", true),
    USD("USD", "$", false),
    EUR("EUR", "€", false),
    GBP("GBP", "£", false),
    SAR("SAR", "SAR", false),
    AED("AED", "AED", false),
    QAR("QAR", "QAR", false),
    KWD("KWD", "KWD", false),
    OMR("OMR", "OMR", false),
    MYR("MYR", "RM", false),
    SGD("SGD", "S$", false),
    CAD("CAD", "C$", false),
    AUD("AUD", "A$", false);

    companion object {
        /** The currency the app starts with, and the one the strings are written with. */
        val DEFAULT = BDT

        fun fromCode(code: String?): AppCurrency? = entries.firstOrNull { it.code == code }
    }
}

/** The currency shown right now (set at start from the saved choice, and when the user changes it). */
object CurrentCurrency {
    @Volatile
    var value: AppCurrency = AppCurrency.DEFAULT
}

/**
 * Groups the digits of a whole number: "1234567" -> "12,34,567" (lakh/crore) or "1,234,567".
 * [digits] holds only 0-9.
 */
fun groupDigits(digits: String, lakhGrouping: Boolean): String {
    if (digits.length <= 3) return digits
    if (!lakhGrouping) return digits.reversed().chunked(3).joinToString(",").reversed()
    val lastThree = digits.takeLast(3)
    val head = digits.dropLast(3)
    // Walk the head from the right in pairs, then put the pairs back in order.
    val pairs = head.reversed().chunked(2).map { it.reversed() }.reversed()
    return pairs.joinToString(",") + "," + lastThree
}

/**
 * Makes a text from strings.xml show the chosen currency. The strings are written with the default
 * currency: its sign "৳" becomes the current symbol, and "{max}" becomes the largest amount the app
 * accepts, written in the current style (money rules: MAX_LEDGER_AMOUNT).
 */
fun String.withCurrency(): String {
    val currency = CurrentCurrency.value
    var text = this
    if ("{max}" in text) text = text.replace("{max}", "${currency.symbol} ${formatTaka(MAX_AMOUNT_PAISA)}")
    return if (currency == AppCurrency.DEFAULT) text else text.replace(AppCurrency.DEFAULT.symbol, currency.symbol)
}

/** The largest single amount a form accepts (৳ 1 crore as paisa): the same number as LedgerRules.MAX_LEDGER_AMOUNT. */
const val MAX_AMOUNT_PAISA: Long = 1_000_000_000L

/** "₹ 1,50,000" / "$ 150,000": a sample amount written the way [currency] writes it (used to explain a currency change). */
fun sampleMoney(currency: AppCurrency): String = "${currency.symbol} ${localDigits(groupDigits("150000", currency.lakhGrouping))}"
