package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

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
