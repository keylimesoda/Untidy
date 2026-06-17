package com.tidal.wear.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerAccessPolicyTest {
    @Test
    fun allowsOwnPackageControllers() {
        assertTrue(
            MediaControllerAccessPolicy.isAllowedController(
                ownPackageName = "com.tidal.wear",
                controllerPackageName = "com.tidal.wear",
                isTrusted = false,
                isMediaNotificationController = false,
            ),
        )
    }

    @Test
    fun allowsTrustedControllers() {
        assertTrue(
            MediaControllerAccessPolicy.isAllowedController(
                ownPackageName = "com.tidal.wear",
                controllerPackageName = "com.android.systemui",
                isTrusted = true,
                isMediaNotificationController = false,
            ),
        )
    }

    @Test
    fun allowsMediaNotificationController() {
        assertTrue(
            MediaControllerAccessPolicy.isAllowedController(
                ownPackageName = "com.tidal.wear",
                controllerPackageName = "com.google.android.wearable.media.sessions",
                isTrusted = false,
                isMediaNotificationController = true,
            ),
        )
    }

    @Test
    fun rejectsUntrustedOtherPackages() {
        assertFalse(
            MediaControllerAccessPolicy.isAllowedController(
                ownPackageName = "com.tidal.wear",
                controllerPackageName = "com.example.attacker",
                isTrusted = false,
                isMediaNotificationController = false,
            ),
        )
    }
}
