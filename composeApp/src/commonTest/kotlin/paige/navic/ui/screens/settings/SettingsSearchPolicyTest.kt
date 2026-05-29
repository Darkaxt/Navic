package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchPolicyTest {
	private val entries = listOf(
		SettingsSearchEntryText(
			id = "respect-audio-focus",
			path = "Settings > Playback",
			title = "Respect audio focus",
			subtitle = "Turn off to keep Navic playing while WhatsApp or other apps play audio",
			keywords = listOf("WhatsApp", "training")
		),
		SettingsSearchEntryText(
			id = "lida-clips-api-key",
			path = "Settings > Data & Storage > Music video clips",
			title = "LidaClips API key",
			subtitle = null,
			keywords = listOf("clips", "video")
		),
		SettingsSearchEntryText(
			id = "rotate-playing-artwork",
			path = "Settings > Now Playing > Layout",
			title = "Rotate playing artwork",
			subtitle = "Spin the active Now Playing cover while music is playing"
		)
	)

	@Test
	fun blankQueryReturnsNoFilteredResults() {
		assertTrue(filteredSettingsSearchEntries(entries, "").isEmpty())
		assertTrue(filteredSettingsSearchEntries(entries, "   ").isEmpty())
	}

	@Test
	fun queryMatchesTitleSubtitlePathAndKeywords() {
		assertEquals(
			listOf("respect-audio-focus"),
			filteredSettingsSearchEntries(entries, "whatsapp").map { it.id }
		)
		assertEquals(
			listOf("lida-clips-api-key"),
			filteredSettingsSearchEntries(entries, "data clips").map { it.id }
		)
		assertEquals(
			listOf("rotate-playing-artwork"),
			filteredSettingsSearchEntries(entries, "spin cover").map { it.id }
		)
	}

	@Test
	fun queryIgnoresCaseAndExtraWhitespace() {
		assertEquals(
			listOf("respect-audio-focus"),
			filteredSettingsSearchEntries(entries, "  AUDIO   Focus ").map { it.id }
		)
	}

	@Test
	fun filteredResultsAreGroupedBySettingsPath() {
		val playbackFade = SettingsSearchEntryText(
			id = "audio-fade",
			path = "Settings > Playback",
			title = "Audio fade",
			subtitle = "Fade playback on pause and resume"
		)
		val groups = filteredSettingsSearchEntryGroups(
			entries = entries + playbackFade,
			query = "settings"
		)

		assertEquals(
			listOf(
				"Settings > Playback" to listOf("respect-audio-focus", "audio-fade"),
				"Settings > Data & Storage > Music video clips" to listOf("lida-clips-api-key"),
				"Settings > Now Playing > Layout" to listOf("rotate-playing-artwork")
			),
			groups.map { group -> group.path to group.entries.map { it.id } }
		)
	}

	@Test
	fun filteredResultItemsKeepEachMatchingRowWithItsOwnPath() {
		val playbackFade = SettingsSearchEntryText(
			id = "audio-fade",
			path = "Settings > Playback",
			title = "Audio fade",
			subtitle = "Fade playback on pause and resume"
		)

		val results = filteredSettingsSearchResultItems(
			entries = entries + playbackFade,
			query = "settings"
		)

		assertEquals(
			listOf(
				"Settings > Playback" to "respect-audio-focus",
				"Settings > Data & Storage > Music video clips" to "lida-clips-api-key",
				"Settings > Now Playing > Layout" to "rotate-playing-artwork",
				"Settings > Playback" to "audio-fade"
			),
			results.map { result -> result.path to result.entry.id }
		)
	}
}
