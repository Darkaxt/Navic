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

Live service diagnosis is separately incomplete. Public requests established TLS
but returned no HTTP response within diagnostic windows. The supplied Proxmox
Tailscale host was online, but SSH requested interactive Tailscale approval before
running the container command. No live Docker status/logs were obtained and no
server mutation was made. These host tests do not prove that the live Aurral
service delay has been repaired.
