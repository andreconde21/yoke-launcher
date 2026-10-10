package com.outsmartis.yoke.backup

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupException(message: String) : Exception(message)

/**
 * All of Yoke's settings as one JSON document:
 * `{ "app":"yoke", "version":1, "exportedAt":..., "prefs": { "<file>": { "<key>": {"t":"b","v":true} } } }`
 * with `t` one of b (boolean), i (int), l (long), f (float), s (string), ss (string set).
 *
 * Device-bound values never travel: the vault's tree URI (the permission is per device), cached
 * readings, timestamps, widget ids and grayscale's "previous daltonizer" state ([EXCLUDED]).
 * The encode/decode half works on plain maps so it is unit-tested without Android.
 */
object YokeBackup {
    const val APP = "yoke"
    const val VERSION = 1

    /** The SharedPreferences files that make up Yoke's settings. */
    val FILES = listOf(
        "com.outsmartis.yoke", // launcher prefs, gestures, links, grayscale
        "yoke.theme",
        "yoke.wallpaper",
        "com.outsmartis.yoke.weather",
        "yoke.cockpit",
        "yoke.search",
        "yoke.backup",
        "yoke.iconrow",
    )

    /** Keys left out of an export and left alone by an import, per file. */
    val EXCLUDED: Map<String, Set<String>> = mapOf(
        "com.outsmartis.yoke" to setOf(
            "WIDGETS_JSON", // AppWidget ids belong to this device
            "SCREEN_TIME_LAST_UPDATED", "LAUNCHER_RECREATE_TIMESTAMP",
            "GRAYSCALE_TAKEN_OVER", "GRAYSCALE_PREV_ENABLED", "GRAYSCALE_PREV_MODE",
            "GRAYSCALE_LAST_PACKAGE", "GRAYSCALE_PAUSED_UNTIL",
        ),
        "yoke.theme" to setOf("conductore_last_known"),
        "com.outsmartis.yoke.weather" to setOf("READING", "USE_CURRENT"), // cache; the location permission is per device
        "yoke.cockpit" to setOf("vault_uri", "agenda_line", "agenda_at"),
        "yoke.backup" to setOf("last_vault_backup", "last_vault_hash"),
    )

    fun isExcluded(file: String, key: String) = key in EXCLUDED[file].orEmpty()

    /** [prefs] maps file name to its entries (values as `SharedPreferences.getAll` gives them). */
    fun encode(prefs: Map<String, Map<String, Any?>>, exportedAt: Long): String {
        val files = JSONObject()
        for (file in FILES) {
            val entries = prefs[file] ?: continue
            val out = JSONObject()
            for ((key, value) in entries.toSortedMap()) {
                if (isExcluded(file, key)) continue
                out.put(key, typed(value) ?: continue)
            }
            files.put(file, out)
        }
        return JSONObject()
            .put("app", APP).put("version", VERSION).put("exportedAt", exportedAt).put("prefs", files)
            .toString(2)
    }

    private fun typed(value: Any?): JSONObject? = when (value) {
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is String -> JSONObject().put("t", "s").put("v", value)
        is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>().sorted()))
        else -> null
    }

    /**
     * Checks [json] is a Yoke backup this build understands and returns its entries per known
     * file, without excluded keys. Throws [BackupException] otherwise.
     */
    fun decode(json: String): Map<String, Map<String, Any>> {
        val root = try { JSONObject(json) } catch (e: JSONException) { throw BackupException("Not a JSON file") }
        if (root.optString("app") != APP) throw BackupException("Not a Yoke backup")
        val version = root.optInt("version", -1)
        if (version < 1) throw BackupException("Unreadable backup version")
        if (version > VERSION) throw BackupException("This backup is from a newer Yoke (version $version)")
        val files = root.optJSONObject("prefs") ?: throw BackupException("Backup has no settings")
        val result = LinkedHashMap<String, Map<String, Any>>()
        for (file in FILES) {
            val entries = files.optJSONObject(file) ?: continue
            val out = LinkedHashMap<String, Any>()
            for (key in entries.keys()) {
                if (isExcluded(file, key)) continue
                val e = entries.optJSONObject(key) ?: continue
                val value: Any = try {
                    when (e.optString("t")) {
                        "b" -> e.getBoolean("v")
                        "i" -> e.getInt("v")
                        "l" -> e.getLong("v")
                        "f" -> e.getDouble("v").toFloat()
                        "s" -> e.getString("v")
                        "ss" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                        else -> continue
                    }
                } catch (ex: JSONException) { continue }
                out[key] = value
            }
            result[file] = out
        }
        return result
    }

    // Android side

    private fun prefsOf(context: Context, file: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)

    /** The settings document for this device. */
    fun export(context: Context, now: Long = System.currentTimeMillis()): String =
        encode(FILES.associateWith { prefsOf(context, it).all }, now)

    /** Same content without the timestamp, to tell whether anything changed since the last copy. */
    fun fingerprint(context: Context): String =
        encode(FILES.associateWith { prefsOf(context, it).all }, 0L).hashCode().toString()

    /**
     * Replaces the settings of every file the backup mentions (except excluded keys). Validates
     * first, so a bad file changes nothing. The caller recreates the activity afterwards.
     */
    fun import(context: Context, json: String) {
        val data = decode(json)
        for ((file, entries) in data) {
            val prefs = prefsOf(context, file)
            val editor = prefs.edit()
            for (key in prefs.all.keys) if (!isExcluded(file, key)) editor.remove(key)
            for ((key, value) in entries) when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            editor.commit()
        }
    }
}
