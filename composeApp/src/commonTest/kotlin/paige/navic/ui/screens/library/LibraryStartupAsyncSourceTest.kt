package paige.navic.ui.screens.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryStartupAsyncSourceTest {
	@Test
	fun mostPlayedArtworkAggregationRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/library/MostPlayedShortcutsViewModel.kt"
		)

		assertTrue(
			".flowOn(Dispatchers.Default)" in source,
			"Most-played shortcut artwork aggregation combines broad artist, album, and song artwork tables; " +
				"that combine body must not run on the UI dispatcher during Library startup."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Most-played Aurral artist photo hydration must run cache/network work on IO."
		)
	}

	@Test
	fun artistListAurralCacheMergeRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistListViewModel.kt"
		)

		assertTrue(
			"artistPhotoCacheDao.observeArtistPhotoCache()" in source &&
				"artistCreditResolutionState" in source,
			"Artist list must keep merging cached Aurral photos into catalog rows."
		)
		assertTrue(
			".flowOn(Dispatchers.Default)" in source,
			"Artist list cache merging should not run on the UI dispatcher during Library startup."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Artist list Aurral photo hydration must run cache/network work on IO."
		)
	}

	@Test
	fun artistDetailAurralEnrichmentRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt"
		)
		val start = source.indexOf("private fun loadAurralEnrichment(")
		val end = source.indexOf("private suspend fun persistArtistPhotoCache", start)
		val loadAurralEnrichment = source.substring(start, end)

		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in loadAurralEnrichment,
			"Artist detail Aurral enrichment reads cache/network data and builds broad image candidates; " +
				"that work must not run on the UI dispatcher."
		)
		assertTrue(
			"artistHeaderImageCacheIndex(" in source,
			"Artist detail should build an indexed artist-photo cache once instead of repeatedly scanning the full cache."
		)
	}

	@Test
	fun artistDetailAurralRowsPublishBeforeCoverHydration() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt"
		)
		val start = source.indexOf("private fun loadAurralEnrichment(")
		val end = source.indexOf("private fun applyAurralEnrichmentSnapshot", start)
		val loadAurralEnrichment = source.substring(start, end)
		val statePublish = loadAurralEnrichment.indexOf("aurralOwnedOrPartialAlbums = ownedOrPartialAlbumRows")
		val coverHydration = loadAurralEnrichment.indexOf("hydrateAurralArtistAlbumCovers(")

		assertTrue(
			coverHydration >= 0,
			"Artist detail should hydrate missing Aurral release-group covers after publishing album rows."
		)
		assertTrue(
			statePublish >= 0 && statePublish < coverHydration,
			"Artist detail must publish Aurral album row titles before waiting on release-group cover lookups."
		)
		assertFalse(
			"resolveAurralOwnershipAlbumCovers(resolvedAurralArtist, rows)" in loadAurralEnrichment,
			"Fresh Aurral artist rows must not block on cover URL hydration before rendering."
		)
	}

	@Test
	fun albumAurralStatusRefreshRunsOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt"
		)

		assertTrue(
			"private fun refreshAurralAcquisitionRequests()" in source,
			"Album list must keep refreshing Aurral acquisition status for request chips."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Album Aurral service-status refresh is cache/network work and must not run on the UI dispatcher."
		)
	}

	@Test
	fun libraryAurralRowsAreDerivedOffTheUiDispatcher() {
		val source = commonMain(
			"paige/navic/ui/screens/library/LibraryScreen.kt"
		)

		assertTrue(
			"produceState<UiState<List<AurralDiscoveryCollectionRow>>>" in source,
			"Library Aurral row projection should be derived in a produced state instead of directly during composition."
		)
		assertTrue(
			"withContext(Dispatchers.Default)" in source,
			"Library Aurral row projection walks discovery and cached artist-photo data; that CPU work must run off the UI dispatcher."
		)
		val backgroundBoundary = source.indexOf("withContext(Dispatchers.Default)")
		val cacheProjection = source.indexOf("val artistPhotoCacheEntries = cachedArtistPhotos.map")
		val rowProjection = source.indexOf("libraryAurralCollectionRowsState(")
		assertTrue(
			cacheProjection > backgroundBoundary,
			"LibraryScreen must map the full artist-photo cache after the background dispatcher boundary."
		)
		assertTrue(
			rowProjection > backgroundBoundary,
			"LibraryScreen must build Aurral collection rows after the background dispatcher boundary."
		)
	}

	@Test
	fun libraryAurralDiscoveryDoesNotBlockOnImageHydration() {
		val source = commonMain(
			"paige/navic/ui/screens/library/LibraryScreen.kt"
		)

		assertTrue(
			"aurralViewModel.refreshDiscovery(hydrateMissingImages = false)" in source,
			"Library should render base/cached Aurral discovery first; missing image hydration belongs on a background path."
		)
		assertFalse(
			"aurralViewModel.refreshDiscovery(hydrateMissingImages = true)" in source,
			"Library startup and pull-refresh should not wait for serial Aurral image hydration lookups."
		)
	}

	@Test
	fun libraryAurralRowsReuseViewModelCacheOnTabReentry() {
		val librarySource = commonMain(
			"paige/navic/ui/screens/library/LibraryScreen.kt"
		)
		val viewModelSource = commonMain(
			"paige/navic/ui/screens/aurral/AurralHubViewModel.kt"
		)

		assertTrue(
			"aurralViewModel.libraryCollectionRows" in librarySource &&
				"initialValue = cachedAurralCollectionRowsState" in librarySource,
			"Library Aurral rows must seed from the ViewModel cache instead of flashing an empty row state on tab re-entry."
		)
		assertTrue(
			"libraryAurralRowsStateWithCache(value, nextState)" in librarySource &&
				"rememberLibraryCollectionRows(value)" in librarySource,
			"Library Aurral row projection must keep resolved rows visible while recomputing in the background."
		)
		assertTrue(
			"val libraryCollectionRows = _libraryCollectionRows.asStateFlow()" in viewModelSource,
			"AurralHubViewModel must retain projected Library rows across Library composable disposal."
		)
	}

	@Test
	fun aurralDiscoveryRefreshIsIdempotentForAlreadyLoadedConfiguration() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralHubViewModel.kt")

		assertTrue(
			"repository.discoveryConfigurationKey(hydrateMissingImages)" in source,
			"Aurral discovery refresh should identify whether the currently loaded config already matches the requested config."
		)
		assertTrue(
			"!forceRefresh" in source &&
				"nextConfigurationKey == discoveryConfigurationKey" in source &&
				"_discovery.value.data != null" in source,
			"Entering a tab with already-loaded Aurral discovery must not re-run the repository load."
		)
	}

	@Test
	fun aurralDetailLocalCatalogLookupsRunOffTheUiDispatcher() {
		val artistSource = commonMain(
			"paige/navic/ui/screens/aurral/AurralArtistScreen.kt"
		)
		val missingAlbumSource = commonMain(
			"paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt"
		)

		listOf(artistSource, missingAlbumSource).forEach { source ->
			assertTrue(
				"withContext(Dispatchers.IO)" in source,
				"Aurral detail screens read local artist/album tables during load; those DAO reads must run off the UI dispatcher."
			)
		}
	}

	@Test
	fun binderyRepositoryJobsRunOffTheUiDispatcher() {
		val binderyViewModels = listOf(
			"paige/navic/ui/screens/bindery/BinderyAudiobookDetailViewModel.kt",
			"paige/navic/ui/screens/bindery/BinderyAudiobookPlayerViewModel.kt",
			"paige/navic/ui/screens/bindery/BinderyBookViewModel.kt",
			"paige/navic/ui/screens/bindery/BinderyCatalogViewModel.kt",
			"paige/navic/ui/screens/bindery/BinderyHubViewModel.kt"
		)

		binderyViewModels.forEach { path ->
			val source = commonMain(path)
			assertTrue(
				"viewModelScope.launch(Dispatchers.IO)" in source,
				"$path should run cache/network repository work on IO."
			)
			assertFalse(
				"viewModelScope.launch {" in source,
				"$path still has a plain ViewModel launch that can run repository work on the UI dispatcher."
			)
		}

		val searchSource = commonMain("paige/navic/ui/screens/bindery/BinderySearchViewModel.kt")
		assertTrue(
			"withContext(Dispatchers.IO)" in searchSource,
			"Bindery search keeps a snapshotFlow collector, but cache/network search work must cross an IO boundary."
		)
		assertTrue(
			"searchCachedBindery(query)" in searchSource && "searchBindery(query)" in searchSource,
			"Bindery search must keep both cached-first and live search behavior."
		)
	}

	@Test
	fun settingsSearchStorageMetricsRunOffTheUiDispatcher() {
		val source = commonMain("paige/navic/ui/screens/settings/SettingsSearchRegistry.kt")

		assertTrue(
			"produceState" in source && "withContext(Dispatchers.IO)" in source,
			"Settings search should load storage/cache metrics asynchronously instead of reading the filesystem during composition."
		)
		assertFalse(
			"remember { storageManager.listLidaClipOfflineFiles() }" in source,
			"Lida clip offline file listing is filesystem work and must not run inside remember."
		)
		assertFalse(
			"remember { storageManager.readerPublicationCacheSizeBytes() }" in source,
			"Reader publication cache sizing is filesystem work and must not run inside remember."
		)
	}

	@Test
	fun playbackArtworkCacheProjectionRunsOffTheUiDispatcher() {
		val source = commonMain("paige/navic/ui/components/common/PlaybackArtworkState.kt")

		assertTrue(
			"produceState<List<PlaybackArtistPhotoCacheEntry>>" in source,
			"Playback artwork should produce projected artist-photo cache entries asynchronously."
		)
		assertTrue(
			"withContext(Dispatchers.Default)" in source,
			"Playback artwork cache projection is CPU work and should run off the UI dispatcher."
		)
		assertFalse(
			"remember(cachedArtistPhotos, aurralBaseUrl) {" in source,
			"Playback artwork must not map the full artist-photo cache directly in composition."
		)
	}

	@Test
	fun aurralFlowPlaybackRepositoryLoadsRunOffTheUiDispatcher() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralHubViewModel.kt")

		assertTrue(
			"withContext(Dispatchers.IO)" in source,
			"Aurral flow playback actions should keep repository loads off the UI dispatcher."
		)
		assertTrue(
			"playlistRepository.getPlaylistForPlayback(station)" in source,
			"Station playback must keep resolving the playable station before queueing it."
		)
		assertTrue(
			"repository.getFlowPlayableSongs(flow.id).getOrThrow()" in source,
			"Direct flow playback must keep resolving playable songs before queueing them."
		)
	}

	@Test
	fun collectionDetailStartupDataDoesNotBlockTheConstructor() {
		val source = commonMain("paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt")

		assertFalse(
			"runBlocking" in source,
			"Collection detail must not synchronously read cached collection data while constructing the ViewModel."
		)
		assertTrue(
			"viewModelScope.launch(Dispatchers.IO)" in source,
			"Collection detail cache/API work should run on IO."
		)
		assertTrue(
			"repository.getLocalData(collectionId)" in source,
			"Collection detail should still hydrate stale local data before the live refresh completes."
		)
		assertTrue(
			".flatMapLatest { album ->" in source &&
				"repository.getOtherAlbums(album.artistId, album.id)" in source,
			"Other-albums rows must be derived from loaded collection state instead of the constructor-time snapshot."
		)
	}

	private fun commonMain(path: String): String =
		File("src/commonMain/kotlin/$path").readText()
}
