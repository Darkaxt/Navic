package paige.navic.ui.screens.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

internal class ReaderPageTurnBundleTeardown(
	private val scope: CoroutineScope,
	private val preCloseFailure: () -> Throwable? = { null },
	private val closePublicationWorkers: suspend () -> Unit,
	private val publicationEntryCount: () -> Int,
	private val publicationDispatchFailure: () -> Throwable? = { null },
	private val closeRasterGenerationWorkers: suspend () -> Unit,
	private val closeRasterHydrationWorkers: suspend () -> Unit,
	private val closePersistentStore: () -> Unit,
	private val closeRasterCache: () -> Unit,
	private val clearReferences: () -> Unit,
	private val onFinished: () -> Unit = {}
) {
	private val closeTask = ReaderPageSharedTeardownTask(scope, onFinished) {
		var failure: Throwable? = null
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.RasterInvalidation
		) {
			preCloseFailure()?.let { throw it }
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.PublicationWorker,
			closePublicationWorkers
		)
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.PublicationLedger
		) {
			check(publicationEntryCount() == 0) {
				"Publication ledger retained entries after worker join"
			}
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.PublicationDispatch
		) {
			publicationDispatchFailure()?.let { throw it }
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.RasterGenerationWorker,
			closeRasterGenerationWorkers
		)
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.RasterHydrationWorker,
			closeRasterHydrationWorkers
		)
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.PersistentStore
		) {
			closePersistentStore()
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.DecodedCache
		) {
			closeRasterCache()
		}
		failure = captureFailure(
			failure,
			ReaderPageTeardownStage.ReferenceClear
		) {
			clearReferences()
		}
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
