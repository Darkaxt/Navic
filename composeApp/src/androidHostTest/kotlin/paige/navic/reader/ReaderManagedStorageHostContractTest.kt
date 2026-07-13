package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderManagedStorageHostContractTest {
	@Test
	fun androidReaderHostsUseManagedStorageAndReleaseSessionLeases() {
		val resourceText = readerAndroidPackageFile("ReaderPublicationResource.android.kt").readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val publicationHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()
		val readaloudHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()
		val fontImporterText = repoFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/settings/ReaderFontImporter.android.kt"
		).readText()
		val storageManagerText = repoFile(
			"composeApp/src/androidMain/kotlin/paige/navic/domain/manager/StorageManager.android.kt"
		).readText()

		assertContains(resourceText, "readerManagedStorageRoot(context)")
		assertContains(webViewHostText, "readerManagedStorageRoot(context)")
		assertFalse(webViewHostText.contains("readerPublicationCacheRoot(context)"))
		assertContains(fontImporterText, "ReaderImportedFontCache(readerManagedStorageRoot(context))")

		assertContains(publicationHostText, "val sessionLeases = remember { mutableListOf<ReaderSessionLease>() }")
		assertContains(publicationHostText, "sessionLeases += resolved.sessionLease")
		assertContains(publicationHostText, "sessionLeases.forEach(ReaderSessionLease::release)")

		assertContains(readaloudHostText, "val sessionLeases = remember { mutableListOf<ReaderSessionLease>() }")
		assertContains(readaloudHostText, "sessionLeases += loadedRuntime.sessionLease")
		val readaloudDisposal = readaloudHostText
			.substringAfter("DisposableEffect(controller, sessionLeases)")
			.substringBefore("LaunchedEffect(reader.bookId")
		assertTrue(
			readaloudDisposal.indexOf("controller.release()") <
				readaloudDisposal.indexOf("sessionLeases.forEach(ReaderSessionLease::release)"),
			"Media3 must release its file handles before read-aloud session files are deleted."
		)

		assertContains(storageManagerText, "readerSessionStorageSizeBytes(context)")
		assertContains(storageManagerText, "clearReaderSessionStorage(context)")
		assertFalse(storageManagerText.contains("File(context.cacheDir, \"reader/reader-publications\")"))
	}
}
