package paige.navic.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.shared.PlaybackService

class ReadaloudAudioControllerTest {
	@Test
	fun readaloudPlaybackUsesDedicatedMediaSessionService() {
		assertEquals(
			"paige.navic.reader.ReadaloudPlaybackService",
			ReadaloudPlaybackService.serviceClassName
		)
		assertNotEquals(
			PlaybackService::class.java.name,
			ReadaloudPlaybackService.serviceClassName
		)
		assertEquals(
			ReadaloudPlaybackService.serviceClassName,
			ReadaloudAudioController.serviceClassName
		)
	}

	@Test
	fun androidManifestDeclaresReadaloudMediaSessionService() {
		val service = androidManifestServices()
			.singleOrNull { it.androidAttribute("name") == ReadaloudPlaybackService.serviceClassName }

		assertNotNull(
			service,
			"ReadaloudPlaybackService must be declared in AndroidManifest.xml so Media3 can resolve its SessionToken."
		)
		assertEquals("true", service.androidAttribute("enabled"))
		assertEquals("true", service.androidAttribute("exported"))
		assertEquals("mediaPlayback", service.androidAttribute("foregroundServiceType"))
		assertEquals("\${applicationId}.permission.PLAYBACK_SERVICE", service.androidAttribute("permission"))
		assertTrue(
			service.childNodes.asSequence().any { node ->
				node.nodeName == "intent-filter" && node.childNodes.asSequence().any { child ->
					child.nodeName == "action" &&
						child.androidAttribute("name") == "androidx.media3.session.MediaSessionService"
				}
			},
			"ReadaloudPlaybackService must advertise androidx.media3.session.MediaSessionService."
		)
	}

	@Test
	fun readaloudMediaSessionUsesDedicatedSessionId() {
		val source = readaloudPlaybackServiceSourceFile().readText()

		assertContains(source, "const val sessionId")
		assertContains(source, ".setId(sessionId)")
	}

	private fun androidManifestServices() = DocumentBuilderFactory
		.newInstance()
		.apply { isNamespaceAware = true }
		.newDocumentBuilder()
		.parse(androidManifestFile())
		.getElementsByTagName("service")
		.asSequence()

	private fun androidManifestFile(): File = listOf(
		File("androidApp/src/main/AndroidManifest.xml"),
		File("../androidApp/src/main/AndroidManifest.xml")
	).firstOrNull { it.isFile }
		?: error("Unable to locate androidApp/src/main/AndroidManifest.xml")

	private fun readaloudPlaybackServiceSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/reader/ReadaloudPlaybackService.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReadaloudPlaybackService.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate ReadaloudPlaybackService.android.kt")

	private fun org.w3c.dom.Node.androidAttribute(name: String): String =
		attributes?.getNamedItemNS(ANDROID_NAMESPACE, name)?.nodeValue.orEmpty()

	private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> =
		(0 until length).asSequence().map(::item)

	private companion object {
		private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
	}
}
