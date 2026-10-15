package paige.navic.ui.screens.reader

/** Admission reads the root's one installed snapshot; it is never an install callback. */
internal interface ReaderTransitionGatewayInstallationSource {
	val awaitingInstallation: Boolean
	fun ownsInstalledCandidate(
		epoch: ReaderTransitionGatewaySemanticEpoch,
		readerSessionGeneration: Long,
		coordinatorEpoch: Long
	): Boolean
	fun hasTransferredCandidate(epoch: ReaderTransitionGatewaySemanticEpoch): Boolean
}

/** Private preinstall identity, not a successful presentation or a command registration. */
internal class ReaderTransitionGatewayCandidate internal constructor(
	internal val gateway: ReaderTransitionGateway,
	val epoch: ReaderTransitionGatewaySemanticEpoch,
	val readerSessionGeneration: Long,
	val coordinatorEpoch: Long,
	internal val installationSource: ReaderTransitionGatewayInstallationSource,
	private val onClose: () -> Unit
) {
	init { require(readerSessionGeneration > 0L && coordinatorEpoch > 0L) }
	val isSelected: Boolean get() = gateway.isSelectedCandidate(this)
	fun close() = onClose()
	override fun toString(): String = "ReaderTransitionGatewayCandidate(<redacted>)"
}
