# Bindery External Fetch Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close B18 by allowing Navic to fetch only approved AudioBookBay provider pages over HTTPS, rejecting local/private DNS results and redirects, and extracting cover candidates with a real HTML parser.

**Architecture:** Replace the generic external-text call with a purpose-bearing request and a common policy/response layer. Android owns the actual provider-page transport: Ktor's OkHttp engine uses a DNS implementation that validates and returns the same resolved address list used for the connection, while redirects are disabled in both layers. Common code parses HTML through Ksoup and only returns HTTPS cover URLs on approved provider image hosts; iOS receives a compile-only unavailable transport because Navic has no iOS product support.

**Tech Stack:** Kotlin Multiplatform 2.4, Ktor 3.5.1, OkHttp, Fleeksoft Ksoup, kotlin.test, Gradle Android host tests.

---

### Task 1: Encode the external-request security contract

**Files:**
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextPolicy.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyExternalTextPolicyTest.kt`

- [x] **Step 1: Write the failing request-policy and address matrix**

Add tests which require `ExternalTextPurpose.AudioBookBayProviderCover`, accept only `https://audiobookbay.lu` on effective port 443, and reject HTTP, unsupported schemes, credentials, custom ports, localhost, RFC1918 IPv4 literals, IPv6 loopback/link-local literals, off-allowlist hosts, deceptive suffixes, and fragments. Add byte-address tests for public IPv4/IPv6 controls plus loopback, private, link-local, carrier-grade NAT, unspecified, multicast, IPv4-mapped private IPv6, and unique-local IPv6.

- [x] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyExternalTextPolicyTest"`

Expected: compilation failure because the purpose, request policy, and address policy do not exist.

- [x] **Step 3: Implement the minimal common policy**

Create an explicit purpose enum and normalized request value. Parse with Ktor `Url`; require HTTPS, effective port 443, no user info or fragment, and an exact purpose-specific hostname. Add a byte-oriented public-address predicate used by the Android DNS boundary, including IPv4-mapped IPv6 handling.

- [x] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyExternalTextPolicyTest"`

Expected: all policy matrix tests pass.

- [x] **Step 5: Commit the contract**

Run:

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextPolicy.kt composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyExternalTextPolicyTest.kt
git commit -m "test(bindery): define external fetch security boundary"
```

### Task 2: Add the Android DNS-pinned transport

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt`
- Create: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextClient.kt`
- Create: `composeApp/src/androidMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextTransport.android.kt`
- Create: `composeApp/src/iosMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextTransport.ios.kt`
- Create: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyExternalTextClientTest.kt`
- Create: `composeApp/src/androidHostTest/kotlin/paige/navic/domain/repositories/BinderyExternalTextDnsTest.kt`

- [x] **Step 1: Write failing redirect, approved-source, and DNS tests**

Use a fake common transport to prove an approved page returns its body, a 3xx response is rejected without a second request, and policy rejection happens before transport. Test the Android DNS adapter with fake DNS answers: exact approved host plus public addresses succeeds; changed hosts, empty answers, RFC1918, IPv4 loopback/link-local, IPv6 loopback/link-local/unique-local, and mixed public/private answers fail.

- [x] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyExternalTextClientTest" --tests "paige.navic.domain.repositories.BinderyExternalTextDnsTest"`

Expected: compilation failure because the secure client, platform transport, and DNS adapter do not exist.

- [x] **Step 3: Implement the secure client and Android transport**

Change `BinderyApiClient.fetchExternalText` to require `ExternalTextPurpose`. Route it through a common secure response policy. On Android, use a dedicated Ktor OkHttp client with Ktor and OkHttp redirects disabled; install the existing provider-fetch timeout values without adding new cancellation policy. Configure OkHttp with a DNS adapter that delegates once, rejects any non-public answer, and returns that same validated list to OkHttp. Add an iOS compile-only transport that reports the feature unavailable.

- [x] **Step 4: Run the focused tests and verify GREEN**

Run: `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyExternalTextClientTest" --tests "paige.navic.domain.repositories.BinderyExternalTextDnsTest"`

Expected: approved-source and DNS controls pass; redirects produce one request and an exception.

- [x] **Step 5: Commit the transport**

Run:

```powershell
git add composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyApiClient.kt composeApp/src/androidMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextTransport.android.kt composeApp/src/iosMain/kotlin/paige/navic/domain/repositories/BinderyExternalTextTransport.ios.kt composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyExternalTextClientTest.kt composeApp/src/androidHostTest/kotlin/paige/navic/domain/repositories/BinderyExternalTextDnsTest.kt
git commit -m "fix(bindery): constrain external provider fetches"
```

### Task 3: Replace regex scraping with allowlisted DOM extraction

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyUrlPolicy.kt`
- Modify: `composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryProviderCoverTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTestFixtures.kt`

- [ ] **Step 1: Add failing parser and integration cases**

Require malformed but recoverable HTML, swapped/case-varied metadata attributes, encoded attribute values, and fallback `<img>` extraction to work. Require internal/off-allowlist cover image URLs and unsupported provider source origins to produce no fetched/returned cover. Assert the repository passes `AudioBookBayProviderCover` explicitly.

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyRepositoryProviderCoverTest"`

Expected: parser-policy cases fail against regex extraction and the old purpose-free fake API.

- [ ] **Step 3: Add Ksoup and implement DOM extraction**

Add `com.fleeksoft.ksoup:ksoup:0.2.6` to `commonMain`. Parse provider HTML into a DOM, collect `og:image`/`twitter:image` metadata and image `src` attributes in priority order, resolve relative URLs, upgrade the known `image.bayimg.com` HTTP form, and retain only HTTPS URLs on exact approved cover-image hosts. Pass the explicit fetch purpose from `BinderyRepository` and update its fake client.

- [ ] **Step 4: Run focused and owning tests**

Run:

```powershell
./gradlew.bat :composeApp:testAndroidHostTest --tests "paige.navic.domain.repositories.BinderyRepositoryProviderCoverTest" --tests "paige.navic.domain.repositories.BinderyExternalText*"
./gradlew.bat :composeApp:testAndroidHostTest
```

Expected: all Android host tests pass.

- [ ] **Step 5: Commit parser integration**

Run:

```powershell
git add gradle/libs.versions.toml composeApp/build.gradle.kts composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyUrlPolicy.kt composeApp/src/commonMain/kotlin/paige/navic/domain/repositories/BinderyRepository.kt composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryProviderCoverTest.kt composeApp/src/commonTest/kotlin/paige/navic/domain/repositories/BinderyRepositoryTestFixtures.kt
git commit -m "fix(bindery): parse approved provider artwork safely"
```

### Task 4: Validate and publish `v1.0.11-kappa2`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `docs/superpowers/plans/2026-07-12-qa-analysis.md`
- Modify: `docs/superpowers/plans/2026-07-13-qa-remediation-deployment-roadmap.md`
- Modify: `docs/superpowers/plans/2026-07-13-bindery-external-fetch-security-implementation.md`

- [ ] **Step 1: Set release metadata**

Set `versionCode` to `534` and `versionName` to `v1.0.11-kappa2`; update B18 and this plan with implementation evidence.

- [ ] **Step 2: Run release gates**

Run:

```powershell
./gradlew.bat :composeApp:testAndroidHostTest :androidApp:assembleDebug
./scripts/verify-android-release-version.ps1 -ExpectedVersionName v1.0.11-kappa2
git diff --check
```

Expected: tests and debug assembly pass, version verification succeeds, and `git diff --check` has no output.

- [ ] **Step 3: Commit, rebase, and publish**

Commit documentation/release metadata, rebase onto current `fork/master` if needed, push the branch to `master`, create annotated tag `v1.0.11-kappa2`, and push the tag. Do not touch reader/page-turn worktrees.

- [ ] **Step 4: Verify the public release**

Watch the GitHub Actions tag workflow through completion. Verify the uploaded `Navic.apk` SHA-256, APK signature certificate, embedded version metadata, and Android-only workflow result. Install with `adb install -r`, launch, and check the process/logcat for fatal startup failures.

- [ ] **Step 5: Record evidence and clean the isolated worktree**

Commit and push final release evidence to public `master`. Confirm the branch is fully represented by `fork/master`/the release tag, remove the worktree, delete the local feature branch, and verify no B18 worktree residue remains.
