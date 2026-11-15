package paige.navic.ui.screens.reader

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import paige.navic.util.core.Logger

private const val ReaderPageLiveValidationRejectDumpTag = "ReaderPageTurnBundleSource"
internal const val ReaderPageLiveValidationRejectDumpLimit = 4
internal const val ReaderPageLiveValidationRejectDumpDirectoryName = "reader-diagnostics"
private const val ReaderPageLiveValidationRejectDumpPrefix = "reject-"

/** Release builds are never debuggable, so they never write diagnostic rasters. */
internal fun readerPageLiveValidationRejectDumpEnabled(applicationFlags: Int): Boolean =
	(applicationFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

/** The dump slot for the [claimed]-th rejection of this process, or null once the bound is spent. */
internal fun readerPageLiveValidationRejectDumpSlot(claimed: Int, limit: Int): Int? =
	claimed.takeIf { it in 0 until limit }

internal fun readerPageLiveValidationRejectDumpFileName(
	pageOrdinal: Int,
	slot: Int,
	role: String
): String = "$ReaderPageLiveValidationRejectDumpPrefix$pageOrdinal-$slot-$role.png"

/**
 * Debug-build-only evidence for a content-rejected post-turn validation: the expected deck
 * reference and the presented candidate are written side by side so they can be pulled and
 * compared. Bounded to [ReaderPageLiveValidationRejectDumpLimit] rejections per process; the
 * first dump of a process removes the previous process's files.
 */
internal object ReaderPageLiveValidationRejectDump {
	private val claimed = AtomicInteger()

	fun directory(context: Context): File? = runCatching {
		if (!readerPageLiveValidationRejectDumpEnabled(context.applicationInfo.flags)) {
			null
		} else {
			File(context.cacheDir, ReaderPageLiveValidationRejectDumpDirectoryName)
		}
	}.getOrNull()

	fun write(
		directory: File,
		pageOrdinal: Int,
		expected: Bitmap,
		candidate: Bitmap
	) {
		val slot = readerPageLiveValidationRejectDumpSlot(
			claimed = claimed.getAndIncrement(),
			limit = ReaderPageLiveValidationRejectDumpLimit
		) ?: return
		runCatching {
			if (slot == 0) {
				directory.listFiles()
					?.filter { file -> file.name.startsWith(ReaderPageLiveValidationRejectDumpPrefix) }
					?.forEach(File::delete)
			}
			check(directory.isDirectory || directory.mkdirs())
			val expectedFile = File(
				directory,
				readerPageLiveValidationRejectDumpFileName(pageOrdinal, slot, "expected")
			)
			val candidateFile = File(
				directory,
				readerPageLiveValidationRejectDumpFileName(pageOrdinal, slot, "candidate")
			)
			check(expected.writePng(expectedFile) && candidate.writePng(candidateFile))
			Logger.i(
				ReaderPageLiveValidationRejectDumpTag,
				"Live page-turn validation reject dump page=$pageOrdinal slot=$slot " +
					"expected=${expectedFile.absolutePath} candidate=${candidateFile.absolutePath} " +
					"expectedSize=${expected.width}x${expected.height} " +
					"candidateSize=${candidate.width}x${candidate.height}"
			)
		}.onFailure { failure ->
			Logger.w(
				ReaderPageLiveValidationRejectDumpTag,
				"Live page-turn validation reject dump failed page=$pageOrdinal slot=$slot " +
					"failureClass=${failure::class.simpleName ?: "unknown"}"
			)
		}
	}

	private fun Bitmap.writePng(target: File): Boolean =
		!isRecycled && target.outputStream().use { output ->
			compress(Bitmap.CompressFormat.PNG, 100, output)
		}
}
