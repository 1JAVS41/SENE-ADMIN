package com.sene.admin

/** Acepta únicamente la referencia bancaria que coincide con el folio de la solicitud. */
internal fun paymentReferenceMatchesFolio(reference: String, folio: String): Boolean {
    val expected = folio.trim()
    return expected.isNotEmpty() && reference.trim() == expected
}
