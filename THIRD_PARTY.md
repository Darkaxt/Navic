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
