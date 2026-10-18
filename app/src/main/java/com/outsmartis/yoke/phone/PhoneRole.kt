package com.outsmartis.yoke.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager

/**
 * Optional: Yoke as the phone's default phone app (ROLE_DIALER). Off by default: the InCallService
 * and the dial activities are disabled components, so Yoke isn't even offered in Default apps →
 * Phone until the user turns it on in Settings (the same pattern as the assistant, see Assistant).
 * They run in their own `:phone` process, so a launcher crash can't take a call screen down.
 *
 * Turning it off can't take the role away (only the system's Default apps can), so the components
 * stay enabled until another phone app holds the role; [reconcile] disables them then.
 */
object PhoneRole {

    private const val FILE = "yoke.phone"
    private const val KEY_WANTED = "wanted"
    private val COMPONENTS = listOf(
        "com.outsmartis.yoke.phone.YokeInCallService",
        "com.outsmartis.yoke.phone.DialActivity",
        "com.outsmartis.yoke.phone.InCallActivity",
    )

    /** Whether the user turned the setting on. */
    fun wanted(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_WANTED, false)

    /** On enables the components (then ask for the role with [requestIntent]); off disables them once the role is gone. */
    fun setWanted(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WANTED, on).apply()
        if (on) setComponentsEnabled(context, true) else reconcile(context)
    }

    /** Puts the components in step with the setting and the role. Cheap: runs whenever home or Settings resumes. */
    fun reconcile(context: Context) {
        val enabled = componentsEnabled(context)
        val wanted = wanted(context)
        when {
            // Restored from another phone's backup: the role didn't travel, so neither does the opt-in.
            wanted && !enabled ->
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WANTED, false).apply()
            !wanted && enabled && !isRoleHeld(context) -> setComponentsEnabled(context, false)
        }
    }

    private fun componentsEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName, COMPONENTS[0])) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun setComponentsEnabled(context: Context, on: Boolean) {
        for (name in COMPONENTS) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, name),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    /** Whether Yoke is the phone app right now. */
    fun isRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            return runCatching { roles.isRoleHeld(RoleManager.ROLE_DIALER) }.getOrDefault(false)
        }
        return defaultDialer(context) == context.packageName
    }

    /** The current phone app's name, for "how to switch back"; null when none is set. */
    fun holderLabel(context: Context): String? {
        val pkg = defaultDialer(context) ?: return null
        val pm = context.packageManager
        return runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
    }

    private fun defaultDialer(context: Context): String? =
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    /** The system dialog that makes Yoke the phone app, or null when this phone doesn't offer the role. */
    fun requestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
            return roles.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }

    /** Default apps, where the user picks another phone app to switch back. */
    fun openDefaultApps(context: Context) {
        val intents = listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        for (i in intents) {
            if (runCatching { context.startActivity(i) }.isSuccess) return
        }
    }

    /**
     * Asked once the role is granted, never before. The role grants the phone group itself;
     * notifications (the incoming-call screen's full-screen intent) are not part of it.
     */
    fun missingPermissions(context: Context): Array<String> {
        val needed = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) needed += Manifest.permission.POST_NOTIFICATIONS
        return needed.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }
}
