package paige.navic.ui.screens.reader

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
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.util.core.Logger

private const val ReaderPageRasterBatchTag = "ReaderPageRasterBatch"

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
	val currentChapterPageCount: Int,
	val targets: List<ReaderPageRasterBatchTarget>
)

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
		currentChapterPageCount =
			(context["currentChapterPageCount"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
		targets = targets
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
	target.priority.rank <= ReaderPageRasterPriority.CurrentChapter.rank
}

internal fun readerPageRasterBackgroundTargets(
	targets: List<ReaderPageRasterBatchTarget>
): List<ReaderPageRasterBatchTarget> = targets.filter { target ->
	target.priority.rank > ReaderPageRasterPriority.CurrentChapter.rank
}

private fun readerPageRasterPriority(value: String): ReaderPageRasterPriority? = when (value) {
	"current" -> ReaderPageRasterPriority.Current
	"next-transition" -> ReaderPageRasterPriority.NextTransition
	"previous-transition" -> ReaderPageRasterPriority.PreviousTransition
	"current-chapter" -> ReaderPageRasterPriority.CurrentChapter
	"next-chapter" -> ReaderPageRasterPriority.NextChapter
	"previous-chapter" -> ReaderPageRasterPriority.PreviousChapter
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

/**
 * Owns one passive raster preparation session. Disk reads and hidden preview captures are deliberately
 * serialized so the passive Foliate renderer is never asked to represent two pages at once.
 */
internal class ReaderPageRasterBatchController(
	private val bundleSource: ReaderPageTurnBundleSource
) {
	private data class Session(
		val token: String,
		val generation: Long,
		val webView: WebView,
		val kind: ReaderPageTurnTransitionKind,
		val reference: ReaderPageSlideSnapshot,
		val targets: List<ReaderPageRasterBatchTarget>,
		val onStagingStarted: (ReaderPageSlideSnapshot) -> Unit,
		val onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit,
		val onProgress: (completedCount: Int, requiredCount: Int) -> Unit,
		val onComplete: (ReaderPageRasterBatchOutcome) -> Unit,
		val missingTargets: MutableList<ReaderPageRasterBatchTarget> = mutableListOf(),
		var completedCount: Int = 0
	) {
		val requiredCount: Int get() = targets.size
	}

	private var nextSessionId = 0L
	private var activeSession: Session? = null

	fun start(
		webView: WebView,
		kind: ReaderPageTurnTransitionKind,
		reference: ReaderPageSlideSnapshot,
		targets: List<ReaderPageRasterBatchTarget>,
		onStagingStarted: (ReaderPageSlideSnapshot) -> Unit = {},
		onActiveTarget: (ReaderPageRasterBatchTarget) -> Unit = {},
		onProgress: (completedCount: Int, requiredCount: Int) -> Unit = { _, _ -> },
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
		val session = Session(
			token = "navic-page-raster-batch-${++nextSessionId}",
			generation = bundleSource.currentGeneration(),
			webView = webView,
			kind = kind,
			reference = reference,
			targets = distinctTargets.values.toList(),
			onStagingStarted = onStagingStarted,
			onActiveTarget = onActiveTarget,
			onProgress = onProgress,
			onComplete = onComplete
		)
		activeSession = session
		session.onProgress(session.completedCount, session.requiredCount)
		hydrateTarget(session, 0)
		return true
	}

	fun cancel() {
		val session = activeSession ?: return
		activeSession = null
		if (session.webView.isAttachedToWindow) {
			session.webView.evaluateJavascript(
				"window.NavicReaderBridge?.cancelPageTurnPreviewBatch?.(" +
					"${JSONObject.quote(session.token)})"
			) { }
		}
		session.reference.release()
		session.onComplete(ReaderPageRasterBatchOutcome.Cancelled)
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
		bundleSource.hydrateSnapshot(
			webView = session.webView,
			pageIndex = target.pageIndex,
			kind = session.kind,
			reference = session.reference
		) { hydrated ->
			if (!isSessionActive(session)) return@hydrateSnapshot
			if (hydrated == null) {
				session.missingTargets += target
				hydrateTarget(session, targetIndex + 1)
				return@hydrateSnapshot
			}
			markCompleted(session)
			bundleSource.ensurePersistentSnapshot(hydrated, target.priority) { persisted ->
				if (!persisted) {
					Logger.w(
						ReaderPageRasterBatchTag,
						"Retained in-memory page remains usable after cache persistence failure " +
							"pageIndex=${target.pageIndex}"
					)
				}
			}
			hydrateTarget(session, targetIndex + 1)
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
		val target = session.missingTargets.firstOrNull { candidate -> candidate.pageIndex == pageIndex }
		if (pageIndex < 0 || itemToken.isBlank() || target == null) {
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
		bundleSource.capturePreparedRasterPage(
			webView = session.webView,
			pageIndex = pageIndex,
			kind = session.kind,
			reference = session.reference,
			itemToken = itemToken,
			priority = target.priority,
			onStagingStarted = session.onStagingStarted
		) { captured ->
			if (!isSessionActive(session)) return@capturePreparedRasterPage
			if (!captured) {
				finish(
					session,
					ReaderPageRasterBatchOutcome.Failed(
						stage = "preview-capture",
						pageIndex = pageIndex,
						reason = "prepared-raster-capture-failed"
					)
				)
				return@capturePreparedRasterPage
			}
			session.missingTargets.remove(target)
			markCompleted(session)
			advancePageTurnPreviewBatch(session, pageIndex)
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

	private fun markCompleted(session: Session) {
		session.completedCount += 1
		session.onProgress(session.completedCount, session.requiredCount)
	}

	private fun isSessionActive(session: Session): Boolean =
		activeSession === session &&
			session.generation == bundleSource.currentGeneration() &&
			session.webView.isAttachedToWindow

	private fun finish(session: Session, outcome: ReaderPageRasterBatchOutcome) {
		if (activeSession !== session) return
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
