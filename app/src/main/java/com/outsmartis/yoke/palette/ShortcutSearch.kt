package com.outsmartis.yoke.palette

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserManager

/** A launcher shortcut (dynamic, manifest or pinned) together with the label of the app it belongs to. */
class ShortcutHit(val info: ShortcutInfo, val appLabel: String) {
    val label: String get() = (info.shortLabel ?: info.longLabel ?: info.id).toString()
}

/** The `/` palette mode. Only works while Yoke is the default launcher. */
object ShortcutSearch {

    fun canQuery(context: Context): Boolean = try {
        launcherApps(context).hasShortcutHostPermission()
    } catch (_: Exception) {
        false
    }

    /** Blocking, one binder call per app: run off the main thread. */
    fun loadAll(context: Context): List<ShortcutHit> {
        val launcherApps = launcherApps(context)
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        val hits = mutableListOf<ShortcutHit>()
        for (profile in userManager.userProfiles) {
            try {
                val apps = launcherApps.getActivityList(null, profile)
                    .groupBy({ it.applicationInfo.packageName }, { it.label.toString() })
                for ((pkg, labels) in apps) {
                    val query = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flags)
                    val shortcuts = try {
                        launcherApps.getShortcuts(query, profile)
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    shortcuts.filter { it.isEnabled }.forEach { hits += ShortcutHit(it, labels.first()) }
                }
            } catch (_: Exception) {
                // a locked or unavailable profile just contributes nothing
            }
        }
        return hits.sortedBy { it.label.lowercase() }
    }

    fun filter(hits: List<ShortcutHit>, query: String): List<ShortcutHit> =
        if (query.isBlank()) hits
        else hits.filter { AppSearch.matches(query, it.label, it.appLabel) }

    fun start(context: Context, hit: ShortcutHit) {
        launcherApps(context).startShortcut(hit.info, null, null)
    }

    private fun launcherApps(context: Context) =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
}
