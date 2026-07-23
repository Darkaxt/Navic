package paige.navic.ui.screens.reader

internal class ReaderPlayLikeCurlAdapterOwnerPool<T : Any>(
	val ownerLimit: Int
) {
	init {
		require(ownerLimit > 0)
	}

	private val owners = linkedSetOf<T>()

	val size: Int
		get() = owners.size

	fun tryAdd(owner: T): Boolean {
		if (owner in owners) return true
		if (owners.size == ownerLimit) return false
		owners += owner
		return true
	}

	fun remove(owner: T): Boolean = owners.remove(owner)

	fun snapshot(): List<T> = owners.toList()
}
