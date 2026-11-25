package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.webkit.WebView
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.util.core.Logger

private const val ReaderPassiveRasterPreparationTag = "ReaderPassiveRasterPreparation"

internal data class ReaderPassiveRasterManifestInputs(
	val canonicalCommit: ReaderPassiveRasterCanonicalCommit,
	val opaqueCaptureTarget: String,
	val visualPageOrdinal: Int,
	val rasterDescriptor: ReaderPageRasterDescriptor
)

internal enum class ReaderPassiveRasterManifestUnavailableCause(
	val diagnosticReason: String,
	val terminal: Boolean = false
) {
	CanonicalRenderedDestinationAbsent("canonical-rendered-destination-absent"),
	CurrentLiveProfileOrLayoutUnavailable("current-live-profile-or-layout-unavailable"),
	LiveWebViewDetached("live-webview-detached"),
	BridgeRequestUnavailable("bridge-request-unavailable", terminal = true)
}

internal sealed interface ReaderPassiveRasterManifestResolution {
	data class Available(
		val inputs: ReaderPassiveRasterManifestInputs
	) : ReaderPassiveRasterManifestResolution

	data class Failed(
		val reason: String
	) : ReaderPassiveRasterManifestResolution

	data class Unavailable(
		val cause: ReaderPassiveRasterManifestUnavailableCause
	) : ReaderPassiveRasterManifestResolution
}

internal data class ReaderPassiveRasterAdmissionAuthority(
	val manifestInputs: ReaderPassiveRasterManifestInputs,
	val activePassiveSessionId: String,
	val expectedPassiveCommitSequence: Long
)

internal fun readerPassiveRasterReceiptAuthoritativeDescriptor(
	descriptor: ReaderPageRasterDescriptor,
	receipt: ReaderPassiveRasterCaptureReceipt
): ReaderPageRasterDescriptor = descriptor.copy(
	paginationFingerprint = receipt.observedPaginationFingerprint,
	layoutFingerprint = receipt.observedLayoutFingerprint,
	decorationFingerprint = receipt.observedDecorationFingerprint,
	viewportWidth = receipt.observedViewportAndCaptureGeometry.viewportWidth,
	viewportHeight = receipt.observedViewportAndCaptureGeometry.viewportHeight,
	visualPageOrdinal = receipt.observedVisualPageOrdinal
)

internal enum class ReaderPassiveRasterAliasMiss {
	Unavailable,
	Absent,
	Incompatible,
	HydrationMiss
}

internal sealed interface ReaderPassiveRasterAliasLookup {
	data class Hit(
		val realizedDescriptor: ReaderPageRasterDescriptor
	) : ReaderPassiveRasterAliasLookup

	data class Miss(
		val reason: ReaderPassiveRasterAliasMiss
	) : ReaderPassiveRasterAliasLookup
}

/**
 * Rebuilds the receipt-authoritative descriptor an alias [storedKey] was published under from the
 * planned descriptor (the inverse of [readerPassiveRasterReceiptAuthoritativeDescriptor]). Every
 * field the receipt does not observe (publication, spine, href, chapter page, ordinal, page count,
 * quality, schema) must be identical, otherwise the alias is rejected.
 */
internal fun readerPassiveRasterAliasLookup(
	plannedDescriptor: ReaderPageRasterDescriptor,
	quality: ReaderPageBitmapQuality,
	storedKey: ReaderPageRasterKey?
): ReaderPassiveRasterAliasLookup {
	if (storedKey == null) {
		return ReaderPassiveRasterAliasLookup.Miss(ReaderPassiveRasterAliasMiss.Absent)
	}
	val realized = plannedDescriptor.copy(
		paginationFingerprint = storedKey.paginationHash,
		layoutFingerprint = storedKey.layoutHash,
		decorationFingerprint = storedKey.decorationHash,
		viewportWidth = storedKey.viewportWidth,
		viewportHeight = storedKey.viewportHeight
	)
	return if (realized.key(quality).identity == storedKey.identity) {
		ReaderPassiveRasterAliasLookup.Hit(realized)
	} else {
		ReaderPassiveRasterAliasLookup.Miss(ReaderPassiveRasterAliasMiss.Incompatible)
	}
}

private fun ReaderPassiveRasterManifestResolution.Unavailable.toBatchOutcome(
	stage: String,
	pageIndex: Int
): ReaderPageRasterBatchOutcome = if (cause.terminal) {
	ReaderPageRasterBatchOutcome.Failed(
		stage = stage,
		pageIndex = pageIndex,
		reason = cause.diagnosticReason
	)
} else {
	ReaderPageRasterBatchOutcome.Deferred(
		stage = stage,
		pageIndex = pageIndex,
		reason = cause.diagnosticReason
	)
}

internal fun interface ReaderPassiveRasterLiveManifestPort {
	fun request(
		visualPageOrdinal: Int,
		captureEpoch: Long,
		rasterGeneration: Long,
		preparationGeneration: Long,
		onResolved: (ReaderPassiveRasterManifestResolution) -> Unit
	)
}

internal fun interface ReaderPageRasterCurrentLivePublicationPort {
	fun publish(
		reference: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		isStillCurrent: () -> Boolean,
		onPublished: (ReaderPageRasterPublicationCompletion) -> Unit
	)
}

private class ReaderPageBundleCurrentLivePublicationPort(
	private val bundleSource: ReaderPageTurnBundleSource
) : ReaderPageRasterCurrentLivePublicationPort {
	override fun publish(
		reference: ReaderPageSlideSnapshot,
		priority: ReaderPageRasterPriority,
		isStillCurrent: () -> Boolean,
		onPublished: (ReaderPageRasterPublicationCompletion) -> Unit
	) {
		bundleSource.ensurePersistentSnapshot(
			snapshot = reference,
			priority = priority,
			isStillCurrent = isStillCurrent,
			onPersisted = onPublished
		)
	}
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
	initialCaptureEpoch: Long,
	private val currentLivePublicationPort: ReaderPageRasterCurrentLivePublicationPort =
		ReaderPageBundleCurrentLivePublicationPort(bundleSource)
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
		var durableCount: Int = 0,
		// #78: the captured target whose durable write runs while the next target renders.
		var inFlightWrite: ReaderPageRasterBatchTarget? = null,
		var afterInFlightWrite: (() -> Unit)? = null,
		var hydration: ReaderPageRasterHydrationRequest? = null,
		var committedCapture: ReaderPassiveRasterCommittedCapture<Bitmap>? = null,
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
		batch?.releaseCommittedCapture()
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
			// Ready only once every target is durable, including the overlapped write.
			if (batch.inFlightWrite != null) {
				batch.afterInFlightWrite = { prepareTarget(batch) }
				return
			}
			finish(batch, ReaderPageRasterBatchOutcome.Ready)
			return
		}
		val targetIndex = batch.targetIndex
		val target = batch.targets[targetIndex]
		batch.onActiveTarget(target)
		if (target.authority == ReaderPageRasterTargetAuthority.CurrentLive) {
			fulfillCurrentLiveTarget(batch, targetIndex, target)
			return
		}
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
			if (resolution is ReaderPassiveRasterManifestResolution.Unavailable) {
				finish(
					batch,
					resolution.toBatchOutcome(
						stage = "passive-manifest",
						pageIndex = target.pageIndex
					)
				)
				return@manifestInputs
			}
			val inputs = (resolution as ReaderPassiveRasterManifestResolution.Available)
				.inputs
			val currentInputs = inputs.takeIf {
				manifestInputsAreCurrent(batch, target, it)
			}
			if (currentInputs == null) {
				finish(
					batch,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "passive-manifest",
						pageIndex = target.pageIndex,
						reason = "manifest-invalid"
					)
				)
				return@manifestInputs
			}
			resolveTarget(batch, targetIndex, target, currentInputs)
		}
	}

	private fun fulfillCurrentLiveTarget(
		batch: Batch,
		targetIndex: Int,
		target: ReaderPageRasterBatchTarget
	) {
		val reference = batch.reference
		if (
			target.pageIndex != reference.key.visualPageIndex ||
			batch.kind != reference.key.kind ||
			reference.bitmap.isRecycled
		) {
			finish(
				batch,
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "current-live-reference",
					pageIndex = target.pageIndex,
					reason = "retained-live-reference-unavailable"
				)
			)
			return
		}
		var publicationSettled = false
		currentLivePublicationPort.publish(
			reference = reference,
			priority = target.priority,
			isStillCurrent = {
				isPreparationGenerationCurrent(batch) && batchIsCurrent(batch)
			}
		) { completion ->
			publicationSettled = true
			val overlapped = batch.inFlightWrite === target
			if (!batchIsCurrent(batch) || (!overlapped && batch.targetIndex != targetIndex)) {
				return@publish
			}
			readerPageRasterPersistenceTerminalOutcome(
				trigger = batch.trigger,
				capacityPolicy = batch.capacityPolicy,
				completion = completion,
				pageIndex = target.pageIndex
			)?.let { outcome ->
				finish(batch, outcome)
				return@publish
			}
			if (overlapped) settleOverlappedWrite(batch, target) else completeTarget(batch, target)
		}
		if (!publicationSettled) overlapWrite(batch, target)
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
			bundleSource.lookupPassiveRasterAlias(inputs.rasterDescriptor) aliased@{ lookup ->
				if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) return@aliased
				when (lookup) {
					is ReaderPassiveRasterAliasLookup.Hit ->
						hydrateAliasedTarget(batch, targetIndex, target, inputs, lookup)
					is ReaderPassiveRasterAliasLookup.Miss -> {
						logAliasMiss(target, lookup.reason)
						captureTarget(batch, target, inputs)
					}
				}
			}
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
		) resolved@{ completion ->
			hydrationCompleted = true
			if (batch.targetIndex == targetIndex) batch.hydration = null
			if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) return@resolved
			when (completion?.result) {
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
								reason = "capacity-reached",
								persistentPublicationResult = completion.result,
								persistentWriteFailureReason = completion.writeFailureReason
							)
						)
					}
				}
				ReaderPageRasterPublicationResult.Failed -> {
					val writeFailureReason = completion.writeFailureReason
					if (writeFailureReason == null) {
						captureTarget(batch, target, inputs)
					} else {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "persistent-publication",
								pageIndex = target.pageIndex,
								reason = "durable-write-failed",
								persistentPublicationResult = completion.result,
								persistentWriteFailureReason = writeFailureReason
							)
						)
					}
				}
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
		val hydrateAfterCommit = inputs.canonicalCommit.profileAuthority ==
			ReaderPassiveRasterProfileAuthority.PassiveRealized
		// Any capture is a miss of the stored page, including one that is hydrated after commit.
		batch.onHydrationMiss(target)
		val liveCommit = manifestIssuer.replaceCanonicalCommit(inputs.canonicalCommit)
		val manifest = manifestIssuer.issue(
			liveCommit = liveCommit,
			opaqueCaptureTarget = inputs.opaqueCaptureTarget,
			visualPageOrdinal = inputs.visualPageOrdinal
		)
		if (manifest == null || !session.commit(manifest) committed@{ committed ->
			if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) {
				committed?.release()
				return@committed
			}
			if (committed == null) {
				finish(
					batch,
					ReaderPageRasterBatchOutcome.Deferred(
						stage = "passive-host",
						pageIndex = target.pageIndex,
						reason = "capture-unavailable"
					)
				)
				return@committed
			}
			check(batch.committedCapture == null) {
				"Passive raster batch already owns a committed capture"
			}
			batch.committedCapture = committed
			if (hydrateAfterCommit) {
				hydrateCommittedTarget(batch, target, inputs, committed)
			} else {
				captureCommittedTarget(
					batch = batch,
					target = target,
					committed = committed,
					reportHydrationMiss = false
				)
			}
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

	private fun hydrateCommittedTarget(
		batch: Batch,
		target: ReaderPageRasterBatchTarget,
		inputs: ReaderPassiveRasterManifestInputs,
		committed: ReaderPassiveRasterCommittedCapture<Bitmap>
	) {
		val receiptMismatch = authorityMismatch(inputs, committed)
		if (receiptMismatch != null) {
			finish(
				batch,
				ReaderPageRasterBatchOutcome.Failed(
					stage = "passive-commit-authority",
					pageIndex = target.pageIndex,
					reason = "raster-rejected",
					passiveRasterRejection = receiptMismatch
				)
			)
			return
		}
		val descriptor = readerPassiveRasterReceiptAuthoritativeDescriptor(
			descriptor = inputs.rasterDescriptor,
			receipt = committed.receipt
		)
		val targetIndex = batch.targetIndex
		var hydrationCompleted = false
		val hydrationRequest = bundleSource.resolvePassiveRasterTarget(
			pageIndex = target.pageIndex,
			kind = batch.kind,
			reference = batch.reference,
			priority = target.priority,
			rasterDescriptor = descriptor,
			persistentOnly = true,
			isStillCurrent = { batchIsCurrent(batch) }
		) resolved@{ completion ->
			hydrationCompleted = true
			if (batch.targetIndex == targetIndex) batch.hydration = null
			if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) return@resolved
			when (completion?.result) {
				ReaderPageRasterPublicationResult.Durable ->
					confirmDurableCommittedTarget(
						batch = batch,
						target = target,
						committed = committed,
						plannedDescriptor = inputs.rasterDescriptor,
						realizedDescriptor = descriptor
					)
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
								reason = "capacity-reached",
								persistentPublicationResult = completion.result,
								persistentWriteFailureReason = completion.writeFailureReason
							)
						)
					}
				}
				ReaderPageRasterPublicationResult.Failed -> {
					val writeFailureReason = completion.writeFailureReason
					if (writeFailureReason == null) {
						captureCommittedTarget(
							batch = batch,
							target = target,
							committed = committed,
							reportHydrationMiss = true
						)
					} else {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "persistent-publication",
								pageIndex = target.pageIndex,
								reason = "durable-write-failed",
								persistentPublicationResult = completion.result,
								persistentWriteFailureReason = writeFailureReason
							)
						)
					}
				}
				null -> captureCommittedTarget(
					batch = batch,
					target = target,
					committed = committed,
					reportHydrationMiss = true
				)
			}
		}
		if (!hydrationCompleted && batchIsCurrent(batch) && batch.targetIndex == targetIndex) {
			batch.hydration = hydrationRequest
		} else {
			hydrationRequest.cancel()
		}
	}

	private fun confirmDurableCommittedTarget(
		batch: Batch,
		target: ReaderPageRasterBatchTarget,
		committed: ReaderPassiveRasterCommittedCapture<Bitmap>,
		plannedDescriptor: ReaderPageRasterDescriptor,
		realizedDescriptor: ReaderPageRasterDescriptor
	) {
		val targetIndex = batch.targetIndex
		liveManifestPort.request(
			visualPageOrdinal = target.pageIndex,
			captureEpoch = captureEpoch,
			rasterGeneration = batch.rasterGeneration,
			preparationGeneration = batch.preparationGeneration
		) currentAuthority@{ resolution ->
			if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) {
				return@currentAuthority
			}
			when (resolution) {
				is ReaderPassiveRasterManifestResolution.Available -> {
					val currentInputs = resolution.inputs
					if (!manifestInputsAreCurrent(batch, target, currentInputs)) {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "passive-hydration-authority",
								pageIndex = target.pageIndex,
								reason = "current-authority-invalid"
							)
						)
						return@currentAuthority
					}
					val mismatch = authorityMismatch(currentInputs, committed)
					if (mismatch != null) {
						finish(
							batch,
							if (mismatch == ReaderPassiveRasterRejection.DestinationCommit) {
								staleDestinationCommitOutcome(target)
							} else ReaderPageRasterBatchOutcome.Failed(
								stage = "passive-hydration-authority",
								pageIndex = target.pageIndex,
								reason = "raster-rejected",
								passiveRasterRejection = mismatch
							)
						)
						return@currentAuthority
					}
					if (!batch.releaseCommittedCapture(committed)) {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "passive-committed-ownership",
								pageIndex = target.pageIndex,
								reason = "committed-capture-unavailable"
							)
						)
						return@currentAuthority
					}
					// Back-fills the alias for rasters already on disk from an earlier capture.
					bundleSource.recordPassiveRasterAlias(plannedDescriptor, realizedDescriptor)
					completeTarget(batch, target)
				}
				is ReaderPassiveRasterManifestResolution.Unavailable -> finish(
					batch,
					resolution.toBatchOutcome(
						stage = "passive-hydration-authority",
						pageIndex = target.pageIndex
					)
				)
				is ReaderPassiveRasterManifestResolution.Failed -> finish(
					batch,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "passive-hydration-authority",
						pageIndex = target.pageIndex,
						reason = resolution.reason
					)
				)
			}
		}
	}

	private fun authorityMismatch(
		inputs: ReaderPassiveRasterManifestInputs,
		committed: ReaderPassiveRasterCommittedCapture<Bitmap>
	): ReaderPassiveRasterRejection? = readerPassiveRasterAuthorityMismatch(
		context = ReaderPassiveRasterAdmissionContext(
			expectedManifestSequence = committed.manifest.manifestSequence,
			currentCaptureEpoch = inputs.canonicalCommit.captureEpoch,
			currentLiveFoliateSessionId = inputs.canonicalCommit.liveFoliateSessionId,
			activePublicationSessionGeneration =
				inputs.canonicalCommit.publicationSessionGeneration,
			currentDestinationCommitToken = inputs.canonicalCommit.destinationCommitToken,
			currentOpaqueCaptureTarget = inputs.opaqueCaptureTarget,
			currentVisualPageOrdinal = inputs.visualPageOrdinal,
			currentRasterProfileKey = inputs.canonicalCommit.rasterProfileKey,
			currentPaginationFingerprint = inputs.canonicalCommit.paginationFingerprint,
			currentLayoutFingerprint = inputs.canonicalCommit.layoutFingerprint,
			currentDecorationFingerprint = inputs.canonicalCommit.decorationFingerprint,
			currentViewportAndCaptureGeometry =
				inputs.canonicalCommit.viewportAndCaptureGeometry,
			currentRasterGeneration = inputs.canonicalCommit.rasterGeneration,
			activePassiveSessionId = session.passiveSessionId,
			expectedPassiveCommitSequence = committed.expectedPassiveCommitSequence,
			currentProfileAuthority = inputs.canonicalCommit.profileAuthority
		),
		manifest = committed.manifest,
		receipt = committed.receipt
	)

	private fun hydrateAliasedTarget(
		batch: Batch,
		targetIndex: Int,
		target: ReaderPageRasterBatchTarget,
		inputs: ReaderPassiveRasterManifestInputs,
		alias: ReaderPassiveRasterAliasLookup.Hit
	) {
		var hydrationCompleted = false
		val hydrationRequest = bundleSource.resolvePassiveRasterTarget(
			pageIndex = target.pageIndex,
			kind = batch.kind,
			reference = batch.reference,
			priority = target.priority,
			rasterDescriptor = alias.realizedDescriptor,
			persistentOnly = true,
			isStillCurrent = { batchIsCurrent(batch) }
		) resolved@{ completion ->
			hydrationCompleted = true
			if (batch.targetIndex == targetIndex) batch.hydration = null
			if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) return@resolved
			when (completion?.result) {
				ReaderPageRasterPublicationResult.Durable ->
					confirmAliasedTargetAuthority(batch, targetIndex, target, inputs)
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
								reason = "capacity-reached",
								persistentPublicationResult = completion.result,
								persistentWriteFailureReason = completion.writeFailureReason
							)
						)
					}
				}
				ReaderPageRasterPublicationResult.Failed -> {
					val writeFailureReason = completion.writeFailureReason
					if (writeFailureReason == null) {
						logAliasMiss(target, ReaderPassiveRasterAliasMiss.HydrationMiss)
						captureTarget(batch, target, inputs)
					} else {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "persistent-publication",
								pageIndex = target.pageIndex,
								reason = "durable-write-failed",
								persistentPublicationResult = completion.result,
								persistentWriteFailureReason = writeFailureReason
							)
						)
					}
				}
				null -> {
					logAliasMiss(target, ReaderPassiveRasterAliasMiss.HydrationMiss)
					captureTarget(batch, target, inputs)
				}
			}
		}
		if (!hydrationCompleted && batchIsCurrent(batch) && batch.targetIndex == targetIndex) {
			batch.hydration = hydrationRequest
		} else {
			hydrationRequest.cancel()
		}
	}

	/**
	 * An alias hit has no receipt of its own: the planned descriptor digest is its invalidation
	 * key, so completion requires the live plan to still be current and to still issue it.
	 */
	private fun confirmAliasedTargetAuthority(
		batch: Batch,
		targetIndex: Int,
		target: ReaderPageRasterBatchTarget,
		inputs: ReaderPassiveRasterManifestInputs
	) {
		liveManifestPort.request(
			visualPageOrdinal = target.pageIndex,
			captureEpoch = captureEpoch,
			rasterGeneration = batch.rasterGeneration,
			preparationGeneration = batch.preparationGeneration
		) currentAuthority@{ resolution ->
			if (!batchIsCurrent(batch) || batch.targetIndex != targetIndex) {
				return@currentAuthority
			}
			when (resolution) {
				is ReaderPassiveRasterManifestResolution.Available -> {
					val currentInputs = resolution.inputs
					if (
						!manifestInputsAreCurrent(batch, target, currentInputs) ||
						currentInputs.rasterDescriptor != inputs.rasterDescriptor
					) {
						finish(
							batch,
							ReaderPageRasterBatchOutcome.Failed(
								stage = "passive-hydration-authority",
								pageIndex = target.pageIndex,
								reason = "current-authority-invalid"
							)
						)
						return@currentAuthority
					}
					Logger.i(
						ReaderPassiveRasterPreparationTag,
						"Page raster alias hydrated page=${target.pageIndex}"
					)
					completeTarget(batch, target)
				}
				is ReaderPassiveRasterManifestResolution.Unavailable -> finish(
					batch,
					resolution.toBatchOutcome(
						stage = "passive-hydration-authority",
						pageIndex = target.pageIndex
					)
				)
				is ReaderPassiveRasterManifestResolution.Failed -> finish(
					batch,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "passive-hydration-authority",
						pageIndex = target.pageIndex,
						reason = resolution.reason
					)
				)
			}
		}
	}

	private fun logAliasMiss(
		target: ReaderPageRasterBatchTarget,
		reason: ReaderPassiveRasterAliasMiss
	) {
		Logger.i(
			ReaderPassiveRasterPreparationTag,
			"Page raster alias miss page=${target.pageIndex} reason=$reason"
		)
	}

	private fun captureCommittedTarget(
		batch: Batch,
		target: ReaderPageRasterBatchTarget,
		committed: ReaderPassiveRasterCommittedCapture<Bitmap>,
		reportHydrationMiss: Boolean
	) {
		if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) {
			if (!batch.releaseCommittedCapture(committed)) committed.release()
			return
		}
		if (!batch.transferCommittedCapture(committed)) {
			committed.release()
			if (batchIsCurrent(batch)) {
				finish(
					batch,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "passive-committed-ownership",
						pageIndex = target.pageIndex,
						reason = "committed-capture-unavailable"
					)
				)
			}
			return
		}
		if (reportHydrationMiss) batch.onHydrationMiss(target)
		if (!committed.capture captured@{ capture ->
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
			if (!isPreparationGenerationCurrent(batch) || !batchIsCurrent(batch)) {
				batch.releasePendingCapture(capture)
				return@currentAuthority
			}
			if (currentInputs == null) {
				batch.releasePendingCapture(capture)
				val outcome = when (resolution) {
					is ReaderPassiveRasterManifestResolution.Unavailable ->
						resolution.toBatchOutcome(
							stage = "passive-admission",
							pageIndex = target.pageIndex
						)
					is ReaderPassiveRasterManifestResolution.Failed ->
						ReaderPageRasterBatchOutcome.Failed(
							stage = "passive-admission",
							pageIndex = target.pageIndex,
							reason = resolution.reason
						)
					is ReaderPassiveRasterManifestResolution.Available ->
						ReaderPageRasterBatchOutcome.Failed(
							stage = "passive-admission",
							pageIndex = target.pageIndex,
							reason = "current-authority-invalid"
						)
				}
				finish(batch, outcome)
				return@currentAuthority
			}
			if (!batch.transferPendingCapture(capture)) {
				capture.raster?.release()
				return@currentAuthority
			}
			var passiveRasterRejection: ReaderPassiveRasterRejection? = null
			var publicationSettled = false
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
				isStillCurrent = { batchIsCurrent(batch) },
				onRejected = { rejection -> passiveRasterRejection = rejection }
			) admitted@{ completion ->
				publicationSettled = true
				if (!batchIsCurrent(batch)) return@admitted
				when (completion.result) {
					ReaderPageRasterPublicationResult.Durable -> {
						if (
							currentInputs.canonicalCommit.profileAuthority ==
							ReaderPassiveRasterProfileAuthority.PassiveRealized
						) {
							bundleSource.recordPassiveRasterAlias(
								plannedDescriptor = currentInputs.rasterDescriptor,
								realizedDescriptor = readerPassiveRasterReceiptAuthoritativeDescriptor(
									descriptor = currentInputs.rasterDescriptor,
									receipt = capture.receipt
								)
							)
						}
						if (batch.inFlightWrite === target) {
							settleOverlappedWrite(batch, target)
						} else {
							completeTarget(batch, target)
						}
					}
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
									reason = "capacity-reached",
									persistentPublicationResult = completion.result,
									persistentWriteFailureReason = completion.writeFailureReason
								)
							)
						}
					}
					ReaderPageRasterPublicationResult.Failed -> finish(
						batch,
						// A concurrent repair re-committed the destination after this capture:
						// the capture is stale, not broken, so retry instead of failing the window.
						if (passiveRasterRejection == ReaderPassiveRasterRejection.DestinationCommit) {
							ReaderPageRasterBatchOutcome.Deferred(
								stage = "passive-admission",
								pageIndex = target.pageIndex,
								reason = "canonical-destination-recommitted"
							)
						} else ReaderPageRasterBatchOutcome.Failed(
							stage = "passive-admission",
							pageIndex = target.pageIndex,
							reason = "raster-rejected-or-publication-failed",
							passiveRasterRejection = passiveRasterRejection,
							persistentPublicationResult = completion.result.takeIf {
								passiveRasterRejection == null
							},
							persistentWriteFailureReason = completion.writeFailureReason
						)
					)
				}
			}
			if (!publicationSettled) overlapWrite(batch, target)
		}
	}

	// The live destination was re-committed after this capture (a repair, or the curl deck of a
	// relayout released early): the capture is stale, not broken, so the window retries.
	private fun staleDestinationCommitOutcome(target: ReaderPageRasterBatchTarget) =
		ReaderPageRasterBatchOutcome.Deferred(
			stage = "passive-admission",
			pageIndex = target.pageIndex,
			reason = "canonical-destination-recommitted"
		)

	private fun completeTarget(batch: Batch, target: ReaderPageRasterBatchTarget) {
		if (!batchIsCurrent(batch)) return
		// Durability is reported in target order: wait for the overlapped earlier write.
		if (batch.inFlightWrite != null) {
			batch.afterInFlightWrite = { completeTarget(batch, target) }
			return
		}
		reportTargetDurable(batch, target)
		batch.targetIndex += 1
		prepareTarget(batch)
	}

	// #78: [target]'s durable write runs off the main thread; render the next target meanwhile.
	// One write overlaps at most, and the batch still counts a target (and reaches Ready) only
	// when its write is durable.
	private fun overlapWrite(batch: Batch, target: ReaderPageRasterBatchTarget) {
		if (
			!batchIsCurrent(batch) ||
			batch.inFlightWrite != null ||
			batch.targets.getOrNull(batch.targetIndex) !== target
		) {
			return
		}
		batch.inFlightWrite = target
		batch.targetIndex += 1
		prepareTarget(batch)
	}

	private fun settleOverlappedWrite(batch: Batch, target: ReaderPageRasterBatchTarget) {
		batch.inFlightWrite = null
		reportTargetDurable(batch, target)
		batch.afterInFlightWrite?.let { next ->
			batch.afterInFlightWrite = null
			next()
		}
	}

	private fun reportTargetDurable(batch: Batch, target: ReaderPageRasterBatchTarget) {
		batch.onTargetDurable(target)
		batch.durableCount += 1
		batch.onProgress(batch.durableCount, batch.targets.size)
	}

	private fun finish(batch: Batch, outcome: ReaderPageRasterBatchOutcome) {
		if (activeBatch !== batch) return
		activeBatch = null
		batch.hydration?.cancel()
		batch.hydration = null
		batch.releaseCommittedCapture()
		batch.releasePendingCapture()
		manifestIssuer.clearCanonicalCommit()
		batch.reference.release()
		batch.onComplete(outcome)
	}

	private fun Batch.releaseCommittedCapture(
		expected: ReaderPassiveRasterCommittedCapture<Bitmap>? = null
	): Boolean {
		val committed = committedCapture ?: return false
		if (expected != null && committed !== expected) return false
		committedCapture = null
		return committed.release()
	}

	private fun Batch.transferCommittedCapture(
		expected: ReaderPassiveRasterCommittedCapture<Bitmap>
	): Boolean {
		if (committedCapture !== expected) return false
		committedCapture = null
		return true
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

	private fun manifestInputsAreCurrent(
		batch: Batch,
		target: ReaderPageRasterBatchTarget,
		inputs: ReaderPassiveRasterManifestInputs
	): Boolean = inputs.visualPageOrdinal == target.pageIndex &&
		inputs.rasterDescriptor.visualPageOrdinal == target.pageIndex &&
		inputs.rasterDescriptor.paginationFingerprint ==
			inputs.canonicalCommit.paginationFingerprint &&
		inputs.rasterDescriptor.layoutFingerprint ==
			inputs.canonicalCommit.layoutFingerprint &&
		inputs.rasterDescriptor.decorationFingerprint ==
			inputs.canonicalCommit.decorationFingerprint &&
		inputs.canonicalCommit.captureEpoch == captureEpoch &&
		inputs.canonicalCommit.rasterGeneration == batch.rasterGeneration

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
	/** #57: the alternate-layout port asks the same live document for the other orientation. */
	private val bridgeMethod: String = "pageTurnPassiveRasterManifestInputs",
	/** Physical size of the document the manifest describes; the live WebView by default. */
	private val measuredSize: (WebView) -> Pair<Int, Int> = { it.width to it.height },
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
			onResolved(
				ReaderPassiveRasterManifestResolution.Unavailable(
					ReaderPassiveRasterManifestUnavailableCause.LiveWebViewDetached
				)
			)
			return
		}
		try {
			webView.evaluateJavascript(
				"JSON.stringify(window.NavicReaderBridge?." +
					"$bridgeMethod?.(" +
					"$visualPageOrdinal, $captureEpoch, $rasterGeneration, " +
						"$preparationGeneration) ?? null)"
			) { encoded ->
				val resolution = readerPassiveRasterManifestResolution(encoded)
				val normalized = when (resolution) {
					is ReaderPassiveRasterManifestResolution.Available -> {
						val (measuredWidth, measuredHeight) = measuredSize(webView)
						val geometry = readerPassiveRasterCanonicalFullFrameGeometry(
							observedGeometry = resolution.inputs.canonicalCommit
								.viewportAndCaptureGeometry,
							measuredWidth = measuredWidth,
							measuredHeight = measuredHeight
						)
						when {
							measuredWidth <= 0 || measuredHeight <= 0 ->
								ReaderPassiveRasterManifestResolution.Unavailable(
									ReaderPassiveRasterManifestUnavailableCause
										.CurrentLiveProfileOrLayoutUnavailable
								)
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
			onResolved(
				ReaderPassiveRasterManifestResolution.Unavailable(
					ReaderPassiveRasterManifestUnavailableCause.BridgeRequestUnavailable
				)
			)
		}
	}
}

internal fun readerPassiveRasterManifestResolution(
	encoded: String?
): ReaderPassiveRasterManifestResolution {
	val bridgeUnavailable = ReaderPassiveRasterManifestResolution.Unavailable(
		ReaderPassiveRasterManifestUnavailableCause.BridgeRequestUnavailable
	)
	val jsonText = runCatching {
		JSONTokener(encoded).nextValue() as? String
	}.getOrNull() ?: return bridgeUnavailable
	if (jsonText == "null") return bridgeUnavailable
	val json = runCatching { JSONObject(jsonText) }.getOrNull()
		?: return ReaderPassiveRasterManifestResolution.Failed("manifest-invalid")
	return when (json.optString("failureReason").takeIf(String::isNotBlank)) {
		"canonical-rendered-destination-absent" ->
			ReaderPassiveRasterManifestResolution.Unavailable(
				ReaderPassiveRasterManifestUnavailableCause
					.CanonicalRenderedDestinationAbsent
			)
		"current-live-profile-or-layout-unavailable" ->
			ReaderPassiveRasterManifestResolution.Unavailable(
				ReaderPassiveRasterManifestUnavailableCause
					.CurrentLiveProfileOrLayoutUnavailable
			)
		"manifest-request-invalid" ->
			ReaderPassiveRasterManifestResolution.Failed("manifest-request-invalid")
		null -> readerPassiveRasterManifestInputs(encoded)
			?.let(ReaderPassiveRasterManifestResolution::Available)
			?: ReaderPassiveRasterManifestResolution.Failed("manifest-invalid")
		else -> ReaderPassiveRasterManifestResolution.Failed("manifest-invalid")
	}
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
