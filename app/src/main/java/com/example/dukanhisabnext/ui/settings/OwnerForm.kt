package com.example.dukanhisabnext.ui.settings

import com.example.dukanhisabnext.ui.customers.normalizeMobile
import com.example.dukanhisabnext.ui.products.FormError

// The small "owner" form: owner name and mobile. Both are optional. A mobile that is typed must be
// a valid Bangladeshi mobile (the same rules as the customer form and the shop form).

data class OwnerFormInput(val ownerName: String, val phone: String)

data class OwnerFormErrors(val phone: FormError? = null) {
    val hasErrors: Boolean get() = phone != null
}

/** Clean, checked values. Empty boxes become null. */
data class ValidatedOwner(
    val ownerName: String?,
    val phone: String?   // normalized: 11 digits
)

sealed interface OwnerFormResult {
    data class Valid(val owner: ValidatedOwner) : OwnerFormResult
    data class Invalid(val errors: OwnerFormErrors) : OwnerFormResult
}

fun validateOwnerForm(input: OwnerFormInput): OwnerFormResult {
    val phoneText = input.phone.trim()
    val phone = if (phoneText.isEmpty()) null else normalizeMobile(phoneText)
    if (phoneText.isNotEmpty() && phone == null) {
        return OwnerFormResult.Invalid(OwnerFormErrors(phone = FormError.INVALID_PHONE))
    }
    return OwnerFormResult.Valid(
        ValidatedOwner(
            ownerName = input.ownerName.trim().ifEmpty { null },
            phone = phone
        )
    )
}
