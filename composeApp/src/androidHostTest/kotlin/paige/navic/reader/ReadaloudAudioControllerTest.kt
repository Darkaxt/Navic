package paige.navic.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
		assertEquals(
			"false",
			service.androidAttribute("exported"),
			"Readaloud playback is app-internal and must not expose a second external Media3 service."
		)
		assertEquals("mediaPlayback", service.androidAttribute("foregroundServiceType"))
		assertEquals("\${applicationId}.permission.PLAYBACK_SERVICE", service.androidAttribute("permission"))
		assertTrue(
			service.handlesMediaSessionServiceAction(),
			"ReadaloudPlaybackService must advertise MediaSessionService so its explicit SessionToken resolves."
		)
	}

	@Test
	fun mediaButtonReceiverTargetsMainPlaybackServiceWithoutMedia3ServiceDiscovery() {
		val receiverNames = androidManifestReceivers().map { it.androidAttribute("name") }.toList()
		val source = playbackMediaButtonReceiverSourceFile().readText()
		val widgetSource = nowPlayingWidgetSourceFile().readText()

		assertContains(receiverNames, "paige.navic.shared.PlaybackMediaButtonReceiver")
		assertFalse(receiverNames.contains("androidx.media3.session.MediaButtonReceiver"))
		assertContains(source, "ComponentName(context, PlaybackService::class.java)")
		assertContains(source, "ContextCompat.startForegroundService(context, serviceIntent)")
		assertContains(widgetSource, "PlaybackMediaButtonReceiver::class.java")
	}

	@Test
	fun readaloudMediaSessionUsesDedicatedSessionId() {
		val source = readaloudPlaybackServiceSourceFile().readText()

		assertContains(source, "const val sessionId")
		assertContains(source, ".setId(sessionId)")
	}

	@Test
	fun readaloudControllerPublishesPositionBeforeRelease() {
		val source = readaloudAudioControllerSourceFile().readText()
		val releaseBody = source.substringAfter("fun release()").substringBefore("\n\t}")

		assertContains(releaseBody, "publishPosition()")
	}

	@Test
	fun wordBoundaryRuntimeReadsFreshMedia3TimelineAndInvalidatesOnPlayerEvents() {
		val controller = readaloudAudioControllerSourceFile().readText()
		val manager = androidAudiobookPlaybackManagerSourceFile().readText()

		assertContains(controller, "fun currentPosition(): ReadaloudPlaybackPosition?")
		assertContains(controller, "override fun onPositionDiscontinuity(")
		assertContains(controller, "override fun onPlaybackParametersChanged(")
		assertContains(controller, "onTimelineChanged()")
		assertContains(manager, "override val playbackTimelineRevision")
		assertContains(manager, "override fun currentPlaybackTimelineSnapshot()")
		assertContains(manager, "controller.currentPosition()")
	}

	@Test
	fun readaloudControllerUsesSharedConnectionOwnerAndHandlesDisconnects() {
		val source = readaloudAudioControllerSourceFile().readText()

		assertContains(source, "FutureConnectionOwner<MediaController>")
		assertContains(source, "MediaController.Listener")
		assertContains(source, "connectionOwner.disconnect(disconnectedController)")
		assertContains(source, "connectionOwner.close()")
	}

	private fun androidManifestServices() = DocumentBuilderFactory
		.newInstance()
		.apply { isNamespaceAware = true }
		.newDocumentBuilder()
		.parse(androidManifestFile())
		.getElementsByTagName("service")
		.asSequence()

	private fun androidManifestReceivers() = DocumentBuilderFactory
		.newInstance()
		.apply { isNamespaceAware = true }
		.newDocumentBuilder()
		.parse(androidManifestFile())
		.getElementsByTagName("receiver")
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

	private fun readaloudAudioControllerSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/reader/ReadaloudAudioController.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReadaloudAudioController.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate ReadaloudAudioController.android.kt")

	private fun androidAudiobookPlaybackManagerSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/AndroidAudiobookPlaybackManager.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidAudiobookPlaybackManager.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate AndroidAudiobookPlaybackManager.kt")

	private fun playbackMediaButtonReceiverSourceFile(): File = listOf(
		File("src/androidMain/kotlin/paige/navic/shared/PlaybackMediaButtonReceiver.android.kt"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/PlaybackMediaButtonReceiver.android.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate PlaybackMediaButtonReceiver.android.kt")

	private fun nowPlayingWidgetSourceFile(): File = listOf(
		File("../androidApp/src/main/kotlin/paige/navic/androidApp/widgets/nowplaying/NowPlayingWidget.kt"),
		File("androidApp/src/main/kotlin/paige/navic/androidApp/widgets/nowplaying/NowPlayingWidget.kt")
	).firstOrNull { it.isFile }
		?: error("Unable to locate NowPlayingWidget.kt")

	private fun org.w3c.dom.Node.androidAttribute(name: String): String =
		attributes?.getNamedItemNS(ANDROID_NAMESPACE, name)?.nodeValue.orEmpty()

	private fun org.w3c.dom.Node.handlesMediaSessionServiceAction(): Boolean =
		childNodes.asSequence().any { node ->
			node.nodeName == "intent-filter" && node.childNodes.asSequence().any { child ->
				child.nodeName == "action" &&
					child.androidAttribute("name") == MEDIA_SESSION_SERVICE_ACTION
			}
		}

	private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> =
		(0 until length).asSequence().map(::item)

	private companion object {
		private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
		private const val MEDIA_SESSION_SERVICE_ACTION = "androidx.media3.session.MediaSessionService"
	}
}
