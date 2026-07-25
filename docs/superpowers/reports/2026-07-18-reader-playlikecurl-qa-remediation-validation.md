# Reader PlayLikeCurl QA Remediation Validation

## Status

- Frozen implementation commit: `118a001a7adb05c8a0b650a9433c45b5fbb6f80c`
- Implementation branch: `fork/reader/playlikecurl-qa-remediation`
- Emulator acceptance: **passed**
- Physical-device acceptance: **blocked — no physical Android device is connected**
- Release readiness: **not established**

Acceptance tooling and this report may be committed after the frozen implementation commit. The helper at `scripts/reader-playlikecurl-acceptance-state.ps1` treats that commit as the immutable implementation identity and rejects any intervening production-source change. This avoids invalidating a proven APK merely by pinning evidence or recording later physical-device acceptance.

## Host baseline

Evidence: `.codex-validation/reader-playlikecurl-frozen-host-118a001a/summary.json`

- Declared tests: 2,983
- Parsed tests: 2,983
- Reference tests: 2,926
- Added tests: 60
- Superseded prior tests: 3
- Accepted reference failures: 65
- Current failures: 65
- New failures: 0
- Missing prior tests: 0
- Skipped tests: 0
- Acceptance: `NoNewFailures`

The raw Gradle task exits nonzero because the 65 accepted reference failures remain present. The baseline comparison found no regression.

## Frozen ReaderDev APK

Evidence: `.codex-validation/reader-playlikecurl-frozen-apk-118a001a/receipt.json`

- SHA-256: `89F0740C0CE9419AFF6E08713ECD9C7F1423C673A64E491CC450DC80CBB3882A`
- Bytes: 90,852,012
- Version code: 555
- Version name: `v1.0.11-iota28`

The receipt binds the APK to the frozen implementation commit and the published implementation branch.

## Bounded landscape emulator probe

Evidence: `.codex-validation/reader-playlikecurl-emulator-probe-118a001a-r1/`

- The emulator was rotated to a current display size of 2,960 × 1,848.
- The intentional first persistence write failed, visible Retry recovered it, and the retry became durable.
- Three raster files and the manifest remained byte-for-byte structurally unchanged across force-stop.
- Warm reopen hydrated ordinals 0, 2, and 4 from persistent storage.
- Warm reopen performed no WebView recapture.
- One committed turn and one close/reopen cycle completed.
- Privacy-safe smoke and ownership checks passed.

The probe exposed a zero-width spread gutter that was valid in live capture geometry but rejected by persistent hydration. Commit `118a001a7adb05c8a0b650a9433c45b5fbb6f80c` aligns hydration with the capture contract and the repeated probe passed.

## Full emulator acceptance

Evidence: `.codex-validation/reader-playlikecurl-emulator-acceptance-118a001a-r1/`

- `run-complete.json`: present with `Status=complete`.
- `run-failed.json`: absent.
- Committed turns: 100.
- Completed relocations: 100.
- Rejected relocations: 0.
- Next commits: 53.
- Previous commits: 47.
- Distinct committed ordinals: 20.
- Close/reopen cycles: 10.
- All ten after-close ownership snapshots report zero residents, decoded entries, staged entries, leases, textures, callbacks, and relocations.
- Privacy-safe smoke assertions passed.
- The installed APK identity matched the sealed artifact before and after the run.

## Physical-device gate

Only `emulator-5554` is currently online. Physical acceptance remains mandatory and must use the exact frozen APK above.

Required physical coverage:

- portrait and landscape;
- LTR and RTL;
- Next and Previous commits;
- snap-back;
- rapid sequential turns;
- non-curl rollback;
- 100 committed turns;
- ten close/reopen cycles;
- zero ownership after every close;
- no blank or stale frame;
- no ordinary-turn loading cover.

The physical run must produce a complete frozen-run evidence root plus `physical-manual-attestation.json`. Until both validate, the acceptance helper returns `PhysicalStatus=BlockedPhysicalDevice` and `ReleaseReady=false`.
