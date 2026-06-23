package paige.navic.domain.models.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArtworkSourcePriorityLabelSourceTest {
	@Test
	fun coverArtworkSettingUsesCoverSpecificLabels() {
		val enumSource = File(
			"src/commonMain/kotlin/paige/navic/domain/models/settings/ArtworkSourcePriority.kt"
		).readText()
		val aurralSettingsSource = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/settings/AurralScreen.kt"
		).readText()
		val searchSettingsSource = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchIntegrationRows.kt"
		).readText()

		assertTrue(
			"coverDisplayName" in enumSource,
			"Cover artwork priority needs labels that describe cover behavior, not artist-photo behavior."
		)
		assertTrue(
			"option_artwork_source_external_cover_first" in enumSource,
			"The cover AurralFirst value should be presented as external cover first."
		)
		assertTrue(
			"stringResource(priority.coverDisplayName)" in aurralSettingsSource,
			"The Aurral settings screen must use cover-specific labels for cover artwork priority."
		)
		assertTrue(
			"stringResource(it.coverDisplayName)" in searchSettingsSource,
			"Settings search must use cover-specific labels for cover artwork priority."
		)
	}
}
