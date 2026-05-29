package paige.navic.ui.screens.musicBrainz

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MusicBrainzInfoResourceLabelTest {
	@Test
	fun metadataLabelsDoNotRepeatMusicBrainzSourcePrefix() {
		val strings = stringsXml().readText()
		val offenders = MetadataLabelKeys.mapNotNull { key ->
			val value = Regex("""<string name="$key">([^<]+)</string>""")
				.find(strings)
				?.groupValues
				?.get(1)
			key.takeIf { value?.startsWith("MusicBrainz ") == true }
		}

		assertTrue(
			offenders.isEmpty(),
			"Metadata labels should be normalized and source-neutral. Offenders: $offenders"
		)
	}

	private fun stringsXml(): File =
		listOf(
			File("src/commonMain/composeResources/values/strings.xml"),
			File("composeApp/src/commonMain/composeResources/values/strings.xml")
		).firstOrNull { it.isFile }
			?: error("Could not locate base strings.xml")
}

private val MetadataLabelKeys = listOf(
	"info_musicbrainz_recording_title",
	"info_musicbrainz_recording_disambiguation",
	"info_musicbrainz_artist_credit",
	"info_musicbrainz_first_release_date",
	"info_musicbrainz_release_title",
	"info_musicbrainz_release_disambiguation",
	"info_musicbrainz_release_group_title",
	"info_musicbrainz_release_group_disambiguation",
	"info_musicbrainz_release_group_type",
	"info_musicbrainz_release_date",
	"info_musicbrainz_country",
	"info_musicbrainz_status",
	"info_musicbrainz_genres",
	"info_musicbrainz_tags",
	"info_musicbrainz_isrcs",
	"info_musicbrainz_external_link",
	"info_musicbrainz_recording_url",
	"info_musicbrainz_release_url",
	"info_musicbrainz_release_group_url"
)
