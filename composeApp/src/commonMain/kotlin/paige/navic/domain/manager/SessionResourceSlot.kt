package paige.navic.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow

internal class SessionResourceSlot<T>(initial: T) {
	private val resource = MutableStateFlow(initial)

	fun swap(replacement: T) {
		resource.value = replacement
	}

	fun snapshot(): T = resource.value

	suspend fun <R> withResource(block: suspend (T) -> R): R =
		block(resource.value)
}
