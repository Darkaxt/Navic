package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class PlaybackDiagnosticsSourceTest {
	@Test
	fun mediaPlayerViewModelLogsPauseAndRecoveryBoundaries() {
		val source = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt").readText() +
			"\n" +
			androidSharedSourceFile("AndroidPlaybackDownloadRecoveryCoordinator.android.kt").readText() +
			"\n" +
			androidSharedSourceFile("AndroidPlaybackDiagnosticsLogger.android.kt").readText()

		assertContains(source, "private val playbackDiagnostics = AndroidPlaybackDiagnosticsLogger()")
		assertContains(source, "override fun onPlayWhenReadyChanged")
		assertContains(source, "playbackDiagnostics.onPlayWhenReadyChanged")
		assertContains(source, "override fun onPlaybackSuppressionReasonChanged")
		assertContains(source, "playbackDiagnostics.onPlaybackSuppressionReasonChanged")
		assertContains(source, "playbackDiagnostics.onIsPlayingChanged")
		assertContains(source, "playbackDiagnostics.onPlayerError")
		assertContains(source, "playbackDiagnostics.onRecoveryPending")
		assertContains(source, "playbackDiagnostics.onRecoveryDownloadStatus")
		assertContains(source, "playbackDiagnostics.onRecoveryLocalFileReady")
		assertContains(source, "playbackDiagnostics.onRecoveryCleared")
		assertContains(source, "onDeferredDownloadRequested")
		assertContains(source, "onPlaybackRecoveryDecision")
		assertContains(source, "onDeferredDownloadReady")
		assertContains(source, "onReplayLastPlayable")
		assertContains(source, "onPlaybackRetry")
		assertContains(source, "onHardPlaybackFailure")
		assertContains(source, "\"skip-to-next-playable\"")
		assertContains(source, "\"retry-playback-source\"")
		assertContains(source, "\"hard-playback-failure\"")
	}

	@Test
	fun playbackServiceLogsExplicitServicePauseAndResumeReasons() {
		val source = androidSharedSourceFile("MediaPlayer.android.kt").readText()

		assertContains(source, "logPlaybackServiceDiagnostic(\"pause-between-songs-paused\"")
		assertContains(source, "logPlaybackServiceDiagnostic(\"pause-between-songs-resumed\"")
		assertContains(source, "logPlaybackServiceDiagnostic(\"volume-zero-paused\"")
		assertContains(source, "logPlaybackServiceDiagnostic(\"volume-restored-resumed\"")
	}
}

private fun androidSharedSourceFile(fileName: String): File =
	listOf(
		File("src/androidMain/kotlin/paige/navic/shared/$fileName"),
		File("composeApp/src/androidMain/kotlin/paige/navic/shared/$fileName")
	).firstOrNull { it.isFile }
		?: error("Could not locate Android shared source file $fileName")
