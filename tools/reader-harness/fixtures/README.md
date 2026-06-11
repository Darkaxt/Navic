# Reader Harness Fixtures

Place local EPUB/PDF fixtures under `tools/reader-harness/fixtures/local/`.

That directory is intentionally git-ignored so private books do not enter the repository. The first required local fixture is:

```text
tools/reader-harness/fixtures/local/frontmatter.epub
```

It should include the same structure as the failing class of books:

```text
cover -> map/front matter -> author's note -> chapter content
```

The harness serves those files under `/fixtures/local/...` while serving the APK reader assets from `composeApp/src/androidMain/assets/reader`.
