package paige.navic.ui.screens.aurral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_monitor_artist
import navic.composeapp.generated.resources.action_view_artist
import navic.composeapp.generated.resources.info_aurral_artist_in_library
import navic.composeapp.generated.resources.info_aurral_external_artist
import navic.composeapp.generated.resources.info_aurral_loading_catalog
import navic.composeapp.generated.resources.info_aurral_match_percent
import navic.composeapp.generated.resources.info_aurral_monitor_waiting
import navic.composeapp.generated.resources.info_aurral_no_artist_albums
import navic.composeapp.generated.resources.info_aurral_no_album_previews
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_aurral_preview_tracks
import navic.composeapp.generated.resources.title_aurral_recommendations
import navic.composeapp.generated.resources.title_similar_artists
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralArtistAlbumRow
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralMissingAlbumRow
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.aurralArtistAlbumRows
import paige.navic.domain.models.aurralMissingAlbumRows
import paige.navic.domain.models.aurralSimilarArtistRows
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.models.sortedByAlbumYearDescending
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.aurralArtistMonitoringConfirmationItem
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Note
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.AurralActionIcon
import paige.navic.ui.components.common.AurralActionIconOverlay
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.AurralIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.aurralAlbumArtworkRenderSpec
import paige.navic.ui.components.common.generatedArtworkSpec
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtCarousel
import paige.navic.ui.components.layouts.ArtCarouselItem
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.artistHeaderImageCacheIndex
import paige.navic.ui.screens.artist.artistImageUrlForExternalArtworkPolicy
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.withCachedArtistPhoto
import paige.navic.ui.screens.artist.components.AurralPreviewTracks
import paige.navic.ui.screens.artist.isAurralNameLookupArtistId
import paige.navic.ui.theme.defaultFont

@Composable
fun AurralArtistScreen(route: Screen.AurralArtist) {
	val preferenceManager = koinInject<PreferenceManager>()
	val artistDao = koinInject<ArtistDao>()
	val albumDao = koinInject<AlbumDao>()
	val artistPhotoCacheDao = koinInject<ArtistPhotoCacheDao>()
	val aurralRepository = koinInject<AurralRepository>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val snackbarState = LocalSnackbarState.current
	val scope = rememberCoroutineScope()
	val confirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()
	var state by remember(route) {
		mutableStateOf(
			AurralArtistUiState(
				artist = route.toDomainArtist(),
				heroImageUrl = route.imageUrl,
				loading = true
			)
		)
	}
	val configured = shouldLoadAurralUi(
		aurralEnabled = preferenceManager.aurralEnabled,
		baseUrl = preferenceManager.aurralBaseUrl
	)

	LaunchedEffect(route, configured) {
		val localCatalog = withContext(Dispatchers.IO) {
			val artistPhotoCacheEntries = artistPhotoCacheDao.getArtistPhotoCache()
				.map { entry -> entry.toArtistHeaderImageCacheEntry() }
			val artistPhotoCacheIndex = artistHeaderImageCacheIndex(artistPhotoCacheEntries)
			val localArtists = artistDao.getAllArtistsList().map { artist ->
				artist.toDomainModel().withCachedArtistPhoto(
					index = artistPhotoCacheIndex,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			}
			val localArtist = localArtists.findAurralLocalArtist(route.artistMbid, route.artistName)
			val artist = localArtist ?: route.toDomainArtist()
			val localAlbums = when {
				localArtist != null -> albumDao.getAlbumsByArtist(localArtist.id).firstOrNull().orEmpty()
				else -> albumDao.getAlbumsByArtistName(route.artistName).firstOrNull().orEmpty()
			}.map { it.toDomainModel() }.sortedByAlbumYearDescending()
			AurralArtistLocalCatalog(
				artist = artist,
				localArtist = localArtist,
				localArtists = localArtists,
				localAlbums = localAlbums,
				artistPhotoCacheEntries = artistPhotoCacheEntries
			)
		}
		val artist = localCatalog.artist

		state = state.copy(
			artist = artist,
			localArtist = localCatalog.localArtist,
			localAlbums = localCatalog.localAlbums,
			heroImageUrl = route.imageUrl ?: localCatalog.localArtist?.let { artist ->
				artistImageUrlForExternalArtworkPolicy(
					artist = artist,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			},
			loading = configured,
			error = null
		)

		if (!configured) {
			state = state.copy(
				enrichment = null,
				missingAlbums = emptyList(),
				recommendedAlbums = emptyList(),
				similarArtists = emptyList(),
				previewTracks = emptyList(),
				monitorConfirmed = false,
				loading = false,
				error = null
			)
			return@LaunchedEffect
		}

		val enrichmentResult = withContext(Dispatchers.IO) {
			aurralRepository.getArtistEnrichment(artist)
		}
		val discovery = if (enrichmentResult.isSuccess) {
			withContext(Dispatchers.IO) {
				aurralRepository.getDiscovery(hydrateMissingImages = false)
					.getOrNull()
			}
		} else {
			null
		}
		enrichmentResult
			.onSuccess { enrichment ->
				val recommendedAlbums = discovery
					?.let {
						aurralRecommendedAlbumsForArtist(
							discovery = it,
							artistMbid = route.artistMbid,
							artistName = route.artistName
						)
					}
					.orEmpty()
				state = state.copy(
					artist = if (localCatalog.localArtist == null && enrichment != null) {
						DomainArtist(
							id = "aurral-${enrichment.artistMbid}",
							name = enrichment.artistName.ifBlank { route.artistName },
							musicBrainzId = enrichment.artistMbid.ifBlank { null },
							artistImageUrl = state.heroImageUrl
						)
					} else {
						state.artist
					},
					enrichment = enrichment,
					missingAlbums = enrichment?.let { aurralMissingAlbumRows(it, localCatalog.localAlbums) }.orEmpty(),
					recommendedAlbums = recommendedAlbums,
					similarArtists = enrichment?.let {
						aurralSimilarArtistRows(
							enrichment = it,
							allLocalArtists = localCatalog.localArtists,
							localSimilarArtists = emptyList(),
							externalArtists = discovery?.let { discoverySummary ->
								aurralSimilarArtistImageCandidates(
									discovery = discoverySummary,
									artistPhotoCacheEntries = localCatalog.artistPhotoCacheEntries,
									artistArtworkPriority = preferenceManager.artistArtworkPriority,
									externalArtworkEnabled = preferenceManager.aurralEnabled
								)
							}.orEmpty()
						)
					}.orEmpty(),
					previewTracks = enrichment?.previewTracks.orEmpty(),
					monitorConfirmed = enrichment?.monitored == true,
					loading = false,
					error = null
				)
			}
			.onFailure { error ->
				state = state.copy(loading = false, error = error)
			}
	}

	val monitorWaitingMessage = stringResource(Res.string.info_aurral_monitor_waiting)
	val aurralArtistIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = configured && (state.loading || state.monitoring)
	)
	val scrollState = rememberScrollState()
	BackToTopScrollHandler(scrollState)
	AurralConfirmationQueueSnackbar(aurralRepository)

	Scaffold(
		topBar = {
			NestedTopBar(
				title = {
					Text(
						text = route.artistName,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		val baseUrl = preferenceManager.aurralBaseUrl
		val requestHeaders = preferenceManager.aurralRequestHeadersMap()
		val heroHeaders = aurralRequestHeadersForUrl(
			baseUrl = baseUrl,
			imageUrl = state.heroImageUrl,
			requestHeaders = requestHeaders
		)
		val albumRows = remember(state.localAlbums, state.missingAlbums) {
			aurralArtistAlbumRows(
				localAlbums = state.localAlbums,
				missingAlbums = state.missingAlbums
			).toImmutableList()
		}

		Box(Modifier.fillMaxSize()) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(top = innerPadding.calculateTopPadding())
					.verticalScroll(scrollState)
					.padding(
						top = 20.dp,
						bottom = innerPadding.calculateBottomPadding() + 32.dp
					),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				AurralArtistHero(
					artist = state.artist,
					localArtist = state.localArtist,
					externalArtworkEnabled = preferenceManager.aurralEnabled,
					imageUrl = state.heroImageUrl,
					imageRequestHeaders = heroHeaders
				)
				AurralArtistActions(
					localArtist = state.localArtist,
					aurralConfigured = configured,
					monitoring = state.monitoring,
					monitorPending = aurralArtistMonitoringConfirmationItem(
						queue = confirmationQueue,
						artistMbid = state.artist.musicBrainzId
					)?.status == AurralConfirmationStatus.Pending,
					monitorConfirmed = state.monitorConfirmed,
					onOpenLocalArtist = { localArtist ->
						backStack.add(Screen.ArtistDetail(localArtist.id))
					},
					onMonitorArtist = {
						platformContext.clickSound()
						scope.launch {
							state = state.copy(monitoring = true, error = null)
							launch { snackbarState.showSnackbar(monitorWaitingMessage) }
							withContext(Dispatchers.IO) {
								aurralRepository.monitorArtist(state.artist)
							}
								.onSuccess {
									state = state.copy(error = null)
								}
								.onFailure { error -> state = state.copy(error = error) }
							state = state.copy(monitoring = false)
						}
					}
				)
				if (state.loading) {
					Text(
						text = stringResource(Res.string.info_aurral_loading_catalog),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 8.dp)
					)
					CircularProgressIndicator(
						modifier = Modifier
							.padding(top = 4.dp)
							.size(32.dp)
					)
				}
				ArtCarousel(
					title = stringResource(Res.string.title_aurral_recommendations),
					items = state.recommendedAlbums.toImmutableList()
				) { album ->
					AurralRecommendedAlbumItem(
						album = album,
						imageRequestHeaders = aurralRequestHeadersForUrl(
							baseUrl = baseUrl,
							imageUrl = album.coverUrl,
							requestHeaders = requestHeaders
						),
						onClick = {
							aurralAlbumSearchDestination(album)?.let(backStack::add)
						}
					)
				}
			ArtCarousel(
				title = stringResource(Res.string.title_albums),
				items = albumRows
			) { row ->
				when (row) {
					is AurralArtistAlbumRow.Local -> AurralArtistLocalAlbumItem(
						album = row.album,
						onClick = {
							backStack.add(
								aurralArtistLocalAlbumCollectionDetailRoute(
									row = row,
									tab = "artist",
									fallbackArtistMbid = route.artistMbid,
									fallbackArtistName = state.artist.name
								) ?: Screen.CollectionDetail(row.album.id, "artist")
							)
						}
					)

					is AurralArtistAlbumRow.Missing -> {
						val missingAlbum = row.album
						val coverUrl = missingAlbum.coverUrl
						AurralArtistMissingAlbumItem(
							row = missingAlbum,
							coverUrl = coverUrl,
							imageRequestHeaders = aurralRequestHeadersForUrl(
								baseUrl = baseUrl,
								imageUrl = coverUrl,
								requestHeaders = requestHeaders
							),
							onClick = {
								backStack.add(
									Screen.AurralMissingAlbum(
										artistId = state.localArtist?.id ?: route.artistMbid,
										artistName = state.artist.name,
										artistMbid = route.artistMbid,
										releaseGroupId = missingAlbum.releaseGroup.id,
										title = missingAlbum.title,
										year = missingAlbum.year,
										primaryType = missingAlbum.releaseGroup.primaryType,
										coverUrl = coverUrl,
										requestStatus = missingAlbum.requestStatus
									)
								)
							}
						)
					}
				}
			}
			if (!state.loading && albumRows.isEmpty()) {
				ContentUnavailable(
					icon = Icons.Outlined.Note,
					label = stringResource(Res.string.info_aurral_no_artist_albums),
					modifier = Modifier.padding(horizontal = 24.dp)
				)
			}
			if (shouldShowAurralArtistGlobalPreviewRow(state.previewTracks)) {
				AurralPreviewTracks(
					title = stringResource(Res.string.title_aurral_preview_tracks),
					tracks = state.previewTracks.toImmutableList(),
					modifier = Modifier.fillMaxWidth()
				)
			}
			if (state.similarArtists.isNotEmpty()) {
				ArtCarousel(
					title = stringResource(Res.string.title_similar_artists),
					items = state.similarArtists.toImmutableList()
				) { row ->
					AurralArtistSimilarArtistItem(
						row = row,
						imageRequestHeaders = aurralRequestHeadersForUrl(
							baseUrl = baseUrl,
							imageUrl = row.artist.imageUrl,
							requestHeaders = requestHeaders
						),
						externalArtworkEnabled = preferenceManager.aurralEnabled,
						onClickLocalArtist = { artistId ->
							backStack.add(Screen.ArtistDetail(artistId))
						},
						onClickAurralArtist = {
							backStack.add(
								Screen.AurralArtist(
									artistMbid = row.artist.id,
									artistName = row.artist.name,
									imageUrl = row.artist.imageUrl
								)
							)
						}
					)
				}
			}
			Spacer(Modifier.height(12.dp))
		}
			IntegrationLoadingIndicatorStrip(
				indicators = aurralArtistIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = aurralArtistIntegrationIndicators,
					relevantServices = AurralIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = state.error,
		onClearError = { state = state.copy(error = null) }
	)
}

@Composable
private fun AurralArtistHero(
	artist: DomainArtist,
	localArtist: DomainArtist?,
	externalArtworkEnabled: Boolean,
	imageUrl: String?,
	imageRequestHeaders: Map<String, String>
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier.fillMaxWidth()
	) {
		CoverArt(
			coverArtId = aurralArtistHeroCoverArtId(
				localArtist = localArtist,
				externalArtworkEnabled = externalArtworkEnabled
			),
			imageUrl = imageUrl,
			imageCacheKey = imageUrl?.let { "aurral-artist-${artist.musicBrainzId.orEmpty()}" },
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = artist.name,
			generatedArtwork = generatedArtworkSpec(
				kindLabel = "Artist",
				primaryLabel = artist.name,
				seed = artist.musicBrainzId ?: artist.id,
				variant = GeneratedArtworkVariant.DetailHero
			),
			modifier = Modifier
				.widthIn(max = 340.dp)
				.padding(horizontal = 76.dp)
				.aspectRatio(1f)
				.clip(RoundedCornerShape(24.dp)),
			shape = RectangleShape
		)
		Text(
			text = artist.name,
			style = MaterialTheme.typography.headlineSmall,
			textAlign = TextAlign.Center,
			fontFamily = defaultFont(round = 100f),
			modifier = Modifier
				.padding(horizontal = 31.dp)
				.padding(top = 16.dp)
		)
		Text(
			text = if (localArtist != null) {
				stringResource(Res.string.info_aurral_artist_in_library)
			} else {
				stringResource(Res.string.info_aurral_external_artist)
			},
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			style = MaterialTheme.typography.bodyMedium,
			fontFamily = defaultFont(grade = 100, round = 100f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 31.dp)
		)
	}
}

@Composable
private fun AurralArtistActions(
	localArtist: DomainArtist?,
	aurralConfigured: Boolean,
	monitoring: Boolean,
	monitorPending: Boolean,
	monitorConfirmed: Boolean,
	onOpenLocalArtist: (DomainArtist) -> Unit,
	onMonitorArtist: () -> Unit
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 31.dp),
		horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
		verticalAlignment = Alignment.CenterVertically
	) {
		localArtist?.let { artist ->
			Button(
				modifier = Modifier.weight(1f),
				onClick = { onOpenLocalArtist(artist) }
			) {
				Text(stringResource(Res.string.action_view_artist))
			}
		}
		if (!aurralConfigured) return@Row
		OutlinedButton(
			modifier = Modifier.weight(1f),
			onClick = onMonitorArtist,
			enabled = !monitoring && !monitorPending && !monitorConfirmed
		) {
			AurralActionIcon(
				overlay = when {
					monitoring || monitorPending -> AurralActionIconOverlay.Progress
					monitorConfirmed -> AurralActionIconOverlay.None
					else -> AurralActionIconOverlay.Crossed
				},
				contentDescription = null,
				size = 22.dp,
				tint = if (monitoring || monitorPending || monitorConfirmed) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				},
				overlayColor = MaterialTheme.colorScheme.primary,
				progressColor = MaterialTheme.colorScheme.primary,
				modifier = Modifier.padding(end = 4.dp)
			)
			Text(
				text = stringResource(Res.string.action_monitor_artist),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralArtistLocalAlbumItem(
	album: DomainAlbum,
	onClick: () -> Unit
) {
	ArtCarouselItem(
		coverArtId = album.coverArtId,
		title = album.name,
		subtitle = album.year?.toString(),
		ownershipStatus = AurralOwnershipStatus.Owned,
		contentDescription = album.name,
		onClick = onClick
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralArtistMissingAlbumItem(
	row: AurralMissingAlbumRow,
	coverUrl: String?,
	imageRequestHeaders: Map<String, String>,
	onClick: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val colorFilter = remember {
		ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
	}
	val artworkSpec = aurralAlbumArtworkRenderSpec(
		id = row.releaseGroup.id,
		title = row.title,
		coverUrl = coverUrl,
		primaryType = row.releaseGroup.primaryType,
		imageRequestHeaders = imageRequestHeaders,
		variant = GeneratedArtworkVariant.CarouselCard
	)

	Column(Modifier.fillMaxWidth()) {
		Box(Modifier.fillMaxWidth()) {
			CoverArt(
				coverArtId = artworkSpec.coverArtId,
				imageUrl = artworkSpec.imageUrl,
				imageCacheKey = artworkSpec.imageCacheKey,
				imageRequestHeaders = artworkSpec.imageRequestHeaders,
				contentDescription = artworkSpec.contentDescription,
				generatedArtwork = artworkSpec.generatedArtwork,
				modifier = Modifier.fillMaxWidth(),
				shape = RectangleShape,
				colorFilter = colorFilter,
				onClick = {
					platformContext.clickSound()
					onClick()
				}
			)
			row.acquisitionProgress?.let { progress ->
				AurralAcquisitionProgressBar(
					progress = progress,
					modifier = Modifier.align(Alignment.BottomCenter)
				)
			}
			AurralOwnershipStatusDot(
				status = aurralMissingAlbumOwnershipStatus(row),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(8.dp)
			)
		}
		Text(
			text = row.title,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
		)
		row.year?.let { year ->
			Text(
				text = year,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				modifier = Modifier.padding(start = 4.dp, end = 4.dp)
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralArtistSimilarArtistItem(
	row: AurralSimilarArtistRow,
	imageRequestHeaders: Map<String, String>,
	externalArtworkEnabled: Boolean,
	onClickLocalArtist: (String) -> Unit,
	onClickAurralArtist: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val localArtistId = row.localArtistId
	val subtitle = row.matchPercent?.let {
		stringResource(Res.string.info_aurral_match_percent, it)
	} ?: if (row.inLibrary) null else stringResource(Res.string.info_aurral_external_artist)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.alpha(if (row.inLibrary) 1f else .62f)
	) {
		CoverArt(
			coverArtId = row.localCoverArtId.takeUnless { externalArtworkEnabled },
			imageUrl = row.artist.imageUrl,
			imageCacheKey = "aurral-similar-artist-${row.artist.id}",
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = row.artist.name,
			generatedArtwork = generatedArtworkSpec(
				kindLabel = "Artist",
				primaryLabel = row.artist.name,
				seed = row.artist.id,
				variant = GeneratedArtworkVariant.CarouselCard
			),
			modifier = Modifier.fillMaxWidth(),
			shape = RectangleShape,
			onClick = {
				platformContext.clickSound()
				if (localArtistId != null) {
					onClickLocalArtist(localArtistId)
				} else {
					onClickAurralArtist()
				}
			}
		)
		Text(
			text = row.artist.name,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
		)
		subtitle?.let {
			Text(
				text = it,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(start = 4.dp, end = 4.dp)
			)
		}
	}
}

private fun Screen.AurralArtist.toDomainArtist(): DomainArtist =
	artistMbid.trim()
		.takeIf { it.isNotEmpty() && !isAurralNameLookupArtistId(it) }
		.let { resolvedMbid ->
			DomainArtist(
				id = resolvedMbid?.let { "aurral-$it" } ?: "aurral-${artistName.normalizedAurralArtistName()}",
				name = artistName.trim().takeIf { it.isNotEmpty() } ?: artistMbid,
				musicBrainzId = resolvedMbid
			)
		}

private fun List<DomainArtist>.findAurralLocalArtist(
	artistMbid: String,
	artistName: String
): DomainArtist? {
	val normalizedMbid = artistMbid.trim().lowercase()
		.takeIf { it.isNotEmpty() && !isAurralNameLookupArtistId(it) }
	val normalizedName = artistName.normalizedAurralArtistName()
	return firstOrNull { artist ->
		normalizedMbid != null && artist.musicBrainzId?.trim()?.lowercase() == normalizedMbid
	} ?: firstOrNull { artist ->
		artist.name.normalizedAurralArtistName() == normalizedName
	}
}

private fun String.normalizedAurralArtistName(): String =
	trim()
		.lowercase()
		.replace(Regex("""\s+"""), " ")

private data class AurralArtistUiState(
	val artist: DomainArtist,
	val localArtist: DomainArtist? = null,
	val heroImageUrl: String? = null,
	val localAlbums: List<DomainAlbum> = emptyList(),
	val missingAlbums: List<AurralMissingAlbumRow> = emptyList(),
	val recommendedAlbums: List<AurralAlbumSearchItem> = emptyList(),
	val similarArtists: List<AurralSimilarArtistRow> = emptyList(),
	val previewTracks: List<AurralPreviewTrack> = emptyList(),
	val enrichment: AurralArtistEnrichment? = null,
	val loading: Boolean = false,
	val monitoring: Boolean = false,
	val monitorConfirmed: Boolean = false,
	val error: Throwable? = null
)

private data class AurralArtistLocalCatalog(
	val artist: DomainArtist,
	val localArtist: DomainArtist?,
	val localArtists: List<DomainArtist>,
	val localAlbums: List<DomainAlbum>,
	val artistPhotoCacheEntries: List<paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry>
)
