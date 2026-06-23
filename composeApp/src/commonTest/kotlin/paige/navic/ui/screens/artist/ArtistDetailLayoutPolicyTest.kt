package paige.navic.ui.screens.artist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.settings.ArtworkSourcePriority

class ArtistDetailLayoutPolicyTest {
	@Test
	fun frequentSongsGridHeightOnlyReservesVisibleRows() {
		assertEquals(0, artistTopSongsGridHeightDp(songCount = 0))
		assertEquals(84, artistTopSongsGridHeightDp(songCount = 1))
		assertEquals(168, artistTopSongsGridHeightDp(songCount = 2))
		assertEquals(252, artistTopSongsGridHeightDp(songCount = 3))
		assertEquals(252, artistTopSongsGridHeightDp(songCount = 12))
	}

	@Test
	fun headingOnlyUsesVerifiedExternalImageUrl() {
		assertNull(
			artistDetailHeadingImageUrl(
				DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = null,
					artistImageUrl = " https://navidrome.example.com/protected/bond.jpg?token=expired "
				)
			)
		)
		assertEquals(
			"https://aurral.example.com/bond.webp",
			artistDetailHeadingImageUrl(
				artist = DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = null,
					artistImageUrl = null
				),
				verifiedExternalImageUrl = " https://aurral.example.com/bond.webp "
			)
		)
		assertEquals(
			"https://aurral.example.com/bond.webp",
			artistDetailHeadingImageUrl(
				artist = DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = "server-cover",
					artistImageUrl = "https://assets.example.com/bond.jpg"
				),
				verifiedExternalImageUrl = "https://aurral.example.com/bond.webp",
				artistArtworkPriority = ArtworkSourcePriority.AurralFirst
			)
		)
		assertEquals(
			"https://aurral.example.com/bond.webp",
			artistDetailHeadingImageUrl(
				artist = DomainArtist(
					id = "bond",
					name = "BOND",
					coverArtId = "server-cover",
					artistImageUrl = "https://assets.example.com/bond.jpg"
				),
				verifiedExternalImageUrl = "https://aurral.example.com/bond.webp",
				artistArtworkPriority = ArtworkSourcePriority.NativeFirst
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheByLocalArtistId() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "아이유",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				)
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheByNormalizedNameAfterIdChange() {
		assertEquals(
			"https://aurral.example.com/lindsey.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "new-local-id",
					name = "Lindsey   Stirling",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "old-local-id",
						sourceArtistId = "source-lindsey",
						name = "Lindsey Stirling",
						normalizedName = "lindsey stirling",
						imageUrl = "https://aurral.example.com/lindsey.webp"
					)
				)
			)
		)
	}

	@Test
	fun headingCanUsePersistentArtistPhotoCacheBeforeNativeCoverWhenAurralIsFirst() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = "ar-local-iu",
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				),
				artistArtworkPriority = ArtworkSourcePriority.AurralFirst
			)
		)
	}

	@Test
	fun headingPrefersExactLocalArtistCacheOverNewerNameOnlyMatch() {
		assertEquals(
			"https://aurral.example.com/exact-iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = null,
						sourceArtistId = null,
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://other.example.com/newer-name-only.webp",
						source = "Other",
						updatedAtMillis = 2_000L
					),
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/exact-iu.webp",
						source = "Aurral",
						updatedAtMillis = 1_000L
					)
				)
			)
		)
	}

	@Test
	fun headingPrefersAurralSourceWithinSameCacheMatchStrength() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://other.example.com/newer-iu.webp",
						source = "Other",
						updatedAtMillis = 2_000L
					),
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp",
						source = "Aurral",
						updatedAtMillis = 1_000L
					)
				)
			)
		)
	}

	@Test
	fun headingUsesPersistentArtistPhotoCacheWhenAurralIsEnabledEvenIfNativeIsFirst() {
		assertEquals(
			"https://aurral.example.com/iu.webp",
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = "ar-local-iu",
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				),
				artistArtworkPriority = ArtworkSourcePriority.NativeFirst
			)
		)
	}

	@Test
	fun headingSkipsPersistentArtistPhotoCacheWhenExternalArtworkIsDisabled() {
		assertNull(
			artistDetailCachedImageUrl(
				artist = DomainArtist(
					id = "local-iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = "source-iu",
						name = "IU",
						normalizedName = "iu",
						imageUrl = "https://aurral.example.com/iu.webp"
					)
				),
				artistArtworkPriority = ArtworkSourcePriority.AurralFirst,
				externalArtworkEnabled = false
			)
		)
	}

	@Test
	fun headingIgnoresNonHttpArtistPhotoCacheUrls() {
		assertNull(
			artistDetailCachedImageUrl(
				artist = DomainArtist(id = "local-iu", name = "IU"),
				entries = listOf(
					ArtistHeaderImageCacheEntry(
						artistId = "local-iu",
						sourceArtistId = null,
						name = "IU",
						normalizedName = "iu",
						imageUrl = "/artist/iu.webp"
					)
				)
			)
		)
	}

	@Test
	fun headingCreatesPersistentArtistPhotoCacheFromVerifiedExternalImage() {
		val entity = artistDetailPhotoCacheEntity(
			localArtist = DomainArtist(
				id = "local-iu",
				name = "IU",
				musicBrainzId = "mbid-iu"
			),
			sourceArtist = DomainArtist(
				id = "local-iu",
				name = "아이유",
				musicBrainzId = "mbid-iu"
			),
			imageUrl = " https://aurral.example.com/iu.webp ",
			nowMillis = 1_000L
		)

		assertEquals("artist:local-iu", entity?.cacheKey)
		assertEquals("local-iu", entity?.artistId)
		assertEquals("mbid-iu", entity?.sourceArtistId)
		assertEquals("iu", entity?.normalizedName)
		assertEquals("https://aurral.example.com/iu.webp", entity?.imageUrl)
	}

	@Test
	fun playbackOriginUsesVerifiedArtistImageShownInHeading() {
		val origin = artistDetailPlaybackOrigin(
			artistStateForTransition("iu").copy(
				artist = DomainArtist(
					id = "iu",
					name = "IU",
					coverArtId = null,
					artistImageUrl = null
				),
				aurralArtistImageUrl = " https://aurral.example.com/iu.webp "
			)
		)

		assertEquals("https://aurral.example.com/iu.webp", origin.coverArtId)
	}

	@Test
	fun artistPageTransitionKeyIgnoresAurralOnlyStateChanges() {
		val baseState = artistStateForTransition("artist-1").copy(
			aurralLoading = true,
			aurralMonitored = null
		)
		val enrichedState = baseState.copy(
			aurralLoading = false,
			aurralMonitored = true,
			aurralArtistImageUrl = "https://aurral.example.com/artist.webp"
		)

		assertEquals(artistDetailTransitionKey(baseState), artistDetailTransitionKey(enrichedState))
		assertFalse(shouldAnimateArtistDetailStateChange(baseState, enrichedState))
		assertTrue(
			shouldAnimateArtistDetailStateChange(
				baseState,
				artistStateForTransition("artist-2")
			)
		)
	}

	private fun artistStateForTransition(artistId: String) =
		paige.navic.ui.screens.artist.viewmodels.ArtistState(
			artist = DomainArtist(id = artistId, name = artistId),
			albums = emptyList(),
			topSongs = emptyList()
		)

	@Test
	fun artistBiographyPreviewExpandsInlineInsteadOfRequiringExternalLink() {
		val biography = "A".repeat(205)

		assertTrue(shouldShowArtistBiographyToggle(biography, limit = 200))
		assertEquals("${"A".repeat(200)}...", artistBiographyDisplayText(biography, expanded = false, limit = 200))
		assertEquals(biography, artistBiographyDisplayText(biography, expanded = true, limit = 200))
		assertFalse(shouldShowArtistBiographyToggle("Short biography", limit = 200))
		assertNull(artistBiographyDisplayText(null, expanded = false, limit = 200))
	}

	@Test
	fun artistBiographyScrollFadesOnlyShowWhenMoreTextExistsInThatDirection() {
		assertEquals(
			ArtistBiographyScrollFades(showTop = false, showBottom = false),
			artistBiographyScrollFades(scrollValue = 0, maxScrollValue = 0)
		)
		assertEquals(
			ArtistBiographyScrollFades(showTop = false, showBottom = true),
			artistBiographyScrollFades(scrollValue = 0, maxScrollValue = 100)
		)
		assertEquals(
			ArtistBiographyScrollFades(showTop = true, showBottom = true),
			artistBiographyScrollFades(scrollValue = 50, maxScrollValue = 100)
		)
		assertEquals(
			ArtistBiographyScrollFades(showTop = true, showBottom = false),
			artistBiographyScrollFades(scrollValue = 100, maxScrollValue = 100)
		)
	}
}
