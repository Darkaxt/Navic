# Reader Listening Mode Settings Design

## Goal

Add a conditional `Listening mode` tab to the reader settings dialog for Whispersync-backed ebook sessions. The tab exposes audiobook and highlight controls that are currently hidden when the full audiobook player is not visible, while keeping normal ebook settings unchanged for books without a sidecar.

## Scope

- Add `Listening mode` as an additional tab in the existing reader settings dialog.
- Show the tab only when the active reader session has a loaded or loadable Whispersync sidecar.
- Store the new listening settings globally at the app level for the first implementation.
- Keep existing `Reading mode`, `General`, PDF image, and `Custom filter` tabs unchanged.
- Keep transport controls such as play/pause, seek bar, and +/-10 or +/-30 in the Whispersync player popup, not in the settings tab.
- Keep page-boundary behavior conservative: Whispersync playback must pause at the end of the visible page and must not auto-follow to the next EPUB section by default.

## Non-Goals

- No per-book listening overrides in the initial implementation.
- No new default-settings page for listening preferences.
- No replacement of the existing Whispersync headset control or player popup.
- No changes to Bindery sidecar generation or pairing logic.
- No synchronous sidecar, network, DB, or artwork work inside the settings UI.

## Visibility Rules

The reader settings dialog computes its tab list from current reader capabilities.

- EPUB/PDF without Whispersync: existing tab list only.
- EPUB with a selected ready Whispersync pair and sidecar route metadata: include `Listening mode`.
- EPUB while sidecar is still loading: include `Listening mode` if the route has Whispersync metadata, but controls that need runtime state may show a loading/disabled state.
- Sidecar load failure: keep `Listening mode` visible for diagnostics and retry-related state if the session was launched as Whispersync-capable.

The tab should be capability-driven, not file-format-driven. The reader should not show it merely because the book is an EPUB.

## Settings Model

Add a small app-level listening settings model, separate from per-book `ReaderSettings`.

Suggested model fields:

- `whispersyncListeningEnabled`: persisted default for whether Whispersync listening should start enabled when a paired reader session opens.
- `whispersyncPlaybackSpeed`: persisted readaloud/audiobook speed for Whispersync sessions.
- `whispersyncHighlightLeadMs`: signed millisecond lead/lag applied only to highlight progress, not to audio playback.
- `whispersyncHighlightColorArgb`: highlight color selected from presets or a color picker.
- `whispersyncHighlightLoading`: persistence behavior for played text.
- `whispersyncHighlightStyle`: visual rendering style for the active and played highlight.
- `whispersyncPageBoundaryBehavior`: initially defaults to `PauseAtVisiblePageEnd`.
- `whispersyncLongPressBehavior`: initially defaults to `SeekAudioToText` while listening mode is active.

These settings live in `PreferenceManager` as global APK preferences. They are not copied into book-level reader settings during the first pass.

## Listening Mode Controls

### Whispersync

Toggle whether the reader session should actively synchronize with the paired audiobook.

- Turning it on starts or prepares the readaloud backend for the current visible page.
- Turning it off stops/resets the readaloud backend and clears active Whispersync highlight state.
- The top-left headset remains the fast session control; this setting is the explicit settings-surface equivalent.

### Playback Speed

Reuse the existing readaloud/audiobook playback speed values and labels. Changing it dispatches the existing readaloud playback speed command and persists the chosen global speed.

### Highlight Lead

Controls how far ahead or behind the visual highlight runs relative to the audio position.

- Suggested initial range: `-1000ms` to `+2000ms`.
- Suggested default: `+750ms`.
- This must affect only highlight progress calculation. It must not seek, delay, or alter audio playback.

### Highlight Color

Controls the color used by Whispersync overlays.

- Provide a small preset row first: amber, yellow, green, blue, pink.
- A custom color path can be added later if it does not bloat the first pass.
- Opacity should be part of the renderer policy, not stored as a separate first-pass setting unless needed.

### Highlight Loading

Controls whether already-played text remains highlighted.

- `Current cue`: only the active cue/progress is highlighted.
- `Persistent played text`: previous cues on the visible page remain highlighted while the current cue continues filling.

This replaces the previous overloaded name `Highlight style` for persistence behavior.

### Highlight Style

Controls the visual shape/animation of the highlight.

- `Selection`: the current precise rectangular fill.
- `Marker`: translucent manual highlighter style with a slanted leading edge, defaulting to roughly 20 degrees.

The style is implemented in the SVG overlay path. It must not wrap or mutate EPUB DOM text nodes.

### Page Boundary Behavior

Controls what happens when audio reaches a cue outside the current visible page.

Initial implementation:

- `Pause at visible page end`: default and only enabled option for the first pass.

Future optional behaviors can be added later, but the first implementation must not reintroduce automatic cross-page following.

### Long Press Behavior

While Whispersync listening is active, long press should seek audio to the current sentence/cue instead of opening the normal text selection menu.

Initial implementation:

- `Seek audio to text`: default behavior while listening is active.
- Normal selection menu remains available when Whispersync listening is disabled.

## Highlight Rendering Contract

The highlight renderer must remain overlay-based.

- Do not use `surroundContents`, spans, wrappers, or DOM mutation for Whispersync progress.
- Draw all highlight styles through Foliate/content overlayer or the existing media-overlay SVG path.
- Target all resolved text ranges, including headings and title text, not only standard paragraph text.
- Support partial progress by character position within the resolved text range.
- Clamp active progress to the current cue and next cue boundary so the visual fill cannot spill into unrelated text.
- For `Persistent played text`, draw completed cue ranges separately from the active cue progress.

`Marker` style should draw per-line polygons rather than a single rectangle. The leading edge should be angled, but the filled area must still stay inside the line bounds.

## Data Flow

1. `ReaderScreen` determines Whispersync capability from route metadata and sidecar load state.
2. `KomikkuReaderSettingsDialog` receives a capability flag and adds `Listening mode` to its tab list.
3. The settings dialog reads global listening preferences from `PreferenceManager`.
4. Setting changes update preferences immediately and dispatch runtime commands only when the active session is loaded.
5. `ReaderController` and the WebView bridge receive a normalized runtime highlight config derived from preferences.
6. Audio playback position updates remain the only driver of highlight progress.
7. Visible range updates remain the only driver of page-scoped seek targets.

No composable should perform sidecar parsing, DB queries, network calls, or synchronous file access.

## Error Handling

- If sidecar loading fails, show `Listening mode` only when the reader route was Whispersync-capable, with runtime controls disabled and the existing status badge/message explaining the failure.
- If playback backend setup fails, settings remain editable but session controls show the error through existing reader Whispersync status.
- If a highlight style fails to render a range, log through existing Whispersync diagnostics and fall back to `Selection` for that range.
- Invalid persisted values are normalized to defaults.

## Performance Requirements

- Settings values are small preference reads and should be projected once per settings-dialog composition.
- Runtime highlight config should be remembered and only resent to the WebView when values change.
- Highlight rendering must stay incremental: update current cue progress without recreating all persistent ranges every playback tick.
- Persistent played ranges should be keyed by cue identity and visible page href/range.
- No artificial cancellation timeouts are introduced.

## Implementation Plan

1. Add the global listening settings fields and normalization helpers.
2. Add a `ReaderListeningSettings` projection for the reader runtime.
3. Add the conditional `Listening mode` tab to `ReaderSettingsDialog`.
4. Move existing Whispersync speed/control UI pieces into reusable settings rows where appropriate.
5. Send highlight config to the reader bridge when the session starts and when preferences change.
6. Extend the SVG overlay renderer with `Selection` and `Marker` styles.
7. Add persistent played-text rendering for visible-page cues.
8. Gate long-press behavior while listening is active.
9. Keep page-boundary behavior at `PauseAtVisiblePageEnd` and add tests proving it does not auto-follow.

## Test Plan

Unit and source tests:

- Listening tab is absent without Whispersync capability.
- Listening tab is present for Whispersync-capable reader routes.
- Listening settings are app-level and do not modify per-book `ReaderSettings`.
- Invalid preference values normalize to defaults.
- Playback speed setting dispatches the existing readaloud speed command.
- Highlight lead changes only progress projection, not audio position.
- Page boundary behavior remains `PauseAtVisiblePageEnd`.
- Long press routes to Whispersync seek while listening is active and to normal selection while inactive.
- Source guard prevents Whispersync highlight code from mutating EPUB DOM text.

Reader/WebView tests:

- `Selection` style paints partial progress without inserting text nodes or line breaks.
- `Marker` style paints a slanted leading edge and stays within line bounds.
- Headings/titles can be highlighted when the resolved range lands there.
- Persistent played text keeps completed visible-page cues highlighted while current cue advances.
- Highlight config changes update the renderer without reloading the EPUB.

Device validation:

- Open a Whispersync-capable ebook and confirm the `Listening mode` tab appears.
- Open a normal ebook and confirm the tab is absent.
- Change playback speed and confirm audio speed changes without opening the full audiobook player.
- Change highlight lead and confirm the fill advances earlier/later without seeking audio.
- Switch between `Selection` and `Marker` styles and confirm the overlay remains stable during playback.
- Confirm playback pauses at the visible page boundary and does not jump to another chapter.

## Acceptance Criteria

- Users can configure Whispersync playback/highlight behavior from the reader settings panel without opening the hidden audiobook player.
- The new tab appears only for Whispersync-capable reader sessions.
- All listening preferences are global APK-level settings in the first implementation.
- Whispersync playback does not auto-continue to another EPUB page or chapter by default.
- Highlight rendering supports color, lead, persistence, and visual style without DOM mutation.
- Existing non-Whispersync reader behavior is unchanged.
