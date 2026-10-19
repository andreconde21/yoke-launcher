package com.outsmartis.yoke

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rule in [com.outsmartis.yoke.helper.HomeReturn]: MainActivity must not pop to the home screen
 * from onStop or onUserLeaveHint (that destroyed screens waiting for a picker or permission result).
 * It reads the source because the activity itself cannot run on the JVM.
 */
class MainActivityHomeResetTest {

    private val source = listOf("src/main/java/com/outsmartis/yoke/MainActivity.kt", "app/src/main/java/com/outsmartis/yoke/MainActivity.kt")
        .map(::File).first { it.exists() }.readText()

    private fun body(fn: String): String {
        val start = source.indexOf("override fun $fn(")
        assertTrue("$fn missing", start >= 0)
        val next = Regex("\\n    (private |override |@)").find(source, start + 10)?.range?.first ?: source.length
        return source.substring(start, next)
    }

    @Test
    fun onStopNeverPopsDirectly() {
        assertTrue(!body("onStop").contains("backToHomeScreen()"))
        assertTrue(body("onStop").contains("HomeReturn.resetOnReturn"))
    }

    @Test
    fun noLeaveHintPop() {
        assertTrue(!source.contains("override fun onUserLeaveHint"))
    }

    @Test
    fun theOnlyCallersAreOnStartAndOnNewIntent() {
        val callers = Regex("backToHomeScreen\\(\\)").findAll(source).count() - 1 // minus the definition
        assertEquals(2, callers)
    }

    @Test
    fun theOverrideDecidesFromTheRequestCodeNotFromWantsResult() {
        val b = body("startActivityForResult")
        assertTrue(b.contains("HomeReturn.holdsScreen(requestCode"))
        assertTrue(!source.contains("override fun startActivity(")) // would hold every launch via -1
    }

    @Test
    fun aHomeIntentCarryingTheHoldExtraIsYokesOwnJumpAndDoesNotPop() {
        val b = body("onNewIntent")
        val guard = b.indexOf("HomeReturn.EXTRA_HOLD")
        assertTrue(guard >= 0)
        assertTrue(guard < b.indexOf("backToHomeScreen()")) // before the pop
    }
}
