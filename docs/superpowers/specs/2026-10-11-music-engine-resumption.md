# Music Engine Resumption

Authorization: already_authorized by the user's 2026-10-11 implementation request.
Scope: Android music playback only. Reader behavior and networking policy are
unchanged. The user subsequently authorized publication of the verified release;
device installation is optional and is not required while the phone is locked.

## Evidence

On iota69 the connected phone displayed Beauty and the Beast Medley and a
409-song queue while its music MediaSession reported STOPPED, no metadata, and
zero queued items. Play reproduced the missing onPlaybackResumption callback at
22:41:51 and 22:55:32. Source inspection identified unconditional cancellation
of engine restore by transport commands and no empty-engine reconciliation.
The precise initial restore cancellation ordering was not logged.

## Required Behavior

- R1: Resume, Next, Previous, Seek, and selecting a retained queue item must
  reconstruct an empty engine timeline before acting. Preserve queue identity,
  selected item, position, shuffle order, repeat, speed, and pitch.
- R2: Commands arriving during initial state load or asynchronous media-item
  construction wait for actual readiness. Newer transport intent supersedes older
  intent; Pause must prevent delayed playback. No arbitrary delays or deadlines.
- R3: Clear, replacement playback, account change, and disposal invalidate pending
  restoration and commands. Old work cannot resurrect an obsolete queue or play
  an outgoing account's files. Reconnection cannot silently drop the UI queue.
- R4: An empty engine must not overwrite retained current-index, progress, or
  shuffle information in the UI. Failures leave the queue intact, clear loading,
  provide existing error feedback, and emit actionable restoration diagnostics.
- R5: Implement Media3 service-side playback resumption from the existing
  account-scoped persisted state, without requiring a viewmodel. Use existing
  media-item/source selection (validated cached file before remote stream), and
  preserve playback settings and shuffle. Disabled persistent queues, missing or
  malformed state, revoked accounts, and newer replacement/clear intent must not
  resurrect playback.
- R6: Verify the actual Android player boundary with behavioral host tests,
  including a 409-item queue, resume/navigation, delayed preparation, cancellation,
  reconnect/replacement fences, persisted-state validation, and local-source
  selection. Compile/package the changed Android application. Do not claim the
  connected phone is fixed without installing and testing a candidate.
- R7: Publish the next correctly numbered iota release after integrated
  verification. Independently download the published APK and verify its version,
  digest, signature, size, and exact source commit. Do not require installation
  on the locked phone or claim device-side verification without performing it.

## Design

Extend the existing AndroidPlaybackStateSynchronizer rather than add a second
queue subsystem. Its restoration completion becomes awaitable by transport
commands. Distinguish cancellation of bulk playlist preparation from invalidating
saved-queue restoration. Keep command ownership and account checks on the main
thread, with media-item construction off the main thread. Preserve real live
engine queues rather than replace them with stale persisted snapshots.

Keep the ViewModel below its existing architectural size guard by placing the
new transport operation's job, revision, readiness wait, and account/controller
fences in AndroidPlaybackTransportCoordinator. It delegates queue reconstruction
to the existing synchronizer; it is not another queue store or playback engine.

The service implements Media3's resumption callback using the existing state
repository and media-item factory. Its cancellable operation is invalidated by
newer player intent, account revocation, and service destruction. It uses the same
restored-state validation and positioning rules as the viewmodel path.

Media3 calls its play action even when a resumption future fails or is cancelled
([Media3 1.10.1 implementation](https://github.com/androidx/media/blob/1.10.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionImpl.java)).
Therefore cancellation alone is insufficient: the service's forwarding player
must suppress that framework play action while completing a rejected resumption
on the application thread. Future completion and its player mutations must be
verified through the real MediaSession/MediaController boundary. Successful
completion restores settings and the saved shuffle order after the framework
installs the returned media items, before it starts playback.

## Stages And Reconciliation

### Stage 1: Awaitable Engine Reconciliation

Status: COMPLETE

Requirements: R1-R4, corresponding R6 behavioral regression tests.
Acceptance: retained 409-item state can populate an empty real Android player;
position/settings survive; delayed restore and newer pause/clear/replacement
cannot race into stale playback; commands are wired through readiness and empty
engine state does not erase the UI snapshot.

Evidence: gated Android host verification on 2026-10-11 passed the eight real
ExoPlayer reconciliation regressions plus viewmodel/controller wiring checks.
XML reports show zero failures/errors. Covered retained 409-item position and
settings, exact shuffle navigation, cancellation, reconnect, live replacement,
invalid progress, and a failed mapping followed by an explicit retry. Command
wiring verifies readiness before transport, Pause suppression without discarding
the saved queue, and account/generation fences. Gate ticket 656 completed with
exit zero; the current production and host-test sources compiled successfully.
Blockers: none identified. Tracked deferrals: none.

### Stage 2: Service Resumption And Integrated Verification

Status: COMPLETE

Requirements: R5, final R6 and integrated R1-R4 reconciliation.
Acceptance: callback returns playable account-owned items and correct position;
invalid/missing/disabled/revoked state and superseding commands are rejected;
affected music tests and Android packaging pass; final diff is scoped, verified,
and committed. Device-side candidate verification is explicitly not claimed.

Evidence: gate ticket 665 completed successfully on 2026-10-12 (local time),
including the nine affected host-test suites and :androidApp:assembleDebug.
Inspected XML reports contain 63 tests, zero failures, zero errors, and no skips.
The twelve service regressions exercise real MediaSession/MediaController
requests, including 409-item resumption at index 37/60 seconds, exact shuffle,
Pause and replacement during loading, Clear followed by Play, account revocation,
disposal, rejected saved states, metadata-only requests, newer settings, and a
reentrant Stop while Media3 installs the playlist. The eight reconciliation tests
also pass against this integrated code. Existing source-selection tests confirm
completed cache precedence and cellular streaming parameters; service wiring
uses that same factory and policy. R1-R6 are satisfied at the host-test boundary.

The generated debug APK was inspected with aapt: darkaxt.navic.debug, code 596,
v1.0.11-iota69 (the pre-release-bump version). It was not installed. No claim of
physical-phone recovery or real audio-output verification is made. The final
diff is limited to music resumption, its regression tests, and this specification.

The shared gate was acquired. Fresh Gradle markers verified two workers,
parallel=false, a 3 GiB Gradle heap, and one 512 MiB fail-fast test fork. Kotlin
used in-process compilation. No memory-related failure or budget increase occurred.
Release-gate reconciliation: the first Linux music-suite run passed 1,152 tests
and failed AndroidMediaPlayerDecompositionSourceTest because the ViewModel grew
to 1,232 lines, exceeding its existing less-than-1,200-line guard. The previously
verified playback behavior is unchanged, but integrated acceptance is reopened
until transport coordination is extracted and the guard plus affected tests pass.
No size threshold or acceptance criterion was weakened. Gate ticket 669 then
passed the architectural guard, ViewModel wiring, all twelve real-controller
resumption checks, eight engine-reconciliation checks, and five new transport
behavior checks (49 tests, no failures/errors/skips, inspected XML). The new
checks verify readiness before navigation, newest intent, Pause cancellation,
reconnection during mapping, account revocation, and cancellation inside the
navigation command before its preserved Play action. Android packaging passed
again; aapt confirms the rebuilt debug APK has code 597/v1.0.11-iota70. It was
not installed. The same fresh two-worker/3 GiB/single-512-MiB-test-fork profile
was verified. Integrated R1-R6 and the architectural constraint are satisfied.
Blockers: none. Tracked deferrals: none.

### Stage 3: Public Release

Status: ACTIVE

Requirements: R7.
Acceptance: the verified integrated source is committed and synchronized with
the fork; the next iota release is public; independently downloaded release
assets match the verified source, version, digest, size, and signing identity.

Evidence: functional implementation committed as 65550854e. Release version is
v1.0.11-iota70, Android versionCode 597. Public build/publication and independent
asset verification remain pending; this stage is not complete.
The failed run 38177210970 published no APK or release. Its architectural
blocker is resolved by Stage 2's verified extraction. The unpublished tag will
be updated to the corrected source without consuming another release number.
Public publication and asset-proof acceptance remain unsatisfied.
Blockers: none. Tracked deferrals: none.
