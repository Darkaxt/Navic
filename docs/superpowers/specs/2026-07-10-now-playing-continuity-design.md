# Now Playing Continuity Design

Date: 2026-07-10
Branch: master

## Goal

Improve continuity in the expanded Now Playing view in two focused ways:

- Optionally keep the screen awake while music is actively playing, with a mode that limits the behavior to external power.
- Prevent the vinyl from briefly showing generated coverless artwork during a song transition when the next cover is already available in Coil's memory cache.

Both changes are scoped to the full Now Playing surface. They must not alter queue order, playback prefetching, download behavior, or background playback.

## Current Behavior

### Screen State

Navic already has a cross-platform `KeepScreenOn()` composable. Android sets `View.keepScreenOn`, while iOS controls `UIApplication.idleTimerDisabled`; both restore the platform value when the composable leaves composition. Lyrics, the reader, and LidaClips use this facility for their own features, but the normal expanded Now Playing view has no policy for it.

The requested policy is:

- `Off`
- `While playing and charging`
- `While playing`

The default must be `Off`. "Charging" means connected to external power, including the battery-full state, rather than only a positive battery charge rate.

### Artwork Transition

The automatic transition was reproduced on the physical tablet with shuffle enabled. The next song's cover was already visible in Up Next, but the destination vinyl showed generated coverless artwork for approximately 400 ms before the real cover appeared.

The relevant flow is:

1. Up Next resolves and displays a small `PlaybackSongCoverArt` thumbnail.
2. The shuffled transition can move the pager between distant canonical queue indexes, so the destination page is composed only when the transition begins.
3. `NowPlayingArtwork` creates a larger Coil request for that destination page.
4. The small and large requests share the normalized artwork identity, but Coil may reject the small bitmap as the final result for the larger requested size.
5. `CoverArt` currently renders `CoverArtFallback` for every loading state, exposing generated coverless vinyl until the larger request succeeds.

This is a rendering-state problem, not a queue or download failure. Up Next churn observed during the first songs came from the existing five-item playback prefetch window and stabilized once those songs were cached; this design does not change that behavior.

## Design Principles

- Scope screen retention through composition. Do not acquire a service wake lock or change background-playback policy.
- Drive state from playback, screen visibility, and external-power observations. Do not use fixed delays or cancellation timeouts.
- Keep settings representable as one valid mode rather than combining switches that can form contradictory states.
- Reuse an exact cached artwork identity only for the matching song and normalization mode.
- A cached thumbnail is an immediate placeholder, not necessarily the final full-size image.
- Preserve generated artwork for genuinely missing, unresolved, or failed covers.
- Keep existing Lyrics, Reader, and LidaClips screen-retention settings independent.

## Screen-On Policy

### Preference Model

Add a common settings enum following the existing Now Playing preference pattern:

```kotlin
enum class NowPlayingScreenOnMode {
    Off,
    WhilePlayingAndCharging,
    WhilePlaying
}
```

The enum should expose localized display names. Persist it in `PreferenceManager` with `Off` as the default.

Add one `SettingSelectionRow` to the Now Playing settings screen and a matching settings-search row. The user-facing values are exactly:

- Off
- While playing and charging
- While playing

### Pure Decision Policy

Keep the decision independent from Compose and platform APIs:

```kotlin
fun shouldKeepNowPlayingScreenOn(
    mode: NowPlayingScreenOnMode,
    hasActiveSong: Boolean,
    isPaused: Boolean,
    isExternalPowerConnected: Boolean
): Boolean
```

The policy returns true only when an active song exists and playback is not paused, plus:

- `Off`: always false.
- `WhilePlayingAndCharging`: true only while external power is connected.
- `WhilePlaying`: true regardless of external power.

`hasActiveSong` prevents an empty or restoring player state from keeping the display awake merely because `isPaused` has not settled yet.

### Platform Power Observation

Add a small common `expect` API that exposes external-power state to Compose. The platform implementations own observation and cleanup.

Android behavior:

- Read the sticky `ACTION_BATTERY_CHANGED` intent for initial state.
- Observe subsequent battery-state broadcasts while the composable is active.
- Treat AC, USB, wireless, and dock power as connected.
- Treat a full battery that remains plugged in as connected.
- Unregister the receiver on disposal.

iOS behavior:

- Enable device battery monitoring while observation is active.
- Read the initial `UIDevice` battery state.
- Observe battery-state change notifications.
- Treat charging and full as externally powered.
- Remove the notification observer on disposal. If this observer enabled battery monitoring, record the prior value and restore it when disposed; do not disable monitoring that was already enabled by another owner.

If power state is unavailable, `While playing and charging` behaves as false. `While playing` does not depend on power observation.

### Composition Ownership

`NowPlayingScreen` evaluates the pure policy from the current song, pause state, preference, and power state. It composes `KeepScreenOn()` only when the result is true.

Consequences are immediate and state-driven:

- Pausing playback disposes `KeepScreenOn()`.
- Unplugging power disposes it in the charging-only mode.
- Collapsing or leaving Now Playing disposes it because the screen leaves composition.
- Changing the preference to `Off` disposes it.
- Background playback and the mini player never acquire this screen flag.

Existing independent calls from Lyrics, Reader, or LidaClips remain unchanged and may still keep the screen awake according to their own settings.

## Cached Artwork Placeholder

### Request Contract

Extend `CoverArt` with a narrowly named opt-in for using its resolved memory-cache identity as a loading placeholder. Keep the default disabled so unrelated surfaces retain their current rendering behavior.

When enabled and a normalized cache key exists:

1. Set Coil's placeholder memory-cache key to the resolved artwork key.
2. Continue making the normal destination request at the destination constraints.
3. In the loading slot, render the memory-cache placeholder when Coil supplies it.
4. Keep `CoverArtFallback` underneath or use it when no matching cached bitmap exists.
5. Replace the cached placeholder with the successful destination image normally.

This follows Coil's thumbnail-to-detail placeholder model. It does not treat an undersized thumbnail as proof that the full request is complete.

### Now Playing Opt-In

Enable the cached loading placeholder for `NowPlayingArtwork`. Up Next and Now Playing both use `CoverArtNormalization.TrimWhitespace`; when their resolved source and normalized key match, the first Up Next item can seed that key before the automatic transition. If Now Playing selects a different fallback source, its different key prevents reuse.

The placeholder must be identity-safe:

- It must use the resolved artwork cache key, not the previous pager page or previous song.
- It must include the same normalization identity used by the destination request.
- It must not reuse a cached bitmap when source resolution selects a different key.
- It must not show the old song's cover while the new song is active.

If the key is absent from memory, the current generated loading artwork remains visible. If the final request fails, the existing generated error fallback and server-cover recovery callback remain in effect.

### Interaction With Crossfade And Vinyl

The cached bitmap should occupy the same clipped artwork bounds and vinyl presentation as the final image. A crossfade from the cached bitmap to the destination image is acceptable, but generated coverless artwork must not appear between two matching real images.

Vinyl grooves, spindle, fit mode, edge compression, rotation, and pause shrinking remain presentation layers around the artwork request and are not reset by the placeholder transition.

## Alternatives Rejected

### Two Screen-On Switches

A `keep screen on` switch plus an `only while charging` switch can express an irrelevant or contradictory second value while the first is disabled. A single enum is clearer to persist, search, test, and present.

### Activity-Level Or Service Wake Lock

An activity flag or playback-service wake lock would outlive the full Now Playing composition unless additional ownership rules were added. That is broader than requested and risks keeping the screen awake in the mini player or background.

### Full-Size Artwork Prewarming

Prewarming all five Up Next covers at expanded-player dimensions would increase decoding and memory pressure and duplicate the existing playback prefetch concept. The observed failure already has a matching cached bitmap; using it as a placeholder addresses the visible gap without broad speculative work.

### Retaining The Previous Cover

Keeping the previous song's cover until the destination request succeeds avoids a blank frame but displays incorrect song metadata. The placeholder must belong to the destination song.

## Error Handling

- Unknown external-power state fails closed for the charging-only mode.
- Power observation errors do not affect playback.
- Leaving composition always releases observers and screen flags.
- A missing cached artwork placeholder keeps the existing generated loading state.
- A failed destination artwork request keeps the existing generated error state and recovery behavior.
- No timeout is added to power observation, artwork loading, page transitions, or playback.

## Code Areas

Likely implementation areas:

- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/settings/NowPlayingScreenOnMode.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/models/NowPlayingScreenOnPolicy.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/domain/manager/PreferenceManager.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/NowPlayingScreen.kt`
- the corresponding Now Playing settings-search rows and string resources
- a common `expect` power-state API with Android and iOS `actual` implementations
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/CoverArt.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt`

Exact filenames for the platform power observer may follow the existing `KeepScreenOn` package convention.

## Tests And Verification

### Automated Tests

Add focused tests for:

- Every screen-on mode across active song, paused state, and powered state.
- An empty player never keeping the screen awake.
- Charging-only mode failing closed when platform state is unavailable.
- Android power mapping for AC, USB, wireless, dock, battery-only, and plugged/full states.
- iOS power mapping for charging, full, unplugged, and unknown states.
- Observer registration and disposal contracts using platform-appropriate source or host tests.
- `CoverArt` applying the placeholder memory-cache key only when opted in and a key exists.
- The loading renderer preferring a matching Coil placeholder while preserving generated fallback when none exists.
- `NowPlayingArtwork` opting into the cached placeholder with `TrimWhitespace` normalization.

Run the focused common and Android host tests, followed by the repository's normal Gradle verification appropriate to the touched common, Android, and iOS source sets.

### Physical Tablet Verification

Use the attached tablet for behavioral validation:

1. Set `Off`; verify normal display sleep remains possible during active playback.
2. Set `While playing and charging`; verify the display is retained while plugged in and released after unplugging or pausing.
3. Set `While playing`; verify the display is retained on battery and released after pausing.
4. In every enabled mode, collapse or leave the full Now Playing view and verify the new policy no longer holds the display awake.
5. Start shuffled playback with Up Next artwork visible and capture an automatic transition.
6. Inspect transition frames and verify the destination cover is present immediately when its vinyl appears, with no generated coverless frame.
7. Test a song whose cover is not cached and a song with genuinely missing artwork; both must retain valid fallback behavior.

## Success Criteria

- The new setting offers the three approved modes and defaults to `Off`.
- Screen retention applies only to active playback in the full Now Playing composition.
- Pause, unplug, navigation, and preference changes release the screen flag immediately when required.
- The first destination frame uses the matching cached cover when Up Next has already loaded it.
- Full-size artwork can continue resolving without exposing generated coverless vinyl between matching images.
- Missing and failed artwork still render Navic's generated fallback.
- Queue order, playback downloads, and the five-item prefetch window are unchanged.
- No wake lock or timeout is introduced.
