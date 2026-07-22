package paige.navic.shared

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import paige.navic.domain.models.CONNECTION_LOST_OFFLINE_MESSAGE

@OptIn(UnstableApi::class)
internal class OfflineAwareMediaNotificationProvider(
	private val context: Context,
	private val delegate: MediaNotification.Provider
) : MediaNotification.Provider {
	private var connectionLost = false
	private var latestBase: MediaNotification? = null
	private var latestCallback: MediaNotification.Provider.Callback? = null

	fun setConnectionLost(value: Boolean) {
		if (connectionLost == value) return
		connectionLost = value
		val base = latestBase ?: return
		val callback = latestCallback ?: return
		callback.onNotificationChanged(decorate(base))
	}

	override fun createNotification(
		mediaSession: MediaSession,
		mediaButtons: ImmutableList<CommandButton>,
		actionFactory: MediaNotification.ActionFactory,
		callback: MediaNotification.Provider.Callback
	): MediaNotification {
		latestCallback = callback
		val base = delegate.createNotification(
			mediaSession,
			mediaButtons,
			actionFactory,
			object : MediaNotification.Provider.Callback {
				override fun onNotificationChanged(notification: MediaNotification) {
					latestBase = notification
					callback.onNotificationChanged(decorate(notification))
				}
			}
		)
		latestBase = base
		return decorate(base)
	}

	override fun handleCustomCommand(
		mediaSession: MediaSession,
		action: String,
		extras: Bundle
	): Boolean = delegate.handleCustomCommand(mediaSession, action, extras)

	override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
		requireNotNull(delegate.notificationChannelInfo)

	private fun decorate(base: MediaNotification): MediaNotification {
		if (!connectionLost) return base
		val notification = NotificationCompat.Builder(context, base.notification)
			.setSubText(CONNECTION_LOST_OFFLINE_MESSAGE)
			.setOnlyAlertOnce(true)
			.build()
		return MediaNotification(base.notificationId, notification)
	}
}
