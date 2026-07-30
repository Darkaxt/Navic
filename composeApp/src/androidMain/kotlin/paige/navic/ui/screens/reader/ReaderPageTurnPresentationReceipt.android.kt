package paige.navic.ui.screens.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal const val ReaderPageTurnPresentationMaximumSafeInteger = 9_007_199_254_740_991L

internal enum class ReaderPageTurnPresentationScope(val wireValue: String) {
	Preview("preview"),
	Live("live");

	companion object {
		fun fromWireValue(value: String): ReaderPageTurnPresentationScope? =
			entries.firstOrNull { it.wireValue == value }
	}
}

internal data class ReaderPageTurnPresentationReceipt(
	val scope: ReaderPageTurnPresentationScope,
	val token: String,
	val pageIndex: Long,
	val presentationSequence: Long,
	val previewGeneration: Long? = null,
	val foliateSessionId: String? = null,
	val rasterGeneration: Long? = null,
	val textureGeneration: Long? = null
) {
	init {
		require(token.isNotEmpty()) { "Receipt token must not be empty" }
		requirePageTurnNonNegativeWireInteger(pageIndex, "pageIndex")
		requirePageTurnPositiveWireInteger(presentationSequence, "presentationSequence")
		when (scope) {
			ReaderPageTurnPresentationScope.Preview -> {
				requirePageTurnNonNegativeWireInteger(
					requireNotNull(previewGeneration),
					"previewGeneration"
				)
				require(foliateSessionId == null && rasterGeneration == null && textureGeneration == null)
			}
			ReaderPageTurnPresentationScope.Live -> {
				require(previewGeneration == null)
				require(!foliateSessionId.isNullOrEmpty()) { "Live session must not be empty" }
				requirePageTurnNonNegativeWireInteger(
					requireNotNull(rasterGeneration),
					"rasterGeneration"
				)
				requirePageTurnNonNegativeWireInteger(
					requireNotNull(textureGeneration),
					"textureGeneration"
				)
			}
		}
	}
}

internal sealed interface ReaderPageTurnPresentationTarget {
	val token: String
	val pageIndex: Long

	data class Preview(
		override val token: String,
		override val pageIndex: Long,
		val previewGeneration: Long
	) : ReaderPageTurnPresentationTarget {
		init {
			require(token.isNotEmpty()) { "Preview target token must not be empty" }
			requirePageTurnNonNegativeWireInteger(pageIndex, "pageIndex")
			requirePageTurnNonNegativeWireInteger(previewGeneration, "previewGeneration")
		}
	}

	data class Live(
		override val token: String,
		override val pageIndex: Long,
		val foliateSessionId: String,
		val rasterGeneration: Long,
		val textureGeneration: Long
	) : ReaderPageTurnPresentationTarget {
		init {
			require(token.isNotEmpty()) { "Live target token must not be empty" }
			requirePageTurnNonNegativeWireInteger(pageIndex, "pageIndex")
			require(foliateSessionId.isNotEmpty()) { "Live target session must not be empty" }
			requirePageTurnNonNegativeWireInteger(rasterGeneration, "rasterGeneration")
			requirePageTurnNonNegativeWireInteger(textureGeneration, "textureGeneration")
		}
	}
}

internal fun readerPageTurnPresentationReceipt(
	encoded: String?
): ReaderPageTurnPresentationReceipt? = runCatching {
	val candidate = encoded.readerPageTurnPresentationObject() ?: return@runCatching null
	val scope = candidate.nonEmptyString("scope")
		?.let(ReaderPageTurnPresentationScope::fromWireValue)
		?: return@runCatching null
	val token = candidate.nonEmptyString("token") ?: return@runCatching null
	val pageIndex = candidate.nonNegativeLong("pageIndex") ?: return@runCatching null
	val presentationSequence = candidate.positiveLong("presentationSequence")
		?: return@runCatching null

	when (scope) {
		ReaderPageTurnPresentationScope.Preview -> {
			if (candidate.keysSet() != PreviewReceiptKeys) return@runCatching null
			ReaderPageTurnPresentationReceipt(
				scope = scope,
				token = token,
				pageIndex = pageIndex,
				previewGeneration = candidate.nonNegativeLong("previewGeneration")
					?: return@runCatching null,
				presentationSequence = presentationSequence
			)
		}
		ReaderPageTurnPresentationScope.Live -> {
			if (candidate.keysSet() != LiveReceiptKeys) return@runCatching null
			ReaderPageTurnPresentationReceipt(
				scope = scope,
				token = token,
				pageIndex = pageIndex,
				foliateSessionId = candidate.nonEmptyString("foliateSessionId")
					?: return@runCatching null,
				rasterGeneration = candidate.nonNegativeLong("rasterGeneration")
					?: return@runCatching null,
				textureGeneration = candidate.nonNegativeLong("textureGeneration")
					?: return@runCatching null,
				presentationSequence = presentationSequence
			)
		}
	}
}.getOrNull()

internal fun ReaderPageTurnPresentationReceipt.matches(
	target: ReaderPageTurnPresentationTarget
): Boolean {
	if (token != target.token || pageIndex != target.pageIndex) return false
	return when (target) {
		is ReaderPageTurnPresentationTarget.Preview ->
			scope == ReaderPageTurnPresentationScope.Preview &&
				previewGeneration == target.previewGeneration &&
				foliateSessionId == null &&
				rasterGeneration == null &&
				textureGeneration == null
		is ReaderPageTurnPresentationTarget.Live ->
			scope == ReaderPageTurnPresentationScope.Live &&
				previewGeneration == null &&
				foliateSessionId == target.foliateSessionId &&
				rasterGeneration == target.rasterGeneration &&
				textureGeneration == target.textureGeneration
	}
}

internal fun readerPageTurnPresentationReceiptAccepted(
	target: ReaderPageTurnPresentationTarget,
	initialReceipt: ReaderPageTurnPresentationReceipt?,
	finalReceipt: ReaderPageTurnPresentationReceipt?,
	foregroundSuccess: Boolean
): Boolean =
	foregroundSuccess &&
		initialReceipt != null &&
		initialReceipt.matches(target) &&
		initialReceipt == finalReceipt

private val PreviewReceiptKeys = setOf(
	"scope",
	"token",
	"pageIndex",
	"previewGeneration",
	"presentationSequence"
)

private val LiveReceiptKeys = setOf(
	"scope",
	"token",
	"pageIndex",
	"foliateSessionId",
	"rasterGeneration",
	"textureGeneration",
	"presentationSequence"
)

private fun String?.readerPageTurnPresentationObject(): JsonObject? {
	val raw = orEmpty().trim()
	if (raw.isEmpty()) return null
	val firstPass = Json.parseToJsonElement(raw)
	val decoded = if (firstPass is JsonPrimitive && firstPass.isString) {
		Json.parseToJsonElement(firstPass.content)
	} else {
		firstPass
	}
	return decoded as? JsonObject
}

private fun JsonObject.keysSet(): Set<String> = keys

private fun JsonObject.nonEmptyString(name: String): String? =
	(get(name) as? JsonPrimitive)
		?.takeIf(JsonPrimitive::isString)
		?.contentOrNull
		?.takeIf(String::isNotEmpty)

private fun JsonObject.nonNegativeLong(name: String): Long? =
	integralLong(name)?.takeIf { it in 0L..ReaderPageTurnPresentationMaximumSafeInteger }

private fun JsonObject.positiveLong(name: String): Long? =
	integralLong(name)?.takeIf { it in 1L..ReaderPageTurnPresentationMaximumSafeInteger }

private fun JsonObject.integralLong(name: String): Long? =
	(get(name) as? JsonPrimitive)
		?.takeUnless(JsonPrimitive::isString)
		?.longOrNull

private fun requirePageTurnNonNegativeWireInteger(value: Long, name: String) {
	require(value in 0L..ReaderPageTurnPresentationMaximumSafeInteger) {
		"$name must be a non-negative JavaScript safe integer"
	}
}

private fun requirePageTurnPositiveWireInteger(value: Long, name: String) {
	require(value in 1L..ReaderPageTurnPresentationMaximumSafeInteger) {
		"$name must be a positive JavaScript safe integer"
	}
}
