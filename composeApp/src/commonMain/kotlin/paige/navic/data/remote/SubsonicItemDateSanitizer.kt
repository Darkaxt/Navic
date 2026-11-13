package paige.navic.data.remote

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Navidrome emits OpenSubsonic `releaseDate` / `originalReleaseDate` as partial objects (`{}`,
 * `{"year":2001}`) when the full date is unknown. The subsonic-client library maps them to
 * `kotlinx.datetime.LocalDate`, which requires year, month and day, so one partially dated album
 * fails the whole response (and the first sync after login). Partial dates are dropped before
 * decoding; the album `year` field is separate and unaffected.
 */
internal val SubsonicItemDateSanitizer = createClientPlugin("SubsonicItemDateSanitizer") {
	client.responsePipeline.intercept(HttpResponsePipeline.Receive) { container ->
		val body = container.response
		if (body !is ByteReadChannel) return@intercept
		if (context.response.contentType()?.match(ContentType.Application.Json) != true) return@intercept
		val bytes = body.readRemaining().readByteArray()
		val text = bytes.decodeToString()
		val sanitized = sanitizeSubsonicItemDates(text)
		val output = if (sanitized === text) bytes else sanitized.encodeToByteArray()
		proceedWith(HttpResponseContainer(container.expectedType, ByteReadChannel(output)))
	}
}

private val itemDateKeys = setOf("releaseDate", "originalReleaseDate")
private val dateComponents = listOf("year", "month", "day")
private val itemDateObject = Regex("\"(?:releaseDate|originalReleaseDate)\"\\s*:\\s*\\{([^{}]*)\\}")

internal fun sanitizeSubsonicItemDates(text: String): String {
	val needsRewrite = itemDateObject.findAll(text).any { match ->
		dateComponents.any { "\"$it\"" !in match.groupValues[1] }
	}
	if (!needsRewrite) return text
	val root = runCatching { NetworkJson.compatible.parseToJsonElement(text) }.getOrNull() ?: return text
	return withoutPartialItemDates(root).toString()
}

private fun withoutPartialItemDates(element: JsonElement): JsonElement = when (element) {
	is JsonObject -> JsonObject(
		buildMap {
			element.forEach { (key, value) ->
				val partial = key in itemDateKeys && value is JsonObject && dateComponents.any { it !in value }
				if (!partial) put(key, withoutPartialItemDates(value))
			}
		}
	)
	is JsonArray -> JsonArray(element.map(::withoutPartialItemDates))
	else -> element
}
