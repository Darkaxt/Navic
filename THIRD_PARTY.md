# Third-Party Notices

Navic is distributed under the GNU General Public License version 3. The full
project license is in [`LICENSE`](LICENSE). Navic is not distributed under the
GNU Affero General Public License.

The generated in-app Acknowledgements screen lists normal Gradle dependencies.
The copied or adapted reader components below are added to the same screen from
the custom records in `composeApp/aboutlibraries`.

## Anx Reader

- Upstream: <https://github.com/Anxcye/anx-reader>
- Pinned source: <https://github.com/Anxcye/anx-reader/commit/107f4fa74db0e7247c846c49d6211df3edf9887c>
- License: MIT
- Copyright: Copyright (c) 2025 Anxcye
- Full notice: [`third_party/licenses/Anx-Reader-MIT.txt`](third_party/licenses/Anx-Reader-MIT.txt)

Navic's reader integration, bridge protocol, and reader interaction behavior
were adapted with reference to this pinned Anx Reader revision. The MIT notice
is preserved for those adapted portions.

## foliate-js 1.0.1

- Upstream: <https://github.com/johnfactotum/foliate-js>
- Pinned source: <https://github.com/johnfactotum/foliate-js/commit/f52d42c6127d0ad981a2c67634113541b17ae01e>
- License: MIT
- Copyright: Copyright (c) 2022 John Factotum
- Full notice: [`third_party/licenses/foliate-js-MIT.txt`](third_party/licenses/foliate-js-MIT.txt)

Navic ships a locally modified foliate-js reader under
`composeApp/src/androidMain/assets/reader/vendor/foliate-js`. Exact upstream
provenance and current file hashes are recorded in that vendor directory's
`manifest.json`.

## PDF.js 3.11.174

- Upstream: <https://github.com/mozilla/pdf.js>
- Pinned release source: <https://github.com/mozilla/pdf.js/commit/ce87167432819f85df49b6b16c7a78556e9a4ee0>
- Published package: `pdfjs-dist@3.11.174`
- License: Apache License 2.0
- Copyright: Copyright 2023 Mozilla Foundation
- Full license: [`third_party/licenses/PDF.js-Apache-2.0.txt`](third_party/licenses/PDF.js-Apache-2.0.txt)

Navic ships `pdf.js` and `pdf.worker.js` from this package inside the foliate-js
vendor tree. Their exact package provenance and current hashes are recorded in
the reader vendor manifest.

## PlayLikeCurl

- Original project: <https://github.com/karankalsi/PlayLikeCurl>
- Maintained fork: <https://github.com/Darkaxt/PlayLikeCurl>
- Pinned release: <https://github.com/Darkaxt/PlayLikeCurl/releases/tag/1.2.0>
- Pinned source: <https://github.com/Darkaxt/PlayLikeCurl/commit/116ea75f86cff26199ab3e7180285e5b728913fa>
- License: MIT
- Full notice: [`third_party/playlikecurl/LICENSE.txt`](third_party/playlikecurl/LICENSE.txt)
- Provenance: [`third_party/playlikecurl/provenance.json`](third_party/playlikecurl/provenance.json)
- Release identity: production API 2; source commit 116ea75f86cff26199ab3e7180285e5b728913fa; release AAR SHA-256 eeead972edb3e7727399e05f380c03bf14118c16d3b8ac25679df10910e0721c

Navic vendors the fork's `karackencurllib` Android library as a mechanically
generated, digest-locked source snapshot. The imported renderer keeps its own
namespace and production bitmap-deck API; Navic-specific reader behavior lives
outside the imported tree. PlayLikeCurl was originally created by Karan Kalsi,
and the maintained GLES2/library fork is published by Darkaxt.
