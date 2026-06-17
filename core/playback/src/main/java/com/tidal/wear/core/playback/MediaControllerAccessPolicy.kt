package com.tidal.wear.core.playback

/**
 * Media3 exposes TidalMediaService so system media controllers can discover and control playback.
 * Keep that exported surface limited to our app, platform-trusted/media-control callers, and the
 * Wear media notification controller that renders the active session notification.
 */
internal object MediaControllerAccessPolicy {
    fun isAllowedController(
        ownPackageName: String,
        controllerPackageName: String,
        isTrusted: Boolean,
        isMediaNotificationController: Boolean,
    ): Boolean = controllerPackageName == ownPackageName || isTrusted || isMediaNotificationController
}
