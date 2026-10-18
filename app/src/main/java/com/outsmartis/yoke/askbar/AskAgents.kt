package com.outsmartis.yoke.askbar

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.provider.Settings
import com.outsmartis.yoke.R
import com.outsmartis.yoke.details.DetailsSheet
import com.outsmartis.yoke.helper.openUrl
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.palette.PaletteQuery

/**
 * Hands the Ask row's text to the chosen agent. Yoke runs no model itself.
 *
 * What actually carries the text:
 * - Conductore: its "Send to agent" share target (ACTION_SEND text/plain) puts it in the active
 *   session's chat composer. If that fails, the Conductore sheet opens with the text on the clipboard.
 * - Phone assistant: Android has no public "ask the assistant this text" intent. ACTION_ASSIST and
 *   ACTION_VOICE_COMMAND take no query. So: ACTION_WEB_SEARCH with [SearchManager.QUERY] sent to the
 *   assistant's own package when it handles it (the Google app does, which is where Gemini lives on
 *   most phones); otherwise ACTION_ASSIST in keyboard mode with the text on the clipboard to paste;
 *   with no assistant at all, a plain web search.
 */
object AskAgents {

    fun conductoreInstalled(context: Context): Boolean = runCatching {
        val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcher.getActivityList(DetailsSheet.CONDUCTORE_PACKAGE, android.os.Process.myUserHandle()).isNotEmpty()
    }.getOrDefault(false)

    fun label(context: Context, agent: AskAgent): String = context.getString(
        when (agent) {
            AskAgent.PHONE_ASSISTANT -> R.string.ask_agent_assistant
            AskAgent.CONDUCTORE -> R.string.ask_agent_conductore
            AskAgent.NONE -> R.string.ask_agent_none
        }
    )

    /** [context] should be the activity: the Conductore fallback shows a sheet on it. */
    fun ask(context: Context, agent: AskAgent, text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        when (AskBarLogic.effective(agent, conductoreInstalled(context))) {
            AskAgent.CONDUCTORE -> askConductore(context, query)
            AskAgent.PHONE_ASSISTANT -> askAssistant(context, query)
            AskAgent.NONE -> {}
        }
    }

    private fun askConductore(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .setPackage(DetailsSheet.CONDUCTORE_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, send)) return
        copy(context, text)
        context.showToast(R.string.ask_copied)
        DetailsSheet.showConductore(context)
    }

    private fun askAssistant(context: Context, text: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val assistant = assistantPackage(context)
        if (assistant != null && start(context, Intent(search).setPackage(assistant))) return
        val assist = Intent(Intent.ACTION_ASSIST)
            .putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (assistant != null) assist.setPackage(assistant)
        copy(context, text)
        if (start(context, assist)) {
            context.showToast(R.string.ask_copied)
            return
        }
        if (!start(context, search)) runCatching { context.openUrl(PaletteQuery.webSearchUrl(text)) }
    }

    /**
     * The default assistant's package: the "assistant" secure setting when readable, else whoever
     * answers ACTION_ASSIST. Null when unknown, ambiguous (the chooser) or Yoke itself.
     */
    private fun assistantPackage(context: Context): String? {
        val fromSetting = runCatching { Settings.Secure.getString(context.contentResolver, "assistant") }.getOrNull()
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }
        val pkg = fromSetting ?: runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_ASSIST), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull()
        return pkg?.takeIf { it != "android" && it != context.packageName }
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess

    private fun copy(context: Context, text: String) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ask", text))
        }
    }
}
