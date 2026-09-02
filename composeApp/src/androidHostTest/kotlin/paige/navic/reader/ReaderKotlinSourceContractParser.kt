package paige.navic.reader

internal data class ReaderKotlinParameterContract(
	val name: String,
	val type: String
)

private val ReaderKotlinParameterModifiers = setOf("crossinline", "noinline", "vararg")
private val ReaderKotlinIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

internal fun readerKotlinDeclarationParameterContract(
	source: String,
	declaration: String
): List<ReaderKotlinParameterContract> =
	readerKotlinTopLevelParts(readerKotlinSingleArgumentList(source, declaration))
		.map { parameter ->
			val defaultIndex = readerKotlinTopLevelAssignmentIndex(parameter)
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
			require(nameParts.size == 1 && ReaderKotlinIdentifier.matches(nameParts.single())) {
				"Parameter has no single valid name: $parameter"
			}
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
		.map { argument ->
			val assignmentIndex = readerKotlinTopLevelAssignmentIndex(argument)
			require(assignmentIndex >= 0) { "Call argument is not a single named assignment: $argument" }
			val name = argument.substring(0, assignmentIndex).trim()
			require(ReaderKotlinIdentifier.matches(name)) {
				"Call argument has no single valid name: $argument"
			}
			name
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
	var depth = 0
	var quote: Char? = null
	var escaped = false
	for (index in openingIndex until source.length) {
		val character = source[index]
		if (quote != null) {
			when {
				escaped -> escaped = false
				character == '\\' -> escaped = true
				character == quote -> quote = null
			}
			continue
		}
		if (character == '"' || character == '\'') {
			quote = character
			continue
		}
		when (character) {
			'(' -> depth++
			')' -> {
				depth--
				if (depth == 0) return index
			}
		}
	}
	throw IllegalArgumentException("Unbalanced parentheses or unterminated literal")
}

private fun readerKotlinTopLevelParts(source: String): List<String> {
	val parts = mutableListOf<String>()
	val scanner = ReaderKotlinDelimiterScanner(source)
	var partStart = 0
	source.forEachIndexed { index, character ->
		if (character == ',' && scanner.isTopLevel) {
			parts += source.substring(partStart, index).trim()
			partStart = index + 1
			scanner.startNextSegment()
		} else {
			scanner.consume(index)
		}
	}
	require(scanner.isTopLevel) {
		"Unsupported or unbalanced delimiters in argument list: $source"
	}
	parts += source.substring(partStart).trim()
	val withoutTrailingComma = if (parts.lastOrNull().isNullOrBlank()) parts.dropLast(1) else parts
	require(withoutTrailingComma.none(String::isBlank)) {
		"Empty top-level argument segment in: $source"
	}
	return withoutTrailingComma
}

private fun readerKotlinTopLevelAssignmentIndex(source: String): Int {
	val scanner = ReaderKotlinDelimiterScanner(source)
	source.forEachIndexed { index, character ->
		if (
			character == '=' &&
			scanner.isTopLevel &&
			readerKotlinIsAssignmentEquals(source, index)
		) {
			return index
		}
		scanner.consume(index)
	}
	require(scanner.isTopLevel) {
		"Unsupported or unbalanced delimiters in argument: $source"
	}
	return -1
}

private fun readerKotlinTopLevelIndexOf(source: String, target: Char): Int {
	val scanner = ReaderKotlinDelimiterScanner(source)
	source.forEachIndexed { index, character ->
		if (character == target && scanner.isTopLevel) return index
		scanner.consume(index)
	}
	require(scanner.isTopLevel) {
		"Unsupported or unbalanced delimiters in parameter signature: $source"
	}
	return -1
}

private fun readerKotlinIsAssignmentEquals(source: String, index: Int): Boolean =
	source.getOrNull(index - 1) !in listOf('=', '!', '<', '>', '+', '-', '*', '/', '%') &&
		source.getOrNull(index + 1) != '='

private fun readerKotlinIsProvenGenericInvocation(source: String, openingIndex: Int): Boolean {
	if (openingIndex == 0 || source[openingIndex - 1].isWhitespace()) return false
	val precedingCharacter = source[openingIndex - 1]
	if (!precedingCharacter.isLetterOrDigit() && precedingCharacter !in "_`") return false
	var depth = 0
	var quote: Char? = null
	var escaped = false
	for (index in openingIndex until source.length) {
		val character = source[index]
		if (quote != null) {
			when {
				escaped -> escaped = false
				character == '\\' -> escaped = true
				character == quote -> quote = null
			}
			continue
		}
		if (character == '"' || character == '\'') {
			quote = character
			continue
		}
		when {
			character == '<' -> depth++
			character == '>' && source.getOrNull(index - 1) != '-' -> {
				depth--
				if (depth == 0) {
					var followingIndex = index + 1
					while (
						followingIndex < source.length &&
						source[followingIndex].isWhitespace()
					) {
						followingIndex++
					}
					return source.getOrNull(followingIndex) == '('
				}
			}
		}
	}
	return false
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

private class ReaderKotlinDelimiterScanner(
	private val source: String
) {
	private var parentheses = 0
	private var angles = 0
	private var brackets = 0
	private var braces = 0
	private var quote: Char? = null
	private var escaped = false
	private var expression = false

	val isTopLevel: Boolean
		get() = quote == null && parentheses == 0 && angles == 0 && brackets == 0 && braces == 0

	fun startNextSegment() {
		expression = false
	}

	fun consume(index: Int) {
		val character = source[index]
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
		when {
			character == '=' && isTopLevel && readerKotlinIsAssignmentEquals(source, index) ->
				expression = true
			character == '(' -> parentheses++
			character == ')' -> parentheses--
			character == '<' && (
				angles > 0 ||
				!expression ||
				readerKotlinIsProvenGenericInvocation(source, index)
			) -> angles++
			character == '>' && angles > 0 && source.getOrNull(index - 1) != '-' -> angles--
			character == '[' -> brackets++
			character == ']' -> brackets--
			character == '{' -> braces++
			character == '}' -> braces--
		}
	}
}
