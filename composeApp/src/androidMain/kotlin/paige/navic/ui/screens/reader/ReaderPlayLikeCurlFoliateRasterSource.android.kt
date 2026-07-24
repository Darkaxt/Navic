package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.webkit.WebView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPixelRect
import paige.navic.util.core.Logger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

private const val ReaderPlayLikeCurlFoliateRasterSourceTag = "ReaderPlayLikeCurlFoliateRaster"

internal enum class ReaderPlayLikeCurlReaderDirection {
	Ltr,
	Rtl
}

internal enum class ReaderPlayLikeCurlFoliateLeaf {
	Full,
	Left,
	Right
}

internal data class ReaderPlayLikeCurlFoliatePageRequest(
	val logicalOrdinal: Int,
	val sourcePageIndex: Int,
	val leaf: ReaderPlayLikeCurlFoliateLeaf
)

internal data class ReaderPlayLikeCurlRasterImage(
	val bitmap: Bitmap,
	val paperColorArgb: Int
) {
	init {
		require((paperColorArgb ushr 24) == 0xFF) {
			"PlayLikeCurl paper color must be opaque"
		}
	}
}

internal fun readerPlayLikeCurlFoliatePageRequest(
	orientation: ReaderPlayLikeCurlOrientation,
	readerDirection: ReaderPlayLikeCurlReaderDirection,
	logicalOrdinal: Int,
	pageCount: Int,
	spreadAnchorParity: Int = 0
): ReaderPlayLikeCurlFoliatePageRequest {
	require(pageCount > 0) { "Foliate page count must be positive" }
	val boundedOrdinal = logicalOrdinal.coerceIn(0, pageCount - 1)
	if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
		return ReaderPlayLikeCurlFoliatePageRequest(
			logicalOrdinal = boundedOrdinal,
			sourcePageIndex = boundedOrdinal,
			leaf = ReaderPlayLikeCurlFoliateLeaf.Full
		)
	}

	val slot = readerPlayLikeCurlSpreadSlot(
		logicalOrdinal = boundedOrdinal,
		pageCount = pageCount,
		readerDirection = readerDirection,
		spreadAnchorParity = spreadAnchorParity
	)
	return ReaderPlayLikeCurlFoliatePageRequest(
		logicalOrdinal = boundedOrdinal,
		sourcePageIndex = slot.sourcePageIndex,
		leaf = when (slot.physicalLeaf) {
			ReaderPlayLikeCurlPhysicalLeaf.Left -> ReaderPlayLikeCurlFoliateLeaf.Left
			ReaderPlayLikeCurlPhysicalLeaf.Right -> ReaderPlayLikeCurlFoliateLeaf.Right
		}
	)
}

internal fun readerPlayLikeCurlQaMissIsEligible(
	request: ReaderPlayLikeCurlFoliatePageRequest,
	targetLogicalOrdinal: Int?
): Boolean = targetLogicalOrdinal == null || request.logicalOrdinal == targetLogicalOrdinal

internal fun readerPlayLikeCurlFoliateLeafRect(
	geometry: ReaderPageTurnLeafGeometry,
	leaf: ReaderPlayLikeCurlFoliateLeaf
): ReaderPageTurnPixelRect? = when (leaf) {
	ReaderPlayLikeCurlFoliateLeaf.Full -> geometry.fullLeafRect
	ReaderPlayLikeCurlFoliateLeaf.Left -> geometry.leftLeafRect
	ReaderPlayLikeCurlFoliateLeaf.Right -> geometry.rightLeafRect
}

/**
 * Converts one retained Foliate snapshot into a PlayLikeCurl-owned immutable page bitmap.
 * The retained snapshot is always released before this function returns.
 */
internal fun readerPlayLikeCurlCopyRetainedFoliateLeaf(
	snapshot: ReaderPageSlideSnapshot,
	leaf: ReaderPlayLikeCurlFoliateLeaf
): ReaderPlayLikeCurlRasterImage? = try {
	val sourceRect = readerPlayLikeCurlFoliateLeafRect(snapshot.leafGeometry, leaf)
		?.takeIf { rect ->
			rect.left >= 0 &&
				rect.top >= 0 &&
				rect.right <= snapshot.bitmap.width &&
				rect.bottom <= snapshot.bitmap.height &&
				rect.width > 0 &&
				rect.height > 0
		}
		?: return null
	if (snapshot.bitmap.isRecycled) return null

	val targetWidth = sourceRect.width
	val targetHeight = sourceRect.height
	val paperColorArgb = snapshot.reverseFaceColor
	val target = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
	target.eraseColor(paperColorArgb)
	Canvas(target).drawBitmap(
		snapshot.bitmap,
		Rect(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom),
		Rect(0, 0, targetWidth, targetHeight),
		Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
	)
	target.setHasAlpha(false)
	ReaderPlayLikeCurlRasterImage(target, paperColorArgb)
} finally {
	snapshot.release()
}

private data class ReaderPlayLikeCurlRasterResolverInputs(
	val webView: WebView,
	val reference: ReaderPageSlideSnapshot
)

/**
 * Production raster loader. It resolves decoded snapshots first, then durable rasters. Foliate
 * capture is requested only after both sources miss while the publication fence remains current.
 */
internal class ReaderPlayLikeCurlFoliateRasterLoader(
	private val bundleSource: ReaderPageTurnBundleSource,
	private val profile: ReaderPlayLikeCurlRasterProfile,
	private val webViewProvider: () -> WebView?,
	private val referenceSnapshotProvider: (
		ReaderPlayLikeCurlFoliatePageRequest
	) -> ReaderPageSlideSnapshot?,
	private val diagnostics: ReaderPageRuntimeDiagnostics? = null,
	private val qaFaultRegistry: ReaderPageQaFaultRegistry? = null,
	private val qaMissTargetOrdinalProvider: () -> Int? = { null },
	private val onMissingRaster: (Int) -> Unit = {},
	private val onQaMissingRaster: (
		Int,
		ReaderPageQaFaultCorrelation
	) -> Unit = { pageIndex, _ -> onMissingRaster(pageIndex) },
	private val copyDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ReaderPlayLikeCurlRasterLoader<ReaderPlayLikeCurlRasterImage> {
	private val transitionKind = when (profile.orientation) {
		ReaderPlayLikeCurlOrientation.Portrait -> ReaderPageTurnTransitionKind.PortraitSlide
		ReaderPlayLikeCurlOrientation.Landscape -> ReaderPageTurnTransitionKind.LandscapeSpreadSlide
	}
	private val fallbackRasterRequestEpochs = AtomicLong()

	override suspend fun load(key: ReaderPlayLikeCurlRasterKey): ReaderPlayLikeCurlRasterImage? {
		if (key.profile != profile || key.pageIndex !in 0 until profile.pageCount) return null
		if (consumeQaMiss(key)) return null
		val request = pageRequest(key.pageIndex)
		val snapshot = resolve(request, key.publicationFence::isCurrent)
		if (snapshot == null) {
			withContext(Dispatchers.Main.immediate) {
				if (key.publicationFence.isCurrent()) {
					onMissingRaster(request.sourcePageIndex)
					Logger.w(
						ReaderPlayLikeCurlFoliateRasterSourceTag,
						"Missing Foliate raster logical=${request.logicalOrdinal} " +
							"source=${request.sourcePageIndex} leaf=${request.leaf} " +
							"profileGeneration=${profile.rasterGeneration} " +
							"activeGeneration=${bundleSource.currentGeneration()} " +
							"cached=${bundleSource.cachedSnapshotPageIndices(transitionKind)}"
					)
				}
			}
			return null
		}
		var snapshotConsumed = false
		return try {
			withContext(copyDispatcher) {
				snapshotConsumed = true
				readerPlayLikeCurlCopyRetainedFoliateLeaf(snapshot, request.leaf)
			}
		} finally {
			if (!snapshotConsumed) snapshot.release()
		}
	}

	internal suspend fun consumeQaMiss(key: ReaderPlayLikeCurlRasterKey): Boolean {
		if (key.profile != profile || key.pageIndex !in 0 until profile.pageCount) return false
		val registry = qaFaultRegistry ?: return false
		return withContext(Dispatchers.Main.immediate) {
			val request = pageRequest(key.pageIndex)
			if (
				!key.publicationFence.isCurrent() ||
				!readerPlayLikeCurlQaMissIsEligible(
					request,
					qaMissTargetOrdinalProvider()
				) ||
				!registry.hasQueued(ReaderPageQaFault.MissNextRasterLoad)
			) {
				return@withContext false
			}
			val diagnosticOperation = diagnostics?.startOperation(
				rasterGeneration = profile.rasterGeneration,
				ordinal = request.logicalOrdinal
			)
			val rasterRequestEpoch = diagnosticOperation?.attempt
				?: fallbackRasterRequestEpochs.incrementAndGet()
			registry.consumeAndApply(
				ReaderPageQaFault.MissNextRasterLoad,
				ReaderPageQaFaultOperationContext(
					rasterRequestEpoch = rasterRequestEpoch
				)
			)?.also { applied ->
				val directCorrelation = applied.correlation()
				diagnosticOperation?.let { operation ->
					diagnostics.rasterAcquisition(
						operation = operation,
						source = ReaderPageRasterAcquisitionSource.PersistentHydration,
						trigger = ReaderPageRasterAcquisitionTrigger.WorkingSetRefill,
						result = ReaderPageRasterAcquisitionResult.Miss,
						qaFaultCorrelation = directCorrelation
					)
				}
				onQaMissingRaster(
					request.sourcePageIndex,
					directCorrelation.withRelation(
						ReaderPageQaFaultRelation.Recovery
					)
				)
			} != null
		}
	}

	suspend fun hydratePersistent(
		logicalOrdinal: Int,
		isStillCurrent: () -> Boolean
	): Boolean {
		if (logicalOrdinal !in 0 until profile.pageCount) return false
		val snapshot = resolve(pageRequest(logicalOrdinal), isStillCurrent) ?: return false
		snapshot.release()
		return true
	}

	private fun pageRequest(logicalOrdinal: Int) = readerPlayLikeCurlFoliatePageRequest(
		orientation = profile.orientation,
		readerDirection = profile.readerDirection,
		logicalOrdinal = logicalOrdinal,
		pageCount = profile.pageCount,
		spreadAnchorParity = profile.spreadAnchorParity
	)

	private suspend fun resolve(
		request: ReaderPlayLikeCurlFoliatePageRequest,
		isStillCurrent: () -> Boolean
	): ReaderPageSlideSnapshot? {
		val inputs = resolverInputs(request, isStillCurrent) ?: return null
		return try {
			bundleSource.resolveSnapshot(
				webView = inputs.webView,
				pageIndex = request.sourcePageIndex,
				kind = transitionKind,
				reference = inputs.reference,
				publicationFence = isStillCurrent
			)
		} finally {
			inputs.reference.release()
		}
	}

	private suspend fun resolverInputs(
		request: ReaderPlayLikeCurlFoliatePageRequest,
		isStillCurrent: () -> Boolean
	): ReaderPlayLikeCurlRasterResolverInputs? =
		withContext(Dispatchers.Main.immediate) {
			if (!isStillCurrent()) return@withContext null
			val webView = webViewProvider()?.takeIf { it.isAttachedToWindow }
				?: return@withContext null
			val reference = referenceSnapshotProvider(request)
				?: return@withContext null
			suspendCancellableCoroutine { continuation ->
				continuation.resume(
					ReaderPlayLikeCurlRasterResolverInputs(webView, reference),
					onCancellation = { _, undelivered, _ ->
						undelivered.reference.release()
					}
				)
			}
		}
}
