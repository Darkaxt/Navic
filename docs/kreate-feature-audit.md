# Kreate Feature Transplant Audit

Reference checked: `knighthat/Kreate` at `228c5e8` (`main`, pushed 2026-05-26).

## Already Adapted

* Audio focus behavior: Navic now exposes `Respect audio focus` and passes the preference into Media3 `setAudioAttributes(..., handleAudioFocus)`.
* Video concept: Kreate's player exposes a configurable video action, but its implementation is YouTube iframe-specific. Navic's first LidaClips pass uses LidaClips lookup plus Media3 stream playback instead.
* Skip silence: Navic now exposes an Android playback toggle and applies it to Media3 when the playback service starts.
* Skip media on error: Navic now exposes an Android playback toggle and advances to the next queued media item when Media3 reports a playback error and a next item exists.
* Player action visibility: Navic now exposes Now Playing settings for Lyrics, Queue, and Music Video actions.

## Best Next Transplants

1. Persistent queue and optional resume on startup
   * Kreate stores queue state in a database and restores it on service startup.
   * This maps well to Navic's existing Room/cache architecture and would improve crash/restart behavior.
   * Settings should live in Settings -> Playback.

2. Audio-device resume
   * Kreate can resume playback when an audio device connects.
   * Useful for headphones/car sessions, but should default off to avoid surprising playback.
   * Setting should live in Settings -> Playback.

3. LidaClips Picture-in-Picture
   * Kreate has PiP support around its video surface.
   * Navic should adapt this specifically for the LidaClips video player, not for the audio-only now-playing screen.
   * Setting should live in Settings -> Data & Storage -> Music video clips or Settings -> Now Playing once video becomes a first-class player view.

4. Android system equalizer shortcut
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
2. Add persistent queue/resume because it is valuable but touches storage and service lifecycle.
3. Add LidaClips PiP after in-app video playback is tested on device.
4. Consider Android system equalizer access once the active audio-session path is cleanly exposed.
