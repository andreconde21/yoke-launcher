package com.outsmartis.yoke.details

/**
 * Pure model of the "launcher details provider" contract (see Conductore's
 * docs/launcher-details-provider.md): rows, mapping from a row source,
 * ordering and the text shown in the sheet's header. No Android types, so it is unit tested.
 */
enum class ItemState(val wire: String) {
    Working("working"), NeedsInput("needsInput"), Blocked("blocked"),
    Finished("finished"), Idle("idle"), Unknown("unknown");

    val urgent: Boolean get() = this == NeedsInput || this == Blocked

    companion object {
        /** Anything the provider sends that we do not know (or null) is [Unknown]. */
        fun parse(wire: String?): ItemState = entries.firstOrNull { it.wire == wire } ?: Unknown
    }
}

data class DetailsItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val state: ItemState,
    /** 0..100, or null when unknown (the provider sends -1). */
    val progress: Int?,
    val updatedAt: Long,
    val deepLink: String?,
)

data class DetailsSummary(
    val monitoring: Boolean,
    val attentionCount: Int,
    val updatedAt: Long,
    /** 0..100, or null when unknown. */
    val limit5hPct: Int?,
    val limit7dPct: Int?,
)

data class AppDetails(val authority: String, val summary: DetailsSummary?, val items: List<DetailsItem>)

/** One row of a provider cursor; null for a missing column or a NULL value. */
interface RowSource {
    fun string(column: String): String?
    fun int(column: String): Int?
    fun long(column: String): Long?
}

object DetailsMapper {

    fun item(row: RowSource): DetailsItem = DetailsItem(
        id = row.string("id").orEmpty(),
        title = row.string("title").orEmpty(),
        subtitle = row.string("subtitle")?.takeIf { it.isNotBlank() },
        state = ItemState.parse(row.string("state")),
        progress = row.int("progress")?.takeIf { it in 0..100 },
        updatedAt = row.long("updated_at") ?: 0L,
        deepLink = row.string("deep_link")?.takeIf { it.isNotBlank() },
    )

    fun summary(row: RowSource): DetailsSummary = DetailsSummary(
        monitoring = row.int("monitoring") == 1,
        attentionCount = row.int("attention_count") ?: 0,
        updatedAt = row.long("updated_at") ?: 0L,
        limit5hPct = row.int("limit_5h_pct")?.takeIf { it in 0..100 },
        limit7dPct = row.int("limit_7d_pct")?.takeIf { it in 0..100 },
    )

    /** Urgent first, then newest first. Stable, so ties keep the provider's order. */
    fun order(items: List<DetailsItem>): List<DetailsItem> =
        items.sortedWith(compareByDescending<DetailsItem> { it.state.urgent }.thenByDescending { it.updatedAt })
}

object DetailsText {

    /** "3 agents · 1 needs you · 5h 42% · 7d 10%". Empty when nothing is monitored. */
    fun summaryLine(summary: DetailsSummary?, itemCount: Int): String {
        if (summary == null || !summary.monitoring) return ""
        val parts = mutableListOf<String>()
        parts += if (itemCount == 1) "1 agent" else "$itemCount agents"
        if (summary.attentionCount > 0) parts += "${summary.attentionCount} needs you"
        summary.limit5hPct?.let { parts += "5h $it%" }
        summary.limit7dPct?.let { parts += "7d $it%" }
        return parts.joinToString(" · ")
    }

    /** "now", "5m ago", "3h ago", "2d ago". Empty when the time is unknown (0). */
    fun relativeTime(then: Long, now: Long): String {
        if (then <= 0L) return ""
        val s = ((now - then) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "now"
            s < 3600 -> "${s / 60}m ago"
            s < 86400 -> "${s / 3600}h ago"
            else -> "${s / 86400}d ago"
        }
    }

    fun updated(then: Long, now: Long): String =
        relativeTime(then, now).let { if (it.isEmpty()) "" else if (it == "now") "updated now" else "updated $it" }
}
