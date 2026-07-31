# Navic v1.0.11-iota29 Release Limitations

## Release decision

Navic iota29 ships the current reasonably stable Android reader and playback state. On 2026-07-31, the release owner explicitly waived further emulator and physical-tablet acceptance and directed that the remaining gaps be documented rather than treated as blockers.

This decision does not convert incomplete acceptance into a pass. It accepts the limitations below for this release while retaining the focused automated checks and production signing gates.

## Verified before release

- Focused Reader PlayLikeCurl parser and runner contract tests pass.
- Captured privacy-safe evidence from the three latest frozen ReaderDev troubleshooting runs passes the corrected correlation parser.
- Adversarial review of replacement-token and QA-fault lineage validation completed with no remaining findings.
- Reader vendor source hashes were regenerated for the reviewed local Foliate paginator/view patches, and the deterministic vendor verifier passes.
- PlayLikeCurl remains the canonical curl renderer; Foliate remains the exact-location authority.
- The iota29 Android version is sequential: version code 556 and version name `v1.0.11-iota29`.
- Publication remains gated on the GitHub-managed persistent release keystore, expected signing-certificate digest, packaged vendor verification, and tag/version consistency checks.

## Incomplete acceptance

- The final iota29 source commit did not complete a new end-to-end ReaderDev emulator acceptance run.
- No iota29 physical-tablet acceptance run was performed.
- The final merged and version-bumped release APK was not exercised through the full portrait/landscape, LTR/RTL, Next/Previous, snap-back, rapid-turn, rollback, 100-turn, and ten-reopen matrix.
- The stopped r4 run is incomplete evidence and must not be represented as a pass.
- Earlier r1/r2/r3 runs ended on QA-tooling false negatives. They provided useful privacy-safe production behavior evidence, but they are not complete iota29 acceptance runs.

## Known non-critical limitations

- The eBook reader remains an evolving integration and may still contain device-, publication-, orientation-, or timing-specific visual defects not covered by focused validation.
- Duplicate-page and long-lived blank-page behavior was fixed and locally observed as corrected, but the final iota29 build did not receive the complete device matrix above.
- Fault-injection and recovery paths are extensively host-tested, but not every replacement/fault ownership sequence has been reproduced on a physical device.
- The repository's broad Android host-test baseline is not a zero-failure baseline; known unrelated source-contract failures remain. Focused tests for this release pass.
- This hyphenated release is Android-only. It does not publish an iOS IPA or update AltStore metadata.

## Follow-up

Development and acceptance work may continue after publication. Any later readiness claim must use new evidence tied to its exact source commit and APK; it must not reuse the incomplete iota29 acceptance roots.
