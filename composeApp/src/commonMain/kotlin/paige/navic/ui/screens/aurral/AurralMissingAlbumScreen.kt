package paige.navic.ui.screens.aurral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_acquire_album
import navic.composeapp.generated.resources.info_aurral_no_album_previews
import navic.composeapp.generated.resources.info_aurral_request_status
import navic.composeapp.generated.resources.notice_aurral_album_requested
import navic.composeapp.generated.resources.title_aurral_missing_album
import navic.composeapp.generated.resources.title_aurral_preview_tracks
import navic.composeapp.generated.resources.title_more_by_artist
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.data.database.dao.AlbumDao
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAcquisitionProgress
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.aurralAlbumAcquisitionProgress
import paige.navic.domain.models.aurralOwnershipStatusForProgress
import paige.navic.domain.models.aurralPreviewTrackOwnershipStatus
import paige.navic.domain.models.aurralPreviewTracksForReleaseGroup
import paige.navic.domain.models.sortedByAlbumYearDescending
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralRepository
import paige.navic.data.remote.aurral.aurralRequestHeadersForUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.LibraryAdd
import paige.navic.icons.outlined.Note
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.AurralIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.aurralAlbumArtworkRenderSpec
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtCarousel
import paige.navic.ui.components.layouts.ArtCarouselItem
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.components.AurralPreviewTracks
import paige.navic.ui.theme.defaultFont
import paige.navic.util.core.Logger

internal enum class AurralMissingAlbumActionIcon {
	Acquire,
	Requesting,
	Requested,
	Play
}

internal data class AurralMissingAlbumActionState(
	val icon: AurralMissingAlbumActionIcon,
	val enabled: Boolean,
	val showSpinner: Boolean
)

internal fun aurralMissingAlbumActionState(
	progress: AurralAcquisitionProgress?,
	requesting: Boolean
): AurralMissingAlbumActionState =
	when {
		requesting -> AurralMissingAlbumActionState(
			icon = AurralMissingAlbumActionIcon.Requesting,
			enabled = false,
			showSpinner = true
		)
		progress?.completed == true -> AurralMissingAlbumActionState(
			icon = AurralMissingAlbumActionIcon.Play,
			enabled = false,
			showSpinner = false
		)
		progress?.active == true -> AurralMissingAlbumActionState(
			icon = AurralMissingAlbumActionIcon.Requested,
			enabled = false,
			showSpinner = false
		)
		else -> AurralMissingAlbumActionState(
			icon = AurralMissingAlbumActionIcon.Acquire,
			enabled = true,
			showSpinner = false
		)
	}

@Composable
fun AurralMissingAlbumScreen(route: Screen.AurralMissingAlbum) {
	val preferenceManager = koinInject<PreferenceManager>()
	val artistDao = koinInject<ArtistDao>()
	val albumDao = koinInject<AlbumDao>()
	val aurralRepository = koinInject<AurralRepository>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val snackbarState = LocalSnackbarState.current
	val scope = rememberCoroutineScope()
	val albumRequestedMessage = stringResource(Res.string.notice_aurral_album_requested)
	val releaseGroup = remember(route) {
		AurralReleaseGroup(
			id = route.releaseGroupId,
			title = route.title,
			firstReleaseDate = route.year,
			primaryType = route.primaryType,
			coverUrl = route.coverUrl
		)
	}
	var state by remember(route) {
		mutableStateOf(
			AurralMissingAlbumUiState(
				artist = DomainArtist(
					id = route.artistId,
					name = route.artistName,
					musicBrainzId = route.artistMbid
				),
				coverUrl = route.coverUrl,
				progress = route.requestStatus?.let(::aurralAcquisitionProgress),
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
			val localArtist = artistDao.getArtistById(route.artistId)?.toDomainModel()
				?: state.artist
			val localAlbums = albumDao.getAlbumsByArtist(localArtist.id).firstOrNull()
				?.takeIf { it.isNotEmpty() }
				?: albumDao.getAlbumsByArtistName(localArtist.name).firstOrNull()
				?: emptyList()
			AurralMissingAlbumLocalCatalog(
				artist = localArtist,
				moreAlbums = localAlbums
					.map { it.toDomainModel() }
					.sortedByAlbumYearDescending()
			)
		}
		val localArtist = localCatalog.artist
		state = state.copy(
			artist = localArtist,
			coverUrl = route.coverUrl,
			moreAlbums = localCatalog.moreAlbums,
			loading = configured,
			error = null
		)

		if (!configured) {
			state = state.copy(
				previewTracks = emptyList(),
				loading = false,
				error = null
			)
			return@LaunchedEffect
		}

		launch {
			withContext(Dispatchers.IO) {
				aurralRepository.getArtistCoreEnrichment(localArtist)
			}
				.onSuccess { enrichment ->
					val refreshedReleaseGroup = enrichment
						?.releaseGroups
						?.firstOrNull { it.id == releaseGroup.id }
						?: releaseGroup
					state = state.withCoreReleaseGroup(
						releaseGroup = refreshedReleaseGroup,
						route = route
					)
					if (enrichment == null) return@onSuccess
					val resolvedArtist = enrichment.toResolvedArtist(localArtist)
					launch {
						withContext(Dispatchers.IO) {
							aurralRepository.getArtistAlbumRequests(resolvedArtist)
						}
							.onSuccess { requests ->
								state = state.withAlbumRequests(
									releaseGroup = refreshedReleaseGroup,
									requests = requests
								)
							}
					}
					launch {
						withContext(Dispatchers.IO) {
							aurralRepository.getArtistPreviewTracks(resolvedArtist)
						}
							.onSuccess { previewTracks ->
								state = state.withPreviewTracks(
									releaseGroup = refreshedReleaseGroup,
									previewTracks = previewTracks
								)
							}
					}
					if (state.coverUrl.isNullOrBlank()) {
						launch {
							withContext(Dispatchers.IO) {
								aurralRepository.getReleaseGroupCoverImageUrl(
									releaseGroup = refreshedReleaseGroup,
									artistName = localArtist.name
								)
							}
								.onSuccess { coverUrl ->
									if (!coverUrl.isNullOrBlank()) {
										state = state.copy(coverUrl = coverUrl)
									}
								}
						}
					}
				}
				.onFailure { error ->
					state = state.copy(
						artist = localArtist,
						loading = false,
						error = error
					)
				}
		}
	}

	val aurralMissingAlbumIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = configured && (state.loading || state.requesting)
	)
	val scrollState = rememberScrollState()
	BackToTopScrollHandler(scrollState)

	Scaffold(
		topBar = {
			NestedTopBar(
				title = {
					Text(
						text = route.title.ifBlank {
							stringResource(Res.string.title_aurral_missing_album)
						},
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
		val imageRequestHeaders = aurralRequestHeadersForUrl(
			baseUrl = preferenceManager.aurralBaseUrl,
			imageUrl = state.coverUrl,
			requestHeaders = preferenceManager.aurralRequestHeadersMap()
		)
		Box(Modifier.fillMaxSize()) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(top = innerPadding.calculateTopPadding())
					.verticalScroll(scrollState)
					.padding(top = 20.dp, bottom = innerPadding.calculateBottomPadding() + 32.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				AurralMissingAlbumHero(
					route = route,
					coverUrl = state.coverUrl,
					imageRequestHeaders = imageRequestHeaders,
					progress = state.progress
				)
				AurralMissingAlbumActions(
					progress = state.progress,
					aurralConfigured = configured,
					requesting = state.requesting,
					onAcquire = {
						platformContext.clickSound()
						val requestArtist = state.artist
						state = state.copy(
							requesting = false,
							error = null,
							progress = aurralAcquisitionProgress("requested")
						)
						scope.launch {
							snackbarState.currentSnackbarData?.dismiss()
							snackbarState.showSnackbar(albumRequestedMessage)
						}
						scope.launch {
							withContext(Dispatchers.IO) {
								aurralRepository.requestAlbum(requestArtist, releaseGroup)
							}
								.onFailure { error ->
									Logger.w("AurralMissingAlbumScreen", "Aurral album request is still pending server-side", error)
								}
						}
					}
				)
				if (state.loading) {
					CircularProgressIndicator(
						modifier = Modifier
							.padding(top = 24.dp)
							.size(32.dp)
					)
				} else if (state.previewTracks.isNotEmpty()) {
					val fallbackTrackStatus = aurralOwnershipStatusForProgress(state.progress)
					AurralPreviewTracks(
						title = stringResource(Res.string.title_aurral_preview_tracks),
						tracks = state.previewTracks.toImmutableList(),
						modifier = Modifier.fillMaxWidth(),
						ownershipStatuses = state.previewTracks.associate { track ->
							track.id to aurralPreviewTrackOwnershipStatus(
								track = track,
								fallbackAlbumStatus = fallbackTrackStatus
							)
						}.toImmutableMap()
					)
				} else {
					ContentUnavailable(
						icon = Icons.Outlined.Note,
						label = stringResource(Res.string.info_aurral_no_album_previews),
						modifier = Modifier.padding(top = 24.dp)
					)
				}
				if (state.moreAlbums.isNotEmpty()) {
					ArtCarousel(
						title = stringResource(Res.string.title_more_by_artist, state.artist.name),
						items = state.moreAlbums.toImmutableList()
					) { album ->
						ArtCarouselItem(
							coverArtId = album.coverArtId,
							title = album.name,
							subtitle = album.year?.toString(),
							ownershipStatus = AurralOwnershipStatus.Owned,
							contentDescription = album.name,
							onClick = {
								backStack.add(
									aurralMissingAlbumLocalCollectionDetailRoute(
										route = route,
										localAlbum = album,
										tab = "aurral",
										coverUrl = state.coverUrl,
										requestStatus = state.progress?.status ?: route.requestStatus
									)
								)
							}
						)
					}
				}
				Spacer(Modifier.height(24.dp))
			}
			IntegrationLoadingIndicatorStrip(
				indicators = aurralMissingAlbumIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = aurralMissingAlbumIntegrationIndicators,
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
private fun AurralMissingAlbumHero(
	route: Screen.AurralMissingAlbum,
	coverUrl: String?,
	imageRequestHeaders: Map<String, String>,
	progress: AurralAcquisitionProgress?
) {
	val artworkSpec = aurralAlbumArtworkRenderSpec(
		id = route.releaseGroupId,
		title = route.title,
		coverUrl = coverUrl,
		primaryType = route.primaryType,
		imageRequestHeaders = imageRequestHeaders,
		variant = GeneratedArtworkVariant.DetailHero
	)
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier.fillMaxWidth()
	) {
		Box(
			modifier = Modifier
				.widthIn(max = 420.dp)
				.padding(horizontal = 64.dp)
				.aspectRatio(1f)
				.clip(RoundedCornerShape(18.dp))
		) {
			CoverArt(
				coverArtId = artworkSpec.coverArtId,
				imageUrl = artworkSpec.imageUrl,
				imageCacheKey = artworkSpec.imageCacheKey,
				imageRequestHeaders = artworkSpec.imageRequestHeaders,
				contentDescription = artworkSpec.contentDescription,
				generatedArtwork = artworkSpec.generatedArtwork,
				modifier = Modifier.fillMaxSize(),
				shape = RectangleShape
			)
			progress?.let {
				AurralAcquisitionProgressBar(
					progress = it,
					modifier = Modifier.align(Alignment.BottomCenter)
				)
			}
		}
		Text(
			text = route.title,
			style = MaterialTheme.typography.headlineSmall,
			textAlign = TextAlign.Center,
			fontFamily = defaultFont(round = 100f),
			modifier = Modifier
				.padding(horizontal = 31.dp)
				.padding(top = 16.dp)
		)
		Text(
			text = route.artistName,
			color = MaterialTheme.colorScheme.primary,
			style = MaterialTheme.typography.bodyMedium,
			fontFamily = defaultFont(grade = 100, round = 100f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 31.dp)
		)
		Text(
			text = listOfNotNull(route.primaryType, route.year).joinToString(" • "),
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			style = MaterialTheme.typography.bodySmall,
			fontFamily = defaultFont(grade = 100, round = 100f),
			modifier = Modifier.padding(horizontal = 31.dp)
		)
	}
}

@Composable
private fun AurralMissingAlbumActions(
	progress: AurralAcquisitionProgress?,
	aurralConfigured: Boolean,
	requesting: Boolean,
	onAcquire: () -> Unit
) {
	if (!aurralConfigured) return
	val status = progress?.status?.trim()?.takeIf { it.isNotEmpty() }
	val actionState = aurralMissingAlbumActionState(
		progress = progress,
		requesting = requesting
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 31.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
	) {
		Button(
			modifier = Modifier
				.weight(1f)
				.height(44.dp),
			onClick = onAcquire,
			shape = RoundedCornerShape(22.dp),
			enabled = actionState.enabled,
			contentPadding = PaddingValues(horizontal = 16.dp),
			colors = ButtonDefaults.buttonColors(
				containerColor = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary
			)
		) {
			if (actionState.showSpinner) {
				CircularProgressIndicator(
					modifier = Modifier
						.size(20.dp)
						.padding(end = 4.dp),
					strokeWidth = 2.dp,
					color = MaterialTheme.colorScheme.onPrimary
				)
			} else {
				Icon(
					imageVector = when (actionState.icon) {
						AurralMissingAlbumActionIcon.Acquire,
						AurralMissingAlbumActionIcon.Requesting -> Icons.Outlined.LibraryAdd
						AurralMissingAlbumActionIcon.Requested -> Icons.Outlined.Check
						AurralMissingAlbumActionIcon.Play -> Icons.Filled.Play
					},
					contentDescription = null,
					modifier = Modifier
						.size(22.dp)
						.padding(end = 4.dp)
				)
			}
			Text(
				text = status?.let { stringResource(Res.string.info_aurral_request_status, it) }
					?: stringResource(Res.string.action_acquire_album),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				fontWeight = FontWeight.SemiBold,
				fontFamily = defaultFont(round = 100f)
			)
		}
	}
}

private fun AurralMissingAlbumUiState.withCoreReleaseGroup(
	releaseGroup: AurralReleaseGroup,
	route: Screen.AurralMissingAlbum
): AurralMissingAlbumUiState =
	copy(
		coverUrl = coverUrl ?: route.coverUrl ?: releaseGroup.coverUrl,
		loading = false,
		error = null
	)

private fun AurralMissingAlbumUiState.withAlbumRequests(
	releaseGroup: AurralReleaseGroup,
	requests: List<AurralAlbumRequest>
): AurralMissingAlbumUiState =
	copy(
		progress = aurralAlbumAcquisitionProgress(
			albumMusicBrainzId = releaseGroup.id,
			albumName = releaseGroup.title,
			artistName = artist.name,
			requests = requests
		) ?: progress
	)

private fun AurralMissingAlbumUiState.withPreviewTracks(
	releaseGroup: AurralReleaseGroup,
	previewTracks: List<AurralPreviewTrack>
): AurralMissingAlbumUiState =
	copy(
		previewTracks = aurralPreviewTracksForReleaseGroup(releaseGroup, previewTracks)
	)

private fun AurralArtistEnrichment.toResolvedArtist(fallback: DomainArtist): DomainArtist {
	val resolvedName = artistName.trim().takeIf { it.isNotEmpty() } ?: fallback.name
	val resolvedMbid = artistMbid.trim().takeIf { it.isNotEmpty() }
	return DomainArtist(
		id = resolvedMbid?.let { "aurral-$it" } ?: fallback.id,
		name = resolvedName,
		musicBrainzId = resolvedMbid ?: fallback.musicBrainzId,
		artistImageUrl = fallback.artistImageUrl
	)
}

private data class AurralMissingAlbumUiState(
	val artist: DomainArtist,
	val coverUrl: String?,
	val moreAlbums: List<DomainAlbum> = emptyList(),
	val previewTracks: List<AurralPreviewTrack> = emptyList(),
	val progress: AurralAcquisitionProgress? = null,
	val loading: Boolean = false,
	val requesting: Boolean = false,
	val error: Throwable? = null
)

private data class AurralMissingAlbumLocalCatalog(
	val artist: DomainArtist,
	val moreAlbums: List<DomainAlbum>
)
