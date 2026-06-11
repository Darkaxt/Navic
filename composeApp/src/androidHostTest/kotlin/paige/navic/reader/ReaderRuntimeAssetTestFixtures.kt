package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun readerAssetRoot(): File =
	listOf(
		File("src/androidMain/assets/reader"),
		File("composeApp/src/androidMain/assets/reader")
	).firstOrNull { it.isDirectory }
		?: error("Could not locate Android reader assets")

internal fun readerBridgeText(root: File = readerAssetRoot()): String =
	listOf("navic-reader-settings.js", "navic-reader-helpers.js", "navic-reader.js")
		.joinToString(separator = "\n") { fileName -> root.resolve(fileName).readText() }

internal fun readerWebRuntimeFile(): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android reader WebView runtime")

internal fun readerWebViewHostFile(): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android reader WebView host")

internal fun settingsFile(fileName: String): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/settings/$fileName"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate settings file $fileName")

internal fun readerScreenFile(): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate ReaderScreen.kt")

internal fun readerOptionsPanelFile(): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate ReaderOptionsPanel.kt")

internal fun readerAndroidFile(fileName: String): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName"),
		File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android reader file $fileName")

internal fun readerAndroidPackageFile(fileName: String): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/reader/$fileName"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android reader package file $fileName")

internal fun readerCommonFile(fileName: String): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/reader/$fileName"),
		File("composeApp/src/commonMain/kotlin/paige/navic/reader/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate common reader file $fileName")

internal fun File.hasPngAlphaChannel(): Boolean {
	val bytes = readBytes()
	require(bytes.size > 25) { "PNG file is too small: $this" }
	val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
	require(bytes.take(8).toByteArray().contentEquals(pngSignature)) { "Not a PNG file: $this" }
	val colorType = bytes[25].toInt() and 0xff
	return colorType == 4 || colorType == 6
}

internal fun File.averagePngAlpha(): Double {
	val image = ImageIO.read(this) ?: error("Could not read PNG file: $this")
	var total = 0L
	for (y in 0 until image.height) {
		for (x in 0 until image.width) {
			total += (image.getRGB(x, y) ushr 24) and 0xff
		}
	}
	return total.toDouble() / (image.width * image.height).toDouble()
}

internal fun File.maxPngAlpha(): Int {
	val image = ImageIO.read(this) ?: error("Could not read PNG file: $this")
	var max = 0
	for (y in 0 until image.height) {
		for (x in 0 until image.width) {
			max = maxOf(max, (image.getRGB(x, y) ushr 24) and 0xff)
		}
	}
	return max
}

internal fun File.outerEdgeAlphaHighFrequencyPercent(): Double {
	val image = ImageIO.read(this) ?: error("Could not read PNG file: $this")
	val edge = (minOf(image.width, image.height) * 0.18).toInt()
	var comparisons = 0
	var highFrequencyComparisons = 0

	fun isOuterEdge(x: Int, y: Int): Boolean =
		x < edge || x >= image.width - edge || y < edge || y >= image.height - edge

	fun alphaAt(x: Int, y: Int): Int = (image.getRGB(x, y) ushr 24) and 0xff

	for (y in 0 until image.height step 4) {
		for (x in 0 until image.width step 4) {
			if (!isOuterEdge(x, y)) continue
			val alpha = alphaAt(x, y)
			if (x + 4 < image.width && isOuterEdge(x + 4, y)) {
				comparisons++
				if (kotlin.math.abs(alpha - alphaAt(x + 4, y)) >= 12) highFrequencyComparisons++
			}
			if (y + 4 < image.height && isOuterEdge(x, y + 4)) {
				comparisons++
				if (kotlin.math.abs(alpha - alphaAt(x, y + 4)) >= 12) highFrequencyComparisons++
			}
		}
	}
	return if (comparisons == 0) 0.0 else highFrequencyComparisons.toDouble() * 100.0 / comparisons
}
