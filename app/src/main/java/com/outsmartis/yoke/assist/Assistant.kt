package com.outsmartis.yoke.assist

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.outsmartis.yoke.gestures.GestureAction

/**
 * Optional: Yoke as the phone's digital assistant, so long-pressing power (or the home gesture)
 * runs a Yoke action instead of Gemini or Bixby. Off by default: the ASSIST entry point is a
 * disabled component, so Yoke isn't even offered as an assistant until the user turns it on here.
 * Becoming the assistant is the user's choice in the system's Default apps screen.
 */
object Assistant {

    /** What long-press can open, in the order the setting lists them. Voice quick-add is the default. */
    val ACTIONS: List<GestureAction> = listOf(
        GestureAction.CockpitVoiceAdd,
        GestureAction.CockpitQuickAdd,
        GestureAction.CommandPalette,
        GestureAction.CockpitToday,
        GestureAction.ConductoreSheet,
    )

    private const val FILE = "yoke.assist"
    private const val KEY_ACTION = "action"
    private const val ENTRY = "com.outsmartis.yoke.assist.AssistEntry"

    /** The chosen action, or null when the feature is off. */
    fun action(context: Context): GestureAction? {
        val type = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_ACTION, null) ?: return null
        return ACTIONS.firstOrNull { it.type == type }
    }

    /** [action] null turns the feature off and withdraws Yoke from the assistant list. */
    fun setAction(context: Context, action: GestureAction?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_ACTION, action?.type).apply()
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context.packageName, ENTRY),
            if (action != null) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }

    /** Puts the entry point in step with the saved choice (after a backup import, say). */
    fun reconcile(context: Context) {
        val enabled = context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, ENTRY)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val action = action(context)
        if (enabled != (action != null)) setAction(context, action)
    }

    /** Whether the system currently uses Yoke as its assistant. */
    fun isAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            return runCatching { roles.isRoleHeld(RoleManager.ROLE_ASSISTANT) }.getOrDefault(false)
        }
        val current = runCatching { Settings.Secure.getString(context.contentResolver, "assistant") }.getOrNull()
        return current?.startsWith(context.packageName + "/") == true
    }
}
