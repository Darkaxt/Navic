# Windows Desktop Music Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a self-contained, music-first Navic Windows x64 client that
reuses the Compose Multiplatform application, excludes Chromium, and remains
below the approved 512 MiB total process-tree memory ceiling.

**Architecture:** Add a JVM desktop variant to `composeApp` and a small
`desktopApp` launcher and packaging module. Implement Windows platform actuals
and map the post-audiobook-rewrite playback contract to one supervised audio-only
mpv process controlled through named-pipe JSON IPC.

**Tech Stack:** Kotlin 2.4, Compose Multiplatform Desktop, JVM 21, Ktor JVM, Room
3, DataStore, Coil 3, Koin, mpv JSON IPC, Windows media APIs, Gradle, jpackage,
PowerShell verification, and GitHub Actions.

**Specification:**
`docs/superpowers/specs/2026-08-23-windows-desktop-music-client-design.md`

---

## Execution Rules

1. Do not begin Stage 0 until the audiobook rewrite is on public `master` and its
   Android validation is complete.
2. At the start of Stage 0, refresh this plan's file map against that commit. The
   specification remains authoritative if playback class names have changed.
3. Implement one stage at a time. Do not pull deferred features into an earlier
   stage merely because adjacent code is open.
4. Use TDD for policy, protocol, lifecycle, and state-mapping behavior. Runtime
   Windows behavior also requires packaged-app evidence.
5. Do not use sleeps or timeouts as cancellation or recovery mechanisms. Observe
   process exit, IPC state, network state, and playback properties directly.
6. At the end of every stage, create the specified validation report and compare
   implementation against the specification.
7. A `Blocker` stops progression. A missing non-blocker is recorded as `Deferred`
   with a target stage and evidence. It is never silently omitted.
8. Preserve Android behavior. The Android host suite and assembly are regression
   gates from Stage 1 onward.
9. Work in a dedicated Windows branch/worktree. Integrate only after the stage
   report is complete and the branch is synchronized with public `master`.

## Stage Review Template

Each report uses this table:

```markdown
| Specification requirement | Status | Evidence | Deferred target |
| --- | --- | --- | --- |
| Resource Contract: idle <= 250 MiB | Satisfied | capture path and values | - |
```

Allowed statuses are `Satisfied`, `Blocker`, `Deferred`, and `Not reached`.
`Not reached` is valid only when the requirement belongs to a later stage.

After filling the table:

- list all blockers and resolve them before advancing;
- list deferred features with the target stage and user impact;
- run `rg -n "T[B]D|T[O]DO|F[I]XME|place[h]older"` over new desktop production
  code and resolve every unfinished product-facing marker;
- record test commands, runtime scenarios, process-tree measurements, artifact
  hashes, and unresolved device-specific evidence;
- compare the actual file and component boundaries to the specification and
  document any approved deviation.

## Initial File Map

The audiobook rewrite may rename playback files. Stage 0 updates this map before
production edits instead of preserving stale names.

### Build and launcher

- Modify `settings.gradle.kts`: include `:desktopApp`.
- Modify `gradle/libs.versions.toml`: add the Kotlin JVM plugin and selected
  desktop-only dependencies.
- Modify `composeApp/build.gradle.kts`: add `jvm("desktop")`, `desktopMain`,
  `desktopTest`, and Room KSP for the desktop target.
- Create `desktopApp/build.gradle.kts`: Compose Desktop entry point and EXE
  distribution configuration.
- Create `desktopApp/src/main/kotlin/paige/navic/desktop/Main.kt`: single-instance
  window lifecycle and shared `App()` host.
- Create `desktopApp/src/main/resources/navic.ico`: Windows package icon.

### Desktop platform actuals

- Create `composeApp/src/desktopMain/kotlin/paige/navic/di/PlatformModule.desktop.kt`.
- Create `composeApp/src/desktopMain/kotlin/paige/navic/di/DatabaseModule.desktop.kt`.
- Create desktop actuals below `composeApp/src/desktopMain/kotlin/paige/navic/`
  for the common platform contracts found by the Stage 0 audit: context, logger,
  capabilities, storage, connectivity, permissions, sharing, notifications,
  database constructors, image conversion/normalization, updater, animated-icon
  fallback, power state, and screen-on behavior.
- Implement `AurralPreviewTracks` through the main music playback contract so it
  does not create a second mpv process.
- Hide the explicitly deferred reader, audiobook, Bindery, and LidaClips routes
  through platform capability policy rather than throwing from visible controls.

### Windows playback

- Create `composeApp/src/desktopMain/kotlin/paige/navic/shared/DesktopMpvProcess.kt`:
  one child process, named-pipe ownership, exit observation, and cleanup.
- Create `composeApp/src/desktopMain/kotlin/paige/navic/shared/DesktopMpvClient.kt`:
  JSON request/response correlation, property observation, and credential-safe
  command dispatch.
- Create `composeApp/src/desktopMain/kotlin/paige/navic/shared/DesktopMpvProtocol.kt`:
  serializable IPC command, response, event, and error types.
- Create `composeApp/src/desktopMain/kotlin/paige/navic/shared/DesktopMediaPlayerViewModel.desktop.kt`:
  mapping between shared Navic state and desktop playback events.
- Create `composeApp/src/desktopMain/kotlin/paige/navic/shared/DesktopMediaSession.kt`:
  global media keys and Windows System Media Transport Controls.
- Add the smallest common playback interface under
  `composeApp/src/commonMain/kotlin/paige/navic/shared/` only if the audiobook
  rewrite has not already produced a platform-neutral contract.

### Verification and release

- Create `composeApp/src/desktopTest/kotlin/paige/navic/shared/` tests for mpv
  protocol, process lifecycle, state mapping, queue ownership, and redaction.
- Create `composeApp/src/desktopTest/kotlin/paige/navic/desktop/WindowsFeatureGateTest.kt`:
  source and capability contracts for required and deferred routes.
- Create `scripts/measure-windows-process-tree.ps1`: repeatable working-set and
  private-byte capture for Navic and descendants.
- Create `scripts/verify-windows-package.ps1`: package metadata, bundled runtime,
  bundled mpv, signature, version, and hash verification.
- Modify `.github/workflows/build.yml`: Windows desktop tests and package build.
- Create one validation report per stage under `docs/superpowers/reports/`.

## Stage 0: Post-Rewrite Feasibility Gate

**Deliverable:** A local unsigned EXE that logs in and plays one authenticated
remote song and one downloaded local song through the final process/IPC shape.
No public release is created.

### Task 0.1: Refresh the baseline and playback map

- [ ] Fetch `fork/master` and `origin/master`, create an isolated worktree from
  current public `master`, and record the exact baseline commit.
- [ ] Read the audiobook rewrite specification, validation report, shared playback
  interfaces, Android implementations, and related tests.
- [ ] Map every Android type still exposed above the platform boundary. Prefer the
  rewrite's existing abstraction; extract only contracts required by the Windows
  music caller.
- [ ] Update this plan's Initial File Map in the same documentation commit if the
  rewrite changed ownership or paths.
- [ ] Run the full pre-change Android host suite and assembly and record the
  baseline result before adding a desktop target.

### Task 0.2: Add a compilable desktop application boundary

- [ ] Write source-contract tests asserting that the desktop target, desktop
  launcher, and required desktop actuals exist while deferred verticals are
  excluded from Windows navigation.
- [ ] Run the new tests and record RED against the post-rewrite baseline.
- [ ] Add the JVM desktop target and `desktopApp` launcher. Keep packaging in the
  launcher module and shared UI/domain code in `composeApp`.
- [ ] Configure the EXE as a per-user self-contained distribution with JVM 21,
  stable upgrade identity, icon, app metadata, and no console window.
- [ ] Add minimal functional actuals for login, networking, settings, database,
  storage, images, logging, and connectivity. Deferred feature actuals must be
  hidden by capability policy and must not be visible throwing stubs.
- [ ] Run desktop compilation and the source-contract tests and record GREEN.

The build configuration begins from this shape, adjusted to the post-rewrite
Gradle DSL if required:

```kotlin
kotlin {
	jvm("desktop")

	sourceSets {
		desktopMain.dependencies {
			implementation(compose.desktop.currentOs)
			implementation(libs.ktor.client.okhttp)
		}
	}
}
```

### Task 0.3: Implement and test the final mpv lifecycle

- [ ] Pin one reviewed Windows x64 mpv distribution by version and SHA-256, retain
  its license/provenance, and package only the required runtime files.
- [ ] Write failing desktop tests for one-child ownership, named-pipe identity,
  command correlation, property events, child exit, graceful shutdown, forced
  cleanup after unexpected exit, and credential redaction.
- [ ] Implement `DesktopMpvProtocol`, `DesktopMpvProcess`, and `DesktopMpvClient`
  with `ProcessBuilder`; do not invoke a shell or parse terminal output.
- [ ] Start mpv without credentials in its arguments. Send custom headers through
  IPC before `loadfile`, and ensure diagnostics render secret values as
  `[REDACTED]`.
- [ ] Run the desktop tests and record GREEN.
- [ ] Terminate mpv during playback and verify Navic reports the engine loss,
  preserves queue/user intent, performs no restart loop, and leaves no orphan.

### Task 0.4: Prove playback and memory feasibility

- [ ] Map the shared playback contract to the minimum desktop view model needed
  for play, pause, seek, stop, progress, duration, end-of-item, and error events.
- [ ] Verify one real authenticated remote stream through the configured proxy and
  one local downloaded file. Confirm secrets do not appear in process arguments
  or logs.
- [ ] Build and run the packaged EXE, not only the Gradle development launcher.
- [ ] Capture idle and 30-minute playback process-tree memory using
  `scripts/measure-windows-process-tree.ps1`.
- [ ] Stop the stage if idle exceeds 250 MiB or normal playback exceeds 400 MiB.
  Diagnose JVM heap, Skia, Coil, and mpv separately before changing code.
- [ ] If measured corrections cannot satisfy the limits, stop the port and record
  the result. Do not proceed to Stage 1 or pivot to WPF/WinUI without a new
  approved specification.
- [ ] Create
  `docs/superpowers/reports/windows-desktop-stage-0-validation.md`, complete the
  Stage Review Template, and commit Stage 0 only when it has no blocker.

## Stage 1: Core Music Client

**Deliverable:** A dogfoodable local Windows build for everyday online library
browsing and playback. It is not publicly released.

### Task 1.1: Persist account, settings, and library data

- [ ] Write desktop tests for `%LOCALAPPDATA%\Navic` path ownership, Room database
  creation/migration, atomic file replacement, DPAPI/Credential Manager secret
  storage, logout cleanup, and multi-server selection.
- [ ] Implement the desktop database constructors, storage manager, preferences,
  and protected credential adapter.
- [ ] Verify login, logout, restart, server switching, custom headers, and Basic Authentication
  against the real API, cover-art, and stream paths.
- [ ] Run desktop tests and inspect logs for unredacted credentials.

### Task 1.2: Enable primary music navigation

- [ ] Add Windows capability tests for Home, library, albums, artists, playlists,
  genres, Most Played, radio, search, and Aurral routes; assert deferred verticals
  are absent.
- [ ] Reuse shared screens and adapt only desktop window, pointer, focus, scrolling,
  context-menu, and width-class behavior.
- [ ] Verify minimum window size, restored bounds, maximized state, wide Now
  Playing, full-width detail panels, zero-entry playlist filtering, and keyboard
  traversal.
- [ ] Exercise a large library while capturing decoded-artwork cache and total
  process-tree memory.

### Task 1.3: Complete online playback and queue behavior

- [ ] Write desktop state-mapping tests for single-song play, Play All, shuffled
  start, previous/next, seek, volume, queue edits, repeat, end-of-item, user pause,
  artwork transition, lyrics, and player errors.
- [ ] Implement the desktop media player view model using shared queue and recovery
  policies. Keep mpv's internal playlist subordinate to Navic queue state.
- [ ] Connect mini-player, Now Playing, Queue, Up Next, synchronized lyrics,
  artwork/vinyl presentation, and Aurral preview actions.
- [ ] Verify authenticated playback from album, artist, playlist, genre, Most
  Played, radio, search, and Aurral entry points.
- [ ] Run all desktop tests plus the Android host suite and Android assembly.

### Task 1.4: Review Stage 1

- [ ] Repeat idle and 30-minute normal-use memory captures on the packaged build.
- [ ] Create
  `docs/superpowers/reports/windows-desktop-stage-1-validation.md` and map every
  Stage 0/1 specification item to evidence.
- [ ] Fix all blockers. Move only non-blocking missing features to Stage 2 or 3 in
  the report's deferred ledger.
- [ ] Commit the reviewed stage and produce an internal CI artifact for dogfooding.

## Stage 2: Reliability, Offline, and Media Integration

**Deliverable:** A Windows alpha that can replace Feishin for extended music
testing, including server outages and offline playback.

### Task 2.1: Complete durable playback behavior

- [ ] Write tests for queue/position restoration, replay gain, pause-between-songs,
  playback speed, gapless handoff, Last.fm scrobbling, duplicate event
  suppression, and process recreation after one unexpected exit.
- [ ] Implement the tested mappings using shared policy and mpv property events.
- [ ] Add global media keys and System Media Transport Controls; verify metadata,
  artwork, timeline, play/pause, previous, and next stay synchronized with Navic.
- [ ] Verify headset removal, output-device loss, suspend/resume, lock/unlock, and
  audio-device switching do not cancel the queue or override user pause intent.

### Task 2.2: Implement downloads and offline recovery

- [ ] Write desktop tests for authenticated download, temporary-file ownership,
  atomic completion, cache validation, deletion, offline-mode selection, complete
  upcoming traversal, and no indefinite wait on an offline queued download.
- [ ] Implement desktop download/storage integration using existing shared download
  entities and offline fallback policies.
- [ ] Verify connection loss displays `Connection lost - Switching to Offline
  mode`, preserves queue ordering, and selects the first usable cached song.
- [ ] Verify restored connectivity does not jump away from a cached song already
  playing and does not create duplicate downloads.

### Task 2.3: Run sustained and fault validation

- [ ] Run eight hours of mixed remote/local playback with library navigation,
  artwork, lyrics, queue changes, network loss/restoration, pause/resume, and media
  keys.
- [ ] Record the post-warmup baseline, maximum working set, final settled working
  set, private bytes, JVM heap, Coil cache, native Skia allocation evidence, and
  mpv working set.
- [ ] Fail the stage if the process tree exceeds 512 MiB, final settled memory is
  more than 64 MiB above baseline, more than one mpv child exists, or an orphan
  remains after exit.
- [ ] Run all desktop tests, Android host tests, Android assembly, and packaged EXE
  verification.

### Task 2.4: Review and publish the alpha

- [ ] Create
  `docs/superpowers/reports/windows-desktop-stage-2-validation.md` and complete the
  specification matrix.
- [ ] Fix all playback, authentication, offline, process, and memory blockers.
  Defer presentation polish only when its control remains functional and usable.
- [ ] Publish an explicitly marked Windows alpha after package hash, provenance,
  install, launch, playback, upgrade, and uninstall checks pass. Do not call it a
  stable release and do not alter Android's `{letter}##` version sequence.

## Stage 3: Windows Release Quality

**Deliverable:** A signed Windows beta followed by a stable EXE after live testing.

### Task 3.1: Finish desktop presentation and accessibility

- [ ] Review every critical music route at minimum, default, wide, and maximized
  window sizes with 100%, 125%, 150%, and 200% display scaling.
- [ ] Fix clipped text, mobile-only interactions, undersized panels, incoherent
  overlays, keyboard focus gaps, screen-reader labels, reduced-motion behavior,
  and high-contrast defects.
- [ ] Verify cover transitions, vinyl loading, queue changes, and resize operations
  do not cause blank intermediate frames or unbounded bitmap retention.

### Task 3.2: Complete Windows lifecycle and packaging

- [ ] Implement and test single-instance activation, clean exit, optional tray
  continuation, taskbar metadata, update flow, diagnostics export, and installer
  upgrade identity.
- [ ] Add Authenticode signing with a Windows code-signing certificate held in CI
  secrets. Do not reuse Android signing material unless certificate inspection
  proves it has the required Windows code-signing purpose.
- [ ] Extend `scripts/verify-windows-package.ps1` to verify signature, version,
  launcher, bundled runtime, bundled mpv hash/provenance, absence of Chromium,
  install path, upgrade behavior, and public artifact SHA-256.
- [ ] Verify uninstall preserves user databases, settings, and downloaded media by
  default and offers deletion only through an explicit user action.

### Task 3.3: Final regression and specification review

- [ ] Run all desktop and common tests, Android host tests, Android assembly,
  packaged runtime scenarios, security checks, and all four memory profiles.
- [ ] Create
  `docs/superpowers/reports/windows-desktop-stage-3-validation.md` and compare every
  specification requirement and acceptance criterion to fresh evidence.
- [ ] Resolve every critical `Blocker`. Keep deferred verticals clearly listed and
  absent from Windows navigation.
- [ ] Publish a signed Windows beta and complete live testing before promoting the
  same verified artifact lineage to stable.
- [ ] Download the public EXE to `D:\Temp`, verify HTTP availability, Authenticode
  signature, version, SHA-256, clean install/update/playback, and then remove the
  temporary download.
- [ ] Remove the implementation worktree only after public verification and leave
  the committed branch/tag history intact.

## Stage 4: Deferred Verticals

Do not begin this stage as part of the first Windows music release.

- [ ] Reassess audiobook playback against the completed shared rewrite and write a
  focused Windows audiobook specification.
- [ ] Treat Bindery/Whispersync, ebook reader/read-aloud, and LidaClips as separate
  specifications and plans with their own runtime and memory acceptance.
- [ ] Add a vertical only after it passes the existing Windows process-tree memory
  contract and does not regress music startup, playback, or Android behavior.

## Completion Boundary

This plan is complete when Stage 3 has produced a verified stable Windows music
EXE and each deferred vertical is either represented by its own approved
specification or remains explicitly deferred. Completion does not require
audiobook, Bindery, ebook, reader, or LidaClips parity.
