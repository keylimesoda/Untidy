package com.tidal.wear.ui.player

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaOutputSwitcherPolicyTest {
    private fun candidate(pkg: String, systemFlags: Int) =
        MediaOutputSwitcherPolicy.Candidate(pkg, "$pkg.Activity", systemFlags)

    @Test
    fun `prefers system app owning the media output panel`() {
        val picked = MediaOutputSwitcherPolicy.systemComponent(
            listOf(
                candidate("com.rogue.app", 0),
                candidate("com.android.settings", ApplicationInfo.FLAG_SYSTEM),
            ),
        )
        assertEquals("com.android.settings", picked?.packageName)
    }

    @Test
    fun `updated system app also qualifies`() {
        val picked = MediaOutputSwitcherPolicy.systemComponent(
            listOf(candidate("com.google.android.apps.wellbeing", ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)),
        )
        assertEquals("com.google.android.apps.wellbeing", picked?.packageName)
    }

    @Test
    fun `refuses to launch a non-system handler`() {
        assertNull(
            MediaOutputSwitcherPolicy.systemComponent(
                listOf(candidate("com.rogue.app", ApplicationInfo.FLAG_DEBUGGABLE)),
            ),
        )
    }

    @Test
    fun `empty resolution falls back`() {
        assertNull(MediaOutputSwitcherPolicy.systemComponent(emptyList()))
    }
}
