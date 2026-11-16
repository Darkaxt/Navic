package paige.navic.ui.screens.reader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderPagePaperSplitTest {
	private val foreground = 0xFF1C1B1A.toInt()
	private val background = 0xFFF4EFE6.toInt()
	private val reference = ReaderPagePaperReference("0123abcd-0.0.64x48", foreground, background)
	private val width = 64
	private val height = 48

	private fun paperPixel(x: Int, y: Int): Int {
		val grain = 200 + ((x * 37 + y * 11) % 50)
		return (0xFF shl 24) or (grain shl 16) or ((grain - 6) shl 8) or (grain - 18)
	}

	private fun coverageAt(x: Int, y: Int): Int = if ((x + y) % 7 == 0) 255 else (x * 13 + y * 5) % 256

	private fun encode(composite: (Int, Int) -> Int): Pair<Boolean, ByteArray> {
		val output = ByteArrayOutputStream()
		val ok = readerPaperSplitEncode(
			width = width,
			height = height,
			reference = reference,
			compositeRow = { y, row -> for (x in 0 until width) row[x] = composite(x, y) },
			paperRow = { y, row -> for (x in 0 until width) row[x] = paperPixel(x, y) },
			output = output
		)
		return ok to output.toByteArray()
	}

	@Test
	fun textOverSharedPaperRoundTripsWithinTwoLevels() {
		val recomposer = ReaderPaperSplitRecomposer(foreground, background)
		val composite = { x: Int, y: Int -> recomposer.pixel(paperPixel(x, y), coverageAt(x, y)) }
		val (ok, bytes) = encode(composite)
		assertTrue(ok)

		val input = ByteArrayInputStream(bytes)
		val header = assertNotNull(readerPaperSplitReadHeader(input))
		assertEquals(width, header.width)
		assertEquals(height, header.height)
		assertEquals(reference, header.reference)
		val decoded = Array(height) { IntArray(width) }
		assertTrue(
			readerPaperSplitDecodeRows(
				header = header,
				input = input,
				paperRow = { y, row -> for (x in 0 until width) row[x] = paperPixel(x, y) },
				outputRow = { y, row -> row.copyInto(decoded[y]) }
			)
		)
		for (y in 0 until height) for (x in 0 until width) {
			val expected = composite(x, y)
			val actual = decoded[y][x]
			for (shift in intArrayOf(16, 8, 0)) {
				val delta = abs(((expected ushr shift) and 0xFF) - ((actual ushr shift) and 0xFF))
				assertTrue(delta <= 2, "pixel $x,$y channel $shift differs by $delta")
			}
		}
	}

	@Test
	fun lightTextOnDarkPaperRoundTrips() {
		val darkForeground = 0xFFD8D4CC.toInt()
		val darkBackground = 0xFF25232B.toInt()
		val darkReference = ReaderPagePaperReference("0123abcd-0.0.64x48", darkForeground, darkBackground)
		val darkPaper = { x: Int, y: Int ->
			val grain = 30 + ((x * 37 + y * 11) % 12)
			(0xFF shl 24) or (grain shl 16) or ((grain - 2) shl 8) or (grain + 6)
		}
		// Independent of the recomposer: C = P * (1 - a + a * F / B) per channel.
		val composite = { x: Int, y: Int ->
			val paper = darkPaper(x, y)
			val a = coverageAt(x, y) / 255.0
			var pixel = 0xFF shl 24
			for (shift in intArrayOf(16, 8, 0)) {
				val t = ((darkForeground ushr shift) and 0xFF).toDouble() / ((darkBackground ushr shift) and 0xFF)
				val value = (((paper ushr shift) and 0xFF) * (1.0 - a + a * t)).let { Math.round(it).toInt() }
				pixel = pixel or (value.coerceIn(0, 255) shl shift)
			}
			pixel
		}
		val output = ByteArrayOutputStream()
		assertTrue(
			readerPaperSplitEncode(
				width = width,
				height = height,
				reference = darkReference,
				compositeRow = { y, row -> for (x in 0 until width) row[x] = composite(x, y) },
				paperRow = { y, row -> for (x in 0 until width) row[x] = darkPaper(x, y) },
				output = output
			)
		)
		val input = ByteArrayInputStream(output.toByteArray())
		val header = assertNotNull(readerPaperSplitReadHeader(input))
		val decoded = Array(height) { IntArray(width) }
		readerPaperSplitDecodeRows(
			header = header,
			input = input,
			paperRow = { y, row -> for (x in 0 until width) row[x] = darkPaper(x, y) },
			outputRow = { y, row -> row.copyInto(decoded[y]) }
		)
		for (y in 0 until height) for (x in 0 until width) for (shift in intArrayOf(16, 8, 0)) {
			val delta = abs(((composite(x, y) ushr shift) and 0xFF) - ((decoded[y][x] ushr shift) and 0xFF))
			assertTrue(delta <= 3, "pixel $x,$y channel $shift differs by $delta")
		}
	}

	@Test
	fun colouredContentThatTextCannotExplainIsRejected() {
		val recomposer = ReaderPaperSplitRecomposer(foreground, background)
		val (ok, _) = encode { x, y ->
			if (x in 10..20 && y in 10..20) 0xFF2050E0.toInt() else recomposer.pixel(paperPixel(x, y), 0)
		}
		assertFalse(ok)
	}

	@Test
	fun pngAndUnknownStreamsAreNotSplitRasters() {
		val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
		assertNull(readerPaperSplitReadHeader(ByteArrayInputStream(png)))
		assertNull(readerPaperSplitReadHeader(ByteArrayInputStream(ByteArray(0))))
	}

	@Test
	fun descriptionParsesOpaqueCssColoursOnly() {
		val encoded = "\"{\\\"fingerprint\\\":\\\"00ff12ab34cd56ef\\\"," +
			"\\\"foreground\\\":\\\"rgb(29, 27, 24)\\\",\\\"background\\\":\\\"rgb(251, 250, 248)\\\"}\""
		val description = assertNotNull(readerPaperSplitDescription(encoded))
		assertEquals("00ff12ab34cd56ef", description.fingerprint)
		assertEquals(0xFF1D1B18.toInt(), description.foreground)
		assertEquals(0xFFFBFAF8.toInt(), description.background)

		assertNull(readerPaperSplitDescription("null"))
		assertNull(readerPaperSplitDescription(null))
		assertNull(readerPaperSplitDescription(encoded.replace("rgb(29, 27, 24)", "rgba(29, 27, 24, 0.5)")))
		assertNull(readerPaperSplitDescription(encoded.replace("00ff12ab34cd56ef", "../escape")))
	}

	@Test
	fun paperKeysStayFileSafe() {
		assertEquals("abc123-0.12.1280x800", readerPaperSplitKey("abc123", 0, 12, 1280, 800))
		assertNull(readerPaperSplitKey("ABC", 0, 0, 10, 10))
		assertNull(readerPaperSplitKey("abc", -1, 0, 10, 10))
		assertTrue(readerPaperSplitKeyIsValid(reference.paperKey))
		assertFalse(readerPaperSplitKeyIsValid("abc/../x-0.0.1x1"))
	}
}
