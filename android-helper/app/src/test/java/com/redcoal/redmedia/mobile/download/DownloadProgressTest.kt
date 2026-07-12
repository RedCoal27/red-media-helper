package com.redcoal.redmedia.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun countsVideoAndSelectedAudioTracks() {
        assertEquals(24, calculatePartsTotal("bestvideo+en+fr+de+es+it+pt+ja+ko+ar+hi+id+tr+ru+th+nl+pl+sv+da+fi+no+cs+uk+vi/best"))
    }

    @Test
    fun ignoresFallbackSelectors() {
        assertEquals(2, calculatePartsTotal("bestvideo+bestaudio/best"))
    }
}
