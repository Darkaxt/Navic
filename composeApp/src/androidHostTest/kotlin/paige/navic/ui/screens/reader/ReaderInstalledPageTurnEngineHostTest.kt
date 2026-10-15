package paige.navic.ui.screens.reader

import android.os.Build
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertSame
import java.lang.reflect.InvocationTargetException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import karacken.curl.PageChange
import karacken.curl.PageSurfaceListener
import karacken.curl.PageSurfaceView
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadow.api.Shadow
import paige.navic.reader.*
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Capability prerequisite plus prospective original-source Engine transport regressions.
 * The capability test remains separate. Behavioral tests reuse command-created B material,
 * original installedGesture custody, original executor and independent atomic display proof.
 * Missing API is a hard prerequisite failure; no later-stage execution is claimed on that run.
 * Controlled renderer/bridge callbacks are not real Foliate, GL or owning Screen-model proof.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
    shadows = [Task8ImmediateGlSurfaceViewShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
class ReaderInstalledPageTurnEngineHostTest {
    /** Diagnostic infrastructure only: never unwrap the reflective target or export payload.
     * These controls do not qualify any original Engine/material/transport behavior. */
    @Test
    fun originalInvocationWitnessForwardsItsOperationAndOnlyClassifiesTheOuterWrapper() {
        val sentinel = Any()
        var calls = 0
        assertSame(sentinel, invokeOriginalEngineFixtureBoundary(
            OriginalEngineFixtureInvocationBoundary.ViewerContentInstallation) { calls++; sentinel })
        assertEquals(1, calls)
        kotlin.test.assertFailsWith<AssertionError> {
            invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.ViewerContentInstallation) {
                throw InvocationTargetException(null)
            }
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            invokeOriginalEngineFixtureBoundary(OriginalEngineFixtureInvocationBoundary.ViewerContentInstallation) {
                throw IllegalStateException()
            }
        }
    }

    /** Exception-precedence controls for the same cleanup helper used by the ORIGINAL
     * Engine lifecycle. Throwable identities below are locally authored sentinels only;
     * no original runtime cause, message, stack or reflective target is inspected. */
    @Test
    fun originalFixtureCleanupRetainsPrimaryAndSeparatelyAttachesCleanupFailure() = runTest {
        val primary = AssertionError()
        val cleanup = IllegalStateException()
        var cleanupCalls = 0
        val caught = kotlin.test.assertFailsWith<AssertionError> {
            withOriginalEngineFixtureCleanup(dispose = { cleanupCalls++; throw cleanup }) {
                throw primary
            }
        }
        assertSame(primary, caught, "E407.Fixture.Cleanup.PrimaryRetained")
        assertEquals(1, cleanupCalls, "E407.Fixture.Cleanup.AttemptedExactlyOnce")
        assertSame(cleanup, primary.suppressed.single(), "E407.Fixture.Cleanup.SeparateCleanupRetained")
    }

    @Test
    fun originalFixtureCleanupOnlyFailureRemainsFatal() = runTest {
        val cleanup = IllegalStateException()
        var actionCalls = 0
        var cleanupCalls = 0
        val caught = kotlin.test.assertFailsWith<IllegalStateException> {
            withOriginalEngineFixtureCleanup(dispose = { cleanupCalls++; throw cleanup }) {
                actionCalls++
            }
        }
        assertSame(cleanup, caught, "E407.Fixture.Cleanup.OnlyFailureFatal")
        assertEquals(1, actionCalls)
        assertEquals(1, cleanupCalls, "E407.Fixture.Cleanup.AttemptedExactlyOnce")
    }

    @Test
    fun originalFixtureCleanupStillRunsOnceAfterPrimaryWithSuccessfulCleanup() = runTest {
        val primary = AssertionError()
        var cleanupCalls = 0
        val caught = kotlin.test.assertFailsWith<AssertionError> {
            withOriginalEngineFixtureCleanup(dispose = { cleanupCalls++ }) { throw primary }
        }
        assertSame(primary, caught, "E407.Fixture.Cleanup.PrimaryRetained")
        assertEquals(1, cleanupCalls, "E407.Fixture.Cleanup.AttemptedExactlyOnce")
        assertTrue(primary.suppressed.isEmpty())
    }

    @Test
    fun originalFixtureCleanupDoesNotReplacePrimaryOnIdenticalRethrow() = runTest {
        val primary = AssertionError()
        var cleanupCalls = 0
        val caught = kotlin.test.assertFailsWith<AssertionError> {
            withOriginalEngineFixtureCleanup(dispose = { cleanupCalls++; throw primary }) { throw primary }
        }
        assertSame(primary, caught, "E407.Fixture.Cleanup.PrimaryRetained")
        assertEquals(1, cleanupCalls, "E407.Fixture.Cleanup.AttemptedExactlyOnce")
        assertTrue(primary.suppressed.isEmpty())
    }

    @Test
    fun originalFixtureCleanupSuccessfulBodyAndCleanupReturnNormally() = runTest {
        val order = mutableListOf<String>()
        withOriginalEngineFixtureCleanup(dispose = { order += "cleanup" }) { order += "body" }
        assertEquals(listOf("body", "cleanup"), order)
    }

    /** Source-static fixture guard, not behavioral RED or renderer/runtime acceptance.
     * Robolectric binds GLSurfaceView implementations selected by their defining class
     * to the ONE shadow created for the actual PageSurfaceView. Both configured layers
     * therefore need a compatible receiver, including queueEvent/requestRender. */
    @Test
    fun originalPageSurfaceShadowAcceptsItsConfiguredGlSuperclassReceiver() {
        assertTrue(Task8ImmediateGlSurfaceViewShadow::class.java
            .isAssignableFrom(ReaderInstalledUnitPageSurfaceShadow::class.java),
            "E407.Fixture.Prerequisite.CompatibleConfiguredGlReceiver")
    }

    /** Calls the ORIGINAL no-argument lifecycle entry on a fresh original receiver. Its
     * sentinel must reach original idle cleanup, not become a controlled gesture admission.
     * This control is not renderer readiness, GL presentation or semantic acceptance. */
    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalFreshSurfaceNoArgumentCancellationReturnsWithoutInventingGestureOwnership() {
        val surface = PageSurfaceView(RuntimeEnvironment.getApplication())
        val unit: ReaderInstalledUnitPageSurfaceShadow = Shadow.extract(surface)
        var cancellationCallbacks = 0
        surface.pageSurfaceListener = object : PageSurfaceListener {
            override fun onGestureCancelled(gestureId: Long, generationId: Long) { cancellationCallbacks++ }
            override fun onSettlementCancelled(gestureId: Long, generationId: Long, currentLogicalPageId: String) {
                cancellationCallbacks++
            }
        }
        assertEquals(-1L, PageSurfaceView.NO_GESTURE_ID, "E407.IdleCancel.OriginalSentinel")
        assertTrue(unit.cancellations.isEmpty(), "E407.IdleCancel.NoControlledGestureBeforeCall")
        assertTrue(unit.submitted.isEmpty(), "E407.IdleCancel.NoDeckBeforeCall")
        assertTrue(unit.frames.isEmpty(), "E407.IdleCancel.NoFrameConsumerBeforeCall")
        try {
            surface.cancelGesture()
        } catch (_: IllegalStateException) {
            // Source-static classification only: never inspect an original exception payload.
            throw AssertionError("E407.IdleCancel.OriginalNoArgumentSentinelMustReturn")
        }
        assertTrue(unit.cancellations.isEmpty(), "E407.IdleCancel.NoInventedControlledCancellation")
        assertTrue(unit.submitted.isEmpty(), "E407.IdleCancel.NoInventedDeck")
        assertTrue(unit.frames.isEmpty(), "E407.IdleCancel.NoInventedFrameConsumer")
        assertTrue(unit.overlays.isEmpty(), "E407.IdleCancel.NoInventedDecoration")
        assertEquals(0, cancellationCallbacks, "E407.IdleCancel.NoInventedTerminalCallback")
    }

    /** Admit through the existing original fixture, never by writing the shadow's private
     * gesture field. The idle sentinel is also a wrong ID while that controlled gesture is active. */
    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalAcceptedSurfaceGestureStillRejectsWrongIdAndIdleSentinelCancellation() {
        ReaderInstalledRasterPreparationOwnershipHostTest().withMaterialFixture(legacySelection = false) { f ->
            f.bootstrap()
            val observations = mutableListOf<ReaderInstalledCurlSettlementObservation>()
            val source = f.admitOriginalSource(observations::add)
            f.dispatchOriginalDown(source)
            val gesture = source.intent.gestureId.value
            val frameConsumers = f.surface.frames.size
            for (wrongId in listOf(gesture + 1L, PageSurfaceView.NO_GESTURE_ID)) {
                kotlin.test.assertFailsWith<IllegalStateException>("E407.ActiveCancel.WrongIdRejected") {
                    f.curl.surfaceView.cancelGesture(wrongId)
                }
                assertTrue(f.surface.cancellations.isEmpty(), "E407.ActiveCancel.WrongIdCannotRecordCancellation")
                assertTrue(observations.isEmpty(), "E407.ActiveCancel.WrongIdCannotReturnPhysicalCustody")
                assertEquals(1, f.surface.submitted.size, "E407.ActiveCancel.WrongIdCannotReplaceDeck")
                assertEquals(frameConsumers, f.surface.frames.size, "E407.ActiveCancel.WrongIdCannotInventFrameConsumer")
            }
            f.curl.cancelGesture(gesture)
            assertEquals(listOf(gesture), f.surface.cancellations, "E407.ActiveCancel.ExactIdStillRecorded")
            assertTrue(observations.isEmpty(), "E407.ActiveCancel.RequestIsNotPhysicalReturn")
            f.surface.listener.onGestureCancelled(gesture, source.deck.textureGeneration)
            assertEquals(listOf(ReaderInstalledCurlSettlementStage.Cancelled), observations.map { it.stage },
                "E407.ActiveCancel.OriginalListenerReturnsCustody")
            assertSame(source, observations.single().source, "E407.ActiveCancel.OriginalSourceRetained")
        }
    }

    @Test
    fun originalEngineExposesInstalledTurnRequestWithoutReadOnlyQuerySubstitution() = runTest {
        withCurrentDestinationEngine { bench ->
            val engine = bench.webView()
            assertTrue(engine is ReaderEngineCurrentDestinationQuerySource,
                "E407.Turn.Prerequisite.OriginalEngine")
            // Public JVM API lookup only. Do not search fields, access a private producer,
            // invoke another existing method, synthesize a returned request, or skip on absence.
            val capability = assertNotNull(engine.javaClass.methods.singleOrNull {
                it.name == "createInstalledPageTurnSemanticRequest" &&
                    it.parameterCount == 3 && Modifier.isPublic(it.modifiers)
            }, "E407.Turn.Prerequisite.MissingInstalledSemanticRequest")
            assertTrue(capability.parameterTypes[0] == ReaderInstalledCurlGestureSource::class.java,
                "E407.Turn.Prerequisite.ExactSourceParameter")
            assertEquals("kotlin.jvm.functions.Function0", capability.parameterTypes[1].name,
                "E407.Turn.Prerequisite.AdmissionParameter")
            // The declared ingress type is present. The actual installed Engine producer
            // is still an independent hard prerequisite, not implied by that declaration.
            assertEquals("ReaderBoundPageTurnSemanticIngress", capability.parameterTypes[2].simpleName,
                "E407.Turn.Prerequisite.BoundIngressParameter")
            assertEquals("kotlin.jvm.functions.Function3", capability.returnType.name,
                "E407.Turn.Prerequisite.ExecutableRequestReturn")
            // Passing this signature check is NOT installed-turn execution qualification.
            // No source can honestly be constructed here from the current Engine-only bench.
        }
    }

    /** Readiness witness reuses the exact behavioral fixture, including the hard API guard.
     * Its original bound token is waiting transport custody, not Foliate/GL/display success. */
    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalEngineMaterialAndSourceReachInstalledRequestPrerequisite() = runTest {
        withOriginalTurn { b ->
            assertTrue(b.engine.webView() is ReaderEngineCurrentDestinationQuerySource,
                "E407.Fixture.Readiness.OriginalEngine")
            assertSame(b.material.work, b.source.work, "E407.Fixture.Readiness.OriginalMaterialWork")
            assertEquals(b.originalPublishedOwner, assertNotNull(b.material.barrier.snapshot).owner,
                "E407.Fixture.Readiness.OriginalAtomicOwner")
            assertEquals(1, b.tokens.size, "E407.Fixture.Readiness.OriginalWaitingToken")
            assertEquals(0, b.mutations, "E407.Fixture.Readiness.NoInventedMutation")
            assertEquals(0, b.ingressCalls, "E407.Fixture.Readiness.NoInventedSemanticSuccess")
        }
    }

    /** Original B admission + original Engine composition. The request API is a hard guard:
     * when absent these tests reach only authentic bootstrap/source admission, not their later bodies.
     * UNIT renderer observations below exercise semantic transport, NOT typed Curl draw authority.
     * The bound sink intentionally returns null: it cannot stand in for ReaderScreen's owning
     * data-only model/progress/WordSync ingress or manufacture an Accepted executor receipt. */
    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalInstalledRequestBindsOneExactRetirementWhileWaitingBeforeMutation() = runTest {
        withOriginalTurn { b ->
            assertEquals(1, b.tokens.size, "E407.Waiting.OriginalExecutorBoundOnce")
            assertEquals(0, b.mutations, "E407.Waiting.NoMutationReservationAsStart")
            assertTrue(b.engine.pending().pendingCommands.isEmpty(), "E407.Waiting.NoEarlyLedgerInsertion")
            assertTrue(b.engine.lastScript() == b.before, "E407.Waiting.NoEarlyJavascript")
            assertFalse(b.source.bindOriginalSemanticTransport(ReaderSemanticTransportRetirement {}) {},
                "E407.Waiting.SourceAlreadyBoundBySameRequest")
            assertEquals(ReaderSemanticTransportBindingResult.Rejected,
                b.call.context.bindTransportRetirement(b.call.origin, ReaderSemanticTransportRetirement {}),
                "E407.Waiting.ExecutorAlreadyBoundBySameRequest")
            b.material.dispatchOriginalDown(b.source)
            b.material.surface.listener.onSettlementStarted(b.source.intent.gestureId.value,
                b.source.deck.textureGeneration, "public-source", "public-target", PageChange.NEXT)
            assertEquals(0, b.mutations, "E407.Waiting.StartedIsNotSettled")
            assertTrue(b.engine.lastScript() == b.before, "E407.Waiting.StartedCannotNavigate")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalWaitingRequestExactRetirementSuppressesDelayedSettlementBeforeLedgerInsertion() = runTest {
        withOriginalTurn { b ->
            b.tokens.single().retire()
            b.tokens.single().retire()
            b.settleOriginalObservation()
            assertEquals(0, b.mutations, "E407.Waiting.ExactTokenRetiredBeforeMutation")
            assertEquals(0, b.ingressCalls, "E407.Waiting.NoRetiredIngress")
            assertTrue(b.engine.pending().pendingCommands.isEmpty(), "E407.Waiting.NoRetiredLedgerInsertion")
            assertTrue(b.engine.lastScript() == b.before, "E407.Waiting.NoRetiredJavascript")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalWaitingCancellationAndPhysicalCancelCallbackDoNotNavigate() = runTest {
        withOriginalTurn { b ->
            b.material.dispatchOriginalDown(b.source)
            b.material.curl.cancelGesture(b.source.intent.gestureId.value)
            assertEquals(listOf(b.source.intent.gestureId.value), b.material.surface.cancellations,
                "E407.Cancel.OriginalPhysicalRequest")
            b.material.surface.listener.onGestureCancelled(b.source.intent.gestureId.value, b.source.deck.textureGeneration)
            b.material.surface.listener.onGestureCancelled(b.source.intent.gestureId.value, b.source.deck.textureGeneration)
            assertEquals(0, b.mutations, "E407.Cancel.BeforeActualMutation")
            assertEquals(0, b.ingressCalls, "E407.Cancel.NoSemanticSuccess")
            assertTrue(b.engine.pending().pendingCommands.isEmpty(), "E407.Cancel.NoCommandLeak")
            assertTrue(b.engine.lastScript() == b.before, "E407.Cancel.NoJavascript")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalWaitingPublicationRuntimeAndRootRetirementFenceDelayedSourceCallback() = runTest {
        for (retirement in EngineRetirement.entries) withOriginalTurn { b ->
            when (retirement) {
                EngineRetirement.Publication -> b.engine.replacePublication()
                EngineRetirement.Runtime -> b.engine.retireRenderer()
                EngineRetirement.Root -> b.engine.closeRoot()
            }
            b.settleOriginalObservation()
            assertEquals(0, b.mutations, "E407.Retirement.WaitingNeverMutates")
            assertEquals(0, b.ingressCalls, "E407.Retirement.WaitingNeverIngests")
            if (retirement != EngineRetirement.Root) assertTrue(
                b.engine.pending().pendingCommands.none { it.dispatch.command.type == "goToVisualPage" },
                "E407.Retirement.WaitingCannotEnterReplacementLedger")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalSettledSourceUsesTypedVisualCommandAndAckIsTransportOnlyBeforeAndAfterLocation() = runTest {
        withOriginalTurn { b ->
            b.settleOriginalObservation()
            val command = b.dispatchedVisualCommand()
            val id = b.string(command, "commandId")
            assertEquals(1, b.mutations, "E407.Mutation.ActualJavascriptBoundaryOnce")
            val pending = b.engine.pending().pendingCommands.single()
            assertEquals("GoToVisualPage", pending.dispatch.command.javaClass.simpleName,
                "E407.Mutation.TypedOriginalLedgerCommand")
            assertTrue(pending.dispatch.id == id, "E407.Mutation.ExactDispatchedHead")
            assertTrue(pending.lastDispatchedGeneration == b.engine.pending().runtimeGeneration,
                "E407.Mutation.ActuallyDispatched")
            assertEquals(2, (command["pageIndex"] as? JsonPrimitive)?.intOrNull,
                "E407.Mutation.OriginalObservedOrdinal")
            assertTrue(b.string(command, "settleSessionId") == b.source.deck.binding.foliateSessionId,
                "E407.Mutation.OriginalSourceSession")
            assertEquals(b.source.deck.rasterGeneration, (command["settleRasterGeneration"] as? JsonPrimitive)?.longOrNull,
                "E407.Mutation.OriginalSourceRaster")
            assertEquals(b.source.deck.textureGeneration, (command["settleTextureGeneration"] as? JsonPrimitive)?.longOrNull,
                "E407.Mutation.OriginalSourceTexture")
            val generation = assertNotNull((command["settleForegroundMutationGeneration"] as? JsonPrimitive)?.longOrNull,
                "E407.Mutation.ProducerGenerationPresentNotConstructed")
            assertTrue(generation > 0L && b.material.foreground.isMutationGenerationCurrent(generation),
                "E407.Mutation.OriginalForegroundCounterNotConstantOrFactory")
            assertTrue(b.material.foreground.isFrozenForTransitionActivation() &&
                !b.material.foreground.originalLegacyAdmissionRestored && !b.material.foreground.canAcquirePassive(),
                "E407.Mutation.InstalledRouteCannotThawOriginalLegacyOwner")
            b.engine.post(b.engine.envelope("commandAck", id))
            assertEquals(0, b.ingressCalls, "E407.Ack.BeforeLocationIsTransportOnly")
            assertTrue(b.engine.pending().pendingCommands.firstOrNull()?.dispatch?.id == id,
                "E407.Ack.RetainsOriginalHead")
            val location = b.location(command)
            b.engine.post(location)
            b.engine.post(location)
            b.engine.post(b.engine.envelope("commandAck", id))
            assertEquals(1, b.ingressCalls, "E407.Ingress.DuplicateAndAckCannotReplay")
            assertTrue(b.consumedBeforeIngress, "E407.Ingress.HeadConsumedBeforeBoundCallback")
            assertTrue(b.engine.pending().pendingCommands.none { it.dispatch.id == id },
                "E407.Ingress.OriginalHeadTerminal")
            assertEquals(0, b.engine.events.count {
                it is ReaderEngineHostEvent.FoliateBridge && it.event is ReaderBridgeEvent.LocationChanged && it.event.commandId == id
            }, "E407.Ingress.NoOrdinaryLocationReplay")
            assertEquals(b.originalPublishedOwner, assertNotNull(b.material.barrier.snapshot).owner, "E407.Ingress.NullSinkCannotMintPublication")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalDispatchedTurnExactRetirementDropsCorrelatedLateLocation() = runTest {
        withOriginalTurn { b ->
            b.settleOriginalObservation()
            val command = b.dispatchedVisualCommand()
            val id = b.string(command, "commandId")
            assertEquals(1, b.mutations, "E407.Cancel.AfterActualMutation")
            b.tokens.single().retire()
            b.tokens.single().retire()
            assertTrue(b.engine.pending().pendingCommands.none { it.dispatch.id == id },
                "E407.Cancel.SameWaitingTokenRemovesOriginalLedgerEntry")
            b.engine.post(b.location(command))
            b.engine.post(b.engine.envelope("commandAck", id))
            assertEquals(0, b.ingressCalls, "E407.Cancel.LateLocationCannotIngest")
            assertEquals(1, b.mutations, "E407.Cancel.NoOrdinaryCommandKeyReplay")
            assertEquals(b.originalPublishedOwner, assertNotNull(b.material.barrier.snapshot).owner, "E407.Cancel.NoSyntheticSuccessor")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalBoundIngressReentrantRetirementCannotReplayOrMintExecutorSuccess() = runTest {
        withOriginalTurn { b ->
            b.settleOriginalObservation()
            val command = b.dispatchedVisualCommand()
            val location = b.location(command)
            b.onIngress = {
                b.tokens.single().retire()
                b.engine.bridge().postMessage(location.toString())
            }
            b.engine.post(location)
            assertEquals(1, b.ingressCalls, "E407.Ingress.ConsumedBeforeReentrantLocation")
            assertTrue(b.consumedBeforeIngress, "E407.Ingress.ReentrantHeadAlreadyAbsent")
            assertEquals(b.originalPublishedOwner, assertNotNull(b.material.barrier.snapshot).owner, "E407.Ingress.NoSelfMintedExecutorReceipt")
            assertEquals(1, b.mutations, "E407.Ingress.NoReentrantJavascriptReplay")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalSettledButQueuedTurnRetiresSameWaitingTokenBeforeActualMutation() = runTest {
        withOriginalTurn { b ->
            b.engine.submit(ReaderBridgeCommand.PreviousPage)
            val ordinary = b.engine.expectDispatch("previousPage")
            b.settleOriginalObservation()
            val queued = assertNotNull(b.engine.pending().pendingCommands.singleOrNull {
                it.dispatch.command.type == "goToVisualPage"
            }, "E407.Queue.OriginalTurnEnqueuedBehindHead")
            assertTrue(queued.lastDispatchedGeneration == null, "E407.Queue.NotDispatched")
            assertEquals(0, b.mutations, "E407.Queue.NotMutatedBeforeActualHeadDispatch")
            b.tokens.single().retire()
            assertTrue(b.engine.pending().pendingCommands.singleOrNull()?.dispatch?.id == ordinary.id,
                "E407.Queue.ExactRetirementPreservesOrdinaryHead")
            b.engine.post(b.engine.envelope("commandAck", ordinary.id))
            assertEquals(0, b.mutations, "E407.Queue.OldAckCannotNavigateRetiredTurn")
            assertEquals(0, b.ingressCalls, "E407.Queue.NoRetiredBoundIngress")
            assertTrue(b.engine.pending().pendingCommands.isEmpty(), "E407.Queue.NoCommandKeyReplay")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalDispatchedTurnRejectsWrongCommandAndSettlementWithoutConsumingExactHead() = runTest {
        withOriginalTurn { b ->
            b.settleOriginalObservation()
            val command = b.dispatchedVisualCommand()
            val original = b.location(command)
            val id = b.string(command, "commandId")
            val mismatches = listOf(
                "commandId" to JsonPrimitive("public-wrong-command"),
                "foliateSessionId" to JsonPrimitive("public-wrong-session"),
                "pageTurnSettleToken" to JsonPrimitive("public-wrong-token"),
                "pageTurnSettleRasterGeneration" to JsonPrimitive(b.source.deck.rasterGeneration + 1L),
                "pageTurnSettleTextureGeneration" to JsonPrimitive(b.source.deck.textureGeneration + 1L))
            for ((key, value) in mismatches) {
                b.engine.post(JsonObject(original + (key to value)))
                assertEquals(0, b.ingressCalls, "E407.Correlation.MismatchCannotIngest")
                assertTrue(b.engine.pending().pendingCommands.firstOrNull()?.dispatch?.id == id,
                    "E407.Correlation.MismatchCannotConsumeExactHead")
            }
            b.engine.post(original)
            assertEquals(1, b.ingressCalls, "E407.Correlation.OriginalLocationReachesBoundIngress")
        }
    }

    /** Prospective successor-producer guards on the ORIGINAL source/Engine callback.
     * A genuine correlated location is semantic input, NOT a queried current layout.
     * Keep these guards before adding the allocation-owned successor query/currency seam;
     * the null owning sink cannot qualify successful relocation or new material. */
    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalSettledTurnCannotCaptureBootstrapLayoutAsAuthenticSuccessorCurrency() = runTest {
        withOriginalTurn { b ->
            val engineSource = b.engine.webView() as ReaderEngineCurrentDestinationQuerySource
            assertTrue(engineSource.captureResolvedLayoutCurrency() == null,
                "E407.SuccessorLayout.NoCurrencyOutsideOriginalQueryDelivery")
            var ingressHadNoLayoutCurrency = false
            var ingressRetainedReadableOwner = false
            b.onIngress = {
                // Production isolates callback throws. Capture finite observations here,
                // then assert OUTSIDE that catch boundary so the test cannot hide a failure.
                ingressHadNoLayoutCurrency = engineSource.captureResolvedLayoutCurrency() == null
                ingressRetainedReadableOwner = b.material.barrier.snapshot?.owner == b.originalPublishedOwner
            }
            b.settleOriginalObservation()
            b.engine.post(b.location(b.dispatchedVisualCommand()))
            assertEquals(1, b.ingressCalls, "E407.SuccessorLayout.OriginalCorrelatedIngressReached")
            assertTrue(ingressHadNoLayoutCurrency,
                "E407.SuccessorLayout.LocationIsNotOriginalLayoutQueryDelivery")
            assertTrue(ingressRetainedReadableOwner,
                "E407.SuccessorLayout.SemanticObservationRetainsOriginalReadableOwner")
            assertTrue(engineSource.captureResolvedLayoutCurrency() == null,
                "E407.SuccessorLayout.NoStaleCurrencyAfterTurnIngress")
            assertEquals(1, b.material.commands.filterIsInstance<ReaderTransitionCommand.AllocateMaterialBinding>().size,
                "E407.SuccessorLayout.NullOwningSinkCannotAllocateSuccessor")
            assertEquals(1, b.material.surface.submitted.size,
                "E407.SuccessorLayout.NoBootstrapDeckReplayAsSuccessor")
        }
    }

    @Test
    @Config(shadows = [Task8ImmediateGlSurfaceViewShadow::class, ReaderInstalledUnitPageSurfaceShadow::class, CarrierQueryWebViewShadow::class,
        Task398CanonicalStoragePathShadow::class])
    fun originalSuccessorLayoutQueryCannotRebindTheAlreadyOwnedTurnSemanticSlot() = runTest {
        withOriginalTurn { b ->
            val engineSource = b.engine.webView() as ReaderEngineCurrentDestinationQuerySource
            var outcomes = 0
            var originalScopeWasCurrent = false
            var headWasConsumed = false
            var noSecondHead = false
            var queryResult: ReaderSemanticExecutableResult? = null
            b.onIngress = {
                headWasConsumed = b.consumedBeforeIngress
                originalScopeWasCurrent = b.call.context.isExecutionCurrent(b.call.origin, b.source.intent.requestHandle)
                queryResult = engineSource.queryCurrentDestination(b.call.origin, b.call.context, { true }) { outcomes++ }
                noSecondHead = b.engine.pending().pendingCommands.isEmpty()
            }
            b.settleOriginalObservation()
            b.engine.post(b.location(b.dispatchedVisualCommand()))
            assertEquals(1, b.ingressCalls, "E407.SuccessorLayout.QueryControlReachedOriginalIngress")
            assertTrue(headWasConsumed, "E407.SuccessorLayout.OriginalTurnConsumedBeforeQueryControl")
            assertTrue(originalScopeWasCurrent, "E407.SuccessorLayout.OriginalTurnStillOwnsItsExecutorScope")
            assertEquals(ReaderSemanticExecutableResult.Rejected, queryResult,
                "E407.SuccessorLayout.NoSecondTransportBindingToTurnSlot")
            assertEquals(0, outcomes, "E407.SuccessorLayout.NoSelfCompletedLayoutOutcome")
            assertTrue(noSecondHead,
                "E407.SuccessorLayout.RejectedRebindingCannotCreateASecondLedgerHead")
            assertEquals(1, b.tokens.size, "E407.SuccessorLayout.SameOriginalRetirementToken")
            assertEquals(1, b.mutations, "E407.SuccessorLayout.ReadOnlyControlCannotNavigateAgain")
        }
    }

    private enum class EngineRetirement { Publication, Runtime, Root }

    private suspend fun withOriginalTurn(action: (OriginalTurnBench) -> Unit) {
        withCurrentDestinationEngine { engine ->
            ReaderInstalledRasterPreparationOwnershipHostTest().withMaterialFixture(
                legacySelection = false, foliateSessionId = engine.openDispatch.session,
                installedForeground = true, liveWebView = engine.webView()) { material ->
                material.bootstrap()
                val bench = OriginalTurnBench(engine, material)
                try { bench.start(); action(bench) }
                finally {
                    // Return actual transport/executor custody, even when a guarded assertion fails.
                    bench.tokens.forEach { it.retire() }
                    material.coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
                }
            }
        }
    }

    private class OriginalTurnBench(val engine: CurrentDestinationEngineBench,
        val material: ReaderInstalledRasterPreparationOwnershipHostTest.MaterialFixture) {
        val before = engine.lastScript()
        val originalPublishedOwner = assertNotNull(material.barrier.snapshot).owner
        val tokens = mutableListOf<ReaderSemanticTransportRetirement>()
        var mutations = 0
        var ingressCalls = 0
        var consumedBeforeIngress = false
        var onIngress: (() -> Unit)? = null
        lateinit var source: ReaderInstalledCurlGestureSource
        lateinit var call: CarrierCall

        fun start() {
            val (originalSource, originalCall) = material.startOriginalInstalledTurn(
                onTransportBound = tokens::add, onMutationStarted = { mutations++ }) { admitted ->
                source = admitted // ONLY the object minted in the original gesture cell.
                val capability = assertNotNull(engine.webView().javaClass.methods.singleOrNull {
                    it.name == "createInstalledPageTurnSemanticRequest" && Modifier.isPublic(it.modifiers) &&
                        it.parameterTypes.contentEquals(arrayOf(ReaderInstalledCurlGestureSource::class.java,
                            kotlin.jvm.functions.Function0::class.java, ReaderBoundPageTurnSemanticIngress::class.java))
                }, "E407.Turn.Prerequisite.MissingInstalledSemanticRequest")
                val ingress = ReaderBoundPageTurnSemanticIngress { observedSource, origin, event ->
                    ingressCalls++
                    assertSame(source, observedSource, "E407.Ingress.ExactOriginalSource")
                    assertTrue(call.origin == origin, "E407.Ingress.ExactExecutorOrigin")
                    assertTrue(event.commandId != null && event.pageTurnSettleToken != null &&
                        event.destinationCommitIdentity != null, "E407.Ingress.CorrelatedSettlementNotAck")
                    consumedBeforeIngress = engine.pending().pendingCommands.none { it.dispatch.id == event.commandId }
                    onIngress?.invoke()
                    null // A transport fixture cannot be the owning Screen model/progress receipt producer.
                }
                @Suppress("UNCHECKED_CAST")
                val request = try { capability.invoke(engine.webView(), source, { true }, ingress) as ReaderSemanticExecutableRequest }
                catch (_: InvocationTargetException) { throw AssertionError("E407.Turn.OriginalRequestInvocationThrew") }
                request
            }
            assertSame(source, originalSource, "E407.Turn.OriginalSourcePreserved")
            call = originalCall
        }

        fun settleOriginalObservation() {
            material.dispatchOriginalDown(source)
            material.surface.listener.onSettlementStarted(source.intent.gestureId.value, source.deck.textureGeneration,
                "public-source", "public-target", PageChange.NEXT)
            material.surface.listener.onSettlementCompleted(source.intent.gestureId.value, source.deck.textureGeneration,
                "public-target", 2, PageChange.NEXT)
            // This is the authentic listener -> typed source observation, not a forged
            // CurlSettlementCompletion, terminal exact draw, or Foliate success receipt.
            engine.drain()
        }

        fun dispatchedVisualCommand(): JsonObject {
            val script = engine.lastScript()
            val prefix = "window.NavicReaderBridge.dispatch("
            assertTrue(script.startsWith(prefix) && script.endsWith(");"), "E407.Mutation.OriginalTypedDispatchEnvelope")
            val payload = runCatching {
                Json.parseToJsonElement(script.removePrefix(prefix).removeSuffix(");"))
            }.getOrNull() as? JsonObject
            val command = assertNotNull(payload, "E407.Mutation.OriginalTypedDispatchObject")
            assertTrue((command["type"] as? JsonPrimitive)?.content == "goToVisualPage",
                "E407.Mutation.OriginalTypedVisualNavigationRequired")
            return command
        }

        fun string(command: JsonObject, key: String): String = assertNotNull(
            (command[key] as? JsonPrimitive)?.content, "E407.Mutation.RequiredOriginalWireIdentity")

        fun location(command: JsonObject): JsonObject = buildJsonObject {
            put("type", "locationChanged")
            put("commandId", string(command, "commandId"))
            put("foliateSessionId", source.deck.binding.foliateSessionId)
            put("destinationFoliateSessionId", source.deck.binding.foliateSessionId)
            put("destinationCommitSequence", assertNotNull(source.deck.binding.destinationCommitIdentity).commitSequence + 1L)
            put("pageIndex", assertNotNull(command["pageIndex"]))
            put("pageTurnSettleToken", string(command, "settleToken"))
            put("pageTurnSettleSessionId", string(command, "settleSessionId"))
            put("pageTurnSettleRasterGeneration", assertNotNull(command["settleRasterGeneration"]))
            put("pageTurnSettleTextureGeneration", assertNotNull(command["settleTextureGeneration"]))
            // Controlled bridge input copied from the ORIGINAL dispatched command; no
            // receipt, mutation generation, destination binding or executor success is minted.
        }
    }
}
