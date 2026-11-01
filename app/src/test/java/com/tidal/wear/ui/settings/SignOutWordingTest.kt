package com.tidal.wear.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Settings action is a LOCAL sign-out (authRepository.signOut); it must
// never read as deleting the TIDAL account (UX-003).
class SignOutWordingTest {
    @Test
    fun `primary secondary states watch-local removal and never implies account deletion`() {
        for (count in intArrayOf(0, 3)) {
            val s = accountSignOutSecondary(count)
            assertTrue(s, s.contains("this watch"))
            assertFalse(s, s.contains("erase"))
            assertFalse(s, s.contains("delete your account"))
        }
    }

    @Test
    fun `downloads are explicitly kept when present`() {
        assertTrue(accountSignOutSecondary(1).contains("keeps downloads"))
        assertTrue(accountSignOutConfirmSecondary(1).contains("stay"))
    }

    @Test
    fun `confirmation without downloads states nothing is deleted from TIDAL`() {
        val s = accountSignOutConfirmSecondary(0)
        assertTrue(s, s.contains("Nothing is deleted from TIDAL"))
        assertFalse(s, s.contains("erase"))
    }
}
