package com.outsmartis.yoke.omarchy

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.outsmartis.yoke.R
import com.outsmartis.yoke.data.Prefs
import com.outsmartis.yoke.theme.ThemeApplier
import com.outsmartis.yoke.theme.ThemeStore
import com.outsmartis.yoke.ui.OmarchyStyleFragment

/** The gear next to Yoke in Android's screensaver settings: the Omarchy style screen on its own. */
class DreamSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(ThemeStore.nightMode(this, Prefs(this).appTheme))
        super.onCreate(savedInstanceState)
        val host = FrameLayout(this).apply { id = R.id.dreamSettingsHost }
        setContentView(host)
        ThemeApplier.applyWindow(this)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.dreamSettingsHost, OmarchyStyleFragment()).commit()
        }
    }
}
