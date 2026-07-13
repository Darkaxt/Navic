# Reader Vendor Asset Governance

Navic ships Foliate and PDF.js as Android WebView assets because the reader must work offline. These files parse untrusted publications and execute with reader DOM access, so every shipped byte must remain attributable and reviewable.

## Source Of Truth

`composeApp/src/androidMain/assets/reader/vendor/manifest.json` records:

- exact upstream version, Git commit, package URL, package integrity, and license for each component;
- the relationship between upstream bytes and Navic's local reader patches;
- a component owner and SHA-256 hash for every file under `reader/vendor`.

The manifest is packaged in the APK. `scripts/verify-reader-vendor-assets.ps1` rejects missing, extra, renamed, or modified source files and can apply the same checks to the final APK.

The 2026-07-13 baseline review found PDF.js `3.11.174` in the affected range for `GHSA-wgrm-67xf-hhpq` / `CVE-2024-4367`. Navic's only `getDocument` call explicitly sets `isEvalSupported: false`, which is the upstream advisory's documented workaround. `FoliatePdfAnxParityTest` prevents that mitigation from being removed while this PDF.js line remains shipped.

## Review And Update Procedure

1. Create an isolated worktree from current public `master`. Do not update vendored reader assets in a page-turn or feature worktree.
2. Review upstream release notes, the upstream Git diff, GitHub Security Advisories, and the NVD entries for both Foliate and PDF.js. Record the review date and source links in the implementation plan or release evidence.
3. Select immutable versions. Retrieve packages only from the manifest's HTTPS npm registry URLs and confirm npm's `dist.integrity` and `gitHead` metadata before copying files.
4. Diff the old and new upstream commits. Treat parser, script-loading, iframe/srcdoc, URL resolution, worker, font, image, archive, and DOM changes as security-sensitive.
5. Reapply each Navic-local Foliate change deliberately. Use the import commit and `git log -- reader/vendor/foliate-js` to separate Navic patches from upstream code; do not overwrite the directory wholesale.
6. Update component metadata in `manifest.json`, then run:

   ```powershell
   pwsh -NoProfile -File scripts/update-reader-vendor-manifest.ps1
   pwsh -NoProfile -File scripts/test-reader-vendor-assets-verifier.ps1
   ```

7. Run reader host tests and real-publication smoke tests for EPUB, PDF, fixed layout, search, annotations, internal/external links, and page navigation.
8. Build the Android APK and verify the packaged bytes:

   ```powershell
   pwsh -NoProfile -File scripts/verify-reader-vendor-assets.ps1 -ApkPath <apk-path>
   ```

9. Review the manifest diff. A version/commit change without corresponding reviewed file changes, or file changes without a provenance/local-patch explanation, blocks release.

GitHub Actions runs the verifier self-test before every Android build and verifies the APK before upload. The check is deterministic and does not contact upstream services during builds.
