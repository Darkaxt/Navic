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
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_acquire_album
import navic.composeapp.generated.resources.info_aurral_no_album_previews
import navic.composeapp.generated.resources.info_aurral_request_status
import navic.composeapp.generated.resources.title_aurral_missing_album
import navic.composeapp.generated.resources.title_aurral_preview_tracks
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalPlatformContext
import paige.navic.data.database.dao.ArtistDao
import paige.navic.data.database.mappers.toDomainModel
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralAcquisitionProgress
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.aurralAcquisitionProgress
import paige.navic.domain.models.aurralAlbumAcquisitionProgress
import paige.navic.domain.models.aurralPreviewTracksForReleaseGroup
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.LibraryAdd
import paige.navic.icons.outlined.Note
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.components.AurralPreviewTracks
import paige.navic.ui.theme.defaultFont

@Composable
fun AurralMissingAlbumScreen(route: Screen.AurralMissingAlbum) {
	val preferenceManager = koinInject<PreferenceManager>()
	val artistDao = koinInject<ArtistDao>()
	val aurralRepository = koinInject<AurralRepository>()
	val platformContext = LocalPlatformContext.current
	val scope = rememberCoroutineScope()
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

	LaunchedEffect(route) {
		val localArtist = artistDao.getArtistById(route.artistId)?.toDomainModel()
			?: state.artist
		state = state.copy(artist = localArtist, coverUrl = route.coverUrl)

		aurralRepository.getArtistEnrichment(localArtist)
			.onSuccess { enrichment ->
				val refreshedReleaseGroup = enrichment
					?.releaseGroups
					?.firstOrNull { it.id == releaseGroup.id }
					?: releaseGroup
				val coverUrl = route.coverUrl
					?: refreshedReleaseGroup.coverUrl
					?: aurralRepository.getReleaseGroupCoverImageUrl(
						releaseGroup = refreshedReleaseGroup,
						artistName = localArtist.name
					).getOrNull()
				state = state.copy(
					artist = localArtist,
					coverUrl = coverUrl,
					previewTracks = enrichment
						?.let { aurralPreviewTracksForReleaseGroup(refreshedReleaseGroup, it.previewTracks) }
						.orEmpty(),
					progress = enrichment?.let {
						aurralAlbumAcquisitionProgress(
							albumMusicBrainzId = refreshedReleaseGroup.id,
							albumName = refreshedReleaseGroup.title,
							artistName = localArtist.name,
							requests = it.requests
						)
					} ?: state.progress,
					loading = false,
					error = null
				)
			}
			.onFailure { error ->
				state = state.copy(
					artist = localArtist,
					loading = false,
					error = error
				)
			}
	}

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
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(top = 20.dp, bottom = 32.dp),
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
				requesting = state.requesting,
				onAcquire = {
					platformContext.clickSound()
					scope.launch {
						state = state.copy(requesting = true, error = null)
						aurralRepository.requestAlbum(state.artist, releaseGroup)
							.onSuccess {
								state = state.copy(
									requesting = false,
									progress = aurralAcquisitionProgress("requested")
								)
							}
							.onFailure { error ->
								state = state.copy(requesting = false, error = error)
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
				AurralPreviewTracks(
					title = stringResource(Res.string.title_aurral_preview_tracks),
					tracks = state.previewTracks.toImmutableList(),
					modifier = Modifier.fillMaxWidth()
				)
			} else {
				ContentUnavailable(
					icon = Icons.Outlined.Note,
					label = stringResource(Res.string.info_aurral_no_album_previews),
					modifier = Modifier.padding(top = 24.dp)
				)
			}
			Spacer(Modifier.height(24.dp))
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
				coverArtId = null,
				imageUrl = coverUrl,
				imageCacheKey = "aurral-release-group-${route.releaseGroupId}",
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = route.title,
				fallbackKind = route.primaryType ?: "Album",
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
	requesting: Boolean,
	onAcquire: () -> Unit
) {
	val status = progress?.status?.trim()?.takeIf { it.isNotEmpty() }
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
			enabled = !requesting && progress?.active != true && progress?.completed != true,
			contentPadding = PaddingValues(horizontal = 16.dp),
			colors = ButtonDefaults.buttonColors(
				containerColor = MaterialTheme.colorScheme.primary,
				contentColor = MaterialTheme.colorScheme.onPrimary
			)
		) {
			if (requesting) {
				CircularProgressIndicator(
					modifier = Modifier
						.size(20.dp)
						.padding(end = 4.dp),
					strokeWidth = 2.dp,
					color = MaterialTheme.colorScheme.onPrimary
				)
			} else {
				Icon(
					imageVector = if (progress?.completed == true) Icons.Filled.Play else Icons.Outlined.LibraryAdd,
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

private data class AurralMissingAlbumUiState(
	val artist: DomainArtist,
	val coverUrl: String?,
	val previewTracks: List<AurralPreviewTrack> = emptyList(),
	val progress: AurralAcquisitionProgress? = null,
	val loading: Boolean = false,
	val requesting: Boolean = false,
	val error: Throwable? = null
)
