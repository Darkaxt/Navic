package paige.navic.ui.screens.bindery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import paige.navic.domain.repositories.BinderyRepository

class BinderyCollectionArtworkResolver(
	private val repository: BinderyRepository,
	private val scope: CoroutineScope
) {
	private val _artworkByPath = MutableStateFlow<Map<String, String>>(emptyMap())
	val artworkByPath = _artworkByPath.asStateFlow()

	private val requestedPaths = mutableSetOf<String>()
	private val jobsByPath = mutableMapOf<String, Job>()

	fun resolve(card: BinderyCatalogCard.Link) {
		if (!card.needsDetailArtworkResolution()) return
		val path = card.path
		if (path in requestedPaths || path in _artworkByPath.value) return
		requestedPaths += path
		jobsByPath[path] = scope.launch {
			repository.getCatalog(path).getOrNull()
				?.firstPublicationImageHref()
				?.let { imageHref ->
					_artworkByPath.value = _artworkByPath.value + (path to imageHref)
				}
			jobsByPath.remove(path)
		}
	}

	fun clear() {
		jobsByPath.values.forEach { it.cancel() }
		jobsByPath.clear()
		requestedPaths.clear()
		_artworkByPath.value = emptyMap()
	}
}
