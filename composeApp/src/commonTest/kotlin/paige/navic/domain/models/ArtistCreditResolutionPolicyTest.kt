package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistCreditResolutionPolicyTest {
	@Test
	fun structuredArtistsWinOnlyWhenTheyDecomposeTheCredit() {
		val context = ArtistCreditContext(
			originalCredit = "Eric Buchholz & Braxton Burks, Eric Buchholz • Eric Buchholz & Braxton Burks",
			structuredArtistNames = listOf("Eric Buchholz", "Braxton Burks")
		)

		val resolution = resolveArtistCredit(
			context = context,
			exactArtistName = { null },
			albumArtistNames = { emptyList() }
		)

		assertEquals(listOf("Eric Buchholz", "Braxton Burks"), resolution?.displayNames)
		assertEquals(ArtistCreditResolutionReason.StructuredArtists, resolution?.reason)
	}

	@Test
	fun singleStructuredArtistThatMatchesOriginalDoesNotBlockValidatedSplit() {
		val context = ArtistCreditContext(
			originalCredit = "Anyma & LISA",
			structuredArtistNames = listOf("Anyma & LISA")
		)

		val resolution = resolveArtistCredit(
			context = context,
			exactArtistName = { candidate ->
				when (candidate) {
					"Anyma" -> "Anyma"
					"LISA" -> "LISA"
					else -> null
				}
			},
			albumArtistNames = { emptyList() }
		)

		assertEquals(listOf("Anyma", "LISA"), resolution?.displayNames)
		assertEquals(ArtistCreditResolutionReason.ValidatedSplit, resolution?.reason)
	}

	@Test
	fun knownFullCreditIsNotSplitEvenWhenItContainsDelimiters() {
		val context = ArtistCreditContext(originalCredit = "Earth, Wind & Fire")

		val resolution = resolveArtistCredit(
			context = context,
			exactArtistName = { candidate ->
				if (candidate == "Earth, Wind & Fire") candidate else null
			},
			albumArtistNames = { emptyList() }
		)

		assertEquals(listOf("Earth, Wind & Fire"), resolution?.displayNames)
		assertEquals(ArtistCreditResolutionReason.ExactFullCredit, resolution?.reason)
	}

	@Test
	fun partialCandidateValidationKeepsCreditUnresolved() {
		val context = ArtistCreditContext(originalCredit = "Chase & Status")

		val resolution = resolveArtistCredit(
			context = context,
			exactArtistName = { candidate -> if (candidate == "Chase") "Chase" else null },
			albumArtistNames = { emptyList() }
		)

		assertNull(resolution)
	}

	@Test
	fun albumContextCanConfirmCandidateSet() {
		val context = ArtistCreditContext(
			originalCredit = "Afrojack, Sia & David Guetta",
			albumTitle = "Titanium Single"
		)

		val resolution = resolveArtistCredit(
			context = context,
			exactArtistName = { null },
			albumArtistNames = { albumTitle ->
				if (albumTitle == "Titanium Single") {
					listOf("Afrojack", "Sia", "David Guetta")
				} else {
					emptyList()
				}
			}
		)

		assertEquals(listOf("Afrojack", "Sia", "David Guetta"), resolution?.displayNames)
		assertEquals(ArtistCreditResolutionReason.AlbumContext, resolution?.reason)
	}

	@Test
	fun cachedResolutionRendersBeforeRawCredit() {
		val context = ArtistCreditContext(originalCredit = "Anyma & LISA")

		assertEquals(
			listOf("Anyma", "LISA"),
			artistCreditDisplayNames(
				context,
				ArtistCreditResolution(
					displayNames = listOf("Anyma", "LISA"),
					reason = ArtistCreditResolutionReason.ValidatedSplit,
					confidence = 0.92
				)
			)
		)
		assertEquals(listOf("Anyma & LISA"), artistCreditDisplayNames(context, null))
	}
}
