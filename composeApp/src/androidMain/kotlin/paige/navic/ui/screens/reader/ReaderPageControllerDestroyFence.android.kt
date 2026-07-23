package paige.navic.ui.screens.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import paige.navic.reader.ReaderPageRelocationDrain

internal class ReaderPageControllerDestroyFence(
	private val scope: CoroutineScope,
	private val fenceAdmission: () -> Unit,
	private val advanceGenerations: () -> Unit,
	private val cancelActiveGesture: () -> Unit,
	private val cancelRecovery: () -> Unit,
	private val closeVisualHandoff: () -> Unit,
	private val cancelRelocations: () -> ReaderPageRelocationDrain,
	private val verifyRelocationsDrained: (ReaderPageRelocationDrain) -> Unit,
	private val markPageSetsObsolete: () -> Unit,
	private val hideSurface: () -> Unit,
	private val disposeRendererAndOwners: suspend () -> Unit
) {
	private val destroyTask = ReaderPageSharedTeardownTask(scope) {
		var failure: Throwable? = null
		failure = captureFailure(failure, ReaderPageTeardownStage.CallbackFence) {
			fenceAdmission()
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.RasterInvalidation) {
			advanceGenerations()
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.ControllerWorker) {
			cancelActiveGesture()
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.RasterDeck) {
			cancelRecovery()
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.CallbackFence) {
			closeVisualHandoff()
		}
		var drained: ReaderPageRelocationDrain? = null
		failure = captureFailure(failure, ReaderPageTeardownStage.ControllerWorker) {
			drained = cancelRelocations()
		}
		drained?.let { owned ->
			failure = captureFailure(failure, ReaderPageTeardownStage.RendererOwnership) {
				verifyRelocationsDrained(owned)
			}
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.RasterDeck) {
			markPageSetsObsolete()
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.ControllerWorker) {
			hideSurface()
		}
		failure = captureFailure(failure, ReaderPageTeardownStage.RendererDisposal) {
			disposeRendererAndOwners()
		}
		failure?.let { throw it }
	}

	fun start(): Deferred<Unit> = destroyTask.start()

	private suspend fun captureFailure(
		current: Throwable?,
		stage: ReaderPageTeardownStage,
		action: suspend () -> Unit
	): Throwable? = try {
		readerPageTeardownStage(stage, action = action)
		current
	} catch (next: Throwable) {
		if (current == null) next
		else current.apply { if (next !== current) addSuppressed(next) }
	}
}
