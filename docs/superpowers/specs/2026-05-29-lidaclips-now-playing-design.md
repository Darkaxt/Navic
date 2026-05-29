# LidaClips Now Playing Integration Design

## Context

The current LidaClips integration is technically usable, but it is too hidden for normal playback. Setup lives under Data & Storage, the video action is buried in song overflow sheets, and clip playback opens a separate screen instead of feeling like part of the standard Now Playing experience.

This design keeps LidaClips as an integration, but moves its primary interaction into the Now Playing player surface.

## Goals

- Move LidaClips settings under a new top-level Integrations settings category.
- Add a movie-style action beside the Lyrics action in Now Playing.
- Use detected clips as ambient Now Playing context by default.
- Let the user promote the clip into the artwork area without opening a separate video screen.
- Keep audio behavior predictable by avoiding doubled song and clip audio unless a later explicit setting changes that.
- Avoid running two video players at the same time.
- Start clip playback near the equivalent song progress instead of always starting videos from zero.

## Non-Goals

- Do not build a LidaClips browser or recent-clips page in this pass.
- Do not replace the normal music queue with a video queue.
- Do not add iOS video playback in this pass.
- Do not remove the existing song-sheet video action until the new Now Playing path is proven stable.

## Settings Structure

Add a top-level Settings category:

- Integrations
  - Music video clips

The existing LidaClips settings move from Settings -> Data & Storage -> Music video clips to Settings -> Integrations -> Music video clips.

The LidaClips settings keep the existing endpoint, API key, connection test, diagnostics, sync control, PiP, landscape mode, remembered position, keep-screen-on, and pause-music options where they still apply.

Add or revise presentation settings:

- Background video: Off / Blurred / Normal
- Auto-play background video: On by default
- Foreground video fit: Fit / Crop, default Fit

Background video modes always use crop sizing so the video fills the player background. This includes both blurred and normal background styles. Foreground artwork video defaults to fit sizing so the actual clip frame is fully visible.

## Now Playing Behavior

When Now Playing receives a stable Navidrome song id and LidaClips is enabled/configured, Navic checks for a matching clip using the existing lookup flow:

1. Try direct Navidrome song-id lookup.
2. Fall back to metadata lookup when the local song has a real title and non-synthetic artist.
3. Cache hit and miss results for the current URL/API-key fingerprint.

If no clip is known yet, Now Playing renders normally while lookup happens. The movie icon can show an unavailable/loading visual state, but it should not force a separate screen.

If a clip is detected and Auto-play background video is enabled:

- The clip starts as the Now Playing background.
- The clip seeks to the current song's normalized progress when it starts.
- The regular cover/artwork area stays visible.
- The background video is muted.
- The background video uses crop sizing.
- Blurred mode applies blur/dimming so player text and controls remain legible.
- Normal background mode remains a user option, but it still crops because it is background media.

If the user taps the movie icon:

- The background video unloads.
- The same clip becomes the foreground video in the artwork area.
- The foreground clip seeks to the current song's normalized progress at the moment it is promoted.
- The foreground video uses fit sizing by default.
- Standard music controls remain visible below the video.
- The user stays on the Now Playing screen.

If the user taps the movie icon again while foreground video is active:

- The foreground video unloads.
- The normal artwork returns.
- If background video is enabled and a clip is still available, the background video resumes.

## Audio Behavior

Background video is visual-only and muted. The Navidrome stream remains the audio source so playback does not double, drift, or fight with the current queue.

Foreground artwork video uses the existing LidaClips music-session policy:

- By default, pause Navic music while the foreground clip plays.
- Resume the same paused song when leaving foreground clip mode.
- If `Pause music while clips play` is disabled, allow music and clip audio together.
- Respect the existing Android audio-focus setting for clip playback.

This keeps background video lightweight and predictable while preserving the current foreground video behavior for users who want real clip audio.

## Clip Progress Mapping

LidaClips videos and Navidrome songs often have different durations, so Now Playing should use proportional progress instead of absolute timestamps when starting or promoting a clip.

When a video presentation starts:

1. Read the current song position and duration from the music player.
2. Compute `songProgress = songPositionMs / songDurationMs`.
3. Seek the clip to `clipDurationMs * songProgress`.

Use the LidaClips `durationSeconds` value when available. If the clip duration is not available before player preparation, use the Media3 duration once it is known, then seek before presenting foreground playback when possible. Clamp the computed clip position into the valid clip range. If the song duration or clip duration cannot be determined, start the clip at zero and keep the player stable.

When the user promotes a background clip into the artwork area, recompute the clip position from the current song progress at tap time. This keeps the foreground video aligned to the song's current percentage even if the background player was delayed, paused, or recreated.

This is a pragmatic approximation, not true audio/video sync. Navic should not attempt lyric/subtitle-based synchronization in this pass because matching lyrics and subtitles across the audio file and music video will be rare.

## Loading And Failure States

Clip lookup and stream loading must not block the normal player.

- Lookup pending: show normal artwork and an inactive/loading movie icon state.
- No clip found: tapping the icon shows a compact unavailable state with Refresh.
- Stream error: keep the user in Now Playing and show retry/error text near the video area.
- Refresh: bypass the short LidaClips lookup cache for that song.

If background video loading is slow, Now Playing should remain stable with the regular artwork until the video is ready. No blank background or layout jump should appear.

## Performance Rules

- Only one LidaClips video player instance should be active at a time.
- Switching from background to foreground must release the background player before creating or attaching the foreground player.
- Background playback should start only after clip lookup succeeds and should be easy to disable.
- Background video should be muted and visually dimmed/blurred to reduce distraction.
- The implementation should preserve the existing short-lived lookup cache and API-key fingerprinting.

## Testing

Focused tests should cover:

- LidaClips settings route moves to Integrations.
- Movie action availability follows enabled/configured/stable-song rules.
- Clip state machine transitions: none, lookup, background, foreground, error, retry.
- Background mode uses crop sizing.
- Foreground artwork mode uses fit sizing by default.
- Switching presentation modes does not create two active video players.
- Existing song-sheet Play music video action still works during the transition period.

Manual Android checks:

- Configure LidaClips from Settings -> Integrations -> Music video clips.
- Play a song with a known clip, for example `Alex Warren - Heaven Without You`.
- Confirm blurred cropped video appears behind Now Playing after detection.
- Tap the movie icon and confirm video moves into the artwork area with fit sizing.
- Tap again and confirm normal artwork/background behavior returns.
- Confirm no separate player screen opens for the main Now Playing interaction.
- Confirm stream failures show retryable in-place errors.
