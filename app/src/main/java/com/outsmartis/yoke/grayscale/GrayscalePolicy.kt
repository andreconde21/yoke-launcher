package com.outsmartis.yoke.grayscale

import org.json.JSONArray
import org.json.JSONException

/** Colour-correction state as Settings.Secure holds it. [mode] null means "leave the mode alone". */
data class DaltonizerState(val enabled: Boolean, val mode: Int?) {
    val isMonochrome: Boolean get() = enabled && mode == MONOCHROMACY

    companion object {
        const val MONOCHROMACY = 0
        val GRAYSCALE = DaltonizerState(true, MONOCHROMACY)
        val COLOUR = DaltonizerState(false, null)
    }
}

data class GrayscaleDecision(val grayscale: Boolean, val rememberedPackage: String?)

/** Pure rules for when the screen is grayscale and what to write. No Android types. */
object GrayscalePolicy {

    /**
     * [foregroundPackage] is the package of the newest window event, null when unknown. A package in
     * [ignoredPackages] (system UI, keyboards) is transient and keeps the previous decision by falling
     * back to [lastPackage]. With no known package at all the phone stays grayscale.
     */
    fun decide(
        featureOn: Boolean,
        pausedUntil: Long,
        now: Long,
        foregroundPackage: String?,
        exceptions: Set<String>,
        ignoredPackages: Set<String>,
        lastPackage: String?,
    ): GrayscaleDecision {
        val usable = foregroundPackage?.takeIf { it.isNotBlank() && it !in ignoredPackages }
        val pkg = usable ?: lastPackage
        val colour = !featureOn || isPaused(pausedUntil, now) || (pkg != null && pkg in exceptions)
        return GrayscaleDecision(grayscale = !colour, rememberedPackage = pkg)
    }

    fun isPaused(pausedUntil: Long, now: Long): Boolean = pausedUntil > now

    /** What to remember as "the user's own setting" when Yoke takes over for the first time. */
    fun previousToRemember(current: DaltonizerState): DaltonizerState =
        if (current.enabled && current.mode != DaltonizerState.MONOCHROMACY) current else DaltonizerState.COLOUR

    /**
     * The state to write, or null when nothing needs writing. [previous] is the remembered user state;
     * null means Yoke has not taken over yet, so colour must not touch a correction the user set.
     */
    fun resolve(grayscale: Boolean, current: DaltonizerState, previous: DaltonizerState?): DaltonizerState? {
        val target = when {
            grayscale -> DaltonizerState.GRAYSCALE
            previous == null -> if (current.isMonochrome) DaltonizerState.COLOUR else return null
            previous.enabled && previous.mode != DaltonizerState.MONOCHROMACY -> previous
            else -> DaltonizerState.COLOUR
        }
        val same = current.enabled == target.enabled &&
                (!target.enabled || target.mode == null || current.mode == target.mode)
        return if (same) null else target
    }
}

/** The exception list as stored in prefs: a JSON array of package names. */
object PackageSetCodec {
    fun encode(packages: Set<String>): String =
        JSONArray().also { arr -> packages.sorted().forEach { arr.put(it) } }.toString()

    /** Null for missing or unreadable text, so callers can fall back to the defaults. */
    fun decode(text: String?): Set<String>? {
        if (text.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }.toSet()
        } catch (e: JSONException) {
            null
        }
    }
}
