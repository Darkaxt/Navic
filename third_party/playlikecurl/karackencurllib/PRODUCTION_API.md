# Production Bitmap API

Production API version `1` accepts client-prepared bitmap page decks through
`PageSurfaceView`.

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

## Lifecycle

Clients attach and detach the surface explicitly, handle structured capability
and rendering callbacks, and release rejected or obsolete decks. After OpenGL
context recreation, retained decks are revalidated against the current texture
limits; only decks that can no longer be uploaded are released.
