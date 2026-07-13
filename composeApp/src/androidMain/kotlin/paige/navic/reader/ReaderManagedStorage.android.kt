package paige.navic.reader

import android.content.Context
import java.io.File

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
