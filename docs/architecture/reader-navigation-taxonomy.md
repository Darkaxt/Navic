# Reader navigation taxonomy

Navic's reader supports two public navigation modes:

| Public mode | Publication behavior | Komikku-derived implementation detail |
| --- | --- | --- |
| `Paged` | Discrete previous/next page navigation | Horizontal or vertical pagination |
| `Scrolled` | Continuous viewport movement | Scroll with optional visual gaps |

The public contract is `ReaderNavigationMode`. It intentionally does not expose manga terms such as
"webtoon" or treat vertical pagination as a separate publication mode. Vertical pagination remains a
viewer lifecycle detail because switching between horizontal and vertical rendering must recreate the
native frame.

Komikku-derived tap regions remain an input adapter. In paged mode they produce page-turn actions. In
scrolled mode, previous/left and next/right produce viewport-up and viewport-down actions. EPUB, PDF,
and image publication settings are normalized to this boundary before viewer selection.
