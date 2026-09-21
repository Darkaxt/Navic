package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReaderResumableTransitionLivenessTest {
	@Test
	fun everyNonTerminalPhaseOfEveryOperationNamesAllSixLivenessFields() {
		val retainedOwner = journalAwaitingSettlement().retainedOwner

		ReaderTransitionOperation.entries.forEach { operation ->
			val id = transitionTestId(
				operation = operation,
				expectedBinding = ReaderExpectedPresentationBinding.Exact(transitionTestBinding())
			)
			val phases = ReaderTransitionLivenessTable.phases(
				id,
				retainedOwner,
				gestureId = ReaderTransitionGestureId(101L).takeIf {
					operation == ReaderTransitionOperation.CurlClaimAndSettlement
				}
			)
			assertEquals(ReaderTransitionPhaseKind.entries.toSet(), phases.map { it.kind }.toSet())
			phases.forEach { phase ->
				assertTrue(phase.contract.awaitedProofs.isNotEmpty())
				assertEquals(id, phase.contract.deadlineOwner)
				assertNotNull(phase.contract.supersession)
				assertEquals(retainedOwner, phase.contract.retainedOwner)
				assertNotNull(phase.contract.inputLease)
				assertTrue(phase.contract.callbackSources.isNotEmpty())
			}
		}
	}

	@Test
	fun wakeKindsAreExactlyTheTenFiniteContractValues() {
		assertEquals(
			setOf(
				ReaderTransitionWakeKind.Restored,
				ReaderTransitionWakeKind.HostAvailable,
				ReaderTransitionWakeKind.WebViewAvailable,
				ReaderTransitionWakeKind.PaginationProfileReady,
				ReaderTransitionWakeKind.FoliateDestinationCommitted,
				ReaderTransitionWakeKind.RasterProofAvailable,
				ReaderTransitionWakeKind.RendererCapacityAvailable,
				ReaderTransitionWakeKind.PresentationCommandApplied,
				ReaderTransitionWakeKind.VisibilityRestored,
				ReaderTransitionWakeKind.Retry
			),
			ReaderTransitionWakeKind.entries.toSet()
		)
	}

	@Test
	fun livenessMatrixNamesExactProofsAndCallbackSourcesPerOperation() {
		val materialSources = setOf(
			ReaderTransitionFactKind.MaterialBindingAllocated,
			ReaderTransitionFactKind.RasterProgress,
			ReaderTransitionFactKind.RasterProven,
			ReaderTransitionFactKind.RasterDeferred,
			ReaderTransitionFactKind.RasterFailed,
			ReaderTransitionFactKind.DeckReserved,
			ReaderTransitionFactKind.DeckOwned,
			ReaderTransitionFactKind.DeckPrepared,
			ReaderTransitionFactKind.DeckRejected,
			ReaderTransitionFactKind.RendererCapacityAvailable,
			ReaderTransitionFactKind.PreparedFrame,
			ReaderTransitionFactKind.ResourceLost,
			ReaderTransitionFactKind.DeadlineExpired
		)
		val materialProofs = setOf(
			ReaderTransitionProofKind.MaterialBindingAllocation,
			ReaderTransitionProofKind.Raster,
			ReaderTransitionProofKind.DeckOwnership,
			ReaderTransitionProofKind.DeckPrepared,
			ReaderTransitionProofKind.PreparedFrame
		)
		val expected = mapOf(
			ReaderTransitionOperation.BootstrapNativePage to (
				(materialProofs + ReaderTransitionProofKind.SemanticDestination) to
					(materialSources + ReaderTransitionFactKind.FoliateDestinationCommitted)
			),
			ReaderTransitionOperation.ShellCoverCommit to (
				setOf(ReaderTransitionProofKind.CoverPostDraw) to setOf(
					ReaderTransitionFactKind.CoverPostDraw,
					ReaderTransitionFactKind.ResourceLost,
					ReaderTransitionFactKind.DeadlineExpired
				)
			),
			ReaderTransitionOperation.CoverToPageEntry to (materialProofs to materialSources),
			ReaderTransitionOperation.CurlClaimAndSettlement to (
				setOf(
					ReaderTransitionProofKind.SettlementAcknowledgement,
					ReaderTransitionProofKind.PreparedFrame
				) to setOf(
					ReaderTransitionFactKind.FoliateDestinationCommitted,
					ReaderTransitionFactKind.SettlementAcknowledged,
					ReaderTransitionFactKind.PreparedFrame,
					ReaderTransitionFactKind.ResourceLost,
					ReaderTransitionFactKind.DeadlineExpired
				)
			),
			ReaderTransitionOperation.NativeToLiveHandoff to (
				setOf(ReaderTransitionProofKind.WebViewExposure) to setOf(
					ReaderTransitionFactKind.WebViewExposure,
					ReaderTransitionFactKind.ResourceLost,
					ReaderTransitionFactKind.DeadlineExpired
				)
			),
			ReaderTransitionOperation.LiveToNativeHandback to (
				setOf(ReaderTransitionProofKind.PreparedFrame) to setOf(
					ReaderTransitionFactKind.PreparedFrame,
					ReaderTransitionFactKind.ResourceLost,
					ReaderTransitionFactKind.DeadlineExpired
				)
			),
			ReaderTransitionOperation.ExternalSemanticRelocation to (
				(materialProofs + ReaderTransitionProofKind.SemanticDestination) to
					(materialSources + ReaderTransitionFactKind.FoliateDestinationCommitted)
			),
			ReaderTransitionOperation.ReflowProfileReplacement to (
				materialProofs to (materialSources + ReaderTransitionFactKind.ViewportProfileReplaced)
			),
			ReaderTransitionOperation.VisibilityRestore to (
				(materialProofs + setOf(
					ReaderTransitionProofKind.HostAvailable,
					ReaderTransitionProofKind.SemanticDestination,
					ReaderTransitionProofKind.PaginationProfile
				)) to (materialSources + setOf(
					ReaderTransitionFactKind.HostAvailable,
					ReaderTransitionFactKind.FoliateDestinationCommitted,
					ReaderTransitionFactKind.PaginationProfileReady
				))
			),
			ReaderTransitionOperation.RendererRecovery to (
				setOf(
					ReaderTransitionProofKind.RendererGeneration,
					ReaderTransitionProofKind.MaterialBindingAllocation,
					ReaderTransitionProofKind.DeckOwnership,
					ReaderTransitionProofKind.DeckPrepared,
					ReaderTransitionProofKind.PreparedFrame
				) to setOf(
					ReaderTransitionFactKind.MaterialBindingAllocated,
					ReaderTransitionFactKind.RendererGenerationReady,
					ReaderTransitionFactKind.DeckReserved,
					ReaderTransitionFactKind.DeckOwned,
					ReaderTransitionFactKind.DeckPrepared,
					ReaderTransitionFactKind.DeckRejected,
					ReaderTransitionFactKind.RendererCapacityAvailable,
					ReaderTransitionFactKind.PreparedFrame,
					ReaderTransitionFactKind.ResourceLost,
					ReaderTransitionFactKind.DeadlineExpired
				)
			),
			ReaderTransitionOperation.PublicationClose to (
				setOf(
					ReaderTransitionProofKind.Cancellation,
					ReaderTransitionProofKind.ReleaseDrain
				) to setOf(
					ReaderTransitionFactKind.ResourceReleased,
					ReaderTransitionFactKind.PublicationClosed,
					ReaderTransitionFactKind.DeadlineExpired
				)
			)
		)

		assertEquals(ReaderTransitionOperation.entries.toSet(), expected.keys)
		expected.forEach { (operation, row) ->
			val liveness = ReaderTransitionLivenessTable.forOperation(operation)
			assertEquals(row.first, liveness.requiredProofs, operation.name)
			assertEquals(row.second, liveness.callbackSources, operation.name)
		}
	}

	@Test
	fun deadlineDefaultsMatchTheOperationMatrixExactly() {
		val material = setOf(
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderTransitionOperation.CoverToPageEntry,
			ReaderTransitionOperation.ExternalSemanticRelocation,
			ReaderTransitionOperation.ReflowProfileReplacement,
			ReaderTransitionOperation.VisibilityRestore,
			ReaderTransitionOperation.RendererRecovery
		)
		val twoSecond = setOf(
			ReaderTransitionOperation.ShellCoverCommit,
			ReaderTransitionOperation.NativeToLiveHandoff,
			ReaderTransitionOperation.LiveToNativeHandback,
			ReaderTransitionOperation.PublicationClose
		)

		material.forEach { operation ->
			assertEquals(ReaderTransitionDeadlinePolicy(10_000L, 30_000L), operation.deadlinePolicy())
		}
		twoSecond.forEach { operation ->
			assertEquals(ReaderTransitionDeadlinePolicy(null, 2_000L), operation.deadlinePolicy())
		}
		assertEquals(
			ReaderTransitionDeadlinePolicy(null, 5_000L),
			ReaderTransitionOperation.CurlClaimAndSettlement.deadlinePolicy()
		)
		assertEquals(ReaderTransitionOperation.entries.toSet(), material + twoSecond + ReaderTransitionOperation.CurlClaimAndSettlement)
	}

	@Test
	fun eachOperationNamesExactTimeoutOutcomeAndFiniteWakeSet() {
		val expected = mapOf(
			ReaderTransitionOperation.BootstrapNativePage to Triple(ReaderTransitionFailureReason.MaterialTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.ShellCoverCommit to Triple(ReaderTransitionFailureReason.CoverCommitTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.HostAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.CoverToPageEntry to Triple(ReaderTransitionFailureReason.PageEntryTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.RasterProofAvailable, ReaderTransitionWakeKind.RendererCapacityAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.CurlClaimAndSettlement to Triple(ReaderTransitionFailureReason.SettlementTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.FoliateDestinationCommitted, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.NativeToLiveHandoff to Triple(ReaderTransitionFailureReason.LiveExposureTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.WebViewAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.LiveToNativeHandback to Triple(ReaderTransitionFailureReason.NativeHandbackTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.RendererCapacityAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.ExternalSemanticRelocation to Triple(ReaderTransitionFailureReason.ExternalRelocationTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.FoliateDestinationCommitted, ReaderTransitionWakeKind.RasterProofAvailable, ReaderTransitionWakeKind.RendererCapacityAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.ReflowProfileReplacement to Triple(ReaderTransitionFailureReason.ReflowTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.PaginationProfileReady, ReaderTransitionWakeKind.RasterProofAvailable, ReaderTransitionWakeKind.RendererCapacityAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.VisibilityRestore to Triple(ReaderTransitionFailureReason.RestoreTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.HostAvailable, ReaderTransitionWakeKind.WebViewAvailable, ReaderTransitionWakeKind.PaginationProfileReady, ReaderTransitionWakeKind.RendererCapacityAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.RendererRecovery to Triple(ReaderTransitionFailureReason.RendererRecoveryTimeout, ReaderTransitionRetryability.Retryable, setOf(ReaderTransitionWakeKind.RendererCapacityAvailable, ReaderTransitionWakeKind.Retry)),
			ReaderTransitionOperation.PublicationClose to Triple(ReaderTransitionFailureReason.CloseDrainTimeout, ReaderTransitionRetryability.NonRetryable, emptySet())
		)

		assertEquals(ReaderTransitionOperation.entries.toSet(), expected.keys)
		expected.forEach { (operation, row) ->
			val liveness = ReaderTransitionLivenessTable.forOperation(operation)
			assertEquals(operation.deadlinePolicy(), liveness.deadlinePolicy)
			assertEquals(row.first, liveness.timeoutFailureReason)
			assertEquals(row.second, liveness.timeoutRetryability)
			assertEquals(row.third, liveness.wakeKinds)
			assertTrue(liveness.wakeKinds.all { it in ReaderTransitionWakeKind.entries })
		}
	}

	@Test
	fun timeoutTerminatesWithoutAutomaticRetry() {
		val fixture = journalAwaitingSettlement()
		val result = fixture.journal.reduce(ReaderTransitionFact.DeadlineExpired(fixture.id), nowMillis = 5_000L)

		assertEquals(null, result.state.active)
		assertTrue(result.state.lastOutcome is ReaderTransitionOutcome.Failed)
		assertFalse(result.commands.any {
			it is ReaderTransitionCommand.RequestSemanticSynchronization ||
				it is ReaderTransitionCommand.RequestRasterPreparation ||
				it is ReaderTransitionCommand.ReserveDeck ||
				it is ReaderTransitionCommand.RequestFramePresentation
		})
	}

	@Test
	fun terminalOutcomesAreExhaustiveAtCompileTime() {
		val binding = transitionTestBinding()
		val retained = journalAwaitingSettlement().retainedOwner
		val resumeRecord = ReaderTransitionResumeRecord(
			operation = ReaderTransitionOperation.VisibilityRestore,
			reason = ReaderTransitionDeferralReason.VisibilityRestore,
			nonce = ReaderTransitionNonce(1L, 2L),
			issuedAtMillis = 10L,
			expiresAtMillis = 20L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.VisibilityRestored
		)
		val outcomes: List<ReaderTransitionOutcome> = listOf(
			ReaderTransitionOutcome.Succeeded(retained, binding),
			ReaderTransitionOutcome.Failed(ReaderTransitionFailureReason.MaterialTimeout, ReaderTransitionRetryability.Retryable, retained),
			ReaderTransitionOutcome.Cancelled(ReaderTransitionCancellationReason.Superseded, retained),
			ReaderTransitionOutcome.Deferred(resumeRecord, retained)
		)

		assertEquals(
			setOf("succeeded", "failed", "cancelled", "deferred"),
			outcomes.map { it.exhaustiveKind() }.toSet()
		)
	}
}

private fun ReaderTransitionOutcome.exhaustiveKind(): String = when (this) {
	is ReaderTransitionOutcome.Succeeded -> "succeeded"
	is ReaderTransitionOutcome.Failed -> "failed"
	is ReaderTransitionOutcome.Cancelled -> "cancelled"
	is ReaderTransitionOutcome.Deferred -> "deferred"
}
