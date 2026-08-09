# Komikku test snapshot

- Repository: https://github.com/komikku-app/komikku
- Commit: `3b06366fd979e7983cd42e3e092608411b70cff3`
- License: Apache-2.0 (see `LICENSE`)

This test-only snapshot contains only the upstream files exercised by Navic's reader parity contracts. It is pinned so host tests do not depend on an untracked checkout or network access. Kotlin snapshots use a `.kt.txt` resource suffix because Android's host-test Java-resource processing excludes `.kt` files; the test fixture resolves their original upstream paths.
