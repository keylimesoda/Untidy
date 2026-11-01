package com.tidal.wear.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.Settings

/**
 * Resolves which activity (if any) should handle the system media-output switcher.
 *
 * The switcher only counts when a system app owns it; an unfiltered launch could hand the
 * user's output choice to any installed app that registers the same action. Mirrors
 * google/horologist's OutputSwitcher (Apache-2.0) contract.
 */
internal object MediaOutputSwitcherPolicy {
    data class Candidate(val packageName: String, val activityName: String, val systemFlags: Int)

    fun systemComponent(candidates: List<Candidate>): Candidate? =
        candidates.firstOrNull {
            (it.systemFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        }
}

internal object MediaOutputSwitcherLauncher {
    private const val OUTPUT_SWITCHER_ACTION = "com.android.settings.panel.action.MEDIA_OUTPUT"
    private const val EXTRA_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"

    /** Opens the Wear OS 5+ system media-output switcher; falls back to Bluetooth settings. */
    fun launch(context: Context): Boolean {
        val switcherIntent = Intent(OUTPUT_SWITCHER_ACTION)
            .putExtra(EXTRA_PACKAGE_NAME, context.packageName)
        val component = MediaOutputSwitcherPolicy.systemComponent(
            context.packageManager.queryIntentActivities(switcherIntent, 0).map { resolveInfo ->
                MediaOutputSwitcherPolicy.Candidate(
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    systemFlags = resolveInfo.activityInfo.applicationInfo?.flags ?: 0,
                )
            },
        )
        if (component != null) {
            switcherIntent.component = ComponentName(component.packageName, component.activityName)
            switcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(switcherIntent) }.onSuccess { return true }
        }
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }
}
