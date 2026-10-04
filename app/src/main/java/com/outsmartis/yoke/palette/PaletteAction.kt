package com.outsmartis.yoke.palette

import android.content.Context
import androidx.navigation.NavController
import com.outsmartis.yoke.MainViewModel

/** Everything an action may need to do its job. [reload] re-reads the drawer's list (links changed...). */
class PaletteContext(
    val context: Context,
    val navController: NavController,
    val viewModel: MainViewModel,
    val reload: () -> Unit,
)

/**
 * One entry of the `>` palette. [label] is what the user searches and sees;
 * [run] executes it. The drawer closes afterwards unless [closeDrawer] is false.
 */
class PaletteAction(
    val id: String,
    val label: String,
    val closeDrawer: Boolean = true,
    val run: (PaletteContext) -> Unit,
)

/**
 * Registry of palette actions. Other parts of the app register theirs
 * (`PaletteActions.register(PaletteAction("theme", "Theme picker") { ... })`);
 * registering an id again replaces the earlier entry, so it is safe to call repeatedly.
 */
object PaletteActions {
    private val actions = LinkedHashMap<String, PaletteAction>()

    @Synchronized
    fun register(action: PaletteAction) {
        actions[action.id] = action
    }

    @Synchronized
    fun get(id: String): PaletteAction? = actions[id]

    @Synchronized
    fun all(): List<PaletteAction> = actions.values.toList()

    @Synchronized
    fun clear() = actions.clear()

    /** Actions whose label matches [query]; everything for a blank query. */
    fun search(query: String): List<PaletteAction> {
        val all = all()
        return if (query.isBlank()) all else all.filter { AppSearch.labelMatches(it.label, query) }
    }
}
