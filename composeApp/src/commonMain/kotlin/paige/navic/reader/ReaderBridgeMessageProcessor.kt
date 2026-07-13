package paige.navic.reader

private const val PersistentReaderBridgeFailureThreshold = 3
private const val ReaderBridgeProtocolErrorCode = "reader_bridge_protocol"
private const val ReaderBridgeProtocolErrorMessage =
	"Reader communication failed repeatedly. Close and reopen this book."

class ReaderBridgeMessageProcessor(
	private val onEvent: (ReaderBridgeEvent) -> Unit,
	private val onRejected: (ReaderBridgeDecodeResult.Rejected) -> Unit
) {
	private var consecutiveFailureCount = 0
	private var persistentFailureReported = false

	fun process(message: String) {
		when (val result = decodeReaderBridgeMessage(message)) {
			is ReaderBridgeDecodeResult.Decoded -> {
				consecutiveFailureCount = 0
				persistentFailureReported = false
				onEvent(result.event)
			}
			is ReaderBridgeDecodeResult.Rejected -> {
				if (consecutiveFailureCount == 0) {
					onRejected(result)
				}
				consecutiveFailureCount += 1
				if (
					consecutiveFailureCount >= PersistentReaderBridgeFailureThreshold &&
					!persistentFailureReported
				) {
					persistentFailureReported = true
					onEvent(
						ReaderBridgeEvent.Error(
							message = ReaderBridgeProtocolErrorMessage,
							code = ReaderBridgeProtocolErrorCode
						)
					)
				}
			}
		}
	}
}
