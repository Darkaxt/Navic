package paige.navic.domain.manager

import paige.navic.domain.manager.base.BasePreferenceManager
import paige.navic.domain.models.settings.AudioReverbPreset
import paige.navic.domain.models.settings.AutoFillQueueSource
import paige.navic.domain.models.settings.AnimationStyle
import paige.navic.domain.models.settings.BottomBarCollapseMode
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.models.settings.CoverArtQuality
import paige.navic.domain.models.settings.CoverArtShape
import paige.navic.domain.models.DefaultNowPlayingBackgroundBlurDp
import paige.navic.domain.models.DefaultNowPlayingBackgroundDimPercent
import paige.navic.domain.models.DefaultLidaClipsVideoCacheSizeMb
import paige.navic.domain.models.QuickPicksDefaultSize
import paige.navic.domain.models.settings.FontOption
import paige.navic.domain.models.settings.GridSize
import paige.navic.domain.models.settings.LidaClipsBackgroundVideoMode
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import paige.navic.domain.models.settings.LyricsAlignment
import paige.navic.domain.models.settings.LyricsFontSize
import paige.navic.domain.models.settings.MarqueeSpeed
import paige.navic.domain.models.settings.MediaNotificationAction
import paige.navic.domain.models.settings.MiniPlayerProgressStyle
import paige.navic.domain.models.settings.MiniPlayerStyle
import paige.navic.domain.models.settings.NavigationBarLabelVisibility
import paige.navic.domain.models.settings.NavigationBarStyle
import paige.navic.domain.models.settings.NowPlayingArtworkSize
import paige.navic.domain.models.settings.NowPlayingArtworkTapAction
import paige.navic.domain.models.settings.NowPlayingBackgroundStyle
import paige.navic.domain.models.settings.NowPlayingInfoStyle
import paige.navic.domain.models.settings.NowPlayingProgressWidth
import paige.navic.domain.models.settings.NowPlayingSliderStyle
import paige.navic.domain.models.settings.NowPlayingTechnicalInfoStyle
import paige.navic.domain.models.settings.OfflineMode
import paige.navic.domain.models.settings.QueueSwipeAction
import paige.navic.domain.models.settings.ReplayGainMode
import paige.navic.domain.models.settings.SongSwipeAction
import paige.navic.domain.models.settings.StreamingQuality
import paige.navic.domain.models.settings.Theme
import paige.navic.domain.models.settings.ThemeMode
import paige.navic.domain.models.settings.ToolbarPosition
import paige.navic.domain.models.DefaultMaxConcurrentDownloads
import com.russhwolf.settings.Settings as KmpSettings
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class PreferenceManager(
	settings: KmpSettings
) : BasePreferenceManager(settings) {
	var font by preference(FontOption.GoogleSans)
	var fontPath by preference("")
	var animationStyle by preference(AnimationStyle.Expressive)
	var nowPlayingBackgroundStyle by preference(NowPlayingBackgroundStyle.Dynamic)
	var nowPlayingBackgroundBlurDp by preference(DefaultNowPlayingBackgroundBlurDp)
	var nowPlayingBackgroundDimPercent by preference(DefaultNowPlayingBackgroundDimPercent)
	var nowPlayingBackgroundBottomGradient by preference(false)
	var swipeToSkip by preference(true)
	var tapArtworkForLyrics by preference(false)
	var gridSize by preference(GridSize.TwoByTwo)
	var coverArtShape by preference(CoverArtShape.Soft)
	var coverArtQuality by preference(CoverArtQuality.High)
	var musicBrainzArtworkFallbackEnabled by preference(false)
	var musicBrainzArtworkCacheJson by preference("")
	var artGridItemSize by preference(150f)
	var marqueeSpeed by preference(MarqueeSpeed.Slow)
	var alphabeticalScroll by preference(false)
	var quickPicksEnabled by preference(true)
	var quickPicksLimit by preference(QuickPicksDefaultSize)
	var quickPicksMinDurationSeconds by preference(0)
	var lyricsAutoscroll by preference(true)
	var lyricsBeatByBeat by preference(true)
	var lyricsKeepAlive by preference(true)
	var lyricsBlur by preference(false)
	var lyricsBrightInactive by preference(false)
	var lyricsAccentBackground by preference(false)
	var showLyricsArtwork by preference(false)
	var lyricsAnimateSize by preference(true)
	var lyricsFontSize by preference(LyricsFontSize.Medium)
	var lyricsAlignment by preference(LyricsAlignment.Auto)
	var lyricsJumpOnTap by preference(true)
	var enableScrobbling by preference(true)
	var pauseListeningHistory by preference(false)
	var scrobblePercentage by preference(.5f)
	var minDurationToScrobble by preference(30f)
	var replayGainMode by preference(ReplayGainMode.Off)
	var gaplessPlayback by preference(true)
	var audioOffload by preference(false)
	var streamingQualityWifi by preference(StreamingQuality.Lossless)
	var streamingQualityCellular by preference(StreamingQuality.Lossless)
	var isAdvancedTranscodingActive by preference(false)
	var customMaxBitrateWifi by preference(0)
	var customMaxBitrateCellular by preference(0)
	var nowPlayingToolbarPosition by preference(ToolbarPosition.Bottom)
	var showNowPlayingArtwork by preference(true)
	var nowPlayingArtworkSize by preference(NowPlayingArtworkSize.Biggest)
	var nowPlayingArtworkTapAction by preference(NowPlayingArtworkTapAction.Disabled)
	var nowPlayingArtworkSwipeToSkip by preference(true)
	var nowPlayingRotatingArtwork by preference(false)
	var shrinkNowPlayingArtworkOnPause by preference(true)
	var nowPlayingInfoStyle by preference(NowPlayingInfoStyle.Essential)
	var showNowPlayingInfoIcons by preference(false)
	var nowPlayingSongInfo by preference(true)
	var nowPlayingTechnicalInfoStyle by preference(NowPlayingTechnicalInfoStyle.Compact)
	var showNowPlayingUpNext by preference(false)
	var showNowPlayingUpNextArtwork by preference(true)
	var nowPlayingUpNextCount by preference(2)
	var showNowPlayingSeekButtons by preference(false)
	var showNowPlayingRemainingTime by preference(false)
	var swapNowPlayingControlsAndTimeline by preference(false)
	var spaceNowPlayingPlaybackControlsEvenly by preference(false)
	var openQueueOnNowPlayingControlsSwipeUp by preference(false)
	var openQueueOnNowPlayingControlsTap by preference(false)
	var showNowPlayingShuffleControl by preference(true)
	var showNowPlayingRepeatControl by preference(true)
	var showNowPlayingIndicator by preference(true)
	var showPlaylistIndicator by preference(false)
	var nowPlayingSliderStyle by preference(NowPlayingSliderStyle.Squiggly)
	var nowPlayingProgressWidth by preference(NowPlayingProgressWidth.Biggest)
	var showNowPlayingLyricsAction by preference(true)
	var showNowPlayingQueueAction by preference(true)
	var showNowPlayingMusicVideoAction by preference(true)
	var showNowPlayingPlaybackSpeedAction by preference(true)
	var showNowPlayingEqualizerAction by preference(true)
	var showNowPlayingSleepTimerAction by preference(true)
	var showNowPlayingStartRadioAction by preference(true)
	var showNowPlayingDiscoverQueueAction by preference(true)
	var showNowPlayingDownloadAction by preference(true)
	var showNowPlayingAddToPlaylistAction by preference(true)
	var showNowPlayingMoreAction by preference(true)
	var customHeaders by preference("")
	var reverseProxyBasicAuthEnabled by preference(false)
	var reverseProxyBasicAuthUsername by preference("")
	var reverseProxyBasicAuthPassword by preference("")
	var mediaNotificationFirstAction by preference(MediaNotificationAction.Disabled)
	var mediaNotificationSecondAction by preference(MediaNotificationAction.Disabled)
	var lidaClipsEnabled by preference(false)
	var lidaClipsBaseUrl by preference("")
	var lidaClipsApiKey by preference("")
	var lidaClipsPictureInPicture by preference(false)
	var lidaClipsLandscapeVideoMode by preference(false)
	var lidaClipsBackgroundVideoMode by preference(LidaClipsBackgroundVideoMode.Blurred)
	var lidaClipsLyricsVideoBackground by preference(false)
	var lidaClipsMusicBrainzInfoVideoBackground by preference(false)
	var lidaClipsVideoFitMode by preference(LidaClipsVideoFitMode.Fit)
	var lidaClipsVideoCacheSizeMb by preference(DefaultLidaClipsVideoCacheSizeMb)
	var lidaClipsPauseMusicPlayback by preference(true)
	var lidaClipsRememberPlaybackPosition by preference(true)
	var lidaClipsKeepScreenOn by preference(true)
	var lidaClipsLastClipId by preference("")
	var lidaClipsLastPositionMs by preference(0L)
	var aurralEnabled by preference(false)
	var aurralBaseUrl by preference("")
	var aurralUsername by preference("")
	var aurralPassword by preference("")
	var respectAudioFocus by preference(true)
	var skipSilence by preference(false)
	var skipMediaOnError by preference(false)
	var resumePlaybackOnAudioDeviceConnect by preference(false)
	var pausePlaybackOnVolumeZero by preference(false)
	var audioFadeDurationMs by preference(0)
	var bassBoostEnabled by preference(false)
	var bassBoostStrength by preference(500)
	var audioReverbPreset by preference(AudioReverbPreset.Off)
	var replayGainLoudnessBoost by preference(false)
	var pauseBetweenSongsSeconds by preference(0)
	var medleyModeSeconds by preference(0)
	var playbackVolumePercent by preference(100)
	var autoFillQueue by preference(false)
	var autoFillQueueTargetSize by preference(25)
	var autoFillQueueSource by preference(AutoFillQueueSource.RecentGenres)
	var queueShuffleLimit by preference(0)
	var shakeToSkip by preference(false)
	var volumeKeysSkipTracks by preference(false)
	var smartRewindSeconds by preference(1)
	var songSwipeActionsEnabled by preference(true)
	var songSwipeStartToEndAction by preference(SongSwipeAction.AddToQueue)
	var songSwipeEndToStartAction by preference(SongSwipeAction.PlayNext)
	var queueSwipeActionsEnabled by preference(true)
	var queueSwipeStartToEndAction by preference(QueueSwipeAction.RemoveFromQueue)
	var queueSwipeEndToStartAction by preference(QueueSwipeAction.RemoveFromQueue)
	var persistentQueue by preference(true)
	var resumePlaybackOnStartup by preference(false)
	var checkForUpdates by preference(true)
	var pauseSearchHistory by preference(false)
	var searchHistoryEntries by preference("")
	var autoDownloadStarredSongs by preference(false)
	var autoDownloadStarredAlbums by preference(false)
	var maxConcurrentDownloads by preference(DefaultMaxConcurrentDownloads)

	// navigation bar settings
	var bottomBarCollapseMode by preference(BottomBarCollapseMode.OnScroll)
	var bottomBarVisibilityMode by preference(BottomBarVisibilityMode.AllScreens)
	var navigationBarStyle by preference(NavigationBarStyle.Normal)
	var navigationBarLabelVisibility by preference(
        NavigationBarLabelVisibility.Always
    )
	var miniPlayerStyle by preference(MiniPlayerStyle.Detached)
	var miniPlayerProgressStyle by preference(MiniPlayerProgressStyle.Seekable)
	var showMiniPlayerQueueAction by preference(false)

	/**
	 * If we have informed the user (on Android) about
	 * Google locking down sideloading.
	 */
	var showedSideloadingWarning by preference(false)

	// theme related settings
	var theme by preference(Theme.Dynamic)
	var themeMode by preference(ThemeMode.System)
	var accentColourH by preference(0f)
	var accentColourS by preference(0f)
	var accentColourV by preference(1f)

	// sync related settings
	var lastFullSyncTime by preference(0L)

	fun customHeadersMap(): Map<String, String> = buildMap {
		for (line in customHeaders.lines()) {
			val parts = line.split(":", limit = 2)
			if (parts.size < 2) continue

			val rawKey = parts[0]
			val rawValue = parts[1]

			val key = rawKey.trim()
			val value = rawValue.trim()
			if (key.isNotEmpty() && value.isNotEmpty()) put(key, value)
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	fun serverRequestHeadersMap(): Map<String, String> = buildMap {
		putAll(customHeadersMap())

		if (
			reverseProxyBasicAuthEnabled &&
			reverseProxyBasicAuthUsername.isNotEmpty() &&
			reverseProxyBasicAuthPassword.isNotEmpty()
		) {
			val credentials = "${reverseProxyBasicAuthUsername}:${reverseProxyBasicAuthPassword}"
			keys.filter { it.equals("Authorization", ignoreCase = true) }.forEach { remove(it) }
			put("Authorization", "Basic ${Base64.encode(credentials.encodeToByteArray())}")
		}
	}

	fun lidaClipsRequestHeadersMap(): Map<String, String> =
		paige.navic.domain.repositories.lidaClipsRequestHeaders(lidaClipsApiKey)

	fun aurralRequestHeadersMap(): Map<String, String> =
		paige.navic.domain.repositories.aurralBasicAuthHeaders(aurralUsername, aurralPassword)

	fun clearMusicBrainzArtworkCache() {
		musicBrainzArtworkCacheJson = ""
	}

	var offlineMode by preference(OfflineMode.Auto)
}
