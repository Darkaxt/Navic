package paige.navic.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import paige.navic.reader.ReaderPageTurnPageRole

internal enum class ReaderPageTurnTransitionKind {
	LandscapeLeaf,
	LandscapeSpreadSlide,
	PortraitLeaf,
	PortraitSlide
}

internal enum class ReaderPageTurnLogicalDirection { Next, Previous }

internal enum class ReaderPageTurnPhysicalSide {
	Left,
	Right,
	Center;

	fun toPageRole(): ReaderPageTurnPageRole = when (this) {
		Left -> ReaderPageTurnPageRole.Left
		Right -> ReaderPageTurnPageRole.Right
		Center -> ReaderPageTurnPageRole.Full
	}
}

internal data class ReaderPageTurnTransitionPlan(
	val token: String,
	val generation: Long,
	val kind: ReaderPageTurnTransitionKind,
	val logicalDirection: ReaderPageTurnLogicalDirection,
	val sourcePageIndex: Int,
	val turningFrontPageIndex: Int,
	val turningReversePageIndex: Int?,
	val underneathPageIndex: Int?,
	val targetPageIndex: Int,
	val sourcePageSide: ReaderPageTurnPhysicalSide,
	val targetPageSide: ReaderPageTurnPhysicalSide,
	val turningFrontPageSide: ReaderPageTurnPhysicalSide,
	val turningReversePageSide: ReaderPageTurnPhysicalSide?,
	val underneathPageSide: ReaderPageTurnPhysicalSide?
) {
	val cacheKey: String
		get() = "$kind:$sourcePageIndex:$targetPageIndex:$sourcePageSide:$targetPageSide"

	fun matchesLayout(spread: Boolean): Boolean =
		(kind == ReaderPageTurnTransitionKind.LandscapeLeaf ||
			kind == ReaderPageTurnTransitionKind.LandscapeSpreadSlide) == spread

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
			val legacyRoles = legacyBitmapRoles(
				kind = kind,
				logicalDirection = logicalDirection,
				sourcePageIndex = sourcePageIndex,
				targetPageIndex = targetPageIndex,
				sourcePageSide = sourcePageSide
			)
			val turningReversePageIndex = json.optionalIndex("turningReversePageIndex")
				?: legacyRoles.turningReversePageIndex
			val underneathPageIndex = json.optionalIndex("underneathPageIndex")
				?: legacyRoles.underneathPageIndex
			val turningReversePageSide = json.optionalSide("turningReversePageSide")
				?: legacyRoles.turningReversePageSide
			val underneathPageSide = json.optionalSide("underneathPageSide")
				?: legacyRoles.underneathPageSide
			require((turningReversePageIndex == null) == (turningReversePageSide == null)) {
				"Reverse page index and side must be supplied together"
			}
			require((underneathPageIndex == null) == (underneathPageSide == null)) {
				"Underneath page index and side must be supplied together"
			}
			return ReaderPageTurnTransitionPlan(
				token = token,
				generation = generation,
				kind = kind,
				logicalDirection = logicalDirection,
				sourcePageIndex = sourcePageIndex,
				turningFrontPageIndex = json.optionalIndex("turningFrontPageIndex")
					?: legacyRoles.turningFrontPageIndex,
				turningReversePageIndex = turningReversePageIndex,
				underneathPageIndex = underneathPageIndex,
				targetPageIndex = targetPageIndex,
				sourcePageSide = sourcePageSide,
				targetPageSide = targetPageSide,
				turningFrontPageSide = json.optionalSide("turningFrontPageSide")
					?: legacyRoles.turningFrontPageSide,
				turningReversePageSide = turningReversePageSide,
				underneathPageSide = underneathPageSide
			)
		}
	}
}

private data class LegacyBitmapRoles(
	val turningFrontPageIndex: Int,
	val turningReversePageIndex: Int?,
	val underneathPageIndex: Int?,
	val turningFrontPageSide: ReaderPageTurnPhysicalSide,
	val turningReversePageSide: ReaderPageTurnPhysicalSide?,
	val underneathPageSide: ReaderPageTurnPhysicalSide?
)

private fun legacyBitmapRoles(
	kind: ReaderPageTurnTransitionKind,
	logicalDirection: ReaderPageTurnLogicalDirection,
	sourcePageIndex: Int,
	targetPageIndex: Int,
	sourcePageSide: ReaderPageTurnPhysicalSide
): LegacyBitmapRoles {
	if (kind != ReaderPageTurnTransitionKind.LandscapeSpreadSlide) {
		return LegacyBitmapRoles(
			turningFrontPageIndex = sourcePageIndex,
			turningReversePageIndex = null,
			underneathPageIndex = null,
			turningFrontPageSide = sourcePageSide,
			turningReversePageSide = null,
			underneathPageSide = null
		)
	}

	val oppositeSide = sourcePageSide.opposite()
	return if (logicalDirection == ReaderPageTurnLogicalDirection.Next) {
		LegacyBitmapRoles(
			turningFrontPageIndex = sourcePageIndex + 1,
			turningReversePageIndex = targetPageIndex,
			underneathPageIndex = targetPageIndex + 1,
			turningFrontPageSide = oppositeSide,
			turningReversePageSide = sourcePageSide,
			underneathPageSide = oppositeSide
		)
	} else {
		LegacyBitmapRoles(
			turningFrontPageIndex = sourcePageIndex,
			turningReversePageIndex = sourcePageIndex - 1,
			underneathPageIndex = targetPageIndex,
			turningFrontPageSide = sourcePageSide,
			turningReversePageSide = oppositeSide,
			underneathPageSide = sourcePageSide
		)
	}
}

private fun ReaderPageTurnPhysicalSide.opposite(): ReaderPageTurnPhysicalSide = when (this) {
	ReaderPageTurnPhysicalSide.Left -> ReaderPageTurnPhysicalSide.Right
	ReaderPageTurnPhysicalSide.Right -> ReaderPageTurnPhysicalSide.Left
	ReaderPageTurnPhysicalSide.Center -> ReaderPageTurnPhysicalSide.Center
}

internal class ReaderPageTurnBitmapBundle(
	val plan: ReaderPageTurnTransitionPlan,
	val surfaceRectInWindow: Rect,
	val turningFrontRectInSurface: Rect,
	val underneathRectInSurface: Rect?,
	val reverseFaceColor: Int,
	val currentBase: Bitmap,
	val turningFront: Bitmap,
	val turningReverse: Bitmap?,
	val underneath: Bitmap?,
	val finalBase: Bitmap
) {
	val renderScaleX: Float
		get() = surfaceRectInWindow.width() / currentBase.width.toFloat()
	val renderScaleY: Float
		get() = surfaceRectInWindow.height() / currentBase.height.toFloat()

	private var recycled = false

	@Synchronized
	fun recycle() {
		if (recycled) return
		recycled = true
		listOfNotNull(currentBase, turningFront, turningReverse, underneath, finalBase)
			.distinctBy { System.identityHashCode(it) }
			.forEach { bitmap ->
				if (!bitmap.isRecycled) bitmap.recycle()
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

private fun JsonObject.requiredIndex(key: String): Int =
	get(key)?.jsonPrimitive?.intOrNull?.takeUnless { it < 0 }
		?: error("Missing page-turn index: $key")

private fun JsonObject.optionalIndex(key: String): Int? =
	get(key)?.takeUnless { it === JsonNull }?.let { requiredIndex(key) }

private fun JsonObject.requiredKind(key: String): ReaderPageTurnTransitionKind = when (requiredString(key)) {
	"landscape-leaf" -> ReaderPageTurnTransitionKind.LandscapeLeaf
	"landscape-spread-slide" -> ReaderPageTurnTransitionKind.LandscapeSpreadSlide
	"portrait-leaf" -> ReaderPageTurnTransitionKind.PortraitLeaf
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

private fun JsonObject.optionalSide(key: String): ReaderPageTurnPhysicalSide? =
	get(key)?.jsonPrimitive?.contentOrNull?.let { requiredSide(key) }

private fun JsonObject.requiredString(key: String): String =
	get(key)?.jsonPrimitive?.contentOrNull ?: error("Missing page-turn value: $key")
