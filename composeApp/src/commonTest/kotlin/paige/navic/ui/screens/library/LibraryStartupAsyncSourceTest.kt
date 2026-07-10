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
		val statePublish = loadAurralEnrichment.indexOf("applyAurralCoreEnrichmentSnapshot(")
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
	fun artistDetailFreshProfilePublishesBeforeFullAurralSections() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt"
		)
		val start = source.indexOf("private fun loadAurralEnrichment(")
		val end = source.indexOf("private fun applyAurralEnrichmentSnapshot", start)
		val loadAurralEnrichment = source.substring(start, end)
		val coreFetch = loadAurralEnrichment.indexOf("getArtistCoreEnrichment(")
		val corePublish = loadAurralEnrichment.indexOf("applyAurralCoreEnrichmentSnapshot(")
		val sectionFetch = listOf(
			loadAurralEnrichment.indexOf("getArtistPreviewTracks("),
			loadAurralEnrichment.indexOf("getArtistSimilarArtists("),
			loadAurralEnrichment.indexOf("getArtistAlbumRequests(")
		).filter { it >= 0 }.minOrNull() ?: -1

		assertTrue(
			coreFetch >= 0,
			"Artist detail should fetch a core Aurral profile/release-group snapshot that is not blocked by preview, similar, or request sections."
		)
		assertTrue(
			corePublish > coreFetch && corePublish < sectionFetch,
			"Fresh Aurral profile rows must render from the core snapshot before starting or awaiting independent section refreshes."
		)
		assertFalse(
			"primaryEnrichmentDeferred = primaryAurralArtist?.let" in loadAurralEnrichment,
			"Artist detail must not start by awaiting the full combined Aurral enrichment payload before rendering the profile."
		)
	}

	@Test
	fun artistDetailAurralSectionsRefreshIndependentlyAfterCoreProfile() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt"
		)
		val start = source.indexOf("private fun loadAurralEnrichment(")
		val end = source.indexOf("private fun applyAurralEnrichmentSnapshot", start)
		val loadAurralEnrichment = source.substring(start, end)
		val corePublish = loadAurralEnrichment.indexOf("applyAurralCoreEnrichmentSnapshot(")
		val previewFetch = loadAurralEnrichment.indexOf("getArtistPreviewTracks(")
		val similarFetch = loadAurralEnrichment.indexOf("getArtistSimilarArtists(")
		val requestFetch = loadAurralEnrichment.indexOf("getArtistAlbumRequests(")

		assertTrue(
			previewFetch > corePublish,
			"Artist detail preview tracks must refresh independently after the core Aurral profile renders."
		)
		assertTrue(
			similarFetch > corePublish,
			"Artist detail similar artists must refresh independently after the core Aurral profile renders."
		)
		assertTrue(
			requestFetch > corePublish,
			"Artist detail request/download status must refresh independently after the core Aurral profile renders."
		)
		assertFalse(
			"getArtistEnrichment(" in loadAurralEnrichment,
			"Artist detail must not call the combined full Aurral enrichment endpoint after the core profile is available."
		)
	}

	@Test
	fun artistDetailAurralSectionsRefreshWithResolvedCoreArtistIdentity() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt"
		)
		val start = source.indexOf("private fun loadAurralEnrichment(")
		val end = source.indexOf("private fun applyAurralEnrichmentSnapshot", start)
		val loadAurralEnrichment = source.substring(start, end)
		val resolvedArtist = loadAurralEnrichment.indexOf("val resolvedAurralArtist =")
		val requestRefresh = loadAurralEnrichment.indexOf("getArtistAlbumRequests(resolvedAurralArtist)")
		val previewRefresh = loadAurralEnrichment.indexOf("getArtistPreviewTracks(resolvedAurralArtist)")
		val similarRefresh = loadAurralEnrichment.indexOf("getArtistSimilarArtists(resolvedAurralArtist)")

		assertTrue(
			resolvedArtist >= 0,
			"Artist detail must canonicalize the Aurral artist identity from the core profile before refreshing sections."
		)
		assertTrue(
			requestRefresh > resolvedArtist,
			"Aurral request status must refresh with the core profile's resolved artist MBID/name."
		)
		assertTrue(
			previewRefresh > resolvedArtist,
			"Aurral preview tracks must refresh with the core profile's resolved artist MBID/name."
		)
		assertTrue(
			similarRefresh > resolvedArtist,
			"Aurral similar artists must refresh with the core profile's resolved artist MBID/name."
		)
	}

	@Test
	fun artistDetailAurralDiscoveryIdentityLookupUsesBaseDiscovery() {
		val source = commonMain(
			"paige/navic/ui/screens/artist/viewmodels/ArtistDetailViewModel.kt"
		)
		val start = source.indexOf("private fun loadAurralEnrichment(")
		val end = source.indexOf("private fun applyAurralEnrichmentSnapshot", start)
		val loadAurralEnrichment = source.substring(start, end)

		assertTrue(
			"getDiscoveryBase()" in loadAurralEnrichment,
			"Artist detail should use base Aurral discovery for identity candidates and recommendations."
		)
		assertFalse(
			"getDiscovery(hydrateMissingImages = false)" in loadAurralEnrichment,
			"Artist detail identity lookup must not wait for aggregate discovery optional sections."
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
	fun aurralLibraryDiscoveryRefreshDoesNotEscalateToHydratedDiscovery() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralHubViewModel.kt")

		assertTrue(
			"loadServiceStatus(refreshDiscovery = false)" in source,
			"refreshDiscovery already loaded the requested discovery mode; service status must not immediately re-run the heavier hydrated discovery path."
		)
		assertTrue(
			"refreshDiscovery: Boolean = false" in source &&
				"if (refreshDiscovery) {" in source,
			"Service status should be cheap by default, with discovery refresh remaining an explicit opt-in."
		)
	}

	@Test
	fun sharedImageMemoryCacheIsBoundedForArtworkHeavyTabs() {
		val source = commonMain("paige/navic/di/SingletonImageLoaderInit.kt")

		assertTrue(
			"SHARED_IMAGE_MEMORY_CACHE_PERCENT" in source,
			"The shared Coil cache should use an explicit reviewed limit; a raw percentage makes tablet artwork grids risky."
		)
		assertFalse(
			".maxSizePercent(context, 0.25)" in source,
			"Artwork-heavy Library scrolling must not reserve 25% of the process memory class for decoded image cache."
		)
	}

	@Test
	fun coverArtExpectedFailuresDoNotSpamThrowableStacksWhileScrolling() {
		val source = commonMain("paige/navic/ui/components/common/CoverArt.kt")

		assertTrue(
			Regex("""if \(imageDiagnosticLabel != null\) \{\s+Logger\.w\(""").containsMatchIn(source),
			"CoverArt should only log image-load failures when an explicit diagnostic label is present."
		)
		assertFalse(
			"coverArtFailureThrowable(" in source,
			"Normal artwork failures should not call a helper that still evaluates a scroll-time log path."
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
	fun aurralArtistPagePublishesFirstResolvedFacetsWithoutFullEnrichmentGate() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralArtistScreen.kt")
		val start = source.indexOf("LaunchedEffect(route, configured)")
		val end = source.indexOf("val monitorWaitingMessage", start)
		val loadBlock = source.substring(start, end)
		val monitoringFetch = loadBlock.indexOf("getArtistMonitoring(artist)")
		val coreFetch = loadBlock.indexOf("getArtistCoreEnrichment(artist)")
		val corePublish = loadBlock.indexOf("withCoreEnrichment(")
		val requestRefresh = loadBlock.indexOf("getArtistAlbumRequests(resolvedArtist)")
		val previewRefresh = loadBlock.indexOf("getArtistPreviewTracks(resolvedArtist)")
		val similarRefresh = loadBlock.indexOf("getArtistSimilarArtists(resolvedArtist)")

		assertFalse(
			"getArtistEnrichment(" in loadBlock,
			"Aurral artist page must not wait for the combined full enrichment payload before publishing page state."
		)
		assertTrue(
			monitoringFetch >= 0 && monitoringFetch < coreFetch,
			"Aurral artist monitoring should start as an independent focused lookup before profile enrichment sections."
		)
		assertTrue(
			coreFetch >= 0 && corePublish > coreFetch,
			"Aurral artist page must publish the core profile/release rows as soon as the core enrichment resolves."
		)
		assertTrue(
			requestRefresh > corePublish && previewRefresh > corePublish && similarRefresh > corePublish,
			"Aurral artist requests, preview tracks, and similar artists should refresh independently after the core profile renders."
		)
	}

	@Test
	fun aurralMissingAlbumPagePublishesAlbumIdentityWithoutFullEnrichmentGate() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralMissingAlbumScreen.kt")
		val start = source.indexOf("LaunchedEffect(route, configured)")
		val end = source.indexOf("val aurralMissingAlbumIntegrationIndicators", start)
		val loadBlock = source.substring(start, end)
		val coreFetch = loadBlock.indexOf("getArtistCoreEnrichment(localArtist)")
		val corePublish = loadBlock.indexOf("withCoreReleaseGroup(")
		val requestRefresh = loadBlock.indexOf("getArtistAlbumRequests(resolvedArtist)")
		val previewRefresh = loadBlock.indexOf("getArtistPreviewTracks(resolvedArtist)")
		val coverRefresh = loadBlock.indexOf("getReleaseGroupCoverImageUrl(")

		assertFalse(
			"getArtistEnrichment(" in loadBlock,
			"Aurral missing-album page must not block album identity, cover seed, or request progress on full artist enrichment."
		)
		assertTrue(
			coreFetch >= 0 && corePublish > coreFetch,
			"Aurral missing-album page should publish the focused release-group snapshot before optional sections finish."
		)
		assertTrue(
			requestRefresh > corePublish && previewRefresh > corePublish && coverRefresh > corePublish,
			"Missing-album progress, preview tracks, and cover fallback should refresh independently after the core release group resolves."
		)
	}

	@Test
	fun aurralHubDiscoveryPublishesBaseBeforeOptionalSections() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralHubViewModel.kt")
		val start = source.indexOf("private suspend fun loadDiscovery(")
		val end = source.indexOf("private suspend fun loadStationPlaylists", start)
		val loadDiscovery = source.substring(start, end)
		val baseFetch = loadDiscovery.indexOf("repository.getDiscoveryBase()")
		val basePublish = loadDiscovery.indexOf("UiState.Success(it)")
		val optionalRefresh = loadDiscovery.indexOf("loadDiscoverySupplement(")

		assertTrue(
			baseFetch >= 0 && basePublish > baseFetch,
			"Aurral lightweight discovery should publish the base discovery summary before recently-added/recent-release sections."
		)
		assertTrue(
			optionalRefresh > basePublish &&
				"repository.getDiscoveryRecentlyAdded()" in source &&
				"repository.getDiscoveryRecentReleases()" in source,
			"Aurral discovery optional sections should refresh after base discovery is already visible."
		)
		assertFalse(
			"repository.getLibraryDiscovery()" in loadDiscovery,
			"Lightweight Aurral discovery should use the focused base endpoint instead of the aggregate discovery payload."
		)
	}

	@Test
	fun aurralAcquisitionRefreshesAvoidFullServiceStatus() {
		val repositorySource = commonMain("paige/navic/domain/repositories/AurralRepository.kt")
		val albumSource = commonMain("paige/navic/ui/screens/album/viewmodels/AlbumListViewModel.kt")
		val collectionSource = commonMain("paige/navic/ui/screens/collection/viewmodels/CollectionDetailViewModel.kt")
		val refreshAlbumRequests = repositorySource.substring(
			repositorySource.indexOf("suspend fun refreshAlbumRequests()"),
			repositorySource.indexOf(
				"suspend fun getActivityStatus()",
				repositorySource.indexOf("suspend fun refreshAlbumRequests()")
			)
		)

		assertTrue(
			"getAcquisitionRequests()" in refreshAlbumRequests,
			"Album and collection acquisition chips need only request status; repository refresh must call the focused request path."
		)
		assertFalse(
			"getServiceStatus()" in refreshAlbumRequests,
			"Album and collection acquisition chips must not wait for health/auth/Weekly Flow service status."
		)
		assertTrue(
			"aurralRepository.refreshAlbumRequests()" in albumSource &&
				"aurralRepository.refreshAlbumRequests()" in collectionSource,
			"Album and collection screens should keep using the shared focused acquisition refresh."
		)
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
	fun aurralDiscoverListProjectionRunsOffTheUiDispatcher() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralDiscoverListScreen.kt")

		assertTrue(
			"val projectedDiscovery by produceState(" in source &&
				"withContext(Dispatchers.Default)" in source,
			"Aurral Discover should project artist/tag rows off the UI dispatcher; tag and image-cache resolution can be large."
		)
		assertFalse(
			"val artistPhotoCacheEntries = cachedArtistPhotos.map" in source,
			"Aurral Discover must not map the full artist-photo cache directly during composition."
		)
	}

	@Test
	fun aurralHubArtistPhotoProjectionRunsOffTheUiDispatcher() {
		val source = commonMain("paige/navic/ui/screens/aurral/AurralHubScreen.kt")

		assertTrue(
			"produceState<List<ArtistHeaderImageCacheEntry>>" in source,
			"Aurral Hub should produce projected artist-photo cache entries asynchronously."
		)
		assertTrue(
			"withContext(Dispatchers.Default)" in source,
			"Aurral Hub artist-photo cache projection is CPU work and should run off the UI dispatcher."
		)
		assertFalse(
			"val artistPhotoCacheEntries = cachedArtistPhotos.map" in source,
			"Aurral Hub must not map the full artist-photo cache directly during composition."
		)
	}

	@Test
	fun offlineCoverCacheDoesNotDecodeOriginalArtworkSize() {
		val source = commonMain("paige/navic/domain/manager/DownloadManager.kt")
		val start = source.indexOf("private suspend fun cacheCoverArt(")
		val end = source.indexOf("private suspend fun cacheAlbumCoverArt(", start)
		val cacheCoverArt = source.substring(start, end)

		assertFalse(
			"Size.ORIGINAL" in cacheCoverArt,
			"Offline cover cache warming must not decode original-size artwork; the server URL already carries the configured cover size."
		)
		assertTrue(
			".diskCachePolicy(CachePolicy.ENABLED)" in cacheCoverArt,
			"Offline cover cache warming should keep priming the Coil disk cache."
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
	fun artistDetailAurralAlbumRowsDoNotCollectDownloadFlowsPerRow() {
		val source = commonMain("paige/navic/ui/screens/artist/ArtistDetailScreen.kt")
		val start = source.indexOf("ArtCarousel(\n\t\t\t\t\t\t\t\tstringResource(Res.string.title_aurral_owned_partial_albums)")
		val end = source.indexOf("ArtCarousel(\n\t\t\t\t\t\t\t\tstringResource(Res.string.title_aurral_missing_albums)", start)
		val ownedAlbumCarousel = source.substring(start, end)

		assertFalse(
			"downloadManager\n\t\t\t\t\t\t\t\t\t\t\t.getCollectionDownloadStatus" in ownedAlbumCarousel,
			"Artist detail's Aurral-owned album carousel already has allDownloads from the ViewModel; " +
				"it should not create one download-status Flow collector per rendered album row."
		)
		assertTrue(
			"collectionDownloadStatus(" in ownedAlbumCarousel &&
				"allDownloads" in ownedAlbumCarousel,
			"Aurral-owned album rows should derive the selected sheet status from the existing download snapshot."
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

	@Test
	fun nowPlayingDynamicThemeUsesResolvedArtworkColorCache() {
		val source = commonMain("paige/navic/ui/navigation/NowPlayingScene.kt")

		assertTrue(
			"rememberResolvedArtworkColorScheme(" in source &&
				"playbackArtwork = playbackArtwork" in source,
			"Now Playing dynamic theming must use the same resolved Aurral/Navidrome artwork identity as playback rendering."
		)
		assertFalse(
			"HttpTimeout" in source,
			"Now Playing should not create a timeout-backed palette HTTP client; artwork color extraction must use the shared cached image pipeline."
		)
		assertFalse(
			"rememberNetworkLoader" in source || "rememberDominantColorState" in source,
			"Now Playing should not maintain a second uncached network palette pipeline."
		)
	}

	@Test
	fun artworkColorCacheIsStoredInCacheDatabase() {
		val databaseSource = commonMain("paige/navic/data/database/CacheDatabase.kt")
		val databaseModuleSource = commonMain("paige/navic/di/DatabaseModule.kt")
		val managerModuleSource = commonMain("paige/navic/di/ManagerModule.kt")

		assertTrue(
			"version = 20" in databaseSource,
			"Adding the artwork color cache must bump CacheDatabase from the fork's version 19 to 20."
		)
		assertTrue(
			"ArtworkColorEntity::class" in databaseSource &&
				"abstract fun artworkColorDao(): ArtworkColorDao" in databaseSource,
			"CacheDatabase must persist color extraction results by resolved artwork identity."
		)
		assertTrue(
			"single { get<CacheDatabase>().artworkColorDao() }" in databaseModuleSource,
			"ArtworkColorDao should be available through DI."
		)
		assertTrue(
			"singleOf(::ArtworkColorManager)" in managerModuleSource,
			"ArtworkColorManager should be shared instead of each composable owning its own cache."
		)
	}

	@Test
	fun artistAndCollectionDynamicThemeDoNotBypassResolvedArtwork() {
		val artistSource = commonMain("paige/navic/ui/screens/artist/ArtistDetailScreen.kt")
		val collectionSource = commonMain("paige/navic/ui/screens/collection/CollectionDetailScreen.kt")

		assertTrue(
			"rememberAurralFirstArtistArtworkUiState(" in artistSource &&
				"rememberResolvedArtworkColorScheme(" in artistSource &&
				"playbackArtwork = headingArtwork" in artistSource,
			"Artist detail must keep the fork's Aurral-first heading artwork resolver as the source for dynamic theming."
		)
		assertTrue(
			"rememberResolvedArtworkColorScheme(" in collectionSource &&
				"displayedCollection?.collectionArtworkRenderSpec(" in collectionSource &&
				"externalImageUrl = headerProjection?.coverUrl" in collectionSource &&
				"coverArtId = collectionArtworkSpec?.coverArtId" in collectionSource &&
				"imageUrl = collectionArtworkSpec?.imageUrl" in collectionSource &&
				"imageCacheKey = collectionArtworkSpec?.imageCacheKey" in collectionSource &&
				"imageRequestHeaders = collectionArtworkSpec?.imageRequestHeaders" in collectionSource,
			"Collection detail dynamic theming should use the resolved shared ArtworkRenderSpec, including Aurral external artwork identity and request metadata."
		)
	}

	@Test
	fun artworkColorSchemeSourceHandlesExternalArtworkAndFailures() {
		val source = commonMain("paige/navic/ui/components/common/ResolvedArtworkColorScheme.kt")

		assertTrue(
			"artworkColorCacheKey(" in source &&
				"imageCacheKey = playbackArtwork.imageCacheKey" in source &&
				"imageUrl = playbackArtwork.imageUrl" in source,
			"Dynamic color cache keys must include resolved external artwork identities, not only native coverArtId."
		)
		assertTrue(
			"runCatching" in source,
			"Palette extraction failures should fall back to the normal theme instead of crashing the screen."
		)
		assertFalse(
			"HttpTimeout" in source,
			"Resolved artwork color extraction must not implement cancellation timeouts."
		)
	}

	private fun commonMain(path: String): String =
		File("src/commonMain/kotlin/$path").readText()
}
