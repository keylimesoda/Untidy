package com.tidal.wear.ui.player

/** Keeps a vanished Bluetooth device from remaining presented as Untidy's selected output. */
internal object AudioOutputSelectionPolicy {
    fun selectedDeviceId(
        requestedDeviceId: Int?,
        routedDeviceId: Int?,
        availableDeviceIds: Set<Int>,
    ): Int? = requestedDeviceId?.takeIf(availableDeviceIds::contains)
        ?: routedDeviceId?.takeIf(availableDeviceIds::contains)
}
