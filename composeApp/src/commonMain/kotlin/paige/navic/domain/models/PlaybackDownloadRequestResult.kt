package paige.navic.domain.models

sealed interface PlaybackDownloadRequestResult {
	val intentGeneration: Long?

	data class Enqueued(
		override val intentGeneration: Long
	) : PlaybackDownloadRequestResult

	data class AlreadyActive(
		override val intentGeneration: Long
	) : PlaybackDownloadRequestResult

	data class AlreadyDownloaded(
		override val intentGeneration: Long
	) : PlaybackDownloadRequestResult

	data object MissingCatalogEntry : PlaybackDownloadRequestResult {
		override val intentGeneration: Long? = null
	}

	data object InactiveSession : PlaybackDownloadRequestResult {
		override val intentGeneration: Long? = null
	}
}
