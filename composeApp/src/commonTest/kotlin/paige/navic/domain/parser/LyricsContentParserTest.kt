package paige.navic.domain.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class LyricsContentParserTest {
	@Test
	fun duplicateTimestampLrcLinesAreMergedIntoTranslatedBlocks() {
		val lines = assertNotNull(
			LyricsContentParser.parse(
				"""
				[00:01.26]세상의 모서리 구부정하게 커버린
				[00:01.26]At the edge of the world, grown up all hunched and curled
				[00:05.84]골칫거리 outsider
				[00:05.84]A troublesome outsider
				""".trimIndent()
			)
		)

		assertEquals(2, lines.size)
		assertEquals(1260.milliseconds, lines[0].time)
		assertEquals(
			"세상의 모서리 구부정하게 커버린\nAt the edge of the world, grown up all hunched and curled",
			lines[0].text
		)
		assertEquals(5840.milliseconds, lines[1].time)
		assertEquals("골칫거리 outsider\nA troublesome outsider", lines[1].text)
	}
}
