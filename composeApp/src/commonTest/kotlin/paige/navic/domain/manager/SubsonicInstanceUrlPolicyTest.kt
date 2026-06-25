package paige.navic.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class SubsonicInstanceUrlPolicyTest {
	@Test
	fun standardLoginUrlDropsNavidromeWebAppPath() {
		assertEquals(
			"https://music.remaxku.eu",
			normalizeSubsonicInstanceUrl("music.remaxku.eu/app/")
		)
	}

	@Test
	fun standardLoginUrlKeepsReverseProxySubpath() {
		assertEquals(
			"https://example.test/navidrome",
			normalizeSubsonicInstanceUrl("https://example.test/navidrome/")
		)
	}

	@Test
	fun standardLoginUrlDropsQueryAndFragment() {
		assertEquals(
			"http://music.example.test",
			normalizeSubsonicInstanceUrl(" http://music.example.test/app/?redirect=1#login ")
		)
	}
}
