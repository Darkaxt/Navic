package paige.navic.reader

internal data class ReaderKotlinParameterContract(
	val name: String,
	val type: String
)

private val ReaderKotlinParameterModifiers = setOf("crossinline", "noinline", "vararg")

internal fun readerKotlinDeclarationParameterContract(
	source: String,
	declaration: String
): List<ReaderKotlinParameterContract> =
	readerKotlinTopLevelParts(readerKotlinSingleArgumentList(source, declaration))
		.filter(String::isNotBlank)
		.map { parameter ->
			val defaultIndex = readerKotlinTopLevelIndexOf(parameter, '=')
			val signature = readerKotlinWithoutAnnotations(
				parameter.substring(0, defaultIndex.takeIf { it >= 0 } ?: parameter.length)
			)
			val colonIndex = readerKotlinTopLevelIndexOf(signature, ':')
			require(colonIndex >= 0) { "Parameter has no top-level type separator: $parameter" }
			val nameParts = signature
				.substring(0, colonIndex)
				.trim()
				.split(Regex("\\s+"))
				.filterNot { it in ReaderKotlinParameterModifiers }
			require(nameParts.size == 1) { "Parameter has no unambiguous name: $parameter" }
			val type = signature
				.substring(colonIndex + 1)
				.filterNot(Char::isWhitespace)
			require(type.isNotEmpty()) { "Parameter has no type: $parameter" }
			ReaderKotlinParameterContract(name = nameParts.single(), type = type)
		}

internal fun readerKotlinNamedCallArgumentNames(
	source: String,
	call: String
): List<String> =
	readerKotlinTopLevelParts(readerKotlinSingleArgumentList(source, call))
		.filter(String::isNotBlank)
		.map { argument ->
			val assignmentIndex = readerKotlinTopLevelIndexOf(argument, '=')
			require(assignmentIndex >= 0) { "Call argument is not named: $argument" }
			argument.substring(0, assignmentIndex).trim()
		}

private fun readerKotlinSingleArgumentList(source: String, marker: String): String {
	val markerIndex = source.indexOf(marker)
	require(markerIndex >= 0 && source.indexOf(marker, markerIndex + marker.length) < 0) {
		"Expected exactly one '$marker'"
	}
	var openingIndex = markerIndex + marker.length
	while (openingIndex < source.length && source[openingIndex].isWhitespace()) openingIndex++
	require(source.getOrNull(openingIndex) == '(') {
		"Expected '$marker' to be followed by an argument list"
	}
	val closingIndex = readerKotlinBalancedClosingParenthesis(source, openingIndex)
	return source.substring(openingIndex + 1, closingIndex)
}

private fun readerKotlinBalancedClosingParenthesis(source: String, openingIndex: Int): Int {
	val scanner = ReaderKotlinDelimiterScanner()
	for (index in openingIndex until source.length) {
		val character = source[index]
		scanner.consume(character)
		if (character == ')' && scanner.isTopLevel) return index
	}
	error("Unbalanced argument list")
}

private fun readerKotlinTopLevelParts(source: String): List<String> {
	val parts = mutableListOf<String>()
	val scanner = ReaderKotlinDelimiterScanner()
	var partStart = 0
	source.forEachIndexed { index, character ->
		if (character == ',' && scanner.isTopLevel) {
			parts += source.substring(partStart, index).trim()
			partStart = index + 1
		} else {
			scanner.consume(character)
		}
	}
	parts += source.substring(partStart).trim()
	return parts
}

private fun readerKotlinTopLevelIndexOf(source: String, target: Char): Int {
	val scanner = ReaderKotlinDelimiterScanner()
	source.forEachIndexed { index, character ->
		if (character == target && scanner.isTopLevel) return index
		scanner.consume(character)
	}
	return -1
}

private fun readerKotlinWithoutAnnotations(source: String): String {
	val result = StringBuilder(source.length)
	var index = 0
	while (index < source.length) {
		if (source[index] != '@') {
			result.append(source[index++])
			continue
		}

		index++
		while (
			index < source.length &&
			(source[index].isLetterOrDigit() || source[index] in "_.:")
		) {
			index++
		}
		if (source.getOrNull(index) == '(') {
			val closingIndex = readerKotlinBalancedClosingParenthesis(source, index)
			var followingIndex = closingIndex + 1
			while (
				followingIndex < source.length &&
				source[followingIndex].isWhitespace()
			) {
				followingIndex++
			}
			if (!source.startsWith("->", followingIndex)) index = closingIndex + 1
		}
		result.append(' ')
	}
	return result.toString()
}

private class ReaderKotlinDelimiterScanner {
	private var parentheses = 0
	private var angles = 0
	private var brackets = 0
	private var braces = 0
	private var quote: Char? = null
	private var escaped = false

	val isTopLevel: Boolean
		get() = quote == null && parentheses == 0 && angles == 0 && brackets == 0 && braces == 0

	fun consume(character: Char) {
		if (quote != null) {
			when {
				escaped -> escaped = false
				character == '\\' -> escaped = true
				character == quote -> quote = null
			}
			return
		}
		if (character == '"' || character == '\'') {
			quote = character
			return
		}
		when (character) {
			'(' -> parentheses++
			')' -> parentheses--
			'<' -> angles++
			'>' -> if (angles > 0) angles--
			'[' -> brackets++
			']' -> brackets--
			'{' -> braces++
			'}' -> braces--
		}
	}
}
