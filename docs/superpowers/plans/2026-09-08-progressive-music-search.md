# Progressive Music Search And Monitoring Plan

Specification: `../specs/2026-09-08-progressive-music-search.md`.
Authorization: already_authorized. Baseline: `fb106c6f7`.

| Stage | Requirements | Work | Verification | Status |
| --- | --- | --- | --- | --- |
| S1 Request ownership and results | Q1, Q3, Q4, Q5, Q6 | Independent library/network providers, per-query reducer, Aurral cancellation, incomplete artist-count repair | Deterministic coroutine/repository/DAO regressions; source wiring review | Passed in the 1,110-test integrated run |
| S2 Presentation and integration | Q1-Q6 | Partial-result UI, truthful progress/errors/counts, retry, shared test gate | Selected search/Aurral tests, integrated music gate, full specification reconciliation | Passed after review corrections in the 1,110-test run |
| S3 Monitoring confirmation | Q7, Q8 | Consume authoritative response, remove mutation replay, guard worker ownership, direct UI confirmation and busy lifecycle | API/queue/UI regressions and integrated music gate | Passed in the final 1,111-test run |

| Requirement | Evidence | Gap | Classification |
| --- | --- | --- | --- |
| Q1 | Independent channel-flow completions; local/remote identity merge; deterministic held-provider tests passed | None | satisfied S1 |
| Q2 | Category-specific content state, reserved-height progress, partial failure/retry, stable result animation key; review and tests passed | None | satisfied S2 |
| Q3 | Query ownership before debounce, cancellation guard before send, raw-query publication guard, same-query retry tests passed | None | satisfied S1 |
| Q4 | Actions/history retained, stable-ID menu selection, offline/configuration request gating; shared gate passed | None | satisfied S2 |
| Q5 | Auth inside cache miss; eight search/auth/cache cancellation regressions passed, including wrapped cancellation | None | satisfied S1 |
| Q6 | Count-only transactional repair; search-only subtitle suppression; six Room and three policy regressions passed | None | satisfied S2 |
| Q7 | Explicit response outcome, read-only observer, per-operation ownership; seven API and eleven pipeline regressions passed, including accepted work surviving caller disposal | None | satisfied S3 |
| Q8 | Direct queue plus retained confirmed-state projection; no success inferred from acceptance; finally clears busy; terminal state hides cleanup spinner; six regular-page and six discovery-page state tests passed | None | satisfied S3 |

Client specification reconciliation: blockers = 0; tracked deferrals = 0.

Review corrections: search selection uses stable album/artist IDs across metadata
replacement. Monitoring preserves the previous confirmed state across a later
pending or failed toggle using the existing repository monitor-state cache.

## Final Verification

Production Android compilation passed (`:composeApp:compileAndroidMain`). The
integrated host gate first passed 1,110 tests. After the accepted-work lifecycle
correction, the final run passed 1,111 tests, zero failures, errors, or skips:

```powershell
.\gradlew.bat :composeApp:testAndroidHostTest '-Pnavic.musicTests=true' --console=plain --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process'
```

Inspected XML results include 10 progressive search state tests, 3 search/UI
wiring tests, 6 Room artist-persistence tests, 3 album-count policy tests, 7 HTTP
monitoring tests, 11 monitoring pipeline tests, and 12 artist monitoring UI-state
tests. Existing Kotlin/Gradle warnings remain; no new compile errors were present.
No release artifact, push, or device installation was produced.

At the initial client verification, live service diagnosis was separately
incomplete: public requests established TLS without returning HTTP responses,
and SSH required interactive Tailscale approval. No server mutation was made by
this task.

On 2026-09-09 the user reported the independent server fix, Aurral commit
`5fca348`: Soulseek had accumulated 821,954 search records whose repeated scans
blocked the Node event loop. Bounded constant-time lookups replaced those scans,
including across reconnects. The user's verification was 35 passing Linux tests,
public health HTTP 200 in 223 ms, and post-restart CPU below 0.1%. This is reported
server evidence, not a result of Navic host tests or a bundled Navic server fix.

## iota68 Release Gate (2026-09-09)

The user subsequently requested publication. Release scope is the completed
music-maintenance and progressive-search specifications, commits `fb106c6f7`
and `6dfdbbdb9`, on current fork master `e1668fad3`. The previously requested
`9c619f10` is an ancestor. Other active worktrees remain untouched.

- Version: `v1.0.11-iota68`, Android version code 595.
- Fresh local test execution, with prior test output removed and build cache
  disabled: 205 suites, 1,111 tests, zero failures, errors, or skips. Command:
  `:composeApp:cleanTestAndroidHostTest :composeApp:testAndroidHostTest
  -Pnavic.musicTests=true --no-build-cache`. Build succeeded in 1m 23s.
- Reader vendor and PlayLikeCurl snapshot verifier self-tests passed.
- Android release-version verification and `git diff --check` passed.
- Publication must pass the tag workflow's music tests, Android release build,
  certificate check, and packaged asset checks. After publication, independently
  verify the downloaded APK identity, version, checksum, and signer.
- No device installation or new physical-device acceptance is claimed. No iOS
  artifact is requested. The server fix is not part of this APK.
