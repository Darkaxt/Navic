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
	listOf(
		"navic-reader-bridge-core.js",
		"navic-reader-settings-core.js",
		"navic-reader-settings.js",
		"navic-reader-media.js",
		"navic-reader-identity.js",
		"navic-reader-pagination-model.js",
		"navic-reader-typography.js",
		"navic-reader-paper-surface.js",
		"navic-reader-helpers.js",
		"navic-reader-motion.js",
		"navic-reader-page-turns.js",
		"navic-reader-content-interactions.js",
		"navic-reader-pagination-stability.js",
		"navic-reader-paginator-commit.js",
		"navic-reader-pagination.js",
		"navic-reader-appearance.js",
		"navic-reader-shell-cover.js",
		"navic-reader-viewport.js",
		"navic-reader-location.js",
		"navic-reader-baseline-hmac.js",
		"navic-reader-media-overlay.js",
		"navic-reader.js"
	)
		.joinToString(separator = "\n") { fileName -> root.resolve(fileName).readText() }

internal fun readerPaperSurfaceText(root: File = readerAssetRoot()): String =
	root.resolve("navic-reader-paper-surface.js").readText()

internal fun readerPaperSurfaceContractText(root: File = readerAssetRoot()): String =
	listOf("navic-reader-paper-surface.js", "navic-reader-helpers.js")
		.joinToString(separator = "\n") { fileName -> root.resolve(fileName).readText() }

internal fun readerRuntimeImplementationText(root: File = readerAssetRoot()): String =
	listOf(
		"navic-reader-motion.js",
		"navic-reader-page-turns.js",
		"navic-reader-content-interactions.js",
		"navic-reader-pagination-stability.js",
		"navic-reader-paginator-commit.js",
		"navic-reader-pagination.js",
		"navic-reader-appearance.js",
		"navic-reader-shell-cover.js",
		"navic-reader-viewport.js",
		"navic-reader-location.js",
		"navic-reader-baseline-hmac.js",
		"navic-reader-media-overlay.js",
		"navic-reader.js"
	)
		.joinToString(separator = "\n") { fileName -> root.resolve(fileName).readText() }

internal fun readerWebRuntimeFile(): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android reader WebView runtime")

internal fun readerEngineWebViewHostFile(): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android reader engine WebView host")

internal fun readerNativeFrameHostFile(): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android Komikku reader native frame host")

internal fun settingsFile(fileName: String): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/settings/$fileName"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate settings file $fileName")

internal fun settingsSearchSourceText(): String {
	val settingsDir = listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/settings"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings")
	).firstOrNull { it.isDirectory }
		?: error("Could not locate common settings source directory")
	return settingsDir
		.listFiles { file -> file.isFile && file.name.startsWith("SettingsSearch") && file.extension == "kt" }
		.orEmpty()
		.sortedBy { it.name }
		.joinToString(separator = "\n") { it.readText() }
}

internal fun readerScreenFile(): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate ReaderScreen.kt")

internal fun readerViewerHostFile(): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate ReaderViewerHost.kt")

internal fun readerOptionsPanelFile(): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt")
	).firstOrNull { it.isFile }
		?: error("Could not locate ReaderOptionsPanel.kt")

internal fun readerCommonUiFile(fileName: String): File =
	listOf(
		File("src/commonMain/kotlin/paige/navic/ui/screens/reader/$fileName"),
		File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate common reader UI file $fileName")

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

internal fun repoScriptFile(fileName: String): File =
	listOf(
		File("scripts/$fileName"),
		File("../scripts/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate script $fileName")

internal fun repoFile(path: String): File =
	listOf(
		File(path),
		File("../$path")
	).firstOrNull { it.isFile }
		?: error("Could not locate repo file $path")

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

internal data class SampledLuminanceStats(
	val average: Double,
	val standardDeviation: Double,
	val width: Int,
	val height: Int
)

internal fun File.sampledLuminanceStats(): SampledLuminanceStats {
	val image = ImageIO.read(this) ?: error("Could not read image file: $this")
	val xStep = maxOf(1, image.width / 128)
	val yStep = maxOf(1, image.height / 128)
	var count = 0L
	var sum = 0.0
	var sumSquares = 0.0
	for (y in 0 until image.height step yStep) {
		for (x in 0 until image.width step xStep) {
			val rgb = image.getRGB(x, y)
			val red = (rgb ushr 16) and 0xff
			val green = (rgb ushr 8) and 0xff
			val blue = rgb and 0xff
			val luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722
			count++
			sum += luminance
			sumSquares += luminance * luminance
		}
	}
	val average = if (count == 0L) 0.0 else sum / count.toDouble()
	val variance = if (count == 0L) 0.0 else (sumSquares / count.toDouble()) - average * average
	return SampledLuminanceStats(
		average = average,
		standardDeviation = kotlin.math.sqrt(maxOf(0.0, variance)),
		width = image.width,
		height = image.height
	)
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
