package com.tidal.wear.ui.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSessionStateTest {
    @Test
    fun `automatic input is consumed once for a retained search session`() {
        val session = SearchSessionState()

        assertTrue(session.consumeAutoInputLaunch())
        assertFalse(session.consumeAutoInputLaunch())
    }

    @Test
    fun `retained session keeps query and result mode after back-stack recreation`() {
        val session = SearchSessionState()
        session.consumeAutoInputLaunch()
        session.query = "Beyonce"
        session.submittedQuery = "Beyonce"
        session.inputPending = false

        assertTrue(session.submittedQuery.isNotBlank())
        assertFalse(session.inputPending)
        assertFalse(session.consumeAutoInputLaunch())
    }
}
