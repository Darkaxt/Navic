# Reader Page Shell Prototype

This prototype is the visual acceptance gate for `docs/superpowers/specs/2026-07-08-reader-page-shell-geometry-design.md`.

It exists to prevent the same failure mode as the earlier APK attempts: drawing nice overlays while the actual content, cover, gutter, and texture layers still use different geometry.

## Modes

- `spread`: two-page landscape layout with a real center gutter.
- `portrait`: single right-side page with a left gutter hint, not a notepad.
- `cover`: foreground cover, diffuse cover backdrop, and a simple tinted back-cover plane.

## Capture

Use Helium Chrome with controls hidden:

```powershell
New-Item -ItemType Directory -Force captures\reader-page-shell-geometry
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=2960,1848 --screenshot=captures\reader-page-shell-geometry\prototype-spread.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=spread&capture=1"
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=1848,2960 --screenshot=captures\reader-page-shell-geometry\prototype-portrait.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=portrait&capture=1"
& "C:\Users\darka\AppData\Local\imput\Helium\Application\chrome.exe" --headless=new --disable-gpu --window-size=2960,1848 --screenshot=captures\reader-page-shell-geometry\prototype-cover.png "file:///$PWD/docs/superpowers/prototypes/reader-page-shell/index.html?mode=cover&capture=1"
```

## Acceptance

- Spread page content is centered inside each visual page, not the raw viewport half.
- Portrait mode reads as the right-side page with a left gutter hint.
- Cover mode keeps the foreground cover fully visible.
- Cover mode fills black areas with a diffuse backdrop.
- Cover mode includes a simple tinted back-cover plane with soft use marks.
- Paper texture, page edges, stains, and cover backdrop can be independently disabled.
- Edge wear reads as worn paper edge, not a broad coffee-stain border.
- Edge intensity and edge width are independently tunable.

Generated captures are evidence only and should stay out of git unless a reviewer explicitly asks for tracked images.
