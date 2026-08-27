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

internal enum class ReaderPassiveRasterProfileAuthority(
	val serializedValue: String
) {
	LiveRealized("live-realized-v1"),
	PassiveRealized("passive-realized-v1");

	companion object {
		fun fromSerializedValue(value: String?): ReaderPassiveRasterProfileAuthority? =
			entries.firstOrNull { it.serializedValue == value }
	}
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
	val rasterGeneration: Long,
	val profileAuthority: ReaderPassiveRasterProfileAuthority =
		ReaderPassiveRasterProfileAuthority.LiveRealized
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
	val rasterGeneration: Long,
	val profileAuthority: ReaderPassiveRasterProfileAuthority =
		ReaderPassiveRasterProfileAuthority.LiveRealized
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
			rasterGeneration = commit.rasterGeneration,
			profileAuthority = commit.profileAuthority
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
	val expectedPassiveCommitSequence: Long,
	val currentProfileAuthority: ReaderPassiveRasterProfileAuthority =
		ReaderPassiveRasterProfileAuthority.LiveRealized
)

internal enum class ReaderPassiveRasterRejection {
	ManifestSequence,
	CaptureEpoch,
	LiveFoliateSession,
	PublicationGeneration,
	DestinationCommit,
	OpaqueTarget,
	VisualPageOrdinal,
	ProfileAuthority,
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
	val raster: ReaderPassiveRasterOwnership<R>?,
	val expectedPassiveCommitSequence: Long = receipt.passiveCommitSequence
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
	profileAuthority != context.currentProfileAuthority ->
		ReaderPassiveRasterRejection.ProfileAuthority
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
	manifest.profileAuthority == ReaderPassiveRasterProfileAuthority.LiveRealized &&
		observedRasterProfileKey != manifest.rasterProfileKey ->
		ReaderPassiveRasterRejection.RasterProfile
	observedRasterProfileKey.isBlank() ->
		ReaderPassiveRasterRejection.RasterProfile
	manifest.profileAuthority == ReaderPassiveRasterProfileAuthority.LiveRealized &&
		observedPaginationFingerprint != manifest.paginationFingerprint ->
		ReaderPassiveRasterRejection.PaginationFingerprint
	observedPaginationFingerprint.isBlank() ->
		ReaderPassiveRasterRejection.PaginationFingerprint
	manifest.profileAuthority == ReaderPassiveRasterProfileAuthority.LiveRealized &&
		observedLayoutFingerprint != manifest.layoutFingerprint ->
		ReaderPassiveRasterRejection.LayoutFingerprint
	observedLayoutFingerprint.isBlank() ->
		ReaderPassiveRasterRejection.LayoutFingerprint
	manifest.profileAuthority == ReaderPassiveRasterProfileAuthority.LiveRealized &&
		observedDecorationFingerprint != manifest.decorationFingerprint ->
		ReaderPassiveRasterRejection.DecorationFingerprint
	observedDecorationFingerprint.isBlank() ->
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
	val isRetired: Boolean
		get() = false

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

	fun cancelActiveCommit(onDrained: () -> Unit)
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
		AwaitingRaster,
		DrainingCommit,
		DrainingRaster
	}

	private class ActiveCapture<R : Any>(
		val manifest: ReaderPassiveRasterCaptureManifest,
		val commitSequence: Long,
		val callback: (ReaderPassiveRasterCaptureResult<R>?) -> Unit,
		var phase: CapturePhase = CapturePhase.AwaitingCommit,
		var failureDelivered: Boolean = false,
		var onDrained: (() -> Unit)? = null
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

	val passiveSessionId: String
		get() = runtime.passiveSessionId

	val isReady: Boolean
		get() = synchronized(lock) {
			lifecycle == ReaderPassiveRasterLifecycle.Active &&
				activeCapture == null &&
				runtime.isReady
		}

	val isRetired: Boolean
		get() = synchronized(lock) {
			lifecycle == ReaderPassiveRasterLifecycle.Destroyed || runtime.isRetired
		}

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

	fun cancelActiveCapture(): Boolean = cancelActiveCapture(onDrained = null)

	private fun cancelActiveCapture(onDrained: (() -> Unit)?): Boolean {
		val cancellation = synchronized(lock) {
			if (lifecycle == ReaderPassiveRasterLifecycle.Destroyed) return false
			val capture = activeCapture ?: return false
			if (
				capture.phase == CapturePhase.DrainingCommit ||
				capture.phase == CapturePhase.DrainingRaster
			) {
				val previous = capture.onDrained
				capture.onDrained = when {
					previous == null -> onDrained
					onDrained == null -> previous
					else -> {
						{
							previous()
							onDrained()
						}
					}
				}
				return true
			}
			capture.failureDelivered = true
			capture.onDrained = onDrained
			captureFailures = captureFailures.incrementBounded()
			capture.phase = when (capture.phase) {
				CapturePhase.AwaitingCommit -> CapturePhase.DrainingCommit
				CapturePhase.AwaitingRaster -> CapturePhase.DrainingRaster
				else -> error("Capture was already draining")
			}
			capture
		}
		deliverFailure(cancellation)
		if (cancellation.phase == CapturePhase.DrainingCommit) {
			try {
				runtime.cancelActiveCommit { completeDrain(cancellation) }
			} catch (_: Throwable) {
				retireAfterCancellationFailure(cancellation)
			}
		}
		return true
	}

	fun pause() {
		val hasCapture = synchronized(lock) {
			if (lifecycle != ReaderPassiveRasterLifecycle.Active) return
			lifecycle = ReaderPassiveRasterLifecycle.Paused
			activeCapture != null
		}
		if (!hasCapture || !cancelActiveCapture(runtime::pause)) runtime.pause()
	}

	fun resume() {
		synchronized(lock) {
			if (lifecycle != ReaderPassiveRasterLifecycle.Paused) return
			lifecycle = ReaderPassiveRasterLifecycle.Active
		}
		runtime.resume()
	}

	override fun close() {
		var shouldDeliverFailure = false
		val retired = synchronized(lock) {
			if (lifecycle == ReaderPassiveRasterLifecycle.Destroyed) return
			lifecycle = ReaderPassiveRasterLifecycle.Destroyed
			activeCapture?.also { capture ->
				activeCapture = null
				if (!capture.failureDelivered) {
					capture.failureDelivered = true
					captureFailures = captureFailures.incrementBounded()
					shouldDeliverFailure = true
				}
			}
		}
		if (shouldDeliverFailure) deliverFailure(retired)
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
			val draining = synchronized(lock) {
				activeCapture === capture && capture.phase == CapturePhase.DrainingCommit
			}
			if (!draining) completeFailure(capture)
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
		var onDrained: (() -> Unit)? = null
		val callback = synchronized(lock) {
			when {
				activeCapture === capture && capture.phase == CapturePhase.DrainingRaster -> {
					activeCapture = null
					staleCallbacks = staleCallbacks.incrementBounded()
					onDrained = capture.onDrained
					null
				}
				activeCapture !== capture ||
					capture.phase != CapturePhase.AwaitingRaster ||
					lifecycle != ReaderPassiveRasterLifecycle.Active -> {
					staleCallbacks = staleCallbacks.incrementBounded()
					null
				}
				else -> {
					activeCapture = null
					captureCompletions = captureCompletions.incrementBounded()
					capture.callback
				}
			}
		}
		if (callback == null) {
			releaseOwnedRaster(raster)
			onDrained?.invoke()
			return
		}
		val result = ReaderPassiveRasterCaptureResult(
			manifest = capture.manifest,
			receipt = receipt,
			raster = ReaderPassiveRasterOwnership(raster, ::releaseOwnedRaster),
			expectedPassiveCommitSequence = capture.commitSequence
		)
		try {
			callback(result)
		} catch (failure: Throwable) {
			result.raster?.release()
			throw failure
		}
	}

	private fun completeFailure(capture: ActiveCapture<R>) {
		var onDrained: (() -> Unit)? = null
		val callback = synchronized(lock) {
			when {
				activeCapture !== capture -> {
					staleCallbacks = staleCallbacks.incrementBounded()
					null
				}
				capture.phase == CapturePhase.DrainingCommit ||
					capture.phase == CapturePhase.DrainingRaster -> {
					activeCapture = null
					onDrained = capture.onDrained
					null
				}
				else -> {
					activeCapture = null
					captureFailures = captureFailures.incrementBounded()
					capture.failureDelivered = true
					capture.callback
				}
			}
		}
		callback?.invoke(null)
		onDrained?.invoke()
	}

	private fun completeDrain(capture: ActiveCapture<R>) {
		val onDrained = synchronized(lock) {
			if (
				activeCapture !== capture ||
				capture.phase != CapturePhase.DrainingCommit
			) {
				return
			}
			activeCapture = null
			capture.onDrained
		}
		onDrained?.invoke()
	}

	private fun retireAfterCancellationFailure(capture: ActiveCapture<R>) {
		val shouldDestroy = synchronized(lock) {
			if (
				activeCapture !== capture ||
				capture.phase != CapturePhase.DrainingCommit
			) {
				false
			} else {
				activeCapture = null
				capture.onDrained = null
				lifecycle = ReaderPassiveRasterLifecycle.Destroyed
				true
			}
		}
		if (shouldDestroy) runCatching(runtime::destroy)
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
