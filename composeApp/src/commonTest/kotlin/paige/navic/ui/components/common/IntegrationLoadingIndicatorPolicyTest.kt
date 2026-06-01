package paige.navic.ui.components.common

import androidx.compose.ui.unit.dp
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
	fun brandedPulseIconsUseAndroidSafeRasterArtwork() {
		listOf(
			IntegrationLoadingIndicator.LidaClips,
			IntegrationLoadingIndicator.Aurral,
			IntegrationLoadingIndicator.MusicBrainz,
			IntegrationLoadingIndicator.LastFm,
			IntegrationLoadingIndicator.Bindery
		).forEach { indicator ->
			assertEquals(
				IntegrationLoadingIndicatorIconKind.Raster,
				integrationLoadingIndicatorIconKind(indicator)
			)
		}
	}

	@Test
	fun lyricsPulseIconCanUseVectorRendering() {
		assertEquals(
			IntegrationLoadingIndicatorIconKind.Vector,
			integrationLoadingIndicatorIconKind(IntegrationLoadingIndicator.Lyrics)
		)
	}

	@Test
	fun overlayTopPaddingUsesStatusBarOnly() {
		assertEquals(
			32.dp,
			integrationLoadingIndicatorOverlayTopPadding(statusBarTop = 24.dp)
		)
	}
}
