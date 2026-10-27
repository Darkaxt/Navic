# Android Background Audio And Balance Warnings

Authorization: already_authorized. Fix the diagnosed silence and phone balance,
add the requested actionable Android-settings warning, and publish one complete
normal release. Preserve reader work and the production signing identity.

## Evidence And Design

The Samsung phone runs Android 17 and Navic iota71 (598). AudioService recorded
actual full background-audio hardening mutes at 17:16:20 and 17:17:38 on October
27. Full indicates foreground service without while-in-use capability. Exact
16:45 app logs were overwritten; no exact afternoon call stack is claimed.
Bluetooth connected immediately before the first retained mute. Connected-audio
balance is -0.68, independently present in AudioService and AudioFlinger.

Relevant source defects: service lifetime uses isPlaying instead of ongoing
playback intent, and device-connect resumption calls play directly. User queue
preparation and restored transport can also complete after the UI is hidden.

Use the existing MediaSessionService. Acquire a foreground preparation lease at
accepted user commands before asynchronous work. Fulfil Android's foreground
notification obligation immediately, retain it until cancellation/failure or
handoff to Media3's real foreground notification, and fence stale lease cleanup.
Do not install a second service, replace the queue with placeholder media, call
notification lifecycle callbacks directly, or extend Media3's existing framework
retention deadline. Preserve the real queue and existing cancellation owner.

## Requirements

- R1: Center only the authorized Samsung phone's connected-audio balance to 0.
  Read back the setting and AudioService/AudioFlinger state. Other settings,
  devices, pairings, permissions and device-wide hardening remain unchanged.
- R2: Accepted user queue preparation and transport resumption establish their
  foreground lifecycle before asynchronous work; terminal work releases only its
  own lease. Preserve previous playback until a replacement commits. Handoff to
  Media3 must not leave a foreground gap or a stale preparation notification.
  Merely opening Navic must not create a preparation foreground service.
- R3: Task removal must not destroy ongoing buffering/temporary suppression or
  active preparation. Explicitly paused/finished work stops through Media3's
  supported lifecycle. Bluetooth device-connect auto-resume may use already
  owned foreground playback, or visible-app admission; otherwise keep the queue
  paused and offer a user-triggered resume notification. Preserve pause/cancel
  fences and existing recovery behavior. Never disable Android audio hardening,
  lower targetSdk, invent a timeout or force audio permission overrides.
- R4: While music uses connected headphones, a non-zero finite Android balance
  produces an actionable, silent informational notification naming the reduced
  side and opening Android accessibility settings. Do not repeatedly alert for
  an unchanged setting, including after service recreation. Centering clears the
  warning. Respect denied notification permission and unreadable settings; do
  not silently rewrite accessibility preferences. Do not claim generic detection
  of every Android restriction or diagnose earbud hardware from software state.
- R5: Verify the integrated lease/cancellation/handoff/lifecycle and warning
  behavior with focused native/behavioral tests and Android packaging. Validate
  the real Android 17 boundary with hardening left enabled: a user start followed
  by background playback retains capability; warning and settings action are
  wired to real Android notifications. No Dev app installation on the tablet.
  Scope clarification (user, October 28): the user will perform the Bluetooth
  listening/warning test later. Do not shift the physical phone's balance again
  for that test. Native notification/action tests remain required; the acoustic
  Bluetooth check is user-deferred and is not a release acceptance requirement.
  The user subsequently prohibited further phone interaction and clarified that
  unplugging USB means stop, not permission to use wireless ADB. The agent's
  wireless connection was closed. Do not reconnect, inspect or control the phone
  without a new explicit request. Remaining physical playback/listening checks
  are user-owned; automated/native and signed-package verification gate release.
- R6: Reconcile all requirements, commit/sync completed changes, publish the
  next unused iota## production release, inspect its music/signing/governance
  gates, independently retrieve public APK and verify source/version/package/
  size/hash/signature. Preserve private device logs. Clean expendable scratch
  immediately after consumers finish.

## Stages And Current Reconciliation

### Stage 1: Integrated Android Audio Behavior

Status: COMPLETE
Requirements: R1-R5.
Acceptance satisfied: R1 corrected to 0.0 and independently read back from the
setting, AudioService mMainBalance and AudioFlinger/HAL default and speaker
balance, all zero. Device disconnection briefly prevented the first attempt;
the user reconnected it and the correction then succeeded. No other setting
was changed.
R2-R4 passed focused behavioral/native checks: request-owned foreground preparation,
supported Media3 notification handoff, intent-aware task lifetime, guarded resume,
and event-driven/deduplicated actionable balance notifications. Native tests also
cover denied permission, reduced channel, settings action and service recreation.
Signed iota72 (599) packaging and packaged governance checks passed. Android 17
captured preparation foreground ownership and PROC_STATE_TOP WIU capability
after Home, with hardening unchanged; handoff was logged. These observations are
not an acoustic test or proof of uninterrupted playback. The user owns remaining
physical checks; no further device access is authorized.
Verification: inspected the full music XML: 1,178 tests, zero failures, errors or
skips. Focused native/behavioral checks passed; the initial notification fixture
failure was traced to Robolectric 4.16 typed settings storage and corrected to
Android's actual string storage, without changing production behavior. Signed
package is darkaxt.navic, iota72/code599, target37, production certificate ending
eaa1dae7; packaged warning resources, vendor assets and acknowledgements passed.
Gate ticket 1800 acquired Local/Darka.AndroidGradleBuildGate; actual 3 GiB heap,
two workers, parallel=false, one 512 MiB test fork, native fail-fast and verified
single-use daemon exit. Kotlin requested in-process; no memory increase required.
Closure assessment: scope remains the existing request coordinator, player and
MediaSessionService; no second service, placeholder queue, independent timer or
new general infrastructure. No required consolidation was identified.
Remaining acceptance criteria: none under the user-revised device-testing scope.
Blockers: none. Required tracked deferrals: none. Physical listening remains
user-deferred and is not claimed passed. Unplugged USB must never be treated as
permission for alternative transport or further phone control.

### Stage 2: Verified Public Delivery

Status: ACTIVE
Requirements: R6. Depends on Stage 1.
Acceptance satisfied: local music, signing and packaged governance prerequisites
passed; fork/master was fetched and remains the unchanged implementation base.
Advancement blockers (internal): final commit/push/tag, successful release CI,
public package proof and reviewed scratch cleanup remain required.
Tracked deferrals: none.

## Storage And Verification

Maintained music worktree remains D:/Temp/navic-music-maintenance-20260908;
this is an existing intentional source binding, not disposable build output.
Build/dependency-inspection scratch will be exactly
D:/Temp/navic-audio-hardening-20261027. D: is required for sustained/small-file
Gradle build I/O; use the installed transactional helper, not a directory sweep.
Private prior diagnostics remain D:/Projects/Navic-diagnostics/
2026-10-27-silent-bluetooth and must not be published with device identifiers.

All Windows Gradle invocations use the installed shared gate, two workers,
no parallel projects, 3 GiB heap and one bounded test fork. Report actual gate,
profile and daemon-exit evidence, not requested settings alone.

The candidate version is assigned iota72 (599) during Stage 1 so the signed
device-validation package has its intended release identity. This is not public
publication; Stage 2 remains gated on the complete Stage 1 workflow.
