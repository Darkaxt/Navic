# Playback Recovery and Streaming Quality

Authoritative specification. Baseline: `0753a842` (iota68). Scope: the
confirmed defects uncovered while investigating long-paused playback and walking
with unstable coverage. On 2026-10-01 the user authorized publication of the
completed fixes in a new release, followed by laptop hibernation using
`shutdown -h -t 00`. Device installation is not requested. Hibernate only after
the public release and signed Android artifact have been independently verified.

## Evidence and Limits

The connected Samsung phone runs iota68. Its existing 409-entry queue is retained
and currently responds on Wi-Fi. Investigation must not replace that queue,
disable its network, clear logs, or install a development APK.

Saved PlaybackDiagnostics logs contain `ERROR_CODE_TIMEOUT` / `Unexpected runtime
error` while playing, and repeated `offline-current-cache` replacements after an
outage. On 2026-09-30 an upcoming cached fallback was subsequently retried at
242000 ms: zero player position was incorrectly treated as missing and replaced
with the previous song's UI progress. The retained diagnostics omit the exception
cause; they cannot establish the origin of the internal Media3 timeout or prove
that this is the user's specific long-pause failure.

Source inspection confirms that seeking/selecting an idle player does not prepare
it, while ordinary MediaController.play already prepares idle playback through
MediaSession. Outage handling replaces healthy local playback on every repeated
availability event. Terminal recovery considers remote songs equally available
as cached songs when online. Queued remote URIs are not refreshed for cellular
quality changes. The phone displayed `OPUS / 48 kHz / 1681 kbps`; the last value
can come from original-file metadata rather than the active source.

## Requirements

- R1: Queue navigation after a source error must prepare an idle player without
  rebuilding its queue. Selection honors its requested play/pause intent; Next
  and Previous preserve existing intent. Normal Play keeps Media3's own retry
  behavior. Acceptance: real Media3 player navigation from IDLE prepares the
  selected item and preserves paused intent and queue identity.
- R2: Repeated outage observations must leave healthy local playback untouched.
  Actual source errors may still recover. Zero is a valid recovery position;
  progress from another item must never be used. Acceptance: repeated local
  observations do not seek/replace/prepare; idle/error local playback is not
  classified as healthy; zero position remains zero despite stale UI progress.
- R3: Recovery advancing to another song prefers an existing usable cached file
  in the current shuffle/repeat traversal before a remote item. Preserve normal
  explicit selection and healthy online queue order, skip-on-error preference,
  and paused intent. Acceptance: a remote earlier candidate loses to a cached
  later candidate during recovery; no-cache online fallback and offline hold
  remain valid; selected recovery source resolves to a local file.
  Updating the URI of the same queue identity must preserve the actual Media3
  shuffle order, not merely the song list's positional order.
- R4: Network/quality changes update upcoming remote sources without interrupting
  the active source. A failed/idle current source must use the latest configured
  request when explicitly resumed. Existing completed files continue to take
  precedence and are not re-encoded. Acceptance: lossless Wi-Fi queue followed
  by cellular High uses OPUS/192 kbps for upcoming remote items; unchanged
  sources/local items and actively playing current items are not replaced.
  Internal upcoming-source updates must not cancel a configured inter-song or
  volume-zero resume; explicit user commands must still cancel it. Batch source
  refreshes at the existing service boundary instead of issuing one apparent
  user queue edit per download/quality update.
- R5: Technical information describes the selected source, not current network
  preferences or the previous track. Do not combine an OPUS decoder with a FLAC
  source bitrate when actual bitrate is unknown. Acceptance: bitrate hints come
  from the selected URI; missing selected-track data clears stale decoder data;
  cached original audio can legitimately retain its original codec.
- R6: Persist playback exception causes with the always-retained diagnostics so
  an unreproduced internal timeout can be diagnosed later without enabling
  general issue logging. Do not log authenticated URLs or credentials. Acceptance:
  a persisted PlaybackDiagnostics error includes its exception cause.
- R7: Only transport/source IO failures may declare Navidrome unavailable.
  Internal Media3 timeouts must not mark the server offline merely because a
  nested message says "timed out". Acceptance: internal timeout with an
  ExoTimeoutException remains a player fault; source IO timeout and HTTP 503
  enter outage recovery; terminal HTTP 403 does not.
- R8: Publish the completed, verified fixes as the next numbered iota release.
  Independently download and verify the public APK's package, version, checksum,
  and expected release certificate. Do not install it on connected devices.
  Clean expendable task outputs, then execute `shutdown -h -t 00` after delivery.

No arbitrary timers, retries, queue reordering, bulk audio prefetch, new setting,
reader changes, or new download infrastructure. The exact historical long-pause
episode remains unproven; correcting these reproducible defects is not proof
that every possible long-pause failure is resolved.

## References

- [Media3 player errors](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)
- [Media3 1.10.1 play handling](https://github.com/androidx/media/blob/1.10.1/libraries/common/src/main/java/androidx/media3/common/util/Util.java)
- [Media3 1.10.1 URI replacement](https://github.com/androidx/media/blob/1.10.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java)

## Implementation Reconciliation (2026-10-01)

- R1: Idle navigation calls prepare after seeking; the real ExoPlayer host test
  preserves queue identity and paused intent. Ordinary Play remains Media3-owned.
- R2: Healthy local-source outage observations return before recovery mutation.
  Position policy keeps zero and rejects UI progress from another queue item.
- R3: Recovery considers validated local files first within existing traversal,
  resolves their URI before seeking, and retains remote fallback. A session
  ForwardingPlayer preserves the actual native ShuffleOrder for equal-count,
  same-ID replacements. Native replacement failed the regression before this fix.
- R4: URI-only refresh commands use the existing service boundary, same-app UID
  authorization, account revision, index/identity, and old-URI preconditions.
  The service excludes its actual current index, batches the remaining ranges,
  and preserves pending gap/volume resume during synchronous source mutations.
  Explicit commands still revoke that intent. Controller timeline/transition
  events reconcile rejected stale snapshots without timers or retries. One owned
  setup job prevents duplicate refresh collectors after reconnect.
- R5: Actual selected track formats clear on transition/missing tracks. A runtime
  selected-URI bitrate hint replaces network-preference guessing. OPUS no longer
  inherits the original FLAC bitrate; cached original audio remains valid.
- R6: Always-retained diagnostics persist cause classes/messages, redact URLs,
  and terminate cyclic cause chains. Verified with the real log manager.
- R7: Real Media3 timeout and source IO/HTTP exception tests verify classification.
- R8: Delivery pending. Host proof does not establish the unreproduced historical
  long-pause cause or constitute live phone verification of the new binary.

Focused host checks and the final music-only host gate passed on 2026-10-01.
The inspected final XML contains 1,132 tests, no failures/errors/skips. Reader and
audiobook behavior was not changed; no development APK was installed.
