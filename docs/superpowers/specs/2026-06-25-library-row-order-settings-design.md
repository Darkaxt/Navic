# Library Row Order Settings Design

## Goal

Add a dedicated settings page that lets the user reorder and show/hide Library rows, including dynamic Aurral rows, without dropping future rows from older preference snapshots.

## Scope

- Add `Settings > Appearance > Library > Library row order`.
- Show every managed row in effective order.
- Each row has a drag handle and an eye/eye-off visibility action.
- Persist row order and hidden row IDs.
- Render the Library from the effective persisted row order.
- Hidden rows do not render; Quick Picks hidden also avoids Quick Picks refresh.
- Missing known rows append at the end and are visible by default.
- Obsolete saved row IDs are ignored.

## Rows

- Quick Picks
- Most Played
- Newest Albums
- Starred Albums
- Recent Albums
- Stations
- Playlists
- Artists
- Genres
- Aurral Recently Added
- Aurral Recent Releases
- Aurral Recommended
- Aurral Based on Library
- Aurral Global Top
- Aurral Genre Rows
- Aurral Tags

## Architecture

`LibraryRowOrderPolicy` owns stable row IDs, default order, preference serialization, effective ordering, visibility, and Aurral-kind mapping. `PreferenceManager` stores two string preferences: row order and hidden row IDs. The settings screen uses the existing draggable list utilities. Library rendering calls the policy once and emits sections by row ID.

## Validation

Unit tests cover default order, custom order, hidden rows, missing/new rows appended visible, obsolete IDs ignored, and Aurral row mapping. A source-level guard ensures Quick Picks refresh checks the row visibility policy.
