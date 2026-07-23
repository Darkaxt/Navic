package paige.navic.ui.screens.reader

import karacken.curl.PageSurfaceDisposalStage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ReaderPageTeardownStage {
	CallbackFence,
	RasterInvalidation,
	RendererDisposal,
	RendererOwnership,
	DeckGeneration,
	RasterDeck,
	RasterAdapter,
	ControllerWorker,
	BundleOwners,
	PublicationWorker,
	PublicationLedger,
	PublicationDispatch,
	RasterGenerationWorker,
	RasterHydrationWorker,
	PersistentStore,
	DecodedCache,
	ReferenceClear
}

internal class ReaderPageTeardownException(
	val stage: ReaderPageTeardownStage,
	val rendererStage: PageSurfaceDisposalStage? = null,
	cause: Throwable? = null
) : IllegalStateException("Reader teardown failed at $stage", cause) {
	fun totalSuppressedFailureCount(): Int =
		suppressed.size +
			(cause?.suppressed?.size ?: 0) +
			suppressed.sumOf { nested ->
				(nested as? ReaderPageTeardownException)
					?.totalSuppressedFailureCount() ?: nested.suppressed.size
			}
}

internal suspend fun <T> readerPageTeardownStage(
	stage: ReaderPageTeardownStage,
	rendererStage: PageSurfaceDisposalStage? = null,
	action: suspend () -> T
): T = try {
	action()
} catch (failure: ReaderPageTeardownException) {
	throw failure
} catch (failure: Throwable) {
	throw ReaderPageTeardownException(stage, rendererStage, failure)
}

internal class ReaderPageSharedTeardownTask(
	private val scope: CoroutineScope,
	private val onFinished: () -> Unit = {},
	private val action: suspend () -> Unit
) {
	private val lock = Any()
	private var completion: CompletableDeferred<Unit>? = null

	fun start(): Deferred<Unit> {
		var launchWorker = false
		val result = synchronized(lock) {
			completion ?: CompletableDeferred<Unit>().also {
				completion = it
				launchWorker = true
			}
		}
		if (launchWorker) launch(result)
		return result
	}

	private fun launch(result: CompletableDeferred<Unit>) {
		val worker = try {
			scope.launch {
				val outcome = runCatching {
					withContext(NonCancellable) { action() }
				}
				outcome.fold(result::complete, result::completeExceptionally)
			}
		} catch (failure: Throwable) {
			result.completeExceptionally(failure)
			onFinished()
			return
		}
		worker.invokeOnCompletion { failure ->
			if (!result.isCompleted) {
				result.completeExceptionally(
					failure ?: IllegalStateException(
						"Reader teardown worker completed without a terminal result"
					)
				)
			}
			onFinished()
		}
	}
}
