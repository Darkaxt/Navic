# Pre-Komikku Reader Reset Vault

Date: 2026-06-13

This vault preserves the reader implementation that existed before the hard reset to a Komikku-derived backbone.

These files are reference material only. They must not be treated as the active reader implementation:

- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt`
- `composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt`
- `composeApp/src/iosMain/kotlin/paige/navic/reader/ReaderWebViewHost.ios.kt`
- `composeApp/src/androidMain/assets/reader/navic-reader.js`
- `composeApp/src/androidMain/assets/reader/navic-reader-helpers.js`

The active reader should be rebuilt around the Komikku root/viewer/overlay contract first, then EPUB/PDF/Foliate/readaloud adapters can be reattached behind that contract.
