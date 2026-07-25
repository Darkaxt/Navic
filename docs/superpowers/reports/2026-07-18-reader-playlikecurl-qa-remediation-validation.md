# Reader PlayLikeCurl QA Remediation Validation

## Status

- Frozen implementation commit: `ee033cc8415b5485acdecd500176f1922cb3f5e1`
- Implementation branch: `fork/reader/playlikecurl-qa-remediation`
- Emulator acceptance: **passed**
- Physical-device acceptance: **blocked — no physical Android device is connected**
- Release readiness: **not established**

Acceptance tooling and this report may be committed after the frozen implementation commit. The helper at `scripts/reader-playlikecurl-acceptance-state.ps1` treats that commit as the immutable implementation identity and rejects any intervening production-source change. This avoids invalidating a proven APK merely by adding validation-only tooling or recording later physical-device evidence.

## Host baseline

Evidence: `.codex-validation/reader-playlikecurl-frozen-host-ee033cc8/summary.json`

- Declared tests: 2,926
- Parsed tests: 2,926
- Accepted reference failures: 65
- Current failures: 65
- New failures: 0
- Missing prior tests: 0
- Skipped tests: 0
- Acceptance: `NoNewFailures`

The raw Gradle task exits nonzero because the 65 accepted reference failures remain present. The baseline comparison found no regression.

## Frozen ReaderDev APK

Evidence: `.codex-validation/reader-playlikecurl-frozen-apk-ee033cc8/receipt.json`

- SHA-256: `D9394211990743CE72844533C9F688B722C004E4BCEBC6DE46F4E3AA0B7D4831`
- Bytes: 90,802,423
- Version code: 552
- Version name: `v1.0.11-iota25`

The receipt binds the APK to the frozen implementation commit and the published implementation branch.

## Bounded emulator probe

Evidence: `.codex-validation/reader-playlikecurl-probe-ee033cc8/`

- 25 committed turns completed.
- One close/reopen cycle completed.
- Frozen APK identity checks passed.
- Privacy-safe smoke and ownership checks passed.

## Full emulator acceptance

Evidence: `.codex-validation/reader-playlikecurl-frozen-emulator-ee033cc8/`

- `run-complete.json`: present with `Status=complete`.
- `run-failed.json`: absent.
- Committed turns: 100.
- Completed relocations: 98.
- Rejected relocations: 2.
- Recovered rejected relocations: 2.
- Next commits: 53.
- Previous commits: 47.
- Distinct committed ordinals: 20.
- Close/reopen cycles: 10.
- All ten after-close ownership snapshots report zero residents, decoded entries, staged entries, leases, textures, callbacks, and relocations.
- Privacy-safe smoke assertions passed.
- The same ReaderDev process successfully consumed single-top direct-reader relaunch intents and remounted a fresh Compose navigation host.

The two rejected relocations were typed acknowledgement timeouts and both recovered. No post-acknowledgement handoff rejection was accepted.

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
