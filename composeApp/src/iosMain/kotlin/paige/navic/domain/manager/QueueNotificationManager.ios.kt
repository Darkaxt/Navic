package paige.navic.domain.manager

actual class QueueNotificationManager {
	actual fun showProgressNotification(
		id: Int,
		title: String,
		message: String,
		progress: Float,
		indeterminate: Boolean
	) = Unit

	actual fun cancelNotification(id: Int) = Unit
}
