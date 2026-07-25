package com.soumya.voicepilot.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaQueryTest {

    @Test
    fun `splits the app off the end`() {
        val request = MediaQuery.parse("shape of you on spotify")
        assertEquals("shape of you", request.query)
        assertEquals("spotify", request.appHint)
    }

    @Test
    fun `no app named leaves the whole utterance as the query`() {
        val request = MediaQuery.parse("lofi hip hop")
        assertEquals("lofi hip hop", request.query)
        assertNull(request.appHint)
    }

    @Test
    fun `splits on the last on so a track can contain one`() {
        val request = MediaQuery.parse("turn it on on youtube")
        assertEquals("turn it on", request.query)
        assertEquals("youtube", request.appHint)
    }

    @Test
    fun `a leading on is not a split point`() {
        val request = MediaQuery.parse("on the road again")
        assertEquals("on the road again", request.query)
        assertNull(request.appHint)
    }
}
