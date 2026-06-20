package paige.navic.domain.manager

expect class QueueNotificationManager {
	fun showProgressNotification(
		id: Int,
		title: String,
		message: String,
		progress: Float,
		indeterminate: Boolean
	)

	fun cancelNotification(id: Int)
}

object QueueNotificationIds {
	const val MUSIC_DOWNLOADS = 1001
	const val LIDA_CLIP_DOWNLOADS = 1002
}
