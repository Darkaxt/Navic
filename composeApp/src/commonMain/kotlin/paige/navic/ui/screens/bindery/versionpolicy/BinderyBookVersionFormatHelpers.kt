package paige.navic.ui.screens.bindery.versionpolicy

import kotlin.math.roundToLong

internal fun String.isGenericBookMediaFormat(): Boolean =
	equals("EBOOK", ignoreCase = true) ||
		equals("BOOK", ignoreCase = true) ||
		equals("AUDIO", ignoreCase = true) ||
		equals("AUDIOBOOK", ignoreCase = true)

internal fun String?.ebookFormatQualityRank(): Int =
	when (this?.uppercase()) {
		"EPUB" -> 50
		"PDF" -> 40
		"AZW3" -> 35
		"MOBI" -> 30
		"CBZ" -> 25
		"TXT" -> 10
		else -> 0
	}

internal fun String?.audioFormatQualityRank(): Int =
	when (this?.uppercase()) {
		"FLAC" -> 60
		"M4B" -> 55
		"M4A" -> 50
		"AAC" -> 45
		"MP3" -> 40
		"OGG" -> 30
		else -> 0
	}

internal fun Long.toBitrateLabel(): String =
	"${(this / 1000).coerceAtLeast(1)} kbps"

internal fun Long.toSampleRateLabel(): String {
	val khz = this.toDouble() / 1000.0
	return if (khz % 1.0 == 0.0) {
		"${khz.toInt()} kHz"
	} else {
		"${((khz * 10).roundToLong() / 10.0)} kHz"
	}
}

internal fun String.fileExtension(): String? {
	val extension = substringAfterLast('.', missingDelimiterValue = "")
		.substringBefore('?')
		.substringBefore('#')
		.trim()
	return extension
		.takeIf { it.length in 2..6 && it.all(Char::isLetterOrDigit) }
		?.uppercase()
}

internal fun String.fileNameStem(): String? {
	val fileName = substringBefore('?')
		.substringBefore('#')
		.substringAfterLast('/')
		.substringAfterLast('\\')
		.trim()
		.takeIf { it.isNotEmpty() }
		?: return null
	val extension = fileName.fileExtension()
	val stem = if (extension != null && fileName.length > extension.length + 1) {
		fileName.dropLast(extension.length + 1)
	} else {
		fileName
	}
	return stem.trim().takeIf { it.isNotEmpty() }
}

internal fun String.leadingBracketLabel(): String? {
	val trimmed = trim()
	if (trimmed.startsWith("[")) {
		val end = trimmed.indexOf(']')
		if (end > 1) return trimmed.substring(1, end).trim().takeIf { it.isNotEmpty() }
	}
	if (trimmed.startsWith("(")) {
		val end = trimmed.indexOf(')')
		if (end > 1) return trimmed.substring(1, end).trim().takeIf { it.isNotEmpty() }
	}
	return null
}

internal fun String?.isEbookMediaType(): Boolean =
	this?.let { mediaType ->
		"epub" in mediaType.lowercase() ||
			"pdf" in mediaType.lowercase() ||
			"azw3" in mediaType.lowercase() ||
			"mobi" in mediaType.lowercase() ||
			"ebook" in mediaType.lowercase()
	} == true

internal fun String?.toReadableBookFormat(): String? {
	val normalized = this?.lowercase() ?: return null
	return when {
		"epub" in normalized -> "EPUB"
		"pdf" in normalized -> "PDF"
		"azw3" in normalized -> "AZW3"
		"mobi" in normalized -> "MOBI"
		"audiobook" in normalized -> "Audiobook"
		"mpeg" in normalized -> "MP3"
		"mp4" in normalized -> "M4A"
		"aac" in normalized -> "AAC"
		"flac" in normalized -> "FLAC"
		"ogg" in normalized -> "OGG"
		else -> substringAfter('/').substringBefore(';').uppercase().takeIf { it.isNotBlank() }
	}
}

internal fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { desiredKey ->
		entries.firstOrNull { (key, value) ->
			key.equals(desiredKey, ignoreCase = true) && value.isNotBlank()
		}?.value?.trim()
	}

internal fun Map<String, String>.hasTruthyValue(vararg keys: String): Boolean =
	keys.any { desiredKey ->
		entries.any { (key, value) ->
			key.equals(desiredKey, ignoreCase = true) &&
				value.trim().lowercase() in setOf("1", "true", "yes", "y")
		}
	}

internal fun String.displayToken(): String =
	trim()
		.replace(Regex("[_-]+"), " ")
		.replace(Regex("\\s+"), " ")
		.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
