package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import paige.navic.reader.ReaderTransitionCancellationReason
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionFailureReason

class ReaderWebViewPhaseDiagnosticTest {
	@Test
	fun phaseMessageContainsOnlyFixedCategoriesAndNonnegativeElapsedTime() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Commit state=Completed " +
				"reason=None elapsedMs=37",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.Commit,
				ReaderWebViewDiagnosticState.Completed,
				ReaderWebViewDiagnosticReason.None,
				37L
			)
		)
		assertEquals(
			"reader-webview-phase source=Engine phase=RuntimeLoad state=Started " +
				"reason=None elapsedMs=0",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.Engine,
				ReaderWebViewDiagnosticPhase.RuntimeLoad,
				ReaderWebViewDiagnosticState.Started,
				ReaderWebViewDiagnosticReason.None,
				-5L
			)
		)
	}

	@Test
	fun genuineRendererLossPreservesOnlyPlatformCrashAndPriorityPrimitives() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Retirement state=Retired " +
				"reason=RendererGone elapsedMs=12 didCrash=true priorityAtExit=1",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.Retirement,
				ReaderWebViewDiagnosticState.Retired,
				ReaderWebViewDiagnosticReason.RendererGone,
				12L,
				didCrash = true,
				priorityAtExit = 1
			)
		)
	}

	@Test
	fun pollExhaustionDoesNotClaimRendererCrashOrShareItsReason() {
		val readyPoll = retirement(ReaderWebViewDiagnosticReason.RuntimeReadyPollBudget)
		val commitPoll = retirement(ReaderWebViewDiagnosticReason.CommitPollBudget)
		val rendererGone = retirement(ReaderWebViewDiagnosticReason.RendererGone)
		assertNotEquals(readyPoll, commitPoll)
		assertNotEquals(readyPoll, rendererGone)
		assertFalse(readyPoll.contains("didCrash="))
		assertFalse(commitPoll.contains("priorityAtExit="))
	}

	@Test
	fun numericWebErrorsNeverRequireUrlDescriptionOrExceptionInputs() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=RuntimeLoad state=Failed " +
				"reason=WebViewError elapsedMs=9 code=-2 mainFrame=true",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.RuntimeLoad,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.WebViewError,
				9L,
				code = -2,
				mainFrame = true
			)
		)
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=RuntimeLoad state=Failed " +
				"reason=HttpError elapsedMs=10 code=503 mainFrame=false",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.RuntimeLoad,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.HttpError,
				10L,
				code = 503,
				mainFrame = false
			)
		)
	}

	@Test
	fun commitCancellationAndCleanupFailureRemainDifferentFromSuccess() {
		val cancelled = ReaderWebViewPhaseDiagnostic.format(
			ReaderWebViewDiagnosticSource.PassiveRaster,
			ReaderWebViewDiagnosticPhase.Commit,
			ReaderWebViewDiagnosticState.Cancelled,
			ReaderWebViewDiagnosticReason.Cancelled,
			25L
		)
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Commit state=Cancelled " +
				"reason=Cancelled elapsedMs=25",
			cancelled
		)
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=Cleanup state=Failed " +
				"reason=CleanupThrew elapsedMs=3",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.Cleanup,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.CleanupThrew,
				3L
			)
		)
		assertFalse(cancelled.contains("state=Completed"))
	}

	@Test
	fun pixelCopyCompletionReportsActualNumericResultAndExistingLatency() {
		assertEquals(
			"reader-webview-phase source=PassiveRaster phase=PixelCopy state=Failed " +
				"reason=PixelCopyResult elapsedMs=18 code=3",
			ReaderWebViewPhaseDiagnostic.format(
				ReaderWebViewDiagnosticSource.PassiveRaster,
				ReaderWebViewDiagnosticPhase.PixelCopy,
				ReaderWebViewDiagnosticState.Failed,
				ReaderWebViewDiagnosticReason.PixelCopyResult,
				18L,
				code = 3
			)
		)
	}

	@Test
	fun stageFormatterProducesFixedTransportMessages() {
		val messages = listOf(
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.PublicationWrite,
				ReaderWebViewDiagnosticState.Completed,
				ReaderStageDiagnosticReason.None,
				12L,
				current = true,
				persisted = true
			),
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.PublicationWrite,
				ReaderWebViewDiagnosticState.Rejected,
				ReaderStageDiagnosticReason.Stale,
				12L,
				current = false,
				persisted = true
			),
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.TargetProgress,
				ReaderWebViewDiagnosticState.Completed,
				ReaderStageDiagnosticReason.None,
				12L,
				completedCount = 1,
				requiredCount = 3
			),
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.CoordinatorFact,
				ReaderWebViewDiagnosticState.Completed,
				ReaderStageDiagnosticReason.None,
				12L,
				factKind = ReaderTransitionFactKind.RasterProven,
				classification = ReaderTransitionFactClassification.CurrentTransition
			),
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.DeadlineProgress,
				ReaderWebViewDiagnosticState.Completed,
				ReaderStageDiagnosticReason.None,
				12L
			),
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.DisplayPublication,
				ReaderWebViewDiagnosticState.Rejected,
				ReaderStageDiagnosticReason.Stale,
				12L
			)
		)
		assertEquals(
			listOf(
				"reader-stage phase=PublicationWrite state=Completed reason=None elapsedMs=12 current=true persisted=true",
				"reader-stage phase=PublicationWrite state=Rejected reason=Stale elapsedMs=12 current=false persisted=true",
				"reader-stage phase=TargetProgress state=Completed reason=None elapsedMs=12 completedCount=1 requiredCount=3",
				"reader-stage phase=CoordinatorFact state=Completed reason=None elapsedMs=12 fact=RasterProven classification=CurrentTransition",
				"reader-stage phase=DeadlineProgress state=Completed reason=None elapsedMs=12",
				"reader-stage phase=DisplayPublication state=Rejected reason=Stale elapsedMs=12"
			),
			messages
		)
	}

	@Test
	fun stageFormatterKeepsWriterFailureCapacityAndStaleDistinct() {
		val writerFailure = ReaderStageDiagnostic.format(
			ReaderStageDiagnosticPhase.PublicationWrite,
			ReaderWebViewDiagnosticState.Failed,
			ReaderStageDiagnosticReason.CommandThrew,
			12L,
			current = true,
			persisted = false
		)
		val capacity = ReaderStageDiagnostic.format(
			ReaderStageDiagnosticPhase.PublicationWrite,
			ReaderWebViewDiagnosticState.Failed,
			ReaderStageDiagnosticReason.DiskCapacity,
			12L,
			current = true,
			persisted = false
		)
		val stale = ReaderStageDiagnostic.format(
			ReaderStageDiagnosticPhase.PublicationWrite,
			ReaderWebViewDiagnosticState.Rejected,
			ReaderStageDiagnosticReason.Stale,
			12L,
			current = false,
			persisted = false
		)
		assertNotEquals(writerFailure, capacity)
		assertNotEquals(capacity, stale)
		assertTrue(writerFailure.contains("reason=CommandThrew"))
		assertTrue(capacity.contains("reason=DiskCapacity"))
		assertTrue(stale.contains("reason=Stale"))
	}

	@Test
	fun stageFormatterAcceptsEveryStageWithOnlyItsDeclaredFields() {
		val stages = ReaderStageDiagnosticPhase.entries.map { phase ->
			when (phase) {
				ReaderStageDiagnosticPhase.PublicationWrite -> ReaderStageDiagnostic.format(
					phase, ReaderWebViewDiagnosticState.Completed, ReaderStageDiagnosticReason.None, -1L,
					current = true, persisted = true
				)
				ReaderStageDiagnosticPhase.PublicationAcceptance -> ReaderStageDiagnostic.format(
					phase, ReaderWebViewDiagnosticState.Completed, ReaderStageDiagnosticReason.None, 0L, applied = true
				)
				ReaderStageDiagnosticPhase.TargetProgress,
				ReaderStageDiagnosticPhase.RasterReady -> ReaderStageDiagnostic.format(
					phase, ReaderWebViewDiagnosticState.Completed, ReaderStageDiagnosticReason.None, 0L,
					completedCount = 0, requiredCount = 0
				)
				ReaderStageDiagnosticPhase.CoordinatorFact -> ReaderStageDiagnostic.format(
					phase, ReaderWebViewDiagnosticState.Completed, ReaderStageDiagnosticReason.None, 0L,
					factKind = ReaderTransitionFactKind.Intent,
					classification = ReaderTransitionFactClassification.CurrentTransition,
					applied = true,
					outcome = ReaderTransitionOutcomeKind.Succeeded
				)
				else -> if (phase.name == "PublicationCost") publicationCostMessage() else ReaderStageDiagnostic.format(
					phase, ReaderWebViewDiagnosticState.Completed, ReaderStageDiagnosticReason.None, 0L
				)
			}
		}
		assertEquals(ReaderStageDiagnosticPhase.entries.size, stages.size)
		stages.forEach { message -> assertTrue(ReaderStageDiagnostic.isSafeMessage(message)) }
		assertTrue(stages.first().contains("elapsedMs=0"))
	}

	@Test
	fun stageValidatorRejectsInvalidScalarsUnknownVocabularyAndPrivateFields() {
		val valid = "reader-stage phase=TargetProgress state=Completed reason=None elapsedMs=9223372036854775807 completedCount=2147483647 requiredCount=2147483647"
		assertTrue(ReaderStageDiagnostic.isSafeMessage(valid))
		listOf(
			valid.replace("elapsedMs=9223372036854775807", "elapsedMs=9223372036854775808"),
			valid.replace("completedCount=2147483647", "completedCount=2147483648"),
			valid.replace("completedCount=2147483647", "completedCount=-1"),
			valid.replace("completedCount=2147483647 requiredCount=2147483647", "completedCount=2 requiredCount=1"),
			valid.replace("reason=None", "reason=R142_PRIVATE_CANARY"),
			valid.replace("phase=TargetProgress", "phase=R142_PRIVATE_CANARY"),
			valid + " bookId=R142_PRIVATE_CANARY",
			"reader-stage phase=CoordinatorFact state=Completed reason=None elapsedMs=0 fact=R142_PRIVATE_CANARY classification=CurrentTransition",
			"reader-stage phase=CoordinatorFact state=Completed reason=None elapsedMs=0 fact=Intent classification=R142_PRIVATE_CANARY"
		).forEach { message -> assertFalse(ReaderStageDiagnostic.isSafeMessage(message)) }
	}

	@Test
	fun stageFormatterRejectsPhaseFieldsThatAreMissingOrInvalid() {
		assertFailsWith<IllegalArgumentException> {
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.PublicationWrite,
				ReaderWebViewDiagnosticState.Completed,
				ReaderStageDiagnosticReason.None,
				0L
			)
		}
		assertFailsWith<IllegalArgumentException> {
			ReaderStageDiagnostic.format(
				ReaderStageDiagnosticPhase.TargetProgress,
				ReaderWebViewDiagnosticState.Completed,
				ReaderStageDiagnosticReason.None,
				0L,
				completedCount = 2,
				requiredCount = 1
			)
		}
		assertFalse(ReaderStageDiagnostic.isSafeMessage(
			"reader-stage phase=DeadlineProgress state=Completed reason=None elapsedMs=0 current=true"
		))
	}

	@Test
	fun stageFormatterPermitsOnlyMatchingTerminalFactDetailsAndContainsSinkFailures() {
		val failed = ReaderStageDiagnostic.format(
			ReaderStageDiagnosticPhase.CoordinatorFact,
			ReaderWebViewDiagnosticState.Failed,
			ReaderStageDiagnosticReason.CommandThrew,
			0L,
			factKind = ReaderTransitionFactKind.Intent,
			classification = ReaderTransitionFactClassification.CurrentTransition,
			outcome = ReaderTransitionOutcomeKind.Failed,
			failure = ReaderTransitionFailureReason.PortRejected
		)
		assertTrue(ReaderStageDiagnostic.isSafeMessage(failed))
		assertFalse(ReaderStageDiagnostic.isSafeMessage(failed.replace("outcome=Failed", "outcome=Succeeded")))
		assertTrue(runCatching {
			ReaderStageDiagnostic.emit(
				ReaderStageDiagnosticPhase.CoordinatorFact,
				ReaderWebViewDiagnosticState.Cancelled,
				ReaderStageDiagnosticReason.Cancelled,
				0L,
				factKind = ReaderTransitionFactKind.Intent,
				classification = ReaderTransitionFactClassification.CurrentTransition,
				outcome = ReaderTransitionOutcomeKind.Cancelled,
				cancellation = ReaderTransitionCancellationReason.UserCancelled,
				write = { error("writer failed") }
			)
		}.isSuccess)
	}

	@Test
	fun stageEmissionContainsInvalidFormattingWithoutCallingTheSink() {
		var writes = 0
		ReaderStageDiagnostic.emit(
			ReaderStageDiagnosticPhase.PublicationWrite,
			ReaderWebViewDiagnosticState.Completed,
			ReaderStageDiagnosticReason.None,
			0L,
			write = { writes++ }
		)
		assertEquals(0, writes)
	}

	@Test
	fun publicationCostAcceptsOnlyTerminalMeasurementStatesWithNoAuthorityReason() {
		val message = publicationCostMessage()
		assertTrue(ReaderStageDiagnostic.isSafeMessage(message))
		assertTrue(ReaderStageDiagnostic.isSafeMessage(message.replace("state=Completed", "state=Failed")))
		listOf("Started", "Rejected", "Cancelled", "Retired").forEach { state ->
			assertFalse(ReaderStageDiagnostic.isSafeMessage(message.replace("state=Completed", "state=$state")))
		}
		ReaderStageDiagnosticReason.entries.filter { it.name != "None" }.forEach { reason ->
			assertFalse(ReaderStageDiagnostic.isSafeMessage(message.replace("reason=None", "reason=${reason.name}")))
		}
	}

	@Test
	fun publicationCostMaskDistinguishesMeasuredZeroFromUnvisitedSpans() {
		assertTrue(ReaderStageDiagnostic.isSafeMessage(publicationCostMessage(mask = "0")))
		publicationCostFields.forEachIndexed { bit, field ->
			val measuredZero = publicationCostMessage(mask = (1 shl bit).toString())
			assertTrue(ReaderStageDiagnostic.isSafeMessage(measuredZero), field)
			assertTrue(ReaderStageDiagnostic.isSafeMessage(measuredZero.replace("$field=0", "$field=7")), field)
			assertFalse(ReaderStageDiagnostic.isSafeMessage(
				publicationCostMessage(mask = "0").replace("$field=0", "$field=7")
			), field)
			assertFalse(ReaderStageDiagnostic.isSafeMessage(
				publicationCostMessage(mask = (1 shl ((bit + 1) % 10)).toString())
					.replace("$field=0", "$field=7")
			), field)
		}
	}

	@Test
	fun publicationCostRejectsOutOfRangeOrNondecimalDurationsAndMasks() {
		val message = publicationCostMessage()
		assertTrue(ReaderStageDiagnostic.isSafeMessage(publicationCostMessage(duration = "9223372036854775807")))
		publicationCostFields.forEach { field ->
			listOf("-1", "+1", "1.0", "1e0", "true", "", "١", "9223372036854775808").forEach { value ->
				assertFalse(ReaderStageDiagnostic.isSafeMessage(message.replace("$field=0", "$field=$value")), "$field=$value")
			}
		}
		listOf("-1", "1024", "2147483648", "9223372036854775808", "true", "", "+1", "١").forEach { mask ->
			assertFalse(ReaderStageDiagnostic.isSafeMessage(publicationCostMessage(mask = mask)), mask)
		}
		assertFalse(ReaderStageDiagnostic.isSafeMessage(message.replace("elapsedMs=0", "elapsedMs=-1")))
		assertFalse(ReaderStageDiagnostic.isSafeMessage(message.replace("elapsedMs=0", "elapsedMs=9223372036854775808")))
	}

	@Test
	fun publicationCostGrammarRejectsPrivateAuthorityMissingDuplicateOrReorderedFields() {
		val message = publicationCostMessage()
		assertTrue(ReaderStageDiagnostic.isSafeMessage(message))
		val invalid = listOf(
			message + " bookId=PUBLICATION_COST_PRIVATE_CANARY",
			message + " href=https://private.invalid/PUBLICATION_COST_PRIVATE_CANARY",
			message + " path=D:/PUBLICATION_COST_PRIVATE_CANARY",
			message + " exception=PUBLICATION_COST_PRIVATE_CANARY",
			message + " current=true",
			message + " persisted=true",
			message + " outcome=Succeeded",
			message.replace("encodeMs=0", "encodeMs=PUBLICATION_COST_PRIVATE_CANARY"),
			message.replace("phase=PublicationCost", "phase=PUBLICATION_COST_PRIVATE_CANARY"),
			message.replace("reason=None", "reason=PUBLICATION_COST_PRIVATE_CANARY"),
			message.replace(" measuredMask=1023", ""),
			message.replace(" hashMs=0", ""),
			message + " hashMs=0",
			message.replace("ioDispatchMs=0 ioReturnMs=0", "ioReturnMs=0 ioDispatchMs=0"),
			message.replace(" encodeMs=0", "  encodeMs=0"),
			message + "\n"
		)
		invalid.forEach { candidate -> assertFalse(ReaderStageDiagnostic.isSafeMessage(candidate)) }
	}

	@Test
	fun publicationCostWorstCaseLongValuesFitTheExisting512CharacterLimit() {
		val message = publicationCostMessage(duration = "9223372036854775807")
			.replace("state=Completed", "state=Failed")
			.replace("elapsedMs=0", "elapsedMs=9223372036854775807")
		assertTrue(message.length <= 512)
		assertTrue(ReaderStageDiagnostic.isSafeMessage(message))
		assertFalse(ReaderStageDiagnostic.isSafeMessage(message + "x".repeat(513 - message.length)))
	}

	@Test
	fun actualCostAccumulatorFormatsMeasuredZeroUnavailableAndScalarSpansWithoutSinkAuthority() {
		val cost = ReaderPublicationCost()
		cost.record(ReaderPublicationCostSpan.Encode, 100L, 100L)
		cost.record(ReaderPublicationCostSpan.Hash, 10_000_000L, 19_999_999L)
		cost.record(ReaderPublicationCostSpan.ManifestSync, 2L, 1L)
		val message = ReaderStageDiagnostic.format(
			ReaderStageDiagnosticPhase.PublicationCost, ReaderWebViewDiagnosticState.Completed,
			ReaderStageDiagnosticReason.None, 0L, cost = cost
		)
		assertEquals(publicationCostMessage(mask = "20").replace("hashMs=0", "hashMs=9"), message)
		assertTrue(runCatching {
			ReaderStageDiagnostic.emit(
				ReaderStageDiagnosticPhase.PublicationCost, ReaderWebViewDiagnosticState.Failed,
				ReaderStageDiagnosticReason.None, 0L, cost = cost, write = { error("sink failed") }
			)
		}.isSuccess)
		val original = IllegalStateException("original operation failure")
		val failure = runCatching {
			readerMeasurePublicationCost(cost, ReaderPublicationCostSpan.RasterSync) { throw original }
		}.exceptionOrNull()
		assertTrue(failure === original)
		assertEquals(28, cost.measuredMask)
		assertEquals(17, readerMeasurePublicationCost(null, ReaderPublicationCostSpan.Encode) { 17 })
	}

	private val publicationCostFields = listOf(
		"ioDispatchMs", "ioReturnMs", "encodeMs", "rasterSyncMs", "hashMs",
		"cacheLockMs", "rasterPromoteMs", "manifestSerializeMs", "manifestSyncMs", "manifestPromoteMs"
	)

	private fun publicationCostMessage(mask: String = "1023", duration: String = "0"): String =
		"reader-stage phase=PublicationCost state=Completed reason=None elapsedMs=0 measuredMask=$mask " +
			"ioDispatchMs=$duration ioReturnMs=$duration encodeMs=$duration rasterSyncMs=$duration " +
			"hashMs=$duration cacheLockMs=$duration rasterPromoteMs=$duration " +
			"manifestSerializeMs=$duration manifestSyncMs=$duration manifestPromoteMs=$duration"

	private fun retirement(reason: ReaderWebViewDiagnosticReason): String =
		ReaderWebViewPhaseDiagnostic.format(
			ReaderWebViewDiagnosticSource.PassiveRaster,
			ReaderWebViewDiagnosticPhase.Retirement,
			ReaderWebViewDiagnosticState.Retired,
			reason,
			48L
		)
}
