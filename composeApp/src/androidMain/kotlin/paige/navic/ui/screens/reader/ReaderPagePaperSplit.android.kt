package paige.navic.ui.screens.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.WeakHashMap
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import paige.navic.reader.readerPageRasterStorageRoot
import paige.navic.util.core.Logger

/*
 * #58: paper/text split page rasters. Every paper layer multiplies over the text, so a captured
 * page is C = P * (1 - a + a * t) per channel: P is the paper alone (text hidden), a is the text
 * coverage and t = foreground / background. A page is stored as A8 coverage plus a reference to a
 * shared paper capture; pages the model cannot reproduce (images, coloured text) stay full PNGs.
 * Decoding rebuilds an ordinary ARGB bitmap, so decks, curl rendering and validation are unchanged.
 */

private const val ReaderPaperSplitTag = "ReaderPaperSplit"
private val ReaderPaperSplitMagic = byteArrayOf('N'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), 'S'.code.toByte())
private const val ReaderPaperSplitFormatVersion = 1

/** Above this the split is rejected and the page keeps its full PNG. */
internal const val ReaderPaperSplitMaximumPixelError = 24
internal const val ReaderPaperSplitMaximumErrorFractionDenominator = 2000

internal data class ReaderPagePaperReference(
	val paperKey: String,
	val foreground: Int,
	val background: Int
)

private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

// Light text on a dark theme gives t > 1: the paper layers multiply it all the same, so the model
// holds and t must not be capped at 1 (that left every dark-theme page as a full PNG).
internal fun readerPaperSplitTint(foreground: Int, background: Int): DoubleArray =
	DoubleArray(3) { index ->
		val shift = intArrayOf(16, 8, 0)[index]
		val bg = channel(background, shift)
		if (bg == 0) 1.0 else channel(foreground, shift).toDouble() / bg
	}

/** Per channel, per coverage: the factor (x256) the paper is multiplied by. */
internal class ReaderPaperSplitRecomposer(foreground: Int, background: Int) {
	private val tint = readerPaperSplitTint(foreground, background)
	private val factors = Array(3) { index ->
		IntArray(256) { alpha ->
			val a = alpha / 255.0
			((1.0 - a + a * tint[index]) * 256.0).roundToInt()
		}
	}
	val weights = DoubleArray(3) { 1.0 - tint[it] }
	val weightSquares = weights.sumOf { it * it }

	fun pixel(paper: Int, alpha: Int): Int {
		val r = (channel(paper, 16) * factors[0][alpha] + 128) shr 8
		val g = (channel(paper, 8) * factors[1][alpha] + 128) shr 8
		val b = (channel(paper, 0) * factors[2][alpha] + 128) shr 8
		return (0xFF shl 24) or (r.coerceAtMost(255) shl 16) or (g.coerceAtMost(255) shl 8) or b.coerceAtMost(255)
	}

	/** Least-squares coverage across the three channels: (1 - C/P) = a * (1 - t). */
	fun coverage(composite: Int, paper: Int): Int {
		if (weightSquares <= 1e-6) return 0
		val numerator = term(composite, paper, 16, weights[0]) +
			term(composite, paper, 8, weights[1]) +
			term(composite, paper, 0, weights[2])
		return (numerator / weightSquares * 255.0).roundToInt().coerceIn(0, 255)
	}

	private fun term(composite: Int, paper: Int, shift: Int, weight: Double): Double {
		val paperChannel = channel(paper, shift)
		if (paperChannel == 0) return 0.0
		return (1.0 - channel(composite, shift).toDouble() / paperChannel) * weight
	}
}

private fun readerPaperSplitPixelError(a: Int, b: Int): Int =
	maxOf(
		abs(channel(a, 16) - channel(b, 16)),
		abs(channel(a, 8) - channel(b, 8)),
		abs(channel(a, 0) - channel(b, 0))
	)

/**
 * Writes the split encoding of one page, row by row. Returns false (output must be discarded) when
 * the coverage model cannot reproduce the page within [ReaderPaperSplitMaximumPixelError].
 */
internal fun readerPaperSplitEncode(
	width: Int,
	height: Int,
	reference: ReaderPagePaperReference,
	compositeRow: (y: Int, row: IntArray) -> Unit,
	paperRow: (y: Int, row: IntArray) -> Unit,
	output: OutputStream
): Boolean {
	val recomposer = ReaderPaperSplitRecomposer(reference.foreground, reference.background)
	val data = DataOutputStream(output)
	data.write(ReaderPaperSplitMagic)
	data.writeByte(ReaderPaperSplitFormatVersion)
	data.writeInt(width)
	data.writeInt(height)
	data.writeInt(reference.foreground)
	data.writeInt(reference.background)
	data.writeUTF(reference.paperKey)
	data.flush()
	val deflater = Deflater(Deflater.BEST_SPEED)
	val compressed = DeflaterOutputStream(output, deflater, 64 * 1024)
	val composite = IntArray(width)
	val paper = IntArray(width)
	val alpha = ByteArray(width)
	val allowedErrors = width.toLong() * height / ReaderPaperSplitMaximumErrorFractionDenominator
	var errors = 0L
	try {
		for (y in 0 until height) {
			compositeRow(y, composite)
			paperRow(y, paper)
			for (x in 0 until width) {
				val coverage = recomposer.coverage(composite[x], paper[x])
				alpha[x] = coverage.toByte()
				val error = readerPaperSplitPixelError(recomposer.pixel(paper[x], coverage), composite[x])
				if (error > ReaderPaperSplitMaximumPixelError) {
					errors += 1
					if (error > ReaderPaperSplitMaximumPixelError * 2 || errors > allowedErrors) return false
				}
			}
			compressed.write(alpha)
		}
		compressed.finish()
		compressed.flush()
		return true
	} finally {
		deflater.end()
	}
}

internal class ReaderPaperSplitHeader(
	val width: Int,
	val height: Int,
	val reference: ReaderPagePaperReference
)

/** Reads the header, or returns null when [input] is not a split raster (it is then a PNG). */
internal fun readerPaperSplitReadHeader(input: InputStream): ReaderPaperSplitHeader? {
	val data = DataInputStream(input)
	val magic = ByteArray(ReaderPaperSplitMagic.size)
	if (data.read(magic) != magic.size || !magic.contentEquals(ReaderPaperSplitMagic)) return null
	if (data.readUnsignedByte() != ReaderPaperSplitFormatVersion) return null
	val width = data.readInt()
	val height = data.readInt()
	val foreground = data.readInt()
	val background = data.readInt()
	val paperKey = data.readUTF()
	if (width <= 0 || height <= 0 || !readerPaperSplitKeyIsValid(paperKey)) return null
	return ReaderPaperSplitHeader(width, height, ReaderPagePaperReference(paperKey, foreground, background))
}

/** Rebuilds the page row by row from the coverage stream that follows the header. */
internal fun readerPaperSplitDecodeRows(
	header: ReaderPaperSplitHeader,
	input: InputStream,
	paperRow: (y: Int, row: IntArray) -> Unit,
	outputRow: (y: Int, row: IntArray) -> Unit
): Boolean {
	val recomposer = ReaderPaperSplitRecomposer(header.reference.foreground, header.reference.background)
	val coverage = DataInputStream(InflaterInputStream(input, java.util.zip.Inflater(), 64 * 1024))
	val alpha = ByteArray(header.width)
	val paper = IntArray(header.width)
	val row = IntArray(header.width)
	for (y in 0 until header.height) {
		coverage.readFully(alpha)
		paperRow(y, paper)
		for (x in 0 until header.width) {
			row[x] = recomposer.pixel(paper[x], alpha[x].toInt() and 0xFF)
		}
		outputRow(y, row)
	}
	return true
}

private val ReaderPaperSplitKeyPattern = Regex("""[0-9a-f]{1,32}-[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}x[0-9]{1,5}""")

internal fun readerPaperSplitKeyIsValid(key: String): Boolean = ReaderPaperSplitKeyPattern.matches(key)

internal fun readerPaperSplitKey(
	fingerprint: String,
	captureLeft: Int,
	captureTop: Int,
	width: Int,
	height: Int
): String? = "$fingerprint-$captureLeft.$captureTop.${width}x$height".takeIf(::readerPaperSplitKeyIsValid)

internal class ReaderPaperSplitDescription(
	val fingerprint: String,
	val foreground: Int,
	val background: Int
)

/** Parses `{fingerprint, foreground, background}` (CSS `rgb(r, g, b)`) from a JSON-string result. */
internal fun readerPaperSplitDescription(encoded: String?): ReaderPaperSplitDescription? {
	val text = encoded?.replace("\\\"", "\"") ?: return null
	val fingerprint = Regex(""""fingerprint":"([0-9a-f]{1,32})"""").find(text)?.groupValues?.get(1)
		?: return null
	fun color(key: String): Int? {
		val css = Regex(""""$key":"rgba?\(([^)]*)\)"""").find(text)?.groupValues?.get(1) ?: return null
		val values = css.split(',').map { it.trim().toDoubleOrNull() ?: return null }
		if (values.size !in 3..4 || (values.size == 4 && values[3] != 1.0)) return null
		val parts = values.take(3).map { it.roundToInt() }
		if (parts.any { it !in 0..255 }) return null
		return (0xFF shl 24) or (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
	}
	val foreground = color("foreground") ?: return null
	val background = color("background") ?: return null
	return ReaderPaperSplitDescription(fingerprint, foreground, background)
}

/** Passive captures carry their paper reference to the cache codec without changing raster types. */
internal object ReaderPagePaperRegistry {
	private val references = WeakHashMap<Bitmap, ReaderPagePaperReference>()

	@Synchronized
	fun attach(bitmap: Bitmap, reference: ReaderPagePaperReference) {
		references[bitmap] = reference
	}

	@Synchronized
	fun referenceFor(bitmap: Bitmap): ReaderPagePaperReference? = references[bitmap]

	// Diagnostics only: bitmaps rebuilt from a split file (a copy of one is not tracked).
	private val splitDecoded = WeakHashMap<Bitmap, Unit>()

	@Synchronized
	fun markSplitDecoded(bitmap: Bitmap) {
		splitDecoded[bitmap] = Unit
	}

	@Synchronized
	fun isSplitDecoded(bitmap: Bitmap): Boolean = splitDecoded.containsKey(bitmap)
}

private const val ReaderPagePaperMaximumStoredPapers = 48
private const val ReaderPagePaperDecodedPapers = 3

/** Shared paper captures, stored once under the page raster cache (`paper/`). */
internal class ReaderPagePaperStore(private val root: File) {
	private val decoded = LinkedHashMap<String, Bitmap>(8, 0.75f, true)

	private fun file(key: String) = File(root, "$key.png")

	@Synchronized
	fun contains(key: String): Boolean = decoded.containsKey(key) || file(key).isFile

	/** Keeps [paper] decoded for immediate use and persists it in the background. */
	fun adopt(key: String, paper: Bitmap) {
		synchronized(this) { remember(key, paper) }
		Thread({
			runCatching {
				root.mkdirs()
				val temporary = File(root, "$key.${System.nanoTime()}.tmp")
				FileOutputStream(temporary).use { paper.compress(Bitmap.CompressFormat.PNG, 100, it) }
				if (!temporary.renameTo(file(key))) temporary.delete()
				prune()
			}.onFailure { Logger.w(ReaderPaperSplitTag, "Paper save failed ${it.javaClass.simpleName}") }
		}, "ReaderPaperSave").start()
	}

	@Synchronized
	fun load(key: String): Bitmap? {
		decoded[key]?.let { return it }
		val bitmap = file(key).takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path) } ?: return null
		remember(key, bitmap)
		return bitmap
	}

	private fun remember(key: String, bitmap: Bitmap) {
		decoded[key] = bitmap
		// Evicted papers are left to the garbage collector: another thread may still read their rows.
		while (decoded.size > ReaderPagePaperDecodedPapers) decoded.remove(decoded.keys.first())
	}

	private fun prune() {
		val papers = root.listFiles().orEmpty().filter { it.name.endsWith(".png") }
		if (papers.size <= ReaderPagePaperMaximumStoredPapers) return
		papers.sortedBy(File::lastModified)
			.take(papers.size - ReaderPagePaperMaximumStoredPapers)
			.forEach(File::delete)
	}

	companion object {
		private var shared: ReaderPagePaperStore? = null

		@Synchronized
		fun current(): ReaderPagePaperStore? = shared

		@Synchronized
		fun of(context: Context): ReaderPagePaperStore {
			val root = readerPageRasterStorageRoot(context.applicationContext).resolve("paper")
			shared?.takeIf { it.root == root }?.let { return it }
			return ReaderPagePaperStore(root).also { shared = it }
		}
	}
}

/**
 * Page snapshots are a crop (and usually a downscale) of the passive capture. The paper gets the
 * identical crop and scale once per paper variant, and the snapshot is tagged with that paper.
 */
internal fun readerPaperSplitCarry(
	reference: ReaderPagePaperReference,
	target: Bitmap,
	left: Int,
	top: Int,
	width: Int,
	height: Int
) {
	val papers = ReaderPagePaperStore.current() ?: return
	val key = readerPaperSplitKey(
		reference.paperKey.substringBefore('-'),
		left,
		top,
		target.width,
		target.height
	) ?: return
	if (!papers.contains(key)) {
		val full = papers.load(reference.paperKey) ?: return
		if (left < 0 || top < 0 || left + width > full.width || top + height > full.height) return
		val derived = runCatching {
			val cropped = Bitmap.createBitmap(full, left, top, width, height)
			if (cropped.width == target.width && cropped.height == target.height) {
				cropped
			} else {
				Bitmap.createScaledBitmap(cropped, target.width, target.height, true).also { scaled ->
					if (scaled !== cropped && cropped !== full) cropped.recycle()
				}
			}
		}.getOrNull() ?: return
		papers.adopt(key, derived)
	}
	ReaderPagePaperRegistry.attach(target, reference.copy(paperKey = key))
}

/** Page raster codec: split encoding when the capture carries a paper reference, PNG otherwise. */
internal class ReaderSplitPageRasterCodec(
	private val papers: ReaderPagePaperStore
) : ReaderPageRasterCodec<Bitmap> {
	override fun encode(value: Bitmap, target: File): Boolean {
		val reference = ReaderPagePaperRegistry.referenceFor(value)
		val paper = reference?.let { papers.load(it.paperKey) }
		if (reference != null && paper != null && paper.width == value.width && paper.height == value.height) {
			val split = runCatching {
				BufferedOutputStream(FileOutputStream(target)).use { output ->
					readerPaperSplitEncode(
						width = value.width,
						height = value.height,
						reference = reference,
						compositeRow = { y, row -> value.getPixels(row, 0, value.width, 0, y, value.width, 1) },
						paperRow = { y, row -> paper.getPixels(row, 0, paper.width, 0, y, paper.width, 1) },
						output = output
					)
				}
			}.getOrDefault(false)
			Logger.i(
				ReaderPaperSplitTag,
				"Page raster encoded split=$split bytes=${if (split) target.length() else 0} " +
					"size=${value.width}x${value.height}"
			)
			if (split) return true
		} else {
			Logger.i(
				ReaderPaperSplitTag,
				"Page raster encoded split=false reference=${reference != null} paper=${paper != null}"
			)
		}
		return ReaderAndroidPageRasterCodec.encode(value, target)
	}

	override fun decode(source: File): Bitmap? {
		BufferedInputStream(source.inputStream(), 64 * 1024).use { input ->
			input.mark(1024)
			val header = runCatching { readerPaperSplitReadHeader(input) }.getOrNull()
			if (header == null) {
				input.reset()
				return BitmapFactory.decodeStream(input)
			}
			val paper = papers.load(header.reference.paperKey) ?: return null
			if (paper.width != header.width || paper.height != header.height) return null
			val bitmap = Bitmap.createBitmap(header.width, header.height, Bitmap.Config.ARGB_8888)
			val decoded = runCatching {
				readerPaperSplitDecodeRows(
					header = header,
					input = input,
					paperRow = { y, row -> paper.getPixels(row, 0, paper.width, 0, y, paper.width, 1) },
					outputRow = { y, row -> bitmap.setPixels(row, 0, header.width, 0, y, header.width, 1) }
				)
			}.getOrDefault(false)
			if (!decoded) {
				bitmap.recycle()
				return null
			}
			ReaderPagePaperRegistry.markSplitDecoded(bitmap)
			return bitmap
		}
	}

	override fun release(value: Bitmap) = ReaderAndroidPageRasterCodec.release(value)
}
