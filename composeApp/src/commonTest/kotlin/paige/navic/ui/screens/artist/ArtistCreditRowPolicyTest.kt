package paige.navic.ui.screens.artist

import paige.navic.domain.models.ArtistCreditResolution
import paige.navic.domain.models.ArtistCreditResolutionReason
import paige.navic.domain.models.DomainArtist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtistCreditRowPolicyTest {
	@Test
	fun resolvedCompositeArtistCreditIsReplacedByResolvedArtists() {
		val artists = listOf(
			DomainArtist(id = "composite", name = "Anyma & LISA")
		)

		val rows = artistCreditResolvedRows(artists) { artist ->
			if (artist.name == "Anyma & LISA") {
				ArtistCreditResolution(
					displayNames = listOf("Anyma", "LISA"),
					reason = ArtistCreditResolutionReason.ValidatedSplit,
					confidence = 0.92
				)
			} else {
				null
			}
		}

		assertEquals(listOf("Anyma", "LISA"), rows.map { it.name })
		assertFalse(rows.any { it.name == "Anyma & LISA" })
	}

	@Test
	fun resolvedCompositeReusesExistingLocalArtistRows() {
		val artists = listOf(
			DomainArtist(id = "composite", name = "Anyma & LISA"),
			DomainArtist(id = "artist-anyma", name = "Anyma", albumCount = 4)
		)

		val rows = artistCreditResolvedRows(artists) { artist ->
			if (artist.name == "Anyma & LISA") {
				ArtistCreditResolution(
					displayNames = listOf("Anyma", "LISA"),
					reason = ArtistCreditResolutionReason.ValidatedSplit,
					confidence = 0.92
				)
			} else {
				null
			}
		}

		assertEquals(listOf("Anyma", "LISA"), rows.map { it.name })
		assertTrue(rows.first { it.name == "Anyma" }.albumCount == 4)
		assertEquals("name:lisa", rows.first { it.name == "LISA" }.id)
	}

	@Test
	fun creditedNameWithoutLibraryArtistCountsTheCreditsAlbums() {
		val artists = listOf(
			DomainArtist(id = "combined", name = "ACRAZE, Sub Focus, Cherish", albumCount = 1),
			DomainArtist(id = "other", name = "Sub Focus & Cherish", albumCount = 2),
			DomainArtist(id = "acraze", name = "ACRAZE", albumCount = 2),
			DomainArtist(id = "subfocus", name = "Sub Focus", albumCount = 11)
		)
		val splits = mapOf(
			"ACRAZE, Sub Focus, Cherish" to listOf("ACRAZE", "Sub Focus", "Cherish"),
			"Sub Focus & Cherish" to listOf("Sub Focus", "Cherish")
		)

		val rows = artistCreditResolvedRows(artists) { artist ->
			splits[artist.name]?.let { names ->
				ArtistCreditResolution(
					displayNames = names,
					reason = ArtistCreditResolutionReason.ValidatedSplit,
					confidence = 0.92
				)
			}
		}

		assertEquals(listOf("ACRAZE", "Sub Focus", "Cherish"), rows.map { it.name })
		assertEquals(3, rows.first { it.name == "Cherish" }.albumCount)
		assertEquals(11, rows.first { it.name == "Sub Focus" }.albumCount)
	}

	@Test
	fun creditIncludesOnlyWholeCreditedNames() {
		assertTrue(artistCreditIncludesName("ACRAZE, Sub Focus, Cherish", "cherish"))
		assertTrue(artistCreditIncludesName("Cherish", "Cherish"))
		assertFalse(artistCreditIncludesName("Cherished Memories", "Cherish"))
		assertFalse(artistCreditIncludesName(null, "Cherish"))
	}

	@Test
	fun unresolvedCompositeArtistCreditIsHiddenFromVisibleRows() {
		val artists = listOf(
			DomainArtist(id = "solo", name = "LISA"),
			DomainArtist(id = "group", name = "Chase & Status")
		)

		val rows = artistCreditResolvedRows(artists) { null }

		assertEquals(listOf("LISA"), rows.map { it.name })
		assertFalse(rows.any { it.name == "Chase & Status" })
	}
}
