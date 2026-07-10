package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals

class CoverArtLoadingPlaceholderPolicyTest {
	@Test
	fun disabledPlaceholderDoesNotReuseANonblankCacheKey() {
		assertEquals(null, cachedCoverArtLoadingPlaceholderKey(false, "musicbrainz:destination"))
	}

	@Test
	fun enabledPlaceholderRejectsNullAndBlankCacheKeys() {
		assertEquals(null, cachedCoverArtLoadingPlaceholderKey(true, null))
		assertEquals(null, cachedCoverArtLoadingPlaceholderKey(true, "   "))
	}

	@Test
	fun enabledPlaceholderUsesTheExactNonblankResolvedCacheKey() {
		assertEquals(
			"  musicbrainz:destination  ",
			cachedCoverArtLoadingPlaceholderKey(true, "  musicbrainz:destination  ")
		)
	}
}
