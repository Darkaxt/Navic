package paige.navic.ui.screens.nowPlaying

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingScreenOnSourceTest {
	@Test
	fun settingsScreenKeepsTheScreenOnModeInsideTheAndroidGuard() {
		val source = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/NowPlayingScreen.kt"
		).readText()

		assertContains(source, "import paige.navic.util.core.PlatformType")
		assertContains(source, "import paige.navic.domain.models.settings.NowPlayingScreenOnMode")
		val screenOnSelection = source.indexOf(
			"selection = preferenceManager.nowPlayingScreenOnMode"
		)
		assertTrue(screenOnSelection >= 0)
		val androidGuardStart = source.lastIndexOf(
			"if (platformContext.platformType == PlatformType.Android)",
			screenOnSelection
		)
		assertTrue(androidGuardStart >= 0)
		val androidGuardEnd = blockEnd(source, androidGuardStart)
		val swipeToSkipRowStart = source.indexOf(
			"title = { Text(stringResource(Res.string.option_swipe_to_skip)) }"
		)
		val rowStart = source.lastIndexOf("SettingSelectionRow(", screenOnSelection)
		val backgroundRowStart = source.indexOf(
			"items = NowPlayingBackgroundStyle.entries.toImmutableList()"
		)

		assertTrue(screenOnSelection in (androidGuardStart + 1)..androidGuardEnd)
		assertTrue(
			swipeToSkipRowStart < androidGuardStart &&
				androidGuardStart < screenOnSelection &&
				screenOnSelection < backgroundRowStart,
			"The Android-only row must follow swipe-to-skip and precede background style."
		)
		val row = source.substring(rowStart, backgroundRowStart)
		listOf(
			"NowPlayingScreenOnMode.entries.toImmutableList()",
			"stringResource(it.displayName)",
			"selection = preferenceManager.nowPlayingScreenOnMode",
			"onSelect = { preferenceManager.nowPlayingScreenOnMode = it }",
			"Res.string.subtitle_now_playing_screen_on_mode",
			"Res.string.option_now_playing_screen_on_mode"
		).forEach { marker -> assertContains(row, marker) }
	}

	@Test
	fun settingsSearchKeepsTheScreenOnModeInsideTheAndroidGuard() {
		val source = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchAppearanceRows.kt"
		).readText()

		val androidGuardStart = source.indexOf("if (isAndroid) {")
		val rowStart = source.indexOf("id = \"now-playing.screen-on-mode\"", androidGuardStart)

		assertTrue(androidGuardStart >= 0)
		assertTrue(rowStart in (androidGuardStart + 1)..blockEnd(source, androidGuardStart))
		val row = source.substring(rowStart, blockEnd(source, androidGuardStart))
		listOf(
			"path = path(nowPlaying, behaviour)",
			"Res.string.option_now_playing_screen_on_mode",
			"Res.string.subtitle_now_playing_screen_on_mode",
			"keywords = listOf(\"screen\", \"awake\", \"charging\", \"power\", \"playback\")",
			"items = NowPlayingScreenOnMode.entries",
			"stringResource(it.displayName)",
			"selection = preferenceManager.nowPlayingScreenOnMode",
			"onSelect = { preferenceManager.nowPlayingScreenOnMode = it }"
		).forEach { marker -> assertContains(row, marker) }
	}

	@Test
	fun nowPlayingOwnsTheScreenFlagOnlyForVisibleAndroidPlayback() {
		val source = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt"
		).readText()

		assertContains(
			source,
			"val isAndroidPlatform = LocalPlatformContext.current.platformType == PlatformType.Android"
		)
		assertContains(source, "val isNowPlayingVisible = currentScreen is Screen.NowPlaying")
		assertEquals(1, Regex("\\brememberExternalPowerConnected\\s*\\(").findAll(source).count())
		assertEquals(1, Regex("\\bKeepScreenOn\\s*\\(").findAll(source).count())

		val powerStateStart = source.indexOf("val isExternalPowerConnected = if (")
		val powerObserverCall = source.indexOf("rememberExternalPowerConnected() == true")
		val powerStateEnd = blockEnd(source, powerStateStart)
		assertTrue(powerStateStart >= 0)
		assertTrue(powerObserverCall in (powerStateStart + 1)..powerStateEnd)
		val powerGuard = source.substring(powerStateStart, powerObserverCall)
		listOf(
			"isAndroidPlatform &&",
			"isNowPlayingVisible &&",
			"screenOnMode == NowPlayingScreenOnMode.WhilePlayingAndCharging"
		).forEach { marker -> assertContains(powerGuard, marker) }
		assertContains(
			source,
			"""val shouldKeepScreenOn = shouldKeepNowPlayingScreenOn(
		mode = screenOnMode,
		hasActiveSong = song != null,
		isPaused = playerState.isPaused,
		isExternalPowerConnected = isExternalPowerConnected
	)"""
		)
		val keepScreenOnGuardStart = source.indexOf(
			"if (isAndroidPlatform && isNowPlayingVisible && shouldKeepScreenOn)"
		)
		val keepScreenOnCall = source.indexOf("KeepScreenOn()")
		assertTrue(keepScreenOnGuardStart >= 0)
		assertTrue(keepScreenOnCall in (keepScreenOnGuardStart + 1)..blockEnd(source, keepScreenOnGuardStart))
		assertTrue(
			"""val isPlayerCurrent = currentScreen is Screen.NowPlaying
		|| currentScreen is Screen.Queue
		|| currentScreen is Screen.PlaybackSpeed""" in source,
			"Queue and Playback Speed must remain current-player destinations without acquiring this flag."
		)
	}

	@Test
	fun settingsSearchDerivesAndroidFromTheTypedPlatform() {
		val source = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchRegistry.kt"
		).readText()

		assertContains(source, "import paige.navic.util.core.PlatformType")
		assertContains(source, "isAndroid = platformContext.platformType == PlatformType.Android")
		assertFalse(source.contains("platformContext.name.lowercase().startsWith(\"android\")"))
	}

	private fun blockEnd(source: String, blockStart: Int): Int {
		val openingBrace = source.indexOf('{', blockStart)
		var depth = 0
		for (index in openingBrace until source.length) {
			when (source[index]) {
				'{' -> depth++
				'}' -> if (--depth == 0) return index
			}
		}
		error("Unclosed block starting at $blockStart")
	}

	private fun sourceFile(path: String): File = listOf(
		File(path),
		File("../$path"),
		File(path.removePrefix("composeApp/"))
	).firstOrNull { it.isFile }
		?: error("Unable to locate $path")
}
