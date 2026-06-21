# Large File Refactor Goal

Date: 2026-06-19

## Evidence

Measured on `master` after fetching all remotes.

- Production source files measured: 683
- Production median file length: 58 lines
- Production files >= 1000 lines: 12
- Production files >= 800 lines: 22
- Test/tool files measured: 217
- Test/tool files >= 1000 lines: 9

## Goal

Reduce maintenance risk in the largest active Navic files by splitting files that mix unrelated responsibilities, especially files involved in reader page-turn behavior, playback behavior, and external metadata/cache pipelines.

The goal is not to rewrite behavior. Each refactor should preserve current behavior and add or keep focused tests around the extracted contracts.

## Highest Priority Targets

### Reader Runtime JS

Files:

- `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js` - 1718 lines
- `composeApp/src/androidMain/assets/reader/navic-reader.js` - 1480 lines
- `composeApp/src/androidMain/assets/reader/navic-reader-page-turns.js` - 1108 lines
- `composeApp/src/androidMain/assets/reader/navic-reader-content-interactions.js` - 1078 lines
- `composeApp/src/androidMain/assets/reader/navic-reader-pagination.js` - 1010 lines

Issue:

Reader page-turn, pagination, surface texture, drag-preview, and coordinate math are spread across several large files. The repeated texture direction regressions suggest there are still duplicated or weakly defined direction/axis/offset paths.

Refactor direction:

- Centralize drag axis, direction, RTL, vertical-flow, and offset normalization.
- Keep paper texture movement, shadow texture movement, and page movement using the same normalized contract.
- Separate page-turn preview, committed navigation, pagination model, and UI surface rendering concerns.
- Add regression tests for horizontal LTR, horizontal RTL, and vertical page turns.

### Android Media Player ViewModel

File:

- `composeApp/src/androidMain/kotlin/paige/navic/shared/AndroidMediaPlayerViewModel.android.kt` - 1384 lines

Issue:

The Android music player view model owns Media3 controller setup, service interaction, queue mutation, autoplay/autofill, artwork prefetch, playback origin tracking, audio effects, noisy/device handling, and notification behavior.

Refactor direction:

- Extract Media3 controller/session bridge.
- Extract queue/autofill mutation logic.
- Extract audio-effects setup and preference application.
- Extract playback-origin checkpointing.
- Extract artwork/metadata prefetch decisions.
- Preserve existing playback behavior and logs while making future pause/crash investigations easier.

### Aurral and MusicBrainz Repositories

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/AurralRepository.kt` - 1411 lines
- `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/MusicBrainzArtworkRepository.kt` - 1146 lines

Issue:

Repository files mix API calls, cache paths, DTO mapping, cache selection policy, lookup policy, and UI-facing metadata helpers. This makes cover/cache debugging harder than needed.

Refactor direction:

- Split API client behavior from cache storage behavior.
- Move ranking/selection rules into policy helpers with tests.
- Keep cache keys and cache invalidation explicit and testable.
- Preserve unified artwork/cache logging.

## Secondary Targets

### Settings Search

File:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/SettingsSearchResults.kt` - 1835 lines

Issue:

This is the largest production Kotlin file. Most of the size appears to come from one searchable settings registry.

Refactor direction:

- Split searchable rows by settings section: appearance, playback, ebooks, storage, integrations, developer.
- Keep the search renderer small.
- Avoid changing settings behavior while moving row definitions.

### Aurral Hub UI

File:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubScreen.kt` - 1318 lines

Issue:

The screen owns artist search, discovery rows, flow creation, queue rows, messages, dialogs, and summary cards.

Refactor direction:

- Keep route/state orchestration in the screen file.
- Move search, discovery, flows, queue, and dialogs into focused component files.

### Bindery Book and Audiobook UI

Files:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt` - 1088 lines
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookScreen.kt` - 918 lines
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyAudiobookPlayerScreen.kt` - 907 lines

Issue:

These fork-specific files are growing around audiobook versions, Whispersync UI, book resources, and player controls.

Refactor direction:

- Split display policy from route/UI rendering.
- Move Whispersync sheets and launch selection into focused components.
- Move audiobook player artwork, progress, transport, speed, and chapter sheets into component files.
- Keep Bindery API model handling out of UI components where possible.

## Lower Priority

Large tests and tooling are worth splitting only after production code stabilizes:

- `tools/reader-harness/src/run-reader-harness.mjs` - 3723 lines
- `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/AurralRepositoryTest.kt` - 1947 lines
- Reader host/runtime tests over 1000 lines

These should be split when touching the related production area, not as standalone churn.

## Acceptance Criteria

- No production file should remain above 1200 lines unless it is intentionally data-like and documented.
- Reader direction and texture movement should have one shared normalization path.
- Playback ViewModel responsibilities should be separated enough that playback crash/pause logs map to a specific subsystem.
- Repository cache and selection policies should be individually testable.
- Refactors should preserve behavior and should not create release artifacts by themselves.
