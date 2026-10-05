package com.outsmartis.yoke.grayscale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.concurrent.thread

/**
 * Grants WRITE_SECURE_SETTINGS to Yoke through Shizuku, so Smart grayscale needs no computer.
 * The only thing ever run through Shizuku is `pm grant <this package> WRITE_SECURE_SETTINGS`;
 * the permission check afterwards is the source of truth.
 */
object ShizukuGrant {

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4107

    enum class State { NotInstalled, NotRunning, NeedsPermission, Ready, Granted }

    /** Ready: Shizuku is running and Yoke may use it. NeedsPermission: running, Yoke not yet allowed. */
    fun state(context: Context): State {
        if (GrayscaleController.hasPermission(context)) return State.Granted
        if (!isInstalled(context)) return State.NotInstalled
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return State.NotRunning
        if (Shizuku.isPreV11()) return State.NotRunning
        return if (hasShizukuPermission()) State.Ready else State.NeedsPermission
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun hasShizukuPermission() =
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    private var pendingListener: Shizuku.OnRequestPermissionResultListener? = null

    /** Remove any listener still registered. Call from onDestroyView. */
    fun release() {
        pendingListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
        pendingListener = null
    }

    /** onResult(null) on success, otherwise an error message. Always called on the main thread. */
    fun requestAndGrant(context: Context, onResult: (error: String?) -> Unit) {
        val app = context.applicationContext
        if (hasShizukuPermission()) {
            grantAsync(app, onResult)
            return
        }
        release()
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                release()
                if (grantResult == PackageManager.PERMISSION_GRANTED) grantAsync(app, onResult)
                else onResult("denied")
            }
        }
        pendingListener = listener
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }.onFailure {
            release()
            onResult(it.message ?: it.javaClass.simpleName)
        }
    }

    private fun grantAsync(context: Context, onResult: (String?) -> Unit) {
        thread(name = "shizuku-grant") {
            val output = StringBuilder()
            val viaProcess = runCatching { runPmGrant(context.packageName, output) }
            if (viaProcess.isFailure) {
                output.append(viaProcess.exceptionOrNull()?.message ?: "newProcess unavailable").append('\n')
                runCatching { grantViaBinder(context.packageName) }
                    .onFailure { output.append(it.message ?: it.javaClass.simpleName) }
            }
            val granted = GrayscaleController.hasPermission(context)
            Handler(Looper.getMainLooper()).post {
                onResult(if (granted) null else output.toString().trim().ifEmpty { "permission still denied" })
            }
        }
    }

    /** Shizuku.newProcess is private in API 13, so reach it by reflection. Returns when pm has exited. */
    private fun runPmGrant(packageName: String, output: StringBuilder) {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val cmd = arrayOf("pm", "grant", packageName, Manifest.permission.WRITE_SECURE_SETTINGS)
        val process = method.invoke(null, cmd, null, null) as Process
        val err = StringBuilder()
        val errReader = thread { process.errorStream.bufferedReader().use { err.append(it.readText()) } }
        output.append(process.inputStream.bufferedReader().use { it.readText() })
        val code = process.waitFor()
        errReader.join(2000)
        output.append(err)
        if (code != 0) output.append("(exit $code)")
    }

    /** Fallback: IPackageManager.grantRuntimePermission as the shell user, over Shizuku's binder. */
    private fun grantViaBinder(packageName: String) {
        val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
        val binder: IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
        val pm = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        pm!!.javaClass.getMethod(
            "grantRuntimePermission", String::class.java, String::class.java, Int::class.javaPrimitiveType
        ).invoke(pm, packageName, Manifest.permission.WRITE_SECURE_SETTINGS, 0)
    }
}
