package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener

internal data class ReaderPassiveRasterManifestInputs(
	val canonicalCommit: ReaderPassiveRasterCanonicalCommit,
	val opaqueCaptureTarget: String,
	val visualPageOrdinal: Int,
	val rasterDescriptor: ReaderPageRasterDescriptor
)

internal sealed interface ReaderPassiveRasterManifestResolution {
	data class Available(
		val inputs: ReaderPassiveRasterManifestInputs
	) : ReaderPassiveRasterManifestResolution

	data class Failed(
		val reason: String
	) : ReaderPassiveRasterManifestResolution

	data object Unavailable : ReaderPassiveRasterManifestResolution
}

internal data class ReaderPassiveRasterAdmissionAuthority(
	val manifestInputs: ReaderPassiveRasterManifestInputs,
	val activePassiveSessionId: String,
	val expectedPassiveCommitSequence: Long
)

internal fun interface ReaderPassiveRasterLiveManifestPort {
	fun request(
		visualPageOrdinal: Int,
		captureEpoch: Long,
		rasterGeneration: Long,
		preparationGeneration: Long,
		onResolved: (ReaderPassiveRasterManifestResolution) -> Unit
	)
}

internal interface ReaderPassiveRasterPreparationPort : AutoCloseable {
	val isAvailable: Boolean
	val isRetired: Boolean
		get() = false

	fun start(
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		rasterGeneration: Long,
		preparationGeneration: Long = 0L,
		isPreparationGenerationCurrent: (Long) -> Boolean = { true },
		isStillCurrent: () -> Boolean,
		trigger: ReaderPageRasterAcquisitionTrigger,
		capacityPolicy: ReaderPageRasterCapacityPolicy =
			ReaderPageRasterCapacityPolicy.FailClosed,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit = {},
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit = {},
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit = {},
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit = { _, _ -> },
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean

	fun cancel()
	fun pause()
	fun resume()
	override fun close()
}

internal class ReaderPassiveRasterPreparationAdapter(
	private val session: ReaderPassiveRasterPrototypeSession<Bitmap>,
	private val liveManifestPort: ReaderPassiveRasterLiveManifestPort,
	private val bundleSource: ReaderPageTurnBundleSource,
	initialCaptureEpoch: Long
) : ReaderPassiveRasterPreparationPort {
	private enum class Lifecycle {
		Active,
		Paused,
		Closed
	}

	private class Batch(
		val token: Long,
		val kind: ReaderPageTurnTransitionKind,
		val reference: ReaderPageSlideSnapshot,
		val targets: List<ReaderPageRasterBatchTarget>,
		val rasterGeneration: Long,
		val preparationGeneration: Long,
		val isPreparationGenerationCurrent: (Long) -> Boolean,
		val isStillCurrent: () -> Boolean,
		val trigger: ReaderPageRasterAcquisitionTrigger,
		val capacityPolicy: ReaderPageRasterCapacityPolicy,
		val onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		val onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		val onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		val onProgress: (Int, Int) -> Unit,
		val onComplete: (ReaderPageRasterBatchOutcome) -> Unit,
		var targetIndex: Int = 0,
		var hydration: ReaderPageRasterHydrationRequest? = null,
		var pendingCapture: ReaderPassiveRasterCaptureResult<Bitmap>? = null
	)

	private val manifestIssuer = ReaderPassiveRasterManifestIssuer()
	private var lifecycle = Lifecycle.Active
	private var captureEpoch = initialCaptureEpoch
	private var nextBatchToken = 0L
	private var activeBatch: Batch? = null

	init {
		require(initialCaptureEpoch >= 0L)
	}

	override val isAvailable: Boolean
		get() = lifecycle == Lifecycle.Active && session.isReady

	override val isRetired: Boolean
		get() = lifecycle == Lifecycle.Closed || session.isRetired

	override fun start(
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		rasterGeneration: Long,
		preparationGeneration: Long,
		isPreparationGenerationCurrent: (Long) -> Boolean,
		isStillCurrent: () -> Boolean,
		trigger: ReaderPageRasterAcquisitionTrigger,
		capacityPolicy: ReaderPageRasterCapacityPolicy,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean {
		if (
			!isAvailable ||
			activeBatch != null ||
			targets.isEmpty() ||
			rasterGeneration != bundleSource.currentGeneration() ||
			!runCatching {
				isPreparationGenerationCurrent(preparationGeneration)
			}.getOrDefault(false) ||
			!runCatching(isStillCurrent).getOrDefault(false)
		) {
			reference.release()
			return false
		}
		val batch = Batch(
			token = Math.incrementExact(nextBatchToken).also { nextBatchToken = it },
			kind = kind,
			reference = reference,
			targets = targets,
			rasterGeneration = rasterGeneration,
			preparationGeneration = preparationGeneration,
			isPreparationGenerationCurrent = isPreparationGenerationCurrent,
			isStillCurrent = isStillCurrent,
			trigger = trigger,
			capacityPolicy = capacityPolicy,
			onActiveTarget = onActiveTarget,
			onHydrationMiss = onHydrationMiss,
			onTargetDurable = onTargetDurable,
			onProgress = onProgress,
			onComplete = onComplete
		)
		activeBatch = batch
		onProgress(0, targets.size)
		prepareTarget(batch)
		return true
	}

	override fun cancel() {
		if (lifecycle == Lifecycle.Closed) return
		val batch = activeBatch
		activeBatch = null
		retireCaptureEpoch()
		manifestIssuer.clearCanonicalCommit()
		batch?.hydration?.cancel()
		batch?.hydration = null
		batch?.releasePendingCapture()
		session.cancelActiveCapture()
		if (batch != null) {
			batch.reference.release()
			batch.onComplete(ReaderPageRasterBatchOutcome.Cancelled)
		}
	}

	override fun pause() {
		if (lifecycle != Lifecycle.Active) return
		cancel()
		lifecycle = Lifecycle.Paused
		session.pause()
	}

	override fun resume() {
		if (lifecycle != Lifecycle.Paused) return
		lifecycle = Lifecycle.Active
		session.resume()
	}

	override fun close() {
		if (lifecycle == Lifecycle.Closed) return
		cancel()
		lifecycle = Lifecycle.Closed
		manifestIssuer.clearCanonicalCommit()
		session.close()
	}

	private fun prepareTarget(batch: Batch) {
		if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) {
			finish(batch, ReaderPageRasterBatchOutcome.Cancelled)
			return
		}
		if (batch.targetIndex >= batch.targets.size) {
			finish(batch, ReaderPageRasterBatchOutcome.Ready)
			return
		}
		val targetIndex = batch.targetIndex
		val target = batch.targets[targetIndex]
		batch.onActiveTarget(target)
		liveManifestPort.request(
			visualPageOrdinal = target.pageIndex,
			captureEpoch = captureEpoch,
			rasterGeneration = batch.rasterGeneration,
			preparationGeneration = batch.preparationGeneration
		) manifestInputs@{ resolution ->
			if (
				!isPreparationGenerationCurrent(batch) ||
				!batchIsCurrent(batch) ||
				batch.targetIndex != targetIndex
			) {
				return@manifestInputs
			}
			if (resolution is ReaderPassiveRasterManifestResolution.Failed) {
				finish(
					batch,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "passive-manifest",
						pageIndex = target.pageIndex,
						reason = resolution.reason
					)
				)
				return@manifestInputs
			}
			val inputs = (resolution as? ReaderPassiveRasterManifestResolution.Available)
				?.inputs
			val currentInputs = inputs?.takeIf {
				it.visualPageOrdinal == target.pageIndex &&
					it.rasterDescriptor.visualPageOrdinal == target.pageIndex &&
					it.rasterDescriptor.paginationFingerprint ==
						it.canonicalCommit.paginationFingerprint &&
					it.rasterDescriptor.layoutFingerprint ==
						it.canonicalCommit.layoutFingerprint &&
					it.rasterDescriptor.decorationFingerprint ==
						it.canonicalCommit.decorationFingerprint &&
					it.canonicalCommit.captureEpoch == captureEpoch &&
					it.canonicalCommit.rasterGeneration == batch.rasterGeneration
			}
			if (currentInputs == null) {
				val outcome = if (resolution == ReaderPassiveRasterManifestResolution.Unavailable) {
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "passive-manifest",
						pageIndex = target.pageIndex,
						reason = "canonical-live-commit-unavailable"
					)
				} else {
					ReaderPageRasterBatchOutcome.Failed(
						stage = "passive-manifest",
						pageIndex = target.pageIndex,
						reason = "manifest-invalid"
					)
				}
				finish(batch, outcome)
				return@manifestInputs
			}
			resolveTarget(batch, targetIndex, target, currentInputs)
		}
	}

	private fun resolveTarget(
		batch: Batch,
		targetIndex: Int,
		target: ReaderPageRasterBatchTarget,
		inputs: ReaderPassiveRasterManifestInputs
	) {
		if (
			inputs.canonicalCommit.profileAuthority ==
			ReaderPassiveRasterProfileAuthority.PassiveRealized
		) {
			captureTarget(batch, target, inputs)
			return
		}
		var hydrationCompleted = false
		val hydrationRequest = bundleSource.resolvePassiveRasterTarget(
			pageIndex = target.pageIndex,
			kind = batch.kind,
			reference = batch.reference,
			priority = target.priority,
			rasterDescriptor = inputs.rasterDescriptor,
			isStillCurrent = { batchIsCurrent(batch) }
		) resolved@{ result ->
			hydrationCompleted = true
			if (batch.targetIndex == targetIndex) batch.hydration = null
			if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) return@resolved
			when (result) {
				ReaderPageRasterPublicationResult.Durable -> completeTarget(batch, target)
				ReaderPageRasterPublicationResult.CapacityReached -> {
					if (batch.capacityPolicy == ReaderPageRasterCapacityPolicy.StopBackgroundRefill) {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.CapacityReached(target.pageIndex)
						)
					} else {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "persistent-publication",
								pageIndex = target.pageIndex,
								reason = "capacity-reached"
							)
						)
					}
				}
				ReaderPageRasterPublicationResult.Failed,
				null -> captureTarget(batch, target, inputs)
			}
		}
		if (!hydrationCompleted && batchIsCurrent(batch) && batch.targetIndex == targetIndex) {
			batch.hydration = hydrationRequest
		} else {
			hydrationRequest.cancel()
		}
	}

	private fun captureTarget(
		batch: Batch,
		target: ReaderPageRasterBatchTarget,
		inputs: ReaderPassiveRasterManifestInputs
	) {
		if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) return
		batch.onHydrationMiss(target)
		val liveCommit = manifestIssuer.replaceCanonicalCommit(inputs.canonicalCommit)
		val manifest = manifestIssuer.issue(
			liveCommit = liveCommit,
			opaqueCaptureTarget = inputs.opaqueCaptureTarget,
			visualPageOrdinal = inputs.visualPageOrdinal
		)
		if (manifest == null || !session.capture(manifest) captured@{ capture ->
			if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) {
				capture?.raster?.release()
				return@captured
			}
			if (capture == null) {
				finish(
					batch,
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "passive-host",
						pageIndex = target.pageIndex,
						reason = "capture-unavailable"
					)
				)
				return@captured
			}
			admitCapturedTarget(batch, target, capture)
		}) {
			finish(
				batch,
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "passive-host",
					pageIndex = target.pageIndex,
					reason = "capture-unavailable"
				)
			)
		}
	}

	private fun admitCapturedTarget(
		batch: Batch,
		target: ReaderPageRasterBatchTarget,
		capture: ReaderPassiveRasterCaptureResult<Bitmap>
	) {
		if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) {
			capture.raster?.release()
			return
		}
		check(batch.pendingCapture == null) {
			"Passive raster batch already owns a captured raster"
		}
		batch.pendingCapture = capture
		liveManifestPort.request(
			visualPageOrdinal = target.pageIndex,
			captureEpoch = captureEpoch,
			rasterGeneration = batch.rasterGeneration,
			preparationGeneration = batch.preparationGeneration
		) currentAuthority@{ resolution ->
			val currentInputs =
				(resolution as? ReaderPassiveRasterManifestResolution.Available)?.inputs
			if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch) || currentInputs == null) {
				batch.releasePendingCapture(capture)
				if (batchIsCurrent(batch)) {
					finish(
						batch,
						ReaderPageRasterBatchOutcome.Failed(
							stage = "passive-admission",
							pageIndex = target.pageIndex,
							reason = "current-authority-unavailable"
						)
					)
				}
				return@currentAuthority
			}
			if (!batch.transferPendingCapture(capture)) {
				capture.raster?.release()
				return@currentAuthority
			}
			bundleSource.admitPassiveRasterCapture(
				capture = capture,
				currentAuthority = ReaderPassiveRasterAdmissionAuthority(
					manifestInputs = currentInputs,
					activePassiveSessionId = session.passiveSessionId,
					expectedPassiveCommitSequence = capture.expectedPassiveCommitSequence
				),
				pageIndex = target.pageIndex,
				kind = batch.kind,
				reference = batch.reference,
				priority = target.priority,
				preparationGeneration = batch.preparationGeneration,
				isPreparationGenerationCurrent = batch.isPreparationGenerationCurrent,
				isStillCurrent = { batchIsCurrent(batch) }
			) admitted@{ result ->
				if (!batchIsCurrent(batch)) return@admitted
				when (result) {
					ReaderPageRasterPublicationResult.Durable -> completeTarget(batch, target)
					ReaderPageRasterPublicationResult.CapacityReached -> {
						if (batch.capacityPolicy == ReaderPageRasterCapacityPolicy.StopBackgroundRefill) {
							finish(
								batch,
								ReaderPageRasterBatchOutcome.CapacityReached(target.pageIndex)
							)
						} else {
							finish(
								batch,
								ReaderPageRasterBatchOutcome.Failed(
									stage = "persistent-publication",
									pageIndex = target.pageIndex,
									reason = "capacity-reached"
								)
							)
						}
					}
					ReaderPageRasterPublicationResult.Failed -> finish(
						batch,
						ReaderPageRasterBatchOutcome.Failed(
							stage = "passive-admission",
							pageIndex = target.pageIndex,
							reason = "raster-rejected-or-publication-failed"
						)
					)
				}
			}
		}
	}

	private fun completeTarget(batch: Batch, target: ReaderPageRasterBatchTarget) {
		if (!batchIsCurrent(batch)) return
		batch.onTargetDurable(target)
		batch.targetIndex += 1
		batch.onProgress(batch.targetIndex, batch.targets.size)
		prepareTarget(batch)
	}

	private fun finish(batch: Batch, outcome: ReaderPageRasterBatchOutcome) {
		if (activeBatch !== batch) return
		activeBatch = null
		batch.hydration?.cancel()
		batch.hydration = null
		batch.releasePendingCapture()
		manifestIssuer.clearCanonicalCommit()
		batch.reference.release()
		batch.onComplete(outcome)
	}

	private fun Batch.releasePendingCapture(
		expected: ReaderPassiveRasterCaptureResult<Bitmap>? = null
	): Boolean {
		val capture = pendingCapture ?: return false
		if (expected != null && capture !== expected) return false
		pendingCapture = null
		return capture.raster?.release() == true
	}

	private fun Batch.transferPendingCapture(
		expected: ReaderPassiveRasterCaptureResult<Bitmap>
	): Boolean {
		if (pendingCapture !== expected) return false
		pendingCapture = null
		return true
	}

	private fun isPreparationGenerationCurrent(batch: Batch): Boolean =
		runCatching {
			batch.isPreparationGenerationCurrent(batch.preparationGeneration)
		}.getOrDefault(false)

	private fun batchIsCurrent(batch: Batch): Boolean =
		lifecycle == Lifecycle.Active &&
			activeBatch === batch &&
			batch.rasterGeneration == bundleSource.currentGeneration() &&
			isPreparationGenerationCurrent(batch) &&
			runCatching(batch.isStillCurrent).getOrDefault(false)

	private fun retireCaptureEpoch() {
		captureEpoch = if (captureEpoch == Long.MAX_VALUE) 0L else captureEpoch + 1L
	}
}

internal class ReaderPageLivePassiveRasterManifestPort(
	private val webViewProvider: () -> WebView?
) : ReaderPassiveRasterLiveManifestPort {
	override fun request(
		visualPageOrdinal: Int,
		captureEpoch: Long,
		rasterGeneration: Long,
		preparationGeneration: Long,
		onResolved: (ReaderPassiveRasterManifestResolution) -> Unit
	) {
		val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
		if (webView == null) {
			onResolved(ReaderPassiveRasterManifestResolution.Unavailable)
			return
		}
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?." +
					"pageTurnPassiveRasterManifestInputs?.(" +
					"$visualPageOrdinal, $captureEpoch, $rasterGeneration, " +
						"$preparationGeneration) ?? null)"
			) { encoded ->
				val resolution = readerPassiveRasterManifestResolution(encoded)
				val normalized = when (resolution) {
					is ReaderPassiveRasterManifestResolution.Available -> {
						val measuredWidth = webView.width
						val measuredHeight = webView.height
						val geometry = readerPassiveRasterCanonicalFullFrameGeometry(
							observedGeometry = resolution.inputs.canonicalCommit
								.viewportAndCaptureGeometry,
							measuredWidth = measuredWidth,
							measuredHeight = measuredHeight
						)
						when {
							measuredWidth <= 0 || measuredHeight <= 0 ->
								ReaderPassiveRasterManifestResolution.Unavailable
							geometry == null -> ReaderPassiveRasterManifestResolution.Failed(
								"manifest-invalid"
							)
							else -> ReaderPassiveRasterManifestResolution.Available(
								resolution.inputs.copy(
									canonicalCommit = resolution.inputs.canonicalCommit.copy(
										viewportAndCaptureGeometry = geometry
									)
								)
							)
						}
					}
					else -> resolution
				}
				onResolved(normalized)
			}
		} catch (_: Throwable) {
			onResolved(ReaderPassiveRasterManifestResolution.Unavailable)
		}
	}
}

internal fun readerPassiveRasterManifestResolution(
	encoded: String?
): ReaderPassiveRasterManifestResolution {
	val jsonText = runCatching {
		JSONTokener(encoded).nextValue() as? String
	}.getOrNull() ?: return ReaderPassiveRasterManifestResolution.Unavailable
	if (jsonText == "null") return ReaderPassiveRasterManifestResolution.Unavailable
	val json = runCatching { JSONObject(jsonText) }.getOrNull()
		?: return ReaderPassiveRasterManifestResolution.Failed("manifest-invalid")
	val failureReason = json.optString("failureReason").takeIf(String::isNotBlank)
	if (failureReason != null) {
		return if (failureReason == "current-live-profile-unavailable") {
			ReaderPassiveRasterManifestResolution.Unavailable
		} else {
			ReaderPassiveRasterManifestResolution.Failed("manifest-invalid")
		}
	}
	return readerPassiveRasterManifestInputs(encoded)
		?.let(ReaderPassiveRasterManifestResolution::Available)
		?: ReaderPassiveRasterManifestResolution.Failed("manifest-invalid")
}

private fun readerPassiveRasterManifestInputs(
	encoded: String?
): ReaderPassiveRasterManifestInputs? = runCatching {
	val jsonText = JSONTokener(encoded).nextValue() as? String ?: return null
	val json = JSONObject(jsonText)
	val geometry = json.getJSONObject("viewportAndCaptureGeometry")
	val profileAuthority = json.optString("profileAuthority").takeIf(String::isNotBlank)
		?.let(ReaderPassiveRasterProfileAuthority::fromSerializedValue)
		?: if (!json.has("profileAuthority")) {
			ReaderPassiveRasterProfileAuthority.LiveRealized
		} else {
			return null
		}
	ReaderPassiveRasterManifestInputs(
		canonicalCommit = ReaderPassiveRasterCanonicalCommit(
			captureEpoch = json.getLong("captureEpoch"),
			liveFoliateSessionId = json.getString("liveFoliateSessionId"),
			publicationSessionGeneration = json.getLong("publicationSessionGeneration"),
			destinationCommitToken = json.getString("destinationCommitToken"),
			rasterProfileKey = json.getString("rasterProfileKey"),
			paginationFingerprint = json.getString("paginationFingerprint"),
			layoutFingerprint = json.getString("layoutFingerprint"),
			decorationFingerprint = json.getString("decorationFingerprint"),
			viewportAndCaptureGeometry = ReaderPassiveRasterGeometry(
				viewportWidth = geometry.getInt("viewportWidth"),
				viewportHeight = geometry.getInt("viewportHeight"),
				captureLeft = geometry.getInt("captureLeft"),
				captureTop = geometry.getInt("captureTop"),
				captureRight = geometry.getInt("captureRight"),
				captureBottom = geometry.getInt("captureBottom")
			),
			rasterGeneration = json.getLong("rasterGeneration"),
			profileAuthority = profileAuthority
		),
		opaqueCaptureTarget = json.getString("opaqueCaptureTarget"),
		visualPageOrdinal = json.getInt("visualPageOrdinal"),
		rasterDescriptor = readerPageRasterDescriptor(
			json.getJSONObject("rasterDescriptor").toString()
		) ?: return null
	)
}.getOrNull()
