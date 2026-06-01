package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DangerZoneActionPolicyTest {
	@Test
	fun allDangerZoneActionsRequireConfirmation() {
		assertTrue(DangerZoneAction.entries.all { it.requiresConfirmation })
	}

	@Test
	fun dangerZoneActionsKeepDestructiveStorageOrder() {
		assertEquals(
			listOf(
				DangerZoneAction.ClearImageCache,
				DangerZoneAction.ClearMusicBrainzCache,
				DangerZoneAction.ClearLidaClipsVideoCache,
				DangerZoneAction.ClearPendingSyncActions,
				DangerZoneAction.ClearDownloads,
				DangerZoneAction.RebuildDatabase
			),
			dangerZoneActions()
		)
	}
}
