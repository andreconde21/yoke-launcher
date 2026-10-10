package com.outsmartis.yoke.grayscale

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: active while Smart grayscale is on, tap toggles it. */
class GrayscaleTileService : TileService() {

    override fun onStartListening() = update()

    override fun onClick() {
        GrayscaleController.toggle(applicationContext)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        tile.state = if (GrayscalePrefs(applicationContext).featureOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
