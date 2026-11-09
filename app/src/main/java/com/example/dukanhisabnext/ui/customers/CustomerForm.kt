package com.example.dukanhisabnext.ui.customers

import com.example.dukanhisabnext.ui.products.FormError
import com.example.dukanhisabnext.util.toLatinDigits

// Plain Kotlin (no Android) so it can be unit-tested on the PC.
// It returns error CODES; the screen turns them into Bengali messages.

/** A Bangladeshi mobile number after cleaning: 11 digits, starting with 01. */
private val BD_MOBILE = Regex("""^01\d{9}$""")

/**
 * Cleans a typed mobile number and checks it. Returns the 11-digit number, or null if it is
 * not a valid Bangladeshi mobile number.
 *   "০১৭১১-০০০০০০"  -> "01711000000"   (Bengali digits, dash)
 *   "+8801711000000" -> "01711000000"   (country code removed)
 *   "12345"          -> null
 */
fun normalizeMobile(text: String): String? {
    // Remove spaces and dashes, and turn Bengali digits into normal digits.
    var digits = toLatinDigits(text).filter { it != ' ' && it != '-' }
    if (digits.startsWith("+88")) {
        digits = digits.removePrefix("+88")
    } else if (digits.startsWith("88") && digits.length == 13) {
        digits = digits.removePrefix("88")
    }
    return digits.takeIf { BD_MOBILE.matches(it) }
}

/** What the user typed. */
data class CustomerFormInput(
    val name: String,
    val phone: String,
    val address: String,
    val note: String
)

data class CustomerFormErrors(
    val name: FormError? = null,
    val phone: FormError? = null
) {
    val hasErrors: Boolean get() = name != null || phone != null
}

/** Clean, checked values. Empty optional boxes become null. */
data class ValidatedCustomer(
    val name: String,
    val phone: String?,   // normalized: 11 digits
    val address: String?,
    val note: String?
)

sealed interface CustomerFormResult {
    data class Valid(val customer: ValidatedCustomer) : CustomerFormResult
    data class Invalid(val errors: CustomerFormErrors) : CustomerFormResult
}

/**
 * Rules: name is required. Mobile is optional, but if typed it must be a valid
 * Bangladeshi mobile number. Address and note are optional.
 */
fun validateCustomerForm(input: CustomerFormInput): CustomerFormResult {
    val name = input.name.trim()
    val nameError = if (name.isEmpty()) FormError.REQUIRED else null

    val phoneText = input.phone.trim()
    val phone = if (phoneText.isEmpty()) null else normalizeMobile(phoneText)
    val phoneError = if (phoneText.isNotEmpty() && phone == null) FormError.INVALID_PHONE else null

    val errors = CustomerFormErrors(name = nameError, phone = phoneError)
    if (errors.hasErrors) return CustomerFormResult.Invalid(errors)

    return CustomerFormResult.Valid(
        ValidatedCustomer(
            name = name,
            phone = phone,
            address = input.address.trim().ifEmpty { null },
            note = input.note.trim().ifEmpty { null }
        )
    )
}

/** Where a customer stands, decided from the balance (paisa) computed from the ledger. */
enum class BalanceStatus {
    OWES,    // balance > 0: the customer owes the shop
    CLEAR,   // balance = 0
    ADVANCE  // balance < 0: the customer paid more than they owed
}

fun balanceStatus(balance: Long): BalanceStatus = when {
    balance > 0 -> BalanceStatus.OWES
    balance < 0 -> BalanceStatus.ADVANCE
    else -> BalanceStatus.CLEAR
}
