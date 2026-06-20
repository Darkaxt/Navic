package paige.navic.domain.manager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import paige.navic.util.core.ResourceProvider

actual class QueueNotificationManager(
	private val context: Context,
	private val resourceProvider: ResourceProvider
) {
	private val notificationManager =
		context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager

	init {
		createNotificationChannel()
	}

	actual fun showProgressNotification(
		id: Int,
		title: String,
		message: String,
		progress: Float,
		indeterminate: Boolean
	) {
		if (!canPostNotifications()) return

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(resourceProvider.icNavic)
			.setContentTitle(title)
			.setContentText(message)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setProgress(100, (progress.coerceIn(0f, 1f) * 100).toInt(), indeterminate)
			.build()

		notificationManager.notify(id, notification)
	}

	actual fun cancelNotification(id: Int) {
		notificationManager.cancel(id)
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

		val channel = NotificationChannel(
			CHANNEL_ID,
			"Download queues",
			AndroidNotificationManager.IMPORTANCE_LOW
		).apply {
			description = "Progress for Navic download queues"
		}
		notificationManager.createNotificationChannel(channel)
	}

	private fun canPostNotifications(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
			ContextCompat.checkSelfPermission(
				context,
				Manifest.permission.POST_NOTIFICATIONS
			) == PackageManager.PERMISSION_GRANTED

	private companion object {
		const val CHANNEL_ID = "download_queues"
	}
}
