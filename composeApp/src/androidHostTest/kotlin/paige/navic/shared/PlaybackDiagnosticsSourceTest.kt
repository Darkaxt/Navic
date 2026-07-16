package paige.navic.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains

class PlaybackDiagnosticsSourceTest {
	@Test
	fun mediaPlayerViewModelLogsPauseAndRecoveryBoundaries() {
		val source = androidSharedSourceFile("AndroidMediaPlayerViewModel.android.kt").readText() +
			"\n" +
			androidSharedSourceFile("AndroidStablePlaybackRecoveryCoordinator.android.kt").readText() +
			"\n" +
			androidSharedSourceFile("AndroidPlaybackDiagnosticsLogger.android.kt").readText()

		assertContains(source, "private val playbackDiagnostics = AndroidPlaybackDiagnosticsLogger()")
		assertContains(source, "override fun onPlayWhenReadyChanged")
		assertContains(source, "playbackDiagnostics.onPlayWhenReadyChanged")
		assertContains(source, "override fun onPlaybackSuppressionReasonChanged")
		assertContains(source, "playbackDiagnostics.onPlaybackSuppressionReasonChanged")
		assertContains(source, "playbackDiagnostics.onIsPlayingChanged")
		assertContains(source, "playbackDiagnostics.onPlayerError")
		assertContains(source, "playbackDiagnostics.onQueueSelection")
		assertContains(source, "onRecoveryLocalFileReady(")
		assertContains(source, "onPlaybackRecoveryDecision")
		assertContains(source, "onPlaybackRetry")
		assertContains(source, "onHardPlaybackFailure")
		assertContains(source, "\"retry-playback-source\"")
		assertContains(source, "\"hard-playback-failure\"")
		assertContains(source, "\"queue-selection\"")
		assertContains(source, "\"recovery-terminal-held\"")
		assertContains(source, "\"recovery-terminal-advanced\"")
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
