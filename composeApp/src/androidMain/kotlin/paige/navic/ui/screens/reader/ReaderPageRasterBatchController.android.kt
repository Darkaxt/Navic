package paige.navic.ui.screens.reader

import android.view.View
import android.webkit.WebView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import paige.navic.reader.ReaderPageAdjacentChapterDirection
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.ReaderPageTurnCaptureGeometry
import paige.navic.reader.adjacentChapterDirection

internal data class ReaderPageRasterBatchTarget(
	val pageIndex: Int,
	val priority: ReaderPageRasterPriority
)

internal data class ReaderPageRasterPreparationPlan(
	val centerPageIndex: Int,
	val pageCount: Int,
	val layoutMode: String,
	val readerDirection: ReaderPlayLikeCurlReaderDirection,
	val step: Int,
	val currentChapterIndex: Int,
	val currentChapterPageStartIndex: Int,
	val currentChapterPageCount: Int,
	val previousChapterPageStartIndex: Int,
	val previousChapterPageCount: Int,
	val nextChapterPageStartIndex: Int,
	val nextChapterPageCount: Int,
	val targets: List<ReaderPageRasterBatchTarget>,
	val captureGeometry: ReaderPageTurnCaptureGeometry? = null
)

internal data class ReaderPageRasterPreparedChapterRange(
	val startPageIndex: Int,
	val pageCount: Int
) {
	val endPageIndexExclusive: Int = startPageIndex + pageCount

	fun contains(pageIndex: Int): Boolean =
		pageCount > 0 && pageIndex in startPageIndex until endPageIndexExclusive
}

internal fun ReaderPageRasterPreparationPlan.preparedChapterRange(): ReaderPageRasterPreparedChapterRange? =
	currentChapterPageStartIndex
		.takeIf { start -> start >= 0 && currentChapterPageCount > 0 }
		?.let { start -> ReaderPageRasterPreparedChapterRange(start, currentChapterPageCount) }

internal fun ReaderPageRasterPreparationPlan.preparedRepairPageIndices(): Set<Int> =
	targets
		.filter { target ->
			target.priority.rank <= ReaderPageRasterPriority.CurrentChapter.rank
		}
		.mapTo(linkedSetOf()) { target -> target.pageIndex }

internal fun readerPageRasterRepairPageIndices(
	preparedPageIndices: Set<Int>,
	protectedSourcePageIndices: Set<Int>,
	pageCount: Int
): Set<Int> = preparedPageIndices.toCollection(linkedSetOf()).apply {
	addAll(protectedSourcePageIndices.filter { pageIndex -> pageIndex in 0 until pageCount })
}

internal const val ReaderPageRasterBlockingRadius = 5
private const val ReaderPageRasterCancellationRestorationTimeoutMillis = 10_000L

internal enum class ReaderPageRasterCancellationRestoration(
	val canRevealContent: Boolean
) {
	Restored(canRevealContent = true),
	Detached(canRevealContent = true),
	TimedOut(canRevealContent = false)
}

internal fun readerPageRasterBlockingWindow(
	centerPageIndex: Int,
	step: Int,
	pageCount: Int
): List<Int> {
	if (centerPageIndex !in 0 until pageCount || step <= 0) return emptyList()
	return buildList {
		add(centerPageIndex)
		listOf(1, -1).forEach { offset ->
			(centerPageIndex + offset * step)
				.takeIf { pageIndex -> pageIndex in 0 until pageCount }
				?.let(::add)
		}
		(2..ReaderPageRasterBlockingRadius).forEach { offset ->
			(centerPageIndex + offset * step)
				.takeIf { pageIndex -> pageIndex in 0 until pageCount }
				?.let(::add)
		}
		(2..ReaderPageRasterBlockingRadius).forEach { offset ->
			(centerPageIndex - offset * step)
				.takeIf { pageIndex -> pageIndex in 0 until pageCount }
				?.let(::add)
		}
	}
}

internal fun ReaderPageRasterPreparationPlan.blockingTargetsOrNull():
	List<ReaderPageRasterBatchTarget>? {
	val blocking = readerPageRasterBlockingTargets(targets)
	val expected = readerPageRasterBlockingWindow(
		centerPageIndex = centerPageIndex,
		step = step,
		pageCount = pageCount
	)
	return blocking.takeIf { candidates ->
		candidates.map { target -> target.pageIndex } == expected
	}
}

internal fun ReaderPageRasterPreparationPlan.adjacentChapterPrefetchChapters():
	List<ReaderPageAdjacentChapterPrefetchChapter> =
		ReaderPageAdjacentChapterDirection.entries.mapNotNull { direction ->
			val currentEnd = currentChapterPageStartIndex + currentChapterPageCount
			val identity = when (direction) {
				ReaderPageAdjacentChapterDirection.Current -> ReaderPageAdjacentChapterIdentity(
					direction = direction,
					chapterIndex = currentChapterIndex,
					pageStartIndex = currentChapterPageStartIndex,
					pageCount = currentChapterPageCount
				)
				ReaderPageAdjacentChapterDirection.Next -> ReaderPageAdjacentChapterIdentity(
					direction = direction,
					chapterIndex = currentChapterIndex + 1,
					pageStartIndex = currentEnd,
					pageCount = (pageCount - currentEnd).coerceAtLeast(0)
				)
				ReaderPageAdjacentChapterDirection.Previous -> ReaderPageAdjacentChapterIdentity(
					direction = direction,
					chapterIndex = (currentChapterIndex - 1).coerceAtLeast(0),
					pageStartIndex = 0,
					pageCount = currentChapterPageStartIndex.coerceAtLeast(0)
				)
			}
			val chapterTargets = targets.filter { target ->
				target.priority.adjacentChapterDirection == direction &&
					identity.contains(target.pageIndex)
			}
			if (
				currentChapterIndex >= 0 &&
					identity.chapterIndex >= 0 &&
					identity.pageStartIndex >= 0 &&
					identity.pageCount > 0 &&
					chapterTargets.isNotEmpty()
			) {
				ReaderPageAdjacentChapterPrefetchChapter(identity, chapterTargets)
			} else {
				null
			}
		}

internal fun readerPageRasterPreparationPlan(encoded: String?): ReaderPageRasterPreparationPlan? {
	val raw = encoded.orEmpty().trim()
	val root = runCatching {
		val firstPass = Json.parseToJsonElement(raw)
		if (raw.startsWith('"')) {
			Json.parseToJsonElement(firstPass.jsonPrimitive.contentOrNull.orEmpty()).jsonObject
		} else firstPass.jsonObject
	}.getOrNull() ?: return null
	val context = root["context"]?.jsonObject ?: return null
	val centerPageIndex = context["centerPageIndex"]?.jsonPrimitive?.intOrNull ?: -1
	val pageCount = context["pageCount"]?.jsonPrimitive?.intOrNull ?: 0
	val layoutMode = context["layoutMode"]?.jsonPrimitive?.contentOrNull.orEmpty()
	if (centerPageIndex < 0 || pageCount <= 0 || layoutMode.isBlank()) return null
	val targetsJson = root["targets"]?.jsonArray.orEmpty()
	val targets = buildList {
		for (element in targetsJson) {
			val item = runCatching { element.jsonObject }.getOrNull() ?: continue
			val pageIndex = item["pageIndex"]?.jsonPrimitive?.intOrNull ?: -1
			val priority = readerPageRasterPriority(
				item["priority"]?.jsonPrimitive?.contentOrNull.orEmpty()
			) ?: continue
			if (pageIndex >= 0) add(ReaderPageRasterBatchTarget(pageIndex, priority))
		}
	}
	return ReaderPageRasterPreparationPlan(
		centerPageIndex = centerPageIndex,
		pageCount = pageCount,
		layoutMode = layoutMode,
		readerDirection = when (
			context["readerDirection"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
		) {
			"rtl" -> ReaderPlayLikeCurlReaderDirection.Rtl
			else -> ReaderPlayLikeCurlReaderDirection.Ltr
		},
		step = (context["step"]?.jsonPrimitive?.intOrNull ?: 1).coerceAtLeast(1),
		currentChapterIndex =
			context["currentChapterIndex"]?.jsonPrimitive?.intOrNull ?: -1,
		currentChapterPageStartIndex =
			context["currentChapterPageStartIndex"]?.jsonPrimitive?.intOrNull ?: -1,
		currentChapterPageCount =
			(context["currentChapterPageCount"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
		previousChapterPageStartIndex =
			context["previousChapterPageStartIndex"]?.jsonPrimitive?.intOrNull ?: -1,
		previousChapterPageCount =
			(context["previousChapterPageCount"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
		nextChapterPageStartIndex =
			context["nextChapterPageStartIndex"]?.jsonPrimitive?.intOrNull ?: -1,
		nextChapterPageCount =
			(context["nextChapterPageCount"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
		targets = targets,
		captureGeometry = root["captureGeometry"]?.let { encodedGeometry ->
			readerPageTurnCaptureGeometry(encodedGeometry.toString())
		}
	)
}

internal fun readerPageRasterCalibrationTargets(
	targets: List<ReaderPageRasterBatchTarget>
): List<ReaderPageRasterBatchTarget> = targets
	.filter { target -> target.priority.rank <= ReaderPageRasterPriority.PreviousTransition.rank }
	.take(3)

internal fun readerPageRasterBlockingTargets(
	targets: List<ReaderPageRasterBatchTarget>
): List<ReaderPageRasterBatchTarget> = targets.filter { target ->
	target.priority.rank <= ReaderPageRasterPriority.PreviousLookahead.rank
}

internal fun readerPageRasterBackgroundTargets(
	targets: List<ReaderPageRasterBatchTarget>
): List<ReaderPageRasterBatchTarget> = targets.filter { target ->
	target.priority.rank > ReaderPageRasterPriority.PreviousLookahead.rank
}

private fun readerPageRasterPriority(value: String): ReaderPageRasterPriority? = when (value) {
	"current" -> ReaderPageRasterPriority.Current
	"next-transition" -> ReaderPageRasterPriority.NextTransition
	"previous-transition" -> ReaderPageRasterPriority.PreviousTransition
	"next-lookahead" -> ReaderPageRasterPriority.NextLookahead
	"previous-lookahead" -> ReaderPageRasterPriority.PreviousLookahead
	"current-chapter" -> ReaderPageRasterPriority.CurrentChapter
	"next-chapter" -> ReaderPageRasterPriority.NextChapter
	"previous-chapter" -> ReaderPageRasterPriority.PreviousChapter
	"next-chapter-remainder" -> ReaderPageRasterPriority.NextChapterRemainder
	"previous-chapter-remainder" -> ReaderPageRasterPriority.PreviousChapterRemainder
	else -> null
}

internal fun readerPageRasterImmediateTargets(
	currentPageIndex: Int,
	nextPageIndex: Int?,
	previousPageIndex: Int?
): List<ReaderPageRasterBatchTarget> = buildList {
	add(ReaderPageRasterBatchTarget(currentPageIndex, ReaderPageRasterPriority.Current))
	nextPageIndex?.let { add(ReaderPageRasterBatchTarget(it, ReaderPageRasterPriority.NextTransition)) }
	previousPageIndex?.let { add(ReaderPageRasterBatchTarget(it, ReaderPageRasterPriority.PreviousTransition)) }
}

internal sealed interface ReaderPageRasterBatchOutcome {
	data object Ready : ReaderPageRasterBatchOutcome
	data object Cancelled : ReaderPageRasterBatchOutcome

	data class Deferred(
		val stage: String,
		val pageIndex: Int?,
		val reason: String
	) : ReaderPageRasterBatchOutcome

	data class Failed(
		val stage: String,
		val pageIndex: Int?,
		val reason: String
	) : ReaderPageRasterBatchOutcome {
		val diagnostic: String
			get() = "stage=$stage pageIndex=${pageIndex ?: "none"} reason=$reason"

		val userMessage: String
			get() = pageIndex?.let { index ->
				"Page ${index + 1} could not be prepared: $reason"
			} ?: "Page preparation failed: $reason"
	}
}

internal fun readerPageRasterPreviewOutcome(
	status: String,
	pageIndex: Int?,
	message: String?,
	paginationReady: Boolean = false
): ReaderPageRasterBatchOutcome {
	val normalizedPageIndex = pageIndex?.takeIf { it >= 0 }
	val reason = message?.trim().takeUnless { it.isNullOrBlank() }
	return when (status) {
		"cancelled" -> ReaderPageRasterBatchOutcome.Cancelled
		"missing" -> ReaderPageRasterBatchOutcome.Deferred(
			stage = "preview-state",
			pageIndex = normalizedPageIndex,
			reason = "preview-state-missing"
		)
		"failed" -> {
			val failureReason = reason ?: "preview-render-failed"
			if (
				failureReason.startsWith("Passive raster page ") &&
				failureReason.endsWith(" is unavailable") &&
				!paginationReady
			) {
				ReaderPageRasterBatchOutcome.Deferred(
					stage = "preview-render",
					pageIndex = normalizedPageIndex,
					reason = failureReason
				)
			} else {
				ReaderPageRasterBatchOutcome.Failed(
					stage = "preview-render",
					pageIndex = normalizedPageIndex,
					reason = failureReason
				)
			}
		}
		else -> ReaderPageRasterBatchOutcome.Failed(
			stage = "preview-state",
			pageIndex = normalizedPageIndex,
			reason = reason ?: "unexpected-status-$status"
		)
	}
}

internal interface ReaderPageRasterBatchPort {
	fun start(
		webView: WebView,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		trigger: ReaderPageRasterAcquisitionTrigger =
			ReaderPageRasterAcquisitionTrigger.InitialPreparation,
		onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit = {},
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit = {},
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit = {},
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit = { _, _ -> },
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean

	fun resetRetryState()

	fun restoreLiveComposition(
		webView: WebView,
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit
	)

	fun cancel(
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit = { _ -> }
	)
}

/**
 * Owns one passive raster preparation session. Disk reads and hidden preview captures are deliberately
 * serialized so the passive Foliate renderer is never asked to represent two pages at once.
 */
internal class ReaderPageRasterBatchController(
	private val bundleSource: ReaderPageTurnBundleSource,
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null
) : ReaderPageRasterBatchPort {
	private data class RetryState(
		val generation: Long,
		val originalPageIndices: Set<Int>,
		val retryPageIndices: Set<Int>
	)

	private data class Session(
		val token: String,
		val generation: Long,
		val webView: WebView,
		val kind: ReaderPageTurnTransitionKind,
		val reference: ReaderPageSlideSnapshot,
		val targets: List<ReaderPageRasterBatchTarget>,
		val trigger: ReaderPageRasterAcquisitionTrigger,
		val originalPageIndices: Set<Int>,
		val progressCompletedOffset: Int,
		val progressRequiredCount: Int,
		val onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
		val onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		val onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		val onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		val onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		val onComplete: (ReaderPageRasterBatchOutcome) -> Unit,
		val missingTargets: MutableList<ReaderPageRasterBatchTarget> =
			mutableListOf(),
		var completedCount: Int = 0,
		var hydrationToken: Long = 0L,
		var hydrationRequest: ReaderPageRasterHydrationRequest? = null,
		val acquisitionOperations:
			MutableMap<Int, ReaderPageDiagnosticOperation> = mutableMapOf(),
		val activeAcquisitionSources:
			MutableMap<Int, ReaderPageRasterAcquisitionSource> = mutableMapOf()
	) {
		val requiredCount: Int get() = targets.size
		val durabilityGate = ReaderPageRasterDurabilityGate(
			targets.mapTo(linkedSetOf()) { target -> target.pageIndex }
		)
	}

	private var nextSessionId = 0L
	private var nextCancellationVisualStateId = 0L
	private var activeSession: Session? = null
	private var retryState: RetryState? = null

	override fun start(
		webView: WebView,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		trigger: ReaderPageRasterAcquisitionTrigger,
		onStagingStarted: (ReaderPageSlideSnapshot, (Boolean) -> Unit) -> Unit,
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		onHydrationMiss: (ReaderPageRasterBatchTarget) -> Unit,
		onTargetDurable: (ReaderPageRasterBatchTarget) -> Unit,
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		onComplete: (ReaderPageRasterBatchOutcome) -> Unit
	): Boolean {
		if (!webView.isAttachedToWindow || targets.isEmpty()) {
			reference.release()
			onComplete(
				if (targets.isEmpty()) ReaderPageRasterBatchOutcome.Ready
				else ReaderPageRasterBatchOutcome.Deferred(
					stage = "batch-start",
					pageIndex = targets.firstOrNull()?.pageIndex,
					reason = "webview-detached"
				)
			)
			return targets.isEmpty()
		}
		cancel()
		val distinctTargets = linkedMapOf<Int, ReaderPageRasterBatchTarget>()
		targets.forEach { target ->
			val existing = distinctTargets[target.pageIndex]
			if (existing == null || target.priority.rank < existing.priority.rank) {
				distinctTargets[target.pageIndex] = target
			}
		}
		val generation = bundleSource.currentGeneration()
		if (retryState?.generation != generation) retryState = null
		val originalPageIndices = distinctTargets.keys.toSet()
		val retry = retryState?.takeIf { candidate ->
			candidate.generation == generation &&
				candidate.originalPageIndices == originalPageIndices
		}
		val sessionTargets = retry?.let { candidate ->
			distinctTargets.values.filter { target ->
				target.pageIndex in candidate.retryPageIndices
			}
		} ?: distinctTargets.values.toList()
		if (retry != null && sessionTargets.isEmpty()) {
			retryState = null
			reference.release()
			onProgress(originalPageIndices.size, originalPageIndices.size)
			onComplete(ReaderPageRasterBatchOutcome.Ready)
			return true
		}
		val progressOffset = originalPageIndices.size - sessionTargets.size
		val session = Session(
			token = "navic-page-raster-batch-${++nextSessionId}",
			generation = generation,
			webView = webView,
			kind = kind,
			reference = reference,
			targets = sessionTargets,
			trigger = trigger,
			originalPageIndices = originalPageIndices,
			progressCompletedOffset = progressOffset,
			progressRequiredCount = originalPageIndices.size,
			onStagingStarted = onStagingStarted,
			onActiveTarget = onActiveTarget,
			onHydrationMiss = onHydrationMiss,
			onTargetDurable = onTargetDurable,
			onProgress = onProgress,
			onComplete = onComplete
		)
		activeSession = session
		session.onProgress(
			session.progressCompletedOffset,
			session.progressRequiredCount
		)
		hydrateTarget(session, 0)
		return true
	}

	override fun resetRetryState() {
		retryState = null
	}

	override fun restoreLiveComposition(
		webView: WebView,
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit
	) {
		requestVisualRestoration(
			webView = webView,
			javascript = "(() => {" +
				"const bridge = window.NavicReaderBridge;" +
				"bridge?.cancelPageTurnPreviewBatch?.();" +
				"bridge?.restorePageTurnLiveComposition?.();" +
				"})()",
			onRestorationFinished = onRestorationFinished
		)
	}

	override fun cancel(
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit
	) {
		val session = activeSession ?: run {
			onRestorationFinished(ReaderPageRasterCancellationRestoration.Restored)
			return
		}
		activeSession = null
		cancelAcquisitions(session)
		session.hydrationToken += 1L
		session.hydrationRequest?.cancel()
		session.hydrationRequest = null
		requestVisualRestoration(
			webView = session.webView,
			javascript = "window.NavicReaderBridge?.cancelPageTurnPreviewBatch?.(" +
				"${JSONObject.quote(session.token)})",
			onRestorationFinished = onRestorationFinished
		)
		session.reference.release()
		session.onComplete(ReaderPageRasterBatchOutcome.Cancelled)
	}

	private fun requestVisualRestoration(
		webView: WebView,
		javascript: String,
		onRestorationFinished: (ReaderPageRasterCancellationRestoration) -> Unit
	) {
		if (!webView.isAttachedToWindow) {
			onRestorationFinished(ReaderPageRasterCancellationRestoration.Detached)
			return
		}
		var restorationCompleted = false
		lateinit var restorationTimeout: Runnable
		lateinit var attachmentListener: View.OnAttachStateChangeListener

		fun completeRestoration(
			restoration: ReaderPageRasterCancellationRestoration
		) {
			if (restorationCompleted) return
			restorationCompleted = true
			webView.removeCallbacks(restorationTimeout)
			webView.removeOnAttachStateChangeListener(attachmentListener)
			onRestorationFinished(restoration)
		}

		restorationTimeout = Runnable {
			completeRestoration(ReaderPageRasterCancellationRestoration.TimedOut)
		}
		attachmentListener = object : View.OnAttachStateChangeListener {
			override fun onViewAttachedToWindow(view: View) = Unit

			override fun onViewDetachedFromWindow(view: View) {
				completeRestoration(ReaderPageRasterCancellationRestoration.Detached)
			}
		}
		webView.addOnAttachStateChangeListener(attachmentListener)
		webView.postDelayed(
			restorationTimeout,
			ReaderPageRasterCancellationRestorationTimeoutMillis
		)
		webView.evaluateJavascript(javascript) {
			if (!webView.isAttachedToWindow) {
				completeRestoration(ReaderPageRasterCancellationRestoration.Detached)
				return@evaluateJavascript
			}
			val visualStateId = Math.incrementExact(nextCancellationVisualStateId).also {
				nextCancellationVisualStateId = it
			}
			webView.postVisualStateCallback(
				visualStateId,
				object : WebView.VisualStateCallback() {
					override fun onComplete(requestId: Long) {
						webView.postOnAnimation {
							completeRestoration(
								ReaderPageRasterCancellationRestoration.Restored
							)
						}
					}
				}
			)
		}
	}

	private fun startAcquisition(
		session: Session,
		target: ReaderPageRasterBatchTarget,
		source: ReaderPageRasterAcquisitionSource
	) {
		val runtime = diagnostics ?: return
		val operation = session.acquisitionOperations.getOrPut(target.pageIndex) {
			runtime.startOperation(session.generation, target.pageIndex)
		}
		session.activeAcquisitionSources[target.pageIndex] = source
		runtime.rasterAcquisition(
			operation = operation,
			source = source,
			trigger = session.trigger,
			result = ReaderPageRasterAcquisitionResult.Started
		)
	}

	private fun finishAcquisition(
		session: Session,
		pageIndex: Int,
		result: ReaderPageRasterAcquisitionResult
	) {
		val runtime = diagnostics ?: return
		val source = session.activeAcquisitionSources.remove(pageIndex) ?: return
		val operation = session.acquisitionOperations[pageIndex] ?: return
		runtime.rasterAcquisition(
			operation = operation,
			source = source,
			trigger = session.trigger,
			result = result
		)
	}

	private fun cancelAcquisitions(session: Session) {
		session.activeAcquisitionSources.keys.toList().forEach { pageIndex ->
			finishAcquisition(
				session,
				pageIndex,
				ReaderPageRasterAcquisitionResult.Cancelled
			)
		}
	}

	private fun hydrateTarget(session: Session, targetIndex: Int) {
		if (!isSessionActive(session)) return
		if (targetIndex >= session.targets.size) {
			if (session.missingTargets.isEmpty()) {
				finish(session, ReaderPageRasterBatchOutcome.Ready)
			}
			else submitMissingTargets(session)
			return
		}
		val target = session.targets[targetIndex]
		session.onActiveTarget(target)
		startAcquisition(
			session,
			target,
			ReaderPageRasterAcquisitionSource.PersistentHydration
		)
		val hydrationToken = ++session.hydrationToken
		val hydrationRequest = bundleSource.hydrateSnapshot(
			webView = session.webView,
			pageIndex = target.pageIndex,
			kind = session.kind,
			reference = session.reference
		) { hydrated ->
			if (session.hydrationToken == hydrationToken) {
				session.hydrationRequest = null
			}
			if (!isSessionActive(session)) {
				finishAcquisition(
					session,
					target.pageIndex,
					if (session.generation == bundleSource.currentGeneration()) {
						ReaderPageRasterAcquisitionResult.Cancelled
					} else {
						ReaderPageRasterAcquisitionResult.Stale
					}
				)
				hydrated?.release()
				return@hydrateSnapshot
			}
			if (hydrated == null) {
				finishAcquisition(
					session,
					target.pageIndex,
					ReaderPageRasterAcquisitionResult.Miss
				)
				session.onHydrationMiss(target)
				session.missingTargets += target
				hydrateTarget(session, targetIndex + 1)
				return@hydrateSnapshot
			}
			finishAcquisition(
				session,
				target.pageIndex,
				ReaderPageRasterAcquisitionResult.Hit
			)
			bundleSource.ensurePersistentSnapshot(
				hydrated,
				target.priority
			) { persisted ->
				hydrated.release()
				if (!isSessionActive(session)) {
					return@ensurePersistentSnapshot
				}
				if (!recordDurability(session, target, persisted)) {
					return@ensurePersistentSnapshot
				}
				hydrateTarget(session, targetIndex + 1)
			}
		}
		if (
			session.hydrationToken == hydrationToken &&
			isSessionActive(session)
		) {
			session.hydrationRequest = hydrationRequest
		} else {
			hydrationRequest.cancel()
		}
	}

	private fun submitMissingTargets(session: Session) = beginPageTurnPreviewBatch(session)

	private fun beginPageTurnPreviewBatch(session: Session) {
		if (!isSessionActive(session)) return
		val pageIndexes = JSONArray(session.missingTargets.map { target -> target.pageIndex })
		session.webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.beginPageTurnPreviewBatch?.(" +
				"${JSONObject.quote(session.token)}, $pageIndexes) ?? null)"
		) {
			if (!isSessionActive(session)) return@evaluateJavascript
			session.webView.postOnAnimation { pollBatchState(session) }
		}
	}

	private fun pollBatchState(session: Session) {
		if (!isSessionActive(session)) return
		session.webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnPreviewBatchState?.(" +
				"${JSONObject.quote(session.token)}) ?? null)"
		) { encodedState ->
			if (!isSessionActive(session)) return@evaluateJavascript
			val state = encodedState.javascriptObject()
			when (state?.optString("status")) {
				"ready" -> captureReadyItem(session, state)
				"complete" -> {
					val outcome = if (session.completedCount == session.requiredCount) {
						ReaderPageRasterBatchOutcome.Ready
					} else {
						ReaderPageRasterBatchOutcome.Failed(
							stage = "batch-completion",
							pageIndex = session.missingTargets.firstOrNull()?.pageIndex,
							reason = "completed-${session.completedCount}-of-${session.requiredCount}"
						)
					}
					finish(session, outcome)
				}
				"failed", "missing", "cancelled" -> finish(
					session,
					readerPageRasterPreviewOutcome(
						status = state.optString("status"),
						pageIndex = state.optInt("pageIndex", -1),
						message = state.optString("message").takeIf { it.isNotBlank() },
						paginationReady = state.optBoolean("paginationReady", false)
					)
				)
				else -> session.webView.postOnAnimation { pollBatchState(session) }
			}
		}
	}

	private fun captureReadyItem(session: Session, state: JSONObject) {
		if (!isSessionActive(session)) return
		val pageIndex = state.optInt("pageIndex", -1)
		val itemToken = state.optString("itemToken")
		val previewGeneration = state.optLong("generation", -1L)
		val target = session.missingTargets.firstOrNull { candidate -> candidate.pageIndex == pageIndex }
		if (
			pageIndex < 0 ||
			itemToken.isBlank() ||
			previewGeneration !in 0L..ReaderPageTurnPresentationMaximumSafeInteger ||
			target == null
		) {
			finish(
				session,
				ReaderPageRasterBatchOutcome.Failed(
					stage = "preview-ready-state",
					pageIndex = pageIndex,
					reason = "malformed-ready-item"
				)
			)
			return
		}
		session.onActiveTarget(target)
		startAcquisition(
			session,
			target,
			ReaderPageRasterAcquisitionSource.WebViewCapture
		)
		bundleSource.capturePreparedRasterPage(
			webView = session.webView,
			pageIndex = pageIndex,
			kind = session.kind,
			reference = session.reference,
			itemToken = itemToken,
			previewGeneration = previewGeneration,
			priority = target.priority,
			isStillCurrent = { isSessionActive(session) },
			onStagingStarted = session.onStagingStarted,
			onCaptureFailed = captureFailed@{
				if (!isSessionActive(session)) {
					finishAcquisition(
						session,
						pageIndex,
						inactiveAcquisitionResult(session)
					)
					return@captureFailed
				}
				repollInvalidatedBatchItem(session, pageIndex, previewGeneration) {
					finishAcquisition(
						session,
						pageIndex,
						ReaderPageRasterAcquisitionResult.Failed
					)
					finish(
						session,
						ReaderPageRasterBatchOutcome.Failed(
							stage = "preview-capture",
							pageIndex = pageIndex,
							reason = "prepared-raster-capture-failed"
						)
					)
				}
			},
			onCaptured = captured@{ persisted ->
				if (!isSessionActive(session)) {
					finishAcquisition(
						session,
						pageIndex,
						inactiveAcquisitionResult(session)
					)
					return@captured
				}
				finishAcquisition(
					session,
					pageIndex,
					if (persisted) ReaderPageRasterAcquisitionResult.Durable
					else ReaderPageRasterAcquisitionResult.Failed
				)
				if (!recordDurability(session, target, persisted)) {
					return@captured
				}
				session.missingTargets.remove(target)
				advancePageTurnPreviewBatch(session, pageIndex)
			}
		)
	}

	private fun repollInvalidatedBatchItem(
		session: Session,
		pageIndex: Int,
		previewGeneration: Long,
		onNotRestarted: () -> Unit
	) {
		if (!isSessionActive(session)) return
		session.webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.pageTurnPreviewBatchState?.(" +
				"${JSONObject.quote(session.token)}) ?? null)"
		) { encodedState ->
			if (!isSessionActive(session)) {
				finishAcquisition(
					session,
					pageIndex,
					inactiveAcquisitionResult(session)
				)
				return@evaluateJavascript
			}
			val state = encodedState.javascriptObject()
			val restarted =
				state != null &&
					state.optString("status") in setOf("preparing", "ready") &&
					state.optInt("pageIndex", -1) == pageIndex &&
					state.optLong("generation", -1L) > previewGeneration
			if (!restarted) {
				onNotRestarted()
				return@evaluateJavascript
			}
			finishAcquisition(
				session,
				pageIndex,
				ReaderPageRasterAcquisitionResult.Cancelled
			)
			session.webView.postOnAnimation { pollBatchState(session) }
		}
	}

	private fun advancePageTurnPreviewBatch(session: Session, pageIndex: Int) {
		if (!isSessionActive(session)) return
		session.webView.evaluateJavascript(
			"JSON.stringify(window.NavicReaderBridge?.advancePageTurnPreviewBatch?.(" +
				"${JSONObject.quote(session.token)}, $pageIndex) ?? null)"
		) {
			if (!isSessionActive(session)) return@evaluateJavascript
			session.webView.postOnAnimation { pollBatchState(session) }
		}
	}

	private fun recordDurability(
		session: Session,
		target: ReaderPageRasterBatchTarget,
		persisted: Boolean
	): Boolean {
		val completed = when (
			val decision = session.durabilityGate.record(
				pageIndex = target.pageIndex,
				persisted = persisted
			)
		) {
			is ReaderPageRasterDurabilityDecision.Continue -> decision.completed
			ReaderPageRasterDurabilityDecision.Ready -> session.requiredCount
			is ReaderPageRasterDurabilityDecision.Failed -> {
				finish(
					session,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "persistent-publication",
						pageIndex = decision.pageIndex,
						reason = "durable-write-failed"
					)
				)
				return false
			}
		}
		session.completedCount = completed
		session.onTargetDurable(target)
		session.onProgress(
			session.progressCompletedOffset + completed,
			session.progressRequiredCount
		)
		return true
	}

	private fun inactiveAcquisitionResult(
		session: Session
	): ReaderPageRasterAcquisitionResult =
		if (session.generation == bundleSource.currentGeneration()) {
			ReaderPageRasterAcquisitionResult.Cancelled
		} else {
			ReaderPageRasterAcquisitionResult.Stale
		}

	private fun isSessionActive(session: Session): Boolean =
		activeSession === session &&
			session.generation == bundleSource.currentGeneration() &&
			session.webView.isAttachedToWindow

	private fun finish(session: Session, outcome: ReaderPageRasterBatchOutcome) {
		if (activeSession !== session) return
		when (outcome) {
			is ReaderPageRasterBatchOutcome.Failed -> {
				retryState = RetryState(
					generation = session.generation,
					originalPageIndices = session.originalPageIndices,
					retryPageIndices = session.durabilityGate.retryPageIndices()
				)
			}
			ReaderPageRasterBatchOutcome.Ready -> {
				retryState?.takeIf { state ->
					state.generation == session.generation &&
						state.originalPageIndices == session.originalPageIndices
				}?.let { retryState = null }
			}
			ReaderPageRasterBatchOutcome.Cancelled,
			is ReaderPageRasterBatchOutcome.Deferred -> Unit
		}
		activeSession = null
		session.reference.release()
		session.onComplete(outcome)
	}
}

private fun String?.javascriptObject(): JSONObject? = runCatching {
	val raw = orEmpty().trim()
	runCatching { JSONObject(raw) }.getOrElse {
		val decoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching null
		JSONObject(decoded)
	}
}.getOrNull()
