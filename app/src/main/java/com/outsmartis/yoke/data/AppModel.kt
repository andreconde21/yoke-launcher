package com.outsmartis.yoke.data

import android.os.UserHandle
import java.text.CollationKey

sealed class AppModel : Comparable<AppModel> {
    abstract val appLabel: String
    abstract val key: CollationKey?
    abstract val appPackage: String
    abstract val user: UserHandle
    abstract val isNew: Boolean

    data class App(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val activityClassName: String?,
        override val isNew: Boolean = false,
        override val user: UserHandle,
        /** The label the app ships with; [appLabel] is the user's alias when one is set. */
        val originalLabel: String = appLabel,
    ) : AppModel()

    data class PinnedShortcut(
        override val appLabel: String,
        override val key: CollationKey?,
        override val appPackage: String,
        val shortcutId: String,
        override val isNew: Boolean = false,
        override val user: UserHandle,
    ) : AppModel() {
        val identity: String
            get() = shortcutIdentity(appPackage, shortcutId, user.toString())
    }

    /** A web link listed alongside apps; opens through ACTION_VIEW. */
    data class Link(
        val entry: LinkEntry,
        override val key: CollationKey? = null,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String get() = entry.name
        override val appPackage: String = ""
        override val isNew: Boolean = false
    }

    /**
     * A command palette row (calculator result, action, shortcut, hint, web search).
     * Never launched as an app: tapping runs [run].
     */
    data class PaletteResult(
        val id: String,
        override val appLabel: String,
        val closeDrawer: Boolean = true,
        val run: () -> Unit = {},
        /** Small text after the label, e.g. "card · in-progress". */
        val detail: String? = null,
    ) : AppModel() {
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val user: UserHandle = android.os.Process.myUserHandle()
        override val isNew: Boolean = false
    }

    data class PrivateSpaceHeader(
        val isLocked: Boolean = true,
        override val user: UserHandle = android.os.Process.myUserHandle(),
    ) : AppModel() {
        override val appLabel: String = ""
        override val key: CollationKey? = null
        override val appPackage: String = ""
        override val isNew: Boolean = false
    }

    override fun compareTo(other: AppModel): Int = when {
        key != null && other.key != null -> key!!.compareTo(other.key)
        else -> appLabel.compareTo(other.appLabel, true)
    }
}
