package paige.navic.ui.screens.musicBrainz

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_album_replay_gain
import navic.composeapp.generated.resources.info_track_album
import navic.composeapp.generated.resources.info_track_artist
import navic.composeapp.generated.resources.info_track_bit_depth
import navic.composeapp.generated.resources.info_track_bitrate
import navic.composeapp.generated.resources.info_track_channel_count
import navic.composeapp.generated.resources.info_track_disc_number
import navic.composeapp.generated.resources.info_track_duration
import navic.composeapp.generated.resources.info_track_file_size
import navic.composeapp.generated.resources.info_track_format
import navic.composeapp.generated.resources.info_track_genre
import navic.composeapp.generated.resources.info_track_name
import navic.composeapp.generated.resources.info_track_number
import navic.composeapp.generated.resources.info_track_path
import navic.composeapp.generated.resources.info_track_replay_gain
import navic.composeapp.generated.resources.info_track_replay_gain_effective
import navic.composeapp.generated.resources.info_track_sampling_rate
import navic.composeapp.generated.resources.info_track_year
import org.jetbrains.compose.resources.StringResource
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.repositories.MusicBrainzMetadataDisplayField
import paige.navic.domain.repositories.MusicBrainzMetadataField
import paige.navic.domain.repositories.MusicBrainzTrackMetadata
import paige.navic.domain.repositories.musicBrainzMetadataDisplayFields
import paige.navic.domain.repositories.musicBrainzMetadataUrlOrNull
import paige.navic.util.core.effectiveGain
import paige.navic.util.core.toFileSize
import paige.navic.util.core.toHoursMinutesSeconds

internal data class MusicBrainzInfoTrackRow(
	val title: StringResource,
	val value: String
)

internal data class MusicBrainzInfoResourceLink(
	val label: String,
	val url: String
)

internal fun musicBrainzInfoTrackRows(
	song: DomainSong,
	replayGainMode: ReplayGainMode
): List<MusicBrainzInfoTrackRow> = buildList {
	addTrackRow(Res.string.info_track_name, song.title)
	addTrackRow(Res.string.info_track_artist, song.artistName)
	addTrackRow(Res.string.info_track_album, song.albumTitle)
	addTrackRow(Res.string.info_track_number, song.trackNumber)
	addTrackRow(Res.string.info_track_disc_number, song.discNumber)
	addTrackRow(Res.string.info_track_year, song.year)
	addTrackRow(Res.string.info_track_genre, song.genre)
	addTrackRow(Res.string.info_track_duration, song.duration.toHoursMinutesSeconds())
	addTrackRow(Res.string.info_track_format, song.mimeType)
	addTrackRow(Res.string.info_track_bitrate, song.bitRate?.let { "$it kbps" })
	addTrackRow(Res.string.info_track_bit_depth, song.bitDepth)
	addTrackRow(Res.string.info_track_sampling_rate, song.sampleRate?.let { "$it Hz" })
	addTrackRow(Res.string.info_track_channel_count, song.audioChannelCount)
	addTrackRow(Res.string.info_track_file_size, song.fileSize.toFileSize())
	addTrackRow(Res.string.info_track_path, song.filePath)
	addTrackRow(Res.string.info_track_replay_gain, song.replayGain?.trackGain?.let { "$it dB" })
	addTrackRow(Res.string.info_album_replay_gain, song.replayGain?.albumGain?.let { "$it dB" })
	addTrackRow(
		title = Res.string.info_track_replay_gain_effective,
		value = song.replayGain?.effectiveGain(replayGainMode)
	)
}

internal fun musicBrainzInfoMetadataRows(
	metadata: MusicBrainzTrackMetadata?
): List<MusicBrainzMetadataDisplayField> =
	musicBrainzMetadataDisplayFields(metadata)
		.filterNot { row -> row.field in MusicBrainzInfoResourceFields }

internal fun musicBrainzInfoResourceLinks(
	metadata: MusicBrainzTrackMetadata?
): List<MusicBrainzInfoResourceLink> {
	if (metadata == null) return emptyList()
	return buildList {
		metadata.externalLinks.forEach { link ->
			val url = link.url.trim().takeIf { it.isNotEmpty() } ?: return@forEach
			add(MusicBrainzInfoResourceLink(label = link.label.trim().ifBlank { url }, url = url))
		}
		addMusicBrainzUrl("Recording", MusicBrainzMetadataField.RecordingUrl, metadata.recordingUrl)
		addMusicBrainzUrl("Release", MusicBrainzMetadataField.ReleaseUrl, metadata.releaseUrl)
		addMusicBrainzUrl("Release group", MusicBrainzMetadataField.ReleaseGroupUrl, metadata.releaseGroupUrl)
	}.distinctBy { it.url.lowercase() }
}

private val MusicBrainzInfoResourceFields = setOf(
	MusicBrainzMetadataField.ExternalLink,
	MusicBrainzMetadataField.RecordingUrl,
	MusicBrainzMetadataField.ReleaseUrl,
	MusicBrainzMetadataField.ReleaseGroupUrl
)

private fun MutableList<MusicBrainzInfoTrackRow>.addTrackRow(
	title: StringResource,
	value: Any?
) {
	val displayValue = when (value) {
		is String -> value.trim().takeIf { it.isNotEmpty() }
		else -> value?.toString()?.trim()?.takeIf { it.isNotEmpty() }
	} ?: return
	add(MusicBrainzInfoTrackRow(title = title, value = displayValue))
}

private fun MutableList<MusicBrainzInfoResourceLink>.addMusicBrainzUrl(
	label: String,
	field: MusicBrainzMetadataField,
	value: String?
) {
	val url = musicBrainzMetadataUrlOrNull(field, value) ?: return
	add(MusicBrainzInfoResourceLink(label = label, url = url))
}
