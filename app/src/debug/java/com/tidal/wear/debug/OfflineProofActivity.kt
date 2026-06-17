package com.tidal.wear.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Debug-only activity trampoline so adb can run the offline proof while the app is foreground. */
class OfflineProofActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, OfflineProofService::class.java).apply {
            putExtra(OfflineProofService.EXTRA_TRACK_ID, intent.getStringExtra(OfflineProofService.EXTRA_TRACK_ID))
            putExtra(OfflineProofService.EXTRA_COUNTRY_CODE, intent.getStringExtra(OfflineProofService.EXTRA_COUNTRY_CODE))
        }
        startService(serviceIntent)
        finish()
    }
}
