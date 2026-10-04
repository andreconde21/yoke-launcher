package com.outsmartis.yoke.helper

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.LinkEntry
import com.outsmartis.yoke.data.Prefs

/** Add / edit / delete dialogs for the drawer's web links. */
object LinkDialogs {

    /** Lists every link; tapping one edits it, the action adds a new one. */
    fun showList(context: Context, prefs: Prefs, onChanged: () -> Unit = {}) {
        val links = prefs.links
        val dialog = context.createDialog(
            title = R.string.web_links,
            action = R.string.add_link,
            onAction = { showEdit(context, prefs, null, onChanged = onChanged) },
        ) { container ->
            LinearLayout(container.context).apply {
                orientation = LinearLayout.VERTICAL
                if (links.isEmpty()) addView(textRow(context, context.getString(R.string.no_links), 0.5f))
                links.forEach { link ->
                    addView(textRow(context, "${link.name}  ↗", 1f).apply {
                        setOnClickListener {
                            showEdit(context, prefs, link, onChanged = onChanged)
                        }
                    })
                }
            }
        }
        dialog.showRespectingStatusBar()
    }

    /**
     * Edits [existing], or adds a new link when null. [name] and [url] prefill the fields
     * for a new link (the command palette passes whatever was typed).
     */
    fun showEdit(
        context: Context,
        prefs: Prefs,
        existing: LinkEntry?,
        name: String = "",
        url: String = "",
        onChanged: () -> Unit = {},
    ) {
        var nameField: EditText? = null
        var urlField: EditText? = null
        val dialog = context.createDialog(
            title = if (existing == null) R.string.add_link else R.string.edit_link,
            action = R.string.save,
            neutral = if (existing != null) R.string.link_remove else 0,
            onNeutral = {
                prefs.links = prefs.links.filter { it.id != existing?.id }
                onChanged()
            },
            onAction = {
                val newName = nameField?.text?.toString()?.trim().orEmpty()
                val newUrl = LinkEntry.normalizeUrl(urlField?.text?.toString().orEmpty())
                if (newName.isEmpty() || newUrl == null) {
                    context.showToast(R.string.link_invalid)
                } else {
                    val entry = LinkEntry(existing?.id ?: java.util.UUID.randomUUID().toString(), newName, newUrl)
                    val current = prefs.links
                    prefs.links =
                        if (existing == null) current + entry
                        else current.map { if (it.id == entry.id) entry else it }
                    onChanged()
                }
            },
        ) { container ->
            LinearLayout(container.context).apply {
                orientation = LinearLayout.VERTICAL
                nameField = field(context, R.string.link_name, existing?.name ?: name, InputType.TYPE_CLASS_TEXT)
                urlField = field(
                    context, R.string.link_url, existing?.url ?: url,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                )
                addView(nameField)
                addView(urlField)
            }
        }
        dialog.showRespectingStatusBar()
    }

    private fun textRow(context: Context, text: String, alpha: Float) = TextView(context).apply {
        this.text = text
        this.alpha = alpha
        setTextAppearance(R.style.TextSmall)
        setTextColor(context.getColorFromAttr(R.attr.primaryColor))
        setPadding(0, dp(context, 10), 0, dp(context, 10))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun field(context: Context, hint: Int, value: String, type: Int) = EditText(context).apply {
        setHint(hint)
        setText(value)
        inputType = type
        maxLines = 1
        setTextColor(context.getColorFromAttr(R.attr.primaryColor))
        setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 8) }
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
