package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual Android Engine transport guardrails, not Foliate/layout or Native activation proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
	shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class, Task398CanonicalStoragePathShadow::class])
class ReaderEngineCurrentDestinationHostTest {
	@Test
	fun matchingDispatchedResultDrainsQueryAndDispatchesNextOrdinaryCommand() = runTest {
		withCurrentDestinationEngine { bench ->
			val query = bench.dispatchQueryWithSuccessor()
			bench.post(bench.result(query))
			assertFalse(bench.pending().pendingCommands.any { it.dispatch.id == query.id },
				"Matching dispatched result must retire actual query custody")
			bench.expectDispatch("nextPage")
		}
	}

	@Test
	fun ackAndUnrelatedEventsRetainActualDispatchedQueryCustody() = runTest {
		withCurrentDestinationEngine { bench ->
			val query = bench.dispatchQueryWithSuccessor()
			val script = bench.lastScript()
			val unrelated = listOf(
				bench.envelope("commandAck", query.id),
				buildJsonObject {
					put("type", "locationChanged")
					put("commandId", query.id)
					put("foliateSessionId", query.session)
					put("href", "synthetic-chapter.xhtml")
					put("destinationFoliateSessionId", query.session)
					put("destinationCommitSequence", 1L)
				},
				bench.envelope("ready"), bench.envelope("publicationReady"),
				bench.envelope("syntheticUnknownEvent"),
				bench.result(query, session = "synthetic-wrong-session"),
				bench.result(query, id = bench.openDispatch.id)
			)
			unrelated.forEach { message ->
				bench.post(message)
				assertTrue(bench.lastScript() == script, "Unrelated event cannot dispatch successor")
				assertTrue(bench.pending().pendingCommands.firstOrNull()?.dispatch?.id == query.id,
					"ACK and unrelated events must retain the exact query head")
			}
			assertEquals(1, bench.events.count {
				it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.LocationChanged
			}, "Ordinary location control must actually reach the Engine callback")
		}
	}

	@Test
	fun queuedUndispatchedResultCannotRetireQueryOrBypassOrdinaryHead() = runTest {
		withCurrentDestinationEngine { bench ->
			bench.submit(ReaderBridgeCommand.PreviousPage,
				ReaderBridgeCommand.ResolveCurrentDestination(bench.openDispatch.session), ReaderBridgeCommand.NextPage)
			val head = bench.expectDispatch("previousPage")
			val pending = bench.pending().pendingCommands.single {
				it.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination
			}
			check(pending.lastDispatchedGeneration == null) { "Fixture query must be queued, not dispatched" }
			val query = EngineObservedDispatch(pending.dispatch.id, bench.openDispatch.session)
			val script = bench.lastScript()
			bench.post(bench.result(query))
			assertTrue(bench.lastScript() == script, "Undispatched result cannot bypass ordinary head")
			assertTrue(bench.pending().pendingCommands.any { it.dispatch.id == query.id },
				"Queued query must retain custody after unsolicited result")
			bench.post(bench.envelope("commandAck", head.id))
			val dispatched = bench.expectDispatch("resolveCurrentDestination")
			assertTrue(dispatched.id == query.id, "Ordinary ACK must dispatch the same retained query")
		}
	}

	@Test
	fun matchingReadOnlyFailureDrainsQueryWithoutReplacingExactWebView() = runTest {
		withCurrentDestinationEngine { bench ->
			val query = bench.dispatchQueryWithSuccessor()
			val engine = bench.webView()
			bench.post(bench.envelope("commandFailed", query.id))
			assertTrue(bench.webView() === engine, "Read-only failure must preserve the exact real Engine WebView")
			assertFalse(bench.pending().pendingCommands.any { it.dispatch.id == query.id },
				"Read-only failure must drain actual query custody")
			bench.expectDispatch("nextPage")
		}
	}

	@Test
	fun typedQueryResultNeverUsesGenericLegacyFoliateBridgeRoute() = runTest {
		withCurrentDestinationEngine { bench ->
			val query = bench.dispatchQueryWithSuccessor()
			bench.post(bench.result(query))
			assertEquals(0, bench.events.count {
				it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.CurrentDestinationResolved
			}, "Private query result cannot become an ordinary legacy presentation observation")
			// No executor/context/continuation success assertion: that production carrier is not installed here.
		}
	}

	@Test
	fun duplicateOldResultCannotConsumeNewQueryHead() = runTest {
		withCurrentDestinationEngine { bench ->
			bench.submit(ReaderBridgeCommand.ResolveCurrentDestination(bench.openDispatch.session),
				ReaderBridgeCommand.ResolveCurrentDestination(bench.openDispatch.session), ReaderBridgeCommand.NextPage)
			val first = bench.expectDispatch("resolveCurrentDestination")
			bench.post(bench.result(first))
			val second = bench.expectDispatch("resolveCurrentDestination")
			assertTrue(first.id != second.id, "First consumption must dispatch the distinct queued query")
			val script = bench.lastScript()
			bench.post(bench.result(first))
			assertTrue(bench.lastScript() == script, "Duplicate result cannot release successor")
			assertTrue(bench.pending().pendingCommands.firstOrNull()?.dispatch?.id == second.id,
				"Duplicate result cannot retire successor query custody")
		}
	}

	@Test
	fun publicationReplacementRetiresOldQueryWithoutReplayingIt() = runTest {
		withCurrentDestinationEngine { bench ->
			val query = bench.dispatchQueryWithSuccessor()
			bench.replacePublication()
			val replacement = bench.expectDispatch("openPublication")
			assertTrue(replacement.session != query.session, "Replacement must use its actual new open session")
			assertFalse(bench.pending().pendingCommands.any { it.dispatch.id == query.id },
				"Publication replacement must retire old query custody")
			val script = bench.lastScript()
			val eventCount = bench.events.size
			bench.post(bench.result(query))
			assertTrue(bench.lastScript() == script, "Old result cannot release replacement open command")
			assertEquals(eventCount, bench.events.size, "Old publication result cannot replay through the Engine callback")
			bench.post(bench.envelope("commandAck", replacement.id))
			bench.submit(ReaderBridgeCommand.NextPage)
			bench.expectDispatch("nextPage")
		}
	}

	/** Wire prerequisite only, not an authenticated installed Curl turn or executor receipt. */
	@Test
	fun originalDecoderKeepsExactNavigationCommandCorrelationOnSettledLocation() {
		val wireCommand = "synthetic-installed-turn-wire-command"
		val event = decodeReaderBridgeEvent(buildJsonObject {
			put("type", "locationChanged")
			put("commandId", wireCommand)
			put("foliateSessionId", "synthetic-installed-turn-wire-session")
			put("destinationFoliateSessionId", "synthetic-installed-turn-wire-session")
			put("destinationCommitSequence", 1L)
			put("pageIndex", 4)
			put("pageTurnSettleToken", "synthetic-installed-turn-wire-token")
			put("pageTurnSettleSessionId", "synthetic-installed-turn-wire-session")
			put("pageTurnSettleRasterGeneration", 11L)
			put("pageTurnSettleTextureGeneration", 13L)
		}.toString()) as? ReaderBridgeEvent.LocationChanged
		assertTrue(event != null, "E407.Wire.Prerequisite.OriginalLocationDecoder")
		assertTrue(event.pageTurnSettleToken != null && event.destinationCommitIdentity != null,
			"E407.Wire.Prerequisite.OriginalSettlementDecoded")
		// Proposed PUBLIC metadata accessor only. Absence is a fixed prerequisite failure,
		// not a compiler error or a fabricated navigation/semantic completion.
		val commandAccessor = event.javaClass.methods.singleOrNull {
			it.name == "getCommandId" && it.parameterCount == 0
		}
		assertTrue(commandAccessor != null, "E407.Wire.Prerequisite.MissingLocationCommandCorrelation")
		assertTrue(commandAccessor.invoke(event) == wireCommand, "E407.Wire.ExactDecodedCommandIdentity")
	}

	@Test
	fun originalLocationCommandCorrelationIsOptionalAndDoesNotCoerceIdentity() {
		fun decode(commandId: kotlinx.serialization.json.JsonElement? = null): ReaderBridgeEvent.LocationChanged? =
			decodeReaderBridgeEvent(buildJsonObject {
				put("type", "locationChanged")
				put("foliateSessionId", "synthetic-wire-session")
				if (commandId != null) put("commandId", commandId)
			}.toString()) as? ReaderBridgeEvent.LocationChanged
		assertTrue(decode()?.commandId == null, "E407.Wire.LegacyLocationCorrelationOptional")
		listOf(JsonPrimitive(17), JsonPrimitive(""), JsonPrimitive(" padded ")).forEach { invalid ->
			assertTrue(decode(invalid)?.commandId == null, "E407.Wire.InvalidCommandCannotAcquireCorrelation")
		}
		assertTrue(decode(JsonPrimitive("synthetic-wire-command"))?.commandId == "synthetic-wire-command",
			"E407.Wire.CorrelationWithoutSettlementIsOnlyMetadata")
		assertTrue(decode(JsonPrimitive("synthetic-wire-command"))?.pageTurnSettleToken == null,
			"E407.Wire.CommandIdentityCannotMintSettlement")
	}

	/** Missing-feature RED group. The method prerequisite runs before any Root construction.
	 * Later assertions exercise the ORIGINAL Engine delegate/ledger with controlled public
	 * allocation-request inputs, not a genuine installed-turn settlement or production Root
	 * activation. No semantic slot, receipt, allocation, frame, deck or input ACK is invented. */
	@Test
	fun allocationLayoutQueryQueuesOnOriginalLedgerAndRequiresActualDispatchedHead() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			bench.submit(ReaderBridgeCommand.PreviousPage)
			val head = bench.expectDispatch("previousPage")
			val allocation = bench.controlledAllocationCommand()
			val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }, outcomes::add),
				"E407.AllocationQuery.OriginalSubmissionAccepted")
			val pending = bench.pending().pendingCommands.single { it.currentDestinationContinuation === retirement }
			val query = EngineObservedDispatch(pending.dispatch.id, bench.openDispatch.session)
			assertTrue(pending.dispatch.command is ReaderBridgeCommand.ResolveCurrentDestination &&
				pending.lastDispatchedGeneration == null && !pending.requiresDispatchedOpening,
				"E407.AllocationQuery.ReadOnlySuccessorNotInitialBootstrap")
			bench.submit(ReaderBridgeCommand.NextPage)
			val script = bench.lastScript()
			listOf(bench.envelope("ready"), bench.envelope("publicationReady"),
				bench.envelope("commandAck", query.id), bench.allocationResult(query, allocation)).forEach {
				bench.post(it)
				assertTrue(outcomes.isEmpty() && bench.lastScript() == script &&
					bench.pending().pendingCommands.first().dispatch.id == head.id &&
					bench.pending().pendingCommands.any { row -> row.currentDestinationContinuation === retirement },
					"E407.AllocationQuery.ReadyAckAndUndispatchedResultRetainOriginalCustody")
			}
			bench.post(bench.envelope("commandAck", head.id))
			val dispatched = bench.expectDispatch("resolveCurrentDestination")
			assertTrue(dispatched.id == query.id && dispatched.session == allocation.binding.foliateSessionId &&
				bench.pending().pendingCommands.first().lastDispatchedGeneration == bench.pending().runtimeGeneration,
				"E407.AllocationQuery.ExactOriginalDispatchedHead")
			bench.post(bench.envelope("commandAck", query.id))
			assertTrue(outcomes.isEmpty() && bench.pending().pendingCommands.first().currentDestinationContinuation === retirement,
				"E407.AllocationQuery.TransportAckCannotResolveLayout")
			bench.post(bench.allocationResult(query, allocation))
			assertTrue(outcomes.singleOrNull() is ReaderCurrentDestinationQueryOutcome.Resolved &&
				bench.pending().pendingCommands.none { it.currentDestinationContinuation === retirement },
				"E407.AllocationQuery.MatchingResultConsumesExactOriginalCustody")
			bench.expectDispatch("nextPage")
		}
	}

	@Test
	fun allocationLayoutQueryCorrelatesBindingLayoutAndOriginalSourceCurrencyWithoutSemanticRebinding() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val allocation = bench.controlledAllocationCommand()
			val originalId = allocation.transitionId
			val originalBinding = allocation.binding
			val originalDispatchState = bench.pending()
			val source = bench.webView() as ReaderEngineCurrentDestinationQuerySource
			val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			var consumedBeforeCallback = false
			var currency: (() -> Boolean)? = null
			val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }) { outcome ->
				outcomes += outcome
				consumedBeforeCallback = bench.pending().pendingCommands.none { it.currentDestinationContinuation === (outcome as? ReaderCurrentDestinationQueryOutcome.Resolved)?.carrier }
				currency = source.captureResolvedLayoutCurrency()
			})
			assertTrue(retirement is ReaderEngineCurrentDestinationContinuation &&
				retirement.expectedGeneration == originalDispatchState.runtimeGeneration &&
				retirement.expectedPublicationKey == originalDispatchState.publicationKey &&
				retirement.expectedFoliateSessionId == originalDispatchState.foliateSessionId,
				"E407.AllocationQuery.OriginalRuntimePublicationAndSessionVersions")
			val query = bench.expectDispatch("resolveCurrentDestination")
			bench.post(bench.allocationResult(query, allocation))
			val resolved = outcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Resolved
			assertTrue(resolved != null && resolved.carrier === retirement && consumedBeforeCallback,
				"E407.AllocationQuery.OriginalContinuationConsumedBeforeDelivery")
			assertTrue(resolved.event.commandId == query.id &&
				resolved.event.foliateSessionId == originalBinding.foliateSessionId &&
				resolved.event.destinationCommitIdentity == originalBinding.destinationCommitIdentity &&
				resolved.event.resolvedLayout == allocationLayout && allocation.transitionId === originalId &&
				allocation.binding === originalBinding && originalId.readerSessionGeneration == 3L &&
				originalBinding.publicationGeneration == 2L && originalBinding.viewportGeneration == 3L &&
				originalBinding.profileGeneration == 5L,
				"E407.AllocationQuery.ExactControlledCommandBindingAndNumericLayout")
			assertTrue(currency?.invoke() == true && source.captureResolvedLayoutCurrency() == null,
				"E407.AllocationQuery.OriginalDeliveryCurrencySurvivesReadOnlyConsumptionOnly")
			bench.post(bench.allocationResult(query, allocation))
			assertTrue(outcomes.size == 1 && currency?.invoke() == true,
				"E407.AllocationQuery.DuplicateCannotDeliverOrInvalidateCurrentLayout")
			bench.post(buildJsonObject {
				put("type", "locationChanged")
				put("foliateSessionId", originalBinding.foliateSessionId)
				put("destinationFoliateSessionId", originalBinding.foliateSessionId)
				put("destinationCommitSequence", checkNotNull(originalBinding.destinationCommitIdentity).commitSequence + 1L)
				put("pageIndex", 5)
			})
			assertTrue(currency?.invoke() == false,
				"E407.AllocationQuery.ActualDestinationChangeRetiresOriginalLayoutCurrency")
			assertTrue(bench.events.none { it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.CurrentDestinationResolved },
				"E407.AllocationQuery.ReadOnlyResultCannotReplaySemanticIngress")
		}
	}

	@Test
	fun allocationLayoutQueryRejectsForeignSessionAndWrongDestinationBinding() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val allocation = bench.controlledAllocationCommand()
			val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val foreignBinding = allocation.binding.copy(foliateSessionId = "synthetic-foreign-allocation-session",
				destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic-foreign-allocation-session", 2L))
			val foreign = allocation.copy(binding = foreignBinding)
			val script = bench.lastScript()
			assertTrue(bench.queryAllocationLayout(capability, allocation, { false }, outcomes::add) == null &&
				outcomes.isEmpty() && bench.pending().pendingCommands.isEmpty() && bench.lastScript() == script,
				"E407.AllocationQuery.StaleAdmissionCannotAcquireOriginalCustody")
			assertTrue(bench.queryAllocationLayout(capability, foreign, { true }, outcomes::add) == null &&
				outcomes.isEmpty() && bench.pending().pendingCommands.isEmpty() && bench.lastScript() == script,
				"E407.AllocationQuery.ForeignSessionCannotAcquireOriginalCustody")
			val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }, outcomes::add))
			val query = bench.expectDispatch("resolveCurrentDestination")
			listOf(bench.allocationResult(query, allocation, session = foreignBinding.foliateSessionId),
				bench.allocationResult(query, allocation, id = bench.openDispatch.id)).forEach {
				bench.post(it)
				assertTrue(outcomes.isEmpty() && bench.pending().pendingCommands.first().currentDestinationContinuation === retirement,
					"E407.AllocationQuery.ForeignWireSessionOrCommandCannotConsumeOriginalHead")
			}
			bench.post(bench.allocationResult(query, allocation,
				destinationSequence = checkNotNull(allocation.binding.destinationCommitIdentity).commitSequence + 1L))
			val failure = outcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Failed
			assertTrue(failure?.reason == ReaderCurrentDestinationQueryFailure.SubmissionRejected &&
				bench.pending().pendingCommands.none { it.currentDestinationContinuation === retirement },
				"E407.AllocationQuery.WrongDestinationCannotBecomeSuccessfulAllocationLayout")
			bench.post(bench.allocationResult(query, allocation))
			assertTrue(outcomes.size == 1, "E407.AllocationQuery.LateMatchingResultCannotReviveRejectedBinding")
		}
	}

	@Test
	fun allocationLayoutQueryAdmissionSupersessionRetiresOnlyExactOriginalContinuation() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val first = bench.controlledAllocationCommand()
			val second = bench.controlledAllocationCommand(first)
			var firstCurrent = true
			val firstOutcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val secondOutcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val firstRetirement = assertNotNull(bench.queryAllocationLayout(capability, first, { firstCurrent }, firstOutcomes::add))
			val firstQuery = bench.expectDispatch("resolveCurrentDestination")
			val secondRetirement = assertNotNull(bench.queryAllocationLayout(capability, second, { true }, secondOutcomes::add))
			assertTrue(firstRetirement !== secondRetirement && second.transitionId.sequence == first.transitionId.sequence + 1L &&
				second.transitionId.parent == first.transitionId.parentIdentity(),
				"E407.AllocationQuery.DistinctControlledCommandAndExactRetirementIdentity")
			firstCurrent = false
			bench.post(bench.allocationResult(firstQuery, first))
			assertTrue(firstOutcomes.singleOrNull() is ReaderCurrentDestinationQueryOutcome.Failed &&
				secondOutcomes.isEmpty(), "E407.AllocationQuery.SupersededAdmissionCannotDeliverLayout")
			val secondQuery = bench.expectDispatch("resolveCurrentDestination")
			firstRetirement.retire(); firstRetirement.retire(); bench.drain()
			bench.post(bench.allocationResult(firstQuery, first))
			assertTrue(bench.pending().pendingCommands.first().currentDestinationContinuation === secondRetirement &&
				secondOutcomes.isEmpty() && firstOutcomes.size == 1,
				"E407.AllocationQuery.OldRetirementAndLateResultCannotRetireSuccessor")
			bench.post(bench.allocationResult(secondQuery, second))
			assertTrue(secondOutcomes.singleOrNull() is ReaderCurrentDestinationQueryOutcome.Resolved &&
				bench.pending().pendingCommands.isEmpty(), "E407.AllocationQuery.CurrentSuccessorUsesSameOriginalLedger")
		}
	}

	@Test
	fun allocationLayoutQueryExplicitRetirementDropsLateResultWithoutCancelingOrdinarySuccessor() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val allocation = bench.controlledAllocationCommand()
			val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }, outcomes::add))
			val query = bench.expectDispatch("resolveCurrentDestination")
			bench.submit(ReaderBridgeCommand.NextPage)
			retirement.retire(); retirement.retire(); bench.drain()
			val next = bench.expectDispatch("nextPage")
			assertTrue(bench.pending().pendingCommands.none { it.currentDestinationContinuation === retirement } && outcomes.isEmpty(),
				"E407.AllocationQuery.ExplicitExactRetirementClearsCallbackAndQueryCustody")
			bench.post(bench.allocationResult(query, allocation))
			assertTrue(outcomes.isEmpty() && bench.pending().pendingCommands.first().dispatch.id == next.id,
				"E407.AllocationQuery.CanceledLateResponseCannotConsumeOrdinarySuccessor")
		}
	}

	@Test
	fun allocationLayoutQueryRetiredResultCannotConsumeSameCommandResubmission() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val allocation = bench.controlledAllocationCommand()
			val source = bench.webView() as ReaderEngineCurrentDestinationQuerySource
			// Controlled leaf admission remains current for this same allocation request.
			// Retiring its transport token is not a revocation of the allocation's authority.
			val admission = { source.admitsCurrentDestinationSession(allocation.binding.foliateSessionId) }
			val firstOutcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val secondOutcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			val firstRetirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, admission, firstOutcomes::add),
				"E407.AllocationQuery.SameCommandFirstSubmissionAccepted")
			val firstQuery = bench.expectDispatch("resolveCurrentDestination")
			val lateFirstResult = bench.allocationResult(firstQuery, allocation)
			firstRetirement.retire(); bench.drain()
			assertTrue(firstOutcomes.isEmpty() && bench.pending().pendingCommands.isEmpty() && admission(),
				"E407.AllocationQuery.TransportRetirementLeavesControlledAllocationAdmissionCurrent")
			val secondRetirement = bench.queryAllocationLayout(capability, allocation, admission, secondOutcomes::add)
			// The nullable original API may reject reuse instead of accepting a fresh wire identity.
			// Neither policy may deliver the retired result or acquire stale callback authority.
			if (secondRetirement == null) {
				assertTrue(secondOutcomes.isEmpty() && bench.pending().pendingCommands.isEmpty(),
					"E407.AllocationQuery.RejectedSameCommandResubmissionOwnsNoCustody")
				bench.post(lateFirstResult)
				assertTrue(firstOutcomes.isEmpty() && secondOutcomes.isEmpty() && bench.pending().pendingCommands.isEmpty(),
					"E407.AllocationQuery.RetiredResultCannotReviveRejectedSameCommandResubmission")
			} else {
				val secondQuery = bench.expectDispatch("resolveCurrentDestination")
				assertTrue(secondRetirement !== firstRetirement &&
					bench.pending().pendingCommands.single().currentDestinationContinuation === secondRetirement &&
					bench.pending().pendingCommands.single().lastDispatchedGeneration == bench.pending().runtimeGeneration,
					"E407.AllocationQuery.SameCommandResubmissionOwnsNewExactDispatchedContinuation")
				firstRetirement.retire(); bench.drain()
				val script = bench.lastScript()
				bench.post(lateFirstResult)
				assertTrue(firstOutcomes.isEmpty() && secondOutcomes.isEmpty() && bench.lastScript() == script &&
					bench.pending().pendingCommands.singleOrNull()?.currentDestinationContinuation === secondRetirement,
					"E407.AllocationQuery.RetiredResultCannotConsumeResubmittedExactHeadOrDeliverLayout")
				val freshLayout = allocationLayout.copy(layoutGeneration = allocationLayout.layoutGeneration + 1L)
				bench.post(bench.allocationResult(secondQuery, allocation, layout = freshLayout))
				val resolved = secondOutcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Resolved
				assertTrue(firstOutcomes.isEmpty() && resolved != null && resolved.carrier === secondRetirement &&
					resolved.event.resolvedLayout == freshLayout && bench.pending().pendingCommands.isEmpty(),
					"E407.AllocationQuery.SameCommandResubmissionDeliversOnlyFreshExactLayout")
				bench.post(lateFirstResult)
				assertTrue(firstOutcomes.isEmpty() && secondOutcomes.size == 1 && bench.pending().pendingCommands.isEmpty(),
					"E407.AllocationQuery.RetiredResultCannotRedeliverAfterFreshSameCommandCompletion")
			}
			assertTrue(bench.events.none { it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.CurrentDestinationResolved },
				"E407.AllocationQuery.SameCommandRetryCannotReplaySemanticIngress")
		}
	}

	@Test
	fun allocationLayoutQueryOriginalCloseReplacementAndRendererRetirementDropLateResponses() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		for (terminal in listOf(ReaderCurrentDestinationQueryFailure.Closed,
			ReaderCurrentDestinationQueryFailure.PublicationReplaced, ReaderCurrentDestinationQueryFailure.RuntimeRetired)) {
			withCurrentDestinationEngine { bench ->
				val allocation = bench.controlledAllocationCommand()
				val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
				val originalEngine = bench.webView()
				val originalBridge = bench.bridge()
				val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }, outcomes::add))
				val query = bench.expectDispatch("resolveCurrentDestination")
				when (terminal) {
					ReaderCurrentDestinationQueryFailure.Closed -> bench.closeRoot()
					ReaderCurrentDestinationQueryFailure.PublicationReplaced -> bench.replacePublication()
					ReaderCurrentDestinationQueryFailure.RuntimeRetired -> bench.retireRenderer()
					else -> error("Unsupported fixed terminal control")
				}
				assertTrue((outcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Failed)?.reason == terminal &&
					bench.pending(originalEngine).pendingCommands.none { it.currentDestinationContinuation === retirement },
					"E407.AllocationQuery.OriginalTerminalRetiresExactQueryOnce")
				retirement.retire(); retirement.retire()
				originalBridge.postMessage(bench.allocationResult(query, allocation).toString())
				bench.drain()
				assertTrue(outcomes.size == 1 &&
					bench.pending(originalEngine).pendingCommands.none { it.currentDestinationContinuation === retirement },
					"E407.AllocationQuery.TerminalLateResponseCannotReviveOriginalCustody")
			}
		}
	}

	@Test
	fun allocationLayoutQueryRejectsMissingLayoutAfterConsumingOnlyItsOriginalHead() = runTest {
		assertAllocationQueryRejectsUnvalidatedLayout(invalidNumericLayout = false)
	}

	@Test
	fun allocationLayoutQueryRejectsInvalidNumericLayoutWithoutSuccessfulSourceCurrency() = runTest {
		assertAllocationQueryRejectsUnvalidatedLayout(invalidNumericLayout = true)
	}

	@Test
	fun allocationLayoutQueryRejectsMissingLayoutWithoutGeometrySuccessorOrSuccessfulCurrency() = runTest {
		assertAllocationQueryRejectsUnvalidatedLayout(invalidNumericLayout = false, withGeometrySuccessor = false)
	}

	@Test
	fun allocationLayoutQueryRejectsInvalidLayoutWithoutGeometrySuccessorOrSuccessfulCurrency() = runTest {
		assertAllocationQueryRejectsUnvalidatedLayout(invalidNumericLayout = true, withGeometrySuccessor = false)
	}

	@Test
	fun allocationLayoutQuerySuccessfulDeliveryCapturesCurrencyWithoutGeometrySuccessor() = runTest {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val allocation = bench.controlledAllocationCommand()
			val source = bench.webView() as ReaderEngineCurrentDestinationQuerySource
			val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			var consumedBeforeCallback = false
			var currency: (() -> Boolean)? = null
			assertTrue(source.captureResolvedLayoutCurrency() == null,
				"E407.AllocationQuery.NoSuccessCurrencyBeforeDelivery")
			val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }) { outcome ->
				outcomes += outcome
				consumedBeforeCallback = bench.pending().pendingCommands.isEmpty()
				currency = source.captureResolvedLayoutCurrency()
			}, "E407.AllocationQuery.NoSuccessorPositiveSubmissionAccepted")
			val query = bench.expectDispatch("resolveCurrentDestination")
			assertTrue(bench.pending().pendingCommands.single().currentDestinationContinuation === retirement,
				"E407.AllocationQuery.NoSuccessorPositiveOwnsOnlyExactHead")
			bench.post(bench.allocationResult(query, allocation))
			val resolved = outcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Resolved
			assertTrue(resolved != null && resolved.carrier === retirement && resolved.event.resolvedLayout == allocationLayout &&
				consumedBeforeCallback && bench.pending().pendingCommands.isEmpty() && currency?.invoke() == true &&
				source.captureResolvedLayoutCurrency() == null,
				"E407.AllocationQuery.NoSuccessorValidatedDeliveryCapturesLiveCurrencyOnlyInCallback")
		}
	}

	private suspend fun assertAllocationQueryRejectsUnvalidatedLayout(
		invalidNumericLayout: Boolean,
		withGeometrySuccessor: Boolean = true
	) {
		val capability = allocationLayoutCapabilityRequired()
		withCurrentDestinationEngine { bench ->
			val allocation = bench.controlledAllocationCommand()
			val source = bench.webView() as ReaderEngineCurrentDestinationQuerySource
			val outcomes = mutableListOf<ReaderCurrentDestinationQueryOutcome>()
			var queryId: String? = null
			var consumedBeforeCallback = false
			var capturedSuccessfulCurrency = false
			var callbackHadNoPendingCommands = false
			val retirement = assertNotNull(bench.queryAllocationLayout(capability, allocation, { true }) { outcome ->
				outcomes += outcome
				consumedBeforeCallback = bench.pending().pendingCommands.none { it.dispatch.id == queryId }
				callbackHadNoPendingCommands = bench.pending().pendingCommands.isEmpty()
				capturedSuccessfulCurrency = source.captureResolvedLayoutCurrency() != null
			})
			val query = bench.expectDispatch("resolveCurrentDestination")
			queryId = query.id
			if (withGeometrySuccessor) bench.submit(ReaderBridgeCommand.NextPage)
			else assertTrue(bench.pending().pendingCommands.single().currentDestinationContinuation === retirement &&
				source.captureResolvedLayoutCurrency() == null,
				"E407.AllocationQuery.UnvalidatedLayoutOwnsOnlyExactHeadWithoutGeometryBarrier")
			val valid = bench.allocationResult(query, allocation)
			val invalid = if (invalidNumericLayout) {
				val layout = valid.getValue("resolvedLayout") as JsonObject
				JsonObject(valid + ("resolvedLayout" to JsonObject(layout + ("pageCount" to JsonPrimitive(0)))))
			} else JsonObject(valid.filterKeys { it != "resolvedLayout" })
			val decoded = decodeReaderBridgeEvent(invalid.toString()) as? ReaderBridgeEvent.CurrentDestinationResolved
			assertTrue(decoded != null && decoded.commandId == query.id &&
				decoded.foliateSessionId == allocation.binding.foliateSessionId &&
				decoded.destinationCommitIdentity == allocation.binding.destinationCommitIdentity && decoded.resolvedLayout == null,
				if (invalidNumericLayout) "E407.AllocationQuery.InvalidNumericLayoutDecodesWithoutAuthority"
				else "E407.AllocationQuery.MissingLayoutDecodesWithoutAuthority")
			bench.post(invalid)
			assertTrue((outcomes.singleOrNull() as? ReaderCurrentDestinationQueryOutcome.Failed)?.reason ==
				ReaderCurrentDestinationQueryFailure.SubmissionRejected && consumedBeforeCallback && !capturedSuccessfulCurrency &&
				bench.pending().pendingCommands.none { it.currentDestinationContinuation === retirement },
				"E407.AllocationQuery.UnvalidatedLayoutRejectsAfterExactConsumptionWithoutCurrency")
			if (withGeometrySuccessor) {
				val next = bench.expectDispatch("nextPage")
				bench.post(valid)
				assertTrue(outcomes.size == 1 && bench.pending().pendingCommands.first().dispatch.id == next.id &&
					bench.events.none { it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.CurrentDestinationResolved },
					"E407.AllocationQuery.LateValidatedLayoutCannotReviveQueryOrReplaySemanticIngress")
			} else {
				assertTrue(callbackHadNoPendingCommands && bench.pending().pendingCommands.isEmpty() &&
					source.captureResolvedLayoutCurrency() == null,
					"E407.AllocationQuery.UnvalidatedLayoutHasNoDeliveryCurrencyWithoutAnyGeometrySuccessor")
				bench.post(valid)
				assertTrue(outcomes.size == 1 && bench.pending().pendingCommands.isEmpty() && !capturedSuccessfulCurrency &&
					source.captureResolvedLayoutCurrency() == null &&
					bench.events.none { it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.CurrentDestinationResolved },
					"E407.AllocationQuery.LateValidatedLayoutCannotReviveFailedNoSuccessorCurrency")
			}
		}
	}

	private fun allocationLayoutCapabilityRequired(): java.lang.reflect.Method {
		val capability = ReaderEngineCurrentDestinationQuerySource::class.java.methods.singleOrNull {
			it.name == "queryAllocationLayout" && it.parameterTypes.contentEquals(arrayOf(
				ReaderTransitionCommand.AllocateMaterialBinding::class.java,
				kotlin.jvm.functions.Function0::class.java, kotlin.jvm.functions.Function1::class.java)) &&
				it.returnType == ReaderSemanticTransportRetirement::class.java
		}
		return assertNotNull(capability, "E407.AllocationQuery.OriginalCapabilityRequired")
	}

	private val allocationLayout = ReaderFoliateResolvedLayout(7L, 11L, 13L, 0, 4, 8, 800.0, 600.0)

	private fun CurrentDestinationEngineBench.queryAllocationLayout(
		capability: java.lang.reflect.Method,
		allocation: ReaderTransitionCommand.AllocateMaterialBinding,
		isAdmissionCurrent: () -> Boolean,
		onOutcome: (ReaderCurrentDestinationQueryOutcome) -> Unit
	): ReaderSemanticTransportRetirement? {
		// Existing Accepted/Rejected enum has no cancellation carrier. Nullable existing
		// retirement is the smallest read-only result: null rejects, non-null owns exact
		// original pending-entry cancellation, without rebinding the consumed semantic slot.
		val result = capability.invoke(webView() as ReaderEngineCurrentDestinationQuerySource,
			allocation, isAdmissionCurrent, onOutcome)
		assertTrue(result == null || result is ReaderEngineCurrentDestinationContinuation,
			"E407.AllocationQuery.ReturnsOriginalCancellableContinuationOnly")
		drain()
		return result as ReaderSemanticTransportRetirement?
	}

	private fun CurrentDestinationEngineBench.controlledAllocationCommand(
		previous: ReaderTransitionCommand.AllocateMaterialBinding? = null
	): ReaderTransitionCommand.AllocateMaterialBinding {
		// This fixture has no authenticated installed-turn semantic receipt/coordinator
		// allocation egress. These are controlled leaf REQUEST inputs only. Session comes
		// from the original actual open dispatch; all other generations are test values.
		val predecessor = previous?.binding ?: ReaderPresentationBinding(openDispatch.session, 2L, 3L, 5L,
			ReaderDestinationCommitIdentity(openDispatch.session, 1L))
		val sequence = (previous?.transitionId?.sequence ?: 0L) + 1L
		return ReaderTransitionCommand.AllocateMaterialBinding(
			ReaderTransitionId(3L, 5L, sequence, ReaderTransitionOperation.CurlClaimAndSettlement,
				ReaderExpectedPresentationBinding.SemanticSuccessor(predecessor, sequence), previous?.transitionId?.parentIdentity()),
			predecessor.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(openDispatch.session,
				checkNotNull(predecessor.destinationCommitIdentity).commitSequence + 1L)))
	}

	private fun CurrentDestinationEngineBench.allocationResult(
		query: EngineObservedDispatch,
		allocation: ReaderTransitionCommand.AllocateMaterialBinding,
		session: String = query.session,
		id: String = query.id,
		destinationSequence: Long = checkNotNull(allocation.binding.destinationCommitIdentity).commitSequence,
		layout: ReaderFoliateResolvedLayout = allocationLayout
	): JsonObject = buildJsonObject {
		// Controlled bridge input, not Foliate layout/display proof or a semantic receipt.
		put("type", "currentDestinationResolved")
		put("commandId", id)
		put("foliateSessionId", session)
		put("destinationFoliateSessionId", session)
		put("destinationCommitSequence", destinationSequence)
		put("pageIndex", layout.pageIndex)
		put("resolvedLayout", buildJsonObject {
			put("layoutGeneration", layout.layoutGeneration)
			put("viewGeneration", layout.viewGeneration)
			put("navigationGeneration", layout.navigationGeneration)
			put("sectionIndex", layout.sectionIndex)
			put("pageIndex", layout.pageIndex)
			put("pageCount", layout.pageCount)
			put("width", layout.width)
			put("height", layout.height)
		})
	}

	@Test
	fun retiredRuntimeAndClosedRootDropOldInstalledBridgeResults() = runTest {
		withCurrentDestinationEngine { bench ->
			val query = bench.dispatchQueryWithSuccessor()
			val oldEngine = bench.webView()
			val oldBridge = bench.bridge()
			bench.retireRenderer()
			assertTrue(bench.webView() !== oldEngine, "Renderer control must install a different real Engine")
			check(bench.bridge() !== oldBridge) { "Fixture retirement must install a different real bridge" }
			bench.post(bench.envelope("ready"))
			bench.expectDispatch("openPublication")
			val script = bench.lastScript()
			val eventCount = bench.events.size
			oldBridge.postMessage(bench.result(query).toString())
			bench.drain()
			assertTrue(bench.lastScript() == script, "Retired bridge cannot change new runtime dispatch")
			assertEquals(eventCount, bench.events.size, "Retired bridge cannot forward stale result")
			val closingBridge = bench.bridge()
			bench.closeRoot()
			val closedEventCount = bench.events.size
			closingBridge.postMessage(bench.result(query).toString())
			bench.drain()
			assertEquals(closedEventCount, bench.events.size, "Closed root must drop stale bridge results")
		}
	}
}

/** Prospective construction-only labels. This holds finite enums, never a Throwable,
 * object snapshot or reader payload; it grants no source/currentness/readiness authority.
 * Only a scoped ORIGINAL root construction enables inner platform-boundary classification. */
internal object OriginalEngineFixtureConstructionProbe {
	enum class Boundary {
		NativeRootConstructor, PageSurfaceConstructor, MainTerminalRegistration,
		OriginalSurfaceListenerRegistration, OwnershipCapacityRegistration
	}
	private enum class FailureKind {
		InvocationTarget, NullPointer, ClassCast, IllegalArgument, IllegalState,
		Reflection, Linkage, Assertion, VirtualMachine, Other
	}
	private class FailureLabel(val boundary: Boundary, val kind: FailureKind) {
		fun fixedLabel() = "E407.Fixture.Construction.${boundary.name}.${kind.name}"
	}
	private class Scope(var failure: FailureLabel? = null)
	private val current = ThreadLocal<Scope>()

	fun <T> constructOriginalRoot(operation: () -> T): T {
		val previous = current.get()
		current.set(Scope())
		return try { at(Boundary.NativeRootConstructor, operation) }
		finally {
			if (previous == null) current.remove() else current.set(previous)
		}
	}

	fun <T> at(boundary: Boundary, operation: () -> T): T {
		val scope = current.get() ?: return operation()
		return try { operation() } catch (failure: Throwable) {
			// Exact finite type mapping only. Never inspect cause, message, stack or class name.
			val kind = when (failure) {
				is java.lang.reflect.InvocationTargetException -> FailureKind.InvocationTarget
				is NullPointerException -> FailureKind.NullPointer
				is ClassCastException -> FailureKind.ClassCast
				is IllegalArgumentException -> FailureKind.IllegalArgument
				is IllegalStateException -> FailureKind.IllegalState
				is ReflectiveOperationException -> FailureKind.Reflection
				is LinkageError -> FailureKind.Linkage
				is AssertionError -> FailureKind.Assertion
				is VirtualMachineError -> FailureKind.VirtualMachine
				else -> FailureKind.Other
			}
			if (scope.failure == null) scope.failure = FailureLabel(boundary, kind)
			// Keep the first source-static boundary through reflection, WITHOUT reading its cause.
			throw AssertionError(checkNotNull(scope.failure).fixedLabel())
		}
	}
}

/** Prospective ORIGINAL reflective-call labels, separate from the unchanged constructor
 * probe. An InvocationTargetException proves only that this exact invocation threw;
 * neither its cause nor its payload is inspected, retained or rendered. MAIN must qualify
 * this finite vocabulary before exporting it. It supplies no runtime/readiness authority. */
internal enum class OriginalEngineFixtureInvocationBoundary {
	ViewerContentInstallation,
	CurrentLiveWebView,
	RootClose,
	StagedInstalledPublication,
	PhysicalStagedInstalledPublication,
	AppliedInstalledPublication,
	CopiedInstalledPublication,
	RecheckedInstalledPublication
}

internal inline fun <T> invokeOriginalEngineFixtureBoundary(
	boundary: OriginalEngineFixtureInvocationBoundary,
	operation: () -> T
): T = try {
	operation()
} catch (_: java.lang.reflect.InvocationTargetException) {
	throw AssertionError("E407.Fixture.Invocation.${boundary.name}.InvocationTarget")
}

/** In-memory transport identities only; deliberately no payload-rendering data-class toString. */
internal class EngineObservedDispatch(val id: String, val session: String)

internal suspend fun withCurrentDestinationEngine(action: (CurrentDestinationEngineBench) -> Unit) {
	val bench = CurrentDestinationEngineBench()
	withOriginalEngineFixtureCleanup(dispose = { bench.dispose() }) {
		bench.install()
		action(bench)
	}
}

/** Original fixture exception precedence only; preserves the real dispose invocation.
 * A cleanup-only failure is fatal. With a primary failure, retain that exact failure and
 * attach a distinct cleanup failure without reading either Throwable's payload. */
internal suspend fun withOriginalEngineFixtureCleanup(dispose: suspend () -> Unit, action: () -> Unit) {
	var primary: Throwable? = null
	try {
		action()
	} catch (failure: Throwable) {
		primary = failure
		throw failure
	} finally {
		try {
			dispose()
		} catch (cleanup: Throwable) {
			val first = primary
			if (first == null) throw cleanup
			if (cleanup !== first) first.addSuppressed(cleanup)
		}
	}
}

/** Prospective finite vocabulary for the one ORIGINAL installation-call witness below.
 * The last entry/return marker is not construction, currentness or lifecycle authority. */
internal enum class OriginalEngineViewerContentStage { NotEntered, Entered, Returned }

/** Installs the existing Engine composable, not a copied dispatcher or a fabricated Native authority. */
internal class CurrentDestinationEngineBench {
	private val activity = run {
		// Legacy-graphics Robolectric otherwise omits ADD_FLAG_APP_VISIBLE from addToDisplay.
		// Model an ordinary visible application window before the real tree is attached.
		val property = "robolectric.areWindowsMarkedVisible"
		val previous = System.getProperty(property)
		System.setProperty(property, "true")
		try {
			Robolectric.buildActivity(ComponentActivity::class.java).setup()
		} finally {
			if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
		}
	}
	private val rootType = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeFrameRoot")
	private val root = OriginalEngineFixtureConstructionProbe.constructOriginalRoot {
		rootType.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
			.apply { isAccessible = true }.newInstance(activity.get(), ReaderActivatedSessionSnapshotStore()) as View
	}
	private val viewer = rootType.getDeclaredField("viewerContainer").apply { isAccessible = true }.get(root)
	private val publication = mutableStateOf("https://synthetic.invalid/engine-bench.epub")
	private val command = mutableStateOf<ReaderEngineHostCommand?>(null)
	private val commandKey = mutableStateOf(0L)
	private var rootClosed = false
	val events = mutableListOf<ReaderEngineHostEvent>()
	var onObservedEvent: ((ReaderEngineHostEvent) -> Unit)? = null
	lateinit var openDispatch: EngineObservedDispatch
		private set

	fun install() {
		activity.get().setContentView(root)
		// Prospective finite ORIGINAL-call witness; MAIN must qualify these three labels
		// before execution/export. No object, callback payload or Throwable is sampled.
		// Entry/return describe only this composable call, never Engine readiness.
		var contentStage = OriginalEngineViewerContentStage.NotEntered
		val content: @Composable () -> Unit = {
			contentStage = OriginalEngineViewerContentStage.Entered
			ReaderEngineWebViewHost(
				publicationUrl = publication.value, title = "Synthetic transport fixture",
				kind = ReaderPublicationKind.Ebook, mediaOverlayEnabled = false,
				externalShellCover = false, suppressWebShellCover = false, nativeShellCoverTint = null,
				settings = ReaderSettings(), startCfi = null, startHref = null, startProgress = null,
				rawTextProvenanceDescriptors = emptyList(), command = command.value,
				commandKey = commandKey.value, onEvent = { events += it; onObservedEvent?.invoke(it) }, modifier = Modifier.fillMaxSize()
			)
			contentStage = OriginalEngineViewerContentStage.Returned
		}
		try {
			rootType.declaredMethods.single { it.name == "setViewerContent" }.apply { isAccessible = true }
				.invoke(root, ReaderViewerKey(ReaderViewerKind.WebViewPublication, "synthetic-engine-bench"), content)
		} catch (_: java.lang.reflect.InvocationTargetException) {
			val stageLabel = when (contentStage) {
				OriginalEngineViewerContentStage.NotEntered -> "E407.Fixture.ViewerContent.OriginalCallStage.NotEntered"
				OriginalEngineViewerContentStage.Entered -> "E407.Fixture.ViewerContent.OriginalCallStage.Entered"
				OriginalEngineViewerContentStage.Returned -> "E407.Fixture.ViewerContent.OriginalCallStage.Returned"
			}
			// Read only the original Root's typed enum. Never unwrap the invocation Throwable.
			val operation = rootType.getDeclaredMethod("originalViewerContentOperationFailure")
				.apply { isAccessible = true }.invoke(root) as ReaderOriginalViewerContentOperation?
			val operationLabel = when (operation) {
				ReaderOriginalViewerContentOperation.CaptureReplacement -> "E407.Fixture.ViewerContent.OriginalOperation.CaptureReplacement"
				ReaderOriginalViewerContentOperation.BeginReturn -> "E407.Fixture.ViewerContent.OriginalOperation.BeginReturn"
				ReaderOriginalViewerContentOperation.ComposeConstruction -> "E407.Fixture.ViewerContent.OriginalOperation.ComposeConstruction"
				ReaderOriginalViewerContentOperation.CompositionStrategy -> "E407.Fixture.ViewerContent.OriginalOperation.CompositionStrategy"
				ReaderOriginalViewerContentOperation.SetContent -> "E407.Fixture.ViewerContent.OriginalOperation.SetContent"
				ReaderOriginalViewerContentOperation.CancelPointer -> "E407.Fixture.ViewerContent.OriginalOperation.CancelPointer"
				ReaderOriginalViewerContentOperation.ReplacementAdmission -> "E407.Fixture.ViewerContent.OriginalOperation.ReplacementAdmission"
				ReaderOriginalViewerContentOperation.ReplacementLifecycle -> "E407.Fixture.ViewerContent.OriginalOperation.ReplacementLifecycle"
				ReaderOriginalViewerContentOperation.CurlInvalidation -> "E407.Fixture.ViewerContent.OriginalOperation.CurlInvalidation"
				ReaderOriginalViewerContentOperation.RasterInvalidation -> "E407.Fixture.ViewerContent.OriginalOperation.RasterInvalidation"
				ReaderOriginalViewerContentOperation.RemovePrewarmListener -> "E407.Fixture.ViewerContent.OriginalOperation.RemovePrewarmListener"
				ReaderOriginalViewerContentOperation.ReportDetached -> "E407.Fixture.ViewerContent.OriginalOperation.ReportDetached"
				ReaderOriginalViewerContentOperation.RetireCaptured -> "E407.Fixture.ViewerContent.OriginalOperation.RetireCaptured"
				ReaderOriginalViewerContentOperation.ReserveInstallation -> "E407.Fixture.ViewerContent.OriginalOperation.ReserveInstallation"
				ReaderOriginalViewerContentOperation.MountInstallation -> "E407.Fixture.ViewerContent.OriginalOperation.MountInstallation"
				ReaderOriginalViewerContentOperation.ReportAttached -> "E407.Fixture.ViewerContent.OriginalOperation.ReportAttached"
				ReaderOriginalViewerContentOperation.RequestPrewarm -> "E407.Fixture.ViewerContent.OriginalOperation.RequestPrewarm"
				ReaderOriginalViewerContentOperation.AcceptReplacement -> "E407.Fixture.ViewerContent.OriginalOperation.AcceptReplacement"
				ReaderOriginalViewerContentOperation.CompleteReplacement -> "E407.Fixture.ViewerContent.OriginalOperation.CompleteReplacement"
				ReaderOriginalViewerContentOperation.ReplacementCleanup -> "E407.Fixture.ViewerContent.OriginalOperation.ReplacementCleanup"
				ReaderOriginalViewerContentOperation.EndReturn -> "E407.Fixture.ViewerContent.OriginalOperation.EndReturn"
				null -> "E407.Fixture.ViewerContent.OriginalOperation.Unobserved"
			}
			throw AssertionError("E407.Fixture.Invocation.ViewerContentInstallation.InvocationTarget $stageLabel $operationLabel")
		}
		drain()
		val wrapper = rootType.getDeclaredField("currentViewerComposeView").apply { isAccessible = true }.get(root) as ComposeView
		check(wrapper.isAttachedToWindow && webView().isShown) { "Fixture must install an attached real Android composition" }
		check(webView().javaClass.simpleName == "ReaderEngineWebView") { "Fixture must install the actual Engine WebView" }
		val engine = webView()
		println("Engine fixture startup scalars: attached=${engine.isAttachedToWindow} shown=${engine.isShown} " +
			"windowVisibility=${engine.windowVisibility} entrypoint=${engine.url == ReaderWebRuntime.entrypointUrl} " +
			"bridge=${shadowOf(engine).getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) is ReaderJavascriptBridge}")
		check(engine.isAttachedToWindow && engine.windowVisibility == View.VISIBLE) {
			"Fixture runtime start requires an attached window-visible Engine"
		}
		bridge() // Installed by the actual Engine runtime start gate.
		check(engine.url == ReaderWebRuntime.entrypointUrl) { "Fixture runtime must load its actual entrypoint" }
		post(envelope("ready"))
		openDispatch = expectDispatch("openPublication")
		check(openDispatch.session.isNotBlank()) { "Fixture must parse the actual dispatched open session" }
		post(envelope("commandAck", openDispatch.id))
		check(pending().pendingCommands.isEmpty()) { "Fixture open ACK must drain the actual open command" }
	}

	fun drain() {
		root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
		root.layout(0, 0, 800, 600)
		shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48))
	}

	fun webView(): WebView = invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.CurrentLiveWebView) {
		viewer.javaClass.getDeclaredMethod("currentLiveWebView")
			.apply { isAccessible = true }.invoke(viewer) as? WebView
			?: error("Fixture has no current real Engine WebView")
	}

	fun nativeBinding(): ReaderPresentationBinding? = viewer.javaClass.getDeclaredMethod("currentPresentationBinding")
		.apply { isAccessible = true }.invoke(viewer) as? ReaderPresentationBinding

	fun nativeRequest(gateway: ReaderTransitionGateway, ingress: ReaderSemanticReceiptIngress): ReaderSemanticExecutableRequest =
		viewer.javaClass.getDeclaredMethod("createCurrentDestinationSemanticRequest", ReaderTransitionGateway::class.java, kotlin.jvm.functions.Function2::class.java)
			.apply { isAccessible = true }.invoke(viewer, gateway, ingress) as ReaderSemanticExecutableRequest

	fun bridge(): ReaderJavascriptBridge = shadowOf(webView())
		.getJavascriptInterface(ReaderWebRuntime.AndroidBridgeName) as? ReaderJavascriptBridge
		?: error("Fixture must have the actual installed Android bridge")

	fun lastScript(): String = shadowOf(webView()).lastEvaluatedJavascript
		?: error("Fixture must observe a real evaluateJavascript dispatch")

	fun expectDispatch(type: String): EngineObservedDispatch {
		val script = lastScript()
		val prefix = "window.NavicReaderBridge.dispatch("
		check(script.startsWith(prefix) && script.endsWith(");")) { "Fixture must observe the actual dispatch script envelope" }
		val payload = runCatching { Json.parseToJsonElement(script.removePrefix(prefix).removeSuffix(");")) }.getOrNull() as? JsonObject
			?: error("Fixture dispatch envelope must parse in memory")
		val observedType = (payload["type"] as? JsonPrimitive)?.content
		val allowedTypes = setOf("openPublication", "resolveCurrentDestination", "previousPage", "nextPage", "applySettings")
		val ledger = pending()
		println("Engine fixture dispatch scalars: expected=${type.takeIf(allowedTypes::contains) ?: "OTHER"} " +
			"actual=${observedType?.takeIf(allowedTypes::contains) ?: "OTHER"} " +
			"pending=${ledger.pendingCommands.size} " +
			"head=${ledger.pendingCommands.firstOrNull()?.dispatch?.command?.type?.takeIf(allowedTypes::contains) ?: "NONE_OR_OTHER"} " +
			"headDispatched=${ledger.pendingCommands.firstOrNull()?.lastDispatchedGeneration == ledger.runtimeGeneration} " +
			"requestedCommands=${command.value != null} commandKey=${commandKey.value} " +
			"attached=${webView().isAttachedToWindow} visible=${webView().windowVisibility == View.VISIBLE}")
		assertTrue(observedType == type, "Expected fixed Engine dispatch kind")
		val id = (payload["commandId"] as? JsonPrimitive)?.content ?: error("Fixture dispatch must contain its actual ID")
		val session = (payload["foliateSessionId"] as? JsonPrimitive)?.content.orEmpty()
		return EngineObservedDispatch(id, session)
	}

	fun pending(engine: WebView = webView()): ReaderWebCommandDispatchState {
		// The actual WebViewClient captures the existing composable dispatch-state delegate.
		// This reads its owning ledger; it does not maintain a second command registry.
		val client = engine.webViewClient
		return client.javaClass.declaredFields.firstNotNullOfOrNull { field ->
			val value = field.apply { isAccessible = true }.get(client)
			(value as? State<*>)?.value as? ReaderWebCommandDispatchState
		} ?: error("Fixture cannot inspect the existing Engine dispatch-state delegate")
	}

	fun submit(vararg commands: ReaderBridgeCommand) {
		command.value = if (commands.size == 1) ReaderEngineHostCommand.FoliateBridge(commands.single())
			else ReaderEngineHostCommand.FoliateBridgeSequence(commands.toList())
		commandKey.value += commands.size.toLong()
		drain()
	}

	fun dispatchQueryWithSuccessor(): EngineObservedDispatch {
		submit(ReaderBridgeCommand.ResolveCurrentDestination(openDispatch.session), ReaderBridgeCommand.NextPage)
		val query = expectDispatch("resolveCurrentDestination")
		check(query.session == openDispatch.session && pending().pendingCommands.size == 2) {
			"Fixture must dispatch a session-bound query and retain its queued successor"
		}
		check(pending().pendingCommands.first().lastDispatchedGeneration == pending().runtimeGeneration) {
			"Fixture query head must be marked actually dispatched"
		}
		return query
	}

	fun envelope(type: String, id: String? = null): JsonObject = buildJsonObject {
		put("type", type)
		id?.let { put("commandId", it) }
	}

	fun result(query: EngineObservedDispatch, session: String = query.session, id: String = query.id): JsonObject = buildJsonObject {
		put("type", "currentDestinationResolved")
		put("commandId", id)
		put("foliateSessionId", session)
		put("destinationFoliateSessionId", session)
		put("destinationCommitSequence", 1L)
		put("href", "synthetic-chapter.xhtml")
		put("cfi", "epubcfi(/6/2!/4/2:0)")
	}

	fun post(message: JsonObject) {
		bridge().postMessage(message.toString())
		drain()
	}

	fun replacePublication() {
		command.value = null
		publication.value = "https://synthetic.invalid/replacement-engine-bench.epub"
		drain()
	}

	fun retireRenderer() {
		val view = webView()
		view.webViewClient.onRenderProcessGone(view, object : RenderProcessGoneDetail() {
			override fun didCrash() = false
			override fun rendererPriorityAtExit() = 0
		})
		drain()
	}

	fun closeRoot() {
		if (rootClosed) return
		rootClosed = true
		invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.RootClose) {
			rootType.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(root)
		}
		shadowOf(Looper.getMainLooper()).idle()
	}

	suspend fun dispose() {
		val raster = viewer.javaClass.getDeclaredField("pageRasterPreparationController").apply { isAccessible = true }
			.get(viewer) as ReaderPageRasterPreparationController
		val closed = raster.destroy()
		try {
			closeRoot()
			shadowOf(Looper.getMainLooper()).idle()
			val disposal = runCatching {
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
			}.exceptionOrNull()
			if (disposal != null) {
				check(disposal is ReaderPageTeardownException && generateSequence(disposal as Throwable?) { it.cause }.any {
					it.message == "GL surface is unavailable for terminal disposal"
				}) { "Unexpected Engine fixture terminal disposal failure" }
			}
			check(closed.isCompleted) { "Engine fixture raster teardown must reach a terminal disposition" }
		} finally {
			activity.pause().stop().destroy()
			// Lifecycle destruction cancels the owning window recomposer asynchronously.
			// Finish those queued returns before Robolectric resets this test's looper.
			shadowOf(Looper.getMainLooper()).idle()
		}
	}
}
