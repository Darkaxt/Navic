# Automatic Offline Playback Fallback Design

**Date:** 2026-07-22

**Status:** Approved direction

**Platform scope:** Android playback and Android release only. Common code may be
changed where the existing Kotlin Multiplatform preference, connectivity, or
download boundary requires it, but no iOS playback behavior or iOS release is
included.

## Goal

When Navic loses access to Navidrome, it must immediately enter the behavior of
Offline Mode, tell the user `Connection lost - Switching to Offline mode`, and
continue with verified downloaded songs whenever possible. The transition must
not rewrite the user's Offline Mode preference, reorder or clear the queue, or
start an unbounded download retry loop.

Manual changes to Offline Mode must also take effect at runtime. The settings UI
must no longer claim that an application restart is required.

## Approved Requirements

1. Make Offline Mode reactive by exposing `offlineMode` as an observable runtime
   state.
2. Remove `Requires application restart` from the setting and from the runtime
   behavior.
3. Add an automatic, non-persistent `Forced` activation when Navidrome becomes
   unreachable.
4. Feed the effective state through Navic's existing Offline Mode behavior.
5. Make playback immediately select verified cached songs and make downloads
   wait while offline.
6. Restore the user's current selected mode automatically when Navidrome
   responds again.
7. Never overwrite the user's persisted Offline Mode selection during automatic
   switching.

## Observed Failure

The tablet was playing a 407-item queue when Navidrome became unavailable after
a server-side power outage. Android still had validated Internet access, but
Media3 failed the active stream with
`ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT`. Navic then:

1. refreshed the remote stream URL once;
2. requested a persistent download for the same song;
3. left the player in recovery;
4. retried the download approximately every 30 seconds; and
5. never notified the user or selected a cached song.

The queue remained present, so the failure was recovery deadlock rather than
queue cancellation.

## Root Cause

Four current design choices combine to create the deadlock.

### Offline Mode is not observable by connectivity

`PreferenceManager.offlineMode` is a persisted Compose state delegate.
`ConnectivityManager.isOnline` reads it inside a transformation driven only by
Android network callbacks. Changing the setting does not itself emit a new
connectivity value, which is why the settings description says a restart is
required.

### Internet reachability is treated as Navidrome reachability

Android reports the network as validated while a single endpoint is down.
`MediaPlayerViewModel.isAvailable` consequently evaluates every remote song as
available through `isOnline || isDownloaded`, even though Navidrome cannot be
reached.

### Download retry is an infinite polling loop

`DownloadManager.executeDownloadProcess` deliberately treats connection,
socket-timeout, and DNS failures as retryable and waits a fixed 30 seconds
before trying again. The download remains non-terminal indefinitely.

### Playback fallback waits for a terminal download

`AndroidStablePlaybackRecoveryCoordinator` requests the download and waits for
`DownloadStatus.FAILED` before applying its terminal policy. Because transport
failures never become terminal, notification and fallback are never reached.
Its fallback lookup also derives natural list order instead of using Media3's
actual shuffle/repeat traversal in `PlayerUiState.upcomingIndexes`.

## Terminology

### Selected Offline Mode

The user's persisted choice: `Auto`, `Forced`, or `NoWiFi`. Only an explicit
user action may modify this value.

### Automatic Offline Reason

A process-local reason that temporarily forces Offline Mode. The first reason
is `NavidromeUnavailable`. It is never written to settings or restored after
process death.

### Effective Offline Mode

The mode consumed by existing Navic behavior:

```text
effectiveMode = Forced                         when automaticReason != null
effectiveMode = selectedOfflineMode            otherwise
```

### Raw Network Availability

Whether the operating system reports a validated Internet path. It is
independent from the selected and effective Offline Mode and remains available
to the service-health monitor while automatic Offline Mode is active.

### Navidrome Availability

The process-local service state `Available` or `Unavailable`. It changes to
`Unavailable` only for a classified network/service failure and returns to
`Available` only after an authenticated Navidrome `ping()` succeeds.

## Required Invariants

1. Automatic fallback never writes `PreferenceManager.offlineMode`.
2. A user mode change emits a new effective connectivity state without an app
   restart or Android network callback.
3. Automatic `Forced` state is process-local and cannot survive a crash or
   restart.
4. Clearing automatic state reveals the latest user selection, including a
   selection changed while the outage was active.
5. Raw network availability remains observable while effective Offline Mode is
   forced.
6. One outage produces one transition and one user-facing loss message.
7. Authentication, missing-song, local-file, decoder, and malformed-media
   failures do not masquerade as a service outage.
8. Queue contents and order never change because connectivity changed.
9. Cached fallback follows Media3's actual upcoming order, including shuffle
   and repeat behavior.
10. A song is cached only when `DownloadManager.getDownloadedFilePath` returns a
    currently usable file.
11. User pause wins over service restoration and download completion.
12. Download intents remain durable while offline, but workers perform no
    network work until effective online state returns.
13. There is no fixed-delay download retry loop. Time-based waiting is allowed
    only for the coalesced service-health heartbeat.
14. Service restoration does not interrupt a cached fallback already playing.

## Target Architecture

### Reactive Offline Mode

`PreferenceManager` keeps the public `offlineMode` property for settings call
sites and adds `offlineModeState: StateFlow<OfflineMode>`. Its setter updates
the persisted value and the flow atomically from the caller's perspective.

`OfflineModeCoordinator` combines the selected mode with a process-local
`AutomaticOfflineReason?` and exposes one `OfflineModeState`:

```kotlin
data class OfflineModeState(
	val selectedMode: OfflineMode,
	val effectiveMode: OfflineMode,
	val automaticReason: AutomaticOfflineReason?
) {
	val isAutomaticallyForced: Boolean
		get() = automaticReason != null
}
```

`ConnectivityManager` combines raw network status with
`OfflineModeCoordinator.state`. Existing consumers continue using `isOnline`,
but that value now updates for network changes, manual mode changes, and
automatic mode changes. A separate `isNetworkAvailable` flow exposes only the
validated operating-system path for health probing.

### Service Availability Manager

`NavidromeAvailabilityManager` is the single owner of service availability. It
accepts classified outage reports from playback and downloads, enters
`AutomaticOfflineReason.NavidromeUnavailable` idempotently, and exposes state
for notification and recovery. A separate bounded event flow reports entered,
duplicate, failed-probe, and restored decisions to diagnostics without using
those events as authoritative state.

While unavailable and while a raw network path exists, the manager performs
one authenticated `SessionManager.ping()` at a time. Probe requests are
conflated. They are generated by:

- the initial outage transition;
- restoration or replacement of the raw network path;
- an explicit user playback/resume request; and
- one monitoring heartbeat while the outage remains active.

The heartbeat does not cancel work or declare failure after a deadline. It only
checks whether an otherwise unchanged server has returned. A successful ping
clears the automatic reason. Failed pings retain the current state and do not
emit duplicate user messages.

### Failure Classification

The following conditions enter automatic Offline Mode:

- Media3 network connection failure or connection timeout;
- DNS resolution failure, connection refusal/reset, or no route to host;
- socket/connect timeout while contacting Navidrome; and
- service-unavailable HTTP responses such as 500, 502, 503, 504, and
  Cloudflare-style 521-524 responses.

The following remain terminal item/request errors and use their existing UI:

- HTTP 401 or 403 authentication/authorization failure;
- HTTP 404 or song-not-found response;
- unsupported or malformed media;
- non-audio response content;
- verified local file read/decoder failure; and
- an unclassified programming or storage exception.

The classifier inspects the complete throwable chain but never logs credentials,
authorization headers, or signed stream URLs.

### Offline Playback Transition

When effective online state becomes false during music playback, or a playback
transport error reports Navidrome unavailable, Android performs this ordered
decision:

1. If the current song has a usable downloaded file, replace its source in
   place, retain position and user playback intent, and resume locally.
2. Otherwise traverse `PlayerUiState.upcomingIndexes` and select the first entry
   with a usable downloaded file.
3. Replace that target's Media3 source with the verified local file before
   seeking to it.
4. If no cached entry exists, retain the current queue/index/position and a
   pending service-recovery intent. Playback waits without scanning remote
   entries.

The fallback does not consult `skipMediaOnError`: connectivity fallback is a
source-selection decision, while that preference remains the terminal
per-item error policy.

As cached songs complete, automatic transitions apply the same decision again.
Remote entries are bypassed without removal, reordering, or persistent download
creation.

### Restoration

After a successful authenticated ping:

- the automatic reason is cleared;
- effective mode returns to the user's current selected mode;
- download workers are awakened by the effective online flow;
- remote queue entries become eligible again; and
- the loss message is removed from the playback notification.

If playback is waiting on the original remote item and retained user intent is
Play, Navic refreshes that item once in place, seeks to the retained position,
and resumes. If a cached fallback is already playing, Navic does not jump back;
the newly available remote entries participate in subsequent normal traversal.

If the user paused while waiting, restoration prepares the source but does not
play. If the selected mode is still `Forced`, or `NoWiFi` still blocks the
current network, service restoration does not bypass that user policy.

### Download Suspension

Download workers observe effective `ConnectivityManager.isOnline` before
claiming a queued intent. When Offline Mode becomes effective:

- no new intent is claimed;
- active network jobs are cancelled cooperatively;
- their current generation is returned from `DOWNLOADING` to `QUEUED`;
- partial files are deleted through the existing cancellation path; and
- queue order, cancellation flags, and intent generations are preserved.

If a download itself encounters a classified Navidrome outage first, it reports
the outage, requeues its current generation, releases its worker slot, and
waits on state. Unknown or content-specific errors become `FAILED` instead of
retrying forever.

When effective online state becomes true, one wake-up restarts normal bounded
worker processing. Service restoration is an event, not permission to create a
second copy of an existing intent.

### User Notification

The automatic `Available -> Unavailable` transition emits exactly:

```text
Connection lost - Switching to Offline mode
```

When the app UI is present, `SnackBarManager` presents it once. The Android
playback notification presents the same text as status/subtext without
replacing the song title or artist and without adding an alert sound. The status
is removed silently when service availability returns.

Manual `Forced`/`NoWiFi` selection does not show a connection-loss message.
Repeated player errors and failed health probes during the same outage do not
show it again.

### Diagnostics

The bounded `PlaybackDiagnostics` log records state transitions and decisions:

- `automatic-offline-entered`
- `automatic-offline-duplicate`
- `offline-current-local-selected`
- `offline-upcoming-local-selected`
- `offline-no-local-fallback`
- `download-suspended-offline`
- `download-requeued-service-unavailable`
- `navidrome-health-probe-failed`
- `navidrome-service-restored`
- `waiting-item-resumed-after-restore`

Entries include song ID, queue index, target index, selected/effective mode,
automatic reason, raw network class, retained play intent, and the classified
failure category. They exclude complete URLs and credentials.

## State Transitions

| Current state | Event | New state | Playback action |
| --- | --- | --- | --- |
| Selected `Auto`, service available | User selects `Forced` | Effective `Forced` | Use current local source or first cached upcoming item |
| Selected `Forced` | User selects `Auto`, service available | Effective `Auto` | Remote sources become eligible immediately |
| Service available | Classified playback/download outage | Automatic `Forced` | Notify once and select cached fallback |
| Automatic `Forced` | Duplicate transport failure | Automatic `Forced` | No duplicate notice or fallback command |
| Automatic `Forced` | Health probe fails | Automatic `Forced` | Keep waiting/cached playback |
| Automatic `Forced` | User changes selected mode | Automatic `Forced` | Persist new selection; do not clear outage |
| Automatic `Forced` | Health probe succeeds | Latest selected mode | Wake downloads; resume only a waiting item |
| Any automatic state | Process restarts | Selected mode only | Automatic reason is absent by construction |

## Acceptance Matrix

| Scenario | Required result |
| --- | --- |
| Change `Auto` to `Forced` | Queue rows and playback availability update without restart |
| Change `Forced` to `Auto` with service available | Remote playback/download eligibility returns without restart |
| General Internet works but Navidrome times out | Automatic `Forced`, one exact notification, cached fallback |
| Current item has a valid download | Same queue item resumes locally at retained position |
| Current item uncached, later shuffled item cached | First cached item in Media3 upcoming order plays |
| Repeat-one on uncached item | Queue/index retained; wait for service rather than scan |
| No cached songs | Queue/index/position retained; playback waits visibly |
| User pauses while waiting | Service restoration does not auto-play |
| Cached fallback is playing when service returns | Current cached song is not interrupted |
| Queued downloads during outage | Intents remain `QUEUED`; no network attempts or fixed retry loop |
| Active download when outage starts | Partial work is cancelled, current generation requeued |
| HTTP 404 or decoder error | Existing item error behavior; no automatic Offline Mode |
| HTTP 503 or DNS failure from download | Automatic `Forced`; current generation requeued |
| User changes mode during outage | New choice persists and is revealed after recovery |
| Process is killed during outage | Stored selection is unchanged; automatic state is gone on restart |

## Staged Delivery

The stages are testable integration checkpoints on one feature branch. No
partially functional public release is published between them.

### Stage 1: Reactive Offline Mode

Make the selected preference observable, introduce selected/effective state,
update Android connectivity from both network and mode changes, and remove the
restart text.

Gate: common policy/preference tests and Android source tests prove runtime
emission and non-persistence of automatic state.

### Stage 2: Event-Driven Service and Download State

Add failure classification, authenticated health monitoring, download worker
suspension/requeue, and remove the fixed 30-second download retry loop.

Gate: deterministic coroutine tests prove one outage transition, conflated
probes, successful restoration, preserved download generations, and no network
work while offline.

### Stage 3: Queue-Preserving Cached Playback

Add the pure fallback decision policy, wire it to Android playback, use actual
upcoming order, retain user intent when no fallback exists, and resume a waiting
item after service recovery.

Gate: policy and Android source tests cover current-local, shuffled upcoming,
repeat-one, no-cache, pause, duplicate event, and restored-service behavior.

### Stage 4: Notification and Diagnostics

Add the exact snackbar and media-notification status, deduplicate it by service
state transition, and record bounded diagnostics.

Gate: resource/policy tests and Android notification source tests prove exact
copy, one-shot behavior, silent clearing, and unchanged title/artist metadata.

### Stage 5: Candidate Deployment

Sync current public master, retain the current `iota##` release family, run
focused and full Android gates, build a debug candidate, and perform a
controlled ADB offline/restoration exercise on an Android device with cached
songs.

Gate: package/version/process state, notification text, queue identity/order,
cached continuation, download suspension, and service restoration are captured
from the installed candidate.

### Stage 6: Public Android Release and Field Validation

Publish one Android-only signed release after all earlier gates pass. The next
walk is field acceptance for real roaming/server-loss conditions, not a reason
to omit deterministic pre-release validation.

Gate: public APK digest, signature, embedded version, tag ancestry, clean
install/upgrade, and retained playback diagnostics are verified. No iOS build
or release is required.

## Non-Goals

- No iOS playback implementation or iOS release.
- No canonical queue-store rewrite.
- No queue reordering, deletion, or reinsertion for connectivity recovery.
- No automatic creation of durable downloads from an outage.
- No server, DNS, reverse-proxy, or Navidrome deployment changes.
- No alerting notification sound or repeated restoration toast.
- No cancellation timeout or deadline-based recovery.
- No claim that Wi-Fi-only testing proves real roaming behavior.

## Success Criteria

The feature is complete when a released Android build can lose Navidrome while
general Internet remains available, show the exact connection-loss message
once, continue through cached songs in actual playback order, hold the intact
queue when no cache exists, suspend rather than poll downloads, and restore
remote eligibility automatically without changing the user's persisted Offline
Mode selection.
