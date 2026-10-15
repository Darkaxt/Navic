package paige.navic.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import karacken.curl.PageDisplayRect
import karacken.curl.PageImage
import karacken.curl.PageLeafRole
import karacken.curl.PageMaterial
import karacken.curl.PageChange
import karacken.curl.PageDeck
import karacken.curl.PortraitPageDeck
import karacken.curl.RenderCapabilities
import karacken.curl.RenderFailure
import karacken.curl.RenderFailureReason
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowGLSurfaceView
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderControllerStep
import paige.navic.reader.ReaderCurlPresentationFrame
import paige.navic.reader.ReaderCurlSettlementStage
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderLegacyLiveCompatibilityContext
import paige.navic.reader.ReaderLegacyLiveCompatibilityIdentity
import paige.navic.reader.ReaderLiveEngineHandoffDirection
import paige.navic.reader.ReaderLiveEnginePresentationProof
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageInteractionState
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageReadinessState
import paige.navic.reader.ReaderPageRendererReadinessState
import paige.navic.reader.ReaderPreparationPresentation
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderDiagnosticPresentation
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationEventDisposition
import paige.navic.reader.ReaderPresentationEventReceipt
import paige.navic.reader.ReaderPresentationFailureReason
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationEffect
import paige.navic.reader.ReaderPresentationLifecycleEvent
import paige.navic.reader.ReaderPresentationLifecycleState
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderPresentationTokenDomain
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationIdentity
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderTextureDeckState
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReaderWhispersyncAnchorReceipt
import paige.navic.reader.ReaderPresentationEffectQueue
import paige.navic.reader.retainsPresentationIdentity
import paige.navic.reader.assertSequenceInvariants
import paige.navic.reader.readerPageOperationPolicy
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce
import paige.navic.reader.readerViewerActionIsAdmitted
import paige.navic.reader.publicationIdentity
import paige.navic.reader.readerPagePreparationState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class KomikkuReaderNativeFrameHostTest {
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398FrameMaterialFirstUnboundEmptyMountKeepsOneExactToken() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			val wrapper = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			val source = task398FrameMaterialSource(fixture)
			fixture.bindSession(); fixture.attach()
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			val row = task398FrameRows(source).single()
			assertEquals(ReaderTransitionResourceKind.FrameHandoff, row.kind)
			assertEquals(ReaderLegacyInventorySource.FrameOrHandoff, row.physicalIdentity.source)
			assertEquals(ReaderLegacyResourceState.Registered, row.state)
			assertNull(row.visibleOwner); assertNull(row.binding); assertFalse(row.mayBeCommittedPredecessor)
			assertSame(wrapper.parent, fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer))
			// Permanent exact Native detach, not reversible activation cancellation.
			fixture.viewerClass.task7Method("detachViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			assertEquals(row.physicalIdentity, task398FrameRows(source).single().physicalIdentity)
			assertEquals(ReaderLegacyResourceState.Released, task398FrameRows(source).single().state)
			val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameDrain(source, row.physicalIdentity, confirmations::add))
			assertNull(wrapper.parent); assertEquals(listOf(row.physicalIdentity), confirmations)
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameRestore(source, fixture.domain))
			assertNull(wrapper.parent, "T398_CORE_NO_MOUNT_RESURRECTION")
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398FrameMaterialPrefixExceptionRetiresCapturedOldWrapperAndListener() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val replace = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get()); replace.invoke(fixture.viewer, outgoing)
			val source = task398FrameMaterialSource(fixture)
			val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
			val field = fixture.curl.javaClass.task7Field("onProtectedRasterSourcePageIndicesChanged")
			@Suppress("UNCHECKED_CAST") val original = field.get(fixture.curl) as (Set<Int>) -> Unit
			val sentinel = IllegalStateException("Synthetic captured-prefix failure")
			field.set(fixture.curl, { indices: Set<Int> -> original(indices); throw sentinel })
			val incoming = FrameLayout(fixture.activity.get())
			var escaped: Throwable? = null
			try { replace.invoke(fixture.viewer, incoming) }
			catch (failure: java.lang.reflect.InvocationTargetException) { escaped = failure.targetException }
			field.set(fixture.curl, original)
			assertSame(sentinel, escaped); assertNull(incoming.parent)
			assertNull(outgoing.parent, "T398_CORE_EXCEPTION_EXACT_MATERIAL_CLEANUP")
			assertFalse(engine.isAttachedToWindow)
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			assertTrue(task398FrameRows(source).isEmpty())
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398FrameMaterialReentrantSuccessorSurvivesCapturedPrefixUnwind() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val replace = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get()); replace.invoke(fixture.viewer, outgoing)
			val source = task398FrameMaterialSource(fixture)
			val field = fixture.curl.javaClass.task7Field("onProtectedRasterSourcePageIndicesChanged")
			@Suppress("UNCHECKED_CAST") val original = field.get(fixture.curl) as (Set<Int>) -> Unit
			val successor = FrameLayout(fixture.activity.get()); val obsoleteIncoming = FrameLayout(fixture.activity.get())
			var nested = false
			field.set(fixture.curl, { indices: Set<Int> ->
				original(indices)
				if (!nested) { nested = true; replace.invoke(fixture.viewer, successor) }
			})
			replace.invoke(fixture.viewer, obsoleteIncoming); field.set(fixture.curl, original)
			assertTrue(nested); assertNull(outgoing.parent); assertNull(obsoleteIncoming.parent)
			assertTrue(successor.isAttachedToWindow, "T398_CORE_SUCCESSOR_MATERIAL_RETAINED")
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			assertEquals(1, task398FrameRows(source).size)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398FrameMaterialActualLiveProducerPromotesSameMountAndListenerDrainPreservesCustody() = runTest {
		task398ActualMaterialProducer("selected")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398FrameMaterialExactProducerRejectsSameBindingDifferentEngineOrNewSlot() = runTest {
		task398ActualMaterialProducer("engine"); task398ActualMaterialProducer("slot")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398FrameMaterialFrozenLateCallbackCannotPromoteSameMount() = runTest {
		task398ActualMaterialProducer("frozen")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398FrameMaterialHiddenWrapperCannotCertifyDisplayedLiveOwner() = runTest {
		task398ActualMaterialProducer("hidden")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398PhysicalAdmissionHiddenBoundaryRetainsSemanticPredecessorAndRecovery() = runTest {
		task398PhysicalAdmissionBoundary("hidden", "T398_ADMISSION_HIDDEN_NO_SEMANTIC_EXPOSURE")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398PhysicalAdmissionReplacedBoundaryRetainsSemanticPredecessorAndRecovery() = runTest {
		task398PhysicalAdmissionBoundary("replaced", "T398_ADMISSION_REPLACED_NO_SEMANTIC_EXPOSURE")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398PhysicalAdmissionFrozenBoundaryRetainsSemanticPredecessorAndRecovery() = runTest {
		task398PhysicalAdmissionBoundary("frozen", "T398_ADMISSION_FROZEN_NO_SEMANTIC_EXPOSURE")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398PhysicalAdmissionValidBoundaryUsesSameMaterialToken() = runTest {
		task398PhysicalAdmissionBoundary("valid", "T398_ADMISSION_VALID")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398PhysicalAdmissionNullAndRejectedReceiptsNeverSelectBeforeRetry() = runTest {
		task398PhysicalAdmissionBoundary("null", "T398_ADMISSION_NULL")
		task398PhysicalAdmissionBoundary("rejected", "T398_ADMISSION_REJECTED")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class])
	fun task398PhysicalAdmissionReentrantCancelCannotDispatchOrResurrectOldProof() = runTest {
		task398PhysicalAdmissionBoundary("cancel", "T398_ADMISSION_REENTRANT_NO_SEMANTIC_EXPOSURE")
	}
	private suspend fun task398PhysicalAdmissionBoundary(mode: String, label: String) {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		var bridge: ReaderPresentationHostBridge? = null
		try {
			fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			val engine = task398OriginalEngine(fixture.activity.get()); wrapper.addView(engine)
			val material = task398FrameMaterialSource(fixture)
			val lease = fixture.viewerClass.task7Field("currentFrameInstallation").get(fixture.viewer) as ReaderFramePhysicalOwnershipAdapter.Lease
			val originalToken = lease.token
			var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = task8SettledCurlSourceState()))
			val predecessor = readerPresentationDecision(common.state.presentation).frameOwner
			assertIs<ReaderPresentationFrameOwner.NativePage>(predecessor)
			val publications = mutableListOf<ReaderPresentationAuthority>()
			val events = mutableListOf<ReaderPresentationEvent>()
			val confirmations = mutableListOf<Boolean>()
			var boundaryEntered = false
			var rejectReceipt = mode == "null" || mode == "rejected"
			var attempt: ReaderRequiredTransition.ExposeLiveEngine? = null
			fun dispatch(event: ReaderPresentationEvent): ReaderPresentationEventReceipt? {
				events += event
				if (event is ReaderPresentationEvent.LiveEngineExposureCommitted && rejectReceipt) {
					return if (mode == "null") null else readerTestPresentationReceipt(event, common.state.presentation)
						.copy(disposition = ReaderPresentationEventDisposition.Rejected)
				}
				val step = common.onPresentationEvent(event); common = step.controller
				publications += common.state.presentation.authority
				return step.presentationReceipt
			}
			val proxy = java.lang.reflect.Proxy.newProxyInstance(ReaderPresentationCommitHost::class.java.classLoader,
				arrayOf(ReaderPresentationCommitHost::class.java)) { _, method, args ->
				when (method.name) {
					"isAttachedToWindow", "getIsAttachedToWindow" -> fixture.viewer.isAttachedToWindow
					"getCurrentPresentationBinding" -> common.state.presentation.binding
					"getCurrentShellCoverGeneration" -> null
					"getShellCoverSelected" -> false
					"getMeasuredViewportWidth" -> 800
					"getMeasuredViewportHeight" -> 600
					"getLiveMaterialWitnessRequired" -> true
					"captureLiveFrameInstallation", "completeLiveFramePresentation" -> fixture.viewerClass.declaredMethods.single { it.name == method.name }
						.apply { isAccessible = true }.invoke(fixture.viewer, *(args ?: emptyArray()))
					"confirmLiveFramePresentation" -> {
						assertTrue((args!![1] as ReaderLiveEnginePhysicalWitness).matches(engine), "T398_ADMISSION_GENUINE_REGISTERED_WITNESS")
						if (!boundaryEntered) {
							boundaryEntered = true
							when (mode) {
								"hidden" -> wrapper.visibility = View.GONE
								// The actual Native replacement capture invalidates this installation's exact currency.
								"replaced" -> fixture.viewerClass.task7Method("captureViewerReplacement").invoke(fixture.viewer)
								"frozen" -> assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(material, fixture.domain))
								"cancel" -> {
									dispatch(ReaderPresentationEvent.Cancel)
									bridge!!.update(readerPresentationDecision(common.state.presentation))
								}
							}
						}
						(fixture.viewerClass.declaredMethods.single { it.name == method.name }.apply { isAccessible = true }
							.invoke(fixture.viewer, *args) as Boolean).also(confirmations::add)
					}
					"applyPresentationFrameOwner" -> fixture.viewerClass.task7Method(method.name, ReaderPresentationDecision::class.java)
						.invoke(fixture.viewer, args!![0])
					else -> Unit
				}
			} as ReaderPresentationCommitHost
			val handoff = ReaderWebViewVisualHandoff(ReaderWebViewVisualHandoffHostAdapter({
				fixture.viewerClass.task7Method("currentLiveWebView").invoke(fixture.viewer) as WebView?
			}))
			val allocator = fixture.viewerClass.task7Field("frameTokenAllocator").get(fixture.viewer) as ReaderLegacySourceLocalTokenAllocator
			bridge = ReaderPresentationHostBridge(proxy, handoff, { true }, tokenAllocator = allocator, onEvent = ::dispatch)
			val source = fixture.viewerClass.task7Method("createFrameOrHandoffSource", ReaderPresentationHostBridge::class.java).invoke(fixture.viewer, bridge)
			bridge.update(readerPresentationDecision(common.state.presentation))
			attempt = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(readerPresentationDecision(common.state.presentation).requiredTransition)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
			if (mode == "valid") {
				assertEquals(listOf(true), confirmations)
				assertIs<ReaderPresentationAuthority.LiveEngineExposed>(common.state.presentation.authority)
				assertEquals(originalToken, lease.token); assertEquals(ReaderLegacyResourceState.Visible, lease.state)
				assertSame(wrapper, engine.parent); assertTrue(engine.isAttachedToWindow)
				return
			}
			if (mode in listOf("hidden", "replaced", "frozen")) assertEquals(listOf(false), confirmations, "T398_ADMISSION_ACTUAL_ADAPTER_FALSE")
			assertFalse(publications.any { it is ReaderPresentationAuthority.LiveEngineExposed }, label)
			assertEquals(predecessor, readerPresentationDecision(common.state.presentation).frameOwner, "T398_ADMISSION_EXACT_READABLE_PREDECESSOR")
			assertFalse(events.any { it is ReaderPresentationEvent.LiveEngineExposureCommitted } && mode !in listOf("null", "rejected"), "T398_ADMISSION_NO_COMMITTED_EVENT_ON_FALSE")
			assertEquals(ReaderLegacyResourceState.Registered, lease.state); assertNull(lease.imported)
			assertEquals(originalToken, lease.token); assertSame(wrapper, engine.parent)
			if (mode in listOf("hidden", "replaced", "frozen")) {
				val failed = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(common.state.presentation.authority)
				assertEquals(attempt!!.token, failed.token); assertEquals(attempt!!.binding, failed.binding)
				assertEquals(predecessor, failed.retainedFrame)
				assertEquals(ReaderDiagnosticPresentation.Failure(ReaderPresentationFailureReason.LiveEngineUnavailable, retryable = true, cancellable = true),
					readerPresentationDecision(common.state.presentation).diagnosticPresentation)
			}
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			val row = task398FrameRows(source).single { it.physicalIdentity.sourceLocalToken == originalToken }
			assertFalse(row.mayBeCommittedPredecessor); assertNull(row.visibleOwner)
			val imported = ReaderImportedLegacyResourceRegistration(row.physicalIdentity,
				ReaderTransitionResourceRegistration(ReaderTransitionResourceKey(ReaderTransitionResourceOwnerId.AdoptedPredecessor(
					ReaderAdoptedPredecessorSeedId.fromValidatedImport(811L)), ReaderTransitionResourceKind.FrameHandoff, 813L), ReaderResourceRetirementOrder(1L, 1L, 1L)))
			val finalize = source.javaClass.declaredMethods.single { it.name.startsWith("finalizeActivatedHandoff") }
			assertIs<ReaderPortCommandResult.Rejected>(finalize.invoke(source, fixture.domain.freezeToken.value, imported, emptySet<ReaderLegacyPhysicalIdentity>()),
				"T398_ADMISSION_NO_IMPORTED_SELECTED_CUSTODY")
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameRestore(source, fixture.domain))
			wrapper.visibility = View.VISIBLE
			if (mode == "null" || mode == "rejected") {
				rejectReceipt = false
				bridge.update(readerPresentationDecision(common.state.presentation))
				assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(common.state.presentation.authority,
					"T398_ADMISSION_REJECTED_RECEIPT_REQUIRES_FRESH_WITNESS_AFTER_FREEZE")
			} else if (mode != "cancel") {
				dispatch(ReaderPresentationEvent.Retry)
				val retry = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(readerPresentationDecision(common.state.presentation).requiredTransition)
				assertTrue(retry.token != attempt!!.token, "T398_ADMISSION_RETRY_NEW_SEMANTIC_TOKEN")
				bridge.update(readerPresentationDecision(common.state.presentation))
			}
			dispatch(ReaderPresentationEvent.Cancel)
			bridge.update(readerPresentationDecision(common.state.presentation))
			bridge.update(readerPresentationDecision(common.state.presentation))
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
			assertEquals(predecessor, readerPresentationDecision(common.state.presentation).frameOwner, "T398_ADMISSION_CANCEL_EXACT_PREDECESSOR")
			assertFalse(publications.any { it is ReaderPresentationAuthority.LiveEngineExposed }, "T398_ADMISSION_CANCEL_NO_RESURRECTION")
			assertEquals(originalToken, lease.token); assertEquals(ReaderLegacyResourceState.Registered, lease.state); assertNull(lease.imported)
		} finally { bridge?.dispose(); fixture.finish() }
	}

	private suspend fun task398ActualMaterialProducer(mode: String) {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		var bridge: ReaderPresentationHostBridge? = null
		try {
			fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			val replace = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			replace.invoke(fixture.viewer, wrapper)
			val source = task398FrameMaterialSource(fixture)
			val captureMethod = fixture.viewerClass.declaredMethods.singleOrNull { it.name == "captureLiveFrameInstallation" }
			assertNotNull(captureMethod, "T398_CORE_NATIVE_EXACT_LIVE_INSTALLATION_API"); captureMethod.isAccessible = true
			val engine = task398OriginalEngine(fixture.activity.get()); wrapper.addView(engine)
			var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = task8SettledCurlSourceState()))
			val binding = assertNotNull(common.state.presentation.binding)
			var capturedInstallation: Any? = null
			val proxy = java.lang.reflect.Proxy.newProxyInstance(ReaderPresentationCommitHost::class.java.classLoader,
				arrayOf(ReaderPresentationCommitHost::class.java)) { _, method, args ->
				when (method.name) {
					"isAttachedToWindow", "getIsAttachedToWindow" -> fixture.viewer.isAttachedToWindow
					"getCurrentPresentationBinding" -> common.state.presentation.binding
					"getCurrentShellCoverGeneration" -> null
					"getShellCoverSelected" -> false
					"getMeasuredViewportWidth" -> 800
					"getMeasuredViewportHeight" -> 600
					"getLiveMaterialWitnessRequired" -> true
					"captureLiveFrameInstallation" -> captureMethod.invoke(fixture.viewer).also { capturedInstallation = it }
					"confirmLiveFramePresentation" -> fixture.viewerClass.declaredMethods.single { it.name == method.name }
						.apply { isAccessible = true }.invoke(fixture.viewer, *(args ?: emptyArray()))
					"applyPresentationFrameOwner" -> fixture.viewerClass.task7Method("applyPresentationFrameOwner", ReaderPresentationDecision::class.java)
						.invoke(fixture.viewer, args!![0])
					else -> Unit
				}
			} as ReaderPresentationCommitHost
			val handoff = ReaderWebViewVisualHandoff(ReaderWebViewVisualHandoffHostAdapter({
				fixture.viewerClass.task7Method("currentLiveWebView").invoke(fixture.viewer) as WebView?
			}))
			bridge = ReaderPresentationHostBridge(proxy, handoff, { true }) { event ->
				common.onPresentationEvent(event).also { common = it.controller }.presentationReceipt
			}
			bridge.update(readerPresentationDecision(common.state.presentation))
			assertNotNull(capturedInstallation, "T398_CORE_CAPTURE_BEFORE_ACTUAL_VISUAL_REQUEST")
			when (mode) {
				"engine" -> { wrapper.removeView(engine); wrapper.addView(task398OriginalEngine(fixture.activity.get())) }
				"slot" -> { val successor = FrameLayout(fixture.activity.get()); replace.invoke(fixture.viewer, successor); wrapper.removeView(engine); successor.addView(engine) }
				"hidden" -> wrapper.visibility = View.GONE
				"frozen" -> assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			}
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			val rows = task398FrameRows(source)
			if (mode != "selected") {
				assertEquals(0, rows.count { it.state == ReaderLegacyResourceState.Visible }, "T398_CORE_MISMATCH_NOT_VISIBLE")
				assertTrue(rows.all { !it.mayBeCommittedPredecessor })
				return
			}
			val row = rows.single()
			assertEquals(ReaderLegacyResourceState.Visible, row.state, "T398_CORE_ACTUAL_LIVE_PROMOTES_SAME_TOKEN")
			assertEquals(binding, row.binding); assertIs<ReaderPresentationFrameOwner.LiveEngine>(row.visibleOwner)
			assertEquals(ReaderTransitionResourceKind.FrameHandoff, row.kind); assertTrue(row.mayBeCommittedPredecessor)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
			fixture.drainRemaining()
			assertTrue(wrapper.isAttachedToWindow); assertTrue(engine.isAttachedToWindow)
			assertSame(wrapper, engine.parent)
			assertEquals(row, task398FrameRows(source).single(), "T398_CORE_LISTENER_DRAIN_PRESERVES_SELECTED_MATERIAL")
			val imported = ReaderImportedLegacyResourceRegistration(row.physicalIdentity,
				ReaderTransitionResourceRegistration(ReaderTransitionResourceKey(ReaderTransitionResourceOwnerId.AdoptedPredecessor(
					ReaderAdoptedPredecessorSeedId.fromValidatedImport(811L)), ReaderTransitionResourceKind.FrameHandoff, 813L),
					ReaderResourceRetirementOrder(1L, 1L, 1L)))
			val finalize = source.javaClass.declaredMethods.single { it.name.startsWith("finalizeActivatedHandoff") }
			assertEquals(ReaderPortCommandResult.Accepted, finalize.invoke(source, fixture.domain, imported, emptySet<ReaderLegacyPhysicalIdentity>()))
			assertTrue(wrapper.isAttachedToWindow); assertTrue(engine.isAttachedToWindow)
			val command = paige.navic.reader.ReaderTransitionCommand.ReleaseResource(paige.navic.reader.ReaderResourceReleaseIssuer.Session(1L, 1L),
				paige.navic.reader.ReaderReleaseCommandIdentity(paige.navic.reader.ReaderPhysicalReleaseAttemptId.fromLedger(1L), imported.registration))
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			val release = source.javaClass.getDeclaredMethod("releaseLegacy", paige.navic.reader.ReaderTransitionCommand.ReleaseResource::class.java,
				ReaderImportedLegacyResourceRegistration::class.java, kotlin.jvm.functions.Function2::class.java)
			val onConfirmed: (ReaderLegacyPhysicalIdentity, paige.navic.reader.ReaderTransitionFact.ResourceReleased) -> Unit = { identity, fact ->
				assertEquals(command.identity, fact.identity); confirmed += identity
			}
			assertEquals(ReaderPortCommandResult.Accepted, release.invoke(source, command, imported, onConfirmed))
			assertNull(wrapper.parent); assertFalse(engine.isAttachedToWindow); assertEquals(listOf(row.physicalIdentity), confirmed)
			assertIs<ReaderPortCommandResult.Rejected>(release.invoke(source, command, imported, onConfirmed))
			assertEquals(1, confirmed.size)
		} finally { bridge?.dispose(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class,
			Task398ExactMountRemovalShadow::class, Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398ComposedFrameSharedAllocatorAndSelectedImportedCustody() = runTest {
		task398ComposedMaterialProducer("selected")
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class, Task398MaterialVisualStateShadow::class,
			Task398ExactMountRemovalShadow::class, Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398ComposedFrameFrozenAndReplacementCannotPromoteIncomingCustody() = runTest {
		task398ComposedMaterialProducer("frozen"); task398ComposedMaterialProducer("slot")
	}
	private suspend fun task398ComposedMaterialProducer(mode: String) {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		var bridge: ReaderPresentationHostBridge? = null
		try {
			fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			val replace = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			replace.invoke(fixture.viewer, wrapper)
			val factory = fixture.viewerClass.declaredMethods.singleOrNull { it.name == "createFrameOrHandoffSource" }
			assertNotNull(factory, "T398_CORE_COMPOSED_FRAME_SOURCE_API"); factory.isAccessible = true
			val allocator = fixture.viewerClass.task7Field("frameTokenAllocator").get(fixture.viewer) as ReaderLegacySourceLocalTokenAllocator
			val captureMethod = fixture.viewerClass.declaredMethods.singleOrNull { it.name == "captureLiveFrameInstallation" }
			assertNotNull(captureMethod, "T398_CORE_NATIVE_EXACT_LIVE_INSTALLATION_API"); captureMethod.isAccessible = true
			val engine = task398OriginalEngine(fixture.activity.get()); wrapper.addView(engine)
			var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = task8SettledCurlSourceState()))
			val binding = assertNotNull(common.state.presentation.binding)
			var capturedInstallation: Any? = null
			val proxy = java.lang.reflect.Proxy.newProxyInstance(ReaderPresentationCommitHost::class.java.classLoader,
				arrayOf(ReaderPresentationCommitHost::class.java)) { _, method, args ->
				when (method.name) {
					"isAttachedToWindow", "getIsAttachedToWindow" -> fixture.viewer.isAttachedToWindow
					"getCurrentPresentationBinding" -> common.state.presentation.binding
					"getCurrentShellCoverGeneration" -> null
					"getShellCoverSelected" -> false
					"getMeasuredViewportWidth" -> 800
					"getMeasuredViewportHeight" -> 600
					"getLiveMaterialWitnessRequired" -> true
					"captureLiveFrameInstallation" -> captureMethod.invoke(fixture.viewer).also { capturedInstallation = it }
					"confirmLiveFramePresentation" -> fixture.viewerClass.declaredMethods.single { it.name == method.name }
						.apply { isAccessible = true }.invoke(fixture.viewer, *(args ?: emptyArray()))
					"applyPresentationFrameOwner" -> fixture.viewerClass.task7Method("applyPresentationFrameOwner", ReaderPresentationDecision::class.java)
						.invoke(fixture.viewer, args!![0])
					else -> Unit
				}
			} as ReaderPresentationCommitHost
			val handoff = ReaderWebViewVisualHandoff(ReaderWebViewVisualHandoffHostAdapter({
				fixture.viewerClass.task7Method("currentLiveWebView").invoke(fixture.viewer) as WebView?
			}))
			bridge = ReaderPresentationHostBridge(proxy, handoff, { true }, tokenAllocator = allocator) { event ->
				common.onPresentationEvent(event).also { common = it.controller }.presentationReceipt
			}
			val source = assertNotNull(factory.invoke(fixture.viewer, bridge))
			val publisher = fixture.viewerClass.task7Field("nativePagePresentationPublisher\$delegate").get(fixture.viewer) as Lazy<*>
			assertSame(allocator, publisher.value!!.javaClass.task7Field("tokenAllocator").get(publisher.value), "T398_CORE_SHARED_FRAME_ALLOCATOR")
			assertSame(allocator, bridge.javaClass.task7Field("tokenAllocator").get(bridge), "T398_CORE_SHARED_FRAME_ALLOCATOR")
			bridge.update(readerPresentationDecision(common.state.presentation))
			assertNotNull(capturedInstallation, "T398_CORE_CAPTURE_BEFORE_ACTUAL_VISUAL_REQUEST")
			when (mode) {
				"engine" -> { wrapper.removeView(engine); wrapper.addView(task398OriginalEngine(fixture.activity.get())) }
				"slot" -> { val successor = FrameLayout(fixture.activity.get()); replace.invoke(fixture.viewer, successor); wrapper.removeView(engine); successor.addView(engine) }
				"hidden" -> wrapper.visibility = View.GONE
				"frozen" -> assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			}
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(source, fixture.domain))
			val rows = task398FrameRows(source)
			if (mode != "selected") {
				assertEquals(0, rows.count { it.state == ReaderLegacyResourceState.Visible }, "T398_CORE_MISMATCH_NOT_VISIBLE")
				assertTrue(rows.all { !it.mayBeCommittedPredecessor })
				return
			}
			val row = rows.single()
			assertEquals(ReaderLegacyResourceState.Visible, row.state, "T398_CORE_ACTUAL_LIVE_PROMOTES_SAME_TOKEN")
			assertEquals(binding, row.binding); assertIs<ReaderPresentationFrameOwner.LiveEngine>(row.visibleOwner)
			assertEquals(ReaderTransitionResourceKind.FrameHandoff, row.kind); assertTrue(row.mayBeCommittedPredecessor)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
			fixture.drainRemaining()
			assertTrue(wrapper.isAttachedToWindow); assertTrue(engine.isAttachedToWindow)
			assertSame(wrapper, engine.parent)
			assertEquals(row, task398FrameRows(source).single(), "T398_CORE_LISTENER_DRAIN_PRESERVES_SELECTED_MATERIAL")
			val imported = ReaderImportedLegacyResourceRegistration(row.physicalIdentity,
				ReaderTransitionResourceRegistration(ReaderTransitionResourceKey(ReaderTransitionResourceOwnerId.AdoptedPredecessor(
					ReaderAdoptedPredecessorSeedId.fromValidatedImport(811L)), ReaderTransitionResourceKind.FrameHandoff, 813L),
					ReaderResourceRetirementOrder(1L, 1L, 1L)))
			val finalize = source.javaClass.declaredMethods.single { it.name.startsWith("finalizeActivatedHandoff") }
			assertIs<ReaderPortCommandResult.Rejected>(finalize.invoke(source, fixture.domain.freezeToken.value + 1L, imported, emptySet<ReaderLegacyPhysicalIdentity>()), "T398_CORE_COMPOSED_EXACT_FREEZE_TOKEN")
			assertIs<ReaderPortCommandResult.Rejected>(finalize.invoke(source, fixture.domain.freezeToken.value, imported, setOf(row.physicalIdentity)), "T398_CORE_COMPOSED_EXACT_SELECTED_CUSTODY")
			assertEquals(ReaderPortCommandResult.Accepted, finalize.invoke(source, fixture.domain.freezeToken.value, imported, emptySet<ReaderLegacyPhysicalIdentity>()))
			assertTrue(wrapper.isAttachedToWindow); assertTrue(engine.isAttachedToWindow)
			val command = paige.navic.reader.ReaderTransitionCommand.ReleaseResource(paige.navic.reader.ReaderResourceReleaseIssuer.Session(1L, 1L),
				paige.navic.reader.ReaderReleaseCommandIdentity(paige.navic.reader.ReaderPhysicalReleaseAttemptId.fromLedger(1L), imported.registration))
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			val release = source.javaClass.getDeclaredMethod("releaseLegacy", paige.navic.reader.ReaderTransitionCommand.ReleaseResource::class.java,
				ReaderImportedLegacyResourceRegistration::class.java, kotlin.jvm.functions.Function2::class.java)
			val onConfirmed: (ReaderLegacyPhysicalIdentity, paige.navic.reader.ReaderTransitionFact.ResourceReleased) -> Unit = { identity, fact ->
				assertEquals(command.identity, fact.identity); confirmed += identity
			}
			Task398ExactMountRemovalShadow.clear()
			Task398ExactMountRemovalShadow.target = wrapper.parent as FrameLayout
			assertEquals(ReaderPortCommandResult.Accepted, release.invoke(source, command, imported, onConfirmed))
			assertEquals(1, Task398ExactMountRemovalShadow.targetEntries, "T398_CORE_COMPOSED_EXACT_IMPORTED_REMOVAL")
			assertNull(wrapper.parent); assertFalse(engine.isAttachedToWindow); assertEquals(listOf(row.physicalIdentity), confirmed)
			assertIs<ReaderPortCommandResult.Rejected>(release.invoke(source, command, imported, onConfirmed))
			assertEquals(1, confirmed.size)
			assertEquals(1, Task398ExactMountRemovalShadow.targetEntries, "T398_CORE_COMPOSED_NO_DUPLICATE_IMPORTED_REMOVAL")
		} finally { Task398ExactMountRemovalShadow.clear(); bridge?.dispose(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task398RemovalEntryInstallationShadow::class, Task398RemovalEntryInstallationShadow.FrameLayoutShadow::class])
	fun task398CoreAuthenticShadowRemovalEntryInstallationControl() {
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val shadow = Task398RemovalEntryInstallationShadow
		try {
			shadow.clear()
			val parent = FrameLayout(activity.get()); val outgoing = FrameLayout(activity.get())
			val engine = task398OriginalEngine(activity.get()); outgoing.addView(engine)
			activity.get().setContentView(parent); parent.addView(outgoing)
			assertTrue(engine.isAttachedToWindow)
			parent.removeView(outgoing); assertNull(outgoing.parent); assertFalse(engine.isAttachedToWindow)
			assertEquals(0, shadow.entries)
			parent.addView(outgoing)
			shadow.target = parent; shadow.child = outgoing
			var reached = 0
			shadow.beforeOriginal = {
				reached++
				assertSame(parent, outgoing.parent, "T398_CONTROL_NEW_SHADOW_HOOK_BEFORE_ORIGINAL")
				assertTrue(engine.isAttachedToWindow)
				assertNull(shadow.beforeOriginal, "T398_CONTROL_NEW_SHADOW_HOOK_CONSUMED_BEFORE_RECIPIENT")
			}
			val untargeted = FrameLayout(activity.get()); val other = View(activity.get())
			untargeted.addView(other); untargeted.removeView(other)
			assertNull(other.parent); assertEquals(0, reached); assertEquals(0, shadow.entries)
			parent.removeView(outgoing)
			assertEquals(1, reached); assertEquals(1, shadow.hooks); assertEquals(1, shadow.entries)
			assertNull(outgoing.parent); assertFalse(engine.isAttachedToWindow)
			parent.addView(outgoing); parent.removeView(outgoing)
			assertEquals(1, reached); assertEquals(1, shadow.hooks); assertEquals(2, shadow.entries)
			assertNull(outgoing.parent); assertFalse(engine.isAttachedToWindow)
		} finally { shadow.clear(); activity.pause().stop().destroy() }
	}
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398RemovalEntryInstallationShadow::class, Task398RemovalEntryInstallationShadow.FrameLayoutShadow::class])
	fun task398CoreActivatedAndReleaseOnlyEntryPreserveExactOutgoingMaterial() = runTest {
		for (releaseOnly in listOf(false, true)) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
			val shadow = Task398RemovalEntryInstallationShadow
			try {
				shadow.clear(); fixture.bindSession(); fixture.attach()
				val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
				val outgoing = FrameLayout(fixture.activity.get()); replacement.invoke(fixture.viewer, outgoing)
				val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
				val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
				val exact = fixture.viewerClass.task7Field("currentFrameInstallation").get(fixture.viewer) as ReaderFramePhysicalOwnershipAdapter.Lease
				shadow.target = content; shadow.child = outgoing
				val installed = task398InstallFacts(store) { ReaderPortCommandResult.Accepted }
				if (releaseOnly) assertTrue(ReaderActivatedSessionInstallationBarrier(store).failClosedAfterFinalizationRejection(installed))
				val incoming = FrameLayout(fixture.activity.get())
				assertEquals(false, replacement.invoke(fixture.viewer, incoming), "T398_CORE_INSTALLED_ENTRY_DENIAL")
				assertSame(content, outgoing.parent); assertTrue(engine.isAttachedToWindow); assertNull(incoming.parent)
				assertEquals(0, shadow.entries, "T398_CORE_INSTALLED_ENTRY_NO_OLD_REMOVAL")
				assertSame(exact, fixture.viewerClass.task7Field("currentFrameInstallation").get(fixture.viewer))
				val material = task398FrameMaterialSource(fixture)
				assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(material, fixture.domain))
				val row = task398FrameRows(material).single()
				assertEquals(exact.token, row.physicalIdentity.sourceLocalToken)
				assertEquals(ReaderLegacyResourceState.Registered, row.state)
			} finally { shadow.clear(); fixture.finish() }
		}
	}
	private fun task398FrameMaterialSource(fixture: Task398RegisteredRootsFixture): Any {
		val field = fixture.viewerClass.declaredFields.singleOrNull { it.name == "framePhysicalOwnership" }
		assertNotNull(field, "T398_CORE_GENUINE_NATIVE_MATERIAL_SOURCE")
		field.isAccessible = true
		return assertNotNull(field.get(fixture.viewer))
	}
	private fun task398FrameFreeze(source: Any, domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult =
		source.javaClass.getDeclaredMethod("freezeForTransitionActivation", ReaderLegacyPhysicalDomain::class.java)
			.invoke(source, domain) as ReaderPortCommandResult
	@Suppress("UNCHECKED_CAST")
	private fun task398FrameRows(source: Any): List<ReaderFrozenLegacyResource> =
		source.javaClass.getDeclaredMethod("snapshotFrozenOwnership").invoke(source) as List<ReaderFrozenLegacyResource>
	private fun task398FrameDrain(source: Any, identity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult =
		source.javaClass.getDeclaredMethod("drainFrozenOwnership", ReaderLegacyPhysicalIdentity::class.java,
			kotlin.jvm.functions.Function1::class.java).invoke(source, identity, onConfirmed) as ReaderPortCommandResult
	private fun task398FrameRestore(source: Any, domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult =
		source.javaClass.getDeclaredMethod("restoreAfterTransitionActivation", ReaderLegacyPhysicalDomain::class.java)
			.invoke(source, domain) as ReaderPortCommandResult

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398StandaloneAdmittedCurlRestoreAcceptedButCapturedEpochCannotResumeProtectedOrReadinessTail() = runTest {
		for (boundary in listOf("protected", "readiness")) {
			val fixture = Task398RegisteredRootsFixture(activityBacked = true)
			try {
				fixture.bindSession(); fixture.attach()
				val curl = fixture.curl
				val type = curl.javaClass
				type.task7Method("updateReadiness", ReaderTextureDeckState::class.java,
					ReaderTextureDeckState::class.java, ReaderPageInteractionState::class.java, String::class.java)
					.invoke(curl, ReaderTextureDeckState.Empty, ReaderTextureDeckState.Empty, ReaderPageInteractionState.Ready,
						"task398-standalone-arrangement")
				val protectedField = type.task7Field("onProtectedRasterSourcePageIndicesChanged")
				val readinessField = type.task7Field("onReadinessStateChange")
				val profileField = type.task7Field("onRasterProfileEpochChanged")
				@Suppress("UNCHECKED_CAST") val originalProtected = protectedField.get(curl) as (Set<Int>) -> Unit
				@Suppress("UNCHECKED_CAST") val originalReadiness = readinessField.get(curl) as (ReaderPageRendererReadinessState) -> Unit
				@Suppress("UNCHECKED_CAST") val originalProfile = profileField.get(curl) as (Long?) -> Unit
				val admission = fixture.delivery.capturePhysicalAdmission()
				var reached = false
				var frozen: ReaderPortCommandResult? = null
				var restored: ReaderPortCommandResult? = null
				var inside = emptyList<ReaderFrozenLegacyResource>()
				var profilesAfter = 0
				var readinessAfter = 0
				fun cross() {
					if (reached) return
					reached = true
					curl.surfaceView.alpha = 0.61f
					frozen = fixture.delivery.freezeForTransitionActivation(fixture.domain)
					inside = fixture.delivery.snapshotFrozenOwnership()
					restored = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
				}
				protectedField.set(curl, { indices: Set<Int> -> originalProtected(indices); if (boundary == "protected") cross() })
				readinessField.set(curl, { state: ReaderPageRendererReadinessState ->
					if (reached) readinessAfter++
					originalReadiness(state); if (boundary == "readiness") cross()
				})
				profileField.set(curl, { epoch: Long? -> if (reached) profilesAfter++; originalProfile(epoch) })
				// Genuine controller invocation with the real captured Native admission, but no enclosing Native method return.
				curl.invalidate("task398-standalone-admitted", legacyConsequencesAdmitted = {
					fixture.delivery.registeredRootAdmits(admission)
				})
				assertTrue(reached)
				assertEquals(ReaderPortCommandResult.Accepted, frozen)
				assertEquals(0, inside.count { it.state == ReaderLegacyResourceState.Running })
				assertEquals(ReaderPortCommandResult.Accepted, restored)
				assertFalse(fixture.delivery.registeredRootAdmits(admission), "Restoration cannot borrow the captured old epoch")
				assertTrue(fixture.delivery.registeredRootAdmits(fixture.delivery.capturePhysicalAdmission()))
				assertEquals(0, profilesAfter); assertEquals(0, readinessAfter)
				assertEquals(0.61f, curl.surfaceView.alpha)
				assertNull(type.task7Field("requestedProfile").get(curl)); assertNull(type.task7Field("activePages").get(curl))
			} finally { fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398MountFrameworkDisarmedActivitySetupControl() {
		Task398ExactMountRemovalShadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		try {
			val parent = FrameLayout(activity.get())
			activity.get().setContentView(parent)
			val child = FrameLayout(activity.get()); parent.addView(child)
			assertSame(parent, child.parent); assertTrue(child.isAttachedToWindow)
			parent.removeView(child)
			assertNull(child.parent); assertFalse(child.isAttachedToWindow)
		} finally {
			Task398ExactMountRemovalShadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398MountFrameworkDisarmedRealComposeSetupControl() {
		Task398ExactMountRemovalShadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
		val activity = Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
		val compose = androidx.compose.ui.platform.ComposeView(activity.get())
		try {
			compose.setContent { }
			activity.get().setContentView(compose)
			assertTrue(compose.isAttachedToWindow, "T398_CONTROL_COMPOSE_REAL_ATTACH")
			assertTrue(compose.hasComposition, "T398_CONTROL_COMPOSE_REAL_COMPOSITION")
			compose.disposeComposition()
			assertFalse(compose.hasComposition, "T398_CONTROL_COMPOSE_REAL_DISPOSAL")
		} finally {
			compose.disposeComposition()
			Task398ExactMountRemovalShadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398MountFrameworkDisarmedNativeSetupControl() = runTest {
		Task398ExactMountRemovalShadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			assertTrue(fixture.viewer.isAttachedToWindow)
			val outgoing = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, outgoing)
			assertTrue(outgoing.isAttachedToWindow)
		} finally {
			Task398ExactMountRemovalShadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
			fixture.finish()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398MountFrameworkUntargetedAndTargetedNormalRemovalControl() {
		val shadow = Task398ExactMountRemovalShadow
		shadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
		val context = RuntimeEnvironment.getApplication()
		try {
			val parent = FrameLayout(context); val child = FrameLayout(context)
			parent.addView(child); parent.removeView(child)
			assertNull(child.parent); assertEquals(0, parent.childCount)
			parent.addView(child); parent.removeAllViews()
			assertNull(child.parent); assertEquals(0, parent.childCount)
			assertEquals(0, shadow.targetEntries); assertEquals(0, shadow.completedOriginalDelegates)
			shadow.target = parent
			parent.addView(child); parent.removeView(child)
			assertNull(child.parent); assertEquals(0, parent.childCount)
			parent.addView(child); parent.removeAllViews()
			assertNull(child.parent); assertEquals(0, parent.childCount)
			assertTrue(shadow.actualPrimitiveEntered); assertTrue(shadow.originalDelegateCompleted)
			assertEquals(2, shadow.targetEntries); assertEquals(2, shadow.completedOriginalDelegates)
		} finally { shadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398MountFrameworkRealEngineAttachDetachControl() = runTest {
		val shadow = Task398ExactMountRemovalShadow
		shadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear()
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val outgoing = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, outgoing)
			val engine = task398OriginalEngine(fixture.activity.get())
			outgoing.addView(engine)
			assertSame(outgoing, engine.parent); assertTrue(engine.isAttachedToWindow)
			assertEquals(1, Task398ForegroundRegistrationWebViewShadow.listeners.size)
			shadow.target = outgoing
			outgoing.removeView(engine)
			assertNull(engine.parent); assertFalse(engine.isAttachedToWindow)
			assertTrue(shadow.actualPrimitiveEntered); assertTrue(shadow.originalDelegateCompleted)
			assertEquals(1, shadow.targetEntries); assertEquals(1, shadow.completedOriginalDelegates)
		} finally {
			shadow.clear(); Task398ForegroundRegistrationWebViewShadow.clear(); fixture.finish()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398MountFrameworkBeforeAndAfterOriginalOneShotRemovalControls() = runTest {
		val shadow = Task398ExactMountRemovalShadow
		val listenerShadow = Task398ForegroundRegistrationWebViewShadow
		shadow.clear(); listenerShadow.clear()
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
			for (removeAll in listOf(false, true)) {
				for (position in Task398ExactMountRemovalShadow.FailurePosition.entries) {
					shadow.clear()
					val outgoing = FrameLayout(fixture.activity.get())
					replacement.invoke(fixture.viewer, outgoing)
					val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
					assertSame(content, outgoing.parent); assertTrue(engine.isAttachedToWindow)
					val sentinel = IllegalStateException("Synthetic exact original removal boundary")
					var callbackCount = 0
					var parentAtFailure: android.view.ViewParent? = null
					var attachedAtFailure: Boolean? = null
					shadow.target = content; shadow.failure = sentinel; shadow.failurePosition = position
					shadow.beforeFailure = {
						callbackCount++; parentAtFailure = outgoing.parent; attachedAtFailure = engine.isAttachedToWindow
					}
					var escaped: Throwable? = null
					try { if (removeAll) content.removeAllViews() else content.removeView(outgoing) }
					catch (failure: Throwable) { escaped = failure }
					assertSame(sentinel, escaped, "T398_CONTROL_REMOVAL_SENTINEL_IDENTITY")
					assertTrue(shadow.actualPrimitiveEntered); assertEquals(1, shadow.targetEntries)
					assertEquals(1, callbackCount); assertNull(shadow.failure)
					val before = position == Task398ExactMountRemovalShadow.FailurePosition.BEFORE_ORIGINAL
					assertEquals(!before, shadow.originalDelegateCompleted)
					assertEquals(if (before) 0 else 1, shadow.completedOriginalDelegates)
					if (before) {
						assertSame(content, parentAtFailure); assertSame(content, outgoing.parent)
						assertEquals(true, attachedAtFailure); assertTrue(engine.isAttachedToWindow)
					} else {
						assertNull(parentAtFailure); assertNull(outgoing.parent)
						assertEquals(false, attachedAtFailure); assertFalse(engine.isAttachedToWindow)
					}
					// The consumed sentinel permits the same real primitive to finish normally.
					if (removeAll) content.removeAllViews() else content.removeView(outgoing)
					assertNull(outgoing.parent); assertFalse(engine.isAttachedToWindow)
					assertTrue(shadow.originalDelegateCompleted); assertEquals(2, shadow.targetEntries)
					assertEquals(if (before) 1 else 2, shadow.completedOriginalDelegates)
					assertEquals(1, callbackCount)
				}
			}
		} finally { shadow.clear(); listenerShadow.clear(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class, Task398ExactMountRemovalShadow::class,
			Task398ExactMountRemovalShadow.FrameLayoutShadow::class])
	fun task398ReplacementUnknownBeforeMountRemovalMustRetainExactRetryNotConfirmListenerOnly() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		val shadow = Task398ExactMountRemovalShadow
		val listenerShadow = Task398ForegroundRegistrationWebViewShadow
		try {
			listenerShadow.clear(); shadow.clear(); fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
			val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
			val listener = listenerShadow.listeners.single()
			val incoming = FrameLayout(fixture.activity.get())
			val material = task398FrameMaterialSource(fixture)
			var materialFrozen: ReaderPortCommandResult? = null
			var uncertain = emptyList<ReaderFrozenLegacyResource>()
			val sentinel = IllegalStateException("Synthetic unknown exact mount removal acceptance")
			var frozen: ReaderPortCommandResult? = null
			shadow.target = content; shadow.failure = sentinel; shadow.beforeFailure = {
				frozen = fixture.delivery.freezeForTransitionActivation(fixture.domain)
				materialFrozen = task398FrameFreeze(material, fixture.domain)
				uncertain = task398FrameRows(material)
			}
			var escaped: Throwable? = null
			try { replacement.invoke(fixture.viewer, incoming) }
			catch (failure: java.lang.reflect.InvocationTargetException) { escaped = failure.targetException }
			assertSame(sentinel, escaped, "T398_VR_UNKNOWN_SENTINEL_IDENTITY")
			assertTrue(shadow.actualPrimitiveEntered, "T398_VR_UNKNOWN_ACTUAL_PRIMITIVE")
			assertFalse(shadow.originalDelegateCompleted, "T398_VR_UNKNOWN_BEFORE_ORIGINAL_UNCOMPLETED")
			assertEquals(1, shadow.targetEntries); assertEquals(0, shadow.completedOriginalDelegates)
			assertEquals(ReaderPortCommandResult.Accepted, frozen, "T398_VR_UNKNOWN_FREEZE_ACCEPTED")
			assertSame(content, outgoing.parent, "T398_VR_UNKNOWN_BEFORE_PARENT"); assertTrue(engine.isAttachedToWindow, "T398_VR_UNKNOWN_ENGINE_ATTACHED"); assertNull(incoming.parent, "T398_VR_UNKNOWN_INCOMING_UNMOUNTED")
			assertFalse(listener in listenerShadow.listeners, "T398_CORE_UNKNOWN_LISTENER_RETIRED_MATERIAL_UNCERTAIN")
			assertEquals(ReaderPortCommandResult.Accepted, materialFrozen)
			val exact = uncertain.single()
			assertEquals(ReaderLegacyResourceState.ReleaseRequested, exact.state)
			assertEquals(ReaderLegacyInventorySource.FrameOrHandoff, exact.physicalIdentity.source)
			assertEquals(ReaderTransitionResourceKind.FrameHandoff, exact.kind)
			// Actual existing frozen ownership cancellation is the required exact retry mechanism.
			// Retiring only its listener must never be represented as successful mount retirement.
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty(), "T398_VR_UNKNOWN_DRAIN_EMPTY")
			assertSame(content, outgoing.parent, "T398_CORE_DELIVERY_DRAIN_NOT_MATERIAL_CONFIRMATION")
			assertTrue(engine.isAttachedToWindow)
			assertEquals(exact.physicalIdentity, task398FrameRows(material).single().physicalIdentity)
			val materialConfirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameDrain(material, exact.physicalIdentity, materialConfirmations::add))
			assertEquals(listOf(exact.physicalIdentity), materialConfirmations)
			fixture.drainRemaining() // Exact material primitive introduced real late Delivery returns.
			assertEquals(2, shadow.targetEntries); assertEquals(1, shadow.completedOriginalDelegates)
			assertEquals(1, if (outgoing.parent == null && !engine.isAttachedToWindow) 1 else 0,
				"T398_VR_UNKNOWN_MOUNT_EXACT_RETRY")
			assertFalse(listener in listenerShadow.listeners)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			assertEquals(ReaderPortCommandResult.Accepted, task398FrameRestore(material, fixture.domain))
			listener.onViewAttachedToWindow(engine)
			assertNull(outgoing.parent); assertFalse(engine.isAttachedToWindow); assertTrue(listenerShadow.listeners.isEmpty())
		} finally { shadow.clear(); listenerShadow.clear(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementEntryOwnsOriginalProtectedRecipientReturn() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			replacement.invoke(fixture.viewer, FrameLayout(fixture.activity.get()))
			val field = fixture.curl.javaClass.task7Field("onProtectedRasterSourcePageIndicesChanged")
			@Suppress("UNCHECKED_CAST") val original = field.get(fixture.curl) as (Set<Int>) -> Unit
			var reached = false
			var frozen: ReaderPortCommandResult? = null
			var restoredInside: ReaderPortCommandResult? = null
			var inside = emptyList<ReaderFrozenLegacyResource>()
			field.set(fixture.curl, { indices: Set<Int> ->
				original(indices)
				if (!reached) {
					reached = true
					frozen = fixture.delivery.freezeForTransitionActivation(fixture.domain)
					inside = fixture.delivery.snapshotFrozenOwnership()
					// Exactly the same pre-drain restore operation used by accepted Q1
					// freeze/restore recipients, but this actual enclosing method is on stack.
					restoredInside = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
				}
			})
			replacement.invoke(fixture.viewer, FrameLayout(fixture.activity.get()))
			assertTrue(reached, "T398_VR_ENTRY_REAL_RECIPIENT")
			assertEquals(ReaderPortCommandResult.Accepted, frozen)
			assertEquals(1, inside.count { it.state == ReaderLegacyResourceState.Running },
				"T398_VR_ENTRY_METHOD_RETURN")
			assertIs<ReaderPortCommandResult.Rejected>(restoredInside,
				"T398_VR_ENTRY_RESTORE_ON_STACK")
			assertEquals(ReaderPortCommandResult.Accepted,
				fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398RemovalEntryInstallationShadow::class, Task398RemovalEntryInstallationShadow.FrameLayoutShadow::class])
	fun task398ReplacementEarlyFactDrainRetainsExactReturnsThroughHierarchyUntilOuterUnwind() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		val shadow = Task398RemovalEntryInstallationShadow
		try {
			shadow.clear(); fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
			val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
			val incoming = FrameLayout(fixture.activity.get())
			assertTrue(engine.isAttachedToWindow)
			val drain = Task398ReplacementDrainObservation(fixture)
			var frozen: ReaderPortCommandResult? = null
			var factInside = emptyList<ReaderFrozenLegacyResource>()
			var factRequested = emptySet<ReaderLegacyPhysicalIdentity>()
			var factPending = emptyList<ReaderFrozenLegacyResource>()
			var hierarchyPending = emptyList<ReaderFrozenLegacyResource>()
			var factReturnsInFlight = false
			var hierarchyReturnsInFlight = false
			var hierarchyReached = false
			var factConfirmations = -1
			var hierarchyConfirmations = -1
			var factRestore: ReaderPortCommandResult? = null
			var hierarchyRestore: ReaderPortCommandResult? = null
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			content.setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
				override fun onChildViewAdded(parent: View, child: View) = Unit
				override fun onChildViewRemoved(parent: View, child: View) {
					if (child === outgoing) {
						hierarchyReached = true
						hierarchyPending = fixture.delivery.snapshotFrozenOwnership().filter { it.physicalIdentity in factRequested }
						hierarchyReturnsInFlight = fixture.delivery.hasPhysicalReturnsInFlight
						hierarchyConfirmations = fixture.confirmations.size
						hierarchyRestore = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
					}
				}
			})
			shadow.target = content; shadow.child = outgoing
			shadow.beforeOriginal = { task398InstallFacts(store) { fact ->
				facts += fact
				if (fact.kind == ReaderTask6LifecycleSafetyKind.WebViewDetached) {
					frozen = fixture.delivery.freezeForTransitionActivation(fixture.domain)
					factInside = fixture.delivery.snapshotFrozenOwnership()
					factRequested = factInside.filter { it.state == ReaderLegacyResourceState.Running }.map { it.physicalIdentity }.toSet()
					// Request the real five-return drain at the fact itself. Recipient failures
					// can be swallowed, so collect observations here and assert after unwind.
					drain.observe()
					factPending = fixture.delivery.snapshotFrozenOwnership().filter { it.physicalIdentity in factRequested }
					factReturnsInFlight = fixture.delivery.hasPhysicalReturnsInFlight
					factConfirmations = fixture.confirmations.size
					factRestore = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
				}
				ReaderPortCommandResult.Accepted
			} }
			replacement.invoke(fixture.viewer, incoming)
			content.setOnHierarchyChangeListener(null)
			assertEquals(1, shadow.entries, "T398_CORE_EARLY_DRAIN_ACTUAL_PRIMITIVE")
			assertEquals(1, shadow.hooks, "T398_CORE_EARLY_DRAIN_ONESHOT_INSTALLATION")
			assertEquals(listOf(ReaderTask6LifecycleSafetyKind.WebViewDetached), facts.map { it.kind }, "T398_CORE_EARLY_DRAIN_REAL_DETACH_FACT")
			assertEquals(ReaderPortCommandResult.Accepted, frozen)
			assertTrue(hierarchyReached, "T398_CORE_EARLY_DRAIN_REAL_HIERARCHY")
			assertEquals(5, factInside.count { it.state == ReaderLegacyResourceState.Running }, "T398_CORE_EARLY_DRAIN_FACT_FIVE_RUNNING")
			assertEquals(5, factRequested.size, "T398_CORE_EARLY_DRAIN_EXACT_FIVE_IDENTITIES")
			assertEquals(factRequested, factPending.map { it.physicalIdentity }.toSet(), "T398_CORE_EARLY_DRAIN_FACT_EXACT_RETENTION")
			assertEquals(factRequested, hierarchyPending.map { it.physicalIdentity }.toSet(), "T398_CORE_EARLY_DRAIN_HIERARCHY_EXACT_RETENTION")
			assertEquals(5, factPending.count { it.state == ReaderLegacyResourceState.ReleaseRequested }, "T398_CORE_EARLY_DRAIN_FACT_REQUESTED_PROJECTION")
			assertEquals(5, hierarchyPending.count { it.state == ReaderLegacyResourceState.ReleaseRequested }, "T398_CORE_EARLY_DRAIN_HIERARCHY_REQUESTED_PROJECTION")
			assertTrue(factReturnsInFlight, "T398_CORE_EARLY_DRAIN_FACT_RETURNS_IN_FLIGHT")
			assertTrue(hierarchyReturnsInFlight, "T398_CORE_EARLY_DRAIN_HIERARCHY_RETURNS_IN_FLIGHT")
			assertEquals(0, factConfirmations, "T398_CORE_EARLY_DRAIN_NO_FACT_CONFIRMATION")
			assertEquals(0, hierarchyConfirmations, "T398_CORE_EARLY_DRAIN_NO_HIERARCHY_CONFIRMATION")
			assertIs<ReaderPortCommandResult.Rejected>(factRestore, "T398_CORE_EARLY_DRAIN_FACT_RESTORE_REJECTED")
			assertIs<ReaderPortCommandResult.Rejected>(hierarchyRestore, "T398_CORE_EARLY_DRAIN_HIERARCHY_RESTORE_REJECTED")
			assertFalse(fixture.delivery.hasPhysicalReturnsInFlight, "T398_CORE_EARLY_DRAIN_OUTER_RETURN_SETTLED")
			assertNull(outgoing.parent); assertFalse(engine.isAttachedToWindow); assertNull(incoming.parent)
			assertEquals(0, content.childCount, "T398_CORE_EARLY_DRAIN_INSTALLED_DENIES_INCOMING")
			assertTrue(factRequested.all { identity -> fixture.confirmations.count { it == identity } == 1 }, "T398_CORE_EARLY_DRAIN_FIVE_CONFIRMED_ON_OUTER_UNWIND")
			drain.observe()
			assertTrue(drain.results.isNotEmpty() && drain.results.all { it == ReaderPortCommandResult.Accepted })
			assertTrue(drain.rows.all { it.physicalIdentity.domain == fixture.domain && it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty(), "T398_CORE_EARLY_DRAIN_FINAL_INVENTORY_EMPTY")
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet(), "T398_CORE_EARLY_DRAIN_ALL_OBSERVED_IDENTITIES_CONFIRM")
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size, "T398_CORE_EARLY_DRAIN_EXACTLY_ONCE")
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
		} finally { shadow.clear(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398RemovalEntryInstallationShadow::class, Task398RemovalEntryInstallationShadow.FrameLayoutShadow::class])
	fun task398ReplacementRemovalOwnsMethodAndPrimitiveBeyondRealInstalledFactReturn() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
			val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
			val incoming = FrameLayout(fixture.activity.get())
			assertTrue(engine.isAttachedToWindow)
			val drain = Task398ReplacementDrainObservation(fixture)
			var frozen: ReaderPortCommandResult? = null
			var factInside = emptyList<ReaderFrozenLegacyResource>()
			var hierarchyInside = emptyList<ReaderFrozenLegacyResource>()
			var hierarchyReached = false
			var hierarchyConfirmations = -1
			var hierarchyRestore: ReaderPortCommandResult? = null
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			content.setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
				override fun onChildViewAdded(parent: View, child: View) = Unit
				override fun onChildViewRemoved(parent: View, child: View) {
					if (child === outgoing) {
						hierarchyReached = true
						hierarchyInside = fixture.delivery.snapshotFrozenOwnership()
						drain.observe()
						hierarchyConfirmations = fixture.confirmations.size
						hierarchyRestore = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
					}
				}
			})
			Task398RemovalEntryInstallationShadow.target = content
			Task398RemovalEntryInstallationShadow.child = outgoing
			Task398RemovalEntryInstallationShadow.beforeOriginal = { task398InstallFacts(store) { fact ->
				facts += fact
				if (fact.kind == ReaderTask6LifecycleSafetyKind.WebViewDetached) {
					frozen = fixture.delivery.freezeForTransitionActivation(fixture.domain)
					factInside = fixture.delivery.snapshotFrozenOwnership()
				}
				ReaderPortCommandResult.Accepted
			} }
			replacement.invoke(fixture.viewer, incoming)
			content.setOnHierarchyChangeListener(null)
			assertEquals(listOf(ReaderTask6LifecycleSafetyKind.WebViewDetached), facts.map { it.kind })
			assertEquals(ReaderPortCommandResult.Accepted, frozen)
			assertTrue(hierarchyReached, "T398_VR_REMOVAL_REAL_HIERARCHY")
			assertFalse(engine.isAttachedToWindow); assertNull(incoming.parent)
			assertEquals(0, content.childCount, "T398_CORE_REMOVAL_INSTALLED_DENIES_OBSOLETE_INCOMING")
			assertEquals(5, factInside.count { it.state == ReaderLegacyResourceState.Running },
				"T398_VR_REMOVAL_FACT_METHOD_PRIMITIVE_RETURNS")
			assertEquals(2, hierarchyInside.count { it.state == ReaderLegacyResourceState.Running },
				"T398_VR_REMOVAL_AFTER_INNER_RETURN")
			assertEquals(0, hierarchyConfirmations, "T398_VR_REMOVAL_NO_INNER_CONFIRMATION")
			assertIs<ReaderPortCommandResult.Rejected>(hierarchyRestore)
			drain.observe()
			assertTrue(drain.results.all { it == ReaderPortCommandResult.Accepted })
			assertTrue(drain.rows.all { it.physicalIdentity.domain == fixture.domain &&
				it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			assertEquals(ReaderPortCommandResult.Accepted,
				fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
		} finally { Task398RemovalEntryInstallationShadow.clear(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementAdditionOwnsActualMethodAndPrimitiveIncludingLateDrainRows() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
			val incoming = FrameLayout(fixture.activity.get())
			val drain = Task398ReplacementDrainObservation(fixture)
			var reached = false
			var inside = emptyList<ReaderFrozenLegacyResource>()
			var frozen: ReaderPortCommandResult? = null
			var restoredInside: ReaderPortCommandResult? = null
			var confirmationsInside = -1
			content.setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
				override fun onChildViewRemoved(parent: View, child: View) = Unit
				override fun onChildViewAdded(parent: View, child: View) {
					if (child === incoming) {
						reached = true
						frozen = fixture.delivery.freezeForTransitionActivation(fixture.domain)
						inside = fixture.delivery.snapshotFrozenOwnership()
						drain.observe()
						confirmationsInside = fixture.confirmations.size
						restoredInside = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
					}
				}
			})
			replacement.invoke(fixture.viewer, incoming)
			content.setOnHierarchyChangeListener(null)
			assertTrue(reached, "T398_VR_ADD_REAL_HIERARCHY")
			assertSame(content, incoming.parent); assertNull(outgoing.parent)
			assertEquals(ReaderPortCommandResult.Accepted, frozen)
			assertEquals(2, inside.count { it.state == ReaderLegacyResourceState.Running },
				"T398_VR_ADD_METHOD_PRIMITIVE_RETURNS")
			assertEquals(0, confirmationsInside, "T398_VR_ADD_NO_ON_STACK_CONFIRMATION")
			assertIs<ReaderPortCommandResult.Rejected>(restoredInside)
			drain.observe()
			assertTrue(drain.results.all { it == ReaderPortCommandResult.Accepted })
			assertTrue(drain.rows.map { it.physicalIdentity }.toSet().size > inside.size,
				"T398_VR_ADD_AUTHENTIC_LATE_CANCELLATION_ROWS")
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			assertEquals(ReaderPortCommandResult.Accepted,
				fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class])
	fun task398ReplacementEmptyWrapperRetiresExactOriginalEngineListenerWithoutNextAttach() = runTest {
		val fixture = Task398RegisteredRootsFixture(activityBacked = true)
		val shadow = Task398ForegroundRegistrationWebViewShadow
		try {
			shadow.clear(); fixture.bindSession(); fixture.attach()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			val engine = task398OriginalEngine(fixture.activity.get()); outgoing.addView(engine)
			val exactListener = shadow.listeners.single()
			assertTrue(engine.isAttachedToWindow)
			val incoming = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, incoming)
			assertFalse(engine.isAttachedToWindow); assertNull(outgoing.parent)
			assertTrue(incoming.isAttachedToWindow); assertEquals(0, incoming.childCount)
			assertEquals(0, shadow.listeners.size, "T398_VR_EMPTY_EXACT_LISTENER_RETIREMENT")
			assertFalse(exactListener in shadow.listeners)
			assertEquals(1, shadow.removes, "T398_VR_EMPTY_EXACT_UNREGISTER_COUNT")
			assertEquals(1, shadow.adds)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(ReaderPortCommandResult.Accepted,
				fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			exactListener.onViewAttachedToWindow(engine)
			assertEquals(1, shadow.adds, "T398_VR_EMPTY_NO_PERMANENT_ROOT_RESTART")
			assertTrue(shadow.listeners.isEmpty())
		} finally { shadow.clear(); fixture.finish() }
	}

	private inner class Task398ReplacementDrainObservation(val fixture: Task398RegisteredRootsFixture) {
		val rows = mutableListOf<ReaderFrozenLegacyResource>()
		val results = mutableListOf<ReaderPortCommandResult>()
		private val requested = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		fun observe() {
			// Never assert from a production observer/recipient catch scope. Only collect
			// exact actual rows and results; all decisive assertions follow original unwind.
			repeat(8) {
				val discovered = fixture.delivery.snapshotFrozenOwnership().filter {
					it.physicalIdentity !in requested
				}
				if (discovered.isEmpty()) return
				discovered.forEach { row ->
					rows += row
					fixture.seenIdentities += row.physicalIdentity
					requested += row.physicalIdentity
					results += fixture.delivery.drainFrozenOwnership(row.physicalIdentity, fixture.confirmations::add)
				}
			}
		}
	}

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingCancellationExceptionLegacy() = runTest { task398ShieldExceptionBoundary("legacy", false) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingCancellationExceptionDefault() = runTest { task398ShieldExceptionBoundary("default", false) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingCancellationExceptionActivated() = runTest { task398ShieldExceptionBoundary("activated", false) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingCancellationExceptionReleaseOnly() = runTest { task398ShieldExceptionBoundary("release-only", false) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingCancellationExceptionFreeze() = runTest { task398ShieldExceptionBoundary("freeze", false) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingCancellationExceptionFreezeRestore() = runTest { task398ShieldExceptionBoundary("freeze-restore", false) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeCancellationExceptionLegacy() = runTest { task398ShieldExceptionBoundary("legacy", true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeCancellationExceptionDefault() = runTest { task398ShieldExceptionBoundary("default", true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeCancellationExceptionActivated() = runTest { task398ShieldExceptionBoundary("activated", true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeCancellationExceptionReleaseOnly() = runTest { task398ShieldExceptionBoundary("release-only", true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeCancellationExceptionFreeze() = runTest { task398ShieldExceptionBoundary("freeze", true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeCancellationExceptionFreezeRestore() = runTest { task398ShieldExceptionBoundary("freeze-restore", true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingExceptionPreservesPublicReentrantSuccessorLegacy() = runTest { task398ShieldExceptionBoundary("legacy", false, successor = true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingExceptionPreservesPublicReentrantSuccessorActivated() = runTest { task398ShieldExceptionBoundary("activated", false, successor = true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeExceptionPreservesPublicReentrantSuccessorLegacy() = runTest { task398ShieldExceptionBoundary("legacy", true, successor = true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1FadeExceptionPreservesPublicReentrantSuccessorActivated() = runTest { task398ShieldExceptionBoundary("activated", true, successor = true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingAndFadeExceptionsPreserveFirstFailureAndBothCleanupLegacy() = runTest { task398ShieldExceptionBoundary("legacy", false, bothThrow = true) }

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398Q1PendingAndFadeExceptionsPreserveFirstFailureAndBothCleanupActivated() = runTest { task398ShieldExceptionBoundary("activated", false, bothThrow = true) }

	private suspend fun task398ShieldExceptionBoundary(
		mode: String, fadeThrows: Boolean, successor: Boolean = false, bothThrow: Boolean = false
	) {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		val shield = fixture.curl.javaClass.task7Field("inlineRasterShield").get(fixture.curl) as ReaderPageInlineRasterShield
		val sentinel = IllegalStateException("Synthetic accepted cancellation recipient failure")
		val laterSentinel = IllegalArgumentException("Synthetic later accepted fade recipient failure")
		try {
			fixture.bindSession(); fixture.attach(); fixture.activity.visible()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			val engine = task398OriginalEngine(fixture.activity.get())
			outgoing.addView(engine)
			fixture.viewer.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
			fixture.viewer.layout(0, 0, 800, 600)
			val host = shield.javaClass.task7Field("host").get(shield) as android.view.ViewGroup
			assertTrue(engine.isAttachedToWindow); assertTrue(shield.view.isAttachedToWindow)
			assertTrue(host.width >= 8 && host.height >= 8)
			fun snapshot(): ReaderPageSlideSnapshot {
				val location = IntArray(2); host.getLocationInWindow(location)
				return ReaderPageSlideSnapshot(
					ReaderPageSlideSnapshotKey(2, ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
						ReaderPageBitmapQuality.Balanced, 8, 8, 8, 8),
					Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
					android.graphics.Rect(location[0], location[1], location[0] + 8, location[1] + 8),
					paige.navic.reader.ReaderPageTurnLeafGeometry(
						paige.navic.reader.ReaderPageTurnPixelRect(0, 0, 8, 8),
						paige.navic.reader.ReaderPageTurnPixelRect(0, 0, 3, 8),
						paige.navic.reader.ReaderPageTurnPixelRect(3, 0, 5, 8),
						paige.navic.reader.ReaderPageTurnPixelRect(5, 0, 8, 8)), 0xffead9ae.toInt()
				).also { it.retain(); it.releaseCacheOwnership() }
			}
			fun overlayReceipt() = ReaderWhispersyncAnchorReceipt(
				foliateSessionId = "synthetic-q1", destinationCommitToken = "synthetic-q1",
				visualPageOrdinal = 2, spineIndex = 1, rasterGeneration = 3L, textureGeneration = 4L,
				presentationMutationGeneration = 5L, presentationSequence = 6L, anchorGeneration = 7L,
				boundarySequence = 8L, layoutGeneration = 9L, viewGeneration = 10L, commitSequence = 11L,
				committedSpineIndex = 1, committedChapterPageIndex = 0, committedChapterPageCount = 2,
				paginationFingerprint = "synthetic", layoutFingerprint = "synthetic", readerSettingsRasterKey = "synthetic",
				captureGeometry = paige.navic.reader.ReaderPageTurnCaptureGeometry(8.0, 8.0,
					paige.navic.reader.ReaderPageTurnLayoutMode.Single,
					listOf(paige.navic.reader.ReaderPageTurnPageRect(paige.navic.reader.ReaderPageTurnPageRole.Full, 0.0, 0.0, 8.0, 8.0))),
				pageLocalRects = listOf(paige.navic.reader.ReaderWhispersyncPageLocalRect(
					paige.navic.reader.ReaderPageTurnPageRole.Full, 2.0, 2.0, 3.0, 2.0)))
			fun retains(value: ReaderPageSlideSnapshot) = value.javaClass.task7Field("retainCount").getInt(value)
			fun animationWork(animator: android.view.ViewPropertyAnimator): Int =
				(animator.javaClass.task7Field("mPendingAnimations").get(animator) as List<*>).size +
					(animator.javaClass.task7Field("mAnimatorMap").get(animator) as Map<*, *>).size
			fun shieldField(name: String) = shield.javaClass.task7Field(name).get(shield)
			val predecessor = snapshot()
			val presentationResults = mutableListOf<Boolean>()
			val fadeResults = mutableListOf<Boolean>()
			val successorResults = mutableListOf<Boolean>()
			var successorSnapshot: ReaderPageSlideSnapshot? = null
			var predecessorOverlay: Bitmap? = null
			var successorOverlay: Bitmap? = null
			var successorRequest: Any? = null
			var successorFirstFrame: Any? = null
			var successorTimeout: Any? = null
			var boundaryReached = false
			var overlayAccepted = false
			var successorOverlayAccepted = false
			var freezeResult: ReaderPortCommandResult? = null
			var restoreResult: ReaderPortCommandResult? = null
			var nativeReturnsInside = emptyList<ReaderFrozenLegacyResource>()
			var releaseOnly = false
			fun beforeThrow() {
				boundaryReached = true
				if (successor) {
					successorSnapshot = snapshot().also { shield.present(it, successorResults::add) }
					successorOverlayAccepted = shield.setWhispersyncOverlay(overlayReceipt(), 0x88ffcc00.toInt())
					successorOverlay = shieldField("ownedWhispersyncOverlay") as Bitmap?
					shield.view.alpha = 0.71f
					successorRequest = shieldField("activeRequest")
					successorFirstFrame = shieldField("firstFrame")
					successorTimeout = shieldField("timeout")
				}
				when (mode) {
					"activated", "release-only" -> {
						val decision = task398InstallFacts(store) { ReaderPortCommandResult.Accepted }
						if (mode == "release-only") releaseOnly = ReaderActivatedSessionInstallationBarrier(store)
							.failClosedAfterFinalizationRejection(decision)
					}
					"freeze", "freeze-restore" -> {
						freezeResult = fixture.delivery.freezeForTransitionActivation(fixture.domain)
						nativeReturnsInside = fixture.delivery.snapshotFrozenOwnership()
						if (mode == "freeze-restore") restoreResult = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
					}
				}
				if (!successor) shield.view.alpha = 0.37f
			}
			var shieldArmed = false
			var preparedPending = -1
			var preparedAnimationWork = -1
			var animator: android.view.ViewPropertyAnimator? = null
			val retiredCallbacks = mutableListOf<Runnable>()
			val protectedField = fixture.curl.javaClass.task7Field("onProtectedRasterSourcePageIndicesChanged")
			@Suppress("UNCHECKED_CAST") val originalProtected = protectedField.get(fixture.curl) as (Set<Int>) -> Unit
			protectedField.set(fixture.curl, { indices: Set<Int> ->
				originalProtected(indices)
				if (!shieldArmed) {
					shieldArmed = true
					// Public operations arm the real shield only on the explicitly owned invalidate,
					// after preceding Native lifecycle/cancelGesture dispatch has already returned.
					shield.present(predecessor) { presented ->
						presentationResults += presented
						if (!presented && !fadeThrows) { beforeThrow(); throw sentinel }
					}
					listOf("firstFrame", "committedFrame", "timeout").mapNotNullTo(retiredCallbacks) { shieldField(it) as Runnable? }
					overlayAccepted = shield.setWhispersyncOverlay(overlayReceipt(), 0x88ffcc00.toInt())
					predecessorOverlay = shieldField("ownedWhispersyncOverlay") as Bitmap?
					shield.fadeOut(10_000L) { committed ->
						fadeResults += committed
						if (!committed && fadeThrows) { beforeThrow(); throw sentinel }
						if (!committed && bothThrow) throw laterSentinel
					}
					listOf("firstFrame", "committedFrame", "timeout").mapNotNullTo(retiredCallbacks) { shieldField(it) as Runnable? }
					animator = shield.view.animate()
					preparedAnimationWork = animationWork(requireNotNull(animator))
					preparedPending = shield.pendingCallbackCount()
					shield.view.alpha = 0.37f
				}
			})
			var escaped: Throwable? = null
			try {
				if (mode == "default") fixture.curl.invalidate("task398-q1-default")
				else replacement.invoke(fixture.viewer, FrameLayout(fixture.activity.get()))
			} catch (failure: Throwable) {
				// Catch only at the external test boundary, never inside any original recipient.
				escaped = if (failure is java.lang.reflect.InvocationTargetException) failure.targetException else failure
			}
			assertTrue(shieldArmed, "Original explicitly owned invalidate must arm public shield operations")
			assertEquals(2, preparedPending); assertTrue(preparedAnimationWork > 0)
			assertTrue(overlayAccepted, "Actual public overlay path must own a typed mask")
			assertTrue(boundaryReached, "Original accepted public false recipient must actually execute")
			assertSame(sentinel, escaped, "Original callback failure must remain externally observable")
			assertEquals(listOf(false), presentationResults)
			// Q1 RED must be this post-unwind ownership assertion, not an intentional thrown test error.
			assertEquals(0, retains(predecessor), "Q1 post-unwind captured predecessor retain must be released exactly once")
			assertTrue(predecessor.bitmap.isRecycled, "Q1 cache ownership was already released before public presentation")
			assertTrue(requireNotNull(predecessorOverlay).isRecycled, "Q1 captured public overlay must be recycled after unwinding")
			assertEquals(listOf(false), fadeResults, "Later actual fade cancellation must still execute after pending recipient failure")
			assertNull(shieldField("activeFadeRequest")); assertNull(shieldField("fadeCallback"))
			assertEquals(0, animationWork(requireNotNull(animator)), "Captured old animator work must retire after failure")
			if (bothThrow) assertTrue(requireNotNull(escaped).suppressed.any { it === laterSentinel },
				"Later cleanup recipient failure must be retained without replacing the first failure")
			if (mode.startsWith("freeze")) {
				assertEquals(ReaderPortCommandResult.Accepted, freezeResult)
				assertTrue(nativeReturnsInside.any { it.state == ReaderLegacyResourceState.Running },
                    "T398_VR_COMPAT_SHIELD_EXCEPTION_NATIVE_RETURN")
                if (mode == "freeze-restore") {
                    assertEquals(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource,
                        assertIs<ReaderPortCommandResult.Rejected>(restoreResult).reason)
                    assertEquals(ReaderPortCommandResult.Accepted,
                        fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
                    assertNull(outgoing.parent, "Restoration cannot resurrect the retired Native predecessor")
                    assertFalse(engine.isAttachedToWindow)
                }
			}
			if (mode == "release-only") { assertTrue(releaseOnly); assertNotNull(store.snapshot) }
			if (mode == "activated") assertNotNull(store.snapshot)
			val rasterView = shieldField("rasterView") as android.widget.ImageView
			val overlayView = shieldField("overlayView") as android.widget.ImageView
			if (successor) {
				val retained = requireNotNull(successorSnapshot)
				assertSame(retained, shieldField("ownedSnapshot")); assertEquals(1, retains(retained))
				assertFalse(retained.bitmap.isRecycled); assertTrue(successorOverlayAccepted)
				assertSame(successorOverlay, shieldField("ownedWhispersyncOverlay")); assertFalse(requireNotNull(successorOverlay).isRecycled)
				assertEquals(successorRequest, shieldField("activeRequest")); assertNotNull(successorRequest)
				assertSame(successorFirstFrame, shieldField("firstFrame")); assertSame(successorTimeout, shieldField("timeout"))
				assertEquals(1, shield.pendingCallbackCount()); assertTrue(successorResults.isEmpty())
				assertSame(retained.bitmap, (rasterView.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
				assertSame(successorOverlay, (overlayView.drawable as android.graphics.drawable.BitmapDrawable).bitmap)
				assertEquals(0.71f, shield.view.alpha); assertEquals(View.VISIBLE, shield.view.visibility)
				assertEquals(View.VISIBLE, overlayView.visibility)
				// Execute captured real stale callbacks: their existing request guards must not touch the successor.
				retiredCallbacks.forEach(Runnable::run)
				assertSame(retained, shieldField("ownedSnapshot")); assertEquals(1, retains(retained))
				assertEquals(successorRequest, shieldField("activeRequest")); assertEquals(1, shield.pendingCallbackCount())
				assertTrue(successorResults.isEmpty()); assertEquals(0.71f, shield.view.alpha)
			} else {
				assertEquals(0, shield.pendingCallbackCount()); assertNull(shieldField("ownedSnapshot"))
				assertNull(shieldField("presentationCallback")); assertNull(shieldField("firstFrame"))
				assertNull(shieldField("committedFrame")); assertNull(shieldField("timeout"))
				assertNull(rasterView.drawable); assertNull(overlayView.drawable)
				if (mode in listOf("legacy", "default")) {
					assertEquals(1f, shield.view.alpha); assertEquals(View.GONE, shield.view.visibility)
					assertEquals(View.GONE, overlayView.visibility); assertNull(store.snapshot)
				} else {
					assertEquals(0.37f, shield.view.alpha); assertEquals(View.VISIBLE, shield.view.visibility)
					assertEquals(View.VISIBLE, overlayView.visibility)
				}
				retiredCallbacks.forEach(Runnable::run)
				assertEquals(0, shield.pendingCallbackCount())
			}
			// Public retry may deliberately retire a current successor, but cannot double-dispose the predecessor.
			shield.dismiss(); shield.dismiss()
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(12))
			assertEquals(0, retains(predecessor)); assertTrue(predecessor.bitmap.isRecycled)
			assertEquals(listOf(false), presentationResults); assertEquals(listOf(false), fadeResults)
			assertEquals(0, shield.pendingCallbackCount())
			if (successor) {
				assertEquals(0, retains(requireNotNull(successorSnapshot))); assertTrue(requireNotNull(successorSnapshot).bitmap.isRecycled)
				assertTrue(requireNotNull(successorOverlay).isRecycled); assertEquals(listOf(false), successorResults)
			}
		} finally {
			try {
				// External teardown boundary only: preserve any earlier post-unwind assertion failure.
				try { shield.dismiss() } catch (failure: Throwable) {
					if (failure !== sentinel && failure !== laterSentinel) throw failure
				}
			} finally { fixture.finish() }
		}
	}

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398OriginalReplacementPendingShieldRecipientDeniesVisualTailAndReleasesExactlyOnce() = runTest {
		for (mode in listOf("legacy", "default", "activated", "release-only", "freeze", "freeze-restore")) {
			task398ShieldDisposalBoundary(mode, fade = false, successor = false)
		}
	}

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398OriginalReplacementFadeCancellationRecipientDeniesResetButRetiresActualFade() = runTest {
		for (mode in listOf("legacy", "default", "activated", "release-only", "freeze-restore")) {
			task398ShieldDisposalBoundary(mode, fade = true, successor = false)
		}
	}

	@Test
	@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398OriginalReplacementShieldCancellationPreservesReentrantSuccessorResourceAndPresentation() = runTest {
		task398ShieldDisposalBoundary("activated", fade = false, successor = true)
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398OriginalReplacementProtectedAndReadinessRecipientsFenceNextSurfaceAndProfileWithControls() = runTest {
		for (boundary in listOf("protected", "readiness")) for (mode in listOf("legacy", "default", "activated", "release-only", "freeze-restore")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
			try {
				fixture.bindSession(); fixture.attach()
				val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
				val outgoing = FrameLayout(fixture.activity.get())
				replacement.invoke(fixture.viewer, outgoing)
				val curl = fixture.curl; val type = curl.javaClass
				// Arrange a real readiness transition through the original helper, not a replacement producer.
				type.task7Method("updateReadiness", ReaderTextureDeckState::class.java,
					ReaderTextureDeckState::class.java, ReaderPageInteractionState::class.java, String::class.java)
					.invoke(curl, ReaderTextureDeckState.Empty, ReaderTextureDeckState.Empty, ReaderPageInteractionState.Ready, "task398-test-arrangement")
				curl.surfaceView.alpha = 0.61f
				val protectedField = type.task7Field("onProtectedRasterSourcePageIndicesChanged")
				val readinessField = type.task7Field("onReadinessStateChange")
				val profileField = type.task7Field("onRasterProfileEpochChanged")
				@Suppress("UNCHECKED_CAST") val originalProtected = protectedField.get(curl) as (Set<Int>) -> Unit
				@Suppress("UNCHECKED_CAST") val originalReadiness = readinessField.get(curl) as (ReaderPageRendererReadinessState) -> Unit
				@Suppress("UNCHECKED_CAST") val originalProfile = profileField.get(curl) as (Long?) -> Unit
				var reached = false; var denied = false; var readinessAfterDenial = 0; var profileAfterDenial = 0
				var readinessCalls = 0; var profileCalls = 0
				var helperReturnAlpha: Float? = null
                var observedBoundary: String? = null
                var frozenInside: ReaderPortCommandResult? = null
                var restoredInside: ReaderPortCommandResult? = null
                var nativeReturnsInside = emptyList<ReaderFrozenLegacyResource>()
				var helperReturnReadinessAfterDenial = -1
				var helperReturnProfileAfterDenial = -1
				val preparationField = fixture.raster.javaClass.task7Field("onPreparationStateChange")
				@Suppress("UNCHECKED_CAST") val originalPreparation = preparationField.get(fixture.raster) as (ReaderPagePreparationState) -> Unit
				preparationField.set(fixture.raster, { state: ReaderPagePreparationState ->
					// This next original Native recipient starts after the owned Curl helper returns.
					// Delegate it normally; later default Curl cleanup remains a named OPEN Native tail.
					if (denied && helperReturnAlpha == null) {
						observedBoundary = "actual-next-Native-recipient"
						helperReturnAlpha = curl.surfaceView.alpha
						helperReturnReadinessAfterDenial = readinessAfterDenial
						helperReturnProfileAfterDenial = profileAfterDenial
					}
					originalPreparation(state)
				})
				fun cross() {
					if (reached) return
					reached = true
					curl.surfaceView.alpha = 0.61f
					when (mode) {
						"activated", "release-only" -> {
							val decision = task398InstallFacts(store) { ReaderPortCommandResult.Accepted }
							if (mode == "release-only") check(ReaderActivatedSessionInstallationBarrier(store).failClosedAfterFinalizationRejection(decision))
							denied = true
						}
						"freeze-restore" -> {
							frozenInside = fixture.delivery.freezeForTransitionActivation(fixture.domain)
                            nativeReturnsInside = fixture.delivery.snapshotFrozenOwnership()
                            restoredInside = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
							denied = true
						}
					}
				}
				protectedField.set(curl, { indices: Set<Int> -> originalProtected(indices); if (boundary == "protected") cross() })
				readinessField.set(curl, { state: ReaderPageRendererReadinessState ->
					readinessCalls++; if (denied) readinessAfterDenial++
					originalReadiness(state); if (boundary == "readiness") cross()
				})
				profileField.set(curl, { epoch: Long? -> profileCalls++; if (denied) profileAfterDenial++; originalProfile(epoch) })
				if (mode == "default") curl.invalidate("task398-readiness-default")
				else replacement.invoke(fixture.viewer, FrameLayout(fixture.activity.get()))
				// A denied next Native consequence cannot serve as an observation hook.
                // Sample the genuine external original replacement unwind, without invoking a recipient.
                if (denied && helperReturnAlpha == null) {
                    observedBoundary = "external-original-Native-unwind"
                    helperReturnAlpha = curl.surfaceView.alpha
                    helperReturnReadinessAfterDenial = readinessAfterDenial
                    helperReturnProfileAfterDenial = profileAfterDenial
                }
                assertTrue(reached, "The original protected/readiness recipient executes before denial")
                if (mode == "freeze-restore") {
                    assertEquals(ReaderPortCommandResult.Accepted, frozenInside)
                    assertTrue(nativeReturnsInside.any { it.state == ReaderLegacyResourceState.Running },
                        "T398_VR_COMPAT_RECIPIENT_NATIVE_RETURN")
                    assertEquals(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource,
                        assertIs<ReaderPortCommandResult.Rejected>(restoredInside).reason)
                    assertEquals(ReaderPortCommandResult.Accepted,
                        fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
                    assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
					assertNull(outgoing.parent, "Restoration cannot resurrect the retired Native predecessor")
                }
				if (mode in listOf("legacy", "default")) {
					assertEquals(1, readinessCalls); assertEquals(1, profileCalls); assertEquals(0f, curl.surfaceView.alpha)
				} else {
					assertTrue(denied); assertNotNull(observedBoundary, "Precisely labeled authentic recipient or external Native unwind observation")
					assertNotNull(helperReturnAlpha, "Authentic observed boundary follows the original helper return")
					assertEquals(0, helperReturnReadinessAfterDenial, "Next original readiness recipient is denied")
					assertEquals(0, helperReturnProfileAfterDenial, "Next original profile recipient is denied")
					assertEquals(0.61f, helperReturnAlpha, "Original hideCurlSurface alpha primitive is denied after recipient return mode=$mode boundary=$boundary")
				}
			} finally { fixture.finish() }
		}
	}

	private suspend fun task398ShieldDisposalBoundary(mode: String, fade: Boolean, successor: Boolean) {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		val shield = fixture.curl.javaClass.task7Field("inlineRasterShield").get(fixture.curl) as ReaderPageInlineRasterShield
		try {
			fixture.bindSession(); fixture.attach(); fixture.activity.visible()
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			val outgoing = FrameLayout(fixture.activity.get())
			replacement.invoke(fixture.viewer, outgoing)
			outgoing.addView(task398OriginalEngine(fixture.activity.get()))
			fixture.viewer.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
			fixture.viewer.layout(0, 0, 800, 600)
			val host = shield.javaClass.task7Field("host").get(shield) as android.view.ViewGroup
			assertTrue(host.isAttachedToWindow); assertTrue(shield.view.isAttachedToWindow)
			assertTrue(host.width >= 8 && host.height >= 8)
			fun snapshot(): ReaderPageSlideSnapshot {
				val location = IntArray(2); host.getLocationInWindow(location)
				return ReaderPageSlideSnapshot(
					ReaderPageSlideSnapshotKey(2, ReaderPageTurnTransitionKind.LandscapeSpreadSlide,
						ReaderPageBitmapQuality.Balanced, 8, 8, 8, 8),
					Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888),
					android.graphics.Rect(location[0], location[1], location[0] + 8, location[1] + 8),
					paige.navic.reader.ReaderPageTurnLeafGeometry(
						paige.navic.reader.ReaderPageTurnPixelRect(0, 0, 8, 8),
						paige.navic.reader.ReaderPageTurnPixelRect(0, 0, 3, 8),
						paige.navic.reader.ReaderPageTurnPixelRect(3, 0, 5, 8),
						paige.navic.reader.ReaderPageTurnPixelRect(5, 0, 8, 8)), 0xffead9ae.toInt()
				).also { it.retain(); it.releaseCacheOwnership() }
			}
			val predecessor = snapshot()
			var successorSnapshot: ReaderPageSlideSnapshot? = null
			var disposalObserved = false
			var disposedAlpha = -1f
			var disposedVisibility = -1
			var disposedSnapshot: ReaderPageSlideSnapshot? = null
			var disposedRetains = -1
			var disposedPending = -1
			var disposedDrawableCleared = false
			var disposedSnapshotRecycled = false
			var originalFadeAnimator: android.view.ViewPropertyAnimator? = null
			var disposedAnimationWork = -1
			fun animationWork(animator: android.view.ViewPropertyAnimator): Int =
				(animator.javaClass.task7Field("mPendingAnimations").get(animator) as List<*>).size +
					(animator.javaClass.task7Field("mAnimatorMap").get(animator) as Map<*, *>).size
			val ownershipField = shield.javaClass.task7Field("onOwnershipMutated")
			@Suppress("UNCHECKED_CAST") val originalOwnership = ownershipField.get(shield) as () -> Unit
			ownershipField.set(shield, {
				originalOwnership()
				if (!disposalObserved && predecessor.javaClass.task7Field("retainCount").getInt(predecessor) == 0) {
					disposalObserved = true
					disposedAlpha = shield.view.alpha; disposedVisibility = shield.view.visibility
					disposedSnapshot = shield.javaClass.task7Field("ownedSnapshot").get(shield) as ReaderPageSlideSnapshot?
					disposedRetains = disposedSnapshot?.let { it.javaClass.task7Field("retainCount").getInt(it) } ?: 0
					disposedPending = shield.pendingCallbackCount()
					disposedSnapshotRecycled = disposedSnapshot?.bitmap?.isRecycled ?: false
					disposedAnimationWork = originalFadeAnimator?.let(::animationWork) ?: 0
					disposedDrawableCleared = (shield.javaClass.task7Field("rasterView").get(shield) as android.widget.ImageView).drawable == null
				}
			})
			val presentationResults = mutableListOf<Boolean>()
			val fadeResults = mutableListOf<Boolean>()
			val successorResults = mutableListOf<Boolean>()
			var boundaryReached = false
			var freezeResult: ReaderPortCommandResult? = null
			var restoreResult: ReaderPortCommandResult? = null
			var nativeReturnsInside = emptyList<ReaderFrozenLegacyResource>()
			var releaseOnly = false
			fun boundary() {
				if (boundaryReached) return
				boundaryReached = true
				if (successor) {
					successorSnapshot = snapshot().also { shield.present(it, successorResults::add) }
					shield.view.alpha = 0.71f
				}
				when (mode) {
					"activated", "release-only" -> {
						val decision = task398InstallFacts(store) { ReaderPortCommandResult.Accepted }
						if (mode == "release-only") releaseOnly = ReaderActivatedSessionInstallationBarrier(store)
							.failClosedAfterFinalizationRejection(decision)
					}
					"freeze", "freeze-restore" -> {
						freezeResult = fixture.delivery.freezeForTransitionActivation(fixture.domain)
						nativeReturnsInside = fixture.delivery.snapshotFrozenOwnership()
						if (mode == "freeze-restore") restoreResult = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
					}
				}
				if (!successor) shield.view.alpha = 0.37f
			}
			var shieldArmed = false
			var preparedPending = -1
			var preparedVisibility = -1
			var preparedAnimationWork = -1
			fun prepareShield() {
				shield.present(predecessor) { presented ->
					presentationResults += presented
					if (!presented && !fade) boundary()
				}
				if (fade) {
					shield.fadeOut(10_000L) { committed ->
						fadeResults += committed
						if (!committed) boundary()
					}
					originalFadeAnimator = shield.view.animate()
					preparedAnimationWork = animationWork(requireNotNull(originalFadeAnimator))
				}
				shield.view.alpha = 0.37f
				preparedVisibility = shield.view.visibility
				preparedPending = shield.pendingCallbackCount()
			}
			val protectedField = fixture.curl.javaClass.task7Field("onProtectedRasterSourcePageIndicesChanged")
			@Suppress("UNCHECKED_CAST") val originalProtected = protectedField.get(fixture.curl) as (Set<Int>) -> Unit
			protectedField.set(fixture.curl, { indices: Set<Int> ->
				originalProtected(indices)
				if (!shieldArmed) { shieldArmed = true; prepareShield() }
			})
			val incoming = FrameLayout(fixture.activity.get())
			if (mode == "default") fixture.curl.invalidate("task398-shield-unrelated-default")
			else replacement.invoke(fixture.viewer, incoming)
			// Original framework calls and original recipients execute; all assertions are outside their catches.
			assertTrue(shieldArmed, "Original explicit invalidate protected recipient arms shield after earlier Native lifecycle work")
			assertEquals(if (fade) 2 else 1, preparedPending, "Original public shield operations schedule actual pending work")
			assertEquals(View.VISIBLE, preparedVisibility)
			if (fade) assertTrue(preparedAnimationWork > 0, "Original public fade schedules actual animator work")
			assertTrue(boundaryReached, "The original shield cancellation recipient must actually execute")
			assertEquals(listOf(false), presentationResults)
			if (fade) {
				assertEquals(listOf(false), fadeResults)
				assertNull(shield.javaClass.task7Field("activeFadeRequest").get(shield))
				assertEquals(0, disposedAnimationWork, "Original animation cancellation clears captured pending/running work")
			}
			assertTrue(disposalObserved, "Observe original captured-release ownership recipient, before unowned Native replacement tails")
			assertEquals(0, predecessor.javaClass.task7Field("retainCount").getInt(predecessor))
			assertTrue(predecessor.bitmap.isRecycled, "Only captured obsolete ownership is released")
			if (mode.startsWith("freeze")) {
				assertEquals(ReaderPortCommandResult.Accepted, freezeResult)
				assertTrue(nativeReturnsInside.any { it.state == ReaderLegacyResourceState.Running },
                    "T398_VR_COMPAT_SHIELD_DISPOSAL_NATIVE_RETURN")
                if (mode == "freeze-restore") {
                    assertEquals(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource,
                        assertIs<ReaderPortCommandResult.Rejected>(restoreResult).reason)
                    assertEquals(ReaderPortCommandResult.Accepted,
                        fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
                    assertNull(outgoing.parent, "Restoration cannot resurrect the retired Native predecessor")
                }
			}
			if (mode == "release-only") { assertTrue(releaseOnly); assertNotNull(store.snapshot) }
			if (successor) {
				val retained = requireNotNull(successorSnapshot)
				assertSame(retained, disposedSnapshot, "Predecessor disposal must not overwrite successor identity")
				assertEquals(1, disposedRetains, "Predecessor cleanup retains successor ownership")
				assertFalse(disposedSnapshotRecycled, "Predecessor cleanup cannot recycle the successor bitmap")
				assertEquals(1, disposedPending, "Predecessor cancellation must not retire successor pending work")
				assertEquals(0.71f, disposedAlpha); assertEquals(View.VISIBLE, disposedVisibility)
			} else {
				assertEquals(0, disposedPending)
				assertNull(disposedSnapshot)
				assertTrue(disposedDrawableCleared, "Sever the obsolete bitmap borrowing reference before recycling")
			}
			if (mode !in listOf("legacy", "default")) {
				if (!successor) {
					assertEquals(0.37f, disposedAlpha, "Original helper next alpha/reset primitive is denied mode=$mode")
					assertEquals(View.VISIBLE, disposedVisibility, "Original helper next visibility primitive is denied mode=$mode")
				}
			} else {
				assertNull(store.snapshot); assertEquals(1f, disposedAlpha); assertEquals(View.GONE, disposedVisibility)
			}
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
			assertEquals(listOf(false), presentationResults, "Canceled callbacks cannot resurrect")
			if (fade) assertEquals(listOf(false), fadeResults)
			assertEquals(0, predecessor.javaClass.task7Field("retainCount").getInt(predecessor), "Captured release occurs exactly once")
		} finally {; shield.dismiss(); fixture.finish()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualReplacementInvalidationRecipientInstallMustFenceHelperInternalLegacyTail() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val outgoing = FrameLayout(fixture.activity.get())
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			replacement.invoke(fixture.viewer, outgoing)
			val oldEngine = task398OriginalEngine(fixture.activity.get())
			outgoing.addView(oldEngine)
			assertTrue(oldEngine.isAttachedToWindow); assertTrue(fixture.viewer.isAttachedToWindow)
			assertFalse(fixture.viewerClass.task7Field("pageTurnCanvasEnabled").getBoolean(fixture.viewer))
			assertNull(fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(fixture.viewer))
			assertNull(store.snapshot)
			val incoming = FrameLayout(fixture.activity.get())
			val newEngine = task398OriginalEngine(fixture.activity.get())
			incoming.addView(newEngine)
			assertFalse(newEngine.isAttachedToWindow)
			val protectedField = fixture.curl.javaClass.task7Field("onProtectedRasterSourcePageIndicesChanged")
			val profileField = fixture.curl.javaClass.task7Field("onRasterProfileEpochChanged")
			@Suppress("UNCHECKED_CAST")
			val protectedRecipient = protectedField.get(fixture.curl) as (Set<Int>) -> Unit
			@Suppress("UNCHECKED_CAST")
			val profileRecipient = profileField.get(fixture.curl) as (Long?) -> Unit
			var installs = 0
			var profileCallsAfterInstall = 0
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			protectedField.set(fixture.curl, { indices: Set<Int> ->
				protectedRecipient(indices)
				if (store.snapshot == null) {
					installs++
					task398InstallFacts(store) { facts += it; ReaderPortCommandResult.Accepted }
				}
			})
			profileField.set(fixture.curl, { epoch: Long? ->
				if (store.snapshot != null) profileCallsAfterInstall++
				profileRecipient(epoch)
			})
			replacement.invoke(fixture.viewer, incoming)
			// Assertions are outside controller observer catches and fact-port catches.
			assertEquals(1, installs, "The real replacement must install from the original invalidation recipient")
			assertNotNull(store.snapshot)
			assertFalse(oldEngine.isAttachedToWindow); assertTrue(fixture.viewer.isAttachedToWindow)
			assertEquals(listOf(ReaderTask6LifecycleSafetyKind.WebViewDetached), facts.map { it.kind })
			assertEquals(0, profileCallsAfterInstall,
				"A caller-only replacement fence cannot stop the next legacy profile primitive inside invalidate")
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class])
	fun task398ActualReleaseOnlyCloseRetriesFailedExactForegroundRemovalWithoutRestart() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		val shadow = Task398ForegroundRegistrationWebViewShadow
		try {
			shadow.clear(); fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			val engine = task398OriginalEngine(fixture.activity.get())
			wrapper.addView(engine)
			val exactListener = shadow.listeners.single()
			val decision = task398InstallFacts(store) { error("ReleaseOnly cannot deliver ordinary safety facts") }
			assertTrue(ReaderActivatedSessionInstallationBarrier(store).failClosedAfterFinalizationRejection(decision))
			var attempted = false
			var inside = emptyList<ReaderFrozenLegacyResource>()
			shadow.beforeRemove = {
				if (!attempted) {
					attempted = true
					assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
					inside = fixture.delivery.snapshotFrozenOwnership()
				}
			}
			shadow.failRemoval = true
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertTrue(attempted, "Actual ReleaseOnly terminal close must attempt the exact framework removal")
			assertEquals(setOf(exactListener), shadow.listeners, "Uncertain removal retains the exact possible installation")
			assertEquals(3, inside.count { it.state == ReaderLegacyResourceState.Running },
				"Close, terminal handling and the invoked listener removal have distinct actual returns")
			assertTrue(fixture.viewerClass.task7Field("physicalPointerDeliveryClosed").getBoolean(fixture.viewer))
			assertTrue(fixture.viewerClass.task7Field("task4ResourceTeardownStarted").getBoolean(fixture.viewer),
				"Failed foreground removal must not suppress independent mandatory teardown")
			val retained = fixture.delivery.snapshotFrozenOwnership().filter { it.state == ReaderLegacyResourceState.Registered }
			assertEquals(2, retained.size, "The failed exact foreground root and still installed lifecycle root remain")
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertTrue(shadow.listeners.isEmpty(), "Already-final close must retry retained physical removal")
			assertEquals(1, shadow.removes); assertEquals(1, shadow.adds)
			assertEquals(1, fixture.delivery.snapshotFrozenOwnership().count { it.state == ReaderLegacyResourceState.Registered })
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			wrapper.removeView(engine); wrapper.addView(engine)
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertEquals(1, shadow.adds, "Permanent exact removal cannot be resurrected by restore or natural attachment")
			assertTrue(shadow.listeners.isEmpty())
		} finally { shadow.clear(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class])
	fun task398ActualCloseRecipientDrainAndReentrantRemovalRetainTerminalReturns() = runTest {
		for (mode in listOf("accepted", "rejected", "unknown")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
			val shadow = Task398ForegroundRegistrationWebViewShadow
			try {
				shadow.clear(); fixture.bindSession(); fixture.attach()
				val wrapper = FrameLayout(fixture.activity.get())
				fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
				val engine = task398OriginalEngine(fixture.activity.get())
				wrapper.addView(engine)
				val exactListener = shadow.listeners.single()
				var reentered = false
				var removalReentryReturned = false
				shadow.beforeRemove = {
					if (!reentered) {
						reentered = true
						fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
						assertEquals(setOf(exactListener), shadow.listeners,
							"Reentrant terminal handling cannot claim that the enclosing remove primitive returned")
						assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
						removalReentryReturned = true
					}
				}
				val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
				var inside = emptyList<ReaderFrozenLegacyResource>()
				var recipientReachedTail = false
				task398InstallFacts(store) { fact ->
					facts += fact
					assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
					inside = fixture.delivery.snapshotFrozenOwnership()
					fixture.drainRemaining()
					assertTrue(fixture.confirmations.isEmpty(), "Root/callback removal cannot confirm either enclosing terminal return")
					assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
					recipientReachedTail = true
					when (mode) {
						"unknown" -> error("Synthetic unknown terminal acceptance after physical drain")
						"rejected" -> ReaderPortCommandResult.Rejected(paige.navic.reader.ReaderTransitionFailureReason.PortRejected)
						else -> ReaderPortCommandResult.Accepted
					}
				}
				fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
				assertTrue(reentered, "The actual foreground unregister primitive must reenter the real terminal path")
				assertTrue(removalReentryReturned, "Removal must not swallow an assertion failure from its reentrant callback")
				assertTrue(recipientReachedTail, "Fact delivery must not swallow an assertion failure before its intentional outcome")
				assertEquals(4, inside.count { it.state == ReaderLegacyResourceState.Running },
					"Close and terminal handling join the unchanged ordered retry and fact-recipient owners")
				assertEquals(listOf(ReaderTask6LifecycleSafetyKind.PublicationClosed), facts.map { it.kind })
				assertTrue(shadow.listeners.isEmpty()); assertEquals(1, shadow.removes); assertEquals(1, shadow.adds)
				fixture.drainRemaining()
				assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
				assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
				assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			} finally { shadow.clear(); fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398ForegroundRegistrationWebViewShadow::class])
	fun task398ActualEngineRegistrationReentrantInstallationAndFrozenCancellationDoNotRestart() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		val shadow = Task398ForegroundRegistrationWebViewShadow
		try {
			shadow.clear(); fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			val engine = task398OriginalEngine(fixture.activity.get())
			var windows = 0
			engine.javaClass.task7Field("windowVisibilityListener").set(engine, { _: Int -> windows++ })
			var inside = emptyList<ReaderFrozenLegacyResource>()
			shadow.afterAdd = {
				val decision = task398InstallFacts(store) { error("ReleaseOnly cannot deliver ordinary safety facts") }
				assertTrue(ReaderActivatedSessionInstallationBarrier(store).failClosedAfterFinalizationRejection(decision))
				assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
				inside = fixture.delivery.snapshotFrozenOwnership()
				fixture.drainRemaining()
				assertTrue(fixture.confirmations.isEmpty(), "Invoked add and exact engine attachment source have not returned")
			}
			wrapper.addView(engine)
			assertEquals(1, shadow.adds, "The shadow must count the original framework registration primitive")
			assertEquals(1, inside.count { it.state == ReaderLegacyResourceState.Reserved })
			assertEquals(2, inside.count { it.state == ReaderLegacyResourceState.Running })
			engine.dispatchWindowVisibilityChanged(View.VISIBLE)
			assertEquals(0, windows)
			assertTrue(shadow.listeners.isEmpty()); assertTrue(engine.isAttachedToWindow)
			shadow.afterAdd = null
			wrapper.removeView(engine); wrapper.addView(engine)
			assertEquals(1, shadow.adds, "Natural reattachment cannot restart a root canceled during installed freeze")
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			assertEquals(2, shadow.adds, "Only explicit valid-root restoration reinstalls the exact safety listener")
			assertEquals(0, windows)
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertTrue(shadow.listeners.isEmpty(), "ReleaseOnly close still removes the actual restored safety listener")
		} finally { shadow.clear(); fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualNativeChildRemovalKeepsBothEnclosingReturnsUntilRemoveViewReturns() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			val engine = task398OriginalEngine(fixture.activity.get())
			wrapper.addView(engine)
			var rowsInside = emptyList<ReaderFrozenLegacyResource>()
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			task398InstallFacts(store) { fact ->
				facts += fact
				assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
				rowsInside = fixture.delivery.snapshotFrozenOwnership()
				fixture.drainRemaining()
				assertTrue(fixture.confirmations.isEmpty())
				assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
				ReaderPortCommandResult.Accepted
			}
			fixture.viewerClass.task7Method("detachViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			assertFalse(engine.isAttachedToWindow); assertTrue(fixture.viewer.isAttachedToWindow)
			assertEquals(listOf(ReaderTask6LifecycleSafetyKind.WebViewDetached), facts.map { it.kind })
			assertEquals(5, rowsInside.count { it.state == ReaderLegacyResourceState.Running },
				"Callback/retry/recipient plus Native method and actual removeView returns are distinct")
			assertTrue(rowsInside.all { it.physicalIdentity.domain == fixture.domain &&
				it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualDelayedForegroundEngineDetachIsFactOnlyAndRepeatable() = runTest {
		task398ForegroundEngineVariant("repeat")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualForegroundEngineRejectedFactRetriesSameObjectAheadOfLaterDetach() = runTest {
		task398ForegroundEngineVariant("reject")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualForegroundEngineUnknownAcceptanceCannotReplayOrOvertake() = runTest {
		task398ForegroundEngineVariant("throw")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualForegroundEngineCloseFirstCannotAppendOrResurrect() = runTest {
		task398ForegroundEngineVariant("close-first")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualForegroundEngineFreezeDrainInsideRecipientOwnsExactReturns() = runTest {
		task398ForegroundEngineVariant("freeze")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualForegroundEngineFirstAttachAfterInstalledFreezeLateJoins() = runTest {
		task398ForegroundEngineVariant("frozen-first")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualForegroundEngineReleaseOnlyFencesWindowListenerAndLegacyControlsRemain() = runTest {
		task398ForegroundEngineVariant("release-only")
		task398ForegroundEngineVariant("legacy")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementHelperActivatedProtectedRecipientDeniesNestedProfile() = runTest {
		task398ReplacementHelperBoundary("activated", "protected")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementHelperReleaseOnlyProtectedRecipientStillDrainsRealLiveClaim() = runTest {
		task398ReplacementHelperBoundary("release-only", "protected")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementHelperCapturedSourceFreezeCannotPublishProfile() = runTest {
		task398ReplacementHelperBoundary("freeze", "protected")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementHelperCapturedSourceFreezeRestoreCannotReadmitOldInvocation() = runTest {
		task398ReplacementHelperBoundary("freeze-restore", "protected")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReplacementHelperPreparedRecipientInstallDeniesNestedProfileTailWithLegacyControls() = runTest {
		task398ReplacementHelperBoundary("legacy", "prepared")
		task398ReplacementHelperBoundary("default", "prepared")
		task398ReplacementHelperBoundary("activated", "prepared")
	}

	private suspend fun task398ReplacementHelperBoundary(mode: String, boundary: String) {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val outgoing = FrameLayout(fixture.activity.get())
			val replacement = fixture.viewerClass.task7Method("replaceViewerContent", View::class.java)
			replacement.invoke(fixture.viewer, outgoing)
			val oldEngine = task398OriginalEngine(fixture.activity.get())
			outgoing.addView(oldEngine)
			val incoming = FrameLayout(fixture.activity.get())
			incoming.addView(task398OriginalEngine(fixture.activity.get()))
			assertTrue(oldEngine.isAttachedToWindow)
			val curl = fixture.curl
			val type = curl.javaClass
			val ownership = type.task7Field("foregroundWebViewOwnership").get(curl) as ReaderForegroundWebViewOwnership
			val dispatch = type.task7Field("relocationLiveDispatchCoordinator").get(curl) as ReaderPageRelocationLiveDispatchCoordinator
			val request = ReaderPageRelocationRequest(
				paige.navic.reader.ReaderPageRelocationToken("task398-helper-cleanup"), 701L,
				0L, 301L, 1, 0, ReaderPageTurnDirection.Previous, "task398-helper-session")
			// Real acquisition and transfer, not a fabricated physical resource row.
			val claim = ownership.acquireLive(request.gestureId)
			assertTrue(dispatch.transfer(request, claim))
			assertEquals(1, ownership.snapshot().liveClaims)
			val protectedField = type.task7Field("onProtectedRasterSourcePageIndicesChanged")
			val preparedField = type.task7Field("onPreparedActiveDeckChanged")
			val profileField = type.task7Field("onRasterProfileEpochChanged")
			@Suppress("UNCHECKED_CAST")
			val protectedRecipient = protectedField.get(curl) as (Set<Int>) -> Unit
			@Suppress("UNCHECKED_CAST")
			val preparedRecipient = preparedField.get(curl) as (ReaderPagePreparedActiveDeck?) -> Unit
			@Suppress("UNCHECKED_CAST")
			val profileRecipient = profileField.get(curl) as (Long?) -> Unit
			var reachedBoundary = false
			var claimPresentAtBoundary = false
			var boundaryDisposition: ReaderPortCommandResult? = null
			var restored: ReaderPortCommandResult? = null
			var nativeReturnsInside = emptyList<ReaderFrozenLegacyResource>()
			var releaseOnly = false
			var denied = false
			var profileCalls = 0
			var profileAfterDenial = 0
			var protectedAfterDenial = 0
			fun crossBoundary() {
				if (reachedBoundary) return
				reachedBoundary = true
				claimPresentAtBoundary = ownership.snapshot().liveClaims == 1
				when (mode) {
					"activated", "release-only" -> {
						val decision = task398InstallFacts(store) { ReaderPortCommandResult.Accepted }
						if (mode == "release-only") releaseOnly =
							ReaderActivatedSessionInstallationBarrier(store).failClosedAfterFinalizationRejection(decision)
						denied = true
					}
					"freeze", "freeze-restore" -> {
						boundaryDisposition = fixture.delivery.freezeForTransitionActivation(fixture.domain)
						nativeReturnsInside = fixture.delivery.snapshotFrozenOwnership()
						if (mode == "freeze-restore") restored = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
						denied = true
					}
				}
			}
			protectedField.set(curl, { indices: Set<Int> ->
				if (denied) protectedAfterDenial++
				protectedRecipient(indices)
				if (boundary == "protected") crossBoundary()
			})
			preparedField.set(curl, { deck: ReaderPagePreparedActiveDeck? ->
				preparedRecipient(deck)
				if (boundary == "prepared") crossBoundary()
			})
			profileField.set(curl, { epoch: Long? ->
				profileCalls++
				if (denied) profileAfterDenial++
				profileRecipient(epoch)
			})
			if (mode == "default") curl.invalidate("task398-unrelated-default-control")
			else replacement.invoke(fixture.viewer, incoming)
			// All decisive assertions remain outside production observer/recipient catches.
			assertTrue(reachedBoundary, "Original recipient must execute on the authentic invocation")
			if (boundary == "protected") assertTrue(claimPresentAtBoundary,
				"The actual acquired live claim must still require cleanup after denial")
			assertEquals(0, ownership.snapshot().liveClaims, "Independent original invalidation must release the real claim")
			assertTrue((dispatch.javaClass.task7Field("claims").get(dispatch) as Map<*, *>).isEmpty())
			assertNull(type.task7Field("requestedProfile").get(curl), "Final invalidation cleanup must still run")
			assertNull(type.task7Field("activePages").get(curl))
			if (mode == "release-only") { assertTrue(releaseOnly); assertNotNull(store.snapshot) }
			if (mode.startsWith("freeze")) {
				assertNull(store.snapshot)
				assertEquals(ReaderPortCommandResult.Accepted, boundaryDisposition)
				assertTrue(nativeReturnsInside.any { it.state == ReaderLegacyResourceState.Running },
                    "T398_VR_COMPAT_HELPER_NATIVE_RETURN")
                if (mode == "freeze-restore") {
                    assertEquals(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource,
                        assertIs<ReaderPortCommandResult.Rejected>(restored).reason)
                    assertEquals(ReaderPortCommandResult.Accepted,
                        fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
                    assertNull(outgoing.parent, "Restoration cannot resurrect the retired Native predecessor")
                    assertFalse(oldEngine.isAttachedToWindow)
                }
			}
			if (mode == "legacy" || mode == "default") {
				assertFalse(denied); assertNull(store.snapshot)
				assertEquals(1, profileCalls, "Valid Legacy and unrelated default callers retain their original profile recipient")
			} else {
				assertTrue(denied)
				assertEquals(0, profileAfterDenial, "The next original legacy profile recipient must be fenced")
				assertEquals(0, protectedAfterDenial, "The nested null-profile protected recipient must be fenced")
			}
		} finally { fixture.finish() }
	}

	private fun task398OriginalEngine(context: Context): WebView =
		Class.forName("paige.navic.ui.screens.reader.ReaderEngineWebView")
			.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
			.newInstance(context) as WebView

	private suspend fun task398ForegroundEngineVariant(mode: String) {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			val wrapper = FrameLayout(fixture.activity.get())
			fixture.viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(fixture.viewer, wrapper)
			assertTrue(wrapper.isAttachedToWindow); assertEquals(0, wrapper.childCount)
			assertFalse(fixture.viewerClass.task7Field("pageTurnCanvasEnabled").getBoolean(fixture.viewer))
			assertNull(fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(fixture.viewer))
			assertFalse(fixture.raster.javaClass.task7Field("prewarmInProgress").getBoolean(fixture.raster))
			val engine = task398OriginalEngine(fixture.activity.get())
			var windowCalls = 0
			engine.javaClass.task7Field("windowVisibilityListener").set(engine, { _: Int -> windowCalls++ })
			var legacyRecipients = 0
			fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
				{ _: ReaderPresentationEvent -> legacyRecipients++; null })
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			var rowsInside = emptyList<ReaderFrozenLegacyResource>()
			var pendingInside = false
			if (mode != "legacy") {
				val decision = task398InstallFacts(store) { fact ->
					facts += fact
					if (mode == "throw") error("Synthetic unknown foreground detach acceptance")
					if (mode == "reject" && facts.size == 1) return@task398InstallFacts ReaderPortCommandResult.Rejected(
						paige.navic.reader.ReaderTransitionFailureReason.PortRejected)
					if (mode == "freeze" && facts.size == 1) {
						assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
						rowsInside = fixture.delivery.snapshotFrozenOwnership()
						fixture.drainRemaining()
						pendingInside = fixture.confirmations.isEmpty()
						assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
					}
					ReaderPortCommandResult.Accepted
				}
				if (mode == "release-only") assertTrue(ReaderActivatedSessionInstallationBarrier(store)
					.failClosedAfterFinalizationRejection(decision))
			}
			if (mode == "frozen-first") assertEquals(ReaderPortCommandResult.Accepted,
				fixture.delivery.freezeForTransitionActivation(fixture.domain))
			// Delayed real factory primitive, not a discovered descendant, prewarm listener, or manual producer callback.
			wrapper.addView(engine, FrameLayout.LayoutParams(800, 600))
			assertTrue(engine.isAttachedToWindow)
			engine.dispatchWindowVisibilityChanged(View.VISIBLE)
			if (mode != "legacy") assertEquals(0, windowCalls,
				"The actual engine attachment window tail must not invoke legacy work after installation")
			if (mode == "close-first") fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			if (mode == "frozen-first") {
				val roots = fixture.delivery.snapshotFrozenOwnership()
				assertEquals(6, roots.size, "Three exact roots plus the actual late source/registration/attach-callback returns remain discoverable")
				assertEquals(3, roots.count { it.state == ReaderLegacyResourceState.Registered })
				assertEquals(3, roots.count { it.state == ReaderLegacyResourceState.Released })
				assertTrue(roots.all { it.physicalIdentity.domain == fixture.domain &&
					it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
				assertEquals(6, roots.map { it.physicalIdentity }.distinct().size)
				val material = task398FrameMaterialSource(fixture)
				assertEquals(ReaderPortCommandResult.Accepted, task398FrameFreeze(material, fixture.domain))
				val frame = task398FrameRows(material).single()
				assertEquals(ReaderLegacyInventorySource.FrameOrHandoff, frame.physicalIdentity.source)
				assertEquals(ReaderTransitionResourceKind.FrameHandoff, frame.kind)
				assertEquals(ReaderLegacyResourceState.Registered, frame.state)
				assertNull(frame.visibleOwner); assertNull(frame.binding); assertFalse(frame.mayBeCommittedPredecessor)
				val combined = roots + frame
				assertEquals(7, combined.size, "T398_CORE_FOREGROUND_SPLIT_SEVEN_TOTAL")
				assertEquals(4, combined.count { it.state == ReaderLegacyResourceState.Registered })
				assertEquals(3, combined.count { it.state == ReaderLegacyResourceState.Released })
				assertEquals(7, combined.map { it.physicalIdentity }.distinct().size)
			}
			wrapper.removeView(engine)
			assertFalse(engine.isAttachedToWindow); assertTrue(fixture.viewer.isAttachedToWindow)
			assertTrue(wrapper.isAttachedToWindow)
			if (mode !in listOf("close-first", "release-only", "legacy")) assertEquals(
				listOf(ReaderTask6LifecycleSafetyKind.WebViewDetached), facts.map { it.kind },
				"Only the real foreground child was removed; no Host/close/renderer fact may substitute")
			if (mode == "repeat" || mode == "reject" || mode == "throw") {
				wrapper.addView(engine); wrapper.removeView(engine)
				assertEquals(when (mode) { "reject" -> listOf(1L, 1L, 2L); "throw" -> listOf(1L); else -> listOf(1L, 2L) },
					facts.map { it.deliverySequence })
				if (mode == "reject") assertSame(facts[0], facts[1])
				assertTrue(facts.all { it.kind == ReaderTask6LifecycleSafetyKind.WebViewDetached })
			}
			if (mode == "close-first") assertEquals(listOf(ReaderTask6LifecycleSafetyKind.PublicationClosed), facts.map { it.kind })
			if (mode == "legacy" || mode == "release-only") assertTrue(facts.isEmpty())
			assertEquals(0, legacyRecipients, "Child detach does not invent a legacy lifecycle demand")
			if (mode == "legacy") assertTrue(windowCalls > 0, "Existing ordinary window callback remains enabled")
			if (mode == "freeze") {
				assertTrue(pendingInside)
				assertTrue(rowsInside.count { it.state == ReaderLegacyResourceState.Running } >= 3,
					"Actual callback, retry and fact recipient are distinct on-stack returns")
			}
			if (mode == "freeze" || mode == "frozen-first") {
				fixture.drainRemaining()
				assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
				if (mode == "frozen-first") {
					assertTrue(wrapper.isAttachedToWindow, "T398_CORE_FOREGROUND_ENGINE_ONLY_REMOVAL_PRESERVES_WRAPPER")
					val material = task398FrameMaterialSource(fixture)
					val frame = task398FrameRows(material).single()
					val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
					assertEquals(ReaderPortCommandResult.Accepted, task398FrameDrain(material, frame.physicalIdentity, confirmed::add))
					assertNull(wrapper.parent)
					assertEquals(listOf(frame.physicalIdentity), confirmed)
					assertIs<ReaderPortCommandResult.Rejected>(task398FrameDrain(material, frame.physicalIdentity, confirmed::add))
					assertEquals(1, confirmed.size)
					fixture.drainRemaining() // Settle the separately owned, genuine removal-return rows.
				}
				assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
				assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
				assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
				assertFalse(fixture.delivery.physicalDeliveryAdmissionOpen)
			}
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ConstructorComponentAndActualViewerObserverShareCompleteInventory() = runTest {
		val fixture = Task398RegisteredRootsFixture()
		try {
			assertEquals(1, fixture.application.registrations)
			assertEquals(1, fixture.application.callbacks.size)
			assertFalse(fixture.delivery.physicalDeliveryAdmissionOpen,
				"The component registration genuinely precedes the first delivery reset")
			fixture.bindSession()
			fixture.attach()
			assertEquals(1, fixture.hostLifecycle.registry.observerCount)
			assertTrue(fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl),
				"The actual LifecycleRegistry must synchronously replay resume")
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
			val rows = fixture.delivery.snapshotFrozenOwnership()
			assertEquals(2, rows.size, "Only the two genuinely installed roots remain; no dormant demand rows")
			assertEquals(2, rows.count { it.state == ReaderLegacyResourceState.Registered })
			assertEquals(2, rows.map { it.physicalIdentity }.distinct().size,
				"Component and lifecycle roots use the same collision-safe source-local namespace")
			assertTrue(rows.all { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
			fixture.drainRemaining()
			assertTrue(fixture.application.callbacks.isEmpty())
			assertEquals(0, fixture.hostLifecycle.registry.observerCount)
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398RegisteredReplayAndTrimDrainWaitForActualAttachReturn() = runTest {
		listOf(false, true).forEach { task398RegisteredReplayAndTrimDrainVariant(it) }
	}

	private suspend fun task398RegisteredReplayAndTrimDrainVariant(throwingRecipient: Boolean) {
		val fixture = Task398RegisteredRootsFixture()
		try {
			fixture.bindSession()
			var recipientEntered = false
			var afterDispatcherPending = false
			var afterAddPending = false
			var insideRows = emptyList<ReaderFrozenLegacyResource>()
			var materialStarts = 0
			var restoreInside: ReaderPortCommandResult? = null
			fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
				{ _: ReaderPresentationEvent ->
					recipientEntered = true
					fixture.delivery.freezeForTransitionActivation(fixture.domain)
					insideRows = fixture.delivery.snapshotFrozenOwnership()
					fixture.drainRemaining()
					restoreInside = fixture.delivery.restoreAfterTransitionActivation(fixture.domain)
					if (throwingRecipient) error("Synthetic accepted recipient failure after freeze")
					null
				})
			@Suppress("UNCHECKED_CAST")
			val originalRecipient = fixture.raster.javaClass.task7Field("onPresentationLifecycleEvent")
				.get(fixture.raster) as (ReaderPresentationLifecycleEvent) -> Unit
			fixture.raster.javaClass.task7Field("onPresentationLifecycleEvent").set(fixture.raster,
				{ event: ReaderPresentationLifecycleEvent ->
					originalRecipient(event)
					fixture.drainRemaining()
					afterDispatcherPending = fixture.confirmations.isEmpty()
				})
			fixture.raster.javaClass.task7Field("onPassiveRasterMemoryPressure").set(fixture.raster,
				{ _: String -> materialStarts++ })
			val bridge = fixture.viewerClass.task7Field("pageRasterHostEventController").get(fixture.viewer)
				.let { it.javaClass.task7Field("bridge").get(it) }
			bridge.javaClass.task7Field("publish").set(bridge, { _: ReaderPageRasterRetryEvent ->
				fixture.application.callbacks.single().onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
			})
			fixture.hostLifecycle.afterAdd = {
				fixture.drainRemaining()
				afterAddPending = fixture.confirmations.isEmpty()
			}
			fixture.attach()
			assertTrue(recipientEntered, "Actual registered observer replay must reach the real registered component recipient")
			assertEquals(2, insideRows.count { it.state == ReaderLegacyResourceState.Registered })
			assertTrue(insideRows.count { it.state == ReaderLegacyResourceState.Running } >= 6,
				"Attach, registration, replay, trim, dispatcher and recipient are distinct real on-stack owners")
			assertIs<ReaderPortCommandResult.Rejected>(restoreInside)
			assertTrue(afterDispatcherPending, "Dispatcher return is not the registered callback/source return")
			assertTrue(afterAddPending, "addObserver return still belongs to the enclosing attach tail")
			assertEquals(0, materialStarts, "Freeze in the recipient fences the next unsafe material primitive")
			assertFalse(fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl))
			assertFalse(fixture.curl.javaClass.task7Field("attached").getBoolean(fixture.curl),
				"Synchronous replay freeze must fence the rest of the real attachment tail")
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualUnregisterFailureRetainsExactRootAndReentrantReturnTails() = runTest {
		val fixture = Task398RegisteredRootsFixture()
		try {
			fixture.bindSession()
			fixture.attach()
			fixture.delivery.freezeForTransitionActivation(fixture.domain)
			val rows = fixture.delivery.snapshotFrozenOwnership()
			assertEquals(2, rows.size)
			val first = rows.first().physicalIdentity
			listOf(first.copy(source = ReaderLegacyInventorySource.Input),
				first.copy(domain = fixture.domain.copy(readerSessionGeneration = 2L)),
				first.copy(domain = fixture.domain.copy(freezeToken = ReaderLegacyFreezeToken(999L))),
				first.copy(sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(Long.MAX_VALUE))).forEach {
				assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.drainFrozenOwnership(it, fixture.confirmations::add))
			}
			fixture.application.failNextRemoval = true
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.drainFrozenOwnership(first, fixture.confirmations::add))
			assertTrue(fixture.application.callbacks.isNotEmpty(), "A failed removal cannot invent physical quiescence")
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain),
				"Partial/failing root removal cannot reopen legacy admission")
			var removalPending = false
			var reentrantRecipients = 0
			fixture.raster.javaClass.task7Field("onPresentationLifecycleEvent").set(fixture.raster,
				{ _: ReaderPresentationLifecycleEvent -> reentrantRecipients++; error("Synthetic recipient failure") })
			fixture.application.beforeRemoval = { callback ->
				callback.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
				fixture.drainRemaining(excluding = first)
				removalPending = first !in fixture.confirmations
				assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			}
			assertEquals(ReaderPortCommandResult.Accepted,
				fixture.delivery.drainFrozenOwnership(first, fixture.confirmations::add))
			assertTrue(removalPending, "Removal/callback return ownership outlives unregister's nested callbacks")
			assertEquals(0, reentrantRecipients, "Already registered callbacks entering frozen admission cannot mutate")
			fixture.drainRemaining()
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.drainFrozenOwnership(first, fixture.confirmations::add))
			assertEquals(fixture.seenIdentities + first, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			fixture.application.beforeRemoval = null
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			assertEquals(2, fixture.application.registrations)
			assertEquals(2, fixture.hostLifecycle.additions)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ExactRootRestoreRefreezeAndPermanentCloseNeverDuplicateRegistration() = runTest {
		val fixture = Task398RegisteredRootsFixture()
		try {
			fixture.bindSession()
			fixture.attach()
			fixture.delivery.freezeForTransitionActivation(fixture.domain)
			assertEquals(2, fixture.delivery.snapshotFrozenOwnership().size)
			fixture.drainRemaining()
			fixture.application.afterRegistration = { error("Synthetic accepted registration return failure") }
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			assertEquals(2, fixture.application.registrations)
			assertEquals(1, fixture.application.callbacks.size, "A throwing register return retains its exact possible installation")
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain),
				"An uncertain accepted root must be exactly removed, not duplicated or falsely restored")
			assertEquals(2, fixture.application.registrations)
			fixture.drainRemaining()
			assertTrue(fixture.application.callbacks.isEmpty())
			fixture.application.afterRegistration = { fixture.delivery.freezeForTransitionActivation(fixture.domain) }
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain),
				"Reentrant refreeze must not expose a partially restored epoch")
			assertFalse(fixture.delivery.physicalDeliveryAdmissionOpen)
			assertEquals(3, fixture.application.registrations)
			fixture.application.afterRegistration = null
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			assertEquals(3, fixture.application.registrations, "The already restored exact component root must not register twice")
			assertEquals(2, fixture.hostLifecycle.additions)
			assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			val closedDomain = fixture.domain.copy(freezeToken = ReaderLegacyFreezeToken(502L))
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(closedDomain))
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
			assertEquals(0, fixture.hostLifecycle.registry.observerCount,
				"Frozen admission suppresses legacy consequences, not truthful physical terminal removal")
			fixture.drainRemaining()
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(closedDomain))
			assertEquals(3, fixture.application.registrations, "Actual registered destroy/PublicationClosed is terminal")
			assertEquals(2, fixture.hostLifecycle.additions)
			assertTrue(fixture.application.callbacks.isEmpty())
			assertEquals(0, fixture.hostLifecycle.registry.observerCount)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398InstalledActualNormalizedLifecycleIsOrderedFactOnly() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store)
		try {
			fixture.bindSession(); fixture.attach()
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			var legacyReceipts = 0; var legacyEffects = 0
			fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
				{ _: ReaderPresentationEvent -> legacyReceipts++; null })
			fixture.viewerClass.task7Field("onAuthoritativePresentationHostEffect").set(fixture.viewer,
				{ _: ReaderPresentationHostEffect -> legacyEffects++ })
			val policy = fixture.viewerClass.task7Field("presentationInputPolicy").get(fixture.viewer)
			task398InstallFacts(store) { facts += it; ReaderPortCommandResult.Accepted }
			fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
			fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
			fixture.viewer.dispatchWindowVisibilityChanged(View.VISIBLE)
			fixture.application.callbacks.single().onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
			@Suppress("UNCHECKED_CAST")
			val rendererCallback = fixture.curl.javaClass.task7Field("onUnsafeLifecycleEvent")
				.get(fixture.curl) as (ReaderPageHostLifecycleEvent) -> Unit
			rendererCallback(ReaderPageHostLifecycleEvent.GlFailed)
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertEquals(listOf(ReaderTask6LifecycleSafetyKind.VisibilityLost,
				ReaderTask6LifecycleSafetyKind.VisibilityLost, ReaderTask6LifecycleSafetyKind.VisibilityLost,
				ReaderTask6LifecycleSafetyKind.RendererUnavailable, ReaderTask6LifecycleSafetyKind.PublicationClosed),
				facts.map { it.kind }, "Actual normalized ingress must not coalesce or invent restoration/memory facts")
			assertEquals((1L..5L).toList(), facts.map { it.deliverySequence })
			assertEquals(0, legacyReceipts, "Installed normalized ingress never calls the legacy recipient")
			assertEquals(0, legacyEffects)
			assertSame(policy, fixture.viewerClass.task7Field("presentationInputPolicy").get(fixture.viewer))
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398InstalledResumeAndMemoryHaveNoLegacyMaterialOrRecovery() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store)
		try {
			fixture.bindSession(); fixture.attach()
			var materialRetirements = 0; var publications = 0; var prewarm = 0
			fixture.raster.javaClass.task7Field("onPassiveRasterMemoryPressure").set(fixture.raster,
				{ _: String -> materialRetirements++ })
			fixture.raster.javaClass.task7Field("onPreparationStateChange").set(fixture.raster,
				{ _: ReaderPagePreparationState -> publications++ })
			fixture.raster.javaClass.task7Field("onRequestPrewarm").set(fixture.raster, { prewarm++ })
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			val decision = task398InstallFacts(store) { facts += it; ReaderPortCommandResult.Accepted }
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
			fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
			fixture.viewer.dispatchWindowVisibilityChanged(View.VISIBLE)
			val callback = fixture.application.callbacks.single()
			listOf(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
				android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
				android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE, 0).forEach(callback::onTrimMemory)
			callback.onLowMemory()
			assertEquals(0, materialRetirements, "Installed memory callbacks suppress retirement even without a safety fact")
			assertEquals(0, publications); assertEquals(0, prewarm)
			assertFalse(fixture.raster.javaClass.task7Field("passiveSessionRetiredForMemory").getBoolean(fixture.raster))
			assertTrue(fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl),
				"Installed lifecycle cannot directly mutate the legacy controller")
			assertTrue(facts.isEmpty(), "Resume/restore/memory do not invent Task6 safety kinds")
			assertTrue(ReaderActivatedSessionInstallationBarrier(store).failClosedAfterFinalizationRejection(decision))
			assertNotNull(store.snapshot); assertNull(store.snapshot?.portAuthority)
			fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
			callback.onLowMemory()
			assertEquals(0, materialRetirements); assertEquals(0, publications); assertTrue(facts.isEmpty(),
				"Release-only never falls back to legacy or ordinary safety ingress")
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ReentrantInstallFencesActualMemoryTailAndFactReturnRemainsOwned() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store)
		try {
			fixture.bindSession(); fixture.attach()
			var recipients = 0; var retirements = 0; var effects = 0
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			fixture.raster.javaClass.task7Field("onPassiveRasterMemoryPressure").set(fixture.raster,
				{ _: String -> retirements++ })
			fixture.viewerClass.task7Field("onAuthoritativePresentationHostEffect").set(fixture.viewer,
				{ _: ReaderPresentationHostEffect -> effects++ })
			fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
				{ event: ReaderPresentationEvent ->
					recipients++
					task398InstallFacts(store) { fact ->
						facts += fact
						assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
						fixture.drainRemaining()
						assertTrue(fixture.confirmations.isEmpty(), "Fact recipient is inside real component/source return ownership")
						ReaderPortCommandResult.Accepted
					}
					val state = task8SettledCurlSourceState()
					val controller = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = state))
					controller.onPresentationEvent(event).presentationReceipt
				})
			val callback = fixture.application.callbacks.single()
			callback.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
			assertEquals(1, recipients)
			assertEquals(0, retirements, "Installation inside accepted recipient fences the next real memory primitive")
			assertEquals(0, effects, "Accepted receipt accounting is not legacy publication authority")
			callback.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
			assertEquals(listOf(ReaderTask6LifecycleSafetyKind.VisibilityLost), facts.map { it.kind })
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
			assertFalse(fixture.delivery.physicalDeliveryAdmissionOpen, "Physical restoration cannot reopen installed legacy publication")
			assertEquals(1, recipients); assertEquals(0, retirements); assertEquals(0, effects)
		} finally { fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398CallerFenceCanvasWindowActivatedReleaseOnlyAndRecipientInstall() = runTest {
		for (mode in listOf("activated", "release-only", "recipient", "getter")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store)
			try {
				fixture.bindSession(); fixture.attach()
				fixture.viewerClass.task7Field("pageTurnCanvasEnabled").setBoolean(fixture.viewer, true)
				val bridge = fixture.task398RetryBridge()
				var retries = 0
				bridge.javaClass.task7Field("publish").set(bridge, { _: ReaderPageRasterRetryEvent -> retries++ })
				val inputCalls = fixture.task398RecordInputLifecycle()
				fixture.viewerClass.task7Field("destinationDeckPrewarmPending").setBoolean(fixture.viewer, true)
				var installed = false
				val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
				fun install() {
					if (installed) return
					installed = true
					val decision = task398InstallFacts(store) { facts += it; ReaderPortCommandResult.Accepted }
					if (mode == "release-only") assertTrue(ReaderActivatedSessionInstallationBarrier(store)
						.failClosedAfterFinalizationRejection(decision))
				}
				if (mode == "recipient") {
					fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
						{ _: ReaderPresentationEvent -> install(); null })
				} else if (mode != "getter") install()
				if (mode == "getter") {
					fixture.curl.javaClass.task7Field("hostResumed").setBoolean(fixture.curl, false)
					bridge.javaClass.task7Field("readerResumed").setBoolean(bridge, false)
					fixture.hostLifecycle.beforeStateRead = { install() }
					fixture.viewer.dispatchWindowVisibilityChanged(View.VISIBLE)
					assertFalse(fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl),
						"A state getter installing the shared snapshot fences the next actual resume primitive")
				} else {
					fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
					assertTrue(fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl),
						"Canvas-enabled installed window loss cannot run the actual legacy pause tail")
					assertTrue(bridge.javaClass.task7Field("readerResumed").getBoolean(bridge))
				}
				assertTrue(installed)
				assertEquals(0, retries)
				assertEquals(0, inputCalls())
				assertTrue(fixture.viewerClass.task7Field("destinationDeckPrewarmPending").getBoolean(fixture.viewer))
				assertNull(fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(fixture.viewer))
				if (mode == "activated") assertEquals(listOf(ReaderTask6LifecycleSafetyKind.VisibilityLost), facts.map { it.kind })
				if (mode == "release-only") { assertNotNull(store.snapshot); assertNull(store.snapshot?.portAuthority); assertTrue(facts.isEmpty()) }
			} finally { fixture.hostLifecycle.beforeStateRead = null; fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398CallerFenceActualAttachReplayGetterAndMissingOwner() = runTest {
		for (mode in listOf("missing-owner", "getter", "retry-recipient", "outer-return")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store)
			try {
				fixture.bindSession()
				val bridge = fixture.task398RetryBridge()
				var installs = 0; var deniedAttachmentRecipients = 0; var insidePending = false
				fun install() {
					if (installs++ == 0) task398InstallFacts(store) { ReaderPortCommandResult.Accepted }
				}
				val hostEvents = fixture.viewerClass.task7Field("pageRasterHostEventController").get(fixture.viewer)
				@Suppress("UNCHECKED_CAST")
				val attachment = hostEvents.javaClass.task7Field("onWebViewAttachmentChanged").get(hostEvents) as (Boolean) -> Unit
				hostEvents.javaClass.task7Field("onWebViewAttachmentChanged").set(hostEvents,
					{ value: Boolean -> deniedAttachmentRecipients++; attachment(value) })
				if (mode == "missing-owner") install()
				else fixture.hostLifecycle.afterAdd = {
					// The real synchronous replay already returned. Create the next actual
					// readiness edge and install from its external getter/recipient.
					fixture.curl.javaClass.task7Field("hostResumed").setBoolean(fixture.curl, false)
					bridge.javaClass.task7Field("readerResumed").setBoolean(bridge, false)
					if (mode == "getter") fixture.hostLifecycle.beforeStateRead = { install() }
					else if (mode == "retry-recipient") bridge.javaClass.task7Field("publish").set(bridge,
						{ _: ReaderPageRasterRetryEvent -> install() })
					else {
						install()
						assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
						fixture.drainRemaining()
						insidePending = fixture.confirmations.isEmpty()
					}
				}
				fixture.attach(withLifecycleOwner = mode != "missing-owner")
				assertTrue(installs > 0, "The actual attach boundary must select installation")
				assertFalse(fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl))
				assertFalse(fixture.curl.javaClass.task7Field("attached").getBoolean(fixture.curl),
					"Installation does not close physical registration, but denies the adjacent legacy attachment")
				assertEquals(0, deniedAttachmentRecipients)
				if (mode == "outer-return") {
					assertTrue(insidePending, "addObserver return is still inside the actual attach source return")
					fixture.drainRemaining()
					assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
					assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
				} else assertEquals(if (mode == "missing-owner") 0 else 1, fixture.hostLifecycle.registry.observerCount,
					"Required physical observer registration is independent of legacy consequence authority")
			} finally { fixture.hostLifecycle.beforeStateRead = null; fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398CallerFenceAlreadyRegisteredFrameworkPrewarmListener() = runTest {
		for (mode in listOf("installed", "getter", "retry-recipient")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store)
			try {
				fixture.bindSession(); fixture.attach()
				val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
				val webView = WebView(fixture.application)
				content.addView(webView, FrameLayout.LayoutParams(800, 600))
				fixture.viewer.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
				fixture.viewer.layout(0, 0, 800, 600)
				assertTrue(webView.isAttachedToWindow)
				fixture.viewerClass.task7Field("pageTurnCanvasEnabled").setBoolean(fixture.viewer, true)
				fixture.viewerClass.task7Field("coldOwnershipAdmitted").setBoolean(fixture.viewer, true)
				fixture.viewerClass.task7Field("pageTurnVisualPageIndex").set(fixture.viewer, 1)
				fixture.viewer.dispatchWindowVisibilityChanged(View.VISIBLE)
				val registered = assertNotNull(fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(fixture.viewer),
					"The actual window callback must have registered a real framework listener before installation")
				val bridge = fixture.task398RetryBridge()
				bridge.javaClass.task7Field("webViewAttached").setBoolean(bridge, false)
				var retries = 0; var attachmentRecipients = 0; var installs = 0
				fun install() { if (installs++ == 0) task398InstallFacts(store) { ReaderPortCommandResult.Accepted } }
				bridge.javaClass.task7Field("publish").set(bridge, { _: ReaderPageRasterRetryEvent ->
					retries++
					if (mode == "retry-recipient") install()
				})
				val hostEvents = fixture.viewerClass.task7Field("pageRasterHostEventController").get(fixture.viewer)
				@Suppress("UNCHECKED_CAST")
				val original = hostEvents.javaClass.task7Field("onWebViewAttachmentChanged").get(hostEvents) as (Boolean) -> Unit
				hostEvents.javaClass.task7Field("onWebViewAttachmentChanged").set(hostEvents,
					{ attached: Boolean -> attachmentRecipients++; original(attached) })
				if (mode == "installed") install()
				if (mode == "getter") fixture.hostLifecycle.beforeStateRead = { install() }
				assertSame(registered, fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(fixture.viewer))
				fixture.viewer.viewTreeObserver.dispatchOnPreDraw()
				assertTrue(installs > 0)
				assertEquals(if (mode == "retry-recipient") 1 else 0, retries,
					"A previously accepted framework listener cannot start a new retry after installation")
				if (mode != "retry-recipient") assertEquals(0, attachmentRecipients)
				assertNull(fixture.viewerClass.task7Field("passiveRasterPreparationAdapter").get(fixture.viewer))
				assertFalse(fixture.raster.javaClass.task7Field("prewarmInProgress").getBoolean(fixture.raster))
				assertNull(fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(fixture.viewer),
					"Physical listener removal is allowed and required; denied recovery is not replay demand")
				fixture.viewer.viewTreeObserver.dispatchOnPreDraw()
				assertEquals(if (mode == "retry-recipient") 1 else 0, retries)
			} finally { fixture.hostLifecycle.beforeStateRead = null; fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398CallerFenceActualTerminalFactsAndLateIngressNeverReopens() = runTest {
		for (mode in listOf("destroy", "close", "detach", "destroy-reject", "destroy-throw")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store)
			try {
				fixture.bindSession(); fixture.attach()
				val inputCalls = fixture.task398RecordInputLifecycle()
				val nativeCalls = fixture.task398RecordNativePublication()
				val bridge = fixture.task398RetryBridge()
				val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
				var closeObservedBeforeLegacyPause = false
				task398InstallFacts(store) { fact ->
					facts += fact
					if (fact.kind == ReaderTask6LifecycleSafetyKind.PublicationClosed) {
						closeObservedBeforeLegacyPause = fixture.curl.javaClass.task7Field("hostResumed").getBoolean(fixture.curl) &&
							bridge.javaClass.task7Field("readerResumed").getBoolean(bridge)
						if (mode == "destroy-throw") error("Synthetic unknown terminal fact acceptance")
						if (mode == "destroy-reject" && facts.size == 1) return@task398InstallFacts ReaderPortCommandResult.Rejected(
							paige.navic.reader.ReaderTransitionFailureReason.PortRejected)
					}
					ReaderPortCommandResult.Accepted
				}
				when (mode) {
					"destroy", "destroy-reject", "destroy-throw" -> {
						fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
						fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
						fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
						assertEquals(0, fixture.hostLifecycle.registry.observerCount)
						assertEquals(1, fixture.application.callbacks.size,
							"Destroy does not falsely report that its separate component root was physically removed")
					}
					"close" -> fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
					else -> fixture.activity.get().setContentView(FrameLayout(fixture.application))
				}
				assertTrue(closeObservedBeforeLegacyPause, "Terminal fact ingress precedes and fences legacy detach/material consequences")
				assertEquals(0, nativeCalls(), "Installed terminal delivery never starts the actual native-publication candidate getter")
				assertEquals(0, inputCalls(), "Installed terminal delivery never invokes the legacy input lifecycle cancellation port")
				if (!mode.startsWith("destroy")) {
					assertTrue(fixture.application.callbacks.isEmpty(), "Actual terminal teardown removes the registered component callback")
					assertTrue(fixture.viewerClass.task7Field("task4ResourceTeardownStarted").getBoolean(fixture.viewer))
				}
				fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
				@Suppress("UNCHECKED_CAST")
				val renderer = fixture.curl.javaClass.task7Field("onUnsafeLifecycleEvent").get(fixture.curl) as (ReaderPageHostLifecycleEvent) -> Unit
				renderer(ReaderPageHostLifecycleEvent.GlFailed)
				fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
				assertEquals(if (mode == "detach") listOf(ReaderTask6LifecycleSafetyKind.HostDetached,
					ReaderTask6LifecycleSafetyKind.PublicationClosed) else
					List(if (mode == "destroy-reject") 2 else 1) { ReaderTask6LifecycleSafetyKind.PublicationClosed },
					facts.map { it.kind }, "Close retry retains the exact head; late ingress cannot append or overtake")
				if (mode == "destroy-reject") {
					assertSame(facts[0], facts[1], "Explicit rejection retries the exact close, consumed only on Accepted")
					assertEquals(listOf(1L, 1L), facts.map { it.deliverySequence })
				} else assertEquals(if (mode == "detach") listOf(1L, 2L) else listOf(1L), facts.map { it.deliverySequence },
					"Unknown throwing acceptance is never guessed or replayed")
			} finally { fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398CallerFenceRegisteredComponentFifoRetryUnknownAcceptanceAndReturns() = runTest {
		for (mode in listOf("reject-retry", "throw", "reentrant", "freeze-restore", "release-only")) {
			val store = ReaderActivatedSessionSnapshotStore()
			val fixture = Task398RegisteredRootsFixture(store)
			try {
				fixture.bindSession(); fixture.attach()
				val callback = fixture.application.callbacks.single()
				val seen = mutableListOf<ReaderTask6LifecycleSafetyFact>()
				var rejected = false; var insidePending = false
				val decision = task398InstallFacts(store) { fact ->
					seen += fact
					if (mode == "throw") error("Synthetic unknown fact acceptance")
					if (mode == "reentrant" && seen.size == 1) {
						callback.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
						assertEquals(1, seen.size, "Reentrant ingress cannot overtake an on-stack recipient")
					}
					if (mode == "freeze-restore" && seen.size == 1) {
						fixture.delivery.freezeForTransitionActivation(fixture.domain)
						fixture.drainRemaining()
						insidePending = fixture.confirmations.isEmpty()
					}
					if (mode == "reject-retry" && !rejected) {
						rejected = true
						ReaderPortCommandResult.Rejected(paige.navic.reader.ReaderTransitionFailureReason.InvalidLegacyResource)
					} else ReaderPortCommandResult.Accepted
				}
				if (mode == "release-only") assertTrue(ReaderActivatedSessionInstallationBarrier(store)
					.failClosedAfterFinalizationRejection(decision))
				callback.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
				if (mode == "freeze-restore") {
					assertTrue(insidePending)
					fixture.drainRemaining()
					assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
					assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
					assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
					fixture.application.callbacks.single().onLowMemory()
				} else {
					callback.onLowMemory() // retries the exact head without inventing a memory safety kind
					callback.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
				}
				when (mode) {
					"reject-retry" -> {
						assertEquals(listOf(1L, 1L, 2L), seen.map { it.deliverySequence })
						assertSame(seen[0], seen[1], "Rejected retry retains the same exact fact")
					}
					"throw", "freeze-restore" -> assertEquals(listOf(1L), seen.map { it.deliverySequence })
					"reentrant" -> assertEquals(listOf(1L, 2L, 3L), seen.map { it.deliverySequence })
					else -> assertTrue(seen.isEmpty())
				}
				assertTrue(seen.all { it.kind == ReaderTask6LifecycleSafetyKind.VisibilityLost })
			} finally { fixture.finish() }
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		instrumentedPackages = ["androidx.webkit.internal"],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398OriginalPauseWebViewShadow::class, Task398CanonicalStoragePathShadow::class])
	fun task398PrewarmHelperGetterInstallationFencesOriginalPassiveWebViewPause() = runTest {
		task398PrewarmHelperGetterVariant(releaseOnly = false)
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		instrumentedPackages = ["androidx.webkit.internal"],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class,
			Task398OriginalPauseWebViewShadow::class, Task398CanonicalStoragePathShadow::class])
	fun task398PrewarmHelperGetterReleaseOnlyFencesOriginalPassiveWebViewPause() = runTest {
		task398PrewarmHelperGetterVariant(releaseOnly = true)
	}

	private suspend fun task398PrewarmHelperGetterVariant(releaseOnly: Boolean) {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store, activityBacked = true)
		try {
			fixture.bindSession(); fixture.attach()
			assertSame(fixture.activity.get(), fixture.viewer.context,
				"The actual helper requires Activity, not a ContextWrapper around an application")
			val content = fixture.viewerClass.task7Field("viewerContentContainer").get(fixture.viewer) as FrameLayout
			val liveWebView = Task398MeasuredLiveWebView(fixture.activity.get())
			content.addView(liveWebView, FrameLayout.LayoutParams(800, 600))
			fun layout() {
				fixture.viewer.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
				fixture.viewer.layout(0, 0, 800, 600)
				liveWebView.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
				liveWebView.layout(0, 0, 800, 600)
			}
			layout()
			assertTrue(liveWebView.isAttachedToWindow)
			assertEquals(800, liveWebView.width); assertEquals(600, liveWebView.height)
			fixture.viewerClass.task7Field("pageTurnCanvasEnabled").setBoolean(fixture.viewer, true)
			fixture.viewerClass.task7Field("coldOwnershipAdmitted").setBoolean(fixture.viewer, true)
			fixture.viewerClass.task7Field("pageTurnVisualPageIndex").set(fixture.viewer, 1)
			fixture.viewer.dispatchWindowVisibilityChanged(View.VISIBLE)
			val listenerField = fixture.viewerClass.task7Field("pageTurnPrewarmLayoutListener")
			val registered = assertNotNull(listenerField.get(fixture.viewer),
				"The actual window callback must register the real framework listener")
			val observer = fixture.viewer.viewTreeObserver
			fun physicalListeners(): List<*> {
				val listeners = observer.javaClass.task7Field("mOnPreDrawListeners").get(observer) ?: return emptyList<Any>()
				return listeners.javaClass.task7Field("mData").get(listeners) as List<*>
			}
			assertTrue(registered in physicalListeners())
			val adapterField = fixture.viewerClass.task7Field("passiveRasterPreparationAdapter")
			assertNull(adapterField.get(fixture.viewer)); assertNull(store.snapshot)
			var postInstallAvailabilityReads = 0; var postInstallRecovery = 0; var postInstallRetries = 0
			val providerField = fixture.raster.javaClass.task7Field("passiveRasterPreparationPortProvider")
			@Suppress("UNCHECKED_CAST")
			val provider = providerField.get(fixture.raster) as () -> ReaderPassiveRasterPreparationPort?
			providerField.set(fixture.raster, {
				if (store.snapshot != null) postInstallAvailabilityReads++
				provider()
			})
			val prewarmField = fixture.raster.javaClass.task7Field("onRequestPrewarm")
			@Suppress("UNCHECKED_CAST")
			val prewarm = prewarmField.get(fixture.raster) as () -> Unit
			prewarmField.set(fixture.raster, { if (store.snapshot != null) postInstallRecovery++; prewarm() })
			val bridge = fixture.task398RetryBridge()
			val retryField = bridge.javaClass.task7Field("publish")
			@Suppress("UNCHECKED_CAST")
			val retry = retryField.get(bridge) as (ReaderPageRasterRetryEvent) -> Unit
			retryField.set(bridge, { event: ReaderPageRasterRetryEvent ->
				if (store.snapshot != null) postInstallRetries++
				retry(event)
			})
			var installCount = 0; var stateReadsAfterReturn = 0
			var originalPassiveShadow: Task398OriginalPauseWebViewShadow? = null
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			fixture.hostLifecycle.beforeStateRead = {
				stateReadsAfterReturn++
				val adapter = adapterField.get(fixture.viewer)
				if (adapter != null && store.snapshot == null) {
					assertEquals(2, fixture.viewerClass.task7Field("pageTurnPrewarmStableFrameCount").getInt(fixture.viewer),
						"Installation is armed only after actual stable-frame adapter assignment")
					val session = adapter.javaClass.task7Field("session").get(adapter)
					val runtime = session.javaClass.task7Field("runtime").get(session)
					assertIs<ReaderPassiveRasterWebViewHost>(runtime)
					val passiveWebView = runtime.javaClass.task7Field("webView").get(runtime) as WebView
					assertTrue(passiveWebView !== liveWebView)
					originalPassiveShadow = Shadow.extract<Task398OriginalPauseWebViewShadow>(passiveWebView)
					assertEquals(0, originalPassiveShadow!!.pauseCalls)
					installCount++
					val decision = task398InstallFacts(store) { facts += it; ReaderPortCommandResult.Accepted }
					if (releaseOnly) assertTrue(ReaderActivatedSessionInstallationBarrier(store)
						.failClosedAfterFinalizationRejection(decision))
					fixture.hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
					assertEquals(Lifecycle.State.STARTED, fixture.hostLifecycle.registry.currentState)
				}
			}
			layout()
			assertFalse(observer.dispatchOnPreDraw(), "The framework dispatch must not cancel drawing")
			assertEquals(1, fixture.viewerClass.task7Field("pageTurnPrewarmStableFrameCount").getInt(fixture.viewer))
			assertNull(store.snapshot); assertNull(adapterField.get(fixture.viewer))
			assertSame(registered, listenerField.get(fixture.viewer))
			layout()
			assertFalse(observer.dispatchOnPreDraw(), "The framework dispatch must not cancel drawing")
			assertEquals(1, installCount, "The real listener must reach the helper's later external lifecycle getter")
			assertNotNull(store.snapshot)
			if (releaseOnly) assertNull(store.snapshot?.portAuthority) else assertNotNull(store.snapshot?.portAuthority)
			val originalPause = assertNotNull(originalPassiveShadow)
			assertEquals(0, originalPause.pauseCalls,
				"A shared snapshot installed by the helper getter must fence the original passive WebView.onPause")
			assertFalse(originalPause.wasOnPauseCalled(), "The original WebView shadow behavior is preserved, not replaced")
			assertEquals(0, postInstallAvailabilityReads); assertEquals(0, postInstallRecovery); assertEquals(0, postInstallRetries)
			assertNull(listenerField.get(fixture.viewer))
			assertFalse(registered in physicalListeners(), "Removal must reach the actual ViewTreeObserver, not only clear a field")
			assertFalse(fixture.raster.javaClass.task7Field("prewarmInProgress").getBoolean(fixture.raster))
			assertFalse(fixture.viewerClass.task7Field("task4ResourceTeardownStarted").getBoolean(fixture.viewer),
				"This is ordinary prewarm, not a mandatory terminal close")
			val readsAtReturn = stateReadsAfterReturn
			assertFalse(observer.dispatchOnPreDraw(), "The framework dispatch must not cancel drawing")
			assertEquals(readsAtReturn, stateReadsAfterReturn)
			assertEquals(0, originalPause.pauseCalls)
			assertTrue(facts.isEmpty(), "ON_PAUSE/STARTED does not manufacture a Task6 lifecycle safety kind")
			fixture.hostLifecycle.beforeStateRead = null
			assertEquals(1, fixture.hostLifecycle.registry.observerCount,
				"Installation does not pretend the registered lifecycle root has physically returned/removed")
			assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
			val roots = fixture.delivery.snapshotFrozenOwnership()
			assertEquals(2, roots.size)
			assertTrue(roots.all { it.state == ReaderLegacyResourceState.Registered },
				"Only existing registered roots are claimed here; no invented pre-draw return owner")
			fixture.drainRemaining()
			assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
			assertEquals(0, fixture.hostLifecycle.registry.observerCount)
			assertTrue(fixture.confirmations.containsAll(roots.map { it.physicalIdentity }))
			// Actual root removal also admits its existing cancellation-return owners;
			// retain those truthful identities instead of pretending only roots returned.
			assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
			assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
		} finally { fixture.hostLifecycle.beforeStateRead = null; fixture.finish() }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachEmitsHostFactBeforeClose() = runTest {
		task398ActualHostDetachVariant("activated")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachReleaseOnlyStillPhysicallyTearsDown() = runTest {
		task398ActualHostDetachVariant("release-only")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachAfterCloseCannotAppendLateHostFact() = runTest {
		task398ActualHostDetachVariant("close-first")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachRejectedHostRetriesSameObjectAheadOfClose() = runTest {
		task398ActualHostDetachVariant("reject")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachUnknownHostAcceptanceCannotReplayOrOvertake() = runTest {
		task398ActualHostDetachVariant("throw")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachFreezeDrainWaitsForEnclosingFrameworkReturn() = runTest {
		task398ActualHostDetachVariant("freeze")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachRecipientReleaseOnlyFencesNextLegacyPrimitive() = runTest {
		task398ActualHostDetachVariant("recipient-release-only")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task398ActualHostDetachLegacyCloseRecipientInstallationFencesRemainingTail() = runTest {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store)
		try {
			fixture.bindSession(); fixture.attach()
			assertTrue(fixture.viewer.isAttachedToWindow)
			val inputCalls = fixture.task398RecordInputLifecycle()
			val nativeCalls = fixture.task398RecordNativePublication()
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			var recipients = 0; var effects = 0
			fixture.viewerClass.task7Field("onAuthoritativePresentationHostEffect").set(fixture.viewer,
				{ _: ReaderPresentationHostEffect -> effects++ })
			fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
				{ event: ReaderPresentationEvent ->
					recipients++
					assertEquals(ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.PublicationClosed), event,
						"Uninstalled physical Host detach cannot invent a legacy recipient or demand before the existing close")
					task398InstallFacts(store) { facts += it; ReaderPortCommandResult.Accepted }
					val controller = ReaderController(ReaderControllerState(readerSessionGeneration = 1L,
						presentation = task8SettledCurlSourceState()))
					controller.onPresentationEvent(event).presentationReceipt
				})
			fixture.activity.get().setContentView(FrameLayout(fixture.application))
			assertFalse(fixture.viewer.isAttachedToWindow)
			assertEquals(1, recipients); assertNotNull(store.snapshot)
			assertIs<ReaderActivatedSessionPortAuthority.Test>(store.snapshot?.portAuthority)
			assertEquals(0, effects); assertEquals(0, inputCalls()); assertEquals(0, nativeCalls())
			assertTrue(facts.isEmpty(), "Installation does not retroactively synthesize Host detach or closed ingress")
			assertTrue(fixture.viewerClass.task7Field("physicalPointerDeliveryClosed").getBoolean(fixture.viewer))
			assertTrue(fixture.viewerClass.task7Field("task4ResourceTeardownStarted").getBoolean(fixture.viewer))
			assertTrue(fixture.application.callbacks.isEmpty()); assertEquals(0, fixture.hostLifecycle.registry.observerCount)
			fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertEquals(1, recipients); assertTrue(facts.isEmpty())
		} finally { fixture.finish() }
	}

	private suspend fun task398ActualHostDetachVariant(mode: String) {
		val store = ReaderActivatedSessionSnapshotStore()
		val fixture = Task398RegisteredRootsFixture(store)
		try {
			fixture.bindSession(); fixture.attach()
			assertTrue(fixture.viewer.isAttachedToWindow, "The real Activity must attach the actual viewer before removal")
			val inputCalls = fixture.task398RecordInputLifecycle()
			val nativeCalls = fixture.task398RecordNativePublication()
			val policy = fixture.viewerClass.task7Field("presentationInputPolicy").get(fixture.viewer)
			var legacyRecipients = 0; var effects = 0; var retirements = 0; var recovery = 0
			fixture.viewerClass.task7Field("onPresentationEvent").set(fixture.viewer,
				{ _: ReaderPresentationEvent -> legacyRecipients++; null })
			fixture.viewerClass.task7Field("onAuthoritativePresentationHostEffect").set(fixture.viewer,
				{ _: ReaderPresentationHostEffect -> effects++ })
			fixture.raster.javaClass.task7Field("onPassiveRasterMemoryPressure").set(fixture.raster,
				{ _: String -> retirements++ })
			fixture.raster.javaClass.task7Field("onRequestPrewarm").set(fixture.raster, { recovery++ })
			val facts = mutableListOf<ReaderTask6LifecycleSafetyFact>()
			var hostBeforeTerminalTeardown = false
			var frozenInsideHost = false
			var sourceStillPendingInsideClose = false
			var hostRows = emptyList<ReaderFrozenLegacyResource>()
			var decision: ReaderInitialActivationDecision? = null
			decision = task398InstallFacts(store) { fact ->
				facts += fact
				if (fact.kind == ReaderTask6LifecycleSafetyKind.HostDetached) {
					hostBeforeTerminalTeardown = !fixture.viewerClass.task7Field("task4ResourceTeardownStarted").getBoolean(fixture.viewer) &&
						!fixture.viewerClass.task7Field("physicalPointerDeliveryClosed").getBoolean(fixture.viewer) &&
						fixture.hostLifecycle.registry.observerCount == 1 && fixture.application.callbacks.size == 1
					if ((mode == "freeze" || mode == "throw") && !frozenInsideHost) {
						frozenInsideHost = true
						assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.freezeForTransitionActivation(fixture.domain))
						hostRows = fixture.delivery.snapshotFrozenOwnership()
						fixture.drainRemaining()
						assertTrue(fixture.confirmations.isEmpty(), "Recipient/root-removal returns cannot confirm the enclosing physical detach source")
						assertIs<ReaderPortCommandResult.Rejected>(fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
					}
					if (mode == "recipient-release-only") assertTrue(ReaderActivatedSessionInstallationBarrier(store)
						.failClosedAfterFinalizationRejection(checkNotNull(decision)))
					if (mode == "throw") error("Synthetic unknown HostDetached acceptance")
					if (mode == "reject" && facts.size == 1) return@task398InstallFacts ReaderPortCommandResult.Rejected(
						paige.navic.reader.ReaderTransitionFailureReason.PortRejected)
				} else if (mode == "freeze" && fact.kind == ReaderTask6LifecycleSafetyKind.PublicationClosed) {
					fixture.drainRemaining()
					sourceStillPendingInsideClose = fixture.confirmations.isEmpty()
				}
				ReaderPortCommandResult.Accepted
			}
			val installed = assertNotNull(store.snapshot)
			assertIs<ReaderActivatedSessionPortAuthority.Test>(installed.portAuthority)
			if (mode == "release-only") assertTrue(ReaderActivatedSessionInstallationBarrier(store)
				.failClosedAfterFinalizationRejection(checkNotNull(decision)))
			if (mode == "close-first") fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			// This is the sole detach trigger: no direct callback/helper or terminal-field fabrication.
			fixture.activity.get().setContentView(FrameLayout(fixture.application))
			assertFalse(fixture.viewer.isAttachedToWindow)
			val expectedKinds = when (mode) {
				"release-only" -> emptyList()
				"close-first" -> listOf(ReaderTask6LifecycleSafetyKind.PublicationClosed)
				"throw", "recipient-release-only" -> listOf(ReaderTask6LifecycleSafetyKind.HostDetached)
				"reject" -> listOf(ReaderTask6LifecycleSafetyKind.HostDetached, ReaderTask6LifecycleSafetyKind.HostDetached,
					ReaderTask6LifecycleSafetyKind.PublicationClosed)
				else -> listOf(ReaderTask6LifecycleSafetyKind.HostDetached, ReaderTask6LifecycleSafetyKind.PublicationClosed)
			}
			assertEquals(expectedKinds, facts.map { it.kind }, "Actual Activity removal must report HostDetached before the distinct close; mode=$mode")
			assertEquals(when (mode) {
				"release-only" -> emptyList()
				"close-first", "throw", "recipient-release-only" -> listOf(1L)
				"reject" -> listOf(1L, 1L, 2L)
				else -> listOf(1L, 2L)
			}, facts.map { it.deliverySequence })
			if (mode !in listOf("release-only", "close-first")) assertTrue(hostBeforeTerminalTeardown,
				"Host fact is encountered before mandatory teardown, not inferred from publication close")
			if (mode == "reject") assertSame(facts[0], facts[1], "Explicit rejection retries the SAME Host fact ahead of close")
			assertEquals(0, legacyRecipients); assertEquals(0, effects); assertEquals(0, inputCalls()); assertEquals(0, nativeCalls())
			assertEquals(0, retirements); assertEquals(0, recovery)
			assertSame(policy, fixture.viewerClass.task7Field("presentationInputPolicy").get(fixture.viewer))
			assertTrue(fixture.viewerClass.task7Field("physicalPointerDeliveryClosed").getBoolean(fixture.viewer))
			assertTrue(fixture.viewerClass.task7Field("task4ResourceTeardownStarted").getBoolean(fixture.viewer))
			assertTrue(fixture.application.callbacks.isEmpty()); assertEquals(0, fixture.hostLifecycle.registry.observerCount)
			assertNull(fixture.viewerClass.task7Field("observedHostLifecycle").get(fixture.viewer))
			if (mode == "freeze" || mode == "throw") {
				assertTrue(frozenInsideHost)
				assertTrue(hostRows.count { it.state == ReaderLegacyResourceState.Running } >= 3,
					"Actual detach, ordered retry and fact recipient retain distinct exact physical-return owners")
				if (mode == "freeze") assertTrue(sourceStillPendingInsideClose, "Even the later close recipient remains inside the physical detach source")
				fixture.drainRemaining()
				assertTrue(fixture.delivery.snapshotFrozenOwnership().isEmpty())
				assertEquals(fixture.seenIdentities, fixture.confirmations.toSet())
				assertEquals(fixture.confirmations.size, fixture.confirmations.distinct().size)
				assertEquals(ReaderPortCommandResult.Accepted, fixture.delivery.restoreAfterTransitionActivation(fixture.domain))
				assertFalse(fixture.delivery.physicalDeliveryAdmissionOpen, "Physical restoration does not grant legacy work permission")
			}
			val atReturn = facts.toList()
			fixture.viewer.dispatchWindowVisibilityChanged(View.INVISIBLE)
			fixture.viewerClass.task7Method("closeReader").invoke(fixture.viewer)
			assertEquals(atReturn, facts, "Accepted once/unknown acceptance cannot replay; late observations cannot overtake the Host head")
			if (mode in listOf("release-only", "recipient-release-only")) {
				assertNotNull(store.snapshot); assertNull(store.snapshot?.portAuthority)
			} else assertSame(installed, store.snapshot, "The immutable installation authority is not rewritten by physical detach")
		} finally { fixture.finish() }
	}

	private fun Task398RegisteredRootsFixture.task398RetryBridge(): Any =
		viewerClass.task7Field("pageRasterHostEventController").get(viewer).let { it.javaClass.task7Field("bridge").get(it) }

	private fun Task398RegisteredRootsFixture.task398RecordInputLifecycle(): () -> Int {
		val input = viewerClass.task7Field("pageInputSettlementHostController").get(viewer)
		val field = input.javaClass.task7Field("cancellationPort")
		val original = field.get(input) as ReaderPageHostCancellationPort
		var calls = 0
		field.set(input, object : ReaderPageHostCancellationPort by original {
			override fun cancelActiveRendererGesture(reason: paige.navic.reader.ReaderPageLifecycleCancellationReason) {
				calls++; original.cancelActiveRendererGesture(reason)
			}
		})
		return { calls }
	}

	private fun Task398RegisteredRootsFixture.task398RecordNativePublication(): () -> Int {
		val publisher = (viewerClass.task7Field("nativePagePresentationPublisher\$delegate").get(viewer) as Lazy<*>).value!!
		val field = publisher.javaClass.task7Field("currentCandidate")
		@Suppress("UNCHECKED_CAST")
		val original = field.get(publisher) as () -> ReaderNativePagePresentationCandidate?
		var calls = 0
		field.set(publisher, { calls++; original() })
		return { calls }
	}

	private fun task398InstallFacts(
		store: ReaderActivatedSessionSnapshotStore,
		enqueue: (ReaderTask6LifecycleSafetyFact) -> ReaderPortCommandResult
	): ReaderInitialActivationDecision {
		// Reuse the established Test-authority fixture, never mint a production package.
		val fixtureType = Class.forName("paige.navic.ui.screens.reader.ReaderTransitionActivationTestKt")
		val ports = fixtureType.task7Method("completeActivatedSessionPorts").invoke(null) as ReaderTestActivatedSessionPorts
		val activationFixture = ReaderTransitionActivationTest()
		val decision = activationFixture.javaClass.task7Method("neutralDecision").invoke(activationFixture) as ReaderInitialActivationDecision
		assertEquals(ReaderActivationInstallResult.Installed, ReaderActivatedSessionInstallationBarrier(store)
			.installTestActivatedSession(ports.copy(lifecycleFacts = ReaderTask6LifecycleFactPort(enqueue)), decision))
		return decision
	}

	private inner class Task398RegisteredRootsFixture(
		installationStore: ReaderActivatedSessionSnapshotStore? = null,
		activityBacked: Boolean = false
	) {
		val application = Task398ComponentContext(RuntimeEnvironment.getApplication())
		val viewerClass: Class<*>
		val viewer: FrameLayout
		val delivery: ReaderPresentationLifecycleDelivery
		val raster: ReaderPageRasterPreparationController
		val curl: ReaderPlayLikeCurlFoliateController
		val hostLifecycle = Task398HostLifecycle()
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(501L))
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val seenIdentities = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		private val requested = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		init {
			Dispatchers.setMain(UnconfinedTestDispatcher())
			val viewerContext = if (activityBacked) activity.get() else application
			val pair = if (installationStore == null) task7Viewer(viewerContext) else {
				val type = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer")
				// Baseline fallback permits genuine behavioral RED before constructor plumbing exists.
				val actualViewer = type.declaredConstructors.firstOrNull {
					it.parameterTypes.contentEquals(arrayOf(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java))
				}?.apply { isAccessible = true }?.newInstance(viewerContext, installationStore) as? FrameLayout
					?: task7Viewer(viewerContext).second
				type to actualViewer
			}
			viewerClass = pair.first; viewer = pair.second
			delivery = viewerClass.task7Field("presentationLifecycleDelivery").get(viewer) as ReaderPresentationLifecycleDelivery
			raster = viewerClass.task7Field("pageRasterPreparationController").get(viewer) as ReaderPageRasterPreparationController
			curl = viewerClass.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		}
		fun bindSession() {
			val state = task8SettledCurlSourceState()
			val controller = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = state))
			val binding = requireNotNull(state.binding)
			val reporter = viewerClass.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
			reporter.reset(1L, controller.presentationVersion, state)
			check(reporter.bindPublication(binding))
			delivery.reset(controller.presentationVersion, observedWindowVisible = null)
			check(delivery.bindPublication(binding.publicationIdentity))
		}
		fun attach(withLifecycleOwner: Boolean = true) {
			if (withLifecycleOwner) Task336ViewTreeLifecycleOwnerShadow.install(viewer, hostLifecycle)
			hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
			hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
			hostLifecycle.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
			activity.get().setContentView(viewer)
		}
		fun drainRemaining(excluding: ReaderLegacyPhysicalIdentity? = null) {
			// Bounded fixed-point discovery includes real cancellation-return owners accepted by removal.
			repeat(8) {
				val rows = delivery.snapshotFrozenOwnership().filter {
					it.physicalIdentity != excluding && it.physicalIdentity !in requested
				}
				if (rows.isEmpty()) return
				rows.forEach { row ->
					seenIdentities += row.physicalIdentity
					requested += row.physicalIdentity
					val result = delivery.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
					if (result != ReaderPortCommandResult.Accepted) requested -= row.physicalIdentity
					assertEquals(ReaderPortCommandResult.Accepted, result)
				}
			}
			error("Actual root cancellation did not reach a bounded inventory fixed point")
		}
		suspend fun finish() {
			application.failNextRemoval = false
			application.beforeRemoval = null
			application.afterRegistration = null
			hostLifecycle.afterAdd = null
			try {
				val closed = raster.destroy()
				ShadowLooper.runUiThreadTasks()
				val disposal = runCatching {
					withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				}.exceptionOrNull()
				// This fixture owns no GL material. Robolectric has no holder surface; the real
				// terminal fallback settles exceptionally, rather than leaving any work pending.
				if (disposal != null) {
					check(disposal is ReaderPageTeardownException &&
						generateSequence(disposal as Throwable?) { it.cause }.any {
							it.message == "GL surface is unavailable for terminal disposal"
						}) { "Unexpected registered-roots fixture disposal failure" }
				}
				check(closed.isCompleted)
				activity.pause().stop().destroy()
			} finally {
				Task336ViewTreeLifecycleOwnerShadow.clear()
				Dispatchers.resetMain()
			}
		}
	}

	private class Task398ComponentContext(base: Context) : android.content.ContextWrapper(base) {
		val callbacks = linkedSetOf<android.content.ComponentCallbacks2>()
		var registrations = 0
		var failNextRemoval = false
		var beforeRemoval: ((android.content.ComponentCallbacks2) -> Unit)? = null
		var afterRegistration: (() -> Unit)? = null
		override fun getApplicationContext(): Context = this
		override fun registerComponentCallbacks(callback: android.content.ComponentCallbacks) {
			baseContext.registerComponentCallbacks(callback)
			callbacks += callback as android.content.ComponentCallbacks2
			registrations++
			afterRegistration?.invoke()
		}
		override fun unregisterComponentCallbacks(callback: android.content.ComponentCallbacks) {
			if (failNextRemoval) { failNextRemoval = false; error("Synthetic pre-removal failure") }
			beforeRemoval?.invoke(callback as android.content.ComponentCallbacks2)
			baseContext.unregisterComponentCallbacks(callback)
			callbacks.remove(callback as android.content.ComponentCallbacks2)
		}
	}

	private class Task398HostLifecycle : Lifecycle(), LifecycleOwner {
		val registry = LifecycleRegistry(this)
		var additions = 0
		var afterAdd: (() -> Unit)? = null
		var beforeStateRead: (() -> Unit)? = null
		override val lifecycle: Lifecycle get() = this
		override val currentState: State get() {
			beforeStateRead?.invoke()
			return registry.currentState
		}
		override fun addObserver(observer: androidx.lifecycle.LifecycleObserver) {
			additions++
			registry.addObserver(observer)
			afterAdd?.invoke()
		}
		override fun removeObserver(observer: androidx.lifecycle.LifecycleObserver) = registry.removeObserver(observer)
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397RelocationProviderFreezeDeniesActualJavascript() =
		task397ActualWebViewFence(initial = false, confirmation = false, authority = false)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397InitialLiveProviderFreezeDeniesActualJavascript() =
		task397ActualWebViewFence(initial = true, confirmation = false, authority = false)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397InitialLiveAuthorityFreezeDeniesActualJavascript() =
		task397ActualWebViewFence(initial = true, confirmation = false, authority = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397InitialLiveConfirmationProviderFreezeParksUnstartedGetter() =
		task397ActualWebViewFence(initial = true, confirmation = true, authority = false)

	private fun task397ActualWebViewFence(initial: Boolean, confirmation: Boolean, authority: Boolean) {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val curl = fixture.controller
		val type = curl.javaClass
		val ownership = type.task7Field("foregroundWebViewOwnership").get(curl) as ReaderForegroundWebViewOwnership
		type.task7Method("releaseInitialLivePresentationAuthority",
			type.declaredClasses.single { it.simpleName == "InitialLivePresentationAuthorityRequest" }).invoke(curl, null)
		@Suppress("UNCHECKED_CAST")
		val oldView = (type.task7Field("webViewProvider").get(curl) as () -> WebView?)()!!
		val host = oldView.parent as FrameLayout
		val view = object : WebView(RuntimeEnvironment.getApplication()) {
			var calls = 0
			override fun evaluateJavascript(script: String, resultCallback: android.webkit.ValueCallback<String>?) {
				calls++
				resultCallback?.onReceiveValue("null")
			}
		}
		host.addView(view)
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(host)
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		var fenced = false
		var enabledFence = !confirmation
		val beforeGeneration = ownership.javaClass.task7Field("mutationGeneration").getLong(ownership)
		fun freezeOnce() {
			if (enabledFence && !fenced) {
				fenced = true
				ownership.freezeForTransitionActivation(domain)
			}
		}
		type.task7Field("webViewProvider").set(curl, { if (!authority) freezeOnce(); view })
		if (authority) {
			@Suppress("UNCHECKED_CAST")
			val epoch = type.task7Field("presentationHostEpoch").get(curl) as () -> Long
			type.task7Field("presentationHostEpoch").set(curl, {
				val result = epoch()
				if (ownership.javaClass.task7Field("mutationGeneration").getLong(ownership) > beforeGeneration) freezeOnce()
				result
			})
		}
		try {
			assertTrue(view.isAttachedToWindow)
			if (initial) {
				type.task7Method("requestInitialLivePresentationAuthority", java.lang.Long.TYPE,
					java.lang.Long::class.java).invoke(curl, 701L, null)
				if (confirmation) {
					assertEquals(1, view.calls, "actual navigation precedes confirmation")
					val request = assertNotNull(type.task7Field("initialLivePresentationAuthority").get(curl))
					val target = request.javaClass.task7Field("target").get(request) as ReaderPageTurnPresentationTarget.Live
					val ack = ReaderPageTurnSettlementAck(target.token, target.pageIndex.toInt(),
						target.foliateSessionId, target.rasterGeneration, target.textureGeneration)
					enabledFence = true
					type.task7Method("confirmInitialLivePresentationAuthority", Integer.TYPE,
						ReaderPageTurnSettlementAck::class.java).invoke(curl, target.pageIndex.toInt(), ack)
				}
			} else {
				val coordinator = type.task7Field("relocationLiveDispatchCoordinator").get(curl) as ReaderPageRelocationLiveDispatchCoordinator
				coordinator.javaClass.task7Field("isDispatchCurrent").set(coordinator, { _: ReaderPageRelocationRequest -> true })
				val request = ReaderPageRelocationRequest(
					paige.navic.reader.ReaderPageRelocationToken("synthetic-boundary"), 701L,
					0L, 301L, 1, 0, ReaderPageTurnDirection.Previous, "critical-4-session")
				assertTrue(coordinator.transfer(request, ownership.acquireLive(request.gestureId)))
				coordinator.dispatch(request)
			}
			assertTrue(fenced, "the actual external provider/authority must run")
			assertEquals(if (confirmation) 1 else 0, view.calls, "no primitive starts after reentrant freeze")
			ownership.snapshotFrozenOwnership().forEach { ownership.drainFrozenOwnership(it.physicalIdentity) {} }
			assertTrue(ownership.snapshotFrozenOwnership().isEmpty(), "denied next phase is not running physical work")
			assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
			assertEquals(if (confirmation) 2 else 1, view.calls, "replay only the unstarted phase once")
		} finally {
			curl.destroy()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397RetainedInitialGetterFreezeThrowDrainsAndConsumesBooleanRecipientOnce() =
		task397AcceptedInitialGetter(throwCanonical = true, synchronousGetter = false)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397AcceptedInitialGetterRetainsOutermostInvocationAfterLogicalRelease() =
		task397AcceptedInitialGetter(throwCanonical = false, synchronousGetter = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397InitialGetterBooleanThrowWithoutFreezeFinishesSafeDisposition() =
		task397AcceptedInitialGetter(throwCanonical = true, synchronousGetter = false, freezeCanonical = false)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397SynchronousInitialGetterCanonicalThrowFinishesAndPropagates() =
		task397AcceptedInitialGetter(throwCanonical = true, synchronousGetter = true, freezeCanonical = false)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397SynchronousInitialGetterFreezeThrowParksAndPropagates() =
		task397AcceptedInitialGetter(throwCanonical = true, synchronousGetter = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397SynchronousInitialGetterWrappedCanonicalThrowPreservesCause() =
		task397AcceptedInitialGetter(throwCanonical = true, synchronousGetter = true,
			freezeCanonical = false, wrapCallbackFailure = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task397InitialGetterPrimitiveFailureRetainsLegacyCleanup() =
		task397AcceptedInitialGetter(throwCanonical = false, synchronousGetter = true,
			freezeCanonical = false, failPrimitive = true)

	private fun task397AcceptedInitialGetter(
		throwCanonical: Boolean,
		synchronousGetter: Boolean,
		freezeCanonical: Boolean = throwCanonical,
		wrapCallbackFailure: Boolean = false,
		failPrimitive: Boolean = false
	) {
		var prewarm = 0
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState(), onRequestPrewarm = { prewarm++ })
		val curl = fixture.controller
		val type = curl.javaClass
		val ownership = type.task7Field("foregroundWebViewOwnership").get(curl) as ReaderForegroundWebViewOwnership
		type.task7Method("releaseInitialLivePresentationAuthority",
			type.declaredClasses.single { it.simpleName == "InitialLivePresentationAuthorityRequest" }).invoke(curl, null)
		@Suppress("UNCHECKED_CAST")
		val oldView = (type.task7Field("webViewProvider").get(curl) as () -> WebView?)()!!
		val host = oldView.parent as FrameLayout
		val domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(23L))
		var confirmations = 0
		var canonical = 0
		var receipt: String? = null
		var outerReturning = false
		val canonicalFailure = IllegalStateException("controlled canonical failure")
		val drainedIdentities = mutableSetOf<ReaderLegacyPhysicalIdentity>()
		val confirmedIdentities = mutableSetOf<ReaderLegacyPhysicalIdentity>()
		fun drainAcceptedTails() {
			val tails = ownership.snapshotFrozenOwnership()
			assertTrue(tails.size >= 2, "getter invocation and accepted result-return tails remain physically owned")
			tails.forEach { tail ->
				drainedIdentities += tail.physicalIdentity
				ownership.drainFrozenOwnership(tail.physicalIdentity) { identity ->
					if (synchronousGetter) assertTrue(outerReturning, "no exact confirmation before outer primitive return")
					assertEquals(tail.physicalIdentity, identity)
					assertTrue(confirmedIdentities.add(identity), "each physical identity confirms exactly once")
					confirmations++
				}
			}
			assertEquals(0, confirmations, "accepted result and outer registration have not returned")
		}
		val callbacks = mutableListOf<android.webkit.ValueCallback<String>>()
		val view = object : WebView(RuntimeEnvironment.getApplication()) {
			var calls = 0
			override fun evaluateJavascript(script: String, resultCallback: android.webkit.ValueCallback<String>?) {
				calls++
				val accepted = checkNotNull(resultCallback)
				if (calls == 2 && synchronousGetter) {
					callbacks += accepted
					try {
						if (failPrimitive) throw IllegalArgumentException("controlled primitive failure")
						try { accepted.onReceiveValue(checkNotNull(receipt)) }
						catch (failure: Throwable) {
							if (wrapCallbackFailure) throw IllegalArgumentException("controlled primitive wrapper", failure)
							throw failure
						}
						assertEquals(ReaderPortCommandResult.Accepted, ownership.freezeForTransitionActivation(domain))
						drainAcceptedTails()
					} finally {
						assertEquals(0, confirmations, "no exact drain confirmation inside outermost primitive invocation")
						outerReturning = true
					}
				} else callbacks += accepted
			}
		}
		host.addView(view)
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(host)
		type.task7Field("webViewProvider").set(curl, { view })
		type.task7Field("onCanonicalLiveCommitIssued").set(curl, {
			canonical++
			if (throwCanonical) {
				if (freezeCanonical) {
					ownership.freezeForTransitionActivation(domain)
					drainAcceptedTails()
				}
				throw canonicalFailure
			}
			false
		})
		try {
			type.task7Method("requestInitialLivePresentationAuthority", java.lang.Long.TYPE,
				java.lang.Long::class.java).invoke(curl, 701L, null)
			assertEquals(1, view.calls)
			assertEquals(1, callbacks.size)
			callbacks.removeAt(0).onReceiveValue("null")
			val request = assertNotNull(type.task7Field("initialLivePresentationAuthority").get(curl))
			val target = request.javaClass.task7Field("target").get(request) as ReaderPageTurnPresentationTarget.Live
			receipt = org.json.JSONObject().apply {
				put("scope", "live"); put("token", target.token); put("pageIndex", target.pageIndex)
				put("foliateSessionId", target.foliateSessionId); put("rasterGeneration", target.rasterGeneration)
				put("textureGeneration", target.textureGeneration)
				put("foregroundMutationGeneration", target.foregroundMutationGeneration); put("presentationSequence", 1L)
			}.toString()
			assertTrue(readerPageTurnPresentationReceipt(receipt)?.matches(target) == true)
			val baselinePrewarm = prewarm
			val availabilityVersion = ownership.javaClass.task7Field("passiveAvailabilityVersion").getLong(ownership)
			fun publicationPhase() = request.javaClass.task7Field("confirmationPublicationPhase").getInt(request)
			val ack = ReaderPageTurnSettlementAck(target.token, target.pageIndex.toInt(), target.foliateSessionId,
				target.rasterGeneration, target.textureGeneration)
			val invocationFailure = runCatching {
				type.task7Method("confirmInitialLivePresentationAuthority", Integer.TYPE,
					ReaderPageTurnSettlementAck::class.java).invoke(curl, target.pageIndex.toInt(), ack)
			}.exceptionOrNull()
			assertEquals(2, view.calls)
			if (!synchronousGetter) {
				assertNull(invocationFailure)
				assertEquals(0, canonical)
				val accepted = callbacks.single()
				assertSame(canonicalFailure, runCatching { accepted.onReceiveValue(checkNotNull(receipt)) }.exceptionOrNull())
			} else if (!throwCanonical) assertNull(invocationFailure, "ordinary primitive failure retains legacy nonthrowing cleanup")
			assertEquals(if (failPrimitive) 0 else 1, canonical)
			assertTrue(ownership.snapshotFrozenOwnership().isEmpty(), "unstarted safe phases must not pin a completed getter")
			if (freezeCanonical && !failPrimitive) {
				assertSame(request, type.task7Field("initialLivePresentationAuthority").get(curl), "safe remainder stays parked")
				assertEquals(3, publicationPhase(), "throwing Boolean recipient is consumed, overlay/release remain unstarted")
				assertNotNull(request.javaClass.task7Field("claim").get(request), "parked continuation retains its claim")
				assertEquals(ReaderPortCommandResult.Accepted, ownership.restoreAfterTransitionActivation(domain))
			} else assertNull(type.task7Field("initialLivePresentationAuthority").get(curl),
				"throw alone or primitive failure must complete safe disposition without restore")
			assertEquals(if (failPrimitive) 0 else 6, publicationPhase(), "safe overlay and release phases are consumed once")
			assertNull(request.javaClass.task7Field("claim").get(request))
			val disposedAvailabilityVersion = ownership.javaClass.task7Field("passiveAvailabilityVersion").getLong(ownership)
			assertEquals(availabilityVersion + 1L, disposedAvailabilityVersion, "logical release publishes availability exactly once")
			callbacks.single().onReceiveValue(checkNotNull(receipt))
			type.task7Method("confirmInitialLivePresentationAuthority", Integer.TYPE,
				ReaderPageTurnSettlementAck::class.java).invoke(curl, target.pageIndex.toInt(), ack)
			assertEquals(2, view.calls, "duplicate confirmation cannot start another getter")
			assertEquals(if (failPrimitive) 0 else 1, canonical, "duplicate result cannot republish consumed recipients")
			assertEquals(if (failPrimitive) 0 else 6, publicationPhase())
			assertEquals(disposedAvailabilityVersion, ownership.javaClass.task7Field("passiveAvailabilityVersion").getLong(ownership))
			assertEquals(baselinePrewarm + if (throwCanonical || failPrimitive) 0 else 1, prewarm,
				"unknown Boolean outcome must not invent false and request conditional prewarm")
			if (freezeCanonical || synchronousGetter && !throwCanonical && !failPrimitive) assertTrue(confirmations >= 2)
			assertEquals(drainedIdentities, confirmedIdentities, "exact confirmations preserve all accepted physical identities")
			assertEquals(confirmedIdentities.size, confirmations)
			assertNull(type.task7Field("initialLivePresentationAuthority").get(curl))
			assertEquals(0, ownership.snapshot().liveClaims)
			assertTrue(ownership.snapshotFrozenOwnership().isEmpty())
			if (synchronousGetter && throwCanonical) {
				assertIs<java.lang.reflect.InvocationTargetException>(invocationFailure,
					"accepted synchronous callback failure must be observable at the production confirmation boundary")
				assertSame(canonicalFailure, invocationFailure.cause, "primitive wrapping must preserve actual accepted failure causality")
			}
		} finally {
			curl.destroy()
			activity.pause().stop().destroy()
		}
	}

	private val hostFile = File(
		"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
			"KomikkuReaderNativeFrameHost.android.kt"
	)

	@Test
	fun firstAuthoritativeDefaultOrdinalRequestsOneHostPrewarmEdge() {
		var prewarmRequests = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = null,
			currentOrdinal = 0,
			onRequestPrewarm = { prewarmRequests += 1 }
		)
		val controller = fixture.controller
		val baselineRequests = prewarmRequests
		assertFalse(
			controller.javaClass.task7Field("authoritativeLocationReady")
				.getBoolean(controller)
		)

		controller.synchronizeVisualPageIndex(0, "initial-location", null)

		assertEquals(baselineRequests + 1, prewarmRequests)
		assertTrue(
			controller.javaClass.task7Field("authoritativeLocationReady")
				.getBoolean(controller)
		)
		controller.synchronizeVisualPageIndex(0, "repeated-location", null)
		assertEquals(baselineRequests + 1, prewarmRequests)
	}

	@Test
	fun firstAuthoritativeNonDefaultOrdinalRequestsOneHostPrewarmEdge() {
		var prewarmRequests = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = null,
			currentOrdinal = 0,
			onRequestPrewarm = { prewarmRequests += 1 }
		)
		val baselineRequests = prewarmRequests

		fixture.controller.synchronizeVisualPageIndex(2, "initial-location", null)

		assertEquals(baselineRequests + 1, prewarmRequests)
		assertEquals(
			2,
			fixture.controller.javaClass.task7Field("currentOrdinal")
				.getInt(fixture.controller)
		)
	}

	@Test
	fun initialProfileBootstrapWaitsForRasterPreparationBeforeDeckLoad() {
		val fixture = task8CurlAuthorityFixture(
			initialState = null,
			currentOrdinal = 0
		)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		val requestedProfile = controllerClass.task7Field("requestedProfile")
		val profile = assertNotNull(requestedProfile.get(controller) as ReaderPlayLikeCurlRasterProfile?)
		requestedProfile.set(controller, null)
		controllerClass.task7Field("publishedRasterProfile").set(controller, null)
		controllerClass.task7Field("publishedRasterProfileEpoch").set(controller, null)
		assertEquals(
			ReaderPagePreparationPhase.Idle,
			controllerClass.task7Field("preparationPhase").get(controller)
		)
		assertNull(controllerClass.task7Field("rasterAdapter").get(controller))

		controllerClass.task7Method(
			"prepareProfile",
			java.lang.Long.TYPE,
			ReaderPlayLikeCurlRasterProfile::class.java,
			Integer.TYPE
		).invoke(
			controller,
			controllerClass.task7Field("requestGeneration").getLong(controller),
			profile,
			0
		)

		assertSame(profile, requestedProfile.get(controller))
		assertNull(
			controllerClass.task7Field("rasterAdapter").get(controller),
			"The initial foreground deck must wait for passive raster preparation"
		)

		@Suppress("UNCHECKED_CAST")
		val webView = assertNotNull(
			(controllerClass.task7Field("webViewProvider").get(controller) as () -> WebView?)()
		) as Task9RecoveryCommandWebView
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(webView.parent as View)
		try {
			assertTrue(webView.isAttachedToWindow)
			webView.retainPlanCallbacks = true
			controllerClass.task7Field("capabilitiesAvailable").setBoolean(controller, true)
			controller.synchronizeVisualPageIndex(0, "initial-location", null)
			assertNull(controllerClass.task7Field("activePages").get(controller))
			assertTrue(webView.planCallbacks.isEmpty())

			controller.onPreparationStateChanged(
				ReaderPagePreparationState(phase = ReaderPagePreparationPhase.Ready)
			)

			val planCallback = webView.planCallbacks.single()
			webView.planCallbacks.clear()
			planCallback.onReceiveValue(
				"""{"context":{"centerPageIndex":0,"pageCount":3,"layoutMode":"single","step":1,"currentChapterIndex":0,"currentChapterPageStartIndex":0,"currentChapterPageCount":3},"targets":[{"pageIndex":0,"priority":"current","authority":"CurrentLive"}]}"""
			)
			assertNotNull(
				controllerClass.task7Field("rasterAdapter").get(controller),
				"Ready must resume the foreground deck producer through the actual plan callback"
			)
		} finally {
			controller.surfaceView.detach()
			controller.destroy()
			activity.pause().stop().destroy()
		}
	}

	@Test
	fun playLikeCurlPhysicalDeckOwnerFreezesExactDeckAndCallbackBeforeDispatch() {
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			prepareActiveDeck = false
		)
		val controller = fixture.controller
		val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(29L))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.freezeDeckOwnershipForTransitionActivation(domain)
		)
		val frozen = controller.snapshotFrozenDeckOwnership()
		assertEquals(2, frozen.size)
		val deck = frozen.single { it.kind == paige.navic.reader.ReaderTransitionResourceKind.Deck }
		val callback = frozen.single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}
		assertEquals(ReaderLegacyInventorySource.Deck, deck.physicalIdentity.source)
		assertEquals(ReaderLegacyResourceState.RendererOwned, deck.state)
		assertEquals(ReaderLegacyResourceState.Registered, callback.state)

		controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(controller.surfaceView, 301L)

		assertFalse(controller.isAvailable)
		val afterCallback = controller.snapshotFrozenDeckOwnership()
		assertEquals(deck.physicalIdentity, afterCallback.single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.Deck
		}.physicalIdentity)
		val completedCallback = afterCallback.single {
			it.kind == paige.navic.reader.ReaderTransitionResourceKind.CallbackRegistration
		}
		assertEquals(ReaderLegacyResourceState.ReleaseRequested, completedCallback.state)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(
				completedCallback.physicalIdentity,
				confirmed::add
			)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(deck.physicalIdentity, confirmed::add)
		)
		controller.surfaceView.javaClass.task7Method(
			"handleDeckReleased",
			java.lang.Long.TYPE,
			karacken.curl.DeckReleaseReason::class.java
		).invoke(controller.surfaceView, 301L, karacken.curl.DeckReleaseReason.EXPLICIT)
		assertEquals(setOf(completedCallback.physicalIdentity, deck.physicalIdentity), confirmed.toSet())
		assertTrue(controller.snapshotFrozenDeckOwnership().isEmpty())
		assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
		@Suppress("UNCHECKED_CAST")
		val restarts = controller.javaClass.task7Field("frozenPhysicalDeckRestarts")
			.get(controller) as Map<Long, Any>
		val restart = assertNotNull(restarts[301L])
		val restartPages = restart.javaClass.task7Field("pages").get(restart)
		assertFalse(restartPages.javaClass.task7Field("obsolete").getBoolean(restartPages))
		val restartAdmission = restart.javaClass.task7Field("admission").get(restart) as ReaderDeckAdmission
		assertEquals(ReaderDeckAdmissionState.Released, restartAdmission.state)

		val restoration = controller.restoreDeckOwnershipAfterTransitionActivation(domain)
		assertEquals(ReaderPortCommandResult.Accepted, restoration)
		val restoredGeneration = assertNotNull(
			controller.javaClass.task7Field("activeDeckGenerationId").get(controller) as Long?
		)
		assertTrue(restoredGeneration > 301L)
		assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, restoredGeneration))
		controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(controller.surfaceView, restoredGeneration)
		assertTrue(controller.isAvailable)
	}

	@Test
	fun activatedPhysicalDeckFinalizationTransfersAndReleasesSelectedAuthorityOnce() {
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			prepareActiveDeck = false
		)
		val controller = fixture.controller
		val token = ReaderLegacyFreezeToken(37L)
		val domain = ReaderLegacyPhysicalDomain(1L, token)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.freezeDeckOwnershipForTransitionActivation(domain)
		)
		val frozen = controller.snapshotFrozenDeckOwnership()
		val selected = frozen.single { it.kind == ReaderTransitionResourceKind.Deck }
		controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(controller.surfaceView, 301L)
		val callback = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		val confirmedRetirements = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(callback.physicalIdentity, confirmedRetirements::add)
		)
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(41L)
		val imported = ReaderImportedLegacyResourceRegistration(
			physicalIdentity = selected.physicalIdentity,
			registration = ReaderTransitionResourceRegistration(
				ReaderTransitionResourceKey(
					ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
					ReaderTransitionResourceKind.Deck,
					43L
				),
				ReaderResourceRetirementOrder(1L, 2L, 1L)
			)
		)

		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.finalizeDeckOwnershipAfterTransitionActivation(
				token,
				imported,
				confirmedRetirements
			)
		)
		assertTrue(controller.snapshotFrozenDeckOwnership().isEmpty())
		assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
		@Suppress("UNCHECKED_CAST")
		val activatedAuthorities = controller.javaClass.task7Field(
			"activatedPhysicalDeckAuthorities"
		).get(controller) as Map<ReaderLegacyPhysicalIdentity, Any>
		assertEquals(setOf(selected.physicalIdentity), activatedAuthorities.keys)

		val releaseConfirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.releaseActivatedDeckOwnership(imported, releaseConfirmations::add)
		)
		assertIs<ReaderPortCommandResult.Rejected>(
			controller.releaseActivatedDeckOwnership(imported, releaseConfirmations::add)
		)
		controller.surfaceView.javaClass.task7Method(
			"handleDeckReleased",
			java.lang.Long.TYPE,
			karacken.curl.DeckReleaseReason::class.java
		).invoke(controller.surfaceView, 301L, karacken.curl.DeckReleaseReason.EXPLICIT)

		assertEquals(listOf(selected.physicalIdentity), releaseConfirmations)
		assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
		assertTrue(activatedAuthorities.isEmpty())
		assertIs<ReaderPortCommandResult.Rejected>(
			controller.releaseActivatedDeckOwnership(imported, releaseConfirmations::add)
		)
		@Suppress("UNCHECKED_CAST")
		val physicalLeases = controller.javaClass.task7Field("physicalDeckLeases")
			.get(controller) as Map<Long, Any>
		assertTrue(physicalLeases.isEmpty())
	}

	@Test
	fun activatedSelectedDeckAlreadyRetiredConfirmsCoordinatorReleaseSynchronously() {
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			prepareActiveDeck = false
		)
		val controller = fixture.controller
		val token = ReaderLegacyFreezeToken(45L)
		val domain = ReaderLegacyPhysicalDomain(1L, token)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.freezeDeckOwnershipForTransitionActivation(domain)
		)
		val selected = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.Deck
		}
		controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(controller.surfaceView, 301L)
		val callback = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		val confirmedRetirements = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(callback.physicalIdentity, confirmedRetirements::add)
		)
		val imported = ReaderImportedLegacyResourceRegistration(
			physicalIdentity = selected.physicalIdentity,
			registration = ReaderTransitionResourceRegistration(
				ReaderTransitionResourceKey(
					ReaderTransitionResourceOwnerId.AdoptedPredecessor(
						ReaderAdoptedPredecessorSeedId.fromValidatedImport(47L)
					),
					ReaderTransitionResourceKind.Deck,
					49L
				),
				ReaderResourceRetirementOrder(1L, 2L, 1L)
			)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.finalizeDeckOwnershipAfterTransitionActivation(
				token,
				imported,
				confirmedRetirements
			)
		)
		controller.surfaceView.javaClass.task7Method(
			"handleDeckReleased",
			java.lang.Long.TYPE,
			karacken.curl.DeckReleaseReason::class.java
		).invoke(controller.surfaceView, 301L, karacken.curl.DeckReleaseReason.EXPLICIT)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()

		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.releaseActivatedDeckOwnership(imported, confirmations::add)
		)
		assertEquals(listOf(selected.physicalIdentity), confirmations)
		@Suppress("UNCHECKED_CAST")
		val authorities = controller.javaClass.task7Field("activatedPhysicalDeckAuthorities")
			.get(controller) as Map<ReaderLegacyPhysicalIdentity, Any>
		assertTrue(authorities.isEmpty())
	}

	@Test
	fun activatedPhysicalDeckFinalizationRetiresNonselectedRestartLeaseAdmissionAndPages() {
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			prepareActiveDeck = false
		)
		val controller = fixture.controller
		val token = ReaderLegacyFreezeToken(47L)
		val domain = ReaderLegacyPhysicalDomain(1L, token)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.freezeDeckOwnershipForTransitionActivation(domain)
		)
		val deck = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.Deck
		}
		controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(controller.surfaceView, 301L)
		val callback = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		val confirmedRetirements = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(callback.physicalIdentity, confirmedRetirements::add)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(deck.physicalIdentity, confirmedRetirements::add)
		)
		controller.surfaceView.javaClass.task7Method(
			"handleDeckReleased",
			java.lang.Long.TYPE,
			karacken.curl.DeckReleaseReason::class.java
		).invoke(controller.surfaceView, 301L, karacken.curl.DeckReleaseReason.EXPLICIT)
		@Suppress("UNCHECKED_CAST")
		val restarts = controller.javaClass.task7Field("frozenPhysicalDeckRestarts")
			.get(controller) as Map<Long, Any>
		val restart = assertNotNull(restarts[301L])
		val restartPages = assertNotNull(restart.javaClass.task7Field("pages").get(restart))

		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.finalizeDeckOwnershipAfterTransitionActivation(
				token,
				null,
				confirmedRetirements
			)
		)
		assertTrue(restarts.isEmpty())
		assertNull(controller.javaClass.task7Field("activePages").get(controller))
		@Suppress("UNCHECKED_CAST")
		val preparedPageSets = controller.javaClass.task7Field("preparedPageSets")
			.get(controller) as Set<Any>
		assertFalse(restartPages in preparedPageSets)
		@Suppress("UNCHECKED_CAST")
		val generationAdmissions = controller.javaClass.task7Field("generationAdmissions")
			.get(controller) as Map<Long, Any>
		assertTrue(generationAdmissions.isEmpty())
		@Suppress("UNCHECKED_CAST")
		val physicalLeases = controller.javaClass.task7Field("physicalDeckLeases")
			.get(controller) as Map<Long, Any>
		assertTrue(physicalLeases.isEmpty())
		assertIs<ReaderPortCommandResult.Rejected>(
			controller.finalizeDeckOwnershipAfterTransitionActivation(
				token,
				null,
				confirmedRetirements
			)
		)
	}

	@Test
	fun throwingPreparedPagesCloseIsTerminalAndCannotFabricateRetryAuthority() {
		var closeCalls = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			prepareActiveDeck = false,
			onActiveRasterReleased = {
				closeCalls += 1
				error("synthetic post-close callback failure")
			}
		)
		val controller = fixture.controller
		val token = ReaderLegacyFreezeToken(51L)
		val domain = ReaderLegacyPhysicalDomain(1L, token)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.freezeDeckOwnershipForTransitionActivation(domain)
		)
		val deck = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.Deck
		}
		controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
			.invoke(controller.surfaceView, 301L)
		val callback = controller.snapshotFrozenDeckOwnership().single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		val confirmedRetirements = linkedSetOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(callback.physicalIdentity, confirmedRetirements::add)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.drainFrozenDeckOwnership(deck.physicalIdentity, confirmedRetirements::add)
		)
		controller.surfaceView.javaClass.task7Method(
			"handleDeckReleased",
			java.lang.Long.TYPE,
			karacken.curl.DeckReleaseReason::class.java
		).invoke(controller.surfaceView, 301L, karacken.curl.DeckReleaseReason.EXPLICIT)
		@Suppress("UNCHECKED_CAST")
		val restarts = controller.javaClass.task7Field("frozenPhysicalDeckRestarts")
			.get(controller) as Map<Long, Any>
		val restart = assertNotNull(restarts[301L])
		val restartPages = assertNotNull(restart.javaClass.task7Field("pages").get(restart))
		@Suppress("UNCHECKED_CAST")
		val physicalLeases = controller.javaClass.task7Field("physicalDeckLeases")
			.get(controller) as Map<Long, Any>
		@Suppress("UNCHECKED_CAST")
		val preparedPageSets = controller.javaClass.task7Field("preparedPageSets")
			.get(controller) as Set<Any>

		assertEquals(
			ReaderPortCommandResult.Accepted,
			controller.finalizeDeckOwnershipAfterTransitionActivation(
				token,
				null,
				confirmedRetirements
			)
		)
		assertEquals(1, closeCalls)
		assertTrue(physicalLeases.isEmpty())
		assertTrue(restarts.isEmpty())
		assertFalse(restartPages in preparedPageSets)
		assertIs<ReaderPortCommandResult.Rejected>(
			controller.finalizeDeckOwnershipAfterTransitionActivation(
				token,
				null,
				confirmedRetirements
			)
		)
		assertEquals(1, closeCalls, "An ambiguous close must never be physically reissued")
		assertIs<ReaderPortCommandResult.Rejected>(
			controller.restoreDeckOwnershipAfterTransitionActivation(domain)
		)
	}

	@Test
	fun productionRelocationTimeoutFailsExactCommonHandoffAndRetainsNativeAuthority() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 51L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "session-a"
		)
		acknowledgeTask7Relocation(queue, request)
		val handoffHost = Task7VisualHandoffHost()
		val binding = ReaderPresentationBinding(
			request.foliateSessionId, 2L, 3L, 4L,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			preparationGeneration = 7L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 8L, 1200, 800,
			request.rasterGeneration, request.textureGeneration
		)
		var presentation = ReaderPresentationState(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 52L
		)
		val events = mutableListOf<ReaderPresentationEvent>()
		fun publish(event: ReaderPresentationEvent) = readerTestPresentationReceipt(
			event = event,
			postState = readerPresentationReduce(presentation, event).also {
				events += event
				presentation = it.state
			}.state
		)
		val coordinator = task7CommonRelocationCoordinator(
			queue = queue,
			request = request,
			host = handoffHost,
			publish = ::publish
		)

		assertTrue(coordinator.onAcknowledged(request))
		val requested = assertIs<ReaderPresentationEvent.WebViewHandoffRequested>(events.single())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, requested.direction)
		val transition = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentation).frameOwner
		)

		handoffHost.timeOut()

		val terminal = assertIs<ReaderPresentationEvent.LiveEngineHandoffTimedOut>(events.last())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, terminal.direction)
		assertEquals(transition.token, terminal.token)
		assertEquals(transition.binding, terminal.binding)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.TimedOut,
				retryable = true,
				cancellable = true
			),
			readerPresentationDecision(presentation).diagnosticPresentation
		)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentation).frameOwner
		)
		assertEquals(request, queue.head())

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		val retry = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertFalse(retry.token == transition.token)
		assertTrue(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertFalse(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertFalse(handoffHost.takeVisual().deliver())
		handoffHost.attached = false
		handoffHost.deliverVisual()

		val retryTerminal = assertIs<ReaderPresentationEvent.LiveEngineExposureFailed>(events.last())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, retryTerminal.direction)
		assertEquals(retry.token, retryTerminal.token)
		assertEquals(retry.binding, retryTerminal.binding)
		assertEquals(
			ReaderPresentationFailureReason.LiveEngineUnavailable,
			retryTerminal.reason
		)
		assertEquals(
			ReaderDiagnosticPresentation.Failure(
				ReaderPresentationFailureReason.LiveEngineUnavailable,
				retryable = true,
				cancellable = true
			),
			readerPresentationDecision(presentation).diagnosticPresentation
		)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentation).frameOwner
		)
	}

	@Test
	fun relocationAndModePublishersKeepLiveFrameSequenceMonotonicWithinPublication() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 52L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "shared-sequence-session"
		)
		acknowledgeTask7Relocation(queue, request)
		val binding = ReaderPresentationBinding(
			request.foliateSessionId, 2L, 3L, 4L,
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration,
			preparationGeneration = 7L
		)
		val predecessorProof = ReaderNativePagePresentationProof(
			binding, null, 8L, 1200, 800,
			request.rasterGeneration, request.textureGeneration
		)
		var presentation = ReaderPresentationState(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(predecessorProof)
			),
			binding = binding,
			nextTokenValue = 53L
		)
		val events = mutableListOf<ReaderPresentationEvent>()
		fun publish(event: ReaderPresentationEvent): paige.navic.reader.ReaderPresentationEventReceipt {
			val reduction = readerPresentationReduce(presentation, event)
			events += event
			presentation = reduction.state
			return readerTestPresentationReceipt(
				event = event,
				postState = reduction.state,
				disposition = reduction.disposition
			)
		}

		val liveFrameSequences = ReaderLiveEnginePresentedFrameSequenceAuthority()
		val relocationHost = Task7VisualHandoffHost()
		val coordinator = task7CommonRelocationCoordinator(
			queue = queue,
			request = request,
			host = relocationHost,
			publish = ::publish,
			presentedFrameSequenceSource = liveFrameSequences::next
		)

		assertTrue(coordinator.onAcknowledged(request))
		relocationHost.deliverVisual()
		relocationHost.presentFrame()
		val firstLive = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			presentation.authority
		).frame.proof
		assertEquals(1L, firstLive.presentedFrameSequence)
		assertNull(queue.head())

		publish(
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			readerPresentationDecision(presentation).requiredTransition
		)
		publish(
			ReaderPresentationEvent.NativePagePresented(
				predecessorProof.copy(
					transitionToken = nativeTransition.token,
					presentedFrame = 9L
				)
			)
		)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.authority)

		val modeHost = Task7VisualHandoffHost()
		val modeBridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding),
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(
				host = modeHost,
				presentedFrameSequenceSource = liveFrameSequences::next
			),
			liveEngineExposureRequired = { true },
			onEvent = ::publish
		)
		modeBridge.update(readerPresentationDecision(presentation))
		modeHost.deliverVisual()
		modeHost.presentFrame()

		val secondLive = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			presentation.authority
		).frame.proof
		assertEquals(2L, secondLive.presentedFrameSequence)
		assertTrue(secondLive.token.value > firstLive.token.value)
		assertEquals(
			listOf(1L, 2L),
			events.filterIsInstance<ReaderPresentationEvent.LiveEngineExposureCommitted>()
				.map { it.proof.presentedFrameSequence }
		)
	}

	@Test
	fun nativeTurnCausallyRebindsPendingLiveHandoffBeforeAcknowledgementRetry() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 53L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "pending-turn-session"
		)
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = request.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				3L
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				4L
			),
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration
		)
		val proofA = ReaderNativePagePresentationProof(
			bindingA, null, 8L, 1200, 800, 9L, 19L
		)
		val settledA = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofA)
			),
			binding = bindingA,
			nextTokenValue = 54L
		)
		val pendingA = readerPresentationReduce(
			settledA,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		).state
		val transitionA = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(pendingA).requiredTransition
		)
		val nativeOwnerA = ReaderPresentationFrameOwner.NativePage(proofA)
		assertEquals(nativeOwnerA, readerPresentationDecision(pendingA).frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(
			readerPresentationDecision(pendingA).inputPolicy
		)

		val reboundReduction = readerPresentationReduce(
			pendingA,
			ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		)
		val authoritative = Task7PresentationStore(reboundReduction.state)
		val staleHost = Task7PresentationStore(pendingA)
		val recovery = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val visualHost = Task7VisualHandoffHost()
		val coordinator = task7CommonRelocationCoordinator(
			queue = queue,
			request = request,
			host = visualHost,
			publish = authoritative::publish,
			requestPresentationEvent = staleHost::publish,
			publishRecovery = { _, reason -> recovery += reason },
			publishLiveEngineHandoffTerminal = { error("Unexpected terminal: $it") }
		)

		acknowledgeTask7Relocation(queue, request)
		assertTrue(
			coordinator.onAcknowledged(request),
			"Exact acknowledged B should defer instead of hitting the stale-A start check"
		)
		assertTrue(recovery.isEmpty())

		assertEquals(ReaderPresentationEventDisposition.Accepted, reboundReduction.disposition)
		val pendingB = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			authoritative.state.authority
		)
		assertEquals(transitionA.token, pendingB.token)
		assertEquals(bindingB, pendingB.binding)
		assertEquals(nativeOwnerA, pendingB.retainedFrame)
		assertEquals(nativeOwnerA, readerPresentationDecision(authoritative.state).frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(
			readerPresentationDecision(authoritative.state).inputPolicy
		)
		assertEquals(pendingA.nextTokenValue, authoritative.state.nextTokenValue)

		val delayedA = readerPresentationReduce(
			authoritative.state,
			ReaderPresentationEvent.LiveEngineExposureCommitted(
				ReaderLiveEnginePresentationProof(transitionA.token, bindingA, 1L)
			)
		)
		assertEquals(ReaderPresentationEventDisposition.Stale, delayedA.disposition)
		assertEquals(authoritative.state, delayedA.state)

		staleHost.state = authoritative.state
		val transitionB = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(authoritative.state).requiredTransition
		)
		assertTrue(coordinator.synchronizeCommonPresentationHandoff(transitionB))
		visualHost.deliverVisual()
		visualHost.presentFrame()

		val exposedB = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			authoritative.state.authority
		).frame.proof
		assertEquals(transitionA.token, exposedB.token)
		assertEquals(bindingB, exposedB.binding)
		assertNull(queue.head())
		assertTrue(recovery.isEmpty())
	}

	@Test
	fun causalLiveHandoffRebindKeepsExistingVisualOwnerAndDeadline() {
		val fixture = BridgeFixture()
		val bindingA = fixture.binding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.binding.foliateSessionId,
				3L
			)
		)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				fixture.binding.foliateSessionId,
				4L
			)
		)
		val nativeA = fixture.nativeProof.copy(binding = bindingA)
		val settledA = fixture.nativeState.copy(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeA)
			),
			binding = bindingA
		)
		val pendingA = readerPresentationReduce(
			settledA,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		).state
		val authorityA = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			pendingA.authority
		)
		val pendingB = pendingA.copy(
			authority = authorityA.copy(binding = bindingB),
			binding = bindingB
		)
		val presentation = Task7PresentationStore(pendingA)
		val commitHost = FakeReaderPresentationCommitHost(bindingA)
		val visualHost = Task7VisualHandoffHost()
		val deadlines = HostBridgeDeadlineScheduler()
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(visualHost),
			liveEngineExposureRequired = { true },
			transitionTimeoutScheduler = deadlines,
			transitionNowMillis = { 0L },
			onEvent = presentation::publish
		)

		bridge.update(readerPresentationDecision(presentation.state))
		val existingVisualRequest = visualHost.takeVisual()
		assertEquals(1, deadlines.postCount)
		assertEquals(0, visualHost.delayedPostCount)
		val ownerGeneration = visualHost.ownerGeneration

		presentation.state = pendingB
		commitHost.currentBinding = bindingB
		bridge.update(readerPresentationDecision(pendingB))

		assertEquals(1, deadlines.postCount, "Causal rebind restarted the whole deadline")
		assertEquals(0, visualHost.delayedPostCount)
		assertEquals(ownerGeneration, visualHost.ownerGeneration)
		assertTrue(existingVisualRequest.deliver())
		visualHost.presentFrame()
		val exposedB = assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
			presentation.state.authority
		).frame.proof
		assertEquals(authorityA.token, exposedB.token)
		assertEquals(bindingB, exposedB.binding)
		bridge.dispose()
	}

	@Test
	fun rootModeHandoffAndCausalRelocationJoinOneAuthorityTransaction() {
		val queue = ReaderPageRelocationQueue()
		val request = enqueueTask7Relocation(
			queue = queue,
			gestureId = 54L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "root-owned-relocation-session"
		)
		assertEquals(request, queue.commandToDispatch())
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = request.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				3L
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val bindingB = bindingA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				request.foliateSessionId,
				4L
			),
			rasterGeneration = request.rasterGeneration,
			textureGeneration = request.textureGeneration
		)
		val proofA = ReaderNativePagePresentationProof(
			bindingA, null, 8L, 1200, 800, 9L, 19L
		)
		val settledA = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofA)
			),
			binding = bindingA,
			nextTokenValue = 55L
		)
		val fixture = Task7ProductionCompositionFixture(queue, request, settledA)

		val modeRequest = ReaderPresentationEvent.WebViewHandoffRequested(
			ReaderLiveEngineHandoffDirection.NativeToLiveEngine
		)
		assertTrue(fixture.publish(modeRequest).authorizes(modeRequest))
		assertEquals(1, fixture.visualHost.visualPostCount)
		assertEquals(1, fixture.rootDeadlines.postCount)
		assertEquals(0, fixture.visualHost.delayedPostCount)

		val relocation = ReaderPresentationEvent.FoliateRelocated(bindingB, acknowledgement = null)
		assertTrue(fixture.setPageTurnVisualLocation(bindingB).authorizes(relocation))

		assertEquals(
			1,
			fixture.visualHost.visualPostCount,
			"The joined root transaction registered a second visual callback"
		)
		assertEquals(
			1,
			fixture.rootDeadlines.postCount,
			"The joined root transaction restarted its deadline"
		)
		assertEquals(0, fixture.visualHost.delayedPostCount)
		fixture.deliverRootFrame()
		val liveCommits = fixture.receipts.filter {
			it.event is ReaderPresentationEvent.LiveEngineExposureCommitted
		}
		assertEquals(1, liveCommits.size)
		assertEquals(ReaderPresentationEventDisposition.Accepted, liveCommits.single().disposition)
		assertEquals(
			bindingB,
			assertIs<ReaderPresentationAuthority.LiveEngineExposed>(
				fixture.controller.state.presentation.authority
			).frame.proof.binding
		)
		assertEquals(listOf(request), fixture.releasedClaims)
		assertNull(queue.head())
		assertTrue(fixture.recoveries.isEmpty())
		assertEquals(0, fixture.rootHandoff.pendingCallbackCount())
		assertEquals(0, fixture.rootHandoff.pendingCapacityRetryEdgeCount())
		assertEquals(0, fixture.coordinator.pendingCallbackCount())
		assertEquals(0, fixture.coordinator.pendingCapacityRetryEdgeCount())
		fixture.close()
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun productionReadinessCallbacksDoNotDispatchCurlSuccessorBeforeNativeProof() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val controller = viewerClass.task7Field("playLikeCurlController")
			.get(viewer) as ReaderPlayLikeCurlFoliateController
		val queue = controller.javaClass.task7Field("relocationQueue")
			.get(controller) as ReaderPageRelocationQueue
		val foregroundOwnership = controller.javaClass.task7Field(
			"foregroundWebViewOwnership"
		).get(controller) as ReaderForegroundWebViewOwnership
		val liveDispatch = controller.javaClass.task7Field(
			"relocationLiveDispatchCoordinator"
		).get(controller) as ReaderPageRelocationLiveDispatchCoordinator
		val first = enqueueTask7Relocation(
			queue = queue,
			gestureId = 91L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "curl-readiness-settlement-session"
		)
		val second = enqueueTask7Relocation(
			queue = queue,
			gestureId = 92L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			foliateSessionId = first.foliateSessionId
		)
		assertEquals(first, queue.commandToDispatch())
		assertTrue(
			queue.acknowledge(
				first.token.value,
				first.destinationOrdinal,
				first.foliateSessionId,
				first.rasterGeneration,
				first.textureGeneration
			)
		)
		assertTrue(queue.completeHandoff(first.token.value))
		val sourceBinding = ReaderPresentationBinding(
			foliateSessionId = first.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.sourceOrdinal.toLong()
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val destinationBinding = sourceBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.destinationOrdinal.toLong()
			),
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val acknowledgement = ReaderPageTurnSettlementAck(
			token = first.token.value,
			pageIndex = first.destinationOrdinal,
			foliateSessionId = first.foliateSessionId,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val sourceProof = ReaderNativePagePresentationProof(
			binding = sourceBinding,
			transitionToken = null,
			presentedFrame = 90L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
			textureGeneration = requireNotNull(sourceBinding.textureGeneration)
		)
		val settled = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(sourceProof)
			),
			binding = sourceBinding
		)
		val claimed = readerPresentationReduce(
			settled,
			requireNotNull(
				readerCurlClaimEvent(
					readerPresentationDecision(settled),
					first.gestureId
				)
			)
		).state
		val awaitingFoliate = readerPresentationReduce(
			claimed,
			ReaderPresentationEvent.CurlTerminal(
				token = assertIs<ReaderPresentationAuthority.CurlGesture>(
					claimed.authority
				).frame.frame.token,
				binding = sourceBinding,
				expectedAcknowledgement = acknowledgement
			)
		).state
		val awaitingNative = readerPresentationReduce(
			awaitingFoliate,
			ReaderPresentationEvent.FoliateRelocated(
				binding = destinationBinding,
				acknowledgement = acknowledgement
			)
		).state
		assertEquals(
			ReaderCurlSettlementStage.AwaitingNativePresentation,
			assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
				awaitingNative.authority
			).stage
		)

		val awaitingNativeDecision = readerPresentationDecision(awaitingNative)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			awaitingNativeDecision.requiredTransition
		)
		controller.setEnabled(true)
		controller.setFoliateSessionId(first.foliateSessionId)
		controller.synchronizePresentationDecision(awaitingNativeDecision)
		assertNotNull(
			foregroundOwnership.tryAcquirePassive(sessionId = 1L) { _ -> }
		)
		val secondClaim = foregroundOwnership.acquireLive(second.gestureId)
		assertTrue(liveDispatch.transfer(second, secondClaim))

		controller.onHostContentReady()
		assertEquals(second, queue.head())
		assertFalse(queue.hasDispatchedHead())
		assertFalse(liveDispatch.isCurrent(second))

		controller.onWebViewAttachmentChanged(true)
		assertEquals(second, queue.head())
		assertFalse(queue.hasDispatchedHead())
		assertFalse(liveDispatch.isCurrent(second))

		assertEquals(second, queue.commandToDispatch())
		assertFalse(liveDispatch.dispatch(second))
		assertFalse(liveDispatch.isCurrent(second))

		val nativeProof = ReaderNativePagePresentationProof(
			binding = destinationBinding,
			transitionToken = nativeTransition.token,
			presentedFrame = 93L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val staleProof = readerPresentationReduce(
			awaitingNative,
			ReaderPresentationEvent.NativePagePresented(
				nativeProof.copy(
					transitionToken = ReaderPresentationToken(
						nativeTransition.token.value + 1L
					),
					presentedFrame = nativeProof.presentedFrame + 1L
				)
			)
		)
		assertEquals(ReaderPresentationEventDisposition.Stale, staleProof.disposition)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(staleProof.state)
		)
		assertFalse(liveDispatch.dispatch(second))

		val nativeEvent = ReaderPresentationEvent.NativePagePresented(nativeProof)
		val exactProof = readerPresentationReduce(awaitingNative, nativeEvent)
		assertEquals(ReaderPresentationEventDisposition.Accepted, exactProof.disposition)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(exactProof.state)
		)
		assertTrue(liveDispatch.dispatch(second))
		val duplicateProof = readerPresentationReduce(exactProof.state, nativeEvent)
		assertEquals(
			ReaderPresentationEventDisposition.Idempotent,
			duplicateProof.disposition
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(duplicateProof.state)
		)
		assertFalse(liveDispatch.dispatch(second))

		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun recoveredDeckCallbacksRetainSelectedAndReleaseActualPendingOwnershipOnce() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val controller = viewerClass.task7Field("playLikeCurlController")
			.get(viewer) as ReaderPlayLikeCurlFoliateController
		val controllerClass = controller.javaClass
		val surface = controller.surfaceView
		task8PrepareSurface(surface)
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = "recovered-callback-fixture",
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 3,
			rasterGeneration = 0L
		)
		controllerClass.task7Field("requestedProfile").set(controller, profile)
		controllerClass.task7Field("currentOrdinal").setInt(controller, 1)
		val selectedGeneration = 101L
		val selectedBinding = ReaderPresentationBinding(
			foliateSessionId = "recovered-callback-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"recovered-callback-session",
				4L
			),
			rasterGeneration = profile.rasterGeneration,
			textureGeneration = selectedGeneration,
			preparationGeneration = 7L
		)
		val selectedProof = ReaderNativePagePresentationProof(
			binding = selectedBinding,
			transitionToken = null,
			presentedFrame = 8L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = profile.rasterGeneration,
			textureGeneration = selectedGeneration
		)
		val selectedState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(selectedProof)
			),
			binding = selectedBinding
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(selectedState)
		)
		controller.onHostResumedChanged(true)
		controller.onWebViewAttachmentChanged(true)
		controllerClass.task7Field("nextRasterProfileEpoch").setLong(
			controller,
			selectedBinding.profileGeneration
		)
		controllerClass.task7Method(
			"publishRasterProfileEpoch",
			ReaderPlayLikeCurlRasterProfile::class.java
		).invoke(controller, profile)
		var common = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 1L,
				presentation = selectedState
			)
		)
		val reporter = viewerClass.task7Field("presentationBindingReporter")
			.get(viewer) as ReaderPresentationBindingReporter
		viewerClass.task7Method(
			"preparePresentationEpoch",
			paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
			ReaderPresentationState::class.java,
			java.lang.Boolean.TYPE,
			java.lang.Boolean.TYPE,
			java.lang.Boolean.TYPE
		).invoke(
			viewer,
			common.presentationVersion,
			common.state.presentation,
			false,
			true,
			false
		)
		assertTrue(reporter.bindPublication(selectedBinding))
		val publish: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt? = { event ->
			val step = common.onPresentationEvent(event)
			common = step.controller
			controller.synchronizePresentationDecision(
				readerPresentationDecision(common.state.presentation)
			)
			step.presentationReceipt
		}
		viewerClass.task7Field("onPresentationEvent").set(viewer, publish)
		viewerClass.task7Field("onAuthoritativePresentationHostEffect").set(
			viewer,
			{ _: ReaderPresentationHostEffect -> }
		)
		val predecessorEvent = ReaderPresentationEvent.NativePagePresented(selectedProof)
		val predecessorReceipt = assertNotNull(
			viewerClass.task7Method(
				"dispatchPresentationEvent",
				ReaderPresentationEvent::class.java
			).invoke(viewer, predecessorEvent) as ReaderPresentationEventReceipt?
		)
		assertTrue(predecessorReceipt.authorizes(predecessorEvent))
		assertEquals(selectedBinding, reporter.lastReportedBinding)
		assertEquals(
			readerPresentationDecision(predecessorReceipt.postState),
			controllerClass.task7Field("commonPresentationDecision").get(controller)
		)
		val cleanup = controllerClass.task7Field("rendererCleanupRetryCoordinator")
			.get(controller) as ReaderRendererCleanupRetryCoordinator
		val owners = controllerClass.task7Field("generationOwners")
			.get(controller) as MutableMap<Long, Any>
		val listener = surface.pageSurfaceListener
		var selectedRasterReleaseCount = 0

		controller.onPreparationStateChanged(
			ReaderPagePreparationState(
				phase = ReaderPagePreparationPhase.Preparing,
				preparationGeneration = 7L
			)
		)
		val selectedPages = task8SubmitRecoveredDeck(
			controller = controller,
			generationId = selectedGeneration,
			role = ReaderDeckSubmissionRole.Active,
			profile = profile,
			onRasterReleased = { selectedRasterReleaseCount += 1 }
		)
		assertTrue(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		controller.retryPreparation(8L)

		listener.onDeckPrepared(selectedGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertIs<ReaderRendererCleanupRequest.StaleGeneration>(
			cleanup.pendingRequest(selectedGeneration)
		)
		assertSame(selectedPages, owners[selectedGeneration])
		assertTrue(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		assertEquals(0, selectedRasterReleaseCount)
		listener.onDeckPrepared(selectedGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertSame(selectedPages, owners[selectedGeneration])
		assertTrue(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		assertEquals(0, selectedRasterReleaseCount)

		val pendingGeneration = 102L
		var pendingRasterReleaseCount = 0
		val curlClaim = assertNotNull(
			readerCurlClaimEvent(
				readerPresentationDecision(common.state.presentation),
				gestureId = 72L
			)
		)
		val curlClaimReceipt = assertNotNull(
			viewerClass.task7Method(
				"dispatchPresentationEvent",
				ReaderPresentationEvent::class.java
			).invoke(viewer, curlClaim) as ReaderPresentationEventReceipt?
		)
		assertTrue(curlClaimReceipt.authorizes(curlClaim))
		task8BeginSurfaceSettlement(surface)
		task8SubmitRecoveredDeck(
			controller = controller,
			generationId = pendingGeneration,
			role = ReaderDeckSubmissionRole.Pending,
			profile = profile,
			onRasterReleased = { pendingRasterReleaseCount += 1 }
		)
		assertTrue(task8SurfaceOwnsGeneration(surface, pendingGeneration))
		val visibilityLost = readerPresentationReduce(
			curlClaimReceipt.postState,
			ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.VisibilityLost
			)
		)
		assertEquals(ReaderPresentationEventDisposition.Accepted, visibilityLost.disposition)
		controller.synchronizePresentationDecision(visibilityLost.decision)

		listener.onDeckPrepared(pendingGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertFalse(owners.containsKey(pendingGeneration))
		assertFalse(task8SurfaceOwnsGeneration(surface, pendingGeneration))
		assertNull(cleanup.pendingRequest(pendingGeneration))
		assertEquals(1, pendingRasterReleaseCount)
		listener.onDeckPrepared(pendingGeneration)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertEquals(1, pendingRasterReleaseCount)
		assertSame(selectedPages, owners[selectedGeneration])
		assertEquals(0, selectedRasterReleaseCount)

		val curlBinding = selectedBinding.copy(preparationGeneration = 8L)
		val liveProof = ReaderLiveEnginePresentationProof(
			token = ReaderPresentationToken(73L),
			binding = curlBinding,
			presentedFrameSequence = 10L
		)
		controller.synchronizePresentationDecision(
			readerPresentationDecision(
				ReaderPresentationState(
					authority = ReaderPresentationAuthority.LiveEngineExposed(
						ReaderPresentationFrameOwner.LiveEngine(liveProof)
					),
					binding = curlBinding
				)
			)
		)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertFalse(owners.containsKey(selectedGeneration))
		assertFalse(task8SurfaceOwnsGeneration(surface, selectedGeneration))
		assertNull(cleanup.pendingRequest(selectedGeneration))
		assertEquals(1, selectedRasterReleaseCount)

		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun missingCommonAuthorityRejectsPhysicalTouchClaimAndCleansPreclaimOnce() {
		var recoveredReleaseCount = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = null,
			onActiveRasterReleased = { recoveredReleaseCount += 1 }
		)
		assertFalse(fixture.controller.isAvailable)
		assertFalse(task8SurfaceOwnsGeneration(fixture.controller.surfaceView, 301L))
		fixture.controller.releaseUnsubmittedRecoveredDeck(301L)
		assertEquals(1, recoveredReleaseCount)
		assertFalse(
			(fixture.controller.javaClass.task7Field("generationOwners")
				.get(fixture.controller) as Map<*, *>).containsKey(301L)
		)
		val gestureId = 201L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)

		val result = fixture.controller.onPageTouchEvent(down, gestureId)

		assertEquals(ReaderPageCurlDispatchResult.TerminalPublished, result)
		assertTrue(fixture.store.presentationEvents.isEmpty())
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertNull(fixture.controller.javaClass.task7Field("activeGestureId").get(fixture.controller))
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun rejectedPreclaimTerminalCleansLocalOwnershipWhenLegacyPublicationRejects() {
		var recoveredReleaseCount = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = null,
			acceptLocalTerminals = false,
			onActiveRasterReleased = { recoveredReleaseCount += 1 }
		)
		assertFalse(fixture.controller.isAvailable)
		assertFalse(task8SurfaceOwnsGeneration(fixture.controller.surfaceView, 301L))
		fixture.controller.releaseUnsubmittedRecoveredDeck(301L)
		assertEquals(1, recoveredReleaseCount)
		assertFalse(
			(fixture.controller.javaClass.task7Field("generationOwners")
				.get(fixture.controller) as Map<*, *>).containsKey(301L)
		)
		fixture.controller.javaClass.task7Field("attached")
			.setBoolean(fixture.controller, false)
		val gestureId = 205L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)

		val result = fixture.controller.onPageTouchEvent(down, gestureId)

		assertEquals(ReaderPageCurlDispatchResult.TerminalPublished, result)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertTrue(fixture.store.presentationEvents.isEmpty())
		assertNull(fixture.controller.javaClass.task7Field("activeGestureId").get(fixture.controller))
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun rejectedCommonTouchAdmissionCancelsExactRendererClaimWithoutAuthorityTerminal() {
		val initial = task8SettledCurlSourceState()
		val fixture = task8CurlAuthorityFixture(
			initialState = initial,
			rejectClaims = true
		)
		val gestureId = 202L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)

		val result = fixture.controller.onPageTouchEvent(down, gestureId)

		assertEquals(ReaderPageCurlDispatchResult.TerminalPublished, result)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlClaimed
		})
		assertEquals(0, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertEquals(initial, fixture.store.state)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun acceptedTouchClaimRejectsStaleTailAndPublishesExactTerminalOnce() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val gestureId = 203L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		assertEquals(
			ReaderPageCurlDispatchResult.Accepted,
			fixture.controller.onPageTouchEvent(down, gestureId)
		)
		assertIs<ReaderPresentationAuthority.CurlGesture>(
			fixture.store.state?.authority
		)

		val staleCancel = task8MotionEvent(MotionEvent.ACTION_CANCEL)
		assertEquals(
			ReaderPageCurlDispatchResult.TerminalPublished,
			fixture.controller.onPageTouchEvent(staleCancel, gestureId + 1L)
		)
		assertTrue(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		assertTrue(fixture.store.localTerminalGestureIds.isEmpty())
		assertEquals(0, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})

		assertEquals(
			ReaderPageCurlDispatchResult.TerminalPublished,
			fixture.controller.onPageTouchEvent(staleCancel, gestureId + 1L)
		)
		assertTrue(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		assertTrue(fixture.store.localTerminalGestureIds.isEmpty())

		val exactCancel = task8MotionEvent(MotionEvent.ACTION_CANCEL)
		assertEquals(
			ReaderPageCurlDispatchResult.Accepted,
			fixture.controller.onPageTouchEvent(exactCancel, gestureId)
		)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlClaimed
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
			fixture.store.state?.authority
		)
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))

		fixture.controller.onPageTouchEvent(exactCancel, gestureId)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		down.recycle()
		staleCancel.recycle()
		exactCancel.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9CurlRetryRequestsCorrelatedCurrentLiveSnapshotRatherThanNavigation() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val controller = fixture.controller
		@Suppress("UNCHECKED_CAST")
		val webView = (controller.javaClass.task7Field("webViewProvider").get(controller) as () -> WebView?)()!!
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(webView.parent as View)
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(requireNotNull(fixture.store.state?.binding)) { decision ->
				controller.synchronizePresentationDecision(decision)
			}, onEvent = fixture.store::publish
		)
		try {
			assertEquals(ReaderPageTurnStartResult.Settling,
				controller.start(222L, PageChange.NEXT) { _, _ -> true })
			controller.cancelGesture(222L)
			bridge.update(readerPresentationDecision(requireNotNull(fixture.store.state)))
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			val retry = requireNotNull(fixture.store.publish(ReaderPresentationEvent.Retry))
			bridge.update(readerPresentationDecision(retry.postState))
			controller.retryPreparation(1L)
			val script = assertNotNull(org.robolectric.Shadows.shadowOf(webView).lastEvaluatedJavascript)
			assertTrue(script.contains("diagnosticLocationSnapshot"))
			assertFalse(script.contains("goToVisualPage"))
			val command = org.json.JSONObject(script.substringAfter("dispatch?.(").substringBeforeLast(")"))
			val correlation = command.getString("reason")
			val retained = readerPresentationDecision(retry.postState).frameOwner
			val retiredAck = paige.navic.reader.ReaderPageTurnSettlementAck("retired", 0,
				"critical-4-session", 0L, 301L)
			controller.synchronizeVisualPageIndex(0, "$correlation-stale", retiredAck)
			assertEquals(1, controller.javaClass.task7Field("currentOrdinal").getInt(controller))
			controller.synchronizeVisualPageIndex(0, correlation, retiredAck)
			assertEquals(0, controller.javaClass.task7Field("currentOrdinal").getInt(controller))
			assertEquals(retained, readerPresentationDecision(requireNotNull(fixture.store.state)).frameOwner)
			assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
			controller.synchronizeVisualPageIndex(2, correlation, retiredAck)
			assertEquals(0, controller.javaClass.task7Field("currentOrdinal").getInt(controller))
		} finally {
			bridge.dispose()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9DispatchedTimeoutRecoveryUsesSourceReportedDestination() =
		task9DispatchedTimeoutRecovery(suspendBeforeObservation = false)

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9DispatchedRecoveryRestoreReissuesSnapshotWithinRemainingDeadline() =
		task9DispatchedTimeoutRecovery(suspendBeforeObservation = true)

	private fun task9DispatchedTimeoutRecovery(suspendBeforeObservation: Boolean) {
		var releases = 0
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState(),
			onActiveRasterReleased = { releases += 1 })
		val controller = fixture.controller
		@Suppress("UNCHECKED_CAST")
		val webView = (controller.javaClass.task7Field("webViewProvider").get(controller) as () -> WebView?)()!!
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		activity.get().setContentView(webView.parent as View)
		assertTrue(webView.isAttachedToWindow)
		// The imported synthetic raster is at window origin. Keep the attached
		// fixture host in that same coordinate space, outside activity chrome.
		val attachedHost = webView.parent as View
		val hostOrigin = IntArray(2).also(attachedHost::getLocationInWindow)
		attachedHost.translationX = -hostOrigin[0].toFloat()
		attachedHost.translationY = -hostOrigin[1].toFloat()
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(requireNotNull(fixture.store.state?.binding)) { decision ->
				controller.synchronizePresentationDecision(decision)
			}, onEvent = fixture.store::publish
		)
		// The shared fixture imports renderer generation 301. The next real submission
		// must use its successor rather than the controller's untouched initial counter.
		controller.javaClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		// Import the initial state's resolved profile through the production publisher.
		val sourceBinding = requireNotNull(fixture.store.state?.binding)
		var reportedDeck: ReaderPagePreparedActiveDeck? = null
		fixture.preparedDeckRelay.sink = { reportedDeck = it }
		controller.javaClass.task7Field("nextRasterProfileEpoch")
			.setLong(controller, sourceBinding.profileGeneration)
		controller.javaClass.task7Method("publishRasterProfileEpoch", ReaderPlayLikeCurlRasterProfile::class.java)
			.invoke(controller, controller.javaClass.task7Field("requestedProfile").get(controller))
		val preparation = ReaderPageRasterPreparationController(
			host = attachedHost as android.view.ViewGroup, webViewProvider = { webView })
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		try {
			assertEquals(ReaderPageCurlDispatchResult.Accepted, controller.onPageTouchEvent(down, 221L))
			val surface = controller.surfaceView
			task8BeginSurfaceSettlement(surface)
			surface.pageSurfaceListener.onSettlementStarted(221L, 301L, "source", "target", PageChange.NEXT)
			val pendingGeneration = assertNotNull(
				controller.javaClass.task7Field("pendingDeckGenerationId").get(controller) as Long?
			)
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE).invoke(surface, pendingGeneration)
			surface.pageSurfaceListener.onSettlementCompleted(221L, 301L, "target", 2, PageChange.NEXT)
			val queue = controller.javaClass.task7Field("relocationQueue").get(controller) as ReaderPageRelocationQueue
			val request = assertNotNull(queue.head())
			assertTrue(queue.hasDispatchedHead())
			val pending = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertEquals(ReaderCurlSettlementStage.AwaitingFoliate,
				assertIs<ReaderPresentationAuthority.CurlSettlementPending>(pending.authority).stage)
			val retained = pending.frameOwner
			val alpha = surface.alpha
			bridge.update(pending)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			val failed = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(failed.diagnosticPresentation).reason)
			assertEquals(retained, failed.frameOwner)
			assertEquals(alpha, surface.alpha)
			assertTrue(task8SurfaceOwnsGeneration(surface, 301L))
			assertEquals(0, releases)
			assertEquals(0, queue.occupiedCount())
			val oldAck = paige.navic.reader.ReaderPageTurnSettlementAck(request.token.value,
				request.destinationOrdinal, request.foliateSessionId, request.rasterGeneration, request.textureGeneration)
			controller.synchronizeVisualPageIndex(request.destinationOrdinal, "late", oldAck)
			assertEquals(failed, readerPresentationDecision(requireNotNull(fixture.store.state)))
			assertEquals(alpha, surface.alpha)

			val retry = requireNotNull(fixture.store.publish(ReaderPresentationEvent.Retry))
			bridge.update(readerPresentationDecision(retry.postState))
			var freshGeneration: Long? = null
			assertTrue(controller.requestPresentationRecoverySnapshot {
				freshGeneration = preparation.retryPreparation(sourceBinding.preparationGeneration)
				controller.retryPreparation(assertNotNull(freshGeneration))
			})
			val script = assertNotNull(org.robolectric.Shadows.shadowOf(webView).lastEvaluatedJavascript)
			var correlation = org.json.JSONObject(script.substringAfter("dispatch?.(").substringBeforeLast(")"))
				.getString("reason")
			if (suspendBeforeObservation) {
				val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				looper.idleFor(java.time.Duration.ofSeconds(4))
				bridge.update(readerPresentationDecision(requireNotNull(fixture.store.publish(
					ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityLost))).postState))
				controller.onHostResumedChanged(false)
				controller.onHostWindowHidden()
				preparation.invalidate("window-hidden")
				looper.idleFor(java.time.Duration.ofSeconds(30))
				assertNull(fixture.store.state?.failure)
				assertFalse(controller.stagePresentationRecoveryObservation(0, correlation, request.foliateSessionId))
				bridge.update(readerPresentationDecision(requireNotNull(fixture.store.publish(
					ReaderPresentationEvent.Lifecycle(ReaderPresentationLifecycleEvent.VisibilityRestored))).postState))
				controller.onHostResumedChanged(true)
				controller.onHostContentReady()
				val restoredCorrelation = assertNotNull(
					(webView as Task9RecoveryCommandWebView).recoveryReasons.lastOrNull())
				assertFalse(restoredCorrelation == correlation,
					"Restore must issue a fresh physical snapshot rather than coalescing the invalidated request")
				assertFalse(controller.stagePresentationRecoveryObservation(0, correlation, request.foliateSessionId))
				correlation = restoredCorrelation
				assertEquals(readerPresentationDecision(retry.postState).pendingTransitionToken,
					readerPresentationDecision(requireNotNull(fixture.store.state)).pendingTransitionToken)
			}
			assertNull(freshGeneration)
			assertFalse(controller.stagePresentationRecoveryObservation(0, correlation, "stale-session"))
			assertFalse(controller.stagePresentationRecoveryObservation(-1, correlation, request.foliateSessionId))
			assertFalse(controller.stagePresentationRecoveryObservation(0, "$correlation-stale", request.foliateSessionId))
			assertTrue(controller.stagePresentationRecoveryObservation(0, correlation, request.foliateSessionId))
			val profileEpoch = controller.javaClass.task7Field("publishedRasterProfileEpoch").get(controller) as Long?
			val observed = assertNotNull(readerPresentationHostBinding(ReaderPresentationHostBindingSnapshot(
				pageTurnCanvasEnabled = true, windowVisible = true,
				foliateSessionId = request.foliateSessionId,
				publicationGeneration = sourceBinding.publicationGeneration,
				viewportGeneration = sourceBinding.viewportGeneration,
				viewportWidth = 1200, viewportHeight = 800,
				profileIdentity = profileEpoch?.let(ReaderPresentationHostProfileIdentity::Resolved)
					?: ReaderPresentationHostProfileIdentity.Provisional,
				destinationCommitIdentity = ReaderDestinationCommitIdentity(request.foliateSessionId,
					assertNotNull(sourceBinding.destinationCommitIdentity).commitSequence + 1L),
				preparationGeneration = assertNotNull(sourceBinding.preparationGeneration),
				visualPageIndex = 0, preparedDeck = reportedDeck, preparedDeckAdmitted = true
			)))
			val reporter = ReaderPresentationBindingReporter()
			val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(reporter.update(
				confirmedTargetBinding = fixture.store.state?.binding, currentBinding = observed,
				publicationOpenPending = false, relocationPending = true
			), "The real host binding after timeout must admit the correlated source observation")
			assertNull(relocation.acknowledgement)
			val step = reporter.dispatch(ReaderController(ReaderControllerState(
				readerSessionGeneration = 1L, presentation = requireNotNull(fixture.store.state))), relocation)
			fixture.store.state = step.controller.state.presentation
			assertEquals(observed, reporter.lastReportedBinding)
			bridge.update(readerPresentationDecision(step.controller.state.presentation))
			controller.synchronizeVisualPageIndex(0, correlation, oldAck)
			assertEquals(assertNotNull(sourceBinding.preparationGeneration) + 1L, freshGeneration)
			assertFalse(controller.stagePresentationRecoveryObservation(2, correlation, request.foliateSessionId))
			if (suspendBeforeObservation) {
				val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				looper.idleFor(java.time.Duration.ofSeconds(5))
				assertNull(fixture.store.state?.failure)
				looper.idleFor(java.time.Duration.ofSeconds(1))
				assertEquals(ReaderPresentationFailureReason.TimedOut, fixture.store.state?.failure?.reason)
			}
			controller.onHostSizeChanged()
			assertNull(controller.javaClass.task7Field("publishedRasterProfileEpoch").get(controller),
				"A real geometry replacement must still invalidate the preserved profile")
		} finally {
			preparation.destroy()
			bridge.dispose()
			down.recycle()
			activity.pause().stop().destroy()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerDefersRecoveryPublicationUntilSourceIngressAndStillAdmitsToc() =
		task9ActualViewerRecoveryPublication(recoveryObservation = true, targetOrdinal = 1)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerSameOrdinalTocRetiresRecoveryAndContinues() =
		task9ActualViewerRecoveryPublication(recoveryObservation = false, targetOrdinal = 1)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerChangedOrdinalTocRetiresRecoveryAndContinues() =
		task9ActualViewerRecoveryPublication(recoveryObservation = false, targetOrdinal = 0)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerSameOrdinalTocRequiresActualNativeSuccessor() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerChangedOrdinalTocRequiresActualNativeSuccessor() =
		task9ActualViewerRecoveryPublication(false, 0, nativePipeline = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerRetainedCurlProjectionThroughRetry() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, projectionOnly = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task10ActualViewerCoverNullDeckTimeoutRetryAllocatesFreshPreparation() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, coverNullDeckRetry = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task10ActualViewerResizeAfterRetryCancelAdoptsPartialAndCompletesReporter() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true,
			coverNullDeckRetry = true, resizeAfterRetryCancel = true)

	private enum class Task9LivenessBoundary { MissingSource, RejectedSource, MissingFrame, HiddenFrame }

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerMissingSourceTimesOutAndCanRetryCancel() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.MissingSource)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerRejectedSourceTimesOutAndCanRetryCancel() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.RejectedSource)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerMissingNativeFrameTimesOutAndCanRetryCancel() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.MissingFrame)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerWindowRestoreResumesRemainingFrameDeadline() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, livenessBoundary = Task9LivenessBoundary.HiddenFrame)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerRetainedSelectedFailureRejectsLateNativeSuccessor() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, failRetainedSelected = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerInvalidSelectedPauseReportsFailureBeforeTerminalRemoval() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, retainedPauseInvalid = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task9NoViewTreeLifecycleOwnerShadow::class])
	fun task9ActualViewerValidSelectedPauseRetainsUntilTerminalClose() =
		task9ActualViewerRecoveryPublication(false, 1, nativePipeline = true, retainedPauseInvalid = false)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun task9ActualViewerPhysicalRuntimeDrainsAndSessionRejectsClosedCaptureTail() {
		val runtime = Task9ControlledPassiveRuntime()
		val geometry = ReaderPassiveRasterGeometry(8, 12, 0, 0, 8, 12)
		val issuer = ReaderPassiveRasterManifestIssuer()
		val commit = issuer.replaceCanonicalCommit(ReaderPassiveRasterCanonicalCommit(
			1L, "task9-source", 1L, "task9-commit", "task9-profile", "task9-pagination",
			"task9-layout", "task9-decoration", geometry, 0L))
		val manifest = assertNotNull(issuer.issue(commit, "task9-target", 1))
		var cancelled = 0
		var drained = 0
		runtime.commit(manifest, manifest.opaqueCaptureTarget, 1L) { assertNull(it); cancelled++ }
		runtime.cancelActiveCommit { drained++ }
		runtime.cancelActiveCommit { drained++ }
		assertEquals(1, cancelled)
		assertEquals(2, drained)
		assertEquals(0, runtime.pendingCallbackCount)
		assertTrue(runtime.commits.tryReceive().isFailure)
		runtime.pause()
		assertFalse(runtime.isReady)
		runtime.capture(geometry) { assertNull(it); cancelled++ }
		assertEquals(2, cancelled)
		runtime.resume()
		assertTrue(runtime.isReady)
		var releases = 0
		val session = ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
			releases++
			bitmap.recycle()
		}
		assertTrue(session.commit(manifest) { committed ->
			assertTrue(assertNotNull(committed).capture { capture -> assertNull(capture) })
		})
		runtime.completeCommit(assertNotNull(runtime.commits.tryReceive().getOrNull()))
		val oldCapture = assertNotNull(runtime.captures.tryReceive().getOrNull())
		assertEquals(geometry, oldCapture.geometry)
		session.close()
		assertTrue(runtime.isRetired)
		assertEquals(0, runtime.pendingCallbackCount)
		runtime.resume()
		assertFalse(runtime.isReady)
		// A callback copied by the test is not a physical owner after cancellation.
		val staleBitmap = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888)
		oldCapture.callback(staleBitmap)
		assertEquals(1, releases)
		assertTrue(staleBitmap.isRecycled)
		session.close()
		assertEquals(1, releases)
	}

	private enum class Task373RegistrationProfileBoundary {
		NoDecision,
		NoTarget,
		NoHostLease,
		ProfileAbsent,
		Zero,
		Conflicting
	}

	private enum class Task373AcceptedCallbackBoundary {
		ProfileAbsent,
		ProfileZero,
		ProfileStale,
		Token,
		Binding,
		Preparation,
		Raster,
		Texture,
		Decision,
		InputPolicy,
		HostInputPolicy,
		HostInputPolicyNewer,
		AuthorityVersionOlder,
		NoTarget,
		Role,
		Slot,
		Viewer,
		Lifecycle
	}

	private inner class Task373AdmissionScenario(
		val bundleSource: ReaderPageTurnBundleSource = ReaderPageTurnBundleSource()
	) {
		val initial = task8SettledCurlSourceState()
		val initialBinding = requireNotNull(initial.binding)
		val fixture = task8CurlAuthorityFixture(initial, bundleSource = bundleSource)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		val surface = controller.surfaceView
		var releases = 0

		@Suppress("UNCHECKED_CAST")
		val admissions: MutableMap<Long, ReaderDeckAdmission>
			get() = controllerClass.task7Field("generationAdmissions")
				.get(controller) as MutableMap<Long, ReaderDeckAdmission>

		@Suppress("UNCHECKED_CAST")
		val prepared: Set<Long>
			get() = controllerClass.task7Field("preparedDeckGenerations")
				.get(controller) as Set<Long>

		fun submit(
			generation: Long,
			role: ReaderDeckSubmissionRole,
			profile: ReaderPlayLikeCurlRasterProfile,
			expected: ReaderPageRecoveredDeckSubmissionResult? =
				ReaderPageRecoveredDeckSubmissionResult.Accepted
		): Any {
			if (
				role == ReaderDeckSubmissionRole.Pending &&
				expected == ReaderPageRecoveredDeckSubmissionResult.Accepted
			) {
				val preState = assertNotNull(fixture.store.state)
				val preBinding = assertNotNull(preState.binding)
				val projectedBinding = assertNotNull(
					(controllerClass.task7Field("commonPresentationDecision").get(controller)
						as ReaderPresentationDecision).targetBinding
				)
				val claimBinding = preBinding.copy(
					profileGeneration = controllerClass
						.task7Field("publishedRasterProfileEpoch").get(controller) as Long
				)
				val preFrame = assertIs<ReaderPresentationAuthority.SettledNativePage>(
					preState.authority
				).frame
				val claimState = preState.copy(
					authority = ReaderPresentationAuthority.SettledNativePage(
						ReaderPresentationFrameOwner.NativePage(
							preFrame.proof.copy(binding = claimBinding)
						)
					),
					binding = claimBinding
				)
				fixture.store.state = claimState
				val claim = assertNotNull(
					readerCurlClaimEvent(readerPresentationDecision(claimState), generation)
				)
				val receipt = assertNotNull(fixture.store.publish(claim))
				assertTrue(receipt.authorizes(claim))
				val acceptedDecision = readerPresentationDecision(receipt.postState)
				val projectedDecision = acceptedDecision.copy(targetBinding = projectedBinding)
				fixture.admissionHost.commit(receipt.postState, receipt.version)
				fixture.admissionHost.synchronizeDecision(projectedDecision)
				val synchronousSubmissionCallback =
					Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent
				Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = null
				try {
					controller.synchronizePresentationDecision(projectedDecision)
				} finally {
					Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent =
						synchronousSubmissionCallback
				}
			}
			return task8SubmitRecoveredDeck(
				controller = controller,
				generationId = generation,
				role = role,
				profile = profile,
				onRasterReleased = { releases += 1 },
				expectedSubmissionResult = expected
			)
		}

		fun complete(generation: Long) {
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, generation)
		}

		@Suppress("UNCHECKED_CAST")
		fun role(generation: Long): ReaderDeckSubmissionRole? =
			(controllerClass.task7Field("generationRoles").get(controller)
				as Map<Long, ReaderDeckSubmissionRole>)[generation]

		fun slotGeneration(slot: ReaderDeckAdmissionSlot): Long? =
			controllerClass.task7Field(
				if (slot == ReaderDeckAdmissionSlot.Active) {
					"activeDeckGenerationId"
				} else {
					"pendingDeckGenerationId"
				}
			).get(controller) as Long?

		fun publishResolvedProfileBeforeComposeEcho(): ReaderPlayLikeCurlRasterProfile {
			val previousProfile = task373PublishCurrentRasterProfile(
				controller,
				initialBinding.profileGeneration
			)
			val provisionalDecision = readerPresentationDecision(
				ReaderPresentationState(
					binding = initialBinding.copy(
						profileGeneration = 0L,
						rasterGeneration = null,
						textureGeneration = null
					)
				)
			)
			controller.synchronizePresentationDecision(provisionalDecision)
			fixture.admissionHost.synchronizeDecision(provisionalDecision)
			val profile = previousProfile.copy(quality = ReaderPageBitmapQuality.High)
			controllerClass.task7Field("requestedProfile").set(controller, profile)
			controllerClass.task7Method(
				"publishRasterProfileEpoch",
				ReaderPlayLikeCurlRasterProfile::class.java
			).invoke(controller, profile)
			val publishedProfile = controllerClass.task7Field("publishedRasterProfileEpoch")
				.get(controller) as Long
			assertTrue(publishedProfile > 0L)
			assertTrue(publishedProfile != initialBinding.profileGeneration)
			assertEquals(0L, (
				controllerClass.task7Field("commonPresentationDecision").get(controller)
					as ReaderPresentationDecision
			).targetBinding?.profileGeneration)
			return profile
		}

		suspend fun destroy() = task373DestroyController(controller)
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373PublishedProfileCompletesProvisionalDeckAcrossSynchronousCallbackForBothRoles() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			ReaderDeckSubmissionRole.entries.forEachIndexed { index, role ->
				val scenario = Task373AdmissionScenario()
				val controller = scenario.controller
				val controllerClass = scenario.controllerClass
				val provisional = scenario.initialBinding.copy(
					profileGeneration = 0L,
					rasterGeneration = null,
					textureGeneration = null
				)
				val profile = scenario.publishResolvedProfileBeforeComposeEcho()
				val publishedProfile = controllerClass.task7Field("publishedRasterProfileEpoch")
					.get(controller) as Long
				val generation = 373_100L + index
				try {
					scenario.submit(generation, role, profile)
					@Suppress("UNCHECKED_CAST")
					val roles = controllerClass.task7Field("generationRoles")
						.get(controller) as Map<Long, ReaderDeckSubmissionRole>
					val admission = assertNotNull(scenario.admissions[generation])
					val capability = admission.capability
					assertEquals(role, roles[generation])
					assertEquals(role, capability.role)
					assertEquals(role.admissionSlot(), capability.slot)
					assertEquals(publishedProfile, capability.originBinding.profileGeneration)
					assertEquals(provisional.foliateSessionId, capability.originBinding.foliateSessionId)
					assertEquals(provisional.publicationGeneration, capability.originBinding.publicationGeneration)
					assertEquals(provisional.viewportGeneration, capability.originBinding.viewportGeneration)
					assertEquals(provisional.destinationCommitIdentity, capability.originBinding.destinationCommitIdentity)
					assertEquals(profile.rasterGeneration, capability.originBinding.rasterGeneration)
					assertEquals(generation, capability.originBinding.textureGeneration)
					assertEquals(provisional.preparationGeneration, capability.originBinding.preparationGeneration)
					assertEquals(ReaderDeckAdmissionState.RendererOwned, admission.state)
					scenario.fixture.admissionHost.advanceCompatibleVersion()
					assertTrue(
						scenario.fixture.admissionHost.isCurrent(capability),
						"A newer host receipt may retain only an identical normalized authority identity"
					)

					// The common decision echo deliberately remains provisional.
					scenario.complete(generation)
					assertTrue(generation in scenario.prepared)
					assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, admission.state)

					val delayedEcho = controllerClass.task7Field("commonPresentationDecision")
						.get(controller) as ReaderPresentationDecision
					controller.synchronizePresentationDecision(
						delayedEcho.copy(targetBinding = capability.originBinding)
					)
					assertSame(
						admission,
						scenario.admissions[generation],
						"A compatible delayed echo must not replace or retag immutable admission"
					)
					assertEquals(
						generation,
						controllerClass.task7Field(
							if (role == ReaderDeckSubmissionRole.Active) {
								"activeDeckGenerationId"
							} else {
								"pendingDeckGenerationId"
							}
						).get(controller)
					)
				} finally {
					scenario.destroy()
				}
				assertEquals(1, scenario.releases, "$role accepted renderer deck must release exactly once")
				assertFalse(task8SurfaceOwnsGeneration(scenario.surface, generation))
			}
		} finally {
			Dispatchers.resetMain()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373RegistrationRequiresPublishedPositiveMatchingProfileAuthority() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			Task373RegistrationProfileBoundary.entries.forEachIndexed { index, boundary ->
				val scenario = Task373AdmissionScenario()
				val controller = scenario.controller
				val controllerClass = scenario.controllerClass
				val profile = controllerClass.task7Field("requestedProfile").get(controller)
					as ReaderPlayLikeCurlRasterProfile
				when (boundary) {
					Task373RegistrationProfileBoundary.NoDecision ->
						controllerClass.task7Field("commonPresentationDecision").set(controller, null)
					Task373RegistrationProfileBoundary.NoTarget -> {
						val decision = controllerClass.task7Field("commonPresentationDecision")
							.get(controller) as ReaderPresentationDecision
						controller.synchronizePresentationDecision(decision.copy(targetBinding = null))
					}
					Task373RegistrationProfileBoundary.NoHostLease -> scenario.fixture.admissionHost.revoke()
					Task373RegistrationProfileBoundary.ProfileAbsent ->
						controllerClass.task7Field("publishedRasterProfileEpoch").set(controller, null)
					Task373RegistrationProfileBoundary.Zero ->
						controllerClass.task7Field("publishedRasterProfileEpoch").set(controller, 0L)
					Task373RegistrationProfileBoundary.Conflicting -> {
						task373PublishCurrentRasterProfile(controller, scenario.initialBinding.profileGeneration)
						val decision = controllerClass.task7Field("commonPresentationDecision")
							.get(controller) as ReaderPresentationDecision
						val binding = assertNotNull(decision.targetBinding)
						controller.synchronizePresentationDecision(
							decision.copy(
								targetBinding = binding.copy(
									profileGeneration = scenario.initialBinding.profileGeneration + 1L
								)
							)
						)
					}
				}
				val generation = 373_200L + index
				try {
					scenario.submit(
						generation,
						ReaderDeckSubmissionRole.Pending,
						profile,
						expected = null
					)
					val submission = runCatching {
						controller.submitRecoveredDeck(generation, ReaderDeckSubmissionRole.Pending)
					}
					assertTrue(
						submission.isSuccess,
						"$boundary missing authority must defer before renderer ownership, not throw after transfer"
					)
					assertEquals("AwaitingAdmission", submission.getOrNull()?.javaClass?.simpleName)
					assertEquals(0, scenario.releases, "$boundary unsubmitted raster must remain recoverable")
					assertFalse(task8SurfaceOwnsGeneration(scenario.surface, generation))
					controller.releaseUnsubmittedRecoveredDeck(generation)
					assertEquals(1, scenario.releases, "$boundary explicit cancellation must release retained raster")
				} finally {
					scenario.destroy()
				}
				assertEquals(1, scenario.releases, "$boundary retained raster release must remain one-shot")
			}
		} finally {
			Dispatchers.resetMain()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373EveryStaleAcceptedAdmissionReleasesItsRendererDeckExactlyOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			Task373AcceptedCallbackBoundary.entries.forEachIndexed { index, boundary ->
				val scenario = Task373AdmissionScenario()
				val controller = scenario.controller
				val controllerClass = scenario.controllerClass
				val profile = task373PublishCurrentRasterProfile(
					controller,
					scenario.initialBinding.profileGeneration
				)
				val generation = 373_300L + index
				try {
					scenario.submit(generation, ReaderDeckSubmissionRole.Pending, profile)
					val decision = controllerClass.task7Field("commonPresentationDecision")
						.get(controller) as ReaderPresentationDecision
					when (boundary) {
						Task373AcceptedCallbackBoundary.ProfileAbsent ->
							controllerClass.task7Field("publishedRasterProfileEpoch").set(controller, null)
						Task373AcceptedCallbackBoundary.ProfileZero ->
							controllerClass.task7Field("publishedRasterProfileEpoch").set(controller, 0L)
						Task373AcceptedCallbackBoundary.ProfileStale -> {
							controllerClass.task7Method(
								"publishRasterProfileEpoch",
								ReaderPlayLikeCurlRasterProfile::class.java
							).invoke(controller, profile.copy(quality = ReaderPageBitmapQuality.High))
						}
						Task373AcceptedCallbackBoundary.Token -> {
							val binding = assertNotNull(decision.targetBinding)
							controller.synchronizePresentationDecision(
								decision.copy(
									authority = ReaderPresentationAuthority.Unavailable,
									requiredTransition = ReaderRequiredTransition.PresentNativePage(
										ReaderPresentationToken(373_400L + index), binding, null
									)
								)
							)
						}
						Task373AcceptedCallbackBoundary.Binding -> {
							val binding = assertNotNull(decision.targetBinding)
							controller.synchronizePresentationDecision(
								decision.copy(
									targetBinding = binding.copy(
										viewportGeneration = binding.viewportGeneration + 1L
									)
								)
							)
						}
						Task373AcceptedCallbackBoundary.Preparation ->
							controllerClass.task7Field("preparationGeneration").setLong(
								controller,
								controllerClass.task7Field("preparationGeneration").getLong(controller) + 1L
							)
						Task373AcceptedCallbackBoundary.Raster ->
							scenario.bundleSource.invalidate("task373-stale-raster")
						Task373AcceptedCallbackBoundary.Texture -> {
							val original = assertNotNull(scenario.admissions[generation])
							val staleTextureGeneration = generation + 1L
							val replacement = ReaderDeckAdmission(
								original.capability.copy(
									originBinding = original.capability.originBinding.copy(
										textureGeneration = staleTextureGeneration
									),
									textureGeneration = staleTextureGeneration
								)
							)
							assertEquals(
								ReaderDeckAdmissionOwnershipDisposition.AwaitingCallback,
								replacement.acknowledgeRendererOwnership()
							)
							scenario.admissions[generation] = replacement
						}
						Task373AcceptedCallbackBoundary.Decision ->
							controllerClass.task7Field("commonPresentationDecision").set(controller, null)
						Task373AcceptedCallbackBoundary.InputPolicy ->
							controllerClass.task7Field("commonPresentationDecision").set(
								controller,
								decision.copy(inputPolicy = ReaderPresentationInputPolicy.RecoveryOnly)
							)
						Task373AcceptedCallbackBoundary.HostInputPolicy ->
							scenario.fixture.admissionHost.synchronizeDecision(
								decision.copy(inputPolicy = ReaderPresentationInputPolicy.RecoveryOnly)
							)
						Task373AcceptedCallbackBoundary.HostInputPolicyNewer -> {
							scenario.fixture.admissionHost.synchronizeDecision(
								decision.copy(inputPolicy = ReaderPresentationInputPolicy.RecoveryOnly)
							)
							scenario.fixture.admissionHost.advanceCompatibleVersion()
						}
						Task373AcceptedCallbackBoundary.AuthorityVersionOlder ->
							scenario.fixture.admissionHost.rewindVersion()
						Task373AcceptedCallbackBoundary.NoTarget ->
							controller.synchronizePresentationDecision(decision.copy(targetBinding = null))
						Task373AcceptedCallbackBoundary.Role -> {
							@Suppress("UNCHECKED_CAST")
							val roles = controllerClass.task7Field("generationRoles")
								.get(controller) as MutableMap<Long, ReaderDeckSubmissionRole>
							roles[generation] = ReaderDeckSubmissionRole.Active
						}
						Task373AcceptedCallbackBoundary.Slot ->
							controllerClass.task7Field("pendingDeckGenerationId").set(controller, generation + 1L)
						Task373AcceptedCallbackBoundary.Viewer -> scenario.fixture.admissionHost.advanceViewer()
						Task373AcceptedCallbackBoundary.Lifecycle -> scenario.fixture.admissionHost.retireLifecycle()
					}

					scenario.complete(generation)
					scenario.complete(generation)
					ShadowLooper.runUiThreadTasks()
					assertFalse(generation in scenario.prepared, "$boundary callback must not prepare")
					assertEquals(1, scenario.releases, "$boundary stale renderer deck must release once")
					assertFalse(task8SurfaceOwnsGeneration(scenario.surface, generation))
				} finally {
					scenario.destroy()
				}
				assertEquals(1, scenario.releases, "$boundary release must remain one-shot after destroy")
			}
		} finally {
			Dispatchers.resetMain()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task374AcceptedCallbackWithoutAuthorityRecordNeverPreparesAndReleasesOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			val scenario = Task373AdmissionScenario()
			val profile = task373PublishCurrentRasterProfile(
				scenario.controller,
				scenario.initialBinding.profileGeneration
			)
			val generation = 374_100L
			try {
				scenario.submit(generation, ReaderDeckSubmissionRole.Pending, profile)
				assertNotNull(scenario.admissions.remove(generation))

				scenario.complete(generation)
				scenario.complete(generation)
				ShadowLooper.runUiThreadTasks()
				assertFalse(generation in scenario.prepared, "A callback without immutable admission cannot prepare")
				assertEquals(1, scenario.releases, "A renderer-owned deck without admission must release once")
				assertFalse(task8SurfaceOwnsGeneration(scenario.surface, generation))
			} finally {
				scenario.destroy()
			}
			assertEquals(1, scenario.releases, "Duplicate callback and destroy cannot double release")
		} finally {
			Dispatchers.resetMain()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373RendererCallbackReentersInsideSubmissionForBothRoles() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			ReaderDeckSubmissionRole.entries.forEachIndexed { index, role ->
				val scenario = Task373AdmissionScenario()
				val generation = 373_500L + index
				val profile = scenario.publishResolvedProfileBeforeComposeEcho()
				var reentered = 0
				try {
					Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = { view ->
						val admission = assertNotNull(scenario.admissions[generation])
						assertEquals(ReaderDeckAdmissionState.Reserved, admission.state)
						assertTrue(admission.capability.profileGeneration > 0L)
						assertTrue(
							admission.capability.profileGeneration != scenario.initialBinding.profileGeneration
						)
						assertEquals(0L, (
							scenario.controllerClass.task7Field("commonPresentationDecision")
								.get(scenario.controller) as ReaderPresentationDecision
						).targetBinding?.profileGeneration)
						assertEquals(role, admission.capability.role)
						assertEquals(role.admissionSlot(), admission.capability.slot)
						assertNull(scenario.role(generation))
						assertFalse(scenario.slotGeneration(admission.capability.slot) == generation)
						(view as karacken.curl.PageSurfaceView).pageSurfaceListener
							.onDeckPrepared(generation)
						assertEquals(ReaderDeckAdmissionState.Reserved, admission.state)
						reentered += 1
					}

					scenario.submit(generation, role, profile)

					val admission = assertNotNull(scenario.admissions[generation])
					assertEquals(1, reentered)
					assertEquals(role, scenario.role(generation))
					assertEquals(generation, scenario.slotGeneration(admission.capability.slot))
					assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, admission.state)
					assertTrue(generation in scenario.prepared)
				} finally {
					Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = null
					scenario.destroy()
				}
				assertEquals(1, scenario.releases)
			}
		} finally {
			Dispatchers.resetMain()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373StaleRendererCallbackReentryNeverPreparesAndReleasesOnce() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			val scenario = Task373AdmissionScenario()
			val generation = 373_600L
			val profile = scenario.publishResolvedProfileBeforeComposeEcho()
			try {
				Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = { view ->
					val admission = assertNotNull(scenario.admissions[generation])
					assertTrue(admission.capability.profileGeneration > 0L)
					assertEquals(0L, (
						scenario.controllerClass.task7Field("commonPresentationDecision")
							.get(scenario.controller) as ReaderPresentationDecision
					).targetBinding?.profileGeneration)
					(view as karacken.curl.PageSurfaceView).pageSurfaceListener
						.onDeckPrepared(generation)
					assertEquals(ReaderDeckAdmissionState.Reserved, admission.state)
					val decision = scenario.controllerClass.task7Field("commonPresentationDecision")
						.get(scenario.controller) as ReaderPresentationDecision
					assertTrue(decision.inputPolicy != ReaderPresentationInputPolicy.ChromeOnly)
					scenario.fixture.admissionHost.synchronizeDecision(
						decision.copy(inputPolicy = ReaderPresentationInputPolicy.ChromeOnly)
					)
					scenario.fixture.admissionHost.advanceCompatibleVersion()
				}

				scenario.submit(generation, ReaderDeckSubmissionRole.Pending, profile)
				ShadowLooper.runUiThreadTasks()
				assertFalse(generation in scenario.prepared)
				assertEquals(1, scenario.releases)
				assertFalse(task8SurfaceOwnsGeneration(scenario.surface, generation))
			} finally {
				Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = null
				scenario.destroy()
			}
			assertEquals(1, scenario.releases)
		} finally {
			Dispatchers.resetMain()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373HostPauseDefersFreshAdmissionAndRejectsReentrantCallback() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		try {
			val deferred = Task373AdmissionScenario()
			val generation = 373_700L
			val profile = deferred.controllerClass.task7Field("requestedProfile")
				.get(deferred.controller) as ReaderPlayLikeCurlRasterProfile
			val coordinator = deferred.controllerClass.task7Field("deckRecoveryCoordinator")
				.get(deferred.controller) as ReaderPageDeckRecoveryCoordinator
			val submitRecovered = coordinator.javaClass.declaredMethods.single {
				it.name == "submitRecoveredGeneration"
			}.apply { isAccessible = true }
			try {
				deferred.submit(generation, ReaderDeckSubmissionRole.Active, profile, expected = null)
				deferred.controller.onHostResumedChanged(false)
				assertTrue(submitRecovered.invoke(
					coordinator, generation, setOf(1), 1, profile.rasterGeneration, null
				) as Boolean)
				assertIs<ReaderPageDeckRecoveryState.WaitingForAdmission>(coordinator.state)
				assertNull(deferred.admissions[generation])
				assertFalse(generation in deferred.prepared)
				assertFalse(task8SurfaceOwnsGeneration(deferred.surface, generation))

				deferred.controller.onHostResumedChanged(true)
				assertEquals(
					ReaderDeckAdmissionState.RendererOwned,
					assertNotNull(deferred.admissions[generation]).state
				)
				assertTrue(task8SurfaceOwnsGeneration(deferred.surface, generation))
				assertIs<ReaderPageDeckRecoveryState.WaitingForPreparation>(coordinator.state)
				deferred.complete(generation)
				assertTrue(generation in deferred.prepared)
				assertIs<ReaderPageDeckRecoveryState.Ready>(coordinator.state)
			} finally {
				deferred.destroy()
			}
			assertEquals(1, deferred.releases)

			val crossing = Task373AdmissionScenario()
			val crossingGeneration = 373_800L
			val crossingProfile = crossing.controllerClass.task7Field("requestedProfile")
				.get(crossing.controller) as ReaderPlayLikeCurlRasterProfile
			try {
				Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = { view ->
					val admission = assertNotNull(crossing.admissions[crossingGeneration])
					assertEquals(ReaderDeckAdmissionState.Reserved, admission.state)
					(view as karacken.curl.PageSurfaceView).pageSurfaceListener
						.onDeckPrepared(crossingGeneration)
					assertEquals(ReaderDeckAdmissionState.Reserved, admission.state)
					crossing.controller.onHostResumedChanged(false)
					assertEquals(ReaderDeckAdmissionState.ReleaseRequested, admission.state)
				}
				crossing.submit(crossingGeneration, ReaderDeckSubmissionRole.Pending, crossingProfile)
				ShadowLooper.runUiThreadTasks()
				assertFalse(crossingGeneration in crossing.prepared)
				assertEquals(1, crossing.releases)
				assertFalse(task8SurfaceOwnsGeneration(crossing.surface, crossingGeneration))
			} finally {
				Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = null
				crossing.destroy()
			}
			assertEquals(1, crossing.releases)
		} finally {
			Dispatchers.resetMain()
		}
	}

	private enum class Task336PendingBoundary {
		AfterClaim, Synchronous, ClaimRejected, RendererRejected, Cancelled, Successor,
		PreparationChanged, GenerationChanged, BindingChanged, CancelDuringReceipt, BindingDuringReceipt
	}

	private enum class Task373DeferredPendingTerminal {
		Rejected,
		Cancelled
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373RejectedDeferredPendingAdmissionCannotResurrect() =
		task373TerminalDeferredPendingAdmission(Task373DeferredPendingTerminal.Rejected)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373CancelledDeferredPendingAdmissionCannotResurrect() =
		task373TerminalDeferredPendingAdmission(Task373DeferredPendingTerminal.Cancelled)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373LiveDeferredPendingAdmissionRetriesUnderOriginatingGesture() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		var rasterReleases = 0
		val fixture = task8CurlAuthorityFixture(
			task8SettledCurlSourceState(),
			onActiveRasterReleased = { rasterReleases += 1 }
		)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		controllerClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		val surface = controller.surfaceView
		val gesture = 373_901L
		var failure: Throwable? = null
		try {
			fixture.admissionHost.reservationsEnabled = false
			assertEquals(
				ReaderPageTurnStartResult.Settling,
				controller.start(gesture, PageChange.NEXT) { _, _ -> true }
			)
			assertEquals(gesture, controllerClass.task7Field("activeGestureId").get(controller))
			assertNotNull(controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller))
			assertNull(controllerClass.task7Field("pendingDeckGenerationId").get(controller))

			fixture.admissionHost.reservationsEnabled = true
			val retryGeneration = controllerClass.task7Field("nextDeckGeneration").getLong(controller)
			surface.pageSurfaceListener.onDeckSubmissionCapacityAvailable()

			assertNull(controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller))
			assertEquals(retryGeneration, controllerClass.task7Field("pendingDeckGenerationId").get(controller))
			@Suppress("UNCHECKED_CAST")
			val admissions = controllerClass.task7Field("generationAdmissions").get(controller)
				as Map<Long, ReaderDeckAdmission>
			val admission = assertNotNull(admissions[retryGeneration])
			assertEquals(ReaderDeckAdmissionState.RendererOwned, admission.state)
			assertTrue(task8SurfaceOwnsGeneration(surface, retryGeneration))
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, retryGeneration)
			assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, admission.state)
			assertEquals(gesture, admission.capability.presentationToken?.value)
			assertEquals(ReaderPresentationTokenDomain.Gesture, admission.capability.presentationToken?.domain)
		} catch (caught: Throwable) {
			failure = caught
			throw caught
		} finally {
			try {
				controller.cancelGesture(gesture)
				surface.detach()
				val closed = controller.destroy()
				ShadowLooper.runUiThreadTasks()
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				assertEquals(1, rasterReleases)
			} catch (cleanup: Throwable) {
				if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
			} finally {
				fixture.admissionHost.reservationsEnabled = true
				Dispatchers.resetMain()
			}
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373DelayedPreparedCallbackAdmitsLawfullyPromotedPendingDeck() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		var rasterReleases = 0
		var callbackStateBeforeReceipt: ReaderDeckAdmissionState? = null
		var callbackAdmissionBeforeReceipt: ReaderDeckAdmission? = null
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			onActiveRasterReleased = { rasterReleases += 1 },
			beforePresentationEventPublished = { callbackController, event ->
				if (event is ReaderPresentationEvent.CurlTerminal) {
					val callbackControllerClass = callbackController.javaClass
					val generation = assertNotNull(
						callbackControllerClass.task7Field("activeDeckGenerationId")
							.get(callbackController) as Long?
					)
					@Suppress("UNCHECKED_CAST")
					val callbackAdmissions = callbackControllerClass
						.task7Field("generationAdmissions").get(callbackController)
						as Map<Long, ReaderDeckAdmission>
					val callbackAdmission = assertNotNull(callbackAdmissions[generation])
					callbackAdmissionBeforeReceipt = callbackAdmission
					callbackController.surfaceView.javaClass
						.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(callbackController.surfaceView, generation)
					callbackStateBeforeReceipt = callbackAdmission.state
				}
			}
		)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		controllerClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		val sourceBinding = requireNotNull(fixture.store.state?.binding)
		controllerClass.task7Field("nextRasterProfileEpoch")
			.setLong(controller, sourceBinding.profileGeneration)
		controllerClass.task7Method(
			"publishRasterProfileEpoch",
			ReaderPlayLikeCurlRasterProfile::class.java
		).invoke(controller, controllerClass.task7Field("requestedProfile").get(controller))
		val surface = controller.surfaceView
		val webView = (controllerClass.task7Field("webViewProvider").get(controller)
			as () -> WebView?)()
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val attachedHost = requireNotNull(webView).parent as View
		activity.get().setContentView(attachedHost)
		val hostOrigin = IntArray(2).also(attachedHost::getLocationInWindow)
		attachedHost.translationX = -hostOrigin[0].toFloat()
		attachedHost.translationY = -hostOrigin[1].toFloat()
		ReaderPageRasterPreparationController(
			host = attachedHost as android.view.ViewGroup,
			webViewProvider = { webView }
		)
		val gesture = 373_902L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		var failure: Throwable? = null
		try {
			assertEquals(
				ReaderPageCurlDispatchResult.Accepted,
				controller.onPageTouchEvent(down, gesture)
			)
			task8BeginSurfaceSettlement(surface)
			surface.pageSurfaceListener.onSettlementStarted(
				gesture,
				301L,
				"source",
				"target",
				PageChange.NEXT
			)
			val promotedGeneration = assertNotNull(
				controllerClass.task7Field("pendingDeckGenerationId").get(controller) as Long?
			)
			assertTrue(promotedGeneration >= 302L)
			@Suppress("UNCHECKED_CAST")
			val admissions = controllerClass.task7Field("generationAdmissions").get(controller)
				as Map<Long, ReaderDeckAdmission>
			val admission = assertNotNull(admissions[promotedGeneration])
			assertEquals(ReaderDeckSubmissionRole.Pending, admission.capability.role)
			assertEquals(ReaderDeckAdmissionSlot.Pending, admission.capability.slot)
			assertEquals(ReaderDeckAdmissionState.RendererOwned, admission.state)
			assertTrue(task8SurfaceOwnsGeneration(surface, promotedGeneration))

			surface.pageSurfaceListener.onSettlementCompleted(
				gesture,
				301L,
				"target",
				2,
				PageChange.NEXT
			)

			assertSame(admission, callbackAdmissionBeforeReceipt)
			assertEquals(
				ReaderDeckAdmissionState.CallbackObserved,
				callbackStateBeforeReceipt,
				"A callback between local promotion and the reducer receipt must await its causal successor"
			)
			assertNotNull(
				admission.promotionReceipt,
				"Local Pending-to-Active promotion must issue one immutable promotion receipt"
			)
			assertEquals(
				promotedGeneration,
				controllerClass.task7Field("activeDeckGenerationId").get(controller)
			)
			assertNull(controllerClass.task7Field("pendingDeckGenerationId").get(controller))
			@Suppress("UNCHECKED_CAST")
			val roles = controllerClass.task7Field("generationRoles").get(controller)
				as Map<Long, ReaderDeckSubmissionRole>
			assertEquals(ReaderDeckSubmissionRole.Active, roles[promotedGeneration])
			val settlementDecision = controllerClass.task7Field("commonPresentationDecision")
				.get(controller) as ReaderPresentationDecision
			assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
				settlementDecision.authority
			)
			assertEquals(
				admission.capability.presentationToken,
				settlementDecision.rendererCallbackTokenOrNull()
			)
			val settlementBinding = assertNotNull(
				readerAcceptedDeckBindingOrNull(
					targetBinding = settlementDecision.targetBinding,
					profileGeneration = admission.capability.profileGeneration,
					preparationGeneration = admission.capability.preparationGeneration,
					rasterGeneration = admission.capability.rasterGeneration,
					textureGeneration = admission.capability.textureGeneration
				)
			)
			assertEquals(admission.capability.originBinding, settlementBinding)
			assertTrue(fixture.admissionHost.isOwnerCurrent(admission.capability))
			assertFalse(fixture.admissionHost.isCurrent(admission.capability))
			assertTrue(
				admission.capability.originDecisionIdentity !=
					readerDeckAdmissionDecisionIdentityOrNull(settlementDecision, settlementBinding)
			)
			assertEquals(
				ReaderDeckAdmissionState.AdmittedRendererOwned,
				admission.state,
				"The CurlTerminal receipt synchronization must retry the observed callback"
			)

			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, promotedGeneration)
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, promotedGeneration)

			@Suppress("UNCHECKED_CAST")
			val prepared = controllerClass.task7Field("preparedDeckGenerations").get(controller)
				as Set<Long>
			assertTrue(
				promotedGeneration in prepared,
				"A delayed callback must admit the exact Pending deck lawfully promoted to Active"
			)
			assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, admission.state)
			assertTrue(task8SurfaceOwnsGeneration(surface, promotedGeneration))
			assertEquals(
				ReaderTextureDeckState.Ready,
				(controllerClass.task7Field("readinessState").get(controller)
					as ReaderPageRendererReadinessState).textureDeck
			)
		} catch (caught: Throwable) {
			failure = caught
			throw caught
		} finally {
			try {
				down.recycle()
				surface.detach()
				val closed = controller.destroy()
				ShadowLooper.runUiThreadTasks()
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				assertEquals(1, rasterReleases)
				activity.pause().stop().destroy()
			} catch (cleanup: Throwable) {
				if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task380PreparedCallbackAdmitsAfterExactFoliateSuccessorAcknowledgement() = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		var rasterReleases = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			onActiveRasterReleased = { rasterReleases += 1 }
		)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		controllerClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		val sourceBinding = requireNotNull(fixture.store.state?.binding)
		controllerClass.task7Field("nextRasterProfileEpoch")
			.setLong(controller, sourceBinding.profileGeneration)
		controllerClass.task7Method(
			"publishRasterProfileEpoch",
			ReaderPlayLikeCurlRasterProfile::class.java
		).invoke(controller, controllerClass.task7Field("requestedProfile").get(controller))
		val surface = controller.surfaceView
		val webView = (controllerClass.task7Field("webViewProvider").get(controller)
			as () -> WebView?)()
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val attachedHost = requireNotNull(webView).parent as View
		activity.get().setContentView(attachedHost)
		val hostOrigin = IntArray(2).also(attachedHost::getLocationInWindow)
		attachedHost.translationX = -hostOrigin[0].toFloat()
		attachedHost.translationY = -hostOrigin[1].toFloat()
		ReaderPageRasterPreparationController(
			host = attachedHost as android.view.ViewGroup,
			webViewProvider = { webView }
		)
		val gesture = 380_902L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		var failure: Throwable? = null
		try {
			assertEquals(
				ReaderPageCurlDispatchResult.Accepted,
				controller.onPageTouchEvent(down, gesture)
			)
			task8BeginSurfaceSettlement(surface)
			surface.pageSurfaceListener.onSettlementStarted(
				gesture,
				301L,
				"source",
				"target",
				PageChange.NEXT
			)
			val promotedGeneration = assertNotNull(
				controllerClass.task7Field("pendingDeckGenerationId").get(controller) as Long?
			)
			@Suppress("UNCHECKED_CAST")
			val admissions = controllerClass.task7Field("generationAdmissions").get(controller)
				as Map<Long, ReaderDeckAdmission>
			val admission = assertNotNull(admissions[promotedGeneration])

			surface.pageSurfaceListener.onSettlementCompleted(
				gesture,
				301L,
				"target",
				2,
				PageChange.NEXT
			)
			val awaitingFoliate = assertNotNull(fixture.store.state)
			val settlement = assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
				awaitingFoliate.authority
			)
			val acknowledgement = assertNotNull(settlement.expectedAcknowledgement)
			val lineage = assertNotNull(awaitingFoliate.rendererSuccessorReceipt)
			val destinationBinding = lineage.originBinding.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					lineage.originBinding.foliateSessionId,
					acknowledgement.pageIndex.toLong()
				),
				rasterGeneration = acknowledgement.rasterGeneration,
				textureGeneration = acknowledgement.textureGeneration
			)
			val relocationEvent = ReaderPresentationEvent.FoliateRelocated(
				destinationBinding,
				acknowledgement
			)
			val relocationReceipt = assertNotNull(fixture.store.publish(relocationEvent))
			assertTrue(relocationReceipt.authorizes(relocationEvent))
			fixture.admissionHost.commit(relocationReceipt.postState, relocationReceipt.version)
			val awaitingNativeDecision = readerPresentationDecision(relocationReceipt.postState)
			controller.synchronizePresentationDecision(awaitingNativeDecision)
			assertEquals(
				ReaderCurlSettlementStage.AwaitingNativePresentation,
				assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
					awaitingNativeDecision.authority
				).stage
			)
			assertEquals(
				ReaderDeckAdmissionCurrency.Current,
				fixture.admissionHost.currency(admission, awaitingNativeDecision),
				"The reducer-authenticated Foliate successor must keep the promoted resource current"
			)
			assertNotNull(admission.promotionReceipt)

			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, promotedGeneration)
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, promotedGeneration)
			assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, admission.state)
			@Suppress("UNCHECKED_CAST")
			val prepared = controllerClass.task7Field("preparedDeckGenerations").get(controller)
				as Set<Long>
			assertTrue(promotedGeneration in prepared)
			assertTrue(task8SurfaceOwnsGeneration(surface, promotedGeneration))
		} catch (caught: Throwable) {
			failure = caught
			throw caught
		} finally {
			try {
				down.recycle()
				surface.detach()
				val closed = controller.destroy()
				ShadowLooper.runUiThreadTasks()
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				assertEquals(1, rasterReleases)
				activity.pause().stop().destroy()
			} catch (cleanup: Throwable) {
				if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	fun task380AdmissionCurrencyRejectsNonCausalDriftAndPreservesExactOnceRelease() {
		val settled = task8SettledCurlSourceState()
		val claimed = readerPresentationReduce(
			settled,
			assertNotNull(readerCurlClaimEvent(readerPresentationDecision(settled), 380_903L))
		).state
		val originDecision = readerPresentationDecision(claimed)
		val originBinding = assertNotNull(claimed.binding)
		val version = paige.navic.reader.ReaderPresentationReceiptVersion(
			1L,
			originBinding.publicationIdentity,
			1L
		)
		fun snapshot(
			state: ReaderPresentationState,
			hostEpoch: Long = 1L,
			viewerGeneration: Long = 1L,
			receiptVersion: paige.navic.reader.ReaderPresentationReceiptVersion = version,
			bindingSeed: ReaderPresentationBinding = assertNotNull(state.binding),
			decision: ReaderPresentationDecision = readerPresentationDecision(state),
			lifecycle: ReaderPresentationLifecycleState = state.lifecycle
		) = ReaderDeckAdmissionAuthoritySnapshot(
			hostEpoch = hostEpoch,
			authorityVersion = receiptVersion,
			viewerGeneration = viewerGeneration,
			lifecycle = lifecycle,
			bindingSeed = bindingSeed,
			presentationToken = decision.rendererCallbackTokenOrNull(),
			decision = decision,
			rendererSuccessorReceipt = state.rendererSuccessorReceipt
		)
		val request = ReaderDeckAdmissionRequest(
			candidateDecision = originDecision,
			profileGeneration = originBinding.profileGeneration,
			preparationGeneration = assertNotNull(originBinding.preparationGeneration),
			rasterGeneration = assertNotNull(originBinding.rasterGeneration),
			textureGeneration = 302L,
			role = ReaderDeckSubmissionRole.Pending
		)
		val capability = assertNotNull(
			readerDeckAdmissionCapabilityOrNull(snapshot(claimed), 1L, request)
		)
		val admission = ReaderDeckAdmission(capability)
		assertEquals(
			ReaderDeckAdmissionOwnershipDisposition.AwaitingCallback,
			admission.acknowledgeRendererOwnership()
		)
		assertTrue(admission.promotePendingToActive())
		assertFalse(admission.promotePendingToActive())
		assertEquals(
			ReaderDeckAdmissionCurrency.AwaitingCausalSuccessor,
			readerDeckAdmissionAuthorityCurrency(snapshot(claimed), admission, originDecision)
		)

		val token = assertNotNull(capability.presentationToken)
		val acknowledgement = ReaderPageTurnSettlementAck(
			"lineage-${token.value}",
			2,
			originBinding.foliateSessionId,
			capability.rasterGeneration,
			capability.textureGeneration
		)
		val terminal = readerPresentationReduce(
			claimed,
			ReaderPresentationEvent.CurlTerminal(token, originBinding, acknowledgement)
		).state
		val terminalDecision = readerPresentationDecision(terminal)
		assertEquals(
			ReaderDeckAdmissionCurrency.Current,
			readerDeckAdmissionAuthorityCurrency(
				snapshot(terminal, receiptVersion = version.copy(eventSequence = 2L)),
				admission,
				terminalDecision
			)
		)
		val lineage = assertNotNull(terminal.rendererSuccessorReceipt)
		val destination = lineage.originBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				lineage.originBinding.foliateSessionId,
				acknowledgement.pageIndex.toLong()
			),
			rasterGeneration = acknowledgement.rasterGeneration,
			textureGeneration = acknowledgement.textureGeneration
		)
		val relocated = readerPresentationReduce(
			terminal,
			ReaderPresentationEvent.FoliateRelocated(destination, acknowledgement)
		).state
		val relocatedDecision = readerPresentationDecision(relocated)
		val relocatedSnapshot = snapshot(
			relocated,
			receiptVersion = version.copy(eventSequence = 3L)
		)
		assertEquals(
			ReaderDeckAdmissionCurrency.Current,
			readerDeckAdmissionAuthorityCurrency(relocatedSnapshot, admission, relocatedDecision)
		)

		val publicationIdentity = assertNotNull(version.publicationIdentity)
		val adversarialAuthorities = listOf(
			snapshot(
				claimed,
				bindingSeed = originBinding.copy(
					viewportGeneration = originBinding.viewportGeneration + 1L
				)
			),
			snapshot(claimed, hostEpoch = 2L),
			snapshot(
				claimed,
				viewerGeneration = 2L,
				receiptVersion = version.copy(readerSessionGeneration = 2L)
			),
			snapshot(
				claimed,
				receiptVersion = version.copy(
					publicationIdentity = publicationIdentity.copy(
						publicationGeneration = publicationIdentity.publicationGeneration + 1L
					)
				)
			),
			snapshot(claimed, lifecycle = ReaderPresentationLifecycleState.Background),
			relocatedSnapshot.copy(
				rendererSuccessorReceipt = assertNotNull(relocated.rendererSuccessorReceipt).copy(
					textureGeneration = capability.textureGeneration + 1L
				)
			)
		)
		adversarialAuthorities.forEach { adversarial ->
			assertEquals(
				ReaderDeckAdmissionCurrency.Revoked,
				readerDeckAdmissionAuthorityCurrency(adversarial, admission, adversarial.decision)
			)
		}
		assertEquals(
			ReaderDeckAdmissionCurrency.Revoked,
			readerDeckAdmissionAuthorityCurrency(
				relocatedSnapshot,
				admission,
				relocatedDecision.copy(targetBinding = null)
			)
		)

		val revokedBeforeOwnership = ReaderDeckAdmission(capability.copy(admissionId = 2L))
		assertFalse(revokedBeforeOwnership.requestRelease())
		assertEquals(
			ReaderDeckAdmissionCallbackDisposition.DuplicateOrTerminal,
			revokedBeforeOwnership.observeCallback()
		)
		assertEquals(
			ReaderDeckAdmissionOwnershipDisposition.Release,
			revokedBeforeOwnership.acknowledgeRendererOwnership()
		)
		assertTrue(revokedBeforeOwnership.markReleased())
		assertFalse(revokedBeforeOwnership.markReleased())
		assertFalse(revokedBeforeOwnership.requestRelease())
		assertEquals(
			ReaderDeckAdmissionCallbackDisposition.DuplicateOrTerminal,
			revokedBeforeOwnership.observeCallback()
		)

		val activeRequest = request.copy(
			role = ReaderDeckSubmissionRole.Active,
			slot = ReaderDeckAdmissionSlot.Active
		)
		val active = ReaderDeckAdmission(
			assertNotNull(readerDeckAdmissionCapabilityOrNull(snapshot(claimed), 3L, activeRequest))
		)
		assertFalse(active.promotePendingToActive())
		assertEquals(
			ReaderDeckAdmissionCallbackDisposition.AwaitingRendererOwnership,
			active.observeCallback()
		)
		assertEquals(
			ReaderDeckAdmissionOwnershipDisposition.ValidateCallback,
			active.acknowledgeRendererOwnership()
		)
		assertNull(
			readerDeckAdmissionCapabilityOrNull(
				snapshot(claimed),
				4L,
				activeRequest.copy(slot = ReaderDeckAdmissionSlot.Pending)
			)
		)
	}

	private enum class Task373DeferredPendingAuthorityBoundary {
		Pause,
		HostEpoch
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373PausedDeferredPendingAdmissionCannotResumeUnderMatchingGesture() =
		task373InvalidatedDeferredPendingAdmission(
			Task373DeferredPendingAuthorityBoundary.Pause
		)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task373PriorEpochDeferredPendingAdmissionCannotUseReplacementAuthority() =
		task373InvalidatedDeferredPendingAdmission(
			Task373DeferredPendingAuthorityBoundary.HostEpoch
		)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private fun task373InvalidatedDeferredPendingAdmission(
		boundary: Task373DeferredPendingAuthorityBoundary
	) = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		var rasterReleases = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			onActiveRasterReleased = { rasterReleases += 1 }
		)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		controllerClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		val surface = controller.surfaceView
		val originalListener = surface.pageSurfaceListener
		val heldSourceReleases = mutableListOf<Pair<Long, karacken.curl.DeckReleaseReason>>()
		surface.pageSurfaceListener = task8ForwardingPageSurfaceListener(
			delegate = originalListener,
			onDeckReleased = { generation, reason ->
				if (generation == 301L) heldSourceReleases += generation to reason
				else originalListener.onDeckReleased(generation, reason)
			}
		)
		val gesture = 373_910L + boundary.ordinal
		var failure: Throwable? = null
		try {
			fixture.admissionHost.reservationsEnabled = false
			assertEquals(
				ReaderPageTurnStartResult.Settling,
				controller.start(gesture, PageChange.NEXT) { _, _ -> true }
			)
			assertEquals(gesture, controllerClass.task7Field("activeGestureId").get(controller))
			assertNotNull(controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller))
			assertNull(controllerClass.task7Field("pendingDeckGenerationId").get(controller))
			val decisionBeforeBoundary = controllerClass.task7Field("commonPresentationDecision")
				.get(controller) as ReaderPresentationDecision
			assertEquals(
				ReaderPresentationToken(
					gesture,
					ReaderPresentationTokenDomain.Gesture
				),
				decisionBeforeBoundary.rendererCallbackTokenOrNull()
			)
			val nextGenerationBeforeBoundary =
				controllerClass.task7Field("nextDeckGeneration").getLong(controller)
			val eventCountBeforeBoundary = fixture.store.presentationEvents.size

			when (boundary) {
				Task373DeferredPendingAuthorityBoundary.Pause -> {
					controller.onHostResumedChanged(false)
					controller.onHostResumedChanged(false)
				}
				Task373DeferredPendingAuthorityBoundary.HostEpoch -> {
					fixture.admissionHost.advanceHostEpoch()
					controller.onPresentationHostEpochChanged()
					controller.onPresentationHostEpochChanged()
				}
			}
			val waitingAfterBoundary =
				controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller)
			fixture.admissionHost.reservationsEnabled = true
			val resurrected = mutableSetOf<Long>()
			fun captureResurrection() {
				(controllerClass.task7Field("pendingDeckGenerationId").get(controller) as Long?)
					?.let(resurrected::add)
				@Suppress("UNCHECKED_CAST")
				val owners = controllerClass.task7Field("generationOwners").get(controller)
					as Map<Long, *>
				resurrected += owners.keys.filter { it > 301L }
				@Suppress("UNCHECKED_CAST")
				val admissions = controllerClass.task7Field("generationAdmissions").get(controller)
					as Map<Long, *>
				resurrected += admissions.keys.filter { it > 301L }
			}

			if (boundary == Task373DeferredPendingAuthorityBoundary.Pause) {
				controller.onHostResumedChanged(true)
			}
			captureResurrection()
			surface.pageSurfaceListener.onDeckSubmissionCapacityAvailable()
			captureResurrection()
			val currentDecision = controllerClass.task7Field("commonPresentationDecision")
				.get(controller) as ReaderPresentationDecision
			controller.synchronizePresentationDecision(currentDecision)
			ShadowLooper.runUiThreadTasks()
			captureResurrection()

			assertNull(
				waitingAfterBoundary,
				"$boundary must retire the gesture-bound Pending wait before retry"
			)
			assertTrue(resurrected.isEmpty(), "$boundary retry edges resurrected $resurrected")
			assertEquals(
				nextGenerationBeforeBoundary,
				controllerClass.task7Field("nextDeckGeneration").getLong(controller),
				"$boundary must not allocate a replacement admission generation"
			)
			assertNull(controllerClass.task7Field("pendingDeckGenerationId").get(controller))
			assertNull(controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller))
			val postBoundaryEvents = fixture.store.presentationEvents.drop(eventCountBeforeBoundary)
			assertTrue(
				postBoundaryEvents.none { it is ReaderPresentationEvent.NativePagePresented },
				"$boundary must not synthesize a native presentation proof"
			)
		} catch (caught: Throwable) {
			failure = caught
			throw caught
		} finally {
			try {
				fixture.admissionHost.reservationsEnabled = true
				controller.cancelGesture(gesture)
				heldSourceReleases.forEach { (generation, reason) ->
					originalListener.onDeckReleased(generation, reason)
				}
				surface.detach()
				val closed = controller.destroy()
				ShadowLooper.runUiThreadTasks()
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				assertEquals(1, rasterReleases, "Shared raster ownership must close exactly once")
			} catch (cleanup: Throwable) {
				if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private fun task373TerminalDeferredPendingAdmission(
		terminal: Task373DeferredPendingTerminal
	) = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		var rasterReleases = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			rejectClaims = terminal == Task373DeferredPendingTerminal.Rejected,
			onActiveRasterReleased = { rasterReleases += 1 }
		)
		val controller = fixture.controller
		val controllerClass = controller.javaClass
		controllerClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		val surface = controller.surfaceView
		val gesture = 373_902L + terminal.ordinal
		var failure: Throwable? = null
		try {
			if (terminal == Task373DeferredPendingTerminal.Cancelled) {
				fixture.admissionHost.reservationsEnabled = false
			}
			val result = controller.start(gesture, PageChange.NEXT) { _, _ -> true }
			when (terminal) {
				Task373DeferredPendingTerminal.Rejected ->
					assertIs<ReaderPageTurnStartResult.TerminalPublished>(result)
				Task373DeferredPendingTerminal.Cancelled -> {
					assertEquals(ReaderPageTurnStartResult.Settling, result)
					controller.cancelGesture(gesture)
				}
			}
			assertNull(controllerClass.task7Field("activeGestureId").get(controller))
			assertFalse(task8SurfaceGestureAccepted(surface))
			val waitingAfterTerminal =
				controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller)
			val nextGenerationAfterTerminal =
				controllerClass.task7Field("nextDeckGeneration").getLong(controller)
			val terminalEventCount = fixture.store.presentationEvents.size
			fixture.admissionHost.reservationsEnabled = true
			val resurrected = mutableSetOf<Long>()
			fun captureResurrection() {
				(controllerClass.task7Field("pendingDeckGenerationId").get(controller) as Long?)
					?.let(resurrected::add)
				@Suppress("UNCHECKED_CAST")
				val owners = controllerClass.task7Field("generationOwners").get(controller)
					as Map<Long, *>
				resurrected += owners.keys.filter { it > 301L }
				@Suppress("UNCHECKED_CAST")
				val admissions = controllerClass.task7Field("generationAdmissions").get(controller)
					as Map<Long, *>
				resurrected += admissions.keys.filter { it > 301L }
			}

			surface.pageSurfaceListener.onDeckSubmissionCapacityAvailable()
			captureResurrection()
			val currentDecision = controllerClass.task7Field("commonPresentationDecision")
				.get(controller) as ReaderPresentationDecision
			controller.synchronizePresentationDecision(currentDecision)
			captureResurrection()
			controllerClass.task7Method(
				"publishGestureTerminal",
				java.lang.Long.TYPE,
				ReaderPageGestureTerminalOutcome::class.java,
				ReaderPageGestureTerminalDetail::class.java
			).invoke(
				controller,
				gesture,
				ReaderPageGestureTerminalOutcome.CancelledByUser,
				ReaderPageGestureTerminalDetail.ControllerCancelled
			)
			ShadowLooper.runUiThreadTasks()
			captureResurrection()
			controller.onPresentationHostEpochChanged()
			controller.onHostResumedChanged(false)
			controller.onHostResumedChanged(true)
			captureResurrection()

			assertNull(waitingAfterTerminal, "A terminal gesture must retire its deferred Pending wait")
			assertTrue(resurrected.isEmpty(), "Terminal retry edges resurrected generations $resurrected")
			assertEquals(
				nextGenerationAfterTerminal,
				controllerClass.task7Field("nextDeckGeneration").getLong(controller),
				"A terminal Pending wait must not allocate a retry generation"
			)
			assertNull(controllerClass.task7Field("pendingDeckGenerationId").get(controller))
			assertNull(controllerClass.task7Field("awaitingLibraryDeckAdmission").get(controller))
			assertNull(controllerClass.task7Field("activeGestureId").get(controller))
			assertFalse(task8SurfaceGestureAccepted(surface))
			val postTerminalEvents = fixture.store.presentationEvents.drop(terminalEventCount)
			assertTrue(
				postTerminalEvents.none { it is ReaderPresentationEvent.NativePagePresented },
				"A terminal deferred Pending wait must not publish synthetic native proof"
			)
			assertTrue(
				postTerminalEvents.isEmpty(),
				"A terminal deferred Pending wait must not publish presentation events"
			)
		} catch (caught: Throwable) {
			failure = caught
			throw caught
		} finally {
			try {
				surface.detach()
				val closed = controller.destroy()
				ShadowLooper.runUiThreadTasks()
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				assertEquals(1, rasterReleases, "Shared raster ownership must close exactly once")
			} catch (cleanup: Throwable) {
				if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
			} finally {
				fixture.admissionHost.reservationsEnabled = true
				Dispatchers.resetMain()
			}
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336PendingCallbackAfterTapClaimPrepares() = task336PendingCallback(Task336PendingBoundary.AfterClaim)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336SynchronousPendingCallbackSurvivesTapClaim() = task336PendingCallback(Task336PendingBoundary.Synchronous)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336RejectedTapClaimDoesNotTransferPending() = task336PendingCallback(Task336PendingBoundary.ClaimRejected)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336RendererRejectionPublishesNoClaim() = task336PendingCallback(Task336PendingBoundary.RendererRejected)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336CancelledTapCallbackCannotPrepare() = task336PendingCallback(Task336PendingBoundary.Cancelled)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336SuccessorAuthorityRejectsPredecessorPending() = task336PendingCallback(Task336PendingBoundary.Successor)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336ChangedPreparationRejectsPending() = task336PendingCallback(Task336PendingBoundary.PreparationChanged)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336RetiredGenerationRejectsPending() = task336PendingCallback(Task336PendingBoundary.GenerationChanged)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336ChangedBindingRejectsPending() = task336PendingCallback(Task336PendingBoundary.BindingChanged)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336CancelledReceiptCannotTransferPending() = task336PendingCallback(Task336PendingBoundary.CancelDuringReceipt)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class])
	fun task336ReplacedReceiptCannotTransferPending() = task336PendingCallback(Task336PendingBoundary.BindingDuringReceipt)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private fun task336PendingCallback(boundary: Task336PendingBoundary) = runTest {
		Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
		val initial = task8SettledCurlSourceState()
		val fixture = task8CurlAuthorityFixture(initial, rejectClaims = boundary == Task336PendingBoundary.ClaimRejected)
		val controller = fixture.controller
		// The existing fixture imports generation301; real successor submissions must
		// start above it, as in task9DispatchedTimeoutRecovery, not at the cold counter1.
		controller.javaClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
		val surface = controller.surfaceView
		val gesture = 33611L
		val owners = controller.javaClass.task7Field("generationOwners").get(controller) as Map<*, *>
		val prepared = controller.javaClass.task7Field("preparedDeckGenerations").get(controller) as Set<*>
		val admissions = controller.javaClass.task7Field("generationAdmissions").get(controller) as Map<*, *>
		val predecessor = admissions[301L]
		var pending: Long? = null
		var synchronousCompletions = 0
		var failure: Throwable? = null
		fun replaceBinding() {
			val previous = assertNotNull(fixture.store.state?.binding)
			val receipt = assertNotNull(fixture.store.publish(ReaderPresentationEvent.BindingReplaced(previous,
				previous.copy(viewportGeneration = previous.viewportGeneration + 1L))))
			assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition)
			controller.synchronizePresentationDecision(readerPresentationDecision(receipt.postState))
		}
		val publish: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt? = { event ->
			if (event is ReaderPresentationEvent.CurlClaimed) {
				assertNull(
					controller.javaClass.task7Field("pendingDeckGenerationId").get(controller) as Long?,
					"Pending physical ownership must wait for the claim receipt"
				)
				assertTrue(owners.keys.none { (it as? Long)?.let { generation -> generation > 301L } == true })
			}
			fixture.store.publish(event).also { receipt ->
				receipt?.let { fixture.admissionHost.commit(it.postState, it.version) }
				if (event is ReaderPresentationEvent.CurlClaimed && receipt != null) {
					if (boundary == Task336PendingBoundary.CancelDuringReceipt || boundary == Task336PendingBoundary.BindingDuringReceipt) {
						controller.synchronizePresentationDecision(readerPresentationDecision(receipt.postState))
						if (boundary == Task336PendingBoundary.CancelDuringReceipt) controller.cancelGesture(gesture)
						else replaceBinding()
					}
				}
			}
		}
		controller.javaClass.task7Field("onPresentationEvent").set(controller, publish)
		try {
			if (boundary == Task336PendingBoundary.RendererRejected) surface.setVisible(false)
			val result = controller.start(gesture, PageChange.NEXT) { _, _ -> true }
			if (owners.containsKey(301L)) {
				assertTrue(predecessor === admissions[301L], "The admission transaction must not replace the retained predecessor")
			} else {
				assertFalse(task8SurfaceOwnsGeneration(surface, 301L))
			}
			if (boundary == Task336PendingBoundary.RendererRejected) {
				assertIs<ReaderPageTurnStartResult.TerminalPublished>(result)
				assertTrue(fixture.store.presentationEvents.none { it is ReaderPresentationEvent.CurlClaimed })
				assertNull(pending)
			} else if (
				boundary == Task336PendingBoundary.ClaimRejected ||
				boundary == Task336PendingBoundary.CancelDuringReceipt ||
				boundary == Task336PendingBoundary.BindingDuringReceipt
			) {
				assertIs<ReaderPageTurnStartResult.TerminalPublished>(result)
				assertNull(pending)
				assertTrue(
					admissions.filterKeys { (it as? Long)?.let { generation -> generation > 301L } == true }
						.values.all {
							(it as ReaderDeckAdmission).state in setOf(
								ReaderDeckAdmissionState.ReleaseRequested,
								ReaderDeckAdmissionState.Released
							)
						},
					"Receipt reentrancy may only leave a terminal successor admission"
				)
			} else {
				val generation = assertNotNull(
					controller.javaClass.task7Field("pendingDeckGenerationId").get(controller) as Long?
				).also { pending = it }
				assertFalse(generation == 301L)
				assertTrue(owners.containsKey(generation))
				assertEquals(ReaderPageTurnStartResult.Settling, result)
				if (boundary == Task336PendingBoundary.Synchronous) {
					surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(surface, generation)
					assertTrue(generation in prepared, "The first post-receipt callback must prepare")
					synchronousCompletions++
				}
				when (boundary) {
					Task336PendingBoundary.Cancelled -> controller.cancelGesture(gesture)
					Task336PendingBoundary.PreparationChanged -> controller.retryPreparation(1L)
					Task336PendingBoundary.GenerationChanged -> controller.onHostSizeChanged()
					Task336PendingBoundary.BindingChanged -> replaceBinding()
					Task336PendingBoundary.Successor -> {
						// Independent common authority input; the delayed callback still belongs
						// to the first actual turn. No callback fence or proof is rewritten.
						val successor = readerPresentationReduce(initial,
							assertNotNull(readerCurlClaimEvent(readerPresentationDecision(initial), gesture + 1L)))
						controller.synchronizePresentationDecision(successor.decision)
					}
					else -> Unit
				}
				if (boundary != Task336PendingBoundary.Synchronous) {
					surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE).invoke(surface, generation)
				}
				if (boundary == Task336PendingBoundary.AfterClaim || boundary == Task336PendingBoundary.Synchronous) {
					assertTrue(generation in prepared, "A newly accepted Pending deck must prepare under its successful Curl claim")
					assertTrue(owners.containsKey(generation))
					assertTrue(task8SurfaceOwnsGeneration(surface, generation))
					val admission = assertIs<ReaderDeckAdmission>(admissions[generation])
					assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, admission.state)
					assertTrue(
						fixture.admissionHost.isCurrent(admission.capability),
						"Both synchronous and deferred completion retain exact claim admission"
					)
					assertEquals(if (boundary == Task336PendingBoundary.Synchronous) 1 else 0, synchronousCompletions)
				} else {
					assertFalse(generation in prepared, "Cancelled, stale and foreign callback inputs cannot prepare")
				}
			}
		} catch (caught: Throwable) {
			failure = caught
			throw caught
		} finally {
			try {
				surface.detach()
				val closed = controller.destroy()
				ShadowLooper.runUiThreadTasks()
				withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } }
				assertTrue(owners.isEmpty())
				assertEquals(0, surface.pendingCallbackCount)
			} catch (cleanup: Throwable) {
				if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
			} finally {
				Dispatchers.resetMain()
			}
		}
	}

	private enum class Task373VisibilityRestoreBoundary {
		DelayedRestoreCaller,
		TemporarilyIneligiblePreparation
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task10ActualViewerAutomaticUnreadyCoverEntrySequence() = task336ActualViewerSequence(observeReconciliation = true)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task373QueuedLossThenDifferentRestoreRetryAdvancesPreparationOnce() =
		task336ActualViewerSequence(restoreBoundary = Task373VisibilityRestoreBoundary.DelayedRestoreCaller)

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task373TemporarilyIneligibleRestoreRetryRetainsFloorUntilLaterIngress() =
		task336ActualViewerSequence(restoreBoundary = Task373VisibilityRestoreBoundary.TemporarilyIneligiblePreparation)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336ReadyViewerReplacementRejectsRetiredDeckAndRequest() =
		task336ActualViewerSequence(readyReplacement = Task336ReadyReplacement.Viewer)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336ReadyEpochReplacementRejectsPreviousPublication() =
		task336ActualViewerSequence(readyReplacement = Task336ReadyReplacement.Epoch)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336ReadyObserverReentrantReplacementWinsWithoutStaleTail() =
		task336ActualViewerSequence(readyReplacement = Task336ReadyReplacement.Reentrant)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task377ReplacementReleaseBeforeProofRestoresSelectedPredecessor() =
		task336ActualViewerSequence(selectedPredecessorRollback = true)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task377HostEpochReplacementRetiresSoftPredecessorBeforeSuccessorRelease() =
		task336ActualViewerSequence(
			selectedPredecessorRollback = true,
			replaceHostEpochBeforeSuccessorRelease = true
		)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5OwnedSameCenterFreshCommitReconcilesPreparedDeck() = task336ActualViewerSequence(observeReconciliation = true) { type, viewer, relocate ->
		val deck = assertNotNull(type.task7Field("preparedActiveDeck").get(viewer) as ReaderPagePreparedActiveDeck?)
		val reporter = type.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
		val predecessor = assertNotNull(reporter.lastReportedBinding?.destinationCommitIdentity)
		val preparation = type.task7Field("pageRasterPreparationController").get(viewer) as ReaderPageRasterPreparationController
		assertTrue(preparation.prewarmAdjacent())
		assertTrue(preparation.javaClass.task7Field("prewarmInProgress").getBoolean(preparation))
		relocate(deck.sourceCenterPageIndex, "task336-session")
		val emitted = assertNotNull(reporter.lastReportedBinding?.destinationCommitIdentity)
		assertEquals(predecessor.foliateSessionId, emitted.foliateSessionId)
		assertTrue(emitted.commitSequence > predecessor.commitSequence)
		assertEquals(deck, type.task7Field("preparedActiveDeck").get(viewer),
			"Same-center source ingress must reconcile the still-owned deck through its actual observer")
		assertEquals(reporter.lastReportedBinding, type.task7Method("currentPresentationBinding").invoke(viewer))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5HostOnlyDelayedTapIsCancelledOnceWithoutReplay() = task336ActualViewerSequence { type, viewer, relocate ->
		val input = type.task7Field("pageInputSettlementHostController").get(viewer) as ReaderPageInputSettlementHostController
		val router = type.task7Field("pagePointerRouter").get(viewer)
		val terminalField = router.javaClass.task7Field("publishTerminal")
		@Suppress("UNCHECKED_CAST")
		val original = terminalField.get(router) as (Long, ReaderPageGestureTerminalOutcome) -> Unit
		val terminals = mutableListOf<Pair<Long, ReaderPageGestureTerminalOutcome>>()
		terminalField.set(router, { id: Long, outcome: ReaderPageGestureTerminalOutcome ->
			terminals += id to outcome
			original(id, outcome)
		})
		val downTime = 336500L
		val gesture = assertNotNull(input.dispatchPointer(ReaderPageHostPointerEvent.Down(20f, 30f, downTime)).gestureId)
		input.dispatchPointer(ReaderPageHostPointerEvent.Up)
		assertEquals(1, input.contentGestureTokenCount())
		val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		assertNull(curl.javaClass.task7Field("activeGestureId").get(curl), "This must be host-only delayed work")
		relocate(1, "task336-session")
		assertEquals(listOf(gesture to ReaderPageGestureTerminalOutcome.CancelledLifecycle), terminals)
		assertEquals(0, input.contentGestureTokenCount())
		assertNull(input.takeDelayedTap(downTime))
		assertFalse(input.completeDelayedTap(gesture, ReaderPageGestureTerminalOutcome.CompletedTapAction))
		input.dispatchPointer(ReaderPageHostPointerEvent.Up)
		assertEquals(1, terminals.size)
		assertEquals(0, (router as paige.navic.reader.ReaderPagePointerRouter).trackedSequenceCount())
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ChangedCenterDoesNotRestoreDeck() = task336ActualViewerSequence { type, viewer, relocate ->
		relocate(0, "task336-session")
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ChangedSessionDoesNotRestoreDeck() = task336ActualViewerSequence { type, viewer, relocate ->
		relocate(1, "task336-successor-session")
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ChangedProfileDoesNotRestoreDeck() = task336ExternalRelocationExclusion { type, viewer ->
		type.task7Method("setPageTurnSnapshotKey", java.lang.Integer.TYPE).invoke(viewer, 336502)
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ChangedPreparationDoesNotRestoreDeck() = task336ExternalRelocationExclusion { type, viewer ->
		val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		val state = type.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState
		curl.onPreparationStateChanged(state.copy(phase = ReaderPagePreparationPhase.Preparing,
			preparationGeneration = state.preparationGeneration + 1L))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ChangedRasterDoesNotRestoreDeck() = task336ExternalRelocationExclusion { type, viewer ->
		val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		(curl.javaClass.task7Field("bundleSource").get(curl) as ReaderPageTurnBundleSource).invalidate("task336-successor-raster")
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5FailedDeckDoesNotRestoreDeck() = task336ExternalRelocationExclusion { type, viewer ->
		val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		val state = type.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState
		curl.onPreparationStateChanged(state.copy(phase = ReaderPagePreparationPhase.Failed))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5RetiredDeckAndStaleCallbackDoNotRestoreDeck() = task336ActualViewerSequence { type, viewer, relocate ->
		val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		val retired = assertNotNull(curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
		curl.invalidate("task336-retired-deck")
		relocate(1, "task336-session")
		curl.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE).invoke(curl.surfaceView, retired)
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
		assertFalse((curl.javaClass.task7Field("preparedDeckGenerations").get(curl) as Set<*>).contains(retired))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ActivePendingSettlementIsCancelledWithoutRestore() = task336ActualViewerSequence { type, viewer, relocate ->
		val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		var terminals = 0
		assertEquals(ReaderPageTurnStartResult.Settling, curl.start(336501L, PageChange.NEXT) { _, _ -> terminals++; true })
		assertNotNull(curl.javaClass.task7Field("activeGestureId").get(curl))
		assertNotNull(curl.javaClass.task7Field("pendingDeckGenerationId").get(curl))
		assertTrue(curl.surfaceView.isSettlementRunning)
		relocate(1, "task336-session")
		assertEquals(1, terminals)
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ReentrantBindingAuthorityReplacementDoesNotRestoreDeck() = task336ActualViewerSequence { type, viewer, relocate ->
		val delivered = task336DuringExternalCleanup(type, viewer) {
			val reporter = type.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
			val binding = assertNotNull(reporter.lastReportedBinding)
			val replacement = binding.copy(viewportGeneration = binding.viewportGeneration + 1L)
			type.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java).invoke(viewer,
				ReaderPresentationEvent.BindingReplaced(binding, replacement))
			assertEquals(replacement, reporter.lastReportedBinding)
		}
		relocate(1, "task336-session")
		assertTrue(delivered(), "Binding replacement must run inside actual ExternalRelocation cleanup")
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ReentrantEpochReplacementDoesNotRestoreDeck() = task336ActualViewerSequence { type, viewer, relocate ->
		val delivered = task336DuringExternalCleanup(type, viewer) {
			val reporter = type.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
			val version = reporter.javaClass.task7Field("authoritativeVersion").get(reporter) as paige.navic.reader.ReaderPresentationReceiptVersion
			val state = reporter.javaClass.task7Field("authoritativeState").get(reporter) as ReaderPresentationState
			val epoch = reporter.captureEpoch()
			type.task7Method("preparePresentationEpoch", paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
				ReaderPresentationState::class.java, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
				.invoke(viewer, version.copy(readerSessionGeneration = version.readerSessionGeneration + 1L), state, false, true, false)
			assertTrue(reporter.captureEpoch() > epoch)
		}
		relocate(1, "task336-session")
		assertTrue(delivered(), "Epoch replacement must run inside actual ExternalRelocation cleanup")
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C5ReentrantOwnershipRetirementDoesNotRestoreDeck() = task336ActualViewerSequence { type, viewer, relocate ->
		val delivered = task336DuringExternalCleanup(type, viewer) {
			val curl = type.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
			curl.invalidate("task336-reentrant-retirement")
			assertFalse(curl.hasUsablePreparedActiveDeck())
		}
		relocate(1, "task336-session")
		assertTrue(delivered(), "Retirement must run inside actual ExternalRelocation cleanup")
		assertNull(type.task7Field("preparedActiveDeck").get(viewer))
	}

	private fun task336ExternalRelocationExclusion(change: (Class<*>, View) -> Unit) =
		task336ActualViewerSequence { type, viewer, relocate ->
			assertNotNull(type.task7Field("preparedActiveDeck").get(viewer))
			change(type, viewer)
			relocate(1, "task336-session")
			assertNull(type.task7Field("preparedActiveDeck").get(viewer), "Ineligible source ingress must not restore a deck")
		}

	// Delegate every real cancellation operation; inject only an adverse reentrant input.
	private fun task336DuringExternalCleanup(type: Class<*>, viewer: View, action: () -> Unit): () -> Boolean {
		val input = type.task7Field("pageInputSettlementHostController").get(viewer) as ReaderPageInputSettlementHostController
		val field = input.javaClass.task7Field("cancellationPort")
		val original = field.get(input) as ReaderPageHostCancellationPort
		var delivered = false
		field.set(input, object : ReaderPageHostCancellationPort by original {
			override fun cancelActiveRendererGesture(reason: paige.navic.reader.ReaderPageLifecycleCancellationReason) {
				original.cancelActiveRendererGesture(reason)
				if (!delivered && reason == paige.navic.reader.ReaderPageLifecycleCancellationReason.RasterProfileInvalidated &&
					Throwable().stackTrace.any { it.className == type.name && it.methodName == "setPageTurnVisualLocation" }) {
					delivered = true
					action()
				}
			}
		})
		return { delivered }
	}

	private enum class Task336C6Boundary { Demand, Predecessor, NativeReceipt, Retirement, Canonical, NoDemand, PostDispatchSupersession }
	private enum class Task336C6NativeBoundary { Rejected, Binding, Token, Epoch }

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6GenuineDemandWaitsForExactCurlNativeProofThenCompletesOnce() =
		task336ActualViewerSequence(c6Boundary = Task336C6Boundary.Demand)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6PredecessorProofWithNoTransitionCannotResumeDemand() =
		task336ActualViewerSequence(c6Boundary = Task336C6Boundary.Predecessor)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6RejectedOrReplacedNativeReceiptCannotResumeDemand() {
		var failure: Throwable? = null
		Task336C6NativeBoundary.entries.forEach { boundary ->
			try {
				task336ActualViewerSequence(c6Boundary = Task336C6Boundary.NativeReceipt, c6NativeBoundary = boundary)
			} catch (caught: Throwable) {
				val original = failure
				if (original == null) failure = caught else original.addSuppressed(caught)
			}
		}
		failure?.let { throw it }
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6DeferredDemandCoalescesAndRetiresWithoutResurrection() =
		task336ActualViewerSequence(c6Boundary = Task336C6Boundary.Retirement)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6ColdAndCanonicalPreparationRemainLiveBeforeNativeReady() =
		task336ActualViewerSequence(c6Boundary = Task336C6Boundary.Canonical)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6SameCenterAndNativeProofWithoutDemandDoNotNavigateAgain() =
		task336ActualViewerSequence(c6Boundary = Task336C6Boundary.NoDemand)

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [Task8ImmediateGlSurfaceViewShadow::class, Task336ViewTreeLifecycleOwnerShadow::class])
	fun task336C6PostDispatchSameCenterSupersessionRetiresBeforeLateReceipt() =
		task336ActualViewerSequence(c6Boundary = Task336C6Boundary.PostDispatchSupersession)

	private fun task336AssertSupersededInitialLiveReceipt(
		curl: ReaderPlayLikeCurlFoliateController,
		request: Any,
		receipts: java.util.ArrayDeque<Pair<Any, android.webkit.ValueCallback<String>>>,
		sourceReceipts: java.util.IdentityHashMap<Any, String>,
		commandCount: () -> Int,
		supersede: () -> Unit,
		onReceiptDelivered: (Boolean) -> Unit
	) {
		fun currentRequest() = curl.javaClass.task7Field("initialLivePresentationAuthority").get(curl)
		val ownership = curl.javaClass.task7Field("foregroundWebViewOwnership").get(curl) as ReaderForegroundWebViewOwnership
		val claim = request.javaClass.task7Field("claim").get(request) as ReaderForegroundWebViewLiveClaim
		val target = request.javaClass.task7Field("target").get(request) as ReaderPageTurnPresentationTarget.Live
		val mutation = ReaderForegroundWebViewMutationGeneration(target.foregroundMutationGeneration)
		val confirmation = request.javaClass.task7Field("confirmationPending")
		fun oldClaimOwned() = (ownership.javaClass.task7Field("liveClaims").get(ownership) as Map<*, *>).containsKey(claim.claimId)
		assertSame(request, currentRequest(), "The own exact source acknowledgement must retain its request until strict confirmation")
		assertTrue(confirmation.getBoolean(request) && ownership.isCurrent(claim, mutation) && oldClaimOwned())
		val held = receipts.single()
		assertSame(request, held.first)
		val encoded = assertNotNull(sourceReceipts[request], "Hold only the actual own-source getter response")
		assertTrue(readerPageTurnPresentationReceipt(encoded)?.matches(target) == true)
		val commandsBefore = commandCount()
		val claimsBefore = ownership.javaClass.task7Field("nextClaimId").getLong(ownership)
		val mutationBefore = ownership.javaClass.task7Field("mutationGeneration").getLong(ownership)
		val continuationField = curl.javaClass.task7Field("onCanonicalLiveCommitIssued")
		@Suppress("UNCHECKED_CAST")
		val original = continuationField.get(curl) as () -> Boolean
		var continuationCalls = 0
		val observer: () -> Boolean = { continuationCalls++; original() }
		continuationField.set(curl, observer)
		try {
			supersede()
			val oldClaimOwnedBeforeCallback = oldClaimOwned()
			val retiredBeforeCallback = currentRequest() == null && !oldClaimOwnedBeforeCallback &&
				request.javaClass.task7Field("claim").get(request) == null && !confirmation.getBoolean(request)
			assertEquals(1, receipts.size, "The strict getter must remain held across the superseding source input")
			assertSame(held.second, receipts.single().second)
			// Deliver the real held callback even on RED, before asserting either captured boundary.
			held.second.onReceiveValue(encoded)
			onReceiptDelivered(continuationCalls > 0)
			assertSame(held.second, receipts.removeFirst().second)
			sourceReceipts.remove(request)
			val noNewDemand = commandCount() == commandsBefore &&
				ownership.javaClass.task7Field("nextClaimId").getLong(ownership) == claimsBefore &&
				ownership.javaClass.task7Field("mutationGeneration").getLong(ownership) == mutationBefore
			val lateCallbackInert = continuationCalls == 0 && currentRequest() == null &&
				!oldClaimOwned() && !confirmation.getBoolean(request) && noNewDemand
			println("TASK336_C6 boundary=PostDispatchSupersession ownAckRetained=true heldGetter=true " +
				"retiredBeforeCallback=$retiredBeforeCallback oldClaimOwnedBeforeCallback=$oldClaimOwnedBeforeCallback " +
				"lateCallbackDelivered=true continuationCalls=$continuationCalls lateCallbackInert=$lateCallbackInert " +
				"noNewDemand=$noNewDemand pendingReceipts=${receipts.size} pendingSourceReceipts=${sourceReceipts.size}")
			assertTrue(retiredBeforeCallback && lateCallbackInert,
				"Untagged same-center supersession must retire before the held getter and make its late callback inert " +
					"(retiredBeforeCallback=$retiredBeforeCallback, lateCallbackInert=$lateCallbackInert, continuationCalls=$continuationCalls)")
		} finally {
			continuationField.set(curl, original)
		}
	}

	private fun task336CurrentLivePublicationObserver(
		bundle: ReaderPageTurnBundleSource,
		webView: Task9RecoveryCommandWebView,
		boundary: Task336C6Boundary?,
		variant: Task336C6NativeBoundary,
		observationFailed: java.util.concurrent.atomic.AtomicBoolean
	): ReaderPageRasterCurrentLivePublicationPort {
		val observed = java.util.concurrent.atomic.AtomicBoolean()
		return ReaderPageRasterCurrentLivePublicationPort { reference, priority, isStillCurrent, onPublished ->
			if (!observed.compareAndSet(false, true)) {
				bundle.ensurePersistentSnapshot(
					snapshot = reference, priority = priority,
					isStillCurrent = isStillCurrent, onPersisted = onPublished
				)
			} else {
				var registeredPresent: Boolean? = null
				var registeredAttached: Boolean? = null
				var registeredIsFixture: Boolean? = null
				var bundleClosed: Boolean? = null
				var descriptorsBefore: Int? = null
				try {
					val registered = (bundle.javaClass.task7Field("activeWebView").get(bundle)
						as java.lang.ref.WeakReference<*>).get() as WebView?
					registeredPresent = registered != null
					registeredAttached = registered?.isAttachedToWindow == true
					registeredIsFixture = registered === webView
					bundleClosed = bundle.javaClass.task7Field("closed").getBoolean(bundle)
					descriptorsBefore = webView.descriptorRequests
				} catch (_: Throwable) {
					observationFailed.set(true)
				}
				val calls = java.util.concurrent.atomic.AtomicInteger()
				val accepted = java.util.concurrent.atomic.AtomicInteger()
				val rejected = java.util.concurrent.atomic.AtomicInteger()
				val returned = java.util.concurrent.atomic.AtomicBoolean()
				val completionObserved = java.util.concurrent.atomic.AtomicBoolean()
				try {
					bundle.ensurePersistentSnapshot(
						snapshot = reference,
						priority = priority,
						isStillCurrent = {
							calls.incrementAndGet()
							val current = isStillCurrent() // Exactly one genuine evaluation; exceptions propagate.
							if (current) accepted.incrementAndGet() else rejected.incrementAndGet()
							current
						},
						onPersisted = { completion ->
							if (completionObserved.compareAndSet(false, true)) {
								try {
									println("TASK336_CURRENT_LIVE first=true boundary=${boundary?.name ?: "Default"} " +
										"variant=${variant.name} registeredPresent=$registeredPresent " +
										"registeredAttached=$registeredAttached registeredIsFixture=$registeredIsFixture " +
										"bundleClosed=$bundleClosed currentnessCalls=${calls.get()} " +
										"currentnessAccepted=${accepted.get()} currentnessRejected=${rejected.get()} " +
										"descriptorDelta=${descriptorsBefore?.let { webView.descriptorRequests - it }} " +
										"callbackSynchronous=${!returned.get()} result=${completion.result.name} " +
										"writeFailure=${completion.writeFailureReason?.name ?: "None"} " +
										"observationFailed=${observationFailed.get()}")
								} catch (_: Throwable) {
									observationFailed.set(true)
								}
							}
							onPublished(completion) // Preserve the actual callback and its exception path.
						}
					)
				} finally {
					returned.set(true)
				}
			}
		}
	}

	private fun task336DecodedRefillCompletionAssertion(
		curl: ReaderPlayLikeCurlFoliateController,
		pendingGeneration: Long,
		destinationOrdinal: Int
	): (List<Job>) -> Unit {
		fun field(owner: Any, name: String) = owner.javaClass.task7Field(name).get(owner)
		// Capture after the genuine pending renderer callback, before animator.end():
		// settlement may finish its lawful refill before the exact ACK is delivered.
		val generationBefore = pendingGeneration
		assertEquals(generationBefore, field(curl, "pendingDeckGenerationId"))
		assertEquals(ReaderDeckSubmissionRole.Pending, (field(curl, "generationRoles") as Map<*, *>)[generationBefore])
		assertTrue(generationBefore in (field(curl, "preparedDeckGenerations") as Set<*>),
			"The genuine pending renderer callback must have been accepted before capture")
		val ownerBefore = assertNotNull((field(curl, "generationOwners") as Map<*, *>)[generationBefore])
		val decodedBefore = assertNotNull(field(curl, "activePages"))
		val profileBefore = field(ownerBefore, "profile") as ReaderPlayLikeCurlRasterProfile
		val sourceOrdinal = field(curl, "currentOrdinal") as Int
		assertEquals(ReaderPlayLikeCurlOrientation.Portrait, profileBefore.orientation)
		assertEquals(6, profileBefore.pageCount)
		assertEquals(0, sourceOrdinal)
		assertEquals(1, destinationOrdinal)
		assertEquals(field(curl, "requestedProfile"), profileBefore)
		assertEquals(profileBefore, field(decodedBefore, "profile"))
		val sourceWindow = readerPlayLikeCurlPreparedPageIndices(profileBefore.orientation, sourceOrdinal,
			profileBefore.pageCount, profileBefore.readerDirection, profileBefore.spreadAnchorParity)
		val destinationWindow = readerPlayLikeCurlPreparedPageIndices(profileBefore.orientation, destinationOrdinal,
			profileBefore.pageCount, profileBefore.readerDirection, profileBefore.spreadAnchorParity)
		assertEquals(setOf(0, 1, 2), sourceWindow.toSet())
		assertEquals(setOf(0, 1, 2, 3), destinationWindow.toSet())
		val ownerDeck = field(ownerBefore, "deck") as ReaderPlayLikeCurlRasterDeck<*>
		val decodedDeck = field(decodedBefore, "deck") as ReaderPlayLikeCurlRasterDeck<*>
		assertTrue(ownerDeck.pageIndices.containsAll(sourceWindow))
		assertFalse(ownerDeck.pageIndices.containsAll(destinationWindow),
			"The actual pending-owner source deck must not cover the expanded destination window")
		assertTrue(decodedDeck.pageIndices.containsAll(sourceWindow))
		assertFalse(decodedDeck.pageIndices.containsAll(destinationWindow),
			"The captured decoded source must require real destination refill, not merely a different private center")
		println("TASK336_REFILL_ORDER boundary=BeforeSecondSettlement pendingPrepared=true " +
			"ownerCoversDestination=false decodedCoversDestination=false ownerIsDecoded=${ownerBefore === decodedBefore}")
		return { consumers ->
			val generation = field(curl, "activeDeckGenerationId") as Long?
			val owner = (field(curl, "generationOwners") as Map<*, *>)[generation]
			val decoded = field(curl, "activePages")
			val profile = owner?.let { field(it, "profile") } as ReaderPlayLikeCurlRasterProfile?
			val preparation = field(curl, "preparationGeneration")
			val target = (field(curl, "commonPresentationDecision") as ReaderPresentationDecision).targetBinding
			val executor = field(curl, "mainTerminalExecutor") as ReaderMainTerminalActionExecutor
			val clauses = linkedMapOf(
				"observedConsumersCompleted" to consumers.all { it.isCompleted && !it.isCancelled },
				"finiteConsumersEmpty" to (field(curl, "teardownJob") as Job).children.none { it !== executor.drainJob && !it.isCompleted },
				"terminalActionsEmpty" to (executor.pendingActionCount() == 0),
				"generationSurvived" to (generation != null && generation == generationBefore),
				"ownerSurvived" to (owner != null && owner === ownerBefore),
				"decodedReplaced" to (decoded != null && decoded !== decodedBefore),
				"decodedDiffersFromOwner" to (decoded != null && owner != null && decoded !== owner),
				"ownerNotObsolete" to (owner != null && field(owner, "obsolete") == false),
				"decodedNotObsolete" to (decoded != null && field(decoded, "obsolete") == false),
				"ownerListsGeneration" to (owner != null && generation in (field(owner, "generations") as Set<*>)),
				"roleActive" to ((field(curl, "generationRoles") as Map<*, *>)[generation] == ReaderDeckSubmissionRole.Active),
				"generationPrepared" to (generation != null && generation in (field(curl, "preparedDeckGenerations") as Set<*>)),
				"textureReady" to ((field(curl, "readinessState") as ReaderPageRendererReadinessState).textureDeck == ReaderTextureDeckState.Ready),
				"profilesCurrent" to (profile != null && profile == field(curl, "requestedProfile") &&
					profile == field(curl, "publishedRasterProfile") && decoded != null && profile == field(decoded, "profile")),
				"rasterCurrent" to (profile != null && profile.rasterGeneration == field(field(curl, "bundleSource")!!, "activeGeneration")),
				"decodedCenterCurrent" to (decoded != null && field(decoded, "centerOrdinal") == field(curl, "currentOrdinal")),
				"sourceOrdinalCurrent" to (field(curl, "currentOrdinal") == field(curl, "currentWebViewOrdinal")),
				"ownerPreparationCurrent" to ((field(curl, "generationPreparationGenerations") as Map<*, *>)[generation] == preparation &&
					field(curl, "activeDeckPreparationGeneration") == preparation),
				"targetCurrent" to (target != null && target.textureGeneration == generation && target.rasterGeneration == profile?.rasterGeneration &&
					target.profileGeneration == field(curl, "publishedRasterProfileEpoch") && target.preparationGeneration == preparation &&
					target.foliateSessionId == field(curl, "currentFoliateSessionId")),
				"notFailed" to (field(curl, "failedPreparationGeneration") != preparation),
				"attachedCurrent" to (field(curl, "enabled") == true && field(curl, "attached") == true && field(curl, "destroyed") == false)
			)
			val firstReject = clauses.entries.firstOrNull { !it.value }?.key ?: "None"
			println("TASK336_REFILL_ORDER boundary=AfterSecondExactAckDrain consumersObserved=${consumers.size} " +
				"consumersCompleted=${consumers.count { it.isCompleted }} consumersCancelled=${consumers.count { it.isCancelled }} " +
				"firstReject=$firstReject " + clauses.entries.joinToString(" ") { "${it.key}=${it.value}" })
			assertTrue(clauses.values.all { it }, "Second exact acknowledgement requires observed lawful decoded replacement before cover: $firstReject")
		}
	}

	private fun task336ObserveReadyOwnerCallbacks(
		viewerClass: Class<*>, viewer: View, curl: ReaderPlayLikeCurlFoliateController,
		preparation: ReaderPageRasterPreparationController, reporter: ReaderPresentationBindingReporter,
		events: List<ReaderPresentationEvent>, commonState: () -> ReaderPresentationState,
		observing: () -> Boolean
	): () -> Unit {
		fun field(owner: Any, name: String) = owner.javaClass.task7Field(name).get(owner)
		val preparationCallback = preparation.javaClass.task7Field("onPreparationStateChange")
		val deckCallback = curl.javaClass.task7Field("onPreparedActiveDeckChanged")
		@Suppress("UNCHECKED_CAST")
		val originalPreparation = preparationCallback.get(preparation) as (ReaderPagePreparationState) -> Unit
		@Suppress("UNCHECKED_CAST")
		val originalDeck = deckCallback.get(curl) as (ReaderPagePreparedActiveDeck?) -> Unit
		var observationFailed = false
		var readyCalls = 0
		var publications = 0
		var nonNullPublications = 0
		fun observingSafely() = try { observing() } catch (_: Throwable) { observationFailed = true; false }
		fun readyReports(): Int? = try {
			events.count { it is ReaderPresentationEvent.PreparationReported && it.facts.phase == ReaderPagePreparationPhase.Ready }
		} catch (_: Throwable) { observationFailed = true; null }
		fun observe(boundary: String, before: Pair<Long?, Any?>? = null): Pair<Long?, Any?>? = try {
			val generation = field(curl, "activeDeckGenerationId") as Long?
			val owner = (field(curl, "generationOwners") as Map<*, *>)[generation]
			val profile = owner?.let { field(it, "profile") } as ReaderPlayLikeCurlRasterProfile?
			val state = commonState()
			val target = state.binding
			val reported = reporter.lastReportedBinding
			val hostDeck = viewerClass.task7Field("preparedActiveDeck").get(viewer) as ReaderPagePreparedActiveDeck?
			val hostPreparation = field(viewer, "latestRasterPreparationState") as ReaderPagePreparationState
			val controllerPreparation = field(curl, "preparationGeneration")
			val hostProfile = field(viewer, "rasterProfileEpoch") as Long?
			val session = field(viewer, "pageTurnFoliateSessionId") as String?
			val ordinal = field(viewer, "pageTurnVisualPageIndex") as Int?
			val publication = field(viewer, "presentationPublicationGeneration") as Long
			val viewport = field(viewer, "presentationViewportGeneration") as Long
			val destination = field(viewer, "presentationDestinationCommitIdentity") as ReaderDestinationCommitIdentity?
			val replacement = field(viewer, "presentationViewerReplacementFence")!!
			val rejectedProfile = field(replacement, "rejectedRasterProfileEpoch") as Long?
			// Comparison-only projection of fields; never call host binding or admission predicates.
			val exactDeck = hostDeck?.takeIf {
				hostProfile != null && field(replacement, "isPending") == false &&
					(rejectedProfile == null || it.rasterProfileEpoch > rejectedProfile) &&
					it.rasterProfileEpoch == hostProfile && it.sourceCenterPageIndex == ordinal &&
					it.preparationGeneration == hostPreparation.preparationGeneration
			}
			val physicalPresent = field(viewer, "lastPresentationWindowVisible") != false &&
				!session.isNullOrBlank() && ordinal != null && ordinal >= 0 && publication > 0 && viewport > 0 &&
				viewer.width > 0 && viewer.height > 0 && hostPreparation.preparationGeneration >= 0 &&
				(destination == null || destination.foliateSessionId == session)
			fun physicalMatches(binding: ReaderPresentationBinding?) = if (!physicalPresent) binding == null else
				binding != null && binding.foliateSessionId == session && binding.publicationGeneration == publication &&
					binding.viewportGeneration == viewport && binding.profileGeneration == (hostProfile ?: 0L) &&
					binding.destinationCommitIdentity == destination &&
					binding.preparationGeneration == hostPreparation.preparationGeneration &&
					binding.rasterGeneration == exactDeck?.rasterEpoch && binding.textureGeneration == exactDeck?.generationId
			println("TASK336_READY_OWNER boundary=$boundary projection=FieldDerived readyCalls=$readyCalls " +
				"publications=$publications nonNullPublications=$nonNullPublications readyReports=${readyReports()} " +
				"hostPhase=${hostPreparation.phase} controllerPhase=${field(curl, "preparationPhase")} commonPhase=${state.preparationFacts.phase} " +
				"generationPresent=${generation != null} ownerPresent=${owner != null} activePagesPresent=${field(curl, "activePages") != null} " +
				"ownerIsActivePages=${owner != null && owner === field(curl, "activePages")} " +
				"ownerSameAcrossReady=${before?.let { generation == it.first && owner === it.second }} " +
				"ownerNotObsolete=${owner?.let { field(it, "obsolete") == false }} " +
				"ownerListsGeneration=${owner?.let { generation in (field(it, "generations") as Set<*>) }} " +
				"roleActive=${(field(curl, "generationRoles") as Map<*, *>)[generation] == ReaderDeckSubmissionRole.Active} " +
				"generationPrepared=${generation != null && generation in (field(curl, "preparedDeckGenerations") as Set<*>)} " +
				"ownerProfileCurrent=${profile != null && profile == field(curl, "requestedProfile") && profile == field(curl, "publishedRasterProfile")} " +
				"ownerRasterCurrent=${profile != null && profile.rasterGeneration == field(field(curl, "bundleSource")!!, "activeGeneration")} " +
				"targetPresent=${target != null} targetTextureSame=${generation != null && target?.textureGeneration == generation} " +
				"targetRasterSame=${profile != null && target?.rasterGeneration == profile.rasterGeneration} " +
				"targetProfileSame=${target != null && target.profileGeneration == field(curl, "publishedRasterProfileEpoch")} " +
				"targetSessionSame=${target != null && target.foliateSessionId == field(curl, "currentFoliateSessionId")} " +
				"controllerDecisionSame=${field(curl, "commonPresentationDecision") == field(viewer, "presentationDecision")} " +
				"preparationCurrent=${controllerPreparation == hostPreparation.preparationGeneration && target?.preparationGeneration == controllerPreparation} " +
				"ownerPreparationCurrent=${(field(curl, "generationPreparationGenerations") as Map<*, *>)[generation] == controllerPreparation && field(curl, "activeDeckPreparationGeneration") == controllerPreparation} " +
				"notFailed=${field(curl, "failedPreparationGeneration") != controllerPreparation} retry=${field(curl, "retryPreparationInProgress")} " +
				"ordinalSame=${ordinal != null && field(curl, "currentOrdinal") == ordinal && field(curl, "currentWebViewOrdinal") == ordinal} " +
				"ownerCenterSame=${owner != null && field(owner, "centerOrdinal") == field(curl, "currentOrdinal")} " +
				"texturePhase=${(field(curl, "readinessState") as ReaderPageRendererReadinessState).textureDeck} " +
				"deferredRefillPresent=${field(curl, "deferredDecodedRefillCenterOrdinal") != null} " +
				"enabled=${field(curl, "enabled")} attached=${field(curl, "attached")} destroyed=${field(curl, "destroyed")} " +
				"hostDeckPresent=${hostDeck != null} physicalPresent=$physicalPresent physicalDeckPresent=${exactDeck != null} " +
				"targetPhysical=${physicalMatches(target)} reporterPhysical=${physicalMatches(reported)} targetReporter=${target == reported}")
			generation to owner
		} catch (_: Throwable) {
			observationFailed = true
			null
		}
		val preparationObserver: (ReaderPagePreparationState) -> Unit = { state ->
			if (!observingSafely() || state.phase != ReaderPagePreparationPhase.Ready) originalPreparation(state)
			else {
				readyCalls++
				val publicationsBefore = publications
				val reportsBefore = readyReports()
				val before = observe("ReadyBefore") // Owner reference lives only across this synchronous original call.
				var returned = false
				try { originalPreparation(state); returned = true } finally {
					observe("ReadyAfter", before)
					try {
						println("TASK336_READY_OWNER boundary=ReadyReturned originalReturned=$returned " +
							"publicationDelta=${publications - publicationsBefore} readyReportDelta=${reportsBefore?.let { beforeCount -> readyReports()?.minus(beforeCount) }} " +
							"observationFailed=$observationFailed")
					} catch (_: Throwable) { observationFailed = true }
				}
			}
		}
		val deckObserver: (ReaderPagePreparedActiveDeck?) -> Unit = { deck ->
			val enabled = observingSafely()
			if (enabled) { publications++; if (deck != null) nonNullPublications++; observe("DeckBefore") }
			var returned = false
			try { originalDeck(deck); returned = true } finally {
				if (enabled) {
					observe("DeckAfter")
					try {
						println("TASK336_READY_OWNER boundary=DeckReturned originalReturned=$returned incomingPresent=${deck != null}")
					} catch (_: Throwable) { observationFailed = true }
				}
			}
		}
		val restore: () -> Unit = {
			try { preparationCallback.set(preparation, originalPreparation) }
			finally { deckCallback.set(curl, originalDeck) }
			assertSame(originalPreparation, preparationCallback.get(preparation))
			assertSame(originalDeck, deckCallback.get(curl))
			assertFalse(observationFailed, "Ready-owner observation must not fail while reading scalar evidence")
		}
		try { preparationCallback.set(preparation, preparationObserver); deckCallback.set(curl, deckObserver) }
		catch (failure: Throwable) {
			try { restore() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
			throw failure
		}
		return restore
	}

	private enum class Task336ReadyReplacement { Viewer, Epoch, Reentrant }

	private inner class Task336ReadyReplacementProbe(
		private val mode: Task336ReadyReplacement,
		private val type: Class<*>, private val viewer: View,
		private val curl: ReaderPlayLikeCurlFoliateController,
		private val preparation: ReaderPageRasterPreparationController,
		private val reporter: ReaderPresentationBindingReporter,
		private val common: () -> ReaderController,
		private val assertNoNewDemand: () -> Unit
	) {
		private fun field(owner: Any, name: String) = owner.javaClass.task7Field(name).get(owner)
		private val preparationField = preparation.javaClass.task7Field("onPreparationStateChange")
		private val deckField = curl.javaClass.task7Field("onPreparedActiveDeckChanged")
		@Suppress("UNCHECKED_CAST")
		private val originalPreparation = preparationField.get(preparation) as (ReaderPagePreparationState) -> Unit
		@Suppress("UNCHECKED_CAST")
		private val originalDeck = deckField.get(curl) as (ReaderPagePreparedActiveDeck?) -> Unit
		private val failures = mutableListOf<Throwable>()
		private var readyEntered = false
		private var readyForwarded = 0
		private var replaced = false
		private var deckDepth = 0
		private var publications = 0
		private var stalePublications = 0
		private var replacementEpoch: Long? = null
		private fun checked(action: () -> Unit) {
			try { action() } catch (failure: Throwable) { failures += failure }
		}
		private fun replace() {
			check(!replaced)
			replaced = true
			val oldEpoch = reporter.captureEpoch()
			val oldBinding = assertNotNull(common().state.presentationDecision.targetBinding)
			val oldRequest = (common().state.presentation.authority as? ReaderPresentationAuthority.BlockingPreparation)
				?.nativePresentationRequest
			if (mode != Task336ReadyReplacement.Epoch) {
				val oldExecutableRequest = field(curl, "initialLivePresentationAuthority")
				val oldClaim = oldExecutableRequest?.let { field(it, "claim") as ReaderForegroundWebViewLiveClaim? }
				// Real renderer/content replacement; the new viewer deliberately has no WebView yet.
				type.task7Method("replaceViewerContent", View::class.java).invoke(viewer, View(viewer.context))
				assertNull(field(curl, "activeDeckGenerationId"))
				assertNull(field(viewer, "preparedActiveDeck"))
				assertNull(field(preparation, "activeDeckPreparationGeneration"))
				assertFalse((field(viewer, "latestRasterPreparationState") as ReaderPagePreparationState).phase == ReaderPagePreparationPhase.Ready)
				assertFalse(type.task7Method("currentPresentationBinding").invoke(viewer) == oldBinding)
				// Common entry intent may survive; executable ownership and physical proof admission must not.
				assertNull(field(curl, "initialLivePresentationAuthority"))
				oldExecutableRequest?.let { request ->
					assertNull(field(request, "acceptedNativeProof"))
					assertNull(field(request, "ownSourceBinding"))
					assertNull(field(request, "claim"))
					assertEquals(false, field(request, "confirmationPending"))
				}
				oldClaim?.let { claim ->
					val ownership = field(curl, "foregroundWebViewOwnership")
					assertFalse((field(ownership, "liveClaims") as Map<*, *>).containsKey(claim.claimId))
				}
				assertFalse(type.task7Method("currentPresentationBinding").invoke(viewer) == assertNotNull(oldRequest).binding)
				assertNull(type.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer))
			}
			val version = common().presentationVersion.let { it.copy(readerSessionGeneration = it.readerSessionGeneration + 1L) }
			val result = type.task7Method("preparePresentationEpoch", paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
				ReaderPresentationState::class.java, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
				.invoke(viewer, version, common().state.presentation, false, true, false)
			assertEquals(ReaderPresentationEpochPreparation.Replaced, result)
			assertTrue(reporter.captureEpoch() > oldEpoch)
			replacementEpoch = reporter.captureEpoch()
			assertNull(reporter.lastReportedBinding)
			assertFalse(type.task7Method("currentPresentationBinding").invoke(viewer) == oldBinding)
		}
		fun onPublished(event: ReaderPresentationEvent) {
			if (mode == Task336ReadyReplacement.Reentrant && readyEntered && !replaced &&
				event is ReaderPresentationEvent.PreparationReported && event.facts.phase == ReaderPagePreparationPhase.Ready) {
				checked {
					assertTrue(deckDepth > 0, "Replacement must occur inside the actual restored-deck observer report")
					assertNotNull(field(viewer, "preparedActiveDeck"))
					replace()
				}
			}
		}
		init {
			val deckObserver: (ReaderPagePreparedActiveDeck?) -> Unit = { deck ->
				if (deck != null) { publications++; if (replaced) stalePublications++ }
				deckDepth++
				try { originalDeck(deck) } finally { deckDepth-- }
			}
			val preparationObserver: (ReaderPagePreparationState) -> Unit = { state ->
				if (readyEntered || state.phase != ReaderPagePreparationPhase.Ready) originalPreparation(state)
				else {
					readyEntered = true
					checked {
						assertNull(field(viewer, "preparedActiveDeck"), "The real Ready boundary must have the missing host observation")
						val generation = assertNotNull(field(curl, "activeDeckGenerationId"))
						val owner = assertNotNull((field(curl, "generationOwners") as Map<*, *>)[generation])
						assertFalse(owner === field(curl, "activePages"))
						assertNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(common().state.presentation.authority).nativePresentationRequest)
						if (mode != Task336ReadyReplacement.Reentrant) replace()
					}
					try { readyForwarded++; originalPreparation(state) } finally {
						checked {
							assertNoNewDemand()
							assertEquals(0, stalePublications)
							if (replaced) {
								assertEquals(replacementEpoch, reporter.captureEpoch())
								assertNull(field(viewer, "preparedActiveDeck"))
								if (mode != Task336ReadyReplacement.Epoch) {
									assertNull(field(preparation, "activeDeckPreparationGeneration"),
										"The superseded observer must not run its old raster-preparation tail")
									assertNull(type.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer))
								}
							}
						}
						// Negative cases finish with a real terminal lifecycle, after assertions, never a positive-entry rescue.
						type.task7Method("closeReader").invoke(viewer)
					}
				}
			}
			deckField.set(curl, deckObserver)
			preparationField.set(preparation, preparationObserver)
		}
		fun verify() {
			failures.firstOrNull()?.let { first -> failures.drop(1).filter { it !== first }.forEach(first::addSuppressed); throw first }
			assertTrue(readyEntered, "Expected the genuine automatic preparation Ready delivery")
			assertEquals(1, readyForwarded)
			assertTrue(replaced, "Expected the genuine restored-deck Ready report before reentrant replacement")
			assertEquals(if (mode == Task336ReadyReplacement.Reentrant) 1 else 0, publications)
			assertEquals(0, stalePublications)
			assertNoNewDemand()
			println("TASK336_READY_REPLACEMENT mode=$mode readyForwarded=$readyForwarded replacementObserved=$replaced " +
				"publications=$publications stalePublications=$stalePublications callbackFailures=${failures.size}")
		}
		fun restore() {
			preparationField.set(preparation, originalPreparation)
			deckField.set(curl, originalDeck)
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private fun task336ActualViewerSequence(
		observeReconciliation: Boolean = false,
		c6Boundary: Task336C6Boundary? = null,
		c6NativeBoundary: Task336C6NativeBoundary = Task336C6NativeBoundary.Rejected,
		readyReplacement: Task336ReadyReplacement? = null,
		selectedPredecessorRollback: Boolean = false,
		replaceHostEpochBeforeSuccessorRelease: Boolean = false,
		restoreBoundary: Task373VisibilityRestoreBoundary? = null,
		focusedRelocation: ((Class<*>, View, (Int, String) -> Unit) -> Unit)? = null
	) = runTest {
		require(!replaceHostEpochBeforeSuccessorRelease || selectedPredecessorRollback)
		// Only the mandatory distinct-decoded sequence needs an expanding 0 -> 1 window.
		// Preserve the original three-page inputs for independent C6/rollback/focused cases.
		val syntheticPageCount = if (c6Boundary != null || selectedPredecessorRollback || focusedRelocation != null) 3 else 6
		// Only lifecycle, source, capture, draw and renderer callbacks are test inputs.
		// No Ready facts, presentation proofs, callback fences or reporter state are assigned.
		val progress = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
		val mainThread = Thread.currentThread()
		val queuedMain = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
		Dispatchers.setMain(object : kotlinx.coroutines.MainCoroutineDispatcher() {
			override val immediate get() = this
			override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext) = Thread.currentThread() !== mainThread
			override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
				queuedMain.dispatch(context, block)
				progress.trySend(Unit)
			}
		})
		val activity = Robolectric.buildActivity(Activity::class.java).setup()
		val (viewerClass, viewer) = task7Viewer(activity.get())
		val webView = Task9RecoveryCommandWebView(activity.get(), syntheticPageCount = syntheticPageCount)
		val curl = viewerClass.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
		val preparation = viewerClass.task7Field("pageRasterPreparationController").get(viewer) as ReaderPageRasterPreparationController
		val reporter = viewerClass.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
		val bundle = curl.javaClass.task7Field("bundleSource").get(curl) as ReaderPageTurnBundleSource
		val surface = curl.surfaceView
		val rollbackRendererReleaseCounts = mutableMapOf<Long, Int>()
		var rollbackPredecessorGeneration: Long? = null
		var rollbackSuccessorGeneration: Long? = null
		var rollbackRestoredGeneration: Long? = null
		var rollbackReplacementHostEpoch: Long? = null
		var rollbackNativeProofCountBeforeEpoch: Int? = null
		var rollbackSelectedBindingBeforeEpoch: ReaderPresentationBinding? = null
		var rollbackCapacityRetry: (() -> Unit)? = null
		if (selectedPredecessorRollback) {
			assertNull(surface.renderCapabilities)
			val originalListener = surface.pageSurfaceListener
			rollbackCapacityRetry = originalListener::onDeckSubmissionCapacityAvailable
			surface.pageSurfaceListener = task8ForwardingPageSurfaceListener(
				delegate = originalListener,
				onDeckSubmissionCapacityAvailable = {
					if (rollbackSuccessorGeneration == null) originalListener.onDeckSubmissionCapacityAvailable()
				},
				onDeckReleased = { generationId, reason ->
					rollbackRendererReleaseCounts[generationId] = rollbackRendererReleaseCounts.getOrDefault(generationId, 0) + 1
					originalListener.onDeckReleased(generationId, reason)
					if (generationId == rollbackSuccessorGeneration) {
						rollbackRestoredGeneration = curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?
					}
				}
			)
		}
		val runtime = Task9ControlledPassiveRuntime()
		val deadlines = HostBridgeDeadlineScheduler()
		val effects = ReaderPresentationEffectQueue()
		val events = mutableListOf<ReaderPresentationEvent>()
		val commands = mutableListOf<paige.navic.reader.ReaderEngineCommand>()
		val decisions = mutableListOf<ReaderPresentationDecision>()
		val session = "task336-session"
		val lifecycleOwner = object : LifecycleOwner {
			val registry = LifecycleRegistry(this)
			override val lifecycle: Lifecycle get() = registry
		}
		fun pauseHostLifecycle() {
			if (lifecycleOwner.registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
				lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
			}
			if (lifecycleOwner.registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
				lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
			}
		}
		fun assertHostResumed() {
			assertSame(lifecycleOwner, Task336ViewTreeLifecycleOwnerShadow.get(viewer))
			assertSame(lifecycleOwner.registry, viewerClass.task7Field("observedHostLifecycle").get(viewer))
			assertEquals(Lifecycle.State.RESUMED, lifecycleOwner.registry.currentState)
			assertTrue(lifecycleOwner.registry.observerCount > 0)
			assertTrue(curl.javaClass.task7Field("hostResumed").getBoolean(curl),
				"The actual registered host observer must deliver resumed ingress")
		}
		val publication = ReaderPublicationIdentity("task336-publication", resourceHref = "")
		val gate10Observations = mutableListOf<String>()
		data class InitialLiveCommand(val command: org.json.JSONObject,
			val callback: android.webkit.ValueCallback<String>?, val request: Any?)
		val initialLiveCommands = java.util.ArrayDeque<InitialLiveCommand>()
		val initialLiveReceipts = java.util.ArrayDeque<Pair<Any, android.webkit.ValueCallback<String>>>()
		val sourceLiveReceipts = java.util.IdentityHashMap<Any, String>()
		var initialLiveCommandCount = 0
		var initialLiveAcknowledgementCount = 0
		var initialLiveReceiptCount = 0
		var initialLiveCompletionCount = 0
		var initialLiveVerifiedRetirementCount = 0
		var initialLivePreSourceRetirementCount = 0
		var sourcePresentationSequence = 0L
		var c6BeforeNativeReceipt: (() -> Unit)? = null
		val c6NativeReceipts = mutableListOf<Pair<ReaderPresentationEvent.NativePagePresented, ReaderPresentationEventReceipt?>>()
		val c6NativeProducerReceipts = mutableListOf<ReaderPresentationEventReceipt?>()
		val c6AppliedHostEffects = mutableListOf<ReaderPresentationHostEffect>()
		val c6CallbackFailures = mutableListOf<Throwable>()
		var c6OriginalNativeCalls = 0
		var restoreC6NativeCallback: (() -> Unit)? = null
		fun captureC6CallbackFailure(action: () -> Unit) {
			try { action() } catch (failure: Throwable) { c6CallbackFailures += failure }
		}
		fun rethrowC6CallbackFailure() {
			c6CallbackFailures.firstOrNull()?.let { first ->
				c6CallbackFailures.drop(1).filter { it !== first }.forEach(first::addSuppressed)
				c6CallbackFailures.clear()
				throw first
			}
		}
		var c6HoldNativeFrames = c6Boundary == Task336C6Boundary.Canonical
		var c6CanonicalFailures = 0
		var c6CanonicalContinuations = 0
		var c6DecklessCommandsBeforeNative = 0
		var c6CancelledRequest: Any? = null
		var c6CancelledCommands = 0
		fun initialLiveRequest() = curl.javaClass.task7Field("initialLivePresentationAuthority").get(curl)
		fun initialLivePending() = initialLiveCommands.isNotEmpty() || initialLiveReceipts.isNotEmpty() ||
			sourceLiveReceipts.isNotEmpty() || initialLiveRequest() != null
		fun initialLiveAccounting() = "commands=$initialLiveCommandCount acknowledgements=$initialLiveAcknowledgementCount " +
			"receipts=$initialLiveReceiptCount completions=$initialLiveCompletionCount " +
			"verifiedRetirements=$initialLiveVerifiedRetirementCount " +
			"preSourceRetirements=$initialLivePreSourceRetirementCount " +
			"pendingCommands=${initialLiveCommands.size} pendingReceipts=${initialLiveReceipts.size} " +
			"pendingSourceReceipts=${sourceLiveReceipts.size} claimPending=${initialLiveRequest() != null}"
		webView.onInitialLiveCommand = { command, callback ->
			initialLiveCommandCount++
			if (c6Boundary == Task336C6Boundary.Canonical) {
				val request = assertNotNull(initialLiveRequest())
				if (request.javaClass.task7Field("requiredDeckGenerationId").get(request) == null &&
					events.none { it is ReaderPresentationEvent.NativePagePresented }) c6DecklessCommandsBeforeNative++
			}
			initialLiveCommands.addLast(InitialLiveCommand(command, callback, initialLiveRequest()))
			progress.trySend(Unit)
		}
		webView.onInitialLiveReceiptRequested = { callback ->
			val request = initialLiveRequest()
			if (request != null && request.javaClass.task7Field("confirmationPending").getBoolean(request)) {
				initialLiveReceipts.addLast(request to callback)
				progress.trySend(Unit)
				true
			} else false
		}
		var pendingCallbackAccepted: Boolean? = null
		var latestTurnAdmissionGeneration: Long? = null
		var latestTurnAdmission: ReaderDeckAdmission? = null
		var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L,
			publication = publication, nativeShellCoverUrl = "task336-cover", canReturnToShellCover = true,
			shellCoverVisible = false))
		var bridge: ReaderPresentationHostBridge? = null
		var bodyFailure: Throwable? = null
		val currentLiveObservationFailed = java.util.concurrent.atomic.AtomicBoolean()
		var controlledPreparationPort: ReaderPassiveRasterPreparationPort? = null
		var controlledManifestRequestCount = 0
		var restorePreparationObservation: (() -> Unit)? = null
		var restoreReadyOwnerObservation: (() -> Unit)? = null
		var readyReplacementProbe: Task336ReadyReplacementProbe? = null
		fun installPreparationObservation() {
			val field = preparation.javaClass.task7Field("onPreparationStateChange")
			val capabilities = curl.javaClass.task7Field("capabilitiesAvailable")
			val selectedPort = viewerClass.task7Field("passiveRasterPreparationAdapter")
			@Suppress("UNCHECKED_CAST")
			val original = field.get(preparation) as (ReaderPagePreparationState) -> Unit
			val observed = mutableSetOf<ReaderPagePreparationPhase>()
			var observationFailed = false
			fun failureKind(state: ReaderPagePreparationState): String {
				if (state.phase == ReaderPagePreparationPhase.Preparing) return "None"
				val error = state.error ?: return "Unknown"
				// Exact source messages and batch reason literals only; never emit raw errors.
				when (error) {
					"Page preparation could not read the current layout." -> return "ProfileBootstrap"
					"Page preparation could not read pagination." -> return "PaginationBootstrap"
					"Page preparation could not establish live authority." -> return "CanonicalLiveAuthority"
					"Page preparation did not become ready." -> return "DeferredRetryExhausted"
					"The passive page preparation session is unavailable." -> return "PassiveSessionUnavailable"
					"Page preparation was released under memory pressure." -> return "MemoryPressure"
				}
				if (Regex("Page [0-9]+ could not be stored\\.").matches(error)) return "CapacityReached"
				val reason = Regex("(?:Page [0-9]+ could not be prepared: |Page preparation failed: )([a-z-]+)")
					.matchEntire(error)?.groupValues?.get(1)
				return when (reason) {
					"incomplete-current-plus-minus-five" -> "IncompleteBlockingWindow"
					"no-calibration-targets" -> "NoCalibrationTargets"
					"bridge-request-unavailable" -> "BridgeRequestUnavailable"
					"manifest-invalid" -> "ManifestInvalid"
					"manifest-request-invalid" -> "ManifestRequestInvalid"
					"current-authority-invalid" -> "CurrentAuthorityInvalid"
					"committed-capture-unavailable" -> "CommittedCaptureUnavailable"
					"raster-rejected" -> "RasterRejected"
					"raster-rejected-or-publication-failed" -> "RasterRejectedOrPublicationFailed"
					"capacity-reached" -> "CapacityReached"
					"disk-capacity-reached" -> "DiskCapacityReached"
					"durable-write-failed" -> "DurableWriteFailed"
					else -> "Unknown"
				}
			}
			restorePreparationObservation = {
				field.set(preparation, original)
				assertSame(original, field.get(preparation))
				assertFalse(observationFailed, "Preparation observation must not fail while reading scalar evidence")
			}
			field.set(preparation, { state: ReaderPagePreparationState ->
				// Observe before the original can cause reentrant publication; do not catch it.
				try {
					if ((state.phase == ReaderPagePreparationPhase.Preparing || state.phase == ReaderPagePreparationPhase.Failed) &&
						observed.add(state.phase)) {
						val selected = selectedPort.get(viewer) as ReaderPassiveRasterPreparationPort?
						val fixture = controlledPreparationPort
						println("TASK336_PREPARATION first=${state.phase} reason=${failureKind(state)} " +
							"boundary=${c6Boundary?.name ?: "Default"} variant=${c6NativeBoundary.name} " +
							"capabilities=${capabilities.getBoolean(curl)} selectedIsFixture=${fixture != null && selected === fixture} " +
							"selectedAvailable=${selected?.isAvailable == true} selectedRetired=${selected?.isRetired == true} " +
							"fixtureAvailable=${fixture?.isAvailable == true} fixtureRetired=${fixture?.isRetired == true} " +
							"controlledManifestRequests=$controlledManifestRequestCount")
					}
				} catch (_: Throwable) {
					// An observer defect is checked during cleanup, never substituted for an
					// original callback invocation, return, or production exception.
					observationFailed = true
				}
				original(state)
			})
			webView.onPassiveManifestRequested = { controlledManifestRequestCount++; null }
		}
		try {
			installPreparationObservation()
			Task336ViewTreeLifecycleOwnerShadow.install(viewer, lifecycleOwner)
			lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
			activity.get().setContentView(viewer)
			viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(viewer, webView)
			assertSame(lifecycleOwner.registry, viewerClass.task7Field("observedHostLifecycle").get(viewer))
			lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
			lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
			assertHostResumed()
			viewer.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
			viewer.layout(0, 0, 800, 1200)
			viewerClass.task7Method("setPageTurnCanvasEnabled", java.lang.Boolean.TYPE, Function0::class.java)
				.invoke(viewer, true, { })
			task8PrepareSurface(surface)
			surface.surfaceCreated(surface.holder)
			ShadowLooper.runUiThreadTasks()
			assertTrue(viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer))
			webView.measure(View.MeasureSpec.makeMeasureSpec(viewer.width, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(viewer.height, View.MeasureSpec.EXACTLY))
			webView.layout(0, 0, viewer.width, viewer.height)
			val origin = IntArray(2).also(viewer::getLocationInWindow)
			viewer.translationX = -origin[0].toFloat()
			viewer.translationY = -origin[1].toFloat()
			val geometry = ReaderPassiveRasterGeometry(webView.width, webView.height, 0, 0, webView.width, webView.height)
			val adapter = ReaderPassiveRasterPreparationAdapter(ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
				if (!bitmap.isRecycled) bitmap.recycle()
			}, ReaderPageLivePassiveRasterManifestPort { webView }, bundle,
				viewerClass.task7Field("passiveRasterCaptureEpoch").getLong(viewer),
				currentLivePublicationPort = task336CurrentLivePublicationObserver(
					bundle, webView, c6Boundary, c6NativeBoundary, currentLiveObservationFailed))
			controlledPreparationPort = adapter
			viewerClass.task7Field("passiveRasterPreparationAdapter").set(viewer, adapter)
			viewerClass.task7Field("passiveRasterPreparationGeometry").set(viewer, geometry)
			webView.retainPlanCallbacks = true
			data class C4PlanObservation(val session: Any?, val preparation: Any?, val raster: Any?, val preparationQuery: Boolean)
			fun preparationField(name: String) = preparation.javaClass.task7Field(name).get(preparation)
			val c4PlanObservations = java.util.ArrayDeque<C4PlanObservation>()
			webView.onPlanRequested = {
				c4PlanObservations.addLast(C4PlanObservation(preparationField("prewarmSession"),
					preparationField("preparationGeneration"), bundle.currentGeneration(),
					Throwable().stackTrace.any { frame -> frame.className == preparation.javaClass.name &&
						frame.methodName == "queryRasterPreparationPlan" }))
				progress.trySend(Unit)
			}
			var c4Observing = false
			if (c6Boundary == null && focusedRelocation == null) {
				restoreReadyOwnerObservation = task336ObserveReadyOwnerCallbacks(
					viewerClass, viewer, curl, preparation, reporter, events,
					commonState = { common.state.presentation }, observing = { c4Observing }
				)
			}
			var c4HeldSession: Any? = null
			var c4HeldPreparation: Any? = null
			var c4HeldRaster: Any? = null
			var c4HeldCallbacks = emptyList<android.webkit.ValueCallback<String>>()
			fun physicalBinding() = viewerClass.task7Method("currentPresentationBinding").invoke(viewer) as ReaderPresentationBinding?
			fun bindingEqualities(): String =
				"targetPhysical=${common.state.presentationDecision.targetBinding == physicalBinding()} " +
					"reporterPhysical=${reporter.lastReportedBinding == physicalBinding()} " +
					"targetReporter=${common.state.presentationDecision.targetBinding == reporter.lastReportedBinding} " +
					"preparedActive=${viewerClass.task7Field("preparedActiveDeck").get(viewer) != null}"
			fun observeC4(boundary: String, response: android.webkit.ValueCallback<String>? = null,
				request: C4PlanObservation? = null) {
				if (!c4Observing) return
				val generation = preparationField("preparationGeneration")
				val phase = (viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState).phase
				gate10Observations += "c4 boundary=$boundary prewarm=${preparationField("prewarmInProgress")} " +
					"heldSessionSame=${preparationField("prewarmSession") == c4HeldSession} " +
					"heldPreparationSame=${generation == c4HeldPreparation} heldRasterSame=${bundle.currentGeneration() == c4HeldRaster} " +
					"plans=${webView.planCallbacks.size} heldPending=${webView.planCallbacks.count { callback -> c4HeldCallbacks.any { it === callback } }} " +
					"responseHeld=${response != null && c4HeldCallbacks.any { it === response }} requestObserved=${request != null} " +
					"preparationQuery=${request?.preparationQuery} requestSessionSame=${request?.session == preparationField("prewarmSession")} " +
					"requestPreparationSame=${request?.preparation == generation} requestRasterSame=${request?.raster == bundle.currentGeneration()} " +
					"phase=$phase commonPhase=${common.state.presentation.preparationFacts.phase} " +
					"readyReports=${events.filterIsInstance<ReaderPresentationEvent.PreparationReported>().count { it.facts.phase == ReaderPagePreparationPhase.Ready }} " +
					"rasterProofCurrent=${preparationField("rasterProofPreparationGeneration") == generation} " +
					"readyGenerationCurrent=${preparationField("readyPreparationGeneration") == generation} " +
					"failedGenerationCurrent=${preparationField("failedPreparationGeneration") == generation} " +
					"deferredPrewarm=${preparationField("deferredPrewarmSessionId") != null} " +
					"attached=${webView.isAttachedToWindow} destroyed=${preparationField("destroyed")} " +
					"profilePresent=${viewerClass.task7Field("rasterProfileEpoch").get(viewer) != null} " +
					"shellVisible=${viewerClass.task7Field("shellCoverVisible").getBoolean(viewer)} " +
					"visualSame=${preparationField("currentVisualPageIndex") == common.state.chrome.currentLocator?.pageIndex} " +
					bindingEqualities()
			}
			val controllerTeardown = curl.javaClass.task7Field("teardownJob").get(curl) as Job
			val mainTerminalExecutor = curl.javaClass.task7Field("mainTerminalExecutor").get(curl) as ReaderMainTerminalActionExecutor
			fun finiteControllerConsumers() = controllerTeardown.children.filter {
				it !== mainTerminalExecutor.drainJob && !it.isCompleted
			}.toList()
			fun ownedJobCounts(): String {
				val scheduler = bundle.javaClass.task7Field("rasterScheduler").get(bundle)
				val worker = scheduler?.javaClass?.task7Field("workerJob")?.get(scheduler) as Job?
				fun children(owner: Any, field: String): Int =
					(owner.javaClass.task7Field(field).get(owner) as Job).children.count { !it.isCompleted && it !== worker }
				val scheduled = if (scheduler == null) 0 else {
					val lock = assertNotNull(scheduler.javaClass.task7Field("lock").get(scheduler))
					synchronized(lock) {
						(scheduler.javaClass.task7Field("pending").get(scheduler) as Map<*, *>).values.count { request ->
							val owned = assertNotNull(request)
							!(owned.javaClass.task7Field("result").get(owned) as Job).isCompleted
						}
					}
				}
				return "curlRaster=${children(curl, "rasterJob")} curlTeardown=${children(curl, "teardownJob")} " +
					"bundleRaster=${children(bundle, "rasterJob")} preparationTeardown=${children(preparation, "teardownJob")} " +
					"schedulerWorkerChildren=${worker?.children?.count { !it.isCompleted } ?: 0} schedulerOwnedResults=$scheduled " +
					"controllerFiniteConsumers=${finiteControllerConsumers().size} mainTerminalActions=${mainTerminalExecutor.pendingActionCount()}"
			}
			webView.sourceBinding = { assertNotNull(physicalBinding()) }
			fun accept(step: ReaderControllerStep) {
				common = step.controller
				commands += step.engineCommands
				effects.retain(step.presentationEffects)
				common.state.presentation.assertSequenceInvariants()
				decisions += common.state.presentationDecision
			}
			var admissionPredecessor: ReaderPresentationDecision? = null
			var visibilityLossDeliveryEnabled =
				restoreBoundary != Task373VisibilityRestoreBoundary.DelayedRestoreCaller
			var blockedVisibilityLossDeliveries = 0
			val visibilityRestoreDeliveryStacks = mutableListOf<List<String>>()
			val publish: (ReaderPresentationEvent) -> ReaderPresentationEventReceipt? = { event ->
				if (
					event == ReaderPresentationEvent.Lifecycle(
						ReaderPresentationLifecycleEvent.VisibilityLost
					) && !visibilityLossDeliveryEnabled
				) {
					blockedVisibilityLossDeliveries++
					progress.trySend(Unit)
					null
				} else try {
				events += event
				if (
					event == ReaderPresentationEvent.Lifecycle(
						ReaderPresentationLifecycleEvent.VisibilityRestored
					)
				) {
					visibilityRestoreDeliveryStacks += Throwable().stackTrace.map { it.methodName }
				}
				if (event is ReaderPresentationEvent.CurlClaimed) {
					val predecessor = assertNotNull(admissionPredecessor)
					val observed = curl.javaClass.task7Field("commonPresentationDecision").get(curl) as ReaderPresentationDecision
					val readiness = common.state.presentation.preparationFacts.readiness
					val onlyInputPolicyChanged = observed.copy(inputPolicy = predecessor.inputPolicy) == predecessor
					gate10Observations += "admission-projection onlyInputPolicyChanged=$onlyInputPolicyChanged " +
						"inputPolicyChanged=${observed.inputPolicy != predecessor.inputPolicy} " +
						"interaction=${readiness.interaction} textureDeck=${readiness.textureDeck}"
					assertTrue(onlyInputPolicyChanged, "Renderer admission must preserve every predecessor field except input policy")
					assertEquals(paige.navic.reader.ReaderPageInteractionState.Settling, readiness.interaction)
					assertEquals(paige.navic.reader.ReaderTextureDeckState.Settling, readiness.textureDeck)
					assertEquals(ReaderPresentationInputPolicy.NativePage(paige.navic.reader.readerPageOperationPolicy(readiness)),
						observed.inputPolicy, "Native input policy must project the actual reported Settling readiness")
				}
				val expected = (common.state.presentation.authority as? ReaderPresentationAuthority.CurlSettlementPending)
					?.expectedAcknowledgement
				val step = common.onPresentationEvent(event)
				accept(step)
				readyReplacementProbe?.onPublished(event)
				if (c6Boundary != null && event is ReaderPresentationEvent.NativePagePresented) {
					// Producer output is not evidence of final host receipt admission.
					c6NativeProducerReceipts += step.presentationReceipt
				}
				if (c4Observing && event is ReaderPresentationEvent.PreparationReported) {
					val methods = Throwable().stackTrace.map { it.methodName }
					val producer = when {
						"cancelPrewarm" in methods && "invalidateCurrentVisualSnapshot" in methods && "setShellCoverVisible" in methods -> "CoverHiddenCancellation"
						"cancelPrewarm" in methods -> "OtherCancellation"
						"finishPrewarm" in methods -> "BatchFinished"
						else -> "Other"
					}
					gate10Observations += "c4 preparationEvent phase=${event.facts.phase} producer=$producer disposition=${step.presentationReceipt?.disposition}"
				}
				if (event is ReaderPresentationEvent.CurlTerminal || event is ReaderPresentationEvent.FoliateRelocated) {
					gate10Observations += "event=${event::class.simpleName} disposition=${step.presentationReceipt?.disposition} " +
						"receiptEventExact=${step.presentationReceipt?.event == event} " +
						"ackExpected=${event is ReaderPresentationEvent.FoliateRelocated && event.acknowledgement == expected} " +
						"authority=${common.state.presentation.authority::class.simpleName} " +
						"stage=${(common.state.presentation.authority as? ReaderPresentationAuthority.CurlSettlementPending)?.stage} " +
						bindingEqualities()
				}
				progress.trySend(Unit)
				step.presentationReceipt
				} catch (failure: Throwable) {
					// Production catches callback failures; retain C6 assertions for the outer input owner.
					if (c6Boundary != null) c6CallbackFailures += failure
					throw failure
				}
			}
			val apply = viewerClass.task7Method("applyPresentationDecision", ReaderPresentationDecision::class.java,
				ReaderRendererLossCancellationIdentity::class.java)
			val applyFrame = viewerClass.task7Method("applyPresentationFrameOwner", ReaderPresentationDecision::class.java)
			val cover = View(activity.get())
			viewerClass.task7Method("setShellCoverView", View::class.java).invoke(viewer, cover)
			var preparedCoverGeneration: Long? = null
			val draws = mutableListOf<FakeDrawRegistration>()
			val animations = mutableListOf<() -> Unit>()
			val commitHost = object : ReaderPresentationCommitHost {
				override val isAttachedToWindow get() = viewer.isAttachedToWindow
				override val currentPresentationBinding get() = physicalBinding()
				override val currentShellCoverGeneration get() = preparedCoverGeneration
				override val shellCoverSelected get() = viewerClass.task7Field("shellCoverVisible").getBoolean(viewer)
				override val measuredViewportWidth get() = viewer.width
				override val measuredViewportHeight get() = viewer.height
				override fun prepareOpaqueShellCover(coverGeneration: Long) {
					preparedCoverGeneration = coverGeneration
					viewerClass.task7Method("prepareShellCoverForCommit", View::class.java).invoke(viewer, cover)
				}
				override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) {
					viewerClass.task7Method("cancelShellCoverCommitPreparation", View::class.java).invoke(viewer, cover)
				}
				override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) { }
				override fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration =
					FakeDrawRegistration(onDraw).also(draws::add)
				override fun postShellCoverAnimationFrame(onFrame: () -> Unit) { animations += onFrame }
				override fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) { applyFrame.invoke(viewer, decision) }
			}
			bridge = ReaderPresentationHostBridge(commitHost, transitionTimeoutScheduler = deadlines,
				transitionNowMillis = { 0L }) { event ->
				viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
					.invoke(viewer, event) as ReaderPresentationEventReceipt?
			}
			val onEffect: (ReaderPresentationHostEffect) -> Unit = { effect ->
				apply.invoke(viewer, effect.decision, effect.rendererLossCancellationIdentity)
				bridge.update(effect.decision)
				if (c6Boundary != null) c6AppliedHostEffects += effect
			}
			val setDecision = viewerClass.task7Method("setPresentationDecision", ReaderPresentationDecision::class.java,
				ReaderPresentationState::class.java, paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
				java.lang.Boolean.TYPE, ReaderDestinationCommitIdentity::class.java, Function1::class.java, Function1::class.java)
			fun synchronize() {
				setDecision.invoke(viewer, common.state.presentationDecision, common.state.presentation,
					common.presentationVersion, false, common.state.destinationCommitIdentity, publish, onEffect)
				bridge.update(common.state.presentationDecision)
			}
			val handler = ReaderPresentationEffectHandler(releaseStalePresentation = { effect ->
				viewerClass.task7Method("releaseStalePresentation", ReaderPresentationEffect.ReleaseStalePresentation::class.java)
					.invoke(viewer, effect) as Boolean
			})
			val visual = viewerClass.task7Method("setPageTurnVisualLocation", Integer::class.java,
				String::class.java, String::class.java, ReaderPageTurnSettlementAck::class.java)
			var destinationSequence = 0L
			fun observeC5(boundary: String, ordinal: Int, reason: String, sourceSession: String,
				acknowledgement: ReaderPageTurnSettlementAck?, deck: ReaderPagePreparedActiveDeck?,
				decision: ReaderPresentationDecision?, binding: ReaderPresentationBinding?, epoch: Long) {
				fun field(name: String) = curl.javaClass.task7Field(name).get(curl)
				val generation = field("activeDeckGenerationId") as Long?
				val owner = (field("generationOwners") as Map<*, *>)[generation]
				val profile = owner?.let { it.javaClass.task7Field("profile").get(it) } as ReaderPlayLikeCurlRasterProfile?
				val phase = field("preparationPhase") as ReaderPagePreparationPhase
				val readiness = field("readinessState")
				val texture = readiness.javaClass.task7Field("textureDeck").get(readiness) as paige.navic.reader.ReaderTextureDeckState
				val recovery = (field("deckRecoveryCoordinator") as ReaderPageDeckRecoveryCoordinator).state
				val role = (field("generationRoles") as Map<*, *>)[generation] as ReaderDeckSubmissionRole?
				val preparationGeneration = field("preparationGeneration")
				val checks = listOf(
					"bindingPresent" to (binding != null),
					"enabled" to (field("enabled") == true), "attached" to (field("attached") == true),
					"notDestroyed" to (field("destroyed") == false), "pageIndexPresent" to (ordinal >= 0),
					"ordinalSame" to (field("currentOrdinal") == ordinal),
					"webViewOrdinalSame" to (field("currentWebViewOrdinal") == ordinal),
					"sessionSame" to (field("currentFoliateSessionId") == sourceSession),
					"bindingSessionSame" to (binding?.foliateSessionId == sourceSession),
					"controllerDecisionSame" to (field("commonPresentationDecision") == decision),
					"noDiagnosticFailure" to (decision?.diagnosticPresentation !is ReaderDiagnosticPresentation.Failure),
					"noSessionRelocation" to (field("foliateSessionRelocationPending") == false),
					"noRecoverySnapshot" to (field("presentationRecoverySnapshot") == null),
					"noInitialLiveAuthority" to (field("initialLivePresentationAuthority") == null),
					"noPassiveRecovery" to (field("passiveManifestAuthorityRecoveryToken") == null),
					"noLiveRecovery" to !(field("livePresentationRecoveryRequest") as ReaderPageLivePresentationRecoveryRequest).pending,
					"noFailedLive" to (field("failedLivePresentationGeneration") == null),
					"noRetry" to (field("retryPreparationInProgress") == false),
					"phaseNotFailed" to (phase != ReaderPagePreparationPhase.Failed),
					"preparationNotFailed" to (field("failedPreparationGeneration") != preparationGeneration),
					"recoveryIdle" to (recovery == ReaderPageDeckRecoveryState.Idle),
					"noActiveGesture" to (field("activeGestureId") == null),
					"noTapGesture" to (field("tapTurnGestureId") == null),
					"noTapSink" to (field("tapTurnTerminalSink") == null),
					"noPendingGeneration" to (field("pendingDeckGenerationId") == null),
					"noSettlement" to !surface.isSettlementRunning,
					"queueEmpty" to ((field("relocationQueue") as ReaderPageRelocationQueue).occupiedCount() == 0),
					"textureReady" to (texture == paige.navic.reader.ReaderTextureDeckState.Ready),
					"generationPresent" to (generation != null), "ownerPresent" to (owner != null),
					"deckGenerationSame" to (deck?.generationId == generation),
					"bindingTextureSame" to (binding?.textureGeneration == generation),
					"roleActive" to (role == ReaderDeckSubmissionRole.Active),
					"generationPrepared" to ((field("preparedDeckGenerations") as Set<*>).contains(generation)),
					"ownerIsActivePages" to (owner === field("activePages")),
					"ownerNotObsolete" to (owner?.let { it.javaClass.task7Field("obsolete").getBoolean(it) } == false),
					"requestedProfileSame" to (profile == field("requestedProfile")),
					"publishedProfileSame" to (profile == field("publishedRasterProfile")),
					"deckPublishedEpochSame" to (deck?.rasterProfileEpoch == field("publishedRasterProfileEpoch")),
					"bindingProfileSame" to (binding?.profileGeneration == deck?.rasterProfileEpoch),
					"sourceCenterSame" to (deck?.sourceCenterPageIndex == profile?.let {
						val request = curl.javaClass.task7Method("pageRequest", ReaderPlayLikeCurlRasterProfile::class.java, java.lang.Integer.TYPE)
							.invoke(curl, it, ordinal)
						request.javaClass.task7Field("sourcePageIndex").getInt(request)
					}),
					"ownerRasterSame" to (deck?.rasterEpoch == profile?.rasterGeneration),
					"currentRasterSame" to (deck?.rasterEpoch == bundle.currentGeneration()),
					"bindingRasterSame" to (binding?.rasterGeneration == deck?.rasterEpoch),
					"generationPreparationSame" to (deck?.preparationGeneration == (field("generationPreparationGenerations") as Map<*, *>)[generation]),
					"activePreparationSame" to (deck?.preparationGeneration == field("activeDeckPreparationGeneration")),
					"currentPreparationSame" to (deck?.preparationGeneration == preparationGeneration),
					"bindingPreparationSame" to (binding?.preparationGeneration == preparationGeneration)
				)
				val canReconcile = deck != null && decision != null &&
					curl.canReconcilePreparedActiveDeck(deck, ordinal, sourceSession, decision)
				val cached = viewerClass.task7Field("preparedActiveDeck").get(viewer)
				val actualSession = viewerClass.task7Field("pageTurnFoliateSessionId").get(viewer)
				val origin = curl.visualLocationOrigin(ordinal, acknowledgement)
				val candidateBinding = viewerClass.task7Method("currentPresentationBindingOrNull", ReaderPagePreparedActiveDeck::class.java)
					.invoke(viewer, deck) as ReaderPresentationBinding?
				val preChecks = listOf(
					"cachedDeckPresent" to (cached != null), "sameSession" to (actualSession == null || actualSession == sourceSession),
					"ordinaryIngress" to !curl.isPresentationRecoverySnapshotReason(reason), "ackAbsent" to (acknowledgement == null),
					"externalOrigin" to (origin == ReaderPageVisualLocationOrigin.External),
					"hostCenterSame" to (viewerClass.task7Field("pageTurnVisualPageIndex").get(viewer) == ordinal),
					"decisionPresent" to (decision != null), "bindingPresent" to (binding != null),
					"reporterBindingSame" to (reporter.lastReportedBinding == binding),
					"physicalBindingSame" to (physicalBinding() == binding), "controllerEligible" to canReconcile
				)
				val postChecks = listOf(
					"candidatePresent" to (deck != null), "decisionPresent" to (decision != null), "cacheCleared" to (cached == null),
					"decisionSame" to (viewerClass.task7Field("presentationDecision").get(viewer) == decision),
					"epochSame" to (reporter.captureEpoch() == epoch), "reporterBindingSame" to (reporter.lastReportedBinding == binding),
					"candidateBindingSame" to (candidateBinding == binding)
				)
				fun labels(values: List<Pair<String, Boolean>>) = values.joinToString(" ") { (name, value) -> "$name=$value" }
				val path = if (focusedRelocation == null) "Original" else "Focused"
				gate10Observations += "c5 path=$path boundary=$boundary hostPre=${preChecks.all { it.second }} " +
					"hostPost=${postChecks.all { it.second }} canReconcile=$canReconcile firstControllerReject=${checks.firstOrNull { !it.second }?.first ?: "None"} " +
					"clausesAgree=${checks.all { it.second } == canReconcile} phase=$phase texture=$texture recovery=${recovery::class.simpleName} role=${role?.name ?: "Absent"}"
				gate10Observations += "c5 path=$path boundary=$boundary hostPreClauses ${labels(preChecks)}"
				gate10Observations += "c5 path=$path boundary=$boundary hostPostClauses ${labels(postChecks)}"
				gate10Observations += "c5 path=$path boundary=$boundary controllerClauses ${labels(checks)}"
			}
			fun relocate(ordinal: Int, reason: String, acknowledgement: ReaderPageTurnSettlementAck? = null,
				sourceSession: String = session): paige.navic.reader.ReaderEngineEvent.Relocated {
				val destination = ReaderDestinationCommitIdentity(sourceSession, ++destinationSequence)
				val event = paige.navic.reader.ReaderEngineEvent.Relocated(
					paige.navic.reader.ReaderLocator(progress = 0.0, pageIndex = ordinal, pageCount = syntheticPageCount, reason = reason),
					sourceSession, pageTurnSettleToken = acknowledgement?.token,
					pageTurnSettleSessionId = acknowledgement?.foliateSessionId,
					pageTurnSettleRasterGeneration = acknowledgement?.rasterGeneration,
					pageTurnSettleTextureGeneration = acknowledgement?.textureGeneration,
					destinationCommitIdentity = destination)
				if (c4Observing) observeC4("relocation-before-engine")
				accept(common.onEngineEvent(event))
				if (c4Observing) observeC4("relocation-after-engine")
				val engineAcknowledgementExact = common.state.pageTurnSettlementAck == acknowledgement
				val eventBoundary = events.size
				synchronize()
				if (c4Observing) observeC4("relocation-after-synchronize")
				val c5Acknowledgement = common.state.pageTurnSettlementAck
				val c5Deck = if (observeReconciliation && c5Acknowledgement == null)
					viewerClass.task7Field("preparedActiveDeck").get(viewer) as ReaderPagePreparedActiveDeck? else null
				val c5Decision = if (c5Deck != null)
					viewerClass.task7Field("presentationDecision").get(viewer) as ReaderPresentationDecision? else null
				val c5Binding = c5Decision?.targetBinding
				val c5Epoch = reporter.captureEpoch()
				if (c5Deck != null) observeC5("before-visual", ordinal, reason, sourceSession, c5Acknowledgement,
					c5Deck, c5Decision, c5Binding, c5Epoch)
				visual.invoke(viewer, ordinal, reason, sourceSession, common.state.pageTurnSettlementAck)
				if (c5Deck != null) observeC5("after-visual", ordinal, reason, sourceSession, c5Acknowledgement,
					c5Deck, c5Decision, c5Binding, c5Epoch)
				if (c4Observing) observeC4("relocation-after-visual")
				gate10Observations += "relocation ackPresent=${acknowledgement != null} engineAckExact=$engineAcknowledgementExact " +
					"foliateEvents=${events.drop(eventBoundary).count { it is ReaderPresentationEvent.FoliateRelocated }} " +
					bindingEqualities() + " " + ownedJobCounts()
				assertEquals(destination, common.state.destinationCommitIdentity)
				assertEquals(ordinal, curl.javaClass.task7Field("currentOrdinal").getInt(curl))
				return event
			}
			fun completeInitialLiveSourceInputs(deliverReceipts: Boolean = true) {
				// Only emitted commands produce a settlement and receipt. Normal turns keep
				// their existing exact-acknowledgement owner and never enter this queue.
				initialLiveCommands.toList().forEach { input ->
					val request = assertNotNull(input.request, "An emitted initial-live command must have its production request")
					if (initialLiveRequest() !== request) {
						assertNull(request.javaClass.task7Field("claim").get(request))
						assertFalse(request.javaClass.task7Field("confirmationPending").getBoolean(request))
						assertNull(request.javaClass.task7Field("acceptedNativeProof").get(request))
						assertNull(request.javaClass.task7Field("ownSourceBinding").get(request))
						assertTrue(initialLiveReceipts.none { it.first === request })
						assertNull(sourceLiveReceipts[request])
						val replacement = initialLiveRequest()
						input.callback?.onReceiveValue("{}")
						assertSame(replacement, initialLiveRequest(), "A retired source callback cannot replace current authority")
						assertTrue(initialLiveCommands.removeFirst() === input)
						initialLiveVerifiedRetirementCount++
						initialLivePreSourceRetirementCount++
						gate10Observations += "initial-live retired-before-source " + initialLiveAccounting()
						return@forEach
					}
					val command = input.command
					val target = request.javaClass.task7Field("target").get(request) as ReaderPageTurnPresentationTarget.Live
					val acknowledgement = ReaderPageTurnSettlementAck(command.getString("settleToken"), command.getInt("pageIndex"),
						command.getString("settleSessionId"), command.getLong("settleRasterGeneration"), command.getLong("settleTextureGeneration"))
					val exactCommand = acknowledgement.token == target.token && acknowledgement.pageIndex.toLong() == target.pageIndex &&
						acknowledgement.foliateSessionId == target.foliateSessionId && acknowledgement.rasterGeneration == target.rasterGeneration &&
						acknowledgement.textureGeneration == target.textureGeneration &&
						command.getLong("settleForegroundMutationGeneration") == target.foregroundMutationGeneration &&
						command.getLong("settleGestureId") == request.javaClass.task7Field("generationId").getLong(request)
					assertTrue(exactCommand, "The source must use every actual emitted settlement field")
					val predecessor = assertNotNull(common.state.destinationCommitIdentity)
					assertTrue(predecessor.foliateSessionId == acknowledgement.foliateSessionId)
					val event = relocate(acknowledgement.pageIndex, "page-turn:exact", acknowledgement, acknowledgement.foliateSessionId)
					initialLiveAcknowledgementCount++
					val destination = assertNotNull(event.destinationCommitIdentity)
					val newerCommit = destination.foliateSessionId == predecessor.foliateSessionId &&
						destination.commitSequence > predecessor.commitSequence
					val exactAck = common.state.pageTurnSettlementAck == acknowledgement
					val getterRequested = initialLiveReceipts.any { it.first === request }
					gate10Observations += "initial-live source exactCommand=$exactCommand newerCommit=$newerCommit " +
						"exactAck=$exactAck getterRequested=$getterRequested requestCurrent=${initialLiveRequest() === request} " + initialLiveAccounting()
					assertTrue(newerCommit)
					assertTrue(exactAck, "The source settlement must traverse actual common ingress unchanged")
					assertTrue(getterRequested, "Exact source settlement must request the production strict receipt getter")
					// JS issues these exact eight keys after the exact source commit; the
					// evaluateJavascript(JSON.stringify(...)) result is a quoted JSON string.
					val receipt = org.json.JSONObject().apply {
						put("scope", "live"); put("token", command.getString("settleToken"))
						put("pageIndex", command.getInt("pageIndex")); put("foliateSessionId", command.getString("settleSessionId"))
						put("rasterGeneration", command.getLong("settleRasterGeneration"))
						put("textureGeneration", command.getLong("settleTextureGeneration"))
						put("foregroundMutationGeneration", command.getLong("settleForegroundMutationGeneration"))
						put("presentationSequence", ++sourcePresentationSequence)
					}
					assertTrue(sourceLiveReceipts.put(request, org.json.JSONObject.quote(receipt.toString())) == null)
					// Bridge dispatch returns a Promise object; this callback is not authority.
					input.callback?.onReceiveValue("{}")
					assertTrue(initialLiveCommands.removeFirst() === input)
				}
				if (!deliverReceipts) return
				initialLiveReceipts.toList().forEach { (request, callback) ->
					val encoded = assertNotNull(sourceLiveReceipts[request], "No receipt may be supplied without its source settlement")
					val target = request.javaClass.task7Field("target").get(request) as ReaderPageTurnPresentationTarget.Live
					val exactReceipt = readerPageTurnPresentationReceipt(encoded)?.matches(target) == true
					val requestCurrent = initialLiveRequest() === request && curl.javaClass.task7Method(
						"initialLivePresentationAuthorityIsCurrent", request.javaClass).invoke(curl, request) == true
					val ownership = curl.javaClass.task7Field("foregroundWebViewOwnership").get(curl) as ReaderForegroundWebViewOwnership
					val claim = request.javaClass.task7Field("claim").get(request) as ReaderForegroundWebViewLiveClaim
					val mutation = ReaderForegroundWebViewMutationGeneration(target.foregroundMutationGeneration)
					val claimCurrent = ownership.isCurrent(claim, mutation)
					gate10Observations += "initial-live receipt exactReceipt=$exactReceipt requestCurrent=$requestCurrent " +
						"claimCurrent=$claimCurrent attached=${webView.isAttachedToWindow} " + initialLiveAccounting()
					assertTrue(exactReceipt && requestCurrent && claimCurrent && webView.isAttachedToWindow,
						"Strict production confirmation must receive an exact current source receipt")
					callback.onReceiveValue(encoded)
					initialLiveReceiptCount++
					val completed = initialLiveRequest() !== request && !ownership.isCurrent(claim, mutation) &&
						!request.javaClass.task7Field("confirmationPending").getBoolean(request)
					assertTrue(completed, "Only the production receipt callback may complete the live claim")
					initialLiveCompletionCount++
					sourceLiveReceipts.remove(request)
					assertTrue(initialLiveReceipts.removeFirst().second === callback)
					gate10Observations += "initial-live completed=$completed " + initialLiveAccounting()
				}
			}
			fun seedCurrentCapture(ordinal: Int) {
				val quality = bundle.javaClass.task7Field("bitmapQuality").get(bundle) as ReaderPageBitmapQuality
				assertNotNull(bundle.cacheCurrentSnapshot(ordinal, ReaderPageTurnTransitionKind.PortraitSlide,
					ReaderPageTurnCaptureResult(Bitmap.createBitmap(readerPageTurnAnimationBitmapDimension(viewer.width, quality),
						readerPageTurnAnimationBitmapDimension(viewer.height, quality), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) },
						android.graphics.Rect(0, 0, viewer.width, viewer.height),
						paige.navic.reader.ReaderPageTurnCaptureGeometry(viewer.width.toDouble(), viewer.height.toDouble(),
							paige.navic.reader.ReaderPageTurnLayoutMode.Single, listOf(paige.navic.reader.ReaderPageTurnPageRect(
								paige.navic.reader.ReaderPageTurnPageRole.Full, 0.0, 0.0, viewer.width.toDouble(), viewer.height.toDouble()))), 1L)))
			}
			fun sourcePlan(): String {
				val ordinal = assertNotNull(common.state.chrome.currentLocator?.pageIndex)
				val targets = readerPageRasterBlockingWindow(ordinal, 1, syntheticPageCount).joinToString(",") { page ->
					val priority = when (page) { ordinal -> "current"; ordinal + 1 -> "next-transition"; else -> "previous-transition" }
					val authority = if (page == ordinal) "CurrentLive" else "OffscreenPassive"
					"""{"pageIndex":$page,"priority":"$priority","authority":"$authority"}"""
				}
				return """{"context":{"centerPageIndex":$ordinal,"pageCount":$syntheticPageCount,"layoutMode":"single","step":1,"currentChapterIndex":0,"currentChapterPageStartIndex":0,"currentChapterPageCount":$syntheticPageCount},"targets":[$targets]}"""
			}
			@Suppress("UNCHECKED_CAST")
			val owners = curl.javaClass.task7Field("generationOwners").get(curl) as Map<Long, Any>
			fun assertLatestTurnAdmission(boundary: String) {
				val generation = assertNotNull(latestTurnAdmissionGeneration, "$boundary must retain the accepted generation")
				val admission = assertNotNull(latestTurnAdmission, "$boundary must retain the accepted admission")
				@Suppress("UNCHECKED_CAST")
				val admissions = curl.javaClass.task7Field("generationAdmissions")
					.get(curl) as Map<Long, ReaderDeckAdmission>
				assertEquals("AdmittedRendererOwned", admission.state.name, "$boundary must use renderer-owned authority")
				assertSame(admission, admissions[generation], "$boundary must retain the admission release ledger")
				assertTrue(owners.containsKey(generation), "$boundary must retain physical renderer ownership")
			}
			val preparedCallbacks = mutableSetOf<Long>()
			fun unprepared(): List<Long> = owners.keys.filter { it !in preparedCallbacks }.sorted()
			fun completePrepared(generation: Long) {
				assertTrue(preparedCallbacks.add(generation), "Each renderer input completion is delivered once")
				surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE).invoke(surface, generation)
			}
			fun jobs(): List<Job> {
				val roots = listOf(curl.javaClass.task7Field("rasterJob").get(curl) as Job,
					bundle.javaClass.task7Field("rasterJob").get(bundle) as Job,
					preparation.javaClass.task7Field("teardownJob").get(preparation) as Job)
				val scheduler = bundle.javaClass.task7Field("rasterScheduler").get(bundle)
				val worker = scheduler?.javaClass?.task7Field("workerJob")?.get(scheduler) as Job?
				val scheduled = if (scheduler == null) emptyList() else {
					val lock = assertNotNull(scheduler.javaClass.task7Field("lock").get(scheduler))
					synchronized(lock) {
						(scheduler.javaClass.task7Field("pending").get(scheduler) as Map<*, *>).values.map { request ->
							val owned = assertNotNull(request)
							owned.javaClass.task7Field("result").get(owned) as Job
						}
					}
				}
				return (roots.flatMap { it.children.toList() }.filter { it !== worker } + finiteControllerConsumers() +
					(worker?.children?.toList() ?: emptyList()) + scheduled).filter { !it.isCompleted }
			}
			fun candidate() = viewerClass.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer)
			fun framePending() = surface.javaClass.task7Field("nativeFrameRequest").get(surface) != null
			fun preparing() = preparation.javaClass.task7Field("prewarmInProgress").getBoolean(preparation)
			fun prewarmLayoutPending() = viewerClass.task7Field("pageTurnPrewarmLayoutListener").get(viewer) != null
			fun observeCanonical(boundary: String) {
				if (c6Boundary != Task336C6Boundary.Canonical) return
				val dispatcher = viewerClass.task7Field("presentationReceiptDispatcher").get(viewer)
				val pendingHostEffects = dispatcher.javaClass.task7Field("pendingHostEffects").get(dispatcher) as Map<*, *>
				val lastNative = c6NativeReceipts.lastOrNull()
				println("TASK336_C6 canonicalBoundary=$boundary listener=${prewarmLayoutPending()} " +
					"attached=${viewer.isAttachedToWindow} webAttached=${webView.isAttachedToWindow} " +
					"layout=${viewer.isLayoutRequested} webLayout=${webView.isLayoutRequested} " +
					"viewportPositive=${viewer.width > 0 && viewer.height > 0 && webView.width > 0 && webView.height > 0} " +
					"stableFrames=${viewerClass.task7Field("pageTurnPrewarmStableFrameCount").getInt(viewer)} " +
					"profile=${viewerClass.task7Field("rasterProfileEpoch").get(viewer) != null} " +
					"pagination=${viewerClass.task7Field("rasterPaginationReady").getBoolean(viewer)} " +
					"preparing=${preparing()} deferred=${preparationField("deferredPrewarmSessionId") != null} " +
					"phase=${common.state.presentation.preparationFacts.phase} " +
					"hostPhase=${(viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState).phase} " +
					"authority=${common.state.presentation.authority::class.simpleName} native=${framePending()} " +
					"producers=${c6NativeProducerReceipts.size} producerDisposition=${c6NativeProducerReceipts.lastOrNull()?.disposition} " +
					"finals=${c6NativeReceipts.size} finalReturned=${lastNative?.second != null} " +
					"finalAuthorizes=${lastNative?.let { (event, receipt) -> receipt.authorizes(event) } == true} " +
					"hostEffects=${c6AppliedHostEffects.size} pendingHostEffects=${pendingHostEffects.size} " +
					"effects=${effects.pendingEffects().size} " + initialLiveAccounting())
			}
			fun deliverPrewarmPreDraw(observe: Boolean) {
				if (!prewarmLayoutPending()) return
				if (observe) observeCanonical("pre-draw-before")
				// Complete requested layout with the existing measured view traversal and
				// unchanged bounds. Never clear layout flags or invoke the listener directly.
				if ((viewer.isLayoutRequested || webView.isLayoutRequested) &&
					viewer.width > 0 && viewer.height > 0 && webView.width > 0 && webView.height > 0) {
					viewer.measure(View.MeasureSpec.makeMeasureSpec(viewer.width, View.MeasureSpec.EXACTLY),
						View.MeasureSpec.makeMeasureSpec(viewer.height, View.MeasureSpec.EXACTLY))
					viewer.layout(viewer.left, viewer.top, viewer.right, viewer.bottom)
					webView.measure(View.MeasureSpec.makeMeasureSpec(webView.width, View.MeasureSpec.EXACTLY),
						View.MeasureSpec.makeMeasureSpec(webView.height, View.MeasureSpec.EXACTLY))
					webView.layout(webView.left, webView.top, webView.right, webView.bottom)
				}
				if (prewarmLayoutPending()) viewer.viewTreeObserver.dispatchOnPreDraw()
				if (observe) observeCanonical("pre-draw-after")
			}
			fun workPending() = initialLivePending() || webView.planCallbacks.isNotEmpty() || runtime.pendingCallbackCount != 0 || preparing() ||
				jobs().isNotEmpty() || mainTerminalExecutor.pendingActionCount() != 0 || unprepared().isNotEmpty() || framePending() || effects.pendingEffects().isNotEmpty() ||
				surface.pendingCallbackCount != 0 || bundle.ownershipMetrics().pendingPublicationCallbacks != 0 || prewarmLayoutPending()
			// A bounded causal input driver: it never advances a presentation deadline or
			// waits for all jobs while one of their own source/capture callbacks is held.
			suspend fun drain(label: String, stopWhen: () -> Boolean = { false }) {
				var rounds = 0
				observeCanonical("$label-enter")
				do {
					assertTrue(rounds++ < 100, "$label input continuation exceeded its event bound; " +
						"listener=${prewarmLayoutPending()} layout=${viewer.isLayoutRequested} webLayout=${webView.isLayoutRequested} " +
						"plans=${webView.planCallbacks.size} passive=${runtime.pendingCallbackCount} prewarm=${preparing()} " +
						"jobs=${jobs().size} unprepared=${unprepared().size} native=${framePending()} " +
						"effects=${effects.pendingEffects().size} surface=${surface.pendingCallbackCount} " +
						"publication=${bundle.ownershipMetrics().pendingPublicationCallbacks} " +
						"authority=${common.state.presentation.authority::class.simpleName}")
					testScheduler.runCurrent()
					ShadowLooper.runUiThreadTasks()
					completeInitialLiveSourceInputs()
					deliverPrewarmPreDraw(observe = rounds <= 4 || rounds == 100)
					val callbacks = webView.planCallbacks.toList()
					callbacks.forEach {
						val observation = c4PlanObservations.pollFirst()
						observeC4("response-before", it, observation)
						check(webView.planCallbacks.remove(it)); it.onReceiveValue(sourcePlan())
						observeC4("response-after", it, observation)
					}
					runtime.commits.tryReceive().getOrNull()?.let(runtime::completeCommit)
					runtime.captures.tryReceive().getOrNull()?.let(runtime::completeCapture)
					testScheduler.runCurrent()
					ShadowLooper.runUiThreadTasks()
					if (stopWhen()) return
					unprepared().forEach(::completePrepared)
					if (framePending() && !c6HoldNativeFrames) {
						assertNotNull(candidate(), "$label native request must retain its exact physical candidate")
						val frames = surface.javaClass.task7Field("presentedFrameRequest").get(surface)
						val completion = frames.javaClass.task7Method("markRendered").invoke(frames) as Long
						surface.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE).invoke(surface, completion)
					}
					if (c6Boundary != null) rethrowC6CallbackFailure()
					handler.deliver(effects.pendingEffects(), common.state.presentationDecision) { assertTrue(effects.acknowledge(it)) }
					testScheduler.runCurrent()
					ShadowLooper.runUiThreadTasks()
					if (workPending() && !prewarmLayoutPending() && initialLiveCommands.isEmpty() && initialLiveReceipts.isEmpty() &&
						webView.planCallbacks.isEmpty() && unprepared().isEmpty() && (!framePending() || c6HoldNativeFrames)) {
						val pendingJobs = jobs()
						fun requireRetiredRuntimeInputs() {
							assertTrue(runtime.isRetired, "Only actual runtime retirement can close passive input lanes")
							assertEquals(0, runtime.pendingCallbackCount)
						}
						val action = withContext(Dispatchers.Default) {
							kotlinx.coroutines.withTimeoutOrNull(5_000L) {
								kotlinx.coroutines.selects.select<() -> Unit> {
									if (runtime.isRetired) {
										requireRetiredRuntimeInputs()
										// Retirement closes these lanes, not the remaining finite work.
										listOf(runtime.commits.tryReceive(), runtime.captures.tryReceive()).forEach { result ->
											result.exceptionOrNull()?.let { throw it }
											assertTrue(result.isClosed)
										}
									} else {
										runtime.commits.onReceiveCatching { result -> {
											result.exceptionOrNull()?.let { throw it }
											if (result.isClosed) requireRetiredRuntimeInputs()
											else runtime.completeCommit(result.getOrThrow())
										} }
										runtime.captures.onReceiveCatching { result -> {
											result.exceptionOrNull()?.let { throw it }
											if (result.isClosed) requireRetiredRuntimeInputs()
											else runtime.completeCapture(result.getOrThrow())
										} }
									}
									progress.onReceive { { } }
									pendingJobs.forEach { job -> job.onJoin { { } } }
								}
							}
						}
						if (action == null) {
							gate10Observations += "blocked-input " + ownedJobCounts() + " " + bindingEqualities() +
								"aggregateJobs=${jobs().size} plans=${webView.planCallbacks.size} passive=${runtime.pendingCallbackCount} " +
								"preparing=${preparing()} phase=${common.state.presentation.preparationFacts.phase} " +
								"authority=${common.state.presentation.authority::class.simpleName} " +
								"stage=${(common.state.presentation.authority as? ReaderPresentationAuthority.CurlSettlementPending)?.stage} " +
								"candidate=${candidate() != null} native=${framePending()} surface=${surface.pendingCallbackCount} " +
								"visual=${curl.applicationOwnershipMetrics().pendingVisualCallbacks}"
							assertTrue(pendingCallbackAccepted != false,
								"The admitted turn's owned Pending prepared callback must survive its Curl claim before exact acknowledgement")
						}
						assertNotNull(action, "$label has no reachable input completion; preparing=${preparing()} jobs=${jobs().size} " +
							"phase=${common.state.presentation.preparationFacts.phase} callbacks=${runtime.pendingCallbackCount} " +
							"authority=${common.state.presentation.authority::class.simpleName} " +
							"stage=${(common.state.presentation.authority as? ReaderPresentationAuthority.CurlSettlementPending)?.stage} " +
							"candidate=${candidate() != null} native=${framePending()} surface=${surface.pendingCallbackCount} " +
							"visual=${curl.applicationOwnershipMetrics().pendingVisualCallbacks} " +
							"reportedExact=${physicalBinding() == reporter.lastReportedBinding} " +
							"targetExact=${physicalBinding() == common.state.presentation.binding}").invoke()
																																																					}
				} while (workPending())
				assertEquals(
					initialLiveCommandCount,
					initialLiveAcknowledgementCount + initialLivePreSourceRetirementCount
				)
				assertEquals(
					initialLiveCommandCount,
					initialLiveReceiptCount + initialLivePreSourceRetirementCount
				)
				assertEquals(initialLiveCommandCount, initialLiveCompletionCount + initialLiveVerifiedRetirementCount)
				assertFalse(initialLivePending())
				assertEquals(0, mainTerminalExecutor.pendingActionCount())
				gate10Observations += "initial-live drained " + initialLiveAccounting()
				observeCanonical("$label-drained")
			}
			fun assertSettled() {
				observeCanonical("settled-check")
				val proof = assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority).frame.proof
				assertEquals(physicalBinding(), proof.binding)
				assertEquals(reporter.lastReportedBinding, proof.binding)
				assertEquals(common.state.destinationCommitIdentity, proof.binding.destinationCommitIdentity)
				assertEquals(viewer.width, proof.viewportWidth)
				assertEquals(viewer.height, proof.viewportHeight)
				assertNull(common.state.presentationDecision.pendingTransitionToken)
				assertEquals(ReaderRequiredTransition.None, common.state.presentationDecision.requiredTransition)
				assertFalse(deadlines.hasPending)
			}
			fun installC6NativeObserver() {
				val delegate = viewerClass.task7Field("nativePagePresentationPublisher\$delegate").get(viewer) as Lazy<*>
				assertTrue(delegate.isInitialized(), "Observe the already initialized production native publisher")
				val publisher = assertNotNull(delegate.value)
				val field = publisher.javaClass.task7Field("onEvent")
				@Suppress("UNCHECKED_CAST")
				val original = field.get(publisher) as (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
				restoreC6NativeCallback = { field.set(publisher, original); assertSame(original, field.get(publisher)) }
				field.set(publisher, { event: ReaderPresentationEvent ->
					// Adverse inputs run before dispatchInProgress and before any retry
					// inside the original host callback. Its return is observation only.
					if (event is ReaderPresentationEvent.NativePagePresented) {
						c6BeforeNativeReceipt?.also { c6BeforeNativeReceipt = null }?.let(::captureC6CallbackFailure)
						c6OriginalNativeCalls++
					}
					val returned = original(event) // Exactly once, including a genuine null return.
					if (event is ReaderPresentationEvent.NativePagePresented) {
						c6NativeReceipts += event to returned
						observeCanonical("native-final")
					}
					returned // Never substitute a producer receipt or a test-created proof.
				})
			}
			suspend fun runStartupPreparation() {
				if (c6Boundary != null) installC6NativeObserver()
				if (c6Boundary == Task336C6Boundary.Canonical) {
					// Source failure is delivered by the existing manifest port, not by assigning
					// preparation state or directly invoking the canonical retry event.
					webView.onPassiveManifestRequested = {
						controlledManifestRequestCount++
						if (c6CanonicalFailures == 0) {
							c6CanonicalFailures++
							assertTrue(c6HoldNativeFrames)
							assertTrue(c6NativeReceipts.isEmpty())
							observeCanonical("manifest-deferred-input")
							org.json.JSONObject.quote("""{"failureReason":"canonical-rendered-destination-absent"}""")
						} else null
					}
					val canonicalField = curl.javaClass.task7Field("onCanonicalLiveCommitIssued")
					@Suppress("UNCHECKED_CAST")
					val canonical = canonicalField.get(curl) as () -> Boolean
					canonicalField.set(curl, {
						val deferred = preparationField("deferredPrewarmSessionId") != null
						val resumed = canonical()
						if (deferred && resumed) {
							assertTrue(c6NativeReceipts.isEmpty(), "Canonical preparation cannot depend on the withheld native frame")
							c6CanonicalContinuations++
							c6HoldNativeFrames = false
							observeCanonical("canonical-retry-returned")
						}
						resumed
					})
				}
				curl.setFoliateSessionId(session)
				viewerClass.task7Field("pageTurnFoliateSessionId").set(viewer, session)
				viewerClass.task7Method("preparePresentationEpoch", paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
					ReaderPresentationState::class.java, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
					.invoke(viewer, common.presentationVersion, common.state.presentation, false, true, false)
				relocate(1, "initial-source")
				viewerClass.task7Method("setPageTurnContentReadyKey", String::class.java).invoke(viewer, "task336-content")
				seedCurrentCapture(1)
				val reference = assertNotNull(bundle.retainedCurrentLayoutSnapshot(1,
					ReaderPageTurnTransitionKind.PortraitSlide))
				try {
					val hydrated = kotlinx.coroutines.CompletableDeferred<ReaderPageSlideSnapshot?>()
					val hydration = bundle.hydrateSnapshot(webView, 1,
						ReaderPageTurnTransitionKind.PortraitSlide, reference, onHydrated = hydrated::complete)
					try { assertNotNull(hydrated.await()).release() } finally { hydration.cancel() }
				} finally { reference.release() }
				bundle.initializeRasterCache(webView)
				viewerClass.task7Method("setPageTurnPaginationStatus", String::class.java).invoke(viewer, "ready")
				surface.pageSurfaceListener.onCapabilitiesAvailable(RenderCapabilities(4096, 8L * 1024L * 1024L))
				curl.onHostContentReady()
				drain("startup-profile")
				assertTrue(preparation.prewarmAdjacent(), "Startup must enter actual preparation; " +
					"cold=${viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer)} " +
					"pagination=${viewerClass.task7Field("rasterPaginationReady").getBoolean(viewer)} " +
					"profile=${viewerClass.task7Field("rasterProfileEpoch").get(viewer) != null} " +
					"passive=${adapter.isAvailable} phase=${common.state.presentation.preparationFacts.phase}")
				drain("startup-preparation")
				assertTrue(initialLiveCompletionCount > 0, "Startup must complete a genuinely emitted initial-live source transaction")
				assertSettled()
			}
			runStartupPreparation()
			if (focusedRelocation != null) {
				admissionPredecessor = common.state.presentationDecision
				focusedRelocation(viewerClass, viewer) { ordinal, sourceSession ->
					relocate(ordinal, "toc", sourceSession = sourceSession)
				}
				return@runTest
			}
			val relocationQueue = curl.javaClass.task7Field("relocationQueue").get(curl) as ReaderPageRelocationQueue
			val localTerminalCounts = mutableMapOf<Long, Int>()
			fun turnPage(gesture: Long, beforeSettlement: ((Long, Int) -> Unit)? = null): ReaderPageRelocationRequest {
				assertHostResumed()
				val before = commands.size
				accept(common.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)))
				assertEquals(1, commands.drop(before).filterIsInstance<paige.navic.reader.ReaderEngineCommand.TurnPage>().size)
				synchronize()
				val ordinal = assertNotNull(common.state.chrome.currentLocator?.pageIndex)
				assertNotNull(physicalBinding()?.textureGeneration)
				assertTrue(curl.isAvailable, "Actual turn admission requires content, decoded window and exact native policy; " +
					"decoded=${curl.javaClass.task7Method("hasDecodedWorkingSetForCurrentOrdinal").invoke(curl)} " +
					"policy=${(curl.javaClass.task7Field("pageOperationPolicy").get(curl) as paige.navic.reader.ReaderPageOperationPolicy).newPointer::class.simpleName}")
				val sourceGeneration = curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?
				val rendererPages = owners[sourceGeneration]
				val decodedPages = curl.javaClass.task7Field("activePages").get(curl)
				val sourceDecision = curl.javaClass.task7Field("commonPresentationDecision").get(curl) as ReaderPresentationDecision
				admissionPredecessor = sourceDecision
				val sourcePreparation = curl.javaClass.task7Field("preparationGeneration").getLong(curl)
				fun sourceRasterMatches(pages: Any?): Boolean = pages != null &&
					(pages.javaClass.task7Field("profile").get(pages) as ReaderPlayLikeCurlRasterProfile).rasterGeneration == bundle.currentGeneration()
				gate10Observations += "source-before-admission rendererOwner=${rendererPages != null} decodedOwner=${decodedPages != null} " +
					"ownersSame=${rendererPages === decodedPages} rendererRasterMatches=${sourceRasterMatches(rendererPages)} " +
					"decodedRasterMatches=${sourceRasterMatches(decodedPages)} selectedTextureMatches=${sourceDecision.targetBinding?.textureGeneration == sourceGeneration} " +
					"selectedRasterMatches=${sourceDecision.targetBinding?.rasterGeneration == bundle.currentGeneration()} " +
					"preparationMatches=${sourceDecision.targetBinding?.preparationGeneration == sourcePreparation} " +
					"decodedObsolete=${decodedPages?.let { it.javaClass.task7Field("obsolete").getBoolean(it) }} " +
					"preparationFailed=${curl.javaClass.task7Field("failedPreparationGeneration").get(curl) == sourcePreparation}"
				// Common emitted one command. The real command ingress owns its local
				// terminal callback; calling the pointer controller directly would bypass
				// the viewer's pointer owner and make a successful terminal unowned.
				assertEquals(ReaderPageTurnStartResult.Settling, curl.start(gesture, PageChange.NEXT) { outcome, detail ->
					val count = localTerminalCounts.getOrDefault(gesture, 0) + 1
					localTerminalCounts[gesture] = count
					assertEquals(1, count, "The exact command's local terminal owner accepts once")
					if (outcome != ReaderPageGestureTerminalOutcome.CommittedForward) {
						val rejectedPending = curl.javaClass.task7Field("pendingDeckGenerationId").get(curl) as Long?
						val rejectedDecision = curl.javaClass.task7Field("commonPresentationDecision").get(curl) as ReaderPresentationDecision?
						gate10Observations += "admission-terminal outcome=$outcome claims=${events.count { it is ReaderPresentationEvent.CurlClaimed }} " +
							"sourceGenerationSame=${curl.javaClass.task7Field("activeDeckGenerationId").get(curl) == sourceGeneration} " +
							"rendererOwnerSame=${owners[sourceGeneration] === rendererPages} " +
							"decodedOwnerSame=${curl.javaClass.task7Field("activePages").get(curl) === decodedPages} " +
							"pendingOwnerMatchesDecoded=${rejectedPending != null && owners[rejectedPending] === decodedPages} " +
							"predecessorDecisionSame=${rejectedDecision == sourceDecision} " +
							"onlyInputPolicyChanged=${rejectedDecision?.copy(inputPolicy = sourceDecision.inputPolicy) == sourceDecision}"
					}
					assertEquals(ReaderPageGestureTerminalOutcome.CommittedForward, outcome)
					assertIs<ReaderPageGestureTerminalDetail.SettlementCompleted>(detail)
					true
				})
				val pending = assertNotNull(curl.javaClass.task7Field("pendingDeckGenerationId").get(curl) as Long?)
				@Suppress("UNCHECKED_CAST")
				val admissions = curl.javaClass.task7Field("generationAdmissions")
					.get(curl) as Map<Long, ReaderDeckAdmission>
				val capturedAdmission = admissions[pending]
				latestTurnAdmissionGeneration = pending
				latestTurnAdmission = capturedAdmission
				val prepared = curl.javaClass.task7Field("preparedDeckGenerations").get(curl) as Set<*>
				fun pendingObservation(boundary: String): String {
					val roles = curl.javaClass.task7Field("generationRoles").get(curl) as Map<*, *>
					val role = roles[pending] as ReaderDeckSubmissionRole?
					val decision = curl.javaClass.task7Field("commonPresentationDecision").get(curl) as ReaderPresentationDecision?
					val token = (decision?.authority as? ReaderPresentationAuthority.CurlGesture)?.frame?.frame?.token
					val preparationGeneration = curl.javaClass.task7Field("preparationGeneration").getLong(curl)
					val texture = when (role) {
						ReaderDeckSubmissionRole.Active -> curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?
						ReaderDeckSubmissionRole.Pending -> curl.javaClass.task7Field("pendingDeckGenerationId").get(curl) as Long?
						null -> null
					}
					val raster = bundle.currentGeneration()
					val publishedProfile = curl.javaClass.task7Field("publishedRasterProfileEpoch")
						.get(curl) as Long?
					val generationPreparation = (curl.javaClass.task7Field("generationPreparationGenerations").get(curl) as Map<*, *>)[pending]
					val capability = capturedAdmission?.capability
					val fullMatch = capability != null && decision != null && texture != null &&
						(curl.javaClass.task7Field("deckAdmissionHost").get(curl) as ReaderDeckAdmissionLeaseHost)
							.isCurrent(capability) &&
						readerAcceptedDeckBindingOrNull(
							decision.targetBinding,
							publishedProfile,
							preparationGeneration,
							raster,
							texture
						) == capability.originBinding
					return "$boundary role=$role owner=${owners.containsKey(pending)} prepared=${pending in prepared} " +
						"surfaceOwner=${task8SurfaceOwnsGeneration(surface, pending)} admissionPresent=${admissions.containsKey(pending)} " +
						"capturedAdmissionPresent=${capturedAdmission != null} capturedTokenPresent=${capability?.presentationToken != null} " +
						"currentCurlTokenPresent=${token != null} tokenMatchesCurl=${capability?.presentationToken == token} " +
						"bindingMatches=${capability != null && capability.originBinding == readerAcceptedDeckBindingOrNull(
							decision?.targetBinding, publishedProfile, preparationGeneration, raster, texture ?: -1L)} " +
						"preparationMatches=${generationPreparation == preparationGeneration} " +
						"preparationFailed=${generationPreparation != null && generationPreparation == curl.javaClass.task7Field("failedPreparationGeneration").get(curl)} " +
						"fullMatch=$fullMatch " + bindingEqualities() + " " + ownedJobCounts()
				}
				gate10Observations += pendingObservation("pending-before-callback")
				completePrepared(pending)
				pendingCallbackAccepted = pending in prepared
				assertLatestTurnAdmission("validated pending callback")
				gate10Observations += pendingObservation("pending-after-callback")
				beforeSettlement?.invoke(pending, ordinal + 1)
				// Finish the owned animation input, not just its downstream listener:
				// production must also complete the model and promote the surface deck.
				assertNotNull(surface.javaClass.task7Field("settlementAnimator").get(surface) as android.animation.ValueAnimator?).end()
				assertLatestTurnAdmission("Curl settlement")
				gate10Observations += "terminal " + bindingEqualities() + " " + ownedJobCounts()
				assertEquals(1, localTerminalCounts[gesture])
				assertIs<ReaderPresentationAuthority.CurlSettlementPending>(common.state.presentation.authority)
				assertTrue(relocationQueue.hasDispatchedHead())
				return assertNotNull(relocationQueue.head()).also {
					assertEquals(gesture, it.gestureId)
					assertEquals(ordinal + 1, it.destinationOrdinal)
				}
			}
			suspend fun completeExactAcknowledgement(request: ReaderPageRelocationRequest, assertDecodedRefill: ((List<Job>) -> Unit)? = null) {
				val acknowledgement = ReaderPageTurnSettlementAck(request.token.value, request.destinationOrdinal,
					request.foliateSessionId, request.rasterGeneration, request.textureGeneration)
				relocate(request.destinationOrdinal, "page-turn:exact", acknowledgement)
				val consumers = if (assertDecodedRefill != null) finiteControllerConsumers() else emptyList()
				drain("exact-turn")
				assertDecodedRefill?.invoke(consumers)
				assertNull(relocationQueue.head())
				assertSettled()
			}
			suspend fun runC6Scenario() {
				val ownership = curl.javaClass.task7Field("foregroundWebViewOwnership").get(curl) as ReaderForegroundWebViewOwnership
				fun ownershipCounter(name: String) = ownership.javaClass.task7Field(name).getLong(ownership)
				fun requestOverlayAuthority() {
					// Two genuine active/unavailable edges exercise the real host producer.
					val setter = viewerClass.task7Method("setWhispersyncOverlay", java.lang.Boolean.TYPE,
						ReaderWhispersyncAnchorReceipt::class.java, java.lang.Integer.TYPE)
					setter.invoke(viewer, false, null, Color.WHITE)
					setter.invoke(viewer, true, null, Color.WHITE)
				}
				fun presentNativeInput(): Pair<ReaderPresentationEvent.NativePagePresented, ReaderPresentationEventReceipt?> {
					assertTrue(framePending(), "C6 requires an actual armed native frame, not an injected proof")
					assertNotNull(candidate(), "C6 native callback must start with an exact physical candidate")
					val count = c6NativeReceipts.size
					val calls = c6OriginalNativeCalls
					val frames = surface.javaClass.task7Field("presentedFrameRequest").get(surface)
					val completion = frames.javaClass.task7Method("markRendered").invoke(frames) as Long
					surface.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE).invoke(surface, completion)
					ShadowLooper.runUiThreadTasks()
					c6NativeReceipts.lastOrNull()?.takeIf { c6NativeReceipts.size == count + 1 }?.let { (event, receipt) ->
						println("TASK336_C6 boundary=$c6Boundary variant=$c6NativeBoundary callbackReturned=true " +
							"originalCalls=${c6OriginalNativeCalls - calls} finalHostReturned=${receipt != null} " +
							"finalHostAuthorizes=${receipt.authorizes(event)} callbackFailures=${c6CallbackFailures.size}")
					}
					rethrowC6CallbackFailure()
					assertEquals(calls + 1, c6OriginalNativeCalls, "The original native host callback is invoked exactly once")
					assertEquals(count + 1, c6NativeReceipts.size, "Observe one actual nullable final host return")
					return c6NativeReceipts.last()
				}
				fun assertC6Endpoint() {
					assertSettled()
					assertEquals(publication, common.state.publication)
					assertNull(common.state.pendingShellCoverDismissal)
					assertNull(relocationQueue.head())
					assertTrue(effects.pendingEffects().isEmpty())
					assertTrue(common.state.presentation.rendererCleanupOwnership.isEmpty())
					assertTrue(animations.isEmpty())
					assertTrue(draws.all { it.unregisterCount == 1 })
					assertFalse(workPending(), "C6 positive acceptance requires all owned work drained before teardown")
					assertEquals(0, curl.applicationOwnershipMetrics().pendingVisualCallbacks)
					assertTrue(decisions.all { it.diagnosticPresentation == ReaderDiagnosticPresentation.Hidden })
					assertTrue(events.none { it == ReaderPresentationEvent.Retry || it == ReaderPresentationEvent.Cancel })
					println("TASK336_C6 boundary=$c6Boundary endpoint=true " + initialLiveAccounting())
				}
				if (c6Boundary == Task336C6Boundary.Canonical) {
					assertTrue(c6DecklessCommandsBeforeNative > 0, "Cold source authority must execute before native presentation")
					assertEquals(1, c6CanonicalFailures)
					assertEquals(1, c6CanonicalContinuations, "The real strict receipt must resume the actually deferred preparation")
					assertFalse(c6HoldNativeFrames)
					assertNull(curl.javaClass.task7Field("passiveManifestAuthorityRecoveryToken").get(curl))
					assertC6Endpoint()
					return
				}
				if (c6Boundary == Task336C6Boundary.NoDemand) {
					val count = initialLiveCommandCount
					val proofCount = c6NativeReceipts.size
					val destination = assertNotNull(common.state.destinationCommitIdentity)
					relocate(1, "toc")
					assertEquals(count, initialLiveCommandCount, "Same-center source ingress must not emit an unsolicited initial-live navigation")
					assertFalse(initialLivePending())
					assertTrue(assertNotNull(common.state.destinationCommitIdentity).commitSequence > destination.commitSequence)
					drain("c6-same-center")
					assertTrue(c6NativeReceipts.drop(proofCount).any { (event, receipt) -> receipt.authorizes(event) },
						"The successor must be proved by an actually authorizing final native host return")
					repeat(3) { synchronize() }
					drain("c6-proof-without-demand")
					assertEquals(count, initialLiveCommandCount, "Native proofs and decision application must not create their own next navigation")
					assertC6Endpoint()
					return
				}

				val turn = if (c6Boundary == Task336C6Boundary.Predecessor) null else turnPage(336601L)
				val commandCount = initialLiveCommandCount
				val claimSequence = ownershipCounter("nextClaimId")
				val mutationSequence = ownershipCounter("mutationGeneration")
				val completionCount = initialLiveCompletionCount
				lateinit var transition: ReaderRequiredTransition.PresentNativePage
				val predecessorProof = if (c6Boundary == Task336C6Boundary.Predecessor) {
					assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority).frame.proof
				} else null
				if (predecessorProof != null) {
					val appliedCount = c6AppliedHostEffects.size
					val source = relocate(assertNotNull(common.state.chrome.currentLocator?.pageIndex), "toc")
					assertTrue(c6AppliedHostEffects.drop(appliedCount).any { effect ->
						val relocation = effect.event as? ReaderPresentationEvent.FoliateRelocated
						relocation?.binding?.destinationCommitIdentity == source.destinationCommitIdentity &&
							relocation?.acknowledgement == null
					}, "The fresh source destination must reach actual admitted host-effect application")
					val decision = common.state.presentationDecision
					assertEquals(decision, viewerClass.task7Field("presentationDecision").get(viewer))
					assertEquals(decision, curl.javaClass.task7Field("commonPresentationDecision").get(curl))
					assertEquals(common.presentationVersion, reporter.javaClass.task7Field("authoritativeVersion").get(reporter))
					assertEquals(ReaderRequiredTransition.None, decision.requiredTransition)
					assertEquals(predecessorProof, assertIs<ReaderPresentationFrameOwner.NativePage>(decision.frameOwner).proof)
					assertEquals(source.destinationCommitIdentity, decision.targetBinding?.destinationCommitIdentity)
					assertFalse(predecessorProof.binding == decision.targetBinding)
					println("TASK336_C6 boundary=Predecessor gapBeforeDemand=true sourceHostApplied=true transitionNone=true " +
						"predecessorTargetMismatch=true sourceUnsolicitedCommands=${initialLiveCommandCount - commandCount}")
				} else {
					val request = assertNotNull(turn)
					relocate(request.destinationOrdinal, "page-turn:exact", ReaderPageTurnSettlementAck(request.token.value,
						request.destinationOrdinal, request.foliateSessionId, request.rasterGeneration, request.textureGeneration))
					assertLatestTurnAdmission("exact Foliate acknowledgement")
					val pending = assertIs<ReaderPresentationAuthority.CurlSettlementPending>(common.state.presentation.authority)
					assertEquals(ReaderCurlSettlementStage.AwaitingNativePresentation, pending.stage)
					transition = assertIs<ReaderRequiredTransition.PresentNativePage>(common.state.presentationDecision.requiredTransition)
					assertLatestTurnAdmission("native-frame arm")
					assertEquals(physicalBinding(), transition.binding)
					assertEquals(reporter.lastReportedBinding, transition.binding)
				}
				assertNull(relocationQueue.head(), "This boundary must be after the real queue handoff")
				requestOverlayAuthority()
				val intent = assertNotNull(initialLiveRequest(), "A genuine deferred producer must retain one owned request")
				val beforeProofQuiet = initialLiveCommandCount == commandCount &&
					ownershipCounter("nextClaimId") == claimSequence && ownershipCounter("mutationGeneration") == mutationSequence &&
					intent.javaClass.task7Field("claim").get(intent) == null
				gate10Observations += "c6 boundary=$c6Boundary variant=$c6NativeBoundary beforeProofQuiet=$beforeProofQuiet " + initialLiveAccounting()
				if (c6Boundary == Task336C6Boundary.Demand || c6Boundary == Task336C6Boundary.PostDispatchSupersession) {
					assertTrue(beforeProofQuiet, "Genuine initial-live demand must not claim or mutate the accepted Curl destination before exact native proof")
					val (event, receipt) = presentNativeInput()
					assertTrue(receipt.authorizes(event))
					assertLatestTurnAdmission("exact native receipt")
					val proof = event.proof
					assertEquals(transition.token, proof.transitionToken)
					assertEquals(transition.binding, proof.binding)
					assertEquals(proof, assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority).frame.proof)
					assertEquals(latestTurnAdmissionGeneration,
						curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?,
						"Exact native proof must promote the admitted Pending deck to Active")
					assertLatestTurnAdmission("Pending promotion")
					assertEquals(commandCount + 1, initialLiveCommandCount, "Exact authorizing native completion must retry the retained demand once")
					if (c6Boundary == Task336C6Boundary.PostDispatchSupersession) {
						completeInitialLiveSourceInputs(deliverReceipts = false)
						task336AssertSupersededInitialLiveReceipt(curl, intent, initialLiveReceipts, sourceLiveReceipts,
							commandCount = { initialLiveCommandCount },
							supersede = {
								val ownDestination = assertNotNull(common.state.destinationCommitIdentity)
								val ordinal = assertNotNull(common.state.chrome.currentLocator?.pageIndex)
								val boundary = c6AppliedHostEffects.size
								val source = relocate(ordinal, "toc")
								val newer = assertNotNull(source.destinationCommitIdentity)
								assertEquals(ownDestination.foliateSessionId, newer.foliateSessionId)
								assertTrue(newer.commitSequence > ownDestination.commitSequence)
								assertEquals(ordinal, source.locator.pageIndex)
								assertNull(common.state.pageTurnSettlementAck)
								assertTrue(c6AppliedHostEffects.drop(boundary).any {
									val event = it.event as? ReaderPresentationEvent.FoliateRelocated
									event?.binding?.destinationCommitIdentity == newer && event?.acknowledgement == null
								}, "The untagged superseding source must reach the actual admitted host effect")
							},
							onReceiptDelivered = { completed -> initialLiveReceiptCount++; if (completed) initialLiveCompletionCount++ })
						initialLiveVerifiedRetirementCount++
						drain("c6-superseded-initial-live")
						assertEquals(commandCount + 1, initialLiveCommandCount)
						assertEquals(completionCount, initialLiveCompletionCount)
						assertEquals(1, initialLiveVerifiedRetirementCount)
						assertC6Endpoint()
						return
					}
					completeInitialLiveSourceInputs()
					assertEquals(completionCount + 1, initialLiveCompletionCount)
					drain("c6-exact-native-then-source")
					repeat(3) { synchronize() }
					drain("c6-no-self-retry")
					assertEquals(commandCount + 1, initialLiveCommandCount)
					assertC6Endpoint()
					return
				}

				// Negative cases deliver their adverse real callback boundary even on RED.
				// Any prematurely emitted source command remains held/accounted, never discarded
				// or used to navigate around the original failure; actual invalidation retires it.
				val heldCommandCount = initialLiveCommandCount
				val heldClaimSequence = ownershipCounter("nextClaimId")
				val heldMutationSequence = ownershipCounter("mutationGeneration")
				var adverseBoundaryDelivered = false
				fun assertAppliedDecision() {
					assertEquals(common.state.presentationDecision, viewerClass.task7Field("presentationDecision").get(viewer))
					assertEquals(common.state.presentationDecision, curl.javaClass.task7Field("commonPresentationDecision").get(curl))
					assertEquals(common.presentationVersion, reporter.javaClass.task7Field("authoritativeVersion").get(reporter))
				}
				fun replaceBinding() {
					val binding = assertNotNull(common.state.presentation.binding)
					val replacement = binding.copy(viewportGeneration = binding.viewportGeneration + 1L)
					val event = ReaderPresentationEvent.BindingReplaced(binding, replacement)
					val receipt = viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
						.invoke(viewer, event) as ReaderPresentationEventReceipt?
					assertTrue(receipt.authorizes(event), "The real replacement dispatch must be admitted, not nested/refused")
					assertEquals(replacement, reporter.lastReportedBinding)
					assertEquals(replacement, common.state.presentation.binding)
					assertTrue(c6AppliedHostEffects.any { it.event == event && it.identity.version == receipt?.version })
					assertAppliedDecision()
					assertFalse(transition.binding == common.state.presentationDecision.targetBinding)
				}
				when (c6Boundary) {
					Task336C6Boundary.Predecessor -> {
						val nativeCount = c6NativeReceipts.size
						repeat(3) { synchronize() }
						assertAppliedDecision()
						assertEquals(ReaderRequiredTransition.None, common.state.presentationDecision.requiredTransition)
						assertEquals(assertNotNull(predecessorProof),
							assertIs<ReaderPresentationFrameOwner.NativePage>(common.state.presentationDecision.frameOwner).proof)
						assertFalse(predecessorProof.binding == common.state.presentationDecision.targetBinding)
						assertEquals(nativeCount, c6NativeReceipts.size, "No successor frame was delivered to resolve the genuine proof/target gap")
						adverseBoundaryDelivered = true
					}
					Task336C6Boundary.NativeReceipt -> {
						val callbackEpoch = reporter.captureEpoch()
						val adverse: () -> Unit = {
							when (c6NativeBoundary) {
								Task336C6NativeBoundary.Rejected, Task336C6NativeBoundary.Binding -> replaceBinding()
								Task336C6NativeBoundary.Token -> {
									// Binding-plus-token replacement, not isolated token-only viewer proof.
									// Same-binding wrong/null tokens remain covered by the selected common
									// textureOnlyReplacementRetainsOriginalCoverThenDismissesOnlyForExactSuccessor test.
									replaceBinding()
									assertEquals(ReaderPresentationAuthority.Unavailable, common.state.presentation.authority)
									val replacement = assertNotNull(common.state.presentation.binding)
									val appliedCount = c6AppliedHostEffects.size
									val step = common.onPageTurnBoundary(ReaderPageTurnDirection.Previous)
									val coverReceipt = assertNotNull(step.presentationReceipt)
									assertIs<ReaderPresentationEvent.ShellCoverRequested>(coverReceipt.event)
									accept(step)
									println("TASK336_C6 boundary=NativeReceipt variant=Token bindingPlusToken=true unavailableBeforeCover=true " +
										"coverRequestDisposition=${coverReceipt.disposition}")
									assertTrue(coverReceipt.authorizes(coverReceipt.event), "The before-original cover request must actually admit a successor token")
									val pendingCover = assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(common.state.presentation.authority)
									assertEquals(replacement, pendingCover.binding)
									assertEquals(replacement, pendingCover.retainedFrame.binding)
									assertEquals(ReaderPresentationFrameOwner.Neutral, pendingCover.retainedFrame.frameOwner)
									val coverTransition = assertIs<ReaderRequiredTransition.CommitShellCover>(common.state.presentationDecision.requiredTransition)
									assertFalse(coverTransition.token == transition.token)
									synchronize()
									assertAppliedDecision()
									assertEquals(coverTransition, common.state.presentationDecision.requiredTransition)
									assertTrue(c6AppliedHostEffects.drop(appliedCount).any { it.identity.version == coverReceipt.version &&
										it.decision.requiredTransition == coverTransition })
								}
								Task336C6NativeBoundary.Epoch -> {
									val oldVersion = common.presentationVersion
									val version = oldVersion.copy(readerSessionGeneration = oldVersion.readerSessionGeneration + 1L)
									val result = viewerClass.task7Method("preparePresentationEpoch", paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
										ReaderPresentationState::class.java, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
										.invoke(viewer, version, common.state.presentation, false, true, false)
									assertEquals(ReaderPresentationEpochPreparation.Replaced, result)
									assertTrue(reporter.captureEpoch() > callbackEpoch)
									assertEquals(version.readerSessionGeneration, reporter.expectedReaderSessionGeneration)
									val replacement = assertNotNull(physicalBinding())
									assertFalse(replacement == transition.binding)
									assertEquals(oldVersion, common.presentationVersion)
									val appliedCount = c6AppliedHostEffects.size
									val eventCount = events.size
									synchronize()
									// Refused Compose input still reports identity. PublicationOpened changes
									// common state, but its old-session receipt cannot commit the host model.
									val opened = events.drop(eventCount).filterIsInstance<ReaderPresentationEvent.PublicationOpened>().single()
									assertEquals(replacement, opened.binding)
									assertEquals(replacement, common.state.presentation.binding)
									assertIs<ReaderPresentationAuthority.BlockingPreparation>(common.state.presentation.authority)
									assertEquals(oldVersion.readerSessionGeneration, common.presentationVersion.readerSessionGeneration)
									assertTrue(common.presentationVersion.eventSequence > oldVersion.eventSequence)
									assertEquals(version.readerSessionGeneration, reporter.expectedReaderSessionGeneration)
									assertEquals(appliedCount, c6AppliedHostEffects.size, "Old-session Compose input and producer receipt must both be refused after epoch replacement")
									println("TASK336_C6 boundary=NativeReceipt variant=Epoch publicationOpened=true commonBindingReplaced=true " +
										"commonBlocking=true commonOldSession=true reporterNextSession=true newHostEffects=0")
								}
							}
							adverseBoundaryDelivered = true
						}
						c6BeforeNativeReceipt = adverse
						val (event, receipt) = presentNativeInput()
						val producer = assertNotNull(c6NativeProducerReceipts.last())
						assertEquals(event, producer.event)
						assertEquals(transition.token, event.proof.transitionToken)
						assertEquals(transition.binding, event.proof.binding)
						if (receipt != null) assertSame(producer, receipt)
						assertFalse(receipt.authorizes(event), "The adverse state must reject the original native event before any retry can use it")
						if (c6NativeBoundary == Task336C6NativeBoundary.Epoch) {
							assertEquals(ReaderPresentationEventDisposition.Stale, producer.disposition)
							assertNull(receipt, "The old-session producer receipt must not survive the changed host epoch/session")
							assertEquals(common.presentationVersion.readerSessionGeneration, producer.version.readerSessionGeneration)
							assertTrue(producer.version.readerSessionGeneration < assertNotNull(reporter.expectedReaderSessionGeneration))
							assertTrue(reporter.captureEpoch() > callbackEpoch)
						} else {
							assertEquals(ReaderPresentationEventDisposition.Stale, producer.disposition)
						}
						println("TASK336_C6 boundary=NativeReceipt variant=$c6NativeBoundary adverseAdmitted=$adverseBoundaryDelivered " +
							"finalHostReturned=${receipt != null} finalHostAuthorizes=${receipt.authorizes(event)} " +
							"producerDisposition=${producer.disposition} oldComposeRefused=${c6NativeBoundary == Task336C6NativeBoundary.Epoch}")
					}
					Task336C6Boundary.Retirement -> {
						requestOverlayAuthority()
						assertSame(intent, initialLiveRequest(), "Repeated actual demand must coalesce in the existing single slot")
						adverseBoundaryDelivered = true
					}
					else -> error("Unexpected C6 negative boundary")
				}
				assertTrue(adverseBoundaryDelivered)
				assertEquals(heldCommandCount, initialLiveCommandCount, "Rejected, replaced, predecessor or coalesced work cannot dispatch another mutation")
				assertEquals(heldClaimSequence, ownershipCounter("nextClaimId"), "Adverse input must not allocate a new live claim")
				assertEquals(heldMutationSequence, ownershipCounter("mutationGeneration"), "Adverse input must not begin a new foreground mutation")
				c6CancelledRequest = intent
				c6CancelledCommands = initialLiveCommands.count { it.request === intent }
				val heldFrame = if (framePending()) {
					val frames = surface.javaClass.task7Field("presentedFrameRequest").get(surface)
					frames.javaClass.task7Method("markRendered").invoke(frames) as Long
				} else null
				curl.invalidate("task336-c6-retirement")
				assertNull(initialLiveRequest(), "Actual invalidation must retire the exact request")
				val retiredMutation = ownershipCounter("mutationGeneration")
				val retiredReceiptCount = c6NativeReceipts.size
				repeat(2) {
					initialLiveCommands.filter { it.request === intent }.forEach { it.callback?.onReceiveValue("{}") }
					heldFrame?.let { surface.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE).invoke(surface, it) }
				}
				ShadowLooper.runUiThreadTasks()
				assertEquals(heldCommandCount, initialLiveCommandCount)
				assertEquals(retiredMutation, ownershipCounter("mutationGeneration"))
				assertEquals(retiredReceiptCount, c6NativeReceipts.size)
				assertNull(initialLiveRequest())
				println("TASK336_C6 boundary=$c6Boundary variant=$c6NativeBoundary adverseDelivered=$adverseBoundaryDelivered " +
					"retired=true cancelledCommands=$c6CancelledCommands beforeProofQuiet=$beforeProofQuiet")
				assertTrue(beforeProofQuiet, "Negative native receipt controls must also retain demand without any pre-proof claim or mutation")
				return
			}
			if (c6Boundary != null) {
				runC6Scenario()
				return@runTest
			}
			suspend fun runMandatoryViewerSequence() {
				completeExactAcknowledgement(turnPage(3361L))
				val tocSelectedGeneration = if (readyReplacement != null || selectedPredecessorRollback) {
					assertNotNull(curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
				} else {
					null
				}
				val tocSelectedPreparation = tocSelectedGeneration?.let { generation ->
					@Suppress("UNCHECKED_CAST")
					(curl.javaClass.task7Field("generationPreparationGenerations").get(curl)
						as Map<Long, Long>)[generation]
				}
				accept(common.navigateTo(paige.navic.reader.ReaderLocator(progress = 0.0, pageIndex = 0, pageCount = syntheticPageCount)))
				relocate(0, "toc")
				if (tocSelectedGeneration != null) {
					@Suppress("UNCHECKED_CAST")
					val roles = curl.javaClass.task7Field("generationRoles").get(curl)
						as Map<Long, ReaderDeckSubmissionRole>
					@Suppress("UNCHECKED_CAST")
					val preparations = curl.javaClass.task7Field("generationPreparationGenerations").get(curl)
						as Map<Long, Long>
					@Suppress("UNCHECKED_CAST")
					val prepared = curl.javaClass.task7Field("preparedDeckGenerations").get(curl)
						as Set<Long>
					@Suppress("UNCHECKED_CAST")
					val admissions = curl.javaClass.task7Field("generationAdmissions").get(curl)
						as Map<Long, ReaderDeckAdmission>
					assertTrue(task8SurfaceOwnsGeneration(surface, tocSelectedGeneration),
						"TOC invalidation must retain the selected physical predecessor")
					assertTrue(owners.containsKey(tocSelectedGeneration))
					assertEquals(tocSelectedGeneration,
						curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
					assertEquals(ReaderDeckSubmissionRole.Active, roles[tocSelectedGeneration])
					assertEquals(tocSelectedPreparation, preparations[tocSelectedGeneration])
					assertTrue(tocSelectedGeneration in prepared)
					assertEquals(ReaderDeckAdmissionState.ReleaseRequested,
						assertNotNull(admissions[tocSelectedGeneration]).state)
					assertNull(curl.javaClass.task7Field("activePages").get(curl))
					assertFalse(curl.isAvailable)
					assertNull(candidate())
					assertNull(initialLiveRequest())
				}
				assertNull(common.state.pageTurnSettlementAck, "Same-session TOC must not inherit the preceding turn receipt")
				seedCurrentCapture(0)
				if (selectedPredecessorRollback) {
					val predecessor = assertNotNull(tocSelectedGeneration)
					rollbackPredecessorGeneration = predecessor
					rollbackSelectedBindingBeforeEpoch = when (val selected = common.state.presentationDecision.frameOwner) {
						is ReaderPresentationFrameOwner.NativePage -> selected.proof.binding
						is ReaderPresentationFrameOwner.Curl -> selected.frame.binding
						else -> error("Expected selected native predecessor before replacement")
					}
					Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = { queuedSurface ->
						assertSame(surface, queuedSurface)
						val coordinator = surface.javaClass.task7Field("deckCoordinator").get(surface)
						val successor = listOf("activeDeck", "pendingDeck")
							.mapNotNull { slot -> coordinator.javaClass.task7Field(slot).get(coordinator) as PageDeck<*>? }
							.single { deck -> deck.generationId != predecessor }
							.generationId
						rollbackSuccessorGeneration = successor
						val releaseSuccessor = {
							if (replaceHostEpochBeforeSuccessorRelease) {
								assertEquals(successor,
									curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
								rollbackNativeProofCountBeforeEpoch =
									events.filterIsInstance<ReaderPresentationEvent.NativePagePresented>().size
								val oldEpoch = reporter.captureEpoch()
								val replacementVersion = common.presentationVersion.copy(
									readerSessionGeneration = common.presentationVersion.readerSessionGeneration + 1L
								)
								val result = viewerClass.task7Method("preparePresentationEpoch",
									paige.navic.reader.ReaderPresentationReceiptVersion::class.java,
									ReaderPresentationState::class.java, java.lang.Boolean.TYPE,
									java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
									.invoke(viewer, replacementVersion, common.state.presentation, false, true, false)
								assertEquals(ReaderPresentationEpochPreparation.Replaced, result)
								assertTrue(reporter.captureEpoch() > oldEpoch)
								rollbackReplacementHostEpoch = reporter.captureEpoch()
								assertNull(reporter.lastReportedBinding)
							}
							val releaseStatus = surface.releaseDeck(successor).status.toString()
							if (replaceHostEpochBeforeSuccessorRelease) {
								assertTrue(releaseStatus == "ACCEPTED" || releaseStatus == "ALREADY_ACCEPTED")
							} else {
								assertEquals("ACCEPTED", releaseStatus)
							}
						}
						if (replaceHostEpochBeforeSuccessorRelease) {
							surface.post(releaseSuccessor)
						} else {
							releaseSuccessor()
						}
					}
				}
				curl.onHostContentReady()
				drain("toc-profile") {
					selectedPredecessorRollback && rollbackSuccessorGeneration?.let {
						rollbackRendererReleaseCounts[it] == 1
					} == true
				}
				if (selectedPredecessorRollback) {
					val predecessor = assertNotNull(tocSelectedGeneration)
					val successor = assertNotNull(rollbackSuccessorGeneration)
					assertEquals(predecessor, rollbackPredecessorGeneration)
					val selectedBinding = if (replaceHostEpochBeforeSuccessorRelease) {
						assertNotNull(rollbackSelectedBindingBeforeEpoch)
					} else when (val selected = common.state.presentationDecision.frameOwner) {
						is ReaderPresentationFrameOwner.NativePage -> selected.proof.binding
						is ReaderPresentationFrameOwner.Curl -> selected.frame.binding
						else -> error("Expected selected native predecessor before replacement proof")
					}
					assertEquals(predecessor, selectedBinding.textureGeneration)
					assertEquals(1, rollbackRendererReleaseCounts[successor])
					assertFalse(task8SurfaceOwnsGeneration(surface, successor))
					assertFalse(owners.containsKey(successor))
					if (replaceHostEpochBeforeSuccessorRelease) {
						assertNull(rollbackRestoredGeneration,
							"An obsolete admission owner scope must not restore selected G")
						assertNull(curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
						assertNull(curl.javaClass.task7Field("activeDeckPreparationGeneration").get(curl) as Long?)
						assertNull(curl.javaClass.task7Field("activePages").get(curl))
						assertFalse(curl.isAvailable)
						assertNull(candidate())
						assertNull(initialLiveRequest())
						assertNull(viewerClass.task7Field("preparedActiveDeck").get(viewer))
						assertEquals(rollbackReplacementHostEpoch, reporter.captureEpoch())
						assertNull(reporter.lastReportedBinding)
						assertEquals(rollbackNativeProofCountBeforeEpoch,
							events.filterIsInstance<ReaderPresentationEvent.NativePagePresented>().size)
						val staleControllerBinding = when (
							val selected = (curl.javaClass.task7Field("commonPresentationDecision").get(curl)
								as ReaderPresentationDecision).frameOwner
						) {
							is ReaderPresentationFrameOwner.NativePage -> selected.proof.binding
							is ReaderPresentationFrameOwner.Curl -> selected.frame.binding
							else -> null
						}
						assertEquals(predecessor, staleControllerBinding?.textureGeneration,
							"The controller must fail closed despite its not-yet-echoed common proof")
						assertEquals(1, rollbackRendererReleaseCounts[predecessor])
						assertFalse(task8SurfaceOwnsGeneration(surface, predecessor))
						assertFalse(owners.containsKey(predecessor))
						assertNull(curl.javaClass.task7Field("retainedSelectedPresentationPredecessorGenerationId").get(curl))
						@Suppress("UNCHECKED_CAST")
						val admissions = curl.javaClass.task7Field("generationAdmissions").get(curl)
							as Map<Long, ReaderDeckAdmission>
						assertFalse(admissions.containsKey(predecessor))
						assertFalse(admissions.containsKey(successor))
						assertTrue(curl.javaClass.task7Field("hostResumed").getBoolean(curl))
						assertTrue(curl.javaClass.task7Field("enabled").getBoolean(curl))
						assertTrue(curl.javaClass.task7Field("attached").getBoolean(curl))
						curl.onPresentationHostEpochChanged()
						ShadowLooper.runUiThreadTasks()
						for (generation in listOf(successor, predecessor)) {
							surface.javaClass.task7Method("handleDeckReleased", java.lang.Long.TYPE,
								karacken.curl.DeckReleaseReason::class.java)
								.invoke(surface, generation, karacken.curl.DeckReleaseReason.EXPLICIT)
							assertEquals(1, rollbackRendererReleaseCounts[generation])
						}
						assertNotNull(rollbackCapacityRetry).invoke()
						testScheduler.runCurrent()
						ShadowLooper.runUiThreadTasks()
						assertNull(curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
						assertFalse(owners.containsKey(predecessor))
						assertFalse(owners.containsKey(successor))
						assertEquals(rollbackNativeProofCountBeforeEpoch,
							events.filterIsInstance<ReaderPresentationEvent.NativePagePresented>().size)
						return
					}
					assertEquals(predecessor, rollbackRestoredGeneration)
					assertEquals(predecessor,
						curl.javaClass.task7Field("activeDeckGenerationId").get(curl) as Long?)
					assertEquals(tocSelectedPreparation,
						curl.javaClass.task7Field("activeDeckPreparationGeneration").get(curl) as Long?)
					assertNull(curl.javaClass.task7Field("activePages").get(curl))
					assertFalse(curl.isAvailable)
					assertNull(candidate())
					assertTrue(task8SurfaceOwnsGeneration(surface, predecessor))
					assertTrue(owners.containsKey(predecessor))
					surface.javaClass.task7Method("handleDeckReleased", java.lang.Long.TYPE,
						karacken.curl.DeckReleaseReason::class.java)
						.invoke(surface, successor, karacken.curl.DeckReleaseReason.EXPLICIT)
					assertEquals(1, rollbackRendererReleaseCounts[successor])

					assertNotNull(publish(ReaderPresentationEvent.Lifecycle(
						ReaderPresentationLifecycleEvent.RendererLost)))
					synchronize()
					if (common.state.presentationDecision.frameOwner != ReaderPresentationFrameOwner.Neutral) {
						assertNotNull(publish(ReaderPresentationEvent.BindingReplaced(selectedBinding,
							selectedBinding.copy(rasterGeneration = null, textureGeneration = null))))
						synchronize()
					}
					ShadowLooper.runUiThreadTasks()
					assertEquals(ReaderPresentationFrameOwner.Neutral,
						common.state.presentationDecision.frameOwner)
					assertEquals(1, rollbackRendererReleaseCounts[predecessor])
					assertFalse(task8SurfaceOwnsGeneration(surface, predecessor))
					assertFalse(owners.containsKey(predecessor))
					surface.javaClass.task7Method("handleDeckReleased", java.lang.Long.TYPE,
						karacken.curl.DeckReleaseReason::class.java)
						.invoke(surface, predecessor, karacken.curl.DeckReleaseReason.EXPLICIT)
					assertEquals(1, rollbackRendererReleaseCounts[predecessor])
					return
				}
				preparation.prewarmAdjacent()
				drain("toc-preparation")
				assertSettled()
				val turnBoundary = commands.size
				lateinit var assertDecodedRefill: (List<Job>) -> Unit
				val secondTurn = turnPage(3362L) { pendingGeneration, destinationOrdinal ->
					assertDecodedRefill = task336DecodedRefillCompletionAssertion(curl, pendingGeneration, destinationOrdinal)
				}
				completeExactAcknowledgement(secondTurn, assertDecodedRefill)
				assertEquals(1, commands.drop(turnBoundary).filterIsInstance<paige.navic.reader.ReaderEngineCommand.TurnPage>().size)
				val destination = assertNotNull(common.state.destinationCommitIdentity)
				val expectedLocator = assertNotNull(common.state.chrome.currentLocator)
				val expectedOrdinal = assertNotNull(expectedLocator.pageIndex)
				accept(common.onPageTurnBoundary(ReaderPageTurnDirection.Previous))
				synchronize()
				assertIs<ReaderRequiredTransition.CommitShellCover>(common.state.presentationDecision.requiredTransition)
				val page = common.state.presentationDecision.frameOwner
				draws.last().draw()
				assertEquals(page, common.state.presentationDecision.frameOwner, "Draw alone cannot replace the selected page")
				animations.removeFirst().invoke()
				val retainedCover = assertIs<ReaderPresentationFrameOwner.ShellCover>(common.state.presentationDecision.frameOwner)
				assertEquals(1, events.filterIsInstance<ReaderPresentationEvent.ShellCoverCommitted>().size)
				assertTrue(preparation.prewarmAdjacent(), "Returned cover must start ordinary owned preparation")
				assertTrue(preparing())
				assertTrue(webView.planCallbacks.isNotEmpty(), "Hold the preparation controller's actual owned source response")
				assertEquals(ReaderPagePreparationPhase.Preparing,
					(viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState).phase)
				assertEquals(ReaderPagePreparationPhase.Preparing, common.state.presentation.preparationFacts.phase)
				assertEquals(ReaderPreparationPresentation.Hidden, common.state.presentationDecision.preparationPresentation)
				assertNull(candidate())
				c4HeldSession = preparationField("prewarmSession")
				c4HeldPreparation = preparationField("preparationGeneration")
				c4HeldRaster = bundle.currentGeneration()
				c4HeldCallbacks = webView.planCallbacks.toList()
				c4Observing = true
				observeC4("before-entry")
				val entryBoundary = common.state.shellCoverDismissalRequestSequence
				val commandBoundary = commands.size
				var entryToken: ReaderPresentationToken? = null
				repeat(2) {
					accept(common.onViewerAction(ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)))
					synchronize()
					observeC4(if (it == 0) "after-first-entry" else "after-second-entry")
					if (it == 0) entryToken = assertNotNull(common.state.presentationDecision.pendingTransitionToken)
					assertEquals(entryToken, common.state.presentationDecision.pendingTransitionToken)
					assertEquals(retainedCover, common.state.presentationDecision.frameOwner)
					assertIs<ReaderPreparationPresentation.Blocking>(common.state.presentationDecision.preparationPresentation)
					assertTrue(preparing())
					assertNull(candidate())
				}
				assertEquals(1L, common.state.shellCoverDismissalRequestSequence - entryBoundary)
				val navigation = commands.drop(commandBoundary).filterIsInstance<paige.navic.reader.ReaderEngineCommand.NavigateTo>().single()
				assertEquals(expectedOrdinal, navigation.locator.pageIndex)
				assertTrue(expectedLocator.copy(reason = null) == navigation.locator.copy(reason = null),
					"Cover entry must navigate to the unchanged semantic destination")
				val entryRelocation = relocate(assertNotNull(navigation.locator.pageIndex), assertNotNull(navigation.relocationReason))
				val entryDestination = assertNotNull(entryRelocation.destinationCommitIdentity)
				assertEquals(destination.foliateSessionId, entryDestination.foliateSessionId)
				assertTrue(entryDestination.commitSequence > destination.commitSequence,
					"Every delivered source relocation must carry a strictly newer same-session commit")
				assertEquals(entryDestination, common.state.destinationCommitIdentity)
				assertEquals(expectedOrdinal, entryRelocation.locator.pageIndex)
				assertTrue(expectedLocator.copy(reason = null) == entryRelocation.locator.copy(reason = null),
					"A fresh source commit must not change the semantic destination")
				assertNull(common.state.pageTurnSettlementAck)
				val readyBoundary = events.size
				if (readyReplacement != null) {
					val commandCount = initialLiveCommandCount
					val engineCommands = commands.size
					val request = initialLiveRequest()
					val ownership = curl.javaClass.task7Field("foregroundWebViewOwnership").get(curl)
					fun claims() = (ownership.javaClass.task7Field("liveClaims").get(ownership) as Map<*, *>).keys.toSet()
					val originalClaims = claims()
					readyReplacementProbe = Task336ReadyReplacementProbe(readyReplacement, viewerClass, viewer, curl,
						preparation, reporter, common = { common }, assertNoNewDemand = {
							assertEquals(commandCount, initialLiveCommandCount)
							assertEquals(engineCommands, commands.size)
							assertTrue(initialLiveRequest() == null || initialLiveRequest() === request)
							assertTrue(originalClaims.containsAll(claims()))
						})
				}
				drain("automatic-cover-entry")
				observeC4("after-drain")
				if (readyReplacement != null) {
					assertNotNull(readyReplacementProbe).verify()
					assertFalse(workPending(), "Negative replacement must retire all finite work before teardown")
					assertNull(initialLiveRequest())
					assertNull(relocationQueue.head())
					assertEquals(ReaderRequiredTransition.None, common.state.presentationDecision.requiredTransition)
					assertNull(common.state.presentationDecision.pendingTransitionToken)
					assertTrue(effects.pendingEffects().isEmpty())
					assertEquals(0, mainTerminalExecutor.pendingActionCount())
					assertEquals(0, curl.applicationOwnershipMetrics().pendingVisualCallbacks)
					assertTrue(events.none { it == ReaderPresentationEvent.Retry || it == ReaderPresentationEvent.Cancel })
					return
				}
				assertTrue(events.drop(readyBoundary).filterIsInstance<ReaderPresentationEvent.PreparationReported>()
					.any { it.facts.phase == ReaderPagePreparationPhase.Ready })
				assertSettled()
				assertEquals(entryDestination, common.state.destinationCommitIdentity)
				assertEquals(expectedOrdinal, common.state.chrome.currentLocator?.pageIndex)
				assertTrue(expectedLocator.copy(reason = null) == common.state.chrome.currentLocator?.copy(reason = null),
					"Settled cover entry must preserve the semantic destination")
				val proof = assertIs<ReaderPresentationFrameOwner.NativePage>(common.state.presentationDecision.frameOwner).proof
				val visibilityLossPreparationFloor = assertNotNull(proof.binding.preparationGeneration)
				assertEquals(entryDestination, physicalBinding()?.destinationCommitIdentity)
				assertEquals(entryDestination, proof.binding.destinationCommitIdentity)
				assertEquals(expectedOrdinal,
					(viewerClass.task7Field("preparedActiveDeck").get(viewer) as ReaderPagePreparedActiveDeck).sourceCenterPageIndex)
				assertEquals(entryToken, proof.transitionToken)
				val restoreLifecycleBoundary = events.size
				pauseHostLifecycle()
				assertFalse(curl.javaClass.task7Field("hostResumed").getBoolean(curl))
				(preparation.javaClass.task7Field("memoryCallbacks").get(preparation) as android.content.ComponentCallbacks2)
					.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
				if (restoreBoundary == Task373VisibilityRestoreBoundary.DelayedRestoreCaller) {
					assertTrue(blockedVisibilityLossDeliveries > 0)
					assertEquals(ReaderPresentationLifecycleState.Foreground, common.state.presentation.lifecycle)
					visibilityLossDeliveryEnabled = true
				} else {
					assertEquals(ReaderPresentationLifecycleState.Background, common.state.presentation.lifecycle)
				}
				assertEquals(publication, common.state.publication)
				lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
				lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
				val retryInProgressField = preparation.javaClass.task7Field("retryPreparationInProgress")
				if (restoreBoundary == Task373VisibilityRestoreBoundary.TemporarilyIneligiblePreparation) {
					retryInProgressField.setBoolean(preparation, true)
				}
				viewerClass.task7Method("onWindowVisibilityChanged", Integer.TYPE).invoke(viewer, View.VISIBLE)
				if (restoreBoundary == Task373VisibilityRestoreBoundary.TemporarilyIneligiblePreparation) {
					assertEquals(
						visibilityLossPreparationFloor,
						preparation.javaClass.task7Field("preparationGeneration").getLong(preparation),
						"A temporarily ineligible accepted restore must retain its exact floor"
					)
					retryInProgressField.setBoolean(preparation, false)
				}
				if (restoreBoundary == Task373VisibilityRestoreBoundary.DelayedRestoreCaller) {
					val visibilityEvents = events.drop(restoreLifecycleBoundary).mapNotNull {
						(it as? ReaderPresentationEvent.Lifecycle)?.event
					}.filter {
						it == ReaderPresentationLifecycleEvent.VisibilityLost ||
							it == ReaderPresentationLifecycleEvent.VisibilityRestored
					}
					assertEquals(
						listOf(
							ReaderPresentationLifecycleEvent.VisibilityLost,
							ReaderPresentationLifecycleEvent.VisibilityRestored
						),
						visibilityEvents
					)
					assertTrue(
						visibilityRestoreDeliveryStacks.single().contains(
							"reportPresentationIdentityIfAvailable"
						),
						"Queued restoration must be accepted by the later identity retry path"
					)
				}
				viewerClass.task7Method("onWindowVisibilityChanged", Integer.TYPE).invoke(viewer, View.VISIBLE)
				if (restoreBoundary != null) {
					assertEquals(
						Math.incrementExact(visibilityLossPreparationFloor),
						preparation.javaClass.task7Field("preparationGeneration").getLong(preparation),
						"Accepted restoration must allocate exactly once above its retained floor"
					)
				}
				assertHostResumed()
				drain("restore")
				assertSettled()
				val restoredProof = assertIs<ReaderPresentationFrameOwner.NativePage>(
					common.state.presentationDecision.frameOwner
				).proof
				assertEquals(
					Math.incrementExact(visibilityLossPreparationFloor),
					restoredProof.binding.preparationGeneration,
					"Automatic restore must allocate exactly once above its retired proof floor"
				)
				val restoredDestination = assertNotNull(common.state.destinationCommitIdentity)
				assertEquals(entryDestination.foliateSessionId, restoredDestination.foliateSessionId)
				assertTrue(
					restoredDestination.commitSequence > entryDestination.commitSequence,
					"Fresh restore authority must carry a newer same-session source commit"
				)
				assertEquals(restoredDestination, physicalBinding()?.destinationCommitIdentity)
				assertEquals(restoredDestination, restoredProof.binding.destinationCommitIdentity)
				assertEquals(expectedOrdinal, common.state.chrome.currentLocator?.pageIndex)
				assertEquals(ReaderPresentationLifecycleState.Foreground, common.state.presentation.lifecycle)
				assertEquals(publication, common.state.publication)
				assertTrue(decisions.all { it.diagnosticPresentation == ReaderDiagnosticPresentation.Hidden })
				assertTrue(events.none { it == ReaderPresentationEvent.Retry || it == ReaderPresentationEvent.Cancel })
				assertNull(common.state.pendingShellCoverDismissal)
				val restoreSettlement = assertNotNull(common.state.pageTurnSettlementAck)
				assertTrue(restoreSettlement.token.startsWith("initial-live-"))
				assertEquals(expectedOrdinal, restoreSettlement.pageIndex)
				assertEquals(restoredProof.binding.foliateSessionId, restoreSettlement.foliateSessionId)
				assertEquals(restoredProof.binding.rasterGeneration, restoreSettlement.rasterGeneration)
				assertEquals(restoredProof.binding.textureGeneration, restoreSettlement.textureGeneration)
				assertNull(relocationQueue.head())
				assertTrue(effects.pendingEffects().isEmpty())
				assertTrue(common.state.presentation.rendererCleanupOwnership.isEmpty())
				assertTrue(animations.isEmpty())
				assertTrue(draws.all { it.unregisterCount == 1 })
				assertFalse(workPending(), "Success requires endpoint quiescence before teardown")
				assertEquals(0, curl.applicationOwnershipMetrics().pendingVisualCallbacks)
			}
			runMandatoryViewerSequence()
		} catch (failure: Throwable) {
			gate10Observations += "initial-live failure " + initialLiveAccounting()
			gate10Observations.forEach { println("TASK336_GATE10 $it") }
			bodyFailure = failure
			throw failure
		} finally {
			suspend fun runSequenceCleanup() {
				var cleanupFailure = bodyFailure
				suspend fun cleanup(block: suspend () -> Unit) {
					try {
						block()
					} catch (failure: Throwable) {
						val original = cleanupFailure
						if (original == null) cleanupFailure = failure
						else if (original !== failure) original.addSuppressed(failure)
					}
				}
				cleanup { readyReplacementProbe?.restore() }
				cleanup { restoreReadyOwnerObservation?.invoke() }
				cleanup { restorePreparationObservation?.invoke() }
				cleanup { assertFalse(currentLiveObservationFailed.get(), "Current-live observation must not fail while reading scalar evidence") }
				if (c6Boundary != null) cleanup {
					restoreC6NativeCallback?.invoke()
					c6BeforeNativeReceipt = null
					assertEquals(c6OriginalNativeCalls, c6NativeReceipts.size)
				}
				if (c6Boundary != null) cleanup { rethrowC6CallbackFailure() }
				cleanup { bridge?.dispose() }
				cleanup { pauseHostLifecycle() }
				cleanup {
					surface.detach()
					surface.surfaceDestroyed(surface.holder)
					viewerClass.task7Method("closeReader").invoke(viewer)
					val teardown = listOf(preparation.destroy(), curl.destroy(), bundle.close())
					withContext(Dispatchers.Default) { withTimeout(5_000L) { teardown.forEach { it.await() } } }
					ShadowLooper.runUiThreadTasks()
					assertEquals(0, surface.pendingCallbackCount)
					assertEquals(0, runtime.pendingCallbackCount)
					assertTrue(runtime.isRetired)
					assertTrue(runtime.deliveredBitmaps.all { it.isRecycled })
					assertFalse(deadlines.hasPending)
					latestTurnAdmission?.let { admission ->
						val generation = assertNotNull(latestTurnAdmissionGeneration)
						@Suppress("UNCHECKED_CAST")
						val admissions = curl.javaClass.task7Field("generationAdmissions")
							.get(curl) as Map<Long, ReaderDeckAdmission>
						assertEquals(ReaderDeckAdmissionState.Released, admission.state,
							"Teardown must terminate the admitted renderer ownership")
						assertFalse(admissions.containsKey(generation), "Teardown must retire the admission release ledger")
						@Suppress("UNCHECKED_CAST")
						val remainingOwners = curl.javaClass.task7Field("generationOwners").get(curl) as Map<Long, Any>
						assertFalse(remainingOwners.containsKey(generation), "Teardown must retire physical renderer ownership")
					}
					if (selectedPredecessorRollback) {
						assertEquals(1, rollbackRendererReleaseCounts[assertNotNull(rollbackSuccessorGeneration)])
						assertEquals(1, rollbackRendererReleaseCounts[assertNotNull(rollbackPredecessorGeneration)])
					}
				}
				cleanup {
					if (lifecycleOwner.registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
						lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
					}
				}
				cleanup { (viewer.parent as? android.view.ViewGroup)?.removeView(viewer) }
				cleanup { activity.pause().stop().destroy() }
				cleanup {
					assertNull(viewerClass.task7Field("observedHostLifecycle").get(viewer))
					assertEquals(0, lifecycleOwner.registry.observerCount)
					assertFalse(curl.javaClass.task7Field("hostResumed").getBoolean(curl))
				}
				cleanup { runtime.destroy() }
				if (readyReplacement != null) cleanup {
					assertNull(initialLiveRequest())
					assertTrue(initialLiveCommands.isEmpty() && initialLiveReceipts.isEmpty() && sourceLiveReceipts.isEmpty())
					assertEquals(initialLiveCommandCount, initialLiveCompletionCount + initialLiveVerifiedRetirementCount)
					val executor = curl.javaClass.task7Field("mainTerminalExecutor").get(curl) as ReaderMainTerminalActionExecutor
					assertEquals(0, executor.pendingActionCount())
					assertTrue(executor.drainJob.isCompleted)
					assertTrue((curl.javaClass.task7Field("teardownJob").get(curl) as Job).children.none { !it.isCompleted })
					val ownership = curl.javaClass.task7Field("foregroundWebViewOwnership").get(curl)
					assertTrue((ownership.javaClass.task7Field("liveClaims").get(ownership) as Map<*, *>).isEmpty())
				}
				if (c6Boundary != null) cleanup {
					assertNull(initialLiveRequest())
					val ownership = curl.javaClass.task7Field("foregroundWebViewOwnership").get(curl)
					assertTrue((ownership.javaClass.task7Field("liveClaims").get(ownership) as Map<*, *>).isEmpty())
					c6CancelledRequest?.let { retired ->
						assertFalse(retired.javaClass.task7Field("confirmationPending").getBoolean(retired))
						assertEquals(c6CancelledCommands, initialLiveCommands.count { it.request === retired })
					}
					println("TASK336_C6 boundary=$c6Boundary variant=$c6NativeBoundary teardownRetired=true " +
						"canonicalFailures=$c6CanonicalFailures canonicalContinuations=$c6CanonicalContinuations " +
						"decklessBeforeNative=$c6DecklessCommandsBeforeNative " + initialLiveAccounting())
				}
				println("TASK336_GATE10 initial-live teardown " + initialLiveAccounting())
				webView.onInitialLiveCommand = null
				webView.onInitialLiveReceiptRequested = null
				webView.onPassiveManifestRequested = null
				initialLiveCommands.clear()
				initialLiveReceipts.clear()
				sourceLiveReceipts.clear()
				webView.sourceBinding = null
				webView.planCallbacks.clear()
				Task8ImmediateGlSurfaceViewShadow.afterNextQueueEvent = null
				Task336ViewTreeLifecycleOwnerShadow.clear()
				Dispatchers.resetMain()
				progress.close()
				if (bodyFailure == null) cleanupFailure?.let { throw it }
			}
			runSequenceCleanup()
		}
	}

	@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
	private fun task9ActualViewerRecoveryPublication(
		recoveryObservation: Boolean,
		targetOrdinal: Int,
		nativePipeline: Boolean = false,
		projectionOnly: Boolean = false,
		failRetainedSelected: Boolean = false,
		retainedPauseInvalid: Boolean? = null,
		livenessBoundary: Task9LivenessBoundary? = null,
		coverNullDeckRetry: Boolean = false,
		resizeAfterRetryCancel: Boolean = false
	) = runTest {
		val androidMainThread = Thread.currentThread()
		assertSame(android.os.Looper.getMainLooper().thread, androidMainThread)
		val queuedMain = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
		Dispatchers.setMain(object : kotlinx.coroutines.MainCoroutineDispatcher() {
			override val immediate get() = this
			override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext) =
				Thread.currentThread() !== androidMainThread
			override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) =
				queuedMain.dispatch(context, block)
		})
		try {
			val sourceSession = "critical-4-session"
			val initialOrdinal = 1
			val activity = Robolectric.buildActivity(Activity::class.java).setup()
			val (viewerClass, viewer) = task7Viewer(activity.get())
			val webView = Task9RecoveryCommandWebView(activity.get())
			activity.get().setContentView(viewer)
			viewerClass.task7Method("replaceViewerContent", View::class.java).invoke(viewer, webView)
			val requestedWidth = if (nativePipeline) 800 else 1200
			val requestedHeight = if (nativePipeline) 1200 else 800
			viewer.measure(View.MeasureSpec.makeMeasureSpec(requestedWidth, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(requestedHeight, View.MeasureSpec.EXACTLY))
			viewer.layout(0, 0, requestedWidth, requestedHeight)
			val controller = viewerClass.task7Field("playLikeCurlController").get(viewer) as ReaderPlayLikeCurlFoliateController
			val preparation = viewerClass.task7Field("pageRasterPreparationController").get(viewer) as ReaderPageRasterPreparationController
			val reporter = viewerClass.task7Field("presentationBindingReporter").get(viewer) as ReaderPresentationBindingReporter
			val runtime = Task9ControlledPassiveRuntime()
			val rendererReleaseCounts = mutableMapOf<Long, Int>()
			val rendererReleaseReasons = mutableMapOf<Long, karacken.curl.DeckReleaseReason>()
			val producedRendererGenerations = mutableListOf<Long>()
			val selectedFailureOrder = mutableListOf<String>()
			val selectedFailureOwned = mutableListOf<Boolean>()
			if (nativePipeline) {
				// Observe leases before surface initialization: replacing a listener later
				// replays cached capabilities and would introduce an extra refresh edge.
				assertNull(controller.surfaceView.renderCapabilities)
				val originalListener = controller.surfaceView.pageSurfaceListener
				controller.surfaceView.pageSurfaceListener = task8ForwardingPageSurfaceListener(
					delegate = originalListener,
					onRenderFailure = { failure ->
						if (retainedPauseInvalid != null && failure.generationId == 301L) {
							selectedFailureOrder += "failure"
							selectedFailureOwned += task8SurfaceOwnsGeneration(controller.surfaceView, 301L)
						}
						originalListener.onRenderFailure(failure)
					},
					onDeckReleased = { generationId, reason ->
						rendererReleaseCounts[generationId] = rendererReleaseCounts.getOrDefault(generationId, 0) + 1
						rendererReleaseReasons[generationId] = reason
						if (retainedPauseInvalid != null && generationId == 301L) {
							selectedFailureOrder += "terminal"
							assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
							val renderer = controller.surfaceView.javaClass.task7Field("renderer").get(controller.surfaceView)
							for (slot in listOf("activeDeck", "replacementDeck")) {
								assertFalse((renderer.javaClass.task7Field(slot).get(renderer) as PageDeck<*>?)?.generationId == 301L)
							}
						}
						originalListener.onDeckReleased(generationId, reason)
					}
				)
			}
			var donor: Task8CurlAuthorityFixture? = null
			var sustainedBridge: ReaderPresentationHostBridge? = null
			val deadlines = HostBridgeDeadlineScheduler()
			var nowMillis = 0L
			var deadlineDelayMillis = 0L
			val timedScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					deadlineDelayMillis = delayMillis
					return deadlines.postDelayed(action, delayMillis)
				}
				override fun removeCallbacks(action: Runnable) = deadlines.removeCallbacks(action)
			}
			var bodyFailure: Throwable? = null
			val bundle = controller.javaClass.task7Field("bundleSource").get(controller) as ReaderPageTurnBundleSource
			val rasterProofReady = kotlinx.coroutines.CompletableDeferred<Long>()
			@Suppress("UNCHECKED_CAST")
			val originalRasterProofReady = preparation.javaClass.task7Field("onRasterProofReady")
				.get(preparation) as (Long) -> Unit
			preparation.javaClass.task7Field("onRasterProofReady").set(preparation, { generation: Long ->
				originalRasterProofReady(generation)
				rasterProofReady.complete(generation)
			})
			try {
			// Seed only pre-publication opaque inputs. Complete receiver startup before
			// constructing a predecessor from its actual settled viewport identity.
			viewerClass.task7Field("presentationPublicationGeneration").setLong(viewer, 0L)
			viewerClass.task7Field("presentationViewportGeneration").setLong(viewer, 2L)
			viewerClass.task7Field("pageTurnFoliateSessionId").set(viewer, sourceSession)
			viewerClass.task7Field("pageTurnVisualPageIndex").set(viewer, initialOrdinal)
			viewerClass.task7Method("setPageTurnCanvasEnabled", java.lang.Boolean.TYPE, Function0::class.java)
				.invoke(viewer, true, { })
			assertTrue(viewerClass.task7Field("pageTurnCanvasEnabled").getBoolean(viewer),
				"The actual viewer fixture must admit canvas mode")
			if (nativePipeline) {
				// Admit the real empty-renderer ownership baseline before importing
				// the already-presented predecessor or any synthetic cache entries.
				task8PrepareSurface(controller.surfaceView)
				controller.surfaceView.surfaceCreated(controller.surfaceView.holder)
				ShadowLooper.runUiThreadTasks()
				assertTrue(viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer),
					"Actual empty-renderer cold baseline must be admitted before predecessor installation")
			}
			controller.setFoliateSessionId(sourceSession)
			val visualLocation = viewerClass.task7Method("setPageTurnVisualLocation", Integer::class.java,
				String::class.java, String::class.java, ReaderPageTurnSettlementAck::class.java)
			visualLocation.invoke(viewer, initialOrdinal, "initial-source", sourceSession, null)
			assertEquals(initialOrdinal, viewerClass.task7Field("pageTurnVisualPageIndex").get(viewer))
			assertEquals(initialOrdinal, controller.javaClass.task7Field("currentOrdinal").getInt(controller),
				"Receiving controller must agree with initial source before predecessor installation")
			val viewportWidth = viewer.width
			val viewportHeight = viewer.height
			if (nativePipeline) {
				webView.measure(View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
					View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY))
				webView.layout(0, 0, viewportWidth, viewportHeight)
				assertEquals(viewportWidth, webView.width, "Source WebView must match settled receiver width")
				assertEquals(viewportHeight, webView.height, "Source WebView must match settled receiver height")
				// The synthetic worker captures the full frame at window origin, outside
				// Activity chrome. Align the real host before constructing either proof.
				val viewerOrigin = IntArray(2).also(viewer::getLocationInWindow)
				viewer.translationX = -viewerOrigin[0].toFloat()
				viewer.translationY = -viewerOrigin[1].toFloat()
			}
			val geometry = ReaderPassiveRasterGeometry(webView.width, webView.height,
				0, 0, webView.width, webView.height)
			val adapter = ReaderPassiveRasterPreparationAdapter(
				ReaderPassiveRasterPrototypeSession(runtime) { bitmap ->
					if (!bitmap.isRecycled) bitmap.recycle()
				}, ReaderPageLivePassiveRasterManifestPort { webView }, bundle,
				viewerClass.task7Field("passiveRasterCaptureEpoch").getLong(viewer))
			// Install one physical worker at the existing port boundary. The controller's
			// production provider reads this very adapter; there is no second fake port.
			viewerClass.task7Field("passiveRasterPreparationAdapter").set(viewer, adapter)
			viewerClass.task7Field("passiveRasterPreparationGeometry").set(viewer, geometry)
			@Suppress("UNCHECKED_CAST")
			val actualPortProvider = preparation.javaClass.task7Field("passiveRasterPreparationPortProvider")
				.get(preparation) as () -> ReaderPassiveRasterPreparationPort?
			assertSame(adapter, actualPortProvider())
			val viewportGeneration = viewerClass.task7Field("presentationViewportGeneration").getLong(viewer)
			val origin = IntArray(2).also(controller.surfaceView::getLocationInWindow)
			val predecessorRect = if (nativePipeline) ReaderPlayLikeCurlPhysicalRect(
				origin[0], origin[1], origin[0] + viewportWidth, origin[1] + viewportHeight
			) else ReaderPlayLikeCurlPhysicalRect(0, 0, 2, 2)
			val sourceRasterGeneration = bundle.currentGeneration()
			val source = task8CurlAuthorityFixture(
				task8SettledCurlSourceState(viewportGeneration, viewportWidth, viewportHeight,
					rasterGeneration = sourceRasterGeneration),
				sourceIdentity = if (nativePipeline) "#${Int.MIN_VALUE}" else sourceSession,
				viewportWidth = viewportWidth, viewportHeight = viewportHeight, physicalRect = predecessorRect,
				bundleSource = bundle
			).also { donor = it }
			val initialState = assertNotNull(source.store.state)
			val binding = assertNotNull(initialState.binding)
			val predecessorProof = assertIs<ReaderPresentationAuthority.SettledNativePage>(initialState.authority).frame.proof
			assertEquals(viewportGeneration, predecessorProof.binding.viewportGeneration)
			assertEquals(viewportWidth, predecessorProof.viewportWidth)
			assertEquals(viewportHeight, predecessorProof.viewportHeight)
			var common = ReaderController(ReaderControllerState(readerSessionGeneration = 1L, presentation = initialState))
			webView.sourceBinding = {
				assertSame(androidMainThread, Thread.currentThread())
				assertSame(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
				assertNotNull(common.state.presentation.binding)
			}
			val retainedProjectionCallers = mutableListOf<String>()
			if (projectionOnly) {
				@Suppress("UNCHECKED_CAST")
				val originalReadiness = controller.javaClass.task7Field("onReadinessStateChange")
					.get(controller) as (ReaderPageRendererReadinessState) -> Unit
				controller.javaClass.task7Field("onReadinessStateChange").set(controller,
					{ state: ReaderPageRendererReadinessState ->
						originalReadiness(state)
						if (readerPresentationDecision(common.state.presentation).frameOwner is ReaderPresentationFrameOwner.Curl) {
							val callers = Throwable().stackTrace.filter { it.className == controller.javaClass.name }
								.map { it.methodName }.filter { it in setOf("invalidate", "synchronizeVisualPageIndex", "cancelGesture", "refreshPreparedDeck") }
							if (callers.isNotEmpty()) retainedProjectionCallers += "${callers.joinToString("/")} alpha=${controller.surfaceView.alpha}"
						}
					})
			}
			val events = mutableListOf<ReaderPresentationEvent>()
			val coverRetryEffects = paige.navic.reader.ReaderPresentationEffectQueue()
			val resizeReceipts = mutableListOf<ReaderPresentationEventReceipt>()
			val publish: (ReaderPresentationEvent) -> paige.navic.reader.ReaderPresentationEventReceipt? = { event ->
				events += event
				if (retainedPauseInvalid != null) {
					if (event is ReaderPresentationEvent.Lifecycle && event.event == ReaderPresentationLifecycleEvent.RendererLost)
						selectedFailureOrder += "renderer-lost"
					if (event is ReaderPresentationEvent.BindingReplaced && event.previousBinding == binding &&
						event.binding == binding.copy(rasterGeneration = null, textureGeneration = null))
						selectedFailureOrder += "proof-removed"
				}
				val step = common.onPresentationEvent(event)
				common = step.controller
				if (coverNullDeckRetry) coverRetryEffects.retain(step.presentationEffects)
				if (resizeAfterRetryCancel) step.presentationReceipt?.let(resizeReceipts::add)
				step.presentationReceipt
			}
			val apply = viewerClass.task7Method("applyPresentationDecision",
				paige.navic.reader.ReaderPresentationDecision::class.java, ReaderRendererLossCancellationIdentity::class.java)
			val applyFrame = viewerClass.task7Method("applyPresentationFrameOwner", ReaderPresentationDecision::class.java)
			val commitHost = object : ReaderPresentationCommitHost {
				override val isAttachedToWindow get() = viewer.isAttachedToWindow
				override val currentPresentationBinding get() =
					viewerClass.task7Method("currentPresentationBinding").invoke(viewer) as ReaderPresentationBinding?
				override val currentShellCoverGeneration: Long? get() = null
				override val shellCoverSelected get() = false
				override val measuredViewportWidth get() = viewer.width
				override val measuredViewportHeight get() = viewer.height
				override fun prepareOpaqueShellCover(coverGeneration: Long) = error("No shell transition on retained Curl route")
				override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = error("No shell preparation to cancel")
				override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = error("No shell preparation to complete")
				override fun registerShellCoverDrawListener(onDraw: () -> Unit): ReaderPresentationDrawRegistration =
					error("No shell draw on retained Curl route")
				override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = error("No shell animation on retained Curl route")
				override fun applyPresentationFrameOwner(decision: ReaderPresentationDecision) {
					applyFrame.invoke(viewer, decision)
				}
			}
			sustainedBridge = ReaderPresentationHostBridge(commitHost,
				transitionTimeoutScheduler = timedScheduler, transitionNowMillis = { nowMillis }) { event ->
				viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
					.invoke(viewer, event) as ReaderPresentationEventReceipt?
			}
			val onEffect: (ReaderPresentationHostEffect) -> Unit = { effect ->
				apply.invoke(viewer, effect.decision, effect.rendererLossCancellationIdentity)
				sustainedBridge?.update(effect.decision)
			}
			val setDecision = viewerClass.task7Method("setPresentationDecision",
				paige.navic.reader.ReaderPresentationDecision::class.java, ReaderPresentationState::class.java,
				paige.navic.reader.ReaderPresentationReceiptVersion::class.java, java.lang.Boolean.TYPE,
				ReaderDestinationCommitIdentity::class.java, Function1::class.java, Function1::class.java)
			fun updateDestination(destination: ReaderDestinationCommitIdentity?) {
				setDecision.invoke(viewer, readerPresentationDecision(common.state.presentation), common.state.presentation,
					common.presentationVersion, false, destination, publish, onEffect)
			}
			// Establish the already-presented source deck before the actual publication
			// bootstrap. A provisional publication here would create a different startup request.
			val requiresRecoveredAdmissionAuthority =
				nativePipeline || (!failRetainedSelected && retainedPauseInvalid == null)
			val sourceProfile = source.controller.javaClass.task7Field("requestedProfile").get(source.controller)
			for (profileController in listOf(source.controller, controller)) {
				profileController.javaClass.task7Field("nextRasterProfileEpoch").setLong(profileController, binding.profileGeneration)
				profileController.javaClass.task7Method("publishRasterProfileEpoch", ReaderPlayLikeCurlRasterProfile::class.java)
					.invoke(profileController, sourceProfile)
			}
			if (requiresRecoveredAdmissionAuthority) {
				controller.onHostResumedChanged(true)
				controller.onWebViewAttachmentChanged(true)
				viewerClass.task7Method("preparePresentationEpoch",
					paige.navic.reader.ReaderPresentationReceiptVersion::class.java, ReaderPresentationState::class.java,
					java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
					.invoke(viewer, common.presentationVersion, common.state.presentation, false, true, false)
				assertTrue(reporter.bindPublication(binding))
				viewerClass.task7Field("onPresentationEvent").set(viewer, publish)
				viewerClass.task7Field("onAuthoritativePresentationHostEffect").set(viewer, onEffect)
				val admissionEvent = ReaderPresentationEvent.NativePagePresented(predecessorProof)
				val admissionReceipt = assertNotNull(
					viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
						.invoke(viewer, admissionEvent) as ReaderPresentationEventReceipt?
				)
				assertTrue(admissionReceipt.authorizes(admissionEvent))
				val preAdmissionDecision = readerPresentationDecision(admissionReceipt.postState)
				assertEquals(binding, reporter.lastReportedBinding)
				assertEquals(reporter.lastReportedBinding, preAdmissionDecision.targetBinding)
				assertEquals(preAdmissionDecision,
					controller.javaClass.task7Field("commonPresentationDecision").get(controller))
				viewerClass.task7Field("composeDestinationCommitIdentity")
					.set(viewer, binding.destinationCommitIdentity)
				viewerClass.task7Field("presentationDestinationCommitIdentity")
					.set(viewer, binding.destinationCommitIdentity)
				// This fixture establishes the publication through the exact authoritative
				// predecessor receipt above rather than through an initial host-binding edge.
				viewerClass.task7Field("presentationPublicationOpenPending").setBoolean(viewer, false)
				viewerClass.task7Field("presentationRelocationPending").setBoolean(viewer, false)
			}
			var predecessorReleases = 0
			if (nativePipeline) {
				controller.javaClass.task7Field("requestedProfile").set(controller, sourceProfile)
				controller.surfaceView.layout(0, 0, viewportWidth, viewportHeight)
				assertEquals(predecessorProof.viewportWidth, controller.surfaceView.width)
				assertEquals(predecessorProof.viewportHeight, controller.surfaceView.height)
				assertEquals(binding.viewportGeneration,
					viewerClass.task7Field("presentationViewportGeneration").getLong(viewer))
				val importedReadinessField = controller.javaClass.task7Field("onReadinessStateChange")
				@Suppress("UNCHECKED_CAST")
				val importedReadiness = importedReadinessField.get(controller) as (ReaderPageRendererReadinessState) -> Unit
				val importedPreparedField = controller.javaClass.task7Field("onPreparedActiveDeckChanged")
				@Suppress("UNCHECKED_CAST")
				val importedPrepared = importedPreparedField.get(controller) as (ReaderPagePreparedActiveDeck?) -> Unit
				var bufferedImportedPrepared: ReaderPagePreparedActiveDeck? = null
				if (requiresRecoveredAdmissionAuthority) {
					importedReadinessField.set(controller, { _: ReaderPageRendererReadinessState -> })
					importedPreparedField.set(controller, { deck: ReaderPagePreparedActiveDeck? ->
						bufferedImportedPrepared = deck
					})
				}
				task8SubmitRecoveredDeck(controller, 301L, ReaderDeckSubmissionRole.Active,
					sourceProfile as ReaderPlayLikeCurlRasterProfile, { predecessorReleases += 1 }, true,
					physicalRect = predecessorRect)
				@Suppress("UNCHECKED_CAST")
				val importedAdmissions = controller.javaClass.task7Field("generationAdmissions")
					.get(controller) as Map<Long, ReaderDeckAdmission>
				val importedAdmission = assertNotNull(importedAdmissions[301L])
				val importedCapability = importedAdmission.capability
				val currentAuthorityState = assertNotNull(
					reporter.javaClass.task7Field("authoritativeState").get(reporter) as ReaderPresentationState?
				)
				val currentAuthorityBinding = assertNotNull(reporter.lastReportedBinding)
				val normalizedCurrentAuthorityBinding = assertNotNull(readerAcceptedDeckBindingOrNull(
					currentAuthorityBinding,
					importedCapability.profileGeneration,
					importedCapability.preparationGeneration,
					importedCapability.rasterGeneration,
					importedCapability.textureGeneration
				))
				assertEquals(importedCapability.originBinding, normalizedCurrentAuthorityBinding)
				assertEquals(importedCapability.originDecisionIdentity,
					readerDeckAdmissionDecisionIdentityOrNull(
						readerPresentationDecision(currentAuthorityState),
						normalizedCurrentAuthorityBinding
					))
				assertTrue(reporter.isCurrent(importedCapability))
				assertTrue(controller.javaClass.task7Field("hostResumed").getBoolean(controller))
				assertTrue(controller.javaClass.task7Field("enabled").getBoolean(controller))
				assertTrue(controller.javaClass.task7Field("attached").getBoolean(controller))
				controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
					.invoke(controller.surfaceView, 301L)
				assertTrue(reporter.isOwnerCurrent(importedAdmission.capability))
				assertEquals(ReaderDeckAdmissionState.AdmittedRendererOwned, importedAdmission.state)
				if (requiresRecoveredAdmissionAuthority) {
					importedReadinessField.set(controller, importedReadiness)
					importedPreparedField.set(controller, importedPrepared)
					importedPrepared(assertNotNull(bufferedImportedPrepared))
					importedReadiness(
						controller.javaClass.task7Field("readinessState").get(controller) as ReaderPageRendererReadinessState
					)
				}
				assertTrue(301L in (controller.javaClass.task7Field("preparedDeckGenerations").get(controller) as Set<*>))
				controller.javaClass.task7Field("nextDeckGeneration").setLong(controller, 302L)
				assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
			} else {
				source.preparedDeckRelay.sink = { deck ->
					viewerClass.task7Method("onPreparedActiveDeckChanged", ReaderPagePreparedActiveDeck::class.java)
						.invoke(viewer, deck)
				}
				source.controller.javaClass.task7Method("publishPreparedActiveDeck", Int::class.javaPrimitiveType!!)
					.invoke(source.controller, source.controller.javaClass.task7Field("currentOrdinal").getInt(source.controller))
			}
			if (requiresRecoveredAdmissionAuthority) {
				viewerClass.task7Field("presentationDestinationCommitIdentity").set(viewer, null)
			}
			if (!requiresRecoveredAdmissionAuthority) {
				viewerClass.task7Method("preparePresentationEpoch",
					paige.navic.reader.ReaderPresentationReceiptVersion::class.java, ReaderPresentationState::class.java,
					java.lang.Boolean.TYPE, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)
					.invoke(viewer, common.presentationVersion, common.state.presentation, false, true, false)
				assertTrue(reporter.bindPublication(binding))
			}
			updateDestination(binding.destinationCommitIdentity)
			if (requiresRecoveredAdmissionAuthority) {
				assertTrue(viewerClass.task7Field("presentationRelocationPending").getBoolean(viewer))
			}
			assertEquals(binding, reporter.lastReportedBinding)
				assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority)
				val dispatchEvent = viewerClass.task7Method("dispatchPresentationEvent", ReaderPresentationEvent::class.java)
				fun deliver(event: ReaderPresentationEvent) = assertNotNull(
					dispatchEvent.invoke(viewer, event) as ReaderPresentationEventReceipt?)
				if (coverNullDeckRetry) {
					// The committed cover is this focused ingress test's precondition, not
					// a claim to exercise physical cover drawing or the full Task10 sequence.
					common = common.onPresentationEvent(ReaderPresentationEvent.ShellCoverRequested(37L)).controller
					val commit = assertIs<ReaderRequiredTransition.CommitShellCover>(
						common.state.presentationDecision.requiredTransition)
					val coverProof = ReaderShellCoverCommitProof(commit.token, commit.binding,
						commit.coverGeneration, 12L, viewportWidth, viewportHeight)
					common = common.onPresentationEvent(ReaderPresentationEvent.ShellCoverCommitted(coverProof)).controller
					updateDestination(binding.destinationCommitIdentity)
					val cover = ReaderPresentationFrameOwner.ShellCover(coverProof)
					assertEquals(cover, common.state.presentationDecision.frameOwner)
					fun physicalBinding() = assertNotNull(viewerClass.task7Method("currentPresentationBinding")
						.invoke(viewer) as ReaderPresentationBinding?)
					assertEquals(binding, physicalBinding())
					val predecessorObservation = assertNotNull(viewerClass.task7Field("preparedActiveDeck").get(viewer))
					val beforeSubmission = events.size
					// Reuse the established synthetic material port, but submit through
					// production ownership/fence capture and actual prepared-deck publication.
					task8SubmitRecoveredDeck(controller, 302L, ReaderDeckSubmissionRole.Active,
						sourceProfile as ReaderPlayLikeCurlRasterProfile, {}, true, predecessorRect)
					producedRendererGenerations += 302L
					controller.javaClass.task7Field("nextDeckGeneration").setLong(controller, 303L)
					@Suppress("UNCHECKED_CAST")
					val admissions = controller.javaClass.task7Field("generationAdmissions").get(controller)
						as Map<Long, ReaderDeckAdmission>
					val oldAdmission = assertNotNull(admissions[302L])
					assertNull(oldAdmission.capability.presentationToken)
					assertEquals(binding.copy(textureGeneration = 302L), oldAdmission.capability.originBinding)
					assertNull(viewerClass.task7Field("preparedActiveDeck").get(viewer))
					val partial = binding.copy(rasterGeneration = null, textureGeneration = null)
					assertEquals(partial, physicalBinding())
					assertEquals(binding, reporter.lastReportedBinding)
					assertEquals(binding, common.state.presentation.binding)
					assertTrue(events.drop(beforeSubmission).none { it is ReaderPresentationEvent.BindingReplaced },
						"Actual complete-to-partial observation must emit no binding replacement")
					val entry = deliver(ReaderPresentationEvent.ShellCoverDismissalRequested)
					val entryToken = assertNotNull(readerPresentationDecision(entry.postState).pendingTransitionToken)
					assertSame(oldAdmission, admissions[302L], "Entry must not replace the already issued null-token admission")
					controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, 302L)
					ShadowLooper.runUiThreadTasks()
					assertNull(viewerClass.task7Field("preparedActiveDeck").get(viewer))
					assertEquals(1, rendererReleaseCounts[302L])
					assertEquals(cover, common.state.presentationDecision.frameOwner)
					assertNull(viewerClass.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer))
					assertTrue(deadlines.hasPending)
					nowMillis += deadlineDelayMillis
					deadlines.runPending()
					val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(
						common.state.presentationDecision.diagnosticPresentation)
					assertEquals(ReaderPresentationFailureReason.TimedOut, diagnostic.reason)
					assertTrue(diagnostic.retryable && diagnostic.cancellable)
					assertFalse(deadlines.hasPending)
					val retry = deliver(ReaderPresentationEvent.Retry)
					val retryEffect = assertIs<ReaderPresentationEffect.RetryPreparation>(retry.effects.single())
					assertTrue(assertNotNull(retryEffect.token).value > entryToken.value)
					assertEquals(binding, retryEffect.binding)
					assertEquals(partial, physicalBinding(), "Retry must inspect physical null-deck truth, not common target")
					val generationBeforeRetry = preparation.javaClass.task7Field("preparationGeneration").getLong(preparation)
					val retryMethod = viewerClass.task7Method("retryPreparation", ReaderPresentationEffect.RetryPreparation::class.java)
					val decisionField = viewerClass.task7Field("presentationDecision")
					val authorized = common.state.presentationDecision
					val pending = assertIs<ReaderPresentationAuthority.BlockingPreparation>(authorized.authority)
					val request = assertNotNull(pending.nativePresentationRequest)
					val rendererRequestBeforeRetry = controller.javaClass.task7Field("requestGeneration").getLong(controller)
					fun assertIgnoredRetry(label: String, decision: ReaderPresentationDecision,
						effect: ReaderPresentationEffect.RetryPreparation = retryEffect) {
						// Fault-injected receiving-port controls, not alternate source traces.
						// The actual physical snapshot still comes from the viewer's factory.
						decisionField.set(viewer, decision)
						try {
							assertEquals(true, retryMethod.invoke(viewer, effect), label)
							assertEquals(generationBeforeRetry,
								preparation.javaClass.task7Field("preparationGeneration").getLong(preparation), label)
							assertEquals(rendererRequestBeforeRetry,
								controller.javaClass.task7Field("requestGeneration").getLong(controller), label)
						} finally { decisionField.set(viewer, authorized) }
					}
					for ((label, decision) in listOf(
						"background" to authorized.copy(lifecycle = ReaderPresentationLifecycleState.Background),
						"terminal" to authorized.copy(lifecycle = ReaderPresentationLifecycleState.Destroyed),
						"failure" to authorized.copy(diagnosticPresentation = diagnostic),
						"wrong-target" to authorized.copy(targetBinding = partial),
						"wrong-request" to authorized.copy(authority = pending.copy(
							nativePresentationRequest = request.copy(binding = partial))),
						"wrong-floor" to authorized.copy(authority = pending.copy(
							nativePresentationRequest = request.copy(retryAfterPreparationGeneration = generationBeforeRetry + 1L))),
						"absent-floor" to authorized.copy(authority = pending.copy(
							nativePresentationRequest = request.copy(retryAfterPreparationGeneration = null))),
						"non-cover" to authorized.copy(authority = pending.copy(retainedFrame = ReaderPresentationFrameOwner.Neutral))
					)) assertIgnoredRetry(label, decision)
					assertIgnoredRetry("stale-token", authorized, retryEffect.copy(token = entryToken))
					assertIgnoredRetry("absent-token", authorized, retryEffect.copy(token = null))
					for ((label, effectBinding) in listOf(
						"one-null-raster" to binding.copy(rasterGeneration = null),
						"one-null-texture" to binding.copy(textureGeneration = null),
						"session" to binding.copy(foliateSessionId = "task10-other-session",
							destinationCommitIdentity = binding.destinationCommitIdentity?.copy(foliateSessionId = "task10-other-session")),
						"publication" to binding.copy(publicationGeneration = binding.publicationGeneration + 1L),
						"viewport" to binding.copy(viewportGeneration = binding.viewportGeneration + 1L),
						"profile" to binding.copy(profileGeneration = binding.profileGeneration + 1L),
						"destination" to binding.copy(destinationCommitIdentity = binding.destinationCommitIdentity?.copy(commitSequence = 2L)),
						"preparation" to binding.copy(preparationGeneration = generationBeforeRetry + 1L),
						"absent-preparation" to binding.copy(preparationGeneration = null)
					)) {
						val effect = retryEffect.copy(binding = effectBinding)
						assertIgnoredRetry(label, authorized.copy(targetBinding = effectBinding,
							authority = pending.copy(nativePresentationRequest = request.copy(binding = effectBinding,
								retryAfterPreparationGeneration = effectBinding.preparationGeneration))), effect)
					}
					val observedVisibility = viewerClass.task7Field("lastPresentationWindowVisible")
					val originalVisibility = observedVisibility.get(viewer)
					observedVisibility.set(viewer, false)
					try {
						assertNull(viewerClass.task7Method("currentPresentationBinding").invoke(viewer))
						assertIgnoredRetry("absent-physical", authorized)
					} finally { observedVisibility.set(viewer, originalVisibility) }
					viewerClass.task7Field("preparedActiveDeck").set(viewer, predecessorObservation)
					try {
						assertEquals(binding, physicalBinding())
						val differentRenderer = binding.copy(textureGeneration = 302L)
						assertIgnoredRetry("different-complete-renderer", authorized.copy(targetBinding = differentRenderer,
							authority = pending.copy(nativePresentationRequest = request.copy(binding = differentRenderer))),
							retryEffect.copy(binding = differentRenderer))
					} finally { viewerClass.task7Field("preparedActiveDeck").set(viewer, null) }
					// Supply only synthetic source inputs at the same real cache/worker ports
					// as Task9. Ready, renderer submission and proof remain production outputs.
					webView.retainPlanCallbacks = true
					val quality = bundle.javaClass.task7Field("bitmapQuality").get(bundle) as ReaderPageBitmapQuality
					val bitmapWidth = readerPageTurnAnimationBitmapDimension(viewportWidth, quality)
					val bitmapHeight = readerPageTurnAnimationBitmapDimension(viewportHeight, quality)
					assertNotNull(bundle.cacheCurrentSnapshot(initialOrdinal, ReaderPageTurnTransitionKind.PortraitSlide,
						ReaderPageTurnCaptureResult(
							Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) },
							android.graphics.Rect(0, 0, viewportWidth, viewportHeight),
							paige.navic.reader.ReaderPageTurnCaptureGeometry(viewportWidth.toDouble(), viewportHeight.toDouble(),
								paige.navic.reader.ReaderPageTurnLayoutMode.Single,
								listOf(paige.navic.reader.ReaderPageTurnPageRect(paige.navic.reader.ReaderPageTurnPageRole.Full,
									0.0, 0.0, viewportWidth.toDouble(), viewportHeight.toDouble()))), 1L)))
					val reference = assertNotNull(bundle.retainedCurrentLayoutSnapshot(initialOrdinal,
						ReaderPageTurnTransitionKind.PortraitSlide))
					try {
						val hydrated = kotlinx.coroutines.CompletableDeferred<ReaderPageSlideSnapshot?>()
						val hydration = bundle.hydrateSnapshot(webView, initialOrdinal,
							ReaderPageTurnTransitionKind.PortraitSlide, reference, onHydrated = hydrated::complete)
						try { assertNotNull(hydrated.await()).release() } finally { hydration.cancel() }
					} finally { reference.release() }
					bundle.initializeRasterCache(webView)
					viewerClass.task7Method("setPageTurnPaginationStatus", String::class.java).invoke(viewer, "ready")
					controller.surfaceView.pageSurfaceListener.onCapabilitiesAvailable(RenderCapabilities(4096, 8L * 1024L * 1024L))
					val oldPlans = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					val planWitnesses = mutableListOf<String>()
					webView.onPlanRequested = { producer ->
						planWitnesses += "$producer request=${controller.javaClass.task7Field("requestGeneration").getLong(controller)} " +
							"active=${controller.javaClass.task7Field("activeDeckGenerationId").get(controller)} " +
							"authority=${common.state.presentation.authority::class.simpleName}"
					}
					val queue = coverRetryEffects
					var ingressCalls = 0
					val releaseIngress = mutableListOf<ReaderPresentationEffect.ReleaseStalePresentation>()
					val handler = ReaderPresentationEffectHandler(retryPreparation = { effect ->
						ingressCalls++
						viewerClass.task7Method("retryPreparation", ReaderPresentationEffect.RetryPreparation::class.java)
							.invoke(viewer, effect) as Boolean
					}, releaseStalePresentation = { effect ->
						releaseIngress += effect
						viewerClass.task7Method("releaseStalePresentation", ReaderPresentationEffect.ReleaseStalePresentation::class.java)
							.invoke(viewer, effect) as Boolean
					})
					val pendingEffects = queue.pendingEffects()
					preparation.javaClass.task7Field("retryPreparationInProgress").setBoolean(preparation, true)
					try {
						handler.deliver(pendingEffects, authorized) { queue.acknowledge(it) }
						assertEquals(pendingEffects, queue.pendingEffects(), "Real allocator refusal must remain pending")
						assertEquals(generationBeforeRetry,
							preparation.javaClass.task7Field("preparationGeneration").getLong(preparation))
					} finally { preparation.javaClass.task7Field("retryPreparationInProgress").setBoolean(preparation, false) }
					handler.deliver(queue.pendingEffects(), common.state.presentationDecision) { queue.acknowledge(it) }
					assertEquals(2, ingressCalls)
					assertTrue(queue.pendingEffects().isEmpty())
					handler.deliver(pendingEffects, common.state.presentationDecision) { queue.acknowledge(it) }
					assertEquals(2, ingressCalls, "Consumed effect replay must not allocate twice")
					assertEquals(generationBeforeRetry + 1L,
						preparation.javaClass.task7Field("preparationGeneration").getLong(preparation),
						"Acknowledged current cover Retry must reach the actual fresh preparation allocator despite a physical null deck")
					assertEquals(generationBeforeRetry + 1L,
						controller.javaClass.task7Field("preparationGeneration").getLong(controller))
					assertTrue(controller.javaClass.task7Field("requestGeneration").getLong(controller) > rendererRequestBeforeRetry)
					val capturedRetry = assertNotNull(viewerClass.task7Field("coverRetryBindingCorrelation").get(viewer))
					assertEquals(retryEffect, capturedRetry.javaClass.task7Field("effect").get(capturedRetry))
					assertEquals(reporter.captureEpoch(), capturedRetry.javaClass.task7Field("hostEpoch").getLong(capturedRetry))
					assertEquals(generationBeforeRetry + 1L,
						capturedRetry.javaClass.task7Field("preparationGeneration").getLong(capturedRetry))
					val physicalFactsField = viewerClass.task7Field("latestRasterPreparationState")
					val profileField = viewerClass.task7Field("rasterProfileEpoch")
					val originalFacts = physicalFactsField.get(viewer) as ReaderPagePreparationState
					val originalProfileEpoch = profileField.get(viewer)
					val beforeUnprovenFacts = common.state
					for ((generation, profile) in listOf(
						(generationBeforeRetry + 2L) to binding.profileGeneration,
						(generationBeforeRetry + 1L) to (binding.profileGeneration + 10L)
					)) {
						try {
							physicalFactsField.set(viewer, originalFacts.copy(preparationGeneration = generation))
							profileField.set(viewer, profile)
							viewerClass.task7Method("reportPresentationIdentityIfAvailable", ReaderPageTurnSettlementAck::class.java)
								.invoke(viewer, null)
							assertEquals(beforeUnprovenFacts, common.state,
								"Neither a different allocation nor an unobserved profile may borrow the current Retry attribution")
						} finally {
							physicalFactsField.set(viewer, originalFacts)
							profileField.set(viewer, originalProfileEpoch)
						}
					}
					val targets = readerPageRasterBlockingWindow(initialOrdinal, 1, 3).joinToString(",") { ordinal ->
						val priority = when (ordinal) {
							initialOrdinal -> "current"
							initialOrdinal + 1 -> "next-transition"
							initialOrdinal - 1 -> "previous-transition"
							else -> "current-chapter"
						}
						val authority = if (ordinal == initialOrdinal) "CurrentLive" else "OffscreenPassive"
						"""{"pageIndex":$ordinal,"priority":"$priority","authority":"$authority"}"""
					}
					val plan = """{"context":{"centerPageIndex":$initialOrdinal,"pageCount":3,"layoutMode":"single","step":1,"currentChapterIndex":0,"currentChapterPageStartIndex":0,"currentChapterPageCount":3},"targets":[$targets]}"""
					val parsedPlan = assertNotNull(readerPageRasterPreparationPlan(plan))
					val profileResolved = kotlinx.coroutines.CompletableDeferred<Unit>()
					@Suppress("UNCHECKED_CAST")
					val originalProfile = controller.javaClass.task7Field("onRasterProfileEpochChanged")
						.get(controller) as (Long?) -> Unit
					controller.javaClass.task7Field("onRasterProfileEpochChanged").set(controller, { epoch: Long? ->
						originalProfile(epoch)
						if (epoch != null) profileResolved.complete(Unit)
					})
					val retryPlans = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					assertNotNull(retryPlans.lastOrNull(), "Actual Retry must request the real source plan").onReceiveValue(plan)
					if (viewerClass.task7Field("rasterProfileEpoch").get(viewer) == null) {
						withContext(Dispatchers.Default) { withTimeout(5_000L) { profileResolved.await() } }
					}
					assertTrue(preparation.prewarmAdjacent(), "Actual Retry must enter the real raster worker")
					val workerPlans = webView.planCallbacks.toList()
					assertNotNull(workerPlans.lastOrNull())
					webView.planCallbacks.clear()
					workerPlans.forEach { it.onReceiveValue(plan) }
					for (ordinal in parsedPlan.targets.filter { it.authority == ReaderPageRasterTargetAuthority.OffscreenPassive }
						.map { it.pageIndex }) {
						val work = withContext(Dispatchers.Default) { withTimeout(5_000L) { runtime.commits.receive() } }
						assertEquals(ordinal, work.manifest.visualPageOrdinal)
						val physicalFacts = viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState
						assertEquals(ReaderPagePreparationPhase.Preparing, physicalFacts.phase)
						assertEquals(generationBeforeRetry + 1L, physicalFacts.preparationGeneration)
						val physical = physicalBinding()
						assertNull(physical.rasterGeneration)
						assertNull(physical.textureGeneration)
						assertEquals(physicalFacts.preparationGeneration, physical.preparationGeneration)
						assertEquals(physical, reporter.lastReportedBinding)
						assertEquals(physical, common.state.presentation.binding)
						val attributed = events.filterIsInstance<ReaderPresentationEvent.BindingReplaced>()
							.single { it.retryAttribution != null }
						assertEquals(binding, attributed.previousBinding)
						assertEquals(physical, attributed.binding)
						assertEquals(retryEffect.token, attributed.retryAttribution?.token)
						assertEquals(generationBeforeRetry + 1L, attributed.retryAttribution?.preparationGeneration)
						assertEquals(physical.profileGeneration, attributed.retryAttribution?.profileGeneration)
						assertTrue(events.filterIsInstance<ReaderPresentationEvent.PreparationReported>().any {
							it.binding == physical && it.facts.phase == ReaderPagePreparationPhase.Idle
						}, "Actual profile callback Idle must be admitted before the worker's Preparing facts")
						assertEquals(ReaderPagePreparationPhase.Preparing, common.state.presentation.preparationFacts.phase,
							"Actual worker Preparing must reach common facts: physicalPreparation=${physical.preparationGeneration} " +
								"physicalProfile=${physical.profileGeneration} reportedPreparation=${reporter.lastReportedBinding?.preparationGeneration} " +
								"reportedProfile=${reporter.lastReportedBinding?.profileGeneration} " +
								"commonProgress=${common.state.presentationDecision.preparationPresentation::class.simpleName}")
						assertIs<ReaderPreparationPresentation.Blocking>(common.state.presentationDecision.preparationPresentation)
						assertEquals(cover, common.state.presentationDecision.frameOwner)
						if (resizeAfterRetryCancel) {
							val cleanupBeforeCancel = common.state.presentation.rendererCleanupOwnership
							val cancelled = deliver(ReaderPresentationEvent.Cancel)
							assertEquals(ReaderPresentationEventDisposition.Accepted, cancelled.disposition)
							assertEquals(coverProof, assertIs<ReaderPresentationAuthority.ShellCover>(cancelled.postState.authority).proof)
							assertEquals(physical, cancelled.postState.binding)
							assertEquals(cleanupBeforeCancel, cancelled.postState.rendererCleanupOwnership)
							assertNull(common.state.presentationDecision.pendingTransitionToken)
							assertFalse(deadlines.hasPending)
							val beforeResize = resizeReceipts.size
							val resizedWidth = viewer.width + 40
							val resizedHeight = viewer.height
							viewer.measure(View.MeasureSpec.makeMeasureSpec(resizedWidth, View.MeasureSpec.EXACTLY),
								View.MeasureSpec.makeMeasureSpec(resizedHeight, View.MeasureSpec.EXACTLY))
							viewer.layout(0, 0, resizedWidth, resizedHeight)
							val resizedPartial = physical.copy(viewportGeneration = physical.viewportGeneration + 1L)
							val viewportReceipt = resizeReceipts.drop(beforeResize).first {
								it.event == ReaderPresentationEvent.BindingReplaced(physical, resizedPartial)
							}
							assertEquals(ReaderPresentationEventDisposition.Accepted, viewportReceipt.disposition,
								"Actual onSizeChanged must acknowledge its partial viewport before subsequent invalidation callbacks")
							assertEquals(resizedPartial, viewportReceipt.postState.binding)
							assertEquals(ReaderPresentationAuthority.Unavailable, viewportReceipt.postState.authority)
							assertTrue(viewportReceipt.postState.rendererCleanupOwnership.isEmpty())
							assertEquals(cleanupBeforeCancel.map { ReaderPresentationEffect.ReleaseStalePresentation(it.token, it.binding) },
								viewportReceipt.effects)
							assertTrue(runtime.isRetired, "Full resize must retire the old passive worker")
							assertEquals(0, runtime.pendingCallbackCount)
							assertNull(common.state.presentationDecision.pendingTransitionToken)
							assertFalse(deadlines.hasPending)
							handler.deliver(queue.pendingEffects(), common.state.presentationDecision) { queue.acknowledge(it) }
							ShadowLooper.runUiThreadTasks()
							assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
							assertEquals(1, rendererReleaseCounts[301L])
							// Supply source/material only at existing test ports. Resize, the
							// callback fence and completion receipt remain production outputs.
							// Source planning follows the later stable-content callback, not
							// the synchronous onSizeChanged invalidation itself.
							controller.onHostContentReady()
							val resizedPlans = webView.planCallbacks.toList()
							assertTrue(resizedPlans.isNotEmpty())
							resizedPlans.forEach { callback ->
								assertTrue(webView.planCallbacks.remove(callback))
								callback.onReceiveValue(plan)
							}
							val resizedProfile = assertNotNull(controller.javaClass.task7Field("requestedProfile")
								.get(controller) as ReaderPlayLikeCurlRasterProfile?)
							val beforeCompletion = physicalBinding()
							// Draining Activity layout may deliver another real size callback.
							// Every viewport receipt must advance; completion uses the exact latest one.
							val viewportReceipts = resizeReceipts.drop(beforeResize).filter {
								val event = it.event
								event is ReaderPresentationEvent.BindingReplaced &&
									event.binding.viewportGeneration != event.previousBinding.viewportGeneration
							}
							var previousViewport = physical.viewportGeneration
							viewportReceipts.forEach { receipt ->
								val event = assertIs<ReaderPresentationEvent.BindingReplaced>(receipt.event)
								assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition)
								assertEquals(previousViewport, event.previousBinding.viewportGeneration)
								assertEquals(previousViewport + 1L, event.binding.viewportGeneration)
								previousViewport = event.binding.viewportGeneration
							}
							assertEquals(previousViewport, beforeCompletion.viewportGeneration)
							assertNull(beforeCompletion.rasterGeneration)
							assertNull(beforeCompletion.textureGeneration)
							assertEquals(beforeCompletion, reporter.lastReportedBinding)
							assertEquals(beforeCompletion, common.state.presentation.binding)
							val resizedOrigin = IntArray(2).also(controller.surfaceView::getLocationInWindow)
							val resizedGeneration = 903L
							task8SubmitRecoveredDeck(controller, resizedGeneration, ReaderDeckSubmissionRole.Active,
								resizedProfile, {}, true, ReaderPlayLikeCurlPhysicalRect(resizedOrigin[0], resizedOrigin[1],
									resizedOrigin[0] + viewer.width, resizedOrigin[1] + viewer.height))
							producedRendererGenerations += resizedGeneration
							val resizedAdmission = assertNotNull(admissions[resizedGeneration])
							assertNull(resizedAdmission.capability.presentationToken)
							assertEquals(beforeCompletion.viewportGeneration, resizedAdmission.capability.originBinding.viewportGeneration)
							assertEquals(beforeCompletion.preparationGeneration, resizedAdmission.capability.originBinding.preparationGeneration)
							assertEquals(beforeCompletion.profileGeneration, resizedAdmission.capability.originBinding.profileGeneration)
							assertEquals(resizedGeneration, resizedAdmission.capability.originBinding.textureGeneration)
							val beforePrepared = resizeReceipts.size
							controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, resizedGeneration)
							val completionReceipt = resizeReceipts.drop(beforePrepared).first {
								it.event is ReaderPresentationEvent.BindingCompleted
							}
							assertEquals(ReaderPresentationEventDisposition.Accepted, completionReceipt.disposition)
							assertEquals(ReaderPresentationEvent.BindingCompleted(beforeCompletion, resizedAdmission.capability.originBinding), completionReceipt.event)
							assertEquals(resizedAdmission.capability.originBinding, reporter.lastReportedBinding)
							assertEquals(resizedAdmission.capability.originBinding, common.state.presentation.binding)
							assertEquals(resizedAdmission.capability.originBinding, physicalBinding())
							assertFalse(common.state.presentationDecision.frameOwner is ReaderPresentationFrameOwner.ShellCover)
							val currentState = common.state.presentation
							work.callback(null)
							(oldPlans + retryPlans + workerPlans).forEach { it.onReceiveValue(plan) }
							controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, 302L)
							assertEquals(currentState, common.state.presentation)
							assertEquals(resizedAdmission.capability.originBinding, reporter.lastReportedBinding)
							assertEquals(1, rendererReleaseCounts[301L])
							assertEquals(1, rendererReleaseCounts[302L])
							// This is reporter/completion/liveness coverage, not automatic
							// post-resize raster preparation or a successful pixel presentation.
							val requested = deliver(ReaderPresentationEvent.NativePageRequested)
							assertEquals(ReaderPresentationEventDisposition.Accepted, requested.disposition)
							assertEquals(resizedAdmission.capability.originBinding, requested.postState.binding)
							assertTrue(deadlines.hasPending)
							nowMillis += deadlineDelayMillis
							deadlines.runPending()
							assertEquals(ReaderPresentationFailureReason.TimedOut,
								assertIs<ReaderDiagnosticPresentation.Failure>(common.state.presentationDecision.diagnosticPresentation).reason)
							assertFalse(deadlines.hasPending)
							assertEquals(resizedAdmission.capability.originBinding, reporter.lastReportedBinding)
							assertEquals(listOf(resizedAdmission.capability.originBinding), common.state.presentation.rendererCleanupOwnership.map { it.binding })
							handler.deliver(queue.pendingEffects(), common.state.presentationDecision) { queue.acknowledge(it) }
							assertTrue(queue.pendingEffects().isEmpty())
							return@runTest
						}
						runtime.completeCommit(work)
						val capture = withContext(Dispatchers.Default) { withTimeout(5_000L) { runtime.captures.receive() } }
						runtime.completeCapture(capture)
					}
					assertEquals(generationBeforeRetry + 1L,
						withContext(Dispatchers.Default) { withTimeout(5_000L) { rasterProofReady.await() } })
					val proofPlans = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					proofPlans.forEach { it.onReceiveValue(plan) }
					val rasterJob = controller.javaClass.task7Field("rasterJob").get(controller) as Job
					withContext(Dispatchers.Default) {
						withTimeout(5_000L) { rasterJob.children.toList().joinAll() }
					}
					@Suppress("UNCHECKED_CAST")
					val owners = controller.javaClass.task7Field("generationOwners").get(controller) as Map<Long, Any>
					val successor = assertNotNull(controller.javaClass.task7Field("activeDeckGenerationId").get(controller) as Long?)
					val plansAfterRaster = webView.planCallbacks.size
					assertTrue(successor > 302L, "Retry successor must be allocated by the actual renderer producer")
					producedRendererGenerations += owners.keys.filter { it > 302L }
					val freshAdmission = assertNotNull(admissions[successor])
					assertEquals(retryEffect.token, freshAdmission.capability.presentationToken)
					assertEquals(generationBeforeRetry + 1L, freshAdmission.capability.originBinding.preparationGeneration)
					assertEquals(bundle.currentGeneration(), freshAdmission.capability.originBinding.rasterGeneration)
					assertEquals(successor, freshAdmission.capability.originBinding.textureGeneration)
					for (obsolete in owners.keys.filter { it > 302L && it != successor }.toList()) {
						controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, obsolete)
					}
					controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, successor)
					ShadowLooper.runUiThreadTasks()
					val plansAfterPrepared = webView.planCallbacks.size
					assertEquals(ReaderPagePreparationPhase.Ready, common.state.presentation.preparationFacts.phase)
					assertEquals(cover, common.state.presentationDecision.frameOwner)
					val candidate = assertNotNull(viewerClass.task7Method("currentNativePagePresentationCandidateOrNull")
						.invoke(viewer) as ReaderNativePagePresentationCandidate?)
					assertEquals(freshAdmission.capability.originBinding, candidate.binding)
					assertEquals(retryEffect.token, candidate.transitionToken)
					handler.deliver(queue.pendingEffects(), common.state.presentationDecision) { queue.acknowledge(it) }
					assertEquals(cover, common.state.presentationDecision.frameOwner)
					var lastNativeCallbackObserved = false
					var lastNativeReceiptAuthorizes: Boolean? = null
					val lastNativeReceiptChain = linkedMapOf<String, String>()
					val firstNativeReceiptChain = linkedMapOf<String, String>()
					val publicationIdentityFlags = linkedMapOf<String, Boolean>()
					val firstArmedCandidate = candidate
					var acceptedAfterFirst: ReaderNativePagePresentationCandidate? = null
					var observingFirstNative = true
					var observedProducerReceipt: ReaderPresentationEventReceipt? = null
					fun chain(label: String, value: Boolean) { lastNativeReceiptChain[label] = value.toString() }
					fun observeCommonNativeGuards(state: ReaderPresentationState, event: ReaderPresentationEvent.NativePagePresented) {
						val proof = event.proof
						chain("commonDestroyed", state.lifecycle == ReaderPresentationLifecycleState.Destroyed)
						chain("commonTimedOut", state.failure?.reason == ReaderPresentationFailureReason.TimedOut)
						chain("commonBindingEqualsProof", state.binding == proof.binding)
						chain("commonPreparationReady", state.preparationFacts.phase == ReaderPagePreparationPhase.Ready)
						lastNativeReceiptChain["commonNativeBranch"] = when (val authority = state.authority) {
							ReaderPresentationAuthority.Unavailable -> {
								chain("commonBindingAbsent", state.binding == null)
								"Unavailable"
							}
							is ReaderPresentationAuthority.SettledNativePage -> {
								val previous = authority.frame.proof
								val nextBinding = proof.binding
								val oldBinding = previous.binding
								chain("commonProofEqualsSelected", previous == proof)
								chain("commonProofOnlyFrameDiffers", previous.copy(presentedFrame = proof.presentedFrame) == proof)
								chain("commonProofFrameEqualsSelected", previous.presentedFrame == proof.presentedFrame)
								chain("commonProofTokenEqualsSelected", previous.transitionToken == proof.transitionToken)
								chain("commonProofBindingEqualsSelected", oldBinding == nextBinding)
								chain("commonProofTokenAbsent", proof.transitionToken == null)
								chain("commonRendererIdentityComplete", nextBinding.rasterGeneration != null && nextBinding.textureGeneration != null)
								chain("commonPreparationPresent", nextBinding.preparationGeneration != null)
								chain("commonOldDestinationPresent", oldBinding.destinationCommitIdentity != null)
								chain("commonNewDestinationPresent", nextBinding.destinationCommitIdentity != null)
								chain("commonSessionEqual", nextBinding.foliateSessionId == oldBinding.foliateSessionId)
								chain("commonPublicationEqual", nextBinding.publicationGeneration == oldBinding.publicationGeneration)
								chain("commonViewportEqual", nextBinding.viewportGeneration == oldBinding.viewportGeneration)
								chain("commonProfileEqual", nextBinding.profileGeneration == oldBinding.profileGeneration)
								chain("commonPreparationCompatible", nextBinding.preparationGeneration == oldBinding.preparationGeneration ||
									(oldBinding.rasterGeneration != null && oldBinding.textureGeneration != null && nextBinding.rasterGeneration != null && nextBinding.textureGeneration != null && nextBinding.preparationGeneration != null))
								chain("commonDestinationAdvanced", oldBinding.destinationCommitIdentity?.let { old ->
									nextBinding.destinationCommitIdentity?.let { next -> next.commitSequence > old.commitSequence }
								} == true)
								"SettledNativePage"
							}
							is ReaderPresentationAuthority.BlockingPreparation -> {
								val request = authority.nativePresentationRequest
								chain("commonRequestPresent", request != null)
								chain("commonRetainedCover", authority.retainedFrame is ReaderPresentationFrameOwner.ShellCover)
								chain("commonRetainedNeutral", authority.retainedFrame == ReaderPresentationFrameOwner.Neutral)
								chain("commonRetryFloorPresent", request?.retryAfterPreparationGeneration != null)
								chain("commonFailureAbsent", state.failure == null)
								chain("commonPreparationAboveRetryFloor", request?.retryAfterPreparationGeneration?.let { (proof.binding.preparationGeneration ?: -1L) > it } == true)
								chain("commonRequestTokenEqual", request?.token == proof.transitionToken)
								chain("commonRequestBindingEqual", request?.binding == proof.binding)
								"BlockingPreparation"
							}
							is ReaderPresentationAuthority.CurlSettlementPending -> {
								chain("commonCurlAwaitingNative", authority.stage == paige.navic.reader.ReaderCurlSettlementStage.AwaitingNativePresentation)
								chain("commonRequestTokenEqual", authority.retainedFrame.frame.token == proof.transitionToken)
								chain("commonRequestBindingEqual", authority.binding == proof.binding)
								"CurlSettlementPending"
							}
							is ReaderPresentationAuthority.LiveEngineHandoffPending -> {
								chain("commonHandbackDirection", authority.direction == paige.navic.reader.ReaderLiveEngineHandoffDirection.LiveEngineToNative)
								chain("commonRetainedLive", authority.retainedFrame is ReaderPresentationFrameOwner.LiveEngine)
								chain("commonRequestTokenEqual", authority.token == proof.transitionToken)
								chain("commonRequestBindingEqual", authority.binding == proof.binding)
								"LiveEngineHandoffPending"
							}
							is ReaderPresentationAuthority.ShellCover -> "ShellCover"
							is ReaderPresentationAuthority.ShellCoverCommitPending -> "ShellCoverCommitPending"
							is ReaderPresentationAuthority.CurlGesture -> "CurlGesture"
							is ReaderPresentationAuthority.LiveEngineExposed -> "LiveEngineExposed"
						}
					}
					val nativePublisherDelegate = viewerClass.task7Field("nativePagePresentationPublisher\$delegate").get(viewer) as Lazy<*>
					assertTrue(nativePublisherDelegate.isInitialized(), "Observe the already initialized production native publisher")
					val observedNativePublisher = assertNotNull(nativePublisherDelegate.value)
					val nativeOnEventField = observedNativePublisher.javaClass.task7Field("onEvent")
					@Suppress("UNCHECKED_CAST")
					val originalNativeOnEvent = nativeOnEventField.get(observedNativePublisher) as (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
					val producerOnEventField = viewerClass.task7Field("onPresentationEvent")
					@Suppress("UNCHECKED_CAST")
					val originalProducerOnEvent = producerOnEventField.get(viewer) as (ReaderPresentationEvent) -> ReaderPresentationEventReceipt?
					val observedReceiptDispatcher = viewerClass.task7Field("presentationReceiptDispatcher").get(viewer)
					fun quiescenceOwnerCategories(): String = try {
						val surface = controller.surfaceView
						fun pending(field: String, method: String = "pendingCount"): Boolean {
							val owner = surface.javaClass.task7Field(field).get(surface)
							return (owner.javaClass.task7Method(method).invoke(owner) as Int) != 0
						}
						val sharedFrames = surface.javaClass.task7Field("presentedFrameRequest").get(surface)
						val nativeRequest = surface.javaClass.task7Field("nativeFrameRequest").get(surface)
						val frameArmed = synchronized(sharedFrames) {
							(sharedFrames.javaClass.task7Field("armedRequestIds").get(sharedFrames) as Set<*>).isNotEmpty()
						}
						val frameCompleted = synchronized(sharedFrames) {
							(sharedFrames.javaClass.task7Field("completedCallbacks").get(sharedFrames) as Map<*, *>).isNotEmpty()
						}
						val releases = queue.pendingEffects().mapNotNull { it.effect as? ReaderPresentationEffect.ReleaseStalePresentation }
						val nativeCategory = if (nativeRequest != null) {
							val publisherDelegate = viewerClass.task7Field("nativePagePresentationPublisher\$delegate").get(viewer) as Lazy<*>
							val publisher = if (publisherDelegate.isInitialized()) publisherDelegate.value else null
							val pendingFrame = publisher?.let { it.javaClass.task7Field("pendingFrame").get(it) }
							val armedCandidate = pendingFrame?.let { it.javaClass.task7Field("candidate").get(it) }
							val currentCandidate = viewerClass.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer)
							val accepted = acceptedAfterFirst
							val current = currentCandidate as? ReaderNativePagePresentationCandidate
							val published = publisher?.let { it.javaClass.task7Field("lastPublishedCandidate").get(it) }
							publicationIdentityFlags["samePublisherAtLast"] = publisher === observedNativePublisher
							publicationIdentityFlags["lastPublishedEqualsFirstArmed"] = published == firstArmedCandidate
							publicationIdentityFlags["lastPublishedSameAcceptedReference"] = published === accepted && accepted != null
							publicationIdentityFlags["lastAcceptedAndCurrentPresent"] = accepted != null && current != null
							if (accepted != null && current != null) {
								val oldFacts = accepted.preparationFacts
								val newFacts = current.preparationFacts
								publicationIdentityFlags["lastBindingEqual"] = current.binding == accepted.binding
								publicationIdentityFlags["lastTokenEqual"] = current.transitionToken == accepted.transitionToken
								publicationIdentityFlags["lastConsumedTokenShape"] = accepted.transitionToken != null && current.transitionToken == null && current.handoffDirection == null
								publicationIdentityFlags["lastHandoffEqual"] = current.handoffDirection == accepted.handoffDirection
								publicationIdentityFlags["lastPageEqual"] = current.visualPageIndex == accepted.visualPageIndex
								publicationIdentityFlags["lastWidthEqual"] = current.viewportWidth == accepted.viewportWidth
								publicationIdentityFlags["lastHeightEqual"] = current.viewportHeight == accepted.viewportHeight
								publicationIdentityFlags["lastPhaseEqual"] = newFacts.phase == oldFacts.phase
								publicationIdentityFlags["lastGenerationEqual"] = newFacts.generation == oldFacts.generation
								publicationIdentityFlags["lastCompletedEqual"] = newFacts.completedCount == oldFacts.completedCount
								publicationIdentityFlags["lastRequiredEqual"] = newFacts.requiredCount == oldFacts.requiredCount
								publicationIdentityFlags["lastReadinessEqual"] = newFacts.readiness == oldFacts.readiness
								publicationIdentityFlags["lastFailureEqual"] = newFacts.failure == oldFacts.failure
								publicationIdentityFlags["lastRetryableEqual"] = newFacts.retryable == oldFacts.retryable
								publicationIdentityFlags["lastAllOtherFactsEqual"] = newFacts.copy(completedCount = oldFacts.completedCount, requiredCount = oldFacts.requiredCount) == oldFacts
							}
							" nativeCandidateCurrent=${armedCandidate != null && armedCandidate == currentCandidate}"
						} else ""
						"ownerRequiredDisposal=${pending("requiredDisposeCallback")}" +
							" ownerAuxiliaryDisposal=${pending("disposeCallbacks")}" +
							" ownerOwnershipSnapshot=${pending("ownershipSnapshotCoordinator", "size")}" +
							" ownerTerminalOwnership=${pending("terminalOwnershipCallbacks")}" +
							" ownerPresentedFrame=${pending("presentedFrameRequest")}" +
							" ownerMainTerminal=${(surface.javaClass.task7Field("pendingMainTerminalActions").get(surface) as java.util.concurrent.atomic.AtomicInteger).get() != 0}" +
							" ownerPageOverlay=${surface.javaClass.task7Field("pageOverlayUpdatePending").getBoolean(surface)}" +
							" effectReleaseStale=${queue.pendingEffects().any { it.effect is ReaderPresentationEffect.ReleaseStalePresentation }}" +
							" effectRetryPreparation=${queue.pendingEffects().any { it.effect is ReaderPresentationEffect.RetryPreparation }}" +
							" ownerNativeRequest=${nativeRequest != null} frameArmed=$frameArmed frameCompleted=$frameCompleted" +
							" releaseRetainedByDecision=${releases.any { common.state.presentationDecision.retainsPresentationIdentity(it.token, it.binding) }}" +
							" releaseProtectedByController=${releases.any { effect -> effect.binding.textureGeneration?.let { generation ->
								controller.javaClass.task7Method("generationBacksCommonPresentation", java.lang.Long.TYPE).invoke(controller, generation) as Boolean
							} == true }}" + nativeCategory +
							" nativeCallbackObserved=$lastNativeCallbackObserved nativeReceiptAuthorizes=$lastNativeReceiptAuthorizes" +
							" nativeReceiptChain=[${lastNativeReceiptChain.entries.joinToString(",") { "${it.key}=${it.value}" }}]" +
							" firstNativeReceiptChain=[${firstNativeReceiptChain.entries.joinToString(",") { "${it.key}=${it.value}" }}]" +
							" nativePublicationIdentity=[${publicationIdentityFlags.entries.joinToString(",") { "${it.key}=${it.value}" }}]"
					} catch (_: ReflectiveOperationException) {
						"quiescenceOwnerDiagnosticsUnavailable=true"
					}
					var sourceRounds = 0
					try {
						producerOnEventField.set(viewer, { event: ReaderPresentationEvent ->
							if (event !is ReaderPresentationEvent.NativePagePresented) {
								originalProducerOnEvent(event)
							} else {
								val producerPreState = common.state.presentation
								val producerPreVersion = common.presentationVersion
								val producerPreShell = common.state.shellCoverVisible
								val producerEntryEpoch = reporter.captureEpoch()
								chain("producerCallbackObserved", true)
								try {
									val returned = originalProducerOnEvent(event) // The original producer runs exactly once.
									observedProducerReceipt = returned
									returned
								} catch (failure: Throwable) {
									chain("producerCallbackThrew", true)
									throw failure
								} finally {
									try {
										val receipt = observedProducerReceipt
										chain("producerReceiptPresent", receipt != null)
										lastNativeReceiptChain["producerDisposition"] = receipt?.disposition?.name ?: "Absent"
										chain("producerReceiptAuthorizes", receipt.authorizes(event))
										observeCommonNativeGuards(producerPreState, event)
										// Pure replay of immutable callback-entry inputs, not a second controller mutation.
										val producerExpected = paige.navic.reader.readerPresentationEventTransition(
											producerPreState, producerPreVersion, producerPreShell, event).receipt
										chain("producerPureTransitionMatches", receipt == producerExpected)
										lastNativeReceiptChain["commonPureDisposition"] = producerExpected.disposition.name
										// Snapshot immediately before returning to the original dispatcher's classifyReceipt call.
										val reporterState = reporter.javaClass.task7Field("authoritativeState").get(reporter) as ReaderPresentationState?
										val reporterVersion = (reporter.javaClass.task7Field("authoritativeVersion").get(reporter)
											?: reporter.javaClass.task7Field("minimumComposeVersion").get(reporter)) as paige.navic.reader.ReaderPresentationReceiptVersion?
										val reporterShell = reporter.javaClass.task7Field("authoritativeShellCoverVisible").getBoolean(reporter)
										val reporterExpected = if (reporterState != null && reporterVersion != null)
											paige.navic.reader.readerPresentationEventTransition(reporterState, reporterVersion, reporterShell, event).receipt else null
										chain("dispatcherBusyBeforeReporterAdmission", observedReceiptDispatcher.javaClass.task7Field("dispatchInProgress").getBoolean(observedReceiptDispatcher))
										chain("reporterStatePresent", reporterState != null)
										chain("reporterVersionPresent", reporterVersion != null)
										chain("reporterEpochCurrent", producerEntryEpoch == reporter.captureEpoch())
										chain("reporterEventEqual", receipt?.event == event)
										chain("reporterStateMatchesProducerPre", reporterState == producerPreState)
										chain("reporterVersionMatchesProducerPre", reporterVersion == producerPreVersion)
										chain("reporterBindingMatchesProducerPre", reporterState?.binding == producerPreState.binding)
										chain("reporterBindingEqualsProof", reporterState?.binding == event.proof.binding)
										chain("reporterShellMatchesProducerPre", reporterShell == producerPreShell)
										chain("reporterReceiptPreVersionEqual", receipt?.preVersion == reporterExpected?.preVersion)
										chain("reporterReceiptVersionEqual", receipt?.version == reporterExpected?.version)
										chain("reporterReceiptDispositionEqual", receipt?.disposition == reporterExpected?.disposition)
										chain("reporterReceiptPostStateEqual", receipt?.postState == reporterExpected?.postState)
										chain("reporterReceiptEffectsEqual", receipt?.effects == reporterExpected?.effects)
										chain("reporterExactTransitionEqual", receipt != null && receipt == reporterExpected)
										chain("reporterPureAdmission", receipt != null && producerEntryEpoch == reporter.captureEpoch() &&
											receipt.event == event && reporterState != null && reporterVersion != null && receipt == reporterExpected)
										lastNativeReceiptChain["reporterPureDisposition"] = reporterExpected?.disposition?.name ?: "Absent"
									} catch (_: Throwable) { chain("chainDiagnosticsUnavailable", true) }
								}
							}
						})
						nativeOnEventField.set(observedNativePublisher, { event: ReaderPresentationEvent ->
							if (event !is ReaderPresentationEvent.NativePagePresented) {
								originalNativeOnEvent(event)
							} else {
								lastNativeCallbackObserved = true
								lastNativeReceiptAuthorizes = null
								lastNativeReceiptChain.clear()
								observedProducerReceipt = null
								chain("chainDiagnosticsUnavailable", false)
								chain("producerCallbackObserved", false)
								chain("producerCallbackThrew", false)
								chain("finalCallbackThrew", false)
								try {
									chain("dispatcherBusyAtNativeCallbackEntry", observedReceiptDispatcher.javaClass.task7Field("dispatchInProgress").getBoolean(observedReceiptDispatcher))
								} catch (_: Throwable) { chain("chainDiagnosticsUnavailable", true) }
								try {
									val returned = originalNativeOnEvent(event) // Exactly once, including null; throws propagate unchanged.
									lastNativeReceiptAuthorizes = returned.authorizes(event)
								if (observingFirstNative) {
									publicationIdentityFlags["firstFinalAuthorizes"] = returned.authorizes(event)
									try {
										publicationIdentityFlags["firstReturnAlreadyPending"] = observedNativePublisher.javaClass.task7Field("pendingFrame").get(observedNativePublisher) != null
									} catch (_: ReflectiveOperationException) { chain("chainDiagnosticsUnavailable", true) }
								}
									chain("finalReceiptPresent", returned != null)
									lastNativeReceiptChain["finalDisposition"] = returned?.disposition?.name ?: "Absent"
									chain("finalReceiptMatchesProducer", returned == observedProducerReceipt)
									returned
								} catch (failure: Throwable) {
									chain("finalCallbackThrew", true)
									throw failure
								} finally { observedProducerReceipt = null }
							}
						})
					val frames = controller.surfaceView.javaClass.task7Field("presentedFrameRequest").get(controller.surfaceView)
					val completion = frames.javaClass.task7Method("markRendered").invoke(frames) as Long
					controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, completion)
					observingFirstNative = false
					firstNativeReceiptChain.putAll(lastNativeReceiptChain)
					acceptedAfterFirst = observedNativePublisher.javaClass.task7Field("lastPublishedCandidate").get(observedNativePublisher) as ReaderNativePagePresentationCandidate?
					publicationIdentityFlags["afterFirstHandlePublishedEqualsArmed"] = acceptedAfterFirst == firstArmedCandidate
					val terminalEffects = queue.pendingEffects()
					handler.deliver(terminalEffects, common.state.presentationDecision) { queue.acknowledge(it) }
					ShadowLooper.runUiThreadTasks()
					assertEquals(candidate.binding,
						assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority).frame.proof.binding)
					// Capacity/source tails cannot drain until their actual downstream
					// prepared/frame acknowledgements and release effects are delivered.
					val eventsBeforeSourceTail = events.size
					val bundleJob = bundle.javaClass.task7Field("rasterJob").get(bundle) as Job
					val preparationJob = preparation.javaClass.task7Field("teardownJob").get(preparation) as Job
					val scheduler = assertNotNull(bundle.javaClass.task7Field("rasterScheduler").get(bundle))
					val schedulerWorker = scheduler.javaClass.task7Field("workerJob").get(scheduler) as Job
					val schedulerLock = checkNotNull(scheduler.javaClass.task7Field("lock").get(scheduler))
					var physicalProgress = kotlinx.coroutines.CompletableDeferred<Unit>()
					@Suppress("UNCHECKED_CAST")
					val originalPreparationState = preparation.javaClass.task7Field("onPreparationStateChange")
						.get(preparation) as (ReaderPagePreparationState) -> Unit
					preparation.javaClass.task7Field("onPreparationStateChange").set(preparation, { state: ReaderPagePreparationState ->
						originalPreparationState(state)
						physicalProgress.complete(Unit)
					})
					fun physicalJobs(): List<Job> {
						val scheduled = synchronized(schedulerLock) {
							(scheduler.javaClass.task7Field("pending").get(scheduler) as Map<*, *>).values.map { work ->
								checkNotNull(work).javaClass.task7Field("result").get(work) as Job
							}
						}
						// The exact scheduler service waits for wakeups until teardown. Its
						// lifetime is not work; its requests and active children still are.
						return (listOf(rasterJob, bundleJob, preparationJob).flatMap { it.children.toList() }
							.filter { it !== schedulerWorker } + schedulerWorker.children.toList() + scheduled)
							.filter { !it.isCompleted }
					}
					fun hasPendingRetention() = synchronized(schedulerLock) {
						scheduler.javaClass.task7Field("profilePendingRetention").get(scheduler) != null
					}
					fun unpreparedGenerations(): List<Long> {
						@Suppress("UNCHECKED_CAST")
						val prepared = controller.javaClass.task7Field("preparedDeckGenerations").get(controller) as Set<Long>
						return owners.keys.filter { it > 302L && it !in prepared }.sorted()
					}
					fun physicalScalars(): String =
						"physicalPhase=${(viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState).phase} " +
							"prewarm=${preparation.javaClass.task7Field("prewarmInProgress").getBoolean(preparation)} " +
							"batch=${adapter.javaClass.task7Field("activeBatch").get(adapter) != null} " +
							"runtime=${runtime.pendingCallbackCount} jobs=${physicalJobs().size} retention=${hasPendingRetention()} plans=${webView.planCallbacks.size} " +
							"renderer=${controller.surfaceView.pendingCallbackCount} unprepared=${unpreparedGenerations().size} visual=${controller.applicationOwnershipMetrics().pendingVisualCallbacks} " +
							"publication=${bundle.ownershipMetrics().pendingPublicationCallbacks} effects=${queue.pendingEffects().size}"
					fun hasPhysicalWork() = webView.planCallbacks.isNotEmpty() || runtime.pendingCallbackCount != 0 ||
						preparation.javaClass.task7Field("prewarmInProgress").getBoolean(preparation) ||
						adapter.javaClass.task7Field("activeBatch").get(adapter) != null || physicalJobs().isNotEmpty() || hasPendingRetention() ||
						controller.surfaceView.pendingCallbackCount != 0 || unpreparedGenerations().isNotEmpty() ||
						controller.applicationOwnershipMetrics().pendingVisualCallbacks != 0 ||
						bundle.ownershipMetrics().pendingPublicationCallbacks != 0 || queue.pendingEffects().isNotEmpty() ||
						common.state.presentation.rendererCleanupOwnership.isNotEmpty() || deadlines.hasPending ||
						common.state.presentationDecision.pendingTransitionToken != null ||
						controller.surfaceView.javaClass.task7Field("nativeFrameRequest").get(controller.surfaceView) != null
					while (hasPhysicalWork()) {
						assertTrue(sourceRounds++ < 8,
							"Acknowledged physical continuation must reach bounded quiescence: ${physicalScalars()}" +
								if (sourceRounds > 8) " ${quiescenceOwnerCategories()}" else "")
						lastNativeCallbackObserved = false
						lastNativeReceiptAuthorizes = null
						lastNativeReceiptChain.clear()
						observedProducerReceipt = null
						physicalProgress = kotlinx.coroutines.CompletableDeferred()
						testScheduler.runCurrent()
						ShadowLooper.runUiThreadTasks()
						val sourcePlans = webView.planCallbacks.toList()
						for (sourcePlan in sourcePlans) {
							check(webView.planCallbacks.remove(sourcePlan))
							sourcePlan.onReceiveValue(plan)
						}
						runtime.commits.tryReceive().getOrNull()?.let(runtime::completeCommit)
						runtime.captures.tryReceive().getOrNull()?.let(runtime::completeCapture)
						testScheduler.runCurrent()
						ShadowLooper.runUiThreadTasks()
						for (generation in unpreparedGenerations()) {
							if (generation !in producedRendererGenerations) producedRendererGenerations += generation
							controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, generation)
						}
						ShadowLooper.runUiThreadTasks()
						if (controller.surfaceView.javaClass.task7Field("nativeFrameRequest").get(controller.surfaceView) != null) {
							assertNotNull(viewerClass.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer))
							val tailFrames = controller.surfaceView.javaClass.task7Field("presentedFrameRequest").get(controller.surfaceView)
							val tailCompletion = tailFrames.javaClass.task7Method("markRendered").invoke(tailFrames) as Long
							controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, tailCompletion)
						}
						handler.deliver(queue.pendingEffects(), common.state.presentationDecision) { queue.acknowledge(it) }
						testScheduler.runCurrent()
						ShadowLooper.runUiThreadTasks()
						if (hasPhysicalWork() && webView.planCallbacks.isEmpty() && unpreparedGenerations().isEmpty() &&
							queue.pendingEffects().isEmpty() &&
							controller.surfaceView.javaClass.task7Field("nativeFrameRequest").get(controller.surfaceView) == null
						) {
							// Wait for one causal edge, never all upstream jobs while their
							// passive commit/capture acknowledgements are still withheld.
							val jobs = physicalJobs()
							val progress = physicalProgress
							val action = withContext(Dispatchers.Default) {
								kotlinx.coroutines.withTimeoutOrNull(5_000L) {
									kotlinx.coroutines.selects.select<() -> Unit> {
										runtime.commits.onReceive { request -> { runtime.completeCommit(request) } }
										runtime.captures.onReceive { request -> { runtime.completeCapture(request) } }
										progress.onAwait { { } }
										jobs.forEach { job -> job.onJoin { { } } }
									}
								}
							}
							assertNotNull(action, "No reachable physical completion: ${physicalScalars()}").invoke()
						}
					}
					val settled = common.state
					val finalProof = assertIs<ReaderPresentationAuthority.SettledNativePage>(settled.presentation.authority,
						"After acknowledged source tail: rounds=$sourceRounds plans=${webView.planCallbacks.size} " +
							"producers=${planWitnesses.takeLast(8)} events=${events.drop(eventsBeforeSourceTail).map { it::class.simpleName }} " +
							"owners=${owners.keys.sorted()} phase=${settled.presentation.preparationFacts.phase} " +
							"targetTexture=${settled.presentation.binding?.textureGeneration} physicalTexture=${physicalBinding().textureGeneration} " +
							"request=${settled.presentationDecision.pendingTransitionToken?.value} ${physicalScalars()}").frame.proof
					assertEquals(physicalBinding(), finalProof.binding, physicalScalars())
					assertEquals(candidate.binding.copy(textureGeneration = finalProof.binding.textureGeneration), finalProof.binding)
					assertIs<paige.navic.reader.ReaderPageNewPointerDecision.Accept>(
						assertIs<ReaderPresentationInputPolicy.NativePage>(settled.presentationDecision.inputPolicy).policy.newPointer)
					assertEquals(1, events.filterIsInstance<ReaderPresentationEvent.NativePagePresented>().count {
						it.proof.transitionToken == retryEffect.token
					})
					val plansAfterNative = webView.planCallbacks.size
					(oldPlans + retryPlans).forEach { it.onReceiveValue(plan) }
					controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, 302L)
					controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, completion)
					ShadowLooper.runUiThreadTasks()
					assertEquals(settled, common.state)
					assertEquals(1, rendererReleaseCounts[302L])
					val releaseCalls = releaseIngress.size
					handler.deliver(terminalEffects, common.state.presentationDecision) { queue.acknowledge(it) }
					assertEquals(releaseCalls, releaseIngress.size, "Terminal effect replay must not release twice")
					assertTrue(releaseIngress.any { it.binding.textureGeneration == 301L })
					assertEquals(1, predecessorReleases)
					assertEquals(setOf(finalProof.binding.textureGeneration), owners.keys)
					assertTrue(common.state.presentation.rendererCleanupOwnership.isEmpty())
					assertTrue(queue.pendingEffects().isEmpty())
					assertFalse(deadlines.hasPending)
					assertNull(common.state.presentationDecision.pendingTransitionToken)
					assertFalse(hasPhysicalWork(), physicalScalars())
					assertTrue(runtime.commits.tryReceive().isFailure)
					assertTrue(runtime.captures.tryReceive().isFailure)
					assertEquals(0, runtime.pendingCallbackCount)
					assertEquals(0, controller.surfaceView.pendingCallbackCount)
					assertEquals(0, controller.applicationOwnershipMetrics().pendingVisualCallbacks)
					assertEquals(0, bundle.ownershipMetrics().pendingPublicationCallbacks)
					assertTrue(webView.planCallbacks.isEmpty(),
						"Undelivered source plans: raster=$plansAfterRaster prepared=$plansAfterPrepared native=$plansAfterNative " +
							"replay=${webView.planCallbacks.size} producers=${webView.planCallbacks.map { it.javaClass.simpleName.substringBefore('$') }}")
					assertNull(controller.surfaceView.javaClass.task7Field("nativeFrameRequest").get(controller.surfaceView))
					} finally {
						nativeOnEventField.set(observedNativePublisher, originalNativeOnEvent)
						producerOnEventField.set(viewer, originalProducerOnEvent)
						observedProducerReceipt = null
						acceptedAfterFirst = null
						assertSame(originalNativeOnEvent, nativeOnEventField.get(observedNativePublisher))
						assertSame(originalProducerOnEvent, producerOnEventField.get(viewer))
					}
					return@runTest
				}
				val gestureController = if (nativePipeline) controller else source.controller
				if (nativePipeline) {
					// Import the source fixture's already-ready predecessor safety policy,
					// before the gesture/timeout/Retry route under test begins.
					controller.setPageOperationPolicy(source.controller.javaClass.task7Field("pageOperationPolicy")
						.get(source.controller) as paige.navic.reader.ReaderPageOperationPolicy)
					assertTrue(controller.isAvailable, "Imported predecessor must admit the actual receiving gesture")
				}
				// Receiver startup invalidates the bundle. The donated proof and both
				// controllers must retain that real raster authority, not a fresh zero epoch.
				assertSame(bundle, source.controller.javaClass.task7Field("bundleSource").get(source.controller))
				assertSame(bundle, gestureController.javaClass.task7Field("bundleSource").get(gestureController))
				assertEquals(sourceRasterGeneration, bundle.currentGeneration(), "Predecessor raster must remain current")
				assertEquals(sourceRasterGeneration, binding.rasterGeneration)
				assertEquals(binding, predecessorProof.binding)
				assertEquals(sourceRasterGeneration, predecessorProof.rasterGeneration)
				assertEquals(sourceRasterGeneration, (sourceProfile as ReaderPlayLikeCurlRasterProfile).rasterGeneration)
				val activePages = assertNotNull(gestureController.javaClass.task7Field("activePages").get(gestureController))
				val activeProfile = activePages.javaClass.task7Field("profile").get(activePages) as ReaderPlayLikeCurlRasterProfile
				assertEquals(sourceRasterGeneration, activeProfile.rasterGeneration)
				val activeGeneration = gestureController.javaClass.task7Field("activeDeckGenerationId").get(gestureController)
				assertEquals(predecessorProof.textureGeneration, activeGeneration)
				assertEquals(binding.textureGeneration, activeGeneration)
				val owners = gestureController.javaClass.task7Field("generationOwners").get(gestureController) as Map<*, *>
				assertSame(activePages, owners[activeGeneration], "Predecessor must be owned by the receiving controller")
				assertEquals(binding.preparationGeneration,
					gestureController.javaClass.task7Field("preparationGeneration").getLong(gestureController))
				assertEquals(binding.foliateSessionId,
					gestureController.javaClass.task7Field("currentFoliateSessionId").get(gestureController))
				assertEquals(ReaderPageTurnStartResult.Settling,
					gestureController.start(225L, PageChange.NEXT) { _, _ -> true })
				if (!nativePipeline) deliver(assertIs<ReaderPresentationEvent.CurlClaimed>(source.store.presentationEvents.last()))
				gestureController.cancelGesture(225L)
				if (!nativePipeline) deliver(assertIs<ReaderPresentationEvent.CurlTerminal>(source.store.presentationEvents.last()))
				val retained = assertIs<ReaderPresentationFrameOwner.Curl>(
					readerPresentationDecision(common.state.presentation).frameOwner)
				@Suppress("UNCHECKED_CAST")
				val preRetryRendererGenerations = (controller.javaClass.task7Field("generationOwners")
					.get(controller) as Map<Long, Any>).keys.toSet()
				if (nativePipeline) {
					@Suppress("UNCHECKED_CAST")
					val preRetryAdmissions = controller.javaClass.task7Field("generationAdmissions")
						.get(controller) as Map<Long, ReaderDeckAdmission>
					// The actual tap submits pending material only after its claim receipt
					// issues immutable admission. It belongs to the predecessor generation,
					// not the later Retry output.
					for (generation in preRetryRendererGenerations - 301L) {
						val admission = assertNotNull(preRetryAdmissions[generation])
						assertEquals(binding.preparationGeneration, admission.capability.preparationGeneration)
						assertEquals(binding.profileGeneration, admission.capability.profileGeneration)
					}
				}
				assertNotNull(sustainedBridge).update(readerPresentationDecision(common.state.presentation))
				deadlines.runPending()
				val retry = deliver(ReaderPresentationEvent.Retry)
				if (projectionOnly) {
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					assertFalse(controller.ownsInlineRasterShieldPresentation)
					assertEquals(1f, controller.surfaceView.alpha,
						"The actual bridge must project the common-retained Curl frame through Retry")
					assertTrue(deadlines.hasPending)
				}
				val retryEffect = assertIs<ReaderPresentationEffect.RetryPreparation>(retry.effects.single())
				val request = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
					common.state.presentation.authority)
				assertEquals(retained, request.retainedFrame)
				assertEquals(retryEffect.token, request.nativePresentationRequest?.token)
				assertEquals(binding.preparationGeneration,
					request.nativePresentationRequest?.retryAfterPreparationGeneration)
				assertEquals(retryEffect.binding,
					assertIs<ReaderPresentationAuthority.BlockingPreparation>(common.state.presentation.authority)
						.nativePresentationRequest?.binding,
					"Uncorrelated host identity publication must not retire the pending Retry effect")
				assertEquals(readerPresentationDecision(common.state.presentation),
					viewerClass.task7Field("presentationDecision").get(viewer),
					"The viewer must apply the actual current common receipt before handling Retry")
				val effectQueue = paige.navic.reader.ReaderPresentationEffectQueue()
				var retryAdmissions = 0
				val effectHandler = ReaderPresentationEffectHandler(
					retryPreparation = { effect ->
						retryAdmissions += 1
						viewerClass.task7Method("retryPreparation", ReaderPresentationEffect.RetryPreparation::class.java)
							.invoke(viewer, effect) as Boolean
					}, releaseStalePresentation = { false })
				effectQueue.retain(retry.effects)
				effectHandler.deliver(effectQueue.pendingEffects(), readerPresentationDecision(common.state.presentation)) {
					assertTrue(effectQueue.acknowledge(it))
				}
				assertEquals(1, retryAdmissions)
				assertTrue(effectQueue.pendingEffects().isEmpty())
				fun assertRetainedProjection(stage: String) {
					if (!projectionOnly) return
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					assertFalse(controller.ownsInlineRasterShieldPresentation)
					assertEquals(1f, controller.surfaceView.alpha,
						"Selected Curl projection lost at $stage; token=${retained.frame.token.value} " +
							"texture=${retained.frame.textureGeneration} callers=$retainedProjectionCallers")
				}
				assertRetainedProjection("retry-effect-delivery")
				val correlation = assertNotNull(webView.recoveryReasons.lastOrNull())
				val retryDeadlinePosts = deadlines.postCount
				fun verifyTimeoutRetryCancel(afterTimeout: () -> Unit = {}, lateTail: () -> Unit) {
					assertTrue(deadlines.hasPending)
					val pendingToken = readerPresentationDecision(common.state.presentation).pendingTransitionToken
					nowMillis += deadlineDelayMillis
					deadlines.runPending()
					ShadowLooper.runUiThreadTasks()
					val failed = common.state
					val failedDecision = readerPresentationDecision(failed.presentation)
					val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(failedDecision.diagnosticPresentation)
					assertEquals(ReaderPresentationFailureReason.TimedOut, diagnostic.reason)
					assertTrue(diagnostic.retryable && diagnostic.cancellable)
					assertEquals(retained, failedDecision.frameOwner)
					assertEquals(0, predecessorReleases)
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					assertEquals(1f, controller.surfaceView.alpha)
					assertFalse(deadlines.hasPending)
					afterTimeout()
					lateTail()
					ShadowLooper.runUiThreadTasks()
					assertEquals(failed, common.state, "Late physical/source completion must not revive timeout")
					val fresh = deliver(ReaderPresentationEvent.Retry)
					val freshToken = assertNotNull(readerPresentationDecision(fresh.postState).pendingTransitionToken)
					assertTrue(freshToken.value > assertNotNull(pendingToken).value)
					assertEquals(retained, readerPresentationDecision(fresh.postState).frameOwner)
					effectQueue.retain(fresh.effects)
					effectHandler.deliver(effectQueue.pendingEffects(), readerPresentationDecision(common.state.presentation)) {
						assertTrue(effectQueue.acknowledge(it))
					}
					assertTrue(effectQueue.pendingEffects().isEmpty())
					assertTrue(deadlines.hasPending)
					val canRestoreSemanticFrame = common.state.presentation.binding == retained.frame.binding
					assertEquals(livenessBoundary == Task9LivenessBoundary.MissingSource ||
						livenessBoundary == Task9LivenessBoundary.RejectedSource, canRestoreSemanticFrame,
						"Only admitted source/native continuation may replace the retained semantic binding")
					deliver(ReaderPresentationEvent.Cancel)
					ShadowLooper.runUiThreadTasks()
					assertFalse(deadlines.hasPending)
					val cancelledDecision = readerPresentationDecision(common.state.presentation)
					assertNull(cancelledDecision.pendingTransitionToken)
					assertEquals(if (canRestoreSemanticFrame) retained else ReaderPresentationFrameOwner.Neutral,
						cancelledDecision.frameOwner)
					val cancelledFailure = assertIs<ReaderDiagnosticPresentation.Failure>(cancelledDecision.diagnosticPresentation)
					assertTrue(cancelledFailure.retryable)
					assertFalse(cancelledFailure.cancellable)
					if (!canRestoreSemanticFrame) {
						assertEquals(ReaderPresentationAuthority.Unavailable, cancelledDecision.authority)
						assertEquals(ReaderPresentationFailureReason.NativePresentationUnavailable, cancelledFailure.reason)
					}
					val cancelled = common.state
					lateTail()
					ShadowLooper.runUiThreadTasks()
					assertEquals(cancelled, common.state)
					assertEquals(if (canRestoreSemanticFrame) 0 else 1, predecessorReleases)
				}
				if (livenessBoundary == Task9LivenessBoundary.MissingSource ||
					livenessBoundary == Task9LivenessBoundary.RejectedSource) {
					producedRendererGenerations += preRetryRendererGenerations - 301L
					if (livenessBoundary == Task9LivenessBoundary.RejectedSource) {
						val beforeRejected = common.state
						visualLocation.invoke(viewer, targetOrdinal, "$correlation-stale", binding.foliateSessionId, null)
						assertEquals(beforeRejected, common.state)
						assertTrue(controller.awaitingPresentationRecoverySnapshot)
					}
					assertEquals(2, retryDeadlinePosts)
					verifyTimeoutRetryCancel {
						visualLocation.invoke(viewer, targetOrdinal, correlation, binding.foliateSessionId, null)
					}
					return@runTest
				}
				if (nativePipeline) {
					webView.retainPlanCallbacks = true
					val bundle = controller.javaClass.task7Field("bundleSource").get(controller) as ReaderPageTurnBundleSource
					val origin = IntArray(2).also(controller.surfaceView::getLocationInWindow)
					assertEquals(0, origin[0])
					assertEquals(0, origin[1])
					val quality = bundle.javaClass.task7Field("bitmapQuality").get(bundle) as ReaderPageBitmapQuality
					val bitmapWidth = readerPageTurnAnimationBitmapDimension(viewportWidth, quality)
					val bitmapHeight = readerPageTurnAnimationBitmapDimension(viewportHeight, quality)
					for (ordinal in listOf(targetOrdinal)) {
						assertNotNull(bundle.cacheCurrentSnapshot(ordinal, ReaderPageTurnTransitionKind.PortraitSlide,
							ReaderPageTurnCaptureResult(
								Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) },
								android.graphics.Rect(origin[0], origin[1], origin[0] + viewportWidth, origin[1] + viewportHeight),
								paige.navic.reader.ReaderPageTurnCaptureGeometry(viewportWidth.toDouble(), viewportHeight.toDouble(),
									paige.navic.reader.ReaderPageTurnLayoutMode.Single,
									listOf(paige.navic.reader.ReaderPageTurnPageRect(paige.navic.reader.ReaderPageTurnPageRole.Full,
										0.0, 0.0, viewportWidth.toDouble(), viewportHeight.toDouble()))), 1L)))
					}
					val reference = assertNotNull(bundle.retainedCurrentLayoutSnapshot(targetOrdinal,
						ReaderPageTurnTransitionKind.PortraitSlide))
					try {
						assertEquals(quality, reference.key.bitmapQuality)
						assertEquals(bitmapWidth, reference.bitmap.width)
						assertEquals(bitmapHeight, reference.bitmap.height)
						assertEquals(geometry.captureLeft, reference.surfaceRectInWindow.left)
						assertEquals(geometry.captureTop, reference.surfaceRectInWindow.top)
						assertEquals(geometry.captureRight, reference.surfaceRectInWindow.right)
						assertEquals(geometry.captureBottom, reference.surfaceRectInWindow.bottom)
						val hydrated = kotlinx.coroutines.CompletableDeferred<ReaderPageSlideSnapshot?>()
						val hydration = bundle.hydrateSnapshot(webView, targetOrdinal,
							ReaderPageTurnTransitionKind.PortraitSlide, reference, onHydrated = hydrated::complete)
						try { assertNotNull(hydrated.await()).release() } finally { hydration.cancel() }
					} finally { reference.release() }
					bundle.initializeRasterCache(webView)
					viewerClass.task7Method("setPageTurnPaginationStatus", String::class.java).invoke(viewer, "ready")
					controller.surfaceView.pageSurfaceListener.onCapabilitiesAvailable(RenderCapabilities(4096, 8L * 1024L * 1024L))
					assertTrue(webView.planCallbacks.isEmpty(), "Pending source must gate the actual plan producer")
				}
				assertRetainedProjection("raster-reference-and-pagination-setup")
				val destination = ReaderDestinationCommitIdentity(binding.foliateSessionId,
					assertNotNull(binding.destinationCommitIdentity).commitSequence + 1L)
				events.clear()
				updateDestination(destination)
				assertTrue(events.none { it is ReaderPresentationEvent.FoliateRelocated },
					"Compose model publication must wait for source ingress during correlated recovery")
				val snapshotCount = webView.recoveryReasons.size
				var checkingSourceContinuation = false
				val reentrantPrewarmGenerations = mutableListOf<Long>()
				if (!recoveryObservation) {
					@Suppress("UNCHECKED_CAST")
					val originalPrewarm = preparation.javaClass.task7Field("onRequestPrewarm").get(preparation) as () -> Unit
					preparation.javaClass.task7Field("onRequestPrewarm").set(preparation, {
						if (checkingSourceContinuation) {
							reentrantPrewarmGenerations += preparation.javaClass.task7Field("preparationGeneration").getLong(preparation)
							controller.onHostContentReady()
							assertTrue(controller.awaitingPresentationRecoverySnapshot,
								"Source continuation must fence reentrant work until fresh generation adoption")
							assertFalse(preparation.prewarmAdjacent(), "Reentrant prewarm must not start old-generation work")
						}
						originalPrewarm()
					})
				}
				visualLocation.invoke(viewer, targetOrdinal, "$correlation-stale", binding.foliateSessionId, null)
				assertTrue(events.none { it is ReaderPresentationEvent.FoliateRelocated })
				fun recoveryScalars(): String {
					val decision = controller.javaClass.task7Field("commonPresentationDecision").get(controller) as ReaderPresentationDecision
					val recovery = (decision.authority as? ReaderPresentationAuthority.BlockingPreparation)?.nativePresentationRequest
					val snapshot = controller.javaClass.task7Field("presentationRecoverySnapshot").get(controller)
					return "ordinal=$targetOrdinal authority=${decision.authority.javaClass.simpleName}" +
						" token=${decision.pendingTransitionToken?.value} retryFloor=${recovery?.retryAfterPreparationGeneration}" +
						" failure=${(decision.diagnosticPresentation as? ReaderDiagnosticPresentation.Failure)?.reason}" +
						" lifecycle=${decision.lifecycle} snapshot=${snapshot != null}" +
						" requestGeneration=${controller.javaClass.task7Field("requestGeneration").getLong(controller)}" +
						" currentOrdinal=${controller.javaClass.task7Field("currentOrdinal").getInt(controller)}" +
						" sessionRelocation=${controller.javaClass.task7Field("foliateSessionRelocationPending").getBoolean(controller)}" +
						" profile=${controller.javaClass.task7Field("publishedRasterProfileEpoch").get(controller)}" +
						" targetProfile=${decision.targetBinding?.profileGeneration}" +
						" targetPreparation=${decision.targetBinding?.preparationGeneration}" +
						" preparation=${preparation.javaClass.task7Field("preparationGeneration").getLong(preparation)}"
				}
				val beforeSource = recoveryScalars()
				checkingSourceContinuation = true
				visualLocation.invoke(viewer, targetOrdinal, if (recoveryObservation) correlation else "toc",
					binding.foliateSessionId, null)
				checkingSourceContinuation = false
				assertRetainedProjection("fresh-source-continuation")
				val afterSource = recoveryScalars()
				assertEquals(destination, common.state.presentation.binding?.destinationCommitIdentity)
				assertTrue(events.any { it is ReaderPresentationEvent.FoliateRelocated })
				assertEquals(assertNotNull(binding.preparationGeneration) + 1L,
					preparation.javaClass.task7Field("preparationGeneration").getLong(preparation),
					"Admitted source must continue one fresh attempt; before=[$beforeSource] after=[$afterSource]")
				if (!recoveryObservation) {
					assertTrue(reentrantPrewarmGenerations.contains(assertNotNull(binding.preparationGeneration) + 1L),
						"The actual allocator's prewarm callback must exercise the reentrant fence")
					assertFalse(controller.matchesPresentationRecoverySnapshot(correlation, binding.foliateSessionId))
					controller.onHostContentReady()
					assertEquals(snapshotCount, webView.recoveryReasons.size,
						"Already admitted external source must not dispatch a redundant snapshot")
					val acceptedState = common.state
					val acceptedScalars = recoveryScalars()
					visualLocation.invoke(viewer, 2, correlation, binding.foliateSessionId, null)
					assertEquals(acceptedState, common.state)
					assertEquals(acceptedScalars, recoveryScalars(), "Retired diagnostic must have no continuation effects")
					assertEquals(destination.commitSequence,
						common.state.presentation.binding?.destinationCommitIdentity?.commitSequence)
				}
				assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
				if (nativePipeline) {
					val callbacks = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					val planCallback = assertNotNull(callbacks.lastOrNull(), "Fresh source must reach the actual Foliate plan producer")
					val targets = readerPageRasterBlockingWindow(targetOrdinal, 1, 3).joinToString(",") { ordinal ->
						val priority = when (ordinal) {
							targetOrdinal -> "current"
							targetOrdinal + 1 -> "next-transition"
							targetOrdinal - 1 -> "previous-transition"
							else -> "current-chapter"
						}
						val authority = if (ordinal == targetOrdinal) "CurrentLive" else "OffscreenPassive"
						"""{"pageIndex":$ordinal,"priority":"$priority","authority":"$authority"}"""
					}
					val plan = """{"context":{"centerPageIndex":$targetOrdinal,"pageCount":3,"layoutMode":"single","step":1,"currentChapterIndex":0,"currentChapterPageStartIndex":0,"currentChapterPageCount":3},"targets":[$targets]}"""
					val parsedPlan = assertNotNull(readerPageRasterPreparationPlan(plan), "Synthetic source plan must satisfy the real parser")
					assertNotNull(parsedPlan.blockingTargetsOrNull(), "Synthetic source plan must contain the full required chapter/window")
					assertTrue(readerPageRasterCalibrationTargets(parsedPlan.targets).isNotEmpty())
					assertTrue(viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer))
					val profileResolved = kotlinx.coroutines.CompletableDeferred<Unit>()
					@Suppress("UNCHECKED_CAST")
					val originalProfile = controller.javaClass.task7Field("onRasterProfileEpochChanged")
						.get(controller) as (Long?) -> Unit
					controller.javaClass.task7Field("onRasterProfileEpochChanged").set(controller, { epoch: Long? ->
						originalProfile(epoch)
						if (epoch != null) profileResolved.complete(Unit)
					})
					assertTrue(adapter.isAvailable)
					assertSame(adapter, actualPortProvider())
					assertEquals(0, runtime.pendingCallbackCount)
					assertFalse(preparation.shouldSuppressViewerContentInput)
					assertFalse(controller.awaitingPresentationRecoverySnapshot)
					assertTrue(webView.isAttachedToWindow)
					assertEquals(targetOrdinal, preparation.javaClass.task7Field("currentVisualPageIndex").get(preparation))
					assertEquals(assertNotNull(binding.preparationGeneration) + 1L,
						preparation.javaClass.task7Field("preparationGeneration").getLong(preparation))
					val bundle = controller.javaClass.task7Field("bundleSource").get(controller) as ReaderPageTurnBundleSource
					assertSame(bundle, preparation.javaClass.task7Field("bundleSource").get(preparation))
					assertNotNull(bundle.retainedCurrentLayoutSnapshot(targetOrdinal, ReaderPageTurnTransitionKind.PortraitSlide),
						"Actual current-generation cache reference must be available before the worker plan").release()
					assertTrue(webView.width > 0 && webView.height > 0 && webView.width < webView.height * 1.12f,
						"Actual receiver WebView must match the single-page source plan; " +
							"source=${webView.width}x${webView.height} host=${viewer.width}x${viewer.height}")
					planCallback.onReceiveValue(plan)
					// Active pagination readiness is an output of the actual profile producer,
					// not a prerequisite while its retained source-plan callback is unanswered.
					if (viewerClass.task7Field("rasterProfileEpoch").get(viewer) == null) {
						withContext(Dispatchers.Default) { withTimeout(5_000L) { profileResolved.await() } }
					}
					assertTrue(viewerClass.task7Field("rasterPaginationReady").getBoolean(viewer))
					assertTrue(preparation.prewarmAdjacent(), "Admitted source must start fresh raster preparation; " +
						"coldAdmitted=${viewerClass.task7Field("coldOwnershipAdmitted").getBoolean(viewer)} " +
						"paginationReady=${viewerClass.task7Field("rasterPaginationReady").getBoolean(viewer)} " +
						"awaitingSource=${controller.awaitingPresentationRecoverySnapshot} " +
						"attached=${webView.isAttachedToWindow} passiveAvailable=${adapter.isAvailable} " +
						"destroyed=${preparation.javaClass.task7Field("destroyed").getBoolean(preparation)} " + recoveryScalars())
					val preparationPlan = assertNotNull(webView.planCallbacks.lastOrNull(), "Actual raster worker must query its source plan")
					webView.planCallbacks.clear()
					preparationPlan.onReceiveValue(plan)
					var lastBatchFailure: ReaderPageRasterBatchOutcome.Failed? = null
					for (ordinal in parsedPlan.targets.filter { it.authority == ReaderPageRasterTargetAuthority.OffscreenPassive }
						.map { it.pageIndex }) {
						val pendingCommit = withContext(Dispatchers.Default) {
							kotlinx.coroutines.withTimeoutOrNull(5_000L) { runtime.commits.receive() }
						}
						val state = viewerClass.task7Field("latestRasterPreparationState").get(viewer) as ReaderPagePreparationState
						val batch = adapter.javaClass.task7Field("activeBatch").get(adapter)
						val commit = assertNotNull(pendingCommit, "Physical commit missing; ordinal=$ordinal " +
							"phase=${state.phase} completed=${state.completedCount}/${state.requiredCount} " +
							"batch=${batch != null} targetIndex=${batch?.javaClass?.task7Field("targetIndex")?.get(batch)} " +
							"manifestRequests=${webView.manifestRequests} descriptorRequests=${webView.descriptorRequests} " +
							"staged=${bundle.ownershipMetrics().stagedPublications} " +
							"callbacks=${bundle.ownershipMetrics().pendingPublicationCallbacks} " +
							"admissionFailure=${lastBatchFailure?.passiveRasterRejection} " +
							"publicationResult=${lastBatchFailure?.persistentPublicationResult} " +
							"writeFailure=${lastBatchFailure?.persistentWriteFailureReason} " + recoveryScalars())
						val actualBatch = assertNotNull(batch)
						val completionField = actualBatch.javaClass.task7Field("onComplete")
						@Suppress("UNCHECKED_CAST")
						val originalCompletion = completionField.get(actualBatch) as (ReaderPageRasterBatchOutcome) -> Unit
						completionField.set(actualBatch, { outcome: ReaderPageRasterBatchOutcome ->
							lastBatchFailure = outcome as? ReaderPageRasterBatchOutcome.Failed
							originalCompletion(outcome)
						})
						assertEquals(ordinal, commit.manifest.visualPageOrdinal)
						assertEquals(bundle.currentGeneration(), commit.manifest.rasterGeneration)
						assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
						assertEquals(0, predecessorReleases)
						runtime.completeCommit(commit)
						val capture = withContext(Dispatchers.Default) {
							withTimeout(5_000L) { runtime.captures.receive() }
						}
						assertEquals(geometry, capture.geometry)
						runtime.completeCapture(capture)
					}
					assertEquals(assertNotNull(binding.preparationGeneration) + 1L,
						withContext(Dispatchers.Default) { withTimeout(5_000L) { rasterProofReady.await() } })
					assertEquals(0, runtime.pendingCallbackCount)
					val proofPlans = webView.planCallbacks.toList()
					webView.planCallbacks.clear()
					proofPlans.lastOrNull()?.onReceiveValue(plan)
					withContext(Dispatchers.Default) {
						withTimeout(5_000L) {
							(controller.javaClass.task7Field("rasterJob").get(controller) as Job).children.toList().joinAll()
						}
					}
					@Suppress("UNCHECKED_CAST")
					val owners = controller.javaClass.task7Field("generationOwners").get(controller) as Map<Long, Any>
					val activation = controller.javaClass.task7Field("lastActivationTrace").get(controller) as String?
					val activationEnums = Regex("(?:event|detail)=[a-zA-Z-]+")
						.findAll(activation.orEmpty()).map { it.value }.toList()
					val successorGeneration = assertNotNull(
						controller.javaClass.task7Field("activeDeckGenerationId").get(controller) as Long?,
						"Actual plan/raster pipeline must select a successor renderer generation; " +
							"generations=${owners.keys.sorted()} proofPlans=${proofPlans.size} remainingPlans=${webView.planCallbacks.size} " +
							"activation=$activationEnums " + recoveryScalars())
					assertTrue(successorGeneration != 301L && owners.containsKey(successorGeneration))
					producedRendererGenerations += owners.keys.filter { it != 301L }.sorted()
					@Suppress("UNCHECKED_CAST")
					val admissions = controller.javaClass.task7Field("generationAdmissions").get(controller)
						as Map<Long, ReaderDeckAdmission>
					val admissionScalars = "selected=$successorGeneration owners=${owners.keys.sorted()} " +
						"admissions=${admissions.map { (generation, admission) -> listOf(generation, admission.capability.presentationToken?.value, admission.capability.preparationGeneration, admission.capability.profileGeneration, admission.capability.rasterGeneration, admission.capability.textureGeneration, admission.state) }} " +
						"prepared=${controller.javaClass.task7Field("preparedDeckGenerations").get(controller)} " +
						"releases=$rendererReleaseCounts " + recoveryScalars()
					val retryRendererGenerations = producedRendererGenerations - preRetryRendererGenerations
					assertTrue(successorGeneration in retryRendererGenerations,
						"Selected successor must be produced after Retry, not imported/preclaim material; $admissionScalars")
					for (generation in retryRendererGenerations) {
						val admission = assertNotNull(admissions[generation], "Actual produced admission missing; generation=$generation $admissionScalars")
						assertEquals(retryEffect.token, admission.capability.presentationToken, "Actual produced admission token; generation=$generation $admissionScalars")
						assertEquals(assertNotNull(common.state.presentation.binding).copy(
							preparationGeneration = assertNotNull(binding.preparationGeneration) + 1L,
							rasterGeneration = bundle.currentGeneration(), textureGeneration = generation), admission.capability.originBinding)
					}
					// Drain only already-produced main callbacks before injecting any prepared
					// callback, to distinguish submission-time release from stale-tail handling.
					fun materialOwnershipScalars(): String {
						val leases = controller.surfaceView.javaClass.task7Field("leaseRegistry").get(controller.surfaceView)
						val inlineShield = controller.javaClass.task7Field("inlineRasterShield").get(controller) as ReaderPageInlineRasterShield
						val owner = readerPresentationDecision(common.state.presentation).frameOwner as? ReaderPresentationFrameOwner.Curl
						return "owners=${owners.keys.sorted()} leases=${leases.javaClass.task7Method("size").invoke(leases)} " +
							"lease301=${task8SurfaceOwnsGeneration(controller.surfaceView, 301L)} " +
							"leaseSelected=${task8SurfaceOwnsGeneration(controller.surfaceView, successorGeneration)} " +
							"staticShield=${preparation.hasStaticRasterShieldOwnership()} inlineShield=${inlineShield.ownsPresentation()} " +
							"commonFrameToken=${owner?.frame?.token?.value} commonTexture=${owner?.frame?.textureGeneration} " +
							"surfaceAlpha=${controller.surfaceView.alpha} inlineAlpha=${controller.inlineRasterShieldView.alpha}"
					}
					assertRetainedProjection("fresh-renderer-submission")
					if (projectionOnly) return@runTest
					val beforeReleaseDelivery = materialOwnershipScalars()
					ShadowLooper.runUiThreadTasks()
					val afterReleaseDelivery = materialOwnershipScalars()
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertEquals(0, predecessorReleases,
						"Predecessor must survive actual fresh submission before any prepared/frame proof; " +
							"before=[$beforeReleaseDelivery] after=[$afterReleaseDelivery] " +
							"releases=$rendererReleaseCounts reasons=$rendererReleaseReasons " + recoveryScalars())
					assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					val obsoleteGenerations = producedRendererGenerations.filter { it != successorGeneration }
					for (obsolete in obsoleteGenerations) {
						controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, obsolete)
						ShadowLooper.runUiThreadTasks()
						assertEquals(1, rendererReleaseCounts[obsolete])
						assertFalse(owners.containsKey(obsolete))
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, obsolete))
						assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
						assertEquals(0, predecessorReleases)
						assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
						assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, successorGeneration))
						controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, obsolete)
						assertEquals(1, rendererReleaseCounts[obsolete])
					}
					assertNull(rendererReleaseCounts[successorGeneration])
					if (retainedPauseInvalid != null) {
						val surface = controller.surfaceView
						val renderer = surface.javaClass.task7Field("renderer").get(surface)
						@Suppress("UNCHECKED_CAST")
						val selectedDeck = renderer.javaClass.task7Field("activeDeck").get(renderer) as PageDeck<Bitmap>
						assertEquals(301L, selectedDeck.generationId)
						assertEquals(301L, (renderer.javaClass.task7Field("portraitFrontResource").get(renderer) as PageImage<*>).generationId)
						assertNull(surface.javaClass.task7Field("nativeFrameRequest").get(surface),
							"Pause must test retained-material validation, not candidate-cancellation rollback")
						val releaseGate = surface.javaClass.task7Field("releaseGate").get(surface)
						fun selectedReleaseState() = releaseGate.javaClass.task7Method("stateFor", java.lang.Long.TYPE)
							.invoke(releaseGate, 301L)?.toString()
						assertEquals("QUEUE_ACCEPTED", selectedReleaseState())
						assertTrue(surface.javaClass.task7Field("attached").getBoolean(surface))
						assertTrue(controller.javaClass.task7Method("generationBacksCommonPresentation", java.lang.Long.TYPE)
							.invoke(controller, 301L) as Boolean)
						selectedFailureOrder.clear()
						if (retainedPauseInvalid) selectedDeck.pages.first { !it.isFiller }.content.recycle()
						// The actual pause validates retained client material after the GL thread stops.
						surface.detach()
						ShadowLooper.runUiThreadTasks()
						val decision = readerPresentationDecision(common.state.presentation)
						if (retainedPauseInvalid) {
							val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(decision.diagnosticPresentation,
								"Invalid selected pause must deliver actionable failure before retiring its registered owner")
							assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
							assertTrue(diagnostic.retryable)
							assertFalse(diagnostic.cancellable)
							assertEquals(listOf("failure", "renderer-lost", "terminal", "proof-removed"), selectedFailureOrder)
							assertEquals(listOf(true), selectedFailureOwned)
							assertEquals(ReaderPresentationFrameOwner.Neutral, decision.frameOwner)
							assertEquals(1, predecessorReleases)
							assertEquals(1, rendererReleaseCounts[301L])
							assertFalse(owners.containsKey(301L))
							assertNull(selectedReleaseState())
						} else {
							assertNull(common.state.presentation.failure)
							assertEquals(retained, decision.frameOwner)
							assertTrue(task8SurfaceOwnsGeneration(surface, 301L))
							assertEquals("QUEUE_ACCEPTED", selectedReleaseState())
							assertSame(selectedDeck, renderer.javaClass.task7Field("activeDeck").get(renderer))
							assertEquals(0, predecessorReleases)
							assertTrue(selectedFailureOrder.isEmpty())
						}
						val paused = common.state
						surface.detach()
						ShadowLooper.runUiThreadTasks()
						assertEquals(paused, common.state)
						return@runTest
					}
					controller.surfaceView.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, successorGeneration)
					ShadowLooper.runUiThreadTasks()
					assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
					assertEquals(0, predecessorReleases)
					val candidate = assertNotNull(viewerClass.task7Method("currentNativePagePresentationCandidateOrNull")
						.invoke(viewer) as ReaderNativePagePresentationCandidate?,
						"Actual fresh raster/deck outputs must reach the native publisher candidate")
					assertEquals(successorGeneration, candidate.binding.textureGeneration)
					assertEquals(assertNotNull(binding.preparationGeneration) + 1L, candidate.binding.preparationGeneration)
					val physicalRenderer = controller.surfaceView.javaClass.task7Field("renderer").get(controller.surfaceView)
					val physicalActive = physicalRenderer.javaClass.task7Field("activeDeck").get(physicalRenderer) as karacken.curl.PageDeck<*>
					assertEquals(successorGeneration, physicalActive.generationId,
						"The actual publisher's authorized queue must activate the candidate before its proof draw")
					val physicalFront = physicalRenderer.javaClass.task7Field("portraitFrontResource").get(physicalRenderer) as karacken.curl.PageImage<*>
					assertEquals(successorGeneration, physicalFront.generationId)
					val physicalRetained = physicalRenderer.javaClass.task7Field("replacementDeck").get(physicalRenderer) as karacken.curl.PageDeck<*>
					assertEquals(301L, physicalRetained.generationId)
					val frames = controller.surfaceView.javaClass.task7Field("presentedFrameRequest").get(controller.surfaceView)
					val completion = frames.javaClass.task7Method("markRendered").invoke(frames) as Long
					if (livenessBoundary == Task9LivenessBoundary.MissingFrame ||
						livenessBoundary == Task9LivenessBoundary.HiddenFrame) {
						assertEquals(2, retryDeadlinePosts)
						assertEquals(retryDeadlinePosts, deadlines.postCount,
							"Source/profile/raster/deck continuation must retain the original Retry deadline")
						if (livenessBoundary == Task9LivenessBoundary.HiddenFrame) {
							val token = readerPresentationDecision(common.state.presentation).pendingTransitionToken
							nowMillis += 4_000L
							viewerClass.task7Method("onWindowVisibilityChanged", Integer.TYPE).invoke(viewer, View.INVISIBLE)
							ShadowLooper.runUiThreadTasks()
							assertFalse(deadlines.hasPending)
							assertEquals(ReaderPresentationLifecycleState.Background, common.state.presentation.lifecycle)
							assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
							assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
							assertEquals(0, predecessorReleases)
							nowMillis += 40_000L
							controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, completion)
							assertNull(common.state.presentation.failure)
							assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
							viewerClass.task7Method("onWindowVisibilityChanged", Integer.TYPE).invoke(viewer, View.VISIBLE)
							ShadowLooper.runUiThreadTasks()
							assertEquals(ReaderPresentationLifecycleState.Foreground, common.state.presentation.lifecycle)
							assertEquals(token, readerPresentationDecision(common.state.presentation).pendingTransitionToken)
							assertEquals(6_000L, deadlineDelayMillis)
							assertEquals(retryDeadlinePosts + 1, deadlines.postCount)
						}
						verifyTimeoutRetryCancel(afterTimeout = {
							if (livenessBoundary == Task9LivenessBoundary.MissingFrame) {
								val lastRequest = frames.javaClass.task7Field("lastRequestId").getLong(frames)
								val proofEvents = events.filterIsInstance<ReaderPresentationEvent.NativePagePresented>().size
								// Replay the current failed Compose model through the real receiving port.
								updateDestination(common.state.presentation.binding?.destinationCommitIdentity)
								ShadowLooper.runUiThreadTasks()
								controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
									.invoke(controller.surfaceView, completion)
								repeat(2) {
									val nextCompletion = frames.javaClass.task7Method("markRendered").invoke(frames) as Long
									controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
										.invoke(controller.surfaceView, nextCompletion)
									ShadowLooper.runUiThreadTasks()
								}
								assertEquals(lastRequest, frames.javaClass.task7Field("lastRequestId").getLong(frames),
									"Failed Compose replay must cancel, not register tokenless replacement frames")
								assertNull(viewerClass.task7Method("currentNativePagePresentationCandidateOrNull").invoke(viewer))
								val publisher = viewerClass.task7Field("nativePagePresentationPublisher\$delegate").get(viewer) as Lazy<*>
								val nativePublisher = assertNotNull(publisher.value)
								assertNull(nativePublisher.javaClass.task7Field("pendingFrame").get(nativePublisher))
								assertNull(controller.surfaceView.javaClass.task7Field("nativeFrameRequest").get(controller.surfaceView))
								assertEquals(301L, (physicalRenderer.javaClass.task7Field("activeDeck")
									.get(physicalRenderer) as PageDeck<*>).generationId)
								assertEquals(301L, (physicalRenderer.javaClass.task7Field("portraitFrontResource")
									.get(physicalRenderer) as PageImage<*>).generationId)
								assertEquals(proofEvents, events.filterIsInstance<ReaderPresentationEvent.NativePagePresented>().size)
							}
						}) {
							controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
								.invoke(controller.surfaceView, completion)
							callbacks.forEach { it.onReceiveValue(plan) }
						}
						return@runTest
					}
					if (failRetainedSelected) {
						val failedRequest = assertIs<ReaderPresentationAuthority.BlockingPreparation>(common.state.presentation.authority)
							.nativePresentationRequest
						assertEquals(candidate.binding, assertNotNull(failedRequest).binding)
						assertEquals(retained, readerPresentationDecision(common.state.presentation).frameOwner)
						assertTrue(controller.javaClass.task7Method("generationBacksCommonPresentation", java.lang.Long.TYPE)
							.invoke(controller, 301L) as Boolean)
						assertFalse(controller.javaClass.task7Field("activeDeckGenerationId").get(controller) == 301L)
						assertFalse(controller.javaClass.task7Field("pendingDeckGenerationId").get(controller) == 301L)
						@Suppress("UNCHECKED_CAST")
						val failedDeck = physicalRetained as PageDeck<Bitmap>
						failedDeck.pages.first { !it.isFiller }.content.recycle()
						assertFalse(physicalRenderer.javaClass.task7Method("rehydrateDeck",
							PageDeck::class.java, PageDeck::class.java, PageDeck::class.java)
							.invoke(physicalRenderer, failedDeck, physicalActive, failedDeck) as Boolean)
						ShadowLooper.runUiThreadTasks()
						val failureDecision = readerPresentationDecision(common.state.presentation)
						val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(failureDecision.diagnosticPresentation,
							"Actual selected retained-material failure must reach current authority")
						assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
						assertTrue(diagnostic.retryable)
						assertFalse(diagnostic.cancellable)
						assertEquals(1, events.count {
							it is ReaderPresentationEvent.Lifecycle && it.event == ReaderPresentationLifecycleEvent.RendererLost
						})
						assertEquals(ReaderPresentationFrameOwner.Neutral, failureDecision.frameOwner)
						assertEquals(candidate.binding, common.state.presentation.binding)
						assertEquals(failedRequest, assertIs<ReaderPresentationAuthority.BlockingPreparation>(failureDecision.authority)
							.nativePresentationRequest)
						assertEquals(1, events.filterIsInstance<ReaderPresentationEvent.BindingReplaced>().count {
							it.previousBinding == retained.frame.binding &&
								it.binding == retained.frame.binding.copy(rasterGeneration = null, textureGeneration = null)
						})
						assertEquals(1, predecessorReleases)
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
						assertFalse(owners.containsKey(301L))
						val failed = common.state
						controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
							.invoke(controller.surfaceView, completion)
						callbacks.forEach { it.onReceiveValue(plan) }
						ShadowLooper.runUiThreadTasks()
						assertEquals(failed, common.state)
						assertEquals(1, predecessorReleases)
						return@runTest
					}
					controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, completion)
					assertIs<ReaderPresentationAuthority.SettledNativePage>(common.state.presentation.authority)
					// Selection moves synchronously; the renderer terminal is posted to main.
					ShadowLooper.runUiThreadTasks()
					assertEquals(1, predecessorReleases)
					val successor = common.state
					callbacks.forEach { it.onReceiveValue(plan) }
					controller.surfaceView.javaClass.task7Method("handlePresentedFrame", java.lang.Long.TYPE)
						.invoke(controller.surfaceView, completion)
					assertEquals(successor, common.state)
					assertEquals(1, predecessorReleases)
				}
			} catch (failure: Throwable) {
				bodyFailure = failure
				throw failure
			} finally {
				sustainedBridge?.dispose()
				assertFalse(deadlines.hasPending)
				try {
					controller.surfaceView.detach()
					donor?.controller?.surfaceView?.detach()
					if (nativePipeline) controller.surfaceView.surfaceDestroyed(controller.surfaceView.holder)
					viewerClass.task7Method("closeReader").invoke(viewer)
					val owners = listOfNotNull(preparation.destroy(), controller.destroy(), bundle.close(),
						donor?.controller?.destroy())
					withContext(Dispatchers.Default) { withTimeout(5_000L) { owners.forEach { it.await() } } }
					ShadowLooper.runUiThreadTasks()
					assertEquals(0, controller.surfaceView.pendingCallbackCount)
					assertEquals(0, controller.applicationOwnershipMetrics().pendingVisualCallbacks)
					if (retainedPauseInvalid != null && bodyFailure == null) {
						assertEquals(1, rendererReleaseCounts[301L], "Terminal close must retire retained selection exactly once")
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
					}
					for (generation in producedRendererGenerations) {
						assertEquals(1, rendererReleaseCounts[generation])
						assertFalse(task8SurfaceOwnsGeneration(controller.surfaceView, generation))
					}
					assertTrue(runtime.isRetired)
					assertEquals(0, runtime.pendingCallbackCount)
					assertEquals(0, bundle.ownershipMetrics().pendingPublicationCallbacks)
					assertTrue(runtime.deliveredBitmaps.all { it.isRecycled })
				} catch (cleanupFailure: Throwable) {
					val original = bodyFailure
					if (original == null) throw cleanupFailure
					if (original !== cleanupFailure) original.addSuppressed(cleanupFailure)
				} finally {
					runtime.destroy()
					webView.sourceBinding = null
					webView.planCallbacks.clear()
					activity.pause().stop().destroy()
				}
			}
		} finally {
			Dispatchers.resetMain()
		}
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun task9RealCurlSettlementTimeoutRetainsRendererAndBoundsFreshRecovery() {
		var releases = 0
		val fixture = task8CurlAuthorityFixture(
			task8SettledCurlSourceState(),
			onActiveRasterReleased = { releases += 1 }
		)
		val controller = fixture.controller
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(requireNotNull(fixture.store.state?.binding)) { decision ->
				controller.synchronizePresentationDecision(decision)
			},
			onEvent = fixture.store::publish
		)
		try {
			assertEquals(ReaderPageTurnStartResult.Settling,
				controller.start(220L, PageChange.NEXT) { _, _ -> true })
			controller.cancelGesture(220L)
			val pending = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertIs<ReaderPresentationAuthority.CurlSettlementPending>(pending.authority)
			val material = pending.frameOwner
			val alpha = controller.surfaceView.alpha
			bridge.update(pending)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			val timedOut = readerPresentationDecision(requireNotNull(fixture.store.state))
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(timedOut.diagnosticPresentation).reason)
			assertEquals(material, timedOut.frameOwner)
			assertEquals(alpha, controller.surfaceView.alpha)
			assertTrue(task8SurfaceOwnsGeneration(controller.surfaceView, 301L))
			assertEquals(0, releases)
			val retryReceipt = requireNotNull(fixture.store.publish(ReaderPresentationEvent.Retry))
			val retry = readerPresentationDecision(retryReceipt.postState)
			assertIs<ReaderPresentationAuthority.BlockingPreparation>(retry.authority)
			assertEquals(material, retry.frameOwner)
			bridge.update(retry)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
				.idleFor(java.time.Duration.ofSeconds(10))
			assertEquals(ReaderPresentationFailureReason.TimedOut,
				assertIs<ReaderDiagnosticPresentation.Failure>(
					readerPresentationDecision(requireNotNull(fixture.store.state)).diagnosticPresentation).reason)
			assertEquals(0, releases)
			assertEquals(0, fixture.store.presentationEvents.count {
				it is ReaderPresentationEvent.Lifecycle &&
					it.event == ReaderPresentationLifecycleEvent.RendererLost
			})
		} finally { bridge.dispose() }
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun acceptedTapClaimPublishesItsExactCancellationTerminalOnce() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val gestureId = 204L
		val tapTerminals = mutableListOf<ReaderPageGestureTerminalOutcome>()

		assertEquals(
			ReaderPageTurnStartResult.Settling,
			fixture.controller.start(gestureId, PageChange.NEXT) { outcome, _ ->
				tapTerminals += outcome
				true
			}
		)
		assertIs<ReaderPresentationAuthority.CurlGesture>(
			fixture.store.state?.authority
		)

		fixture.controller.cancelGesture(gestureId)

		assertEquals(listOf(ReaderPageGestureTerminalOutcome.CancelledByUser), tapTerminals)
		assertTrue(fixture.store.localTerminalGestureIds.isEmpty())
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlClaimed
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
			fixture.store.state?.authority
		)
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun currentRendererFailureRetainsBridgeSelectedCurlWhileCleaningGestureOnce() {
		val fixture = task8CurlAuthorityFixture(task8SettledCurlSourceState())
		val recoveredGenerations = fixture.controller.javaClass
			.task7Field("recoveredDeckGenerations")
			.get(fixture.controller) as MutableSet<Long>
		assertTrue(recoveredGenerations.remove(301L))
		val projection = task8BindRendererFailureProjection(fixture)
		val gestureId = 206L
		val down = task8MotionEvent(MotionEvent.ACTION_DOWN)
		assertEquals(
			ReaderPageCurlDispatchResult.Accepted,
			fixture.controller.onPageTouchEvent(down, gestureId)
		)
		assertEquals(1f, fixture.controller.surfaceView.alpha)

		task8DeliverSurfaceRenderFailure(
			fixture.controller.surfaceView,
			RenderFailure(
				301L,
				true,
				RenderFailureReason.TEXTURE_UPLOAD,
				"critical-5-current",
				null
			)
		)

		val decision = readerPresentationDecision(requireNotNull(fixture.store.state))
		val retained = assertIs<ReaderPresentationFrameOwner.Curl>(decision.frameOwner)
		assertEquals(gestureId, retained.frame.token.value)
		val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(
			decision.diagnosticPresentation
		)
		assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
		assertEquals(decision, projection.projectedDecisions.last())
		assertEquals(1f, fixture.controller.surfaceView.alpha)
		assertEquals(listOf(gestureId), fixture.store.localTerminalGestureIds)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.CurlTerminal
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.Lifecycle &&
				it.event == ReaderPresentationLifecycleEvent.RendererLost
		})
		assertEquals(0, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.LiveEngineExposureCommitted
		})
		assertEquals(0, task8RelocationOccupiedCount(fixture.controller))
		assertFalse(task8SurfaceGestureAccepted(fixture.controller.surfaceView))
		assertTrue(task8SurfaceOwnsGeneration(fixture.controller.surfaceView, 301L))
		projection.bridge.dispose()
		down.recycle()
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun destructiveActiveRendererRehydrationProjectsNeutralAfterActualRelease() {
		var rasterReleaseCount = 0
		val fixture = task8CurlAuthorityFixture(
			initialState = task8SettledCurlSourceState(),
			onActiveRasterReleased = { rasterReleaseCount += 1 }
		)
		val surface = fixture.controller.surfaceView
		val recoveredGenerations = fixture.controller.javaClass
			.task7Field("recoveredDeckGenerations")
			.get(fixture.controller) as MutableSet<Long>
		assertTrue(recoveredGenerations.remove(301L))
		val attachedHost = FrameLayout(RuntimeEnvironment.getApplication()).apply {
			addView(surface)
		}
		val activityController = Robolectric.buildActivity(Activity::class.java).setup()
		activityController.get().setContentView(attachedHost)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
		assertTrue(surface.isAttachedToWindow)
		val projection = task8BindRendererFailureProjection(fixture)
		assertEquals(1f, surface.alpha)

		task8FailActiveRendererRehydration(surface)

		assertFalse(task8SurfaceOwnsGeneration(surface, 301L))
		val owners = fixture.controller.javaClass.task7Field("generationOwners")
			.get(fixture.controller) as MutableMap<Long, Any>
		assertFalse(owners.containsKey(301L))
		assertEquals(1, rasterReleaseCount)
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.Lifecycle &&
				it.event == ReaderPresentationLifecycleEvent.RendererLost
		})
		assertEquals(1, fixture.store.presentationEvents.count {
			it is ReaderPresentationEvent.BindingReplaced
		})
		val decision = readerPresentationDecision(requireNotNull(fixture.store.state))
		val blocking = assertIs<ReaderPresentationAuthority.BlockingPreparation>(
			decision.authority
		)
		assertEquals(ReaderPresentationFrameOwner.Neutral, blocking.retainedFrame)
		assertEquals(ReaderPresentationFrameOwner.Neutral, decision.frameOwner)
		assertNull(decision.targetBinding?.rasterGeneration)
		assertNull(decision.targetBinding?.textureGeneration)
		val diagnostic = assertIs<ReaderDiagnosticPresentation.Failure>(
			decision.diagnosticPresentation
		)
		assertEquals(ReaderPresentationFailureReason.RendererLost, diagnostic.reason)
		assertEquals(decision, projection.projectedDecisions.last())
		assertEquals(0f, surface.alpha)
		projection.bridge.dispose()
		activityController.pause().stop().destroy()
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun rendererLossNeutralOwnerHidesReleasedMaterialOnlyThroughBridgeProjection() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val applyFrameOwner = viewerClass.task7Method(
			"applyPresentationFrameOwner",
			paige.navic.reader.ReaderPresentationDecision::class.java
		)
		val controller = viewerClass.task7Field("playLikeCurlController").get(viewer) as
			ReaderPlayLikeCurlFoliateController
		controller.surfaceView.alpha = 1f
		controller.inlineRasterShieldView.alpha = 1f
		val deckless = requireNotNull(task8SettledCurlSourceState().binding).copy(
			rasterGeneration = null,
			textureGeneration = null
		)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral
			),
			binding = deckless,
			failure = ReaderDiagnosticPresentation.Failure(
				reason = ReaderPresentationFailureReason.RendererLost,
				retryable = true,
				cancellable = false
			)
		)
		val decision = readerPresentationDecision(state)
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(deckless) { projected ->
				applyFrameOwner.invoke(viewer, projected)
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(Task7VisualHandoffHost()),
			liveEngineExposureRequired = { false }
		) { event -> readerTestPresentationReceipt(event, state) }

		bridge.update(decision)

		assertEquals(0f, controller.surfaceView.alpha)
		assertEquals(0f, controller.inlineRasterShieldView.alpha)
		bridge.dispose()
		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	@Config(
		manifest = Config.NONE,
		sdk = [Build.VERSION_CODES.P],
		shadows = [Task8ImmediateGlSurfaceViewShadow::class]
	)
	fun staleRendererFailureReleasesOnlyStaleControllerOwnershipWithoutHidingCurrentFrame() {
		val initial = task8SettledCurlSourceState()
		val fixture = task8CurlAuthorityFixture(initial)
		val projection = task8BindRendererFailureProjection(fixture)
		val controllerClass = fixture.controller.javaClass
		val activePages = checkNotNull(
			controllerClass.task7Field("activePages").get(fixture.controller)
		)
		val generations = activePages.javaClass.task7Field("generations")
			.get(activePages) as MutableSet<Long>
		val owners = controllerClass.task7Field("generationOwners")
			.get(fixture.controller) as MutableMap<Long, Any>
		val staleGeneration = 302L
		generations += staleGeneration
		owners[staleGeneration] = activePages
		val failure = RenderFailure(
			staleGeneration,
			true,
			RenderFailureReason.TEXTURE_UPLOAD,
			"critical-5-stale",
			null
		)

		fixture.controller.surfaceView.pageSurfaceListener.onRenderFailure(failure)
		assertFalse(owners.containsKey(staleGeneration))
		assertFalse(generations.contains(staleGeneration))
		fixture.controller.surfaceView.pageSurfaceListener.onRenderFailure(failure)

		assertEquals(initial, fixture.store.state)
		assertTrue(fixture.store.presentationEvents.isEmpty())
		assertEquals(1f, fixture.controller.surfaceView.alpha)
		assertEquals(1, projection.projectedDecisions.size)
		assertTrue(owners.containsKey(301L))
		assertTrue(task8SurfaceOwnsGeneration(fixture.controller.surfaceView, 301L))
		projection.bridge.dispose()
	}

	@Test
	fun exactCurlRelocationSkipsLiveExposureAndReleasesTransportBeforeNativeProof() {
		val queue = ReaderPageRelocationQueue()
		val first = enqueueTask7Relocation(
			queue = queue,
			gestureId = 81L,
			sourceOrdinal = 3,
			destinationOrdinal = 4,
			foliateSessionId = "curl-native-settlement-session"
		)
		val second = enqueueTask7Relocation(
			queue = queue,
			gestureId = 82L,
			sourceOrdinal = 4,
			destinationOrdinal = 5,
			foliateSessionId = first.foliateSessionId
		)
		assertEquals(first, queue.commandToDispatch())
		val sourceBinding = ReaderPresentationBinding(
			foliateSessionId = first.foliateSessionId,
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 4L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.sourceOrdinal.toLong()
			),
			rasterGeneration = 9L,
			textureGeneration = 19L,
			preparationGeneration = 7L
		)
		val destinationBinding = sourceBinding.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				first.foliateSessionId,
				first.destinationOrdinal.toLong()
			),
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val acknowledgement = ReaderPageTurnSettlementAck(
			token = first.token.value,
			pageIndex = first.destinationOrdinal,
			foliateSessionId = first.foliateSessionId,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration
		)
		val sourceProof = ReaderNativePagePresentationProof(
			binding = sourceBinding,
			transitionToken = null,
			presentedFrame = 80L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
			textureGeneration = requireNotNull(sourceBinding.textureGeneration)
		)
		val settled = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(sourceProof)
			),
			binding = sourceBinding
		)
		val claimed = readerPresentationReduce(
			settled,
			requireNotNull(
				readerCurlClaimEvent(
					readerPresentationDecision(settled),
					first.gestureId
				)
			)
		).state
		val awaitingFoliate = readerPresentationReduce(
			claimed,
			ReaderPresentationEvent.CurlTerminal(
				token = assertIs<ReaderPresentationAuthority.CurlGesture>(
					claimed.authority
				).frame.frame.token,
				binding = sourceBinding,
				expectedAcknowledgement = acknowledgement
			)
		).state
		val presentation = Task7PresentationStore(awaitingFoliate)
		val foregroundOwnership = ReaderForegroundWebViewOwnership()
		val transportDispatches = mutableListOf<ReaderPageRelocationRequest>()
		val liveDispatch = ReaderPageRelocationLiveDispatchCoordinator(
			foregroundWebViewOwnership = foregroundOwnership,
			isDispatchCurrent = { request ->
				request == queue.head() && queue.hasInFlightHead()
			},
			dispatchExact = { request, _ ->
				transportDispatches += request
				ReaderPageRelocationExactDispatchResult.Dispatched
			},
			onRejected = { _, _ -> }
		)
		val firstClaim = foregroundOwnership.acquireLive(first.gestureId)
		val secondClaim = foregroundOwnership.acquireLive(second.gestureId)
		assertTrue(liveDispatch.transfer(first, firstClaim))
		assertTrue(liveDispatch.transfer(second, secondClaim))
		assertTrue(liveDispatch.dispatch(first))
		var currentWebViewOrdinal = first.sourceOrdinal
		val webViewRequests = mutableListOf<ReaderPresentationEvent.WebViewHandoffRequested>()
		val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val hostEffects = mutableListOf<ReaderPresentationFrameOwner>()
		val visualHost = Task7VisualHandoffHost()
		val commitHost = FakeReaderPresentationCommitHost(sourceBinding) { decision ->
			hostEffects += decision.frameOwner
		}
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = visualHost,
			currentState = {
				ReaderPageRelocationVisualState(
					attached = true,
					resumed = true,
					foliateSessionId = first.foliateSessionId,
					webViewOrdinal = currentWebViewOrdinal,
					rasterGeneration = first.rasterGeneration,
					textureGeneration = first.textureGeneration
				)
			},
			dispatch = { request ->
				check(liveDispatch.dispatch(request))
			},
			publishRecovery = { request, reason ->
				recoveries += reason
				liveDispatch.fail(
					request,
					ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
				)
			},
			finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
			validateContent = { _, _ ->
				error("Curl settlement must not validate WebView content")
			},
			requestPresentationHandoff = {
				val event = ReaderPresentationEvent.WebViewHandoffRequested(
					ReaderLiveEngineHandoffDirection.NativeToLiveEngine
				)
				webViewRequests += event
				val receipt = presentation.publish(event)
				if (!receipt.authorizes(event)) {
					null
				} else {
					(readerPresentationDecision(receipt.postState).requiredTransition as?
						ReaderRequiredTransition.ExposeLiveEngine)
				}
			},
			commitLiveEngineExposure = { proof ->
				presentation.publish(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
			},
			publishLiveEngineHandoffTerminal = presentation::publish,
			onCompleted = { request ->
				completed += request
				check(liveDispatch.complete(request))
			}
		)
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineExposureRequired = { false },
			onEvent = presentation::publish
		)
		val bindingReporter = ReaderPresentationBindingReporter()
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			bindingReporter.update(
				confirmedTargetBinding = sourceBinding,
				currentBinding = destinationBinding,
				publicationOpenPending = false,
				relocationPending = true,
				relocationAcknowledgement = acknowledgement
			)
		)
		currentWebViewOrdinal = first.destinationOrdinal
		val relocationReceipt = presentation.publish(relocation)
		assertEquals(ReaderPresentationEventDisposition.Accepted, relocationReceipt.disposition)
		val awaitingNativeDecision = readerPresentationDecision(presentation.state)
		commitHost.currentBinding = destinationBinding
		bridge.update(awaitingNativeDecision)
		val awaitingNative = assertIs<ReaderPresentationAuthority.CurlSettlementPending>(
			awaitingNativeDecision.authority
		)
		assertEquals(ReaderCurlSettlementStage.AwaitingNativePresentation, awaitingNative.stage)
		val nativeTransition = assertIs<ReaderRequiredTransition.PresentNativePage>(
			awaitingNativeDecision.requiredTransition
		)
		assertTrue(
			queue.acknowledge(
				first.token.value,
				first.destinationOrdinal,
				first.foliateSessionId,
				first.rasterGeneration,
				first.textureGeneration
			)
		)

		val settledByAuthority =
			coordinator.synchronizeCommonPresentationDecision(awaitingNativeDecision)
		val acknowledged = settledByAuthority || coordinator.onAcknowledged(first)
		if (!acknowledged) {
			liveDispatch.fail(
				first,
				ReaderPageRelocationDiagnosticRejectionReason.OwnershipInvalidated
			)
		}

		assertTrue(
			webViewRequests.isEmpty(),
			"Exact curl acknowledgement requested obsolete WebView exposure"
		)
		assertTrue(recoveries.isEmpty())
		assertEquals(listOf(first), completed)
		assertEquals(second, queue.head())
		assertFalse(queue.hasDispatchedHead())
		assertEquals(listOf(first), transportDispatches)
		assertEquals(1, foregroundOwnership.snapshot().liveClaims)
		assertFalse(liveDispatch.isCurrent(second))
		assertEquals(awaitingNative.retainedFrame, awaitingNativeDecision.frameOwner)
		assertIs<ReaderPresentationFrameOwner.Curl>(hostEffects.last())
		assertFalse(coordinator.synchronizeCommonPresentationDecision(awaitingNativeDecision))
		assertEquals(listOf(first), completed)
		assertFalse(queue.hasDispatchedHead())
		assertEquals(listOf(first), transportDispatches)

		var nativeCandidate = ReaderNativePagePresentationCandidate(
			binding = destinationBinding,
			transitionToken = ReaderPresentationToken(nativeTransition.token.value + 1L),
			visualPageIndex = first.destinationOrdinal,
			viewportWidth = 1200,
			viewportHeight = 800,
			preparationFacts = ReaderPagePreparationFacts(
				phase = ReaderPagePreparationPhase.Ready,
				generation = requireNotNull(destinationBinding.preparationGeneration)
			)
		)
		val frameSource = Task8PresentedFrameSource()
		val nativeReceipts = mutableListOf<ReaderPresentationEventReceipt>()
		val nativePublisher = ReaderNativePagePresentationPublisher(
			frameSource = frameSource,
			currentCandidate = { nativeCandidate },
			onEvent = { event ->
				presentation.publish(event).also(nativeReceipts::add)
			}
		)
		nativePublisher.update()
		frameSource.presentNext()

		val staleNativeReceipt = nativeReceipts.single()
		assertEquals(ReaderPresentationEventDisposition.Stale, staleNativeReceipt.disposition)
		val afterStaleProof = readerPresentationDecision(presentation.state)
		assertFalse(coordinator.synchronizeCommonPresentationDecision(afterStaleProof))
		bridge.update(afterStaleProof)
		assertFalse(queue.hasDispatchedHead())
		assertEquals(listOf(first), transportDispatches)
		assertIs<ReaderPresentationFrameOwner.Curl>(hostEffects.last())

		nativeCandidate = nativeCandidate.copy(transitionToken = nativeTransition.token)
		nativePublisher.update()
		frameSource.presentNext()
		val nativeReceipt = nativeReceipts.last()
		val nativeEvent = assertIs<ReaderPresentationEvent.NativePagePresented>(nativeReceipt.event)
		val nativeProof = nativeEvent.proof
		assertEquals(ReaderPresentationEventDisposition.Accepted, nativeReceipt.disposition)
		val nativeDecision = readerPresentationDecision(presentation.state)
		assertTrue(coordinator.synchronizeCommonPresentationDecision(nativeDecision))
		bridge.update(nativeDecision)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.state.authority)
		assertEquals(second, queue.head())
		assertTrue(queue.hasDispatchedHead())
		assertEquals(listOf(first, second), transportDispatches)
		assertEquals(1, foregroundOwnership.snapshot().liveClaims)
		assertTrue(liveDispatch.isCurrent(second))
		assertEquals(listOf(first), completed)
		assertEquals(
			ReaderPresentationFrameOwner.NativePage(nativeProof),
			hostEffects.last()
		)

		val duplicateNativeReceipt = presentation.publish(nativeEvent)
		assertEquals(
			ReaderPresentationEventDisposition.Idempotent,
			duplicateNativeReceipt.disposition
		)
		assertFalse(
			coordinator.synchronizeCommonPresentationDecision(
				readerPresentationDecision(presentation.state)
			)
		)
		assertEquals(listOf(first, second), transportDispatches)
		assertEquals(listOf(first), completed)
		assertEquals(1, foregroundOwnership.snapshot().liveClaims)
		assertTrue(liveDispatch.complete(second))
		assertEquals(0, foregroundOwnership.snapshot().liveClaims)
		nativePublisher.dispose()
		coordinator.close()
		bridge.dispose()
		foregroundOwnership.close()
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun nativeToLiveModeToggleRetainsNativePresentationUntilExactLiveReceipt() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val setCanvasEnabled = viewerClass.task7Method(
			"setPageTurnCanvasEnabled",
			requireNotNull(Boolean::class.javaPrimitiveType)
		)
		val requiresLiveHandoff = viewerClass.task7Method(
			"requiresLiveEngineExposureHandoff"
		)
		val applyFrameOwner = viewerClass.task7Method(
			"applyPresentationFrameOwner",
			paige.navic.reader.ReaderPresentationDecision::class.java
		)
		val controller = viewerClass.getDeclaredField("playLikeCurlController").run {
			isAccessible = true
			get(viewer)
		}
		val controllerEnabled = controller.javaClass.getDeclaredField("enabled").apply {
			isAccessible = true
		}
		fun nativeControllerEnabled(): Boolean = controllerEnabled.getBoolean(controller)

		val binding = ReaderPresentationBinding(
			"mode-toggle-session", 1L, 2L, 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 7L, 1200, 800, 4L, 5L
		)
		var presentation = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 8L
		)
		val visualHost = Task7VisualHandoffHost()
		val events = mutableListOf<ReaderPresentationEvent>()
		val deadlines = HostBridgeDeadlineScheduler()
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding) { decision ->
				applyFrameOwner.invoke(viewer, decision)
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(visualHost),
			transitionTimeoutScheduler = deadlines,
			transitionNowMillis = { 0L },
			liveEngineExposureRequired = { requiresLiveHandoff.invoke(viewer) as Boolean }
		) { event ->
			events += event
			presentation = readerPresentationReduce(presentation, event).state
			readerTestPresentationReceipt(event, presentation)
		}

		setCanvasEnabled.invoke(viewer, true)
		assertTrue(nativeControllerEnabled())
		bridge.update(readerPresentationDecision(presentation))
		assertTrue(events.isEmpty())

		setCanvasEnabled.invoke(viewer, false)
		bridge.update(readerPresentationDecision(presentation))

		assertIs<ReaderPresentationEvent.WebViewHandoffRequested>(events.single())
		val pending = readerPresentationDecision(presentation)
		assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(pending.authority)
		assertIs<ReaderPresentationFrameOwner.NativePage>(pending.frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(pending.inputPolicy)
		assertTrue(nativeControllerEnabled())

		assertEquals(0, visualHost.delayedPostCount)
		deadlines.runPending()
		val failed = readerPresentationDecision(presentation)
		assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(failed.authority)
		assertIs<ReaderPresentationFrameOwner.NativePage>(failed.frameOwner)
		assertIs<ReaderPresentationInputPolicy.NativePage>(failed.inputPolicy)
		assertTrue(nativeControllerEnabled())
		assertFalse(visualHost.takeVisual().deliver())

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		bridge.update(readerPresentationDecision(presentation))
		visualHost.deliverVisual()
		visualHost.presentFrame()

		assertIs<ReaderPresentationAuthority.LiveEngineExposed>(presentation.authority)
		assertFalse(nativeControllerEnabled())
		bridge.dispose()
		viewerClass.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(viewer)
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun nativeModeRequestArmsWholeHandbackBeforeNativePreparationStarts() {
		val (viewerClass, viewer) = task7Viewer(RuntimeEnvironment.getApplication())
		val fixture = BridgeFixture()
		var presentation = fixture.liveEngineExposedState(presentedFrameSequence = 24L)
		viewerClass.task7SetPresentationDecision(viewer, readerPresentationDecision(presentation))
		fixture.host.currentBinding = fixture.binding.copy(
			rasterGeneration = null,
			textureGeneration = null,
			preparationGeneration = 8L
		)
		var nativePreparationEnabledWhenHandoffRequested: Boolean? = null
		var nativePreparationEnabledWhenDeadlineScheduled: Boolean? = null
		val deadlines = HostBridgeDeadlineScheduler()
		val scheduledDelays = mutableListOf<Long>()
		val bridge = ReaderPresentationHostBridge(
			host = fixture.host,
			transitionTimeoutScheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
				override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
					nativePreparationEnabledWhenDeadlineScheduled = viewerClass.task7CanvasEnabled(viewer)
					scheduledDelays += delayMillis
					return deadlines.postDelayed(action, delayMillis)
				}
				override fun removeCallbacks(action: Runnable) = deadlines.removeCallbacks(action)
			},
			transitionNowMillis = { 0L },
			liveEngineExposureRequired = {
				viewerClass.task7Method("requiresLiveEngineExposureHandoff")
					.invoke(viewer) as Boolean
			}
		) { event ->
			if (event is ReaderPresentationEvent.WebViewHandoffRequested) {
				nativePreparationEnabledWhenHandoffRequested =
					viewerClass.task7CanvasEnabled(viewer)
			}
			val reduction = readerPresentationReduce(presentation, event)
			presentation = reduction.state
			viewerClass.task7SetPresentationDecision(viewer, reduction.decision)
			readerTestPresentationReceipt(event, reduction.state)
		}
		val admitNativeMode = {
			bridge.update(readerPresentationDecision(presentation))
		}

		viewerClass.task7Method(
			"setPageTurnCanvasEnabled",
			requireNotNull(Boolean::class.javaPrimitiveType),
			Function0::class.java
		).invoke(viewer, true, admitNativeMode)

		val pending = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
			presentation.authority
		)
		assertTrue(viewerClass.task7CanvasEnabled(viewer))
		assertIs<ReaderRequiredTransition.PresentNativePage>(readerPresentationDecision(presentation).requiredTransition)
		assertEquals(listOf(10_000L), scheduledDelays)
		assertEquals(false, nativePreparationEnabledWhenDeadlineScheduled)
		assertTrue(deadlines.hasPending)
		assertEquals(false, nativePreparationEnabledWhenHandoffRequested)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(pending.retainedFrame)
		deadlines.runPending()
		assertEquals(ReaderPresentationFailureReason.TimedOut, presentation.failure?.reason)
		assertEquals(pending.retainedFrame, readerPresentationDecision(presentation).frameOwner)
		assertFalse(deadlines.hasPending)
		viewerClass.task7Method("closeReader").invoke(viewer)
		bridge.dispose()
	}

	@Test
	fun retryThenExactCancelReleasesCommonRelocationAndDispatchesNextOnce() {
		val queue = ReaderPageRelocationQueue()
		val first = enqueueTask7Relocation(queue, 61L, 3, 4, "cancel-session")
		val second = enqueueTask7Relocation(queue, 62L, 4, 5, "cancel-session")
		acknowledgeTask7Relocation(queue, first)
		val binding = ReaderPresentationBinding(
			first.foliateSessionId, 2L, 3L, 4L,
			rasterGeneration = first.rasterGeneration,
			textureGeneration = first.textureGeneration,
			preparationGeneration = 7L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 8L, 1200, 800,
			first.rasterGeneration, first.textureGeneration
		)
		var presentation = ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 63L
		)
		fun publish(event: ReaderPresentationEvent) = readerTestPresentationReceipt(
			event = event,
			postState = readerPresentationReduce(presentation, event).also {
				presentation = it.state
			}.state
		)
		val host = Task7VisualHandoffHost()
		val dispatched = mutableListOf<ReaderPageRelocationRequest>()
		val completed = mutableListOf<ReaderPageRelocationRequest>()
		val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = {
				ReaderPageRelocationVisualState(
					true, true, first.foliateSessionId, first.destinationOrdinal,
					first.rasterGeneration, first.textureGeneration
				)
			},
			dispatch = dispatched::add,
			publishRecovery = { _, reason -> error("Unexpected recovery: $reason") },
			finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
			validateContent = { _, validated ->
				validated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			requestPresentationHandoff = {
				publish(
					ReaderPresentationEvent.WebViewHandoffRequested(
						ReaderLiveEngineHandoffDirection.NativeToLiveEngine
					)
				)
				readerPresentationDecision(presentation).requiredTransition as
					ReaderRequiredTransition.ExposeLiveEngine
			},
			commitLiveEngineExposure = { proof ->
				publish(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
			},
			publishLiveEngineHandoffTerminal = ::publish,
			onCompleted = completed::add
		)

		assertTrue(coordinator.onAcknowledged(first))
		host.timeOut()
		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		val retry = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertTrue(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertFalse(host.takeVisual().deliver())
		val cancelled = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.LiveEngineHandoffCancelled(
				direction = retry.direction,
				token = retry.token,
				binding = retry.binding
			)
		)
		presentation = cancelled.state

		assertTrue(coordinator.synchronizeCommonPresentationDecision(cancelled.decision))
		assertFalse(coordinator.synchronizeCommonPresentationDecision(cancelled.decision))
		assertEquals(1, coordinator.pendingCallbackCount())
		assertFalse(coordinator.synchronizeCommonPresentationHandoff(retry))
		assertEquals(listOf(first), completed)
		assertEquals(second, queue.head())
		assertTrue(queue.hasDispatchedHead())
		assertEquals(listOf(second), dispatched)
		assertFalse(host.takeVisual().deliver())
		assertEquals(0, coordinator.pendingCallbackCount())
	}

	@Test
	fun coldNonCanvasPublicationCommitsOpaqueShellBeforeLiveEngineHandoff() {
		val binding = ReaderPresentationBinding(
			"cold-session", 1L, 2L, 3L,
			preparationGeneration = 4L
		)
		var presentation = ReaderPresentationState(nextTokenValue = 5L)
		val unboundDecision = readerPresentationDecision(presentation)
		assertNull(unboundDecision.targetBinding)
		assertIs<ReaderPresentationAuthority.Unavailable>(unboundDecision.authority)
		assertEquals(ReaderPresentationFrameOwner.Neutral, unboundDecision.frameOwner)

		val hostSource = hostFile.readText()
		val factorySource = hostSource
			.substringAfter("factory = { context ->")
			.substringBefore("update = { root ->")
		assertTrue(
			factorySource.indexOf("setPresentationDecision(") in
				0 until factorySource.indexOf("setViewerContent("),
			"The atomic neutral predecessor must be projected before viewer installation"
		)
		val nativeVisibilitySource = hostSource
			.substringAfter("private fun updateNativeCoverVisibilityLayers(")
			.substringBefore("fun setOnStartupShellPrepared")
		assertContains(nativeVisibilitySource, "neutralPredecessor")

		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val viewerContentContainer = viewer.getChildAt(0) as FrameLayout
		val shellCoverClass = Class.forName(
			"paige.navic.ui.screens.reader.KomikkuReaderNativeShellCoverView"
		)
		val shellCoverView = shellCoverClass.getDeclaredConstructor(Context::class.java).run {
			isAccessible = true
			newInstance(context) as View
		}
		viewerClass.task7Method("setShellCoverView", View::class.java).invoke(viewer, shellCoverView)
		shellCoverView.visibility = View.VISIBLE
		assertEquals(viewer.childCount - 1, viewer.indexOfChild(shellCoverView))

		val liveWebView = WebView(context)
		viewerContentContainer.addView(liveWebView)
		assertTrue(viewerContentContainer.indexOfChild(liveWebView) >= 0)
		assertEquals(View.VISIBLE, shellCoverView.visibility)
		assertTrue(
			viewer.indexOfChild(viewerContentContainer) < viewer.indexOfChild(shellCoverView)
		)
		val shellCoverSource = hostSource
			.substringAfter("private class KomikkuReaderNativeShellCoverView")
			.substringBefore("private data class NativeReaderShellCoverGeometry")
		assertContains(shellCoverSource, "canvas.drawColor(Color.rgb(16, 14, 10))")

		val prepareShellCover = viewerClass.task7Method("prepareShellCoverForCommit", View::class.java)
		val cancelShellCover = viewerClass.task7Method(
			"cancelShellCoverCommitPreparation",
			View::class.java
		)
		val selectShellCover = viewerClass.task7Method(
			"selectShellCover",
			View::class.java,
			requireNotNull(Boolean::class.javaPrimitiveType)
		)
		val presentationDecisionField = viewerClass.getDeclaredField(
			"presentationDecision"
		).apply { isAccessible = true }
		val appliedOwners = mutableListOf<ReaderPresentationFrameOwner>()
		var currentBinding: ReaderPresentationBinding? = null
		var preparedCoverGeneration: Long? = null
		var coverSelected = false
		var drawListener: (() -> Unit)? = null
		val animationFrames = ArrayDeque<() -> Unit>()
		val commitHost = object : ReaderPresentationCommitHost {
			override val isAttachedToWindow = true
			override val currentPresentationBinding: ReaderPresentationBinding?
				get() = currentBinding
			override val currentShellCoverGeneration: Long?
				get() = preparedCoverGeneration
			override val shellCoverSelected: Boolean
				get() = coverSelected
			override val measuredViewportWidth = 1200
			override val measuredViewportHeight = 800
			override fun prepareOpaqueShellCover(coverGeneration: Long) {
				preparedCoverGeneration = coverGeneration
				prepareShellCover.invoke(viewer, shellCoverView)
			}
			override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) {
				if (preparedCoverGeneration == coverGeneration) {
					preparedCoverGeneration = null
					cancelShellCover.invoke(viewer, shellCoverView)
				}
			}
			override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) {
				if (preparedCoverGeneration == coverGeneration) {
					preparedCoverGeneration = null
				}
			}
			override fun registerShellCoverDrawListener(
				onDraw: () -> Unit
			): ReaderPresentationDrawRegistration {
				drawListener = onDraw
				return ReaderPresentationDrawRegistration { drawListener = null }
			}
			override fun postShellCoverAnimationFrame(onFrame: () -> Unit) {
				animationFrames.addLast(onFrame)
			}
			override fun applyPresentationFrameOwner(
				decision: paige.navic.reader.ReaderPresentationDecision
			) {
				presentationDecisionField.set(viewer, decision)
				val application = readerNativePresentationApplication(decision)
				val neutralPredecessor =
					decision.authority == ReaderPresentationAuthority.Unavailable &&
						decision.frameOwner == ReaderPresentationFrameOwner.Neutral
				shellCoverView.visibility = if (
					application.layers.shellCover || neutralPredecessor
				) View.VISIBLE else View.GONE
				appliedOwners += decision.frameOwner
			}
		}
		val visualHost = Task7VisualHandoffHost()
		val visualHandoff = ReaderWebViewVisualHandoff(visualHost)
		val events = mutableListOf<ReaderPresentationEvent>()
		val bridge = ReaderPresentationHostBridge(
			host = commitHost,
			liveEngineVisualHandoff = visualHandoff,
			liveEngineExposureRequired = { true }
		) { event ->
			events += event
			if (event is ReaderPresentationEvent.ShellCoverCommitted) {
				coverSelected = true
				selectShellCover.invoke(viewer, shellCoverView, true)
			}
			presentation = readerPresentationReduce(presentation, event).state
			readerTestPresentationReceipt(event, presentation)
		}

		bridge.update(unboundDecision)
		assertTrue(events.isEmpty())
		assertEquals(View.VISIBLE, shellCoverView.visibility)

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.PublicationOpened(binding)
		).state
		currentBinding = binding
		bridge.update(readerPresentationDecision(presentation))

		assertIs<ReaderPresentationEvent.ShellCoverRequested>(events.single())
		assertIs<ReaderPresentationAuthority.ShellCoverCommitPending>(presentation.authority)
		assertEquals(listOf(ReaderPresentationFrameOwner.Neutral), appliedOwners.distinct())
		assertEquals(View.VISIBLE, shellCoverView.visibility)
		val pendingDecision = readerPresentationDecision(presentation)
		assertFalse(
			readerViewerActionIsAdmitted(
				pendingDecision.inputPolicy,
				ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
			)
		)
		assertFalse(events.any { it is ReaderPresentationEvent.WebViewHandoffRequested })
		assertNotNull(drawListener).invoke()
		animationFrames.removeFirst().invoke()
		assertIs<ReaderPresentationEvent.ShellCoverCommitted>(events.last())
		assertIs<ReaderPresentationAuthority.ShellCover>(presentation.authority)
		assertFalse(events.any { it is ReaderPresentationEvent.WebViewHandoffRequested })

		bridge.update(readerPresentationDecision(presentation))
		val request = assertIs<ReaderPresentationEvent.WebViewHandoffRequested>(events.last())
		assertEquals(ReaderLiveEngineHandoffDirection.NativeToLiveEngine, request.direction)
		assertIs<ReaderPresentationFrameOwner.ShellCover>(appliedOwners.last())
		visualHost.deliverVisual()
		visualHost.presentFrame()
		assertIs<ReaderPresentationEvent.LiveEngineExposureCommitted>(events.last())
		assertIs<ReaderPresentationAuthority.LiveEngineExposed>(presentation.authority)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(appliedOwners.last())
		assertEquals(View.GONE, shellCoverView.visibility)
		bridge.dispose()
		viewerClass.task7Method("closeReader").invoke(viewer)
	}

	@Test
	fun nativeHandbackDeadlineFailsVisibleAndRetryUsesFreshToken() {
		val binding = ReaderPresentationBinding(
			"handback-session", 1L, 2L, 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding, null, 7L, 1200, 800, 4L, 5L
		)
		var presentation = ReaderPresentationState(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(nativeProof)
			),
			binding = binding,
			nextTokenValue = 8L
		)
		val exposure = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
		)
		val exposureToken = assertIs<ReaderRequiredTransition.ExposeLiveEngine>(
			exposure.decision.requiredTransition
		).token
		presentation = readerPresentationReduce(
			exposure.state,
			ReaderPresentationEvent.LiveEngineExposureCommitted(
				ReaderLiveEnginePresentationProof(exposureToken, binding, 9L)
			)
		).state
		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.LiveEngineToNative
			)
		).state
		val first = assertIs<ReaderRequiredTransition.PresentNativePage>(
			readerPresentationDecision(presentation).requiredTransition
		)
		var candidate = ReaderNativePagePresentationCandidate(
			binding, first.token, 4, 1200, 800,
			ReaderPagePreparationFacts(
				phase = ReaderPagePreparationPhase.Ready,
				generation = 6L
			),
			handoffDirection = ReaderLiveEngineHandoffDirection.LiveEngineToNative
		)
		val frames = object : ReaderNativePagePresentedFrameSource {
			var requests = 0
			var cancels = 0
			private val callbacks = mutableMapOf<Long, (Long) -> Unit>()
			override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
				val requestId = (++requests).toLong()
				callbacks[requestId] = onPresented
				return requestId
			}
			override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
				cancels += 1
				callbacks.remove(requestId)
				return true
			}
			fun present(requestId: Long) {
				callbacks.remove(requestId)?.invoke(requestId)
			}
		}
		val deadlines = object : ReaderPageRelocationDispatchTimeoutScheduler {
			var pending: Runnable? = null
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				pending = action
				return true
			}
			override fun removeCallbacks(action: Runnable) {
				if (pending === action) pending = null
			}
		}
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = frames,
			currentCandidate = { candidate },
			currentHandoffTransition = {
				readerPresentationDecision(presentation).requiredTransition as?
					ReaderRequiredTransition.PresentNativePage
			},
			handoffTimeoutScheduler = deadlines,
			handoffTimeoutMillis = 1_000L
		) { event ->
			events += event
			presentation = readerPresentationReduce(presentation, event).state
			readerTestPresentationReceipt(event, presentation)
		}

		publisher.update()
		requireNotNull(deadlines.pending).run()

		assertEquals(1, frames.cancels)
		val timedOut = assertIs<ReaderPresentationEvent.LiveEngineHandoffTimedOut>(events.single())
		assertEquals(ReaderLiveEngineHandoffDirection.LiveEngineToNative, timedOut.direction)
		assertEquals(first.token, timedOut.token)
		assertEquals(first.binding, timedOut.binding)
		assertEquals(
			ReaderPresentationFailureReason.TimedOut,
			assertIs<ReaderDiagnosticPresentation.Failure>(presentation.failure).reason
		)
		assertIs<ReaderPresentationFrameOwner.LiveEngine>(
			readerPresentationDecision(presentation).frameOwner
		)
		val cancelled = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.LiveEngineHandoffCancelled(
				direction = requireNotNull(first.direction),
				token = first.token,
				binding = first.binding
			)
		)
		assertIs<ReaderPresentationAuthority.LiveEngineExposed>(cancelled.state.authority)
		assertNull(cancelled.state.failure)

		presentation = readerPresentationReduce(
			presentation,
			ReaderPresentationEvent.Retry
		).state
		val retry = assertIs<ReaderRequiredTransition.PresentNativePage>(
			readerPresentationDecision(presentation).requiredTransition
		)
		assertFalse(retry.token == first.token)
		candidate = candidate.copy(transitionToken = retry.token)
		publisher.update()
		assertEquals(2, frames.requests)
		assertNotNull(deadlines.pending)

		frames.present(2L)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentation.authority)
		candidate = candidate.copy(transitionToken = null, handoffDirection = null)
		publisher.update()
		assertEquals(2, frames.requests)

		candidate = candidate.copy(
			binding = binding.copy(viewportGeneration = 3L),
			visualPageIndex = 5
		)
		publisher.update()
		assertEquals(3, frames.requests)
	}

	@Test
	fun webViewOwnerReplacementCannotCombineAVisualProofWithBConnection() {
		val host = Task7VisualHandoffHost()
		val binding = ReaderPresentationBinding(
			"owner-session", 1L, 2L, 3L,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val results = mutableListOf<ReaderPresentationWebViewVisualHandoffResult>()
		val handoff = ReaderWebViewVisualHandoff(host)
		handoff.await(ReaderPresentationToken(7L), binding, results::add)
		val ownerAVisual = host.takeVisual()
		assertTrue(ownerAVisual.deliver())
		host.ownerGeneration = 2L
		host.presentFrame()

		assertEquals(
			ReaderWebViewVisualHandoffFailure.Invalidated,
			assertIs<ReaderPresentationWebViewVisualHandoffResult.Failed>(results.single()).reason
		)
		assertFalse(ownerAVisual.deliver())

		handoff.await(ReaderPresentationToken(8L), binding, results::add)
		host.deliverVisual()
		host.presentFrame()
		val ready = assertIs<ReaderPresentationWebViewVisualHandoffResult.Ready>(results.last())
		assertEquals(ReaderPresentationToken(8L), ready.token)
		assertEquals(binding, ready.binding)
	}

	@Test
	fun nativeHostApplicationKeepsLayerPreparationDiagnosticAndInputOnOneDecision() {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "application-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				foliateSessionId = "application-session",
				commitSequence = 4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val coverProof = ReaderShellCoverCommitProof(
			token = ReaderPresentationToken(8L),
			binding = binding,
			coverGeneration = 9L,
			presentedFrame = 10L,
			viewportWidth = 1200,
			viewportHeight = 800
		)
		val decision = readerPresentationReduce(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.ShellCover(coverProof),
				binding = binding,
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 7L,
					completedCount = 1,
					requiredCount = 4,
					readiness = ReaderPageReadinessState(
						textureDeck = ReaderTextureDeckState.Preparing,
						interaction = ReaderPageInteractionState.BlockingInitialPreparation
					)
				),
				nextTokenValue = 11L
			),
			ReaderPresentationEvent.ShellCoverDismissalRequested
		).decision

		val application = readerNativePresentationApplication(decision)

		assertSame(decision, application.decision)
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = true,
				preparationShield = false
			),
			application.layers
		)
		assertEquals(decision.preparationPresentation, application.preparation)
		assertEquals(decision.diagnosticPresentation, application.diagnostic)
		assertEquals(decision.inputPolicy, application.inputPolicy)
		assertIs<ReaderPreparationPresentation.Blocking>(application.preparation)
		assertEquals(ReaderDiagnosticPresentation.Hidden, application.diagnostic)
		assertEquals(ReaderPresentationInputPolicy.ChromeOnly, application.inputPolicy)
	}

	@Test
	fun unavailableRawCoverIntentCannotSelectOrExposeAShellCover() {
		val unavailableDecision = readerPresentationDecision(ReaderPresentationState())
		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationApplication(unavailableDecision).layers
		)

		val binding = ReaderPresentationBinding(
			foliateSessionId = "native-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = null,
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val nativeProof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 7L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		val nativeDecision = readerPresentationDecision(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.SettledNativePage(
					ReaderPresentationFrameOwner.NativePage(nativeProof)
				),
				binding = binding,
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Ready,
					generation = 6L,
					readiness = ReaderPageReadinessState(
						textureDeck = ReaderTextureDeckState.Ready,
						interaction = ReaderPageInteractionState.Ready
					)
				)
			)
		)

		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = false
			),
			readerNativePresentationApplication(nativeDecision).layers
		)
	}

	@Test
	fun chromeOnlyUsesAChromeGestureRouteThatNeverDispatchesToPageCurlOrContent() {
		val source = hostFile.readText()
		val modeSelector = source
			.substringAfter("internal fun readerPagePhysicalDispatchMode(")
			.substringBefore("internal interface ReaderPagePhysicalDispatchTarget")
		val physicalDispatch = source
			.substringAfter("internal fun readerDispatchPagePhysicalEvent(")
			.substringBefore("internal data class ReaderLegacyLivePointerContext")
		val chromeTarget = source
			.substringAfter("override fun dispatchChromeOnly(event: MotionEvent): Boolean =")
			.substringBefore("override fun dispatchDenied(event: MotionEvent)")
		val shellCoverTarget = source
			.substringAfter("override fun dispatchShellCover(event: MotionEvent): Boolean =")
			.substringBefore("override fun dispatchLiveEngine(event: MotionEvent)")
		val shellCoverDispatch = source
			.substringAfter("private fun dispatchShellCoverPointerEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchLegacyReaderPointerEvent")

		assertContains(modeSelector, "ReaderPresentationInputPolicy.ChromeOnly ->")
		assertContains(modeSelector, "ReaderPagePhysicalDispatchMode.ChromeOnly")
		assertContains(
			physicalDispatch,
			"ReaderPagePhysicalDispatchMode.ChromeOnly -> target.dispatchChromeOnly(event)"
		)
		assertContains(chromeTarget, "dispatchChromeOnlyPointerEvent(event)")
		assertFalse(chromeTarget.contains("dispatchPlayLikeCurlPointerEvent"))
		assertFalse(chromeTarget.contains("dispatchLegacyReaderPointerEvent"))
		assertFalse(chromeTarget.contains("viewerContentContainer.dispatchTouchEvent"))
		assertContains(shellCoverTarget, "dispatchShellCoverPointerEvent(event)")
		assertContains(shellCoverDispatch, "shellCoverView?.dispatchTouchEvent(event)")
		assertFalse(shellCoverDispatch.contains("viewerContentContainer.dispatchTouchEvent"))
		assertFalse(shellCoverDispatch.contains("super.dispatchTouchEvent"))
	}

	@Test
	fun startupShellPageActionWaitsForMatchingPresentationProofAndThenDispatchesOnce() {
		val gate = ReaderStartupShellHandoffGate()
		var dispatches = 0
		fun dispatchQualifyingCoverAction() {
			gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true)
			if (!gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true)) {
				dispatches += 1
			}
		}

		dispatchQualifyingCoverAction()
		assertEquals(0, dispatches)
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Ready,
			textureDeck = ReaderTextureDeckState.Ready,
			onPrepared = {},
			onRejected = { error("Matching presentation proof was rejected") }
		)

		dispatchQualifyingCoverAction()

		assertEquals(1, dispatches)
	}

	@Test
	fun startupShellHandoffIsOneShotAcrossAnOrdinaryReturnedCover() {
		val gate = ReaderStartupShellHandoffGate()
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		var prepared = 0
		var rejected = 0

		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Ready,
			textureDeck = ReaderTextureDeckState.Ready,
			onPrepared = { prepared += 1 },
			onRejected = { rejected += 1 }
		)

		assertEquals(1, prepared)
		assertEquals(0, rejected)
		assertFalse(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		assertTrue(gate.consumePreparedHandoff())
		assertFalse(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
		assertNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)

		gate.resetForNewViewer()
		assertTrue(gate.consumesCanvasShellPageAction(shellVisible = true, canvasEnabled = true))
	}

	@Test
	fun nonCanvasStartupDismissalRetiresLaterCanvasHandoff() {
		val gate = ReaderStartupShellHandoffGate()

		assertFalse(
			gate.consumesCanvasShellPageAction(
				shellVisible = true,
				canvasEnabled = false
			)
		)
		assertFalse(gate.consumePreparedHandoff())
		assertFalse(
			gate.consumesCanvasShellPageAction(
				shellVisible = true,
				canvasEnabled = true
			)
		)
		assertNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
	}

	@Test
	fun regressedReadinessRejectsCommittedStartupShieldAndPermitsRetry() {
		val gate = ReaderStartupShellHandoffGate()
		val attempt = assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
		var prepared = 0
		var staleShieldDismissed = 0

		gate.completeAttempt(
			attempt = attempt,
			shellVisible = true,
			canvasEnabled = true,
			rasterPhase = ReaderPagePreparationPhase.Preparing,
			textureDeck = ReaderTextureDeckState.Preparing,
			onPrepared = { prepared += 1 },
			onRejected = { staleShieldDismissed += 1 }
		)

		assertEquals(0, prepared)
		assertEquals(1, staleShieldDismissed)
		assertFalse(gate.attemptInFlight)
		assertNotNull(
			gate.beginAttempt(
				shellVisible = true,
				canvasEnabled = true,
				rasterPhase = ReaderPagePreparationPhase.Ready,
				textureDeck = ReaderTextureDeckState.Ready
			)
		)
	}

	@Test
	fun hostPreparationFactsStayBlockedUntilPublicationContentBecomesReady() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Idle,
			requiredCount = 0,
			completedCount = 0,
			interactiveRequiredCount = 0,
			interactiveCompletedCount = 0,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val pending = readerHostPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = false,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)
		val ready = readerHostPagePreparationState(
			pageTurnCanvasEnabled = false,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState()
		)

		assertEquals(ReaderPagePreparationPhase.Idle, pending.phase)
		assertEquals(
			ReaderPageInteractionState.BlockingInitialPreparation,
			pending.readiness.interaction
		)
		assertEquals(ReaderPagePreparationPhase.Ready, ready.phase)
		assertEquals(ReaderTextureDeckState.Ready, ready.readiness.textureDeck)
		assertEquals(ReaderPageInteractionState.Ready, ready.readiness.interaction)
	}

	@Test
	fun enabledCanvasMergesRendererReadinessIntoRawPreparationFacts() {
		val rasterState = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 1,
			readiness = ReaderPageReadinessState(
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		val merged = readerHostPagePreparationState(
			pageTurnCanvasEnabled = true,
			pageTurnContentReady = true,
			rasterState = rasterState,
			rendererState = ReaderPageRendererReadinessState(
				textureDeck = ReaderTextureDeckState.Preparing,
				interaction = ReaderPageInteractionState.BlockingInitialPreparation
			)
		)

		assertEquals(ReaderPagePreparationPhase.Preparing, merged.phase)
		assertEquals(ReaderTextureDeckState.Preparing, merged.readiness.textureDeck)
		assertEquals(ReaderPageInteractionState.BlockingInitialPreparation, merged.readiness.interaction)
		assertEquals(1, merged.completedCount)
	}

	@Test
	fun canvasAndContentReadinessTransitionsRepublishPreparationFacts() {
		val source = hostFile.readText()
		val canvasTransition = source
			.substringAfterLast("fun setPageTurnCanvasEnabled(enabled: Boolean)")
			.substringBefore("fun setPageTurnReadingDirection(")
		val contentTransition = source
			.substringAfterLast("fun setPageTurnContentReadyKey(contentReadyKey: String?)")
			.substringBefore("fun setPageTurnPaginationStatus(")

		assertContains(canvasTransition, "publishPagePreparationFacts()")
		assertContains(contentTransition, "publishPagePreparationFacts()")
		assertTrue(
			contentTransition.indexOf("publishPagePreparationFacts()") <
				contentTransition.indexOf("if (contentReadyKey == null) return")
		)
	}

	@Test
	fun busyFeedbackUsesTheOneTerminalPublisherWithoutInterceptingPreparationGestures() {
		val source = hostFile.readText()

		assertContains(source, "readerPageGestureShouldShowBusyFeedback(outcome)")
		assertContains(source, "onRendererBusyGestureRejected()")
		assertFalse(source.contains("composeOverlay.isClickable = visible"))
	}

	@Test
	fun busyFeedbackClearsOnlyAfterActualRendererPointerAdmission() {
		val source = hostFile.readText()

		assertContains(source, "fun canAcceptNewPointer(): Boolean = viewerContainer.canAcceptNewPointer()")
		val admission = source
			.substringAfter("fun canAcceptNewPointer(): Boolean {", missingDelimiterValue = "")
			.substringBefore("\n\tfun closeReader()", missingDelimiterValue = "")
		assertContains(admission, "if (readInstalledPublication == null) return playLikeCurlController.isAvailable")
		val publication = admission.indexOf("val publication = readInstalledPublication?.invoke() ?: return false")
		val current = admission.indexOf("if (installedCommandCurrency?.invoke() != true) return false")
		val policy = admission.indexOf("readerTransitionPresentationInputPolicy(publication.owner,")
		val decision = admission.indexOf("readerPageNewPointerDecision(policy, localPageSafetyPolicy) == ReaderPageNewPointerDecision.Accept")
		assertContains(admission, "publication.binding, publication.lease")
		assertTrue(publication >= 0 && current > publication && policy > current && decision > policy,
			"Installed pointer admission must use the exact current publication, its input lease and local renderer safety.")
		assertContains(source, "currentNativeFrameRoot?.canAcceptNewPointer() == true")
		assertFalse(
			source.contains(
				"pageOperationPolicy.newPointer == ReaderPageNewPointerDecision.Accept"
			)
		)
	}

	@Test
	fun busyFeedbackUsesANonTouchableWindowAboveTheOnTopCurlSurface() {
		val hostSource = hostFile.readText()
		val popupSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderRendererBusyPopup.android.kt"
		).readText()

		assertContains(hostSource, "ReaderRendererBusyPopup(")
		assertContains(popupSource, "PopupWindow(")
		assertContains(popupSource, "isFocusable = false")
		assertContains(popupSource, "isTouchable = false")
		assertContains(
			popupSource,
			"windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL"
		)
		assertFalse(hostSource.contains("androidx.compose.ui.window.Popup"))
	}

	@Test
	fun retainedValidatedPresentationSurvivesOnlyTransientRendererReadiness() {
		val ownership = ReaderRetainedValidatedPresentationOwnership()

		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Preparing)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Settling)
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = false))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Empty)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
		assertTrue(ownership.hasPresentation(staticRasterShieldOwnership = true))

		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Ready)
		ownership.onRendererReadinessChanged(ReaderTextureDeckState.Failed)
		assertFalse(ownership.hasPresentation(staticRasterShieldOwnership = false))
	}

	@Test
	fun busyFeedbackMinimumTimerIsOwnedByFullyVisibleNestedOverlay() {
		val source = hostFile.readText()

		assertContains(
			source,
			"val rendererBusyFeedbackVisibility = remember { MutableTransitionState(false) }"
		)
		assertContains(source, "fullyVisibleRejectionToken = activeToken")
		assertContains(
			source,
			"readerRendererBusyFeedbackCanStartMinimumTimer("
		)
		assertFalse(source.contains("var rendererBusyFeedbackVisible by remember"))
	}

	@Test
	fun neutralBlockingDecisionUsesOnlyTheFailClosedPreparationShield() {
		val decision = readerPresentationDecision(
			ReaderPresentationState(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					retainedFrame = ReaderPresentationFrameOwner.Neutral
				),
				preparationFacts = ReaderPagePreparationFacts(
					phase = ReaderPagePreparationPhase.Preparing,
					generation = 1L,
					completedCount = 0,
					requiredCount = 0,
					readiness = ReaderPageReadinessState(
						interaction = ReaderPageInteractionState.BlockingInitialPreparation
					)
				)
			)
		)

		assertEquals(
			ReaderNativePresentationLayerVisibility(
				shellCover = false,
				preparationShield = true
			),
			readerNativePresentationApplication(decision).layers
		)
	}

	@Test
	fun delayedNativeProofCannotLeaveReporterOnRejectedPartialBasis() {
		val completeA = ReaderPresentationBinding(
			foliateSessionId = "delayed-proof-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"delayed-proof-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val partialB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"delayed-proof-session",
				5L
			),
			rasterGeneration = null,
			textureGeneration = null
		)
		val completeB = partialB.copy(rasterGeneration = 8L, textureGeneration = 9L)
		val request = paige.navic.reader.ReaderNativePagePresentationRequest(
			ReaderPresentationToken(10L),
			completeA
		)
		val cachedState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral,
				request
			),
			binding = completeA,
			nextTokenValue = 11L
		)
		val reporter = ReaderPresentationBindingReporter()
		reporter.update(completeA, completeA, false, false)
		val proofA = ReaderNativePagePresentationProof(
			binding = completeA,
			transitionToken = request.token,
			presentedFrame = 11L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 5L,
			textureGeneration = 6L
		)
		val settled = reporter.dispatch(
			ReaderController(
				ReaderControllerState(
					readerSessionGeneration = 5L,
					presentation = cachedState
				)
			),
			ReaderPresentationEvent.NativePagePresented(proofA)
		)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(
			settled.controller.state.presentation.authority
		)

		val partialEvent = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(completeA, partialB, false, true)
		)
		val rejected = reporter.dispatch(settled.controller, partialEvent)
		assertEquals(completeA, rejected.controller.state.presentation.binding)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(
			rejected.controller.state.presentation.authority
		)

		val completeEvent = assertNotNull(
			reporter.update(completeA, completeB, false, true)
		)
		assertIs<ReaderPresentationEvent.FoliateRelocated>(completeEvent)
		assertEquals(completeB, completeEvent.binding)
	}

	@Test
	fun productionBindingReporterUsesReducerReceiptsAndAtomicCombinedReplacement() {
		val session = "fixture-session"
		val bindingA = ReaderPresentationBinding(
			foliateSessionId = session,
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 1L),
			rasterGeneration = 4L,
			textureGeneration = 5L,
			preparationGeneration = 6L
		)
		val combinedB = bindingA.copy(
			viewportGeneration = 7L,
			profileGeneration = 8L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 2L),
			rasterGeneration = 9L,
			textureGeneration = 10L,
			preparationGeneration = 11L
		)
		val pureDestinationC = combinedB.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(session, 3L),
			rasterGeneration = 12L,
			textureGeneration = 13L,
			preparationGeneration = 14L
		)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 1L)
		)

		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(null, bindingA, true, false)
		)
		assertNull(reporter.lastReportedBinding)
		controller = reporter.dispatch(controller, opened).controller
		assertEquals(bindingA, reporter.lastReportedBinding)
		assertNull(reporter.update(null, bindingA, false, false))

		val request = assertNotNull(
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation).nativePresentationRequest
		)
		val proofA = ReaderNativePagePresentationProof(
			binding = bindingA,
			transitionToken = request.token,
			presentedFrame = 1L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 4L,
			textureGeneration = 5L
		)
		controller = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proofA)
		).controller
		val combinedEvent = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(bindingA, combinedB, false, true)
		)
		assertEquals(bindingA, combinedEvent.previousBinding)
		assertEquals(combinedB, combinedEvent.binding)
		assertEquals(bindingA, reporter.lastReportedBinding)

		val replacement = reporter.dispatch(controller, combinedEvent)
		controller = replacement.controller
		assertEquals(ReaderPresentationAuthority.Unavailable, controller.state.presentation.authority)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					request.token,
					bindingA
				)
			),
			replacement.presentationEffects
		)
		assertEquals(combinedB, reporter.lastReportedBinding)
		assertNull(reporter.update(bindingA, combinedB, false, false))
		assertEquals(combinedB, reporter.lastReportedBinding)
		assertNull(reporter.update(combinedB, combinedB, false, false))

		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(combinedB, pureDestinationC, false, true)
		)
		assertEquals(pureDestinationC, relocation.binding)
	}

	@Test
	fun bindingReporterPublishesPartialOpenThenTypedExactCompletion() {
		val partial = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture-session", 1L),
			preparationGeneration = 6L
		)
		val complete = partial.copy(rasterGeneration = 7L, textureGeneration = 8L)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 9L)
		)

		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(
				confirmedTargetBinding = null,
				currentBinding = partial,
				publicationOpenPending = true,
				relocationPending = false
			)
		)
		controller = reporter.dispatch(controller, opened).controller
		assertEquals(partial, reporter.lastReportedBinding)
		assertNull(reporter.update(null, partial, false, false))

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(
				confirmedTargetBinding = partial,
				currentBinding = complete,
				publicationOpenPending = false,
				relocationPending = false
			)
		)
		assertEquals(partial, completion.previousBinding)
		assertEquals(complete, completion.binding)
		assertEquals(partial, reporter.lastReportedBinding)
		controller = reporter.dispatch(controller, completion).controller
		assertEquals(complete, controller.state.presentation.binding)
		assertNull(reporter.update(partial, complete, false, false))
		assertEquals(complete, reporter.lastReportedBinding)
	}

	@Test
	fun bindingReporterCompletesResolvedProfileFromAuthoritativeReceiptBeforeComposeEcho() {
		val provisional = ReaderPresentationBinding(
			foliateSessionId = "profile-race-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 0L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"profile-race-session",
				3L
			),
			preparationGeneration = 4L
		)
		val resolved = provisional.copy(profileGeneration = 5L)
		val complete = resolved.copy(rasterGeneration = 6L, textureGeneration = 7L)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 2L)
		)
		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(null, provisional, true, false)
		)
		controller = reporter.dispatch(controller, opened).controller

		val replacement = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(provisional, resolved, false, false)
		)
		assertEquals(provisional, replacement.previousBinding)
		assertEquals(resolved, replacement.binding)
		controller = reporter.dispatch(controller, replacement).controller
		assertEquals(resolved, reporter.lastReportedBinding)
		assertNull(reporter.update(provisional, resolved, false, false))

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(provisional, complete, false, false)
		)
		assertEquals(resolved, completion.previousBinding)
		assertEquals(complete, completion.binding)
		controller = reporter.dispatch(controller, completion).controller
		assertEquals(complete, controller.state.presentation.binding)
		assertEquals(complete, reporter.lastReportedBinding)
		assertNull(reporter.update(provisional, complete, false, false))
		assertNull(reporter.update(complete, complete, false, false))
	}

	@Test
	fun bindingReporterAdvancesOnlyThroughNewerAuthoritativeReceipts() {
		val provisional = ReaderPresentationBinding(
			foliateSessionId = "pending-basis-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 0L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-basis-session",
				3L
			),
			preparationGeneration = 4L
		)
		val resolved = provisional.copy(profileGeneration = 5L)
		val relocated = resolved.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-basis-session",
				4L
			)
		)
		val complete = relocated.copy(rasterGeneration = 6L, textureGeneration = 7L)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(readerSessionGeneration = 3L)
		)
		val opened = assertIs<ReaderPresentationEvent.PublicationOpened>(
			reporter.update(null, provisional, true, false)
		)
		controller = reporter.dispatch(controller, opened).controller

		val replacement = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(provisional, resolved, false, false)
		)
		controller = reporter.dispatch(controller, replacement).controller
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(provisional, relocated, false, true)
		)
		assertEquals(relocated, relocation.binding)
		controller = reporter.dispatch(controller, relocation).controller
		assertNull(reporter.update(provisional, relocated, false, true))
		assertNull(reporter.update(provisional, resolved, false, true))

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(provisional, complete, false, false)
		)
		assertEquals(relocated, completion.previousBinding)
		assertEquals(complete, completion.binding)
		controller = reporter.dispatch(controller, completion).controller
		assertEquals(complete, controller.state.presentation.binding)

		val otherSession = complete.copy(
			foliateSessionId = "authoritative-session",
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"authoritative-session",
				1L
			),
			rasterGeneration = null,
			textureGeneration = null
		)
		assertNull(reporter.update(otherSession, otherSession, false, false))
		assertNull(reporter.update(otherSession, complete, false, false))
		assertEquals(complete, reporter.lastReportedBinding)

		reporter.reset()
		assertNull(reporter.update(null, complete, false, false))
		assertNull(reporter.lastReportedBinding)
	}

	@Test
	fun bindingReporterNeverUsesARejectedOneSidedRendererFactAsItsBasis() {
		val confirmed = ReaderPresentationBinding(
			foliateSessionId = "rejected-basis-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"rejected-basis-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val oneSided = confirmed.copy(
			viewportGeneration = 8L,
			rasterGeneration = 9L,
			textureGeneration = null
		)
		val complete = oneSided.copy(textureGeneration = 10L)
		val reporter = ReaderPresentationBindingReporter()

		reporter.update(confirmed, confirmed, false, false)
		assertNull(reporter.update(confirmed, oneSided, false, false))
		val replacement = assertIs<ReaderPresentationEvent.BindingReplaced>(
			reporter.update(confirmed, complete, false, false)
		)
		assertEquals(confirmed, replacement.previousBinding)
		assertEquals(complete, replacement.binding)
	}

	@Test
	fun pendingCompleteReporterRelocationIsAcceptedByNeutralPreparationReducer() {
		val partialA = ReaderPresentationBinding(
			foliateSessionId = "pending-complete-relocation-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-complete-relocation-session",
				4L
			),
			preparationGeneration = 5L
		)
		val completeA = partialA.copy(rasterGeneration = 6L, textureGeneration = 7L)
		val completeB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"pending-complete-relocation-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 6L,
				presentation = readerPresentationReduce(
					readerPresentationReduce(
						ReaderPresentationState(nextTokenValue = 10L),
						ReaderPresentationEvent.PublicationOpened(partialA)
					).state,
					ReaderPresentationEvent.NativePageRequested
				).state
			)
		)
		controller = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePageRequested
		).controller

		val completion = assertIs<ReaderPresentationEvent.BindingCompleted>(
			reporter.update(partialA, completeA, false, false)
		)
		controller = reporter.dispatch(controller, completion).controller
		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(partialA, completeB, false, true)
		)
		controller = reporter.dispatch(controller, relocation).controller

		val request = assertNotNull(
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation)
				.nativePresentationRequest
		)
		assertEquals(ReaderPresentationToken(10L), request.token)
		assertEquals(completeB, request.binding)
		assertEquals(completeB, controller.state.presentation.binding)

		val proofB = ReaderNativePagePresentationProof(
			binding = completeB,
			transitionToken = request.token,
			presentedFrame = 11L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val presented = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proofB)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proofB)
			),
			presented.controller.state.presentation.authority
		)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					ReaderPresentationToken(10L),
					completeA
				)
			),
			presented.presentationEffects
		)
		assertTrue(presented.controller.state.presentation.rendererCleanupOwnership.isEmpty())
	}

	@Test
	fun confirmedCompleteReporterRetriesRelocationUntilAuthoritativeReceipt() {
		val completeA = ReaderPresentationBinding(
			foliateSessionId = "confirmed-complete-relocation-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"confirmed-complete-relocation-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val completeB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"confirmed-complete-relocation-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val reporter = ReaderPresentationBindingReporter()
		val request = paige.navic.reader.ReaderNativePagePresentationRequest(
			ReaderPresentationToken(10L),
			completeA
		)
		val initialState = ReaderPresentationState(
			authority = ReaderPresentationAuthority.BlockingPreparation(
				ReaderPresentationFrameOwner.Neutral,
				request
			),
			binding = completeA,
			nextTokenValue = 11L
		)
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 7L,
				presentation = initialState
			)
		)
		reporter.update(completeA, completeA, false, false)

		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(completeA, completeB, false, true)
		)
		assertEquals(
			relocation,
			reporter.update(completeA, completeB, false, true)
		)
		controller = reporter.dispatch(controller, relocation).controller
		assertEquals(completeB, controller.state.presentation.binding)
		assertEquals(
			request.copy(binding = completeB),
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation).nativePresentationRequest
		)
		assertNull(reporter.update(completeA, completeB, false, true))
		assertEquals(completeB, reporter.lastReportedBinding)
		val proofB = ReaderNativePagePresentationProof(
			binding = completeB,
			transitionToken = request.token,
			presentedFrame = 12L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val presented = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proofB)
		)
		assertEquals(
			listOf(
				paige.navic.reader.ReaderPresentationEffect.ReleaseStalePresentation(
					request.token,
					completeA
				)
			),
			presented.presentationEffects
		)
		assertTrue(presented.controller.state.presentation.rendererCleanupOwnership.isEmpty())
	}

	@Test
	fun completeReporterRelocationFencesReverseAndForeignAuthorityFacts() {
		val completeA = ReaderPresentationBinding(
			foliateSessionId = "fenced-complete-relocation-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"fenced-complete-relocation-session",
				4L
			),
			rasterGeneration = 5L,
			textureGeneration = 6L,
			preparationGeneration = 7L
		)
		val completeB = completeA.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"fenced-complete-relocation-session",
				5L
			),
			rasterGeneration = 8L,
			textureGeneration = 9L
		)
		val otherSession = "foreign-complete-relocation-session"
		val rejected = listOf(
			completeB.copy(
				destinationCommitIdentity = ReaderDestinationCommitIdentity(
					"fenced-complete-relocation-session",
					3L
				)
			),
			completeB.copy(
				foliateSessionId = otherSession,
				destinationCommitIdentity = ReaderDestinationCommitIdentity(otherSession, 5L)
			),
			completeB.copy(publicationGeneration = 2L),
			completeB.copy(viewportGeneration = 1L),
			completeB.copy(profileGeneration = 2L)
		)

		rejected.forEach { candidate ->
			val reporter = ReaderPresentationBindingReporter()
			reporter.update(completeA, completeA, false, false)
			assertNull(reporter.update(completeA, candidate, false, true))
			assertNull(reporter.lastReportedBinding)
		}
		listOf(
			completeB.copy(viewportGeneration = 3L),
			completeB.copy(profileGeneration = 4L)
		).forEach { replacement ->
			val reporter = ReaderPresentationBindingReporter()
			reporter.update(completeA, completeA, false, false)
			assertIs<ReaderPresentationEvent.BindingReplaced>(
				reporter.update(completeA, replacement, false, true)
			)
		}
	}

	@Test
	fun bindingReporterTransitionTableAdvancesOnlyThroughReducerReceipts() {
		val requestToken = ReaderPresentationToken(501L)
		fun stateFor(binding: ReaderPresentationBinding, coverBacked: Boolean): ReaderPresentationState {
			val logicalBinding = binding.copy(rasterGeneration = null, textureGeneration = null)
			val retainedFrame = if (coverBacked) {
				ReaderPresentationFrameOwner.ShellCover(
					ReaderShellCoverCommitProof(
						token = ReaderPresentationToken(500L),
						binding = logicalBinding,
						coverGeneration = 502L,
						presentedFrame = 503L,
						viewportWidth = 1200,
						viewportHeight = 800
					)
				)
			} else {
				ReaderPresentationFrameOwner.Neutral
			}
			return ReaderPresentationState(
				authority = ReaderPresentationAuthority.BlockingPreparation(
					retainedFrame = retainedFrame,
					nativePresentationRequest =
						paige.navic.reader.ReaderNativePagePresentationRequest(
							requestToken,
							binding
						)
				),
				binding = binding,
				nextTokenValue = 502L
			)
		}

		listOf(false, true).forEach { coverBacked ->
			listOf(false, true).forEach { pendingReporterBasis ->
				listOf(false, true).forEach { currentComplete ->
					listOf(false, true).forEach { destinationSuccessor ->
						listOf(false, true).forEach { incomingComplete ->
							val cell = "cover=$coverBacked pending=$pendingReporterBasis " +
								"currentComplete=$currentComplete successor=$destinationSuccessor " +
								"incomingComplete=$incomingComplete"
							val partialCurrent = ReaderPresentationBinding(
								foliateSessionId = "reporter-transition-matrix-session",
								publicationGeneration = 1L,
								viewportGeneration = 2L,
								profileGeneration = 3L,
								destinationCommitIdentity = ReaderDestinationCommitIdentity(
									"reporter-transition-matrix-session",
									4L
								),
								preparationGeneration = 7L
							)
							val current = if (currentComplete) {
								partialCurrent.copy(rasterGeneration = 5L, textureGeneration = 6L)
							} else {
								partialCurrent
							}
							val incomingPartial = if (destinationSuccessor) {
								partialCurrent.copy(
									destinationCommitIdentity = ReaderDestinationCommitIdentity(
										"reporter-transition-matrix-session",
										5L
									)
								)
							} else if (currentComplete) {
								partialCurrent
							} else {
								partialCurrent.copy(preparationGeneration = 8L)
							}
							val incoming = if (incomingComplete) {
								when {
									!destinationSuccessor && !currentComplete -> current.copy(
										rasterGeneration = 8L,
										textureGeneration = 9L
									)
									!destinationSuccessor -> current.copy(preparationGeneration = 8L)
									else -> incomingPartial.copy(
										rasterGeneration = 8L,
										textureGeneration = 9L
									)
								}
							} else {
								incomingPartial
							}
							val reporter = ReaderPresentationBindingReporter()
							val confirmedBinding = if (pendingReporterBasis) {
								if (currentComplete) {
									partialCurrent
								} else {
									partialCurrent.copy(profileGeneration = 2L)
								}
							} else {
								current
							}
							val confirmedState = stateFor(confirmedBinding, coverBacked)
							var controller = ReaderController(
								ReaderControllerState(
									readerSessionGeneration = 4L,
									presentation = confirmedState
								)
							)
							controller = reporter.dispatch(
								controller,
								ReaderPresentationEvent.NativePageRequested
							).controller
							if (pendingReporterBasis) {
								val seed = assertNotNull(
									reporter.update(
										confirmedBinding,
										current,
										false,
										false
									),
									cell
								)
								controller = reporter.dispatch(controller, seed).controller
								assertEquals(current, controller.state.presentation.binding, cell)
							}

							val event = reporter.update(
								confirmedBinding,
								incoming,
								false,
								destinationSuccessor
							)
							val accepted = destinationSuccessor || !currentComplete || incomingComplete
							if (!accepted) {
								assertNull(event, cell)
							} else {
								val emitted = assertNotNull(event, cell)
								controller = reporter.dispatch(controller, emitted).controller
								assertEquals(incoming, controller.state.presentation.binding, cell)
								assertEquals(
									requestToken,
									(controller.state.presentation.authority as
										ReaderPresentationAuthority.BlockingPreparation)
										.nativePresentationRequest?.token,
									cell
								)
								assertNull(
									reporter.update(
										confirmedBinding,
										incoming,
										false,
										destinationSuccessor
									),
									cell
								)
								assertEquals(incoming, reporter.lastReportedBinding, cell)
							}
						}
					}
				}
			}
		}
	}

	@Test
	fun bindingReporterDirectCompleteRelocationSettlesTheReducerWithoutRetry() {
		val partial = ReaderPresentationBinding(
			foliateSessionId = "direct-complete-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"direct-complete-session",
				4L
			),
			preparationGeneration = 5L
		)
		val complete = partial.copy(
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"direct-complete-session",
				5L
			),
			rasterGeneration = 6L,
			textureGeneration = 7L
		)
		val reporter = ReaderPresentationBindingReporter()
		var controller = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 8L,
				presentation = readerPresentationReduce(
					readerPresentationReduce(
						ReaderPresentationState(nextTokenValue = 8L),
						ReaderPresentationEvent.PublicationOpened(partial)
					).state,
					ReaderPresentationEvent.NativePageRequested
				).state
			)
		)
		controller = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePageRequested
		).controller

		val relocation = assertIs<ReaderPresentationEvent.FoliateRelocated>(
			reporter.update(partial, complete, false, true)
		)
		controller = reporter.dispatch(controller, relocation).controller
		assertNull(reporter.update(partial, complete, false, false))
		assertEquals(complete, reporter.lastReportedBinding)
		val request = requireNotNull(
			(controller.state.presentation.authority as
				ReaderPresentationAuthority.BlockingPreparation)
				.nativePresentationRequest
		)
		assertEquals(ReaderPresentationToken(8L), request.token)
		assertEquals(complete, request.binding)

		val proof = ReaderNativePagePresentationProof(
			binding = complete,
			transitionToken = request.token,
			presentedFrame = 9L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = 6L,
			textureGeneration = 7L
		)
		val presented = reporter.dispatch(
			controller,
			ReaderPresentationEvent.NativePagePresented(proof)
		)
		assertEquals(
			ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			presented.controller.state.presentation.authority
		)
		assertTrue(presented.presentationEffects.isEmpty())
	}

	@Test
	fun coldCanvasBindingIsPartialUntilAnExactPreparedDeckArrives() {
		val destination = ReaderDestinationCommitIdentity("fixture-session", 4L)
		val snapshot = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = destination,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)
		val partial = assertNotNull(readerPresentationHostBinding(snapshot))

		assertEquals("fixture-session", partial.foliateSessionId)
		assertEquals(3L, partial.profileGeneration)
		assertEquals(destination, partial.destinationCommitIdentity)
		assertEquals(6L, partial.preparationGeneration)
		assertNull(partial.rasterGeneration)
		assertNull(partial.textureGeneration)

		val exactDeck = ReaderPagePreparedActiveDeck(
			rasterProfileEpoch = 3L,
			rasterEpoch = 8L,
			sourceCenterPageIndex = 7,
			generationId = 9L,
			preparationGeneration = 6L
		)
		assertEquals(
			partial.copy(rasterGeneration = 8L, textureGeneration = 9L),
			readerPresentationHostBinding(
				snapshot.copy(preparedDeck = exactDeck, preparedDeckAdmitted = true)
			)
		)
	}

	@Test
	fun staleLateDeckNeverCompletesOrFabricatesRendererIdentity() {
		val base = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = true
		)
		val staleDecks = listOf(
			ReaderPagePreparedActiveDeck(4L, 8L, 7, 9L, 6L),
			ReaderPagePreparedActiveDeck(3L, 8L, 8, 9L, 6L),
			ReaderPagePreparedActiveDeck(3L, 8L, 7, 9L, 5L)
		)

		staleDecks.forEach { staleDeck ->
			val binding = assertNotNull(
				readerPresentationHostBinding(base.copy(preparedDeck = staleDeck))
			)
			assertNull(binding.rasterGeneration)
			assertNull(binding.textureGeneration)
		}
		val unadmitted = assertNotNull(
			readerPresentationHostBinding(
				base.copy(
					preparedDeck = ReaderPagePreparedActiveDeck(3L, 8L, 7, 9L, 6L),
					preparedDeckAdmitted = false
				)
			)
		)
		assertNull(unadmitted.rasterGeneration)
		assertNull(unadmitted.textureGeneration)
	}

	@Test
	fun provisionalProfileBootstrapIsReportableWhileUnknownFactsStayFenced() {
		val completeFacts = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = true,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)

		assertNull(readerPresentationHostBinding(completeFacts.copy(foliateSessionId = null)))
		assertNull(readerPresentationHostBinding(completeFacts.copy(viewportWidth = 0)))
		val provisional = assertNotNull(
			readerPresentationHostBinding(
				completeFacts.copy(
					profileIdentity = ReaderPresentationHostProfileIdentity.Provisional
				)
			)
		)
		assertEquals(0L, provisional.profileGeneration)
		assertNull(provisional.rasterGeneration)
		assertNull(provisional.textureGeneration)
		assertNotNull(readerPresentationHostBinding(completeFacts))
	}

	@Test
	fun hostUsesTypedProfileIdentityAndRetriesBootstrapAfterFailure() {
		val bindingSource = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPresentationHostBinding.android.kt"
		).readText()
		val retry = hostFile.readText()
			.substringAfter("fun retryPreparation(effect: ReaderPresentationEffect.RetryPreparation)")
			.substringBefore("private fun onPreparedActiveDeckChanged")

		assertContains(bindingSource, "sealed interface ReaderPresentationHostProfileIdentity")
		assertContains(bindingSource, "data object Provisional")
		assertContains(bindingSource, "data class Resolved")
		assertFalse(bindingSource.contains("val profileGeneration: Long?"))
		assertContains(retry, "if (rasterProfileEpoch == null)")
		assertContains(retry, "requestPageTurnPrewarmWhenReady()")
	}

	@Test
	fun nonCanvasModeNeverBypassesPresentationPolicyToDirectWebView() {
		val snapshot = ReaderPresentationHostBindingSnapshot(
			pageTurnCanvasEnabled = false,
			windowVisible = true,
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			viewportWidth = 1200,
			viewportHeight = 800,
			profileIdentity = ReaderPresentationHostProfileIdentity.Resolved(3L),
			destinationCommitIdentity = null,
			preparationGeneration = 6L,
			visualPageIndex = 7,
			preparedDeck = null,
			preparedDeckAdmitted = false
		)

		assertNotNull(readerPresentationHostBinding(snapshot))
		val cold = coldLegacyLiveCompatibilityContext()
		assertEquals(
			ReaderPagePhysicalDispatchMode.Denied,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = false,
				presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly,
				legacyLiveCompatibilityContext = cold
			)
		)
		assertEquals(
			ReaderPagePhysicalDispatchMode.Denied,
			readerPagePhysicalDispatchMode(
				pageTurnCanvasEnabled = false,
				presentationInputPolicy = ReaderPresentationInputPolicy.RecoveryOnly
			)
		)
		val expectedByPolicy = listOf(
			ReaderPresentationInputPolicy.ChromeOnly to ReaderPagePhysicalDispatchMode.ChromeOnly,
			ReaderPresentationInputPolicy.ShellCover to ReaderPagePhysicalDispatchMode.ShellCover,
			ReaderPresentationInputPolicy.ClaimedCurl(ReaderPresentationToken(1L)) to
				ReaderPagePhysicalDispatchMode.PlayLikeCurl,
			ReaderPresentationInputPolicy.NativePage(
				paige.navic.reader.readerPageOperationPolicy(ReaderPageReadinessState())
			) to ReaderPagePhysicalDispatchMode.Denied,
			ReaderPresentationInputPolicy.LiveEngine to ReaderPagePhysicalDispatchMode.LiveEngine
		)
		expectedByPolicy.forEach { (policy, expected) ->
			assertEquals(
				expected,
				readerPagePhysicalDispatchMode(
					pageTurnCanvasEnabled = false,
					presentationInputPolicy = policy
				),
				"policy=$policy"
			)
		}
		listOf(
			ReaderPresentationLifecycleState.Background,
			ReaderPresentationLifecycleState.Destroyed
		).forEach { lifecycle ->
			assertEquals(
				ReaderPagePhysicalDispatchMode.Denied,
				readerPagePhysicalDispatchMode(
					pageTurnCanvasEnabled = false,
					presentationInputPolicy = ReaderPresentationInputPolicy.ChromeOnly,
					legacyLiveCompatibilityContext =
						ReaderLegacyLiveCompatibilityContext.Denied(lifecycle)
				)
			)
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun shellCoverPolicyDoesNotAdmitCueMapPointerCapture() {
		val (viewerClass, viewer) = task7Viewer(RuntimeEnvironment.getApplication())
		viewerClass.task7Field("presentationInputPolicy").set(
			viewer,
			ReaderPresentationInputPolicy.ShellCover
		)

		val admitted = viewerClass.task7Method("allowsCueMapInput").invoke(viewer) as Boolean

		assertFalse(admitted)
	}

	@Test
	fun physicalDispatchInvokesOnlyTheSelectedTypedTarget() {
		val calls = mutableListOf<String>()
		val target = object : ReaderPagePhysicalDispatchTarget {
			override fun dispatchCueMap(event: MotionEvent) = calls.add("cue-map")
			override fun dispatchChromeOnly(event: MotionEvent) = calls.add("chrome-only")
			override fun dispatchDenied(event: MotionEvent) = calls.add("denied")
			override fun dispatchLegacy(event: MotionEvent) = calls.add("legacy")
			override fun dispatchLegacyLive(event: MotionEvent) = calls.add("legacy-live")
			override fun dispatchPlayLikeCurl(event: MotionEvent) = calls.add("play-like-curl")
			override fun dispatchShellCover(event: MotionEvent) = calls.add("shell-cover")
			override fun dispatchLiveEngine(event: MotionEvent) = calls.add("live-engine")
		}
		val expectedByMode = mapOf(
			ReaderPagePhysicalDispatchMode.CueMap to "cue-map",
			ReaderPagePhysicalDispatchMode.ChromeOnly to "chrome-only",
			ReaderPagePhysicalDispatchMode.Denied to "denied",
			ReaderPagePhysicalDispatchMode.Legacy to "legacy",
			ReaderPagePhysicalDispatchMode.LegacyLive to "legacy-live",
			ReaderPagePhysicalDispatchMode.PlayLikeCurl to "play-like-curl",
			ReaderPagePhysicalDispatchMode.ShellCover to "shell-cover",
			ReaderPagePhysicalDispatchMode.LiveEngine to "live-engine"
		)
		val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
		try {
			expectedByMode.forEach { (mode, expected) ->
				calls.clear()
				assertTrue(
					readerDispatchPagePhysicalEvent(mode, event, target) {
						calls.add("fallback")
					}
				)
				assertEquals(listOf(expected), calls, "mode=$mode")
			}
			calls.clear()
			assertTrue(
				readerDispatchPagePhysicalEvent(null, event, target) {
					calls.add("fallback")
				}
			)
			assertEquals(listOf("fallback"), calls)
		} finally {
			event.recycle()
		}
	}

	@Test
	@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
	fun pendingNativeHandbackRevokesCapturedLiveEnginePointerUntilNativeProof() {
		val context = RuntimeEnvironment.getApplication()
		val (viewerClass, viewer) = task7Viewer(context)
		val liveActions = mutableListOf<Int>()
		val liveContent = object : View(context) {
			override fun onTouchEvent(event: MotionEvent): Boolean {
				liveActions += event.actionMasked
				return true
			}
		}.apply { isClickable = true }
		viewerClass.task7Method("replaceViewerContent", View::class.java)
			.invoke(viewer, liveContent)
		viewerClass.task7Field("pageTurnCanvasEnabled").setBoolean(viewer, true)
		viewer.measure(
			View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
			View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
		)
		viewer.layout(0, 0, 1200, 800)
		val applyDecision = viewerClass.task7Method(
			"applyPresentationDecision",
			ReaderPresentationDecision::class.java,
			ReaderRendererLossCancellationIdentity::class.java
		)
		fun apply(decision: ReaderPresentationDecision) {
			applyDecision.invoke(viewer, decision, null)
		}
		val binding = ReaderPresentationBinding(
			"task363-live-stream",
			1L,
			2L,
			3L,
			ReaderDestinationCommitIdentity("task363-live-stream", 1L),
			4L,
			5L,
			6L
		)
		val liveProof = ReaderLiveEnginePresentationProof(
			ReaderPresentationToken(1L),
			binding,
			presentedFrameSequence = 1L
		)
		val exposed = ReaderPresentationState(
			authority = ReaderPresentationAuthority.LiveEngineExposed(
				ReaderPresentationFrameOwner.LiveEngine(liveProof)
			),
			binding = binding,
			lastLiveEnginePresentedFrameSequence = 1L,
			nextTokenValue = 2L
		)
		apply(readerPresentationDecision(exposed))
		val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
		val move = MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_MOVE, 180f, 100f, 0)
		val up = MotionEvent.obtain(0L, 2L, MotionEvent.ACTION_UP, 180f, 100f, 0)
		try {
			assertTrue(viewer.dispatchTouchEvent(down))
			val pending = readerPresentationReduce(
				exposed,
				ReaderPresentationEvent.WebViewHandoffRequested(
					ReaderLiveEngineHandoffDirection.LiveEngineToNative
				)
			)
			apply(pending.decision)
			assertIs<ReaderPresentationFrameOwner.LiveEngine>(pending.decision.frameOwner)
			assertEquals(View.VISIBLE, liveContent.visibility)
			assertTrue(viewer.dispatchTouchEvent(move))
			assertTrue(viewer.dispatchTouchEvent(up))

			assertEquals(
				listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
				liveActions
			)

			val handback = assertIs<ReaderPresentationAuthority.LiveEngineHandoffPending>(
				pending.state.authority
			)
			val nativeProof = ReaderNativePagePresentationProof(
				binding = binding,
				transitionToken = handback.token,
				presentedFrame = 2L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = 4L,
				textureGeneration = 5L
			)
			val settled = readerPresentationReduce(
				pending.state,
				ReaderPresentationEvent.NativePagePresented(nativeProof)
			)
			apply(settled.decision)
			val nativeDown = MotionEvent.obtain(
				3L,
				3L,
				MotionEvent.ACTION_DOWN,
				100f,
				100f,
				0
			)
			try {
				assertTrue(viewer.dispatchTouchEvent(nativeDown))
				assertEquals(
					ReaderPagePhysicalDispatchMode.PlayLikeCurl,
					viewerClass.task7Field("physicalDispatchMode").get(viewer)
				)
			} finally {
				val cancel = MotionEvent.obtain(nativeDown).apply {
					action = MotionEvent.ACTION_CANCEL
				}
				try {
					viewer.dispatchTouchEvent(cancel)
				} finally {
					cancel.recycle()
					nativeDown.recycle()
				}
			}
		} finally {
			down.recycle()
			move.recycle()
			up.recycle()
		}
	}

	@Test
	fun legacyLivePointerStreamRevokesChangedContextOnceAndSuppressesLateTerminal() {
		val stream = ReaderLegacyLivePointerStream()
		val initial = legacyLivePointerContext()

		stream.begin(ReaderPagePhysicalDispatchMode.LegacyLive, initial)

		assertFalse(stream.revokeIfContextChanged(initial))
		assertTrue(stream.revokeIfContextChanged(initial.copy(shellCoverVisible = true)))
		assertFalse(stream.revokeIfContextChanged(initial.copy(shellCoverVisible = true)))
		assertFalse(stream.revoke())
		assertTrue(stream.suppressesOriginalTerminal)
		stream.finish()
		assertFalse(stream.suppressesOriginalTerminal)
		assertFalse(stream.revoke())
	}

	@Test
	fun nonLiveContentPointerStreamsNeverRevokeLiveDelivery() {
		ReaderPagePhysicalDispatchMode.entries
			.filterNot { mode ->
				mode == ReaderPagePhysicalDispatchMode.LegacyLive ||
					mode == ReaderPagePhysicalDispatchMode.LiveEngine
			}
			.forEach { mode ->
				val stream = ReaderLegacyLivePointerStream()
				val context = legacyLivePointerContext()

				stream.begin(mode, context)

				assertFalse(stream.revokeIfContextChanged(context.copy(shellCoverVisible = true)))
				assertFalse(stream.revoke())
				assertFalse(stream.suppressesOriginalTerminal)
			}
	}

	@Test
	fun physicalDispatchUsesTypedTargetsAndCancelsChangedLegacyStreams() {
		val source = hostFile.readText()
		val physicalDispatch = source
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("private fun dispatchLegacyLivePointerEvent")
		val viewerContainer = source
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
		val viewerReplacement = viewerContainer
			.substringAfter("fun replaceViewerContent(viewerView: View)")
			.substringBefore("fun detachViewerContent(viewerView: View?)")
		val compatibilityUpdate = viewerContainer
			.substringAfter("fun setLegacyLiveCompatibilityContext(")
			.substringBefore("fun setPageTurnCanvasEnabled(")
		val canvasUpdate = viewerContainer
			.substringAfter("fun setPageTurnCanvasEnabled(")
			.substringBefore("fun setPageTurnReadingDirection(")
		val decisionUpdate = viewerContainer
			.substringAfter("fun setPresentationDecision(")
			.substringBefore("fun releaseStalePresentation(")
		val authoritativeDecisionApplication = viewerContainer
			.substringAfter("fun applyPresentationDecision(")
			.substringBefore("private fun setLocalPageSafetyPolicy(")
		val shellCoverUpdate = viewerContainer
			.substringAfter("fun setShellCoverVisible(")
			.substringBefore("fun setPageOperationPolicy(")
		val physicalClose = viewerContainer
			.substringAfter("private fun closePhysicalPointerDelivery()")
			.substringBefore("private fun teardownTask4Resources()")

		assertContains(source, "ReaderPagePhysicalDispatchMode.Denied")
		assertContains(source, "ReaderPagePhysicalDispatchMode.ShellCover")
		assertContains(source, "ReaderPagePhysicalDispatchMode.LegacyLive")
		assertContains(source, "legacyLiveCompatibilityContext: ReaderLegacyLiveCompatibilityContext")
		assertContains(source, "internal interface ReaderPagePhysicalDispatchTarget")
		assertContains(source, "internal fun readerDispatchPagePhysicalEvent(")
		assertContains(source, "internal class ReaderLegacyLivePointerStream")
		assertContains(source, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(source, "physicalDispatchMode = ReaderPagePhysicalDispatchMode.Denied")
		assertContains(source, "action = MotionEvent.ACTION_CANCEL")
		assertContains(physicalDispatch, "legacyLivePointerStream.begin(")
		assertContains(physicalDispatch, "legacyLivePointerStream.suppressesOriginalTerminal")
		assertContains(physicalDispatch, "legacyLivePointerStream.finish()")
		assertContains(viewerReplacement, "cancelLegacyLivePointerStream()")
		assertContains(compatibilityUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(canvasUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(
			decisionUpdate,
			"presentationReceiptDispatcher.synchronizeComposeModel("
		)
		assertContains(authoritativeDecisionApplication, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(shellCoverUpdate, "cancelLegacyLivePointerStreamIfContextChanged()")
		assertContains(physicalClose, "cancelLegacyLivePointerStream()")
		assertContains(physicalClose, "legacyLivePointerStream.suppressesOriginalTerminal")
		assertFalse(
			source.contains(
				"if (!pageTurnCanvasEnabled) return ReaderPagePhysicalDispatchMode.LiveEngine"
			)
		)
	}

	@Test
	fun shellCoverIsDrawnBehindPredecessorUntilCommittedAuthoritySelectsIt() {
		val source = hostFile.readText()
		val preparation = source
			.substringAfter("fun prepareShellCoverForCommit(")
			.substringBefore("fun cancelShellCoverCommitPreparation(")
		val finalSelection = source
			.substringAfter("fun selectShellCover(")
			.substringBefore("fun setVerticalPageDragPreview(")
		val proofRetention = source
			.substringAfter("fun setShellCoverVisible(")
			.substringBefore("fun setPageOperationPolicy(")

		assertContains(preparation, "addView(shellCoverView, 0")
		assertContains(preparation, "shellCoverView.visibility = VISIBLE")
		assertContains(preparation, "shellCoverView.isClickable = false")
		assertFalse(preparation.contains("invalidate(\"shell-cover-visible\")"))
		assertContains(finalSelection, "shellCoverView.bringToFront()")
		assertContains(finalSelection, "preserveNativePresentationProof")
		assertContains(
			proofRetention,
			"if (visibilityChanged && visible && !preserveNativePresentationProof)"
		)
		assertContains(proofRetention, "playLikeCurlController.invalidate(\"shell-cover-visible\")")
		assertContains(
			proofRetention,
			"pageRasterPreparationController.invalidate(\"shell-cover-visible\")"
		)
	}

	private fun Class<*>.task7SetPresentationDecision(
		viewer: Any,
		decision: paige.navic.reader.ReaderPresentationDecision
	) = task7Field("presentationDecision").set(viewer, decision)

	private fun Class<*>.task7CanvasEnabled(viewer: Any): Boolean =
		task7Field("pageTurnCanvasEnabled").getBoolean(viewer)

	private fun Class<*>.task7PendingNativeHandoff(
		viewer: Any
	): ReaderRequiredTransition.PresentNativePage? {
		val publisher = (task7Field("nativePagePresentationPublisher\$delegate")
			.get(viewer) as Lazy<*>).value ?: return null
		val timeout = publisher.javaClass.task7Field("pendingHandoffTimeout")
			.get(publisher) ?: return null
		return timeout.javaClass.task7Field("transition").get(timeout) as?
			ReaderRequiredTransition.PresentNativePage
	}

	private class Task8CurlAuthorityStore(
		initialState: ReaderPresentationState?,
		private val rejectClaims: Boolean,
		val acceptLocalTerminals: Boolean
	) {
		var state = initialState
		val presentationEvents = mutableListOf<ReaderPresentationEvent>()
		val localTerminalGestureIds = mutableListOf<Long>()

		fun publish(event: ReaderPresentationEvent): ReaderPresentationEventReceipt? {
			presentationEvents += event
			val current = state ?: return null
			if (rejectClaims && event is ReaderPresentationEvent.CurlClaimed) {
				return readerTestPresentationReceipt(
					event,
					current,
					ReaderPresentationEventDisposition.Rejected
				)
			}
			val reduction = readerPresentationReduce(current, event)
			state = reduction.state
			return readerTestPresentationReceipt(
				event,
				reduction.state,
				reduction.disposition
			)
		}
	}

	private class Task8UnsafeLifecycleRelay {
		var sink: (ReaderPageHostLifecycleEvent) -> Unit = {}

		fun dispatch(event: ReaderPageHostLifecycleEvent) {
			sink(event)
		}
	}

	private class Task8PreparedDeckRelay {
		var sink: (ReaderPagePreparedActiveDeck?) -> Unit = {}

		fun dispatch(deck: ReaderPagePreparedActiveDeck?) {
			sink(deck)
		}
	}

	private class Task8PresentedFrameSource : ReaderNativePagePresentedFrameSource {
		private var nextRequestId = 1L
		private val callbacks = linkedMapOf<Long, (Long) -> Unit>()

		override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long =
			nextRequestId++.also { requestId -> callbacks[requestId] = onPresented }

		override fun cancelPresentedFrameRequest(requestId: Long): Boolean =
			callbacks.remove(requestId) != null

		fun presentNext() {
			val next = callbacks.entries.first()
			callbacks.remove(next.key)
			next.value(next.key)
		}
	}

	private class DeckAdmissionHostHarness(
		initialState: ReaderPresentationState?
	) : ReaderDeckAdmissionLeaseHost {
		private var nextAdmissionId = 0L
		private var eventSequence = 1L
		var reservationsEnabled = true
		private var authority = initialState?.binding?.let { binding ->
			ReaderDeckAdmissionAuthoritySnapshot(
				hostEpoch = 1L,
				authorityVersion = paige.navic.reader.ReaderPresentationReceiptVersion(
					1L,
					binding.publicationIdentity,
					eventSequence
				),
				viewerGeneration = 1L,
				lifecycle = initialState.lifecycle,
				bindingSeed = binding,
				presentationToken = readerPresentationDecision(initialState)
					.rendererCallbackTokenOrNull(),
				decision = readerPresentationDecision(initialState),
				rendererSuccessorReceipt = initialState.rendererSuccessorReceipt
			)
		}

		override fun reserve(request: ReaderDeckAdmissionRequest): ReaderDeckAdmission? {
			if (!reservationsEnabled) return null
			val admissionId = nextAdmissionId + 1L
			val capability = readerDeckAdmissionCapabilityOrNull(
				authority = authority,
				admissionId = admissionId,
				request = request
			) ?: return null
			nextAdmissionId = admissionId
			return ReaderDeckAdmission(capability)
		}

		override fun currency(
			admission: ReaderDeckAdmission,
			observedDecision: ReaderPresentationDecision?
		): ReaderDeckAdmissionCurrency = readerDeckAdmissionAuthorityCurrency(
			authority,
			admission,
			observedDecision
		)

		override fun isCurrent(admission: ReaderDeckAdmissionCapability): Boolean =
			readerDeckAdmissionAuthorityMatches(authority, admission)

		override fun isOwnerCurrent(admission: ReaderDeckAdmissionCapability): Boolean =
			readerDeckAdmissionOwnerAuthorityMatches(authority, admission)

		fun captureEpoch(): Long = authority?.hostEpoch ?: 0L

		fun advanceHostEpoch() {
			val current = checkNotNull(authority)
			authority = current.copy(hostEpoch = current.hostEpoch + 1L)
		}

		fun synchronizeDecision(decision: ReaderPresentationDecision) {
			val current = authority ?: return
			authority = current.copy(
				presentationToken = decision.rendererCallbackTokenOrNull(),
				decision = decision
			)
		}

		fun advanceCompatibleVersion() {
			val current = authority ?: return
			eventSequence += 1L
			authority = current.copy(
				authorityVersion = current.authorityVersion.copy(eventSequence = eventSequence)
			)
		}

		fun rewindVersion() {
			val current = authority ?: return
			authority = current.copy(
				authorityVersion = current.authorityVersion.copy(
					eventSequence = (current.authorityVersion.eventSequence - 1L).coerceAtLeast(0L)
				)
			)
		}

		fun revoke() {
			authority = null
		}

		fun advanceViewer() {
			val current = authority ?: return
			eventSequence += 1L
			val viewerGeneration = current.viewerGeneration + 1L
			authority = current.copy(
				authorityVersion = paige.navic.reader.ReaderPresentationReceiptVersion(
					viewerGeneration,
					current.bindingSeed.publicationIdentity,
					eventSequence
				),
				viewerGeneration = viewerGeneration
			)
		}

		fun retireLifecycle() {
			val current = authority ?: return
			authority = current.copy(
				lifecycle = paige.navic.reader.ReaderPresentationLifecycleState.Background
			)
		}

		fun publishProfile(profileGeneration: Long?) {
			val current = authority ?: return
			if (profileGeneration == null) {
				authority = null
				return
			}
			eventSequence += 1L
			val binding = current.bindingSeed.copy(
				profileGeneration = profileGeneration,
				rasterGeneration = null,
				textureGeneration = null
			)
			authority = current.copy(
				authorityVersion = paige.navic.reader.ReaderPresentationReceiptVersion(
					current.viewerGeneration,
					binding.publicationIdentity,
					eventSequence
				),
				bindingSeed = binding
			)
		}

		fun commit(state: ReaderPresentationState, version: paige.navic.reader.ReaderPresentationReceiptVersion) {
			val binding = state.binding ?: run {
				authority = null
				return
			}
			eventSequence = maxOf(eventSequence, version.eventSequence)
			authority = ReaderDeckAdmissionAuthoritySnapshot(
				hostEpoch = authority?.hostEpoch ?: 1L,
				authorityVersion = version,
				viewerGeneration = version.readerSessionGeneration,
				lifecycle = state.lifecycle,
				bindingSeed = binding,
				presentationToken = readerPresentationDecision(state)
					.rendererCallbackTokenOrNull(),
				decision = readerPresentationDecision(state),
				rendererSuccessorReceipt = state.rendererSuccessorReceipt
			)
		}
	}

	private data class Task8CurlAuthorityFixture(
		val controller: ReaderPlayLikeCurlFoliateController,
		val store: Task8CurlAuthorityStore,
		val unsafeLifecycleRelay: Task8UnsafeLifecycleRelay,
		val preparedDeckRelay: Task8PreparedDeckRelay,
		val admissionHost: DeckAdmissionHostHarness
	)

	private fun task8SettledCurlSourceState(
		viewportGeneration: Long = 2L,
		viewportWidth: Int = 1200,
		viewportHeight: Int = 800,
		rasterGeneration: Long = 0L
	): ReaderPresentationState {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "critical-4-session",
			publicationGeneration = 1L,
			viewportGeneration = viewportGeneration,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity(
				"critical-4-session",
				1L
			),
			rasterGeneration = rasterGeneration,
			textureGeneration = 301L,
			preparationGeneration = 0L
		)
		val proof = ReaderNativePagePresentationProof(
			binding = binding,
			transitionToken = null,
			presentedFrame = 4L,
			viewportWidth = viewportWidth,
			viewportHeight = viewportHeight,
			rasterGeneration = rasterGeneration,
			textureGeneration = 301L
		)
		return ReaderPresentationState(
			authority = ReaderPresentationAuthority.SettledNativePage(
				ReaderPresentationFrameOwner.NativePage(proof)
			),
			binding = binding
		)
	}

	private fun task8CurlAuthorityFixture(
		initialState: ReaderPresentationState?,
		rejectClaims: Boolean = false,
		acceptLocalTerminals: Boolean = true,
		onActiveRasterReleased: () -> Unit = {},
		sourceIdentity: String = "critical-4-session",
		viewportWidth: Int = 1200,
		viewportHeight: Int = 800,
		physicalRect: ReaderPlayLikeCurlPhysicalRect = ReaderPlayLikeCurlPhysicalRect(0, 0, 2, 2),
		bundleSource: ReaderPageTurnBundleSource = ReaderPageTurnBundleSource(),
		prepareActiveDeck: Boolean = true,
		currentOrdinal: Int = 1,
		onRequestPrewarm: () -> Unit = {},
		beforePresentationEventPublished: (
			ReaderPlayLikeCurlFoliateController,
			ReaderPresentationEvent
		) -> Unit = { _, _ -> }
	): Task8CurlAuthorityFixture {
		val context = RuntimeEnvironment.getApplication()
		val host = FrameLayout(context).apply { layout(0, 0, viewportWidth, viewportHeight) }
		val webView = Task9RecoveryCommandWebView(context).also(host::addView)
		val store = Task8CurlAuthorityStore(
			initialState,
			rejectClaims,
			acceptLocalTerminals
		)
		val admissionHost = DeckAdmissionHostHarness(initialState)
		val unsafeLifecycleRelay = Task8UnsafeLifecycleRelay()
		val preparedDeckRelay = Task8PreparedDeckRelay()
		lateinit var controller: ReaderPlayLikeCurlFoliateController
		controller = ReaderPlayLikeCurlFoliateController(
			host = host,
			webViewProvider = { webView },
			presentationHostEpoch = admissionHost::captureEpoch,
			deckAdmissionHost = admissionHost,
			bundleSource = bundleSource,
			onRequestPrewarm = onRequestPrewarm,
			onRequestRasterRepair = { _, _ -> },
			onGestureTerminal = { gestureId, _, _ ->
				store.localTerminalGestureIds += gestureId
				store.acceptLocalTerminals
			},
			onPresentationEvent = { event ->
				beforePresentationEventPublished(controller, event)
				store.publish(event).also { receipt ->
					receipt?.let { admissionHost.commit(it.postState, it.version) }
				}
			},
			onRasterProfileEpochChanged = admissionHost::publishProfile,
			onUnsafeLifecycleEvent = unsafeLifecycleRelay::dispatch,
			onPreparedActiveDeckChanged = preparedDeckRelay::dispatch
		)
		val profile = ReaderPlayLikeCurlRasterProfile(
			sourceIdentity = sourceIdentity,
			orientation = ReaderPlayLikeCurlOrientation.Portrait,
			quality = ReaderPageBitmapQuality.Balanced,
			pageCount = 3,
			rasterGeneration = bundleSource.currentGeneration()
		)
		val controllerClass = controller.javaClass
		controllerClass.task7Field("enabled").setBoolean(controller, true)
		controllerClass.task7Field("attached").setBoolean(controller, true)
		controllerClass.task7Field("currentFoliateSessionId")
			.set(controller, "critical-4-session")
		controllerClass.task7Field("requestedProfile").set(controller, profile)
		controllerClass.task7Field("currentOrdinal").setInt(controller, currentOrdinal)
		controller.setPageOperationPolicy(
			readerPageOperationPolicy(
				ReaderPageReadinessState(
					textureDeck = ReaderTextureDeckState.Ready,
					interaction = ReaderPageInteractionState.Ready
				)
			)
		)
		initialState?.let {
			controller.synchronizePresentationDecision(readerPresentationDecision(it))
		}
		val profileEpoch = initialState?.binding?.profileGeneration?.takeIf { it > 0L } ?: 1L
		controllerClass.task7Field("nextRasterProfileEpoch").setLong(controller, profileEpoch)
		controllerClass.task7Method(
			"publishRasterProfileEpoch",
			ReaderPlayLikeCurlRasterProfile::class.java
		).invoke(controller, profile)
		val surface = controller.surfaceView
		task8PrepareSurface(surface)
		surface.layout(0, 0, viewportWidth, viewportHeight)
		controller.onHostResumedChanged(true)
		task8SubmitRecoveredDeck(
			controller = controller,
			generationId = 301L,
			role = ReaderDeckSubmissionRole.Active,
			profile = profile,
			onRasterReleased = onActiveRasterReleased,
			populateRasterWindow = true,
			physicalRect = physicalRect,
			expectedSubmissionResult = if (initialState == null) {
				ReaderPageRecoveredDeckSubmissionResult.AwaitingAdmission
			} else {
				ReaderPageRecoveredDeckSubmissionResult.Accepted
			}
		)
		if (initialState != null && prepareActiveDeck) {
			surface.javaClass.task7Method("handleDeckPrepared", java.lang.Long.TYPE)
				.invoke(surface, 301L)
			assertTrue(controller.isAvailable)
		} else if (initialState != null) {
			assertTrue(task8SurfaceOwnsGeneration(surface, 301L))
			assertFalse(controller.isAvailable)
		} else {
			assertFalse(task8SurfaceOwnsGeneration(surface, 301L))
			assertFalse(controller.isAvailable)
		}
		return Task8CurlAuthorityFixture(
			controller,
			store,
			unsafeLifecycleRelay,
			preparedDeckRelay,
			admissionHost
		)
	}

	private data class Task8RendererFailureProjection(
		val bridge: ReaderPresentationHostBridge,
		val projectedDecisions: List<ReaderPresentationDecision>
	)

	private fun task8BindRendererFailureProjection(
		fixture: Task8CurlAuthorityFixture
	): Task8RendererFailureProjection {
		val binding = checkNotNull(fixture.store.state?.binding)
		val initialState = checkNotNull(fixture.store.state)
		var reporterController = ReaderController(
			ReaderControllerState(
				readerSessionGeneration = 1L,
				presentation = initialState
			)
		)
		val bindingReporter = ReaderPresentationBindingReporter().also { reporter ->
			reporter.reset(
				expectedReaderSessionGeneration = 1L,
				minimumComposeVersion = reporterController.presentationVersion,
				initialPresentationState = initialState
			)
			check(reporter.bindPublication(binding))
		}
		val lifecycleDelivery = ReaderPresentationLifecycleDelivery().also { delivery ->
			delivery.reset(reporterController.presentationVersion, observedWindowVisible = null)
			check(delivery.bindPublication(binding.publicationIdentity))
		}
		val receiptDispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter,
			lifecycleDelivery
		) { _, _ -> }
		fun dispatchThroughProductionReporter(
			event: ReaderPresentationEvent
		): ReaderPresentationEventReceipt? = receiptDispatcher.dispatch(event) { dispatched ->
			val step = reporterController.onPresentationEvent(dispatched)
			reporterController = step.controller
			step.presentationReceipt
		}
		val initialProof = assertIs<ReaderPresentationAuthority.SettledNativePage>(
			initialState.authority
		).frame.proof
		checkNotNull(
			dispatchThroughProductionReporter(
				ReaderPresentationEvent.NativePagePresented(initialProof)
			)
		)
		val projected = mutableListOf<ReaderPresentationDecision>()
		val bridge = ReaderPresentationHostBridge(
			host = FakeReaderPresentationCommitHost(binding) { decision ->
				projected += decision
				when (decision.frameOwner) {
					is ReaderPresentationFrameOwner.NativePage ->
						fixture.controller.surfaceView.alpha = 1f
					is ReaderPresentationFrameOwner.LiveEngine,
					ReaderPresentationFrameOwner.Neutral ->
						fixture.controller.surfaceView.alpha = 0f
					else -> Unit
				}
			},
			liveEngineVisualHandoff = ReaderWebViewVisualHandoff(Task7VisualHandoffHost()),
			liveEngineExposureRequired = { false },
			onEvent = fixture.store::publish
		)
		bridge.update(readerPresentationDecision(checkNotNull(fixture.store.state)))
		fixture.unsafeLifecycleRelay.sink = { hostEvent ->
			val lifecycleEvent = ReaderPresentationEvent.Lifecycle(
				ReaderPresentationLifecycleEvent.RendererLost
			)
			lifecycleDelivery.observe(ReaderPresentationLifecycleEvent.RendererLost)
			checkNotNull(dispatchThroughProductionReporter(lifecycleEvent))
			val receipt = checkNotNull(fixture.store.publish(lifecycleEvent))
			check(receipt.authorizes(lifecycleEvent))
			val decision = readerPresentationDecision(receipt.postState)
			fixture.controller.synchronizePresentationDecision(decision)
			bridge.update(decision)
			fixture.controller.cancelActiveGesture(hostEvent.cancellationReason())
		}
		fixture.preparedDeckRelay.sink = { deck ->
			if (deck == null) {
				val previous = checkNotNull(bindingReporter.lastReportedBinding)
				val current = previous.copy(
					rasterGeneration = null,
					textureGeneration = null
				)
				val event = checkNotNull(
					bindingReporter.update(
						confirmedTargetBinding = fixture.store.state?.binding,
						currentBinding = current,
						publicationOpenPending = false,
						relocationPending = false
					)
				)
				fixture.store.publish(event)?.takeIf { it.authorizes(event) }?.let { receipt ->
					val decision = readerPresentationDecision(receipt.postState)
					fixture.controller.synchronizePresentationDecision(decision)
					bridge.update(decision)
				}
			}
		}
		return Task8RendererFailureProjection(bridge, projected)
	}

	private fun task8DeliverSurfaceRenderFailure(
		surface: karacken.curl.PageSurfaceView,
		failure: RenderFailure
	) {
		surface.javaClass.task7Method("handleRenderFailure", RenderFailure::class.java)
			.invoke(surface, failure)
	}

	@Suppress("UNCHECKED_CAST")
	private fun task8FailActiveRendererRehydration(
		surface: karacken.curl.PageSurfaceView
	) {
		val renderer = surface.javaClass.task7Field("renderer").get(surface)
		val activeDeck = checkNotNull(
			renderer.javaClass.task7Field("activeDeck").get(renderer)
		) as PageDeck<Bitmap>
		activeDeck.pages.first { !it.isFiller }.content.recycle()
		assertFalse(
			renderer.javaClass.task7Method(
				"rehydrateDeck",
				PageDeck::class.java,
				PageDeck::class.java,
				PageDeck::class.java
			).invoke(renderer, activeDeck, activeDeck, null) as Boolean
		)
		ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
	}

	private fun task8MotionEvent(action: Int): MotionEvent = MotionEvent.obtain(
		100L,
		101L,
		action,
		1f,
		1f,
		0
	)

	private fun task8RelocationOccupiedCount(
		controller: ReaderPlayLikeCurlFoliateController
	): Int {
		val queue = controller.javaClass.task7Field("relocationQueue").get(controller)
		return queue.javaClass.task7Method("occupiedCount").invoke(queue) as Int
	}

	private fun task8SurfaceGestureAccepted(surface: karacken.curl.PageSurfaceView): Boolean =
		surface.javaClass.task7Field("gestureAccepted").getBoolean(surface)

	private fun task7Viewer(context: Context): Pair<Class<*>, FrameLayout> {
		val viewerClass = Class.forName(
			"paige.navic.ui.screens.reader.KomikkuReaderNativeViewerContainer"
		)
		val viewer = viewerClass.getDeclaredConstructor(Context::class.java).run {
			isAccessible = true
			newInstance(context) as FrameLayout
		}
		return viewerClass to viewer
	}

	private fun Class<*>.task7Field(name: String) =
		getDeclaredField(name).apply { isAccessible = true }

	private fun Class<*>.task7Method(name: String, vararg parameters: Class<*>) =
		getDeclaredMethod(name, *parameters).apply { isAccessible = true }

	private fun task373PublishCurrentRasterProfile(
		controller: ReaderPlayLikeCurlFoliateController,
		expectedEpoch: Long
	): ReaderPlayLikeCurlRasterProfile {
		val controllerClass = controller.javaClass
		val profile = controllerClass.task7Field("requestedProfile").get(controller)
			as ReaderPlayLikeCurlRasterProfile
		val publishedField = controllerClass.task7Field("publishedRasterProfileEpoch")
		if (publishedField.get(controller) == null) {
			controllerClass.task7Field("nextRasterProfileEpoch").setLong(controller, expectedEpoch)
			controllerClass.task7Method(
				"publishRasterProfileEpoch",
				ReaderPlayLikeCurlRasterProfile::class.java
			).invoke(controller, profile)
		}
		assertEquals(expectedEpoch, publishedField.get(controller))
		return profile
	}

	private suspend fun task373DestroyController(
		controller: ReaderPlayLikeCurlFoliateController
	) {
		controller.surfaceView.detach()
		val closed = controller.destroy()
		ShadowLooper.runUiThreadTasks()
		withContext(Dispatchers.Default) {
			withTimeout(5_000L) { closed.await() }
		}
	}

	private fun task8PrepareSurface(surface: karacken.curl.PageSurfaceView) {
		val capabilities = RenderCapabilities(4_096, 8L * 1024L * 1024L)
		surface.javaClass.task7Field("attached").setBoolean(surface, true)
		surface.javaClass.task7Field("surfaceVisible").setBoolean(surface, true)
		surface.javaClass.task7Field("renderCapabilities").set(surface, capabilities)
		val renderer = surface.javaClass.task7Field("renderer").get(surface)
		renderer.javaClass.task7Field("maxTextureSize").setInt(renderer, 4_096)
		renderer.javaClass.task7Field("gpuBudgetBytes")
			.setLong(renderer, capabilities.gpuBudgetBytes)
	}

	private fun task8SubmitRecoveredDeck(
		controller: ReaderPlayLikeCurlFoliateController,
		generationId: Long,
		role: ReaderDeckSubmissionRole,
		profile: ReaderPlayLikeCurlRasterProfile,
		onRasterReleased: () -> Unit,
		populateRasterWindow: Boolean = false,
		physicalRect: ReaderPlayLikeCurlPhysicalRect = ReaderPlayLikeCurlPhysicalRect(0, 0, 2, 2),
		expectedSubmissionResult: ReaderPageRecoveredDeckSubmissionResult? =
			ReaderPageRecoveredDeckSubmissionResult.Accepted
	): Any {
		val controllerClass = controller.javaClass
		val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
			eraseColor(Color.WHITE)
			setHasAlpha(false)
		}
		val rasterImage = ReaderPlayLikeCurlRasterImage(
			bitmap = bitmap,
			paperColorArgb = Color.WHITE,
			layout = ReaderPlayLikeCurlRasterLayout(
				surfaceRectInWindow = physicalRect,
				fullLeafRect = ReaderPlayLikeCurlPhysicalRect(0, 0, physicalRect.width, physicalRect.height),
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null
			),
			leaf = ReaderPlayLikeCurlFoliateLeaf.Full
		)
		val rasterDeck = ReaderPlayLikeCurlRasterDeck(
			profile = profile,
			values = if (populateRasterWindow) {
				(0 until profile.pageCount).associateWith { rasterImage }
			} else {
				emptyMap()
			},
			releaseOwnership = {
				if (!bitmap.isRecycled) bitmap.recycle()
				onRasterReleased()
			}
		)
		val preparedPagesClass = controllerClass.declaredClasses.single {
			it.simpleName == "PreparedPages"
		}
		val preparedPages = preparedPagesClass.declaredConstructors.single().run {
			isAccessible = true
			newInstance(profile, rasterDeck, 1)
		}
		val generations = preparedPagesClass.task7Field("generations")
			.get(preparedPages) as MutableSet<Long>
		val owners = controllerClass.task7Field("generationOwners")
			.get(controller) as MutableMap<Long, Any>
		val preparedPageSets = controllerClass.task7Field("preparedPageSets")
			.get(controller) as MutableSet<Any>
		val builtRecoveredDecks = controllerClass.task7Field("builtRecoveredDecks")
			.get(controller) as MutableMap<Long, Any>
		generations += generationId
		preparedPageSets += preparedPages
		owners[generationId] = preparedPages
		val displayRect = PageDisplayRect(0, 0, 2, 2)
		val material = PageMaterial(
			generationId,
			Color.WHITE,
			Color.WHITE,
			Color.GRAY,
			Color.WHITE,
			PageLeafRole.FULL,
			displayRect,
			displayRect,
			null,
			1
		)
		fun page(ordinal: Int) = PageImage(
			generationId,
			"recovered-surface-$generationId-$ordinal",
			ordinal,
			2,
			2,
			displayRect,
			bitmap,
			material
		)
		val pageDeck = PortraitPageDeck(
			page(0),
			page(1),
			page(2),
			true,
			true
		)
		val builtRecoveredDeckClass = controllerClass.declaredClasses.single {
			it.simpleName == "BuiltRecoveredDeck"
		}
		val builtRecoveredDeck = builtRecoveredDeckClass.declaredConstructors.single().run {
			isAccessible = true
			newInstance(preparedPages, 1, pageDeck)
		}
		builtRecoveredDecks[generationId] = builtRecoveredDeck
		expectedSubmissionResult?.let { expected ->
			assertEquals(
				expected,
				controller.submitRecoveredDeck(generationId, role)
			)
		}
		return preparedPages
	}

	private fun task8ForwardingPageSurfaceListener(
		delegate: karacken.curl.PageSurfaceListener,
		onDeckSubmissionCapacityAvailable: () -> Unit = delegate::onDeckSubmissionCapacityAvailable,
		onRenderFailure: (RenderFailure) -> Unit = delegate::onRenderFailure,
		onDeckReleased: (Long, karacken.curl.DeckReleaseReason) -> Unit = delegate::onDeckReleased
	): karacken.curl.PageSurfaceListener = object : karacken.curl.PageSurfaceListener {
		override fun onCapabilitiesAvailable(capabilities: RenderCapabilities) = delegate.onCapabilitiesAvailable(capabilities)
		override fun onDeckPrepared(generationId: Long) = delegate.onDeckPrepared(generationId)
		override fun onDeckRejected(generationId: Long, reason: karacken.curl.DeckRejectionReason) = delegate.onDeckRejected(generationId, reason)
		override fun onDeckSubmissionCapacityAvailable() = onDeckSubmissionCapacityAvailable.invoke()
		override fun onRendererAvailabilityRestored() = delegate.onRendererAvailabilityRestored()
		override fun onPageOverlayUpdateCapacityAvailable(applied: Boolean) = delegate.onPageOverlayUpdateCapacityAvailable(applied)
		override fun onPageOverlayStateInvalidated() = delegate.onPageOverlayStateInvalidated()
		override fun onRenderFailure(failure: RenderFailure) = onRenderFailure.invoke(failure)
		override fun onGestureRejected(generationId: Long, reason: karacken.curl.GestureRejectionReason) = delegate.onGestureRejected(generationId, reason)
		override fun onGestureRejected(gestureId: Long, generationId: Long, reason: karacken.curl.GestureRejectionReason) = delegate.onGestureRejected(gestureId, generationId, reason)
		override fun onGestureRejected(gestureId: Long, generationId: Long, reason: karacken.curl.GestureRejectionReason, pageChange: PageChange) = delegate.onGestureRejected(gestureId, generationId, reason, pageChange)
		override fun onGestureCancelled(gestureId: Long, generationId: Long) = delegate.onGestureCancelled(gestureId, generationId)
		override fun onSettlementStarted(generationId: Long, source: String, target: String, pageChange: PageChange) = delegate.onSettlementStarted(generationId, source, target, pageChange)
		override fun onSettlementStarted(gestureId: Long, generationId: Long, source: String, target: String, pageChange: PageChange) = delegate.onSettlementStarted(gestureId, generationId, source, target, pageChange)
		override fun onSettlementCompleted(generationId: Long, current: String, ordinal: Int, pageChange: PageChange) = delegate.onSettlementCompleted(generationId, current, ordinal, pageChange)
		override fun onSettlementCompleted(gestureId: Long, generationId: Long, current: String, ordinal: Int, pageChange: PageChange) = delegate.onSettlementCompleted(gestureId, generationId, current, ordinal, pageChange)
		override fun onSettlementCancelled(generationId: Long, current: String) = delegate.onSettlementCancelled(generationId, current)
		override fun onSettlementCancelled(gestureId: Long, generationId: Long, current: String) = delegate.onSettlementCancelled(gestureId, generationId, current)
		override fun onDeckReleased(generationId: Long, reason: karacken.curl.DeckReleaseReason) =
			onDeckReleased.invoke(generationId, reason)
	}

	private fun task8BeginSurfaceSettlement(surface: karacken.curl.PageSurfaceView) {
		val coordinator = surface.javaClass.task7Field("deckCoordinator").get(surface)
		coordinator.javaClass.task7Method("beginSettlement").invoke(coordinator)
	}

	private fun task8SurfaceOwnsGeneration(
		surface: karacken.curl.PageSurfaceView,
		generationId: Long
	): Boolean {
		val leases = surface.javaClass.task7Field("leaseRegistry").get(surface)
		return leases.javaClass.task7Method("contains", java.lang.Long.TYPE)
			.invoke(leases, generationId) as Boolean
	}

	private fun task7CommonRelocationCoordinator(
		queue: ReaderPageRelocationQueue,
		request: ReaderPageRelocationRequest,
		host: ReaderWebViewVisualHandoffHost,
		publish: (ReaderPresentationEvent) -> paige.navic.reader.ReaderPresentationEventReceipt,
		requestPresentationEvent: (
			ReaderPresentationEvent
		) -> paige.navic.reader.ReaderPresentationEventReceipt = publish,
		publishRecovery: (
			ReaderPageRelocationRequest,
			ReaderWebViewVisualHandoffFailure
		) -> Unit = { _, reason -> error("Unexpected recovery: $reason") },
		publishLiveEngineHandoffTerminal: (
			ReaderPresentationEvent
		) -> paige.navic.reader.ReaderPresentationEventReceipt? = publish,
		presentedFrameSequenceSource: ((ReaderPresentationBinding) -> Long)? = null
	): ReaderPageRelocationVisualHandoffCoordinator =
		ReaderPageRelocationVisualHandoffCoordinator(
			queue = queue,
			host = host,
			currentState = {
				ReaderPageRelocationVisualState(
					true, true, request.foliateSessionId, request.destinationOrdinal,
					request.rasterGeneration, request.textureGeneration
				)
			},
			dispatch = { error("Unexpected later relocation") },
			publishRecovery = publishRecovery,
			finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
			validateContent = { _, validated ->
				validated(ReaderPageRelocationContentValidationResult.Accepted)
				ReaderPageRelocationContentValidationHandle.Completed
			},
			requestPresentationHandoff = {
				val event = ReaderPresentationEvent.WebViewHandoffRequested(
					ReaderLiveEngineHandoffDirection.NativeToLiveEngine
				)
				readerPresentationDecision(
					requestPresentationEvent(event).postState
				).requiredTransition as ReaderRequiredTransition.ExposeLiveEngine
			},
			commitLiveEngineExposure = { proof ->
				publish(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
			},
			publishLiveEngineHandoffTerminal = publishLiveEngineHandoffTerminal,
			presentedFrameSequenceSource = presentedFrameSequenceSource
		)

	private fun enqueueTask7Relocation(
		queue: ReaderPageRelocationQueue,
		gestureId: Long,
		sourceOrdinal: Int,
		destinationOrdinal: Int,
		foliateSessionId: String
	): ReaderPageRelocationRequest {
		val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			queue.reserve(gestureId)
		).reservation
		return assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			queue.enqueueReserved(
				reservation = reservation,
				rasterGeneration = 10L,
				textureGeneration = 20L,
				sourceOrdinal = sourceOrdinal,
				destinationOrdinal = destinationOrdinal,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = foliateSessionId
			)
		).request
	}

	private fun acknowledgeTask7Relocation(
		queue: ReaderPageRelocationQueue,
		request: ReaderPageRelocationRequest
	) {
		assertEquals(request, queue.commandToDispatch())
		assertTrue(
			queue.acknowledge(
				request.token.value,
				request.destinationOrdinal,
				request.foliateSessionId,
				request.rasterGeneration,
				request.textureGeneration
			)
		)
	}

	private fun ReaderPresentationBindingReporter.dispatch(
		controller: ReaderController,
		event: ReaderPresentationEvent
	): ReaderControllerStep {
		if (expectedReaderSessionGeneration == null) {
			reset(
				expectedReaderSessionGeneration = controller.state.readerSessionGeneration,
				minimumComposeVersion = controller.presentationVersion,
				initialPresentationState = controller.state.presentation,
				initialShellCoverVisible = controller.state.shellCoverVisible
			)
		}
		val epoch = captureEpoch()
		val step = controller.onPresentationEvent(event)
		val receipt = assertNotNull(step.presentationReceipt)
		val publicationBinding = receipt.postState.binding
			?: controller.state.presentation.binding
		assertTrue(bindPublication(assertNotNull(publicationBinding)))
		assertTrue(consumeReceipt(epoch, event, receipt))
		return step
	}

	private fun legacyLivePointerContext() = ReaderLegacyLivePointerContext(
		pageTurnCanvasEnabled = false,
		presentationDecision = null,
		compatibilityContext = coldLegacyLiveCompatibilityContext(),
		shellCoverVisible = false
	)

	private fun coldLegacyLiveCompatibilityContext() =
		ReaderLegacyLiveCompatibilityContext.ColdSession(
			identity = ReaderLegacyLiveCompatibilityIdentity(
				readerSessionGeneration = 1L,
				publication = ReaderPublicationIdentity(
					bookId = "book-1",
					title = "Book",
					resourceHref = "book.epub",
					kind = ReaderPublicationKind.Ebook,
					format = ReaderPublicationFormat.Epub
				),
				foliateSessionId = null,
				destinationCommitIdentity = null
			),
			lifecycle = ReaderPresentationLifecycleState.Foreground
		)
}

private class Task9ControlledPassiveRuntime : ReaderPassiveRasterRuntimePort<Bitmap> {
	class Commit(val manifest: ReaderPassiveRasterCaptureManifest, val sequence: Long,
		val callback: (ReaderPassiveRasterCaptureReceipt?) -> Unit)
	class Capture(val geometry: ReaderPassiveRasterGeometry, val callback: (Bitmap?) -> Unit)
	override val passiveSessionId = "task9-physical-worker"
	private var paused = false
	override var isRetired = false
		private set
	override val isReady get() = !paused && !isRetired
	private var pendingCommit: Commit? = null
	private var pendingCapture: Capture? = null
	val commits = kotlinx.coroutines.channels.Channel<Commit>(kotlinx.coroutines.channels.Channel.UNLIMITED)
	val captures = kotlinx.coroutines.channels.Channel<Capture>(kotlinx.coroutines.channels.Channel.UNLIMITED)
	val deliveredBitmaps = mutableListOf<Bitmap>()
	val pendingCallbackCount get() = (if (pendingCommit != null) 1 else 0) +
		(if (pendingCapture != null) 1 else 0)

	override fun commit(manifest: ReaderPassiveRasterCaptureManifest, captureTarget: String,
		passiveCommitSequence: Long, onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit) {
		if (!isReady) { onCommitted(null); return }
		check(pendingCallbackCount == 0)
		check(manifest.opaqueCaptureTarget == captureTarget)
		val request = Commit(manifest, passiveCommitSequence, onCommitted)
		pendingCommit = request
		check(commits.trySend(request).isSuccess)
	}

	fun completeCommit(request: Commit) {
		check(isReady && pendingCommit === request)
		pendingCommit = null
		commits.tryReceive()
		val manifest = request.manifest
		// Same physical receipt construction as SuccessfulPassiveBitmapRuntime:
		// echo only the actual session-issued manifest and commit correlation.
		request.callback(ReaderPassiveRasterCaptureReceipt(
			passiveSessionId = passiveSessionId,
			echoedManifestSequence = manifest.manifestSequence,
			echoedCaptureEpoch = manifest.captureEpoch,
			echoedLiveFoliateSessionId = manifest.liveFoliateSessionId,
			echoedPublicationSessionGeneration = manifest.publicationSessionGeneration,
			echoedDestinationCommitToken = manifest.destinationCommitToken,
			echoedCaptureBasis = manifest.captureBasis,
			observedCaptureTarget = manifest.opaqueCaptureTarget,
			observedVisualPageOrdinal = manifest.visualPageOrdinal,
			observedRasterProfileKey = manifest.rasterProfileKey,
			observedPaginationFingerprint = manifest.paginationFingerprint,
			observedLayoutFingerprint = manifest.layoutFingerprint,
			observedDecorationFingerprint = manifest.decorationFingerprint,
			observedViewportAndCaptureGeometry = manifest.viewportAndCaptureGeometry,
			echoedRasterGeneration = manifest.rasterGeneration,
			passiveCommitSequence = request.sequence))
	}

	override fun capture(geometry: ReaderPassiveRasterGeometry, onCaptured: (Bitmap?) -> Unit) {
		if (!isReady) { onCaptured(null); return }
		check(pendingCallbackCount == 0)
		val request = Capture(geometry, onCaptured)
		pendingCapture = request
		check(captures.trySend(request).isSuccess)
	}

	fun completeCapture(request: Capture) {
		check(isReady && pendingCapture === request)
		pendingCapture = null
		captures.tryReceive()
		val bitmap = Bitmap.createBitmap(request.geometry.captureWidth,
			request.geometry.captureHeight, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
		deliveredBitmaps += bitmap
		request.callback(bitmap)
	}

	override fun cancelActiveCommit(onDrained: () -> Unit) {
		val commit = pendingCommit
		val capture = pendingCapture
		pendingCommit = null
		pendingCapture = null
		while (commits.tryReceive().isSuccess) { }
		while (captures.tryReceive().isSuccess) { }
		try {
			try { commit?.callback?.invoke(null) } finally { capture?.callback?.invoke(null) }
		} finally { onDrained() }
	}
	override fun pause() {
		paused = true
		cancelActiveCommit { }
	}
	override fun resume() { if (!isRetired) paused = false }
	override fun destroy() {
		if (isRetired) return
		isRetired = true
		cancelActiveCommit { }
		commits.close()
		captures.close()
	}
}

private class Task9RecoveryCommandWebView(
	context: Context,
	private val syntheticPageCount: Int = 3
) : WebView(context) {
	// Robolectric's stub provider omits Chromium's superclass-frame delegation.
	// The hidden Android method is absent from public SDK compile stubs.
	protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
		Shadow.directlyOn<Boolean, View>(
			this, View::class.java, "setFrame",
			ClassParameter.from(Int::class.javaPrimitiveType!!, left),
			ClassParameter.from(Int::class.javaPrimitiveType!!, top),
			ClassParameter.from(Int::class.javaPrimitiveType!!, right),
			ClassParameter.from(Int::class.javaPrimitiveType!!, bottom)
		)

	var sourceBinding: (() -> ReaderPresentationBinding)? = null
	var manifestRequests = 0
		private set
	var descriptorRequests = 0
		private set
	// Synthetic Foliate input, using the existing prototype manifest/descriptor schema.
	// Geometry and correlations come from this receiver and the actual bridge request.
	private fun descriptor(ordinal: Int) = org.json.JSONObject().apply {
		put("publicationUrl", "task9-synthetic-publication")
		put("paginationFingerprint", "task9-pagination-$width-$height")
		put("layoutFingerprint", "task9-layout-$width-$height")
		put("decorationFingerprint", "task9-undecorated")
		put("viewportWidth", width)
		put("viewportHeight", height)
		put("pageCount", syntheticPageCount)
		put("spineIndex", 0)
		put("href", "task9-synthetic-chapter")
		put("chapterPageIndex", ordinal)
		put("chapterPageCount", syntheticPageCount)
		put("visualPageOrdinal", ordinal)
	}

	private fun manifest(arguments: org.json.JSONArray): String {
		check(arguments.length() == 5)
		val binding = checkNotNull(sourceBinding).invoke()
		val ordinal = arguments.getInt(0)
		val descriptor = descriptor(ordinal)
		val json = org.json.JSONObject().apply {
			put("captureEpoch", arguments.getLong(1))
			put("rasterGeneration", arguments.getLong(2))
			put("liveFoliateSessionId", binding.foliateSessionId)
			put("publicationSessionGeneration", binding.publicationGeneration)
			// Echo an initial basis from the request, never reconstruct it from fixture state.
			if (arguments.isNull(4)) {
				put("destinationCommitToken", "task9-commit-${checkNotNull(binding.destinationCommitIdentity).commitSequence}")
			} else {
				put("captureBasis", arguments.getJSONObject(4))
			}
			put("rasterProfileKey", "task9-profile-$width-$height")
			for (key in listOf("paginationFingerprint", "layoutFingerprint", "decorationFingerprint")) {
				put(key, descriptor.getString(key))
			}
			put("viewportAndCaptureGeometry", org.json.JSONObject().apply {
				put("viewportWidth", width); put("viewportHeight", height)
				put("captureLeft", 0); put("captureTop", 0)
				put("captureRight", width); put("captureBottom", height)
			})
			put("opaqueCaptureTarget", "task9-target-$ordinal")
			put("visualPageOrdinal", ordinal)
			put("profileAuthority", ReaderPassiveRasterProfileAuthority.LiveRealized.serializedValue)
			put("rasterDescriptor", descriptor)
		}
		return org.json.JSONObject.quote(json.toString())
	}

	val recoveryReasons = mutableListOf<String>()
	var retainPlanCallbacks = false
	var onPlanRequested: ((String) -> Unit)? = null
	val planCallbacks = mutableListOf<android.webkit.ValueCallback<String>>()
	// Task336 opts into source completion; all existing Task9 receivers keep their behavior.
	var onInitialLiveCommand: ((org.json.JSONObject, android.webkit.ValueCallback<String>?) -> Unit)? = null
	var onInitialLiveReceiptRequested: ((android.webkit.ValueCallback<String>) -> Boolean)? = null
	var onPassiveManifestRequested: (() -> String?)? = null
	override fun evaluateJavascript(script: String, resultCallback: android.webkit.ValueCallback<String>?) {
		if (sourceBinding != null && script.contains("pageTurnPassiveRasterManifestInputs")) {
			manifestRequests++
			onPassiveManifestRequested?.invoke()?.let { response ->
				resultCallback?.onReceiveValue(response)
				return
			}
			val arguments = org.json.JSONArray("[" + script
				.substringAfter("pageTurnPassiveRasterManifestInputs?.(")
				.substringBeforeLast(") ?? null)") + "]")
			resultCallback?.onReceiveValue(manifest(arguments))
			return
		}
		if (sourceBinding != null && (script.contains("pageTurnRasterDescriptor") ||
			script.contains("pageTurnPassiveRasterDescriptor"))) {
			descriptorRequests++
			val ordinal = checkNotNull(Regex("""\((\d+)\)""").find(script)).groupValues[1].toInt()
			resultCallback?.onReceiveValue(org.json.JSONObject.quote(descriptor(ordinal).toString()))
			return
		}
		if (retainPlanCallbacks && script.contains("pageTurnRasterPreparationPlan") && resultCallback != null) {
			onPlanRequested?.let { observe ->
				// Retain only an allowlisted producer label, never a stack or script.
				val stack = Throwable().stackTrace
				val producer = listOf("signalRasterCapacityAvailable", "retireRasterAdapter", "onRasterProofReady",
					"onPreparationStateChanged", "retryPreparation", "onHostContentReady", "onCapabilitiesAvailable")
					.firstOrNull { name -> stack.any { it.methodName.startsWith(name) } } ?: "Other"
				observe(producer)
			}
			planCallbacks += resultCallback
			return
		}
		if (script == "JSON.stringify(window.NavicReaderBridge?.pageTurnLivePresentationReceiptAndRepublishActiveMediaOverlayAnchor?.() ?? null)" &&
			resultCallback != null && onInitialLiveReceiptRequested?.invoke(resultCallback) == true) return
		if (script.startsWith("window.NavicReaderBridge?.dispatch?.(")) {
			val command = runCatching {
				org.json.JSONObject(script.substringAfter("dispatch?.(").substringBeforeLast(")"))
			}.getOrNull()
			if (command?.optString("type") == "goToVisualPage" &&
				command.optString("settleToken").startsWith("initial-live-")) {
				onInitialLiveCommand?.let { receive ->
					receive(command, resultCallback)
					return
				}
			}
			if (command?.optString("type") == "diagnosticLocationSnapshot") {
				command.optString("reason").takeIf { it.startsWith("presentation-recovery-") }
					?.let(recoveryReasons::add)
			}
		}
		super.evaluateJavascript(script, resultCallback)
	}
}

// This isolated lookup supplies only the fixture's lifecycle owner; attachment and
// lifecycle observer registration/delivery still run through the real viewer host.
@Implements(className = "androidx.lifecycle.ViewTreeLifecycleOwner", isInAndroidSdk = false)
internal class Task336ViewTreeLifecycleOwnerShadow {
	companion object {
		private var root: View? = null
		private var owner: LifecycleOwner? = null

		fun install(view: View, lifecycleOwner: LifecycleOwner) {
			check(root == null && owner == null)
			root = view
			owner = lifecycleOwner
		}

		fun clear() {
			root = null
			owner = null
		}

		@Implementation
		@JvmStatic
		fun get(view: View): LifecycleOwner? {
			var current: View? = view
			while (current != null) {
				if (current === root) return owner
				current = current.parent as? View
			}
			return null
		}
	}
}

// The host-test dependency lacks lifecycle-runtime's generated R class. Model
// the ordinary no-owner attachment without replacing any Navic lifecycle route.
@Implements(className = "androidx.lifecycle.ViewTreeLifecycleOwner", isInAndroidSdk = false)
internal class Task9NoViewTreeLifecycleOwnerShadow {
	companion object {
		@Implementation
		@JvmStatic
		fun get(view: View): androidx.lifecycle.LifecycleOwner? = null
	}
}

private class Task398MeasuredLiveWebView(context: Context) : WebView(context) {
	// Match the existing Task9 fixture's real View geometry delegation: the
	// Robolectric stub provider omits Chromium's superclass setFrame call.
	protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
		Shadow.directlyOn<Boolean, View>(
			this, View::class.java, "setFrame",
			ClassParameter.from(Int::class.javaPrimitiveType!!, left),
			ClassParameter.from(Int::class.javaPrimitiveType!!, top),
			ClassParameter.from(Int::class.javaPrimitiveType!!, right),
			ClassParameter.from(Int::class.javaPrimitiveType!!, bottom)
		)
}

@Implements(className = "androidx.webkit.internal.AssetHelper", isInAndroidSdk = false)
internal class Task398CanonicalStoragePathShadow {
	companion object {
		// AndroidX uses Android's '/' boundary for its real storage allowlist.
		// Normalize only host canonical separators; retain canonical resolution
		// and all real InternalStoragePathHandler validation/initialization.
		@JvmStatic
		@Implementation
		fun getCanonicalDirPath(directory: File): String =
			directory.canonicalPath.replace('\\', '/').trimEnd('/') + "/"
	}
}


// The concrete mapping invalidates FrameLayout's shadow-creator call site when a method
// Config changes. ViewGroup alone invalidates removal dispatch but can leave a creator
// bound to its earlier default shadow in Robolectric's reused sandbox (4.16.1).
// Both mappings retain ShadowViewGroup compatibility and delegate original ViewGroup work.
@Implements(android.view.ViewGroup::class)
internal open class Task398ExactMountRemovalShadow : org.robolectric.shadows.ShadowViewGroup() {
	@RealObject private lateinit var actual: android.view.ViewGroup

	@Implements(FrameLayout::class)
	class FrameLayoutShadow : Task398ExactMountRemovalShadow()

	private fun crossFailureBoundary() {
		val exactFailure = failure ?: return
		failure = null // Consume before external work; a retry must perform genuine removal.
		beforeFailure?.invoke()
		throw exactFailure
	}
	private fun removeWithBoundary(original: () -> Unit) {
		val targeted = actual === target
		if (targeted) { actualPrimitiveEntered = true; targetEntries++ }
		if (targeted && failurePosition == FailurePosition.BEFORE_ORIGINAL) crossFailureBoundary()
		original()
		if (targeted) { originalDelegateCompleted = true; completedOriginalDelegates++ }
		if (targeted && failurePosition == FailurePosition.AFTER_ORIGINAL) crossFailureBoundary()
	}
	@Implementation
	fun removeAllViews() = removeWithBoundary {
		Shadow.directlyOn<Any?, android.view.ViewGroup>(actual, android.view.ViewGroup::class.java, "removeAllViews")
	}
	@Implementation
	fun removeView(child: View) = removeWithBoundary {
		Shadow.directlyOn<Any?, android.view.ViewGroup>(actual, android.view.ViewGroup::class.java, "removeView",
			ClassParameter.from(View::class.java, child))
	}
	enum class FailurePosition { BEFORE_ORIGINAL, AFTER_ORIGINAL }
	companion object {
		var target: FrameLayout? = null
		var failure: Throwable? = null
		var failurePosition = FailurePosition.BEFORE_ORIGINAL
		var beforeFailure: (() -> Unit)? = null
		var actualPrimitiveEntered = false
		var originalDelegateCompleted = false
		var targetEntries = 0
		var completedOriginalDelegates = 0
		fun clear() {
			target = null; failure = null; beforeFailure = null
			failurePosition = FailurePosition.BEFORE_ORIGINAL
			actualPrimitiveEntered = false; originalDelegateCompleted = false
			targetEntries = 0; completedOriginalDelegates = 0
		}
	}
}


@Implements(android.view.ViewGroup::class)
internal open class Task398RemovalEntryInstallationShadow : org.robolectric.shadows.ShadowViewGroup() {
	@RealObject private lateinit var actual: android.view.ViewGroup
	@Implements(FrameLayout::class) class FrameLayoutShadow : Task398RemovalEntryInstallationShadow()
	@Implementation fun removeView(view: View) {
		if (actual === target && view === child) {
			entries++
			val hook = beforeOriginal; beforeOriginal = null
			if (hook != null) { hooks++; hook() }
		}
		Shadow.directlyOn<Any?, android.view.ViewGroup>(actual, android.view.ViewGroup::class.java, "removeView",
			ClassParameter.from(View::class.java, view))
	}
	companion object {
		var target: FrameLayout? = null
		var child: View? = null
		var beforeOriginal: (() -> Unit)? = null
		var entries = 0; var hooks = 0
		fun clear() { target = null; child = null; beforeOriginal = null; entries = 0; hooks = 0 }
	}
}

@Implements(WebView::class)
internal class Task398ForegroundRegistrationWebViewShadow : org.robolectric.shadows.ShadowWebView() {
	@RealObject private lateinit var actual: WebView
	@Implementation
	override fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
		Shadow.directlyOn<Any?, View>(actual, View::class.java, "addOnAttachStateChangeListener",
			ClassParameter.from(View.OnAttachStateChangeListener::class.java, listener))
		if (actual.javaClass.simpleName == "ReaderEngineWebView") {
			adds++
			listeners += listener
			afterAdd?.invoke()
		}
	}
	@Implementation
	override fun removeOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener) {
		if (actual.javaClass.simpleName == "ReaderEngineWebView") {
			beforeRemove?.invoke()
			if (failRemoval) { failRemoval = false; error("Synthetic exact listener removal failure") }
		}
		Shadow.directlyOn<Any?, View>(actual, View::class.java, "removeOnAttachStateChangeListener",
			ClassParameter.from(View.OnAttachStateChangeListener::class.java, listener))
		if (actual.javaClass.simpleName == "ReaderEngineWebView") { removes++; listeners -= listener }
	}
	companion object {
		var adds = 0
		var removes = 0
		var failRemoval = false
		var afterAdd: (() -> Unit)? = null
		var beforeRemove: (() -> Unit)? = null
		val listeners = linkedSetOf<View.OnAttachStateChangeListener>()
		fun clear() { adds = 0; removes = 0; failRemoval = false; afterAdd = null; beforeRemove = null; listeners.clear() }
	}
}

@Implements(WebView::class)
internal class Task398OriginalPauseWebViewShadow : org.robolectric.shadows.ShadowWebView() {
	var pauseCalls = 0
		private set

	@Implementation
	override fun onPause() {
		pauseCalls++
		super.onPause()
	}
}

@Implements(GLSurfaceView::class)
internal open class Task8ImmediateGlSurfaceViewShadow : ShadowGLSurfaceView() {
	@RealObject
	private lateinit var realView: GLSurfaceView

	@Implementation
	fun queueEvent(action: Runnable) {
		action.run()
		val callback = afterNextQueueEvent ?: return
		afterNextQueueEvent = null
		callback(realView)
	}

	@Implementation
	fun requestRender() = Unit

	companion object {
		var afterNextQueueEvent: ((GLSurfaceView) -> Unit)? = null
	}
}

private class Task7PresentationStore(initial: ReaderPresentationState) {
	var state: ReaderPresentationState = initial

	fun publish(
		event: ReaderPresentationEvent
	): paige.navic.reader.ReaderPresentationEventReceipt {
		val reduction = readerPresentationReduce(state, event)
		state = reduction.state
		return readerTestPresentationReceipt(event, reduction.state, reduction.disposition)
	}
}

private class Task7ProductionCompositionFixture(
	val queue: ReaderPageRelocationQueue,
	val request: ReaderPageRelocationRequest,
	initialState: ReaderPresentationState
) {
	var controller = ReaderController(
		state = ReaderControllerState(
			readerSessionGeneration = 1L,
			presentation = initialState
		)
	)
		private set
	val visualHost = Task7VisualHandoffHost()
	val rootHandoff = ReaderWebViewVisualHandoff(visualHost)
	val rootDeadlines = HostBridgeDeadlineScheduler()
	val receipts = mutableListOf<ReaderPresentationEventReceipt>()
	val recoveries = mutableListOf<ReaderWebViewVisualHandoffFailure>()
	val releasedClaims = mutableListOf<ReaderPageRelocationRequest>()
	private val commitHost = FakeReaderPresentationCommitHost(requireNotNull(initialState.binding))
	private var currentWebViewOrdinal = request.sourceOrdinal
	private val initialVersion = controller.presentationVersion
	private val bindingReporter = ReaderPresentationBindingReporter().also {
		it.reset(
			expectedReaderSessionGeneration = 1L,
			minimumComposeVersion = initialVersion,
			initialPresentationState = initialState
		)
		check(it.bindPublication(requireNotNull(initialState.binding)))
	}
	private val lifecycleDelivery = ReaderPresentationLifecycleDelivery().also {
		it.reset(initialVersion, observedWindowVisible = null)
		check(it.bindPublication(requireNotNull(initialState.binding).publicationIdentity))
	}
	private val dispatcher: ReaderPresentationReceiptDispatcher
	private val rootBridge = ReaderPresentationHostBridge(
		host = commitHost,
		liveEngineVisualHandoff = rootHandoff,
		liveEngineExposureRequired = { true },
		transitionTimeoutScheduler = rootDeadlines,
		transitionNowMillis = { 0L },
		onEvent = ::publishOrNull
	)
	val coordinator = ReaderPageRelocationVisualHandoffCoordinator(
		queue = queue,
		host = visualHost,
		currentState = {
			ReaderPageRelocationVisualState(
				true, true, request.foliateSessionId, currentWebViewOrdinal,
				request.rasterGeneration, request.textureGeneration
			)
		},
		dispatch = { error("Unexpected later relocation") },
		publishRecovery = { _, reason -> recoveries += reason },
		finalizePresentation = { _, _ -> error("Local exposure is forbidden") },
		validateContent = { _, validated ->
			validated(ReaderPageRelocationContentValidationResult.Accepted)
			ReaderPageRelocationContentValidationHandle.Completed
		},
		requestPresentationHandoff = { acknowledged ->
			val event = ReaderPresentationEvent.WebViewHandoffRequested(
				ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)
			val receipt = publishOrNull(event)
			if (!receipt.authorizes(event)) {
				null
			} else {
				(readerPresentationDecision(requireNotNull(receipt).postState).requiredTransition as?
					ReaderRequiredTransition.ExposeLiveEngine)?.takeIf {
					it.binding.foliateSessionId == acknowledged.foliateSessionId &&
						it.binding.rasterGeneration == acknowledged.rasterGeneration &&
						it.binding.textureGeneration == acknowledged.textureGeneration
				}
			}
		},
		commitLiveEngineExposure = { proof ->
			publishOrNull(ReaderPresentationEvent.LiveEngineExposureCommitted(proof))
		},
		publishLiveEngineHandoffTerminal = ::publishOrNull,
		onCompleted = releasedClaims::add
	)

	init {
		dispatcher = ReaderPresentationReceiptDispatcher(
			bindingReporter = bindingReporter,
			lifecycleDelivery = lifecycleDelivery,
			applyHostEffect = { effect ->
				effect.decision.targetBinding?.let { commitHost.currentBinding = it }
				val transition = effect.decision.requiredTransition as?
					ReaderRequiredTransition.ExposeLiveEngine
				if (transition?.direction == ReaderLiveEngineHandoffDirection.NativeToLiveEngine) {
					coordinator.synchronizeCommonPresentationHandoff(transition)
				} else {
					coordinator.synchronizeCommonPresentationDecision(effect.decision)
				}
				rootBridge.update(effect.decision)
			}
		)
	}

	fun publish(event: ReaderPresentationEvent): ReaderPresentationEventReceipt =
		requireNotNull(publishOrNull(event))

	fun setPageTurnVisualLocation(
		binding: ReaderPresentationBinding
	): ReaderPresentationEventReceipt {
		val relocation = ReaderPresentationEvent.FoliateRelocated(
			binding,
			acknowledgement = null
		)
		currentWebViewOrdinal = request.destinationOrdinal
		val receipt = publish(relocation)
		check(acknowledge())
		return receipt
	}

	private fun acknowledge(): Boolean {
		check(
			queue.acknowledge(
				request.token.value,
				request.destinationOrdinal,
				request.foliateSessionId,
				request.rasterGeneration,
				request.textureGeneration
			)
		)
		return coordinator.onAcknowledged(request)
	}

	fun deliverRootFrame() {
		visualHost.deliverVisual()
		visualHost.presentFrame()
	}

	fun close() = rootBridge.dispose()

	private fun publishOrNull(event: ReaderPresentationEvent): ReaderPresentationEventReceipt? =
		dispatcher.dispatch(event) { dispatched ->
			val step = controller.onPresentationEvent(dispatched)
			controller = step.controller
			requireNotNull(step.presentationReceipt).also(receipts::add)
		}
}

private class Task7VisualHandoffHost : ReaderWebViewVisualHandoffHost {
	var attached = true
	override val isAttachedToWindow: Boolean get() = attached
	override val visualStateOwnerGeneration: Long get() = ownerGeneration
	var ownerGeneration = 1L
	var visualPostCount = 0
		private set
	var delayedPostCount = 0
		private set
	private val visuals = ArrayDeque<ReaderWebViewVisualDeliveryCell>()
	private val frames = ArrayDeque<() -> Unit>()
	private val delays = ArrayDeque<() -> Unit>()

	override fun synchronizeVisualStateOwner() = Unit

	override fun abandonVisualStateCallbacks() {
		while (visuals.isNotEmpty()) visuals.removeFirst().abandonPhysicalOwnership()
	}

	override fun postVisualStateCallback(
		relocationToken: String,
		handoffAttemptId: Long,
		registration: ReaderWebViewVisualDeliveryCell
	) {
		visualPostCount += 1
		visuals.addLast(registration)
	}

	override fun postOnAnimation(action: () -> Unit) {
		frames.addLast(action)
	}

	override fun postDelayed(delayMillis: Long, action: () -> Unit) {
		delayedPostCount += 1
		delays.addLast(action)
	}

	override fun removeCallbacks(action: () -> Unit) {
		frames.remove(action)
		delays.remove(action)
	}

	fun takeVisual(): ReaderWebViewVisualDeliveryCell = visuals.removeFirst()

	fun deliverVisual() {
		check(takeVisual().deliver())
	}

	fun presentFrame() {
		frames.removeFirst().invoke()
	}

	fun timeOut() {
		delays.removeFirst().invoke()
	}
}
