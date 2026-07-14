package paige.navic.reader

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal const val ReaderManagedStorageDirectoryName = "reader"
internal const val ReaderFontStorageDirectoryName = "fonts"
internal const val ReaderPublicationSessionDirectoryName = "reader-publications"
internal const val ReaderReadaloudSessionDirectoryName = "storyteller-readaloud"
internal const val ReaderPageRasterStorageDirectoryName = "reader-page-rasters"
internal const val ReaderPageRasterStorageVersion = "v1"

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

internal fun readerLegacyStorageRoot(context: Context): File =
	File(context.cacheDir, ReaderManagedStorageDirectoryName)

internal fun readerPageRasterStorageRoot(context: Context): File =
	readerManagedStorageRoot(context)
		.resolve(ReaderPageRasterStorageDirectoryName)
		.resolve(ReaderPageRasterStorageVersion)
		.also(File::mkdirs)

internal fun clearReaderPageRasterStorage(context: Context): Int {
	val directory = readerManagedStorageRoot(context).resolve(ReaderPageRasterStorageDirectoryName)
	return if (directory.exists() && directory.deleteRecursively()) 1 else 0
}

internal fun readerSessionStorageSizeBytes(context: Context): Long =
	readerSessionStorageSizeBytes(
		readerManagedStorageRoot(context),
		readerLegacyStorageRoot(context)
	)

internal fun clearReaderSessionStorage(context: Context): Int =
	clearReaderSessionStorage(
		readerManagedStorageRoot(context),
		readerLegacyStorageRoot(context)
	)

internal fun initializeReaderManagedStorage(
	managedRoot: File,
	legacyRoot: File
) {
	managedRoot.mkdirs()
	migrateReaderFontStorage(
		source = legacyRoot.resolve(ReaderFontStorageDirectoryName),
		target = managedRoot.resolve(ReaderFontStorageDirectoryName)
	)
	removeObsoleteReaderPageRasterSchemas(managedRoot.resolve(ReaderPageRasterStorageDirectoryName))
	ReaderSessionStorageDirectoryNames.forEach { directoryName ->
		managedRoot.resolve(directoryName).deleteRecursively()
		legacyRoot.resolve(directoryName).deleteRecursively()
	}
}

private fun removeObsoleteReaderPageRasterSchemas(root: File) {
	root.listFiles().orEmpty().forEach { child ->
		when {
			child.name != ReaderPageRasterStorageVersion -> child.deleteRecursively()
			child.isDirectory -> child.listFiles().orEmpty()
				.filter { file -> file.name.endsWith(".tmp") }
				.forEach(File::delete)
		}
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
