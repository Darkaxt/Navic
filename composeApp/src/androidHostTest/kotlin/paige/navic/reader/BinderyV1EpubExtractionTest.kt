package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class BinderyV1EpubExtractionTest {
	@Test
	fun preservesBinderyGoRegexReplacementSemantics() {
		val cases = listOf(
			"A<script>x</script>B<style>y</style>C" to "A B C",
			"A<script>unterminated<style>x</style>B" to "A unterminated B",
			"A<scripture>x</script>B" to "A B",
			"A</P>B</h6>C</br>D" to "A. B. C. D",
			"A<>B<<tag>C<unclosed" to "A<>B C<unclosed",
			"A<div data='>'>B</div>C" to "A '>B. C"
		)

		cases.forEach { (source, expected) ->
			assertEquals(expected, extractBinderyV1Text(source), source)
		}
	}

	@Test
	fun handlesManyUnclosedScriptPrefixesWithoutBacktracking() {
		val source = "prefix" + "<script".repeat(100_000)

		assertEquals(source, extractBinderyV1Text(source))
	}
}
