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
	require(marker.isNotEmpty()) { "Marker must not be empty" }
	val openingIndices = readerKotlinArgumentListOpenings(source, marker)
	require(openingIndices.size == 1) {
		"Expected exactly one '$marker' argument list"
	}
	val openingIndex = openingIndices.single()
	val closingIndex = readerKotlinBalancedClosingParenthesis(source, openingIndex)
	return source.substring(openingIndex + 1, closingIndex)
}

private fun readerKotlinArgumentListOpenings(source: String, marker: String): List<Int> {
	val lexicalState = ReaderKotlinLexicalState(source)
	val markerIndices = mutableListOf<Int>()
	source.indices.forEach { index ->
		if (
			lexicalState.consume(index) &&
			source.startsWith(marker, index) &&
			readerKotlinHasIdentifierTokenBoundaries(source, index, marker.length)
		) {
			markerIndices += index
		}
	}
	lexicalState.requireComplete("source marker search")
	return markerIndices.mapNotNull { markerIndex ->
		readerKotlinArgumentListOpeningAfter(source, markerIndex + marker.length)
	}
}

private fun readerKotlinHasIdentifierTokenBoundaries(
	source: String,
	markerIndex: Int,
	markerLength: Int
): Boolean =
	!readerKotlinIsIdentifierTokenCharacter(source.getOrNull(markerIndex - 1)) &&
		!readerKotlinIsIdentifierTokenCharacter(source.getOrNull(markerIndex + markerLength))

private fun readerKotlinIsIdentifierTokenCharacter(character: Char?): Boolean =
	character != null && (character.isLetterOrDigit() || character == '_' || character == '`')

private fun readerKotlinArgumentListOpeningAfter(source: String, markerEndIndex: Int): Int? {
	val lexicalState = ReaderKotlinLexicalState(source)
	var skippingComment = false
	for (index in markerEndIndex until source.length) {
		if (lexicalState.consume(index)) {
			skippingComment = false
			if (source[index].isWhitespace()) continue
			return index.takeIf { source[index] == '(' }
		}
		if (skippingComment) continue
		if (lexicalState.isComment) {
			skippingComment = true
			continue
		}
		return null
	}
	return null
}

private fun readerKotlinBalancedClosingParenthesis(source: String, openingIndex: Int): Int {
	val lexicalState = ReaderKotlinLexicalState(source)
	var depth = 0
	for (index in openingIndex until source.length) {
		if (!lexicalState.consume(index)) continue
		when (source[index]) {
			'(' -> depth++
			')' -> {
				depth--
				if (depth == 0) return index
			}
		}
	}
	lexicalState.requireComplete("parenthesized argument list")
	throw IllegalArgumentException("Unbalanced parentheses in argument list")
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
	scanner.requireBalanced("argument list")
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
	scanner.requireBalanced("argument")
	return -1
}

private fun readerKotlinTopLevelIndexOf(source: String, target: Char): Int {
	val scanner = ReaderKotlinDelimiterScanner(source)
	source.forEachIndexed { index, character ->
		if (character == target && scanner.isTopLevel) return index
		scanner.consume(index)
	}
	scanner.requireBalanced("parameter signature")
	return -1
}

private fun readerKotlinIsAssignmentEquals(source: String, index: Int): Boolean =
	source.getOrNull(index - 1) !in listOf('=', '!', '<', '>', '+', '-', '*', '/', '%') &&
		source.getOrNull(index + 1) != '='

private fun readerKotlinIsProvenGenericInvocation(source: String, openingIndex: Int): Boolean {
	if (openingIndex == 0 || source[openingIndex - 1].isWhitespace()) return false
	val precedingCharacter = source[openingIndex - 1]
	if (!precedingCharacter.isLetterOrDigit() && precedingCharacter !in "_`") return false
	val lexicalState = ReaderKotlinLexicalState(source)
	var depth = 0
	for (index in openingIndex until source.length) {
		if (!lexicalState.consume(index)) continue
		when {
			source[index] == '<' -> depth++
			source[index] == '>' && source.getOrNull(index - 1) != '-' -> {
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

private enum class ReaderKotlinLexicalMode {
	Normal,
	OrdinaryString,
	RawTripleString,
	Character,
	LineComment,
	BlockComment
}

private class ReaderKotlinLexicalState(
	private val source: String
) {
	private var mode = ReaderKotlinLexicalMode.Normal
	private var escaped = false
	private var blockCommentDepth = 0
	private var skipThroughIndex = -1

	val isNormal: Boolean
		get() = mode == ReaderKotlinLexicalMode.Normal

	val isComment: Boolean
		get() = mode == ReaderKotlinLexicalMode.LineComment ||
			mode == ReaderKotlinLexicalMode.BlockComment

	fun consume(index: Int): Boolean {
		if (index <= skipThroughIndex) return false
		val character = source[index]
		when (mode) {
			ReaderKotlinLexicalMode.Normal -> when {
				source.startsWith("\"\"\"", index) -> {
					mode = ReaderKotlinLexicalMode.RawTripleString
					skipThroughIndex = index + 2
				}
				source.startsWith("//", index) -> {
					mode = ReaderKotlinLexicalMode.LineComment
					skipThroughIndex = index + 1
				}
				source.startsWith("/*", index) -> {
					mode = ReaderKotlinLexicalMode.BlockComment
					blockCommentDepth = 1
					skipThroughIndex = index + 1
				}
				character == '"' -> mode = ReaderKotlinLexicalMode.OrdinaryString
				character == '\'' -> mode = ReaderKotlinLexicalMode.Character
				else -> return true
			}
			ReaderKotlinLexicalMode.OrdinaryString,
			ReaderKotlinLexicalMode.Character -> when {
				escaped -> escaped = false
				character == '\\' -> escaped = true
				mode == ReaderKotlinLexicalMode.OrdinaryString && character == '"' ->
					mode = ReaderKotlinLexicalMode.Normal
				mode == ReaderKotlinLexicalMode.Character && character == '\'' ->
					mode = ReaderKotlinLexicalMode.Normal
			}
			ReaderKotlinLexicalMode.RawTripleString -> if (
				source.startsWith("\"\"\"", index)
			) {
				mode = ReaderKotlinLexicalMode.Normal
				skipThroughIndex = index + 2
			}
			ReaderKotlinLexicalMode.LineComment -> if (character == '\n' || character == '\r') {
				mode = ReaderKotlinLexicalMode.Normal
			}
			ReaderKotlinLexicalMode.BlockComment -> when {
				source.startsWith("/*", index) -> {
					blockCommentDepth++
					skipThroughIndex = index + 1
				}
				source.startsWith("*/", index) -> {
					blockCommentDepth--
					skipThroughIndex = index + 1
					if (blockCommentDepth == 0) mode = ReaderKotlinLexicalMode.Normal
				}
			}
		}
		return false
	}

	fun requireComplete(context: String) {
		if (mode == ReaderKotlinLexicalMode.LineComment) {
			mode = ReaderKotlinLexicalMode.Normal
		}
		require(mode == ReaderKotlinLexicalMode.Normal) {
			"Unterminated ${mode.description} in $context"
		}
	}

	private val ReaderKotlinLexicalMode.description: String
		get() = when (this) {
			ReaderKotlinLexicalMode.Normal -> "normal source"
			ReaderKotlinLexicalMode.OrdinaryString -> "ordinary string"
			ReaderKotlinLexicalMode.RawTripleString -> "raw triple string"
			ReaderKotlinLexicalMode.Character -> "character literal"
			ReaderKotlinLexicalMode.LineComment -> "line comment"
			ReaderKotlinLexicalMode.BlockComment -> "block comment"
		}
}

private class ReaderKotlinDelimiterScanner(
	private val source: String
) {
	private val lexicalState = ReaderKotlinLexicalState(source)
	private var parentheses = 0
	private var angles = 0
	private var brackets = 0
	private var braces = 0
	private var expression = false

	val isTopLevel: Boolean
		get() = lexicalState.isNormal &&
			parentheses == 0 &&
			angles == 0 &&
			brackets == 0 &&
			braces == 0

	fun startNextSegment() {
		expression = false
	}

	fun consume(index: Int) {
		if (!lexicalState.consume(index)) return
		val character = source[index]
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

	fun requireBalanced(context: String) {
		lexicalState.requireComplete(context)
		require(isTopLevel) {
			"Unsupported or unbalanced delimiters in $context: $source"
		}
	}
}
