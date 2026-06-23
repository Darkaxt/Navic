package paige.navic.ui.screens.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paige.navic.domain.models.MaxNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MaxNowPlayingBackgroundDimPercent
import paige.navic.domain.models.MinNowPlayingBackgroundBlurDp
import paige.navic.domain.models.MinNowPlayingBackgroundDimPercent
import paige.navic.domain.models.LidaClipsVideoCacheSizeOptionsMb
import paige.navic.domain.models.lidaClipsVideoCacheSizeLabel
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.models.nowPlayingBackgroundBlurDp
import paige.navic.domain.models.settings.*
import paige.navic.reader.DefaultReaderParagraphSpacingPercent
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderFontSourceNavic
import paige.navic.reader.ReaderLightTheme
import paige.navic.reader.ReaderOrientationDefault
import paige.navic.reader.ReaderNavBarTypeVerticalRight
import paige.navic.reader.ReaderPdfFitWidth
import paige.navic.reader.ReaderSansFontFamily
import paige.navic.reader.ReaderTapZoneDefault
import paige.navic.reader.ReaderTapZoneInvertNone
import kotlin.math.roundToInt

@Composable
internal fun settingsSearchIntegrationRows(context: SettingsSearchContext): List<SearchableSettingsRow> = with(context) {
	buildList {
		add(switchRow(
			id = "lida.enabled",
			path = path(integrations, lidaClips),
			title = stringResource(Res.string.option_lida_clips_enabled),
			subtitle = stringResource(Res.string.subtitle_lida_clips_enabled),
			keywords = listOf("music video clips"),
			value = preferenceManager.lidaClipsEnabled,
			onSetValue = { preferenceManager.lidaClipsEnabled = it }
		))
		add(textFieldRow(
			id = "lida.base-url",
			path = path(integrations, lidaClips),
			title = stringResource(Res.string.option_lida_clips_base_url),
			value = preferenceManager.lidaClipsBaseUrl,
			keywords = listOf("endpoint", "server", "music video clips"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.lidaClipsBaseUrl = it }
		))
		add(textFieldRow(
			id = "lida.api-key",
			path = path(integrations, lidaClips),
			title = stringResource(Res.string.option_lida_clips_api_key),
			value = preferenceManager.lidaClipsApiKey,
			keywords = listOf("token", "music video clips"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.lidaClipsApiKey = it }
		))
		if (isAndroid) {
			add(switchRow(
				id = "lida.pip",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_picture_in_picture),
				subtitle = stringResource(Res.string.subtitle_lida_clips_picture_in_picture),
				value = preferenceManager.lidaClipsPictureInPicture,
				onSetValue = { preferenceManager.lidaClipsPictureInPicture = it }
			))
			add(switchRow(
				id = "lida.landscape",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_landscape_video_mode),
				subtitle = stringResource(Res.string.subtitle_lida_clips_landscape_video_mode),
				value = preferenceManager.lidaClipsLandscapeVideoMode,
				onSetValue = { preferenceManager.lidaClipsLandscapeVideoMode = it }
			))
			add(selectionRow(
				id = "lida.background-video",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_background_video),
				subtitle = stringResource(Res.string.subtitle_lida_clips_background_video),
				items = LidaClipsBackgroundVideoMode.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.lidaClipsBackgroundVideoMode,
				onSelect = { preferenceManager.lidaClipsBackgroundVideoMode = it }
			))
			add(switchRow(
				id = "lida.lyrics-video-background",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_lyrics_video_background),
				subtitle = stringResource(Res.string.subtitle_lida_clips_lyrics_video_background),
				keywords = listOf("lyrics", "background", "music video clips"),
				value = preferenceManager.lidaClipsLyricsVideoBackground,
				onSetValue = { preferenceManager.lidaClipsLyricsVideoBackground = it }
			))
			add(switchRow(
				id = "lida.musicbrainz-video-background",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_musicbrainz_video_background),
				subtitle = stringResource(Res.string.subtitle_lida_clips_musicbrainz_video_background),
				keywords = listOf("musicbrainz", "trivia", "metadata", "background", "music video clips"),
				value = preferenceManager.lidaClipsMusicBrainzInfoVideoBackground,
				onSetValue = { preferenceManager.lidaClipsMusicBrainzInfoVideoBackground = it }
			))
			add(selectionRow(
				id = "lida.video-fit",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_video_fit),
				subtitle = stringResource(Res.string.subtitle_lida_clips_video_fit),
				items = LidaClipsVideoFitMode.entries,
				label = { stringResource(it.displayName) },
				selection = preferenceManager.lidaClipsVideoFitMode,
				onSelect = { preferenceManager.lidaClipsVideoFitMode = it }
			))
			add(selectionRow(
				id = "lida.video-cache-size",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_video_cache_size),
				subtitle = stringResource(Res.string.subtitle_lida_clips_video_cache_size),
				keywords = listOf("cache", "download", "offline", "music video clips"),
				items = LidaClipsVideoCacheSizeOptionsMb,
				label = { lidaClipsVideoCacheSizeLabel(it) },
				selection = preferenceManager.lidaClipsVideoCacheSizeMb,
				onSelect = { preferenceManager.lidaClipsVideoCacheSizeMb = it }
			))
			add(switchRow(
				id = "lida.save-with-downloads",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_save_with_downloads),
				subtitle = stringResource(Res.string.subtitle_lida_clips_save_with_downloads),
				keywords = listOf("download", "offline", "cache", "music video clips"),
				value = preferenceManager.lidaClipsSaveClipsWithDownloads,
				onSetValue = { preferenceManager.lidaClipsSaveClipsWithDownloads = it }
			))
			add(switchRow(
				id = "lida.pause-music",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_pause_music_playback),
				subtitle = stringResource(Res.string.subtitle_lida_clips_pause_music_playback),
				value = preferenceManager.lidaClipsPauseMusicPlayback,
				onSetValue = { preferenceManager.lidaClipsPauseMusicPlayback = it }
			))
			add(switchRow(
				id = "lida.remember-position",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_remember_playback_position),
				subtitle = stringResource(Res.string.subtitle_lida_clips_remember_playback_position),
				value = preferenceManager.lidaClipsRememberPlaybackPosition,
				onSetValue = { preferenceManager.lidaClipsRememberPlaybackPosition = it }
			))
			add(switchRow(
				id = "lida.keep-screen-on",
				path = path(integrations, lidaClips),
				title = stringResource(Res.string.option_lida_clips_keep_screen_on),
				subtitle = stringResource(Res.string.subtitle_lida_clips_keep_screen_on),
				value = preferenceManager.lidaClipsKeepScreenOn,
				onSetValue = { preferenceManager.lidaClipsKeepScreenOn = it }
			))
		}
		add(switchRow(
			id = "aurral.enabled",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_enabled),
			subtitle = stringResource(Res.string.subtitle_aurral_enabled),
			keywords = listOf("Aurral", "Flows", "artist acquisition", "self hosted"),
			value = preferenceManager.aurralEnabled,
			onSetValue = { preferenceManager.aurralEnabled = it }
		))
		add(selectionRow(
			id = "aurral.artist-artwork-priority",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_artist_artwork_priority),
			subtitle = stringResource(Res.string.subtitle_artist_artwork_priority),
			keywords = listOf("Aurral", "artist", "photo", "cover", "artwork"),
			items = ArtworkSourcePriority.entries,
			label = { stringResource(it.displayName) },
			selection = preferenceManager.artistArtworkPriority,
			onSelect = { preferenceManager.artistArtworkPriority = it }
		))
		add(selectionRow(
			id = "aurral.cover-artwork-priority",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_cover_artwork_priority),
			subtitle = stringResource(Res.string.subtitle_cover_artwork_priority),
			keywords = listOf("Aurral", "album", "track", "cover", "artwork"),
			items = ArtworkSourcePriority.entries,
			label = { stringResource(it.coverDisplayName) },
			selection = preferenceManager.coverArtworkPriority,
			onSelect = { preferenceManager.coverArtworkPriority = it }
		))
		add(switchRow(
			id = "lastfm.enabled",
			path = path(integrations, lastFm),
			title = stringResource(Res.string.option_lastfm_enabled),
			subtitle = stringResource(Res.string.subtitle_lastfm_enabled),
			keywords = listOf("Last.fm", "artist top tracks", "recommendations", "public metadata"),
			value = preferenceManager.lastFmEnabled,
			onSetValue = { preferenceManager.lastFmEnabled = it }
		))
		add(textFieldRow(
			id = "lastfm.api-key",
			path = path(integrations, lastFm),
			title = stringResource(Res.string.option_lastfm_api_key),
			value = preferenceManager.lastFmApiKey,
			keywords = listOf("Last.fm", "artist top tracks", "scrobble", "recommendations"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.lastFmApiKey = it }
		))
		add(switchRow(
			id = "bindery.enabled",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_enabled),
			subtitle = stringResource(Res.string.subtitle_bindery_enabled),
			keywords = listOf("Bindery", "OPDS", "audiobooks", "long-form"),
			value = preferenceManager.binderyEnabled,
			onSetValue = { preferenceManager.binderyEnabled = it }
		))
		add(textFieldRow(
			id = "bindery.opds-url",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_opds_url),
			value = preferenceManager.binderyOpdsBaseUrl,
			keywords = listOf("Bindery", "OPDS", "endpoint", "server", "audiobooks"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.binderyOpdsBaseUrl = it }
		))
		add(textFieldRow(
			id = "bindery.api-key",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_api_key),
			value = preferenceManager.binderyApiKey,
			keywords = listOf("Bindery", "OPDS", "token", "audiobooks"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.binderyApiKey = it }
		))
		add(textFieldRow(
			id = "bindery.language-filter",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_language_filter),
			value = preferenceManager.binderyLanguageFilter,
			keywords = listOf("Bindery", "OPDS", "language", "audiobooks", "books"),
			onValueChange = { preferenceManager.binderyLanguageFilter = it }
		))
		add(selectionRow(
			id = "bindery.book-grid-columns",
			path = path(integrations, bindery),
			title = stringResource(Res.string.option_bindery_book_grid_columns),
			subtitle = stringResource(Res.string.subtitle_bindery_book_grid_columns),
			keywords = listOf("Bindery", "audiobooks", "books", "collections", "grid", "columns"),
			items = binderyBookGridColumnSearchOptions,
			label = { columns -> columns.toString() },
			selection = normalizedBinderyBookGridColumns(preferenceManager.binderyBookGridColumns),
			onSelect = { columns -> preferenceManager.binderyBookGridColumns = columns }
		))
		add(textFieldRow(
			id = "aurral.base-url",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_base_url),
			value = preferenceManager.aurralBaseUrl,
			keywords = listOf("endpoint", "server", "Aurral", "Flows"),
			keyboardType = KeyboardType.Uri,
			onValueChange = { preferenceManager.aurralBaseUrl = it }
		))
		add(textFieldRow(
			id = "aurral.username",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_username),
			value = preferenceManager.aurralUsername,
			keywords = listOf("login", "Basic Auth", "Aurral"),
			onValueChange = { preferenceManager.aurralUsername = it }
		))
		add(textFieldRow(
			id = "aurral.password",
			path = path(integrations, aurral),
			title = stringResource(Res.string.option_aurral_password),
			value = preferenceManager.aurralPassword,
			keywords = listOf("login", "Basic Auth", "Aurral"),
			keyboardType = KeyboardType.Password,
			isPassword = true,
			onValueChange = { preferenceManager.aurralPassword = it }
		))

	}
}
