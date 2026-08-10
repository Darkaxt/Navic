package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderSettingsWebViewMutationCoordinatorTest {
	@Test
	fun settingsMutationWaitsForPassiveRestorationAndCommitsBeforePassiveResumes() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val events = mutableListOf<String>()
		val ownership = ReaderForegroundWebViewOwnership {
			events += "passive-available"
		}
		checkNotNull(
			ownership.tryAcquirePassive(sessionId = 7L) { onRestored ->
				finishRestoration = onRestored
			}
		)
		val coordinator = ReaderSettingsWebViewMutationCoordinator(
			ownership = ownership,
			onSnapshotCommitted = { snapshotKey ->
				events += "snapshot:$snapshotKey"
			}
		)
		val readiness = mutableListOf<ReaderSettingsWebViewMutationReadiness>()

		coordinator.acquireSettingsMutation(requestId = 11L, readiness::add)

		assertTrue(readiness.isEmpty())
		assertEquals(1, ownership.snapshot().restorationCallbacks)
		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.Restored
		)
		val mutation = assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(
			readiness.single()
		).mutation
		assertTrue(mutation.isCurrent())

		assertTrue(mutation.commit(snapshotKey = 29))

		assertEquals(
			listOf("snapshot:29", "passive-available"),
			events
		)
		assertFalse(mutation.isCurrent())
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun failedPassiveRestorationRejectsSettingsMutationWithoutChangingSnapshot() {
		var finishRestoration:
			((ReaderPageRasterCancellationRestoration) -> Unit)? = null
		val committedSnapshots = mutableListOf<Int>()
		val ownership = ReaderForegroundWebViewOwnership()
		checkNotNull(
			ownership.tryAcquirePassive(sessionId = 7L) { onRestored ->
				finishRestoration = onRestored
			}
		)
		val coordinator = ReaderSettingsWebViewMutationCoordinator(
			ownership = ownership,
			onSnapshotCommitted = committedSnapshots::add
		)
		val readiness = mutableListOf<ReaderSettingsWebViewMutationReadiness>()

		coordinator.acquireSettingsMutation(requestId = 11L, readiness::add)
		checkNotNull(finishRestoration)(
			ReaderPageRasterCancellationRestoration.TimedOut
		)

		assertEquals(
			ReaderSettingsWebViewMutationReadiness.Rejected(
				ReaderForegroundWebViewLiveReadiness.Failed(
					ReaderPageRasterCancellationRestoration.TimedOut
				)
			),
			readiness.single()
		)
		assertTrue(committedSnapshots.isEmpty())
		assertEquals(0, ownership.snapshot().liveClaims)
	}

	@Test
	fun settingsMutationWaitsForAnExistingLiveMutation() {
		val ownership = ReaderForegroundWebViewOwnership()
		val pageClaim = ownership.acquireLive(gestureId = 4L)
		val pageGeneration = checkNotNull(
			ownership.beginLiveMutation(pageClaim)
		)
		val coordinator = ReaderSettingsWebViewMutationCoordinator(
			ownership = ownership,
			onSnapshotCommitted = {}
		)
		val readiness = mutableListOf<ReaderSettingsWebViewMutationReadiness>()

		coordinator.acquireSettingsMutation(requestId = 11L, readiness::add)

		assertTrue(readiness.isEmpty())
		assertTrue(ownership.isCurrent(pageClaim, pageGeneration))
		assertTrue(ownership.releaseLive(pageClaim))
		val settingsMutation =
			assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(
				readiness.single()
			).mutation
		assertTrue(settingsMutation.cancel())
	}

	@Test
	fun pageMutationWaitsUntilSettingsVisualCommitReleasesExclusiveOwnership() {
		val ownership = ReaderForegroundWebViewOwnership()
		val coordinator = ReaderSettingsWebViewMutationCoordinator(
			ownership = ownership,
			onSnapshotCommitted = {}
		)
		val settingsReadiness = mutableListOf<ReaderSettingsWebViewMutationReadiness>()
		coordinator.acquireSettingsMutation(
			requestId = 11L,
			settingsReadiness::add
		)
		val settingsMutation =
			assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(
				settingsReadiness.single()
			).mutation
		val pageClaim = ownership.acquireLive(gestureId = 4L)
		val pageReadiness = mutableListOf<ReaderForegroundWebViewLiveReadiness>()

		ownership.whenLiveReady(pageClaim, pageReadiness::add)

		assertTrue(pageReadiness.isEmpty())
		assertTrue(settingsMutation.isCurrent())
		assertTrue(settingsMutation.commit(snapshotKey = 29))
		assertEquals(
			listOf<ReaderForegroundWebViewLiveReadiness>(
				ReaderForegroundWebViewLiveReadiness.Ready
			),
			pageReadiness
		)
		assertTrue(ownership.releaseLive(pageClaim))
	}
}
