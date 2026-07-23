package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageAdjacentPrefetchPublicationAllowance
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageMaximumForegroundPublicationEntries
import paige.navic.reader.ReaderPageMaximumPublicationCallbacks
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.readerPageRasterStorageRoot
import paige.navic.util.core.Logger
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val ReaderPageTurnBundleSourceTag = "ReaderPageTurnBundleSource"
private const val MaxCachedSnapshots = 5

internal fun interface ReaderPageRasterDescriptorPort {
	fun request(
		webView: WebView,
		pageIndex: Int,
		onDescriptor: (ReaderPageRasterDescriptor?) -> Unit
	)
}

internal interface ReaderPageRasterHydrationStorePort {
	suspend fun readCopy(key: ReaderPageRasterKey): ReaderPageRaster<Bitmap>?
	suspend fun remove(key: ReaderPageRasterKey): Boolean
}

internal fun interface ReaderPageRasterHydrationRequest {
	fun cancel()
}

internal data class ReaderPageRasterHydrationOwnerCounts(
	val descriptorRequests: Int,
	val descriptorRecipients: Int,
	val readWorkers: Int,
	val readRecipients: Int
)

private class ReaderPageWebViewRasterDescriptorPort : ReaderPageRasterDescriptorPort {
	override fun request(
		webView: WebView,
		pageIndex: Int,
		onDescriptor: (ReaderPageRasterDescriptor?) -> Unit
	) {
		webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterDescriptor?.($pageIndex) ?? null)"
		) { encoded -> onDescriptor(readerPageRasterDescriptor(encoded)) }
	}
}

private data class ReaderPageRasterHydrationRecipient(
	val token: Long,
	val publicationFence: () -> Boolean,
	val callback: (ReaderPageSlideSnapshot?) -> Unit
)

private data class ReaderPageRasterDescriptorIdentity(
	val generation: Long,
	val quality: ReaderPageBitmapQuality,
	val pageIndex: Int,
	val kind: ReaderPageTurnTransitionKind,
	val surfaceRectInWindow: Rect
)

private class ReaderPageRasterDescriptorRequest(
	val token: Long,
	val identity: ReaderPageRasterDescriptorIdentity,
	val webView: WeakReference<WebView>,
	val recipients: MutableMap<Long, ReaderPageRasterHydrationRecipient>
)

private data class ReaderPageRasterHydrationIdentity(
	val rasterIdentity: String,
	val kind: ReaderPageTurnTransitionKind,
	val surfaceRectInWindow: Rect
)

private class InFlightRasterHydration(
	val token: Long,
	val identity: ReaderPageRasterHydrationIdentity,
	val generation: Long,
	val quality: ReaderPageBitmapQuality,
	val key: ReaderPageRasterKey,
	val kind: ReaderPageTurnTransitionKind,
	val surfaceRectInWindow: Rect,
	val webView: WeakReference<WebView>,
	val recipients: MutableMap<Long, ReaderPageRasterHydrationRecipient>,
	var job: Job? = null
)

internal data class ReaderPageRasterPublicationValue<T : Any>(
	val key: ReaderPageRasterKey,
	val generation: ReaderPageRasterGeneration<T>
)

internal data class ReaderPageTurnBundleOwnershipMetrics(
	val rasterCache: ReaderPageRasterCacheMetrics,
	val stagedPublications: Int,
	val stagedPublicationLimit: Int,
	val pendingPublicationCallbacks: Int,
	val pendingPublicationCallbackLimit: Int
)

internal class ReaderPageTurnBundleSource(
	private val bitmapSource: ReaderPageTurnBitmapSource = ReaderPageTurnBitmapSource(),
	private val mainHandler: Handler = Handler(Looper.getMainLooper()),
	private val descriptorPort: ReaderPageRasterDescriptorPort =
		ReaderPageWebViewRasterDescriptorPort(),
	private val hydrationStorePort: ReaderPageRasterHydrationStorePort? = null,
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null,
	private val onOwnershipMutated: () -> Unit = {}
) {
	private var activeGeneration = 0L
	private var bitmapQuality = ReaderPageBitmapQuality.Balanced
	private val rasterJob = SupervisorJob()
	private val rasterScope = CoroutineScope(rasterJob + Dispatchers.Main.immediate)
	private val teardownJob = SupervisorJob()
	private val teardownScope = CoroutineScope(teardownJob + Dispatchers.Default)
	private val closeFenceLock = Any()
	private val rasterInitializationMutex = Mutex()
	private val rasterPersistenceJobLock = Any()
	private val rasterPersistenceJobs = linkedSetOf<Job>()
	private val pendingDescriptorOwners = ReaderPagePendingCallbackOwners<ReaderPageSlideSnapshot>(
		retain = ReaderPageSlideSnapshot::retain,
		release = ReaderPageSlideSnapshot::release
	)
	private val visualStateRequestId = AtomicLong()
	private val snapshotCache = LinkedHashMap<ReaderPageSlideSnapshotKey, ReaderPageSlideSnapshot>(0, 0.75f, true)
	private val descriptorRequests =
		mutableMapOf<Long, ReaderPageRasterDescriptorRequest>()
	private val descriptorRequestTokens =
		mutableMapOf<ReaderPageRasterDescriptorIdentity, Long>()
	private val inFlightRasterHydrations =
		mutableMapOf<ReaderPageRasterHydrationIdentity, InFlightRasterHydration>()
	private val hydrationScheduler = ReaderPageRasterHydrationScheduler(
		scope = rasterScope,
		maxConcurrentWorkers = 2
	)
	private var nextHydrationToken = 0L
	private val publicationScheduler = ReaderPageRasterPublicationScheduler(
		scope = rasterScope,
		maxConcurrentWorkers = 1
	)
	private val publicationLedger =
		ReaderPageRasterPublicationLedger<
			ReaderPageRasterPublicationValue<Bitmap>
		>(
			currentEpochEntryLimit =
				ReaderPageMaximumForegroundPublicationEntries +
					ReaderPageAdjacentPrefetchPublicationAllowance,
			persistenceWorkerLimit = publicationScheduler.maxConcurrentWorkers,
			callbackLimit = ReaderPageMaximumPublicationCallbacks,
			onOwnershipMutated = onOwnershipMutated,
			release = { value ->
				ReaderAndroidPageRasterCodec.release(value.generation.value)
			}
		)
	private val rasterPersistenceDiagnostics = linkedSetOf<String>()
	private var protectedSnapshotPageIndices = emptySet<Int>()
	private var rasterCache: ReaderPageRasterCache<Bitmap>? = null
	private var persistentStore: ReaderPageRasterCacheStore<Bitmap>? = null
	private var rasterScheduler: ReaderPageRasterScheduler<Bitmap>? = null
	private var activeWebView = WeakReference<WebView>(null)
	private var closed = false
	private var closeInvalidationFailure: Throwable? = null
	private var disposedRasterCacheMetrics: ReaderPageRasterCacheMetrics? = null
	private val teardown = ReaderPageTurnBundleTeardown(
		scope = teardownScope,
		preCloseFailure = { closeInvalidationFailure },
		closePublicationWorkers = {
			publicationScheduler.closeAndJoin()
		},
		publicationEntryCount = publicationLedger::entryCount,
		publicationDispatchFailure = publicationLedger::dispatchFailure,
		closeRasterGenerationWorkers = {
			rasterScheduler?.closeAndJoin()
			val persistenceJobs = synchronized(rasterPersistenceJobLock) {
				rasterPersistenceJobs.toList()
			}
			persistenceJobs.forEach { job -> job.join() }
			rasterScheduler?.closeAndJoin()
			check(synchronized(rasterPersistenceJobLock) {
				rasterPersistenceJobs.isEmpty()
			}) { "Raster persistence initialization workers did not drain" }
			check(pendingDescriptorOwners.pendingCount() == 0) {
				"Raster descriptor callbacks retained snapshot owners"
			}
		},
		closeRasterHydrationWorkers = {
			hydrationScheduler.closeAndJoin()
		},
		closePersistentStore = {
			persistentStore?.close()
		},
		closeRasterCache = {
			rasterCache?.let { cache ->
				try {
					cache.close()
				} finally {
					disposedRasterCacheMetrics = cache.metrics()
				}
			}
		},
		clearReferences = {
			persistentStore = null
			rasterCache = null
			activeWebView.clear()
		},
		onFinished = {
			teardownJob.complete()
		}
	)
	val isAvailable: Boolean
		get() = bitmapSource.isAvailable

	fun setPublicationCapacityAvailableListener(listener: () -> Unit) {
		publicationLedger.setCapacityAvailableListener(listener)
	}

	fun clearPublicationCapacityAvailableListener(listener: () -> Unit) {
		publicationLedger.clearCapacityAvailableListener(listener)
	}

	fun rasterCacheMetrics(): ReaderPageRasterCacheMetrics =
		rasterCache?.metrics()
			?: disposedRasterCacheMetrics
			?: ReaderPageRasterCacheMetrics(
				diskEntries = 0,
				diskBytes = 0L,
				diskByteLimit = 0L,
				decodedEntries = 0,
				uniqueDecodedBitmaps = 0,
				uniqueDecodedBitmapLimit = 0,
				pendingDecodedReleases = 0,
				activeEncodePins = 0,
				encodePinnedIdentities = 0
			)

	fun ownershipMetrics(): ReaderPageTurnBundleOwnershipMetrics =
		ReaderPageTurnBundleOwnershipMetrics(
			rasterCache = rasterCacheMetrics(),
			stagedPublications = publicationLedger.entryCount(),
			stagedPublicationLimit = publicationLedger.entryLimit,
			pendingPublicationCallbacks = publicationLedger.callbackCount(),
			pendingPublicationCallbackLimit = publicationLedger.callbackLimit
		)

	fun updateBitmapQuality(quality: ReaderPageBitmapQuality): Boolean {
		if (bitmapQuality == quality) return false
		bitmapQuality = quality
		bitmapSource.updateBitmapQuality(quality)
		invalidate("bitmap-quality-${quality.persistedValue}")
		return true
	}

	fun currentGeneration(): Long = activeGeneration

	fun hydrationOwnerCounts(): ReaderPageRasterHydrationOwnerCounts =
		ReaderPageRasterHydrationOwnerCounts(
			descriptorRequests = descriptorRequests.size,
			descriptorRecipients = descriptorRequests.values.sumOf { it.recipients.size },
			readWorkers = hydrationScheduler.activeWorkerCount,
			readRecipients = inFlightRasterHydrations.values.sumOf { it.recipients.size }
		)

	fun protectDecodedWindow(centerPageIndex: Int, step: Int, pageCount: Int) {
		protectDecodedPageIndices(
			readerPageSlideSnapshotWindow(
				centerPageIndex = centerPageIndex,
				step = step,
				pageCount = pageCount
			).toSet()
		)
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Decoded page window protected center=$centerPageIndex step=$step " +
				"rasters=${protectedSnapshotPageIndices.size} leaves=${protectedSnapshotPageIndices.size * step} " +
				"pages=${protectedSnapshotPageIndices.sorted()} generation=$activeGeneration"
		)
	}

	fun protectDecodedPageIndices(pageIndices: Set<Int>) {
		protectedSnapshotPageIndices = pageIndices.filterTo(linkedSetOf()) { it >= 0 }
		trimSnapshotCacheToCapacity()
		rasterCache?.protectDecodedPageIndices(protectedSnapshotPageIndices)
	}

	fun hasSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): Boolean = cachedSnapshot(pageIndex, kind) != null

	fun retainedReferenceSnapshot(
		preferredPageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = (
		cachedSnapshot(preferredPageIndex, kind)
			?: snapshotCache.entries.lastOrNull { (key, _) -> key.kind == kind }?.value
	).also { snapshot -> snapshot?.retain() }

	fun trimMemory(reason: String) {
		val removedSnapshots = snapshotCache.entries
			.filter { (key, _) -> key.visualPageIndex !in protectedSnapshotPageIndices }
			.map { it.key to it.value }
		removedSnapshots.forEach { (key, snapshot) ->
			snapshotCache.remove(key)
			snapshot.releaseCacheOwnership()
		}
		val removedDecoded = rasterCache?.trimDecodedToProtectedWindow() ?: 0
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Decoded working sets trimmed reason=$reason snapshots=${removedSnapshots.size} " +
				"rasters=$removedDecoded protected=${protectedSnapshotPageIndices.sorted()} " +
				"generation=$activeGeneration"
		)
	}

	fun retainedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = cachedSnapshot(pageIndex, kind)?.also { snapshot -> snapshot.retain() }

	fun cachedSnapshotPageIndices(kind: ReaderPageTurnTransitionKind): List<Int> =
		snapshotCache.keys
			.filter { key -> key.kind == kind }
			.map { key -> key.visualPageIndex }
			.distinct()
			.sorted()

	fun captureCurrentSurface(
		webView: WebView,
		generation: Long,
		onCaptured: (ReaderPageTurnCaptureResult?) -> Unit
	) {
		activeWebView = WeakReference(webView)
		bitmapSource.captureSurface(webView) { result ->
			if (generation != activeGeneration) {
				result?.bitmap?.takeUnless { it.isRecycled }?.recycle()
				onCaptured(null)
			} else {
				onCaptured(result)
			}
		}
	}

	suspend fun resolveSnapshot(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		publicationFence: () -> Boolean
	): ReaderPageSlideSnapshot? = withContext(Dispatchers.Main.immediate) {
		if (!runCatching(publicationFence).getOrDefault(false)) return@withContext null
		retainedSnapshot(pageIndex, kind)?.let { retained ->
			return@withContext suspendCancellableCoroutine { continuation ->
				continuation.resume(
					retained,
					onCancellation = { _, undelivered, _ -> undelivered.release() }
				)
			}
		}
		if (!webView.isAttachedToWindow) return@withContext null
		suspendCancellableCoroutine { continuation ->
			val request = hydrateSnapshot(
				webView = webView,
				pageIndex = pageIndex,
				kind = kind,
				reference = reference,
				publicationFence = publicationFence
			) { hydrated ->
				if (continuation.isActive) {
					continuation.resume(
						hydrated,
						onCancellation = { _, undelivered, _ -> undelivered?.release() }
					)
				} else {
					hydrated?.release()
				}
			}
			continuation.invokeOnCancellation { request.cancel() }
		}
	}

	fun hydrateSnapshot(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		publicationFence: () -> Boolean = { true },
		onHydrated: (ReaderPageSlideSnapshot?) -> Unit
	): ReaderPageRasterHydrationRequest {
		activeWebView = WeakReference(webView)
		retainedSnapshot(pageIndex, kind)?.let { retained ->
			deliverHydrationResult(onHydrated, retained)
			return ReaderPageRasterHydrationRequest { }
		}
		if (
			closed ||
			!webView.isAttachedToWindow ||
			!runCatching(publicationFence).getOrDefault(false)
		) {
			onHydrated(null)
			return ReaderPageRasterHydrationRequest { }
		}
		return registerPersistentHydration(
			webView = webView,
			pageIndex = pageIndex,
			kind = kind,
			surfaceRectInWindow = Rect(reference.surfaceRectInWindow),
			publicationFence = publicationFence,
			onHydrated = onHydrated
		)
	}

	private fun registerPersistentHydration(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		surfaceRectInWindow: Rect,
		publicationFence: () -> Boolean,
		onHydrated: (ReaderPageSlideSnapshot?) -> Unit
	): ReaderPageRasterHydrationRequest {
		val recipientToken = Math.incrementExact(nextHydrationToken)
		nextHydrationToken = recipientToken
		val recipient = ReaderPageRasterHydrationRecipient(
			token = recipientToken,
			publicationFence = publicationFence,
			callback = onHydrated
		)
		val identity = ReaderPageRasterDescriptorIdentity(
			generation = activeGeneration,
			quality = bitmapQuality,
			pageIndex = pageIndex,
			kind = kind,
			surfaceRectInWindow = Rect(surfaceRectInWindow)
		)
		val existing = descriptorRequestTokens[identity]
			?.let(descriptorRequests::get)
		if (existing != null) {
			existing.recipients[recipientToken] = recipient
		} else {
			val descriptorToken = Math.incrementExact(nextHydrationToken)
			nextHydrationToken = descriptorToken
			val request = ReaderPageRasterDescriptorRequest(
				token = descriptorToken,
				identity = identity,
				webView = WeakReference(webView),
				recipients = linkedMapOf(recipientToken to recipient)
			)
			descriptorRequests[descriptorToken] = request
			descriptorRequestTokens[identity] = descriptorToken
			try {
				descriptorPort.request(webView, pageIndex) { descriptor ->
					dispatchRasterDescriptor(descriptorToken, descriptor)
				}
			} catch (_: Throwable) {
				failDescriptorRequest(descriptorToken)
			}
		}
		return ReaderPageRasterHydrationRequest {
			dispatchToMain { cancelHydrationRecipient(recipientToken) }
		}
	}

	private fun dispatchRasterDescriptor(
		requestToken: Long,
		descriptor: ReaderPageRasterDescriptor?
	) = dispatchToMain {
		val request = descriptorRequests.remove(requestToken)
			?.takeIf { candidate -> candidate.token == requestToken }
			?: return@dispatchToMain
		descriptorRequestTokens[request.identity]
			?.takeIf { token -> token == requestToken }
			?.let { descriptorRequestTokens.remove(request.identity) }
		val recipients = request.recipients.values.toList()
		val webView = request.webView.get()
		if (
			descriptor == null ||
			descriptor.visualPageOrdinal != request.identity.pageIndex ||
			closed ||
			request.identity.generation != activeGeneration ||
			request.identity.quality != bitmapQuality ||
			webView?.isAttachedToWindow != true
		) {
			recipients.forEach { recipient ->
				deliverHydrationResult(recipient.callback, null)
			}
			return@dispatchToMain
		}
		val currentRecipients = recipients.filter { recipient ->
			runCatching(recipient.publicationFence).getOrDefault(false)
		}
		(recipients - currentRecipients.toSet()).forEach { recipient ->
			deliverHydrationResult(recipient.callback, null)
		}
		if (currentRecipients.isEmpty()) return@dispatchToMain
		val key = descriptor.key(request.identity.quality)
		val hydrationIdentity = ReaderPageRasterHydrationIdentity(
			rasterIdentity = key.identity,
			kind = request.identity.kind,
			surfaceRectInWindow = Rect(request.identity.surfaceRectInWindow)
		)
		inFlightRasterHydrations[hydrationIdentity]
			?.takeIf { hydration ->
				hydration.generation == request.identity.generation &&
					hydration.quality == request.identity.quality
			}
			?.let { hydration ->
				currentRecipients.forEach { recipient ->
					hydration.recipients[recipient.token] = recipient
				}
				return@dispatchToMain
			}
		val hydrationToken = Math.incrementExact(nextHydrationToken)
		nextHydrationToken = hydrationToken
		val hydration = InFlightRasterHydration(
			token = hydrationToken,
			identity = hydrationIdentity,
			generation = request.identity.generation,
			quality = request.identity.quality,
			key = key,
			kind = request.identity.kind,
			surfaceRectInWindow = Rect(request.identity.surfaceRectInWindow),
			webView = WeakReference(webView),
			recipients = currentRecipients.associateByTo(linkedMapOf()) { it.token }
		)
		inFlightRasterHydrations[hydrationIdentity] = hydration
		val job = hydrationScheduler.schedule { runPersistentHydration(hydration) }
		if (job == null) {
			if (inFlightRasterHydrations[hydrationIdentity] === hydration) {
				inFlightRasterHydrations.remove(hydrationIdentity)
				hydration.recipients.values.forEach { recipient ->
					deliverHydrationResult(recipient.callback, null)
				}
			}
		} else if (inFlightRasterHydrations[hydrationIdentity] === hydration) {
			hydration.job = job
		} else {
			job.cancel()
		}
	}

	private fun failDescriptorRequest(requestToken: Long) = dispatchToMain {
		val request = descriptorRequests.remove(requestToken) ?: return@dispatchToMain
		descriptorRequestTokens[request.identity]
			?.takeIf { token -> token == requestToken }
			?.let { descriptorRequestTokens.remove(request.identity) }
		request.recipients.values.forEach { recipient ->
			deliverHydrationResult(recipient.callback, null)
		}
	}

	private suspend fun runPersistentHydration(
		hydration: InFlightRasterHydration
	) {
		var raster: ReaderPageRaster<Bitmap>? = null
		var rasterOwnershipTransferred = false
		try {
			if (!isHydrationCurrent(hydration)) return
			val webView = hydration.webView.get() ?: return
			raster = readPersistentRaster(webView, hydration.key)
			val value = raster ?: return
			if (value.key.identity != hydration.key.identity) return
			val bitmap = value.value
			val leafGeometry = readerPageRasterLeafGeometry(
				metadata = value.metadata,
				bitmapWidth = bitmap.width,
				bitmapHeight = bitmap.height
			)
			val kindMatches = when (hydration.kind) {
				ReaderPageTurnTransitionKind.PortraitSlide ->
					leafGeometry?.fullLeafRect != null
				ReaderPageTurnTransitionKind.LandscapeSpreadSlide ->
					leafGeometry?.leftLeafRect != null &&
						leafGeometry.rightLeafRect != null
			}
			if (!kindMatches) {
				runCatching { removePersistentRaster(hydration.key) }
				Logger.w(
					ReaderPageTurnBundleSourceTag,
					"Discarded incompatible page raster page=${hydration.key.visualPageOrdinal} " +
						"kind=${hydration.kind} key=${hydration.key.digest}"
				)
				return
			}
			withContext(Dispatchers.Main.immediate) {
				if (inFlightRasterHydrations[hydration.identity] !== hydration) {
					return@withContext
				}
				val sourceCurrent = !closed &&
					hydration.generation == activeGeneration &&
					hydration.quality == bitmapQuality &&
					hydration.webView.get()?.isAttachedToWindow == true
				inFlightRasterHydrations.remove(hydration.identity)
				val recipients = hydration.recipients.values.toList()
				val eligible = recipients.filter { recipient ->
					sourceCurrent &&
						runCatching(recipient.publicationFence).getOrDefault(false)
				}
				(recipients - eligible.toSet()).forEach { recipient ->
					deliverHydrationResult(recipient.callback, null)
				}
				if (eligible.isEmpty()) return@withContext
				val snapshot = ReaderPageSlideSnapshot(
					key = snapshotKey(
						hydration.key.visualPageOrdinal,
						hydration.kind,
						bitmap,
						hydration.surfaceRectInWindow
					),
					bitmap = bitmap,
					surfaceRectInWindow = Rect(hydration.surfaceRectInWindow),
					leafGeometry = checkNotNull(leafGeometry),
					reverseFaceColor = value.metadata.reverseFaceColor
				)
				val cached = putSnapshot(
					snapshot = snapshot,
					priority = ReaderPageRasterPriority.NextTransition,
					persist = false
				)
				rasterOwnershipTransferred = true
				eligible.forEach { cached.retain() }
				eligible.forEach { recipient ->
					deliverHydrationResult(recipient.callback, cached)
				}
			}
		} finally {
			if (!rasterOwnershipTransferred) {
				raster?.value?.let(ReaderAndroidPageRasterCodec::release)
			}
			withContext(NonCancellable + Dispatchers.Main.immediate) {
				if (inFlightRasterHydrations[hydration.identity] === hydration) {
					inFlightRasterHydrations.remove(hydration.identity)
					hydration.recipients.values.forEach { recipient ->
						deliverHydrationResult(recipient.callback, null)
					}
				}
			}
		}
	}

	private suspend fun readPersistentRaster(
		webView: WebView,
		key: ReaderPageRasterKey
	): ReaderPageRaster<Bitmap>? {
		val injectedStore = hydrationStorePort
		val productionStore = if (injectedStore == null) {
			val scheduler = rasterScheduler(webView)
			scheduler.activateProfile(key.profile)
			persistentStore
		} else {
			null
		}
		var result: ReaderPageRaster<Bitmap>? = null
		var readFailure: Throwable? = null
		try {
			withContext(NonCancellable + Dispatchers.IO) {
				try {
					result = if (injectedStore != null) {
						injectedStore.readCopy(key)
					} else {
						productionStore?.readCopy(key) { cached ->
							cached.copy(Bitmap.Config.ARGB_8888, false)
						}
					}
				} catch (failure: Throwable) {
					readFailure = failure
				}
			}
		} catch (_: CancellationException) {
			// The non-cancellable read already captured ownership or failure.
		} catch (failure: Throwable) {
			readFailure = failure
		}
		readFailure?.let { failure -> throw failure }
		return result
	}

	private suspend fun removePersistentRaster(key: ReaderPageRasterKey): Boolean {
		hydrationStorePort?.let { store -> return store.remove(key) }
		val store = persistentStore ?: return false
		return withContext(Dispatchers.IO) { store.remove(key) }
	}

	private fun isHydrationCurrent(hydration: InFlightRasterHydration): Boolean =
		!closed &&
			hydration.generation == activeGeneration &&
			hydration.quality == bitmapQuality &&
			hydration.webView.get()?.isAttachedToWindow == true &&
			inFlightRasterHydrations[hydration.identity] === hydration

	private fun cancelHydrationRecipient(recipientToken: Long) {
		descriptorRequests.values.firstOrNull { request ->
			recipientToken in request.recipients
		}?.let { request ->
			request.recipients.remove(recipientToken)
			if (request.recipients.isEmpty()) {
				descriptorRequests.remove(request.token)
				descriptorRequestTokens[request.identity]
					?.takeIf { token -> token == request.token }
					?.let { descriptorRequestTokens.remove(request.identity) }
			}
			return
		}
		inFlightRasterHydrations.values.firstOrNull { hydration ->
			recipientToken in hydration.recipients
		}?.let { hydration ->
			hydration.recipients.remove(recipientToken)
			if (hydration.recipients.isEmpty()) {
				inFlightRasterHydrations.remove(hydration.identity)
				hydration.job?.cancel()
			}
		}
	}

	private fun deliverHydrationResult(
		callback: (ReaderPageSlideSnapshot?) -> Unit,
		snapshot: ReaderPageSlideSnapshot?
	) {
		try {
			callback(snapshot)
		} catch (_: Throwable) {
			snapshot?.release()
		}
	}

	private fun dispatchToMain(action: () -> Unit) {
		if (Looper.myLooper() == Looper.getMainLooper()) action()
		else mainHandler.post(action)
	}

	fun capturePreparedRasterPage(
		webView: WebView,
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		itemToken: String,
		priority: ReaderPageRasterPriority,
		isStillCurrent: () -> Boolean = { true },
		onStagingStarted: (ReaderPageSlideSnapshot) -> Unit = {},
		onCaptureFailed: () -> Unit,
		onCaptured: (Boolean) -> Unit
	) {
		activeWebView = WeakReference(webView)
		val generation = activeGeneration
		if (closed || !webView.isAttachedToWindow || !isStillCurrent()) {
			onCaptureFailed()
			return
		}
		cachedSnapshot(pageIndex, kind)?.let { cached ->
			schedulePersistentSnapshot(cached, priority) { persisted ->
				if (isStillCurrent()) onCaptured(persisted)
			}
			return
		}
		val destinationKey = snapshotKey(
			pageIndex = pageIndex,
			kind = kind,
			bitmap = reference.bitmap,
			surfaceRectInWindow = reference.surfaceRectInWindow
		)
		reference.retain()
		capturePreparedPage(
			webView = webView,
			token = itemToken,
			generation = generation,
			reference = reference,
			destinationKey = destinationKey,
			isStillCurrent = isStillCurrent,
			onStagingStarted = { onStagingStarted(reference) }
		) { captured ->
			try {
				if (
					captured == null ||
					generation != activeGeneration ||
					closed ||
					!isStillCurrent()
				) {
					captured?.releaseCacheOwnership()
					onCaptureFailed()
					return@capturePreparedPage
				}
				putSnapshot(
					snapshot = captured,
					priority = priority,
					onPersisted = { persisted ->
						onCaptured(persisted)
					}
				)
			} finally {
				reference.release()
			}
		}
	}

	private fun capturePreparedPage(
		webView: WebView,
		token: String,
		generation: Long,
		reference: ReaderPageSlideSnapshot,
		destinationKey: ReaderPageSlideSnapshotKey,
		isStillCurrent: () -> Boolean,
		onStagingStarted: () -> Unit,
		onCaptured: (ReaderPageSlideSnapshot?) -> Unit
	) {
		if (!isStillCurrent()) {
			onCaptured(null)
			return
		}
		val captureStartedAt = SystemClock.uptimeMillis()
		val quotedToken = JSONObject.quote(token)
		onStagingStarted()
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.exposePageTurnPreviewFinal?.($quotedToken) === true"
		) { encoded ->
			if (
				generation != activeGeneration ||
				!isStillCurrent() ||
				!encoded.isJavascriptTrue()
			) {
				restoreLiveComposition(webView, token) { onCaptured(null) }
				return@evaluateJavascript
			}
			webView.postOnAnimation {
				if (!isStillCurrent()) {
					restoreLiveComposition(webView, token) { onCaptured(null) }
					return@postOnAnimation
				}
				capturePreparedSurface(webView, reference) { bitmap ->
					restoreLiveComposition(webView, token) {
						if (
							bitmap == null ||
							generation != activeGeneration ||
							!isStillCurrent()
						) {
							bitmap?.takeUnless { it.isRecycled }?.recycle()
							onCaptured(null)
							return@restoreLiveComposition
						}
						onCaptured(
							ReaderPageSlideSnapshot(
								key = destinationKey,
								bitmap = bitmap,
								surfaceRectInWindow = Rect(reference.surfaceRectInWindow),
								leafGeometry = reference.leafGeometry,
								reverseFaceColor = reference.reverseFaceColor,
								captureMillis = SystemClock.uptimeMillis() - captureStartedAt
							)
						)
					}
				}
			}
		}
	}

	fun cacheCurrentSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long = activeGeneration
	): ReaderPageSlideSnapshot? = cacheSnapshot(pageIndex, kind, current, generation)

	fun ensurePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		onPersisted: (Boolean) -> Unit
	) {
		schedulePersistentSnapshot(snapshot, priority, onPersisted)
	}

	private fun cacheSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		current: ReaderPageTurnCaptureResult,
		generation: Long
	): ReaderPageSlideSnapshot? {
		if (generation != activeGeneration) {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		val key = snapshotKey(pageIndex, kind, current.bitmap, current.sourceRectInWindow)
		val leafGeometry = current.geometry.leafGeometry(current.bitmap.width, current.bitmap.height) ?: run {
			current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return null
		}
		snapshotCache[key]?.let { cached ->
			snapshotCache[key]
			if (cached.bitmap !== current.bitmap) current.bitmap.takeUnless { it.isRecycled }?.recycle()
			return cached
		}
		return putSnapshot(
			ReaderPageSlideSnapshot(
				key = key,
				bitmap = current.bitmap,
				surfaceRectInWindow = Rect(current.sourceRectInWindow),
				leafGeometry = leafGeometry,
				reverseFaceColor = readerPageTurnOpaqueColor(current.geometry.reverseFaceColorArgb),
				captureMillis = current.elapsedMs
			),
			ReaderPageRasterPriority.Current
		)
	}

	private fun cachedSnapshot(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind
	): ReaderPageSlideSnapshot? = snapshotCache.entries
		.lastOrNull { (key, _) -> key.visualPageIndex == pageIndex && key.kind == kind }
		?.let { (key, value) ->
			snapshotCache[key]
			value
		}

	private fun putSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		persist: Boolean = true,
		onPersisted: (Boolean) -> Unit = {}
	): ReaderPageSlideSnapshot {
		snapshotCache[snapshot.key]?.let { cached ->
			snapshot.releaseCacheOwnership()
			if (persist) schedulePersistentSnapshot(cached, priority, onPersisted)
			return cached
		}
		snapshotCache[snapshot.key] = snapshot
		if (persist) schedulePersistentSnapshot(snapshot, priority, onPersisted)
		trimSnapshotCacheToCapacity()
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot cached key=${snapshot.key} entries=${snapshotCache.keys}"
		)
		return snapshot
	}

	private fun trimSnapshotCacheToCapacity() {
		while (snapshotCache.size > MaxCachedSnapshots) {
			val eviction = snapshotCache.entries.firstOrNull { (key, _) ->
				key.visualPageIndex !in protectedSnapshotPageIndices
			} ?: break
			snapshotCache.remove(eviction.key)
			eviction.value.releaseCacheOwnership()
		}
	}

	private fun schedulePersistentSnapshot(
		snapshot: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		onPersisted: (Boolean) -> Unit = {}
	) {
		val pageIndex = snapshot.key.visualPageIndex
		if (closed) {
			rasterPersistenceSkipped(
				pageIndex,
				"bundle-source-closed",
				activeGeneration
			)
			onPersisted(false)
			return
		}
		val webView = activeWebView.get()?.takeIf { it.isAttachedToWindow }
			?: run {
				rasterPersistenceSkipped(
					pageIndex,
					"webview-unavailable",
					activeGeneration
				)
				onPersisted(false)
				return
			}
		val generation = activeGeneration
		val descriptorOwner = pendingDescriptorOwners.acquire(snapshot) {
			onPersisted(false)
		} ?: run {
			rasterPersistenceSkipped(
				pageIndex,
				"bundle-source-closed",
				generation
			)
			onPersisted(false)
			return
		}
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?.pageTurnRasterDescriptor?.($pageIndex) ?? null)"
			) callback@{ encodedDescriptor ->
				val claimedOwner = pendingDescriptorOwners.claim(descriptorOwner)
					?: return@callback
				try {
				if (closed || generation != activeGeneration) {
					rasterPersistenceSkipped(
						pageIndex,
						"generation-changed",
						generation
					)
					onPersisted(false)
					return@callback
				}
				val descriptor = readerPageRasterDescriptor(encodedDescriptor)
					?: run {
						rasterPersistenceSkipped(
							pageIndex,
							"descriptor-unavailable",
							generation
						)
						onPersisted(false)
						return@callback
					}
				val key = descriptor.key(snapshot.key.bitmapQuality)
				val persistentBitmap = runCatching {
					snapshot.bitmap.copy(Bitmap.Config.ARGB_8888, false)
				}.getOrNull()
				if (persistentBitmap == null) {
					rasterPersistenceSkipped(
						pageIndex,
						"bitmap-copy-failed",
						generation
					)
					onPersisted(false)
					return@callback
				}
				val rasterGeneration = ReaderPageRasterGeneration(
					metadata = snapshot.toRasterMetadata(),
					value = persistentBitmap,
					captureMillis = snapshot.captureMillis.coerceAtLeast(0L)
				)
				val persistenceJob = rasterScope.launch {
					var publicationValueTransferred = false
					try {
						val scheduler = rasterScheduler(webView)
						if (closed || generation != activeGeneration) {
							rasterPersistenceSkipped(
								pageIndex,
								"generation-changed",
								generation
							)
							onPersisted(false)
							return@launch
						}
						if (priority == ReaderPageRasterPriority.Current) {
							scheduler.protectChapter(key.chapter)
						}
						scheduler.activateProfile(key.profile)
						val value = ReaderPageRasterPublicationValue(
							key = key,
							generation = rasterGeneration
						)
						val publicationEpoch = publicationLedger.currentEpoch()
						val publicationStartedAt = diagnostics?.now() ?: 0L
						val registration = publicationLedger.begin(
							digest = key.digest,
							value = value
						) { persisted ->
							diagnostics?.publication(
								digest = key.digest,
								rasterEpoch = publicationEpoch,
								result = when {
									persisted -> ReaderPagePublicationDiagnosticResult.Durable
									closed -> ReaderPagePublicationDiagnosticResult.Cancelled
									publicationEpoch != publicationLedger.currentEpoch() ->
										ReaderPagePublicationDiagnosticResult.Stale
									else -> ReaderPagePublicationDiagnosticResult.Failed
								},
								startedAtMs = publicationStartedAt
							)
							if (!persisted) {
								rasterPersistenceSkipped(
									pageIndex,
									"durable-publication-failed",
									generation
								)
							}
							onPersisted(persisted)
						}
						publicationValueTransferred = true
						when (registration) {
							is ReaderPageRasterPublicationRegistration.Started ->
								scheduleRasterPublication(registration.request)
							is ReaderPageRasterPublicationRegistration.Coalesced ->
								Unit
							is ReaderPageRasterPublicationRegistration.Rejected ->
								rasterPersistenceSkipped(
									pageIndex,
									"publication-${
										registration.reason.name.lowercase()
									}",
									generation
								)
						}
					} catch (failure: CancellationException) {
						if (!publicationValueTransferred) onPersisted(false)
						throw failure
					} catch (failure: Throwable) {
						publicationLedger.recordFailure(failure)
						rasterPersistenceSkipped(
							pageIndex,
							"publication-initialization-failed",
							generation
						)
						if (!publicationValueTransferred) onPersisted(false)
					} finally {
						if (!publicationValueTransferred) {
							ReaderAndroidPageRasterCodec.release(persistentBitmap)
						}
					}
				}
				trackRasterPersistenceJob(persistenceJob)
			} finally {
				pendingDescriptorOwners.complete(claimedOwner)
			}
		}
		} catch (failure: Throwable) {
			val claimedOwner = pendingDescriptorOwners.claim(descriptorOwner)
				?: return
			try {
				pendingDescriptorOwners.abandon(claimedOwner)
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
			try {
				rasterPersistenceSkipped(
					pageIndex,
					"descriptor-request-failed",
					generation
				)
			} catch (reportingFailure: Throwable) {
				if (reportingFailure !== failure) failure.addSuppressed(reportingFailure)
			}
			publicationLedger.recordFailure(failure)
		}
	}

	private fun scheduleRasterPublication(
		request: ReaderPageRasterPublicationRequest
	) {
		publicationScheduler.schedule(request) {
			val value = publicationLedger.acquireForPersistence(request)
				?: return@schedule
			var write = ReaderPageRasterWriteResult(
				persisted = false,
				ownership = ReaderPageRasterValueOwnership.Caller
			)
			val store = persistentStore
			var writeFailure: Throwable? = null
			try {
				try {
					withContext(NonCancellable + Dispatchers.IO) {
						try {
							write = if (store?.contains(value.key) == true) {
								ReaderPageRasterWriteResult(
									persisted = true,
									ownership =
										ReaderPageRasterValueOwnership.Caller
								)
							} else {
								store?.writePublication(
									key = value.key,
									metadata = value.generation.metadata,
									value = value.generation.value,
									commitFence =
										publicationLedger.commitFence(request)
								) ?: write
							}
						} catch (failure: Throwable) {
							writeFailure = failure
						}
					}
				} catch (_: CancellationException) {
					// The non-cancellable worker already captured its write result.
				} catch (failure: Throwable) {
					writeFailure = failure
				}
				writeFailure?.let(publicationLedger::recordFailure)
				check(
					write.ownership == ReaderPageRasterValueOwnership.Caller
				) {
					"Publication store adopted a ledger-owned value"
				}
			} finally {
				val accepted = publicationLedger.complete(
					request = request,
					persisted = write.persisted
				)
				if (!accepted) {
					write.receipt?.let { receipt ->
						var rollbackFailure: Throwable? = null
						try {
							withContext(NonCancellable + Dispatchers.IO) {
								try {
									store?.rollbackPublication(receipt)
								} catch (failure: Throwable) {
									rollbackFailure = failure
								}
							}
						} catch (_: CancellationException) {
							// Rollback already completed on the non-cancellable worker.
						} catch (failure: Throwable) {
							rollbackFailure = failure
						}
						rollbackFailure?.let(publicationLedger::recordFailure)
					}
				}
			}
		}
	}

	private fun rasterPersistenceSkipped(
		pageIndex: Int,
		reason: String,
		requestGeneration: Long
	) {
		val diagnosticKey = "$pageIndex:$reason"
		if (!rasterPersistenceDiagnostics.add(diagnosticKey)) return
		while (rasterPersistenceDiagnostics.size > 64) {
			rasterPersistenceDiagnostics.remove(rasterPersistenceDiagnostics.first())
		}
		Logger.w(
			ReaderPageTurnBundleSourceTag,
			"Page raster persistence skipped page=$pageIndex reason=$reason " +
				"requestGeneration=$requestGeneration activeGeneration=$activeGeneration"
		)
	}

	private fun trackRasterPersistenceJob(job: Job) {
		synchronized(rasterPersistenceJobLock) {
			rasterPersistenceJobs += job
		}
		job.invokeOnCompletion {
			synchronized(rasterPersistenceJobLock) {
				rasterPersistenceJobs -= job
			}
		}
	}

	private suspend fun rasterScheduler(webView: WebView): ReaderPageRasterScheduler<Bitmap> =
		rasterInitializationMutex.withLock {
			rasterScheduler?.let { return@withLock it }
			var cache: ReaderPageRasterCache<Bitmap>? = null
			var store: ReaderPageRasterCacheStore<Bitmap>? = null
			var scheduler: ReaderPageRasterScheduler<Bitmap>? = null
			try {
				requireRasterInitializationOpen()
				withContext(Dispatchers.IO) {
					ReaderPageRasterCache(
						root = readerPageRasterStorageRoot(webView.context.applicationContext),
						codec = ReaderAndroidPageRasterCodec,
						onDiagnostic = { diagnostic ->
							Logger.w(ReaderPageTurnBundleSourceTag, "Page raster cache $diagnostic")
						},
						onOwnershipMutated = onOwnershipMutated
					).also { created -> cache = created }
				}
				requireRasterInitializationOpen()
				val createdCache = checkNotNull(cache)
				val createdStore = ReaderPageRasterCacheStore(createdCache)
				store = createdStore
				val createdScheduler = ReaderPageRasterScheduler(
					scope = rasterScope,
					store = createdStore,
					generator = ReaderPageRasterGenerator { null },
					release = ReaderAndroidPageRasterCodec::release
				)
				scheduler = createdScheduler
				requireRasterInitializationOpen()
				createdCache.protectDecodedPageIndices(protectedSnapshotPageIndices)
				rasterCache = createdCache
				persistentStore = createdStore
				rasterScheduler = createdScheduler
					onOwnershipMutated()
				createdScheduler
			} catch (failure: Throwable) {
				closeUnpublishedRasterOwners(
					cache = cache,
					store = store,
					scheduler = scheduler,
					failure = failure
				)
				throw failure
			}
		}

	private fun requireRasterInitializationOpen() {
		val fenced = synchronized(closeFenceLock) { closed }
		if (fenced || !rasterJob.isActive) {
			throw CancellationException(
				"Raster persistence initialization is closed"
			)
		}
	}

	private suspend fun closeUnpublishedRasterOwners(
		cache: ReaderPageRasterCache<Bitmap>?,
		store: ReaderPageRasterCacheStore<Bitmap>?,
		scheduler: ReaderPageRasterScheduler<Bitmap>?,
		failure: Throwable
	) {
		withContext(NonCancellable) {
			try {
				scheduler?.closeAndJoin()
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
			try {
				store?.close()
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
			try {
				cache?.close()
			} catch (cleanupFailure: Throwable) {
				if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
			}
		}
	}

	private fun ReaderPageSlideSnapshot.toRasterMetadata(): ReaderPageRasterMetadata = ReaderPageRasterMetadata(
		surfaceLeft = 0,
		surfaceTop = 0,
		surfaceRight = bitmap.width,
		surfaceBottom = bitmap.height,
		fullLeafRect = leafGeometry.fullLeafRect?.toRasterRect(),
		leftLeafRect = leafGeometry.leftLeafRect?.toRasterRect(),
		gutterRect = leafGeometry.gutterRect?.toRasterRect(),
		rightLeafRect = leafGeometry.rightLeafRect?.toRasterRect(),
		reverseFaceColor = reverseFaceColor
	)

	private fun paige.navic.reader.ReaderPageTurnPixelRect.toRasterRect() = ReaderPageRasterRect(
		left = left,
		top = top,
		right = right,
		bottom = bottom
	)

	private fun snapshotKey(
		pageIndex: Int,
		kind: ReaderPageTurnTransitionKind,
		bitmap: Bitmap,
		surfaceRectInWindow: Rect
	): ReaderPageSlideSnapshotKey = ReaderPageSlideSnapshotKey(
		visualPageIndex = pageIndex,
		kind = kind,
		bitmapQuality = bitmapQuality,
		bitmapWidth = bitmap.width,
		bitmapHeight = bitmap.height,
		surfaceWidth = surfaceRectInWindow.width(),
		surfaceHeight = surfaceRectInWindow.height()
	)

	internal fun captureStagedSurface(
		webView: WebView,
		geometry: ReaderPageTurnCaptureGeometry,
		sourceRectInWindow: Rect,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (geometry.pages.isEmpty()) {
			onCaptured(null)
			return
		}
		captureCompositedSurface(
			webView = webView,
			sourceRectInWindow = sourceRectInWindow,
			backgroundColor = readerPageTurnOpaqueColor(geometry.reverseFaceColorArgb),
			onCaptured = onCaptured
		)
	}

	private fun capturePreparedSurface(
		webView: WebView,
		source: ReaderPageSlideSnapshot,
		onCaptured: (Bitmap?) -> Unit
	) = captureCompositedSurface(
		webView = webView,
		sourceRectInWindow = source.surfaceRectInWindow,
		backgroundColor = source.reverseFaceColor,
		onCaptured = onCaptured
	)

	private fun captureCompositedSurface(
		webView: WebView,
		sourceRectInWindow: Rect,
		backgroundColor: Int,
		onCaptured: (Bitmap?) -> Unit
	) {
		if (!webView.isAttachedToWindow || sourceRectInWindow.width() <= 0 || sourceRectInWindow.height() <= 0) {
			onCaptured(null)
			return
		}
		val draw = {
			val bitmap = runCatching {
				Bitmap.createBitmap(
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.width(), bitmapQuality),
					readerPageTurnAnimationBitmapDimension(sourceRectInWindow.height(), bitmapQuality),
					Bitmap.Config.ARGB_8888
				)
			}.getOrNull()
			if (bitmap == null) {
				onCaptured(null)
			} else {
				val location = IntArray(2)
				webView.getLocationInWindow(location)
				val canvas = Canvas(bitmap)
				canvas.drawColor(backgroundColor)
				canvas.scale(
					bitmap.width / sourceRectInWindow.width().toFloat(),
					bitmap.height / sourceRectInWindow.height().toFloat()
				)
				canvas.translate(
					-(sourceRectInWindow.left - location[0]).toFloat(),
					-(sourceRectInWindow.top - location[1]).toFloat()
				)
				webView.draw(canvas)
				onCaptured(bitmap)
			}
		}
		val awaitCompositedPreview = {
			if (!webView.isAttachedToWindow) {
				onCaptured(null)
			} else {
				webView.postVisualStateCallback(
					visualStateRequestId.incrementAndGet(),
					object : WebView.VisualStateCallback() {
						override fun onComplete(requestId: Long) {
							if (!webView.isAttachedToWindow) onCaptured(null)
							else webView.postOnAnimation(draw)
						}
					}
				)
			}
		}
		if (Looper.myLooper() == Looper.getMainLooper()) awaitCompositedPreview() else mainHandler.post(awaitCompositedPreview)
	}

	fun invalidatePage(pageIndex: Int, reason: String) {
		val removed = snapshotCache.entries
			.filter { (key, _) -> key.visualPageIndex == pageIndex }
			.map { it.key to it.value }
		removed.forEach { (key, snapshot) ->
			snapshotCache.remove(key)
			snapshot.releaseCacheOwnership()
		}
		Logger.i(
			ReaderPageTurnBundleSourceTag,
			"Page-turn snapshot page cleared page=$pageIndex reason=$reason removed=${removed.size} entries=${snapshotCache.keys}"
		)
	}

	fun invalidate(reason: String) {
		activeGeneration += 1
		try {
			pendingDescriptorOwners.cancelAll()
		} catch (failure: Throwable) {
			publicationLedger.recordFailure(failure)
		}
		publicationLedger.invalidate()
		publicationScheduler.cancelBeforeEpoch(publicationLedger.currentEpoch())
		protectedSnapshotPageIndices = emptySet()
		rasterCache?.protectDecodedPageIndices(emptySet())
		val descriptorRecipients = descriptorRequests.values
			.flatMap { request -> request.recipients.values }
		descriptorRequests.clear()
		descriptorRequestTokens.clear()
		val hydrations = inFlightRasterHydrations.values.toList()
		inFlightRasterHydrations.clear()
		hydrations.forEach { hydration -> hydration.job?.cancel() }
		(descriptorRecipients + hydrations.flatMap { hydration -> hydration.recipients.values })
			.forEach { recipient -> deliverHydrationResult(recipient.callback, null) }
		snapshotCache.values.distinctBy { System.identityHashCode(it) }.forEach { it.releaseCacheOwnership() }
		snapshotCache.clear()
		Logger.i(ReaderPageTurnBundleSourceTag, "Page-turn snapshot cache cleared reason=$reason")
	}

	fun fenceForClose() {
		synchronized(closeFenceLock) {
			if (closed) return
			closed = true
			rasterJob.cancel()
			try {
				pendingDescriptorOwners.close()
			} catch (failure: Throwable) {
				publicationLedger.recordFailure(failure)
			}
			try {
				invalidate("close")
			} catch (failure: Throwable) {
				closeInvalidationFailure = failure
			}
		}
	}

	fun close(): Deferred<Unit> {
		fenceForClose()
		return teardown.start()
	}

	suspend fun closeAndJoin() {
		close().await()
	}

	private fun restoreLiveComposition(
		webView: WebView,
		token: String,
		onRestored: () -> Unit = {}
	) {
		if (!webView.isAttachedToWindow) {
			onRestored()
			return
		}
		val quotedToken = JSONObject.quote(token)
		webView.evaluateJavascript(
			"window.NavicReaderBridge?.restorePageTurnLiveComposition?.($quotedToken)"
		) {
			if (!webView.isAttachedToWindow) {
				onRestored()
				return@evaluateJavascript
			}
			webView.postVisualStateCallback(
				visualStateRequestId.incrementAndGet(),
				object : WebView.VisualStateCallback() {
					override fun onComplete(requestId: Long) {
						if (!webView.isAttachedToWindow) onRestored()
						else webView.postOnAnimation(onRestored)
					}
				}
			)
		}
	}
}

internal fun readerPageTurnOpaqueColor(argb: Long?): Int {
	val color = argb?.toInt() ?: Color.rgb(234, 217, 174)
	return color or Color.BLACK
}

internal fun readerPageTurnAnimationBitmapDimension(
	physicalPixels: Int,
	quality: ReaderPageBitmapQuality
): Int = (physicalPixels * quality.scale).roundToInt().coerceAtLeast(1)

internal fun readerPageSlideSnapshotWindow(
	centerPageIndex: Int,
	step: Int,
	pageCount: Int
): List<Int> = listOf(
	centerPageIndex,
	centerPageIndex + step,
	centerPageIndex + (2 * step),
	centerPageIndex - step,
	centerPageIndex - (2 * step)
).filter { it in 0 until pageCount }.distinct()

private fun String?.isJavascriptTrue(): Boolean = runCatching {
	JSONTokener(orEmpty()).nextValue() as? Boolean == true
}.getOrDefault(false)
