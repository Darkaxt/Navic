package paige.navic.ui.screens.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ReaderPageRecoveredDeckBuildOperation<T : AutoCloseable>(
	val preparation: Deferred<T?>,
	private val scope: CoroutineScope,
	private val publicationDispatcher: CoroutineDispatcher,
	private val onResult: suspend (T?, resolveOwnership: () -> Unit) -> Unit,
	private val onFailure: suspend (Throwable) -> Unit
) {
	var waiter: Job? = null
		private set

	fun start() {
		check(waiter == null) { "Recovered deck build operation is already started" }
		waiter = scope.launch(start = CoroutineStart.UNDISPATCHED) {
			var awaitedResult: T? = null
			var ownershipResolved = false
			try {
				awaitedResult = preparation.await()
				withContext(publicationDispatcher) {
					onResult(awaitedResult) { ownershipResolved = true }
				}
			} catch (cancelled: CancellationException) {
				throw cancelled
			} catch (failure: Throwable) {
				withContext(NonCancellable + publicationDispatcher) {
					onFailure(failure)
				}
			} finally {
				if (!ownershipResolved) {
					preparation.cancel()
					val undeliveredResult = awaitedResult ?: withContext(NonCancellable) {
						runCatching { preparation.await() }.getOrNull()
					}
					undeliveredResult?.close()
				}
			}
		}
	}

	fun cancel() {
		waiter?.cancel()
		preparation.cancel()
	}
}
