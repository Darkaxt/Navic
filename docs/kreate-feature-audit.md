# Kreate Feature Transplant Audit

Reference checked: `knighthat/Kreate` at `228c5e8` (`main`, pushed 2026-05-26).

## Already Adapted

* Audio focus behavior: Navic now exposes `Respect audio focus` and passes the preference into Media3 `setAudioAttributes(..., handleAudioFocus)`.
* Video concept: Kreate's player exposes a configurable video action, but its implementation is YouTube iframe-specific. Navic's first LidaClips pass uses LidaClips lookup plus Media3 stream playback instead.

## Best Next Transplants

1. Persistent queue and optional resume on startup
   * Kreate stores queue state in a database and restores it on service startup.
   * This maps well to Navic's existing Room/cache architecture and would improve crash/restart behavior.
   * Settings should live in Settings -> Playback.

2. Skip media on error
   * Kreate has a playback setting to skip failed media instead of stalling playback.
   * This is low-risk for Navic because Media3 already reports player errors centrally.
   * Setting should live in Settings -> Playback.

3. Skip silence
   * Kreate toggles Media3/ExoPlayer silence skipping.
   * This is an Android-only playback setting and should sit near gapless/audio focus/offload.

4. Audio-device resume
   * Kreate can resume playback when an audio device connects.
   * Useful for headphones/car sessions, but should default off to avoid surprising playback.
   * Setting should live in Settings -> Playback.

5. LidaClips Picture-in-Picture
   * Kreate has PiP support around its video surface.
   * Navic should adapt this specifically for the LidaClips video player, not for the audio-only now-playing screen.
   * Setting should live in Settings -> Data & Storage -> Music video clips or Settings -> Now Playing once video becomes a first-class player view.

6. Player action visibility
   * Kreate lets users choose which now-playing action buttons are shown.
   * Navic already has a simpler now-playing design, so transplant this selectively: hide/show Lyrics, Queue, Play music video, and More.
   * Setting should live in Settings -> Now Playing.

7. Android system equalizer shortcut
   * Kreate opens Android's audio effect control panel for the active audio session.
   * This is useful and contained, but it needs careful handling because not all devices provide an equalizer.
   * Setting/action should live in Playback or the song/player menu.

## Higher-Risk Candidates

* Loudness normalization, bass boost, and reverb: Kreate uses Android audio effects and/or track loudness metadata. Navic already has ReplayGain support, so this needs design work to avoid conflicting gain paths.
* Pause on volume zero, volume buttons change song, and shake to skip: useful for some users, but device-event handling increases background behavior complexity.
* Queue auto-append and Discover-style queue generation: valuable, but product behavior needs more definition for a Navidrome library client.
* Visualizers, thumbnail animations, and extensive player layout variants: high UI churn and likely not worth transplanting until core playback/video behavior is stable.

## Recommended Order

1. Finish LidaClips smoke testing and add PiP only after in-app video playback is verified on device.
2. Add `Skip media on error` and `Skip silence` because they are small Android playback toggles.
3. Add persistent queue/resume because it is valuable but touches storage and service lifecycle.
4. Add selective now-playing action visibility once the LidaClips action proves useful.
