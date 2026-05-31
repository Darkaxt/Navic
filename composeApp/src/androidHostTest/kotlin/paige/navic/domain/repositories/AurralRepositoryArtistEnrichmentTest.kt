package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainArtist

class AurralRepositoryArtistEnrichmentTest {
	@Test
	fun artistEnrichmentReturnsNullWhenAurralIsDisabled(): Unit = runBlocking {
		val apiClient = FakeAurralArtistApiClient()
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralBaseUrl = "https://aurral.example.com"
		}
		val repository = AurralRepository(preferenceManager, apiClient)

		assertNull(repository.getArtistEnrichment(artist()).getOrThrow())
		assertEquals(emptyList(), apiClient.artistEnrichmentBaseUrls)
	}

	@Test
	fun artistEnrichmentRequiresArtistMusicBrainzId(): Unit = runBlocking {
		val apiClient = FakeAurralArtistApiClient()
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = "https://aurral.example.com"
		}
		val repository = AurralRepository(preferenceManager, apiClient)

		assertNull(repository.getArtistEnrichment(artist(musicBrainzId = null)).getOrThrow())
		assertEquals(emptyList(), apiClient.artistEnrichmentBaseUrls)
	}

	@Test
	fun artistEnrichmentUsesNormalizedBaseUrlHeadersAndArtistMbid(): Unit = runBlocking {
		val enrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "The Artist",
			previewTracks = listOf(
				AurralPreviewTrack(
					id = "preview-1",
					title = "Preview Track",
					album = "Preview Album",
					previewUrl = "https://cdn.example.com/preview.mp3",
					durationMs = 30_000
				)
			),
			releaseGroups = listOf(AurralReleaseGroup(id = "rg-1", title = "Missing Album")),
			similarArtists = listOf(AurralSimilarArtist(id = "similar-1", name = "Similar Artist"))
		)
		val apiClient = FakeAurralArtistApiClient(enrichment = enrichment)
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val repository = AurralRepository(preferenceManager, apiClient)

		assertEquals(enrichment, repository.getArtistEnrichment(artist()).getOrThrow())
		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.artistEnrichmentBaseUrls)
		assertEquals(listOf("artist-mbid"), apiClient.artistEnrichmentMbids)
		assertEquals(listOf("The Artist"), apiClient.artistEnrichmentNames)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.artistEnrichmentRequestHeaders
		)
	}

	@Test
	fun releaseGroupCoverUrlEncodesPathAndQueryValues() {
		assertEquals(
			"https://aurral.example.com/aurral/api/artists/release-group/release%20group%2F1/cover?artistName=The%20Artist&albumTitle=Best%20Of",
			aurralReleaseGroupCoverUrl(
				baseUrl = "https://aurral.example.com/aurral/",
				releaseGroupMbid = "release group/1",
				artistName = "The Artist",
				albumTitle = "Best Of"
			)
		)
		assertNull(
			aurralReleaseGroupCoverUrl(
				baseUrl = "aurral.example.com",
				releaseGroupMbid = "release-group",
				artistName = "The Artist",
				albumTitle = "Best Of"
			)
		)
		assertNull(
			aurralReleaseGroupCoverUrl(
				baseUrl = "https://aurral.example.com",
				releaseGroupMbid = " ",
				artistName = "The Artist",
				albumTitle = "Best Of"
			)
		)
	}

	@Test
	fun requestAlbumUsesNormalizedBaseUrlHeadersAndPayload(): Unit = runBlocking {
		val apiClient = FakeAurralArtistApiClient()
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val repository = AurralRepository(preferenceManager, apiClient)
		val releaseGroup = AurralReleaseGroup(
			id = " album-mbid ",
			title = " Album Title "
		)

		repository.requestAlbum(artist(), releaseGroup).getOrThrow()

		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.requestAlbumBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.requestAlbumHeaders
		)
		assertEquals(
			listOf(
				AurralAlbumRequestPayload(
					albumMbid = "album-mbid",
					albumName = "Album Title",
					artistMbid = "artist-mbid",
					artistName = "The Artist",
					triggerSearch = true
				)
			),
			apiClient.requestAlbumPayloads
		)
	}

	@Test
	fun releaseGroupCoverImageUrlUsesNormalizedBaseUrlHeadersAndJsonImage(): Unit = runBlocking {
		val apiClient = FakeAurralArtistApiClient(
			releaseGroupCoverImageUrl = "/api/image-proxy/cached-cover.webp"
		)
		val preferenceManager = PreferenceManager(MapSettings()).apply {
			aurralEnabled = true
			aurralBaseUrl = " https://aurral.example.com/aurral/ "
			aurralUsername = " user "
			aurralPassword = " pass "
		}
		val repository = AurralRepository(preferenceManager, apiClient)
		val releaseGroup = AurralReleaseGroup(
			id = " release-group-mbid ",
			title = " Album Title "
		)

		assertEquals(
			"https://aurral.example.com/aurral/api/image-proxy/cached-cover.webp",
			repository.getReleaseGroupCoverImageUrl(
				releaseGroup = releaseGroup,
				artistName = " The Artist "
			).getOrThrow()
		)
		assertEquals(listOf("https://aurral.example.com/aurral"), apiClient.releaseGroupCoverBaseUrls)
		assertEquals(
			listOf(mapOf("Authorization" to "Basic dXNlcjpwYXNz")),
			apiClient.releaseGroupCoverHeaders
		)
		assertEquals(listOf("release-group-mbid"), apiClient.releaseGroupCoverMbids)
		assertEquals(listOf("The Artist"), apiClient.releaseGroupCoverArtistNames)
		assertEquals(listOf("Album Title"), apiClient.releaseGroupCoverAlbumTitles)
	}

	private fun artist(
		musicBrainzId: String? = " artist-mbid "
	) = DomainArtist(
		id = "artist-id",
		name = "The Artist",
		musicBrainzId = musicBrainzId
	)

	private class FakeAurralArtistApiClient(
		private val enrichment: AurralArtistEnrichment = AurralArtistEnrichment(
			artistMbid = "artist-mbid",
			artistName = "The Artist"
		),
		private val releaseGroupCoverImageUrl: String? = null
	) : AurralApiClient {
		val artistEnrichmentBaseUrls = mutableListOf<String>()
		val artistEnrichmentRequestHeaders = mutableListOf<Map<String, String>>()
		val artistEnrichmentMbids = mutableListOf<String>()
		val artistEnrichmentNames = mutableListOf<String>()
		val requestAlbumBaseUrls = mutableListOf<String>()
		val requestAlbumHeaders = mutableListOf<Map<String, String>>()
		val requestAlbumPayloads = mutableListOf<AurralAlbumRequestPayload>()
		val releaseGroupCoverBaseUrls = mutableListOf<String>()
		val releaseGroupCoverHeaders = mutableListOf<Map<String, String>>()
		val releaseGroupCoverMbids = mutableListOf<String>()
		val releaseGroupCoverArtistNames = mutableListOf<String>()
		val releaseGroupCoverAlbumTitles = mutableListOf<String>()

		override suspend fun testConnection(
			baseUrl: String,
			requestHeaders: Map<String, String>
	): AurralConnectionResult = AurralConnectionResult.Connected

		override suspend fun fetchServiceStatus(
			baseUrl: String,
			requestHeaders: Map<String, String>
		): AurralServiceStatus = AurralServiceStatus()

		override suspend fun fetchArtistEnrichment(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			artistName: String
		): AurralArtistEnrichment {
			artistEnrichmentBaseUrls += baseUrl
			artistEnrichmentRequestHeaders += requestHeaders
			artistEnrichmentMbids += artistMbid
			artistEnrichmentNames += artistName
			return enrichment
		}

		override suspend fun fetchLibraryArtistMonitoring(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String
		): Boolean? = null

		override suspend fun requestAlbum(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			payload: AurralAlbumRequestPayload
		) {
			requestAlbumBaseUrls += baseUrl
			requestAlbumHeaders += requestHeaders
			requestAlbumPayloads += payload
		}

		override suspend fun monitorArtist(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			artistMbid: String,
			payload: AurralArtistMonitorPayload
		) = Unit

		override suspend fun fetchReleaseGroupCoverImageUrl(
			baseUrl: String,
			requestHeaders: Map<String, String>,
			releaseGroupMbid: String,
			artistName: String,
			albumTitle: String
		): String? {
			releaseGroupCoverBaseUrls += baseUrl
			releaseGroupCoverHeaders += requestHeaders
			releaseGroupCoverMbids += releaseGroupMbid
			releaseGroupCoverArtistNames += artistName
			releaseGroupCoverAlbumTitles += albumTitle
			return aurralAbsoluteImageUrl(baseUrl, releaseGroupCoverImageUrl)
		}
	}
}
