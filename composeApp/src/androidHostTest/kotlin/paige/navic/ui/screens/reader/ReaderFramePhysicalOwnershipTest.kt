package paige.navic.ui.screens.reader

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.publicationIdentity
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P],
	// Scope mutation interception to the actual FrameLayout recipients. Registering
	// its ViewGroup base also replaces decor LinearLayout's specialized shadow.
	shadows = [Task398MaterialAddShadow.FrameLayoutShadow::class,
		Task398MaterialVisualStateShadow::class])
class ReaderFramePhysicalOwnershipTest {
	private val domain = ReaderLegacyPhysicalDomain(1L, ReaderLegacyFreezeToken(809L))
	@Test
	@Config(shadows = [Task398MaterialAddShadow::class, Task398MaterialAddShadow.FrameLayoutShadow::class])
	fun actualAddBeforeAndAfterThrowKeepSameUncertainTokenAndNeverDoubleAdd() {
		for (after in listOf(false, true)) {
			Task398MaterialAddShadow.clearAdd()
			val parent = FrameLayout(RuntimeEnvironment.getApplication()); val wrapper = View(parent.context)
			val source = ReaderFramePhysicalOwnershipAdapter()
			val lease = assertNotNull(source.reserve(parent, wrapper) { true })
			val sentinel = IllegalStateException("Synthetic exact add uncertainty")
			Task398MaterialAddShadow.targetAdd = parent; Task398MaterialAddShadow.failureAdd = sentinel
			Task398MaterialAddShadow.afterOriginal = after
			var escaped: Throwable? = null
			try { source.mount(lease) } catch (failure: Throwable) { escaped = failure }
			assertSame(sentinel, escaped); assertEquals(1, Task398MaterialAddShadow.addEntries)
			assertEquals(if (after) 1 else 0, Task398MaterialAddShadow.completedAdds)
			if (after) assertSame(parent, wrapper.parent) else assertNull(wrapper.parent)
			assertFalse(source.mount(lease)); assertEquals(1, Task398MaterialAddShadow.addEntries)
			assertNull(source.reserve(parent, wrapper) { true }, "T398_CORE_SAME_VIEW_UNCERTAIN_DENIED")
			assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
			val row = source.snapshotFrozenOwnership().single()
			assertEquals(lease.token, row.physicalIdentity.sourceLocalToken); assertEquals(ReaderLegacyResourceState.Reserved, row.state)
			assertEquals(ReaderTransitionResourceKind.FrameHandoff, row.kind); assertNull(row.ownerOrNull())
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(row.physicalIdentity, confirmed::add))
			assertEquals(listOf(row.physicalIdentity), confirmed); assertNull(wrapper.parent)
			assertIs<ReaderPortCommandResult.Rejected>(source.drainFrozenOwnership(row.physicalIdentity, confirmed::add))
			assertEquals(1, confirmed.size)
		}
		Task398MaterialAddShadow.clearAdd()
	}
	@Test
	@Config(shadows = [Task398MaterialAddShadow::class, Task398MaterialAddShadow.FrameLayoutShadow::class])
	fun actualRemoveBeforeAndAfterThrowRetainSameExactPrimitiveForOneRetry() {
		for (position in Task398ExactMountRemovalShadow.FailurePosition.entries) {
			Task398ExactMountRemovalShadow.clear()
			val parent = FrameLayout(RuntimeEnvironment.getApplication()); val wrapper = View(parent.context)
			val source = ReaderFramePhysicalOwnershipAdapter(); val lease = assertNotNull(source.reserve(parent, wrapper) { true })
			assertTrue(source.mount(lease)); assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
			val identity = source.snapshotFrozenOwnership().single().physicalIdentity
			val sentinel = IllegalStateException("Synthetic exact remove uncertainty")
			Task398ExactMountRemovalShadow.target = parent; Task398ExactMountRemovalShadow.failure = sentinel
			Task398ExactMountRemovalShadow.failurePosition = position
			val result = source.retire(lease)
			assertSame(sentinel, result.failure); assertFalse(result.completed)
			assertEquals(1, Task398ExactMountRemovalShadow.targetEntries)
			assertEquals(identity, source.snapshotFrozenOwnership().single().physicalIdentity)
			assertEquals(ReaderLegacyResourceState.ReleaseRequested, source.snapshotFrozenOwnership().single().state)
			if (position == Task398ExactMountRemovalShadow.FailurePosition.BEFORE_ORIGINAL) assertSame(parent, wrapper.parent) else assertNull(wrapper.parent)
			assertIs<ReaderPortCommandResult.Rejected>(source.restoreAfterTransitionActivation(domain))
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(identity, confirmed::add))
			assertEquals(2, Task398ExactMountRemovalShadow.targetEntries); assertEquals(listOf(identity), confirmed)
			assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
			assertNull(wrapper.parent, "T398_CORE_PERMANENT_REMOVAL_NO_RESURRECTION")
		}
		Task398ExactMountRemovalShadow.clear()
	}
	@Test fun reversibleActivationDrainRestoresOnlyStillCurrentExactInstallation() {
		val parent = FrameLayout(RuntimeEnvironment.getApplication()); val wrapper = View(parent.context)
		val source = ReaderFramePhysicalOwnershipAdapter(); val lease = assertNotNull(source.reserve(parent, wrapper) { true })
		assertTrue(source.mount(lease)); source.freezeForTransitionActivation(domain)
		val identity = source.snapshotFrozenOwnership().single().physicalIdentity
		assertEquals(ReaderPortCommandResult.Accepted, source.drainFrozenOwnership(identity) { })
		assertNull(wrapper.parent)
		assertEquals(ReaderPortCommandResult.Accepted, source.restoreAfterTransitionActivation(domain))
		assertSame(parent, wrapper.parent); source.freezeForTransitionActivation(domain)
		assertEquals(identity, source.snapshotFrozenOwnership().single().physicalIdentity)
		assertEquals(ReaderLegacyResourceState.Registered, source.snapshotFrozenOwnership().single().state)
	}
	@Test fun unresolvedOwnersHitRealCapacityWithoutEviction() {
		val parent = FrameLayout(RuntimeEnvironment.getApplication()); val source = ReaderFramePhysicalOwnershipAdapter(capacity = 2)
		val first = assertNotNull(source.reserve(parent, View(parent.context)) { true })
		val second = assertNotNull(source.reserve(parent, View(parent.context)) { true })
		assertNull(source.reserve(parent, View(parent.context)) { true }); source.freezeForTransitionActivation(domain)
		assertEquals(setOf(first.token, second.token), source.snapshotFrozenOwnership().map { it.physicalIdentity.sourceLocalToken }.toSet())
	}
	@Test fun sourceFinalizationRequiresExactRetirementsAndSettledReturns() {
		val source = ReaderFramePhysicalOwnershipAdapter()
		val method = source.javaClass.declaredMethods.singleOrNull { it.name == "finalizeActivatedHandoff" }
		assertNotNull(method, "T398_CORE_MATERIAL_FINALIZATION_API")
		assertEquals(ReaderPortCommandResult.Accepted, source.freezeForTransitionActivation(domain))
		assertEquals(ReaderPortCommandResult.Accepted, method.invoke(source, domain, null, emptySet<ReaderLegacyPhysicalIdentity>()))
		assertFalse(source.admissionOpen)
	}
	@Test fun actualRegisteredVisualCallbackCarriesOpaqueExactPhysicalWitness() {
		val activity = buildPhysicalActivityWithFiniteDiagnosis()
		val webView = WebView(activity.get()); activity.get().setContentView(webView)
		val handoff = ReaderWebViewVisualHandoff(ReaderWebViewVisualHandoffHostAdapter({ webView }))
		val results = mutableListOf<ReaderPresentationWebViewVisualHandoffResult>()
		try {
			assertTrue(webView.isAttachedToWindow)
			handoff.await(ReaderPresentationToken(41L), ReaderPresentationBinding("synthetic-frame", 1L, 1L, 1L), results::add)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
			val ready = assertIs<ReaderPresentationWebViewVisualHandoffResult.Ready>(results.single())
			val field = ready.javaClass.declaredFields.singleOrNull { it.name == "physicalWitness" }
			assertNotNull(field, "T398_CORE_ACTUAL_VISUAL_WITNESS_API"); field.isAccessible = true
			assertNotNull(field.get(ready), "T398_CORE_ACTUAL_VISUAL_WITNESS_VALUE")
		} finally { handoff.close(); activity.pause().stop().destroy() }
	}
	@Test fun task398ComposedSubownersContributeOnceAndExactDrainRestoreFinalize() {
		val type = try { Class.forName("paige.navic.ui.screens.reader.ReaderFrameOrHandoffPhysicalSource") }
			catch (_: ClassNotFoundException) { null }
		assertNotNull(type, "T398_CORE_COMPOSED_SOURCE_PROTOCOL")
		val allocator = ReaderLegacySourceLocalTokenAllocator()
		val parent = FrameLayout(RuntimeEnvironment.getApplication()); val wrapper = View(parent.context)
		val material = ReaderFramePhysicalOwnershipAdapter(tokenAllocator = allocator)
		val lease = assertNotNull(material.reserve(parent, wrapper) { true }); assertTrue(material.mount(lease))
		var requests = 0; var cancellations = 0
		val frames = object : ReaderNativePagePresentedFrameSource {
			override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long = (++requests).toLong()
			override fun cancelPresentedFrameRequest(requestId: Long): Boolean { cancellations++; return true }
		}
		val binding = ReaderPresentationBinding("synthetic-composed", 1L, 1L, 1L,
			preparationGeneration = 3L, rasterGeneration = 4L, textureGeneration = 5L)
		val candidate = ReaderNativePagePresentationCandidate(binding, null, 0, 800, 600,
			paige.navic.reader.ReaderPagePreparationFacts(paige.navic.reader.ReaderPagePreparationPhase.Ready, generation = 3L))
		val publisher = ReaderNativePagePresentationPublisher(frames, { candidate }, tokenAllocator = allocator) { null }
		val host = object : ReaderPresentationCommitHost {
			override val isAttachedToWindow = true
			override val currentPresentationBinding = binding
			override val currentShellCoverGeneration: Long? = null
			override val shellCoverSelected = false
			override val measuredViewportWidth = 800
			override val measuredViewportHeight = 600
			override fun prepareOpaqueShellCover(coverGeneration: Long) = Unit
			override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun registerShellCoverDrawListener(onDraw: () -> Unit) = ReaderPresentationDrawRegistration { }
			override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
		}
		val bridge = ReaderPresentationHostBridge(host, tokenAllocator = allocator) { null }
		val source = type.declaredConstructors.single().apply { isAccessible = true }.newInstance(material, publisher, bridge)
		fun invoke(name: String, vararg arguments: Any?): Any? = type.declaredMethods.single { it.name.startsWith(name) }
			.apply { isAccessible = true }.invoke(source, *arguments)
		@Suppress("UNCHECKED_CAST") fun rows() = invoke("snapshotFrozenOwnership") as List<ReaderFrozenLegacyResource>
		try {
			publisher.update(); assertEquals(1, requests)
			assertEquals(ReaderPortCommandResult.Accepted, invoke("freezeForTransitionActivation", domain))
			val initial = rows(); assertEquals(2, initial.size, "T398_CORE_COMPOSED_ONE_CONTRIBUTION_EACH")
			assertEquals(2, initial.map { it.physicalIdentity }.toSet().size, "T398_CORE_COMPOSED_SHARED_ALLOCATOR_DISTINCT")
			assertEquals(setOf(ReaderTransitionResourceKind.FrameHandoff, ReaderTransitionResourceKind.CallbackRegistration), initial.map { it.kind }.toSet())
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>(); val callback: (ReaderLegacyPhysicalIdentity) -> Unit = confirmed::add
			assertIs<ReaderPortCommandResult.Rejected>(invoke("drainFrozenOwnership", initial.first().physicalIdentity.copy(sourceLocalToken = ReaderLegacySourceLocalOpaqueToken(999L)), callback))
			for (row in initial) assertEquals(ReaderPortCommandResult.Accepted, invoke("drainFrozenOwnership", row.physicalIdentity, callback))
			assertEquals(initial.map { it.physicalIdentity }.toSet(), confirmed.toSet()); assertEquals(1, cancellations); assertNull(wrapper.parent)
			assertEquals(ReaderPortCommandResult.Accepted, invoke("restoreAfterTransitionActivation", domain))
			assertSame(parent, wrapper.parent); assertEquals(2, requests)
			assertEquals(ReaderPortCommandResult.Accepted, invoke("freezeForTransitionActivation", domain))
			assertEquals(initial.map { it.physicalIdentity }.toSet(), rows().map { it.physicalIdentity }.toSet())
			confirmed.clear()
			for (row in rows()) assertEquals(ReaderPortCommandResult.Accepted, invoke("drainFrozenOwnership", row.physicalIdentity, callback))
			assertIs<ReaderPortCommandResult.Rejected>(invoke("finalizeActivatedHandoff", domain.freezeToken.value, null, emptySet<ReaderLegacyPhysicalIdentity>()))
			assertEquals(ReaderPortCommandResult.Accepted, invoke("finalizeActivatedHandoff", domain.freezeToken.value, null, confirmed.toSet()))
			assertIs<ReaderPortCommandResult.Rejected>(invoke("restoreAfterTransitionActivation", domain)); assertNull(wrapper.parent)
		} finally { publisher.dispose(); bridge.dispose() }
	}
	@Test fun actualReceiptDispatcherReentrantUpdatePromotesOnlyAfterOriginalReceiptReturns() {
		verifyMaterialReceiptReturn(cancelBeforeReturn = false)
	}
	@Test fun reentrantCancellationDuringReceiptReturnCannotPromoteOrResurrectOldProof() {
		verifyMaterialReceiptReturn(cancelBeforeReturn = true)
	}
	private fun verifyMaterialReceiptReturn(cancelBeforeReturn: Boolean) {
		val activity = buildPhysicalActivityWithFiniteDiagnosis()
		val parent = FrameLayout(activity.get()); activity.get().setContentView(parent)
		val wrapper = FrameLayout(activity.get()); val engine = WebView(activity.get()); wrapper.addView(engine)
		val source = ReaderFramePhysicalOwnershipAdapter()
		val lease = assertNotNull(source.reserve(parent, wrapper) { true }); assertTrue(source.mount(lease))
		val materialToken = lease.token
		val fixture = BridgeFixture()
		var common = paige.navic.reader.ReaderController(paige.navic.reader.ReaderControllerState(
			readerSessionGeneration = 1L, presentation = fixture.nativeState))
		val predecessor = common.state.presentationDecision.frameOwner
		val reporter = ReaderPresentationBindingReporter().also {
			it.reset(1L, common.presentationVersion, common.state.presentation, false)
			assertTrue(it.bindPublication(fixture.binding))
		}
		val lifecycle = ReaderPresentationLifecycleDelivery().also {
			it.reset(common.presentationVersion, null, paige.navic.reader.ReaderPresentationLifecycleState.Foreground)
			assertTrue(it.bindPublication(fixture.binding.publicationIdentity))
		}
		lateinit var bridge: ReaderPresentationHostBridge
		val dispatcher = ReaderPresentationReceiptDispatcher(reporter, lifecycle, applyHostEffect = { effect ->
			if (effect.event is paige.navic.reader.ReaderPresentationEvent.LiveEngineExposureCommitted) {
				assertEquals(ReaderLegacyResourceState.Registered, lease.state)
				assertNull(lease.owner); assertNull(lease.imported)
			}
			bridge.update(effect.decision)
		})
		val host = object : ReaderPresentationCommitHost {
			override val liveMaterialWitnessRequired = true
			override fun captureLiveFrameInstallation() = source.captureLiveInstallation(lease, engine, { true }, { wrapper.isShown && engine.isShown })
			override fun confirmLiveFramePresentation(installation: ReaderFramePhysicalOwnershipAdapter.LiveInstallation,
				witness: ReaderLiveEnginePhysicalWitness, owner: paige.navic.reader.ReaderPresentationFrameOwner.LiveEngine): Boolean {
				assertTrue(witness.matches(engine))
				return source.acceptLivePresentation(installation, witness, owner).also {
					assertTrue(it); assertEquals(ReaderLegacyResourceState.Registered, lease.state); assertNull(lease.owner)
				}
			}
			override val isAttachedToWindow get() = wrapper.isAttachedToWindow
			override val currentPresentationBinding get() = fixture.binding
			override val currentShellCoverGeneration: Long? = null
			override val shellCoverSelected = false
			override val measuredViewportWidth = 800
			override val measuredViewportHeight = 600
			override fun prepareOpaqueShellCover(coverGeneration: Long) = Unit
			override fun cancelOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun completeOpaqueShellCoverPreparation(coverGeneration: Long) = Unit
			override fun registerShellCoverDrawListener(onDraw: () -> Unit) = ReaderPresentationDrawRegistration { }
			override fun postShellCoverAnimationFrame(onFrame: () -> Unit) = Unit
		}
		val handoff = ReaderWebViewVisualHandoff(ReaderWebViewVisualHandoffHostAdapter({ engine }))
		val committedEvents = mutableListOf<paige.navic.reader.ReaderPresentationEvent>()
		bridge = ReaderPresentationHostBridge(host, handoff, { true }, transitionTimeoutScheduler = HostBridgeDeadlineScheduler(),
			transitionNowMillis = { 0L }) { event ->
			if (cancelBeforeReturn && event is paige.navic.reader.ReaderPresentationEvent.LiveEngineExposureCommitted) {
				committedEvents += event
				// A genuine old-controller receipt returning after cancellation is not new authority.
				val stale = common.onPresentationEvent(event).presentationReceipt
				common = common.onPresentationEvent(paige.navic.reader.ReaderPresentationEvent.Cancel).controller
				bridge.update(common.state.presentationDecision)
				stale
			} else dispatcher.dispatch(event) { dispatched ->
				if (dispatched is paige.navic.reader.ReaderPresentationEvent.LiveEngineExposureCommitted) committedEvents += dispatched
				common.onPresentationEvent(dispatched).also { common = it.controller }.presentationReceipt
			}
		}
		try {
			// The immutable native baseline has no pending handoff. Admit a real request
			// through the same receipt dispatcher before waiting for framework visual work.
			dispatcher.dispatch(paige.navic.reader.ReaderPresentationEvent.WebViewHandoffRequested(
				paige.navic.reader.ReaderLiveEngineHandoffDirection.NativeToLiveEngine
			)) { event -> common.onPresentationEvent(event).also { common = it.controller }.presentationReceipt }
			bridge.update(common.state.presentationDecision)
			org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(48))
			assertEquals(1, committedEvents.size); assertEquals(materialToken, lease.token)
			if (cancelBeforeReturn) {
				bridge.update(common.state.presentationDecision); bridge.update(common.state.presentationDecision)
				assertEquals(predecessor, common.state.presentationDecision.frameOwner)
				assertEquals(ReaderLegacyResourceState.Registered, lease.state); assertNull(lease.owner); assertNull(lease.imported)
				assertEquals(1, committedEvents.size)
			} else {
				assertIs<paige.navic.reader.ReaderPresentationAuthority.LiveEngineExposed>(common.state.presentation.authority)
				assertEquals(ReaderLegacyResourceState.Visible, lease.state)
				assertEquals(common.state.presentationDecision.frameOwner, lease.owner)
				assertTrue(reporter.matchesAuthoritativePresentationVersion(common.presentationVersion))
			}
		} finally { bridge.dispose(); activity.pause().stop().destroy() }
	}
	private fun buildPhysicalActivityWithFiniteDiagnosis() = try {
		Robolectric.buildActivity(Activity::class.java).setup()
	} catch (failure: Throwable) {
		// Project only source-known setup/cause types; never retain the raw failure or its payload.
		val types = mutableListOf<String>()
		var current: Throwable? = failure
		repeat(8) {
			val cause = current ?: return@repeat
			types += when (cause) {
				is android.view.InflateException -> "INFLATE"
				is java.lang.reflect.InvocationTargetException -> "INVOCATION_TARGET"
				is UninitializedPropertyAccessException -> "UNINITIALIZED_PROPERTY"
				is NullPointerException -> "NULL_POINTER"
				is ClassCastException -> "CLASS_CAST"
				is android.content.res.Resources.NotFoundException -> "RESOURCE_NOT_FOUND"
				is IllegalStateException -> "ILLEGAL_STATE"
				is IllegalArgumentException -> "ILLEGAL_ARGUMENT"
				is LinkageError -> "LINKAGE"
				else -> "OTHER"
			}
			current = cause.cause
		}
		throw AssertionError("T398_FIXTURE_ACTIVITY_SETUP:" + types.joinToString(","))
	}
	private fun ReaderFrozenLegacyResource.ownerOrNull() = visibleOwner
}

@Implements(ViewGroup::class)
internal open class Task398MaterialAddShadow : Task398ExactMountRemovalShadow() {
	@RealObject private lateinit var actualAdd: ViewGroup
	@Implements(FrameLayout::class) class FrameLayoutShadow : Task398MaterialAddShadow()
	@Implementation fun addView(child: View, params: ViewGroup.LayoutParams) {
		val targeted = actualAdd === targetAdd
		if (targeted) addEntries++
		fun fail() { val exact = failureAdd ?: return; failureAdd = null; beforeAddFailure?.invoke(); throw exact }
		if (targeted && !afterOriginal) fail()
		Shadow.directlyOn<Any?, ViewGroup>(actualAdd, ViewGroup::class.java, "addView",
			ClassParameter.from(View::class.java, child), ClassParameter.from(ViewGroup.LayoutParams::class.java, params))
		if (targeted) completedAdds++
		if (targeted && afterOriginal) fail()
	}
	companion object {
		var targetAdd: ViewGroup? = null
		var failureAdd: Throwable? = null
		var afterOriginal = false
		var beforeAddFailure: (() -> Unit)? = null
		var addEntries = 0
		var completedAdds = 0
		fun clearAdd() { targetAdd = null; failureAdd = null; afterOriginal = false; beforeAddFailure = null; addEntries = 0; completedAdds = 0 }
	}
}

@Implements(WebView::class)
internal class Task398MaterialVisualStateShadow : org.robolectric.shadows.ShadowWebView() {
	@RealObject private lateinit var actual: WebView
	@Implementation fun postVisualStateCallback(requestId: Long, callback: WebView.VisualStateCallback) {
		// Deliver only the callback registered through the genuine framework API, on its real scheduler.
		actual.post { callback.onComplete(requestId) }
	}
}
