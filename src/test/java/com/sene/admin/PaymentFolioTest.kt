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
        assertTrue(paymentReferenceMatchesFolio("\tSENE-2026-ABC123\n", " SENE-2026-ABC123 "))
    }

    @Test fun rejectsDifferentOrPartialReferences() {
        assertFalse(paymentReferenceMatchesFolio("SENE-2026-ABC124", "SENE-2026-ABC123"))
        assertFalse(paymentReferenceMatchesFolio("SENE-2026-ABC12", "SENE-2026-ABC123"))
        assertFalse(paymentReferenceMatchesFolio("SENE-2026-ABC123-extra", "SENE-2026-ABC123"))
    }

    @Test fun comparisonIsCaseSensitiveAndDoesNotNormalizeInternalCharacters() {
        assertFalse(paymentReferenceMatchesFolio("sene-2026-abc123", "SENE-2026-ABC123"))
        assertFalse(paymentReferenceMatchesFolio("SENE 2026 ABC123", "SENE-2026-ABC123"))
        assertFalse(paymentReferenceMatchesFolio("SENE--2026-ABC123", "SENE-2026-ABC123"))
    }

    @Test fun rejectsBlankFolioAndBlankReference() {
        assertFalse(paymentReferenceMatchesFolio("anything", "  "))
        assertFalse(paymentReferenceMatchesFolio(" ", "SENE-2026-ABC123"))
    }
}