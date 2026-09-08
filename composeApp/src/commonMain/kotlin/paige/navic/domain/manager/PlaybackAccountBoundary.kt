package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Registration and player mutation are main-thread confined; account transitions await revocation. */
class PlaybackAccountBoundary(
	initialOwnerId: String?,
	val legacyOwnerId: String? = null,
	private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
	data class Lease internal constructor(val ownerId: String?, internal val revision: Long)

	private val current = MutableStateFlow(Lease(initialOwnerId, 0))
	val state = current.asStateFlow()
	private val listeners = mutableSetOf<(String?) -> Unit>()

	fun capture(): Lease = current.value

	fun isCurrent(lease: Lease): Boolean = current.value == lease

	fun register(onRevoked: (nextOwnerId: String?) -> Unit): () -> Unit {
		listeners += onRevoked
		return { listeners -= onRevoked }
	}

	suspend fun transitionTo(nextOwnerId: String?) = withContext(dispatcher) {
		val previous = current.value
		if (previous.ownerId == nextOwnerId) return@withContext
		val revision = previous.revision + 1
		current.value = Lease(null, revision)
		listeners.toList().forEach { revoke -> revoke(nextOwnerId) }
		current.value = Lease(nextOwnerId, revision)
	}
}
