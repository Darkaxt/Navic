# Storage Ownership

Navic uses three persistence mechanisms on Android, each with a distinct contract:

- `Settings` stores user configuration, integration configuration, credentials, and sync scheduling markers. It must not own relational work queues.
- Preferences DataStore stores the serialized playback session because playback restoration is asynchronous, transactional, and updated independently of general settings.
- Room stores relational application data and durable work. `CacheDatabase` owns library, metadata, history, and sync-action tables. `DownloadDatabase` is the sole owner of music and Lida Clip download registries.

## Upgrade Rules

- Database versions only move forward. Every production schema change requires an explicit migration and a fixture-backed migration test.
- Missing migrations fail closed. Destructive upgrade fallback is not permitted for cache, sync-action, playback-history, or download state.
- Download registry rows from legacy `cache.db` are copied into `downloads.db` before cache schema 21 removes the duplicate table. Existing `downloads.db` rows win by song ID.
- Player-state DataStore is created once by Koin. Repositories must not add a second static singleton or double-checked-locking owner.

## Session Rules

Settings may hold sync scheduling state while Room holds queued sync work. Logout handling must clear or namespace both for the outgoing account before credentials are removed. That lifecycle behavior is delivered separately from the schema-ownership migration.
