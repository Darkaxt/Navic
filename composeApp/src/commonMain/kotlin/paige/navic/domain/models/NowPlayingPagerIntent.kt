package paige.navic.domain.models

enum class QueueSelectionOrigin {
	DirectPlay,
	NowPlayingArtworkSwipe
}

data class QueueSelectionRequest(
	val index: Int,
	val playWhenReady: Boolean,
	val origin: QueueSelectionOrigin
)

class NowPlayingPagerIntentTracker {
	private var userDragArmed = false

	fun onUserDragStarted() {
		userDragArmed = true
	}

	fun onSettledPage(
		settledPage: Int,
		currentIndex: Int,
		queueSize: Int,
		isPaused: Boolean
	): QueueSelectionRequest? {
		if (!userDragArmed) return null
		userDragArmed = false
		if (settledPage !in 0 until queueSize || settledPage == currentIndex) return null

		return QueueSelectionRequest(
			index = settledPage,
			playWhenReady = !isPaused,
			origin = QueueSelectionOrigin.NowPlayingArtworkSwipe
		)
	}
}
