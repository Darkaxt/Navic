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
		(kind == ReaderPageTurnTransitionKind.LandscapeLeaf) == spread

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
			val turningReversePageIndex = json.optionalIndex("turningReversePageIndex")
			val underneathPageIndex = json.optionalIndex("underneathPageIndex")
			val turningReversePageSide = json.optionalSide("turningReversePageSide")
			val underneathPageSide = json.optionalSide("underneathPageSide")
			require((turningReversePageIndex == null) == (turningReversePageSide == null)) {
				"Reverse page index and side must be supplied together"
			}
			require((underneathPageIndex == null) == (underneathPageSide == null)) {
				"Underneath page index and side must be supplied together"
			}
			return ReaderPageTurnTransitionPlan(
				token = token,
				generation = generation,
				kind = json.requiredKind("kind"),
				logicalDirection = json.requiredLogicalDirection("logicalDirection"),
				sourcePageIndex = json.requiredIndex("sourcePageIndex"),
				turningFrontPageIndex = json.requiredIndex("turningFrontPageIndex"),
				turningReversePageIndex = turningReversePageIndex,
				underneathPageIndex = underneathPageIndex,
				targetPageIndex = json.requiredIndex("targetPageIndex"),
				sourcePageSide = json.requiredSide("sourcePageSide"),
				targetPageSide = json.requiredSide("targetPageSide"),
				turningFrontPageSide = json.requiredSide("turningFrontPageSide"),
				turningReversePageSide = turningReversePageSide,
				underneathPageSide = underneathPageSide
			)
		}
	}
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

private fun JsonObject.requiredIndex(key: String): Int =
	get(key)?.jsonPrimitive?.intOrNull?.takeUnless { it < 0 }
		?: error("Missing page-turn index: $key")

private fun JsonObject.optionalIndex(key: String): Int? =
	get(key)?.takeUnless { it === JsonNull }?.let { requiredIndex(key) }

private fun JsonObject.requiredKind(key: String): ReaderPageTurnTransitionKind = when (requiredString(key)) {
	"landscape-leaf" -> ReaderPageTurnTransitionKind.LandscapeLeaf
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
