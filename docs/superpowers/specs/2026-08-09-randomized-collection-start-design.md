# Randomized Collection Start Design

**Date:** 2026-08-09  
**Status:** Approved for implementation  
**Scope:** Android music playback

## Problem

Collection-level playback always starts the source collection at index `0`.
This remains true when shuffle is already enabled and when the dedicated
Shuffle action is used. Media3 then randomizes later transitions, so genres and
artists repeatedly begin with the same song.

The confirmed examples are Classical Crossover genre playback and Lindsey
Stirling artist playback. The same construction pattern is shared by albums,
playlists, Aurral stations, and other collection-level Play actions.

Repeat-all is retained correctly and is not part of the defect. Directly
selecting a particular song must also remain deterministic.

## User Contract

1. With shuffle disabled, collection-level Play preserves source order and
   starts the first source song.
2. With shuffle enabled, collection-level Play generates a fresh shuffled
   playback list before publication and starts its index `0`.
3. The dedicated Shuffle action always generates a fresh shuffled playback
   list, enables shuffle state, and starts its index `0`.
4. A repeated shuffled launch may select any song, including the previous
   first song by chance, but no source song is permanently bound to index `0`.
5. Every source song appears exactly once in the generated list. No song is
   dropped or duplicated by playlist generation.
6. Repeat mode is preserved. In particular, repeat-all remains enabled when it
   was enabled before replacing the queue.
7. Selecting a song row still starts that exact song. This design changes only
   collection-level Play and Shuffle commands.
8. Queue persistence and recovery preserve the generated order; recovery does
   not reshuffle an existing session.

## Design

### Playback Plan

Extend the common playback policy so collection startup produces a typed plan:

- `Canonical` order when shuffle is disabled.
- `Shuffled` order when shuffle is enabled or explicitly requested.
- Start index remains `0` in both cases.
- The shuffled list is generated once per user command and becomes the
  authoritative queue for that playback session.

The pure policy boundary accepts deterministic randomness in tests. Production
uses Kotlin's default random source. This keeps unit tests stable while avoiding
a global seed or persisted pseudo-random generator.

### Player Boundary

Expose one collection-level player operation that receives the source songs and
whether shuffle is forced. It performs queue replacement as one ordered player
transaction:

1. Produce the playback plan off the main dispatcher.
2. Convert the planned songs to Media3 items.
3. Replace the Media3 queue and publish the same planned song order to
   `PlayerUiState`.
4. Preserve repeat mode.
5. Enable shuffle state when requested.
6. Prepare and start planned index `0`.

The UI must not independently sequence `clearQueue`, multiple `addToQueue`
calls, and `playAt(0)` for collection startup. Those separately launched
commands make the queue replacement observable in intermediate states and make
the behavior inconsistent between surfaces.

### Call Sites

Migrate collection-level startup paths, including:

- genre Play and Shuffle;
- artist Play and Shuffle;
- album and playlist heading Play and Shuffle;
- Aurral station and generated-flow playback;
- other bulk Play actions that currently build a multi-song queue and then call
  `playAt(0)`.

Single-song Play, Play Next, Add to Queue, queue-row selection, and explicit
album-song selection remain unchanged.

### Queue and Recovery Semantics

The generated song order is the session queue order. This is intentional: it
allows index `0` to keep its existing meaning while making its song random in
shuffle mode. Persistence stores that exact order, and Media3 recovery restores
it without generating a second shuffle.

Media3 shuffle state remains enabled for controls, notification actions, and
subsequent traversal. The implementation must not disable shuffle merely to
obtain deterministic startup.

## Failure Handling

- Empty collections perform no player mutation.
- Playlist generation and media-item conversion complete before the old queue
  is replaced.
- If no controller is connected, the complete planned replacement is retained
  as one pending command rather than decomposed into pending clear/add/select
  operations.
- A failed source later follows the existing playback-recovery and offline
  fallback policy; this feature does not reorder the queue during recovery.

## Verification

### Common Tests

- Ordered mode preserves source order and starts index `0`.
- Shuffle mode returns a permutation containing every source song exactly once.
- Deterministic random input proves that source index `0` is not hard-coded as
  the shuffled first song.
- Empty and one-song collections are handled safely.
- Repeat mode is not part of or modified by playlist generation.

### Android Host Tests

- Collection replacement publishes identical Media3 and `PlayerUiState` order.
- Existing shuffle state causes ordinary collection Play to use shuffled order.
- Explicit Shuffle forces shuffled order and enabled shuffle state.
- Direct song selection remains deterministic.
- Controller-unavailable startup retains one complete pending replacement.

### Regression Scope

Run focused playback policy and Android player tests, then the broad Android
host suite. The known 68 baseline failures must remain unchanged; no new failure
is accepted.

## Deployment

Ship as one playback behavior change without a feature flag. Validate the
signed Android artifact on an attached device by launching the same genre and
artist several times, confirming that the initial song is drawn from the newly
generated queue and that repeat-all remains active.

Rollback is a forward release that restores canonical collection generation;
persisted shuffled queues remain valid ordinary queues and require no data
migration.

## Out of Scope

- Changing shuffle probability or preventing the same first song from being
  selected on two consecutive launches.
- Reordering an already-playing queue when the shuffle toggle changes.
- Modifying repeat-mode behavior.
- iOS playback support.
