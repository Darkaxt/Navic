package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFailureReason

/** One gateway member only. This cannot mint a production port package. */
internal class ReaderSnapshotBackedActivatedGatewayPort(
	internal val candidate: ReaderTransitionGatewayCandidate
) : ReaderActivatedGatewayPort {
	private fun admitted(): Boolean = candidate.isSelected &&
		candidate.gateway.isCurrentActivatedSemanticEpoch(candidate.epoch) &&
		candidate.installationSource.ownsInstalledCandidate(
			candidate.epoch, candidate.readerSessionGeneration, candidate.coordinatorEpoch
		)

	override fun routeIntent(fact: ReaderTransitionFact.Intent): ReaderPortCommandResult {
		if (!admitted()) return rejected()
		candidate.gateway.enqueue(fact)
		return ReaderPortCommandResult.Accepted
	}

	override fun routeReceipt(receipt: ReaderPresentationEventReceipt): ReaderPortCommandResult {
		if (!admitted() || receipt.version.readerSessionGeneration != candidate.readerSessionGeneration) return rejected()
		candidate.gateway.observePresentationReceipt(receipt)
		return ReaderPortCommandResult.Accepted
	}

	override fun closeToReleaseOnly() = candidate.close()

	private fun rejected() = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.ActivationPrerequisiteMissing)
}
