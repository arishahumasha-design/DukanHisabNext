package com.example.dukanhisabnext.ui.shop

import com.example.dukanhisabnext.ui.customers.normalizeMobile
import com.example.dukanhisabnext.ui.products.FormError

// The "দোকানের তথ্য" form rules, as plain Kotlin so they can be unit-tested on the PC.
// Only the shop name is required. The mobile is optional but, if typed, must be a valid
// Bangladeshi mobile (the same rules as the customer form).

/** What the user typed. */
data class ShopFormInput(
    val name: String,
    val ownerName: String,
    val phone: String,
    val address: String
)

data class ShopFormErrors(
    val name: FormError? = null,
    val phone: FormError? = null
) {
    val hasErrors: Boolean get() = name != null || phone != null
}

/** Clean, checked values. Empty optional boxes become null. */
data class ValidatedShop(
    val name: String,
    val ownerName: String?,
    val phone: String?,   // normalized: 11 digits
    val address: String?
)

sealed interface ShopFormResult {
    data class Valid(val shop: ValidatedShop) : ShopFormResult
    data class Invalid(val errors: ShopFormErrors) : ShopFormResult
}

fun validateShopForm(input: ShopFormInput): ShopFormResult {
    val name = input.name.trim()
    val nameError = if (name.isEmpty()) FormError.REQUIRED else null

    val phoneText = input.phone.trim()
    val phone = if (phoneText.isEmpty()) null else normalizeMobile(phoneText)
    val phoneError = if (phoneText.isNotEmpty() && phone == null) FormError.INVALID_PHONE else null

    val errors = ShopFormErrors(name = nameError, phone = phoneError)
    if (errors.hasErrors) return ShopFormResult.Invalid(errors)

    return ShopFormResult.Valid(
        ValidatedShop(
            name = name,
            ownerName = input.ownerName.trim().ifEmpty { null },
            phone = phone,
            address = input.address.trim().ifEmpty { null }
        )
    )
}
