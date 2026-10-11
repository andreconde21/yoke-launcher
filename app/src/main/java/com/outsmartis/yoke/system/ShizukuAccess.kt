package com.outsmartis.yoke.system

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * Shizuku is optional everywhere in Yoke: features that need it check [state] and say
 * "Needs Shizuku" when it isn't [State.Ready]; nothing else depends on it.
 */
object ShizukuAccess {

    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 4108

    enum class State { NotInstalled, NotRunning, NeedsPermission, Ready }

    fun state(context: Context): State {
        if (!isInstalled(context)) return State.NotInstalled
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return State.NotRunning
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) return State.NotRunning
        return if (hasPermission()) State.Ready else State.NeedsPermission
    }

    fun isReady(context: Context) = state(context) == State.Ready

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun hasPermission() =
        runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    private var pendingListener: Shizuku.OnRequestPermissionResultListener? = null

    /** Asks Shizuku to allow Yoke. [onResult] gets true when allowed, on the main thread. */
    fun requestPermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }
        release()
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                release()
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        pendingListener = listener
        Shizuku.addRequestPermissionResultListener(listener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }.onFailure {
            release()
            onResult(false)
        }
    }

    fun release() {
        pendingListener?.let { runCatching { Shizuku.removeRequestPermissionResultListener(it) } }
        pendingListener = null
    }

    class Result(val exitCode: Int, val output: String) {
        val ok get() = exitCode == 0
    }

    /**
     * Runs [cmd] as the shell user and waits for it. Blocking: call off the main thread.
     * Shizuku.newProcess is private in API 13, so it is reached by reflection.
     */
    fun run(vararg cmd: String): Result {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        val process = method.invoke(null, arrayOf(*cmd), null, null) as Process
        val err = StringBuilder()
        val errReader = thread { process.errorStream.bufferedReader().use { err.append(it.readText()) } }
        val out = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        errReader.join(2000)
        return Result(code, (out + err).trim())
    }
}
