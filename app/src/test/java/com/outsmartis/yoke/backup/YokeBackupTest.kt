package com.outsmartis.yoke.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YokeBackupTest {

    private val main = "com.outsmartis.yoke"

    @Test fun typedValuesRoundTrip() {
        val prefs = mapOf(
            main to mapOf<String, Any?>(
                "b" to true, "i" to 7, "l" to 1_700_000_000_000L, "f" to 1.1f,
                "s" to "héllo \"quoted\"", "set" to setOf("b", "a", "c"), "empty" to emptySet<String>(),
            ),
            "yoke.theme" to mapOf<String, Any?>("theme_id" to "nord"),
        )
        val decoded = YokeBackup.decode(YokeBackup.encode(prefs, 123L))
        val m = decoded.getValue(main)
        assertEquals(true, m["b"])
        assertEquals(7, m["i"])
        assertEquals(1_700_000_000_000L, m["l"])
        assertEquals(1.1f, m["f"] as Float, 0f)
        assertEquals("héllo \"quoted\"", m["s"])
        assertEquals(setOf("a", "b", "c"), m["set"])
        assertEquals(emptySet<String>(), m["empty"])
        assertEquals("nord", decoded.getValue("yoke.theme")["theme_id"])
    }

    @Test fun typesAreTaggedInTheDocument() {
        val json = JSONObject(YokeBackup.encode(mapOf(main to mapOf<String, Any?>("x" to 5L, "y" to setOf("q"))), 99L))
        assertEquals("yoke", json.getString("app"))
        assertEquals(1, json.getInt("version"))
        assertEquals(99L, json.getLong("exportedAt"))
        val x = json.getJSONObject("prefs").getJSONObject(main).getJSONObject("x")
        assertEquals("l", x.getString("t"))
        assertEquals("ss", json.getJSONObject("prefs").getJSONObject(main).getJSONObject("y").getString("t"))
    }

    @Test fun secretsAndDeviceBoundValuesAreNotExported() {
        val prefs = mapOf(
            "yoke.cockpit" to mapOf<String, Any?>("vault_uri" to "content://tree/x", "known_labels" to setOf("work")),
            "com.outsmartis.yoke.weather" to mapOf<String, Any?>("READING" to "{}", "UNITS" to "CELSIUS", "USE_CURRENT" to true),
            main to mapOf<String, Any?>(
                "WIDGETS_JSON" to "[1,2]", "GRAYSCALE_TAKEN_OVER" to true, "GRAYSCALE_PREV_MODE" to 2,
                "SCREEN_TIME_LAST_UPDATED" to 5L, "LAUNCHER_RECREATE_TIMESTAMP" to 6L, "HOME_APPS_NUM" to 4,
            ),
            "yoke.backup" to mapOf<String, Any?>("last_vault_backup" to 1L, "keep_vault_copy" to true),
        )
        val text = YokeBackup.encode(prefs, 0L)
        for (secret in listOf("vault_uri", "content://tree", "READING", "USE_CURRENT", "WIDGETS_JSON", "GRAYSCALE_TAKEN_OVER", "GRAYSCALE_PREV_MODE", "SCREEN_TIME_LAST_UPDATED", "LAUNCHER_RECREATE_TIMESTAMP", "last_vault_backup"))
            assertFalse("$secret leaked", text.contains(secret))
        val decoded = YokeBackup.decode(text)
        assertEquals(setOf("work"), decoded.getValue("yoke.cockpit")["known_labels"])
        assertEquals("CELSIUS", decoded.getValue("com.outsmartis.yoke.weather")["UNITS"])
        assertEquals(4, decoded.getValue(main)["HOME_APPS_NUM"])
        assertEquals(true, decoded.getValue("yoke.backup")["keep_vault_copy"])
    }

    @Test fun importIgnoresExcludedKeysAndUnknownFiles() {
        val doc = """{"app":"yoke","version":1,"prefs":{
            "yoke.cockpit":{"vault_uri":{"t":"s","v":"content://evil"},"known_labels":{"t":"ss","v":["a"]}},
            "not.a.yoke.file":{"k":{"t":"b","v":true}}}}"""
        val decoded = YokeBackup.decode(doc)
        assertEquals(setOf("yoke.cockpit"), decoded.keys)
        assertEquals(setOf("known_labels"), decoded.getValue("yoke.cockpit").keys)
    }

    @Test fun malformedEntriesAreSkipped() {
        val doc = """{"app":"yoke","version":1,"prefs":{"$main":{
            "ok":{"t":"i","v":3},"badType":{"t":"zz","v":1},"badValue":{"t":"i","v":"abc"},"notObject":5}}}"""
        assertEquals(mapOf<String, Any>("ok" to 3), YokeBackup.decode(doc).getValue(main))
    }

    private fun rejects(doc: String, containing: String) {
        try {
            YokeBackup.decode(doc)
            fail("accepted: $doc")
        } catch (e: BackupException) {
            assertTrue(e.message, e.message!!.contains(containing))
        }
    }

    @Test fun validatesAppAndVersion() {
        rejects("not json", "JSON")
        rejects("""{"app":"olauncher","version":1,"prefs":{}}""", "Yoke backup")
        rejects("""{"version":1,"prefs":{}}""", "Yoke backup")
        rejects("""{"app":"yoke","prefs":{}}""", "version")
        rejects("""{"app":"yoke","version":0,"prefs":{}}""", "version")
        rejects("""{"app":"yoke","version":2,"prefs":{}}""", "newer")
        rejects("""{"app":"yoke","version":1}""", "no settings")
        assertTrue(YokeBackup.decode("""{"app":"yoke","version":1,"prefs":{}}""").isEmpty())
    }
}
