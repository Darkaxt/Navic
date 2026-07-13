package paige.navic.reader

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal const val ReaderManagedStorageDirectoryName = "reader"
internal const val ReaderFontStorageDirectoryName = "fonts"
internal const val ReaderPublicationSessionDirectoryName = "reader-publications"
internal const val ReaderReadaloudSessionDirectoryName = "storyteller-readaloud"

private val readerStorageInitializationLock = Any()
private val initializedReaderStorageRoots = mutableSetOf<String>()

internal fun readerManagedStorageRoot(context: Context): File {
	val managedRoot = File(context.filesDir, ReaderManagedStorageDirectoryName)
	val rootIdentity = managedRoot.absoluteFile.normalize().path
	synchronized(readerStorageInitializationLock) {
		if (initializedReaderStorageRoots.add(rootIdentity)) {
			initializeReaderManagedStorage(
				managedRoot = managedRoot,
				legacyRoot = File(context.cacheDir, ReaderManagedStorageDirectoryName)
			)
		}
	}
	return managedRoot
}

internal fun initializeReaderManagedStorage(
	managedRoot: File,
	legacyRoot: File
) {
	managedRoot.mkdirs()
	migrateReaderFontStorage(
		source = legacyRoot.resolve(ReaderFontStorageDirectoryName),
		target = managedRoot.resolve(ReaderFontStorageDirectoryName)
	)
	ReaderSessionStorageDirectoryNames.forEach { directoryName ->
		managedRoot.resolve(directoryName).deleteRecursively()
		legacyRoot.resolve(directoryName).deleteRecursively()
	}
}

class ReaderSessionLease private constructor(
	private val directories: List<File>
) {
	private val released = AtomicBoolean(false)

	fun release(): Int {
		if (!released.compareAndSet(false, true)) return 0
		return directories.count { directory ->
			directory.exists() && directory.deleteRecursively()
		}
	}

	operator fun plus(other: ReaderSessionLease): ReaderSessionLease =
		ReaderSessionLease((directories + other.directories).distinctBy(File::getPath))

	companion object {
		fun of(vararg directories: File): ReaderSessionLease =
			ReaderSessionLease(
				directories
					.map { directory -> directory.absoluteFile.normalize() }
					.filter { directory -> directory.parentFile?.name in ReaderSessionStorageDirectoryNames }
					.distinctBy(File::getPath)
			)
	}
}

internal fun readerSessionStorageSizeBytes(vararg roots: File): Long =
	roots.sumOf { root ->
		ReaderSessionStorageDirectoryNames.sumOf { directoryName ->
			root.resolve(directoryName)
				.walkTopDown()
				.filter(File::isFile)
				.sumOf { file -> file.length().coerceAtLeast(0L) }
		}
	}

internal fun clearReaderSessionStorage(vararg roots: File): Int =
	roots.sumOf { root ->
		ReaderSessionStorageDirectoryNames.count { directoryName ->
			val directory = root.resolve(directoryName)
			directory.exists() && directory.deleteRecursively()
		}
	}

private fun migrateReaderFontStorage(source: File, target: File) {
	if (!source.exists()) return
	if (!target.exists()) {
		target.parentFile?.mkdirs()
		if (source.renameTo(target)) return
	}
	source.walkBottomUp().forEach { sourceEntry ->
		val relativePath = sourceEntry.relativeTo(source).path
		val targetEntry = if (relativePath.isEmpty()) target else target.resolve(relativePath)
		if (sourceEntry.isDirectory) {
			targetEntry.mkdirs()
			if (sourceEntry != source) sourceEntry.delete()
		} else if (sourceEntry.isFile) {
			moveReaderFontFile(sourceEntry, targetEntry)
		}
	}
	source.delete()
}

private fun moveReaderFontFile(source: File, target: File) {
	target.parentFile?.mkdirs()
	if (target.isFile && target.length() > 0L) {
		source.delete()
		return
	}
	if (target.exists()) target.deleteRecursively()
	if (source.renameTo(target)) return
	source.copyTo(target, overwrite = true)
	if (target.isFile && target.length() == source.length()) {
		source.delete()
	}
}

private val ReaderSessionStorageDirectoryNames = listOf(
	ReaderPublicationSessionDirectoryName,
	ReaderReadaloudSessionDirectoryName
)
