package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistIdentityPolicyTest {
	@Test
	fun absentArtistIdentityRemainsNull() {
		assertNull(resolveArtistId(overrideId = null, sourceId = null))
	}

	@Test
	fun overrideWinsWithoutInventingIdentity() {
		assertEquals("override", resolveArtistId("override", "source"))
		assertEquals("source", resolveArtistId(null, "source"))
	}
}
