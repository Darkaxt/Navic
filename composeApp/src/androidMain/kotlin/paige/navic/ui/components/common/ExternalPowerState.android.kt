package paige.navic.ui.components.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import paige.navic.util.core.Logger

@Composable
actual fun rememberExternalPowerConnected(): Boolean? {
	val context = LocalContext.current
	var externalPowerConnected by remember(context) { mutableStateOf<Boolean?>(null) }

	DisposableEffect(context) {
		val receiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				externalPowerConnected = externalPowerConnectedFromPluggedValue(
					intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
				)
			}
		}
		var receiverRegistered = false

		try {
			val stickyIntent = context.registerReceiver(
				receiver,
				IntentFilter(Intent.ACTION_BATTERY_CHANGED)
			)
			receiverRegistered = true
			externalPowerConnected = externalPowerConnectedFromPluggedValue(
				stickyIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
			)
		} catch (error: RuntimeException) {
			externalPowerConnected = null
			Logger.w("ExternalPowerState", "Unable to register battery state receiver", error)
		}

		onDispose {
			if (receiverRegistered) {
				try {
					context.unregisterReceiver(receiver)
				} catch (error: RuntimeException) {
					Logger.w("ExternalPowerState", "Unable to unregister battery state receiver", error)
				}
			}
		}
	}

	return externalPowerConnected
}

internal fun externalPowerConnectedFromPluggedValue(plugged: Int?): Boolean? =
	plugged?.takeIf { it >= 0 }?.let { it != 0 }
