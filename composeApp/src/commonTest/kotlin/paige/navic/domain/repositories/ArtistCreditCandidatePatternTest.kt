package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistCreditCandidatePatternTest {
	@Test
	fun contributorIdentityIsWrappedForContainsMatching() {
		assertEquals("%John Powell%", artistCreditContributorLikePattern("  John Powell  "))
	}

	@Test
	fun sqliteLikeMetacharactersAreEscaped() {
		assertEquals(
			"%artist\\%name\\_part\\\\live%",
			artistCreditContributorLikePattern("artist%name_part\\live")
		)
	}

	@Test
	fun blankContributorIdentityDoesNotCreateMatchAllPattern() {
		assertNull(artistCreditContributorLikePattern(null))
		assertNull(artistCreditContributorLikePattern("   "))
	}
}
