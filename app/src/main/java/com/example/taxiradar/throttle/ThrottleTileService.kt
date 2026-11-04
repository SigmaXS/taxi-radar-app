package com.example.taxiradar.throttle

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.taxiradar.MainActivity

/** Плитка «Отмена» в шторке быстрых настроек. */
class ThrottleTileService : TileService() {

    companion object {
        private val BURST_MS = ThrottleAuto.BURST_MS
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val now = SystemClock.elapsedRealtime()
        val endsAt = ThrottleState.endsAtElapsedRealtime.value
        val active = ThrottleState.isRunning.value && endsAt > now

        if (active) {
            ThrottleVpnService.endBurst(this)
            refresh()
            return
        }

        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }

        ThrottleVpnService.start(this, BURST_MS, keepAlive = true)
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        ThrottleState.ensureLoaded(applicationContext)
        val now = SystemClock.elapsedRealtime()
        val active = ThrottleState.isRunning.value && ThrottleState.endsAtElapsedRealtime.value > now
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(com.example.taxiradar.R.string.nav_throttle)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
