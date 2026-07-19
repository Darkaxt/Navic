package paige.navic.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals

class GenreRepositoryQueryPolicyTest {
	@Test
	fun normalizedDisplayVariantsUseABroadEnoughCandidateTerm() {
		assertEquals("soundtrack", genreCandidateSearchTerm("Soundtracks"))
		assertEquals("game", genreCandidateSearchTerm("Game"))
		assertEquals("Classical Crossover", genreCandidateSearchTerm("Classical Crossover"))
	}

	@Test
	fun candidateLikePatternEscapesSqlMetacharacters() {
		assertEquals("%R\\%B\\_Mix\\\\Live%", genreCandidateLikePattern("R%B_Mix\\Live"))
	}
}
