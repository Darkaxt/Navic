package paige.navic.ui.screens.collection.components

import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.ui.components.common.AurralActionIconOverlay

class HeadingRowButtonsPolicyTest {
	@Test
	fun missingAurralAlbumUsesCrossedActionIcon() {
		assertEquals(
			AurralActionIconOverlay.Crossed,
			aurralAlbumActionIconOverlay(AurralOwnershipStatus.Missing)
		)
	}

	@Test
	fun partialAurralAlbumUsesProgressActionIcon() {
		assertEquals(
			AurralActionIconOverlay.Progress,
			aurralAlbumActionIconOverlay(AurralOwnershipStatus.Partial)
		)
	}

	@Test
	fun requestedAndProcessingAurralAlbumsUseProgressActionIcon() {
		assertEquals(
			AurralActionIconOverlay.Progress,
			aurralAlbumActionIconOverlay(AurralOwnershipStatus.Requested)
		)
		assertEquals(
			AurralActionIconOverlay.Progress,
			aurralAlbumActionIconOverlay(AurralOwnershipStatus.Processing)
		)
	}

	@Test
	fun failedAurralAlbumUsesCrossedActionIcon() {
		assertEquals(
			AurralActionIconOverlay.Crossed,
			aurralAlbumActionIconOverlay(AurralOwnershipStatus.Failed)
		)
	}
}
