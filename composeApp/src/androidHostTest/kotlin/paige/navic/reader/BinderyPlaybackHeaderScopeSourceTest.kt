package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class BinderyPlaybackHeaderScopeSourceTest {
	@Test
	fun readaloudServiceResolvesHeadersPerRequestedUri() {
		val source = File(androidSourceRoot(), "reader/ReadaloudPlaybackService.android.kt").readText()

		assertFalse("setDefaultRequestProperties(binderyApiKeyHeaders" in source)
		assertContains(source, "ResolvingDataSource.Factory")
		assertContains(source, "dataSpec.uri.toString()")
		assertContains(source, "binderyRequestHeadersForUrl")
		assertContains(source, "dataSpec.withAdditionalHeaders")
	}

	@Test
	fun notificationArtworkHeaderProviderReceivesRequestedUri() {
		val loader = File(androidSourceRoot(), "ui/components/common/CoilBitmapLoader.kt").readText()
		val service = File(androidSourceRoot(), "reader/ReadaloudPlaybackService.android.kt").readText()

		assertContains(loader, "private val requestHeaders: (Uri) -> Map<String, String>")
		assertContains(loader, "requestHeaders(uri).toNetworkHeaders()")
		assertContains(service, "CoilBitmapLoader(this) { uri ->")
		assertContains(service, "url = uri.toString()")
	}

	private fun androidSourceRoot(): File = listOf(
		File("src/androidMain/kotlin/paige/navic"),
		File("composeApp/src/androidMain/kotlin/paige/navic")
	).firstOrNull(File::isDirectory)
		?: error("Could not find Android source root")
}
