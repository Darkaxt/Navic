package paige.navic.ui.screens.reader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.roundToInt
import paige.navic.util.core.Logger

/*
 * #58 stage 1 (debug-only diagnostics). Every paper layer multiplies over the text, so a page is
 * C = P * (1 - a + a * t) per channel, where P is the paper-only capture, a is text coverage and
 * t = foreground / background. The probe derives a from a second, text-hidden capture, recomposes
 * the page and reports fidelity and the A8 text-layer size. It never changes the stored raster.
 */

internal data class ReaderPaperSplitStats(
	val pixels: Int,
	val meanError: Double,
	val over24Fraction: Double,
	val over48Count: Int,
	val maxError: Int
)

internal class ReaderPaperSplit(
	val alpha: ByteArray,
	val stats: ReaderPaperSplitStats
)

private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 0xFF

internal fun readerPaperSplitTint(foreground: Int, background: Int): DoubleArray =
	DoubleArray(3) { index ->
		val shift = intArrayOf(16, 8, 0)[index]
		val bg = channel(background, shift)
		if (bg == 0) 1.0 else (channel(foreground, shift).toDouble() / bg).coerceIn(0.0, 1.0)
	}

internal fun readerPaperSplitRecomposePixel(paper: Int, alpha: Int, tint: DoubleArray): Int {
	val a = alpha / 255.0
	var out = 0xFF shl 24
	for ((index, shift) in intArrayOf(16, 8, 0).withIndex()) {
		val value = (channel(paper, shift) * (1.0 - a + a * tint[index])).roundToInt().coerceIn(0, 255)
		out = out or (value shl shift)
	}
	return out
}

internal fun readerPaperSplitAnalyze(
	composite: IntArray,
	paper: IntArray,
	foreground: Int,
	background: Int
): ReaderPaperSplit {
	require(composite.size == paper.size)
	val tint = readerPaperSplitTint(foreground, background)
	val weights = DoubleArray(3) { 1.0 - tint[it] }
	val weightSquares = weights.sumOf { it * it }
	val alpha = ByteArray(composite.size)
	var errorSum = 0L
	var over24 = 0
	var over48 = 0
	var maxError = 0
	for (pixel in composite.indices) {
		val c = composite[pixel]
		val p = paper[pixel]
		// Least-squares coverage across the three channels: (1 - C/P) = a * (1 - t).
		var numerator = 0.0
		for ((index, shift) in intArrayOf(16, 8, 0).withIndex()) {
			val paperChannel = channel(p, shift)
			if (paperChannel == 0) continue
			numerator += (1.0 - channel(c, shift).toDouble() / paperChannel) * weights[index]
		}
		val coverage = if (weightSquares <= 1e-6) 0 else
			(numerator / weightSquares * 255.0).roundToInt().coerceIn(0, 255)
		alpha[pixel] = coverage.toByte()
		val rebuilt = readerPaperSplitRecomposePixel(p, coverage, tint)
		var pixelError = 0
		for (shift in intArrayOf(16, 8, 0)) {
			pixelError = maxOf(pixelError, abs(channel(rebuilt, shift) - channel(c, shift)))
		}
		errorSum += pixelError
		if (pixelError > 24) over24 += 1
		if (pixelError > 48) over48 += 1
		if (pixelError > maxError) maxError = pixelError
	}
	val pixels = composite.size.coerceAtLeast(1)
	return ReaderPaperSplit(
		alpha = alpha,
		stats = ReaderPaperSplitStats(
			pixels = composite.size,
			meanError = errorSum.toDouble() / pixels,
			over24Fraction = over24.toDouble() / pixels,
			over48Count = over48,
			maxError = maxError
		)
	)
}

internal fun readerPaperSplitDeflatedSize(bytes: ByteArray): Int {
	val deflater = Deflater(Deflater.BEST_COMPRESSION)
	try {
		deflater.setInput(bytes)
		deflater.finish()
		val buffer = ByteArray(64 * 1024)
		var total = 0
		while (!deflater.finished()) total += deflater.deflate(buffer)
		return total
	} finally {
		deflater.end()
	}
}

/**
 * Parses the probe's `{hidden, foreground, background}` result (CSS `rgb(r, g, b)` colours) from the
 * JSON-string-encoded evaluateJavascript value.
 */
internal fun readerPaperProbeColors(encoded: String?): Pair<Int, Int>? {
	val text = encoded?.replace("\\\"", "\"") ?: return null
	val hidden = Regex(""""hidden":(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
	if (hidden <= 0) return null
	fun color(key: String): Int? {
		val css = Regex(""""$key":"rgba?\(([^)]*)\)"""").find(text)?.groupValues?.get(1) ?: return null
		val parts = css.split(',').mapNotNull { it.trim().toDoubleOrNull()?.roundToInt() }
		if (parts.size < 3 || parts.take(3).any { it !in 0..255 }) return null
		return (0xFF shl 24) or (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
	}
	val foreground = color("foreground") ?: return null
	val background = color("background") ?: return null
	return foreground to background
}

private const val ReaderPaperProbeMaximumSamples = 6

// One analysis at a time: a 2560x1600 spread needs ~50 MB of working arrays.
private val readerPaperProbeBusy = java.util.concurrent.atomic.AtomicBoolean(false)

private fun readerPaperProbeDirectory(context: Context): File =
	File(context.filesDir, "reader/paper-probe")

internal fun readerPaperProbeEnabled(context: Context): Boolean =
	(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
		File(readerPaperProbeDirectory(context), "enabled").isFile

/** Analyses one page off the main thread; [paper] is recycled once its pixels are copied. */
internal fun readerPaperProbeRecord(
	context: Context,
	composite: Bitmap,
	paper: Bitmap,
	colors: Pair<Int, Int>
) {
	val width = composite.width
	val height = composite.height
	if (
		paper.width != width || paper.height != height || composite.isRecycled ||
		!readerPaperProbeBusy.compareAndSet(false, true)
	) {
		paper.recycle()
		return
	}
	val compositePixels = IntArray(width * height)
	val paperPixels = IntArray(width * height)
	composite.getPixels(compositePixels, 0, width, 0, 0, width, height)
	paper.getPixels(paperPixels, 0, width, 0, 0, width, height)
	paper.recycle()
	val directory = readerPaperProbeDirectory(context)
	Thread({
		runCatching {
			val split = readerPaperSplitAnalyze(compositePixels, paperPixels, colors.first, colors.second)
			val compositeBitmap = Bitmap.createBitmap(compositePixels, width, height, Bitmap.Config.ARGB_8888)
			val fullPng = ByteArrayOutputStream().also {
				compositeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
			}.size()
			compositeBitmap.recycle()
			val alphaBytes = readerPaperSplitDeflatedSize(split.alpha)
			val stats = split.stats
			val sample = (directory.list()?.count { it.endsWith("-full.png") } ?: 0)
			Logger.i(
				"ReaderPaperProbe",
				"Paper split probe size=${width}x$height fullPng=$fullPng alphaDeflate=$alphaBytes " +
					"ratio=${"%.1f".format(fullPng.toDouble() / alphaBytes.coerceAtLeast(1))} " +
					"meanErr=${"%.2f".format(stats.meanError)} " +
					"over24=${"%.4f".format(stats.over24Fraction * 100)}% over48=${stats.over48Count} " +
					"maxErr=${stats.maxError} sample=$sample " +
					"fg=${Integer.toHexString(colors.first)} bg=${Integer.toHexString(colors.second)}"
			)
			if (sample < ReaderPaperProbeMaximumSamples) {
				val prefix = "s%02d".format(sample)
				fun save(name: String, pixels: IntArray) {
					val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
					File(directory, "$prefix-$name.png").outputStream().use {
						bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
					}
					bitmap.recycle()
				}
				save("full", compositePixels)
				save("paper", paperPixels)
			}
		}.onFailure { Logger.w("ReaderPaperProbe", "Paper split probe failed ${it.javaClass.simpleName}") }
		readerPaperProbeBusy.set(false)
	}, "ReaderPaperProbe").start()
}
