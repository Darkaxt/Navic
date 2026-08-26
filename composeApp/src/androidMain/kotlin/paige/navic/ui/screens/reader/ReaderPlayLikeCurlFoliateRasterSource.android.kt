package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.webkit.WebView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import karacken.curl.PageDisplayRect
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

internal fun readerPlayLikeCurlFillerFoliateLeaf(
	orientation: ReaderPlayLikeCurlOrientation,
	physicalLeaf: ReaderPlayLikeCurlPhysicalLeaf
): ReaderPlayLikeCurlFoliateLeaf = if (orientation == ReaderPlayLikeCurlOrientation.Portrait) {
	ReaderPlayLikeCurlFoliateLeaf.Full
} else {
	when (physicalLeaf) {
		ReaderPlayLikeCurlPhysicalLeaf.Left -> ReaderPlayLikeCurlFoliateLeaf.Left
		ReaderPlayLikeCurlPhysicalLeaf.Right -> ReaderPlayLikeCurlFoliateLeaf.Right
	}
}

internal data class ReaderPlayLikeCurlFoliatePageRequest(
	val logicalOrdinal: Int,
	val sourcePageIndex: Int,
	val leaf: ReaderPlayLikeCurlFoliateLeaf
)

internal data class ReaderPlayLikeCurlPhysicalRect(
	val left: Int,
	val top: Int,
	val right: Int,
	val bottom: Int
) {
	init {
		require(right >= left) { "Physical rectangle width must not be negative" }
		require(bottom > top) { "Physical rectangle height must be positive" }
	}

	val width: Int
		get() = right - left

	val height: Int
		get() = bottom - top

	fun asDisplayRect(): PageDisplayRect? = if (width > 0) {
		PageDisplayRect(left, top, right, bottom)
	} else {
		null
	}
}

internal data class ReaderPlayLikeCurlRasterLayout(
	val surfaceRectInWindow: ReaderPlayLikeCurlPhysicalRect,
	val fullLeafRect: ReaderPlayLikeCurlPhysicalRect?,
	val leftLeafRect: ReaderPlayLikeCurlPhysicalRect?,
	val gutterRect: ReaderPlayLikeCurlPhysicalRect?,
	val rightLeafRect: ReaderPlayLikeCurlPhysicalRect?
) {
	init {
		require(surfaceRectInWindow.width > 0)
		listOfNotNull(fullLeafRect, leftLeafRect, gutterRect, rightLeafRect).forEach { rect ->
			require(
				rect.left >= 0 &&
					rect.top >= 0 &&
					rect.right <= surfaceRectInWindow.width &&
					rect.bottom <= surfaceRectInWindow.height
			) { "Physical leaf geometry must fit the Foliate surface" }
		}
	}

	private fun physicalRect(leaf: ReaderPlayLikeCurlFoliateLeaf): ReaderPlayLikeCurlPhysicalRect? =
		when (leaf) {
			ReaderPlayLikeCurlFoliateLeaf.Full -> fullLeafRect
			ReaderPlayLikeCurlFoliateLeaf.Left -> leftLeafRect
			ReaderPlayLikeCurlFoliateLeaf.Right -> rightLeafRect
		}

	fun displayRect(leaf: ReaderPlayLikeCurlFoliateLeaf): PageDisplayRect? =
		physicalRect(leaf)?.asDisplayRect()

	fun displayRect(
		leaf: ReaderPlayLikeCurlFoliateLeaf,
		rendererLeftInWindow: Int,
		rendererTopInWindow: Int,
		rendererWidth: Int,
		rendererHeight: Int
	): PageDisplayRect? {
		if (rendererWidth <= 0 || rendererHeight <= 0) return null
		val rect = physicalRect(leaf) ?: return null
		val surfaceLeft = surfaceRectInWindow.left.toLong() - rendererLeftInWindow
		val surfaceTop = surfaceRectInWindow.top.toLong() - rendererTopInWindow
		val left = surfaceLeft + rect.left
		val top = surfaceTop + rect.top
		val right = surfaceLeft + rect.right
		val bottom = surfaceTop + rect.bottom
		if (
			left < 0L ||
			top < 0L ||
			right > rendererWidth.toLong() ||
			bottom > rendererHeight.toLong() ||
			right <= left ||
			bottom <= top
		) {
			return null
		}
		return PageDisplayRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
	}

	fun pageBackingRect(
		leaf: ReaderPlayLikeCurlFoliateLeaf,
		rendererLeftInWindow: Int,
		rendererTopInWindow: Int,
		rendererWidth: Int,
		rendererHeight: Int
	): PageDisplayRect? {
		if (rendererWidth <= 0 || rendererHeight <= 0) return null
		val page = physicalRect(leaf) ?: return null
		val surfaceWidth = surfaceRectInWindow.width
		val leftReveal = page.left
		val rightReveal = surfaceWidth - page.right
		val backing = when (leaf) {
			ReaderPlayLikeCurlFoliateLeaf.Full -> when {
				leftReveal > 0 && rightReveal == 0 ->
					ReaderPlayLikeCurlPhysicalRect(0, page.top, page.left, page.bottom)
				rightReveal > 0 && leftReveal == 0 ->
					ReaderPlayLikeCurlPhysicalRect(page.right, page.top, surfaceWidth, page.bottom)
				else -> null
			}
			ReaderPlayLikeCurlFoliateLeaf.Left -> if (leftReveal > 0) {
				ReaderPlayLikeCurlPhysicalRect(0, page.top, page.left, page.bottom)
			} else {
				null
			}
			ReaderPlayLikeCurlFoliateLeaf.Right -> if (rightReveal > 0) {
				ReaderPlayLikeCurlPhysicalRect(page.right, page.top, surfaceWidth, page.bottom)
			} else {
				null
			}
		} ?: return null
		val surfaceLeft = surfaceRectInWindow.left.toLong() - rendererLeftInWindow
		val surfaceTop = surfaceRectInWindow.top.toLong() - rendererTopInWindow
		val left = surfaceLeft + backing.left
		val top = surfaceTop + backing.top
		val right = surfaceLeft + backing.right
		val bottom = surfaceTop + backing.bottom
		if (
			left < 0L ||
			top < 0L ||
			right > rendererWidth.toLong() ||
			bottom > rendererHeight.toLong()
		) {
			return null
		}
		return PageDisplayRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
	}
}

internal data class ReaderPlayLikeCurlPageMaterial(
	val frontPaperColorArgb: Int,
	val reversePaperColorArgb: Int,
	val fixedBorderColorArgb: Int,
	val uncoveredBackgroundColorArgb: Int
) {
	init {
		listOf(
			frontPaperColorArgb,
			reversePaperColorArgb,
			fixedBorderColorArgb,
			uncoveredBackgroundColorArgb
		).forEach { colorArgb ->
			require((colorArgb ushr 24) == 0xFF) {
				"PlayLikeCurl page material colors must be opaque"
			}
		}
	}
}

private fun readerPlayLikeCurlDerivedMaterialColor(
	paperColorArgb: Int,
	contrastWeight: Int
): Int {
	require(contrastWeight in 1..254)
	val red = paperColorArgb ushr 16 and 0xFF
	val green = paperColorArgb ushr 8 and 0xFF
	val blue = paperColorArgb and 0xFF
	val luminance = (red * 299 + green * 587 + blue * 114) / 1_000
	val contrastTarget = if (luminance >= 128) 0 else 255
	fun blend(channel: Int): Int =
		(channel * (255 - contrastWeight) + contrastTarget * contrastWeight + 127) / 255
	return 0xFF000000.toInt() or
		(blend(red) shl 16) or
		(blend(green) shl 8) or
		blend(blue)
}

internal fun readerPlayLikeCurlPageMaterial(
	paperColorArgb: Int
) = ReaderPlayLikeCurlPageMaterial(
	frontPaperColorArgb = paperColorArgb,
	reversePaperColorArgb = readerPlayLikeCurlDerivedMaterialColor(paperColorArgb, 18),
	fixedBorderColorArgb = readerPlayLikeCurlDerivedMaterialColor(paperColorArgb, 48),
	uncoveredBackgroundColorArgb = paperColorArgb
)

internal data class ReaderPlayLikeCurlRasterImage(
	val bitmap: Bitmap,
	val paperColorArgb: Int,
	val layout: ReaderPlayLikeCurlRasterLayout,
	val leaf: ReaderPlayLikeCurlFoliateLeaf,
	val material: ReaderPlayLikeCurlPageMaterial = readerPlayLikeCurlPageMaterial(paperColorArgb)
) {
	init {
		require((paperColorArgb ushr 24) == 0xFF) {
			"PlayLikeCurl paper color must be opaque"
		}
		require(material.frontPaperColorArgb == paperColorArgb) {
			"PlayLikeCurl front paper material must match raster compositing"
		}
		requireNotNull(layout.displayRect(leaf)) {
			"PlayLikeCurl raster must have positive physical leaf placement"
		}
	}

	val displayRect: PageDisplayRect
		get() = checkNotNull(layout.displayRect(leaf))
}

internal fun readerPlayLikeCurlRasterLayout(
	geometry: ReaderPageTurnLeafGeometry,
	bitmapWidth: Int,
	bitmapHeight: Int,
	surfaceLeftInWindow: Int,
	surfaceTopInWindow: Int,
	surfaceRightInWindow: Int,
	surfaceBottomInWindow: Int
): ReaderPlayLikeCurlRasterLayout? {
	if (
		bitmapWidth <= 0 ||
		bitmapHeight <= 0 ||
		surfaceRightInWindow <= surfaceLeftInWindow ||
		surfaceBottomInWindow <= surfaceTopInWindow
	) {
		return null
	}
	val surface = ReaderPlayLikeCurlPhysicalRect(
		left = surfaceLeftInWindow,
		top = surfaceTopInWindow,
		right = surfaceRightInWindow,
		bottom = surfaceBottomInWindow
	)
	val surfaceWidth = surface.width
	val surfaceHeight = surface.height

	fun scaleBoundary(value: Int, sourceExtent: Int, targetExtent: Int): Int =
		((value.toLong() * targetExtent + sourceExtent / 2L) / sourceExtent).toInt()

	fun map(
		rect: ReaderPageTurnPixelRect?,
		allowZeroWidth: Boolean
	): ReaderPlayLikeCurlPhysicalRect? {
		if (rect == null) return null
		val validWidth = if (allowZeroWidth) rect.right >= rect.left else rect.right > rect.left
		if (
			rect.left < 0 ||
			rect.top < 0 ||
			rect.right > bitmapWidth ||
			rect.bottom > bitmapHeight ||
			!validWidth ||
			rect.bottom <= rect.top
		) {
			return null
		}
		val mapped = ReaderPlayLikeCurlPhysicalRect(
			left = scaleBoundary(rect.left, bitmapWidth, surfaceWidth),
			top = scaleBoundary(rect.top, bitmapHeight, surfaceHeight),
			right = scaleBoundary(rect.right, bitmapWidth, surfaceWidth),
			bottom = scaleBoundary(rect.bottom, bitmapHeight, surfaceHeight)
		)
		return mapped.takeIf { allowZeroWidth || it.width > 0 }
	}

	val full = map(geometry.fullLeafRect, allowZeroWidth = false)
	val left = map(geometry.leftLeafRect, allowZeroWidth = false)
	val gutter = map(geometry.gutterRect, allowZeroWidth = true)
	val right = map(geometry.rightLeafRect, allowZeroWidth = false)
	if (full == null && left == null && right == null) return null
	return ReaderPlayLikeCurlRasterLayout(
		surfaceRectInWindow = surface,
		fullLeafRect = full,
		leftLeafRect = left,
		gutterRect = gutter,
		rightLeafRect = right
	)
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

internal fun readerPlayLikeCurlProtectedSourcePageIndices(
	profile: ReaderPlayLikeCurlRasterProfile,
	logicalOrdinals: List<Int>
): Set<Int> = logicalOrdinals.mapTo(linkedSetOf()) { ordinal ->
	readerPlayLikeCurlFoliatePageRequest(
		orientation = profile.orientation,
		readerDirection = profile.readerDirection,
		logicalOrdinal = ordinal,
		pageCount = profile.pageCount,
		spreadAnchorParity = profile.spreadAnchorParity
	).sourcePageIndex
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

private fun readerPlayLikeCurlCopyOpaqueLeaf(
	source: Bitmap,
	sourceRect: ReaderPageTurnPixelRect
): Bitmap {
	val cropped = Bitmap.createBitmap(
		source,
		sourceRect.left,
		sourceRect.top,
		sourceRect.width,
		sourceRect.height
	)
	val target = if (cropped === source || cropped.config != Bitmap.Config.ARGB_8888) {
		try {
			checkNotNull(cropped.copy(Bitmap.Config.ARGB_8888, false))
		} finally {
			if (cropped !== source) cropped.recycle()
		}
	} else {
		cropped
	}
	target.setHasAlpha(false)
	target.setPremultiplied(true)
	return target
}

private fun readerPlayLikeCurlFlattenLeafOverPaper(
	source: Bitmap,
	sourceRect: ReaderPageTurnPixelRect,
	paperColorArgb: Int
): Bitmap {
	val width = sourceRect.width
	val height = sourceRect.height
	val pixels = IntArray(width * height)
	source.getPixels(
		pixels,
		0,
		width,
		sourceRect.left,
		sourceRect.top,
		width,
		height
	)
	val paperRed = paperColorArgb ushr 16 and 0xff
	val paperGreen = paperColorArgb ushr 8 and 0xff
	val paperBlue = paperColorArgb and 0xff
	pixels.indices.forEach { index ->
		val color = pixels[index]
		val alpha = color ushr 24
		if (alpha < 0xff) {
			val inverseAlpha = 0xff - alpha
			val red = ((color ushr 16 and 0xff) * alpha + paperRed * inverseAlpha + 127) / 255
			val green = ((color ushr 8 and 0xff) * alpha + paperGreen * inverseAlpha + 127) / 255
			val blue = ((color and 0xff) * alpha + paperBlue * inverseAlpha + 127) / 255
			pixels[index] = -0x1000000 or (red shl 16) or (green shl 8) or blue
		}
	}
	return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
		target.setPixels(pixels, 0, width, 0, 0, width, height)
		target.setHasAlpha(false)
		target.setPremultiplied(true)
	}
}

/**
 * Converts one retained Foliate snapshot into a PlayLikeCurl-owned independent page bitmap.
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
	val surfaceRect = snapshot.surfaceRectInWindow
	val layout = readerPlayLikeCurlRasterLayout(
		geometry = snapshot.leafGeometry,
		bitmapWidth = snapshot.bitmap.width,
		bitmapHeight = snapshot.bitmap.height,
		surfaceLeftInWindow = surfaceRect.left,
		surfaceTopInWindow = surfaceRect.top,
		surfaceRightInWindow = surfaceRect.right,
		surfaceBottomInWindow = surfaceRect.bottom
	) ?: return null
	if (layout.displayRect(leaf) == null) return null

	val paperColorArgb = snapshot.reverseFaceColor
	val material = readerPlayLikeCurlPageMaterial(paperColorArgb)
	val target = if (snapshot.bitmap.hasAlpha()) {
		readerPlayLikeCurlFlattenLeafOverPaper(snapshot.bitmap, sourceRect, paperColorArgb)
	} else {
		readerPlayLikeCurlCopyOpaqueLeaf(snapshot.bitmap, sourceRect)
	}
	ReaderPlayLikeCurlRasterImage(
		bitmap = target,
		paperColorArgb = paperColorArgb,
		layout = layout,
		leaf = leaf,
		material = material
	)
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
				if (
					key.missingRasterPolicy ==
						ReaderPlayLikeCurlMissingRasterPolicy.RequestRepair &&
					key.publicationFence.isCurrent()
				) {
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
		if (
			key.profile != profile ||
			key.pageIndex !in 0 until profile.pageCount ||
			key.missingRasterPolicy == ReaderPlayLikeCurlMissingRasterPolicy.CacheOnly
		) {
			return false
		}
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
