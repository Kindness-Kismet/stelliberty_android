package com.stelliberty.android.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.stelliberty.android.R
import com.stelliberty.android.platform.ProxyServiceBridge
import com.stelliberty.android.platform.ProxyServiceController
import com.stelliberty.android.platform.ProxyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class StellibertyTileService : TileService() {

    private val controller: ProxyServiceController by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = scope.launch {
            ProxyServiceBridge.state.collect { status ->
                val tile = qsTile ?: return@collect
                tile.state = when (status.state) {
                    ProxyState.Running -> Tile.STATE_ACTIVE
                    ProxyState.Starting -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
                tile.subtitle = when (status.state) {
                    ProxyState.Running -> getString(R.string.tile_connected)
                    ProxyState.Starting -> getString(R.string.tile_connecting)
                    ProxyState.Error -> getString(R.string.tile_error)
                    else -> getString(R.string.tile_disconnected)
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        stateJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val currentState = ProxyServiceBridge.state.value
        if (currentState.state == ProxyState.Starting) return

        if (currentState.state == ProxyState.Running) {
            controller.stop()
            return
        }
        if (!controller.hasVpnPermission()) {
            val target = controller.resolveStartTarget() ?: return
            launchVpnPermissionActivity(target.id)
            return
        }
        controller.start()
    }

    private fun launchVpnPermissionActivity(subscriptionId: String?) {
        val intent = Intent(this, VpnPermissionActivity::class.java).apply {
            subscriptionId?.let { putExtra(VpnPermissionActivity.EXTRA_SUBSCRIPTION_ID, it) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
