package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.reader.ReaderPageTurnLeafGeometry
import paige.navic.reader.ReaderPageTurnPhysicalDirection
import paige.navic.reader.ReaderPageTurnPixelRect

internal enum class ReaderPageTurnTransitionKind {
	LandscapeSpreadSlide,
	PortraitSlide
}

internal enum class ReaderPageTurnLogicalDirection { Next, Previous }

internal enum class ReaderPageTurnPhysicalSide {
	Left,
	Right,
	Center
}

internal data class ReaderPageTurnTransitionPlan(
	val token: String,
	val generation: Long,
	val kind: ReaderPageTurnTransitionKind,
	val logicalDirection: ReaderPageTurnLogicalDirection,
	val sourcePageIndex: Int,
	val targetPageIndex: Int,
	val sourcePageSide: ReaderPageTurnPhysicalSide,
	val targetPageSide: ReaderPageTurnPhysicalSide
) {
	val cacheKey: String
		get() = "$kind:$sourcePageIndex:$targetPageIndex:$sourcePageSide:$targetPageSide"

	fun matchesLayout(spread: Boolean): Boolean =
		(kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) == spread

	companion object {
		fun parse(encoded: String?, token: String, generation: Long): ReaderPageTurnTransitionPlan? =
			runCatching { parseOrThrow(encoded, token, generation) }.getOrNull()

		internal fun parseOrThrow(encoded: String?, token: String, generation: Long): ReaderPageTurnTransitionPlan {
			val raw = encoded.orEmpty().trim()
			val firstPass = Json.parseToJsonElement(raw)
			val jsonText = if (raw.startsWith('"')) {
				firstPass.jsonPrimitive.contentOrNull ?: error("Quoted page-turn plan is not a string")
			} else {
				raw
			}
			val json = Json.parseToJsonElement(jsonText).jsonObject
			val kind = json.requiredKind("kind")
			val logicalDirection = json.requiredLogicalDirection("logicalDirection")
			val sourcePageIndex = json.requiredIndex("sourcePageIndex")
			val targetPageIndex = json.requiredIndex("targetPageIndex")
			val sourcePageSide = json.requiredSide("sourcePageSide")
			val targetPageSide = json.requiredSide("targetPageSide")
			return ReaderPageTurnTransitionPlan(
				token = token,
				generation = generation,
				kind = kind,
				logicalDirection = logicalDirection,
				sourcePageIndex = sourcePageIndex,
				targetPageIndex = targetPageIndex,
				sourcePageSide = sourcePageSide,
				targetPageSide = targetPageSide
			)
		}
	}
}

internal data class ReaderPageSlideSnapshotKey(
	val visualPageIndex: Int,
	val kind: ReaderPageTurnTransitionKind,
	val bitmapWidth: Int,
	val bitmapHeight: Int,
	val surfaceWidth: Int,
	val surfaceHeight: Int
)

internal class ReaderPageSlideSnapshot(
	val key: ReaderPageSlideSnapshotKey,
	val bitmap: Bitmap,
	val surfaceRectInWindow: Rect,
	val leafGeometry: ReaderPageTurnLeafGeometry,
	val reverseFaceColor: Int
) {
	private var cacheOwned = true
	private var retainCount = 0
	private var recycled = false

	@Synchronized
	fun retain() {
		check(!recycled) { "Cannot retain a recycled page snapshot" }
		retainCount += 1
	}

	@Synchronized
	fun release() {
		check(retainCount > 0) { "Page snapshot released without a matching retain" }
		retainCount -= 1
		recycleIfUnowned()
	}

	@Synchronized
	fun releaseCacheOwnership() {
		if (!cacheOwned) return
		cacheOwned = false
		recycleIfUnowned()
	}

	private fun recycleIfUnowned() {
		if (cacheOwned || retainCount > 0 || recycled) return
		recycled = true
		if (!bitmap.isRecycled) bitmap.recycle()
	}
}

internal class ReaderPageSlideTransition(
	val plan: ReaderPageTurnTransitionPlan,
	val source: ReaderPageSlideSnapshot,
	val destination: ReaderPageSlideSnapshot
) {
	private var closed = false

	init {
		source.retain()
		destination.retain()
	}

	val surfaceRectInWindow: Rect
		get() = source.surfaceRectInWindow
	val leafGeometry: ReaderPageTurnLeafGeometry
		get() = source.leafGeometry
	fun activeLeafRect(direction: ReaderPageTurnPhysicalDirection) = leafGeometry.activeLeafRect(
		direction = direction,
		spread = plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide
	)
	val renderScaleX: Float
		get() = surfaceRectInWindow.width() / source.bitmap.width.toFloat()
	val renderScaleY: Float
		get() = surfaceRectInWindow.height() / source.bitmap.height.toFloat()

	@Synchronized
	fun close() {
		if (closed) return
		closed = true
		source.release()
		destination.release()
	}
}

internal data class ReaderPageCurlTextureSet(
	val identity: String,
	val sourceBitmap: Bitmap,
	val destinationBitmap: Bitmap,
	val direction: ReaderPageTurnPhysicalDirection,
	val kind: ReaderPageTurnTransitionKind,
	val surfaceLeft: Float,
	val surfaceTop: Float,
	val surfaceWidth: Float,
	val surfaceHeight: Float,
	val activeLeafRect: ReaderPageTurnPixelRect,
	val companionLeafRect: ReaderPageTurnPixelRect?
) {
	val isComplete: Boolean
		get() = !sourceBitmap.isRecycled &&
			!destinationBitmap.isRecycled &&
			surfaceWidth > 0f &&
			surfaceHeight > 0f &&
			activeLeafRect.width > 0 &&
			activeLeafRect.height > 0

	val bitmapWidth: Int get() = sourceBitmap.width
	val bitmapHeight: Int get() = sourceBitmap.height
	val scaleX: Float get() = surfaceWidth / bitmapWidth.toFloat()
	val scaleY: Float get() = surfaceHeight / bitmapHeight.toFloat()

	companion object {
		fun from(
			transition: ReaderPageSlideTransition,
			direction: ReaderPageTurnPhysicalDirection,
			surfaceLeft: Int,
			surfaceTop: Int
		): ReaderPageCurlTextureSet? {
			val activeLeaf = transition.activeLeafRect(direction) ?: return null
			val companionLeaf = if (transition.plan.kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
				when (direction) {
					ReaderPageTurnPhysicalDirection.TowardLeft -> transition.leafGeometry.leftLeafRect
					ReaderPageTurnPhysicalDirection.TowardRight -> transition.leafGeometry.rightLeafRect
				}
			} else {
				null
			}
			return ReaderPageCurlTextureSet(
				identity = buildString {
					append(transition.plan.cacheKey)
					append(':')
					append(System.identityHashCode(transition.source.bitmap))
					append(':')
					append(System.identityHashCode(transition.destination.bitmap))
					append(':')
					append(direction)
				},
				sourceBitmap = transition.source.bitmap,
				destinationBitmap = transition.destination.bitmap,
				direction = direction,
				kind = transition.plan.kind,
				surfaceLeft = surfaceLeft.toFloat(),
				surfaceTop = surfaceTop.toFloat(),
				surfaceWidth = transition.surfaceRectInWindow.width().toFloat(),
				surfaceHeight = transition.surfaceRectInWindow.height().toFloat(),
				activeLeafRect = activeLeaf,
				companionLeafRect = companionLeaf
			)
		}
	}
}

private fun JsonObject.requiredIndex(key: String): Int =
	get(key)?.jsonPrimitive?.intOrNull?.takeUnless { it < 0 }
		?: error("Missing page-turn index: $key")

private fun JsonObject.requiredKind(key: String): ReaderPageTurnTransitionKind = when (requiredString(key)) {
	"landscape-spread-slide" -> ReaderPageTurnTransitionKind.LandscapeSpreadSlide
	"portrait-slide" -> ReaderPageTurnTransitionKind.PortraitSlide
	else -> error("Unsupported page-turn kind: ${get(key)}")
}

private fun JsonObject.requiredLogicalDirection(key: String): ReaderPageTurnLogicalDirection = when (requiredString(key)) {
	"next" -> ReaderPageTurnLogicalDirection.Next
	"previous" -> ReaderPageTurnLogicalDirection.Previous
	else -> error("Unsupported page-turn direction: ${get(key)}")
}

private fun JsonObject.requiredSide(key: String): ReaderPageTurnPhysicalSide = when (requiredString(key)) {
	"left" -> ReaderPageTurnPhysicalSide.Left
	"right" -> ReaderPageTurnPhysicalSide.Right
	"center" -> ReaderPageTurnPhysicalSide.Center
	else -> error("Unsupported page-turn side: ${get(key)}")
}

private fun JsonObject.requiredString(key: String): String =
	get(key)?.jsonPrimitive?.contentOrNull ?: error("Missing page-turn value: $key")
