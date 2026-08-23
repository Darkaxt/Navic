# Windows Desktop Music Client Design

**Date:** 2026-08-23

**Status:** Approved deferred blueprint

**Baseline:** `v1.0.11-iota62`

**Implementation prerequisite:** Begin only after the planned audiobook playback
rewrite is integrated into public `master`, its Android behavior is validated, and
the Windows plan has been refreshed against the resulting playback contracts.

**Platform scope:** Windows 11 x64. Android behavior and releases must remain
unchanged. Windows ARM64, macOS, Linux, and iOS are not included.

## Goal

Produce a self-contained Navic Windows music client that preserves Navic's
presentation and music features without embedding Chromium and without consuming
multiple gigabytes of memory merely to play music.

The Windows client is Navic on a new Compose Multiplatform desktop target. It is
not a Feishin skin, an Android emulator, an Electron wrapper, or a WPF rewrite.

## Product Decision

Use Compose Multiplatform Desktop on JVM 21 and package a per-user Windows EXE
installer with its required runtime. Reuse `commonMain` screens, navigation,
repositories, policies, models, networking, Room entities, and settings wherever
their contracts remain platform-neutral.

Use one supervised, bundled, audio-only `mpv` child process as the Windows music
decoder and output engine. Control it through JSON IPC over a unique Windows named
pipe. Start it with `--no-config`, `--terminal=no`, and video disabled so user mpv
configuration cannot alter Navic behavior and no unused video window or renderer
is created.

This design deliberately does not introduce a general playback plugin system.
Navic has one Windows playback adapter and one selected engine.

## Resource Contract

Memory is a release requirement, not an optimization to revisit after feature
parity.

All measurements cover the complete process tree: the Navic JVM process, the mpv
child process, and any Navic-owned helper. Windows Task Manager background usage
is approximated with the sum of `WorkingSet64`; `PrivateMemorySize64` is recorded
alongside it for diagnosis. A JVM heap limit alone is not acceptance evidence.

### Required limits

- Logged-in idle after a two-minute settling period: at most 250 MiB working set.
- Thirty minutes of normal playback while navigating albums, artists, playlists,
  lyrics, queue, and Now Playing: at most 400 MiB working set.
- Eight hours of continuous mixed playback: never above 512 MiB working set.
- At the end of the eight-hour run, working set must be no more than 64 MiB above
  the post-warmup baseline after returning to Now Playing and allowing a two-minute
  settling period.
- Exactly one mpv child may exist for a running Navic instance.
- Closing Navic must terminate its mpv child and release the named pipe.

The Coil decoded-artwork memory cache is explicitly bounded to 64 MiB. Large
artwork collections remain disk-backed; queues and grids must not retain decoded
bitmaps for off-screen entries. Diagnostic captures must distinguish JVM heap,
native Skia allocations, decoded artwork, and mpv memory before changing limits.

Any stage that exceeds its applicable limit stops. The implementation must fix
the regression or reduce that stage's scope before progressing. Raising the
limits requires a new approved specification revision.

If the Stage 0 implementation still cannot satisfy the limits after measured
JVM, Skia, artwork-cache, and mpv corrections, stop the Compose Desktop port and
record the evidence. A WPF or WinUI replacement would be a separate product
decision and specification, not an unreviewed pivot inside this plan.

## Scope

### Critical music experience

The first stable Windows release includes:

- Navidrome/Subsonic login, logout, server switching, custom headers, and Basic Authentication
  across API, artwork, stream, download, and probe requests.
- Home, library, search, albums, artists, playlists, genres, Most Played, radio,
  and existing music discovery routes.
- Zero-entry playlist filtering and the existing playlist-generation behavior.
- Aurral artist, discovery, monitoring, recommendation, and request surfaces that
  are available from the music application.
- Single-song and Play All entry points, queue and Up Next, shuffle, repeat,
  previous, next, seek, volume, and queue persistence.
- Now Playing, mini-player, artwork, vinyl presentation, synchronized lyrics,
  technical information, and existing music appearance settings.
- Stream authentication, replay gain, gapless transitions where the source allows
  them, pause-between-songs behavior, and supported playback-speed controls.
- Last.fm integration and scrobbling.
- Downloads, cache validation, offline mode, and cached-song queue fallback.
- Keyboard commands, global media keys, and Windows System Media Transport
  Controls for play/pause, previous, next, timeline, title, artist, and artwork.
- Actionable connection-loss and playback-error presentation.
- Local logs with credential redaction and a user-invoked diagnostics export.

### Deferred until the shared rewrite is ready

These features do not block the first Windows music release:

- Audiobook playback and audiobook mini-player.
- Bindery audiobook discovery, playback, and Whispersync.
- Ebook library, reader, read-aloud, annotations, and ebook Whispersync.
- LidaClips playback, video cache, and offline video.
- Shake-to-skip, Android widgets, Android notifications, Android Auto, and Android
  audio-effect panels.
- Windows ARM64, Microsoft Store publication, MSI packaging, shell extensions,
  Discord Rich Presence, and system-wide equalizer controls.

Deferred routes must be hidden or unavailable on Windows rather than displayed as
controls that fail at runtime. Shared Android behavior remains visible and
unchanged.

## Architecture

### Build and application boundary

Add `jvm("desktop")` to `composeApp` and a `desktopMain` source set for Windows
actual implementations. Add a small `desktopApp` launcher module, analogous to
`androidApp`, that owns window lifecycle, packaging, installer metadata, process
single-instancing, and Windows startup wiring.

The distribution is produced through Compose Desktop `jpackage` support as a
self-contained per-user EXE installer. The target machine does not require a
separate JDK. A stable public release requires Authenticode signing; the Android
release PKCS#12 is not assumed to be a Windows code-signing certificate.

### Playback boundary

The post-audiobook-rewrite shared playback contract is authoritative. Stage 0
must map it before adding desktop code. If the rewrite still leaves Android
Media3 types exposed to common music UI, extract only the smallest platform-
neutral music playback contract required by existing callers. Do not copy
`AndroidMediaPlayerViewModel` into `desktopMain` and rename it.

`DesktopMpvProcess` owns process start, exit observation, named-pipe connection,
and shutdown. `DesktopMpvClient` owns JSON commands and property events.
`DesktopMediaPlayerViewModel` maps the selected shared playback contract to mpv
without reimplementing queue policies already present in `commonMain`.

Authentication headers are delivered through IPC before a URL is loaded. Secrets
must never appear in command-line arguments, process listings, diagnostics,
exceptions, or logs. Navic uses one mpv process for the session and does not spawn
a process per song.

Playback progress is event-driven. Fixed-delay polling and timeout-based
cancellation are not introduced. Process exit, IPC disconnect, connectivity,
download state, and player property changes are explicit events with deterministic
recovery paths.

### Data and networking

Desktop uses Ktor's JVM client and the existing request/authentication pipeline.
The same header contract applies independently to login, API calls, cover art,
streaming, downloads, Aurral, and integration traffic.

Room databases and DataStore use per-user files below `%LOCALAPPDATA%\Navic`.
Downloaded music and disk caches use separately named subdirectories. Writes use
temporary files followed by atomic replacement where the filesystem supports it.
Credentials use Windows Credential Manager or DPAPI-protected storage, never
plaintext preferences.

### Presentation

Reuse Navic Compose screens. Desktop-specific work adapts window-level behavior,
width classes, pointer affordances, keyboard focus, scroll behavior, context
menus, and hover states without creating a second visual system.

The first window opens at a stable desktop size and remembers bounds while
remaining usable at the minimum supported size. Existing wide Now Playing and
tablet layouts are starting points, not proof of desktop readiness. No mobile
bottom-sheet assumption may trap essential controls in an undersized panel.

### Windows integration

The launcher owns single-instance activation and forwards subsequent launches to
the existing window. The playback layer owns Windows media transport state. The
application layer owns taskbar, tray, protocol activation, installer, and update
behavior.

Closing the window follows one explicit preference: exit completely or continue
playing in the tray. The default is exit completely until tray behavior is
implemented and validated.

## Playback and Failure Rules

1. User pause intent always wins over automatic recovery.
2. Queue IDs and ordering remain owned by shared Navic policies, not mpv's
   internal playlist.
3. Navic sends only the current source and bounded next-source prefetch state to
   mpv; mpv is not the persistent queue database.
4. Loss of connectivity shows `Connection lost - Switching to Offline mode` and
   applies the existing cached-song traversal without removing queue entries.
5. An unavailable uncached song cannot leave playback waiting indefinitely on a
   download that cannot progress.
6. An mpv crash is reported, the child is cleaned up, and one recovery attempt may
   recreate the engine while preserving user intent and queue state. Repeated
   failure remains visible and stopped; no restart loop is allowed.
7. Corrupt local media is an item failure, not a connection outage.
8. Authentication failure is distinct from server unavailability and offline
   state.
9. Artwork, lyrics, and enrichment failures cannot terminate otherwise valid
   audio playback.
10. No recovery path depends on a sleep, arbitrary timeout, or fixed retry loop.

## Staged Delivery Model

Each stage ends with a specification comparison. Every requirement is marked
`Satisfied`, `Blocker`, `Deferred`, or `Not reached`. A blocker is fixed before
advancing. A non-blocking missing feature moves to the explicit deferred ledger
with its target stage and evidence; it is not silently forgotten.

### Stage 0: Post-rewrite feasibility gate

Refresh the platform map after the audiobook rewrite, add the smallest desktop
target and launcher, prove authenticated playback of one remote and one local
song through the supervised mpv process, and produce an unsigned local EXE.

Blockers: reliable process/IPC lifecycle, no credential exposure, and the 250 MiB
idle and 400 MiB playback limits. No public artifact is produced.

### Stage 1: Core music client

Enable login, persisted settings, primary browse/search routes, artwork, single-
song playback, Play All, queue, Now Playing, lyrics, shuffle/repeat, and basic
error presentation. Validate mouse, keyboard, resizing, and large-library memory.

Blockers: all listed paths function against the real Navidrome/Traefik contract,
queue state remains correct, Android tests do not regress, and memory remains
within the normal-use limit. Internal local builds may be used for dogfooding.

### Stage 2: Playback reliability and offline parity

Add media keys and System Media Transport Controls, persistence and restoration,
Last.fm, replay gain, gapless behavior, downloads, offline mode, cached fallback,
connection-loss handling, and long-running diagnostics.

Blockers: the eight-hour 512 MiB gate, queue/recovery acceptance scenarios, no
orphan mpv process, authenticated stream/download parity, and clean recovery from
server loss and player-process exit. Publish only an explicitly marked Windows
alpha after this stage.

### Stage 3: Windows release quality

Finish desktop layout gaps, accessibility, installer upgrade behavior, credential
storage migration, diagnostics export, tray preference, update flow, Authenticode
signing, and clean install/update/uninstall verification.

Blockers: specification matrix has no unresolved critical music requirement,
Windows package verification passes, security review finds no exposed secret,
memory gates pass on the packaged build, and Android regression suites remain at
or better than baseline. Publish a Windows beta, then stable after live testing.

### Stage 4: Deferred verticals

Reassess audiobooks, Bindery, ebooks, reader, and LidaClips only after their shared
contracts are stable and the Windows music client is released. Each vertical gets
its own focused specification and staged plan; none may enlarge or destabilize
the music process without passing the same memory gates.

## Validation

### Automated

- Common tests continue to run for shared policies and repositories.
- Desktop JVM tests cover IPC serialization, event mapping, queue ownership,
  process lifecycle, credential redaction, path handling, and spec feature gates.
- Android host tests and Android assembly remain required regression gates.
- Packaging tests inspect the launcher, bundled runtime, bundled mpv provenance,
  version metadata, and installer upgrade identity.

### Runtime

- Real login through the configured proxy and custom-header path.
- Cover art, API, stream, download, lyrics, Last.fm, and Aurral requests.
- Play All from large artist, genre, album, and playlist collections.
- Shuffle/repeat traversal, queue edits, restart restoration, and media keys.
- Server outage with cached and uncached upcoming songs.
- Local-file corruption and mpv process termination.
- Idle, navigation, 30-minute playback, and eight-hour memory profiles.
- Fresh install, in-place update, uninstall, and residue inspection.

Build success alone is not runtime acceptance.

## Acceptance Criteria

- A Windows x64 user can install and launch Navic without installing Java,
  Chromium, Electron, Android, or an emulator.
- The critical music experience is available or explicitly classified by the
  staged specification matrix before stable release.
- All API and resource paths honor configured authentication without exposing
  secrets to process arguments or logs.
- Playback, queue, offline fallback, shuffle, repeat, restoration, and user pause
  intent match the shared Navic contracts.
- Total process-tree memory passes every Resource Contract limit on the packaged
  build.
- One Navic instance owns at most one mpv child and leaves no child after exit.
- Windows media controls and keyboard controls report and change the same playback
  state shown by Navic.
- Stable installer upgrade preserves settings, database, and downloads; uninstall
  does not delete user media without an explicit user choice.
- Existing Android tests and release behavior do not regress.
- Audiobook, ebook, Bindery, and LidaClips absence does not block the first Windows
  music release and is represented honestly in Windows navigation.

## Non-Goals

- Replacing or reskinning Feishin.
- Rewriting Navic in C#, XAML, React, or TypeScript.
- Embedding Chromium, WebView2, or an Android runtime for the application shell.
- Shipping every mobile feature in the first Windows release.
- Generalizing the playback engine for arbitrary third-party plugins.
- Raising memory limits to accommodate optional features without a specification
  revision and new measurements.

## References

- Kotlin Multiplatform native distributions:
  <https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html>
- Kotlin Multiplatform desktop window management:
  <https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html>
- mpv stable manual, including JSON IPC, Windows named pipes, HTTP headers,
  gapless audio, and WASAPI:
  <https://mpv.io/manual/stable/>
