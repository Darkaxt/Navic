package paige.navic.ui.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import paige.navic.util.core.synchronized
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Serializes ownership changes and short state commits, never network work. */
internal class EnrichmentRequestOwner {
	private val lock = Any()
	private var generation = 0L
	private var job: Job? = null

	val isRunning: Boolean
		get() = synchronized(lock) { job?.isCompleted == false }

	fun cancel(): Boolean {
		val previous = synchronized(lock) {
			generation++
			job.also { job = null }
		}
		val wasRunning = previous?.isCompleted == false
		previous?.cancel()
		return wasRunning
	}

	fun launch(
		scope: CoroutineScope,
		context: CoroutineContext = EmptyCoroutineContext,
		block: suspend CoroutineScope.() -> Unit
	) {
		val (previous, next) = synchronized(lock) {
			val previous = job
			val token = Token(this, ++generation)
			val next = scope.launch(context + token, start = CoroutineStart.LAZY, block = block)
			job = next
			previous to next
		}
		previous?.cancel()
		next.start()
	}

	suspend fun commit(block: () -> Unit): Boolean {
		val context = currentCoroutineContext()
		context.ensureActive()
		val token = context[Token] ?: error("Enrichment state requires an owned request")
		return synchronized(lock) {
			if (token.owner !== this || token.generation != generation) false
			else {
				context.ensureActive()
				block()
				true
			}
		}
	}

	suspend fun <T> update(state: MutableStateFlow<T>, transform: (T) -> T): Boolean =
		commit { state.update(transform) }

	private class Token(val owner: EnrichmentRequestOwner, val generation: Long) :
		AbstractCoroutineContextElement(Key) {
		companion object Key : CoroutineContext.Key<Token>
	}
}
