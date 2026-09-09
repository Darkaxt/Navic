package paige.navic.ui.screens.nowPlaying.viewmodels

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisualEnrichmentSourceTest {
	@Test
	fun lifecycleResumesAndPausesEveryAutomaticConsumer() {
		val effect = source("ui/components/common/VisualContentLifecycleEffect.kt")
		assertContains(effect, "LifecycleResumeEffect(visible, owner)")
		assertContains(effect, "onActiveChanged(visible)")
		assertContains(effect, "onPauseOrDispose { onActiveChanged(false) }")
		assertFalse(effect.contains("rememberUpdatedState"))
		assertContains(source("ui/screens/nowPlaying/NowPlayingScreen.kt"),
			"VisualContentLifecycleEffect(isNowPlayingVisible, viewModel, viewModel::setVisualContentActive)")
		assertContains(source("ui/screens/lyrics/LyricsScreen.kt"), "viewModel::setVisualContentActive")
		assertContains(source("ui/screens/lyrics/LyricsScreen.kt"), "owner = viewModel")
		assertContains(source("ui/screens/nowPlaying/components/LidaClipVideo.kt"),
			"val shouldLoadClip = visualContentActive && startup == null")
	}

	@Test
	fun skippedLookupsDoNotConsumeDedupeKeysOrTriggerClipDownloads() {
		val viewModel = source("ui/screens/nowPlaying/viewmodels/NowPlayingViewModel.kt")
		val clipLookup = viewModel.substringAfter("private fun loadLidaClip(").substringBefore("private fun canLoadLidaClip(")
		assertTrue(clipLookup.indexOf("if (!canRequestVisualContent(song.id)) return") <
			clipLookup.indexOf("lastLidaClipsPrefetchKey = nextPrefetchKey"))
		val discovered = clipLookup.substringAfter(".onSuccess { clip ->")
		assertTrue(discovered.indexOf("if (!canRequestVisualContent(song.id))") <
			discovered.indexOf("getOrQueueClipForPlayback("))
		assertContains(discovered, "deferClipLookup(song.id)")
		assertContains(viewModel, "lastLidaClipsPrefetchKey = null")
		assertContains(discovered, "canStartRequest = { canRequestVisualContent(song.id) }")
		val downloads = source("domain/manager/LidaClipDownloadManager.kt")
		val admission = downloads.substringAfter("val activeDownload = activeDownloadsMutex.withLock")
		assertTrue(admission.indexOf("if (!canStartRequest()) return@withLock null") < admission.indexOf("job.start()"))
		val lyricsLookup = viewModel.substringAfter("private fun loadLyrics(")
		assertTrue(lyricsLookup.indexOf("if (!canRequestVisualContent(song.id)) return") <
			lyricsLookup.indexOf("currentLyricsSongId = song.id"))
	}

	@Test
	fun lyricsPanelDoesNotStartFetchingInItsConstructor() {
		val source = source("ui/screens/lyrics/viewmodels/LyricsScreenViewModel.kt")
		assertFalse(source.contains("init {"))
		assertContains(source, "if (!visualContentActive)")
		assertContains(source, "refreshPending = true")
		assertContains(source, "if (active && (refreshPending || lyricsState.value is UiState.Error)) refreshResults()")
	}

	private fun source(path: String): String {
		val relative = "src/commonMain/kotlin/paige/navic/$path"
		return sequenceOf(File(relative), File("composeApp/$relative"))
			.first { it.isFile }.readText()
	}
}
