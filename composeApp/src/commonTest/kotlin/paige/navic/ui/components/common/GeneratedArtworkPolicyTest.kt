package paige.navic.ui.components.common

import paige.navic.domain.models.DomainPlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GeneratedArtworkPolicyTest {
	@Test
	fun generatedMixPlaylistSpecSuppressesServerCollageAndUsesCompactMixArtwork() {
		val spec = playlist(
			id = "genre",
			name = "Electronic_Pop_Rock_Medium_Danceable_Party_1_automatic",
			coverArtId = "server-generated-collage"
		).playlistArtworkRenderSpec()

		assertNull(spec.coverArtId)
		assertNull(spec.imageUrl)
		assertEquals("Electronic / Pop / Rock", spec.contentDescription)
		assertEquals("Mix", spec.generatedArtwork.kindLabel)
		assertEquals("Electronic\nPop\nRock", spec.generatedArtwork.primaryLabel)
		assertEquals(GeneratedArtworkVariant.GridCard, spec.generatedArtwork.variant)
	}

	@Test
	fun aurralFlowPlaylistSpecSuppressesServerCoverAndUsesFlowArtwork() {
		val spec = playlist(
			id = "flow",
			name = "[A] Discover",
			coverArtId = "flow-cover"
		).playlistArtworkRenderSpec()

		assertNull(spec.coverArtId)
		assertNull(spec.imageUrl)
		assertEquals("Discover", spec.contentDescription)
		assertEquals("Flow", spec.generatedArtwork.kindLabel)
		assertEquals("Discover", spec.generatedArtwork.primaryLabel)
	}

	@Test
	fun aurralAlbumSpecUsesCompactAlbumFallbackWhenCoverIsMissing() {
		val spec = aurralAlbumArtworkRenderSpec(
			id = "release-group-1",
			title = "How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]",
			coverUrl = null,
			primaryType = "Album",
			imageRequestHeaders = mapOf("Authorization" to "Basic x"),
			variant = GeneratedArtworkVariant.DetailHero
		)

		assertNull(spec.coverArtId)
		assertNull(spec.imageUrl)
		assertEquals("Album", spec.generatedArtwork.kindLabel)
		assertEquals("How to Train Your Dragon - For Your Consideration Best Original Score [2 CD]", spec.generatedArtwork.primaryLabel)
		assertEquals(GeneratedArtworkVariant.DetailHero, spec.generatedArtwork.variant)
	}

	@Test
	fun collectionSpecRoutesPlaylistsThroughTheSameGeneratedArtworkPolicy() {
		val playlist = playlist(
			id = "mood",
			name = "Chill Mix",
			coverArtId = "server-generated-collage"
		)
		val spec = playlist.collectionArtworkRenderSpec(
			displayTitle = null,
			externalImageUrl = null,
			variant = GeneratedArtworkVariant.SheetThumbnail
		)

		assertNull(spec.coverArtId)
		assertEquals("Chill", spec.contentDescription)
		assertEquals("Mix", spec.generatedArtwork.kindLabel)
		assertEquals("Chill", spec.generatedArtwork.primaryLabel)
		assertEquals(GeneratedArtworkVariant.SheetThumbnail, spec.generatedArtwork.variant)
	}

	private fun playlist(
		id: String,
		name: String,
		coverArtId: String? = null
	) = DomainPlaylist(
		id = id,
		name = name,
		owner = "owner",
		comment = null,
		coverArtId = coverArtId,
		songCount = 0,
		duration = 0.seconds,
		createdAt = Instant.DISTANT_PAST,
		modifiedAt = Instant.DISTANT_PAST,
		public = null,
		readOnly = null,
		allowedUsers = emptyList(),
		validUntil = null,
		songs = emptyList()
	)
}
