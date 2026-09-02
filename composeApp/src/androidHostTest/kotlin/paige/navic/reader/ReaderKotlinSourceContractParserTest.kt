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
	fun lexicalMarkerTextDoesNotCompeteWithRealDeclarationOrCall() {
		val rawDeclarationMarker = "\"\"\"expect fun Host(raw: Int)\"\"\""
		val declarationSource = listOf(
			"// expect fun Host(commented: Int)",
			"/* outer expect fun Host(blocked: Int) /* nested expect fun Host(nested: Int) */ end */",
			"val ordinary = \"expect fun Host(\\\"escaped\\\")\"",
			"val raw = $rawDeclarationMarker",
			"val `expect fun Host` = Unit",
			"expect fun Host(value: Int)"
		).joinToString("\n")
		val rawCallMarker = "\"\"\"Host(raw = ignored)\"\"\""
		val callSource = listOf(
			"// Host(commented = ignored)",
			"/* outer Host(blocked = ignored) /* nested Host(nested = ignored) */ end */",
			"val ordinary = \"Host(\\\"escaped\\\")\"",
			"val raw = $rawCallMarker",
			"val `Host` = callback",
			"Host(value = 1)"
		).joinToString("\n")

		assertEquals(
			listOf("value"),
			readerKotlinDeclarationParameterContract(
				declarationSource,
				"expect fun Host"
			).map { it.name }
		)
		assertEquals(listOf("value"), readerKotlinNamedCallArgumentNames(callSource, "Host"))
	}

	@Test
	fun characterLiteralAndBacktickIdentifierDoNotCountAsShortMarkers() {
		val source = "val marker: Char = 'H'\nval `H` = callback\nH(value = 1)"

		assertEquals(listOf("value"), readerKotlinNamedCallArgumentNames(source, "H"))
	}

	@Test
	fun backtickIdentifierMarkerAndParenthesesDoNotCompeteWithRealCall() {
		val source = "val `ignored Host (value = 1)` = Unit\nHost(value = 2)"

		assertEquals(listOf("value"), readerKotlinNamedCallArgumentNames(source, "Host"))
	}

	@Test
	fun backtickIdentifiersKeepContainedSyntaxInertAcrossAllScanners() {
		val richIdentifier =
			"`first Host (value = 1), \"quoted\", 'single', /* block */ // line-like, [ ] { } < > ->`"
		val secondIdentifier = "`second Host (ignored = 2), \"quoted\"`"
		val escapedLookingIdentifier = "`slash-before-close\\`"
		val prefix = listOf(
			"val first = $richIdentifier",
			"val second = $secondIdentifier",
			"val third = $escapedLookingIdentifier"
		).joinToString("\n")
		val declarationSource =
			"$prefix\nexpect fun Host(value: Any = $richIdentifier, onDone: () -> Unit)"
		val callSource = "$prefix\nHost(value = $richIdentifier, onDone = callback)"

		assertEquals(
			listOf("value", "onDone"),
			readerKotlinDeclarationParameterContract(
				declarationSource,
				"expect fun Host"
			).map { it.name }
		)
		assertEquals(
			listOf("value", "onDone"),
			readerKotlinNamedCallArgumentNames(callSource, "Host")
		)
	}

	@Test
	fun closingBacktickReleasesImmediatelyAdjacentRealMarker() {
		val source = "`ignored marker`Host(value = 1)"

		assertEquals(listOf("value"), readerKotlinNamedCallArgumentNames(source, "Host"))
	}

	@Test
	fun unterminatedBacktickIdentifierFailsClosedBeforeMarkerCounting() {
		val declarationFailure = assertFailsWith<IllegalArgumentException> {
			readerKotlinDeclarationParameterContract(
				"expect fun Host(value: Int)\nval broken = `unterminated",
				"expect fun Host"
			)
		}
		val callFailure = assertFailsWith<IllegalArgumentException> {
			readerKotlinNamedCallArgumentNames(
				"Host(value = 1)\nval broken = `unterminated",
				"Host"
			)
		}

		assertTrue(declarationFailure.message.orEmpty().contains("backtick identifier"))
		assertTrue(callFailure.message.orEmpty().contains("backtick identifier"))
	}

	@Test
	fun escapedParameterAndArgumentNamesAreRejectedByContractGrammar() {
		val declarationFailure = assertFailsWith<IllegalArgumentException> {
			readerKotlinDeclarationParameterContract(
				"expect fun Host(`when`: Int)",
				"expect fun Host"
			)
		}
		val callFailure = assertFailsWith<IllegalArgumentException> {
			readerKotlinNamedCallArgumentNames("Host(`when` = 1)", "Host")
		}

		assertTrue(declarationFailure.message.orEmpty().contains("single valid name"))
		assertTrue(callFailure.message.orEmpty().contains("single valid name"))
	}

	@Test
	fun markerCandidatesRequireIdentifierBoundariesAndArgumentListOpener() {
		val declarationSource = """
			expect fun FakeHost(ignored: Int)
			expect fun HostFactory(ignored: Int)
			val reference = Host
			expect fun Host(value: Int)
		""".trimIndent()
		val callSource = """
			FakeKomikkuReaderNativeFrameHost(ignored = 1)
			KomikkuReaderNativeFrameHostFactory(ignored = 2)
			val reference = KomikkuReaderNativeFrameHost
			KomikkuReaderNativeFrameHost<String>(ignored = 3)
			KomikkuReaderNativeFrameHost(value = 4)
		""".trimIndent()

		assertEquals(
			listOf("value"),
			readerKotlinDeclarationParameterContract(declarationSource, "Host").map { it.name }
		)
		assertEquals(
			listOf("value"),
			readerKotlinNamedCallArgumentNames(callSource, "KomikkuReaderNativeFrameHost")
		)
	}

	@Test
	fun nestedBlockCommentsMaySeparateDeclarationMarkerFromArgumentList() {
		val source = "expect fun Host /* outer /* nested */ comment */ (value: Int)"

		assertEquals(
			listOf("value"),
			readerKotlinDeclarationParameterContract(source, "expect fun Host").map { it.name }
		)
	}

	@Test
	fun lineAndBlockCommentsMaySeparateCallMarkerFromArgumentList() {
		val source = "Host /* block */ // line\n(value = 1)"

		assertEquals(listOf("value"), readerKotlinNamedCallArgumentNames(source, "Host"))
	}

	@Test
	fun twoRealTokenBoundaryMarkersStillFailAsDuplicates() {
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

	@Test
	fun unterminatedLexicalStatesOutsideArgumentListFailBeforeMarkerCounting() {
		val malformedDeclarationSources = listOf(
			"expect fun Host(value: Int)\nval broken = \"unterminated",
			"expect fun Host(value: Int)\nval broken = \"\"\"unterminated",
			"expect fun Host(value: Int)\nval broken = '",
			"expect fun Host(value: Int)\n/* unterminated"
		)
		val malformedCallSources = listOf(
			"Host(value = 1)\nval broken = \"unterminated",
			"Host(value = 1)\nval broken = \"\"\"unterminated",
			"Host(value = 1)\nval broken = '",
			"Host(value = 1)\n/* unterminated"
		)

		malformedDeclarationSources.forEach { source ->
			assertFailsWith<IllegalArgumentException> {
				readerKotlinDeclarationParameterContract(source, "expect fun Host")
			}
		}
		malformedCallSources.forEach { source ->
			assertFailsWith<IllegalArgumentException> {
				readerKotlinNamedCallArgumentNames(source, "Host")
			}
		}
	}

	@Test
	fun rawTripleStringsKeepFollowingParametersAndArgumentsVisible() {
		val rawLiteral =
			"\"\"\"text 'single' and \"double, <,>, ->\" tail, [brackets], {braces}, (parentheses), " +
				"\\\\looks-escaped\n\$name and \${expr, with, commas}\nend\"\"\""
		val parameters = readerKotlinDeclarationParameterContract(
			"expect fun Host(label: String = $rawLiteral, onDone: () -> Unit)",
			"expect fun Host"
		)
		val arguments = readerKotlinNamedCallArgumentNames(
			"Host(label = $rawLiteral, onDone = callback)",
			"Host"
		)

		assertEquals(2, parameters.size)
		assertEquals(listOf("label", "onDone"), parameters.map { it.name })
		assertEquals(2, arguments.size)
		assertEquals(listOf("label", "onDone"), arguments)
	}

	@Test
	fun escapedOrdinaryLiteralsAndCommentsKeepStructuralTokensInert() {
		val ordinaryParameters = readerKotlinDeclarationParameterContract(
			"expect fun Host(label: String = \"text \\\"quoted\\\", <,>, \\\\path\", marker: Char = '\\'', slash: Char = '\\\\', onDone: () -> Unit)",
			"expect fun Host"
		)
		val ordinaryArguments = readerKotlinNamedCallArgumentNames(
			"Host(label = \"text \\\"quoted\\\", <,>, \\\\path\", marker = '\\'', slash = '\\\\', onDone = callback)",
			"Host"
		)
		val lineCommentedDeclaration = """
			expect fun Host(
				value: Int = 1 // inert , < > -> ( [ {
				,
				onDone: () -> Unit
			)
		""".trimIndent()
		val lineCommentedCall = """
			Host(
				value = 1 // inert , < > -> ( [ {
				,
				onDone = callback
			)
		""".trimIndent()
		val blockCommentedDeclaration = """
			expect fun Host(
				value: Int = 1 /* outer , < > -> ( [ {
					/* nested , < > -> ) ] } */
				*/,
				onDone: () -> Unit
			)
		""".trimIndent()
		val blockCommentedCall = """
			Host(
				value = 1 /* outer , < > -> ( [ {
					/* nested , < > -> ) ] } */
				*/,
				onDone = callback
			)
		""".trimIndent()

		assertEquals(listOf("label", "marker", "slash", "onDone"), ordinaryParameters.map { it.name })
		assertEquals(listOf("label", "marker", "slash", "onDone"), ordinaryArguments)
		listOf(
			"line declaration" to readerKotlinDeclarationParameterContract(
				lineCommentedDeclaration,
				"expect fun Host"
			).map { it.name },
			"block declaration" to readerKotlinDeclarationParameterContract(
				blockCommentedDeclaration,
				"expect fun Host"
			).map { it.name }
		).forEach { (label, names) ->
			assertEquals(listOf("value", "onDone"), names, label)
		}
		listOf(
			"line call" to readerKotlinNamedCallArgumentNames(lineCommentedCall, "Host"),
			"block call" to readerKotlinNamedCallArgumentNames(blockCommentedCall, "Host")
		).forEach { (label, names) ->
			assertEquals(listOf("value", "onDone"), names, label)
		}
	}

	@Test
	fun unterminatedLexicalOrDelimiterStatesFailClosed() {
		val malformedDeclarations = mapOf(
			"ordinary string" to "expect fun Host(label: String = \"unterminated, onDone: () -> Unit)",
			"raw string" to "expect fun Host(label: String = \"\"\"unterminated, onDone: () -> Unit)",
			"character" to "expect fun Host(marker: Char = ', onDone: () -> Unit)",
			"block comment" to "expect fun Host(value: Int = 1 /* unterminated, onDone: () -> Unit)",
			"delimiter" to "expect fun Host(value: Int = values[0, onDone: () -> Unit)"
		)

		malformedDeclarations.forEach { (label, source) ->
			val failure = assertFails(label) {
				readerKotlinDeclarationParameterContract(source, "expect fun Host")
			}
			assertTrue(!failure.message.isNullOrBlank(), label)
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
