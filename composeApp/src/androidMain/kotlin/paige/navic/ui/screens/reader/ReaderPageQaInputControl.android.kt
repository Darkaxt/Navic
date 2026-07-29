package paige.navic.ui.screens.reader

object ReaderPageQaInputControl {
	private var pendingRequestId: String? = null

	@Synchronized
	fun arm(requestId: String): Boolean {
		require(isReaderPageQaRequestId(requestId))
		if (pendingRequestId != null) return false
		pendingRequestId = requestId
		return true
	}

	@Synchronized
	fun consume(): String? = pendingRequestId.also {
		pendingRequestId = null
	}

	@Synchronized
	fun clear(requestId: String): Boolean {
		require(isReaderPageQaRequestId(requestId))
		if (pendingRequestId != requestId) return false
		pendingRequestId = null
		return true
	}
}
