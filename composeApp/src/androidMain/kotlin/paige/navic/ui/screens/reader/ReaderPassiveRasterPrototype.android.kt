package paige.navic.ui.screens.reader

private const val ReaderPassiveRasterMaximumSafeInteger = 9_007_199_254_740_991L

internal data class ReaderPassiveRasterGeometry(
	val viewportWidth: Int,
	val viewportHeight: Int,
	val captureLeft: Int,
	val captureTop: Int,
	val captureRight: Int,
	val captureBottom: Int
) {
	init {
		require(viewportWidth > 0)
		require(viewportHeight > 0)
		require(captureLeft >= 0)
		require(captureTop >= 0)
		require(captureRight > captureLeft)
		require(captureBottom > captureTop)
		require(captureRight <= viewportWidth)
		require(captureBottom <= viewportHeight)
	}

	val captureWidth: Int
		get() = captureRight - captureLeft
	val captureHeight: Int
		get() = captureBottom - captureTop
}

internal data class ReaderPassiveRasterCanonicalCommit(
	val captureEpoch: Long,
	val liveFoliateSessionId: String,
	val publicationSessionGeneration: Long,
	val destinationCommitToken: String,
	val rasterProfileKey: String,
	val paginationFingerprint: String,
	val layoutFingerprint: String,
	val decorationFingerprint: String,
	val viewportAndCaptureGeometry: ReaderPassiveRasterGeometry,
	val rasterGeneration: Long
) {
	init {
		requireSafeIdentity(captureEpoch)
		require(liveFoliateSessionId.isNotBlank())
		requireSafeIdentity(publicationSessionGeneration)
		require(destinationCommitToken.isNotBlank())
		require(rasterProfileKey.isNotBlank())
		require(paginationFingerprint.isNotBlank())
		require(layoutFingerprint.isNotBlank())
		require(decorationFingerprint.isNotBlank())
		requireSafeIdentity(rasterGeneration)
	}
}

internal class ReaderPassiveRasterLiveCommit private constructor(
	internal val issuerIdentity: Any,
	internal val canonicalCommit: ReaderPassiveRasterCanonicalCommit
) {
	internal companion object {
		fun create(
			issuerIdentity: Any,
			canonicalCommit: ReaderPassiveRasterCanonicalCommit
		) = ReaderPassiveRasterLiveCommit(issuerIdentity, canonicalCommit)
	}
}

internal data class ReaderPassiveRasterCaptureManifest(
	val manifestSequence: Long,
	val captureEpoch: Long,
	val liveFoliateSessionId: String,
	val publicationSessionGeneration: Long,
	val destinationCommitToken: String,
	val opaqueCaptureTarget: String,
	val visualPageOrdinal: Int,
	val rasterProfileKey: String,
	val paginationFingerprint: String,
	val layoutFingerprint: String,
	val decorationFingerprint: String,
	val viewportAndCaptureGeometry: ReaderPassiveRasterGeometry,
	val rasterGeneration: Long
)

internal class ReaderPassiveRasterManifestIssuer {
	private val issuerIdentity = Any()
	private var currentLiveCommit: ReaderPassiveRasterLiveCommit? = null
	private var manifestSequence = 0L

	fun replaceCanonicalCommit(
		commit: ReaderPassiveRasterCanonicalCommit
	): ReaderPassiveRasterLiveCommit = ReaderPassiveRasterLiveCommit.create(
		issuerIdentity = issuerIdentity,
		canonicalCommit = commit
	).also { currentLiveCommit = it }

	fun clearCanonicalCommit() {
		currentLiveCommit = null
	}

	fun issue(
		liveCommit: ReaderPassiveRasterLiveCommit,
		opaqueCaptureTarget: String,
		visualPageOrdinal: Int
	): ReaderPassiveRasterCaptureManifest? {
		if (
			currentLiveCommit !== liveCommit ||
			liveCommit.issuerIdentity !== issuerIdentity ||
			opaqueCaptureTarget.isBlank() ||
			visualPageOrdinal < 0 ||
			manifestSequence >= ReaderPassiveRasterMaximumSafeInteger
		) {
			return null
		}
		manifestSequence += 1L
		val commit = liveCommit.canonicalCommit
		return ReaderPassiveRasterCaptureManifest(
			manifestSequence = manifestSequence,
			captureEpoch = commit.captureEpoch,
			liveFoliateSessionId = commit.liveFoliateSessionId,
			publicationSessionGeneration = commit.publicationSessionGeneration,
			destinationCommitToken = commit.destinationCommitToken,
			opaqueCaptureTarget = opaqueCaptureTarget,
			visualPageOrdinal = visualPageOrdinal,
			rasterProfileKey = commit.rasterProfileKey,
			paginationFingerprint = commit.paginationFingerprint,
			layoutFingerprint = commit.layoutFingerprint,
			decorationFingerprint = commit.decorationFingerprint,
			viewportAndCaptureGeometry = commit.viewportAndCaptureGeometry,
			rasterGeneration = commit.rasterGeneration
		)
	}
}

internal data class ReaderPassiveRasterCaptureReceipt(
	val passiveSessionId: String,
	val echoedManifestSequence: Long,
	val echoedCaptureEpoch: Long,
	val echoedLiveFoliateSessionId: String,
	val echoedPublicationSessionGeneration: Long,
	val echoedDestinationCommitToken: String,
	val observedCaptureTarget: String,
	val observedVisualPageOrdinal: Int,
	val observedRasterProfileKey: String,
	val observedPaginationFingerprint: String,
	val observedLayoutFingerprint: String,
	val observedDecorationFingerprint: String,
	val observedViewportAndCaptureGeometry: ReaderPassiveRasterGeometry,
	val echoedRasterGeneration: Long,
	val passiveCommitSequence: Long
)

internal data class ReaderPassiveRasterAdmissionContext(
	val expectedManifestSequence: Long,
	val currentCaptureEpoch: Long,
	val currentLiveFoliateSessionId: String,
	val activePublicationSessionGeneration: Long,
	val currentDestinationCommitToken: String,
	val currentOpaqueCaptureTarget: String,
	val currentVisualPageOrdinal: Int,
	val currentRasterProfileKey: String,
	val currentPaginationFingerprint: String,
	val currentLayoutFingerprint: String,
	val currentDecorationFingerprint: String,
	val currentViewportAndCaptureGeometry: ReaderPassiveRasterGeometry,
	val currentRasterGeneration: Long,
	val activePassiveSessionId: String,
	val expectedPassiveCommitSequence: Long
)

internal enum class ReaderPassiveRasterRejection {
	ManifestSequence,
	CaptureEpoch,
	LiveFoliateSession,
	PublicationGeneration,
	DestinationCommit,
	OpaqueTarget,
	VisualPageOrdinal,
	RasterProfile,
	PaginationFingerprint,
	LayoutFingerprint,
	DecorationFingerprint,
	Geometry,
	RasterGeneration,
	PassiveSession,
	PassiveCommitSequence,
	RasterUnavailable
}

internal class ReaderPassiveRasterOwnership<R : Any>(
	raster: R,
	private val releaseRaster: (R) -> Unit
) {
	private var ownedRaster: R? = raster

	fun transfer(): R? = synchronized(this) {
		ownedRaster.also { ownedRaster = null }
	}

	fun release(): Boolean {
		val raster = synchronized(this) {
			ownedRaster?.also { ownedRaster = null }
		} ?: return false
		releaseRaster(raster)
		return true
	}
}

internal data class ReaderPassiveRasterCaptureResult<R : Any>(
	val manifest: ReaderPassiveRasterCaptureManifest,
	val receipt: ReaderPassiveRasterCaptureReceipt,
	val raster: ReaderPassiveRasterOwnership<R>?
)

internal sealed interface ReaderPassiveRasterAdmission<out R : Any> {
	class Admitted<R : Any> internal constructor(
		val receipt: ReaderPassiveRasterCaptureReceipt,
		private val raster: ReaderPassiveRasterOwnership<R>
	) : ReaderPassiveRasterAdmission<R> {
		fun transferRaster(): R? = raster.transfer()
		fun releaseRaster(): Boolean = raster.release()
	}

	data class Rejected(
		val reason: ReaderPassiveRasterRejection
	) : ReaderPassiveRasterAdmission<Nothing>
}

internal fun <R : Any> readerAdmitPassiveRaster(
	context: ReaderPassiveRasterAdmissionContext,
	capture: ReaderPassiveRasterCaptureResult<R>
): ReaderPassiveRasterAdmission<R> {
	val mismatch = capture.manifest.currentMismatch(context)
		?: capture.receipt.manifestMismatch(capture.manifest, context)
	if (mismatch != null) {
		capture.raster?.release()
		return ReaderPassiveRasterAdmission.Rejected(mismatch)
	}
	val raster = capture.raster
		?: return ReaderPassiveRasterAdmission.Rejected(
			ReaderPassiveRasterRejection.RasterUnavailable
		)
	return ReaderPassiveRasterAdmission.Admitted(capture.receipt, raster)
}

private fun ReaderPassiveRasterCaptureManifest.currentMismatch(
	context: ReaderPassiveRasterAdmissionContext
): ReaderPassiveRasterRejection? = when {
	manifestSequence != context.expectedManifestSequence ->
		ReaderPassiveRasterRejection.ManifestSequence
	captureEpoch != context.currentCaptureEpoch ->
		ReaderPassiveRasterRejection.CaptureEpoch
	liveFoliateSessionId != context.currentLiveFoliateSessionId ->
		ReaderPassiveRasterRejection.LiveFoliateSession
	publicationSessionGeneration != context.activePublicationSessionGeneration ->
		ReaderPassiveRasterRejection.PublicationGeneration
	destinationCommitToken != context.currentDestinationCommitToken ->
		ReaderPassiveRasterRejection.DestinationCommit
	opaqueCaptureTarget != context.currentOpaqueCaptureTarget ->
		ReaderPassiveRasterRejection.OpaqueTarget
	visualPageOrdinal != context.currentVisualPageOrdinal ->
		ReaderPassiveRasterRejection.VisualPageOrdinal
	rasterProfileKey != context.currentRasterProfileKey ->
		ReaderPassiveRasterRejection.RasterProfile
	paginationFingerprint != context.currentPaginationFingerprint ->
		ReaderPassiveRasterRejection.PaginationFingerprint
	layoutFingerprint != context.currentLayoutFingerprint ->
		ReaderPassiveRasterRejection.LayoutFingerprint
	decorationFingerprint != context.currentDecorationFingerprint ->
		ReaderPassiveRasterRejection.DecorationFingerprint
	viewportAndCaptureGeometry != context.currentViewportAndCaptureGeometry ->
		ReaderPassiveRasterRejection.Geometry
	rasterGeneration != context.currentRasterGeneration ->
		ReaderPassiveRasterRejection.RasterGeneration
	else -> null
}

private fun ReaderPassiveRasterCaptureReceipt.manifestMismatch(
	manifest: ReaderPassiveRasterCaptureManifest,
	context: ReaderPassiveRasterAdmissionContext
): ReaderPassiveRasterRejection? = when {
	echoedManifestSequence != manifest.manifestSequence ->
		ReaderPassiveRasterRejection.ManifestSequence
	echoedCaptureEpoch != manifest.captureEpoch ->
		ReaderPassiveRasterRejection.CaptureEpoch
	echoedLiveFoliateSessionId != manifest.liveFoliateSessionId ->
		ReaderPassiveRasterRejection.LiveFoliateSession
	echoedPublicationSessionGeneration != manifest.publicationSessionGeneration ->
		ReaderPassiveRasterRejection.PublicationGeneration
	echoedDestinationCommitToken != manifest.destinationCommitToken ->
		ReaderPassiveRasterRejection.DestinationCommit
	observedCaptureTarget != manifest.opaqueCaptureTarget ->
		ReaderPassiveRasterRejection.OpaqueTarget
	observedVisualPageOrdinal != manifest.visualPageOrdinal ->
		ReaderPassiveRasterRejection.VisualPageOrdinal
	observedRasterProfileKey != manifest.rasterProfileKey ->
		ReaderPassiveRasterRejection.RasterProfile
	observedPaginationFingerprint != manifest.paginationFingerprint ->
		ReaderPassiveRasterRejection.PaginationFingerprint
	observedLayoutFingerprint != manifest.layoutFingerprint ->
		ReaderPassiveRasterRejection.LayoutFingerprint
	observedDecorationFingerprint != manifest.decorationFingerprint ->
		ReaderPassiveRasterRejection.DecorationFingerprint
	observedViewportAndCaptureGeometry != manifest.viewportAndCaptureGeometry ->
		ReaderPassiveRasterRejection.Geometry
	echoedRasterGeneration != manifest.rasterGeneration ->
		ReaderPassiveRasterRejection.RasterGeneration
	passiveSessionId != context.activePassiveSessionId ->
		ReaderPassiveRasterRejection.PassiveSession
	passiveCommitSequence != context.expectedPassiveCommitSequence ->
		ReaderPassiveRasterRejection.PassiveCommitSequence
	else -> null
}

internal interface ReaderPassiveRasterRuntimePort<R : Any> {
	val passiveSessionId: String
	val isReady: Boolean

	fun commit(
		manifest: ReaderPassiveRasterCaptureManifest,
		captureTarget: String,
		passiveCommitSequence: Long,
		onCommitted: (ReaderPassiveRasterCaptureReceipt?) -> Unit
	)

	fun capture(
		geometry: ReaderPassiveRasterGeometry,
		onCaptured: (R?) -> Unit
	)

	fun pause()
	fun resume()
	fun destroy()
}

internal enum class ReaderPassiveRasterLifecycle {
	Active,
	Paused,
	Destroyed
}

internal data class ReaderPassiveRasterPrototypeMetrics(
	val captureAttempts: Int,
	val captureCompletions: Int,
	val captureFailures: Int,
	val staleCallbacks: Int,
	val rasterReleases: Int,
	val activeCaptures: Int,
	val lifecycle: ReaderPassiveRasterLifecycle
)

internal class ReaderPassiveRasterPrototypeSession<R : Any>(
	private val runtime: ReaderPassiveRasterRuntimePort<R>,
	private val releaseRaster: (R) -> Unit
) : AutoCloseable {
	private enum class CapturePhase {
		AwaitingCommit,
		AwaitingRaster
	}

	private class ActiveCapture<R : Any>(
		val manifest: ReaderPassiveRasterCaptureManifest,
		val commitSequence: Long,
		val callback: (ReaderPassiveRasterCaptureResult<R>?) -> Unit,
		var phase: CapturePhase = CapturePhase.AwaitingCommit
	)

	private val lock = Any()
	private var lifecycle = ReaderPassiveRasterLifecycle.Active
	private var activeCapture: ActiveCapture<R>? = null
	private var passiveCommitSequence = 0L
	private var captureAttempts = 0
	private var captureCompletions = 0
	private var captureFailures = 0
	private var staleCallbacks = 0
	private var rasterReleases = 0

	fun capture(
		manifest: ReaderPassiveRasterCaptureManifest,
		onCaptured: (ReaderPassiveRasterCaptureResult<R>?) -> Unit
	): Boolean {
		val capture = synchronized(lock) {
			if (
				lifecycle != ReaderPassiveRasterLifecycle.Active ||
				activeCapture != null ||
				!runtime.isReady ||
				passiveCommitSequence >= ReaderPassiveRasterMaximumSafeInteger
			) {
				return false
			}
			passiveCommitSequence += 1L
			captureAttempts = captureAttempts.incrementBounded()
			ActiveCapture(manifest, passiveCommitSequence, onCaptured).also {
				activeCapture = it
			}
		}
		try {
			runtime.commit(
				manifest = manifest,
				captureTarget = manifest.opaqueCaptureTarget,
				passiveCommitSequence = capture.commitSequence
			) { receipt ->
				onCommitCompleted(capture, receipt)
			}
		} catch (_: Throwable) {
			completeFailure(capture)
		}
		return true
	}

	fun pause() {
		val retired = synchronized(lock) {
			if (lifecycle != ReaderPassiveRasterLifecycle.Active) return
			lifecycle = ReaderPassiveRasterLifecycle.Paused
			retireActiveCaptureLocked()
		}
		deliverFailure(retired)
		runtime.pause()
	}

	fun resume() {
		synchronized(lock) {
			if (lifecycle != ReaderPassiveRasterLifecycle.Paused) return
			lifecycle = ReaderPassiveRasterLifecycle.Active
		}
		runtime.resume()
	}

	override fun close() {
		val retired = synchronized(lock) {
			if (lifecycle == ReaderPassiveRasterLifecycle.Destroyed) return
			lifecycle = ReaderPassiveRasterLifecycle.Destroyed
			retireActiveCaptureLocked()
		}
		deliverFailure(retired)
		runtime.destroy()
	}

	fun metrics(): ReaderPassiveRasterPrototypeMetrics = synchronized(lock) {
		ReaderPassiveRasterPrototypeMetrics(
			captureAttempts = captureAttempts,
			captureCompletions = captureCompletions,
			captureFailures = captureFailures,
			staleCallbacks = staleCallbacks,
			rasterReleases = rasterReleases,
			activeCaptures = if (activeCapture == null) 0 else 1,
			lifecycle = lifecycle
		)
	}

	private fun onCommitCompleted(
		capture: ActiveCapture<R>,
		receipt: ReaderPassiveRasterCaptureReceipt?
	) {
		if (receipt == null) {
			completeFailure(capture)
			return
		}
		val shouldCapture = synchronized(lock) {
			if (
				activeCapture !== capture ||
				capture.phase != CapturePhase.AwaitingCommit ||
				lifecycle != ReaderPassiveRasterLifecycle.Active
			) {
				staleCallbacks = staleCallbacks.incrementBounded()
				false
			} else {
				capture.phase = CapturePhase.AwaitingRaster
				true
			}
		}
		if (!shouldCapture) return
		try {
			runtime.capture(capture.manifest.viewportAndCaptureGeometry) { raster ->
				onRasterCompleted(capture, receipt, raster)
			}
		} catch (_: Throwable) {
			completeFailure(capture)
		}
	}

	private fun onRasterCompleted(
		capture: ActiveCapture<R>,
		receipt: ReaderPassiveRasterCaptureReceipt,
		raster: R?
	) {
		if (raster == null) {
			completeFailure(capture)
			return
		}
		val callback = synchronized(lock) {
			if (
				activeCapture !== capture ||
				capture.phase != CapturePhase.AwaitingRaster ||
				lifecycle != ReaderPassiveRasterLifecycle.Active
			) {
				staleCallbacks = staleCallbacks.incrementBounded()
				null
			} else {
				activeCapture = null
				captureCompletions = captureCompletions.incrementBounded()
				capture.callback
			}
		}
		if (callback == null) {
			releaseOwnedRaster(raster)
			return
		}
		val result = ReaderPassiveRasterCaptureResult(
			manifest = capture.manifest,
			receipt = receipt,
			raster = ReaderPassiveRasterOwnership(raster, ::releaseOwnedRaster)
		)
		try {
			callback(result)
		} catch (failure: Throwable) {
			result.raster?.release()
			throw failure
		}
	}

	private fun completeFailure(capture: ActiveCapture<R>) {
		val callback = synchronized(lock) {
			if (activeCapture !== capture) {
				staleCallbacks = staleCallbacks.incrementBounded()
				null
			} else {
				activeCapture = null
				captureFailures = captureFailures.incrementBounded()
				capture.callback
			}
		}
		callback?.invoke(null)
	}

	private fun retireActiveCaptureLocked(): ActiveCapture<R>? = activeCapture?.also {
		activeCapture = null
		captureFailures = captureFailures.incrementBounded()
	}

	private fun deliverFailure(capture: ActiveCapture<R>?) {
		capture?.callback?.invoke(null)
	}

	private fun releaseOwnedRaster(raster: R) {
		try {
			releaseRaster(raster)
		} finally {
			synchronized(lock) {
				rasterReleases = rasterReleases.incrementBounded()
			}
		}
	}
}

private fun requireSafeIdentity(value: Long) {
	require(value in 0L..ReaderPassiveRasterMaximumSafeInteger)
}

private fun Int.incrementBounded(): Int = if (this == Int.MAX_VALUE) this else this + 1
