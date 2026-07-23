package paige.navic.ui.screens.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

internal class ReaderPageReaderTeardown(
	private val scope: CoroutineScope,
	private val fenceCallbacks: () -> Unit = {},
	private val fenceBundleOwners: () -> Unit,
	private val closeRendererAndAdapter: suspend () -> Unit,
	private val closeBundleOwners: suspend () -> Unit,
	private val onFinished: () -> Unit = {}
) {
	private val closeTask = ReaderPageSharedTeardownTask(scope, onFinished) {
		var failure: Throwable? = null
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.CallbackFence
		) {
			fenceCallbacks()
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.RasterInvalidation
		) {
			fenceBundleOwners()
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.RendererDisposal,
			closeRendererAndAdapter
		)
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.BundleOwners,
			closeBundleOwners
		)
		failure?.let { throw it }
	}

	fun start(): Deferred<Unit> = closeTask.start()

	suspend fun closeAndJoin() {
		start().await()
	}

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
