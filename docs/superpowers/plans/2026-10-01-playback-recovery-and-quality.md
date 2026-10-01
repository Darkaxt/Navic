# Playback Recovery and Quality Plan

Specification: `../specs/2026-10-01-playback-recovery-and-quality.md`.
Worktree: `D:/Temp/navic-music-maintenance-20260908`.
Branch: `fix/music-resume-cache-quality-20261001`.

## Stage 1: Idle Navigation and Idempotent Outage Recovery

Status: COMPLETE. Requirements: R1, R2, R6.
Add failing behavioral tests, correct idle navigation and zero-position handling,
avoid healthy local-source resets, retain error causes. Verify focused policies,
real ExoPlayer navigation, and production wiring. Remaining acceptance
criteria: none. Verification: focused host gate passed on 2026-10-01; real
ExoPlayer retained its queue/paused intent after idle navigation; outage/position
policies passed; persisted error cause and URL redaction passed; production
wiring source checks passed. The initial test-first run stopped at absent new
hooks (compilation failure), not a behavioral red run. Blockers: none.
Tracked deferrals: none.

## Stage 2: Cached-First Recovery

Status: COMPLETE. Requirements: R3, R7.
Use the existing traversal policy and validated download paths to prefer local
recovery targets; retain online fallback, offline hold, and user intent. Verify
shuffled order, no-cache, unavailable files, and paused/skip-disabled behavior.
Remaining acceptance criteria: none. Reopened after proving Media3's native
URI replacement changes shuffle order; a same-identity session-player boundary
now preserves the original order. The real ExoPlayer regression failed before
the boundary fix and passed afterward, retaining current index and position.
Blockers: none. Tracked deferrals: none.
Include transport-only outage classification; preserve player fault recovery
without incorrectly changing global service availability.
Verification: focused host gate passed. Traversal policies select cached before
remote, reject unavailable preferred files, retain online fallback/offline hold,
and retain paused/skip-disabled behavior. Production wiring resolves the cached
target URI before seeking. Real Media3 exception classification separates internal
foreground-mode timeout from IO timeout, HTTP 503, and terminal HTTP 403.

## Stage 3: Network Quality and Source Information

Status: COMPLETE. Requirements: R4, R5.
Refresh upcoming remote URIs for network/quality changes, refresh an idle source
on explicit Play, use selected-source bitrate hints, and clear stale track data.
Verify URI selection, current-item stability, original cached audio, and format
presentation. Reconcile the entire specification, run the music integration gate,
commit the verified implementation before the authorized delivery stage.
Quality/format policy and client-URI tests passed in the music gate, including a
behavioral red/green display regression. Stable, non-user service-side source
refresh is verified. Remaining acceptance criteria: none. The former
internal R3 blocker is resolved by Stage 2's verified boundary. Real ExoPlayer
batch tests preserve current URI/position/metadata/shuffle/paused intent and
reject stale account, URI, and queue snapshots. The actual service callback
preserves gap/volume resume and rejects another app; explicit commands still
cancel resume. Repeated real-client URI selection settles without another update.
Final music host gate passed; inspected XML has no failures/errors/skips.
Blockers: none. Tracked deferrals: none.

## Stage 4: Authorized Public Delivery

Status: COMPLETE. Requirement: public-delivery portion of R8.
Publish `v1.0.11-iota69` (versionCode 596) from the verified implementation.
Verify tag CI, public release, independently downloaded package/version,
SHA-256, and certificate. Clean registered expendable artifacts and preserve
the specifications and verified source. Do not install on devices. Remaining
delivery acceptance criteria: none. Blockers: none. Tracked deferrals: none.
Verification: tag CI succeeded, public package/version/certificate/checksum and
packaged governance checks passed, and both reviewed cleanup transactions applied
without residual errors. See the specification's Public Delivery Verification.
Post-delivery action: execute `shutdown -h -t 00` after this result is recorded.
Hibernation is a simple final machine command outside the implementation stages;
it remains required and is not claimed executed by this checkpoint document.
