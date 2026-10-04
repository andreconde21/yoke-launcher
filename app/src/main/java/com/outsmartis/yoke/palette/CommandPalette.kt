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

    fun args(): Bundle = bundleOf(
        Constants.Key.FLAG to Constants.FLAG_LAUNCH_APP,
        ARG_PALETTE to true,
    )

    /** Opens the palette from the home screen or from anywhere in the nav graph. */
    fun open(navController: NavController) {
        try {
            if (navController.currentDestination?.id == R.id.mainFragment)
                navController.navigate(R.id.action_mainFragment_to_appListFragment, args())
            else
                navController.navigate(R.id.appListFragment, args())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
