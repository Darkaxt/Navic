package paige.navic.ui.screens.reader

internal class ReaderPlayLikeCurlAdapterOwnerPool<T : Any>(
	val ownerLimit: Int
) {
	init {
		require(ownerLimit > 0)
	}

	private val owners = linkedMapOf<T, (() -> Unit)?>()

	val size: Int
		get() = owners.size

	fun tryAdd(owner: T): Boolean {
		if (owner in owners) return true
		if (owners.size == ownerLimit) return false
		owners[owner] = null
		return true
	}

	/** Keep original return custody on the bounded owner, not its cancelable worker. */
	fun retainDrainCallback(owner: T, onDrained: () -> Unit) {
		check(owner in owners)
		check(owners[owner] == null) { "Adapter return custody already retained" }
		owners[owner] = onDrained
	}

	/** Call only after successful physical drain. Removal claims callback delivery once. */
	fun remove(owner: T, onRemoved: () -> Unit = {}): Boolean {
		if (owner !in owners) return false
		val onDrained = owners.remove(owner)
		onRemoved()
		onDrained?.invoke()
		return true
	}

	fun snapshot(): List<T> = owners.keys.toList()
}
