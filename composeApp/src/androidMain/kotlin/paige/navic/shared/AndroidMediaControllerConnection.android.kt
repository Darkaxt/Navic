package paige.navic.shared

import android.app.Application
import androidx.media3.session.MediaController

internal interface AndroidMediaControllerConnection : AutoCloseable {
	fun connect()
}

internal class DefaultAndroidMediaControllerConnection(
	private val application: Application,
	private val onConnected: (MediaController) -> Unit,
	private val onConnectionFailed: (Throwable) -> Unit,
	private val onDisconnected: (MediaController) -> Unit
) : AndroidMediaControllerConnection {
	private val owner = FutureConnectionOwner(
		onConnected = onConnected,
		onConnectionFailed = onConnectionFailed,
		onDisconnected = { controller ->
			onDisconnected(controller)
			connect()
		},
		releaseFuture = MediaController::releaseFuture
	)
	private val listener = object : MediaController.Listener {
		override fun onDisconnected(controller: MediaController) {
			owner.disconnect(controller)
		}
	}

	override fun connect() {
		val future = MediaController.Builder(application, PlaybackService.newSessionToken(application))
			.setListener(listener)
			.buildAsync()
		if (!owner.connect(future)) MediaController.releaseFuture(future)
	}

	override fun close() {
		owner.close()
	}
}
