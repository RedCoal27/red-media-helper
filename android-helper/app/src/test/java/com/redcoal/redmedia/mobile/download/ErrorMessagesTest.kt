package com.redcoal.redmedia.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun replacesObfuscatedClassNames() {
        assertEquals(
            "The media engine could not start. Reinstall or update the app and try again.",
            userFacingError(IllegalStateException("e4.e"), "Download failed"),
        )
    }

    @Test
    fun usesReadableRootCause() {
        val error = IllegalStateException("Wrapper", IllegalArgumentException("HTTP Error 403"))
        assertEquals("Wrapper", userFacingError(error, "Download failed"))
    }
}
