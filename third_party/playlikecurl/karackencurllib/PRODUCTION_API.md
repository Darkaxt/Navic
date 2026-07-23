# Production Bitmap API

Production API version `2` accepts client-prepared bitmap page decks through
`PageSurfaceView` and exposes typed asynchronous ownership snapshots.

## Bitmap Page Decks

Clients submit a complete portrait or landscape interaction window. Each
accepted deck has a generation ID, an active page, and the neighboring page
images required by the current turn direction.

## Bitmap Lease

After a deck is accepted, its bitmaps must remain immutable and available until
the matching deck-release callback. The renderer does not decode files or own
the client's bitmap cache.

The required formats are opaque ARGB_8888 base pages and optional
premultiplied ARGB_8888 overlays with alpha. Overlays use the same page mesh
and texture coordinates as their base page.

## Ownership Snapshots

`PageSurfaceView.requestOwnershipSnapshot(PageSurfaceOwnershipResult.Callback)`
reports ownership asynchronously on the main thread. Every accepted request is
completed exactly once with a typed status:

- `AVAILABLE` includes an immutable `PageSurfaceOwnershipSnapshot` sampled
  across a stable main-thread ownership epoch and the GL texture state.
- `SURFACE_UNAVAILABLE` means no live GL sample can be obtained while the
  surface is detached or unavailable.
- `QUEUE_REJECTED` means GL execution or its main-thread completion could not be
  admitted.
- `CALLBACK_CAPACITY` means the fixed request registry is full; the callback is
  notified immediately and is not retained.

The limits carried by the snapshot are renderer ownership limits, not
best-effort diagnostic thresholds. During disposal, accepted live requests are
transferred into the bounded terminal callback owner and receive the final
terminal snapshot. A terminal snapshot can report retained resources when the
disposal result also reports an ownership-retained failure.

## Lifecycle

Clients attach and detach the surface explicitly, handle structured capability
and rendering callbacks, and release rejected or obsolete decks. After OpenGL
context recreation, retained decks are revalidated against the current texture
limits; only decks that can no longer be uploaded are released.

On the main thread, `PageSurfaceView.isSettlementRunning()` is the authoritative
placement query immediately before deck submission. An idle submission enters
the active slot; a submission during settlement enters the pending slot.

## Programmatic Turns

`PageSurfaceView.turn(PageChange)` starts a prepared `PREVIOUS` or `NEXT` turn
using the same deformation and settlement path as a committed edge drag. It
returns `false` without changing pages when the surface cannot accept the turn
or the requested direction is outside the current deck boundary.
