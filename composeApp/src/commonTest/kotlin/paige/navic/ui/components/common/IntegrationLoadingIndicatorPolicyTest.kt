package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationLoadingIndicatorPolicyTest {
	@Test
	fun activeIndicatorsKeepPageLocalOrderAndOmitInactiveIntegrations() {
		val indicators = integrationLoadingIndicators(
			lidaClipsLoading = true,
			aurralLoading = false,
			musicBrainzLoading = true,
			lastFmLoading = false,
			binderyLoading = true,
			lyricsLoading = false
		)

		assertEquals(
			listOf(
				IntegrationLoadingIndicator.LidaClips,
				IntegrationLoadingIndicator.MusicBrainz,
				IntegrationLoadingIndicator.Bindery
			),
			indicators
		)
	}

	@Test
	fun activeIndicatorsCanRepresentOnlyAurralPageLoading() {
		assertEquals(
			listOf(IntegrationLoadingIndicator.Aurral),
			integrationLoadingIndicators(aurralLoading = true)
		)
	}

	@Test
	fun activeIndicatorsAreEmptyWhenPageHasNoIntegrationWork() {
		assertTrue(integrationLoadingIndicators().isEmpty())
	}

	@Test
	fun pulseIconsUseAndroidSafeVectorRendering() {
		IntegrationLoadingIndicator.entries.forEach { indicator ->
			assertEquals(
				IntegrationLoadingIndicatorIconKind.Vector,
				integrationLoadingIndicatorIconKind(indicator)
			)
		}
	}
}
