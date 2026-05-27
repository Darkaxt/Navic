package paige.navic.ui.components.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import paige.navic.domain.models.shakeToSkipReading
import paige.navic.domain.models.shouldSkipOnShake
import paige.navic.util.core.Logger

@Composable
actual fun ShakeToSkipEffect(
	enabled: Boolean,
	onSkip: () -> Unit
) {
	val context = LocalContext.current
	val latestOnSkip = rememberUpdatedState(onSkip)

	DisposableEffect(context, enabled) {
		if (!enabled) {
			return@DisposableEffect onDispose {}
		}

		val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
		val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
		if (sensorManager == null || accelerometer == null) {
			Logger.w("ShakeToSkip", "Accelerometer unavailable")
			return@DisposableEffect onDispose {}
		}

		var previousMagnitude = 0f
		var acceleration = 0f
		var lastSkipTimeMs = 0L
		val listener = object : SensorEventListener {
			override fun onSensorChanged(event: SensorEvent) {
				val values = event.values
				if (values.size < 3) return

				val reading = shakeToSkipReading(
					previousAcceleration = acceleration,
					previousMagnitude = previousMagnitude,
					x = values[0],
					y = values[1],
					z = values[2]
				)
				previousMagnitude = reading.magnitude
				acceleration = reading.acceleration

				val eventTimeMs = SystemClock.elapsedRealtime()
				if (
					shouldSkipOnShake(
						shakeToSkip = enabled,
						acceleration = acceleration,
						eventTimeMs = eventTimeMs,
						lastSkipTimeMs = lastSkipTimeMs
					)
				) {
					lastSkipTimeMs = eventTimeMs
					latestOnSkip.value()
				}
			}

			override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
		}

		sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
		Logger.i("ShakeToSkip", "registered accelerometer listener")

		onDispose {
			sensorManager.unregisterListener(listener)
			Logger.i("ShakeToSkip", "unregistered accelerometer listener")
		}
	}
}
