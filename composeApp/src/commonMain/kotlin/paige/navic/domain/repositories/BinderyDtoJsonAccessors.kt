package paige.navic.domain.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun Map<String, JsonElement>.jsonArray(key: String): JsonArray =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonArray
		?: JsonArray(emptyList())

internal fun Map<String, JsonElement>.jsonObject(key: String): JsonObject? =
	entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonObject

internal fun Map<String, JsonElement>.stringValue(key: String): String? =
	(entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value as? JsonPrimitive)
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotEmpty() }

internal fun Map<String, JsonElement>.intValue(key: String): Int? =
	stringValue(key)?.toIntOrNull()

internal fun Map<String, JsonElement>.longValue(key: String): Long? =
	stringValue(key)?.toLongOrNull()

internal fun Map<String, JsonElement>.doubleValue(key: String): Double? =
	stringValue(key)?.toDoubleOrNull()

internal fun JsonObject.stringValue(key: String): String? =
	(get(key) as? JsonPrimitive)
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotEmpty() }

internal fun JsonObject.booleanValue(key: String): Boolean? =
	stringValue(key)?.toBooleanStrictOrNull()

internal fun JsonObject.intValue(key: String): Int? =
	stringValue(key)?.toIntOrNull()

internal fun JsonObject.longValue(key: String): Long? =
	stringValue(key)?.toLongOrNull()

internal fun JsonObject.doubleValue(key: String): Double? =
	stringValue(key)?.toDoubleOrNull()

internal fun JsonObject.stringList(key: String): List<String> =
	when (val value = get(key)) {
		is JsonArray -> value.mapNotNull { element ->
			(element as? JsonPrimitive)
				?.contentOrNull
				?.trim()
				?.takeIf { it.isNotEmpty() }
		}
		is JsonPrimitive -> value.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.split(',')
			?.mapNotNull { item -> item.trim().takeIf(String::isNotEmpty) }
			.orEmpty()
		else -> emptyList()
	}

internal fun JsonObject.objectList(key: String): List<JsonObject> =
	(get(key) as? JsonArray)
		?.mapNotNull { element -> element as? JsonObject }
		.orEmpty()

internal fun JsonObject.toAvailabilityCombination(): BinderyAvailabilityCombination? {
	val format = stringValue("format") ?: return null
	val language = stringValue("language") ?: return null
	return BinderyAvailabilityCombination(
		format = format,
		language = language
	)
}

internal fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { desiredKey ->
		entries.firstOrNull { (key, value) ->
			key.equals(desiredKey, ignoreCase = true) && value.isNotBlank()
		}?.value?.trim()
	}

internal fun JsonElement?.toRelList(): List<String> =
	when (this) {
		null -> emptyList()
		is JsonPrimitive -> listOfNotNull(contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
		is JsonArray -> mapNotNull { element ->
			element.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
		}
		else -> emptyList()
	}
