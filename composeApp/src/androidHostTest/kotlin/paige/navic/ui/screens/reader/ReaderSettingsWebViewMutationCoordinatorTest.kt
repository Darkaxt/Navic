package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReaderSettingsWebViewMutationCoordinatorTest {
	@Test
	fun frozenCommitKeepsPhysicalCurrencyAndDefersTheOriginalSnapshotExactlyOnce() {
		var snapshots = 0
		val ownership = ReaderForegroundWebViewOwnership()
		val coordinator = ReaderSettingsWebViewMutationCoordinator(ownership) { snapshots++ }
		var accepted: ReaderSettingsWebViewMutation? = null
		coordinator.acquireSettingsMutation(11L) {
			accepted = assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(it).mutation
		}
		val mutation = checkNotNull(accepted)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		ownership.freezeForTransitionActivation(domain)
		assertTrue(mutation.isCurrent(), "physical cleanup currency survives freeze alone")
		assertFalse(mutation.commit(29), "a frozen owner cannot start snapshot publication")
		assertEquals(0, snapshots)
		ownership.snapshotFrozenOwnership().forEach { ownership.drainFrozenOwnership(it.physicalIdentity) {} }
		assertTrue(ownership.snapshotFrozenOwnership().isEmpty(), "unstarted publication is not running physical work")
		assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
		assertEquals(1, snapshots)
		assertFalse(mutation.commit(29))
		assertEquals(1, snapshots)
	}

	@Test
	fun frozenCommitThenPermanentCloseNeverReplaysSnapshot() {
		var snapshots = 0
		val ownership = ReaderForegroundWebViewOwnership()
		val coordinator = ReaderSettingsWebViewMutationCoordinator(ownership) { snapshots++ }
		var accepted: ReaderSettingsWebViewMutation? = null
		coordinator.acquireSettingsMutation(11L) {
			accepted = assertIs<ReaderSettingsWebViewMutationReadiness.Ready>(it).mutation
		}
		val mutation = checkNotNull(accepted)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		ownership.freezeForTransitionActivation(domain)
		mutation.commit(29)
		ownership.close()
		ownership.snapshotFrozenOwnership().forEach { ownership.drainFrozenOwnership(it.physicalIdentity) {} }
		assertTrue(ownership.restoreAfterTransitionActivation(domain) is ReaderPortCommandResult.Rejected)
		assertEquals(0, snapshots)
		assertFalse(mutation.isCurrent())
	}

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
