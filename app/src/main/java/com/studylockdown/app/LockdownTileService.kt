package com.studylockdown.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Lets the user start/stop lockdown directly from the Quick Settings shade
 * without opening the app. Reads/writes the same [LockdownManager] state the
 * Activity and Accessibility Service use, so all three stay consistent.
 */
class LockdownTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val turnOn = !LockdownManager.isLockdownEnabled(applicationContext)
        LockdownManager.setLockdownEnabled(applicationContext, turnOn)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val enabled = LockdownManager.isLockdownEnabled(applicationContext)
        val enforcing = LockdownManager.isEnforcementActive(applicationContext)

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = when {
            enforcing -> "Lockdown: ON"
            enabled -> "Lockdown: Paused"
            else -> "Lockdown: OFF"
        }
        tile.updateTile()
    }
}
