package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongActionPolicyTest {
	@Test
	fun stableNavidromeSongIdsExcludeMissingBlankAndRadioRows() {
		assertFalse(hasStableNavidromeSongId(null))
		assertFalse(hasStableNavidromeSongId(""))
		assertFalse(hasStableNavidromeSongId("   "))
		assertFalse(hasStableNavidromeSongId("radio_live"))
		assertFalse(hasStableNavidromeSongId("${AurralFlowSongIdPrefix}job-1"))

		assertTrue(hasStableNavidromeSongId("song-1"))
	}
}
