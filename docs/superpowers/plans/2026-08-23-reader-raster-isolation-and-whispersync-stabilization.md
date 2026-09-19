# Reader Raster Isolation And Whispersync Stabilization Staged Plan

**Goal:** Replace shared-foreground passive capture, correct the Whispersync
lifecycle and seek loop, translate Bindery canonical EPUB coordinates into exact
Foliate-owned DOM ranges, make preparation Retry generation-safe, and make curl
presentation visually atomic without changing Foliate or PlayLikeCurl authority.

**Specification:**
`docs/superpowers/specs/2026-08-23-reader-raster-isolation-and-whispersync-stabilization-design.md`

**Active Stage 6 corrective specification:**
`docs/superpowers/specs/2026-09-13-reader-resumable-transition-coordinator-design.md`

**Active Stage 6 corrective implementation plan:**
`docs/superpowers/plans/2026-09-13-reader-resumable-transition-coordinator.md`

**Superseded Android control-plane specification and implementation plan:**

- `docs/superpowers/specs/2026-09-01-reader-hierarchical-presentation-authority-design.md`
  — retain its visual policy and semantic boundaries only.
- `docs/superpowers/plans/2026-09-01-reader-hierarchical-presentation-authority.md`
  — historical implementation record; do not resume it or patch its distributed
  callback control plane.

The active coordinator specification and implementation plan are release-blocking.
The replacement consolidates visual ownership, interaction, deck admission,
lifecycle, deadlines, deferred wakes, and release accounting under one resumable
Android transition owner because the prior hierarchical implementation failed the
decisive consecutive emulator gate.

**Delivery style:** Each stage is a focused TDD checkpoint. Group coherent RED
tests, run one focused RED gate, implement only the stage contract, run one
focused GREEN gate, validate against the specification, then commit and push to
`fork`. Do not launch Gradle after every file edit.

## Stage Exit Rule

After every stage:

1. Re-read the specification sections named by that stage.
2. Map each requirement to code, a test, runtime evidence, or an explicit
   deferral.
3. Classify every gap as:
   - **Blocker:** continuing would build on an invalid authority/state contract,
     hide the defect, violate privacy, or make the next stage's result unreliable.
   - **Deferred:** the requirement is not exercised by the next stage and is safer
     or cheaper to complete when its consumer exists.
4. For every deferral, record the latest stage where it must be completed.
5. Stop the stage if any blocker remains. Do not compensate with fixed delays,
   retries over stale state, native EPUB inference, or a shared-WebView fallback.
6. For every coordinator migration slice, reconcile every row of the active
   specification's legacy-writer replacement map. Record its sole writer,
   replacement fact and command, deadline, proof, terminal outcome, release rule,
   wake source, test evidence, and legacy deletion status. Discovery of an unmapped
   writer is a blocker, not an implicit deferral.
7. Commit and push the verified checkpoint. Keep `.codex-validation` local.

Prefer deferral when implementing a requirement now would create unused
abstractions or speculative integration. Prefer blocking when the next stage
would depend on the missing invariant. No requirement may remain unclassified,
and all deferrals must close before the final release unless the specification
explicitly marks them non-release scope.

## Stage 1 — Whispersync Intent And Event Provenance

### Steps

1. Add RED reducer/policy tests for durable manual Stop, same-spread Start,
   boundary pause, post-turn resume, explicit cue selection, and maintenance
   events that must not seek.
2. Separate playback intent and prepared visible target from engine phase and
   active visual highlight.
3. Add one-shot typed provenance from user navigation/cue selection through the
   causally matching committed destination.
4. Remove generic `visibleTextRange`/`locationChanged` as standalone seek
   authority; unknown provenance fails closed as maintenance.
5. Run focused GREEN tests and preserve exact/progressive cue and page-boundary
   coverage.

### Expected outcome

- Stop resets/prepares the current spread and leaves Start available immediately
  after preparation.
- Stopped intent survives page turns; enabled intent resumes only after manual
  curl settlement and destination commit.
- Internal reader settlements produce zero audio seeks, eliminating the verified
  full-spread restart mechanism.

### Specification validation

Re-read Sections 7, 8, 11.3-11.5, 12, and 14.1. Any path where active highlight
controls eligibility, or a maintenance event can seek, is a blocker. Isolated
backend/exact-cue mismatch investigation may defer to Stage 6 unless it prevents
these lifecycle tests from being evaluated.

### Stage 1 execution ledger

- **Validated:** Playback intent, transport phase, prepared target, active overlay,
  and one-shot causal provenance are independent; focused reducer, bridge,
  WordSync, legacy Readaloud, JavaScript, and bounded Android host gates pass.
- **Deferred to Stage 5:** A bridge overlay-activation event is not final native
  mask/presentation-bundle proof. Native receipt, mask, and bundle admission must
  close this visual-proof requirement before Stage 5 exits.
- **Blockers:** None after the Stage 1 correction and verification gates.

## Stage 2 — Passive Capture Feasibility Prototype

### Steps

1. Add RED contract tests for a raster-only passive port, live-issued manifest,
   passive receipt, strict admission, and absence of semantic callbacks.
2. Implement a minimal passive WebView host and passive Foliate capture runtime
   without routing production preparation through it.
3. Build a synthetic parity harness for portrait, landscape spread, profile
   change, orientation replacement, and stale-generation rejection.
4. Measure capture reliability plus bounded memory, CPU, and lifecycle behavior
   on the supported emulator/tablet class without using protected content.
5. Run the focused GREEN/parity gates and retain only privacy-safe results.

### Expected outcome

A separately hosted passive Foliate session can reproduce the live-issued raster
profile and geometry reliably, return strictly admissible rasters, and remain
incapable of publishing live reader events.

### Specification validation

Re-read Sections 4-6, 13, 14.2, and 15.1. Pagination/profile mismatch, unreliable
off-screen capture, a semantic event path, or unacceptable steady-state resource
cost is a blocker for Stage 3. Fine-grained low-memory tuning may defer to Stage
4 if the prototype remains safe and bounded under normal conditions.

### Stage 2 execution ledger

- **Validated:** ReaderDev now uses distinct live-fixture and passive Foliate
  sessions. The passive module graph exposes only bounded raster capture/result
  operations, accepts only live-issued immutable manifests, returns
  runtime-observed receipts, and has no semantic callback or Android bridge path.
- **Validated:** Strict native admission covers session, publication, destination,
  profile, physical geometry, fingerprints, raster generation, and passive commit
  sequence. Rejected or transferred bitmaps retain once-only release ownership.
- **Validated:** Public synthetic portrait, landscape spread, profile/theme,
  chapter-boundary, live-session replacement, orientation, and stale-generation
  scenarios passed. Ten cold matrices produced 50/50 varying positive rasters and
  10/10 expected stale-generation rejections with no capture failures.
- **Validated:** Pause/resume and renderer-process loss recover without app-process
  death. A clean-emulator Back teardown removed the Activity, passive virtual
  display, and WebView renderer in 3.4 seconds while retaining the cached app
  process. The earlier blank foreground and failed Back probe were traced to stale
  emulator WindowManager/SurfaceFlinger state; after reboot, the unchanged host
  remained visible, focused, interactive, and teardown-safe.
- **Validated:** Steady resource probes settled at zero active CPU, thermal status
  0, approximately 176 MB PSS, and approximately 314 MB RSS after renderer
  recovery. Cold PixelCopy latency remained bounded, with a worst observed maximum
  of 260 ms. Diagnostics and retained results contain only finite counters and
  failure categories; screenshots remain local under `.codex-validation`.
- **Deferred to Stage 4:** Wire platform low-memory/trim callbacks to passive-host
  eviction and re-creation before the production passive path is fully enabled.
  Normal pause, resume, replacement, renderer-loss, and destroy behavior is already
  bounded.
- **Deferred to Stage 6:** Repeat the bounded passive-isolation check on the
  explicitly owned approved tablet class. Stage 2 used only the configured tablet
  emulator and public synthetic content.
- **Not Stage 2 scope:** Production manifest issuance and prewarm/background routing
  remain unreachable until Stage 3; repair routing and final shared-path removal
  remain Stage 4 work.
- **Blockers:** None after clean-emulator foreground, off-screen capture, parity,
  stale-rejection, renderer-loss, lifecycle-release, and resource gates.

## Stage 3 — Isolate Prewarm And Background Capture

### Steps

1. Issue passive manifests from current canonical live commits.
2. Route prewarm and background capture through the passive port.
3. Admit results only in `ReaderPageTurnBundleSource`; keep current-page live
   snapshots separate.
4. Fence session, settings, orientation, profile, and deck changes with capture
   epochs and strict receipt equality.
5. Prove repeated passive prewarm leaves live mutation generation, receipt,
   anchor, overlay, and committed location unchanged.
6. Run focused and consolidated GREEN gates for the migrated path.

### Expected outcome

Ordinary passive preparation no longer touches foreground WebView composition or
requires live-authority restoration. Initial and progressing highlights remain
valid while background raster work runs.

### Specification validation

Re-read Sections 4-6, 11.1-11.2, 12, and 14.2. Any prewarm/background route that
can expose the foreground preview, advance foreground mutation, or promote
passive proof is a blocker. Repair routing, failed-generation Retry, and final
removal of the transitional shared path may defer only to Stage 4.

### Stage 3 execution ledger

- **Validated:** Cold startup establishes one serialized deckless current-live
  presentation authority before passive preparation. The confirmation is fenced by
  Foliate session, visual ordinal, raster generation, attachment, and foreground
  mutation identity, so asynchronous prewarm re-entry cannot invalidate it.
- **Validated:** Passive manifests issue only from already-current canonical live
  commits. Manifest queries cannot navigate, commit a foreground page, expose a
  preview, restore composition, advance foreground mutation, or promote passive
  proof into live authority.
- **Validated:** Ordinary prewarm and idle background acquisition use the isolated
  passive preparation port. Current-page live capture remains a distinct operation,
  while repair remains on the transitional route until Stage 4.
- **Validated:** `ReaderPageTurnBundleSource` is the only passive admission and
  publication boundary. It requires exact manifest/receipt equality across capture
  epoch, live and passive sessions, publication generation, destination and opaque
  target, visual ordinal, render-profile fingerprints, physical geometry, raster
  generation, manifest sequence, and passive commit sequence. Full physical rasters
  are ownership-safely downsampled for 25/50/75/100 percent quality and transferred
  or released exactly once.
- **Validated:** Runtime geometry must equal canonical physical geometry exactly.
  Cancellation remains occupied until abort/drain settles; cancellation dispatch,
  readiness timeout, ordinary commit timeout, renderer loss, and uncertain runtime
  state retire the WebView before completion. Same-geometry owners recreate only a
  retired adapter, and stale renderer callbacks are rejected by runtime identity.
- **Validated:** Live and passive Foliate sessions share one realized render-profile
  pipeline for direction, typography, paragraph normalization, chapter-opening
  margins, theme, paper texture, borders, stains, spread gutter, geometry, and
  verified assets. Publication replacement recreates the passive `foliate-view`;
  failed opens can retry; asset verification admits only the latest request whose
  captured URLs still match the current decoration layers. Public-fixture live and
  passive screenshot hashes match exactly.
- **Validated:** Repeated real passive prewarm and idle background work preserve the
  foreground live receipt, anchor, overlay, committed location, and mutation
  generation. No protected payload logging or persistence was introduced.
- **Gates:** The consolidated JavaScript boundary passed 148/148 plus the relocation
  bridge. The focused nine-class Android boundary passed 381/381; the full Android
  host suite passed 3,622/3,622; `:androidApp:assembleReaderDev` passed. Final
  specification and code-quality reviews approved the corrected Stage 3 diff.
- **Deferred to Stage 4 (latest required stage):** Migrate repair capture, implement
  fresh-generation Retry, wire low-memory passive-host eviction/recreation, and
  remove the final reachable shared-foreground preview route.
- **Deferred to Stage 6:** Bounded emulator/tablet runtime acceptance remains an
  integration gate; Stage 3 claims local/browser/host architecture verification,
  not physical-device acceptance.
- **Blockers:** None after non-mutating manifest, isolated routing, exact admission,
  live-authority preservation, timeout/retirement, publication replacement,
  realized-profile parity, consolidated host, and ReaderDev build gates.

## Stage 4 — Fresh-Generation Recovery And Full Passive Isolation

### Steps

1. Add RED tests proving failed-generation callbacks/decks cannot publish and
   Retry creates a new generation with fresh manifests.
2. Route repair capture through the passive host and recreate an unhealthy
   passive session when required.
3. Make duplicate Retry input coalesce into the active fresh attempt while
   preserving valid cache entries, live location, and playback intent.
4. Remove the shared-foreground preview path from all production passive work and
   narrow foreground ownership to genuine live mutations.
5. Verify Retry reaches Ready without closing the publication and that failure
   keeps the current page rather than exposing black content.
6. Run focused and consolidated GREEN gates.

### Expected outcome

A preparation failure is recoverable in-session through one fresh attempt.
Prewarm, background capture, and repair are fully isolated from the live WebView;
production no longer depends on expose/capture/restore preview transactions.

### Specification validation

Re-read Sections 5, 6, 9, 11, 12, and 14.3. Reusing failed state, accepting a late
old-generation callback, requiring book reopen, or retaining a reachable
production shared-preview fallback is a blocker. Optional cache tuning may defer
to Stage 6 if it does not affect correctness or bounded resource use.

### Stage 4 execution ledger

- **Validated:** Every raster preparation attempt carries a monotonic preparation
  generation through passive manifest requests, capture, admission, persistent
  publication, and renderer deck submission. A failed generation is terminal;
  old capture, publication, state, and accepted renderer callbacks cannot publish
  readiness or ownership. Stale renderer-owned decks are tombstoned locally and
  released from PlayLikeCurl exactly once.
- **Validated:** Retry cancels and retires failed work, coalesces duplicate input,
  allocates one fresh generation, requests fresh manifests from the current live
  commit, and reaches `Ready` only after both current raster and active-deck proof.
  Targeted recovery retains valid persistent cache entries and does not reopen the
  publication, navigate the live WebView, seek audio, or alter the committed
  location, playback intent, prepared audio target, or current page.
- **Validated:** Repair, prewarm, and background raster acquisition now use only
  the isolated passive adapter. An unavailable or unhealthy passive session is
  closed and recreated before a fresh attempt. The foreground preview
  expose/capture/restore route is unreachable from production preparation, while
  genuine current-live surface capture remains a separate live-only operation.
- **Validated:** Low-memory and qualifying trim callbacks cancel passive work,
  close the passive session, release decoded working sets outside the protected
  window, retain valid persistent rasters, and recreate passive state lazily from
  fresh manifests without sacrificing live reader authority.
- **Gates:** The focused five-class Stage 4 Android boundary passed 205/205. The
  consolidated reader JavaScript boundary passed 148/148 plus the relocation
  bridge; the full Android host suite passed 3,625/3,625;
  `:androidApp:assembleReaderDev` passed; and final diff checks passed.
- **Deferred to Stage 6:** Bounded emulator/tablet runtime acceptance remains the
  integrated acceptance gate. Stage 4 performed no device or emulator work.
  Optional cache tuning may also wait until Stage 6 because correctness, valid
  persistent-cache retention, and bounded low-memory eviction are already proven.
- **Not Stage 4 scope:** Curl material, backing, border, clipping, and highlight-mask
  ownership remain Stage 5 work and were not introduced here.
- **Blockers:** None after terminal-generation fencing, exact renderer-deck release,
  fresh Retry proof, passive-only repair, passive-session recreation, low-memory
  eviction, live-state preservation, consolidated host, and ReaderDev build gates.

## Stage 5 — Atomic Curl Material And Highlight Presentation

### Steps

1. Add RED bundle/renderer/host tests requiring matching raster, geometry, page
   material, border, and highlight-mask ownership.
2. Make PlayLikeCurl own paper backing, back-cover material, clipping, and borders
   for every forward, backward, cancelled, and settled curl frame.
3. Route exact/progressive word updates through bounded page-local masks or
   renderer-native rectangles without full-page recapture.
4. Coalesce updates during curl and enforce source/destination ownership at
   settlement.
5. Verify Compose Start/Stop and Retry remain above the native surface and
   hit-testable.
6. Run focused renderer, host, and integration GREEN gates.

### Expected outcome

Curl animation never depends on transparent exposure of the WebView, never shows
black/missing backing or borders, and presents only a current page-owned
highlight without recapturing page pixels per word.

### Specification validation

Re-read Sections 4.3-4.4, 10, 12, and 14.4. Missing backing/borders, mixed bundle
generations, full-page highlight recapture, or source/destination mask leakage is
a blocker. Live in-curl word updates may use the specification's supported
suspension-and-coalescing path instead of forcing a new renderer API.

### Stage 5 execution ledger

- **Validated:** Every production portrait or landscape deck now carries complete,
  immutable generation-owned material. Submission rejects missing material,
  generation mismatch, embedded base-page overlays, missing or mismatched display
  geometry, wrong physical leaf roles, and inconsistent side-slot geometry before
  coordinator or lease ownership. Reusing one generation with different material
  is a conflict rather than an unchanged deck.
- **Validated:** PlayLikeCurl clears uncovered regions with an opaque deck color and
  owns front paper, explicit reverse paper, fixed edge material, clipping, and
  borders. Portrait and landscape turning and incoming leaves select reverse
  material from renderer-controlled curl state, so forward, backward, cancellation,
  settlement, and rest do not depend on transparent WebView exposure or an
  implicit GPU front-face heuristic.
- **Validated:** Exact and progressive updates replace only bounded page-local mask
  textures. Each replacement binds deck generation, destination commit identity,
  live presentation proof, visual ordinal, physical leaf role, anchor generation,
  and boundary generation. The surface and renderer reject stale/cross-leaf
  ownership; curl-time updates remain suspended and latest-value coalesced; no word
  update captures or resubmits a base page raster. This closes the Stage 1 native
  mask/presentation-proof deferral.
- **Validated:** Compose Start/Stop, Retry, preparation UI, semantics, and pointer
  handling remain above the native surface; host updates reassert the same ordering.
- **Gates:** The focused Stage 5 boundary passed 361/361 (214 renderer/module and
  147 Android host). The consolidated renderer boundary passed 214/214; the reader
  JavaScript boundary passed 148/148 plus the relocation bridge; the full Android
  host suite passed 3,630/3,630; `:androidApp:assembleReaderDev` passed; and final
  diff checks passed.
- **Deferred to Stage 6:** Observe exact back-cover/edge appearance and full
  forward, backward, cancelled, and settled animation coherence from the frozen
  APK on the bounded synthetic emulator and approved tablet probes. Stage 5 proves
  complete opaque material ownership and renderer paths but makes no device-level
  visual-parity claim.
- **Not Stage 5 scope:** No emulator, physical-device, signed-release, or broad
  chapter acceptance work was performed.
- **Blockers:** None after atomic material admission, explicit reverse-material
  selection, opaque uncovered backing, role/geometry fencing, page-local mask
  ownership, stale-boundary rejection, chrome ordering, consolidated host, and
  ReaderDev build gates.

## Stage 6 — Integrated Reader Acceptance

### Steps

1. Preserve the completed cue-map, canonical Bindery-to-Foliate mapping, WordSync,
   and prior Stage 6 evidence as historical prerequisites. Do not reinterpret the
   failed distributed authority implementation as accepted runtime evidence.
2. Complete written review of
   `docs/superpowers/specs/2026-09-13-reader-resumable-transition-coordinator-design.md`,
   then write its task-level implementation plan. Carry the specification's complete
   legacy-writer replacement map into that plan as the mandatory migration ledger.
   Close Task383 only after that review/plan handoff; Task384 exclusively owns the
   five implementation slices.
3. **Slice 1 — shadow journal:** introduce typed transition identity, facts, phases,
   outcomes, deterministic clock, and non-reentrant mailbox. Compare privacy-safe
   predicted decisions while legacy code remains the sole consequence writer.
4. **Slice 2 — inactive deck and semantic preparation:** Task384 Task 3 establishes the
   exact-key release ledger and fully tested freeze/inventory/adopt/drain protocol as
   inactive infrastructure. Task 4 adds exact deck/raster leases, typed callback
   adapters, bounded exact-once physical-release accounting, and fail-closed Task 4
   fact/command ports. Task 5 adds the inactive semantic gateway, one-shot receipt
   fences, relocation/retry model, and publication-replacement release-only sink.
   Production remains `Shadow`/`LegacyOnly`; legacy is the sole consequence writer.
5. **Slice 3 — one atomic per-session semantic, material/deck, frame, input,
   inventory, and release-sink cutover:** Task 6 supersedes the prior frame/input-only
   package. It must close the original six preflight gaps, the six defects found in the
   first amendment, and the three remaining rereview defects: deadline scheduling must
   stay Task 7-owned while Task 6 retains one fact-only timer; adopted predecessor kind
   must match its truthful owner; and physical identity must remain collision-safe across
   all 16 sources. The earlier defects were route install without initial publication;
   deadline/lifecycle gaps or dual writers; semantic intents without executable
   `ReaderSemanticRequestHandle` capability and shared/unbounded slots; transition-
   dependent adopted retirement; omitted subordinate physical owners; and unsafe direct
   legacy rollback after destructive drain. Under one opaque token,
   freeze every legacy semantic dispatch, physical pointer admission,
   deck/raster/prewarm/repair/capture/hydration/publication/generation/persistence start,
   renderer/draw/WebView/Foliate/visual-state/pending-owner callback registration, and
   `ReaderForegroundWebViewOwnership` claim/mutation. Inventory every deck/raster/frame
   owner and every inspected bundle-source scheduler/ledger/cache/store, bitmap-source
   presented/live owner plus Handler/renderer/PixelCopy/JavaScript/visual-state/draw
   callback, validation/teardown, foreground passive/restoration/live claim, lifecycle-
   delivery registration, and deadline-registration owner using exact `(session/freeze
   domain, ReaderLegacyInventorySource, source-local opaque token)` identity; aggregate
   counts do not qualify. Equal local IDs from different sources/domains remain distinct.
   Capture a complete pre-drain restoration checkpoint, drain all but at most one
   directly proven predecessor using complete-identity confirmations, and carry the
   selected row's exact identity, truthful owner/resource kind, binding, and provenance
   through seed → collision-free imported key → owner-independent retirement
   registration without a cycle. Neutral has no resource; native-page and curl/native
   material use `Deck`; shell-cover and live/WebView use `FrameHandoff`. Compute the
   narrowed initial lease. One
   `installActivatedSession` commit must switch every route, publish initial owner and
   physical lease, mark `Activated`, and open egress with no intermediate observation.
   Failure before first drain may expose unchanged `Legacy` only after complete
   unfreeze; unfreeze rejection enters frozen `ActivationBlocked`. Failure afterward
   must restore one
   complete legacy snapshot through bounded source confirmations or enter
   `ActivationBlocked`, never partial `Legacy`. Post-install failure stays
   coordinator-owned. Close enters permanent `ReleaseOnly` before cancellation.
6. **Slice 4 — lifecycle, reflow, deadlines, and persistence:** Task 7 owns lifecycle
   normalization/policy, visibility/recreation, viewport/reflow/profile replacement,
   renderer-loss recovery policy, all deadline scheduling/policy, and SavedState wakes.
   Task 6 must suppress every legacy lifecycle consequence overlapping activated routes
   while retaining the existing normalizer as sole fact-only compatibility ingress. For
   each activated attempt it retains exactly one existing command-scoped physical timer,
   binds the exact `ReaderTransitionId` before work, and permits only its exact typed
   expiry/failure fact through FIFO. That timer cannot mutate presentation/input/release,
   retry locally, or rearm beyond the immutable command contract; production coordinator-
   clock scheduling stays inactive. Task 7 atomically transfers every live registration's
   exact transition/current-next-expiry/hard/no-progress snapshot to the coordinator
   clock and deletes retained physical schedulers. Activation,
   supersession, close, and transfer may expose neither zero nor two timer owners.
   `T6-LIFECYCLE-FACT-ONLY`, `T6-NO-DEADLINE-TRANSFER`, and
   `T6-NO-WAKE-TRANSFER` remain explicit carried rows. Remove compatibility lifecycle/
   retry queues only after Task 7 replacement proof.
7. **Slice 5 — deletion:** delete legacy authority replicas, local admission
   currencies, callback continuations, transition-starting `update()` paths,
   duplicate timeout owners, and direct release writers. Reconcile every migration-
   ledger row with a deletion or a fact/command-only adapter.
8. For each slice, use grouped TDD: focused RED, minimal GREEN, callback-before-
   ownership and ownership-before-callback races, one-shot receipt duplication,
   timeout, Retry, supersession, restore, and exact-once release coverage. After
   Slice 5, run the deterministic public-fixture sequence and final consolidated
   JavaScript, common, Android host, renderer, build, and lint gates.
9. After Task384 completes all migration rows and host gates, close Task382 only as
   the preserved failed/superseded callback attempt; exclude its superseded patch
   from the coordinator checkpoint. Commit and push the verified coordinator,
   freeze ReaderDev from that exact commit, and run Task339's consecutive
   configured-EPUB emulator gate: cold landscape, landscape turns, portrait reflow,
   portrait turns, minimize/restore, and one post-restore turn. Automated gates
   cannot substitute for this run.
10. Only after Task 339 passes, complete Task 338's GitHub-managed signed candidate,
    Task 282's production-signed first-two-landscape-pages tablet gate, and Task 283's
    complete Stage 6 accounting and closure. Existing release assets remain
    immutable; Stage 7 remains blocked until closure.

### Production cue-map diagnostic amendment

The cue map is a normal-release diagnostic capability, not a ReaderDev-only
probe, because tablet bug reports must be reproducible from production builds.
It is disabled by default and toggled by a dedicated control beside the existing
eye control when a Whispersync sidecar is available.

1. Preserve each cue's immutable raw sidecar ordinal before filtering or sorting.
   The same sidecar revision must show the same ordinal on every device. Display
   a short privacy-safe sidecar revision digest in diagnostics so reports remain
   unambiguous after sidecar regeneration.
2. Project only cues intersecting the current Foliate-owned visible text range.
   Reuse the production resource normalization, range mapping, and page-local
   overlay/highlight path; do not create a second semantic mapper or recapture a
   full-page raster.
3. At each cue start, draw a tiny custom circled ordinal, visually comparable to
   `℗` with the number replacing `P`. Keep it offset from the text baseline,
   readable in portrait and landscape, non-selectable, and generation-fenced.
4. Preserve the ordinal while styling mapped, prepared/requested, audio-active,
   and rendered-highlight states distinctly. Retain only a bounded ordinal
   transition trail so a report can expose sequences such as forward-two-cues
   then back without storing EPUB text, hrefs, CFIs, payloads, book IDs, or user
   identifiers.
5. While cue-map mode is enabled, pressing over a cue starts a roughly one-second
   progress ring at the touch point. Release, movement beyond touch slop, pointer
   cancellation, chrome interception, or curl start cancels it. Completion seeks
   the exact Foliate-resolved cue; if transport acknowledgement remains pending,
   the ring becomes indeterminate rather than issuing duplicate seeks.
6. Clear and rebuild markers on destination, layout, profile, orientation,
   sidecar-revision, or presentation-generation changes. Passive capture cannot
   publish, activate, or satisfy cue-map state.
7. Add focused coverage for ordinal preservation through cue filtering; visible
   projection without semantic reimplementation; marker placement and cleanup;
   state styling; non-monotonic transition evidence; hold completion and every
   cancellation path; privacy-safe diagnostics; and absence of base-raster
   recapture.
8. The first live acceptance records only two binary findings before audio is
   involved: whether numbered markers render on the current spread, and whether
   their ordinals progress in DOM reading order. Only after those pass may the
   same APK evaluate requested, audio-active, and rendered cue transitions.

### Canonical Bindery-to-Foliate text-mapping amendment

This amendment is a Stage 6 blocker discovered by the production cue map. The
bounded tablet probe exposed a source ordinal after later ordinals on the same
spine component. A privacy-safe replay against the exact EPUB established that
Bindery's source and audio anchors were monotonic, while Navic's bounded locator
search missed a globally unique complete locator and silently admitted the
foreign numeric offset as a DOM offset. The resulting Foliate range landed after
two later cues. No emulator or tablet acceptance may resume until the following
contract is implemented and verified.

1. Define one versioned canonical EPUB text coordinate mode shared by Bindery and
   Foliate. Specify body traversal, excluded non-reading nodes, block separators,
   entity decoding, Unicode normalization, whitespace collapse, punctuation,
   offset unit, and spine scope. Prefer Unicode-scalar or canonical-token offsets;
   never leave UTF-8 byte versus JavaScript UTF-16 interpretation implicit.
2. Bindery aligns and emits cue ranges only in that declared coordinate mode. Each
   referenced spine component carries a SHA-256 digest of its canonical text plus
   the extraction-contract version. A newly generated sidecar must preserve
   source-ordinal and audio chronology and reject decreasing same-spine anchors.
3. Foliate remains the exclusive cue-to-DOM authority. While walking the loaded
   EPUB DOM, it constructs the same canonical text and an in-memory projection
   from each canonical boundary to the originating DOM text node and UTF-16
   boundary. It verifies the version and digest before translating cue ranges into
   browser `Range` objects.
4. A matching canonical digest makes mapping deterministic: resolve `ebookStart`
   and `ebookEnd` through the projection table, validate the resulting canonical
   slice against the cue locator, and return that exact Foliate range. This path
   performs no fuzzy search and never asks native code to infer EPUB semantics.
5. A digest/version/slice mismatch is a visible, terminal mapping outcome for that
   sidecar generation. It cannot publish cue geometry, satisfy highlight proof,
   seek audio, or fall back to treating a Bindery extraction offset as a DOM
   offset. A reachable fresh-sidecar or publication-reload event must exist for
   every deferred outcome.
6. Keep legacy sidecars usable only through a fenced compatibility mapper. When
   locator text is present, search the complete canonical spine, admit a unique
   complete match, and otherwise constrain candidates between already-resolved
   previous and next source ordinals. Ambiguous, missing, or non-monotonic results
   fail visibly. Remove the current raw-offset fallback for foreign coordinates;
   suffix matching alone cannot override complete-locator or neighbor order.
7. Add public synthetic cross-system fixtures covering block boundaries, entities,
   collapsed whitespace, composed/decomposed Unicode, non-ASCII offset units,
   split DOM text nodes, repeated sentences, a unique locator outside the former
   bounded window, and malformed decreasing anchors. The same fixture must produce
   identical canonical text, digest, and range boundaries in Bindery and Foliate.
8. Add focused RED coverage proving the formerly reachable failure: monotonic
   sidecar cues plus a drifted foreign offset must not render a later DOM range;
   the unique complete locator must resolve correctly, repeated ambiguous text
   must use neighbor constraints or fail, and no mismatch may emit cue receipts.
   Run one focused RED gate before production changes and one focused GREEN gate
   after the minimal implementation.
9. Preserve page-local presentation: canonical mapping may update cue geometry and
   highlight masks but may not recapture or resubmit the base page raster. Existing
   destination, Foliate-session, presentation-generation, and sidecar-generation
   fences remain mandatory.
10. Regenerate the configured pair's sidecar with the canonical contract, publish
    it through normal Book Sync selection, and invalidate only cached sidecars whose
    coordinate version or canonical digest is incompatible. Before audio testing,
    the focused emulator must prove matching canonical receipts and monotonic cue
    ordinals on the first spread.
11. **Historical mapping-gate completion only:** The earlier mapping checkpoint and
    signed RC satisfied this amendment's canonical-ordering investigation. This item
    no longer authorizes another RC or tablet run. Every future signed candidate and
    device action must follow the active coordinator ordering: close Task383's
    design/plan handoff, complete Task384's implementation, pass Task339's
    consecutive emulator gate, create and verify Task338's production-signed
    candidate, then run Task282. Task382 remains the preserved failed callback
    attempt and is not an implementation checkpoint.

### Stage 6 current blocker ledger

- **Validated canonical mapping:** Bindery artifact 386 and the Foliate projection
  satisfy the versioned raw-UTF-8 coordinate, digest, boundary, monotonicity,
  fail-closed, and canonical-preflight contract for the configured pair. Production
  iota65 demonstrated canonical cue ordering on the bounded path.
- **Validated post-mapping corrections:** Production iota66 proved that an untagged
  same-session TOC relocation clears the completed exact-turn acknowledgement, so
  the next page action advances. It also proved ordinary Home/restore no longer
  classifies `TRIM_MEMORY_UI_HIDDEN` as memory pressure or closes the publication.
- **Hierarchical-authority blocker discovery (historical):** The historical response
  was to implement
  `docs/superpowers/specs/2026-09-01-reader-hierarchical-presentation-authority-design.md`.
  Bounded testing showed that shell cover, renderer/raster preparation, Compose
  overlays, and input gates could publish locally correct but globally contradictory
  decisions. A returned native cover could display off-screen preparation as
  foreground work, and the hide-before-cover-commit ordering lacked an authoritative
  transaction even though no mixed frame was captured.
- **Rejected endpoint:** The focused
  `pageTurnPreparationPresentationVisible` Boolean prototype documents the visible
  cover-progress regression but is not the durable fix. It must not replace the
  hierarchical arbiter, proof-before-hide shell transactions, or fail-visible
  liveness contract.
- **Historical hierarchical-authority automated checkpoint, not accepted runtime
  architecture:** Android production source audit passed at
  `8d7a61deb61c1e745ad87f46818d5b6785c75cdb`, matching
  `fork/fix/foreground-webview-handoff-ownership`. One common
  `ReaderPresentationDecision` drove layer, input, preparation, diagnostic, and
  transition projections, and host coverage included success, failure, timeout,
  Retry, cancellation/recovery, and restore. Task 355 (`2e9dbc19`) gated native-page
  publication on an Accepted or Idempotent receipt; Task 356 (`ccf9eff1`) retained
  privacy-safe diagnostics. These automated results remain useful regression
  evidence but do not establish reader reliability.
- **Task382 decisive runtime failure:** The final bounded callback continuation passed
  its focused Android host tests and ReaderDev assembly, then failed at cold start.
  Raster preparation and renderer deck loading each completed all six targets, but
  no matching deck-prepared/Ready presentation committed and two separately
  pre-captured forward taps could not leave the cover. The remaining landscape,
  portrait/reflow, and minimize/restore sequence was not attempted because the
  consecutive gate had already failed. No callback-level continuation from the dirty
  Task382 patch may be committed or represented as a successful checkpoint.
- **Active Stage 6 architecture replacement:** Task383 and
  `docs/superpowers/specs/2026-09-13-reader-resumable-transition-coordinator-design.md`
  define the replacement of the distributed Android control plane with one
  per-session activation coordinator and one resumable transition coordinator, typed
  terminal outcomes, command-bound semantic receipts, fresh material allocation, one
  combined owner/input barrier, one deadline owner, bounded opaque wakes, one-shot
  semantic facts, and one exact-once release ledger. Task384 exclusively owns
  implementation after Task383's design/plan handoff. The exhaustive legacy-writer
  map and five migration slices are the authoritative implementation and audit
  boundary.
- **Task384 Slice 1 pure-model checkpoint:** The common transition identity,
  eleven-operation liveness matrix, ten finite wakes, typed facts/commands, bounded
  active-only settlement consumption, authoritative external relocation, and
  centralized exact-key resource disposition are implemented in
  `ReaderResumableTransition.kt`. Grouped TDD recorded feature-missing and
  adversarial RED failures before each correction. MAIN's final focused rerun of
  `ReaderResumableTransitionModelTest` and
  `ReaderResumableTransitionLivenessTest` passed 35 tests with zero failures,
  errors, or skips. Independent specification and code-quality reviews approved the
  resulting Slice 1 contract. This slice is common shadow-model infrastructure only;
  it does not claim Android coordinator wiring or runtime acceptance.
- **Task384 Slice 2 Android shadow-coordinator checkpoint:** The Android main-thread
  FIFO mailbox, typed command ports, bounded content-free shadow predictions, and
  shadow gateway are wired through `ReaderScreen`, `ReaderRoot`, and
  `KomikkuReaderNativeFrameHost`. Journal state and one immutable per-transition hard
  deadline are published before effects; no-progress expiry uses the earlier bound,
  and exact phase/slot fencing rejects cancellation-resistant stale callbacks.
  Grouped TDD captured the feature-missing RED and four deadline-policy RED failures.
  MAIN's forced focused rerun of `ReaderResumableTransitionCoordinatorTest` and
  `ReaderPresentationAuthoritySequenceTest` passed 45 tests with zero failures,
  errors, or skips. Independent specification and code-quality reviews approved the
  corrected seven-file implementation. Shadow mode issues no mutating commands and
  legacy presentation remains the sole writer; this checkpoint does not claim deck
  admission cutover, runtime acceptance, or any later migration slice.
- **Task384 Task 3 preparatory deck-cutover checkpoint:** The exact-key release ledger
  and inactive freeze/inventory/adopt/drain protocol are implemented with bounded
  early-confirmation latching, exact-once release accounting, callback-order
  convergence, release-only late-resource handling, and privacy-safe count/enum
  snapshots. The checkpoint's deck-only protocol is inactive preparation; Task 6
  supersedes its activation shape with the exhaustive one-token, all-resource atomic
  cutover. Production therefore remains explicitly `LegacyOnly`, the Task 2
  coordinator remains Shadow-only, and legacy remains the sole deck writer.
  MAIN's forced focused rerun of `ReaderTransitionReleaseLedgerTest`,
  `ReaderDeckAdmissionCutoverTest`, `ReaderResumableTransitionCoordinatorTest`, and
  `ReaderPresentationAuthoritySequenceTest` passed 65 tests with zero failures,
  errors, or skips. Independent specification and code-quality reviews approved this
  preparatory boundary. This checkpoint does not claim production cutover, physical
  resource migration, close-timeout activation, or runtime acceptance.
- **Task384 Task 4 inactive renderer-adapter checkpoint:** Exact deck and raster leases,
  binding/generation/provenance validation, Android deck-role mapping, typed renderer
  and raster fact emitters, capacity deferral/wake behavior, fresh monotonic recovery
  generations, and kind-dispatched physical release ports are implemented. Task 4
  accepts only exact matching Deck/Raster fact identities, rejects duplicate or fenced
  registration before physical reserve/prepare, removes confirmed physical lease and
  command bookkeeping, and retains only bounded exact tombstones plus fail-closed
  lifecycle/transition-sequence retirement fences. Those fences are valid for this
  inactive transition-owned checkpoint only; amended Task 6 must replace them with
  owner-independent `ReaderResourceRetirementOrder` fences before adopting legacy
  resources. Active registrations take precedence over either fence, so a live old
  lease remains releasable after later terminal releases or lifecycle advance. Raster/deck facts cannot publish
  `Ready`, commit a frame, or grant input; `PreparedFrame` remains mandatory. These
  inactive adapters validate generations already present on a binding but do not
  allocate fresh material generations from semantic proof; amended Task 6 owns that
  missing causal port. Independent specification and code-quality reviews approved
  the corrected boundary.
  MAIN's forced Android host rerun of `ReaderPresentationAuthoritySequenceTest`,
  `ReaderDeckAdmissionCutoverTest`,
  `ReaderPageAdjacentChapterPrefetchIntegrationTest`,
  `ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest`,
  `ReaderPlayLikeCurlFoliateControllerSourceTest`,
  `ReaderResumableTransitionCoordinatorTest`, and
  `ReaderTransitionReleaseLedgerTest` passed 259 tests with zero failures, errors, or
  skips. Production remains `LegacyOnly`; this checkpoint does not claim active
  semantic transition identity, atomic production cutover, Task 5 behavior, emulator
  acceptance, or runtime reliability.
- **Task384 Task 5 inactive semantic-adapter checkpoint:** The shadow gateway records
  page, cover, TOC, search, bookmark, annotation, jump, Retry, and guarded cancel
  intent before the corresponding legacy dispatch. Accepted Foliate receipts enter
  before presentation effects; untagged settlement acknowledgements cannot borrow an
  active identity or satisfy proof. Exact wrong, duplicate, and superseded receipts
  fail closed. Post-settlement Retry uses fresh `RendererRecovery` identity without
  inheriting acknowledgement or gesture authority. Publication replacement retires
  the old coordinator into a release-only sink, and every truthful active, committed,
  or retryable publication identity fences foreign destinations. Untagged destination
  arbitration coalesces an identical accepted binding and supersedes a different
  same-publication binding with a fresh exact relocation; tagged differing facts stay
  stale. Independent specification and code-quality reviews approved this preparatory
  boundary. MAIN's forced consolidated Android host rerun passed 541 unique tests with
  zero failures, errors, or skips: the Task 5 four-suite group passed 239, the Task 1–4
  regression group passed 250, and the affected authority/source/routing group passed
  428. Production remains `Shadow`/`LegacyOnly`; no semantic, deck, frame, input,
  inventory, or release consequence port is active.
- **Task384 Task 6 preflight and amendment review — activation blockers:** The Task 1–5
  interfaces cannot truthfully activate production. The original six gaps remain:
  atomic complete activation, truthful adopted predecessor, complete all-resource
  inventory/drain, command-bound semantic identity, fresh semantic-to-material
  allocation, and combined owner/input publication. Independent review of the first
  amendment found six additional defects that the same Task 6 package must close:
  (1) initial owner/input publication and egress were fallible post-install steps;
  (2) active routes had no explicit one-deadline/lifecycle-no-dual-writer boundary;
  (3) semantic intents carried no executable `ReaderSemanticRequestHandle` capability
  and slots were not causally isolated/bounded/fully retired; (4) adopted-resource retirement depended on
  transition ownership; (5) inventory omitted actual `ReaderPageTurnBundleSource`,
  `ReaderPageTurnBitmapSource`, and `ReaderForegroundWebViewOwnership` subordinate
  owners; and (6) destructive drain could claim direct rollback to
  a partially dismantled legacy session. Latest independent rereview found three more:
  (7) Task 6 incorrectly promoted the coordinator clock despite Task 7 deadline
  ownership; (8) adopted resource kind was incorrectly universalized as `FrameHandoff`;
  and (9) source-local scalar identifiers could collide across the 16 independent sources.
  The prior frame/input-only map and both defective amendments are superseded, not
  completed. Task 5's 541-test evidence waives none of these blockers and no partial
  cutover is permitted.
- **T5-ACTIVE-SOURCE — carried to amended Task 6:** One
  `installActivatedSession` transaction must suppress every legacy semantic and
  overlapping lifecycle consequence; activate coordinator semantic-handle/slot,
  material/deck, frame, retained fact-only timer, successor owner/input, inventory/
  resource, and release-sink routes while the production coordinator clock stays
  inactive; publish the adopted/neutral initial owner and physical lease;
  mark `Activated`; and expose command egress. No installed-but-unpublished snapshot,
  dual-writer interval, or active-session fallback is legal.
- **T5-EXACT-SEED — carried to amended Task 6:** One synchronous freeze token must
  fence all named acquisition/dispatch/registration/mutation paths and identify every
  deck, raster, callback, frame/handoff, bundle-source subordinate, and foreground
  ownership resource by composite `(session/freeze domain, source, source-local opaque
  token)` identity. Aggregate counts do not qualify. Equal local IDs from distinct
  sources/domains never coalesce; only identical complete identities converge. Drain all
  but at most one directly proven predecessor with identity-exact confirmation. Carry
  the selected row's complete identity, exact binding, truthful owner/resource kind,
  session/epoch, and `AdoptedLegacy` provenance through adopted seed → collision-free
  imported key → owner-independent retirement registration → initial decision without a
  fabricated transition, binding-only inference, or seed/key cycle. Neutral has no
  adopted resource; native-page/curl material uses `Deck`; shell-cover/live-WebView uses
  `FrameHandoff`. Session/freeze/local components are positive and inventory is bounded
  by source capacities plus the fixed-point protocol; overflow blocks activation. Raw
  identity/import/retirement values remain memory-only and are barred from logs,
  diagnostics, analytics, screenshots, crash metadata, and persistence; only bounded
  source/state enums, counts, and mismatch categories are diagnostic-safe.
- **T6-LIFECYCLE-FACT-ONLY — carried to Task 7:** At installation Task 6 suppresses all
  legacy lifecycle consequences overlapping activated routes. The existing normalizer
  remains sole ordered ingress through a safety-fact-only compatibility adapter; Task
  6 may fence/cancel unsafe physical work but cannot create visibility/restore/reflow/
  recovery or wake policy. Task 7 replaces and owns that policy.
- **T6-NO-DEADLINE-TRANSFER — carried to Task 7:** Task 7 retains all deadline
  scheduling and policy. Task 6 keeps production coordinator-clock scheduling inactive
  and selects exactly one existing command-scoped physical timer for each activated
  attempt. It binds the exact `ReaderTransitionId` and immutable command bounds before
  work; its only output is the matching typed `DeadlineExpired`/failure fact through the
  FIFO. It cannot mutate presentation/input/release, invoke local Retry, or rearm beyond
  matching progress explicitly allowed by that command. Activation, supersession,
  settlement, rejection, and close serialize timer ownership with no zero/two-owner
  interval. Task 7 atomically transfers each live registration and remaining bounds to
  the coordinator clock, retires the old registration in the same commit, and deletes
  retained physical schedulers; transfer racing expiry/supersession/close is classified
  once at the mailbox boundary.
- **T6-NO-WAKE-TRANSFER — carried to Task 7:** Task 6 does not persist or consume
  SavedState wake demand. Its one 3-second, all-source pre-drain restoration checkpoint
  is in-memory activation safety state only. Task 7 owns wake storage, normalization,
  and resumption.
- **Task384 Task 6 destructive-drain restoration gate:** Before first drain, capture
  opaque restart descriptors for all 16 inventory sources. Pre-drain failure exposes
  unchanged `Legacy` only after complete unfreeze; unfreeze rejection enters frozen
  `ActivationBlocked`. Post-drain failure enters `RestoringLegacy` and
  may expose `Legacy` only after every exact source confirmation and one atomic route/
  lifecycle/deadline/owner/lease restoration commit. Otherwise enter fail-visible
  `ActivationBlocked` with chrome/navigation only and the permanent release sink.
- **Task384 Task 6 mandatory RED groups:** A: atomic route + initial owner/physical
  lease + `Activated` + egress publication, table-driving neutral/no-resource, shell-
  cover/`FrameHandoff`, native-page/`Deck`, curl-native-material/`Deck`, live-WebView/
  `FrameHandoff`, and mismatched-kind rejection without binding-only inference; B: all 16
  exact subordinate inventory sources, late discovery, fixed point, and collision-safe
  races proving equal local IDs from different sources/domains produce distinct drains
  and confirmations while identical complete identities converge; C: exact identity/
  owner/kind/binding/provenance seed/import chain plus cross-source independent release-
  once and owner-independent ordered/bounded retirement; D: executable opaque handles
  plus dedicated 16-handle/8-slot/32-out-of-order bounded causality and every retirement
  path; E: fresh exact material allocation; F: prepared-frame-gated successor owner/
  input commit; G: exactly one retained fact-only timer per activated attempt, exact
  transition binding before work, inactive production coordinator clock, FIFO-only
  expiry, no out-of-contract rearm/local Retry/consequence mutation, no zero/two-owner
  interval through activation/supersession/close, plus lifecycle fact-only compatibility/
  no dual consequence; H: pre-drain cancel versus post-drain `RestoringLegacy`/atomic
  restoration/`ActivationBlocked`; I: post-install no fallback; J: close from all eight
  activation states into permanent `ReleaseOnly`. Task 7 RED additionally proves atomic
  timer transfer/deletion during expiry, supersession, and close races without zero/two
  owners. Every group must begin RED for the named missing behavior and pass before Task
  6 can claim implementation completion.
- **Current Android build/lint/compile evidence:** Task 361 MAIN independently
  verified
  `C:/Users/darka/Documents/Projects/Android/.codex-temp/task361-host-8d7a61de-v1-b0ed9aed5606480abd68ffba7b6c121e`
  with summary SHA-256
  `aa6c1924dda52a2b04de3bed3a239702633128a2f96d4894415de3d2ab5fae51`
  and final SHA-256
  `c7e2f62934e165baaca9c0e01e6029d0272d25f0bb3a32c055135891ee7b204d`:
  `actual=0`, `736.188s`, and all 165 actionable tasks executed, including
  `assembleReaderDev`, `lintReaderDev`, and `compileAndroidMain`. The 91,458,926-byte
  ReaderDev APK has SHA-256
  `bb7bee0185733efdf25d9d7259ecc7023a7012d62e75d08bc2ad1bf635d4a97a`,
  app ID `darkaxt.navic.readerdev`, version code 595, and version name
  `v1.0.11-iota68`; lint reported 0 Fatal, 0 Error, 16 Warning, and 1 Hint.
  Accounting recorded original production `1053 actual / 921 tracked` and identical
  before/after 2,261-source inventories with canonical-index SHA-256
  `9db8cde32138cb46b6b5f88a25b96f784d677cf3001c9c021247f6b2a2270c88`.
  `exportLibraryDefinitions` was explicitly excluded with committed
  acknowledgements; there were no overlays, filters, or stubs. ReaderDev is a
  non-release artifact and remains device-pending.
- **Historical full-test reuse, not a current rerun:** The full Android host/source
  checkpoint at `b9ab62ca4` in
  `C:/Users/darka/Documents/Projects/Android/.codex-temp/task357-main-final-v2-36a85438187043d4bf8945d3e98c6d73`
  (summary SHA-256
  `32d550b4db10c0dedb7e8406166f85f2d30e3c9a5b166b5a59c241956331578e`)
  passed six static gates plus 4,294 tests in 514 suites: 2,529 reader and 1,765
  non-reader tests, with zero failures, errors, or skips, over 2,259 raw app sources
  and original-input proofs. Task 361 did not rerun the Android host tests or the
  six static gates. At `8d7a61de`, `composeApp/src` and `androidApp/src` match that
  checkpoint byte-for-byte by the app-source diff check; only workflow/CI support
  changed. This identity permits reuse of the historical result but is not
  represented as a current test or static-gate rerun.
- **Acceptance state and ordered Android-only gates:** Stage 6 remains open, with no
  accepted coordinator runtime or production-signed acceptance claimed. Task 340 is
  complete. The mandatory order is Task383 written-spec review and implementation
  plan handoff; Task384 coordinator migration, deletion accounting, public-fixture
  and consolidated gates; Task382 closure as the preserved failed/superseded
  attempt; a pushed coordinator checkpoint and frozen ReaderDev; Task339's exact
  configured-EPUB consecutive emulator gate on an explicitly owned emulator; Task338
  production-signed candidate from that accepted source; Task282's bounded
  production-signed physical-tablet acceptance; then Task283 accounting and Stage 6
  closure. ReaderDev and every debug APK are barred from the physical tablet.
  Existing releases and release assets remain immutable. iOS, macOS, and Native are
  excluded from this Android-only scope. Stage 7 remains blocked, and WordSync is not
  deferred.

### Expected outcome

The integrated reader satisfies the authoritative interaction contract without
highlight loss, same-spread Start failure, maintenance-origin audio loops,
poisoned Retry, black curl material, cross-page highlight leakage, non-monotonic cue
placement, or non-terminal accepted transitions. Matching Bindery canonical
coordinates translate deterministically into Foliate-owned DOM ranges. One Android
session activation coordinator performs the complete one-token cutover: collision-safe
16-source composite-identity inventory, truthful predecessor owner/kind adoption, bounded
restoration/blocked failure handling, executable semantic handles and isolated slots,
owner-independent retirement, and one atomic route/initial-owner/physical-lease/
`Activated`/egress publication. One Android transition coordinator owns transition
identity, command-bound receipts, material allocation, presentation commit, combined
successor input leases, and exact-once release accounting. Task 6 retains exactly one
fact-only command timer per active attempt while production coordinator-clock scheduling
is inactive; Task 7 retains lifecycle/reflow/recreation/deadline/wake policy and
atomically transfers all timer scheduling to that clock without a zero/two-owner interval.
Foliate, PlayLikeCurl, raster preparation, and Compose retain their domain authorities.

### Specification validation

Re-read the complete active coordinator specification and account for every
Acceptance Summary item, operation-liveness row, and all 20 legacy-writer/resource-
owner rows, plus the canonical-mapping amendment above. Any separate install/initial
publication/egress step, partial activation, incomplete/count-only/collision-prone
subordinate inventory, coalescing equal local IDs across sources/domains, non-exact drain
or confirmation identity, direct post-drain legacy rollback, missing/partial restoration,
fabricated adopted identity, mismatched predecessor owner/resource kind, binding-only
kind inference, circular seed/key construction, reuse of a source-local token as an
imported key, transition-dependent retirement, unbounded retirement fence, non-
executable semantic source metadata, shared/unbounded/unretired command slot, command
receipt inferred from state, unallocated material binding, split successor owner/input
publication, post-install fallback, zero or dual Task 6 timer owner, Task 6 production
coordinator-clock scheduling, a timer that mutates consequences/retries/rearms outside
its command contract, non-atomic Task 7 timer transfer/deletion, overlapping lifecycle
consequence writer, Task 6 ownership of Task 7 lifecycle/reflow/recreation/deadline/wake
policy, callback-started transition, local admission currency, inherited semantic
receipt, unbounded wake, direct callback release, unmapped path/owner, or missing terminal
outcome is a blocker. The cue map
must expose production mapping behavior rather than bypass it and must remain content-
free in retained evidence. No feature may defer beyond Stage 6 unless the active
specification names it as a non-goal and the bounded acceptance proves it cannot mask
a core failure.

## Stage 7 — Signed Production Delivery

### Steps

1. Verify Task283 closed every coordinator migration row, deleted every superseded
   writer, reconciled all terminal/release/wake outcomes, and recorded the exact
   accepted emulator, signed-candidate, and tablet provenance.
2. Run the final release gates from that exact pushed commit.
3. Prepare the next immutable version and trigger the GitHub-managed signed
   production build.
4. Verify workflow success, APK hash, package/version, and certificate SHA-256
   `ebbe97087182d720ffcb5125b1050e8adccc5db25b23b5b73c9495b9eaa1dae7`.
5. Publish the immutable release and verify the downloaded asset independently.
6. Record acceptance evidence and close the deferral ledger.
7. Save and stop any owned emulator after validation; do not alter unrelated
   emulators or devices.

### Expected outcome

A signed production release contains the verified architecture and behavior, all
release-blocking specification requirements are satisfied, and no local work or
untracked deferral remains.

### Specification validation

Re-read the active coordinator specification, its complete migration map, the Stage 6
closure ledger, and the original stabilization Acceptance Summary before publication.
Any unchecked release-path item blocks publication; there is no post-release
correctness deferral.
