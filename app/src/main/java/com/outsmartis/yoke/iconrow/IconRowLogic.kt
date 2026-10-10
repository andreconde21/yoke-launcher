package com.outsmartis.yoke.iconrow

import org.json.JSONArray
import org.json.JSONObject

/** One app on the icon row. [user] is `UserHandle.toString()`, the format the rest of Yoke stores. */
data class IconSlot(val packageName: String, val activityClassName: String?, val user: String)

enum class IconStyle(val id: String) {
    MONOCHROME("mono"), ORIGINAL("original");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: MONOCHROME
    }
}

enum class IconSize(val id: String, val dp: Int) {
    SMALL("small", 26), MEDIUM("medium", 32), LARGE("large", 42);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class IconPosition(val id: String) {
    BOTTOM("bottom"), UNDER_CLOCK("under_clock");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: BOTTOM
    }
}

/** The ordered list of slots: JSON codec and edits. Pure, so it is unit tested. */
object IconSlots {
    const val MAX = 6

    fun encode(slots: List<IconSlot>): String = JSONArray().also { arr ->
        slots.take(MAX).forEach { s ->
            arr.put(JSONObject().apply {
                put("package", s.packageName)
                s.activityClassName?.let { put("activity", it) }
                put("user", s.user)
            })
        }
    }.toString()

    /** Tolerant: null, blank or broken JSON gives an empty list; entries without a package are dropped. */
    fun decode(json: String?): List<IconSlot> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pkg = o.optString("package", "")
                if (pkg.isBlank()) null
                else IconSlot(pkg, o.optString("activity", "").ifBlank { null }, o.optString("user", ""))
            }.take(MAX)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Moves the slot at [index] by [delta] places (-1 up, +1 down); out of range is a no-op. */
    fun move(slots: List<IconSlot>, index: Int, delta: Int): List<IconSlot> {
        val to = index + delta
        if (index !in slots.indices || to !in slots.indices) return slots
        return slots.toMutableList().apply { add(to, removeAt(index)) }
    }

    fun remove(slots: List<IconSlot>, index: Int): List<IconSlot> =
        if (index in slots.indices) slots.filterIndexed { i, _ -> i != index } else slots

    /** Sets slot [index]; an index past the end appends (while there is room). */
    fun set(slots: List<IconSlot>, index: Int, slot: IconSlot): List<IconSlot> = when {
        index in slots.indices -> slots.toMutableList().also { it[index] = slot }
        slots.size < MAX -> slots + slot
        else -> slots
    }
}

/** Fallback for apps without a monochrome layer: turn a coloured icon into an alpha mask. */
object IconMask {
    /** Below this share of the square covered the mask is nearly empty; above [MAX_COVERAGE] nearly full. */
    const val MIN_COVERAGE = 0.04
    const val MAX_COVERAGE = 0.85
    private const val OPAQUE = 16

    private fun lum(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    }

    /** Alpha 0..255 for one pixel: its own alpha times its luminance (bright = drawn), or inverted. */
    fun alphaOf(argb: Int, invert: Boolean = false): Int {
        val a = (argb ushr 24) and 0xFF
        val l = lum(argb)
        return Math.round(a * (if (invert) 1.0 - l else l)).toInt().coerceIn(0, 255)
    }

    /**
     * Pixels (ARGB) to a mask tinted with [tint]'s RGB, or null when the result would be unreadable.
     * Light glyphs on a darker tile are drawn as is; when most of the opaque area is bright the
     * icon is the other way round (dark glyph on light), so the mask is inverted.
     */
    fun toMask(pixels: IntArray, tint: Int): IntArray? {
        if (pixels.isEmpty()) return null
        var opaque = 0
        var brightSum = 0.0
        for (p in pixels) if (((p ushr 24) and 0xFF) >= OPAQUE) {
            opaque++
            brightSum += lum(p)
        }
        if (opaque == 0) return null
        val invert = brightSum / opaque > 0.5
        val rgb = tint and 0x00FFFFFF
        var coverage = 0.0
        val out = IntArray(pixels.size) { i ->
            val a = alphaOf(pixels[i], invert)
            coverage += a / 255.0
            (a shl 24) or rgb
        }
        return if (isReadable(coverage / pixels.size)) out else null
    }

    fun isReadable(coverage: Double) = coverage in MIN_COVERAGE..MAX_COVERAGE
}
