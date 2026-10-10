package com.outsmartis.yoke.cockpit

import org.json.JSONObject
import java.time.LocalDate

/**
 * A column of the Obsidian Cockpit Board plugin, as stored in its settings
 * (`.obsidian/plugins/cockpit-board/data.json`, `columns`). Mirrors
 * `ColumnConfig` in cockpit-board's src/types.ts.
 */
data class CockpitColumn(val id: String, val label: String, val color: String, val rule: String?)

/**
 * The part of the plugin's settings quick-add needs: the cards folder
 * (vault-relative, "" for the vault root) and the board's columns.
 */
data class CockpitBoardConfig(
    val folder: String,
    val columns: List<CockpitColumn>,
    /** The plugin's `labelColors`: only labels the user gave a custom colour. */
    val labelColors: Map<String, String> = emptyMap(),
    /** The plugin's `clearDateOnInProgress` (default on): starting a card without a time clears its due date. */
    val clearDateOnInProgress: Boolean = true,
) {

    companion object {
        /** The plugin's DEFAULT_COLUMNS (src/constants.ts), for a vault whose data.json has none. */
        val DEFAULT_COLUMNS = listOf(
            CockpitColumn("backlog", "Backlog", "#778CA3", "no-date"),
            CockpitColumn("scheduled", "Scheduled", "#45B7D1", "date:future"),
            CockpitColumn("soon", "Soon", "#F7B731", "date:tomorrow"),
            CockpitColumn("today", "Today", "#FC5C65", "date:today"),
            CockpitColumn("in-progress", "In Progress", "#0079BF", "status:in-progress"),
            CockpitColumn("done", "Done", "#61BD4F", "status:done"),
        )

        /** Parses data.json; missing fields fall back to the plugin's defaults. */
        fun parse(json: String): CockpitBoardConfig {
            val root = JSONObject(json)
            val columnsJson = root.optJSONArray("columns")
            val columns = if (columnsJson == null || columnsJson.length() == 0) DEFAULT_COLUMNS
            else (0 until columnsJson.length()).map { i ->
                val c = columnsJson.getJSONObject(i)
                CockpitColumn(
                    id = c.optString("id"),
                    label = c.optString("label", c.optString("id")),
                    color = c.optString("color", "#778CA3"),
                    rule = if (c.isNull("rule")) null else c.optString("rule"),
                )
            }
            val colours = root.optJSONObject("labelColors")
            val labelColors = colours?.keys()?.asSequence()?.associateWith { colours.optString(it) }.orEmpty()
            return CockpitBoardConfig(root.optString("folder", "").trim('/'), columns, labelColors, root.optBoolean("clearDateOnInProgress", true))
        }
    }
}

/** Frontmatter a column gives a new card, as the plugin's `getDropUpdates` (src/rule-engine.ts) does. */
data class ColumnFields(val status: String, val due: String, val label: String?)

object CockpitCards {

    /**
     * `getDropUpdates(col, { due: "", labels: [] })` for a brand-new card:
     * no existing due date, no forced date. Keep in step with rule-engine.ts.
     */
    fun fieldsFor(column: CockpitColumn, today: LocalDate): ColumnFields {
        val rule = column.rule.orEmpty()
        return when {
            rule.contains("status:") -> {
                val status = Regex("""status:(\S+)""").find(rule)?.groupValues?.get(1) ?: column.id
                ColumnFields(status, "", null)
            }
            rule.contains("date:today") -> ColumnFields("scheduled", today.toString(), null)
            rule.contains("date:tomorrow") -> ColumnFields("scheduled", today.plusDays(1).toString(), null)
            rule.contains("date:future") -> ColumnFields("scheduled", "", null)
            rule.contains("no-date") -> {
                val label = if (rule.contains("label:") && !rule.contains("NOT label:"))
                    Regex("""label:(\S+)""").find(rule)?.groupValues?.get(1) else null
                ColumnFields("", "", label)
            }
            rule.isEmpty() -> ColumnFields(column.id, "", null)
            else -> ColumnFields("", "", null)
        }
    }

    /** The label a column's rule adds to a card dropped there (`no-date label:work` → work), or null. */
    fun columnLabel(column: CockpitColumn): String? = fieldsFor(column, LocalDate.of(2000, 1, 1)).label

    /**
     * The `labels` of a card from its text: `labels: ["a", "b"]`, `labels: [a, b]` or a YAML
     * list under `labels:`. Only the frontmatter is looked at; anything unparseable gives none.
     */
    fun parseLabels(text: String): List<String> {
        val lines = text.lineSequence().toList()
        if (lines.firstOrNull()?.trim() != "---") return emptyList()
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }.let { if (it < 0) lines.size else it + 1 }
        val fm = lines.subList(1, end)
        val i = fm.indexOfFirst { it.startsWith("labels:") }
        if (i < 0) return emptyList()
        val inline = fm[i].removePrefix("labels:").trim()
        val raw = if (inline.startsWith("[")) inline.trim('[', ']').split(',')
        else if (inline.isNotEmpty()) listOf(inline)
        else fm.drop(i + 1).takeWhile { it.trimStart().startsWith("- ") }.map { it.trimStart().removePrefix("- ") }
        return raw.map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
    }

    /** The column quick-add starts on: "Pending" if the board has one, else the first plain column without a label rule. */
    fun defaultColumn(columns: List<CockpitColumn>): CockpitColumn? =
        columns.firstOrNull { it.id.equals("pending", true) || it.label.equals("pending", true) }
            ?: columns.firstOrNull { !isDateColumn(it) && columnLabel(it) == null && it.rule?.contains("status:") != true }
            ?: columns.firstOrNull()

    /** The board column a due date lands in: today → date:today, tomorrow → date:tomorrow, later → date:future. */
    fun columnForDue(columns: List<CockpitColumn>, due: LocalDate, today: LocalDate): CockpitColumn? {
        val rule = when {
            !due.isAfter(today) -> "date:today"
            due == today.plusDays(1) -> "date:tomorrow"
            else -> "date:future"
        }
        return columns.firstOrNull { it.rule.orEmpty().contains(rule) }
    }

    /** Columns placed by a due date (Today, Soon, Scheduled); quick-add sets the date directly instead. */
    fun isDateColumn(column: CockpitColumn): Boolean = column.rule.orEmpty().contains("date:")

    /**
     * A column's fields with the due date the user picked. A date makes a
     * status-less card "scheduled" (the plugin then files it by date); a
     * status column (e.g. In Progress) keeps its status. Null keeps the column's own fields.
     */
    fun withDue(fields: ColumnFields, due: LocalDate?): ColumnFields =
        if (due == null) fields else fields.copy(status = fields.status.ifEmpty { "scheduled" }, due = due.toString())

    /**
     * The file name stem the plugin's `createCardInColumn` uses. One
     * deviation: a title with no latin letters or digits gives "card"
     * instead of a bare ".md".
     */
    fun slug(title: String): String =
        title.lowercase()
            .replace(Regex("""[^a-z0-9\s-]"""), "")
            .replace(Regex("""\s+"""), "-")
            .take(60)
            .ifEmpty { "card" }

    /** Vault-relative path for a new card: `<folder>/<slug>.md`, then `-1`, `-2`, ... */
    fun pathFor(folder: String, title: String, exists: (String) -> Boolean): String {
        val dir = if (folder.isEmpty()) "" else "$folder/"
        val slug = slug(title)
        var path = "$dir$slug.md"
        var i = 1
        while (exists(path)) { path = "$dir$slug-$i.md"; i++ }
        return path
    }

    /** The exact file body `createCardInColumn` writes, with `source: yoke`; [extraLabels] join the column's label. */
    fun content(title: String, fields: ColumnFields, today: LocalDate, extraLabels: List<String> = emptyList()): String {
        val all = (extraLabels + listOfNotNull(fields.label)).distinct()
        val labels = all.joinToString(", ", "[", "]") { "\"" + it.replace("\"", "\\\"") + "\"" }
        val escaped = title.replace("\"", "\\\"")
        // Straight into Done: completed is today, as getDropUpdates sets it.
        val completed = if (fields.status == "done") " $today" else ""
        return "---\ntitle: \"$escaped\"\nstatus: ${fields.status}\ndue: ${fields.due}\ntime:\ncompleted:$completed\nproject:\n" +
            "labels: $labels\ncreated: $today\nsource: yoke\n---\n\n# $title\n"
    }
}
