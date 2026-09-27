# BrightFetch V3 — market-led visual redesign

This concept is independent of the existing application implementation. Only design artifacts were read or written; application source was not used as a basis.

## Scope

Three editable SVG boards have been imported into the existing Figma file. Text and vector objects remain editable; photos are embedded raster fills. These boards are visual screen designs, not native auto-layout components or a wired interactive prototype. The SVG workflow was used while the official connector was unavailable; connector access was restored on 2026-09-25 and used to finish delivery and verify the file.

## Design decisions

- Put capture first: one primary paste action, with browser discovery secondary.
- Keep source context through detection and quality selection.
- Replace abstract media placeholders with representative photographs.
- Use a consistent white/ink/violet system, spacious layouts and three primary destinations.
- Show meaningful queue progress, and provide explicit recovery for expired links and playback errors.
- Treat Library as a visual collection, not a second download-task list.

## Research

Google Play listings and the InShot screenshot gallery were reviewed. Patterns are synthesized, not copied pixel-for-pixel.

- [Google Play category search](https://play.google.com/store/search?q=video+downloader&c=apps&hl=en)
- [InShot Video Downloader](https://play.google.com/store/apps/details?id=video.downloader.videodownloader)
- [1DM](https://play.google.com/store/apps/details?id=idm.internet.download.manager)
- [Private Browser & Downloader](https://play.google.com/store/apps/details?id=instagram.story.reels.photo.videodownloader.saver)

## Representative media

These are still photographs used to illustrate sample downloaded videos, not actual playable video files or assertions about a particular download service.

- Coast: [Justin Shen / Unsplash](https://unsplash.com/photos/an-aerial-view-of-the-ocean-and-a-road-HMEcNTp4ap4)
- Mountain: [Mattia Poli / Unsplash](https://unsplash.com/photos/a-mountain-lake-surrounded-by-snow-covered-mountains-XPVVtqCQWzY)
- City: [Derch / Unsplash](https://unsplash.com/es/fotos/vehiculos-en-la-calzada-entre-edificios-iluminados-por-la-noche-4CMq1GxDSPA)
- Coffee: [Unsplash](https://unsplash.com/photos/white-cup-filled-with-latte-with-art-iJMHMXT5-5E)

## Files

- `ui-kit.cjs`: shared visual helper functions for generating the concept boards; not application code.
- `board1.cjs`, `board2.cjs`, `board3.cjs`: reproducible board generators.
- `v3-01-capture.svg/png`, `v3-02-library.svg/png`, `v3-03-player.svg/png`: editable imports and rendered previews.

Figma import IDs and verification are recorded in `figma-delivery.json`.

## Current delivery status

All 15 screen/state compositions are now in Figma, each board positioned at (3000, 0) on its respective page:

- [Capture / Browser / Detection / Quality](https://www.figma.com/design/CLCW7nUO2zZhSbDfsxXRI8/BrightFetch?node-id=16-2): page 0:1, node 16:2, 4 compositions, 89 editable text nodes.
- [Downloads / Library / Search / Empty states](https://www.figma.com/design/CLCW7nUO2zZhSbDfsxXRI8/BrightFetch?node-id=19-281): page 2:2, node 19:281, 6 compositions, 164 editable text nodes.
- [Player / Settings / Recovery](https://www.figma.com/design/CLCW7nUO2zZhSbDfsxXRI8/BrightFetch?node-id=19-2): page 2:3, node 19:2, 5 compositions, 96 editable text nodes.

The secondary color improvement (#70788B → #626D82) has also been applied to the first Figma board. Prior designs were preserved. Screenshots exported directly from Figma are saved as `figma-v3-01-verified.png`, `figma-v3-02-verified.png`, and `figma-v3-03-verified.png`; these reflect the imported file, including Figma's Inter text rendering.
