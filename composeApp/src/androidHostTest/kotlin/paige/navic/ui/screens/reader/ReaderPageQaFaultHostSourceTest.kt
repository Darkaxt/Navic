package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ReaderPageQaFaultHostSourceTest {
	@Test
	fun hostUsesOnePrivacySafeFaultSinkAndIdentitySafeRegistration() {
		val source = hostSource()

		assertEquals(
			1,
			Regex("ReaderPageQaFaultRegistry\\(").findAll(source).count()
		)
		assertContains(source, "ReaderPageQaFaultEventSink { event ->")
		assertContains(
			source,
			"onOwnershipMutated = applicationOwnershipEpoch::ownerMutationCommitted"
		)
		assertContains(
			source,
			"ReaderPageDiagnostic.qaFault(readerDiagnosticSession, event)"
		)
		assertContains(source, "ReaderPageQaFaultControl.attach(qaFaultRegistry)")
		assertContains(
			source,
			"ReaderPageQaFaultControl.detach(qaFaultRegistration)"
		)
		assertContains(source, "qaFaultRegistry.closeAndDrain()")
	}
}

private fun hostSource(): String {
	var current: File? =
		File(checkNotNull(System.getProperty("user.dir"))).canonicalFile
	repeat(10) {
		val root = current ?: return@repeat
		val candidate = File(
			root,
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"KomikkuReaderNativeFrameHost.android.kt"
		)
		if (candidate.isFile) return candidate.readText()
		current = root.parentFile
	}
	error("Could not locate KomikkuReaderNativeFrameHost.android.kt")
}
