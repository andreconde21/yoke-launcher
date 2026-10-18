package com.outsmartis.yoke.palette

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Constants

/**
 * Entry point for opening the drawer straight into command palette mode,
 * e.g. as the "CommandPalette" gesture action.
 */
object CommandPalette {
    /** Nav argument (Boolean) that starts the drawer in palette mode. */
    const val ARG_PALETTE = "palette"

    /** Nav argument (String): text the search box starts with, e.g. dictated into the home bar. */
    const val ARG_QUERY = "palette_query"

    /** [forceKeyboard] (or a non-empty [query]) brings the keyboard up whatever the auto-show setting says. */
    fun args(query: String = "", forceKeyboard: Boolean = false): Bundle = bundleOf(
        Constants.Key.FLAG to Constants.FLAG_LAUNCH_APP,
        ARG_PALETTE to true,
        ARG_QUERY to query,
        Constants.Key.SEARCH to (forceKeyboard || query.isNotEmpty()),
    )

    /** Opens the palette from the home screen or from anywhere in the nav graph. */
    fun open(navController: NavController, query: String = "", forceKeyboard: Boolean = false) {
        try {
            if (navController.currentDestination?.id == R.id.mainFragment)
                navController.navigate(R.id.action_mainFragment_to_appListFragment, args(query, forceKeyboard))
            else
                navController.navigate(R.id.appListFragment, args(query, forceKeyboard))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
