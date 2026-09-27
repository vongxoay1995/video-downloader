# BrightFetch — market-led redesign brief

Date: 2026-09-24. Status: research complete; Figma redesign not applied in this pass because the official connector requires reauthentication. Existing web session is readable. No application source inspected or changed.

## Sources and evidence

- Google Play search supplied by owner: https://play.google.com/store/search?q=video+downloader&c=apps&hl=en
- InShot Video Downloader: https://play.google.com/store/apps/details?id=video.downloader.videodownloader
- 1DM: https://play.google.com/store/apps/details?id=idm.internet.download.manager
- Private Browser & Downloader: https://play.google.com/store/apps/details?id=instagram.story.reels.photo.videodownloader.saver

Listings were inspected for functionality and public reviews. The InShot screenshot gallery was also viewed directly in Chrome. Its browser/media screenshot keeps source URL, video preview and download actions together. Screenshot galleries can mix old and new promotional designs; they are evidence of patterns, not a definitive view of every current app screen.

Shared functional pattern: paste or browse → detect media → choose quality → background download queue → offline library/player. Public reviews mention intrusive ads, lost browser tabs, inconsistent speed and repetitive prompts; these are qualitative signals, not prevalence estimates.

## Current Figma observations

File: https://www.figma.com/design/CLCW7nUO2zZhSbDfsxXRI8

The visible market-v2 board has a large introductory hero, abstract thumbnails, purple/mint surfaces and a dark pill navigation bar. The composition presents the concept but does not yet feel like a content-rich everyday Android download app. These observations are from the Figma canvas, not the app implementation.

## Proposed direction: fast capture, confident progress, enjoyable playback

Retain the project identity BrightFetch. The earlier Droply name is only a concept label, not a requirement to rename the product.

1. Home: compact brand header; prominent paste-link action near the top; secondary Open browser; up to four user bookmarks; recent downloads with real preview imagery. Avoid a marketing hero above the primary task.
2. Navigation: Home / Downloads / Library, with 48dp interaction targets and clear selected state. Settings via the header.
3. Browser: compact address field, tab count and navigation. Show a bottom detection dock tied to the source page, e.g. “2 videos found”. Preserve tabs across navigation.
4. Media selection: preview, title, duration and source before quality choices. 1080p Best / 720p Smaller / Audio, each with size. Final CTA includes expected size. Do not promise downloads from every site.
5. Downloads: thumbnail and title first; progress with transferred/total bytes and estimated time; one clear pause/resume action. Separate queued, downloading, finishing, ready and failed states.
6. Error recovery: short reason plus Retry or Open source. Keep successful files visible after a failed item. No automatic re-download after deletion.
7. Library: large two-column thumbnails, duration badges, search and compact type filters; Continue watching appears only with saved playback progress.
8. Player: dark immersive surface, clear seek bar and transport controls; secondary speed, captions and fit controls grouped; portrait and landscape treatments.

## Visual specification for the next Figma pass

- Warm near-white background, ink text, restrained violet primary, soft neutral containers. Use accent colors for meaning rather than decorating every surface.
- Consistent type hierarchy: 28–32dp main headings, 18–20dp section titles, 14–16dp body; avoid tiny uppercase labels for essential information.
- Spacing: 20–24dp screen padding, 12–16dp internal gaps, 16–24dp container radii.
- Content-led imagery: representative video stills with consistent crop and duration overlays; no arbitrary gradients standing in for all media.
- Native editable frames, auto-layout, reusable navigation/buttons/media cards and semantic variables are the target. Do not describe prior SVG groups as a completed component library.

## Screen inventory

Primary: Home, Browser, Detected media, Quality sheet, Active downloads, Completed downloads, Library, Search, Portrait player, Landscape player, Settings.

Required states: checking link, unsupported/expired link, paused task, finishing task, failed download with recovery, empty downloads, empty library, no search results, player buffering/error.

## Acceptance criteria

- No codebase-derived information architecture or feature constraints.
- Existing designs preserved as an archive, not deleted.
- Primary flows navigable in a Figma prototype when connector access is restored.
- Components and variable bindings verified; no claim of implementation readiness without inspection.
- Screens checked visually at readable scale for overflow, spacing, hierarchy and contrast.
