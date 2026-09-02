package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReaderKotlinSourceContractParserTest {
	@Test
	fun equivalentHostDeclarationsIgnoreFormattingAndExpectDefaults() {
		val expectSource = """
			@Composable
			expect fun Host(
				items: List<String?>,
				onItem: @Composable (item: Pair<String, Int?>) -> Unit,
				modifier: Modifier = Modifier,
			)
		""".trimIndent()
		val actualSource = """
			@Composable
			actual fun Host(items:List<String?>, onItem:@Composable(item:Pair<String,Int?>)->Unit, modifier: Modifier) { }
		""".trimIndent()
		val differentlyDefaultedSource = """
			actual fun Host(
			  items : List < String ? > = emptyList(),
			  onItem : @Composable (item : Pair < String, Int ? >) -> Unit = { _, _ -> },
			  modifier : Modifier = defaultModifier()
			  ) { }
		""".trimIndent()

		val expected = readerKotlinDeclarationParameterContract(expectSource, "expect fun Host")
		assertEquals(expected, readerKotlinDeclarationParameterContract(actualSource, "actual fun Host"))
		assertEquals(
			expected,
			readerKotlinDeclarationParameterContract(
				differentlyDefaultedSource,
				"actual fun Host"
			)
		)
	}

	@Test
	fun comparisonDefaultRetainsFollowingParameter() {
		val parsed = readerKotlinDeclarationParameterContract(
			"expect fun Host(enabled: Boolean = lower < upper, onDone: () -> Unit)",
			"expect fun Host"
		)

		assertEquals(2, parsed.size)
		assertEquals(listOf("enabled", "onDone"), parsed.map { it.name })
	}

	@Test
	fun declarationDefaultsRetainAllParametersAcrossOperatorsGenericsAndLiterals() {
		val cases = listOf(
			Triple(
				"generic call",
				"expect fun Host(value: Map<String, Int> = mapOf<String, Int>(), onDone: () -> Unit)",
				listOf("value", "onDone")
			),
			Triple(
				"nested generic call",
				"expect fun Host(value: Map<String, List<Int>> = factory<Map<String, List<Int>>, Set<Long>>(), onDone: () -> Unit)",
				listOf("value", "onDone")
			),
			Triple(
				"paired comparisons",
				"expect fun Host(enabled: Boolean = lower < upper && upper > floor, onDone: () -> Unit)",
				listOf("enabled", "onDone")
			),
			Triple(
				"shift and arrows",
				"expect fun Host(transform: (Int) -> Int = { value -> value shl 1 }, mask: Int = value shr 1, onDone: () -> Unit)",
				listOf("transform", "mask", "onDone")
			),
			Triple(
				"quoted angles and commas",
				"expect fun Host(label: String = \"<,>\", marker: Char = '<', onDone: () -> Unit)",
				listOf("label", "marker", "onDone")
			)
		)

		cases.forEach { (label, source, expectedNames) ->
			val parsed = readerKotlinDeclarationParameterContract(source, "expect fun Host")
			assertEquals(expectedNames.size, parsed.size, label)
			assertEquals(expectedNames, parsed.map { it.name }, label)
		}
	}

	@Test
	fun namedCallsRetainAllArgumentsAcrossOperatorsGenericsAndLiterals() {
		val cases = listOf(
			Triple(
				"comparison",
				"Host(enabled = lower < upper, onDone = callback)",
				listOf("enabled", "onDone")
			),
			Triple(
				"generic call",
				"Host(value = mapOf<String, Int>(), onDone = callback)",
				listOf("value", "onDone")
			),
			Triple(
				"nested generic call",
				"Host(value = factory<Map<String, List<Int>>, Set<Long>>(), onDone = callback)",
				listOf("value", "onDone")
			),
			Triple(
				"paired comparisons",
				"Host(enabled = lower < upper && upper > floor, onDone = callback)",
				listOf("enabled", "onDone")
			),
			Triple(
				"shift and arrow",
				"Host(transform = { value -> value shl 1 }, mask = value shr 1, onDone = callback)",
				listOf("transform", "mask", "onDone")
			),
			Triple(
				"quoted angles and commas",
				"Host(label = \"<,>\", marker = '<', onDone = callback)",
				listOf("label", "marker", "onDone")
			)
		)

		cases.forEach { (label, source, expectedNames) ->
			val names = readerKotlinNamedCallArgumentNames(source, "Host")
			assertEquals(expectedNames.size, names.size, label)
			assertEquals(expectedNames, names, label)
		}
	}

	@Test
	fun malformedOrAmbiguousSyntaxFailsClosedInsteadOfReturningPartialContracts() {
		val declarationCases = mapOf(
			"unbalanced generic type" to "expect fun Host(value: Map<String, Int, onDone: () -> Unit)",
			"unbalanced bracket" to "expect fun Host(value: List<Int> = values[index, onDone: () -> Unit)",
			"unbalanced quote" to "expect fun Host(label: String = \"<,>, onDone: () -> Unit)",
			"empty segment" to "expect fun Host(value: Int = 1,, onDone: () -> Unit)",
			"unsupported spaced generic invocation" to "expect fun Host(value: Map<String, Int> = mapOf <String, Int>(), onDone: () -> Unit)"
		)
		val callCases = mapOf(
			"empty call segment" to "Host(value = 1,, onDone = callback)",
			"positional call argument" to "Host(value, onDone = callback)",
			"unsupported spaced generic call" to "Host(value = mapOf <String, Int>(), onDone = callback)"
		)

		declarationCases.forEach { (label, source) ->
			val failure = assertFails(label) {
				readerKotlinDeclarationParameterContract(source, "expect fun Host")
			}
			assertTrue(!failure.message.isNullOrBlank(), label)
		}
		callCases.forEach { (label, source) ->
			val failure = assertFails(label) {
				readerKotlinNamedCallArgumentNames(source, "Host")
			}
			assertTrue(!failure.message.isNullOrBlank(), label)
		}
	}

	@Test
	fun declarationContractHandlesAnnotationsModifiersAndNestedDelimiters() {
		val expectSource = """
			expect fun AnnotatedHost(
				@ReaderFixture(names = ["left", "right"])
				noinline onResult: @Composable (
					value: Map<String, List<Int?>>
				) -> Result<Unit> = defaultHandler({ left, right -> left to right }),
				values: Array<List<String?>> = arrayOf(listOf("a", "b")),
			)
		""".trimIndent()
		val actualSource = """
			actual fun AnnotatedHost(onResult:@Composable(value:Map<String,List<Int?>>)->Result<Unit>, values:Array<List<String?>>)
		""".trimIndent()

		assertEquals(
			readerKotlinDeclarationParameterContract(expectSource, "expect fun AnnotatedHost"),
			readerKotlinDeclarationParameterContract(actualSource, "actual fun AnnotatedHost")
		)
	}

	@Test
	fun declarationContractRejectsStructuralParameterChanges() {
		val expectSource = """
			expect fun Host(
				items: List<String?>,
				onItem: @Composable (item: Pair<String, Int?>) -> Unit,
				modifier: Modifier = Modifier,
			)
		""".trimIndent()
		val expected = readerKotlinDeclarationParameterContract(expectSource, "expect fun Host")
		val changedDeclarations = mapOf(
			"reordered" to "actual fun Host(onItem: @Composable (item: Pair<String, Int?>) -> Unit, items: List<String?>, modifier: Modifier)",
			"missing" to "actual fun Host(items: List<String?>, onItem: @Composable (item: Pair<String, Int?>) -> Unit)",
			"extra" to "actual fun Host(items: List<String?>, onItem: @Composable (item: Pair<String, Int?>) -> Unit, modifier: Modifier, enabled: Boolean)",
			"renamed" to "actual fun Host(values: List<String?>, onItem: @Composable (item: Pair<String, Int?>) -> Unit, modifier: Modifier)",
			"type-changed" to "actual fun Host(items: List<String>, onItem: @Composable (item: Pair<String, Int?>) -> Unit, modifier: Modifier)"
		)

		changedDeclarations.forEach { (label, declaration) ->
			assertNotEquals(
				expected,
				readerKotlinDeclarationParameterContract(declaration, "actual fun Host"),
				label
			)
		}
	}

	@Test
	fun namedCallParserHandlesNestedArgumentsAndRequiresOneIntendedCall() {
		val callSource = """
			Host(
				first = values[indexes[1]],
				callback = { left, right -> consume(Pair(left, right)) },
				mapping = mapOf("a" to listOf(1, 2)),
			)
		""".trimIndent()

		assertEquals(
			listOf("first", "callback", "mapping"),
			readerKotlinNamedCallArgumentNames(callSource, "Host")
		)
		assertFailsWith<IllegalArgumentException> {
			readerKotlinDeclarationParameterContract("expect fun Other()", "expect fun Host")
		}
		assertFailsWith<IllegalArgumentException> {
			readerKotlinDeclarationParameterContract(
				"expect fun Host()\nexpect fun Host(value: Int)",
				"expect fun Host"
			)
		}
		assertFailsWith<IllegalArgumentException> {
			readerKotlinNamedCallArgumentNames("Host()\nHost(value = 1)", "Host")
		}
	}
}
