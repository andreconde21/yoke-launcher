package com.outsmartis.yoke.palette

import android.content.Context
import android.content.Intent
import androidx.core.os.bundleOf
import com.outsmartis.yoke.R
import com.outsmartis.yoke.cockpit.CockpitLinks
import com.outsmartis.yoke.cockpit.QuickAddActivity
import com.outsmartis.yoke.data.Constants
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.grayscale.GrayscaleController
import com.outsmartis.yoke.helper.LinkDialogs
import com.outsmartis.yoke.helper.showToast
import com.outsmartis.yoke.theme.ThemePickerActivity

/**
 * The `>` actions this branch ships. Other branches add theirs next to these:
 * `PaletteActions.register(PaletteAction(id = "theme_picker", label = "Theme picker") { ... })`.
 * Ids: settings, wallpaper, add_to_cockpit, add_link, web_links, theme_picker, gestures, hidden_apps, grayscale_toggle, grayscale_pause.
 * Lock is not offered because
 * locking needs the home screen's accessibility hook.
 */
object DefaultPaletteActions {

    fun register(context: Context) {
        val app = context.applicationContext
        PaletteActions.register(
            PaletteAction("settings", app.getString(R.string.palette_settings), closeDrawer = false) {
                it.navController.navigate(R.id.action_appListFragment_to_settingsFragment2)
            }
        )
        PaletteActions.register(
            PaletteAction("add_to_cockpit", app.getString(R.string.quick_add_title)) {
                it.context.startActivity(Intent(it.context, QuickAddActivity::class.java))
            }
        )
        PaletteActions.register(
            PaletteAction("open_cockpit_board", app.getString(R.string.palette_cockpit_board)) {
                CockpitLinks.openBoard(it.context)
            }
        )
        PaletteActions.register(
            PaletteAction("open_cockpit_calendar", app.getString(R.string.palette_cockpit_calendar)) {
                CockpitLinks.openCalendar(it.context)
            }
        )
        PaletteActions.register(
            PaletteAction("add_link", app.getString(R.string.add_link), closeDrawer = false) {
                LinkDialogs.showEdit(it.context, Prefs(it.context), null, onChanged = it.reload)
            }
        )
        PaletteActions.register(
            PaletteAction("web_links", app.getString(R.string.web_links), closeDrawer = false) {
                LinkDialogs.showList(it.context, Prefs(it.context), it.reload)
            }
        )
        PaletteActions.register(
            PaletteAction("theme_picker", app.getString(R.string.palette_theme_picker)) {
                ThemePickerActivity.open(it.context)
            }
        )
        PaletteActions.register(
            PaletteAction("wallpaper", app.getString(R.string.palette_wallpaper)) {
                com.outsmartis.yoke.wallpaper.WallpaperActivity.open(it.context)
            }
        )
        PaletteActions.register(
            PaletteAction("gestures", app.getString(R.string.palette_gestures), closeDrawer = false) {
                it.navController.navigate(R.id.gesturesFragment)
            }
        )
        PaletteActions.register(
            PaletteAction("grayscale_toggle", app.getString(R.string.palette_grayscale_toggle)) {
                GrayscaleController.toggle(it.context)
            }
        )
        PaletteActions.register(
            PaletteAction("grayscale_pause", app.getString(R.string.palette_grayscale_pause)) {
                GrayscaleController.pause(it.context)
            }
        )
        PaletteActions.register(
            PaletteAction("hidden_apps", app.getString(R.string.hidden_apps), closeDrawer = false) {
                if (Prefs(it.context).hiddenApps.isEmpty()) {
                    it.context.showToast(R.string.no_hidden_apps)
                } else {
                    it.viewModel.getHiddenApps()
                    it.navController.navigate(
                        R.id.appListFragment,
                        bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
                    )
                }
            }
        )
    }
}
