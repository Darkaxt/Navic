package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

internal suspend fun runConnectedDownloadWorkers(
	online: Flow<Boolean>,
	recoverInterrupted: suspend () -> Unit,
	workers: suspend CoroutineScope.() -> Unit
) {
	recoverInterrupted()
	online.distinctUntilChanged().collectLatest { connected ->
		if (connected) {
			try {
				coroutineScope { workers() }
			} finally {
				// coroutineScope has joined every claimant, including unregistered jobs.
				withContext(NonCancellable) { recoverInterrupted() }
			}
		}
	}
}
