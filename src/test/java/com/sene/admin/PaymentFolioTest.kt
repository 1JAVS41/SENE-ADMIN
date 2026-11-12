package com.sene.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentFolioTest {
    @Test fun acceptsExactFolio() {
        assertTrue(paymentReferenceMatchesFolio("SENE-2026-ABC123", "SENE-2026-ABC123"))
    }

    @Test fun trimsOnlyOuterWhitespace() {
        assertTrue(paymentReferenceMatchesFolio("  SENE-2026-ABC123  ", "SENE-2026-ABC123"))
    }

    @Test fun rejectsDifferentReference() {
        assertFalse(paymentReferenceMatchesFolio("SENE-2026-ABC124", "SENE-2026-ABC123"))
        assertFalse(paymentReferenceMatchesFolio("SENE-2026-abc123", "SENE-2026-ABC123"))
        assertFalse(paymentReferenceMatchesFolio("SENE 2026 ABC123", "SENE-2026-ABC123"))
    }

    @Test fun rejectsBlankFolio() {
        assertFalse(paymentReferenceMatchesFolio("anything", "  "))
    }
}
