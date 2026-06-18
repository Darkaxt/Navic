# Whispersync Ebook Launch UI Plan

## Goal

Finish the Whispersync UI contract without touching the ebook reader internals. Supported ebook rows should expose an action that lets the user choose a compatible audiobook version and open the ebook with the selected Whispersync sidecar reference attached to the reader route.

## Scope

- Keep Findings out of the user-facing flow.
- Keep the existing Whispersync badge as the match information entry point.
- Add an ebook-row launch action for ready ebook-to-audiobook pairs only.
- Add a modal that lists compatible audiobook candidates with coverage and score.
- Route the ebook reader with the selected sidecar URL and audiobook identity.
- Do not implement sidecar consumption inside the reader in this pass.
- Build a release APK for validation after tests pass.

## Implementation

1. Add policy tests for ready and pending Whispersync launch behavior.
2. Extend `Screen.Reader` with optional Whispersync route metadata.
3. Preserve opposite audiobook identity in `BinderyWhispersyncMatch`.
4. Add a policy helper that builds a reader destination for a selected ebook/audiobook pair.
5. Add the ebook-row action and candidate modal to `BinderyBookScreen`.
6. Run targeted tests and build the release APK.

## Verification

- `BinderyBookVersionPolicyTest` covers ready pair launch metadata and pending pair filtering.
- Release APK build must complete successfully.
